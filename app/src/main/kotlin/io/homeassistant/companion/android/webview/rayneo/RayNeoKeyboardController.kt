package io.homeassistant.companion.android.webview.rayneo

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

internal data class RayNeoKeyboardKey(val label: String, val action: RayNeoKeyboardAction, val weight: Float = 1f)

internal data class RayNeoKeyboardState(
    val rows: List<List<RayNeoKeyboardKey>>,
    val selectedRow: Int,
    val selectedColumn: Int,
)

internal interface RayNeoKeyboardListener {
    fun onKeyboardAction(action: RayNeoKeyboardAction)
}

/** D-pad keyboard model adapted from TapLink's AR keyboard. */
internal class RayNeoKeyboardController(private val listener: RayNeoKeyboardListener) {
    private var uppercase = true
    private var symbols = false
    private val mutableState = mutableStateOf(buildState(selectedRow = 0, selectedColumn = 1))
    val state: State<RayNeoKeyboardState> = mutableState

    fun move(horizontal: Int, vertical: Int) {
        val current = mutableState.value
        val row = (current.selectedRow + vertical).coerceIn(0, current.rows.lastIndex)
        val column = if (row == current.selectedRow) {
            (current.selectedColumn + horizontal).coerceIn(0, current.rows[row].lastIndex)
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
            RayNeoKeyboardAction.ToggleCase -> {
                uppercase = !uppercase
                rebuild()
            }
            RayNeoKeyboardAction.ToggleSymbols -> {
                symbols = !symbols
                rebuild()
            }
            else -> listener.onKeyboardAction(action)
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
        listOf(special(if (uppercase) "abc" else "ABC", RayNeoKeyboardAction.ToggleCase)) +
            textKeys("ZXCVBNM") + listOf(text("."), text("/")),
        listOf(
            special("Clear", RayNeoKeyboardAction.Clear),
            special("123", RayNeoKeyboardAction.ToggleSymbols),
            text(" ", label = "Space", weight = 3f),
            special("←", RayNeoKeyboardAction.MoveLeft),
            special("→", RayNeoKeyboardAction.MoveRight),
            special("Enter", RayNeoKeyboardAction.Enter, weight = 1.5f),
        ),
    )

    private fun symbolRows(): List<List<RayNeoKeyboardKey>> = listOf(
        listOf(special("Hide", RayNeoKeyboardAction.Hide)) + textKeys("1234567890") +
            special("⌫", RayNeoKeyboardAction.Backspace),
        listOf("@", "#", "$", "_", "&", "-", "+", "(", ")").map(::text),
        listOf("*", "'", ":", ";", "!", "?", "<", ">", "/").map(::text),
        listOf(
            special("Clear", RayNeoKeyboardAction.Clear),
            special("ABC", RayNeoKeyboardAction.ToggleSymbols),
            text(" ", label = "Space", weight = 3f),
            special("←", RayNeoKeyboardAction.MoveLeft),
            special("→", RayNeoKeyboardAction.MoveRight),
            special("Enter", RayNeoKeyboardAction.Enter, weight = 1.5f),
        ),
    )

    private fun textKeys(characters: String): List<RayNeoKeyboardKey> = characters.map {
        val value = if (uppercase && !symbols) it.uppercase() else it.lowercase()
        text(value)
    }

    private fun text(value: String, label: String = value, weight: Float = 1f) =
        RayNeoKeyboardKey(label, RayNeoKeyboardAction.Text(value), weight)

    private fun special(label: String, action: RayNeoKeyboardAction, weight: Float = 1f) =
        RayNeoKeyboardKey(label, action, weight)
}
