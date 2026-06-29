package io.homeassistant.companion.android.themes

import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import io.homeassistant.companion.android.common.data.prefs.NightModeTheme
import io.homeassistant.companion.android.common.data.prefs.NightModeTheme.ANDROID
import io.homeassistant.companion.android.common.data.prefs.NightModeTheme.DARK
import io.homeassistant.companion.android.common.data.prefs.NightModeTheme.LIGHT
import io.homeassistant.companion.android.common.data.prefs.NightModeTheme.SYSTEM
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.util.SdkVersion
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Manages the night mode theme for the application based on the user selection from the settings.
 *
 * This class is responsible for retrieving, saving, and applying the night mode theme.
 * It interacts with [PrefsRepository] to persist the selected theme.
 *
 * @property prefsRepository The repository for accessing and storing application preferences.
 */
class NightModeManager @Inject constructor(private val prefsRepository: PrefsRepository) {

    suspend fun getCurrentNightMode(): NightModeTheme {
        // Always force dark mode
        return DARK
    }

    suspend fun saveNightMode(nightModeTheme: NightModeTheme?) {
        // Ignore any attempt to change — always dark
        Timber.i("Ignoring night mode change request, forcing DARK mode")
    }

    suspend fun applyCurrentNightMode() {
        withContext(Dispatchers.Main) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }
    }
}

private suspend fun NightModeTheme.setAsDefaultNightMode() {
    withContext(Dispatchers.Main) {
        when (this@setAsDefaultNightMode) {
            DARK -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
            ANDROID, SYSTEM -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
            else -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }
    }
}
