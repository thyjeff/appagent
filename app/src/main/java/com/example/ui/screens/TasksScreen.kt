package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.TaskEntity
import com.example.ui.FocusPilotViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(viewModel: FocusPilotViewModel) {
    val context = LocalContext.current
    val tasks by viewModel.tasks.collectAsState()
    val reminders by viewModel.reminders.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddTaskDialog by remember { mutableStateOf(false) }
    var showAddReminderDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Tasks (${tasks.count { !it.completed }})", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Scheduled Reminders (${reminders.count { !it.isFired }})", fontWeight = FontWeight.Bold) }
                )
            }

            if (selectedTab == 0) {
                // Tasks List
                if (tasks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outlineVariant)
                            Text("No tasks currently scheduled", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Tap '+' to create a task or let Hermes add one via /v1/tasks", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(tasks, key = { it.id }) { task ->
                            TaskItemCard(
                                task = task,
                                onToggle = { viewModel.toggleTask(task.id, it) },
                                onDelete = { viewModel.deleteTask(task.id) }
                            )
                        }
                    }
                }
            } else {
                // Reminders List
                if (reminders.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Alarm, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outlineVariant)
                            Text("No active reminders", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Alarms work offline without requiring agent connectivity", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(reminders, key = { it.id }) { reminder ->
                            ReminderItemCard(
                                reminder = reminder,
                                onDelete = { viewModel.deleteReminder(reminder.id) }
                            )
                        }
                    }
                }
            }
        }

        // Floating Action Button
        FloatingActionButton(
            onClick = {
                if (selectedTab == 0) showAddTaskDialog = true else showAddReminderDialog = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .testTag("fab_add_task_or_reminder"),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add New Item")
        }
    }

    // Add Task Dialog
    if (showAddTaskDialog) {
        var title by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        var category by remember { mutableStateOf("Focus") }
        var priority by remember { mutableStateOf("HIGH") }

        AlertDialog(
            onDismissRequest = { showAddTaskDialog = false },
            title = { Text("New Scheduled Task") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Task Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description / Target") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Category") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Reminder Before Start
                    var remindBeforeMinutes by remember { mutableIntStateOf(10) }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Alarm Reminder Before Start", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(0 to "None", 10 to "10m", 30 to "30m", 60 to "1h").forEach { (m, lbl) ->
                                val sel = remindBeforeMinutes == m
                                Surface(
                                    onClick = { remindBeforeMinutes = m },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = lbl,
                                            fontSize = 11.sp,
                                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                            color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            viewModel.addTask(title, description, priority, category)
                            // If reminder requested, schedule exact reminder
                            showAddTaskDialog = false
                            Toast.makeText(context, "Task created", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Add Task")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTaskDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add Reminder Dialog with 10m, 15m, 30m, 1h Presets
    if (showAddReminderDialog) {
        var title by remember { mutableStateOf("") }
        var message by remember { mutableStateOf("") }
        var minutesDelay by remember { mutableStateOf("10") }

        AlertDialog(
            onDismissRequest = { showAddReminderDialog = false },
            title = { Text("Schedule Alarm Reminder") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Reminder Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it },
                        label = { Text("Notification Message") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Quick Presets (Before Task):", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(10 to "10 min", 15 to "15 min", 30 to "30 min", 60 to "1 hour").forEach { (m, lbl) ->
                            val isSel = minutesDelay == m.toString()
                            Surface(
                                onClick = { minutesDelay = m.toString() },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = lbl,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = minutesDelay,
                        onValueChange = { minutesDelay = it },
                        label = { Text("Trigger in (minutes)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val mins = minutesDelay.toLongOrNull() ?: 10L
                        if (title.isNotBlank()) {
                            val msg = message.ifBlank { "Hermes scheduled reminder for $title." }
                            viewModel.scheduleReminder(title, msg, mins * 60)
                            showAddReminderDialog = false
                            Toast.makeText(context, "Exact alarm scheduled for $mins mins", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Schedule")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddReminderDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun TaskItemCard(
    task: TaskEntity,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (task.completed) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = task.completed,
                onCheckedChange = onToggle,
                modifier = Modifier.testTag("task_checkbox_${task.id}")
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = task.title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None,
                        color = if (task.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                when (task.priority) {
                                    "HIGH" -> Color(0xFFEF4444).copy(alpha = 0.2f)
                                    "MEDIUM" -> Color(0xFF0284C7).copy(alpha = 0.2f)
                                    else -> Color(0xFF64748B).copy(alpha = 0.2f)
                                }
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = task.priority,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (task.priority) {
                                "HIGH" -> Color(0xFFEF4444)
                                "MEDIUM" -> Color(0xFF0284C7)
                                else -> Color(0xFF64748B)
                            }
                        )
                    }
                }

                if (task.description.isNotBlank()) {
                    Text(
                        text = task.description,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = "Category: ${task.category}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete Task",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ReminderItemCard(
    reminder: ReminderEntity,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }
    val timeFormatted = remember(reminder.triggerTimeEpochMs) { dateFormat.format(Date(reminder.triggerTimeEpochMs)) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (reminder.isFired) Icons.Default.CheckCircle else Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = if (reminder.isFired) Color.Gray else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = reminder.title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = reminder.message,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Trigger: $timeFormatted ${if (reminder.isFired) "(Triggered)" else "(Armed)"}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (reminder.isFired) Color.Gray else MaterialTheme.colorScheme.primary
                    )
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Cancel Reminder",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
