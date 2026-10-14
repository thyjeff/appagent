package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.FocusPilotViewModel
import com.example.ui.theme.EmeraldOnline
import kotlinx.coroutines.delay
import java.util.Calendar

@Composable
fun HomeScreen(
    viewModel: FocusPilotViewModel,
    onNavigateToSchedule: () -> Unit,
    onNavigateToConnectAgent: () -> Unit = {}
) {
    val context = LocalContext.current
    val tasks by viewModel.tasks.collectAsState()
    val blocks by viewModel.timetableBlocks.collectAsState()
    val usageSummary by viewModel.usageSummary.collectAsState()
    val appBannerText by viewModel.appBannerText.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()
    val customQuote by viewModel.customQuote.collectAsState()
    val isServerRunning by viewModel.isServerRunning.collectAsState()

    var currentMinuteOfDay by remember {
        val now = Calendar.getInstance()
        mutableIntStateOf(now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE))
    }

    LaunchedEffect(Unit) {
        while (true) {
            val now = Calendar.getInstance()
            currentMinuteOfDay = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
            delay(15000)
        }
    }

    val activeBlock = remember(blocks, currentMinuteOfDay) {
        blocks.firstOrNull { currentMinuteOfDay in it.startMinuteOfDay until it.endMinuteOfDay }
    }

    val upcomingBlocks = remember(blocks, currentMinuteOfDay) {
        blocks.filter { it.startMinuteOfDay > currentMinuteOfDay }.take(3)
    }

    val completedTasksCount = remember(tasks) { tasks.count { it.completed } }
    val totalTasksCount = remember(tasks) { tasks.size }
    val pendingTasks = remember(tasks) { tasks.filter { !it.completed } }

    var showAddTaskDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // --- 1. Top Agent Status Bar & Hero Headline (Inspired by Reference Images) ---
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pill Agent Status Badge (Clickable to open pairing hub)
                Surface(
                    onClick = onNavigateToConnectAgent,
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.testTag("home_agent_status_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isServerRunning) EmeraldOnline else Color(0xFF94A3B8))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isServerRunning) "Hermes Connected" else "Connect Agent",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Time Indicator Pill
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = formatMinute(currentMinuteOfDay),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Big Bold Hero Headline (Reference Image 1 style: "Your car can arrive in 16 min" / Image 2 style: "Zero tasks. You're all set.")
            val headlineText = when {
                activeBlock != null -> "In session:\n${activeBlock.title}"
                pendingTasks.isNotEmpty() -> "${pendingTasks.size} tasks left.\nStay on track."
                totalTasksCount > 0 && pendingTasks.isEmpty() -> "Zero tasks.\nYou're all set."
                else -> "Plan your day.\nReady for college."
            }

            Text(
                text = headlineText,
                fontSize = 28.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // --- Agent Focus Banner & Active Profile (Customizable by Agent) ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = appBannerText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = customQuote,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = activeProfile,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // --- 2. Action Shortcut Pills (Reference Image 1: "+ Add home", "+ Add work", "+") ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ActionPill(
                icon = Icons.Default.Add,
                label = "Add task",
                onClick = { showAddTaskDialog = true },
                testTag = "home_add_task_pill"
            )
            ActionPill(
                icon = Icons.Default.Alarm,
                label = "Test alarm",
                onClick = {
                    viewModel.triggerLoudAlarmNow(
                        title = activeBlock?.title ?: "Wake Up / Follow Timetable",
                        message = "Hermes alarm check: verify full ringing screen."
                    )
                },
                testTag = "home_test_alarm_pill"
            )
            ActionPill(
                icon = Icons.Default.AutoAwesome,
                label = "Auto-fill timetable",
                onClick = {
                    viewModel.seedStudent24hTimetable()
                    Toast.makeText(context, "Student 24h schedule populated", Toast.LENGTH_SHORT).show()
                },
                testTag = "home_autofill_pill"
            )
            ActionPill(
                icon = Icons.Default.Refresh,
                label = "Refresh sync",
                onClick = {
                    viewModel.refreshState()
                    Toast.makeText(context, "Sync refreshed", Toast.LENGTH_SHORT).show()
                },
                testTag = "home_refresh_pill"
            )
        }

        // --- 3. Telemetry Pulse (Reference Image 2 Style 2x2 Grid with Progress Indicators) ---
        Text(
            text = "Daily Pulse",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onBackground
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Card 1: Tasks Completion
            MetricProgressCard(
                modifier = Modifier.weight(1f),
                title = "Tasks Done",
                value = if (totalTasksCount > 0) "$completedTasksCount / $totalTasksCount" else "0 / 0",
                subtext = if (totalTasksCount > 0) "${(completedTasksCount * 100 / totalTasksCount)}% completed" else "None added",
                progress = if (totalTasksCount > 0) completedTasksCount.toFloat() / totalTasksCount.toFloat() else 0f,
                accentColor = Color(0xFF6366F1) // Indigo
            )

            // Card 2: Screen Time Today
            val screenTimeDuration = if (usageSummary.hasPermission) usageSummary.formattedTotalTime else "Needs access"
            val screenProgress = if (usageSummary.hasPermission) {
                (usageSummary.totalScreenTimeMs.toFloat() / (6 * 3600 * 1000f)).coerceIn(0f, 1f)
            } else 0f

            MetricProgressCard(
                modifier = Modifier.weight(1f),
                title = "Screen Time",
                value = screenTimeDuration,
                subtext = if (usageSummary.hasPermission) "${usageSummary.appCount} apps used" else "Grant in settings",
                progress = screenProgress,
                accentColor = Color(0xFF0284C7) // Sky
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Card 3: Active Focus Block with Remaining Time displayed directly in the tab
            val (sessionValue, sessionSubtext, sessionProgress) = remember(activeBlock, currentMinuteOfDay, upcomingBlocks) {
                if (activeBlock != null) {
                    val totalDuration = (activeBlock.endMinuteOfDay - activeBlock.startMinuteOfDay).coerceAtLeast(1)
                    val elapsed = (currentMinuteOfDay - activeBlock.startMinuteOfDay).coerceIn(0, totalDuration)
                    val remainingMins = (activeBlock.endMinuteOfDay - currentMinuteOfDay).coerceAtLeast(0)
                    val prog = elapsed.toFloat() / totalDuration.toFloat()
                    val timeRemainingText = if (remainingMins >= 60) {
                        "${remainingMins / 60}h ${remainingMins % 60}m left"
                    } else {
                        "${remainingMins}m remaining"
                    }
                    Triple(
                        timeRemainingText,
                        "${activeBlock.title} • Ends ${formatMinute(activeBlock.endMinuteOfDay)}",
                        prog
                    )
                } else {
                    val nextUpcoming = upcomingBlocks.firstOrNull()
                    if (nextUpcoming != null) {
                        val minsToStart = (nextUpcoming.startMinuteOfDay - currentMinuteOfDay).coerceAtLeast(0)
                        val startStr = if (minsToStart >= 60) "${minsToStart / 60}h ${minsToStart % 60}m" else "${minsToStart}m"
                        Triple("Next in $startStr", nextUpcoming.title, 0f)
                    } else {
                        Triple("Free Time", "Schedule open", 0f)
                    }
                }
            }

            MetricProgressCard(
                modifier = Modifier.weight(1f),
                title = "Current Session",
                value = sessionValue,
                subtext = sessionSubtext,
                progress = sessionProgress,
                accentColor = Color(0xFF10B981) // Emerald
            )

            // Card 4: Distraction Radar
            val topApp = usageSummary.topApps.firstOrNull()
            val isHighDistraction = topApp?.category in listOf("Social", "Gaming", "Entertainment")
            MetricProgressCard(
                modifier = Modifier.weight(1f),
                title = "Adherence",
                value = if (isHighDistraction) "Watch out" else "Focused",
                subtext = if (isHighDistraction) "High ${topApp?.category}" else "Healthy usage",
                progress = if (isHighDistraction) 0.35f else 0.95f,
                accentColor = if (isHighDistraction) Color(0xFFEF4444) else Color(0xFF10B981)
            )
        }

        // --- 4. Interactive Tasks Checklist ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Today's Checklist",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onBackground
            )

            TextButton(
                onClick = { showAddTaskDialog = true },
                modifier = Modifier.testTag("home_add_task_text_btn")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (tasks.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text("No tasks added yet", fontWeight = FontWeight.Bold)
                    Text(
                        "Tap '+ Add task' or let Hermes push your daily college assignments.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                tasks.forEach { task ->
                    InteractiveTaskCard(
                        title = task.title,
                        category = task.category,
                        priority = task.priority,
                        completed = task.completed,
                        onToggle = { viewModel.toggleTask(task.id, it) },
                        onDelete = { viewModel.deleteTask(task.id) }
                    )
                }
            }
        }

        // --- 5. Upcoming Schedule Peek ---
        if (upcomingBlocks.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Upcoming on Timetable",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )

                TextButton(onClick = onNavigateToSchedule) {
                    Text("View 24h", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp))
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                upcomingBlocks.forEach { block ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToSchedule() },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = formatMinute(block.startMinuteOfDay),
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = block.title,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "${block.category} • ${block.subjectOrProject}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(60.dp)) // Padding for bottom floating pill bar
    }

    // Add Task Dialog
    if (showAddTaskDialog) {
        var title by remember { mutableStateOf("") }
        var category by remember { mutableStateOf("College") }
        var priority by remember { mutableStateOf("HIGH") }

        AlertDialog(
            onDismissRequest = { showAddTaskDialog = false },
            title = { Text("New Daily Task") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Task Title (e.g. Finish Math Problem Set)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Category / Subject") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            viewModel.addTask(title, "", priority, category)
                            showAddTaskDialog = false
                            Toast.makeText(context, "Task added", Toast.LENGTH_SHORT).show()
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
}

@Composable
private fun ActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun MetricProgressCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtext: String,
    progress: Float,
    accentColor: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )

            Text(
                text = value,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = accentColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subtext,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun InteractiveTaskCard(
    title: String,
    category: String,
    priority: String,
    completed: Boolean,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (completed) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = completed,
                onCheckedChange = onToggle,
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary
                )
            )

            Spacer(modifier = Modifier.width(6.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (completed) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$category • $priority priority",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete Task",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private fun formatMinute(min: Int): String {
    val h = (min / 60) % 24
    val m = min % 60
    return String.format(java.util.Locale.US, "%02d:%02d", h, m)
}
