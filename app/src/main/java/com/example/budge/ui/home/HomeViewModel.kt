package com.example.budge.ui.home

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.budge.data.prefs.Prefs
import com.example.budge.data.prefs.safeData
import com.example.budge.data.repository.TransactionRepository
import com.example.budge.model.Transaction
import com.example.budge.model.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

/**
 * Immutable state describing everything the home screen renders: the selected
 * month, that month's transactions, the aggregated expense/income totals and the
 * user's currency symbol.
 *
 * [transactions] is null until the first query answers, which is a different thing from an
 * empty list: nothing has been read yet, so the totals are not zero — they are unknown. The
 * screen draws a placeholder for the first frame or two rather than a balance of zero in a
 * currency the reader may not have chosen.
 */
data class HomeUiState(
    val currentMonth: YearMonth = YearMonth.now(),
    val transactions: List<Transaction>? = null,
    val totalExpense: Long = 0L,
    val totalIncome: Long = 0L,
    val currencySymbol: String = "$",
)

/**
 * Backing [HomeViewModel] for the home screen.
 *
 * Combines the currently displayed month with the user's currency symbol from
 * DataStore, then subscribes to the Room queries (monthly summary plus the
 * month's transactions) via [flatMapLatest]. The resulting Room Flow is exposed
 * to Compose as a [StateFlow] that stops when no subscriber is active.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val transactionRepository: TransactionRepository,
        private val dataStore: DataStore<Preferences>,
    ) : ViewModel() {
        private val _currentMonth = MutableStateFlow(YearMonth.now())

        val uiState: StateFlow<HomeUiState> =
            combine(
                _currentMonth,
                // The symbol is a write-only-through-Settings value, so a preference write
                // for anything else must not cancel and restart the two Room queries below.
                dataStore
                    .safeData()
                    .map { it[Prefs.currencySymbolKey] ?: "$" }
                    .distinctUntilChanged(),
            ) { month, symbol ->
                Pair(month, symbol)
            }.flatMapLatest { (month, symbol) ->
                // Re-run the Room queries whenever the selected month or the
                // currency symbol changes; flatMapLatest cancels the previous
                // collection so stale data never reaches the UI.
                val (startTime, endTime) = TransactionRepository.yearMonthToRange(month)
                combine(
                    transactionRepository.getMonthlySummary(month),
                    transactionRepository.getByDateRange(startTime, endTime),
                ) { summary, transactions ->
                    HomeUiState(
                        currentMonth = month,
                        transactions = transactions,
                        totalExpense = summary.totalExpense,
                        totalIncome = summary.totalIncome,
                        currencySymbol = symbol,
                    )
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = HomeUiState(),
            )

        /**
         * Re-reads the month the list is showing.
         *
         * The month is captured when this view model is created and every query below is
         * keyed on it, so a session left open — or backgrounded — across midnight on the
         * first of a month went on showing the previous month until the process was
         * restarted. The screen calls this whenever it comes back to the foreground.
         */
        fun refreshMonth() {
            val today = YearMonth.now()
            if (_currentMonth.value != today) _currentMonth.value = today
        }

        private val _deleteError = MutableStateFlow(false)

        /** True once a delete has failed, until the screen has said so. */
        val deleteError: StateFlow<Boolean> = _deleteError.asStateFlow()

        fun clearDeleteError() {
            _deleteError.value = false
        }

        fun deleteTransaction(id: Long) {
            // Runs on Room's dispatcher via the suspend DAO; the Flow-backed
            // uiState automatically re-emits the updated transaction list.
            viewModelScope.launch {
                try {
                    transactionRepository.deleteById(id)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // The row is still there, which is the truth of it; the reader is told
                    // so rather than left to wonder why the swipe did nothing.
                    _deleteError.value = true
                }
            }
        }
    }
