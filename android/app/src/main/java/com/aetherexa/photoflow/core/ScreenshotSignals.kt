package com.aetherexa.photoflow.core

/** Screen-like images are OCR candidates. A receipt requires additional text evidence. */
object ScreenshotSignals {
    private val names = Regex("""(?i)(screenshot|screen[_ .-]?shot|screen[_ .-]?capture|screencap|payment[_ .-]?proof|transaction[_ .-]?receipt|gpay|phonepe)""")
    private val ui = Regex("""(?i)\b(notification|settings|search|payment|transaction|upi|account|chat|messages|home|activity|transfer|balance|history|successful|paid|received|share|merchant)\b""")

    fun namedScreenshot(name: String): Boolean = names.containsMatchIn(name)

    fun shouldReadText(name: String, width: Int, height: Int, cameraModel: String?): Boolean {
        if (namedScreenshot(name)) return true
        // Avoid OCR on normal camera images. Many forwarded screenshots are JPG without EXIF.
        if (cameraModel != null || width < 450 || height < 700 || height < width * 1.35) return false
        return name.substringAfterLast('.', "").lowercase() in setOf("png", "jpg", "jpeg", "webp")
    }

    fun recognizedScreenshot(name: String, lines: List<String>): Boolean {
        if (namedScreenshot(name)) return true
        if (lines.size < 5) return false
        return lines.count { ui.containsMatchIn(it) } >= 3
    }
}
