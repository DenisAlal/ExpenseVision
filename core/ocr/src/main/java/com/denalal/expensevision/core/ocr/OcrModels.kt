package com.denalal.expensevision.core.ocr

import org.opencv.core.Point

/** Итог распознавания: полный текст + строки. */
data class OcrResult(
    val text: String,
    val lines: List<OcrLine>,
)

data class OcrLine(
    val text: String,
    val score: Float,
)

internal data class ScaleParam(
    val srcWidth: Int,
    val srcHeight: Int,
    val dstWidth: Int,
    val dstHeight: Int,
    val ratioWidth: Float,
    val ratioHeight: Float,
)

internal data class DetPoint(var x: Int, var y: Int) {
    fun toCvPoint() = Point(x.toDouble(), y.toDouble())
}

internal data class DetResult(
    val points: List<DetPoint>,
    val score: Float,
)

internal data class RecResult(
    val text: String,
    val charScores: List<Float>,
)
