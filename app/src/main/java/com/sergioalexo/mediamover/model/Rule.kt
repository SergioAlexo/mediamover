package com.sergioalexo.mediamover.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class TransferMode(val label: String) { MOVE("Move"), COPY("Copy") }

@Serializable
enum class ConflictPolicy(val label: String) { RENAME("Rename"), SKIP("Skip"), OVERWRITE("Overwrite") }

@Serializable
enum class FileKind(val label: String) {
    IMAGE("Photos"), VIDEO("Videos"), AUDIO("Audio"), DOCUMENT("Documents"), OTHER("Other");

    companion object {
        private val byExtension = buildMap {
            listOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "dng").forEach { put(it, IMAGE) }
            listOf("mp4", "mkv", "mov", "3gp", "webm", "avi", "m4v").forEach { put(it, VIDEO) }
            listOf("mp3", "m4a", "aac", "opus", "ogg", "wav", "flac", "amr").forEach { put(it, AUDIO) }
            listOf(
                "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "rtf", "odt",
                "zip", "rar", "7z", "apk", "vcf",
            ).forEach { put(it, DOCUMENT) }
        }

        fun of(fileName: String): FileKind =
            byExtension[fileName.substringAfterLast('.', "").lowercase()] ?: OTHER
    }
}

/**
 * One "take files from these folders and put them there" rule.
 * Paths are relative to primary shared storage (/storage/emulated/0).
 */
@Serializable
data class Rule(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val sources: List<String>,
    val destination: String,
    val enabled: Boolean = true,
    val mode: TransferMode = TransferMode.MOVE,
    /** Empty means every kind. */
    val kinds: Set<FileKind> = emptySet(),
    val includeSubfolders: Boolean = true,
    /** WhatsApp keeps media you sent in "Sent" subfolders. */
    val skipSent: Boolean = true,
    val keepStructure: Boolean = false,
    val conflict: ConflictPolicy = ConflictPolicy.RENAME,
    /** Files younger than this are left alone so in-progress downloads can finish. */
    val minAgeSeconds: Int = 10,
)

@Serializable
data class AppSettings(
    val instant: Boolean = true,
    /** 0 = off. WorkManager's minimum is 15. */
    val intervalMinutes: Int = 15,
)

@Serializable
enum class LogAction(val label: String) {
    MOVED("Moved"), COPIED("Copied"), PLANNED("Would move"), SKIPPED("Skipped"), FAILED("Failed"), UNDONE("Moved back")
}

@Serializable
data class LogEntry(
    val time: Long,
    val batch: String,
    val rule: String,
    val action: LogAction,
    val from: String,
    val to: String? = null,
    val message: String? = null,
)
