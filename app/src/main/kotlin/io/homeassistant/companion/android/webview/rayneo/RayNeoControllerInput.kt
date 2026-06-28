package io.homeassistant.companion.android.webview.rayneo

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal interface RayNeoControllerInputListener {
    fun onControllerConnected(name: String, address: String)

    fun onControllerDisconnected()

    fun onControllerModeChanged(mode: RayNeoControllerMode)

    fun onControllerKey(key: String)

    fun onControllerAirMouseRay(x: Float, y: Float, select: Boolean)

    fun onControllerTrackpadGesture(action: RayNeoTrackpadAction, dx: Float, dy: Float, pointerCount: Int)

    fun onControllerScroll(dx: Float, dy: Float)

    fun onControllerTap()

    fun onControllerTouch(action: RayNeoTouchAction, x: Float, y: Float)
}

enum class RayNeoControllerMode {
    AIR_MOUSE,
    TRACKPAD,
    META,
}

enum class RayNeoTrackpadAction {
    DOWN,
    MOVE,
    POINTER,
    UP,
    CANCEL,
}

enum class RayNeoTouchAction {
    DOWN,
    MOVE,
    UP,
    CANCEL,
}

internal object RayNeoReliableMessageDeduper {
    private const val CACHE_SIZE = 256
    private val mutex = Mutex()
    private val messageIds = LinkedHashSet<String>()

    suspend fun shouldDispatch(messageId: String?): Boolean = mutex.withLock {
        if (messageId.isNullOrEmpty()) return@withLock true
        if (!messageIds.add(messageId)) return@withLock false
        if (messageIds.size > CACHE_SIZE) {
            messageIds.remove(messageIds.first())
        }
        true
    }
}
