package com.aetherexa.photoflow.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.LruCache
import com.aetherexa.photoflow.core.Category
import com.aetherexa.photoflow.core.CategoryEngine
import com.aetherexa.photoflow.core.PaymentInsightParser
import com.aetherexa.photoflow.core.ScreenshotSignals
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.aetherexa.photoflow.core.ClassificationSignals
import com.aetherexa.photoflow.core.PhotoTagEngine
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** All inference runs with bundled ML Kit models on the device. Does not upload photos. */
class VisionAnalyzer(private val resolver: ContentResolver) : AutoCloseable {
    private val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder().setConfidenceThreshold(0.50f).build()
    )
    private val ocr = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val faces = FaceDetection.getClient(
        FaceDetectorOptions.Builder().setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setMinFaceSize(0.06f).build()
    )

    suspend fun analyze(source: PhotoSource, useAI: Boolean, useScreenshotOCR: Boolean): PhotoItem {
        // Metadata precedes scene recognition so temporal and geographic evidence is independent of AI.
        val metadata = ImageMetadataReader.read(resolver, source)
        val strongScreenshotHint = ScreenshotSignals.namedScreenshot(source.name)
        val (bitmap, fullW, fullH) = withContext(Dispatchers.IO) {
            BitmapLoader.decode(resolver, source.uri, if (useScreenshotOCR && metadata.cameraModel == null) 2100 else 1200)
        }
        try {
            val shouldOcr = useScreenshotOCR && ScreenshotSignals.shouldReadText(
                source.name, fullW, fullH, metadata.cameraModel
            )
            val textLines = if (shouldOcr) try {
                var lines = ocr.process(InputImage.fromBitmap(bitmap, 0)).await()
                    .textBlocks.flatMap { it.lines }.map { it.text }
                if (lines.size < 3 && bitmap.width > 550 && bitmap.height > 800) {
                    // Second pass only for difficult screenshots; avoids processing entire trip gallery twice.
                    val enhanced = ScreenshotImageProcessor.improveContrast(bitmap)
                    try {
                        val retry = ocr.process(InputImage.fromBitmap(enhanced, 0)).await()
                            .textBlocks.flatMap { it.lines }.map { it.text }
                        if (retry.joinToString("").length > lines.joinToString("").length) lines = retry
                    } finally { enhanced.recycle() }
                }
                lines
            } catch (cancel: CancellationException) { throw cancel }
              catch (_: Exception) { emptyList() } else emptyList()
            val payment = PaymentInsightParser.parse(textLines)
            val isScreenshot = strongScreenshotHint || payment != null ||
                (shouldOcr && ScreenshotSignals.recognizedScreenshot(source.name, textLines))
            if (isScreenshot) {
                val category = if (payment != null) Category.PAYMENTS else Category.SCREENSHOTS
                val visual = buildSet {
                    add("Screenshot")
                    if (textLines.isNotEmpty()) add("OCR processed")
                    if (payment != null) add("Payment receipt")
                }
                return PhotoItem(source, fullW, fullH, PhotoMetrics.dHash(bitmap),
                    category, if (payment != null) payment.confidence else 0.85f,
                    if (payment != null) "On-device payment screenshot OCR; review before relying on amounts"
                    else "Screen capture detected; OCR text was not retained",
                    emptySet(), "", metadata = metadata, visualTags = visual, payment = payment)
            }
            val labels = if (useAI) try {
                labeler.process(InputImage.fromBitmap(bitmap, 0)).await()
                    .associate { it.text to it.confidence }
            } catch (cancel: CancellationException) { throw cancel }
              catch (_: Exception) { emptyMap() } else emptyMap()
            val faceList = if (useAI) try {
                faces.process(InputImage.fromBitmap(bitmap, 0)).await()
            } catch (cancel: CancellationException) { throw cancel }
              catch (_: Exception) { emptyList() } else emptyList()
            val largestFace = faceList.maxOfOrNull { f ->
                f.boundingBox.width().toDouble() * f.boundingBox.height() /
                    (bitmap.width.toDouble() * bitmap.height)
            } ?: 0.0
            val sharpness = PhotoMetrics.sharpness(bitmap)
            val brightness = PhotoMetrics.brightness(bitmap)
            val metrics = PhotoMetrics.tonalMetrics(bitmap)
            val signals = ClassificationSignals(
                fullW, fullH, faceList.size, largestFace,
                faceList.any { abs(it.headEulerAngleY) > 23f },
                labels, sharpness, brightness,
                metrics.contrast, metrics.highlights, metrics.shadows,
                faceList.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                    ?.let { f -> f.boundingBox.exactCenterX().toDouble() / bitmap.width } ?: 0.5
            )
            val result = CategoryEngine.classify(signals)
            val visual = PhotoTagEngine.sceneTags(labels) + PhotoTagEngine.qualityTags(
                brightness, metrics.contrast, metrics.highlights, metrics.shadows, fullW, fullH
            )
            return PhotoItem(
                source, fullW, fullH, PhotoMetrics.dHash(bitmap),
                result.category, result.confidence, result.reason, result.tags,
                labels.entries.sortedByDescending { it.value }.take(3)
                    .joinToString(" · ") { "${it.key} ${(it.value * 100).toInt()}%" },
                metadata = metadata,
                visualTags = visual
            )
        } finally {
            bitmap.recycle()
        }
    }

    override fun close() {
        labeler.close()
        faces.close()
        ocr.close()
    }
}

/** Decode only a bounded bitmap; ImageDecoder also applies recorded orientation. */
object BitmapLoader {
    fun decode(resolver: ContentResolver, uri: Uri, maxSide: Int): Triple<Bitmap, Int, Int> {
        var originalWidth = 0
        var originalHeight = 0
        val source = ImageDecoder.createSource(resolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            originalWidth = info.size.width
            originalHeight = info.size.height
            val scale = min(1.0, maxSide.toDouble() / max(originalWidth, originalHeight).coerceAtLeast(1))
            decoder.setTargetSize(
                max(1, (originalWidth * scale).toInt()),
                max(1, (originalHeight * scale).toInt())
            )
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        return Triple(bitmap, originalWidth, originalHeight)
    }
}

/** Local, lightweight quality hints; not objective aesthetics or medical-grade focus metrics. */
object PhotoMetrics {
    data class TonalMetrics(val contrast: Double, val highlights: Double, val shadows: Double)

    fun tonalMetrics(bitmap: Bitmap): TonalMetrics {
        val n = 40
        val scaled = Bitmap.createScaledBitmap(bitmap, n, n, true)
        return try {
            val pixels = IntArray(n * n)
            scaled.getPixels(pixels, 0, n, 0, 0, n, n)
            val lumas = DoubleArray(pixels.size) { normalizedLuma(pixels[it]) }
            val mean = lumas.average()
            val contrast = sqrt(lumas.sumOf { (it - mean) * (it - mean) } / lumas.size)
            TonalMetrics(
                contrast = contrast,
                highlights = lumas.count { it >= 0.97 }.toDouble() / lumas.size,
                shadows = lumas.count { it <= 0.035 }.toDouble() / lumas.size
            )
        } finally { if (scaled !== bitmap) scaled.recycle() }
    }

    private fun normalizedLuma(color: Int): Double =
        (0.2126 * ((color ushr 16) and 255) +
         0.7152 * ((color ushr 8) and 255) +
         0.0722 * (color and 255)) / 255.0

    fun brightness(bitmap: Bitmap): Double {
        val scaled = Bitmap.createScaledBitmap(bitmap, 24, 24, true)
        return try {
            val pixels = IntArray(24 * 24)
            scaled.getPixels(pixels, 0, 24, 0, 0, 24, 24)
            pixels.sumOf { normalizedLuma(it) } / pixels.size
        } finally { if (scaled !== bitmap) scaled.recycle() }
    }

    fun sharpness(bitmap: Bitmap): Double {
        val n = 96
        val scaled = Bitmap.createScaledBitmap(bitmap, n, n, true)
        return try {
            val pixels = IntArray(n * n)
            scaled.getPixels(pixels, 0, n, 0, 0, n, n)
            val lum = DoubleArray(n * n) { normalizedLuma(pixels[it]) * 255.0 }
            var sum = 0.0
            var sumSq = 0.0
            var count = 0
            for (y in 1 until n - 1) for (x in 1 until n - 1) {
                val i = y * n + x
                val lap = 4.0 * lum[i] - lum[i-1] - lum[i+1] - lum[i-n] - lum[i+n]
                sum += lap
                sumSq += lap * lap
                count++
            }
            val variance = sumSq / count - (sum / count) * (sum / count)
            (sqrt(max(0.0, variance)) / 56.0).coerceIn(0.0, 1.0)
        } finally { if (scaled !== bitmap) scaled.recycle() }
    }

    fun dHash(bitmap: Bitmap): Long {
        val thumb = Bitmap.createScaledBitmap(bitmap, 9, 8, true)
        return try {
            val pixels = IntArray(72)
            thumb.getPixels(pixels, 0, 9, 0, 0, 9, 8)
            var hash = 0L
            for (row in 0 until 8) for (col in 0 until 8) {
                hash = (hash shl 1) or if (normalizedLuma(pixels[row*9+col]) >
                    normalizedLuma(pixels[row*9+col+1])) 1L else 0L
            }
            hash
        } finally { if (thumb !== bitmap) thumb.recycle() }
    }
}

/** Small thumbnail cache, not a memory cache for full-resolution photos. */
object Thumbnails {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }
    suspend fun get(resolver: ContentResolver, uri: Uri): Bitmap? {
        val key = uri.toString()
        synchronized(cache) { cache.get(key)?.let { return it } }
        val decoded = runCatching {
            withContext(Dispatchers.IO) { BitmapLoader.decode(resolver, uri, 440).first }
        }.getOrNull() ?: return null
        synchronized(cache) {
            cache.get(key)?.let { previous ->
                if (previous !== decoded) decoded.recycle()
                return previous
            }
            cache.put(key, decoded)
        }
        return decoded
    }
}
