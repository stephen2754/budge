package com.example.budge.ui.entry

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.budge.R
import com.example.budge.model.TransactionType
import com.example.budge.ui.applyDateToTimestamp
import com.example.budge.ui.applyTimeToTimestamp
import com.example.budge.ui.datePickerMillisFor
import com.example.budge.ui.formatClockTime
import com.example.budge.ui.formatShortDate
import com.example.budge.ui.localDateFromPickerMillis
import com.example.budge.ui.theme.pageWindowInsets
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Add/edit transaction form. With a [transactionId] greater than zero the form
 * loads and edits that transaction; otherwise it creates a new one. All fields
 * are bound to [EntryViewModel] state, and the date/time pickers only allow
 * past values.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EntryScreen(
    transactionId: Long = -1L,
    onNavigateBack: () -> Unit,
    viewModel: EntryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    LaunchedEffect(transactionId) {
        // Re-initialize the form whenever the target transaction changes (the
        // effect's key makes this run once per distinct id).
        viewModel.init(transactionId)
    }

    LaunchedEffect(uiState.saveSuccess) {
        // saveSuccess is a one-shot event: consume it before navigating back so
        // a configuration change cannot trigger navigation a second time.
        if (uiState.saveSuccess) {
            viewModel.consumeSaveSuccess()
            onNavigateBack()
        }
    }

    LaunchedEffect(uiState.error) {
        // Surface validation/save errors to the user as a snackbar, then clear
        // the field so the same message can be shown again next time.
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (uiState.isEditing) R.string.entry_edit else R.string.entry_new)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    // Disabled while saving for the same reason as the button at
                    // the bottom of the form: one tap, one transaction.
                    IconButton(
                        onClick = { viewModel.save() },
                        enabled = !uiState.isSaving,
                    ) {
                        Icon(Icons.Default.Check, contentDescription = stringResource(R.string.cd_save))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
            // Type selector
            Text(
                text = stringResource(R.string.entry_type),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = uiState.type == TransactionType.EXPENSE,
                    onClick = { viewModel.updateType(TransactionType.EXPENSE) },
                    label = { Text(stringResource(R.string.entry_expense)) },
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = uiState.type == TransactionType.INCOME,
                    onClick = { viewModel.updateType(TransactionType.INCOME) },
                    label = { Text(stringResource(R.string.entry_income)) },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Amount input
            OutlinedTextField(
                value = uiState.amount,
                onValueChange = { viewModel.updateAmount(it) },
                label = { Text(stringResource(R.string.entry_amount)) },
                prefix = { Text(uiState.currencySymbol) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Category selector
            Text(
                text = stringResource(R.string.entry_category),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                uiState.categories.forEach { category ->
                    FilterChip(
                        selected = uiState.selectedCategoryId == category.id,
                        onClick = { viewModel.updateCategoryId(category.id) },
                        label = { Text(category.name) },
                        leadingIcon = {
                            Box(
                                modifier =
                                    Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(Color(category.color)),
                            )
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Date & time (editable independently)
            Text(
                text = stringResource(R.string.entry_date_time),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = formatShortDate(uiState.timestamp))
                }
                OutlinedButton(
                    onClick = { showTimePicker = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = formatClockTime(uiState.timestamp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Note input
            OutlinedTextField(
                value = uiState.note,
                onValueChange = { viewModel.updateNote(it) },
                label = { Text(stringResource(R.string.entry_note)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Save button
            Button(
                onClick = { viewModel.save() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isSaving,
            ) {
                Text(stringResource(if (uiState.isSaving) R.string.entry_saving else R.string.entry_save))
            }
        }
    }

    if (showDatePicker) {
        val zone = ZoneId.systemDefault()
        // The picker identifies a day by the epoch millis of its UTC midnight, so the
        // local date is converted before being handed in. Passing the raw local instant
        // would highlight, and then re-apply, the previous day for anyone east of UTC in
        // the early morning (07:00 in UTC+8, say) and the next day for anyone west of it
        // in the evening.
        val currentDate = Instant.ofEpochMilli(uiState.timestamp).atZone(zone).toLocalDate()
        val datePickerState =
            rememberDatePickerState(
                initialSelectedDateMillis = datePickerMillisFor(currentDate),
                initialDisplayMode = DisplayMode.Picker,
                selectableDates = pastDatesOnly,
            )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            // Only the date changes; the time of day is kept.
                            viewModel.updateTimestamp(applyDateToTimestamp(millis, uiState.timestamp, zone))
                        }
                        showDatePicker = false
                    },
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val currentTime =
            Instant
                .ofEpochMilli(uiState.timestamp)
                .atZone(zone)
                .toLocalTime()
        val timePickerState =
            rememberTimePickerState(
                initialHour = currentTime.hour,
                initialMinute = currentTime.minute,
                is24Hour = true,
            )
        val futureTimeMessage = stringResource(R.string.entry_future_time)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(R.string.entry_time)) },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val candidate =
                            applyTimeToTimestamp(
                                hour = timePickerState.hour,
                                minute = timePickerState.minute,
                                timestamp = uiState.timestamp,
                                zone = zone,
                            )
                        // Future timestamps are not allowed: clamp to "now" and
                        // notify the user that the chosen time was adjusted.
                        val clamped = if (candidate > now.toEpochMilli()) now.toEpochMilli() else candidate
                        viewModel.updateTimestamp(clamped)
                        showTimePicker = false
                        if (clamped != candidate) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(futureTimeMessage)
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

/**
 * Only today and earlier are selectable. Date-picker cells are normalized to
 * UTC midnight, so the cell's calendar date is read back in UTC while it is
 * compared against the *local* today.
 */
@OptIn(ExperimentalMaterial3Api::class)
private val pastDatesOnly =
    object : SelectableDates {
        override fun isSelectableDate(utcTimeMillis: Long): Boolean = !localDateFromPickerMillis(utcTimeMillis).isAfter(LocalDate.now())

        override fun isSelectableYear(year: Int): Boolean = year <= LocalDate.now().year
    }
