package com.example.budge.ui.home

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.budge.data.prefs.Prefs
import com.example.budge.data.repository.TransactionRepository
import com.example.budge.model.Transaction
import com.example.budge.model.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

/**
 * Immutable state describing everything the home screen renders: the selected
 * month, that month's transactions, the aggregated expense/income totals, the
 * user's currency symbol, and whether data is still loading.
 */
data class HomeUiState(
    val currentMonth: YearMonth = YearMonth.now(),
    val transactions: List<Transaction> = emptyList(),
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
                dataStore.data.map { it[Prefs.currencySymbolKey] ?: "$" },
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

        fun deleteTransaction(id: Long) {
            // Runs on Room's dispatcher via the suspend DAO; the Flow-backed
            // uiState automatically re-emits the updated transaction list.
            viewModelScope.launch {
                transactionRepository.deleteById(id)
            }
        }
    }
