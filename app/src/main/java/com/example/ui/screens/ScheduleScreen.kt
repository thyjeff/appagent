package com.example.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.TimetableBlockEntity
import com.example.ui.FocusPilotViewModel
import com.example.ui.theme.EmeraldOnline
import kotlinx.coroutines.delay
import java.util.Calendar

fun getTodayDayOfWeek(): Int {
    val cal = Calendar.getInstance()
    return when (cal.get(Calendar.DAY_OF_WEEK)) {
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

@Composable
fun ScheduleScreen(viewModel: FocusPilotViewModel) {
    val context = LocalContext.current
    val blocks by viewModel.timetableBlocks.collectAsState()

    var selectedViewMode by remember { mutableIntStateOf(0) } // 0: Day (24h), 1: Week, 2: Month
    var selectedDayOfWeek by remember { mutableIntStateOf(getTodayDayOfWeek()) }

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

    var editingBlock by remember { mutableStateOf<TimetableBlockEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Segmented View Mode Tabs (Day | Week | Month)
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val tabs = listOf("Day (24h)", "Week", "Month")
                    tabs.forEachIndexed { index, title ->
                        val isSelected = selectedViewMode == index
                        Surface(
                            onClick = { selectedViewMode = index },
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                            shadowElevation = if (isSelected) 1.dp else 0.dp,
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = title,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // View Content Switcher
            when (selectedViewMode) {
                0 -> DayTimelineView(
                    blocks = blocks,
                    selectedDayOfWeek = selectedDayOfWeek,
                    onSelectDay = { selectedDayOfWeek = it },
                    currentMinuteOfDay = currentMinuteOfDay,
                    onEditBlock = { editingBlock = it },
                    onDeleteBlock = { viewModel.deleteTimetableBlock(it) },
                    onAutoFill = {
                        viewModel.seedStudent24hTimetable()
                        Toast.makeText(context, "7-day distinct timetable seeded", Toast.LENGTH_SHORT).show()
                    }
                )
                1 -> WeekScheduleView(
                    blocks = blocks,
                    selectedDayOfWeek = selectedDayOfWeek,
                    onSelectDay = { selectedDayOfWeek = it },
                    onEditBlock = { editingBlock = it },
                    onDeleteBlock = { viewModel.deleteTimetableBlock(it) }
                )
                2 -> MonthCalendarView(
                    blocks = blocks,
                    onEditBlock = { editingBlock = it }
                )
            }
        }

        // Add Floating Action Button
        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 70.dp, end = 24.dp)
                .testTag("schedule_add_block_fab"),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Schedule Block")
        }
    }

    // Add / Edit Block Dialog
    if (showAddDialog || editingBlock != null) {
        val initial = editingBlock
        val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        var dayOfWeek by remember { mutableIntStateOf(initial?.dayOfWeek ?: selectedDayOfWeek) }
        var title by remember { mutableStateOf(initial?.title ?: "") }
        var subjectOrProject by remember { mutableStateOf(initial?.subjectOrProject ?: "") }
        var category by remember { mutableStateOf(initial?.category ?: "Subject") }
        var startMinStr by remember { mutableStateOf((initial?.startMinuteOfDay ?: 540).toString()) }
        var endMinStr by remember { mutableStateOf((initial?.endMinuteOfDay ?: 630).toString()) }
        var notes by remember { mutableStateOf(initial?.notes ?: "") }

        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
                editingBlock = null
            },
            title = { Text(if (initial != null) "Edit Schedule Block" else "Add Schedule Block") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Day of Week:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        dayNames.forEachIndexed { idx, name ->
                            val dVal = idx + 1
                            val isSel = dayOfWeek == dVal
                            Surface(
                                onClick = { dayOfWeek = dVal },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = name.take(1),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title (e.g. Operating Systems Lab)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = subjectOrProject,
                        onValueChange = { subjectOrProject = it },
                        label = { Text("Subject or Project (e.g. CS302 / Hermes)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = startMinStr,
                            onValueChange = { startMinStr = it },
                            label = { Text("Start Min (0-1439)") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = endMinStr,
                            onValueChange = { endMinStr = it },
                            label = { Text("End Min") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Category (Subject, Project, Health, Routine, Sleep)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes / Target") },
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
                                dayOfWeek = dayOfWeek,
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
                            Toast.makeText(context, "Schedule block saved for ${dayNames[dayOfWeek - 1]}", Toast.LENGTH_SHORT).show()
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

// --- 1. Day 24-Hour Timeline View ---
@Composable
private fun DayTimelineView(
    blocks: List<TimetableBlockEntity>,
    selectedDayOfWeek: Int,
    onSelectDay: (Int) -> Unit,
    currentMinuteOfDay: Int,
    onEditBlock: (TimetableBlockEntity) -> Unit,
    onDeleteBlock: (Int) -> Unit,
    onAutoFill: () -> Unit
) {
    val dayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    val shortNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val dayBlocks = blocks.filter { it.dayOfWeek == selectedDayOfWeek }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Day selector strip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            shortNames.forEachIndexed { idx, name ->
                val d = idx + 1
                val isSel = d == selectedDayOfWeek
                Surface(
                    onClick = { onSelectDay(d) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    border = if (!isSel) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = name,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${dayNames[selectedDayOfWeek - 1]}'s 24h Flow (${dayBlocks.size} blocks)",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            TextButton(onClick = onAutoFill) {
                Text("Seed 7-Day Timetable", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

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
                    Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.outlineVariant)
                    Text("No timetable blocks for ${dayNames[selectedDayOfWeek - 1]}", fontWeight = FontWeight.Bold)
                    Button(onClick = onAutoFill) {
                        Text("Load College Student Schedule")
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
                    val isToday = selectedDayOfWeek == getTodayDayOfWeek()
                    val isNow = isToday && currentMinuteOfDay in block.startMinuteOfDay until block.endMinuteOfDay
                    ScheduleBlockItem(
                        block = block,
                        isNow = isNow,
                        onEdit = { onEditBlock(block) },
                        onDelete = { onDeleteBlock(block.id) }
                    )
                }
                item { Spacer(modifier = Modifier.height(60.dp)) }
            }
        }
    }
}

// --- 2. Week Schedule View ---
@Composable
private fun WeekScheduleView(
    blocks: List<TimetableBlockEntity>,
    selectedDayOfWeek: Int,
    onSelectDay: (Int) -> Unit,
    onEditBlock: (TimetableBlockEntity) -> Unit,
    onDeleteBlock: (Int) -> Unit
) {
    val daysOfWeek = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val fullNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    val dayBlocks = blocks.filter { it.dayOfWeek == selectedDayOfWeek }

    val totalMins = dayBlocks.sumOf { maxOf(0, it.endMinuteOfDay - it.startMinuteOfDay) }
    val totalHours = totalMins / 60
    val remMins = totalMins % 60

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Weekday Strip Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            daysOfWeek.forEachIndexed { index, day ->
                val dVal = index + 1
                val isSelected = selectedDayOfWeek == dVal
                Surface(
                    onClick = { onSelectDay(dVal) },
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    border = if (!isSelected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = day,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Weekly Summary Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "${fullNames[selectedDayOfWeek - 1]}'s Academic Load",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = "${dayBlocks.size} scheduled blocks • ${totalHours}h ${remMins}m planned study & lab work",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (dayBlocks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No blocks scheduled for ${fullNames[selectedDayOfWeek - 1]}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // Day's block list
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(dayBlocks, key = { it.id }) { block ->
                    ScheduleBlockItem(
                        block = block,
                        isNow = false,
                        onEdit = { onEditBlock(block) },
                        onDelete = { onDeleteBlock(block.id) }
                    )
                }
                item { Spacer(modifier = Modifier.height(60.dp)) }
            }
        }
    }
}

// --- 3. Month Calendar View ---
@Composable
private fun MonthCalendarView(
    blocks: List<TimetableBlockEntity>,
    onEditBlock: (TimetableBlockEntity) -> Unit
) {
    var selectedDate by remember { mutableIntStateOf(12) }
    // October 2026 starts on Thursday (index 4)
    val dayOfWeek = ((selectedDate - 1 + 3) % 7) + 1
    val dayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    val dateBlocks = blocks.filter { it.dayOfWeek == dayOfWeek }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "October 2026",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Row {
                IconButton(onClick = {}) { Icon(Icons.Default.ChevronLeft, contentDescription = "Prev") }
                IconButton(onClick = {}) { Icon(Icons.Default.ChevronRight, contentDescription = "Next") }
            }
        }

        // Days of week header
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach {
                Text(it, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
            }
        }

        // Month grid (October 31 days)
        val days = (1..31).toList()
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(days) { day ->
                val isSelected = selectedDate == day
                val dDayOfWeek = ((day - 1 + 3) % 7) + 1
                val hasClasses = blocks.any { it.dayOfWeek == dDayOfWeek }
                Surface(
                    onClick = { selectedDate = day },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    border = if (hasClasses && !isSelected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) else null,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = day.toString(),
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Selected Date Agenda
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Agenda for Oct $selectedDate, 2026 (${dayNames[dayOfWeek - 1]})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )

                if (dateBlocks.isEmpty()) {
                    Text(
                        text = "No scheduled blocks for ${dayNames[dayOfWeek - 1]}.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(dateBlocks, key = { it.id }) { block ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                    .clickable { onEditBlock(block) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = block.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${formatMinute(block.startMinuteOfDay)} - ${formatMinute(block.endMinuteOfDay)} • ${block.subjectOrProject}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(60.dp))
    }
}

@Composable
private fun ScheduleBlockItem(
    block: TimetableBlockEntity,
    isNow: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val categoryColor = when (block.category.lowercase()) {
        "subject" -> Color(0xFF6366F1) // Indigo
        "project" -> Color(0xFF0284C7) // Sky
        "health" -> Color(0xFF10B981)  // Emerald
        "routine" -> Color(0xFFF59E0B) // Amber
        "sleep" -> Color(0xFF64748B)   // Slate
        "break" -> Color(0xFF8B5CF6)   // Purple
        else -> Color(0xFF0284C7)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
            .then(
                if (isNow) Modifier.border(2.dp, categoryColor, RoundedCornerShape(14.dp))
                else Modifier
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isNow) categoryColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                // Time Badge
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
                    Text("to", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Edit, contentDescription = "Edit Block", modifier = Modifier.size(16.dp))
            }
        }
    }
}

private fun formatMinute(min: Int): String {
    val h = (min / 60) % 24
    val m = min % 60
    return String.format(java.util.Locale.US, "%02d:%02d", h, m)
}
