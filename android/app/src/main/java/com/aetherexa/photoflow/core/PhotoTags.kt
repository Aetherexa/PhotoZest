package com.aetherexa.photoflow.core

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** The camera's local wall-clock time has no reliable time-zone unless offset metadata exists. */
data class PhotoMetadata(
    val capturedAt: String? = null, // yyyy-MM-dd HH:mm:ss, camera local time
    val timeSource: String? = null,  // DateTimeOriginal, DateTimeDigitized, DateTime
    val latitude: Double? = null,
    val longitude: Double? = null,
    val approximateCity: String? = null,
    val cameraMake: String? = null,
    val cameraModel: String? = null,
    val iso: Int? = null,
    val aperture: String? = null,
    val exposureSeconds: String? = null,
    val focalLength: String? = null
) {
    val timeOfDay: String? get() = PhotoTagEngine.timeOfDay(capturedAt)
    val month: String? get() = capturedAt?.take(7)
    val date: String? get() = capturedAt?.take(10)
    val location: String get() = approximateCity ?: PhotoTagEngine.UNKNOWN_LOCATION
    val hasGps: Boolean get() = latitude != null && longitude != null
}

/** City identification without uploading precise coordinates to any geocoding service. */
object OfflineCityIndex {
    private data class Place(val name: String, val lat: Double, val lng: Double, val radiusKm: Double)
    // Approximate urban centers, not administrative polygons. Don't label distant trips as cities.
    private val places = listOf(
        Place("Mumbai", 19.0760, 72.8777, 25.0),
        Place("Thane", 19.2183, 72.9781, 13.0),
        Place("Navi Mumbai", 19.0330, 73.0297, 17.0),
        Place("Pune", 18.5204, 73.8567, 28.0),
        Place("Nashik", 19.9975, 73.7898, 20.0),
        Place("Lonavala", 18.7546, 73.4062, 10.0),
        Place("Alibaug", 18.6414, 72.8722, 12.0),
        Place("Nagpur", 21.1458, 79.0882, 24.0),
        Place("Ujjain", 23.1765, 75.7885, 14.0),
        Place("Indore", 22.7196, 75.8577, 21.0),
        Place("Hyderabad", 17.3850, 78.4867, 32.0),
        Place("Bengaluru", 12.9716, 77.5946, 37.0),
        Place("Delhi", 28.6139, 77.2090, 35.0),
        Place("Jaipur", 26.9124, 75.7873, 24.0),
        Place("Kolkata", 22.5726, 88.3639, 28.0),
        Place("Goa - Panaji", 15.4909, 73.8278, 12.0)
    )

    fun approximateCity(lat: Double, lng: Double): String? {
        if (!lat.isFinite() || !lng.isFinite() || lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
        return places.map { it to distanceKm(lat, lng, it.lat, it.lng) }
            .filter { (city, distance) -> distance <= city.radiusKm }
            .minByOrNull { it.second }?.first?.name
    }

    private fun distanceKm(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Double {
        val latDiff = Math.toRadians(bLat - aLat)
        val lngDiff = Math.toRadians(bLng - aLng)
        val h = sin(latDiff / 2).pow(2.0) + cos(Math.toRadians(aLat)) *
            cos(Math.toRadians(bLat)) * sin(lngDiff / 2).pow(2.0)
        return 6371.0 * 2.0 * asin(min(1.0, sqrt(h)))
    }
}

object PhotoTagEngine {
    const val UNKNOWN_LOCATION = "Location unknown"
    private val exifClock = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.ROOT)
    private val cameraClock = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)

    fun parseExifDate(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return try {
            LocalDateTime.parse(raw.trim().take(19), exifClock).format(cameraClock)
        } catch (_: DateTimeParseException) { null }
    }

    /** Screenshot filenames can carry capture time even when EXIF is absent; never use receipt time as photo time. */
    fun parseScreenshotFilename(name: String): String? {
        if (!ScreenshotSignals.namedScreenshot(name)) return null
        val matcher = Regex("""(20\d{2})[-_]?([01]\d)[-_]?([0-3]\d)[-_ T.-]*([0-2]\d)[-_:]?([0-5]\d)(?:[-_:]?([0-5]\d))?""").find(name) ?: return null
        return runCatching {
            val components = matcher.groupValues
            val time = LocalDateTime.of(components[1].toInt(), components[2].toInt(),
                components[3].toInt(), components[4].toInt(), components[5].toInt(),
                components[6].toIntOrNull() ?: 0)
            time.format(cameraClock)
        }.getOrNull()
    }

    fun timeOfDay(capturedAt: String?): String? {
        val hour = capturedAt?.takeIf { it.length >= 13 }?.substring(11, 13)?.toIntOrNull() ?: return null
        return when (hour) {
            in 4..6 -> "Early morning"
            in 7..10 -> "Morning"
            in 11..15 -> "Afternoon"
            in 16..19 -> "Evening"
            in 0..3, in 20..23 -> "Night"
            else -> null
        }
    }

    fun derivedTags(category: Category, quality: Set<SmartTag>, metadata: PhotoMetadata,
                    visual: Set<String>, manual: Set<String>): Set<String> = buildSet {
        add(category.display)
        quality.forEach { add(it.display) }
        addAll(visual)
        addAll(manual)
        metadata.approximateCity?.let { add(it) }
        metadata.timeOfDay?.let { add(it) }
        metadata.date?.let { add(it) }
        metadata.month?.let { add(it) }
        metadata.capturedAt?.take(4)?.let { add(it) }
        metadata.cameraModel?.let { add(it) }
        if (metadata.latitude != null && metadata.longitude != null) add("GPS recorded")
        if (metadata.capturedAt == null) add("Date unknown")
    }

    fun sceneTags(labels: Map<String, Float>): Set<String> = buildSet {
        for ((label, confidence) in labels) {
            if (confidence < 0.62f) continue
            val word = label.lowercase(Locale.ROOT)
            when {
                listOf("beach", "sea", "ocean").any { word.contains(it) } -> add("Beach")
                listOf("mountain", "hill", "valley").any { word.contains(it) } -> add("Mountains")
                listOf("sunset", "dusk").any { word.contains(it) } -> add("Sunset scene")
                listOf("sunrise", "dawn").any { word.contains(it) } -> add("Sunrise scene")
                listOf("forest", "tree", "nature").any { word.contains(it) } -> add("Nature")
                listOf("food", "meal", "dish", "cuisine").any { word.contains(it) } -> add("Food scene")
                listOf("building", "architecture", "city").any { word.contains(it) } -> add("Urban")
            }
        }
    }

    fun qualityTags(brightness: Double, contrast: Double, clippedHighlights: Double,
                    blockedShadows: Double, width: Int, height: Int): Set<String> = buildSet {
        if (brightness < 0.20) add("Low light")
        if (clippedHighlights > 0.24) add("Bright highlights")
        if (blockedShadows > 0.28) add("Dark shadows")
        if (contrast < 0.16) add("Low contrast")
        if (contrast > 0.54) add("High contrast")
        when {
            width > height * 1.15 -> add("Landscape orientation")
            height > width * 1.15 -> add("Portrait orientation")
            else -> add("Square format")
        }
        if (width >= 2400 && height >= 1600 || height >= 2400 && width >= 1600) add("High resolution")
    }
}
