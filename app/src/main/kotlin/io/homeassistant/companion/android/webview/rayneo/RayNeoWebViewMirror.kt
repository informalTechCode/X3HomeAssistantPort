package io.homeassistant.companion.android.webview.rayneo

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.view.PixelCopy
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import timber.log.Timber

/**
 * Mirrors the composed left-eye window region to a right-eye [SurfaceView].
 *
 * This is the TapLink X3 PixelCopy pipeline: capture the complete left-eye region from the
 * activity window, then post that bitmap to the right-eye surface at a power-aware cadence.
 * Capturing the window instead of calling `WebView.draw()` preserves Chromium's
 * hardware-composited content.
 */
internal class RayNeoWebViewMirror @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    SurfaceView(context, attrs),
    SurfaceHolder.Callback {
    private val activity = context as? Activity
    private val refreshHandler = Handler(Looper.getMainLooper())
    private val captureBounds = Rect()
    private val destinationBounds = Rect()
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var source: View? = null
    private var bitmap: Bitmap? = null
    private var surfaceReady = false
    private var shouldRun = false
    private var captureInFlight = false
    private var frameScheduled = false
    private var lastCaptureTime = 0L
    private var lastFailureLogTime = 0L
    private var interactiveUntil = 0L
    private var releaseRequested = false
    private var loggedFirstFrame = false

    private val captureRunnable = Runnable {
        frameScheduled = false
        if (!shouldRun) return@Runnable
        val attemptTime = SystemClock.uptimeMillis()
        captureLeftEyeContent()
        if (!captureInFlight) {
            if (lastCaptureTime == 0L) lastCaptureTime = attemptTime
            scheduleNextFrame()
        }
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        holder.setFormat(PixelFormat.RGBA_8888)
        holder.addCallback(this)
    }

    fun setSource(source: View) {
        this.source = source
    }

    fun setCaptureBounds(left: Int, top: Int, right: Int, bottom: Int) {
        if (right <= left || bottom <= top) return
        captureBounds.set(left, top, right, bottom)
        noteInteraction()
    }

    fun start() {
        shouldRun = true
        scheduleNextFrame()
    }

    fun stop() {
        shouldRun = false
        frameScheduled = false
        refreshHandler.removeCallbacks(captureRunnable)
    }

    /** Temporarily increases mirror refresh frequency after visible user interaction. */
    fun noteInteraction() {
        if (!shouldRun) return
        interactiveUntil = maxOf(interactiveUntil, SystemClock.uptimeMillis() + INTERACTIVE_WINDOW_MS)
        if (!surfaceReady) return
        rescheduleFrame()
    }

    /** Returns the mirror to its low-power cadence during expensive screen transitions. */
    fun preferIdle() {
        interactiveUntil = 0L
        if (!shouldRun || !surfaceReady) return
        rescheduleFrame()
    }

    private fun rescheduleFrame() {
        refreshHandler.removeCallbacks(captureRunnable)
        frameScheduled = false
        scheduleNextFrame()
    }

    fun release() {
        releaseRequested = true
        stop()
        if (!captureInFlight) {
            bitmap?.takeUnless(Bitmap::isRecycled)?.recycle()
        }
        bitmap = null
        holder.removeCallback(this)
    }

    private fun scheduleNextFrame() {
        if (!shouldRun || !surfaceReady || frameScheduled) return
        val now = SystemClock.uptimeMillis()
        val interval = if (now < interactiveUntil) INTERACTIVE_CAPTURE_INTERVAL_MS else IDLE_CAPTURE_INTERVAL_MS
        val delay = (interval - (now - lastCaptureTime)).coerceAtLeast(0L)
        frameScheduled = true
        refreshHandler.postDelayed(captureRunnable, delay)
    }

    private fun captureLeftEyeContent() {
        val window = activity?.window ?: return
        val sourceView = source ?: return
        if (
            !surfaceReady ||
            !holder.surface.isValid ||
            !sourceView.isAttachedToWindow ||
            captureBounds.isEmpty ||
            captureInFlight ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.N
        ) {
            return
        }

        val now = SystemClock.uptimeMillis()
        val captureBitmap = getOrCreateBitmap(captureBounds.width(), captureBounds.height()) ?: return

        lastCaptureTime = now
        captureInFlight = true
        try {
            PixelCopy.request(
                window,
                captureBounds,
                captureBitmap,
                { result ->
                    captureInFlight = false
                    if (result == PixelCopy.SUCCESS && shouldRun && bitmap === captureBitmap) {
                        drawBitmapToSurface(captureBitmap)
                        if (!loggedFirstFrame) {
                            loggedFirstFrame = true
                            Timber.i(
                                "RayNeo right-eye mirroring active: ${captureBitmap.width}x${captureBitmap.height}",
                            )
                        }
                    } else if (result != PixelCopy.SUCCESS) {
                        logPixelCopyFailure(result)
                    }
                    if (releaseRequested && !captureBitmap.isRecycled) {
                        captureBitmap.recycle()
                    }
                    scheduleNextFrame()
                },
                refreshHandler,
            )
        } catch (exception: IllegalArgumentException) {
            captureInFlight = false
            Timber.w(exception, "Unable to capture RayNeo left-eye window region")
            scheduleNextFrame()
        }
    }

    private fun logPixelCopyFailure(result: Int) {
        val now = SystemClock.uptimeMillis()
        if (now - lastFailureLogTime >= FAILURE_LOG_INTERVAL_MS) {
            lastFailureLogTime = now
            Timber.w("RayNeo PixelCopy failed with result=$result")
        }
    }

    private fun getOrCreateBitmap(width: Int, height: Int): Bitmap? {
        val current = bitmap
        if (current != null && !current.isRecycled && current.width == width && current.height == height) {
            return current
        }

        current?.takeUnless(Bitmap::isRecycled)?.recycle()
        bitmap = try {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        } catch (exception: IllegalArgumentException) {
            Timber.w(exception, "Unable to allocate RayNeo mirror bitmap")
            null
        }
        return bitmap
    }

    private fun drawBitmapToSurface(captureBitmap: Bitmap) {
        if (!surfaceReady || !holder.surface.isValid || captureBitmap.isRecycled) return
        var canvas: Canvas? = null
        try {
            canvas = holder.lockCanvas() ?: return
            destinationBounds.set(0, 0, canvas.width, canvas.height)
            canvas.drawColor(Color.BLACK)
            canvas.drawBitmap(captureBitmap, null, destinationBounds, bitmapPaint)
        } catch (exception: IllegalArgumentException) {
            Timber.w(exception, "Unable to draw RayNeo right-eye frame")
        } finally {
            canvas?.let {
                try {
                    holder.unlockCanvasAndPost(it)
                } catch (exception: IllegalStateException) {
                    Timber.w(exception, "Unable to post RayNeo right-eye frame")
                }
            }
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
        scheduleNextFrame()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        surfaceReady = width > 0 && height > 0
        Timber.d("RayNeo right-eye surface changed: ${width}x$height")
        scheduleNextFrame()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        refreshHandler.removeCallbacks(captureRunnable)
        frameScheduled = false
    }
}

private const val INTERACTIVE_CAPTURE_INTERVAL_MS = 100L
private const val IDLE_CAPTURE_INTERVAL_MS = 500L
private const val INTERACTIVE_WINDOW_MS = 750L
private const val FAILURE_LOG_INTERVAL_MS = 1_000L
