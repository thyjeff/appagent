package com.example.util

import android.app.AppOpsManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
import android.provider.Settings
import java.util.Calendar

data class AppUsageItem(
    val packageName: String,
    val appName: String,
    val totalTimeInForegroundMs: Long,
    val lastTimeUsedEpochMs: Long,
    val formattedDuration: String,
    val category: String
)

enum class UsageTimeframe {
    DAY,
    WEEK,
    MONTH
}

data class UsageSummary(
    val hasPermission: Boolean,
    val timeframe: UsageTimeframe = UsageTimeframe.DAY,
    val startTimeEpochMs: Long,
    val endTimeEpochMs: Long,
    val totalScreenTimeMs: Long,
    val formattedTotalTime: String,
    val dailyAverageMs: Long = 0L,
    val formattedDailyAverage: String = "0m",
    val topApps: List<AppUsageItem>,
    val appCount: Int,
    val productiveMinutes: Long = 0L,
    val distractionMinutes: Long = 0L,
    val categoryDistribution: Map<String, Long> = emptyMap(),
    val dataLimitations: String = "Live Android OS UsageStatsManager data for student screen time tracking."
)

class UsageStatsHelper(private val context: Context) {

    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
    private val packageManager = context.packageManager

    fun hasUsagePermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun getUsageAccessSettingsIntent(): Intent {
        return Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
    }

    fun getTodayUsageSummary(): UsageSummary {
        return getTimeframeUsageSummary(UsageTimeframe.DAY)
    }

    fun getTimeframeUsageSummary(timeframe: UsageTimeframe): UsageSummary {
        val hasPerm = hasUsagePermission()
        val now = System.currentTimeMillis()

        val (startTime, divisorDays, intervalCode) = when (timeframe) {
            UsageTimeframe.DAY -> {
                val calendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                Triple(calendar.timeInMillis, 1, UsageStatsManager.INTERVAL_DAILY)
            }
            UsageTimeframe.WEEK -> {
                val weekAgo = now - (7L * 24 * 60 * 60 * 1000)
                Triple(weekAgo, 7, UsageStatsManager.INTERVAL_WEEKLY)
            }
            UsageTimeframe.MONTH -> {
                val monthAgo = now - (30L * 24 * 60 * 60 * 1000)
                Triple(monthAgo, 30, UsageStatsManager.INTERVAL_MONTHLY)
            }
        }

        if (!hasPerm || usageStatsManager == null) {
            return UsageSummary(
                hasPermission = false,
                timeframe = timeframe,
                startTimeEpochMs = startTime,
                endTimeEpochMs = now,
                totalScreenTimeMs = 0L,
                formattedTotalTime = "0m",
                dailyAverageMs = 0L,
                formattedDailyAverage = "0m",
                topApps = emptyList(),
                appCount = 0
            )
        }

        // Query raw usage stats
        var rawStats: List<UsageStats> = usageStatsManager.queryUsageStats(
            intervalCode,
            startTime,
            now
        ) ?: emptyList()

        if (rawStats.isEmpty() || rawStats.all { it.totalTimeInForeground <= 0 }) {
            val bestStats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST,
                startTime,
                now
            )
            if (!bestStats.isNullOrEmpty() && bestStats.any { it.totalTimeInForeground > 0 }) {
                rawStats = bestStats
            } else {
                val aggMap = usageStatsManager.queryAndAggregateUsageStats(startTime, now)
                if (aggMap.isNotEmpty()) {
                    rawStats = aggMap.values.toList()
                }
            }
        }

        // Filter and aggregate apps with non-zero usage
        val aggregated = mutableMapOf<String, AppUsageItem>()
        var totalMs = 0L

        for (stat in rawStats) {
            if (stat.totalTimeInForeground <= 0) continue

            val pkg = stat.packageName
            val appName = getAppName(pkg)
            val category = guessCategory(pkg)
            val existing = aggregated[pkg]

            val currentTotal = (existing?.totalTimeInForegroundMs ?: 0L) + stat.totalTimeInForeground
            val latestUsed = maxOf(existing?.lastTimeUsedEpochMs ?: 0L, stat.lastTimeUsed)

            aggregated[pkg] = AppUsageItem(
                packageName = pkg,
                appName = appName,
                totalTimeInForegroundMs = currentTotal,
                lastTimeUsedEpochMs = latestUsed,
                formattedDuration = formatDuration(currentTotal),
                category = category
            )
        }

        val sortedList = aggregated.values
            .filter { it.totalTimeInForegroundMs > 2_000 } // more than 2 seconds
            .sortedByDescending { it.totalTimeInForegroundMs }

        totalMs = sortedList.sumOf { it.totalTimeInForegroundMs }
        val dailyAvgMs = if (divisorDays > 0) totalMs / divisorDays else totalMs

        val categoryDist = mutableMapOf<String, Long>()
        var productiveMinutes = 0L
        var distractionMinutes = 0L

        for (item in sortedList) {
            val mins = item.totalTimeInForegroundMs / 60_000
            categoryDist[item.category] = (categoryDist[item.category] ?: 0L) + mins
            if (item.category == "Productivity" || item.category == "Communication") {
                productiveMinutes += mins
            } else if (item.category == "Social" || item.category == "Gaming" || item.category == "Entertainment") {
                distractionMinutes += mins
            }
        }

        return UsageSummary(
            hasPermission = true,
            timeframe = timeframe,
            startTimeEpochMs = startTime,
            endTimeEpochMs = now,
            totalScreenTimeMs = totalMs,
            formattedTotalTime = formatDuration(totalMs),
            dailyAverageMs = dailyAvgMs,
            formattedDailyAverage = formatDuration(dailyAvgMs),
            topApps = sortedList.take(25),
            appCount = sortedList.size,
            productiveMinutes = productiveMinutes,
            distractionMinutes = distractionMinutes,
            categoryDistribution = categoryDist
        )
    }

    fun getAllAppsUsage(startTimeEpochMs: Long, endTimeEpochMs: Long): List<AppUsageItem> {
        if (!hasUsagePermission() || usageStatsManager == null) return emptyList()

        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST,
            startTimeEpochMs,
            endTimeEpochMs
        ) ?: return emptyList()

        val map = mutableMapOf<String, Long>()
        val lastUsedMap = mutableMapOf<String, Long>()

        for (s in stats) {
            if (s.totalTimeInForeground > 0) {
                map[s.packageName] = (map[s.packageName] ?: 0L) + s.totalTimeInForeground
                lastUsedMap[s.packageName] = maxOf(lastUsedMap[s.packageName] ?: 0L, s.lastTimeUsed)
            }
        }

        return map.entries
            .filter { it.value > 5_000 }
            .sortedByDescending { it.value }
            .map { (pkg, duration) ->
                AppUsageItem(
                    packageName = pkg,
                    appName = getAppName(pkg),
                    totalTimeInForegroundMs = duration,
                    lastTimeUsedEpochMs = lastUsedMap[pkg] ?: 0L,
                    formattedDuration = formatDuration(duration),
                    category = guessCategory(pkg)
                )
            }
    }

    private fun getAppName(packageName: String): String {
        return try {
            val appInfo: ApplicationInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(packageName, 0)
            }
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName.substringAfterLast('.')
        }
    }

    private fun guessCategory(packageName: String): String {
        val lower = packageName.lowercase()
        return when {
            lower.contains("chrome") || lower.contains("browser") || lower.contains("firefox") -> "Browsing"
            lower.contains("youtube") || lower.contains("netflix") || lower.contains("spotify") || lower.contains("twitch") -> "Entertainment"
            lower.contains("twitter") || lower.contains("instagram") || lower.contains("facebook") || lower.contains("reddit") || lower.contains("tiktok") -> "Social"
            lower.contains("slack") || lower.contains("teams") || lower.contains("discord") || lower.contains("telegram") || lower.contains("whatsapp") -> "Communication"
            lower.contains("docs") || lower.contains("sheets") || lower.contains("notion") || lower.contains("github") || lower.contains("focus") -> "Productivity"
            lower.contains("game") -> "Gaming"
            else -> "Utility"
        }
    }

    companion object {
        fun formatDuration(durationMs: Long): String {
            val totalSeconds = durationMs / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            return when {
                hours > 0 -> "${hours}h ${minutes}m"
                minutes > 0 -> "${minutes}m"
                else -> "${totalSeconds}s"
            }
        }
    }
}
