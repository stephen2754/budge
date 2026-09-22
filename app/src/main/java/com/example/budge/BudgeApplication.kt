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
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
            val preferences = dataStore.data.first()
            val deviceLocale = deviceLocale()

            // First launch only. The seeded categories are named in the device language
            // of that moment and never renamed again: from here on they belong to the
            // user, who is free to edit or delete them.
            if (!preferences.contains(Prefs.categoriesSeededKey)) {
                categoryRepository.seedDefaultsIfEmpty(deviceLocale)
                dataStore.edit { it[Prefs.categoriesSeededKey] = true }
            }

            // First launch only — a stored symbol is never overwritten. Chinese and
            // Japanese → ¥, Russian → ₽, other European → €, everything else → $.
            val storedSymbol = preferences[Prefs.currencySymbolKey]
            if (storedSymbol == null) {
                dataStore.edit { it[Prefs.currencySymbolKey] = defaultCurrencyFor(deviceLocale) }
            } else if (storedSymbol !in Currencies.choices) {
                // A symbol an older build offered and this one does not ("CNY", "¥CNY").
                // Without this the amount would show a token the picker cannot produce,
                // and the user could not get back to it.
                dataStore.edit { it[Prefs.currencySymbolKey] = defaultCurrencyFor(deviceLocale) }
            }
        }
    }
}
