package com.aetherexa.photoflow.data

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/** Bounded on-device preprocessing used only if the first OCR pass finds too little text. */
object ScreenshotImageProcessor {
    suspend fun improveContrast(original: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        val scale = min(1.0, 1750.0 / max(original.width, original.height))
        val w = (original.width * scale).toInt().coerceAtLeast(1)
        val h = (original.height * scale).toInt().coerceAtLeast(1)
        val base = Bitmap.createScaledBitmap(original, w, h, true)
        try {
            val pixels = IntArray(w * h)
            base.getPixels(pixels, 0, w, 0, 0, w, h)
            // Local contrast lift without cropping payment amounts or the receiver name.
            for (i in pixels.indices) {
                val px = pixels[i]
                val r = Color.red(px)
                val g = Color.green(px)
                val b = Color.blue(px)
                val l = (r * 0.2126 + g * 0.7152 + b * 0.0722).toInt()
                val boosted = ((l - 128) * 1.35 + 128).toInt().coerceIn(0, 255)
                pixels[i] = Color.argb(Color.alpha(px), boosted, boosted, boosted)
            }
            Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
        } finally {
            if (base !== original) base.recycle()
        }
    }
}
