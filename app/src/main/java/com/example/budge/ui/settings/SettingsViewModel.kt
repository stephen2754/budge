package com.example.budge.ui.settings

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.budge.R
import com.example.budge.data.backup.BackupData
import com.example.budge.data.backup.decodeBackup
import com.example.budge.data.backup.encodeBackup
import com.example.budge.data.prefs.Prefs
import com.example.budge.data.prefs.appLocaleFor
import com.example.budge.data.repository.BackupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Screen state for [SettingsScreen]. `message` is a one-shot field: the screen
 * shows it in a snackbar and calls [SettingsViewModel.clearMessage].
 * `showImportConfirm` drives the "import will overwrite" confirmation dialog.
 */
data class SettingsUiState(
    val currencySymbol: String = "$",
    val theme: String = Prefs.FOLLOW_SYSTEM,
    val language: String = Prefs.FOLLOW_SYSTEM,
    val message: String? = null,
    val showImportConfirm: Boolean = false,
)

/**
 * Backs the settings screen. Preferences are read/written through DataStore; the
 * database side of export/import/clear lives in [BackupRepository] so the
 * destructive path is not spread through the UI layer.
 *
 * Import is deliberately two-phase: the picked file is validated and parsed
 * first, and the overwrite only runs after the user confirms it.
 */
@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        application: Application,
        private val dataStore: DataStore<Preferences>,
        private val backupRepository: BackupRepository,
    ) : AndroidViewModel(application) {
        private val _uiState = MutableStateFlow(SettingsUiState())
        val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

        // Already-parsed backup awaiting confirmation. Holding the parsed document
        // rather than its URI means the bytes cannot change between "validated" and
        // "confirmed", so what the user approves is what gets restored.
        private var pendingImport: BackupData? = null

        init {
            // Single source of truth: mirror the DataStore preferences into
            // _uiState so the screen always reflects the persisted values.
            viewModelScope.launch {
                dataStore.data.collect { preferences ->
                    _uiState.update {
                        it.copy(
                            currencySymbol = preferences[Prefs.currencySymbolKey] ?: "$",
                            theme = preferences[Prefs.themeKey] ?: Prefs.FOLLOW_SYSTEM,
                            language = preferences[Prefs.languageKey] ?: Prefs.FOLLOW_SYSTEM,
                        )
                    }
                }
            }
        }

        fun updateCurrencySymbol(symbol: String) {
            viewModelScope.launch {
                dataStore.edit { preferences ->
                    preferences[Prefs.currencySymbolKey] = symbol
                }
            }
        }

        /** Cycles theme system -> light -> dark so one tap moves to the next. */
        fun cycleTheme() {
            val nextTheme =
                when (_uiState.value.theme) {
                    Prefs.FOLLOW_SYSTEM -> Prefs.LIGHT
                    Prefs.LIGHT -> Prefs.DARK
                    else -> Prefs.FOLLOW_SYSTEM
                }
            viewModelScope.launch {
                dataStore.edit { preferences ->
                    preferences[Prefs.themeKey] = nextTheme
                }
            }
        }

        /**
         * Persists the chosen language. Category names are deliberately left alone:
         * the built-ins are seeded in the device language on first launch and are
         * ordinary, user-editable categories from then on, so switching the UI
         * language must not rewrite names the user may have chosen.
         */
        fun updateLanguage(language: String) {
            viewModelScope.launch {
                dataStore.edit { preferences ->
                    preferences[Prefs.languageKey] = language
                }
            }
        }

        /**
         * Wipes the database and re-seeds the built-in categories in the current
         * language. Preferences (currency, theme, language) are left alone.
         */
        fun clearAllRecords() {
            viewModelScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        backupRepository.clearAll(appLocaleFor(_uiState.value.language))
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    _uiState.update { it.copy(message = string(R.string.settings_clear_all_failed)) }
                }
            }
        }

        /**
         * Serializes the database as [BackupData] JSON and streams it to the
         * user-picked URI. Database reads and the file write happen off the main
         * thread; the result is reported as a one-shot message in the UI state.
         */
        fun exportRecords(
            context: Context,
            uri: Uri,
        ) {
            viewModelScope.launch {
                val message =
                    try {
                        val json = withContext(Dispatchers.IO) { encodeBackup(backupRepository.export()) }
                        val written =
                            withContext(Dispatchers.IO) {
                                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                                    outputStream.write(json.toByteArray())
                                    true
                                } ?: false
                            }
                        string(if (written) R.string.settings_export_success else R.string.settings_export_failed)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        string(R.string.settings_export_failed)
                    }
                _uiState.update { it.copy(message = message) }
            }
        }

        /**
         * Phase 1 of import: read and validate the picked file. If it really is a
         * backup, remember it and raise the confirmation dialog instead of touching
         * the database yet.
         */
        fun onImportPicked(
            context: Context,
            uri: Uri,
        ) {
            viewModelScope.launch {
                val data = withContext(Dispatchers.IO) { readBackup(context, uri) }
                if (data == null) {
                    _uiState.update { it.copy(message = string(R.string.settings_import_invalid)) }
                    return@launch
                }
                pendingImport = data
                _uiState.update { it.copy(showImportConfirm = true) }
            }
        }

        /**
         * Phase 2 of import: the user confirmed the overwrite. Runs the destructive
         * restore and reports what actually happened, including any records that had
         * to be re-homed onto a different category.
         */
        fun confirmImport() {
            val data = pendingImport ?: return
            pendingImport = null
            viewModelScope.launch {
                _uiState.update { it.copy(showImportConfirm = false) }
                val report =
                    try {
                        withContext(Dispatchers.IO) {
                            backupRepository.import(data, appLocaleFor(_uiState.value.language))
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                val message =
                    when {
                        report == null -> string(R.string.settings_import_failed)
                        report.reassigned > 0 -> string(R.string.settings_import_reassigned, report.reassigned)
                        else -> string(R.string.settings_import_success)
                    }
                _uiState.update { it.copy(message = message) }
            }
        }

        fun cancelImport() {
            pendingImport = null
            _uiState.update { it.copy(showImportConfirm = false) }
        }

        fun clearMessage() {
            _uiState.update { it.copy(message = null) }
        }

        /**
         * Reads and validates the picked file, returning null unless it is a backup
         * document.
         *
         * This check is the guard that matters: Gson deserializes *any* JSON object
         * — `{}`, `{"unrelated":1}` — into an empty record set, and an import that
         * got that far wiped the database and restored nothing.
         */
        private fun readBackup(
            context: Context,
            uri: Uri,
        ): BackupData? =
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    decodeBackup(input.bufferedReader().use { it.readText() })
                }
            } catch (_: Exception) {
                null
            }

        private fun string(
            @StringRes id: Int,
            vararg args: Any,
        ): String = getApplication<Application>().getString(id, *args)
    }
