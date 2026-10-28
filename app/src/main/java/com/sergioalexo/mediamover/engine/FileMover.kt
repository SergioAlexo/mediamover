package com.sergioalexo.mediamover.engine

import android.content.Context
import android.media.MediaScannerConnection
import com.sergioalexo.mediamover.data.Storage
import com.sergioalexo.mediamover.data.Store
import com.sergioalexo.mediamover.data.fileKey
import com.sergioalexo.mediamover.model.ConflictPolicy
import com.sergioalexo.mediamover.model.FileKind
import com.sergioalexo.mediamover.model.LogAction
import com.sergioalexo.mediamover.model.LogEntry
import com.sergioalexo.mediamover.model.Rule
import com.sergioalexo.mediamover.model.TransferMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

data class RunSummary(val entries: List<LogEntry>) {
    fun count(action: LogAction) = entries.count { it.action == action }

    fun describe(): String {
        val parts = listOfNotNull(
            count(LogAction.MOVED).takeIf { it > 0 }?.let { "moved $it" },
            count(LogAction.COPIED).takeIf { it > 0 }?.let { "copied $it" },
            count(LogAction.FAILED).takeIf { it > 0 }?.let { "$it failed" },
        )
        return if (parts.isEmpty()) "Nothing new to move" else parts.joinToString(", ").replaceFirstChar { it.uppercase() }
    }
}

object FileMover {
    /** Manual runs and background workers share this so two passes never race on the same files. */
    private val lock = Mutex()
    private val tempSuffixes = listOf(".tmp", ".temp", ".part", ".partial", ".crdownload", ".download")

    suspend fun run(context: Context, store: Store, rules: List<Rule>, dryRun: Boolean): RunSummary =
        lock.withLock {
            withContext(Dispatchers.IO) {
                val batch = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()
                val touched = mutableListOf<String>()
                val ignored = store.ignored
                val entries = rules.flatMap { runRule(it, batch, now, dryRun, ignored, touched) }
                if (!dryRun) {
                    rescan(context, touched)
                    store.appendLog(entries)
                }
                RunSummary(entries)
            }
        }

    /** Moves the files of the most recent batch that still has moves back where they came from. */
    suspend fun undoLastBatch(context: Context, store: Store): Int =
        lock.withLock {
            withContext(Dispatchers.IO) {
                val batch = store.log.value.firstOrNull { it.action == LogAction.MOVED }?.batch
                    ?: return@withContext 0
                val touched = mutableListOf<String>()
                val undone = store.log.value
                    .filter { it.batch == batch && it.action == LogAction.MOVED }
                    .filter { e ->
                        val to = e.to?.let(Storage::resolve) ?: return@filter false
                        val from = Storage.resolve(e.from)
                        if (!to.isFile || from.exists()) return@filter false
                        from.parentFile?.mkdirs()
                        to.renameTo(from).also { ok -> if (ok) touched += listOf(to.path, from.path) }
                    }
                rescan(context, touched)
                store.markUndone(undone)
                // Otherwise the next automatic run would move them right back out.
                store.ignore(undone.map { Storage.resolve(it.from) })
                undone.size
            }
        }

    private fun runRule(
        rule: Rule,
        batch: String,
        now: Long,
        dryRun: Boolean,
        ignored: Set<String>,
        touched: MutableList<String>,
    ): List<LogEntry> {
        val out = mutableListOf<LogEntry>()
        fun entry(action: LogAction, from: File, to: File?, message: String? = null) =
            LogEntry(now, batch, rule.name, action, Storage.relative(from), to?.let(Storage::relative), message)

        val dest = Storage.resolve(rule.destination)
        if (!dryRun && !dest.isDirectory && !dest.mkdirs()) {
            out += entry(LogAction.FAILED, dest, null, "Couldn't create destination folder")
            return out
        }
        val destCanonical = dest.canonicalPath

        for (sourceRel in rule.sources) {
            val source = Storage.resolve(sourceRel)
            if (!source.isDirectory || source.canonicalPath == destCanonical) continue

            for (file in collect(source, rule, destCanonical, now)) {
                if (ignored.isNotEmpty() && fileKey(file) in ignored) continue
                val subdir = if (rule.keepStructure) file.parentFile?.relativeTo(source)?.path.orEmpty() else ""
                val wanted = File(if (subdir.isEmpty()) dest else File(dest, subdir), file.name)

                // Copy mode leaves the original in place, so recognise what we already copied.
                if (rule.mode == TransferMode.COPY && wanted.isFile && wanted.length() == file.length()) continue

                val target = pickTarget(wanted, rule.conflict)
                if (target == null) {
                    if (dryRun) out += entry(LogAction.SKIPPED, file, wanted, "Name already taken")
                    continue
                }
                if (dryRun) {
                    out += entry(LogAction.PLANNED, file, target)
                    continue
                }
                try {
                    transfer(file, target, rule.mode)
                    if (rule.mode == TransferMode.MOVE) touched += file.path
                    touched += target.path
                    out += entry(if (rule.mode == TransferMode.MOVE) LogAction.MOVED else LogAction.COPIED, file, target)
                } catch (e: Exception) {
                    out += entry(LogAction.FAILED, file, target, e.message ?: e.javaClass.simpleName)
                }
            }
        }
        return out
    }

    private fun collect(dir: File, rule: Rule, destCanonical: String, now: Long): List<File> {
        val result = mutableListOf<File>()
        fun walk(d: File) {
            for (child in d.listFiles() ?: return) {
                if (child.name.startsWith(".")) continue // .nomedia, .Statuses, .trash, …
                if (child.isDirectory) {
                    if (!rule.includeSubfolders) continue
                    if (rule.skipSent && child.name.equals("Sent", ignoreCase = true)) continue
                    val canonical = child.canonicalPath
                    if (canonical == destCanonical || canonical.startsWith("$destCanonical/")) continue
                    walk(child)
                } else if (child.isFile && accepts(child, rule, now)) {
                    result += child
                }
            }
        }
        walk(dir)
        return result
    }

    private fun accepts(file: File, rule: Rule, now: Long): Boolean {
        val name = file.name.lowercase()
        if (tempSuffixes.any { name.endsWith(it) } || name.startsWith(".pending-")) return false
        if (file.length() == 0L) return false
        if (now - file.lastModified() < rule.minAgeSeconds * 1000L) return false
        return rule.kinds.isEmpty() || FileKind.of(file.name) in rule.kinds
    }

    private fun pickTarget(wanted: File, policy: ConflictPolicy): File? {
        if (!wanted.exists()) return wanted
        return when (policy) {
            ConflictPolicy.SKIP -> null
            ConflictPolicy.OVERWRITE -> wanted
            ConflictPolicy.RENAME -> {
                val base = wanted.nameWithoutExtension
                val ext = wanted.extension.let { if (it.isEmpty()) "" else ".$it" }
                generateSequence(1) { it + 1 }
                    .map { File(wanted.parentFile, "$base ($it)$ext") }
                    .first { !it.exists() }
            }
        }
    }

    private fun transfer(source: File, target: File, mode: TransferMode) {
        target.parentFile?.mkdirs()
        if (target.exists() && !target.delete()) throw IOException("Couldn't replace existing file")
        // Same volume: a rename is instant and keeps the timestamp.
        if (mode == TransferMode.MOVE && source.renameTo(target)) return

        source.inputStream().use { input -> target.outputStream().use { input.copyTo(it) } }
        if (target.length() != source.length()) {
            target.delete()
            throw IOException("Copy was incomplete")
        }
        target.setLastModified(source.lastModified())
        if (mode == TransferMode.MOVE && !source.delete()) throw IOException("Copied, but couldn't remove the original")
    }

    /** Lets Photos / Files see the new locations and forget the old ones. */
    private fun rescan(context: Context, paths: List<String>) {
        if (paths.isNotEmpty()) MediaScannerConnection.scanFile(context, paths.toTypedArray(), null, null)
    }
}
