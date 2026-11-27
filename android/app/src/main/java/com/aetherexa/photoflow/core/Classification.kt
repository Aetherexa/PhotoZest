package com.aetherexa.photoflow.core

import kotlin.math.abs

/** A single physical destination, with independent searchable labels and quality suggestions. */
enum class Category(val display: String, val folder: String) {
    LANDSCAPES("Landscapes", "Landscapes"),
    GROUP("Group photos", "Group Photos"),
    SELFIES("Selfies", "Selfies"),
    CANDIDS("Candids", "Candids"),
    FOOD("Food", "Food"),
    PORTRAITS("Portraits", "Portraits"),
    PAYMENTS("Payment screenshots", "Payment Receipts"),
    SCREENSHOTS("Screenshots", "Screenshots"),
    REVIEW("Needs review", "Needs Review")
}

enum class SmartTag(val display: String, val folder: String) {
    SOCIAL_READY("Social-ready", "Social Ready"),
    FRAME_WORTHY("Frame-worthy", "Frame Worthy"),
    MAYBE_BLURRED("Possibly blurred", "Possibly Blurred"),
    MAYBE_DUPLICATE("Possible duplicate", "Possible Duplicates")
}

data class ClassificationSignals(
    val width: Int,
    val height: Int,
    val faceCount: Int,
    val biggestFaceFraction: Double,
    val sidewaysFace: Boolean,
    val labels: Map<String, Float>,
    val sharpness: Double,
    val brightness: Double,
    // Optional cues can be measured without changing previously persisted images.
    val contrast: Double = 0.30,
    val clippedHighlights: Double = 0.0,
    val blockedShadows: Double = 0.0,
    val biggestFaceCenterX: Double = 0.50
)

data class Classification(val category: Category, val confidence: Float, val reason: String, val tags: Set<SmartTag>)

/** Conservative, explainable fusion of ML Kit labels, face geometry and local quality metrics.
 *  ML Kit confidence is evidence, not a calibrated probability of a correct category.
 */
object CategoryEngine {
    private val foodTerms = setOf("food", "dish", "meal", "lunch", "dinner", "breakfast", "dessert", "pizza", "cake", "bread", "salad", "fruit", "vegetable", "sushi", "pasta", "pastry", "burger", "sandwich", "noodle", "cuisine")
    private val sceneryTerms = setOf("landscape", "mountain", "mountainous", "forest", "beach", "lake", "river", "waterfall", "nature", "seaside", "sea", "sunset", "sunrise", "countryside", "valley", "desert", "scenery", "ocean", "coast")

    private fun score(labels: Map<String, Float>, terms: Set<String>): Float {
        val matches = labels.entries.mapNotNull { (raw, conf) ->
            if (!conf.isFinite()) null else {
                // Token-boundaries avoid e.g. "sea" matching "research".
                val normalized = raw.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
                val tokens = normalized.split(' ').filter { it.isNotBlank() }
                if (tokens.any { it in terms } || normalized in terms) conf.coerceIn(0f, 1f) else null
            }
        }.sortedDescending()
        return ((matches.firstOrNull() ?: 0f) + (matches.getOrNull(1) ?: 0f) * 0.08f).coerceAtMost(1f)
    }

    fun classify(s: ClassificationSignals): Classification {
        val food = score(s.labels, foodTerms)
        val scenic = score(s.labels, sceneryTerms)
        val isLowLight = s.brightness < 0.16
        val blurred = s.sharpness < (if (isLowLight) 0.15 else 0.19)
        val clipped = s.clippedHighlights > 0.22 || s.blockedShadows > 0.36
        val wellExposed = s.brightness in 0.19..0.88 && !clipped && s.contrast >= 0.10
        val highRes = minOf(s.width, s.height) >= 1000
        val tags = buildSet {
            if (blurred) add(SmartTag.MAYBE_BLURRED)
            if (!blurred && s.sharpness >= 0.43 && wellExposed && highRes) add(SmartTag.SOCIAL_READY)
            if (!blurred && s.sharpness >= 0.51 && wellExposed && highRes && scenic >= 0.69f)
                add(SmartTag.FRAME_WORTHY)
        }
        // Multiple detected faces outweigh a food background label. A group dining photo
        // should not be silently relocated to the Food directory.
        if (s.faceCount >= 2) return Classification(Category.GROUP, 0.84f,
            "${s.faceCount} faces detected; multiple people take priority over scene labels", tags)
        if (s.faceCount == 1) {
            val centered = s.biggestFaceCenterX in 0.26..0.74
            if (s.biggestFaceFraction >= 0.23 && centered && !s.sidewaysFace)
                return Classification(Category.SELFIES, 0.53f, "Centered close face; selfie suggestion, verify manually", tags)
            if (s.sidewaysFace && s.biggestFaceFraction <= 0.21)
                return Classification(Category.CANDIDS, 0.45f, "Side-facing subject; candid suggestion", tags)
            return Classification(Category.PORTRAITS, 0.71f, "One detected face", tags)
        }
        // No hard guessing when a scene is ambiguous. Original remains in Needs Review.
        if (food >= 0.68f && scenic >= 0.68f && abs(food - scenic) < 0.14f)
            return Classification(Category.REVIEW, 0.45f, "Conflicting food and scenery labels; review required", tags)
        if (food >= 0.68f && food > scenic) return Classification(Category.FOOD, food,
            "Food-related image labels", tags)
        if (scenic >= 0.67f && scenic >= food) return Classification(Category.LANDSCAPES, scenic,
            "Scenery-related image labels", tags)
        return Classification(Category.REVIEW, 0f,
            if (s.labels.isEmpty()) "No reliable on-device labels" else "Labels not reliable enough for auto sorting", tags)
    }

    fun looksLikeDuplicate(hashA: Long, hashB: Long, ratioA: Double, ratioB: Double): Boolean =
        ratioA.isFinite() && ratioB.isFinite() && abs(ratioA - ratioB) <= 0.06 &&
            (hashA xor hashB).countOneBits() <= 4
}
