package com.sergioalexo.mediamover.data

import android.os.Environment
import java.io.File

object Storage {
    val root: File get() = Environment.getExternalStorageDirectory()

    fun hasAccess(): Boolean = Environment.isExternalStorageManager()

    fun resolve(relative: String): File = File(root, relative.trim('/'))

    fun relative(file: File): String =
        file.absolutePath.removePrefix(root.absolutePath).trimStart('/')
}
