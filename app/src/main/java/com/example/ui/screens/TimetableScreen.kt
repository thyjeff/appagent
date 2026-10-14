package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.TimetableBlockEntity
import com.example.ui.FocusPilotViewModel
import com.example.ui.theme.CrimsonKillSwitch
import com.example.ui.theme.EmeraldOnline
import kotlinx.coroutines.delay
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(viewModel: FocusPilotViewModel) {
    val context = LocalContext.current
    val blocks by viewModel.timetableBlocks.collectAsState()
    val usageSummary by viewModel.usageSummary.collectAsState()

    var currentMinuteOfDay by remember {
        val now = Calendar.getInstance()
        mutableIntStateOf(now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE))
    }

    LaunchedEffect(Unit) {
        while (true) {
            val now = Calendar.getInstance()
            currentMinuteOfDay = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
            delay(15000) // Update every 15 seconds
        }
    }

    val todayDayOfWeek = remember {
        val cal = Calendar.getInstance()
        when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> 1
        }
    }

    var selectedDayOfWeek by remember { mutableIntStateOf(todayDayOfWeek) }

    // Active block right now (matching today's day of week)
    val activeBlock = remember(blocks, currentMinuteOfDay, todayDayOfWeek) {
        blocks.firstOrNull { it.dayOfWeek == todayDayOfWeek && currentMinuteOfDay in it.startMinuteOfDay until it.endMinuteOfDay }
    }

    var editingBlock by remember { mutableStateOf<TimetableBlockEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // --- 1. Current Active Schedule & Adherence Hero ---
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("active_block_hero_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(EmeraldOnline)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "NOW • ${formatMinute(currentMinuteOfDay)}",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        // Test alarm button
                        OutlinedButton(
                            onClick = {
                                viewModel.triggerLoudAlarmNow(
                                    title = activeBlock?.title ?: "Wake Up / Follow Timetable",
                                    message = "Hermes alarm: time for your scheduled block."
                                )
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                            modifier = Modifier.testTag("test_loud_alarm_button")
                        ) {
                            Icon(Icons.Default.Alarm, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Test Loud Alarm", fontSize = 11.sp)
                        }
                    }

                    if (activeBlock != null) {
                        Text(
                            text = activeBlock.title,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        Text(
                            text = "${formatMinute(activeBlock.startMinuteOfDay)} - ${formatMinute(activeBlock.endMinuteOfDay)} • ${activeBlock.subjectOrProject} (${activeBlock.category})",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )

                        if (activeBlock.notes.isNotBlank()) {
                            Text(
                                text = "Notes: ${activeBlock.notes}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                            )
                        }

                        // Adherence check against UsageStats
                        val topAppToday = usageSummary.topApps.firstOrNull()
                        val isDistracted = topAppToday != null &&
                                (topAppToday.category == "Social" || topAppToday.category == "Gaming") &&
                                activeBlock.category in listOf("Subject", "Project")

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isDistracted) CrimsonKillSwitch.copy(alpha = 0.2f)
                                    else EmeraldOnline.copy(alpha = 0.2f)
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isDistracted) Icons.Default.Warning else Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (isDistracted) CrimsonKillSwitch else EmeraldOnline,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isDistracted) "Adherence Warning: High ${topAppToday?.category} phone usage during focus block!"
                                    else "Adherence Good: Follow planned timetable block",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isDistracted) CrimsonKillSwitch else EmeraldOnline
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "Free / Unscheduled Window",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Hermes or you can schedule an active study, project, or rest block anytime.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Day selector strip
            val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                dayNames.forEachIndexed { idx, name ->
                    val d = idx + 1
                    val isSel = d == selectedDayOfWeek
                    Surface(
                        onClick = { selectedDayOfWeek = d },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = name,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            val dayBlocks = blocks.filter { it.dayOfWeek == selectedDayOfWeek }

            // Quick Actions: Seed College Timetable
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${dayNames[selectedDayOfWeek - 1]}'s Timeline (${dayBlocks.size} blocks)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )

                TextButton(
                    onClick = {
                        viewModel.seedStudent24hTimetable()
                        Toast.makeText(context, "College engineering schedule seeded", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.testTag("seed_timetable_button")
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Auto-Fill 24h Schedule", fontSize = 12.sp)
                }
            }

            // --- 2. 24-Hour Chronological Blocks List ---
            if (dayBlocks.isEmpty()) {
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
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.outlineVariant
                        )
                        Text("No schedule blocks for ${dayNames[selectedDayOfWeek - 1]}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { viewModel.seedStudent24hTimetable() }) {
                            Text("Load Student Engineering Timetable")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(dayBlocks, key = { it.id }) { block ->
                        val isToday = selectedDayOfWeek == todayDayOfWeek
                        val isNow = isToday && currentMinuteOfDay in block.startMinuteOfDay until block.endMinuteOfDay

                        TimetableBlockCard(
                            block = block,
                            isNow = isNow,
                            onEdit = { editingBlock = block },
                            onDelete = { viewModel.deleteTimetableBlock(block.id) }
                        )
                    }
                }
            }
        }

        // Add Floating Action Button
        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .testTag("fab_add_timetable_block"),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Schedule Block")
        }
    }

    // Add / Edit Dialog
    if (showAddDialog || editingBlock != null) {
        val initial = editingBlock
        var title by remember { mutableStateOf(initial?.title ?: "") }
        var subjectOrProject by remember { mutableStateOf(initial?.subjectOrProject ?: "") }
        var category by remember { mutableStateOf(initial?.category ?: "Subject") }
        var startMinStr by remember { mutableStateOf((initial?.startMinuteOfDay ?: 540).toString()) } // default 09:00
        var endMinStr by remember { mutableStateOf((initial?.endMinuteOfDay ?: 630).toString()) }   // default 10:30
        var notes by remember { mutableStateOf(initial?.notes ?: "") }

        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
                editingBlock = null
            },
            title = { Text(if (initial != null) "Edit Timetable Block" else "Add Schedule Block") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Block Title (e.g. Operating Systems Lab)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = subjectOrProject,
                        onValueChange = { subjectOrProject = it },
                        label = { Text("Subject or Project (e.g. CS302 / Hermes)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = startMinStr,
                            onValueChange = { startMinStr = it },
                            label = { Text("Start Minute (0-1439)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = endMinStr,
                            onValueChange = { endMinStr = it },
                            label = { Text("End Minute") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Category (Subject, Project, Health, Routine, Sleep)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes & Objectives") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            val startMin = startMinStr.toIntOrNull()?.coerceIn(0, 1439) ?: 540
                            val endMin = endMinStr.toIntOrNull()?.coerceIn(startMin, 1440) ?: (startMin + 60)
                            val entity = TimetableBlockEntity(
                                id = initial?.id ?: 0,
                                dayOfWeek = initial?.dayOfWeek ?: selectedDayOfWeek,
                                title = title.trim(),
                                subjectOrProject = subjectOrProject.trim(),
                                category = category.trim().ifEmpty { "Subject" },
                                startMinuteOfDay = startMin,
                                endMinuteOfDay = endMin,
                                notes = notes.trim(),
                                isAgentGenerated = false
                            )
                            viewModel.addOrUpdateTimetableBlock(entity)
                            showAddDialog = false
                            editingBlock = null
                            Toast.makeText(context, "Schedule block saved", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Save Block")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddDialog = false
                        editingBlock = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun TimetableBlockCard(
    block: TimetableBlockEntity,
    isNow: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val categoryColor = getCategoryColor(block.category)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
            .then(
                if (isNow) Modifier.border(2.dp, categoryColor, RoundedCornerShape(12.dp))
                else Modifier
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isNow) categoryColor.copy(alpha = 0.12f)
            else MaterialTheme.colorScheme.surface
        )
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
                // Time pill indicator
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(categoryColor.copy(alpha = 0.2f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = formatMinute(block.startMinuteOfDay),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = categoryColor
                    )
                    Text(
                        text = "to",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatMinute(block.endMinuteOfDay),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = categoryColor
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = block.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isNow) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(EmeraldOnline)
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text("NOW", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                            }
                        }
                    }

                    Text(
                        text = "${block.category} • ${block.subjectOrProject}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (block.notes.isNotBlank()) {
                        Text(
                            text = block.notes,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Row {
                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Block", modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Block", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

private fun formatMinute(min: Int): String {
    val h = (min / 60) % 24
    val m = min % 60
    return String.format(java.util.Locale.US, "%02d:%02d", h, m)
}

private fun getCategoryColor(category: String): Color {
    return when (category.lowercase()) {
        "subject" -> Color(0xFF6366F1) // Indigo
        "project" -> Color(0xFF0284C7) // Sky
        "health" -> Color(0xFF10B981)  // Emerald
        "routine" -> Color(0xFFF59E0B) // Amber
        "sleep" -> Color(0xFF64748B)   // Slate
        "break" -> Color(0xFF8B5CF6)   // Purple
        else -> Color(0xFF0284C7)
    }
}
