package com.example.storage

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppThemeMode {
    SYSTEM, DARK, LIGHT
}

class SettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("pskbtauto_settings", Context.MODE_PRIVATE)

    private val _keepScreenAwake = MutableStateFlow(prefs.getBoolean(KEY_KEEP_AWAKE, true))
    val keepScreenAwake: StateFlow<Boolean> = _keepScreenAwake.asStateFlow()

    private val _defaultDelayMs = MutableStateFlow(prefs.getLong(KEY_DEFAULT_DELAY, 30L))
    val defaultDelayMs: StateFlow<Long> = _defaultDelayMs.asStateFlow()

    private val _themeMode = MutableStateFlow(
        try {
            AppThemeMode.valueOf(prefs.getString(KEY_THEME_MODE, AppThemeMode.DARK.name) ?: AppThemeMode.DARK.name)
        } catch (_: Exception) {
            AppThemeMode.DARK
        }
    )
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _isTutorialCompleted = MutableStateFlow(prefs.getBoolean(KEY_TUTORIAL_COMPLETED, false))
    val isTutorialCompleted: StateFlow<Boolean> = _isTutorialCompleted.asStateFlow()

    fun setKeepScreenAwake(enabled: Boolean) {
        _keepScreenAwake.value = enabled
        prefs.edit().putBoolean(KEY_KEEP_AWAKE, enabled).apply()
    }

    fun setDefaultDelayMs(delayMs: Long) {
        _defaultDelayMs.value = delayMs
        prefs.edit().putLong(KEY_DEFAULT_DELAY, delayMs).apply()
    }

    fun setThemeMode(mode: AppThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    private val _preferredConnectionMode = MutableStateFlow(
        prefs.getString(KEY_PREFERRED_CONNECTION_MODE, "AUTO") ?: "AUTO"
    )
    val preferredConnectionMode: StateFlow<String> = _preferredConnectionMode.asStateFlow()

    private val _lastConnectedServerIp = MutableStateFlow(
        prefs.getString(KEY_LAST_CONNECTED_IP, "127.0.0.1") ?: "127.0.0.1"
    )
    val lastConnectedServerIp: StateFlow<String> = _lastConnectedServerIp.asStateFlow()

    fun setPreferredConnectionMode(mode: String) {
        _preferredConnectionMode.value = mode
        prefs.edit().putString(KEY_PREFERRED_CONNECTION_MODE, mode).apply()
    }

    fun setLastConnectedServerIp(ip: String) {
        _lastConnectedServerIp.value = ip
        prefs.edit().putString(KEY_LAST_CONNECTED_IP, ip).apply()
    }

    fun setTutorialCompleted(completed: Boolean) {
        _isTutorialCompleted.value = completed
        prefs.edit().putBoolean(KEY_TUTORIAL_COMPLETED, completed).apply()
    }

    companion object {
        private const val KEY_KEEP_AWAKE = "keep_screen_awake"
        private const val KEY_DEFAULT_DELAY = "default_delay_ms"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_TUTORIAL_COMPLETED = "tutorial_completed"
        private const val KEY_PREFERRED_CONNECTION_MODE = "preferred_connection_mode"
        private const val KEY_LAST_CONNECTED_IP = "last_connected_ip"
    }
}
