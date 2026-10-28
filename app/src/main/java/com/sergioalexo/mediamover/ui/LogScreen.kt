package com.sergioalexo.mediamover.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sergioalexo.mediamover.model.LogAction
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(vm: AppViewModel, snackbar: SnackbarHostState, onBack: () -> Unit) {
    val log by vm.log.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    val format = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Activity") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    TextButton(onClick = vm::undoLast, enabled = log.any { it.action == LogAction.MOVED }) {
                        Text("Undo last")
                    }
                    IconButton(onClick = { confirmClear = true }, enabled = log.isNotEmpty()) {
                        Icon(Icons.Filled.Delete, "Clear log")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (log.isEmpty()) {
            Box(Modifier.padding(padding).padding(16.dp)) { Hint("Nothing moved yet.") }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                itemsIndexed(log) { _, e ->
                    ListItem(
                        overlineContent = { Text("${format.format(Date(e.time))} · ${e.rule}") },
                        headlineContent = { Text(e.from.substringAfterLast('/')) },
                        supportingContent = {
                            Text(
                                buildString {
                                    append(e.action.label)
                                    e.to?.let { append(" → /").append(it.substringBeforeLast('/')) }
                                    e.message?.let { append(" — ").append(it) }
                                },
                                color = if (e.action == LogAction.FAILED) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear activity?") },
            text = { Text("This only clears the history. Undo won't be possible for these moves afterwards.") },
            confirmButton = { TextButton(onClick = { vm.clearLog(); confirmClear = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}
