package com.aetherexa.photoflow.data

import android.content.ContentResolver
import androidx.exifinterface.media.ExifInterface
import com.aetherexa.photoflow.core.OfflineCityIndex
import com.aetherexa.photoflow.core.PhotoMetadata
import com.aetherexa.photoflow.core.PhotoTagEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads embedded metadata only. Never rewrites a JPEG, transmits GPS, or requests device location. */
object ImageMetadataReader {
    suspend fun read(resolver: ContentResolver, source: PhotoSource): PhotoMetadata = withContext(Dispatchers.IO) {
        runCatching {
            resolver.openInputStream(source.uri)?.use { stream ->
                val exif = ExifInterface(stream)
                val timePairs = listOf(
                    "Original" to exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL),
                    "Digitized" to exif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED),
                    "Modified" to exif.getAttribute(ExifInterface.TAG_DATETIME)
                )
                val picked = timePairs.firstOrNull { PhotoTagEngine.parseExifDate(it.second) != null }
                val coords = exif.latLong
                val lat = coords?.getOrNull(0)?.takeIf { it in -90.0..90.0 }
                val lng = coords?.getOrNull(1)?.takeIf { it in -180.0..180.0 }
                val iso = exif.getAttributeInt(ExifInterface.TAG_ISO_SPEED_RATINGS, -1)
                    .takeIf { it > 0 }
                PhotoMetadata(
                    capturedAt = PhotoTagEngine.parseExifDate(picked?.second)
                        ?: PhotoTagEngine.parseScreenshotFilename(source.name),
                    timeSource = picked?.first
                        ?: if (PhotoTagEngine.parseScreenshotFilename(source.name) != null) "Screenshot filename" else null,
                    latitude = lat,
                    longitude = lng,
                    approximateCity = if (lat != null && lng != null) OfflineCityIndex.approximateCity(lat, lng) else null,
                    cameraMake = exif.getAttribute(ExifInterface.TAG_MAKE)?.trim()?.take(60)?.ifBlank { null },
                    cameraModel = exif.getAttribute(ExifInterface.TAG_MODEL)?.trim()?.take(80)?.ifBlank { null },
                    iso = iso,
                    aperture = exif.getAttribute(ExifInterface.TAG_F_NUMBER)?.take(20),
                    exposureSeconds = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.take(20),
                    focalLength = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)?.take(20)
                )
            }
        }.getOrNull() ?: PhotoMetadata(
            capturedAt = PhotoTagEngine.parseScreenshotFilename(source.name),
            timeSource = if (PhotoTagEngine.parseScreenshotFilename(source.name) != null) "Screenshot filename" else null
        )
    }
}
