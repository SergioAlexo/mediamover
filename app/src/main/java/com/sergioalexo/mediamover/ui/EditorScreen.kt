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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sergioalexo.mediamover.model.ConflictPolicy
import com.sergioalexo.mediamover.model.FileKind
import com.sergioalexo.mediamover.model.Presets
import com.sergioalexo.mediamover.model.Rule
import com.sergioalexo.mediamover.model.TransferMode

private enum class PickFor { SOURCE, DESTINATION }

private val waitChoices = listOf(0 to "Right away", 10 to "10 s", 60 to "1 min", 3600 to "1 hour", 86400 to "1 day")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(initial: Rule?, onSave: (Rule) -> Unit, onDelete: (String) -> Unit, onBack: () -> Unit) {
    var draft by remember { mutableStateOf(initial ?: Rule(name = "", sources = emptyList(), destination = "Download")) }
    var pickFor by remember { mutableStateOf<PickFor?>(null) }
    var presetNote by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val valid = draft.name.isNotBlank() && draft.sources.isNotEmpty() && draft.destination.isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (initial == null) "New rule" else "Edit rule") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (initial != null) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete rule") }
                    }
                    TextButton(onClick = { onSave(draft.copy(name = draft.name.trim())) }, enabled = valid) { Text("Save") }
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
            Section("Quick pick") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Presets.all.forEach { preset ->
                        AssistChip(
                            onClick = {
                                val found = Presets.detect(preset)
                                draft = draft.copy(
                                    name = draft.name.ifBlank { preset.name },
                                    sources = (draft.sources + found).distinct(),
                                )
                                presetNote = if (found.isEmpty()) {
                                    "No ${preset.name} folders on this phone yet" +
                                        if (preset.name == "Telegram") " — turn on “Save to Gallery” in Telegram." else "."
                                } else {
                                    "Added ${found.size} ${preset.name} folder(s)."
                                }
                            },
                            label = { Text(preset.name) },
                        )
                    }
                }
                presetNote?.let { Hint(it) }
            }

            OutlinedTextField(
                value = draft.name,
                onValueChange = { draft = draft.copy(name = it) },
                label = { Text("Rule name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Section("Take files from") {
                if (draft.sources.isEmpty()) Hint("No folders yet.")
                draft.sources.forEach { source ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("/$source", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { draft = draft.copy(sources = draft.sources - source) }) {
                            Icon(Icons.Filled.Close, "Remove")
                        }
                    }
                }
                OutlinedButton(onClick = { pickFor = PickFor.SOURCE }) {
                    Icon(Icons.Filled.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add folder")
                }
            }

            Section("Put them in") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("/${draft.destination}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { pickFor = PickFor.DESTINATION }) { Text("Choose") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Download", "Download/${draft.name.trim().ifBlank { "Media" }}").distinct().forEach { choice ->
                        FilterChip(
                            selected = draft.destination == choice,
                            onClick = { draft = draft.copy(destination = choice) },
                            label = { Text(choice) },
                        )
                    }
                }
            }

            Section("Action") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    TransferMode.entries.forEachIndexed { i, mode ->
                        SegmentedButton(
                            selected = draft.mode == mode,
                            onClick = { draft = draft.copy(mode = mode) },
                            shape = SegmentedButtonDefaults.itemShape(i, TransferMode.entries.size),
                        ) { Text(mode.label) }
                    }
                }
                Hint(
                    if (draft.mode == TransferMode.MOVE) {
                        "Moved files show as “media not found” inside the chat. The files themselves are safe in the new folder."
                    } else {
                        "Originals stay where they are; files already copied are recognised and not copied again."
                    }
                )
            }

            Section("File types") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FileKind.entries.forEach { kind ->
                        FilterChip(
                            selected = kind in draft.kinds,
                            onClick = {
                                draft = draft.copy(kinds = if (kind in draft.kinds) draft.kinds - kind else draft.kinds + kind)
                            },
                            label = { Text(kind.label) },
                        )
                    }
                }
                Hint(if (draft.kinds.isEmpty()) "Nothing selected = all files." else "Only the selected types are moved.")
            }

            Section("Options") {
                SwitchRow("Include subfolders", "e.g. WhatsApp Images, WhatsApp Video…", draft.includeSubfolders) {
                    draft = draft.copy(includeSubfolders = it)
                }
                SwitchRow("Skip “Sent” folders", "Leave media you sent yourself where it is", draft.skipSent) {
                    draft = draft.copy(skipSent = it)
                }
                SwitchRow("Keep folder structure", "Recreate subfolders in the destination", draft.keepStructure) {
                    draft = draft.copy(keepStructure = it)
                }
            }

            Section("If a file with the same name exists") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ConflictPolicy.entries.forEachIndexed { i, policy ->
                        SegmentedButton(
                            selected = draft.conflict == policy,
                            onClick = { draft = draft.copy(conflict = policy) },
                            shape = SegmentedButtonDefaults.itemShape(i, ConflictPolicy.entries.size),
                        ) { Text(policy.label) }
                    }
                }
                Hint("Rename saves it as “name (1).jpg”.")
            }

            Section("Wait before moving") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    waitChoices.forEach { (seconds, label) ->
                        FilterChip(
                            selected = draft.minAgeSeconds == seconds,
                            onClick = { draft = draft.copy(minAgeSeconds = seconds) },
                            label = { Text(label) },
                        )
                    }
                }
                Hint("Files newer than this are left alone so downloads can finish.")
            }
        }
    }

    pickFor?.let { target ->
        FolderPickerDialog(
            start = if (target == PickFor.DESTINATION) draft.destination else draft.sources.lastOrNull().orEmpty(),
            onDismiss = { pickFor = null },
            onPick = { picked ->
                draft = when (target) {
                    PickFor.SOURCE -> draft.copy(sources = (draft.sources + picked).distinct())
                    PickFor.DESTINATION -> draft.copy(destination = picked)
                }
                pickFor = null
            },
        )
    }

    if (confirmDelete && initial != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete “${initial.name}”?") },
            text = { Text("Files that were already moved stay where they are.") },
            confirmButton = { TextButton(onClick = { onDelete(initial.id) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
