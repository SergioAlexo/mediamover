package com.sergioalexo.mediamover.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val intervalChoices = listOf(0 to "Off", 15 to "15 min", 60 to "1 hour", 360 to "6 hours")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    vm: AppViewModel,
    hasAccess: Boolean,
    batteryUnrestricted: Boolean,
    onRequestAccess: () -> Unit,
    onRequestBattery: () -> Unit,
    onBack: () -> Unit,
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val version = remember(context) { context.packageManager.getPackageInfo(context.packageName, 0).versionName }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Section("Permissions") {
                StatusRow(
                    title = "All files access",
                    status = if (hasAccess) "Granted" else "Needed to read messenger folders",
                    ok = hasAccess,
                    onFix = onRequestAccess,
                )
                StatusRow(
                    title = "Unrestricted battery",
                    status = if (batteryUnrestricted) "On" else "Recommended so Android doesn't delay background moves",
                    ok = batteryUnrestricted,
                    onFix = onRequestBattery,
                )
            }

            Section("Automation") {
                SwitchRow(
                    "Move automatically",
                    "Runs about 15 seconds after new photos, videos or files appear",
                    settings.instant,
                ) { on -> vm.updateSettings { it.copy(instant = on) } }
                Text("Also check every", style = MaterialTheme.typography.bodyLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    intervalChoices.forEach { (minutes, label) ->
                        FilterChip(
                            selected = settings.intervalMinutes == minutes,
                            onClick = { vm.updateSettings { it.copy(intervalMinutes = minutes) } },
                            label = { Text(label) },
                        )
                    }
                }
                Hint("A safety net in case Android skips an automatic run.")
            }

            Section("Telegram") {
                Hint(
                    "Telegram keeps its downloads in a folder Android hides from other apps. In Telegram → Settings → " +
                        "Chat Settings, turn on “Save to Gallery” so files land in Pictures/Telegram and Download/Telegram, " +
                        "where MediaMover can pick them up."
                )
            }

            Hint("MediaMover $version")
        }
    }
}

@Composable
private fun StatusRow(title: String, status: String, ok: Boolean, onFix: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
        if (!ok) {
            Spacer(Modifier.width(12.dp))
            FilledTonalButton(onClick = onFix) { Text("Allow") }
        }
    }
}
