package com.example.budge.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.IOException

/**
 * Tests for reading preferences when the settings file cannot be read.
 *
 * DataStore reports an unreadable file by throwing from its flow, and every reader in this
 * app collects that flow either in a view model or straight from composition, where an
 * exception is a crash the reader cannot get past. A failed read has to come back as
 * "nothing is stored" instead; anything that is not an IO problem still has to surface,
 * because swallowing a programming error is how a bug becomes invisible.
 */
class SafePreferencesTest {
    /** A store whose reads fail the way DataStore's do. */
    private class FailingStore(
        private val failure: Throwable,
    ) : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw failure }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = throw failure
    }

    @Test
    fun `an unreadable file reads as no preferences`() = runBlocking {
        val store = FailingStore(IOException("the file is gone"))

        assertEquals(emptyPreferences(), store.safeData().first())
    }

    @Test
    fun `a failure that is not an IO problem still surfaces`() = runBlocking {
        val store = FailingStore(IllegalStateException("not an IO failure"))

        val thrown =
            try {
                store.safeData().first()
                null
            } catch (e: IllegalStateException) {
                e
            }

        assertNotNull("a programming error must not be swallowed as an empty store", thrown)
    }
}
