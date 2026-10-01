package com.example.budge.ui.entry

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.budge.R
import com.example.budge.data.prefs.Prefs
import com.example.budge.data.prefs.Currencies
import com.example.budge.data.prefs.deviceLocale
import com.example.budge.data.prefs.safeData
import com.example.budge.data.repository.CategoryRepository
import com.example.budge.data.repository.TransactionRepository
import com.example.budge.model.Category
import com.example.budge.model.Transaction
import com.example.budge.model.TransactionType
import com.example.budge.ui.centsToEditableAmount
import com.example.budge.ui.parseAmountToCents
import com.example.budge.ui.sanitizeAmountInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** How long an empty category table is given to fill itself in before it is repaired. */
private const val SEED_GRACE_MILLIS = 750L

/**
 * Form state for the add/edit screen. [categories] resolves to the expense or
 * income list depending on the selected [type]. [saveSuccess] is a one-shot
 * event that the screen consumes to trigger navigation back.
 */
data class EntryUiState(
    val type: TransactionType = TransactionType.EXPENSE,
    val amount: String = "",
    val selectedCategoryId: Long = 0L,
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val currencySymbol: String = "$",
    val expenseCategories: List<Category> = emptyList(),
    val incomeCategories: List<Category> = emptyList(),
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val error: String? = null,
) {
    val categories: List<Category>
        get() = if (type == TransactionType.EXPENSE) expenseCategories else incomeCategories
}

/**
 * Backing [EntryViewModel] for the add/edit transaction form.
 *
 * The form is driven by user input rather than the database, so state is held
 * in a plain [StateFlow]; Room writes and DataStore reads run in the
 * viewModelScope and the results are folded back into [uiState].
 */
@HiltViewModel
class EntryViewModel
    @Inject
    constructor(
        application: Application,
        private val transactionRepository: TransactionRepository,
        private val categoryRepository: CategoryRepository,
        private val dataStore: DataStore<Preferences>,
    ) : AndroidViewModel(application) {
        private val _transactionId = MutableStateFlow(-1L)
        private val _uiState = MutableStateFlow(EntryUiState())
        val uiState: StateFlow<EntryUiState> = _uiState.asStateFlow()

        // Creation time of the transaction being edited, so saving an edit keeps
        // the original audit timestamp instead of overwriting it with "now".
        private var loadedCreatedAt: Long? = null

        /** Whether the collectors below have been started for this instance. */
        private var collectorsStarted = false

        /** The target the form has already been prepared for, or null before the first call. */
        private var initializedId: Long? = null

        fun init(transactionId: Long) {
            // The screen calls init() from a LaunchedEffect, so it runs again whenever the
            // form is composed — including during the exit animation, when the navigation
            // state has already cleared the target. Re-initialising then blanked the form
            // while it was still fading out and cost a categories query and a preference
            // read per close; after a configuration change it threw away what the user had
            // typed, because the view model survived but the reset did not spare it. A
            // *different* target still resets the form; the same one coming back is the
            // same form.
            if (initializedId == transactionId) return
            initializedId = transactionId
            // Reset the form; if editing (id > 0) populate it from the stored
            // transaction, and always load the category lists and currency.
            _transactionId.value = transactionId
            loadedCreatedAt = null
            _uiState.update { EntryUiState(isEditing = transactionId > 0) }
            if (transactionId > 0) {
                loadTransaction(transactionId)
            }

            // The reset above empties the form, and the collectors started below run once
            // and then only emit when the database or the preferences are *written*. A
            // second open of the form would therefore show no categories at all until
            // something happened to write to the database — the first open worked, later
            // ones did not. Reading both once per open is what makes every open work; the
            // collectors are what keep them current while the form is up.
            viewModelScope.launch { applyCategories(categoryRepository.getAllOnce()) }
            viewModelScope.launch { applyCurrencySymbol(dataStore.safeData().first()[Prefs.currencySymbolKey]) }

            // The screen calls init() from a LaunchedEffect, which re-runs every time the
            // form is composed — including during the exit animation, when the target id
            // has already flipped back to "adding". Starting one category collector and one
            // preference collector per call left an activity-scoped view model holding a
            // new pair for every add and edit, each one re-querying on every write.
            if (!collectorsStarted) {
                collectorsStarted = true
                loadAllCategories()
                loadCurrency()
            }
        }

        /**
         * Puts [allCategories] into the form, selecting the first of the current type when
         * nothing is selected yet. Shared by the one-shot read on open and by the collector.
         */
        private fun applyCategories(allCategories: List<Category>) {
            if (allCategories.isEmpty()) return
            val expense = allCategories.filter { it.type == TransactionType.EXPENSE }
            val income = allCategories.filter { it.type == TransactionType.INCOME }
            _uiState.update {
                it.copy(
                    expenseCategories = expense,
                    incomeCategories = income,
                )
            }
            val currentList = if (_uiState.value.type == TransactionType.EXPENSE) expense else income
            if (_uiState.value.selectedCategoryId == 0L && currentList.isNotEmpty()) {
                _uiState.update { it.copy(selectedCategoryId = currentList.first().id) }
            }
        }

        private fun applyCurrencySymbol(symbol: String?) {
            _uiState.update { it.copy(currencySymbol = symbol ?: Currencies.DOLLAR) }
        }

        private fun loadCurrency() {
            // Read the currency symbol preference from DataStore once it is
            // available and merge it into the form state.
            viewModelScope.launch {
                dataStore.safeData()
                    .map { it[Prefs.currencySymbolKey] }
                    .collect { symbol -> applyCurrencySymbol(symbol) }
            }
        }

        private fun loadAllCategories() {
            // Collects for the life of the view model and auto-selects the first category
            // of the chosen type.
            //
            // An empty table has two causes. On first launch it is the seed still landing,
            // which fills itself in. Otherwise the user has deleted every category — only
            // one that transactions still point at is protected — and the form can no
            // longer save anything at all, because a transaction must carry a category.
            // After a grace period for the first case, a default of the type being recorded
            // is put back, which the collector then picks up like any other row.
            viewModelScope.launch {
                var repaired = false
                categoryRepository.getAll().collect { allCategories ->
                    if (allCategories.isEmpty()) {
                        if (!repaired) {
                            repaired = true
                            delay(SEED_GRACE_MILLIS)
                            categoryRepository.ensureCategoryOfType(_uiState.value.type, deviceLocale())
                        }
                        return@collect
                    }
                    applyCategories(allCategories)
                }
            }
        }

        private fun loadTransaction(id: Long) {
            // Populate the form fields from the persisted transaction; the stored
            // amount in cents is rendered back as an editable decimal string.
            viewModelScope.launch {
                val transaction = transactionRepository.getById(id)
                if (transaction != null) {
                    loadedCreatedAt = transaction.createdAt
                    _uiState.update {
                        it.copy(
                            type = transaction.type,
                            amount = centsToEditableAmount(transaction.amount),
                            selectedCategoryId = transaction.categoryId,
                            note = transaction.note ?: "",
                            timestamp = transaction.timestamp,
                        )
                    }
                }
            }
        }

        /**
         * Switches between expense and income. The selected category is kept
         * when it exists in the newly shown list, otherwise it falls back to the
         * list's first category (or none when the list is empty).
         */
        fun updateType(type: TransactionType) {
            _uiState.update { state ->
                val newCategories = if (type == TransactionType.EXPENSE) state.expenseCategories else state.incomeCategories
                state.copy(
                    type = type,
                    selectedCategoryId =
                        if (newCategories.any { it.id == state.selectedCategoryId }) {
                            state.selectedCategoryId
                        } else {
                            newCategories.firstOrNull()?.id ?: 0L
                        },
                )
            }
            // The other direction can be empty on its own: the collector above only
            // repairs a table that has no categories at all, and switching to a type whose
            // rows were all deleted would leave the form with nothing to select.
            if (_uiState.value.categories.isEmpty()) {
                viewModelScope.launch {
                    categoryRepository.ensureCategoryOfType(type, deviceLocale())
                }
            }
        }

        /**
         * Sanitizes the amount as the user types: only digits and a single
         * decimal point are kept, with at most two decimal places.
         */
        fun updateAmount(amount: String) {
            _uiState.update { it.copy(amount = sanitizeAmountInput(amount)) }
        }

        fun updateCategoryId(categoryId: Long) {
            _uiState.update { it.copy(selectedCategoryId = categoryId) }
        }

        fun updateNote(note: String) {
            _uiState.update { it.copy(note = note) }
        }

        fun updateTimestamp(timestamp: Long) {
            _uiState.update { it.copy(timestamp = timestamp) }
        }

        /**
         * Clears the one-shot save-success flag so a recomposition or a
         * configuration change cannot navigate back more than once.
         */
        fun consumeSaveSuccess() {
            _uiState.update { it.copy(saveSuccess = false) }
        }

        /**
         * Clears the one-shot error once it has been shown, so the same problem
         * occurring twice (e.g. two failed saves in a row) is reported twice
         * instead of being swallowed as an unchanged value.
         */
        fun clearError() {
            _uiState.update { it.copy(error = null) }
        }

        /**
         * Validates the form, converts the amount to cents, and inserts or
         * updates the transaction in Room. Sets [EntryUiState.saveSuccess] on
         * success (consumed by the screen) or [EntryUiState.error] on failure.
         *
         * Re-entrant calls are ignored while a save is in flight, so tapping the
         * toolbar action twice cannot insert the same transaction twice.
         */
        fun save() {
            val state = _uiState.value
            if (state.isSaving) return

            val amountCents = parseAmountToCents(state.amount)
            if (amountCents == null) {
                _uiState.update { it.copy(error = getApplication<Application>().getString(R.string.entry_invalid_amount)) }
                return
            }
            if (state.selectedCategoryId == 0L) {
                _uiState.update { it.copy(error = getApplication<Application>().getString(R.string.entry_select_category)) }
                return
            }

            _uiState.update { it.copy(isSaving = true, error = null) }

            viewModelScope.launch {
                try {
                    val now = System.currentTimeMillis()
                    val transaction =
                        Transaction(
                            id = if (state.isEditing) _transactionId.value else 0,
                            type = state.type,
                            amount = amountCents,
                            categoryId = state.selectedCategoryId,
                            note = state.note.ifBlank { null },
                            timestamp = state.timestamp,
                            // An edit keeps the original creation time; only
                            // updatedAt moves.
                            createdAt = loadedCreatedAt ?: now,
                            updatedAt = now,
                        )
                    if (state.isEditing) {
                        transactionRepository.update(transaction)
                    } else {
                        transactionRepository.insert(transaction)
                    }
                    _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            error = getApplication<Application>().getString(R.string.entry_save_failed),
                        )
                    }
                }
            }
        }
    }
