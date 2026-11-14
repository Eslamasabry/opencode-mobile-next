@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.eslamasabry.oc.feature.phone

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.runtime.host.RuntimePhase
import io.github.eslamasabry.oc.engine.runtime.host.RuntimeStatus
import io.github.eslamasabry.oc.engine.runtime.server.ProbeAnswer
import io.github.eslamasabry.oc.feature.phone.presentation.InstalledLine
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneDetailsSource
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneDetailsUi
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneServerFacts
import io.github.eslamasabry.oc.feature.phone.presentation.ThisPhoneModel
import io.github.eslamasabry.oc.feature.phone.presentation.installedFailureShown
import io.github.eslamasabry.oc.feature.phone.presentation.installedLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** The Installed row leaves "Checking…" after a stated limit, and a late answer still arrives. */
class PhoneInstalledLimitTest {
    private val facts =
        PhoneServerFacts(
            status = RuntimeStatus(installed = true, phase = RuntimePhase.Ready, serverRunning = true),
            probe = ProbeAnswer.Answered,
            installedVersion = "1.4.0",
            pinnedVersion = "1.4.0",
        )

    /** A read that says "Checking…" and answers only when [gate] opens. */
    private class Slow : PhoneDetailsSource {
        val gate = CompletableDeferred<Unit>()
        var reads = 0
        override val state = MutableStateFlow(PhoneDetailsUi(installed = true))

        override fun shown(visible: Boolean) = Unit

        override suspend fun refresh() {
            reads++
            state.value = state.value.copy(loading = true)
            gate.await()
            state.value =
                state.value.copy(
                    loading = false,
                    installedNames = persistentListOf("Linux base", "Git"),
                    inventoryFailure = null,
                )
        }

        override suspend fun refreshHistory(): Boolean = false

        override suspend fun refreshLog() = Unit

        override suspend fun connect(): Outcome<Unit> = Outcome.Ok(Unit)
    }

    private fun kotlinx.coroutines.test.TestScope.model(details: Slow) =
        ThisPhoneModel(
            FakeServer(facts),
            FakeSettings(),
            RecordingNav(),
            backgroundScope,
            testClock(),
            foregroundPacer(),
            phoneDetails = details,
        )

    @Test
    fun `B-LINUX-400 a read that never answers ends in a failure line with Try again, not Checking`() =
        runTest {
            val details = Slow()
            val model = model(details)
            model.shown(true)
            runCurrent()
            assertEquals(InstalledLine.Checking, installedLine(model.state.value.details))
            advanceTimeBy(61.seconds)
            runCurrent()
            val shown = model.state.value.details
            assertEquals(InstalledLine.Names, installedLine(shown))
            assertNotNull(installedFailureShown(shown))
        }

    @Test
    fun `B-LINUX-400 Try again joins the read that still runs and a late answer replaces the failure`() =
        runTest {
            val details = Slow()
            val model = model(details)
            model.shown(true)
            runCurrent()
            advanceTimeBy(61.seconds)
            runCurrent()
            model.refreshDetails()
            runCurrent()
            assertEquals(1, details.reads, "one read at a time")
            details.gate.complete(Unit)
            runCurrent()
            val shown = model.state.value.details
            assertEquals(listOf("Linux base", "Git"), shown.installedNames)
            assertNull(installedFailureShown(shown))
            assertTrue(!shown.loading)
        }
}
