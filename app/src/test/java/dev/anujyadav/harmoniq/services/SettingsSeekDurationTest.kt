package dev.anujyadav.harmoniq.services

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsSeekDurationTest {
    private val preferences = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("test", Context.MODE_PRIVATE)

    @Test
    fun aValueSavedBeforeTheSplitIsKept() {
        preferences.edit { putInt("from", 10) }
        Settings.copyIfAbsent(preferences, "from", "to")
        assertEquals(10, preferences.getInt("to", 0))
    }

    @Test
    fun nothingSavedStaysUnset() {
        Settings.copyIfAbsent(preferences, "from", "to")
        assertFalse(preferences.contains("to"))
    }

    @Test
    fun anExistingValueIsNotOverwritten() {
        preferences.edit {
            putInt("from", 10)
            putInt("to", 30)
        }
        Settings.copyIfAbsent(preferences, "from", "to")
        assertEquals(30, preferences.getInt("to", 0))
    }
}
