package io.homeassistant.companion.android.webview.rayneo

import android.webkit.JavascriptInterface
import android.webkit.WebView

/** Receives editable-element focus notifications from the Home Assistant frontend. */
internal class RayNeoKeyboardBridge(private val webView: WebView, private val onInputFocus: () -> Unit) {
    @JavascriptInterface
    fun onInputFocus() {
        webView.post(onInputFocus)
    }
}

internal const val RAYNEO_KEYBOARD_BRIDGE_NAME = "RayNeoKeyboardBridge"
