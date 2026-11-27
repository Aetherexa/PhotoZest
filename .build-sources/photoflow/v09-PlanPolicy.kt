package com.aetherexa.photoflow.core

/**
 * Product entitlements only. This policy MUST receive an authenticated plan
 * from a purchase-verification service, never a user-editable preference.
 */
enum class PhotoFlowTier { FREE, PLUS, PRO, MAX }

enum class PremiumFeature {
    CATEGORY_SEARCH, VIEW_EXIF, EDIT_METADATA, LOCATION_SORTING, CUSTOM_TAGS,
    PAYMENT_OCR_BASIC, PAYMENT_OCR_ADVANCED, NATURAL_LANGUAGE_SEARCH,
    CROSS_CATEGORY_BULK_SHARE, ADVANCED_CLASSIFICATION, HIGH_PRECISION_CLASSIFICATION
}

data class TierCapabilities(
    val tier: PhotoFlowTier,
    val maxImagesPerBatch: Int?,
    val maxPrimaryCategories: Int?,
    val maxCustomTagsPerPhoto: Int?,
    val features: Set<PremiumFeature>
) {
    fun supports(feature: PremiumFeature) = feature in features
    fun accepts(count: Int): Boolean =
        count >= 0 && (maxImagesPerBatch == null || count <= maxImagesPerBatch)
}

object PlanPolicy {
    private val plusFeatures = setOf(PremiumFeature.CATEGORY_SEARCH)
    private val proFeatures = plusFeatures + setOf(
        PremiumFeature.VIEW_EXIF, PremiumFeature.EDIT_METADATA,
        PremiumFeature.LOCATION_SORTING, PremiumFeature.CUSTOM_TAGS,
        PremiumFeature.PAYMENT_OCR_BASIC, PremiumFeature.ADVANCED_CLASSIFICATION
    )
    private val maxFeatures = proFeatures + setOf(
        PremiumFeature.PAYMENT_OCR_ADVANCED, PremiumFeature.NATURAL_LANGUAGE_SEARCH,
        PremiumFeature.CROSS_CATEGORY_BULK_SHARE, PremiumFeature.HIGH_PRECISION_CLASSIFICATION
    )
    private val capabilities = mapOf(
        PhotoFlowTier.FREE to TierCapabilities(PhotoFlowTier.FREE, 100, 5, 0, emptySet()),
        PhotoFlowTier.PLUS to TierCapabilities(PhotoFlowTier.PLUS, 300, 7, 0, plusFeatures),
        PhotoFlowTier.PRO to TierCapabilities(PhotoFlowTier.PRO, 500, 15, 30, proFeatures),
        PhotoFlowTier.MAX to TierCapabilities(PhotoFlowTier.MAX, null, null, null, maxFeatures)
    )
    fun forTier(tier: PhotoFlowTier): TierCapabilities = requireNotNull(capabilities[tier])
    /** Review is a mandatory safety bucket, not a commercial category. */
    fun isSafetyBucket(category: Category) = category == Category.REVIEW
    /** Caller must verify Play purchase token BEFORE passing its product ID. */
    fun fromVerifiedProductId(productId: String?): PhotoFlowTier = when (productId) {
        "photoflow_plus" -> PhotoFlowTier.PLUS
        "photoflow_pro" -> PhotoFlowTier.PRO
        "photoflow_max" -> PhotoFlowTier.MAX
        else -> PhotoFlowTier.FREE
    }
}
