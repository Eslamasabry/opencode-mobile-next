package io.github.eslamasabry.oc.engine.runtime.recovery

import io.github.eslamasabry.oc.engine.runtime.lifecycle.LifecycleReport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Upgrade test 2026-11-09: a force stop by the person showed "Android closed OpenCode Mobile" on the next start. */
class ExitNoticeRuleTest {
    @Test
    fun `EXIT-NOTICE-010 only a system kill or a crash is announced`() {
        val announced = PreviousExitKind.entries.filter { it.announced }.toSet()
        assertEquals(
            setOf(
                PreviousExitKind.LowMemory, PreviousExitKind.MemoryLimit, PreviousExitKind.Killed,
                PreviousExitKind.Crash,
            ),
            announced,
        )
        for (kind in listOf(PreviousExitKind.ForceStop, PreviousExitKind.Update, PreviousExitKind.Normal)) {
            assertFalse(kind.announced, kind.name)
        }
    }

    @Test
    fun `EXIT-NOTICE-011 the This phone notice stays quiet after the person's force stop`() {
        assertFalse(LifecycleReport(exitKind = PreviousExitKind.ForceStop, hadServices = true).showNotice)
        assertFalse(LifecycleReport(exitKind = PreviousExitKind.Update, hadServices = true).showNotice)
        assertFalse(LifecycleReport(exitKind = PreviousExitKind.Normal, hadServices = true).showNotice)
        assertTrue(LifecycleReport(exitKind = PreviousExitKind.LowMemory, hadServices = true).showNotice)
        assertTrue(LifecycleReport(exitKind = PreviousExitKind.Killed, hadServices = true).showNotice)
        assertFalse(LifecycleReport(exitKind = PreviousExitKind.LowMemory, hadServices = false).showNotice)
    }
}
