package com.denalal.expensevision.core.ocr

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.geometry.Geometry
import org.opencv.imgproc.Imgproc
import java.util.Collections
import kotlin.math.max
import kotlin.math.min

/** Детекция текстовых блоков (модель det + DB-постобработка). */
internal class DetProcessor(
    private val ortEnv: OrtEnvironment,
    private val session: OrtSession,
) {
    fun detect(src: Mat, s: ScaleParam, boxScoreThresh: Float, boxThresh: Float, unClipRatio: Float): List<DetResult> {
        val srcResize = Mat()
        Imgproc.resize(src, srcResize, Size(s.dstWidth.toDouble(), s.dstHeight.toDouble()))

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
                val onnxValue = output.get(0) as OnnxTensor
                val tensorInfo = onnxValue.info as TensorInfo

                val outHeight = tensorInfo.shape[2].toInt()
                val outWidth = tensorInfo.shape[3].toInt()

                val outputData = FloatArray(outHeight * outWidth)

                onnxValue.floatBuffer.get(outputData)

                val cbufData = ByteArray(outputData.size) { i ->
                    (outputData[i] * 255).toInt().toByte()
                }

                val predMat = Mat(outHeight, outWidth, CvType.CV_32F)
                predMat.put(0, 0, outputData)

                val cBufMat = Mat(outHeight, outWidth, CvType.CV_8UC1)
                cBufMat.put(0, 0, cbufData)

                val thresholdMat = Mat()
                Imgproc.threshold(cBufMat, thresholdMat, boxThresh * 255.0, 255.0, Imgproc.THRESH_BINARY)

                val dilateMat = Mat()
                val dilateElement = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(2.0, 2.0))
                Imgproc.dilate(thresholdMat, dilateMat, dilateElement)

                return findRsBoxes(predMat, dilateMat, s, boxScoreThresh, unClipRatio)
            }
        }
    }

    private fun findRsBoxes(
        predMat: Mat,
        dilateMat: Mat,
        s: ScaleParam,
        boxScoreThresh: Float,
        unClipRatio: Float,
    ): List<DetResult> {
        val longSideThresh = 3
        val maxCandidates = 1000

        val contours: MutableList<MatOfPoint> = mutableListOf()
        val hierarchy = Mat()

        Imgproc.findContours(dilateMat, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

        val numContours = if (contours.size >= maxCandidates) maxCandidates else contours.size

        val rsBoxes: MutableList<DetResult> = mutableListOf()

        for (i in 0 until numContours) {
            if (contours[i].elemSize() <= 2) {
                continue
            }
            val minAreaRect = Geometry.minAreaRect(MatOfPoint2f(*contours[i].toArray()))

            val minBoxes: Array<Point> = Array(4) { Point() }
            val longSide: Float = getMinBoxes(minAreaRect, minBoxes)

            if (longSide < longSideThresh) {
                continue
            }
            val boxScore: Float = boxScoreFast(minBoxes, predMat)
            if (boxScore < boxScoreThresh) {
                continue
            }

            val clipRect = unClip(minBoxes, unClipRatio)
            if (clipRect.size.height < 1.001 && clipRect.size.width < 1.001) {
                continue
            }
            val clipMinBoxes: Array<Point> = Array(4) { Point() }
            val clipLongSide = getMinBoxes(clipRect, clipMinBoxes)
            if (clipLongSide < longSideThresh + 2) {
                continue
            }

            val detPoints = clipMinBoxes.map { point ->
                val x = point.x / s.ratioWidth
                val y = point.y / s.ratioHeight
                val ptX = min(max(x.toInt(), 0), s.srcWidth - 1)
                val ptY = min(max(y.toInt(), 0), s.srcHeight - 1)
                DetPoint(ptX, ptY)
            }
            rsBoxes.add(DetResult(detPoints, boxScore))
        }
        return rsBoxes.asReversed()
    }

    companion object {
        private val meanValues = floatArrayOf(0.485F * 255F, 0.456F * 255F, 0.406F * 255F)
        private val normValues = floatArrayOf(1.0F / 0.229F / 255.0F, 1.0F / 0.224F / 255.0F, 1.0F / 0.225F / 255.0F)
    }
}
