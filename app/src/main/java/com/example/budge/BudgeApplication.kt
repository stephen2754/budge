package com.example.budge

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.example.budge.data.prefs.Currencies
import com.example.budge.data.prefs.Prefs
import com.example.budge.data.prefs.defaultCurrencyFor
import com.example.budge.data.prefs.deviceLocale
import com.example.budge.data.repository.CategoryRepository
import java.io.IOException
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/**
 * Application entry point. Does the first-launch work: seed the categories, pick the
 * starting currency.
 *
 * It all runs on [applicationScope] rather than in `onCreate`. This opens the database
 * and reads and writes DataStore, which is slow enough on the main thread to be felt at
 * every cold start. The screens collect Room `Flow`s, so they fill in by themselves as
 * soon as the rows land.
 */
@HiltAndroidApp
class BudgeApplication : Application() {
    @Inject
    lateinit var categoryRepository: CategoryRepository

    @Inject
    lateinit var dataStore: DataStore<Preferences>

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            // A read that fails is not a empty store, and this work writes defaults for
            // everything it cannot find — so treating a failure as "nothing is stored"
            // would overwrite the user's currency choice with the locale default. The next
            // launch tries again instead.
            val preferences =
                try {
                    dataStore.data.first()
                } catch (_: IOException) {
                    return@launch
                }
            val deviceLocale = deviceLocale()

            // Everything below is best-effort first-launch work with no reader waiting on
            // it, and none of it may turn into a crash before the first screen. The first
            // database open happens here, on a fresh install or a damaged file, and a write
            // can fail on a full disk; either one would kill the process every launch while
            // the condition lasts. The screens report what they can and cannot read.
            try {
                // First launch only. The seeded categories are named in the device language
                // of that moment and never renamed again: from here on they belong to the
                // user, who is free to edit or delete them.
                if (!preferences.contains(Prefs.categoriesSeededKey)) {
                    categoryRepository.seedDefaultsIfEmpty(deviceLocale)
                    dataStore.edit { it[Prefs.categoriesSeededKey] = true }
                }

                // First launch only — a stored symbol is never overwritten. Chinese and
                // Japanese → ¥, Russian → ₽, other European → €, everything else → $.
                //
                // Each decision is made *inside* the write it belongs to. The snapshot
                // above was taken before a slow first database open, and Settings can be
                // used while it runs, so a check against that snapshot would overwrite a
                // sign the user had just chosen.
                val storedSymbol = preferences[Prefs.currencySymbolKey]
                if (storedSymbol == null) {
                    dataStore.edit { stored ->
                        if (stored[Prefs.currencySymbolKey] == null) {
                            stored[Prefs.currencySymbolKey] = defaultCurrencyFor(deviceLocale)
                        }
                    }
                } else if (storedSymbol !in Currencies.choices) {
                    // A symbol an older build offered and this one does not ("CNY", "¥CNY").
                    // Without this the amount would show a token the picker cannot produce,
                    // and the user could not get back to it. Only if it is still there: a
                    // token the user has since replaced must not be "repaired" back.
                    dataStore.edit { stored ->
                        if (stored[Prefs.currencySymbolKey] == storedSymbol) {
                            stored[Prefs.currencySymbolKey] = defaultCurrencyFor(deviceLocale)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Left for the next launch.
            }
        }
    }
}
