package com.aetherexa.photoflow.core

import org.junit.Assert.*
import org.junit.Test

class ResultsUiPolicyTest {
    @Test fun quickFiltersExcludeEmptyCategories() {
        assertEquals(listOf("All", "Payment screenshots", "Screenshots"),
            ResultsUiPolicy.categoryShortcuts(listOf(Category.SCREENSHOTS,
                Category.SCREENSHOTS, Category.PAYMENTS)))
    }

    @Test fun selectingNothingCannotPerformActions() {
        assertFalse(ResultsUiPolicy.canActOnSelection(false, false))
        assertFalse(ResultsUiPolicy.canActOnSelection(false, true))
        assertFalse(ResultsUiPolicy.canActOnSelection(true, false))
        assertTrue(ResultsUiPolicy.canActOnSelection(true, true))
    }
}
