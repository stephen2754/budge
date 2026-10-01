package com.example.budge.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import java.io.IOException

/**
 * The preference flow, with a read failure reported as "nothing is stored" instead of
 * thrown.
 *
 * DataStore documents that `data` throws on an unreadable file, and that callers handle
 * it. Every consumer in this app collects that flow either in a view model or straight
 * from composition, where an exception is a crash the reader cannot get past — a damaged
 * or unreadable settings file would take the app down on every launch. A failed read
 * therefore yields empty preferences, so the app comes up with its defaults, and the next
 * successful write repairs the file.
 *
 * A *corrupt* file is a different case and is handled where the store is built, in
 * `di/AppModule.kt`, by replacing it: corrupt content throws while the flow is being read,
 * which is what [catch] would see, and replacing it is the repair that makes the app usable
 * again rather than merely survivable.
 */
fun DataStore<Preferences>.safeData(): Flow<Preferences> =
    data.catch { cause ->
        if (cause is IOException) emit(emptyPreferences()) else throw cause
    }
