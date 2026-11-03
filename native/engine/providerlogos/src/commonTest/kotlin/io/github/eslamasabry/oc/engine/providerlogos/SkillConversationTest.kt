package io.github.eslamasabry.oc.engine.providerlogos

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionSelection
import io.github.eslamasabry.oc.core.model.SkillInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private val SKILL = SkillInfo(
    id = "skill-review",
    name = "Review",
    description = null,
    location = "/skills/review/SKILL.md",
    content = "Review changes.",
    slashCommand = false,
)

private val SELECTION = SessionSelection(model = null, variant = "focused", agent = "reviewer", modelKnown = false, agentKnown = true)

private data class Prompt(val session: String, val text: String, val model: ModelRef?, val agent: String?, val variant: String?)

private class FakeServer : SkillConversationServer {
    override var serverCatalog = true
    override var sessionSkillsSupported = true
    override var isClosed = false
    val session = Session(SessionId("ses_new"))
    var creation: CompletableDeferred<Outcome<Session>>? = null
    var creates = 0
    var selected: SessionSelection? = null
    val activations = mutableListOf<Triple<String, String, Boolean>>()
    var failActivation = false
    val prompts = mutableListOf<Prompt>()
    var failPrompt = false

    override suspend fun createSession(selection: SessionSelection): Outcome<Session> {
        creates++
        selected = selection
        return creation?.await() ?: session.ok()
    }

    override suspend fun activateSessionSkill(session: SessionId, skillId: String, resume: Boolean): Outcome<Unit> {
        activations += Triple(session.raw, skillId, resume)
        return if (failActivation) Failure.Network().failed() else Unit.ok()
    }

    override suspend fun promptAsync(
        session: SessionId,
        text: String,
        model: ModelRef?,
        agent: String?,
        variant: String?,
    ): Outcome<Unit> {
        prompts += Prompt(session.raw, text, model, agent, variant)
        return if (failPrompt) Failure.Network().failed() else Unit.ok()
    }
}

/** Masks registered values, like core:security Redactor with a known secret. */
private class FakeSecrets(private val known: Set<String>) : SecretScreen {
    override fun redact(text: String): String = known.fold(text) { acc, s -> acc.replace(s, "•••") }

    override fun containsSecret(text: String): Boolean = redact(text) != text
}

class SkillConversationTest {
    private val server = FakeServer()
    private var current = true
    private val controller = SkillConversationController(server, FakeSecrets(emptySet()), { current }, SELECTION)

    @Test
    fun `B-CHAT-108 captures the creation selection and refuses a stale connection`() = runTest {
        current = false
        assertEquals(SkillConversationStatus.Changed, controller.useInNewConversation(SKILL).status)
        assertEquals(0, server.creates)
        current = true
        assertEquals(SkillConversationStatus.Ready, controller.useInNewConversation(SKILL).status)
        assertSame(SELECTION, server.selected)
        assertEquals(1, server.creates)
    }

    @Test
    fun `B-CHAT-108 a closed gateway counts as a changed connection`() = runTest {
        server.isClosed = true
        assertEquals(SkillConversationStatus.Changed, controller.useInNewConversation(SKILL).status)
        assertEquals(0, server.creates)
    }

    @Test
    fun `B-CHAT-108 creates then stages the protocol skill ID without running the agent`() = runTest {
        val result = controller.useInNewConversation(SKILL)
        assertEquals(SkillConversationStatus.Ready, result.status)
        assertSame(server.session, result.session)
        assertEquals(1, server.creates)
        assertEquals(listOf(Triple("ses_new", "skill-review", false)), server.activations)
        assertTrue(server.prompts.isEmpty())
        assertEquals(SkillConversationStatus.AlreadyStarted, controller.useInNewConversation(SKILL).status)
        assertEquals(1, server.creates)
    }

    @Test
    fun `B-CHAT-108 unsupported or missing ID never creates a conversation`() = runTest {
        server.sessionSkillsSupported = false
        assertEquals(SkillConversationStatus.Unsupported, controller.useInNewConversation(SKILL).status)
        server.sessionSkillsSupported = true
        val missingId = SkillInfo(null, "Review", null, "", "", slashCommand = true)
        assertEquals(SkillConversationStatus.InvalidInput, controller.useInNewConversation(missingId).status)
        for (bad in listOf("", " skill", "skill\u0007", "skill\u007f")) {
            assertEquals(
                SkillConversationStatus.InvalidInput,
                controller.useInNewConversation(SKILL.copy(id = bad)).status,
                bad,
            )
        }
        server.serverCatalog = false
        assertFalse(controller.canUseSkill)
        assertFalse(controller.canAskForSkill)
        assertEquals(SkillConversationStatus.Unsupported, controller.askAgentToWriteSkill("Review code").status)
        assertFalse(ProviderKeyPageLinks)
        assertEquals(0, server.creates)
    }

    @Test
    fun `B-CHAT-108 a skill ID holding a known secret is refused`() = runTest {
        val guarded = SkillConversationController(server, FakeSecrets(setOf("fake-key")), { true }, SELECTION)
        val result = guarded.useInNewConversation(SKILL.copy(id = "x-fake-key"))
        assertEquals(SkillConversationStatus.InvalidInput, result.status)
        assertEquals(0, server.creates)
    }

    @Test
    fun `B-CHAT-108 a connection change during create keeps the session without activation`() = runTest {
        server.creation = CompletableDeferred()
        val pending = async { controller.useInNewConversation(SKILL) }
        testScheduler.runCurrent()
        current = false
        server.creation!!.complete(server.session.ok())
        val result = pending.await()
        assertEquals(SkillConversationStatus.Changed, result.status)
        assertSame(server.session, result.session)
        assertTrue(server.activations.isEmpty())
    }

    @Test
    fun `B-CHAT-108 an ambiguous activation keeps the session and never retries`() = runTest {
        server.failActivation = true
        val result = controller.useInNewConversation(SKILL)
        assertEquals(SkillConversationStatus.ActivationUncertain, result.status)
        assertSame(server.session, result.session)
        controller.useInNewConversation(SKILL)
        assertEquals(1, server.creates)
        assertEquals(1, server.activations.size)
    }

    @Test
    fun `B-CHAT-108 a creation failure prevents both dispatch and duplicate creation`() = runTest {
        server.creation = CompletableDeferred()
        val pending = async { controller.useInNewConversation(SKILL) }
        testScheduler.runCurrent()
        assertEquals(SkillConversationStatus.AlreadyStarted, controller.useInNewConversation(SKILL).status)
        server.creation!!.complete(Failure.Network().failed())
        val result = pending.await()
        assertEquals(SkillConversationStatus.CreationUncertain, result.status)
        assertNull(result.session)
        assertEquals(1, server.creates)
        assertTrue(server.activations.isEmpty())
    }

    @Test
    fun `B-CHAT-108 authoring sends a redacted draft request and reports uncertain sends`() = runTest {
        val secret = "fake-sv3-provider-value"
        val authoring = SkillConversationController(server, FakeSecrets(setOf(secret)), { current }, SELECTION)
        assertEquals(SkillConversationStatus.InvalidInput, authoring.askAgentToWriteSkill("  ").status)
        server.failPrompt = true
        val result = authoring.askAgentToWriteSkill("Review code with $secret")
        assertEquals(SkillConversationStatus.PromptUncertain, result.status)
        assertSame(server.session, result.session)
        val prompt = server.prompts.single()
        assertEquals("ses_new", prompt.session)
        assertEquals("reviewer", prompt.agent)
        assertEquals("focused", prompt.variant)
        assertNull(prompt.model)
        assertFalse(prompt.text.contains(secret))
        assertTrue(prompt.text.contains("Do not install"))
        authoring.askAgentToWriteSkill("Review code")
        assertEquals(1, server.prompts.size)
        assertTrue(server.activations.isEmpty())
    }
}
