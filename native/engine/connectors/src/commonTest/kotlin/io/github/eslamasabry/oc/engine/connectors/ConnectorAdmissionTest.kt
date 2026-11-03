package io.github.eslamasabry.oc.engine.connectors

import io.github.eslamasabry.oc.core.domain.McpGateway
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.engine.connectors.chat.Admission
import io.github.eslamasabry.oc.engine.connectors.chat.AdmissionContext
import io.github.eslamasabry.oc.engine.connectors.chat.AdmissionEnvironment
import io.github.eslamasabry.oc.engine.connectors.chat.AdmissionRefusal
import io.github.eslamasabry.oc.engine.connectors.chat.CardAuthority
import io.github.eslamasabry.oc.engine.connectors.chat.CardRef
import io.github.eslamasabry.oc.engine.connectors.chat.CardState
import io.github.eslamasabry.oc.engine.connectors.chat.ConnectorAdmission
import io.github.eslamasabry.oc.engine.connectors.chat.McpChatController
import io.github.eslamasabry.oc.engine.connectors.chat.McpChatFailure
import io.github.eslamasabry.oc.engine.connectors.chat.McpChatPhase
import io.github.eslamasabry.oc.engine.connectors.chat.ObservedCard
import io.github.eslamasabry.oc.engine.connectors.chat.ProfileIdentity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

private const val SCOPE = "phone|opencode|/work/project"
private val PHONE = ProfileIdentity("phone", "http://127.0.0.1:4097", null, "v2")
private val OTHER = ProfileIdentity("other", "https://other.invalid", null, "v2")

/** A transcript holding one suggested-connector card (Dart `_message`), observed once the session tail loads. */
private class FakeCards : CardAuthority {
    var revision = 1
    var catalogId = "com.example/design"
    var loaded = true
    var scope: String? = SCOPE
    var recoverReached: CompletableDeferred<Unit>? = null
    var resumeRecover: CompletableDeferred<Unit>? = null

    fun card() = CardRef(SCOPE, "session", "suggest-design", revision)

    override fun observed(card: CardRef): ObservedCard? =
        if (loaded && card.identity == "suggest-design" && card.revision == revision) {
            ObservedCard(card, catalogId)
        } else {
            null
        }

    override fun state(card: CardRef): CardState? =
        when {
            !loaded -> null
            card.identity == "suggest-design" && card.revision == revision -> CardState.Report
            else -> CardState.Other
        }

    override fun activeScope(): String? = scope

    override fun registeredReady(scope: String): Boolean = true

    override suspend fun recover(card: CardRef) {
        recoverReached?.complete(Unit)
        resumeRecover?.await()
    }
}

private class Env : AdmissionEnvironment {
    var context =
        AdmissionContext(
            activeProfile = PHONE,
            connectedProfile = PHONE,
            profileReadable = true,
            deletionRevision = 0,
            directory = "/work/project",
            workspace = null,
            locationRevision = 0,
            cardsEnabled = true,
        )
    var repository = FakeMcp()
    override val cards = FakeCards()

    override fun context(): AdmissionContext = context

    override fun capabilities(): ServerCapabilities =
        ServerCapabilities(
            genUi = true,
            serverCatalog = true,
            mcpRuntimeAdds = true,
            mcpChatConnect = true,
            mcpChatToolRefresh = true,
        )

    override suspend fun prepareActionRepository(): McpGateway = repository
}

class ConnectorAdmissionTest {
    private val env = Env()

    private fun TestScope.admission() = ConnectorAdmission(env, backgroundScope)

    private fun ConnectorAdmission.controller(card: CardRef = env.cards.card(), name: String = "com.example/design") =
        assertIs<Admission.Admitted>(admit(card, hostedItem(name))).controller

    private fun McpChatController.failure() = snapshot.value.failure

    @Test
    fun `B-AGENT-134 authoritative connector admission coalesces the same card`() = runTest {
        val admission = admission()
        val first = admission.controller()
        assertSame(first, admission.controller())
        first.connect("design")
        assertEquals(McpChatPhase.ToolsReady, first.snapshot.value.phase)
        assertEquals(1, env.repository.adds)
    }

    @Test
    fun `B-AGENT-134 unobserved and mismatched catalog suggestions cannot be admitted`() = runTest {
        val admission = admission()
        env.cards.loaded = false
        assertEquals(Admission.Refused(AdmissionRefusal.NotAvailable), admission.admit(env.cards.card(), hostedItem()))
        env.cards.loaded = true
        assertEquals(
            Admission.Refused(AdmissionRefusal.NotAvailable),
            admission.admit(env.cards.card(), hostedItem("com.example/other")),
        )
        assertEquals(0, env.repository.adds)
    }

    @Test
    fun `B-AGENT-134 a changed authoritative transcript rejects the earlier card controller`() = runTest {
        val admission = admission()
        val card = env.cards.card()
        val connector = admission.controller(card)
        env.cards.revision = 2 // "The recommendation changed."
        admission.onChanged()
        assertEquals(Admission.Refused(AdmissionRefusal.NotAvailable), admission.admit(card, hostedItem()))
        connector.connect("design")
        assertNotEquals(McpChatPhase.ToolsReady, connector.snapshot.value.phase)
        assertEquals(0, env.repository.adds + env.repository.lists)
    }

    @Test
    fun `B-AGENT-134 a changed project invalidates an admitted connector before dispatch`() = runTest {
        val connector = admission().controller()
        env.context = env.context.copy(directory = "/work/other", locationRevision = 1)
        connector.connect("design")
        assertEquals(McpChatFailure.SourceChanged, connector.failure())
        assertEquals(0, env.repository.adds + env.repository.lists)
    }

    @Test
    fun `B-AGENT-134 a changed active profile invalidates an admitted connector`() = runTest {
        val connector = admission().controller()
        env.context = env.context.copy(activeProfile = OTHER)
        connector.connect("design")
        assertEquals(McpChatFailure.SourceChanged, connector.failure())
        assertEquals(0, env.repository.adds + env.repository.lists)
    }

    @Test
    fun `B-AGENT-134 a same-profile replacement retains the card and uses its fresh repository`() = runTest {
        val admission = admission()
        val connector = admission.controller()
        val old = env.repository
        val replacement = FakeMcp()
        env.repository = replacement
        admission.onChanged()
        assertSame(connector, admission.controller())
        connector.connect("design")
        assertEquals(McpChatPhase.ToolsReady, connector.snapshot.value.phase)
        assertEquals(1, replacement.adds)
        assertEquals(true, replacement.lists > 0)
        assertEquals(0, old.adds + old.lists)
    }

    @Test
    fun `B-AGENT-134 disabling agent cards invalidates retained connector actions`() = runTest {
        val admission = admission()
        val connector = admission.controller()
        env.context = env.context.copy(cardsEnabled = false)
        admission.onChanged()
        connector.connect("design")
        assertEquals(McpChatFailure.SourceChanged, connector.failure())
        assertEquals(0, env.repository.adds + env.repository.lists)
    }

    @Test
    fun `B-AGENT-134 disposed card controllers do not exhaust the pending card budget`() = runTest {
        val admission = admission()
        repeat(40) { admission.controller().dispose() }
        val connector = admission.controller()
        connector.connect("design")
        assertEquals(McpChatPhase.ToolsReady, connector.snapshot.value.phase)
        assertEquals(1, env.repository.adds)
    }

    @Test
    fun `B-AGENT-134 a source change during history recovery never calls the stale repository`() = runTest {
        val connector = admission().controller()
        env.cards.recoverReached = CompletableDeferred()
        env.cards.resumeRecover = CompletableDeferred()
        val work = async { connector.connect("design") }
        env.cards.recoverReached!!.await()
        env.context = env.context.copy(directory = "/work/other", locationRevision = 1)
        env.cards.resumeRecover!!.complete(Unit)
        work.await()
        assertEquals(McpChatFailure.SourceChanged, connector.failure())
        assertEquals(0, env.repository.adds + env.repository.lists)
    }
}
