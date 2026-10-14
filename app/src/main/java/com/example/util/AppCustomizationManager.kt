package com.example.util

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class AppCustomizationManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("focuspilot_customization", Context.MODE_PRIVATE)

    private val _themeColorHex = MutableStateFlow(prefs.getString("theme_color_hex", "#B45309") ?: "#B45309")
    val themeColorHex: StateFlow<String> = _themeColorHex.asStateFlow()

    private val _themeMode = MutableStateFlow(prefs.getString("theme_mode", "system") ?: "system")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _appBannerText = MutableStateFlow(prefs.getString("app_banner_text", "AI Agent Active • Continuous Study Pilot") ?: "AI Agent Active • Continuous Study Pilot")
    val appBannerText: StateFlow<String> = _appBannerText.asStateFlow()

    private val _studentName = MutableStateFlow(prefs.getString("student_name", "Joss Richard") ?: "Joss Richard")
    val studentName: StateFlow<String> = _studentName.asStateFlow()

    private val _activeProfile = MutableStateFlow(prefs.getString("active_profile", "Deep Study") ?: "Deep Study")
    val activeProfile: StateFlow<String> = _activeProfile.asStateFlow()

    private val _customQuote = MutableStateFlow(prefs.getString("custom_quote", "Consistency is the DNA of mastery.") ?: "Consistency is the DNA of mastery.")
    val customQuote: StateFlow<String> = _customQuote.asStateFlow()

    private val _customRules = MutableStateFlow(loadCustomRules())
    val customRules: StateFlow<List<String>> = _customRules.asStateFlow()

    private fun loadCustomRules(): List<String> {
        val jsonStr = prefs.getString("custom_rules", null) ?: return listOf(
            "Phone lock active during scheduled college lecture blocks",
            "Screen time audited every 30 minutes by Hermes AI",
            "Alarm rings continuously if student stays in hostel during lab hours"
        )
        return try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                list.add(arr.getString(i))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun applyCustomization(
        themeColorHex: String? = null,
        themeMode: String? = null,
        bannerText: String? = null,
        studentName: String? = null,
        activeProfile: String? = null,
        customQuote: String? = null,
        customRulesList: List<String>? = null
    ) {
        val editor = prefs.edit()
        themeColorHex?.let {
            val sanitized = if (it.startsWith("#")) it else "#$it"
            _themeColorHex.value = sanitized
            editor.putString("theme_color_hex", sanitized)
        }
        themeMode?.let {
            _themeMode.value = it
            editor.putString("theme_mode", it)
        }
        bannerText?.let {
            _appBannerText.value = it
            editor.putString("app_banner_text", it)
        }
        studentName?.let {
            _studentName.value = it
            editor.putString("student_name", it)
        }
        activeProfile?.let {
            _activeProfile.value = it
            editor.putString("active_profile", it)
        }
        customQuote?.let {
            _customQuote.value = it
            editor.putString("custom_quote", it)
        }
        customRulesList?.let {
            _customRules.value = it
            val arr = JSONArray()
            for (rule in it) arr.put(rule)
            editor.putString("custom_rules", arr.toString())
        }
        editor.apply()
    }

    fun getAsJsonObject(): JSONObject {
        val json = JSONObject()
        json.put("theme_color_hex", _themeColorHex.value)
        json.put("theme_mode", _themeMode.value)
        json.put("app_banner_text", _appBannerText.value)
        json.put("student_name", _studentName.value)
        json.put("active_profile", _activeProfile.value)
        json.put("custom_quote", _customQuote.value)
        val arr = JSONArray()
        for (r in _customRules.value) arr.put(r)
        json.put("custom_rules", arr)
        return json
    }

    fun getPrimaryColor(): Color {
        return try {
            val hex = _themeColorHex.value.trim().removePrefix("#")
            val colorLong = hex.toLong(16)
            if (hex.length == 6) {
                Color(0xFF000000 or colorLong)
            } else if (hex.length == 8) {
                Color(colorLong)
            } else {
                Color(0xFFB45309)
            }
        } catch (e: Exception) {
            Color(0xFFB45309)
        }
    }
}
