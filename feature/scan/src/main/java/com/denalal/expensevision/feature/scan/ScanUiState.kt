package com.denalal.expensevision.feature.scan

import com.denalal.expensevision.core.model.ReceiptDraftModel

sealed class ScanUiState {
    data object Idle : ScanUiState()
    data object Scanning : ScanUiState()
    data class Editing(val draft: ReceiptDraftModel) : ScanUiState()
    data object Saving : ScanUiState()
    data object Saved : ScanUiState()
    data class Error(val message: String) : ScanUiState()
}