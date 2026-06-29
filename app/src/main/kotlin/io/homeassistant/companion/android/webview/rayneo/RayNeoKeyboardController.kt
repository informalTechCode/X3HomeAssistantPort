package io.homeassistant.companion.android.webview.rayneo

import android.os.SystemClock
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf

sealed interface RayNeoKeyboardAction {
    data class Text(val value: String) : RayNeoKeyboardAction

    data object Backspace : RayNeoKeyboardAction

    data object Clear : RayNeoKeyboardAction

    data object Enter : RayNeoKeyboardAction

    data object Hide : RayNeoKeyboardAction

    data object MoveLeft : RayNeoKeyboardAction

    data object MoveRight : RayNeoKeyboardAction

    data object ToggleCase : RayNeoKeyboardAction

    data object ToggleSymbols : RayNeoKeyboardAction
}

internal data class RayNeoKeyboardKey(
    val label: String,
    val action: RayNeoKeyboardAction,
    val weight: Float = 1f,
    val active: Boolean = false,
)

internal data class RayNeoKeyboardState(
    val rows: List<List<RayNeoKeyboardKey>>,
    val selectedRow: Int,
    val selectedColumn: Int,
)

internal interface RayNeoKeyboardListener {
    fun onKeyboardAction(action: RayNeoKeyboardAction)
}

/** TapLink X3 AR keyboard state and navigation model. */
internal class RayNeoKeyboardController(private val listener: RayNeoKeyboardListener) {
    private var uppercase = true
    private var capsLocked = false
    private var symbols = false
    private var lastShiftPressTime = 0L
    private var lastEmittedCharacter: String? = null
    private val mutableState = mutableStateOf(buildState(selectedRow = 0, selectedColumn = 1))
    val state: State<RayNeoKeyboardState> = mutableState

    fun move(horizontal: Int, vertical: Int) {
        val current = mutableState.value
        val row = (current.selectedRow + vertical).mod(current.rows.size)
        val column = if (row == current.selectedRow) {
            (current.selectedColumn + horizontal).mod(current.rows[row].size)
        } else {
            current.selectedColumn.coerceIn(0, current.rows[row].lastIndex)
        }
        mutableState.value = current.copy(selectedRow = row, selectedColumn = column)
    }

    fun pressSelected() {
        val current = mutableState.value
        press(current.rows[current.selectedRow][current.selectedColumn].action)
    }

    fun press(action: RayNeoKeyboardAction) {
        when (action) {
            RayNeoKeyboardAction.ToggleCase -> toggleCase()
            RayNeoKeyboardAction.ToggleSymbols -> {
                symbols = !symbols
                rebuild()
            }
            is RayNeoKeyboardAction.Text -> emitText(action)
            RayNeoKeyboardAction.Clear -> {
                lastEmittedCharacter = null
                listener.onKeyboardAction(action)
            }
            else -> listener.onKeyboardAction(action)
        }
    }

    private fun toggleCase() {
        if (symbols) return
        val now = SystemClock.uptimeMillis()
        if (capsLocked) {
            capsLocked = false
            uppercase = false
        } else if (now - lastShiftPressTime < DOUBLE_TAP_SHIFT_TIMEOUT_MS) {
            capsLocked = true
            uppercase = true
        } else {
            uppercase = !uppercase
        }
        lastShiftPressTime = now
        rebuild()
    }

    private fun emitText(action: RayNeoKeyboardAction.Text) {
        listener.onKeyboardAction(action)
        if (action.value == " ") {
            if (lastEmittedCharacter in PUNCTUATION_CHARACTERS && !uppercase && !capsLocked) {
                uppercase = true
                rebuild()
            }
            lastEmittedCharacter = action.value
            return
        }

        lastEmittedCharacter = action.value
        if (uppercase && !capsLocked && !symbols) {
            uppercase = false
            rebuild()
        }
    }

    private fun rebuild() {
        val current = mutableState.value
        mutableState.value = buildState(current.selectedRow, current.selectedColumn)
    }

    private fun buildState(selectedRow: Int, selectedColumn: Int): RayNeoKeyboardState {
        val rows = if (symbols) symbolRows() else letterRows()
        val safeRow = selectedRow.coerceIn(0, rows.lastIndex)
        return RayNeoKeyboardState(
            rows = rows,
            selectedRow = safeRow,
            selectedColumn = selectedColumn.coerceIn(0, rows[safeRow].lastIndex),
        )
    }

    private fun letterRows(): List<List<RayNeoKeyboardKey>> = listOf(
        listOf(special("Hide", RayNeoKeyboardAction.Hide)) + textKeys("QWERTYUIOP") +
            special("⌫", RayNeoKeyboardAction.Backspace),
        textKeys("ASDFGHJKL"),
        listOf(
            special(
                label = when {
                    capsLocked -> "CAPS"
                    uppercase -> "ABC"
                    else -> "abc"
                },
                action = RayNeoKeyboardAction.ToggleCase,
                active = capsLocked,
            ),
        ) +
            textKeys("ZXCVBNM") + listOf(text("."), text("/")),
        listOf(
            special("Clear", RayNeoKeyboardAction.Clear),
            special("123", RayNeoKeyboardAction.ToggleSymbols),
            text(" ", label = "Space", weight = 3f),
            text("@"),
            special("Enter", RayNeoKeyboardAction.Enter, weight = 1.5f),
        ),
    )

    private fun symbolRows(): List<List<RayNeoKeyboardKey>> = listOf(
        listOf(special("Hide", RayNeoKeyboardAction.Hide)) + textKeys("1234567890") +
            special("⌫", RayNeoKeyboardAction.Backspace),
        listOf("@", "#", "$", "_", "&", "-", "+", "(", ")").map(::text),
        listOf("*", "'", ":", ";", "!", "?").map(::text) +
            listOf(
                special("<", RayNeoKeyboardAction.MoveLeft),
                special(">", RayNeoKeyboardAction.MoveRight),
            ),
        listOf(
            special("Clear", RayNeoKeyboardAction.Clear),
            special("ABC", RayNeoKeyboardAction.ToggleSymbols),
            text(" ", label = "Space", weight = 3f),
            special("◀", RayNeoKeyboardAction.MoveLeft),
            special("Enter", RayNeoKeyboardAction.Enter, weight = 1.5f),
        ),
    )

    private fun textKeys(characters: String): List<RayNeoKeyboardKey> = characters.map {
        val value = if (uppercase && !symbols) it.uppercase() else it.lowercase()
        text(value)
    }

    private fun text(value: String, label: String = value, weight: Float = 1f) =
        RayNeoKeyboardKey(label, RayNeoKeyboardAction.Text(value), weight)

    private fun special(label: String, action: RayNeoKeyboardAction, weight: Float = 1f, active: Boolean = false) =
        RayNeoKeyboardKey(label, action, weight, active)
}

private val PUNCTUATION_CHARACTERS = setOf(".", "?", "!")
private const val DOUBLE_TAP_SHIFT_TIMEOUT_MS = 300L
