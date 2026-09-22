package com.example.budge.ui.category

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.budge.R
import com.example.budge.model.Category
import com.example.budge.model.TransactionType
import com.example.budge.ui.initialChar
import com.example.budge.ui.theme.pageWindowInsets

// Fixed palette offered when creating/editing a category; users pick one of
// these instead of entering a custom color.
private val categoryColors =
    listOf(
        0xFFE57373L,
        0xFF81C784L,
        0xFF64B5F6L,
        0xFFFFB74D,
        0xFFBA68C8L,
        0xFF4DB6ACL,
        0xFF90A4AEL,
        0xFFFFF176L,
        0xFF7986CBL,
        0xFFA1887FL,
    )

/**
 * Category management screen, shown as a full-screen overlay. Lists existing
 * categories and hosts the add/edit dialog plus a snackbar for validation
 * errors surfaced by [CategoryViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: CategoryViewModel = hiltViewModel(),
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.category_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.startEditing(null) }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_add_category))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
            // The bottom system inset belongs to the navigation bar laid out below this
        // page, not to the page itself.
        contentWindowInsets = pageWindowInsets,
    ) { paddingValues ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = paddingValues.calculateTopPadding()),
            // The horizontal padding is content padding rather than a modifier so the
            // rows keep scrolling edge to edge underneath it, and the bottom inset keeps
            // the last category clear of the floating action button.
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(categories, key = { it.id }) { category ->
                CategoryItem(
                    category = category,
                    onEdit = { viewModel.startEditing(category) },
                    onDelete = { viewModel.delete(category) },
                )
            }
        }
    }

    // Add/Edit dialog
    if (uiState.showDialog) {
        CategoryDialog(
            uiState = uiState,
            onNameChange = { viewModel.updateName(it) },
            onTypeChange = { viewModel.updateType(it) },
            onColorChange = { viewModel.updateColor(it) },
            onSave = { viewModel.save() },
            onDismiss = { viewModel.dismissDialog() },
        )
    }

    // Error snackbar
    uiState.error?.let { error ->
        LaunchedEffect(error) {
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }
}

/**
 * One category row: a colored circle with the first letter of the name, the
 * type (expense/income), and edit/delete actions. Deletion asks for
 * confirmation first, because it is destructive and cannot be undone.
 */
@Composable
private fun CategoryItem(
    category: Category,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.category_delete_title)) },
            text = { Text(stringResource(R.string.category_delete_confirm, category.name)) },
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

    Card(
        modifier = Modifier.fillMaxWidth(),
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
                        .background(Color(category.color)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = category.name.initialChar(),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(if (category.type == TransactionType.EXPENSE) R.string.entry_expense else R.string.entry_income),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.cd_edit))
            }
            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.cd_delete),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/**
 * Shared dialog for creating and editing a category: name field, expense /
 * income type chips, and the color picker. All field edits and [onSave] are
 * delegated to the ViewModel so the dialog stays stateless.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryDialog(
    uiState: CategoryUiState,
    onNameChange: (String) -> Unit,
    onTypeChange: (TransactionType) -> Unit,
    onColorChange: (Long) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (uiState.isEditing) R.string.category_edit else R.string.category_new)) },
        text = {
            Column {
                OutlinedTextField(
                    value = uiState.name,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(R.string.category_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.category_type),
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = uiState.type == TransactionType.EXPENSE,
                        onClick = { onTypeChange(TransactionType.EXPENSE) },
                        label = { Text(stringResource(R.string.entry_expense)) },
                    )
                    FilterChip(
                        selected = uiState.type == TransactionType.INCOME,
                        onClick = { onTypeChange(TransactionType.INCOME) },
                        label = { Text(stringResource(R.string.entry_income)) },
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.category_color),
                    style = MaterialTheme.typography.titleSmall,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    categoryColors.forEachIndexed { index, color ->
                        val selected = uiState.color == color
                        val label = stringResource(R.string.category_color_option, index + 1)
                        Box(
                            modifier =
                                Modifier
                                    // 48dp touch target (Material minimum) around a 32dp swatch.
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .selectable(
                                        selected = selected,
                                        role = Role.RadioButton,
                                        onClick = { onColorChange(color) },
                                    )
                                    .semantics { contentDescription = label },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(color)),
                            )
                            // White inner circle marks the currently selected color.
                            if (selected) {
                                Box(
                                    modifier =
                                        Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.4f)),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSave,
                enabled = !uiState.isSaving,
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
