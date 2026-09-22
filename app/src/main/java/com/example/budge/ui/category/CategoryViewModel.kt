package com.example.budge.ui.category

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.budge.R
import com.example.budge.data.repository.CategoryRepository
import com.example.budge.data.repository.TransactionRepository
import com.example.budge.model.Category
import com.example.budge.model.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State of the add/edit dialog.
 *
 * [showDialog] is whether the dialog is open; [editingCategory] is which category is
 * being edited, or null when adding one. Those are two different questions, and
 * answering both with a single flag is what once made "add" run an update against
 * `id = 0` and quietly do nothing.
 *
 * [error] is shown in a snackbar and cleared by [CategoryViewModel.clearError].
 */
data class CategoryUiState(
    val showDialog: Boolean = false,
    val editingCategory: Category? = null,
    val name: String = "",
    val type: TransactionType = TransactionType.EXPENSE,
    val color: Long = DEFAULT_CATEGORY_COLOR,
    val isSaving: Boolean = false,
    val error: String? = null,
) {
    /** True only when an existing category is being edited (never for a new one). */
    val isEditing: Boolean
        get() = editingCategory != null
}

/** Default color used for a new category and by the color picker. */
const val DEFAULT_CATEGORY_COLOR = 0xFFE57373

/**
 * Backs the category screen: exposes the category list as a reactive flow and
 * owns the add/edit dialog state, including validation on save and the rules
 * for when a category may be deleted.
 */
@HiltViewModel
class CategoryViewModel
    @Inject
    constructor(
        application: Application,
        private val categoryRepository: CategoryRepository,
        private val transactionRepository: TransactionRepository,
    ) : AndroidViewModel(application) {
        // Room-backed list, kept alive only while the screen is subscribed so
        // the DB query stops after the screen leaves composition.
        val categories: StateFlow<List<Category>> =
            categoryRepository
                .getAll()
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = emptyList(),
                )

        private val _uiState = MutableStateFlow(CategoryUiState())
        val uiState: StateFlow<CategoryUiState> = _uiState.asStateFlow()

        /** Opens the dialog, pre-filled with the given category or blank for a new one. */
        fun startEditing(category: Category?) {
            _uiState.update {
                it.copy(
                    showDialog = true,
                    editingCategory = category,
                    name = category?.name ?: "",
                    type = category?.type ?: TransactionType.EXPENSE,
                    color = category?.color ?: DEFAULT_CATEGORY_COLOR,
                )
            }
        }

        fun updateName(name: String) {
            _uiState.update { it.copy(name = name) }
        }

        fun updateType(type: TransactionType) {
            _uiState.update { it.copy(type = type) }
        }

        fun updateColor(color: Long) {
            _uiState.update { it.copy(color = color) }
        }

        /**
         * Validates the name, then inserts (new) or updates (existing) the
         * category. Existing categories keep their icon, default flag and sort
         * order so editing only touches the user-facing fields.
         *
         * The insert/update decision is driven by [CategoryUiState.editingCategory]
         * (the same field the id comes from), never by "is the dialog open".
         */
        fun save() {
            val state = _uiState.value
            if (state.isSaving) return
            if (state.name.isBlank()) {
                _uiState.update { it.copy(error = getApplication<Application>().getString(R.string.category_name_empty)) }
                return
            }

            _uiState.update { it.copy(isSaving = true, error = null) }

            viewModelScope.launch {
                try {
                    val existing = state.editingCategory
                    val category =
                        Category(
                            id = existing?.id ?: 0, // 0 lets Room assign a new id
                            name = state.name.trim(),
                            icon = existing?.icon ?: "circle",
                            color = state.color,
                            type = state.type,
                            isDefault = existing?.isDefault ?: false,
                            sortOrder = existing?.sortOrder ?: 0,
                        )
                    if (existing != null) {
                        categoryRepository.update(category)
                    } else {
                        categoryRepository.insert(category)
                    }
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            showDialog = false,
                            editingCategory = null,
                            name = "",
                            error = null,
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Surface a localizable message rather than a raw driver string.
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            error = getApplication<Application>().getString(R.string.category_save_failed),
                        )
                    }
                }
            }
        }

        /**
         * Deletes a category, seeded or not.
         *
         * A category that transactions still reference is refused. The foreign key is
         * RESTRICT, so the delete would fail anyway, and a sentence beats a constraint
         * error. The check is advisory, so the write is guarded too: between the two,
         * a transaction can appear.
         */
        fun delete(category: Category) {
            viewModelScope.launch {
                if (transactionRepository.getTransactionCountForCategory(category.id) > 0) {
                    _uiState.update { it.copy(error = getApplication<Application>().getString(R.string.category_cannot_delete)) }
                    return@launch
                }
                try {
                    categoryRepository.deleteById(category.id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.update { it.copy(error = getApplication<Application>().getString(R.string.category_cannot_delete)) }
                }
            }
        }

        fun clearError() {
            _uiState.update { it.copy(error = null) }
        }

        fun dismissDialog() {
            _uiState.update {
                it.copy(
                    showDialog = false,
                    editingCategory = null,
                    name = "",
                    error = null,
                )
            }
        }
    }
