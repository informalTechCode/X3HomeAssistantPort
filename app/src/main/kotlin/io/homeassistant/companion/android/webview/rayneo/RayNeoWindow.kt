package io.homeassistant.companion.android.webview.rayneo

import android.app.Activity
import android.view.WindowManager

/** Applies the display and input policy required while the RayNeo application is visible. */
internal fun Activity.configureRayNeoWindow() {
    window.addFlags(
        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
    )
    window.setSoftInputMode(
        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING or
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN,
    )
    window.attributes = window.attributes.apply {
        screenBrightness = RAYNEO_SCREEN_BRIGHTNESS
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            preferredRefreshRate = 30f
        }
    }
}

private const val RAYNEO_SCREEN_BRIGHTNESS = 0.05f
