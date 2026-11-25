package com.denalal.expensevision.core.ocr

import android.graphics.Bitmap

/**
 * Контракт OCR-движка. Реализации распознают текст на переданном изображении.
 */
interface OcrEngine {
    fun recognize(bitmap: Bitmap): OcrResult
}
