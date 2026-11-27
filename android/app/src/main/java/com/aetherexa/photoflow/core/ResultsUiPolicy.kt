package com.aetherexa.photoflow.core

/** Results are visual-first. Never present zero-result category shortcuts. */
object ResultsUiPolicy {
    fun categoryShortcuts(categories: Collection<Category>): List<String> {
        val available = categories.toSet()
        return listOf("All") + Category.entries.filter { it in available }.map { it.display }
    }

    /** Searching and filtering do not imply permission to share or move any original. */
    fun canActOnSelection(selectionMode: Boolean, hasCheckedPhotos: Boolean): Boolean =
        selectionMode && hasCheckedPhotos
}
