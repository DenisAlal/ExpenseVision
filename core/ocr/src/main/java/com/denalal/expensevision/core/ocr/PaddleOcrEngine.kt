package com.denalal.expensevision.core.ocr

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.io.Closeable
import kotlin.math.max

/**
 * PaddleOCR (PP-OCRv5) на устройстве через ONNX Runtime + OpenCV.
 * Модели и словарь лежат в assets модуля.
 */
class PaddleOcrEngine(context: Context) : OcrEngine, Closeable {

    private val assetManager = context.applicationContext.assets

    private val ortEnv: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }

    private val detSession: OrtSession by lazy { loadSession(DET_NAME) }
    private val recSession: OrtSession by lazy { loadSession(REC_NAME) }

    private val det by lazy { DetProcessor(ortEnv, detSession) }
    private val rec by lazy { RecProcessor(ortEnv, recSession, loadKeys()) }

    init {
        if (!OpenCVLoader.initLocal()) {
            Log.w(TAG, "OpenCV native library not loaded via initLocal()")
        }
    }

    private fun loadSession(name: String): OrtSession {
        val bytes = assetManager.open(name).readBytes()
        return ortEnv.createSession(bytes)
    }

    private fun loadKeys(): List<String> =
        assetManager.open(KEYS_NAME).bufferedReader().use { it.readLines() }

    override fun recognize(bitmap: Bitmap): OcrResult {
        val inputRGBA = Mat(bitmap.height, bitmap.width, CvType.CV_8UC4)
        Utils.bitmapToMat(bitmap, inputRGBA)
        val inputBGR = Mat()
        Imgproc.cvtColor(inputRGBA, inputBGR, Imgproc.COLOR_RGBA2BGR)

        val originMaxSide = max(inputBGR.cols(), inputBGR.rows())
        var resize = if (MAX_SIDE_LEN <= 0 || originMaxSide < MAX_SIDE_LEN) originMaxSide else MAX_SIDE_LEN
        resize += 2 * PADDING

        val paddingSrc = makePadding(inputBGR, PADDING)
        val s = getScaleParam(paddingSrc, resize)

        val detResults = det.detect(paddingSrc, s, BOX_SCORE_THRESH, BOX_THRESH, UNCLIP_RATIO)
        val partMats = getPartMats(paddingSrc, detResults)
        val recResults = rec.recognizeAll(partMats)

        val lines = recResults.map { r ->
            OcrLine(
                text = r.text,
                score = if (r.charScores.isEmpty()) 0f else r.charScores.average().toFloat(),
            )
        }
        val text = lines.joinToString(separator = "\n") { it.text }
        return OcrResult(text = text, lines = lines)
    }

    override fun close() {
        runCatching { detSession.close() }
        runCatching { recSession.close() }
        runCatching { ortEnv.close() }
    }

    companion object {
        private const val TAG = "PaddleOcrEngine"
        private const val DET_NAME = "ch_PP-OCRv5_det_mobile.onnx"
        private const val REC_NAME = "cyrillic_PP-OCRv5_rec_mobile.onnx"
        private const val KEYS_NAME = "ppocrv5_cyrillic_dict.txt"

        private const val MAX_SIDE_LEN = 960
        private const val PADDING = 0
        private const val BOX_SCORE_THRESH = 0.6f
        private const val BOX_THRESH = 0.3f
        private const val UNCLIP_RATIO = 1.6f
    }
}
