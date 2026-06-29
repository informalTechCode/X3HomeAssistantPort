package io.homeassistant.companion.android.webview.rayneo

import android.os.SystemClock
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber

/** Receives TapLink companion input and advertises this glasses endpoint over UDP. */
internal class RayNeoNetworkInputServer(private val listener: RayNeoControllerInputListener) {
    private val running = AtomicBoolean(false)
    private val loggedActiveReceive = AtomicBoolean(false)
    private val lastReceiveTime = AtomicLong(0L)
    private val startTime = AtomicLong(0L)
    private var socket: DatagramSocket? = null
    private var receiveJob: Job? = null
    private var discoveryJob: Job? = null

    fun start(scope: CoroutineScope) {
        if (!running.compareAndSet(false, true)) return
        loggedActiveReceive.set(false)
        lastReceiveTime.set(0L)
        startTime.set(SystemClock.elapsedRealtime())
        try {
            val inputSocket = DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                bind(InetSocketAddress(GLASSES_INPUT_PORT))
            }
            socket = inputSocket
            receiveJob = scope.launch(Dispatchers.IO) { receiveLoop(inputSocket) }
            discoveryJob = scope.launch(Dispatchers.IO) { discoveryLoop(inputSocket) }
            Timber.i("RayNeo network input listening on UDP $GLASSES_INPUT_PORT")
        } catch (exception: SocketException) {
            running.set(false)
            Timber.w(exception, "Failed to start RayNeo network input server")
        }
    }

    fun stop() {
        running.set(false)
        socket?.close()
        socket = null
        receiveJob?.cancel()
        receiveJob = null
        discoveryJob?.cancel()
        discoveryJob = null
    }

    fun localIpv4Addresses(): List<String> = NetworkInterface.getNetworkInterfaces()
        ?.toList()
        .orEmpty()
        .asSequence()
        .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
        .flatMap { it.inetAddresses.toList().asSequence() }
        .filterIsInstance<Inet4Address>()
        .filter { !it.isLoopbackAddress && !it.isAnyLocalAddress }
        .mapNotNull { it.hostAddress }
        .distinct()
        .toList()

    private suspend fun receiveLoop(inputSocket: DatagramSocket) {
        val buffer = ByteArray(MAX_PACKET_SIZE)
        while (running.get() && currentCoroutineContext().isActive) {
            try {
                val packet = DatagramPacket(buffer, buffer.size)
                inputSocket.receive(packet)
                lastReceiveTime.set(SystemClock.elapsedRealtime())
                if (loggedActiveReceive.compareAndSet(false, true)) {
                    Timber.i("RayNeo companion UDP traffic received")
                }
                val message = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                sendAcknowledgement(inputSocket, packet, message)
                handleMessage(message)
            } catch (exception: SocketException) {
                if (running.get()) Timber.w(exception, "RayNeo network input socket closed unexpectedly")
                break
            }
        }
    }

    private suspend fun discoveryLoop(inputSocket: DatagramSocket) {
        while (running.get() && currentCoroutineContext().isActive) {
            sendDiscovery(inputSocket)
            val lastReceive = lastReceiveTime.get()
            val recentlyActive = lastReceive > 0L &&
                SystemClock.elapsedRealtime() - lastReceive < ACTIVE_THRESHOLD_MS
            val inStartupWindow = SystemClock.elapsedRealtime() - startTime.get() < DISCOVERY_STARTUP_WINDOW_MS
            delay(
                when {
                    recentlyActive -> DISCOVERY_ACTIVE_INTERVAL_MS
                    inStartupWindow -> DISCOVERY_STARTUP_INTERVAL_MS
                    else -> DISCOVERY_INACTIVE_INTERVAL_MS
                },
            )
        }
    }

    private fun sendDiscovery(inputSocket: DatagramSocket) {
        val payload = JSONObject()
            .put(FIELD_TYPE, TYPE_ENDPOINT)
            .put(FIELD_PORT, GLASSES_INPUT_PORT)
            .toString()
            .toByteArray(Charsets.UTF_8)
        val interfaceBroadcasts = NetworkInterface.getNetworkInterfaces()
            ?.toList()
            .orEmpty()
            .asSequence()
            .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
            .flatMap { it.interfaceAddresses.toList().asSequence() }
            .mapNotNull { it.broadcast }
            .toList()
        val broadcasts = (listOf(InetAddress.getByName(LIMITED_BROADCAST_ADDRESS)) + interfaceBroadcasts)
            .distinctBy { it.hostAddress }

        broadcasts.forEach { address ->
            runCatching {
                inputSocket.send(DatagramPacket(payload, payload.size, address, PHONE_DISCOVERY_PORT))
            }.onFailure { Timber.d(it, "Failed to broadcast RayNeo controller endpoint") }
        }
    }

    private suspend fun handleMessage(message: String) {
        val json = runCatching { JSONObject(message) }.getOrNull() ?: return
        val messageId = json.optString(FIELD_MESSAGE_ID).takeIf { it.isNotEmpty() }
        if (!RayNeoReliableMessageDeduper.shouldDispatch(messageId)) return

        withContext(Dispatchers.Main.immediate) {
            when (json.optString(FIELD_TYPE)) {
                TYPE_PING -> Unit
                TYPE_TRACKPAD -> listener.onControllerTrackpadGesture(
                    action = parseTrackpadAction(json.optString(FIELD_ACTION)) ?: RayNeoTrackpadAction.MOVE,
                    dx = json.optDouble(FIELD_DX, 0.0).toFloat(),
                    dy = json.optDouble(FIELD_DY, 0.0).toFloat(),
                    pointerCount = json.optInt(FIELD_POINTER_COUNT, 1).coerceAtLeast(1),
                )
                TYPE_AIR_MOUSE -> listener.onControllerAirMouseRay(
                    x = json.optDouble(FIELD_X, 0.5).toFloat().coerceIn(0f, 1f),
                    y = json.optDouble(FIELD_Y, 0.5).toFloat().coerceIn(0f, 1f),
                    select = json.optBoolean(FIELD_SELECT),
                )
                TYPE_SCROLL -> listener.onControllerScroll(
                    dx = json.optDouble(FIELD_DX, 0.0).toFloat(),
                    dy = json.optDouble(FIELD_DY, 0.0).toFloat(),
                )
                TYPE_KEY -> listener.onControllerKey(json.optString(FIELD_KEY))
                TYPE_TAP -> listener.onControllerTap()
                TYPE_TOUCH -> parseTouchAction(json.optString(FIELD_ACTION))?.let { action ->
                    listener.onControllerTouch(
                        action = action,
                        x = json.optDouble(FIELD_X, 0.5).toFloat().coerceIn(0f, 1f),
                        y = json.optDouble(FIELD_Y, 0.5).toFloat().coerceIn(0f, 1f),
                    )
                }
                TYPE_MODE -> parseMode(json.optString(FIELD_MODE))?.let(listener::onControllerModeChanged)
            }
        }
    }

    private fun sendAcknowledgement(inputSocket: DatagramSocket, packet: DatagramPacket, message: String) {
        val source = runCatching { JSONObject(message) }.getOrNull()
        val acknowledgement = JSONObject().put(FIELD_TYPE, TYPE_ACK)
        source?.optString(FIELD_MESSAGE_ID)?.takeIf { it.isNotEmpty() }?.let {
            acknowledgement.put(FIELD_MESSAGE_ID, it)
        }
        val payload = acknowledgement.toString().toByteArray(Charsets.UTF_8)
        runCatching {
            inputSocket.send(DatagramPacket(payload, payload.size, packet.address, PHONE_DISCOVERY_PORT))
        }.onFailure { Timber.d(it, "Failed to acknowledge RayNeo controller message") }
    }
}

internal fun parseMode(value: String): RayNeoControllerMode? = when (value) {
    "airMouse" -> RayNeoControllerMode.AIR_MOUSE
    "trackpad" -> RayNeoControllerMode.TRACKPAD
    "meta" -> RayNeoControllerMode.META
    else -> null
}

internal fun parseTrackpadAction(value: String): RayNeoTrackpadAction? = when (value) {
    "down" -> RayNeoTrackpadAction.DOWN
    "move" -> RayNeoTrackpadAction.MOVE
    "pointer" -> RayNeoTrackpadAction.POINTER
    "up" -> RayNeoTrackpadAction.UP
    "cancel" -> RayNeoTrackpadAction.CANCEL
    else -> null
}

internal fun parseTouchAction(value: String): RayNeoTouchAction? = when (value) {
    "down" -> RayNeoTouchAction.DOWN
    "move" -> RayNeoTouchAction.MOVE
    "up" -> RayNeoTouchAction.UP
    "cancel" -> RayNeoTouchAction.CANCEL
    else -> null
}

private const val PHONE_DISCOVERY_PORT = 37692
internal const val GLASSES_INPUT_PORT = 37693
internal const val TYPE_ENDPOINT = "controllerNetworkEndpoint"
private const val TYPE_ACK = "controllerNetworkAck"
private const val TYPE_PING = "controllerNetworkPing"
private const val TYPE_TRACKPAD = "trackpad"
private const val TYPE_AIR_MOUSE = "airMouse"
private const val TYPE_SCROLL = "scroll"
private const val TYPE_KEY = "key"
private const val TYPE_TAP = "tap"
private const val TYPE_TOUCH = "touch"
private const val TYPE_MODE = "mode"
private const val FIELD_TYPE = "type"
private const val FIELD_PORT = "port"
private const val FIELD_MESSAGE_ID = "messageId"
private const val FIELD_ACTION = "action"
private const val FIELD_DX = "dx"
private const val FIELD_DY = "dy"
private const val FIELD_POINTER_COUNT = "pointerCount"
private const val FIELD_X = "x"
private const val FIELD_Y = "y"
private const val FIELD_SELECT = "select"
private const val FIELD_KEY = "key"
private const val FIELD_MODE = "mode"
private const val MAX_PACKET_SIZE = 4096
private const val DISCOVERY_STARTUP_INTERVAL_MS = 1_000L
private const val DISCOVERY_INACTIVE_INTERVAL_MS = 5_000L
private const val DISCOVERY_ACTIVE_INTERVAL_MS = 15_000L
private const val DISCOVERY_STARTUP_WINDOW_MS = 10_000L
private const val ACTIVE_THRESHOLD_MS = 10_000L
private const val LIMITED_BROADCAST_ADDRESS = "255.255.255.255"
