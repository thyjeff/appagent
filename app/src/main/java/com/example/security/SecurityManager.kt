package com.example.security

import android.content.Context
import android.content.SharedPreferences
import java.security.SecureRandom
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SecurityManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isRemoteAccessEnabled = MutableStateFlow(prefs.getBoolean(KEY_REMOTE_ENABLED, true))
    val isRemoteAccessEnabled: StateFlow<Boolean> = _isRemoteAccessEnabled.asStateFlow()

    private val _allowUsageStats = MutableStateFlow(prefs.getBoolean(KEY_ALLOW_USAGE, true))
    val allowUsageStats: StateFlow<Boolean> = _allowUsageStats.asStateFlow()

    private val _allowFileAccess = MutableStateFlow(prefs.getBoolean(KEY_ALLOW_FILES, true))
    val allowFileAccess: StateFlow<Boolean> = _allowFileAccess.asStateFlow()

    private val _allowAppLaunch = MutableStateFlow(prefs.getBoolean(KEY_ALLOW_LAUNCH, true))
    val allowAppLaunch: StateFlow<Boolean> = _allowAppLaunch.asStateFlow()

    private val _allowTaskManagement = MutableStateFlow(prefs.getBoolean(KEY_ALLOW_TASKS, true))
    val allowTaskManagement: StateFlow<Boolean> = _allowTaskManagement.asStateFlow()

    private val _allowAlerts = MutableStateFlow(prefs.getBoolean(KEY_ALLOW_ALERTS, true))
    val allowAlerts: StateFlow<Boolean> = _allowAlerts.asStateFlow()

    private val _serverPort = MutableStateFlow(prefs.getInt(KEY_SERVER_PORT, DEFAULT_PORT))
    val serverPort: StateFlow<Int> = _serverPort.asStateFlow()

    private val _apiToken = MutableStateFlow(getOrGenerateApiToken())
    val apiToken: StateFlow<String> = _apiToken.asStateFlow()

    private val _ntfyUrl = MutableStateFlow(getOrInitNtfyUrl())
    val ntfyUrl: StateFlow<String> = _ntfyUrl.asStateFlow()

    private val _customMcpUrl = MutableStateFlow(prefs.getString(KEY_CUSTOM_MCP_URL, DEFAULT_MCP_URL) ?: DEFAULT_MCP_URL)
    val customMcpUrl: StateFlow<String> = _customMcpUrl.asStateFlow()

    private val _pairingPin = MutableStateFlow(getOrGeneratePairingPin())
    val pairingPin: StateFlow<String> = _pairingPin.asStateFlow()

    fun getApiToken(): String = _apiToken.value

    private fun getOrInitNtfyUrl(): String {
        val existing = prefs.getString(KEY_NTFY_URL, null)
        if (!existing.isNullOrBlank()) {
            return existing
        }
        val defaultUrl = "https://ntfy.sh/focuspilot_${getOrGenerateApiToken()}"
        prefs.edit().putString(KEY_NTFY_URL, defaultUrl).apply()
        return defaultUrl
    }

    private fun getOrGenerateApiToken(): String {
        val existing = prefs.getString(KEY_API_TOKEN, null)
        if (!existing.isNullOrBlank()) {
            return existing
        }
        val generated = generateSecureToken()
        prefs.edit().putString(KEY_API_TOKEN, generated).apply()
        return generated
    }

    fun regenerateApiToken(): String {
        val oldToken = _apiToken.value
        val newToken = generateSecureToken()
        prefs.edit().putString(KEY_API_TOKEN, newToken).apply()
        _apiToken.value = newToken
        if (_ntfyUrl.value.isBlank() || _ntfyUrl.value == "https://ntfy.sh/focuspilot_$oldToken") {
            val newNtfy = "https://ntfy.sh/focuspilot_$newToken"
            prefs.edit().putString(KEY_NTFY_URL, newNtfy).apply()
            _ntfyUrl.value = newNtfy
        }
        return newToken
    }

    fun setApiToken(token: String) {
        prefs.edit().putString(KEY_API_TOKEN, token).apply()
        _apiToken.value = token
    }

    fun setRemoteAccessEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REMOTE_ENABLED, enabled).apply()
        _isRemoteAccessEnabled.value = enabled
    }

    fun setAllowUsageStats(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ALLOW_USAGE, enabled).apply()
        _allowUsageStats.value = enabled
    }

    fun setAllowFileAccess(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ALLOW_FILES, enabled).apply()
        _allowFileAccess.value = enabled
    }

    fun setAllowAppLaunch(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ALLOW_LAUNCH, enabled).apply()
        _allowAppLaunch.value = enabled
    }

    fun setAllowTaskManagement(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ALLOW_TASKS, enabled).apply()
        _allowTaskManagement.value = enabled
    }

    fun setAllowAlerts(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ALLOW_ALERTS, enabled).apply()
        _allowAlerts.value = enabled
    }

    fun setServerPort(port: Int) {
        val validPort = if (port in 1024..65535) port else DEFAULT_PORT
        prefs.edit().putInt(KEY_SERVER_PORT, validPort).apply()
        _serverPort.value = validPort
    }

    fun setNtfyUrl(url: String) {
        prefs.edit().putString(KEY_NTFY_URL, url.trim()).apply()
        _ntfyUrl.value = url.trim()
    }

    fun setCustomMcpUrl(url: String) {
        val clean = url.trim()
        prefs.edit().putString(KEY_CUSTOM_MCP_URL, clean).apply()
        _customMcpUrl.value = clean
    }

    private fun getOrGeneratePairingPin(): String {
        val existing = prefs.getString(KEY_PAIRING_PIN, null)
        if (!existing.isNullOrBlank()) {
            return existing
        }
        val pin = (100000..999999).random().toString()
        prefs.edit().putString(KEY_PAIRING_PIN, pin).apply()
        return pin
    }

    fun regeneratePairingPin(): String {
        val pin = (100000..999999).random().toString()
        prefs.edit().putString(KEY_PAIRING_PIN, pin).apply()
        _pairingPin.value = pin
        return pin
    }

    /**
     * Validates whether a file is safe to read:
     * - Name cannot match secret patterns (.env, id_rsa, .pem, .key, etc.)
     * - Size must not exceed maxAllowedSizeBytes (default 10MB)
     */
    fun isFileSafeToExpose(fileName: String, fileSizeBytes: Long): SecurityCheckResult {
        val lower = fileName.lowercase()
        val blockedPatterns = listOf(
            ".env", "id_rsa", "id_ed25519", ".pem", ".key", ".p12", ".pfx",
            "keystore", ".jks", "credentials", "secret", "password", "shadow", "masterkey"
        )
        for (pattern in blockedPatterns) {
            if (lower.contains(pattern)) {
                return SecurityCheckResult.Blocked("Filename matches security blocklist pattern: '$pattern'")
            }
        }

        val maxAllowed = 10L * 1024 * 1024 // 10MB
        if (fileSizeBytes > maxAllowed) {
            return SecurityCheckResult.Blocked("File size exceeds 10MB safety limit ($fileSizeBytes bytes)")
        }

        return SecurityCheckResult.Allowed
    }

    sealed class SecurityCheckResult {
        object Allowed : SecurityCheckResult()
        data class Blocked(val reason: String) : SecurityCheckResult()
    }

    companion object {
        private const val PREFS_NAME = "focuspilot_security_prefs"
        private const val KEY_API_TOKEN = "key_api_token"
        private const val KEY_REMOTE_ENABLED = "key_remote_enabled"
        private const val KEY_ALLOW_USAGE = "key_allow_usage"
        private const val KEY_ALLOW_FILES = "key_allow_files"
        private const val KEY_ALLOW_LAUNCH = "key_allow_launch"
        private const val KEY_ALLOW_TASKS = "key_allow_tasks"
        private const val KEY_ALLOW_ALERTS = "key_allow_alerts"
        private const val KEY_SERVER_PORT = "key_server_port"
        private const val KEY_NTFY_URL = "key_ntfy_url"
        private const val KEY_CUSTOM_MCP_URL = "key_custom_mcp_url"
        private const val KEY_PAIRING_PIN = "key_pairing_pin"
        const val DEFAULT_PORT = 8080
        const val DEFAULT_MCP_URL = "https://focuspilot-mcp.vercel.app/sse"

        fun generateSecureToken(): String {
            val random = SecureRandom()
            val bytes = ByteArray(24)
            random.nextBytes(bytes)
            val hex = StringBuilder()
            for (b in bytes) {
                hex.append(String.format("%02x", b))
            }
            return "fp_" + hex.toString()
        }
    }
}
