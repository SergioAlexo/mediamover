package com.sergioalexo.mediamover.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sergioalexo.mediamover.data.Storage
import java.io.File

/** Full-screen folder browser, drawn over the editor. Plain File-based — works because we hold All files access, and shows real paths (SAF can't). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPicker(start: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val root = remember { Storage.root }
    var current by remember {
        mutableStateOf(Storage.resolve(start).takeIf { start.isNotBlank() && it.isDirectory } ?: root)
    }
    var refresh by remember { mutableIntStateOf(0) }
    var creating by remember { mutableStateOf(false) }
    val children = remember(current, refresh) {
        current.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.sortedBy { it.name.lowercase() }
    }
    val atRoot = current == root

    BackHandler { if (atRoot) onDismiss() else current = current.parentFile ?: root }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.systemBarsPadding()) {
            TopAppBar(
                title = { Text(if (atRoot) "Internal storage" else current.name) },
                navigationIcon = {
                    IconButton(onClick = { if (atRoot) onDismiss() else current = current.parentFile ?: root }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Up")
                    }
                },
                actions = {
                    IconButton(onClick = { creating = true }) { Icon(Icons.Filled.Add, "New folder") }
                },
            )
            Text(
                "/" + Storage.relative(current),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            HorizontalDivider()
            Box(Modifier.weight(1f)) {
                when {
                    children == null -> Box(Modifier.padding(16.dp)) {
                        Hint("Android doesn't let apps open this folder.")
                    }
                    children.isEmpty() -> Box(Modifier.padding(16.dp)) { Hint("No subfolders.") }
                    else -> LazyColumn {
                        items(children, key = { it.path }) { dir ->
                            ListItem(
                                headlineContent = { Text(dir.name) },
                                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                                modifier = Modifier.clickable { current = dir },
                            )
                        }
                    }
                }
            }
            HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(onClick = { onPick(Storage.relative(current)) }, enabled = !atRoot) { Text("Use this folder") }
            }
        }
    }

    if (creating) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("New folder") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name") })
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank() && !name.contains('/'),
                    onClick = {
                        val dir = File(current, name.trim())
                        if (dir.isDirectory || dir.mkdirs()) current = dir else refresh++
                        creating = false
                    },
                ) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("Cancel") } },
        )
    }
}
