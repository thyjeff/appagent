package com.example.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.FocusPilotViewModel
import com.example.ui.FocusPilotViewModel.ScreenTimePeriod
import com.example.util.AppUsageItem

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScreenTimeScreen(viewModel: FocusPilotViewModel) {
    val context = LocalContext.current
    val usageSummary by viewModel.usageSummary.collectAsState()
    val selectedPeriod by viewModel.selectedScreenTimePeriod.collectAsState()

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.refreshUsageStats()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // --- 1. Period Selector: Day, Week, Month ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val periods = listOf(
                Triple(ScreenTimePeriod.DAY, "Day (Today)", Icons.Default.Today),
                Triple(ScreenTimePeriod.WEEK, "Week (7 Days)", Icons.Default.DateRange),
                Triple(ScreenTimePeriod.MONTH, "Month (30 Days)", Icons.Default.CalendarMonth)
            )

            periods.forEach { (period, label, icon) ->
                val isSelected = selectedPeriod == period
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else Color.Transparent
                        )
                        .clickable { viewModel.selectScreenTimePeriod(period) }
                        .padding(vertical = 8.dp, horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = when (period) {
                                ScreenTimePeriod.DAY -> "Day"
                                ScreenTimePeriod.WEEK -> "Week"
                                ScreenTimePeriod.MONTH -> "Month"
                            },
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // --- 2. Screen Time Hero Card ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
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
                        text = when (selectedPeriod) {
                            ScreenTimePeriod.DAY -> "TODAY'S SCREEN TIME"
                            ScreenTimePeriod.WEEK -> "PAST 7 DAYS SCREEN TIME"
                            ScreenTimePeriod.MONTH -> "PAST 30 DAYS SCREEN TIME"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                        letterSpacing = 1.sp
                    )
                    IconButton(
                        onClick = { viewModel.refreshState() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh Real-Time Stats",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = if (usageSummary.hasPermission) usageSummary.formattedTotalTime else "Permission Required",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    if (usageSummary.hasPermission && selectedPeriod != ScreenTimePeriod.DAY) {
                        Text(
                            text = "Avg: ${usageSummary.formattedDailyAverage}/day",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }

                Text(
                    text = if (usageSummary.hasPermission) {
                        "${usageSummary.appCount} applications recorded in Android OS • Productive: ${usageSummary.productiveMinutes}m • Distractions: ${usageSummary.distractionMinutes}m"
                    } else {
                        "Usage stats access must be granted in Android settings to compute real-time screen time"
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                )

                // Productivity ratio bar
                if (usageSummary.hasPermission && (usageSummary.productiveMinutes + usageSummary.distractionMinutes) > 0) {
                    val totalTracked = (usageSummary.productiveMinutes + usageSummary.distractionMinutes).toFloat()
                    val productiveFraction = (usageSummary.productiveMinutes / totalTracked).coerceIn(0f, 1f)

                    Spacer(modifier = Modifier.height(2.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Study & Focus: ${(productiveFraction * 100).toInt()}%",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Entertainment & Social: ${((1f - productiveFraction) * 100).toInt()}%",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        LinearProgressIndicator(
                            progress = { productiveFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = Color(0xFF10B981), // Emerald for productive
                            trackColor = Color(0xFFEF4444) // Red for distraction
                        )
                    }
                }

                if (!usageSummary.hasPermission) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = {
                            try {
                                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                            } catch (ignored: Exception) {}
                        },
                        modifier = Modifier.testTag("grant_usage_hero_button")
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Grant Usage Access in Settings")
                    }
                }
            }
        }

        // --- 3. Category Breakdown Chips ---
        if (usageSummary.categoryDistribution.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                usageSummary.categoryDistribution.entries.sortedByDescending { it.value }.forEach { (category, minutes) ->
                    val catColor = getCategoryColor(category)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = catColor.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, catColor.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(catColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "$category: ${minutes}m",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // --- 4. Foreground App Activity Header ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Real-Time Foreground Activity",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Text(
                text = "${usageSummary.topApps.size} apps",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (usageSummary.topApps.isEmpty()) {
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
                        Icons.Default.Analytics,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.outlineVariant
                    )
                    Text(
                        text = if (usageSummary.hasPermission) "No foreground activity recorded for this period"
                        else "Grant Usage Access to view application stats",
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            val maxDuration = usageSummary.topApps.maxOfOrNull { it.totalTimeInForegroundMs }?.coerceAtLeast(1L) ?: 1L

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(usageSummary.topApps, key = { it.packageName }) { appItem ->
                    AppUsageCard(item = appItem, maxDurationMs = maxDuration)
                }
            }
        }
    }
}

@Composable
private fun AppUsageCard(item: AppUsageItem, maxDurationMs: Long) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
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
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(getCategoryColor(item.category).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = item.appName.take(1).uppercase(),
                            fontWeight = FontWeight.Bold,
                            color = getCategoryColor(item.category),
                            fontSize = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = item.appName,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${item.category} • ${item.packageName}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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

            Spacer(modifier = Modifier.height(8.dp))

            val progress = (item.totalTimeInForegroundMs.toFloat() / maxDurationMs.toFloat()).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = getCategoryColor(item.category),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

private fun getCategoryColor(category: String): Color {
    return when (category) {
        "Productivity" -> Color(0xFF10B981) // emerald
        "Communication" -> Color(0xFF0284C7) // sky
        "Browsing" -> Color(0xFF8B5CF6) // purple
        "Social" -> Color(0xFFF59E0B) // amber
        "Gaming", "Entertainment" -> Color(0xFFEF4444) // red
        else -> Color(0xFF64748B) // slate
    }
}
