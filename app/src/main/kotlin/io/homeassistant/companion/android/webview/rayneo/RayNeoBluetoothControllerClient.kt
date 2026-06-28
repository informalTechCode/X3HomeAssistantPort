package io.homeassistant.companion.android.webview.rayneo

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/** Classic Bluetooth SPP client for the TapLink phone controller. */
internal class RayNeoBluetoothControllerClient(
    private val context: Context,
    private val listener: RayNeoControllerInputListener,
) {
    private val running = AtomicBoolean(false)
    private val connected = AtomicBoolean(false)
    private val outputMutex = Mutex()
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }
    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var connectionJob: Job? = null

    fun start(scope: CoroutineScope): Boolean {
        if (running.get()) return true
        if (bluetoothAdapter?.isEnabled != true || !hasConnectPermission()) return false
        running.set(true)
        connectionJob = scope.launch(Dispatchers.IO) { connectLoop() }
        return true
    }

    fun stop() {
        val wasConnected = connected.getAndSet(false)
        running.set(false)
        connectionJob?.cancel()
        connectionJob = null
        closeSocket()
        if (wasConnected) listener.onControllerDisconnected()
    }

    fun sendNetworkEndpoint(scope: CoroutineScope, port: Int, addresses: List<String>) {
        scope.launch(Dispatchers.IO) {
            send(
                JSONObject()
                    .put(FIELD_TYPE, TYPE_ENDPOINT)
                    .put(FIELD_PORT, port)
                    .put(FIELD_ADDRESSES, JSONArray(addresses)),
            )
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun connectLoop() {
        while (running.get() && currentCoroutineContext().isActive) {
            val phone = findPairedPhone()
            if (phone == null) {
                delay(RETRY_DELAY_MS)
                continue
            }
            try {
                val controllerSocket = phone.createRfcommSocketToServiceRecord(SPP_UUID)
                socket = controllerSocket
                bluetoothAdapter?.cancelDiscovery()
                controllerSocket.connect()
                if (!controllerSocket.isConnected) {
                    closeSocket()
                    delay(RETRY_DELAY_MS)
                    continue
                }

                connected.set(true)
                outputStream = controllerSocket.outputStream
                preferences.edit().putString(LAST_CONTROLLER_ADDRESS, phone.address).apply()
                withContext(Dispatchers.Main.immediate) {
                    listener.onControllerConnected(phone.name ?: "TapLink controller", phone.address)
                }
                readLoop(controllerSocket)
            } catch (exception: IOException) {
                if (running.get()) Timber.d(exception, "RayNeo Bluetooth controller connection ended")
            } catch (exception: SecurityException) {
                Timber.w(exception, "Missing permission for RayNeo Bluetooth controller")
                running.set(false)
            } finally {
                val wasConnected = connected.getAndSet(false)
                closeSocket()
                if (wasConnected) {
                    withContext(Dispatchers.Main.immediate) { listener.onControllerDisconnected() }
                }
            }
            if (running.get()) delay(RETRY_DELAY_MS)
        }
    }

    @SuppressLint("MissingPermission")
    private fun findPairedPhone(): BluetoothDevice? {
        val devices = bluetoothAdapter?.bondedDevices.orEmpty()
        val savedAddress = preferences.getString(LAST_CONTROLLER_ADDRESS, null)
        devices.firstOrNull { it.address == savedAddress }?.let { return it }
        val likelyPhone = devices.firstOrNull { device ->
            val name = device.name?.lowercase().orEmpty()
            !name.contains("rayneo") &&
                !name.contains("glasses") &&
                !name.contains("watch") &&
                !name.contains("buds") &&
                !name.contains("headphone")
        }
        return likelyPhone ?: devices.firstOrNull()
    }

    private suspend fun readLoop(controllerSocket: BluetoothSocket) {
        val reader = BufferedReader(InputStreamReader(controllerSocket.inputStream), READ_BUFFER_SIZE)
        while (running.get() && connected.get() && currentCoroutineContext().isActive) {
            val line = reader.readLine() ?: return
            handleMessage(line, controllerSocket)
        }
    }

    private suspend fun handleMessage(message: String, controllerSocket: BluetoothSocket) {
        val json = runCatching { JSONObject(message) }.getOrNull() ?: return
        val messageId = json.optString(FIELD_MESSAGE_ID).takeIf { it.isNotEmpty() }
        if (!RayNeoReliableMessageDeduper.shouldDispatch(messageId)) return

        withContext(Dispatchers.Main.immediate) {
            when (json.optString(FIELD_TYPE)) {
                TYPE_HELLO -> listener.onControllerConnected(
                    json.optString(FIELD_NAME, "TapLink controller"),
                    controllerSocket.remoteDevice?.address ?: "bluetooth",
                )
                TYPE_MODE -> parseMode(json.optString(FIELD_MODE))?.let(listener::onControllerModeChanged)
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
                TYPE_TAP -> listener.onControllerTap()
                TYPE_KEY -> listener.onControllerKey(json.optString(FIELD_KEY))
                TYPE_TOUCH -> parseTouchAction(json.optString(FIELD_ACTION))?.let { action ->
                    listener.onControllerTouch(
                        action = action,
                        x = json.optDouble(FIELD_X, 0.5).toFloat().coerceIn(0f, 1f),
                        y = json.optDouble(FIELD_Y, 0.5).toFloat().coerceIn(0f, 1f),
                    )
                }
            }
        }
    }

    private suspend fun send(json: JSONObject) = outputMutex.withLock {
        val output = outputStream ?: return@withLock
        if (!connected.get()) return@withLock
        try {
            output.write((json.toString() + "\n").toByteArray(Charsets.UTF_8))
            output.flush()
        } catch (exception: IOException) {
            connected.set(false)
            Timber.w(exception, "Failed to send RayNeo controller message")
        }
    }

    private fun closeSocket() {
        outputStream = null
        try {
            socket?.close()
        } catch (exception: IOException) {
            Timber.d(exception, "Failed to close RayNeo Bluetooth socket")
        }
        socket = null
    }

    private fun hasConnectPermission(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) ==
            PackageManager.PERMISSION_GRANTED
    }
}

private const val READ_BUFFER_SIZE = 8192
private const val RETRY_DELAY_MS = 2_500L
private const val PREFERENCES_NAME = "TapLinkControllerClient"
private const val LAST_CONTROLLER_ADDRESS = "last_controller_address"
private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
private const val TYPE_HELLO = "hello"
private const val TYPE_MODE = "mode"
private const val TYPE_TRACKPAD = "trackpad"
private const val TYPE_AIR_MOUSE = "airMouse"
private const val TYPE_SCROLL = "scroll"
private const val TYPE_TAP = "tap"
private const val TYPE_KEY = "key"
private const val TYPE_TOUCH = "touch"
private const val FIELD_TYPE = "type"
private const val FIELD_PORT = "port"
private const val FIELD_ADDRESSES = "addresses"
private const val FIELD_MESSAGE_ID = "messageId"
private const val FIELD_NAME = "name"
private const val FIELD_MODE = "mode"
private const val FIELD_ACTION = "action"
private const val FIELD_DX = "dx"
private const val FIELD_DY = "dy"
private const val FIELD_POINTER_COUNT = "pointerCount"
private const val FIELD_X = "x"
private const val FIELD_Y = "y"
private const val FIELD_SELECT = "select"
private const val FIELD_KEY = "key"
