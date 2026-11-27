package com.aetherexa.photoflow.core

import org.junit.Assert.*
import org.junit.Test

class PhotoTagEngineTest {
    @Test fun mumbaiFromGps() {
        assertEquals("Mumbai", OfflineCityIndex.approximateCity(19.076, 72.8777))
    }

    @Test fun puneFromGps() {
        assertEquals("Pune", OfflineCityIndex.approximateCity(18.5204, 73.8567))
    }

    @Test fun unknownCityDoesNotInventLocation() {
        assertNull(OfflineCityIndex.approximateCity(0.0, 0.0))
        assertNull(OfflineCityIndex.approximateCity(91.0, 0.0))
        assertEquals("Location unknown", PhotoMetadata().location)
    }

    @Test fun exifDateIsParsedInCameraLocalTime() {
        assertEquals("2026-11-24 18:42:07", PhotoTagEngine.parseExifDate("2026:11:24 18:42:07"))
        assertEquals("Evening", PhotoTagEngine.timeOfDay("2026-11-24 18:42:07"))
        assertNull(PhotoTagEngine.parseExifDate("invalid"))
    }

    @Test fun timeBucketsDoNotMistakeImageBrightnessForCaptureTime() {
        assertEquals("Night", PhotoTagEngine.timeOfDay("2026-10-03 22:05:00"))
        assertEquals("Morning", PhotoTagEngine.timeOfDay("2026-10-03 09:04:00"))
        assertEquals("Afternoon", PhotoTagEngine.timeOfDay("2026-10-03 14:04:00"))
        assertNull(PhotoTagEngine.timeOfDay(null))
    }

    @Test fun oneImageMayHaveMultipleVirtualLabels() {
        val tags = PhotoTagEngine.derivedTags(
            Category.LANDSCAPES, setOf(SmartTag.SOCIAL_READY),
            PhotoMetadata(
                capturedAt = "2026-11-24 18:42:07",
                approximateCity = "Mumbai", cameraModel = "Pixel 8"
            ),
            setOf("Sunset scene", "Landscape orientation"),
            setOf("Trip with friends")
        )
        assertTrue(tags.containsAll(setOf("Mumbai", "Evening", "2026-11-24", "2026-11", "Landscape orientation", "Sunset scene", "Trip with friends", "Social-ready", "Pixel 8")))
    }

    @Test fun modelTagsNeedConfidenceEvidence() {
        assertTrue(PhotoTagEngine.sceneTags(mapOf("Beach" to 0.90f)).contains("Beach"))
        assertFalse(PhotoTagEngine.sceneTags(mapOf("Beach" to 0.40f)).contains("Beach"))
    }

    @Test fun qualityMeasurementsAreClearlyHints() {
        val tags = PhotoTagEngine.qualityTags(0.12, 0.10, 0.30, 0.34, 3000, 2000)
        assertTrue(tags.containsAll(setOf("Low light", "Low contrast", "Bright highlights", "Dark shadows", "Landscape orientation", "High resolution")))
    }
}
