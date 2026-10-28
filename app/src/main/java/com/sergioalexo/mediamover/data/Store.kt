package com.sergioalexo.mediamover.data

import android.content.Context
import com.sergioalexo.mediamover.model.AppSettings
import com.sergioalexo.mediamover.model.LogAction
import com.sergioalexo.mediamover.model.LogEntry
import com.sergioalexo.mediamover.model.Rule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** Rules + settings in SharedPreferences, activity log in a JSON file. One instance per process. */
class Store(context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val prefs = context.getSharedPreferences("mediamover", Context.MODE_PRIVATE)
    private val logFile = File(context.filesDir, "log.json")

    private val _rules = MutableStateFlow(decode<List<Rule>>(prefs.getString(KEY_RULES, null)) ?: emptyList())
    val rules: StateFlow<List<Rule>> = _rules.asStateFlow()

    private val _settings = MutableStateFlow(decode<AppSettings>(prefs.getString(KEY_SETTINGS, null)) ?: AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    /** Newest first. */
    private val _log = MutableStateFlow(
        decode<List<LogEntry>>(logFile.takeIf { it.exists() }?.readText()) ?: emptyList()
    )
    val log: StateFlow<List<LogEntry>> = _log.asStateFlow()

    /** Files the user moved back with Undo; rules leave them alone until they change. Keys from [fileKey]. */
    @Volatile
    var ignored: Set<String> = prefs.getStringSet(KEY_IGNORED, null)?.toSet() ?: emptySet()
        private set

    @Synchronized
    fun ignore(files: List<File>) {
        if (files.isEmpty()) return
        // Drop keys for files that no longer exist so the set doesn't grow forever.
        val live = ignored.filter { File(it.substringBefore('|')).exists() }
        ignored = (live + files.map(::fileKey)).toSet()
        prefs.edit().putStringSet(KEY_IGNORED, ignored).apply()
    }

    @Synchronized
    fun saveRule(rule: Rule) {
        val list = _rules.value
        val i = list.indexOfFirst { it.id == rule.id }
        _rules.value = if (i >= 0) list.toMutableList().also { it[i] = rule } else list + rule
        persistRules()
    }

    @Synchronized
    fun deleteRule(id: String) {
        _rules.value = _rules.value.filterNot { it.id == id }
        persistRules()
    }

    @Synchronized
    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        _settings.value = transform(_settings.value)
        prefs.edit().putString(KEY_SETTINGS, json.encodeToString(_settings.value)).apply()
    }

    @Synchronized
    fun appendLog(entries: List<LogEntry>) {
        if (entries.isEmpty()) return
        _log.value = (entries.asReversed() + _log.value).take(MAX_LOG)
        persistLog()
    }

    @Synchronized
    fun markUndone(entries: List<LogEntry>) {
        if (entries.isEmpty()) return
        val keys = entries.map { it.batch to it.from }.toSet()
        _log.value = _log.value.map {
            if (it.action == LogAction.MOVED && (it.batch to it.from) in keys) it.copy(action = LogAction.UNDONE) else it
        }
        persistLog()
    }

    @Synchronized
    fun clearLog() {
        _log.value = emptyList()
        logFile.delete()
    }

    private fun persistRules() {
        prefs.edit().putString(KEY_RULES, json.encodeToString(_rules.value)).apply()
    }

    private fun persistLog() {
        val tmp = File(logFile.parentFile, "log.json.tmp")
        tmp.writeText(json.encodeToString(_log.value))
        tmp.renameTo(logFile)
    }

    private inline fun <reified T> decode(text: String?): T? =
        text?.let { runCatching { json.decodeFromString<T>(it) }.getOrNull() }

    private companion object {
        const val KEY_RULES = "rules"
        const val KEY_SETTINGS = "settings"
        const val KEY_IGNORED = "ignored"
        const val MAX_LOG = 1000
    }
}

fun fileKey(file: File) = "${file.path}|${file.length()}|${file.lastModified()}"
