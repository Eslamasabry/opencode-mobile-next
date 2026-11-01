package io.github.eslamasabry.oc.android.migration

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** Plan 05 §10.5 J2 / J8 / J1c controls, compiled only into the `qa` build type. */
class QaStepInterrupterTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `B-MIGR-010 qa-kill-after kills once at the named point and is consumed first, so the relaunch runs through`() {
        val paths = MigrationPaths(tmp.newFolder()).apply { nativeDir.mkdirs() }
        paths.qaKillAfter.writeText("M2:1\n")
        val kills = mutableListOf<Boolean>()
        val qa = QaStepInterrupter(paths) { kills += paths.qaKillAfter.exists() }
        qa.reached("M1")
        assertEquals(emptyList(), kills)
        qa.reached("M2:1")
        assertEquals(listOf(false), kills) // the file was deleted before the kill
        qa.reached("M2:1")
        assertEquals(1, kills.size)
    }

    @Test
    fun `qa-delay-ms and qa-free-bytes are read as numbers and anything else is ignored`() {
        val paths = MigrationPaths(tmp.newFolder()).apply { nativeDir.mkdirs() }
        val qa = QaStepInterrupter(paths) {}
        assertEquals(0L, qa.delayMs())
        assertNull(qa.freeBytesOverride())
        paths.qaDelayMs.writeText("5000")
        paths.qaFreeBytes.writeText("1024")
        assertEquals(5000L, qa.delayMs())
        assertEquals(1024L, qa.freeBytesOverride())
        paths.qaFreeBytes.writeText("lots")
        assertNull(qa.freeBytesOverride())
        assertFalse(StepInterrupters.forBuild(paths) === StepInterrupter.None)
    }
}
