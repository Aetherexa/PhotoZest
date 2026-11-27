package com.aetherexa.photoflow.core

import org.junit.Assert.*
import org.junit.Test

class CategoryEngineTest {
    private fun signals(
        faces: Int = 0, faceFraction: Double = 0.0,
        sideways: Boolean = false, labels: Map<String, Float> = emptyMap(),
        sharpness: Double = 0.6
    ) = ClassificationSignals(2400, 1600, faces, faceFraction, sideways, labels, sharpness, 0.5)

    @Test fun uncertainImageGoesToReview() {
        assertEquals(Category.REVIEW, CategoryEngine.classify(signals()).category)
    }
    @Test fun groupIsDetectedFromFaces() {
        assertEquals(Category.GROUP, CategoryEngine.classify(signals(faces = 4)).category)
    }
    @Test fun foodIsDetectedWhenHighConfidence() {
        assertEquals(Category.FOOD, CategoryEngine.classify(signals(labels = mapOf("Food" to 0.91f))).category)
    }
    @Test fun lowFoodConfidenceDoesNotInventCategory() {
        assertEquals(Category.REVIEW, CategoryEngine.classify(signals(labels = mapOf("Food" to 0.54f))).category)
    }
    @Test fun sceneryCanBeFrameWorthy() {
        val classified = CategoryEngine.classify(signals(labels = mapOf("Mountain" to 0.96f)))
        assertEquals(Category.LANDSCAPES, classified.category)
        assertTrue(classified.tags.contains(SmartTag.FRAME_WORTHY))
    }
    @Test fun blurTagIsOnlyASuggestion() {
        val classified = CategoryEngine.classify(signals(sharpness = 0.05))
        assertTrue(classified.tags.contains(SmartTag.MAYBE_BLURRED))
    }
    @Test fun selfieIsLowConfidence() {
        assertEquals(Category.SELFIES, CategoryEngine.classify(signals(faces = 1, faceFraction = 0.31)).category)
    }
    @Test fun twoFacesTakePrecedenceOverDiningLabel() {
        val result = CategoryEngine.classify(signals(faces = 3, labels = mapOf("Food" to 0.99f)))
        assertEquals(Category.GROUP, result.category)
    }
    @Test fun conflictingSceneLabelsGoToReview() {
        val result = CategoryEngine.classify(signals(labels = mapOf("Food" to 0.90f, "Mountain" to 0.89f)))
        assertEquals(Category.REVIEW, result.category)
    }
    @Test fun shortWordDoesNotMatchInsideUnrelatedLabel() {
        val result = CategoryEngine.classify(signals(labels = mapOf("research" to 0.99f)))
        assertEquals(Category.REVIEW, result.category)
    }
    @Test fun severeClippingPreventsSocialReadySuggestion() {
        val result = CategoryEngine.classify(signals(sharpness = 0.9).copy(clippedHighlights = 0.50))
        assertFalse(SmartTag.SOCIAL_READY in result.tags)
    }
    @Test fun offCenterFaceShouldNotBeAssumedSelfie() {
        val result = CategoryEngine.classify(signals(faces = 1, faceFraction = 0.40).copy(biggestFaceCenterX = 0.02))
        assertEquals(Category.PORTRAITS, result.category)
    }
    @Test fun duplicatesRequireSimilarAspectRatio() {
        assertTrue(CategoryEngine.looksLikeDuplicate(15L, 14L, 1.50, 1.52))
        assertFalse(CategoryEngine.looksLikeDuplicate(15L, 14L, 1.50, 2.0))
    }
}
