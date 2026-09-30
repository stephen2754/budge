package com.example.budge.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** The preferences file the app's own settings live in. */
private const val STORE_NAME = "settings"

private val lock = Any()
private var instance: DataStore<Preferences>? = null

/**
 * The app settings store, created once per process.
 *
 * This is written out rather than left to the `preferencesDataStore` delegate because the
 * delegate cannot be given a corruption handler, and a corrupted preferences file with no
 * handler is not a recoverable state: DataStore reports the damage by throwing from `data`,
 * `data` is read on the startup path and collected in composition, and the file stays
 * damaged — so every launch dies the same way and the only way back into the app is to
 * clear its data from system settings. Replacing the file with empty preferences loses the
 * theme, language and currency choice, which is a far smaller loss than the app.
 *
 * [AppModule] hands this same instance to Hilt. It is reachable without an injected
 * instance because `MainActivity` has to read the stored language before it can resolve any
 * resource, which is earlier than injection is available.
 */
fun settingsDataStore(context: Context): DataStore<Preferences> =
    instance ?: synchronized(lock) {
        instance ?: PreferenceDataStoreFactory
            .create(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                produceFile = { context.applicationContext.preferencesDataStoreFile(STORE_NAME) },
            ).also { instance = it }
    }

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    /**
     * Provides the app settings store ("settings" preferences file). Scoped to
     * the singleton so all layers read/write the same DataStore instance.
     */
    @Provides
    @Singleton
    fun provideDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = settingsDataStore(context)
}
