package com.denalal.expensevision.feature.scan

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.denalal.expensevision.core.model.ReceiptDraftModel
import com.denalal.expensevision.core.model.ReceiptModel
import com.denalal.expensevision.core.model.parser.ReceiptParser
import com.denalal.expensevision.core.model.repository.ReceiptRepository
import com.denalal.expensevision.core.ocr.OcrEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val ocrEngine: OcrEngine,
    private val repository: ReceiptRepository,
) : ViewModel() {

    private val parser = ReceiptParser()

    private val _state = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    fun onImageScanned(bitmap: Bitmap) {
        viewModelScope.launch {
            _state.value = ScanUiState.Scanning
            try {
                val ocrResult = withContext(Dispatchers.Default) { ocrEngine.recognize(bitmap) }
                val draft = parser.parse(ocrResult.text)
                Log.d("OCR", ocrResult.text)
                _state.value = ScanUiState.Editing(draft)
            } catch (e: Exception) {
                _state.value = ScanUiState.Error(e.message ?: "Ошибка распознавания")
            }
        }
    }
    fun onDraftChanged(draft: ReceiptDraftModel) {
        if (_state.value is ScanUiState.Editing) {
            _state.value = ScanUiState.Editing(draft)
        }
    }

    fun onSave() {
        val draft = (_state.value as? ScanUiState.Editing)?.draft ?: return
        val date = draft.date
        val sum = draft.sum
        if (date == null || sum == null) {
            _state.value = ScanUiState.Error("Укажите дату и сумму")
            return
        }
        viewModelScope.launch {
            _state.value = ScanUiState.Saving
            try {
                withContext(Dispatchers.IO) {
                    repository.save(
                        ReceiptModel(
                            id = 0,
                            date = date,
                            sum = sum,
                            storeName = draft.storeName,
                            category = draft.category,
                        )
                    )
                }
                _state.value = ScanUiState.Saved
            } catch (e: Exception) {
                _state.value = ScanUiState.Error(e.message ?: "Ошибка сохранения")
            }
        }
    }

    fun onReset() {
        _state.value = ScanUiState.Idle
    }
}
