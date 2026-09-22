package com.example.budge.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow

/**
 * A labelled money figure for the summary cards on Home and Statistics.
 *
 * The amount is pinned to a single line and takes a style that shrinks with its length
 * ([amountTextStyle]), so two large totals can sit side by side without wrapping onto a
 * second line or pushing each other off the card. The caller supplies the width — the
 * figures live in a `Row`, each with `Modifier.weight(1f)`.
 */
@Composable
fun SummaryFigure(
    label: String,
    amount: String,
    color: Color,
    horizontalAlignment: Alignment.Horizontal,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = horizontalAlignment) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = amount,
            style = amountTextStyle(amount),
            color = color,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
