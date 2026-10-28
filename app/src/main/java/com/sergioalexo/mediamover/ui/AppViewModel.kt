package com.sergioalexo.mediamover.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sergioalexo.mediamover.MediaMoverApp
import com.sergioalexo.mediamover.data.Storage
import com.sergioalexo.mediamover.engine.FileMover
import com.sergioalexo.mediamover.model.AppSettings
import com.sergioalexo.mediamover.model.LogEntry
import com.sergioalexo.mediamover.model.Rule
import com.sergioalexo.mediamover.work.Scheduler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = (app as MediaMoverApp).store

    val rules = store.rules
    val settings = store.settings
    val log = store.log

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** Dry-run result waiting to be shown; null when no preview is open. */
    private val _preview = MutableStateFlow<List<LogEntry>?>(null)
    val preview: StateFlow<List<LogEntry>?> = _preview.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    fun runNow(dryRun: Boolean) = viewModelScope.launch {
        if (!Storage.hasAccess()) return@launch say("Allow “All files access” first")
        val enabled = rules.value.filter { it.enabled }
        if (enabled.isEmpty()) return@launch say("No active rules")
        _busy.value = true
        try {
            val summary = FileMover.run(getApplication(), store, enabled, dryRun)
            if (dryRun) {
                if (summary.entries.isEmpty()) say("Nothing to move right now") else _preview.value = summary.entries
            } else {
                say(summary.describe())
            }
        } finally {
            _busy.value = false
        }
    }

    fun closePreview() {
        _preview.value = null
    }

    fun undoLast() = viewModelScope.launch {
        val n = FileMover.undoLastBatch(getApplication(), store)
        say(if (n == 0) "Nothing to undo" else "Moved $n file(s) back")
    }

    fun save(rule: Rule) = store.saveRule(rule)
    fun delete(id: String) = store.deleteRule(id)
    fun setEnabled(id: String, enabled: Boolean) {
        rules.value.find { it.id == id }?.let { store.saveRule(it.copy(enabled = enabled)) }
    }

    fun clearLog() = store.clearLog()

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        store.updateSettings(transform)
        Scheduler.apply(getApplication(), store.settings.value)
    }

    private suspend fun say(text: String) {
        _messages.emit(text)
    }
}
