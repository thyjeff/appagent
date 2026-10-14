package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class FocusScreen(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
    SCHEDULE("Schedule", Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth, "nav_schedule"),
    ANALYTICS("Screen Time", Icons.Filled.Analytics, Icons.Outlined.Analytics, "nav_analytics"),
    SETTINGS("Settings", Icons.Filled.Settings, Icons.Outlined.Settings, "nav_settings")
}
