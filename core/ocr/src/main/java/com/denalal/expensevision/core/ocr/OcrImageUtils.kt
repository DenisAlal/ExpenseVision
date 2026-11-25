package com.denalal.expensevision.core.ocr

import de.lighti.clipper.Clipper
import de.lighti.clipper.ClipperOffset
import de.lighti.clipper.Path
import de.lighti.clipper.Paths
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.RotatedRect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.geometry.Geometry
import org.opencv.imgproc.Imgproc
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

internal fun getScaleParam(src: Mat, targetSize: Int): ScaleParam {
    val srcWidth = src.cols()
    var dstWidth = src.cols()
    val srcHeight = src.rows()
    var dstHeight = src.rows()
    var scale = 1.0f

    if (dstWidth > dstHeight) {
        scale = targetSize.toFloat() / dstWidth.toFloat()
        dstWidth = targetSize
        dstHeight = (dstHeight.toFloat() * scale).toInt()
    } else {
        scale = targetSize.toFloat() / dstHeight.toFloat()
        dstHeight = targetSize
        dstWidth = (dstWidth.toFloat() * scale).toInt()
    }
    if (dstWidth % 32 != 0) {
        dstWidth = (dstWidth / 32 - 1) * 32
        dstWidth = max(dstWidth, 32)
    }
    if (dstHeight % 32 != 0) {
        dstHeight = (dstHeight / 32 - 1) * 32
        dstHeight = max(dstHeight, 32)
    }
    return ScaleParam(
        srcWidth = srcWidth,
        srcHeight = srcHeight,
        dstWidth = dstWidth,
        dstHeight = dstHeight,
        ratioWidth = dstWidth.toFloat() / srcWidth.toFloat(),
        ratioHeight = dstHeight.toFloat() / srcHeight.toFloat(),
    )
}

internal fun makePadding(src: Mat, padding: Int): Mat {
    if (padding <= 0) return src
    val paddingScalar = Scalar(255.0, 255.0, 255.0)
    val paddingSrc = Mat()
    Core.copyMakeBorder(src, paddingSrc, padding, padding, padding, padding, Core.BORDER_ISOLATED, paddingScalar)
    return paddingSrc
}

/** Пиксели в BGR-порядке -> тензор NCHW float32 (mean/norm). */
internal fun substractMeanNormalize(src: Mat, meanVals: FloatArray, normVals: FloatArray): FloatBuffer {
    val inputTensorSize = src.cols() * src.rows() * src.channels()
    val numChannels = src.channels()
    val imageSize = src.cols() * src.rows()
    src.convertTo(src, CvType.CV_32FC3)
    val srcArray = FloatArray(inputTensorSize)
    src.get(0, 0, srcArray)
    val imgData = FloatBuffer.allocate(inputTensorSize)
    imgData.rewind()
    for (pid in 0 until imageSize) {
        for (ch in 0 until numChannels) {
            val data = srcArray[pid * numChannels + ch] * normVals[ch] - meanVals[ch] * normVals[ch]
            imgData.put(ch * imageSize + pid, data)
        }
    }
    imgData.rewind()
    return imgData
}

internal fun getMinBoxes(boxRect: RotatedRect, minBoxes: Array<Point>): Float {
    val maxSideLen = max(boxRect.size.width, boxRect.size.height)
    val boxPoint = getBox(boxRect)
    boxPoint.sortBy { it.x }
    val index1: Int
    val index2: Int
    val index3: Int
    val index4: Int
    if (boxPoint[1].y > boxPoint[0].y) {
        index1 = 0
        index4 = 1
    } else {
        index1 = 1
        index4 = 0
    }
    if (boxPoint[3].y > boxPoint[2].y) {
        index2 = 2
        index3 = 3
    } else {
        index2 = 3
        index3 = 2
    }
    minBoxes[0] = boxPoint[index1]
    minBoxes[1] = boxPoint[index2]
    minBoxes[2] = boxPoint[index3]
    minBoxes[3] = boxPoint[index4]
    return maxSideLen.toFloat()
}

internal fun getBox(boxRect: RotatedRect): Array<Point> {
    val points = Array(4) { Point() }
    boxRect.points(points)
    return points
}

internal fun boxScoreFast(boxes: Array<Point>, pred: Mat): Float {
    val width = pred.cols()
    val height = pred.rows()
    val arrayX = Array(4) { boxes[it].x }
    val arrayY = Array(4) { boxes[it].y }
    val minX = floor(arrayX.min()).toFloat().coerceIn(0f, width - 1f).toInt()
    val maxX = ceil(arrayX.max()).toFloat().coerceIn(0f, width - 1f).toInt()
    val minY = floor(arrayY.min()).toFloat().coerceIn(0f, height - 1f).toInt()
    val maxY = ceil(arrayY.max()).toFloat().coerceIn(0f, height - 1f).toInt()

    val mask = Mat.zeros(maxY - minY + 1, maxX - minX + 1, CvType.CV_8UC1)
    val box = arrayOf(
        Point((boxes[0].x.toInt() - minX).toDouble(), (boxes[0].y.toInt() - minY).toDouble()),
        Point((boxes[1].x.toInt() - minX).toDouble(), (boxes[1].y.toInt() - minY).toDouble()),
        Point((boxes[2].x.toInt() - minX).toDouble(), (boxes[2].y.toInt() - minY).toDouble()),
        Point((boxes[3].x.toInt() - minX).toDouble(), (boxes[3].y.toInt() - minY).toDouble()),
    )
    val pts = listOf(MatOfPoint(*box))
    Imgproc.fillPoly(mask, pts, Scalar(1.0))

    val croppedImg = pred.submat(Rect(minX, minY, maxX - minX + 1, maxY - minY + 1))
    val score = Core.mean(croppedImg, mask)
    return score.`val`[0].toFloat()
}

internal fun unClip(box: Array<Point>, unClipRatio: Float): RotatedRect {
    val distance = getContourArea(box, unClipRatio)
    val path = Path().apply {
        addAll(box.map { de.lighti.clipper.Point.LongPoint(it.x.toLong(), it.y.toLong()) })
    }
    val offset = ClipperOffset()
    offset.addPath(path, Clipper.JoinType.ROUND, Clipper.EndType.CLOSED_POLYGON)
    val soln = Paths()
    offset.execute(soln, distance.toDouble())

    val points = soln.flatten().map { Point(it.x.toDouble(), it.y.toDouble()) }.toTypedArray()

    return if (points.isEmpty()) {
        RotatedRect(Point(0.0, 0.0), Size(1.0, 1.0), 0.0)
    } else {
        Geometry.minAreaRect(MatOfPoint2f(*points))
    }
}

internal fun getContourArea(box: Array<Point>, unClipRatio: Float): Float {
    val size = box.size
    var area = 0.0
    var dist = 0.0
    for (i in 0 until size) {
        area += box[i].x * box[(i + 1) % size].y - box[i].y * box[(i + 1) % size].x
        dist += sqrt(
            (box[i].x - box[(i + 1) % size].x) * (box[i].x - box[(i + 1) % size].x) +
                (box[i].y - box[(i + 1) % size].y) * (box[i].y - box[(i + 1) % size].y)
        )
    }
    area = abs(area / 2.0)
    return area.toFloat() * unClipRatio / dist.toFloat()
}

internal fun getRotateCropImage(src: Mat, box: List<DetPoint>): Mat {
    val points = box.map { it.toCvPoint() }.toTypedArray()

    val collectX = arrayOf(box[0].x, box[1].x, box[2].x, box[3].x)
    val collectY = arrayOf(box[0].y, box[1].y, box[2].y, box[3].y)
    val left = collectX.min()
    val right = collectX.max()
    val top = collectY.min()
    val bottom = collectY.max()

    val imgCrop = src.submat(Rect(left, top, right - left, bottom - top))

    for (i in points.indices) {
        points[i].x -= left
        points[i].y -= top
    }

    val imgCropWidth = sqrt(
        (points[0].x - points[1].x) * (points[0].x - points[1].x) +
            (points[0].y - points[1].y) * (points[0].y - points[1].y)
    )
    val imgCropHeight = sqrt(
        (points[0].x - points[3].x) * (points[0].x - points[3].x) +
            (points[0].y - points[3].y) * (points[0].y - points[3].y)
    )

    val ptsDst = arrayOf(
        Point(0.0, 0.0),
        Point(imgCropWidth, 0.0),
        Point(imgCropWidth, imgCropHeight),
        Point(0.0, imgCropHeight),
    )
    val ptsSrc = arrayOf(
        Point(points[0].x, points[0].y),
        Point(points[1].x, points[1].y),
        Point(points[2].x, points[2].y),
        Point(points[3].x, points[3].y),
    )
    val transformation =  Geometry.getPerspectiveTransform(MatOfPoint2f(*ptsSrc), MatOfPoint2f(*ptsDst))

    val partImg = Mat()
    Imgproc.warpPerspective(
        imgCrop, partImg, transformation,
        Size(imgCropWidth, imgCropHeight),
        Core.BORDER_REPLICATE,
    )

    return if (partImg.rows() >= partImg.cols() * 1.5) {
        val srcCopy = Mat(partImg.rows(), partImg.cols(), partImg.depth())
        Core.transpose(partImg, srcCopy)
        Core.flip(srcCopy, srcCopy, 0)
        srcCopy
    } else {
        partImg
    }
}

internal fun getPartMats(src: Mat, detResults: List<DetResult>): List<Mat> =
    detResults.map { getRotateCropImage(src, it.points) }
