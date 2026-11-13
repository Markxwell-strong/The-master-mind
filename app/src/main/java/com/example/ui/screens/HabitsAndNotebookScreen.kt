package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.NoteItem
import com.example.data.model.UserWorkspaceConfig
import com.example.ui.components.MasterMindAdaptiveBoard
import com.example.ui.components.PulseCard
import com.example.ui.components.SectionHeaderRow
import com.example.ui.viewmodel.FocusPulseViewModel
import com.example.ui.viewmodel.UiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HabitsAndNotebookScreen(
    viewModel: FocusPulseViewModel,
    showHeader: Boolean = true
) {
    val configState by viewModel.workspaceConfig.collectAsStateWithLifecycle()
    val habits by viewModel.habits.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val config = (configState as? UiState.Success)?.data ?: UserWorkspaceConfig()

    var activeTab by remember { mutableIntStateOf(0) } // 0 = Habits, 1 = Notebook & Reminders
    var showAddHabit by remember { mutableStateOf(false) }
    var showAddNote by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (showHeader) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Habits & Notebook",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Button(
                        onClick = {
                            if (activeTab == 0) showAddHabit = true else showAddNote = true
                        },
                        modifier = Modifier.testTag("habits_notes_add_button")
                    ) {
                        Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (activeTab == 0) "New Habit" else "New Note")
                    }
                }
            }
        } else {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = {
                            if (activeTab == 0) showAddHabit = true else showAddNote = true
                        },
                        modifier = Modifier.testTag("habits_notes_add_button")
                    ) {
                        Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (activeTab == 0) "New Habit" else "New Note")
                    }
                }
            }
        }

        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    label = { Text("Habit Controller") },
                    leadingIcon = { Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    label = { Text("Notebook & Reminders") },
                    leadingIcon = { Icon(Icons.Default.EditNote, null, Modifier.size(16.dp)) }
                )
            }
        }

        if (activeTab == 0) {
            if (habits.isEmpty()) {
                item {
                    PulseCard {
                        SectionHeaderRow(
                            title = "Build Intentional Routines",
                            subtitle = "Track daily habits, streaks, and anti-doomscroll replacements.",
                            icon = Icons.Default.CheckCircle
                        )
                        OutlinedButton(onClick = { showAddHabit = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Create Your First Habit")
                        }
                    }
                }
            } else {
                val habitBlocks: List<@Composable (Modifier) -> Unit> = habits.map { habit ->
                    { mod ->
                        val progress = (habit.completedToday.toFloat() / habit.targetPerDay.coerceAtLeast(1L)).coerceIn(0f, 1f)
                        PulseCard(modifier = mod, accentBorder = progress >= 1f) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(habit.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "${habit.category.uppercase()} • ${habit.completedToday}/${habit.targetPerDay}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Icon(
                                            Icons.Default.LocalFireDepartment,
                                            contentDescription = "Streak",
                                            tint = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "${habit.streakDays}d",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                    }
                                }
                                Row {
                                    Button(
                                        onClick = { viewModel.incrementHabit(habit) },
                                        modifier = Modifier.testTag("habit_check_${habit.id}")
                                    ) {
                                        Text(if (progress >= 1f) "Done" else "+1")
                                    }
                                    IconButton(onClick = { viewModel.deleteHabit(habit.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete habit")
                                    }
                                }
                            }
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                        }
                    }
                }
                item {
                    MasterMindAdaptiveBoard(
                        layoutStyle = config.appLayoutStyle,
                        blocks = habitBlocks
                    )
                }
            }
        } else {
            val sortedNotes = notes.sortedWith(
                compareByDescending<NoteItem> { it.isPinned }.thenBy { it.isCompleted }
            )
            if (sortedNotes.isEmpty()) {
                item {
                    PulseCard {
                        SectionHeaderRow(
                            title = "Inclusive Notebook & Alarms",
                            subtitle = "Capture ideas, tasks, and timed reminders synced to the cloud.",
                            icon = Icons.Default.EditNote
                        )
                        OutlinedButton(onClick = { showAddNote = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Create First Note or Reminder")
                        }
                    }
                }
            } else {
                val noteBlocks: List<@Composable (Modifier) -> Unit> = sortedNotes.map { note ->
                    { mod ->
                        PulseCard(modifier = mod, accentBorder = note.isPinned) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                IconButton(onClick = { viewModel.toggleNoteComplete(note) }) {
                                    Icon(
                                        imageVector = if (note.isCompleted) Icons.Default.CheckCircle else Icons.Outlined.CheckCircle,
                                        contentDescription = "Toggle note complete",
                                        tint = if (note.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = note.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        textDecoration = if (note.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                                    )
                                    if (note.content.isNotBlank()) {
                                        Text(
                                            text = note.content,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (note.reminderAtMillis > 0L) {
                                        val timeStr = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
                                            .format(Date(note.reminderAtMillis))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(Icons.Default.Alarm, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                                            Text(
                                                text = "Reminder: $timeStr",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                    }
                                }
                                Row {
                                    IconButton(onClick = { viewModel.toggleNotePin(note) }) {
                                        Icon(
                                            imageVector = if (note.isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                                            contentDescription = "Pin note"
                                        )
                                    }
                                    IconButton(onClick = { viewModel.deleteNote(note.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete note")
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    MasterMindAdaptiveBoard(
                        layoutStyle = config.appLayoutStyle,
                        blocks = noteBlocks
                    )
                }
            }
        }
        item { Spacer(Modifier.height(72.dp)) }
    }

    if (showAddHabit) {
        AddHabitDialog(
            onDismiss = { showAddHabit = false },
            onConfirm = { title, cat, target, reminder ->
                viewModel.createHabit(title, cat, target, reminder)
                showAddHabit = false
            }
        )
    }

    if (showAddNote) {
        AddNoteDialog(
            onDismiss = { showAddNote = false },
            onConfirm = { title, content, cat, remindInMins ->
                val reminderMs = if (remindInMins > 0L) System.currentTimeMillis() + remindInMins * 60_000L else 0L
                viewModel.saveNote(
                    NoteItem(
                        title = title,
                        content = content,
                        category = cat,
                        reminderAtMillis = reminderMs
                    )
                )
                showAddNote = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddHabitDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, cat: String, target: Long, reminder: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("focus") }
    var target by remember { mutableStateOf("1") }
    var reminder by remember { mutableStateOf("08:00") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Habit") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Habit Name (e.g. No phone in bed)") },
                    singleLine = true
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("focus", "wellness", "sleep", "business", "mindset").forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it.filter { c -> c.isDigit() } },
                    label = { Text("Daily Target Count") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = reminder,
                    onValueChange = { reminder = it },
                    label = { Text("Reminder Time (e.g. 08:00)") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onConfirm(title.trim(), category, target.toLongOrNull() ?: 1L, reminder.trim())
                    }
                }
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddNoteDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, content: String, cat: String, remindInMins: Long) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("note") }
    var addReminder by remember { mutableStateOf(false) }
    var remindMins by remember { mutableStateOf("30") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Note / Reminder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Details") },
                    minLines = 3
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("note", "reminder", "idea", "work").forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Schedule Notification Alarm")
                    Switch(checked = addReminder, onCheckedChange = { addReminder = it })
                }
                if (addReminder) {
                    OutlinedTextField(
                        value = remindMins,
                        onValueChange = { remindMins = it.filter { c -> c.isDigit() } },
                        label = { Text("Remind me in (minutes)") },
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val mins = if (addReminder) (remindMins.toLongOrNull() ?: 30L) else 0L
                        onConfirm(title.trim(), content.trim(), category, mins)
                    }
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
