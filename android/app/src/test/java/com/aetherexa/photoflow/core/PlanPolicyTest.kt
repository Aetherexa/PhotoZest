package com.aetherexa.photoflow.core
import org.junit.Assert.*
import org.junit.Test

class PlanPolicyTest {
    @Test fun freeTierHasFiveCategoriesAnd100PhotoBatchLimit() {
        val p = PlanPolicy.forTier(PhotoFlowTier.FREE)
        assertEquals(100, p.maxImagesPerBatch)
        assertEquals(5, p.maxPrimaryCategories)
        assertTrue(p.accepts(100))
        assertFalse(p.accepts(101))
        assertFalse(p.accepts(-1))
        assertFalse(p.supports(PremiumFeature.CATEGORY_SEARCH))
        assertFalse(p.supports(PremiumFeature.PAYMENT_OCR_BASIC))
    }

    @Test fun plusHasSevenCategoriesSearchAndNoOcr() {
        val p = PlanPolicy.forTier(PhotoFlowTier.PLUS)
        assertEquals(300, p.maxImagesPerBatch)
        assertEquals(7, p.maxPrimaryCategories)
        assertTrue(p.supports(PremiumFeature.CATEGORY_SEARCH))
        assertFalse(p.supports(PremiumFeature.EDIT_METADATA))
        assertFalse(p.supports(PremiumFeature.LOCATION_SORTING))
        assertFalse(p.supports(PremiumFeature.PAYMENT_OCR_BASIC))
    }

    @Test fun proProvidesMetadataLocationTagsAndBasicOcr() {
        val p = PlanPolicy.forTier(PhotoFlowTier.PRO)
        assertEquals(500, p.maxImagesPerBatch)
        assertEquals(15, p.maxPrimaryCategories)
        assertEquals(30, p.maxCustomTagsPerPhoto)
        assertTrue(p.supports(PremiumFeature.EDIT_METADATA))
        assertTrue(p.supports(PremiumFeature.LOCATION_SORTING))
        assertTrue(p.supports(PremiumFeature.CUSTOM_TAGS))
        assertTrue(p.supports(PremiumFeature.PAYMENT_OCR_BASIC))
        assertFalse(p.supports(PremiumFeature.PAYMENT_OCR_ADVANCED))
        assertFalse(p.supports(PremiumFeature.NATURAL_LANGUAGE_SEARCH))
    }

    @Test fun maxSupportsUnboundedCommercialQuotasAndAdvancedSearch() {
        val p = PlanPolicy.forTier(PhotoFlowTier.MAX)
        assertNull(p.maxImagesPerBatch)
        assertNull(p.maxPrimaryCategories)
        assertNull(p.maxCustomTagsPerPhoto)
        assertTrue(p.accepts(1_000_000))
        assertTrue(p.supports(PremiumFeature.NATURAL_LANGUAGE_SEARCH))
        assertTrue(p.supports(PremiumFeature.CROSS_CATEGORY_BULK_SHARE))
        assertTrue(p.supports(PremiumFeature.PAYMENT_OCR_ADVANCED))
    }

    @Test fun paidTiersInheritLowerTierFeatures() {
        for (f in PlanPolicy.forTier(PhotoFlowTier.PLUS).features)
            assertTrue(PlanPolicy.forTier(PhotoFlowTier.PRO).supports(f))
        for (f in PlanPolicy.forTier(PhotoFlowTier.PRO).features)
            assertTrue(PlanPolicy.forTier(PhotoFlowTier.MAX).supports(f))
    }

    @Test fun invalidProductIdFallsBackToFree() {
        assertEquals(PhotoFlowTier.FREE, PlanPolicy.fromVerifiedProductId(null))
        assertEquals(PhotoFlowTier.FREE, PlanPolicy.fromVerifiedProductId("forged_max"))
        assertEquals(PhotoFlowTier.PLUS, PlanPolicy.fromVerifiedProductId("photoflow_plus"))
        assertEquals(PhotoFlowTier.MAX, PlanPolicy.fromVerifiedProductId("photoflow_max"))
        assertTrue(PlanPolicy.isSafetyBucket(Category.REVIEW))
    }
}
