package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.runtime.services.kept.KeptAct
import io.github.eslamasabry.oc.engine.runtime.services.kept.KeptService
import io.github.eslamasabry.oc.engine.runtime.services.kept.KeptServiceDef
import io.github.eslamasabry.oc.engine.runtime.services.kept.KeptServices
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxLifecycle
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxResetFlow
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxResetMarker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

class LinuxStartupTest {
    private class Marker(var value: String?) : LinuxResetMarker {
        override suspend fun read(): String? = value

        override suspend fun write(value: String) {
            this.value = value
        }

        override suspend fun clear() {
            value = null
        }

        override suspend fun forgetReplySpeed() = Unit
    }

    private class Lifecycle : LinuxLifecycle {
        val calls = mutableListOf<String>()
        var isInstalled = false

        override suspend fun installed(): Boolean = isInstalled

        override suspend fun erase(): Outcome<Unit> = Outcome.Ok(Unit).also { calls += "erase" }

        override suspend fun startSetup(): Outcome<Boolean> = Outcome.Ok(true).also { calls += "setup" }

        override suspend fun repair(): Outcome<Unit> = Outcome.Ok(Unit)
    }

    /** Counts supervisor starts; every other kept-services call is unused at app start. */
    private class Kept : KeptServices {
        var supervisorStarts = 0

        override suspend fun read(): Outcome<List<KeptService>> = Outcome.Ok(emptyList())

        override suspend fun save(definition: KeptServiceDef): Outcome<Unit> = Outcome.Ok(Unit)

        override suspend fun act(name: String, action: KeptAct): Outcome<Unit> = Outcome.Ok(Unit)

        override suspend fun log(name: String): Outcome<String> = Outcome.Ok("")

        override suspend fun copyLog(name: String): Outcome<Unit> = Outcome.Ok(Unit)

        override suspend fun ensureSupervisor(): Outcome<Unit> {
            supervisorStarts++
            return Outcome.Ok(Unit)
        }
    }

    @Test
    fun `B-LINUX-093 a reset killed while erasing is finished when the app starts`() = runTest {
        val marker = Marker("erasing 2026-11-03T05:00:00Z")
        val lifecycle = Lifecycle()
        val kept = Kept()
        LinuxStartup(
            LinuxResetFlow(lifecycle, marker, Clock.System),
            AppCoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
            kept,
        ).start()
        testScheduler.runCurrent()
        assertEquals(listOf("erase", "setup"), lifecycle.calls)
        assertNull(marker.value)
        assertEquals(1, kept.supervisorStarts)
    }

    @Test
    fun `B-LINUX-093 no marker means nothing runs at start`() = runTest {
        val lifecycle = Lifecycle()
        val kept = Kept()
        LinuxStartup(
            LinuxResetFlow(lifecycle, Marker(null), Clock.System),
            AppCoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
            kept,
        ).start()
        testScheduler.runCurrent()
        assertEquals(emptyList(), lifecycle.calls)
        // The kept-services supervisor starts at every app start, reset or not.
        assertEquals(1, kept.supervisorStarts)
    }
}
