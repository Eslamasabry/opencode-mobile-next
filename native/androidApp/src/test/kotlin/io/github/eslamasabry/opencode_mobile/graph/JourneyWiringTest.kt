package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.engine.analytics.health.HealthWords
import io.github.eslamasabry.oc.engine.analytics.health.SetupJobState
import io.github.eslamasabry.oc.engine.analytics.health.SetupStepState
import io.github.eslamasabry.oc.engine.runtime.setup.ComponentProgress
import io.github.eslamasabry.oc.engine.runtime.setup.ComponentState
import io.github.eslamasabry.oc.engine.runtime.setup.SetupError
import io.github.eslamasabry.oc.engine.runtime.setup.SetupProgress
import io.github.eslamasabry.oc.engine.runtime.setup.SetupState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The app-side half of the journey analytics: setup errors as fail kinds, progress as funnel steps, the approval feed. */
class JourneyWiringTest {
    private val everyError: List<SetupError> =
        listOf(
            SetupError.OwnershipUnknown, SetupError.StatusUnreadable, SetupError.HostMismatch, SetupError.ToolsUncheckable,
            SetupError.PersistenceFailure, SetupError.PackageRepair, SetupError.CannotStart, SetupError.PortBusy(4096),
            SetupError.OpenCodeNoProgram, SetupError.OpenCodeWontRun, SetupError.OpenCodeNoStart, SetupError.NodeCannotRun,
            SetupError.Interrupted, SetupError.NoInternet, SetupError.NoSpace("Node"), SetupError.Offline("Node"),
            SetupError.Checksum("Node"), SetupError.Install("Node"), SetupError.HostRefused, SetupError.Unexpected,
        )

    @Test
    fun `B-JOURNEY-030 every setup error becomes one word of the closed fail kind list, never its text`() {
        for (error in everyError) assertTrue(setupFailKindOf(error) in HealthWords.setupFailKinds, error.toString())
        assertEquals("network", setupFailKindOf(SetupError.Offline("secret title")))
        assertEquals("storage", setupFailKindOf(SetupError.NoSpace("x")))
        assertEquals("checksum", setupFailKindOf(SetupError.Checksum("x")))
        assertEquals("killed_by_system", setupFailKindOf(SetupError.Interrupted))
        assertEquals("unknown", setupFailKindOf(null))
    }

    @Test
    fun `B-JOURNEY-031 setup progress becomes funnel steps, a failed one carrying its fail kind`() {
        val progress =
            SetupProgress(
                state = SetupState.Failed,
                components =
                    listOf(
                        ComponentProgress("linux", ComponentState.Done),
                        ComponentProgress("node", ComponentState.Failed, error = SetupError.NoInternet),
                        ComponentProgress("opencode", ComponentState.Pending),
                    ),
                overall = 0.3,
                jobId = "job1",
                firstSetup = true,
            )
        assertEquals(SetupJobState.Failed, journeyJobState(progress.state))
        val steps = journeySteps(progress)
        assertEquals(listOf(SetupStepState.Done, SetupStepState.Failed, SetupStepState.Pending), steps.map { it.state })
        assertEquals(listOf(null, "network", null), steps.map { it.failKind })
    }

    private class Sink : ApprovalSink {
        val calls = mutableListOf<String>()

        override fun asked(id: String, sessionId: String, kind: String) {
            calls += "asked $id $kind"
        }

        override fun answered(id: String, result: String) {
            calls += "answered $id $result"
        }

        override fun gone(id: String) {
            calls += "gone $id"
        }
    }

    @Test
    fun `B-JOURNEY-032 the approval feed tells asked once, the answer when held, and gone when the request leaves`() {
        val sink = Sink()
        val feed = ApprovalFeed(sink)
        feed.update(listOf(PendingFacts("r1", "s1", "run", null)))
        feed.update(listOf(PendingFacts("r1", "s1", "run", null)))
        feed.update(listOf(PendingFacts("r1", "s1", "run", "denied"), PendingFacts("r2", "s1", "edit", null)))
        feed.update(listOf(PendingFacts("r2", "s1", "edit", null)))
        feed.update(emptyList())
        assertEquals(
            listOf("asked r1 run", "answered r1 denied", "asked r2 edit", "gone r1", "gone r2"),
            sink.calls,
        )
    }
}
