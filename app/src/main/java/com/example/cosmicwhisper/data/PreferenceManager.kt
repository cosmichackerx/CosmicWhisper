package com.example.cosmicwhisper.data

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit

/**
 * Modern wrapper for SharedPreferences to handle app settings.
 */
class PreferenceManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /**
     * App theme mode: Light, Dark, or System Default.
     */
    var themeMode: Int
        get() = prefs.getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        set(value) = prefs.edit { putInt(KEY_THEME_MODE, value) }

    /**
     * Flag to check if it's the first time the user opens the app.
     */
    var isFirstRun: Boolean
        get() = prefs.getBoolean(KEY_IS_FIRST_RUN, true)
        set(value) = prefs.edit { putBoolean(KEY_IS_FIRST_RUN, value) }

    companion object {
        private const val PREF_NAME = "settings" // Keep consistent with existing implementation
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_IS_FIRST_RUN = "is_first_run"
    }
}
