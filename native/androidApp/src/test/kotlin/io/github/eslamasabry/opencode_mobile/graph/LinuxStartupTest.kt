package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.Outcome
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

    @Test
    fun `B-LINUX-093 a reset killed while erasing is finished when the app starts`() = runTest {
        val marker = Marker("erasing 2026-11-03T05:00:00Z")
        val lifecycle = Lifecycle()
        LinuxStartup(
            LinuxResetFlow(lifecycle, marker, Clock.System),
            AppCoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
        ).start()
        testScheduler.runCurrent()
        assertEquals(listOf("erase", "setup"), lifecycle.calls)
        assertNull(marker.value)
    }

    @Test
    fun `B-LINUX-093 no marker means nothing runs at start`() = runTest {
        val lifecycle = Lifecycle()
        LinuxStartup(
            LinuxResetFlow(lifecycle, Marker(null), Clock.System),
            AppCoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
        ).start()
        testScheduler.runCurrent()
        assertEquals(emptyList(), lifecycle.calls)
    }
}
