package com.sergioalexo.mediamover.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sergioalexo.mediamover.model.LogAction
import com.sergioalexo.mediamover.model.LogEntry
import com.sergioalexo.mediamover.model.Rule

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: AppViewModel,
    hasAccess: Boolean,
    snackbar: SnackbarHostState,
    onRequestAccess: () -> Unit,
    onEdit: (String?) -> Unit,
    onLog: () -> Unit,
    onSettings: () -> Unit,
) {
    val rules by vm.rules.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MediaMover") },
                actions = {
                    IconButton(onClick = onLog) { Icon(Icons.AutoMirrored.Filled.List, "Activity") }
                    IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Settings") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onEdit(null) },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Add rule") },
            )
        },
        bottomBar = {
            BottomAppBar {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(onClick = { vm.runNow(dryRun = true) }, enabled = !busy, modifier = Modifier.weight(1f)) {
                        Text("Preview")
                    }
                    Button(onClick = { vm.runNow(dryRun = false) }, enabled = !busy, modifier = Modifier.weight(1f)) {
                        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Run now")
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!hasAccess) item { AccessCard(onRequestAccess) }
            if (rules.isEmpty()) {
                item {
                    Column(Modifier.padding(vertical = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("No rules yet", style = MaterialTheme.typography.titleMedium)
                        Hint("Add a rule, tap WhatsApp, Telegram, Viber or Instagram, and pick where the files should go.")
                    }
                }
            }
            items(rules, key = { it.id }) { rule ->
                RuleCard(rule, onClick = { onEdit(rule.id) }, onToggle = { vm.setEnabled(rule.id, it) })
            }
        }
    }
}

@Composable
private fun AccessCard(onRequestAccess: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, null)
                Spacer(Modifier.width(8.dp))
                Text("Needs “All files access”", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "Android only lets an app reach WhatsApp, Viber and other messenger folders with this permission.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onRequestAccess) { Text("Allow") }
        }
    }
}

@Composable
private fun RuleCard(rule: Rule, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(rule.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${rule.sources.size} folder(s) → /${rule.destination}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val kinds = if (rule.kinds.isEmpty()) "All files" else rule.kinds.joinToString { it.label }
                Hint("${rule.mode.label} · $kinds")
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = rule.enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
fun PreviewDialog(entries: List<LogEntry>, onDismiss: () -> Unit) {
    val planned = entries.count { it.action == LogAction.PLANNED }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("$planned file(s) would move") },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(entries) { e ->
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Text(e.from.substringAfterLast('/'), style = MaterialTheme.typography.bodyMedium)
                        Hint(
                            if (e.action == LogAction.SKIPPED) "Skipped — ${e.message}"
                            else "→ /${e.to?.substringBeforeLast('/')}"
                        )
                    }
                    HorizontalDivider()
                }
            }
        },
    )
}
