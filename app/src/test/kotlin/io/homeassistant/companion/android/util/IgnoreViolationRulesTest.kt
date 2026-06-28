package io.homeassistant.companion.android.util

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class IgnoreViolationRulesTest {
    @ParameterizedTest
    @ValueSource(strings = ["readLocales", "persistLocales"])
    fun `Given AppCompat locale storage violation when matching stack then it is ignored`(storageMethod: String) {
        assertTrue(isAppCompatLocaleStorageStack(appCompatLocaleStack(storageMethod)))
    }

    @Test
    fun `Given unrelated locale storage read when matching stack then it is not ignored`() {
        assertFalse(isAppCompatLocaleStorageStack(appCompatLocaleStack("unrelatedRead")))
    }

    private fun appCompatLocaleStack(storageMethod: String) = arrayOf(
        StackTraceElement("androidx.core.app.AppLocalesStorageHelper", storageMethod, "AppLocalesStorageHelper.java", 63),
        StackTraceElement(
            "androidx.appcompat.app.AppCompatDelegate",
            "syncRequestedAndStoredLocales",
            "AppCompatDelegate.java",
            981,
        ),
        StackTraceElement(
            "androidx.appcompat.app.AppCompatDelegateImpl",
            "attachBaseContext2",
            "AppCompatDelegateImpl.java",
            403,
        ),
    )
}
