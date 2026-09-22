package com.example.budge.ui.stats

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.budge.data.prefs.Prefs
import com.example.budge.data.repository.BudgetRepository
import com.example.budge.data.repository.TransactionRepository
import com.example.budge.model.Budget
import com.example.budge.model.CategorySummary
import com.example.budge.model.MonthlySummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/**
 * Granularity of the stats view, which determines how a "period" is computed
 * and how previous/next navigation steps (by year, month or day).
 */
enum class StatsPeriod {
    YEARLY,
    MONTHLY,
    DAILY,
}

/**
 * UI state for the stats screen. [currentDate] is the single anchor every period is a
 * view onto, and [currentMonth] is read from it; only the fields relevant to the
 * active [period] are populated by the queries.
 */
data class StatsUiState(
    val period: StatsPeriod = StatsPeriod.MONTHLY,
    val currentDate: LocalDate = LocalDate.now(),
    val monthlySummary: MonthlySummary? = null,
    val categorySummaries: List<CategorySummary> = emptyList(),
    val budget: Budget? = null,
    val currencySymbol: String = "$",
) {
    /**
     * The month on screen, derived from [currentDate].
     *
     * There is deliberately no separately stored month: when the month and the date
     * were independent fields, switching from the day view to the month view kept
     * showing whatever month had been left behind weeks earlier.
     */
    val currentMonth: YearMonth
        get() = YearMonth.from(currentDate)
}

/**
 * Everything the query planner below needs, bundled so the sources can be funnelled
 * through a single `flatMapLatest`.
 */
private data class StatsInputs(
    val period: StatsPeriod,
    val date: LocalDate,
    val currencySymbol: String,
)

/**
 * Backing [StatsViewModel] for the stats screen.
 *
 * Combines the selected period, the anchor date and the currency symbol, then uses
 * [flatMapLatest] to run the Room queries appropriate for the active period. The
 * resulting StateFlow stops when no subscriber is active.
 *
 * One cursor — [currentDate] — anchors every period: the month view reads its month,
 * the year view its year and the day view the day itself. That is what keeps Day /
 * Month / Year in step: picking 3 December in the day view and switching to the month
 * view lands on December, not on whichever month happened to be selected before.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel
    @Inject
    constructor(
        private val transactionRepository: TransactionRepository,
        private val budgetRepository: BudgetRepository,
        private val dataStore: DataStore<Preferences>,
    ) : ViewModel() {
        private val _period = MutableStateFlow(StatsPeriod.MONTHLY)
        private val _currentDate = MutableStateFlow(LocalDate.now())

        val uiState: StateFlow<StatsUiState> =
            combine(
                _period,
                _currentDate,
                dataStore.data.map { it[Prefs.currencySymbolKey] ?: "$" },
            ) { period, date, symbol ->
                StatsInputs(period, date, symbol)
            }.flatMapLatest { (period, date, symbol) ->
                val zone = ZoneId.systemDefault()
                when (period) {
                    StatsPeriod.MONTHLY -> {
                        val month = YearMonth.from(date)
                        // The repository's dedicated monthly queries are keyed on
                        // the YearMonth; the budget lookup uses the same month code.
                        combine(
                            transactionRepository.getMonthlySummary(month),
                            transactionRepository.getCategorySummaries(month),
                            budgetRepository.getByMonth(yearMonthToMonthCode(month)),
                        ) { summary, categories, budget ->
                            StatsUiState(
                                period = period,
                                currentDate = date,
                                monthlySummary = summary,
                                categorySummaries = categories,
                                budget = budget,
                                currencySymbol = symbol,
                            )
                        }
                    }

                    StatsPeriod.YEARLY -> {
                        // Range covers the whole calendar year: Jan 1 00:00 of
                        // the selected year up to Jan 1 of the next year, in the
                        // device's default timezone.
                        val year = date.year
                        val startOfRange =
                            YearMonth
                                .of(year, 1)
                                .atDay(1)
                                .atStartOfDay(zone)
                                .toInstant()
                                .toEpochMilli()
                        val endOfRange =
                            YearMonth
                                .of(year, 12)
                                .plusMonths(1)
                                .atDay(1)
                                .atStartOfDay(zone)
                                .toInstant()
                                .toEpochMilli()

                        rangeState(period, date, startOfRange, endOfRange, symbol)
                    }

                    StatsPeriod.DAILY -> {
                        // Range covers a single day: start of the selected day
                        // up to start of the following day.
                        val startOfRange = date.atStartOfDay(zone).toInstant().toEpochMilli()
                        val endOfRange =
                            date
                                .plusDays(1)
                                .atStartOfDay(zone)
                                .toInstant()
                                .toEpochMilli()

                        rangeState(period, date, startOfRange, endOfRange, symbol)
                    }
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = StatsUiState(),
            )

        /**
         * Builds the state for an arbitrary date range from the two queries every
         * non-monthly period needs: the income/expense rollup and the per-category
         * breakdown. The rollup runs in SQL, so a yearly view no longer pulls every
         * transaction of the year into memory to add it up.
         */
        private fun rangeState(
            period: StatsPeriod,
            date: LocalDate,
            startOfRange: Long,
            endOfRange: Long,
            currencySymbol: String,
        ): Flow<StatsUiState> =
            combine(
                transactionRepository.getSummary(startOfRange, endOfRange),
                transactionRepository.getCategorySummariesByDateRange(startOfRange, endOfRange),
            ) { summary, categories ->
                StatsUiState(
                    period = period,
                    currentDate = date,
                    monthlySummary = summary,
                    categorySummaries = categories,
                    currencySymbol = currencySymbol,
                )
            }

        /** Encodes a YearMonth as yyyyMM, the key the budgets table uses. */
        private fun yearMonthToMonthCode(yearMonth: YearMonth): Long = yearMonth.year * 100L + yearMonth.monthValue

        /**
         * Switches the visible period. Nothing has to be re-synced here: every period
         * is a view onto the same [StatsUiState.currentDate] cursor.
         */
        fun setPeriod(period: StatsPeriod) {
            _period.value = period
        }

        /**
         * Moves the anchor back by one unit of the active period (a month, a year or a
         * day). Month arithmetic clamps at the end of a short month, as `java.time`
         * defines it: 31 March minus a month is 28 February.
         */
        fun previousPeriod() {
            _currentDate.value = _currentDate.value.shiftedBy(_period.value, -1)
        }

        /** Moves the anchor forward by one unit of the active period. */
        fun nextPeriod() {
            _currentDate.value = _currentDate.value.shiftedBy(_period.value, 1)
        }
    }

/**
 * Moves [this] date by [steps] units of [period].
 *
 * Extracted from the view model so the navigation rule is testable without a
 * dispatcher, a database or a DataStore.
 */
internal fun LocalDate.shiftedBy(
    period: StatsPeriod,
    steps: Int,
): LocalDate =
    when (period) {
        StatsPeriod.MONTHLY -> plusMonths(steps.toLong())
        StatsPeriod.YEARLY -> plusYears(steps.toLong())
        StatsPeriod.DAILY -> plusDays(steps.toLong())
    }
