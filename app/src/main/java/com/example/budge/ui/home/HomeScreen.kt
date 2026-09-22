package com.example.budge.ui.home

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.budge.R
import com.example.budge.model.Transaction
import com.example.budge.model.TransactionType
import com.example.budge.ui.SummaryFigure
import com.example.budge.ui.amountTextStyle
import com.example.budge.ui.formatDayHeader
import com.example.budge.ui.formatMoney
import com.example.budge.ui.formatMonthYear
import com.example.budge.ui.initialChar
import com.example.budge.ui.theme.incomeColor
import com.example.budge.ui.theme.pageWindowInsets
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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
                        val expense = formatMoney(uiState.totalExpense, uiState.currencySymbol)
                        val income = formatMoney(uiState.totalIncome, uiState.currencySymbol)
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
                    val balance = formatMoney(uiState.totalIncome - uiState.totalExpense, uiState.currencySymbol)
                    Text(
                        text = stringResource(R.string.home_balance, balance),
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

            // Transaction list
            if (uiState.transactions.isEmpty()) {
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
                val groupedTransactions =
                    remember(uiState.transactions) {
                        uiState.transactions.groupBy { transaction ->
                            Instant
                                .ofEpochMilli(transaction.timestamp)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                        }
                    }

                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .weight(1f),
                ) {
                    groupedTransactions.forEach { (date, transactions) ->
                        item {
                            DayHeader(date, transactions, uiState.currencySymbol)
                        }
                        items(transactions, key = { it.id }) { transaction ->
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
 * Header row for a single day: the formatted date on the left and the day's
 * expense/income subtotals (signed and colored) on the right.
 */
@Composable
private fun DayHeader(
    date: LocalDate,
    transactions: List<Transaction>,
    currencySymbol: String,
) {
    val dayExpense = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
    val dayIncome = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }

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

    val dismissState =
        rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                // Intercept the swipe instead of dismissing the row: snap it back
                // and ask the user to confirm before actually deleting.
                if (value == SwipeToDismissBoxValue.EndToStart) {
                    showDeleteDialog = true
                    false
                } else {
                    false
                }
            },
        )

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
                        color = Color.White,
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
