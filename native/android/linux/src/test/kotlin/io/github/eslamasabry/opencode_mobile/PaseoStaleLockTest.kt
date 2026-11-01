package io.github.eslamasabry.opencode_mobile

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaseoStaleLockTest {
    private val home: File = Files.createTempDirectory("paseo-home-").toFile()
    private val lock = File(home, PaseoStaleLock.FILE)

    @After fun cleanUp() { home.deleteRecursively() }

    @Test fun lockLeftByAKilledSupervisorIsRemovedBeforeTheNextLaunch() {
        lock.writeText("{\"pid\":1}")
        assertTrue(PaseoStaleLock.clear(home, helperRunning = false) { false })
        assertFalse(lock.exists())
    }

    @Test fun runningHelperKeepsItsLockAndThePortIsNotProbed() {
        lock.writeText("{\"pid\":1}")
        var probed = false
        assertFalse(PaseoStaleLock.clear(home, helperRunning = true) { probed = true; false })
        assertTrue(lock.exists())
        assertFalse(probed)
    }

    @Test fun helperAnsweringOnThePortKeepsItsLock() {
        lock.writeText("{\"pid\":1}")
        assertFalse(PaseoStaleLock.clear(home, helperRunning = false) { true })
        assertTrue(lock.exists())
    }

    @Test fun missingLockNeedsNoProbe() {
        var probed = false
        assertFalse(PaseoStaleLock.clear(home, helperRunning = false) { probed = true; false })
        assertFalse(probed)
    }

    @Test fun linkInPlaceOfTheLockIsNeitherFollowedNorRemoved() {
        val outside = File(Files.createTempDirectory("outside-").toFile(), "kept").apply { writeText("kept") }
        try {
            Files.createSymbolicLink(lock.toPath(), outside.toPath())
            assertFalse(PaseoStaleLock.clear(home, helperRunning = false) { false })
            assertTrue(Files.isSymbolicLink(lock.toPath()))
            assertEquals("kept", outside.readText())
        } finally { outside.parentFile.deleteRecursively() }
    }
}
