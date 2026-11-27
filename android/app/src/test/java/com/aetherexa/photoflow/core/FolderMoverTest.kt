package com.aetherexa.photoflow.core

import com.aetherexa.photoflow.data.FolderMover
import org.junit.Assert.assertEquals
import org.junit.Test

class FolderMoverTest {
    @Test fun sameExternalVolumeDifferentFolders() {
        assertEquals("primary", FolderMover.storageVolume("primary:DCIM/Trip"))
        assertEquals("439A-10BF", FolderMover.storageVolume("439A-10BF:Photos"))
    }

    @Test fun namesAreSafeAndBounded() {
        assertEquals("Trip", FolderMover.safeFolderName(""))
        assertEquals("Holiday-2026-A", FolderMover.safeFolderName("Holiday/2026:A"))
        assertEquals(58, FolderMover.safeFolderName("a".repeat(90)).length)
    }
}
