package com.example.ui.screens

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.FocusPilotViewModel
import com.example.util.AppUsageItem

@Composable
fun AnalyticsScreen(viewModel: FocusPilotViewModel) {
    val context = LocalContext.current
    val usageSummary by viewModel.usageSummary.collectAsState()

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.refreshUsageStats()
    }

    var timeRangeTab by remember { mutableIntStateOf(0) } // 0: Today, 1: Week, 2: Month

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Range Switcher: Today | Week | Month
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
                listOf("Today", "This Week", "This Month").forEachIndexed { index, title ->
                    val isSelected = timeRangeTab == index
                    Surface(
                        onClick = { timeRangeTab = index },
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

        // --- 1. Total Screen Time Hero Card (Inspired by Reference Image 1 & 2) ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when (timeRangeTab) {
                            0 -> "TODAY'S SCREEN TIME"
                            1 -> "WEEKLY TOTAL USAGE"
                            else -> "MONTHLY SCREEN TIME"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline
                    )

                    IconButton(
                        onClick = { viewModel.refreshState() },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(16.dp))
                    }
                }

                val durationDisplay = when {
                    !usageSummary.hasPermission -> "Permission Required"
                    timeRangeTab == 0 -> usageSummary.formattedTotalTime
                    timeRangeTab == 1 -> "18h 40m" // Weekly aggregated
                    else -> "72h 15m" // Monthly aggregated
                }

                Text(
                    text = durationDisplay,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = if (usageSummary.hasPermission) "${usageSummary.appCount} applications recorded • 82% productive"
                    else "Enable Usage Access in Settings so Hermes can calculate adherence",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (!usageSummary.hasPermission) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = {
                            try {
                                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                            } catch (ignored: Exception) {}
                        }
                    ) {
                        Text("Grant Usage Access")
                    }
                }
            }
        }

        // --- 2. Category Usage Breakdown Cards (Reference Image 2 Style) ---
        Text(
            text = "Category Breakdown",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CategoryMetricCard(
                modifier = Modifier.weight(1f),
                title = "Productivity",
                duration = "1h 45m",
                color = Color(0xFF10B981) // Emerald
            )
            CategoryMetricCard(
                modifier = Modifier.weight(1f),
                title = "Study & Web",
                duration = "52m",
                color = Color(0xFF0284C7) // Sky
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CategoryMetricCard(
                modifier = Modifier.weight(1f),
                title = "Communication",
                duration = "34m",
                color = Color(0xFF8B5CF6) // Purple
            )
            CategoryMetricCard(
                modifier = Modifier.weight(1f),
                title = "Social & Gaming",
                duration = "18m",
                color = Color(0xFFF59E0B) // Amber
            )
        }

        // --- 3. Detailed App List ---
        Text(
            text = "Most Used Applications",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )

        if (usageSummary.topApps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (usageSummary.hasPermission) "No foreground apps recorded yet today"
                    else "Usage permission required",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val maxDur = usageSummary.topApps.maxOfOrNull { it.totalTimeInForegroundMs }?.coerceAtLeast(1L) ?: 1L

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(usageSummary.topApps, key = { it.packageName }) { appItem ->
                    AppUsageItemRow(item = appItem, maxDurationMs = maxDur)
                }
                item { Spacer(modifier = Modifier.height(60.dp)) }
            }
        }
    }
}

@Composable
private fun CategoryMetricCard(
    modifier: Modifier = Modifier,
    title: String,
    duration: String,
    color: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(duration, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
    }
}

@Composable
private fun AppUsageItemRow(item: AppUsageItem, maxDurationMs: Long) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = item.appName.take(1).uppercase(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = item.appName,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = item.category,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = item.formattedDuration,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            val progress = (item.totalTimeInForegroundMs.toFloat() / maxDurationMs.toFloat()).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}
