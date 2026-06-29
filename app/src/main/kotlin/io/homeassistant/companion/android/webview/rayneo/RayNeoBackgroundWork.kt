package io.homeassistant.companion.android.webview.rayneo

import android.content.Context
import androidx.work.WorkManager

/** Cancels unsupported background work inherited from the phone companion application. */
internal fun disableRayNeoBackgroundWork(context: Context) {
    WorkManager.getInstance(context).cancelAllWork()
}
