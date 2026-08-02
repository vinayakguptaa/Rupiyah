package com.krtky.financetracker.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krtky.financetracker.data.importcsv.DedupeConfidence
import com.krtky.financetracker.data.repository.AccountRepository
import com.krtky.financetracker.data.repository.ImportCommitResult
import com.krtky.financetracker.data.repository.ImportPreview
import com.krtky.financetracker.data.repository.ImportPreviewRow
import com.krtky.financetracker.data.repository.ImportRowAction
import com.krtky.financetracker.data.repository.StatementImportRepository
import com.krtky.financetracker.domain.model.Account
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class CsvImportStep {
    PICK_ACCOUNT,
    PICK_FILE,
    PREVIEW,
    DONE,
}

data class CsvImportUiState(
    val step: CsvImportStep = CsvImportStep.PICK_ACCOUNT,
    val selectedAccountId: Long? = null,
    val fileName: String? = null,
    val preview: ImportPreview? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val result: ImportCommitResult? = null,
)

@HiltViewModel
class CsvImportViewModel @Inject constructor(
    accountRepository: AccountRepository,
    private val statementImportRepository: StatementImportRepository,
) : ViewModel() {
    val accounts: StateFlow<List<Account>> = accountRepository.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _state = MutableStateFlow(CsvImportUiState())
    val state: StateFlow<CsvImportUiState> = _state.asStateFlow()

    fun selectAccount(id: Long) {
        _state.update {
            it.copy(
                selectedAccountId = id,
                step = CsvImportStep.PICK_FILE,
                error = null,
                preview = null,
                result = null,
            )
        }
    }

    fun backToAccount() {
        _state.update {
            it.copy(
                step = CsvImportStep.PICK_ACCOUNT,
                fileName = null,
                preview = null,
                error = null,
                result = null,
            )
        }
    }

    fun backToFile() {
        _state.update {
            it.copy(
                step = CsvImportStep.PICK_FILE,
                preview = null,
                error = null,
                result = null,
            )
        }
    }

    fun loadFile(uri: Uri, displayName: String?) {
        val accountId = _state.value.selectedAccountId ?: return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                statementImportRepository.buildPreview(
                    accountId = accountId,
                    uri = uri,
                    fileName = displayName ?: "statement.csv",
                )
            }.onSuccess { preview ->
                _state.update {
                    it.copy(
                        loading = false,
                        preview = preview,
                        fileName = preview.fileName,
                        step = CsvImportStep.PREVIEW,
                        error = if (preview.rows.isEmpty() && preview.parseErrors.isNotEmpty()) {
                            preview.parseErrors.first()
                        } else {
                            null
                        },
                    )
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        loading = false,
                        error = e.message ?: "Could not read CSV",
                        step = CsvImportStep.PICK_FILE,
                    )
                }
            }
        }
    }

    fun setRowAction(rowId: String, action: ImportRowAction) {
        _state.update { st ->
            val p = st.preview ?: return@update st
            val rows = p.rows.map { row ->
                if (row.id == rowId) row.copy(action = action) else row
            }
            st.copy(preview = p.copy(rows = rows))
        }
    }

    fun importAllNew() {
        _state.update { st ->
            val p = st.preview ?: return@update st
            val rows = p.rows.map { row ->
                when (row.confidence) {
                    DedupeConfidence.LOW -> row.copy(action = ImportRowAction.IMPORT)
                    DedupeConfidence.MEDIUM -> row.copy(action = ImportRowAction.IMPORT_ANYWAY)
                    DedupeConfidence.HIGH -> row // keep skip merge
                }
            }
            st.copy(preview = p.copy(rows = rows))
        }
    }

    fun commit() {
        val st = _state.value
        val accountId = st.selectedAccountId ?: return
        val rows = st.preview?.rows ?: return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                statementImportRepository.commit(accountId, rows)
            }.onSuccess { result ->
                _state.update {
                    it.copy(loading = false, result = result, step = CsvImportStep.DONE)
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(loading = false, error = e.message ?: "Import failed")
                }
            }
        }
    }

    fun reset() {
        _state.value = CsvImportUiState()
    }
}
