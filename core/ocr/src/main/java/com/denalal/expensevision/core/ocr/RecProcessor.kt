package com.denalal.expensevision.core.ocr

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.util.Collections

/** Распознавание текста (модель rec + CTC-декодирование). */
internal class RecProcessor(
    private val ortEnv: OrtEnvironment,
    private val session: OrtSession,
    keys: List<String>,
) {
    private val keys: List<String> = buildList {
        add("#")
        addAll(keys)
        add(" ")
    }

    private fun scoreToTextLine(outputData: List<FloatArray>): RecResult {
        val sb = StringBuilder()
        val scores: MutableList<Float> = mutableListOf()
        var lastIndex = 0
        outputData.forEach {
            val max = it.withIndex().maxBy { e -> e.value }
            if (max.index in 1 until keys.size && max.index != lastIndex) {
                sb.append(keys[max.index])
                scores.add(max.value)
            }
            lastIndex = max.index
        }
        return RecResult(sb.toString(), scores)
    }

    fun recognize(src: Mat): RecResult {
        val scale = dstHeight / src.rows()
        val dstWidth = (src.cols() * scale).toInt().toDouble()
        val srcResize = Mat()
        Imgproc.resize(src, srcResize, Size(dstWidth, dstHeight))

        val inputTensorValues = substractMeanNormalize(srcResize, meanValues, normValues)
        val inputShape = longArrayOf(
            1,
            srcResize.channels().toLong(),
            srcResize.rows().toLong(),
            srcResize.cols().toLong(),
        )
        val inputName = session.inputNames.iterator().next()

        OnnxTensor.createTensor(ortEnv, inputTensorValues, inputShape).use { inputTensor ->
            session.run(Collections.singletonMap(inputName, inputTensor)).use { output ->
                val onnxValue = output.first().value
                val values = onnxValue.value as Array<Array<FloatArray>>
                val outputData = values.flatMap { a -> a.flatMap { b -> listOf(b) } }
                return scoreToTextLine(outputData)
            }
        }
    }

    fun recognizeAll(mats: List<Mat>): List<RecResult> = mats.map { recognize(it) }

    companion object {
        private const val dstHeight = 48.0
        private val meanValues = floatArrayOf(127.5F, 127.5F, 127.5F)
        private val normValues = floatArrayOf(1.0F / 127.5F, 1.0F / 127.5F, 1.0F / 127.5F)
    }
}
