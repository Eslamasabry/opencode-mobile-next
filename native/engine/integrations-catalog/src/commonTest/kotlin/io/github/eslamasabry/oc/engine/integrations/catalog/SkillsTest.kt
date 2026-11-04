package io.github.eslamasabry.oc.engine.integrations.catalog

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.security.Redactor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SkillsTest {
    // ---- B-PROTO-103 (test/session_skill_test.dart:141, :186)

    @Test
    fun `B-PROTO-103 catalog identity and activation body retain scope and explicit resume`() =
        runTest {
            val transport =
                FakeTransport(payload = """{"data":[{"id":"wire-id","name":"Display name"}]}""")
            transport.answer = { r ->
                if (r.method == "GET") CatalogReply(200, json("""{"data":[{"id":"wire-id","name":"Display name"}]}""")).ok() else CatalogReply(204, null).ok()
            }
            val ops = SessionSkillGateway(transport) { CatalogLocation("/home/dev/projects/oc_app", null) }
            val skill = assertIs<Outcome.Ok<List<CatalogSkill>>>(ops.listSkills()).value.single()
            assertEquals("wire-id", skill.id)
            assertEquals("Display name", skill.name)
            assertEquals(SkillActivation.Done, ops.activateSessionSkill("ses_target", skill.id, resume = false))
            assertEquals(SkillActivation.Done, ops.activateSessionSkill("ses_target", skill.id, resume = true))
            assertEquals("/home/dev/projects/oc_app", transport.requests.first().query["location[directory]"])
            for (request in transport.requests.drop(1)) {
                assertEquals("POST", request.method)
                assertEquals("/session/ses_target/skill", request.path)
                assertTrue(request.query.isEmpty())
            }
            assertEquals(JsonObject(mapOf("skill" to JsonPrimitive("wire-id"), "resume" to JsonPrimitive(false))), transport.requests[1].body)
            assertEquals(JsonObject(mapOf("skill" to JsonPrimitive("wire-id"), "resume" to JsonPrimitive(true))), transport.requests[2].body)
        }

    @Test
    fun `B-PROTO-103 known missing skill does not disable activation, server failures are uncertain`() =
        runTest {
            var status = 404
            var tag = "SkillNotFoundError"
            val transport = FakeTransport()
            transport.answer = { CatalogReply(status, JsonObject(mapOf("_tag" to JsonPrimitive(tag)))).ok() }
            val ops = SessionSkillGateway(transport) { CatalogLocation(null, null) }
            suspend fun send() = ops.activateSessionSkill("ses_test", "review", resume = false)
            assertIs<SkillActivation.Failed>(send())
            assertTrue(ops.sessionSkillsSupported)
            status = 500
            tag = "InternalError"
            assertEquals(SkillActivation.Refused(SessionSkillFailure.Uncertain), send())
            assertTrue(ops.sessionSkillsSupported)
            status = 405
            assertEquals(SkillActivation.Refused(SessionSkillFailure.Unsupported), send())
            assertFalse(ops.sessionSkillsSupported)
            assertEquals(3, transport.requests.size)
            // Once unsupported, nothing more is sent.
            assertEquals(SkillActivation.Refused(SessionSkillFailure.Unsupported), send())
            assertEquals(3, transport.requests.size)
        }

    // ---- B-STATE-120 (test/session_skill_test.dart:222, :260, :280)

    private class Ports(val gateway: SessionSkillGateway) : SkillActivationPorts {
        var wake: CompletableDeferred<Unit>? = null
        var readGate: CompletableDeferred<Unit>? = null
        var facts = SkillSessionFacts(reverted = false, stagedRevert = false)
        val changed = mutableListOf<String>()

        override suspend fun prepare(): SessionSkillGateway {
            wake?.await()
            return gateway
        }

        override suspend fun sessionFacts(gateway: SessionSkillGateway, sessionId: String): Outcome<SkillSessionFacts> {
            readGate?.await()
            return facts.ok()
        }

        override fun skillChanged(sessionId: String) {
            changed += sessionId
        }
    }

    private fun recording(): Pair<FakeTransport, Ports> {
        val transport = FakeTransport().apply { answer = { CatalogReply(204, null).ok() } }
        return transport to Ports(SessionSkillGateway(transport) { CatalogLocation(null, null) })
    }

    @Test
    fun `B-STATE-120 location change during wake or session read prevents dispatch`() =
        runTest {
            val (transport, ports) = recording()
            val guard = SkillActivationGuard(ports)
            ports.wake = CompletableDeferred()
            val pending = async { guard.activate("ses_test", "review", resume = false, expectedLocation = guard.locationRevision) }
            yield()
            guard.locationRevision++
            ports.wake!!.complete(Unit)
            assertEquals(SkillActivation.Refused(SessionSkillFailure.Changed), pending.await())
            ports.wake = null
            ports.readGate = CompletableDeferred()
            val read = async { guard.activate("ses_test", "review", resume = false, expectedLocation = guard.locationRevision) }
            yield()
            guard.locationRevision++
            ports.readGate!!.complete(Unit)
            assertEquals(SkillActivation.Refused(SessionSkillFailure.Changed), read.await())
            assertTrue(transport.requests.isEmpty())
        }

    @Test
    fun `B-STATE-120 fresh staged revert and concurrent activation are guarded`() =
        runTest {
            val (transport, ports) = recording()
            val guard = SkillActivationGuard(ports)
            suspend fun send() = guard.activate("ses_test", "review", resume = false, expectedLocation = guard.locationRevision)
            ports.facts = SkillSessionFacts(reverted = true, stagedRevert = false)
            assertEquals(SkillActivation.Refused(SessionSkillFailure.Staged), send())
            assertTrue(transport.requests.isEmpty())
            ports.facts = SkillSessionFacts(reverted = false, stagedRevert = false)
            transport.gate = CompletableDeferred()
            val pending = async { send() }
            yield()
            assertEquals(SkillActivation.Refused(SessionSkillFailure.Busy), send())
            transport.gate!!.complete(Unit)
            assertEquals(SkillActivation.Done, pending.await())
            assertEquals(1, transport.requests.size)
            assertEquals(JsonObject(mapOf("skill" to JsonPrimitive("review"), "resume" to JsonPrimitive(false))), transport.requests.single().body)
            assertEquals(listOf("ses_test"), ports.changed)
        }

    @Test
    fun `B-STATE-120 skill events request a refresh without exposing skill instruction text`() {
        val hint =
            skillEventHint(
                "session.skill.activated",
                json("""{"sessionID":"ses_test","name":"review","text":"private instructions"}"""),
            )!!
        assertEquals("session.skill.changed", hint.type)
        assertEquals(mapOf("sessionID" to "ses_test"), hint.properties)
        assertFalse(hint.toString().contains("private instructions"))
        assertNull(skillEventHint("session.updated", null))
    }

    // ---- B-SEC-173 (test/sv3_skill_conversation_test.dart:213)

    private class ConversationPorts : SkillConversationPorts {
        override var isClosed = false
        override val canUseSkill = true
        override val canAskForSkill = true
        val prompts = mutableListOf<Pair<String, String>>()
        var promptSelection: SkillSelection? = null
        var failPrompt = false
        var creates = 0
        val activations = mutableListOf<String>()

        override suspend fun createSession(selection: SkillSelection): Outcome<String> {
            creates++
            return "ses_new".ok()
        }

        override suspend fun prompt(sessionId: String, text: String, selection: SkillSelection): Outcome<Unit> {
            prompts += sessionId to text
            promptSelection = selection
            return if (failPrompt) Failure.Network().failed() else Unit.ok()
        }

        override suspend fun activateSkill(sessionId: String, skillId: String, resume: Boolean): Outcome<Unit> {
            activations += skillId
            return Unit.ok()
        }
    }

    @Test
    fun `B-SEC-173 authoring sends a redacted draft request and reports uncertain sends`() =
        runTest {
            val redactor = Redactor()
            val fakeSecret = "fake-sv3-provider-value"
            redactor.registerSecret(fakeSecret)
            val ports = ConversationPorts()
            val selection = SkillSelection(agent = "reviewer", variant = "focused")
            val controller = SkillConversation(ports, redactor, { true }, selection)
            assertEquals(SkillConversationStatus.InvalidInput, controller.askAgentToWriteSkill("  ").status)
            ports.failPrompt = true
            val result = controller.askAgentToWriteSkill("Review code with $fakeSecret")
            assertEquals(SkillConversationStatus.PromptUncertain, result.status)
            assertEquals("ses_new", result.sessionId)
            assertEquals("ses_new", ports.prompts.single().first)
            assertEquals("reviewer", ports.promptSelection?.agent)
            assertEquals("focused", ports.promptSelection?.variant)
            assertFalse(ports.prompts.single().second.contains(fakeSecret))
            assertTrue(ports.prompts.single().second.contains("Do not install"))
            controller.askAgentToWriteSkill("Review code")
            assertEquals(1, ports.prompts.size)
            assertTrue(ports.activations.isEmpty())
        }

    @Test
    fun `B-SEC-173 a stale connection is refused and creation happens at most once`() =
        runTest {
            var current = false
            val ports = ConversationPorts()
            val controller = SkillConversation(ports, Redactor(), { current })
            assertEquals(SkillConversationStatus.Changed, controller.useInNewConversation("skill-review").status)
            assertEquals(0, ports.creates)
            current = true
            assertEquals(SkillConversationStatus.Ready, controller.useInNewConversation("skill-review").status)
            assertEquals(SkillConversationStatus.AlreadyStarted, controller.useInNewConversation("skill-review").status)
            assertEquals(1, ports.creates)
            assertEquals(listOf("skill-review"), ports.activations)
        }
}
