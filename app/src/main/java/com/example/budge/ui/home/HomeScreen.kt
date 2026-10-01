package com.example.budge.ui.home

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.budge.R
import com.example.budge.model.Transaction
import com.example.budge.model.TransactionType
import com.example.budge.model.Amount
import com.example.budge.ui.SummaryFigure
import com.example.budge.ui.amountTextStyle
import com.example.budge.ui.categoryInitialColor
import com.example.budge.ui.formatDayHeader
import com.example.budge.ui.formatMoney
import com.example.budge.ui.formatMonthYear
import com.example.budge.ui.initialChar
import com.example.budge.ui.theme.incomeColor
import com.example.budge.ui.theme.pageWindowInsets
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/**
 * How far a row has to travel sideways before the gesture means something.
 *
 * Deleting asks for a deliberate swipe: the dismiss box's own default is half the row's
 * width, and a shorter, fixed distance that the user can feel through the haptic below is
 * easier to aim at than a proportion of a screen that changes size.
 */
private val deleteSwipeDistance = 96.dp

/**
 * Main home screen for a given month.
 *
 * Renders a summary card (expense/income/balance) followed by the month's
 * transactions grouped by day, or an empty-state message when there are none.
 * The floating action button opens the add-transaction form, and tapping a
 * transaction row opens it for editing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddTransaction: () -> Unit,
    onEditTransaction: (Long) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // The month the list is keyed on is captured when the view model is created, so a
    // session left open across midnight on the first of a month kept showing the previous
    // one. Re-reading it on resume is what closes that gap.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshMonth() }

    // A delete that failed leaves the row in place; saying so is the difference between a
    // gesture that did nothing and one that silently did nothing.
    val deleteError by viewModel.deleteError.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val deleteFailed = stringResource(R.string.home_delete_failed)
    LaunchedEffect(deleteError) {
        if (deleteError) {
            snackbarHostState.showSnackbar(deleteFailed)
            viewModel.clearDeleteError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = formatMonthYear(uiState.currentMonth),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddTransaction) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_add_transaction))
            }
        },
            // The bottom system inset belongs to the navigation bar laid out below this
        // page, not to the page itself.
        contentWindowInsets = pageWindowInsets,
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = paddingValues.calculateTopPadding()),
        ) {
            // Monthly summary card
            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Two figures side by side, each given half the width and told not
                    // to wrap: a large total drops a text size instead of pushing its
                    // neighbour off the card or spilling onto a second line.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Placeholders rather than "$0.00" before the first emission: the
                        // figures are not known yet, and zeros in a currency the reader may
                        // not have chosen are worse than a mark that admits it.
                        val loaded = uiState.transactions != null
                        val expense =
                            if (loaded) formatMoney(uiState.totalExpense, uiState.currencySymbol) else UNKNOWN_AMOUNT
                        val income =
                            if (loaded) formatMoney(uiState.totalIncome, uiState.currencySymbol) else UNKNOWN_AMOUNT
                        SummaryFigure(
                            label = stringResource(R.string.home_expense),
                            amount = expense,
                            color = MaterialTheme.colorScheme.error,
                            horizontalAlignment = Alignment.Start,
                            modifier = Modifier.weight(1f),
                        )
                        SummaryFigure(
                            label = stringResource(R.string.home_income),
                            amount = income,
                            color = incomeColor(),
                            horizontalAlignment = Alignment.End,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    val balanceCents = uiState.totalIncome - uiState.totalExpense
                    val balance = formatMoney(balanceCents, uiState.currencySymbol)
                    val balanceText =
                        stringResource(
                            R.string.home_balance,
                            if (uiState.transactions != null) balance else UNKNOWN_AMOUNT,
                        )
                    // The same two colours the figures above use: money in is green, money
                    // out is the theme's error colour. Break-even is neither, so it keeps
                    // the page's own colour rather than claiming to be one of them.
                    val balanceColor =
                        when {
                            balanceCents > 0L -> incomeColor()
                            balanceCents < 0L -> MaterialTheme.colorScheme.error
                            else -> Color.Unspecified
                        }
                    Text(
                        text =
                            buildAnnotatedString {
                                append(balanceText)
                                val start = balanceText.lastIndexOf(balance)
                                if (start >= 0 && balanceColor != Color.Unspecified) {
                                    addStyle(SpanStyle(color = balanceColor), start, start + balance.length)
                                }
                            },
                        style = amountTextStyle(balance),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // Transaction list. Null is "nothing has been read yet" and gets no message:
            // "no transactions this month" would be a claim the app cannot make yet.
            val transactions = uiState.transactions
            if (transactions == null) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .weight(1f),
                )
            } else if (transactions.isEmpty()) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.home_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                // Group the month's transactions by their local calendar date so
                // each day is shown under its own header with a daily subtotal.
                // Remembered because this walks (and re-buckets) the whole month
                // on every recomposition otherwise.
                // Grouped once, with each day's two totals summed once here rather than
                // in the header composable: a header that adds up its own rows does that
                // work again on every recomposition of the list, and its parameters are then
                // a list instead of two numbers, which is the difference between a row that
                // can be skipped and one that cannot.
                val days =
                    remember(transactions) {
                        transactions
                            .groupBy { transaction ->
                                Instant
                                    .ofEpochMilli(transaction.timestamp)
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDate()
                            }.map { (date, rows) ->
                                DayTransactions(
                                    date = date,
                                    rows = rows,
                                    // Clamped like the month's own totals are: a day of
                                    // amounts that each fit can still add up past what an
                                    // amount may be, and a wrapped total looks like a small
                                    // real figure rather than an impossible one.
                                    expense =
                                        Amount.coerceToAmount(
                                            rows.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount },
                                        ),
                                    income =
                                        Amount.coerceToAmount(
                                            rows.filter { it.type == TransactionType.INCOME }.sumOf { it.amount },
                                        ),
                                )
                            }
                    }

                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .weight(1f),
                    // Keeps the last row — and the amount on its right, which is where the
                    // button sits — clear of the floating action button. Without it a tap
                    // on the newest transaction opened the add form instead of editing it.
                    contentPadding = PaddingValues(bottom = 96.dp),
                ) {
                    days.forEach { day ->
                        item(key = "day-${day.date}") {
                            DayHeader(day.date, day.expense, day.income, uiState.currencySymbol)
                        }
                        items(day.rows, key = { it.id }) { transaction ->
                            TransactionItem(
                                transaction = transaction,
                                onClick = { onEditTransaction(transaction.id) },
                                onDelete = { viewModel.deleteTransaction(transaction.id) },
                                currencySymbol = uiState.currencySymbol,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Stands in for a figure that has not been read yet.
 *
 * Two hyphens, not a zero: this is what the summary shows for the frame or two between the
 * screen appearing and the first query answering, and a placeholder that cannot be mistaken
 * for a balance is the point of it.
 */
private const val UNKNOWN_AMOUNT = "--"

/** One day of the list: its rows and the two figures its header shows. */
private data class DayTransactions(
    val date: LocalDate,
    val rows: List<Transaction>,
    val expense: Long,
    val income: Long,
)

/**
 * Header row for a single day: the formatted date on the left and the day's
 * expense/income subtotals (signed and colored) on the right.
 */
@Composable
private fun DayHeader(
    date: LocalDate,
    dayExpense: Long,
    dayIncome: Long,
    currencySymbol: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = formatDayHeader(date),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row {
            if (dayExpense > 0) {
                Text(
                    text = "-${formatMoney(dayExpense, currencySymbol)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (dayIncome > 0) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "+${formatMoney(dayIncome, currencySymbol)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = incomeColor(),
                )
            }
        }
    }
}

/**
 * A single transaction row: a category-colored circle showing the category's
 * first letter, the category name, an optional note, and the signed amount.
 * Swiping the row left opens a confirmation dialog before deletion.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionItem(
    transaction: Transaction,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    currencySymbol: String,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val deleteLabel = stringResource(R.string.delete_transaction)
    val haptics = LocalHapticFeedback.current
    val deleteDistance = with(LocalDensity.current) { deleteSwipeDistance.toPx() }

    // The callback below has to read the state it is passed to, which it cannot do through
    // the property being initialized. A plain array is the holder: snapshot state written
    // during composition would recompose forever.
    val stateHolder = remember { arrayOfNulls<SwipeToDismissBoxState>(1) }

    val dismissState =
        rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                // Intercept the swipe instead of dismissing the row: snap it back and ask
                // the user to confirm before anything is deleted.
                //
                // What decides is the distance the row actually travelled, not the claim a
                // flung gesture makes about it. A quick flick is settled past the threshold
                // by its speed alone, and taking that as a delete made the confirmation
                // appear with no buzz behind it — the reader had been told nothing about
                // having gone far enough. A flick that never travelled the distance now
                // settles back like any other short swipe.
                if (value == SwipeToDismissBoxValue.EndToStart) {
                    val travelled = stateHolder[0]?.let { state -> runCatching { abs(state.requireOffset()) }.getOrDefault(0f) }
                    if ((travelled ?: 0f) >= deleteDistance) {
                        showDeleteDialog = true
                    }
                }
                false
            },
            positionalThreshold = { totalDistance -> minOf(deleteDistance, totalDistance) },
        )
    stateHolder[0] = dismissState

    // The buzz says "far enough to delete", and it is measured the same way the decision
    // below is: the distance the row has actually travelled. Watching what the gesture is
    // about to settle to instead would buzz for a flick that never got there — a quick
    // flick is settled past the threshold by its speed alone — and then delete nothing,
    // which is the same disagreement in the other direction. The magnitude is used rather
    // than a signed offset so the two cannot drift apart over which way is negative.
    LaunchedEffect(dismissState) {
        var armed = false
        snapshotFlow { runCatching { abs(dismissState.requireOffset()) }.getOrDefault(0f) }
            .collect { travelled ->
                val crossed = travelled >= deleteDistance
                if (crossed && !armed) {
                    armed = true
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                } else if (!crossed) {
                    armed = false
                }
            }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_transaction)) },
            text = { Text(stringResource(R.string.confirm_delete)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDelete()
                }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Transparent)
                        .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.cd_delete),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
        enableDismissFromStartToEnd = false,
    ) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clickable(onClick = onClick)
                    // No gesture modifier here on purpose. The dismiss box below owns
                    // horizontal drags, and a second detector on the row — which is what
                    // this used to have — consumes the touch slop before the box sees it,
                    // so the swipe stops working entirely. The box's own progress is
                    // watched instead, which interferes with nothing.
                    //
                    // Deleting a row is also not something every user can perform: the
                    // gesture is not in the accessibility tree at all, so the same deletion
                    // is offered as an action.
                    .semantics {
                        customActions =
                            listOf(
                                CustomAccessibilityAction(deleteLabel) {
                                    showDeleteDialog = true
                                    true
                                },
                            )
                    }
                    .animateContentSize(),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(transaction.categoryColor)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = transaction.categoryName.initialChar(),
                        color = Color(categoryInitialColor(transaction.categoryColor)),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = transaction.categoryName,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!transaction.note.isNullOrBlank()) {
                        Text(
                            text = transaction.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text =
                        if (transaction.type ==
                            TransactionType.EXPENSE
                        ) {
                            "-${formatMoney(transaction.amount, currencySymbol)}"
                        } else {
                            "+${formatMoney(transaction.amount, currencySymbol)}"
                        },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (transaction.type == TransactionType.EXPENSE) MaterialTheme.colorScheme.error else incomeColor(),
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

// Amount, month-title and day-header formatting live in com.example.budge.ui.Formats
// so the home and statistics screens cannot drift apart again.
