package com.example.budge.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.budge.R
import com.example.budge.model.CategorySummary
import com.example.budge.model.TransactionType
import com.example.budge.ui.SummaryFigure
import com.example.budge.ui.amountTextStyle
import com.example.budge.ui.formatDayMonthYear
import com.example.budge.ui.formatMoney
import com.example.budge.ui.formatMonthYear
import com.example.budge.ui.formatYear
import com.example.budge.ui.theme.incomeColor
import com.example.budge.ui.theme.pageWindowInsets

/**
 * Stats screen: a period selector (year/month/day) with previous/next
 * navigation in the top bar, plus cards showing the period summary, budget
 * progress, and per-category expense/income breakdowns with donut charts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(viewModel: StatsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        IconButton(onClick = { viewModel.previousPeriod() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.cd_previous_period),
                            )
                        }
                        Text(
                            text =
                                when (uiState.period) {
                                    StatsPeriod.YEARLY -> formatYear(uiState.currentDate)
                                    StatsPeriod.DAILY -> formatDayMonthYear(uiState.currentDate)
                                    StatsPeriod.MONTHLY -> formatMonthYear(uiState.currentMonth)
                                },
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                        )
                        IconButton(onClick = { viewModel.nextPeriod() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = stringResource(R.string.cd_next_period),
                            )
                        }
                    }
                },
            )
        },
            // The bottom system inset belongs to the navigation bar laid out below this
        // page, not to the page itself.
        contentWindowInsets = pageWindowInsets,
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = paddingValues.calculateTopPadding())
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
        ) {
            // Centered period selector: each chip takes equal width so the
            // active period always sits in the middle of the row.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatsPeriod.entries.forEach { period ->
                    FilterChip(
                        selected = uiState.period == period,
                        onClick = { viewModel.setPeriod(period) },
                        label = {
                            Text(
                                text =
                                    when (period) {
                                        StatsPeriod.YEARLY -> stringResource(R.string.stats_period_year)
                                        StatsPeriod.MONTHLY -> stringResource(R.string.stats_period_month)
                                        StatsPeriod.DAILY -> stringResource(R.string.stats_period_day)
                                    },
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Monthly summary card
            uiState.monthlySummary?.let { summary ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text =
                                when (uiState.period) {
                                    StatsPeriod.YEARLY -> stringResource(R.string.stats_summary_yearly)
                                    StatsPeriod.DAILY -> stringResource(R.string.stats_summary_daily)
                                    StatsPeriod.MONTHLY -> stringResource(R.string.stats_summary_monthly)
                                },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            val expense = formatMoney(summary.totalExpense, uiState.currencySymbol)
                            val income = formatMoney(summary.totalIncome, uiState.currencySymbol)
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
                        val balance = summary.totalIncome - summary.totalExpense
                        val balanceText = formatMoney(balance, uiState.currencySymbol)
                        Text(
                            text = stringResource(R.string.home_balance, balanceText),
                            style = amountTextStyle(balanceText),
                            fontWeight = FontWeight.Bold,
                            color = if (balance >= 0) incomeColor() else MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Budget progress (if available)
            uiState.budget?.let { budget ->
                uiState.monthlySummary?.let { summary ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = stringResource(R.string.stats_budget),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text =
                                        stringResource(
                                            R.string.stats_budget_progress,
                                            formatMoney(summary.totalExpense, uiState.currencySymbol),
                                            formatMoney(budget.amount, uiState.currencySymbol),
                                        ),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            // Spend ratio relative to the budget, clamped to
                            // 100%; the bar turns red once it exceeds 90%.
                            val progress =
                                if (budget.amount > 0) {
                                    (summary.totalExpense.toFloat() / budget.amount.toFloat()).coerceIn(0f, 1f)
                                } else {
                                    0f
                                }
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(MaterialTheme.shapes.small),
                                color = if (progress > 0.9f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text =
                                    stringResource(
                                        R.string.stats_remaining,
                                        formatMoney(budget.amount - summary.totalExpense, uiState.currencySymbol),
                                    ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Expense category breakdown
            val expenseCategories = uiState.categorySummaries.filter { it.type == TransactionType.EXPENSE }
            if (expenseCategories.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.home_expense),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        CategoryDonutChart(
                            categories = expenseCategories,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(180.dp),
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        expenseCategories.forEach { category ->
                            CategorySummaryItem(category, uiState.currencySymbol)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Income category breakdown
            val incomeCategories = uiState.categorySummaries.filter { it.type == TransactionType.INCOME }
            if (incomeCategories.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.home_income),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        CategoryDonutChart(
                            categories = incomeCategories,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(180.dp),
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        incomeCategories.forEach { category ->
                            CategorySummaryItem(category, uiState.currencySymbol)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Draws a donut chart of category totals with a Canvas: each category gets a
 * colored arc whose sweep angle is proportional to its share of the total.
 * Renders nothing when the total is zero.
 *
 * The arcs carry no text, so the chart exposes a short description for screen
 * readers; the per-category figures are listed underneath it anyway.
 */
@Composable
private fun CategoryDonutChart(
    categories: List<CategorySummary>,
    modifier: Modifier = Modifier,
) {
    val total = categories.sumOf { it.total }
    if (total <= 0L) return

    val categoryColors = categories.map { Color(it.categoryColor) }
    val chartDescription = stringResource(R.string.stats_donut_description, categories.size)

    Canvas(
        modifier =
            modifier.semantics {
                contentDescription = chartDescription
            },
    ) {
        val strokeWidth = 40.dp.toPx()
        val radius = (size.minDimension - strokeWidth) / 2
        val center = Offset(size.width / 2, size.height / 2)
        val rect = Size(radius * 2, radius * 2)
        val topLeft = Offset(center.x - radius, center.y - radius)

        var startAngle = -90f
        categories.forEachIndexed { index, category ->
            val sweepAngle = (category.total.toFloat() / total.toFloat()) * 360f
            drawArc(
                color = categoryColors[index],
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = topLeft,
                size = rect,
                style = Stroke(width = strokeWidth),
            )
            startAngle += sweepAngle
        }
    }
}

/**
 * One row of the category breakdown: a color dot, the category name, and the
 * signed total colored according to the transaction type.
 */
@Composable
private fun CategorySummaryItem(
    category: CategorySummary,
    currencySymbol: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color(category.categoryColor)),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = category.categoryName,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatMoney(category.total, currencySymbol),
            style = MaterialTheme.typography.bodyMedium,
            color = if (category.type == TransactionType.EXPENSE) MaterialTheme.colorScheme.error else incomeColor(),
            maxLines = 1,
            softWrap = false,
        )
    }
}

// Amount and period-title formatting live in com.example.budge.ui.Formats so the
// home and statistics screens cannot drift apart again.

