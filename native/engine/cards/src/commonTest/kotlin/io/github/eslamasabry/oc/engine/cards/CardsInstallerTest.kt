package io.github.eslamasabry.oc.engine.cards

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Flutter `gen_ui_install_test.dart` 64-252 (B-CHAT-040) on the native installer. */
class CardsInstallerTest {
    private class Runner : CardsSetupRunner {
        val calls = mutableListOf<CardAgent>()
        val outcomes = mutableMapOf<CardAgent, CardsInstallOutcome>()
        var hold: CompletableDeferred<Unit>? = null

        override suspend fun run(profileId: String, agent: CardAgent, enabled: Boolean): CardsInstallOutcome {
            calls += agent
            hold?.await()
            return outcomes[agent] ?: CardsInstallOutcome.Registered
        }
    }

    private class Verifier(val ready: Set<CardAgent>) : CardsSetupVerifier {
        val calls = mutableListOf<CardAgent>()

        override suspend fun verify(profileId: String, agent: CardAgent): Boolean {
            calls += agent
            return agent in ready
        }
    }

    private val both = listOf(CardAgent.Claude, CardAgent.OpenCode2)

    @Test
    fun `B-CHAT-040 every tool is staged but only evidence-backed agents are verified`() = runTest {
        val runner = Runner()
        val verifier = Verifier(setOf(CardAgent.Claude))
        val status = ManagedCardsInstaller(runner, verifier).setEnabled("one", both, enabled = true)
        assertEquals(both, runner.calls)
        assertEquals(listOf(CardAgent.Claude), verifier.calls)
        assertIs<CardsStatus.Partial>(status)
        assertEquals(listOf(CardAgent.Claude), status.agents)
        assertEquals(listOf(CardAgent.OpenCode2), status.affected, "OpenCode 2 registration stays unqualified")
    }

    @Test
    fun `B-CHAT-040 a failed verifier reports verification failed and a missing verifier not qualified, never restart`() = runTest {
        val failed = ManagedCardsInstaller(Runner(), Verifier(emptySet())).setEnabled("one", listOf(CardAgent.Claude), true)
        assertEquals(CardsProblem.VerificationFailed, assertIs<CardsStatus.Failed>(failed).reason)
        val missing = ManagedCardsInstaller(Runner()).setEnabled("one", listOf(CardAgent.Claude), true)
        assertEquals(CardsProblem.NotQualified, assertIs<CardsStatus.Unavailable>(missing).reason)
        assertEquals(emptyList(), missing.agents, "registration alone never claims a ready runtime")
    }

    @Test
    fun `B-CHAT-040 an OpenCode collision gives partial while Claude stays ready`() = runTest {
        val runner = Runner().apply { outcomes[CardAgent.OpenCode2] = CardsInstallOutcome.NameCollision }
        val status = ManagedCardsInstaller(runner, Verifier(setOf(CardAgent.Claude))).setEnabled("profile-1", both, true)
        assertIs<CardsStatus.Partial>(status)
        assertEquals(listOf(CardAgent.Claude), status.agents)
        assertEquals(both, runner.calls)
        assertEquals(listOf(CardAgent.OpenCode2), status.affected)
    }

    @Test
    fun `B-CHAT-040 OpenCode 2 registration cannot become ready through a permissive verifier`() = runTest {
        val runner = Runner()
        val verifier = Verifier(both.toSet())
        val status = ManagedCardsInstaller(runner, verifier).setEnabled("one", listOf(CardAgent.OpenCode2), true)
        assertEquals(listOf(CardAgent.OpenCode2), runner.calls)
        assertEquals(emptyList(), verifier.calls)
        val unavailable = assertIs<CardsStatus.Unavailable>(status)
        assertEquals(CardsProblem.NotQualified, unavailable.reason)
        assertEquals(emptyList(), unavailable.agents)
        assertEquals(listOf(CardAgent.OpenCode2), unavailable.affected)
    }

    @Test
    fun `B-CHAT-040 verified Claude is ready, also when the OpenCode runtime is missing`() = runTest {
        val on = ManagedCardsInstaller(Runner(), Verifier(setOf(CardAgent.Claude))).setEnabled("one", listOf(CardAgent.Claude), true)
        assertEquals(listOf(CardAgent.Claude), assertIs<CardsStatus.On>(on).agents)
        val runner = Runner().apply { outcomes[CardAgent.OpenCode2] = CardsInstallOutcome.RuntimeMissing }
        val verifier = Verifier(setOf(CardAgent.Claude))
        val partial = ManagedCardsInstaller(runner, verifier).setEnabled("one", both, true)
        assertIs<CardsStatus.Partial>(partial)
        assertEquals(listOf(CardAgent.Claude), partial.agents)
        assertEquals(both, runner.calls)
        assertEquals(listOf(CardAgent.Claude), verifier.calls)
    }

    @Test
    fun `B-CHAT-040 a missing installation disables without verification`() = runTest {
        val runner = Runner().apply { outcomes[CardAgent.OpenCode2] = CardsInstallOutcome.NotInstalled }
        val verifier = Verifier(emptySet())
        assertEquals(CardsStatus.Off, ManagedCardsInstaller(runner, verifier).setEnabled("one", listOf(CardAgent.OpenCode2), false))
        assertEquals(emptyList(), verifier.calls)
    }

    @Test
    fun `B-CHAT-040 an invalid profile is rejected before any setup runs`() = runTest {
        val runner = Runner()
        assertIs<CardsStatus.Failed>(ManagedCardsInstaller(runner).setEnabled("../other; touch x", listOf(CardAgent.Claude), true))
        assertEquals(emptyList(), runner.calls)
    }

    @Test
    fun `B-CHAT-040 installers serialize across instances`() = runTest {
        val first = Runner().apply { hold = CompletableDeferred() }
        val second = Runner()
        val a = async { ManagedCardsInstaller(first).setEnabled("one", listOf(CardAgent.Claude), true) }
        val b = async { ManagedCardsInstaller(second).setEnabled("two", listOf(CardAgent.Claude), false) }
        runCurrent()
        assertEquals(listOf(CardAgent.Claude), first.calls)
        assertEquals(emptyList(), second.calls)
        first.hold!!.complete(Unit)
        a.await()
        b.await()
        assertEquals(listOf(CardAgent.Claude), second.calls)
    }

    @Test
    fun `B-CHAT-040 the script exit status is the whole result protocol`() {
        assertEquals(
            listOf(
                CardsInstallOutcome.Registered, CardsInstallOutcome.RemovalPending, CardsInstallOutcome.NotInstalled,
                CardsInstallOutcome.RuntimeMissing, CardsInstallOutcome.NameCollision, CardsInstallOutcome.UnsafePath,
                CardsInstallOutcome.Failed,
            ),
            listOf(0, 10, 11, 20, 21, 22, 1).map(CardsInstallOutcome::ofExitCode),
        )
    }
}
