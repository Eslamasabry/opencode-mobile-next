package io.github.eslamasabry.oc.protocol.oc2.fixtures

import io.github.eslamasabry.oc.protocol.oc2.events.Oc2Event
import io.github.eslamasabry.oc.protocol.oc2.events.Oc2Phase
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2AgentInfo
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2AssistantContent
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2Command
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2Delivery
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2ErrorEnvelope
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2FsEntry
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2InboxItem
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2Location
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2Message
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2ModelInfo
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2Page
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2ProviderInfo
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2Session
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2Skill
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2ToolResultItem
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2ToolState
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2UsageStatistics
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Ports the fixture cases of test/api2_models_test.dart and test/api2_events_test.dart. */
class Oc2FixtureDecodeTest {
    @Test
    fun `B-PROTO-010 the captured session page parses with both cursors kept verbatim`() {
        val page = Oc2Page.decode(Fixtures.json("api2/sessions_page.json"), Oc2Session::fromJson)
        assertEquals(3, page.data.size)
        assertTrue(page.hasNext)
        assertTrue(page.hasPrevious)
        assertTrue(page.nextCursor!!.isNotEmpty())
        val first = page.data.first()
        assertTrue(first.id.startsWith("ses_"))
        assertEquals("Repo test strategy overview", first.title)
        assertEquals("openai", first.model?.providerID)
        assertEquals("gpt-5.6-sol", first.model?.id)
        assertEquals(59422L, first.tokens.input)
        assertEquals(78336L, first.tokens.cacheRead)
        assertNotNull(first.time.created)
        assertEquals("/home/dev/projects/oc_app", first.directory)
        assertFalse(first.archived)
        assertNotNull(page.data[1].parentID)
        assertEquals("explore", page.data[1].agent)

        val created = Oc2Session.fromJson(Fixtures.data("api2/session_create.json"))!!
        assertEquals("api2 fixture probe", created.title)
        assertTrue(created.projectID!!.isNotEmpty())
    }

    @Test
    fun `B-PROTO-010 the captured message list parses into the typed union`() {
        val page = Oc2Page.decode(Fixtures.json("api2/messages.json"), Oc2Message::fromJson)
        assertTrue(page.data.size >= 3)
        val user = assertIs<Oc2Message.User>(page.data.first())
        assertTrue("pong" in user.text)
        assertEquals(1, user.files.size)
        assertEquals("text/plain", user.files.first().mime)
        assertEquals("inline", user.files.first().sourceType)
        assertEquals("att.txt", user.files.first().name)
        assertEquals("tiny attachment", Base64.decode(user.files.first().data!!).decodeToString())

        val assistant = assertIs<Oc2Message.Assistant>(page.data[1])
        assertEquals("build", assistant.agent)
        assertEquals("openai", assistant.model?.providerID)
        assertEquals("tool-calls", assistant.finish)
        assertTrue(assistant.completed)
        assertTrue(assistant.tokens!!.input > 0)
        assertTrue(assistant.content.any { it is Oc2AssistantContent.Reasoning })
        assertEquals("pong.", assistant.content.filterIsInstance<Oc2AssistantContent.Text>().first().text)
        val tool = assistant.content.filterIsInstance<Oc2AssistantContent.Tool>().first()
        assertEquals("read", tool.name)
        assertTrue(tool.id.startsWith("call_"))
        val state = assertIs<Oc2ToolState.Completed>(tool.state)
        assertEquals(JsonPrimitive("README.md"), state.input["path"])
        assertTrue("OpenCode for Android" in state.content.filterIsInstance<Oc2ToolResultItem.Text>().first().text)
        assertNotNull(tool.time.ran)

        val single = assertIs<Oc2Message.Assistant>(Oc2Message.fromJson(Fixtures.data("api2/message_assistant.json")))
        assertTrue(single.content.isNotEmpty())
        assertTrue(single.text.isNotEmpty())
    }

    @Test
    fun `B-PROTO-061 message pages keep the next cursor for cursor-only continuation`() {
        val first = Oc2Page.decode(Fixtures.json("api2/messages_page1.json"), Oc2Message::fromJson)
        val second = Oc2Page.decode(Fixtures.json("api2/messages_page2.json"), Oc2Message::fromJson)
        assertEquals(1, first.data.size)
        assertTrue(first.hasNext)
        assertEquals(listOf("assistant", "user"), second.data.map { it.type }, "wire order kept (newest first)")
        assertTrue((first.data + second.data).all { it.id.startsWith("msg_") })
        // OpenCode returns a cursor on the last page too, so "older pages exist" cannot be read from the cursor alone.
        assertTrue(second.hasNext)
    }

    @Test
    fun `B-PROTO-018 v2-only message kinds parse and a live 2_1_1 page keeps unknown kinds as Unknown`() {
        val variants = Oc2Page.decode(Fixtures.json("api2/messages_variants.json"), Oc2Message::fromJson).data
        assertTrue(variants.any { it is Oc2Message.Shell })
        val live = Oc2Page.decode(Fixtures.json("api2/messages_2_1_1.json"), Oc2Message::fromJson).data
        val unknown = live.filterIsInstance<Oc2Message.Unknown>()
        assertEquals(listOf("idle", "idle"), unknown.map { it.type })
        assertTrue(live.any { it is Oc2Message.Compaction })
    }

    @Test
    fun `B-PROTO-011 the captured prompt receipt and inbox list parse`() {
        val item = Oc2InboxItem.fromJson(Fixtures.data("api2/prompt_receipt.json"))!!
        assertTrue(item.id.startsWith("msg_"))
        assertTrue(item.sessionID!!.startsWith("ses_"))
        assertEquals("user", item.type)
        assertEquals(Oc2Delivery.Steer, item.delivery)
        assertTrue("pong" in item.promptText!!)
        assertNotNull(item.timeCreated)
        val queued = Fixtures.dataList("api2/inbox_list.json").mapNotNull(Oc2InboxItem::fromJson)
        assertEquals(Oc2Delivery.Queue, queued.single().delivery)
    }

    @Test
    fun `B-PROTO-019 captured model provider agent command and skill listings parse`() {
        val models = Fixtures.dataList("api2/models_sample.json").mapNotNull(Oc2ModelInfo::fromJson)
        assertTrue(models.isNotEmpty())
        val fallback = Oc2ModelInfo.fromJson(Fixtures.data("api2/model_default.json"))!!
        assertEquals("gpt-5.6-sol", fallback.id)
        assertEquals("openai", fallback.providerID)
        assertTrue(fallback.capabilities.tools)
        assertTrue("image" in fallback.capabilities.input)
        assertTrue("high" in fallback.variants)
        assertEquals(1050000L, fallback.limit.context)
        assertEquals("openai/gpt-5.6-sol#high", fallback.ref(variant = "high").toString())

        val providers = Fixtures.dataList("api2/providers.json").mapNotNull(Oc2ProviderInfo::fromJson)
        assertTrue(providers.any { it.id == "openai" })
        val agents = Fixtures.dataList("api2/agents.json").mapNotNull(Oc2AgentInfo::fromJson)
        assertTrue(agents.map { it.id }.containsAll(listOf("build", "plan")))
        assertTrue(agents.filter { it.mode == "subagent" }.any { !it.selectable })
        val commands = Fixtures.dataList("api2/commands.json").mapNotNull(Oc2Command::fromJson)
        assertTrue(commands.any { it.name == "review" })
        val skills = Fixtures.dataList("api2/skills_sample.json").mapNotNull(Oc2Skill::fromJson)
        assertTrue(skills.first().id.isNotEmpty())
    }

    @Test
    fun `B-PROTO-019 captured location fs listing error envelopes and usage statistics parse`() {
        val location = Oc2Location.fromJson(Fixtures.json("api2/location.json"))!!
        assertEquals("/home/dev/projects/oc_app", location.directory)
        val project = assertNotNull(location.project)
        assertTrue(project.id.isNotEmpty())
        assertEquals(location.directory, project.canonical)
        val entries = Fixtures.dataList("api2/fs_list.json").mapNotNull(Oc2FsEntry::fromJson)
        assertTrue(entries.first().isDirectory)
        assertTrue(entries.any { it.path == "lib/main.dart" && !it.isDirectory })
        val notFound = Oc2ErrorEnvelope.fromJson(Fixtures.json("api2/error_not_found.json").jsonObject)
        assertEquals("SessionNotFoundError", notFound.tag)
        assertEquals("ses_doesnotexist", notFound.detail("sessionID"))
        val stats = Oc2UsageStatistics.fromJson(Fixtures.data("api2/session_stats.json"))!!
        assertEquals(12L, stats.sessions)
        assertEquals(3.42, stats.cost)
    }

    @Test
    fun `B-PROTO-014 a captured prompt round-trip replays into typed events`() {
        val envelopes = Fixtures.sse("api2/events.sse")
        assertTrue(envelopes.size > 15)
        assertTrue(envelopes.none { it.event is Oc2Event.Unknown }, "every captured event type must be recognized")
        assertIs<Oc2Event.ServerConnected>(envelopes.first().event)

        val created = envelopes.first { it.event is Oc2Event.SessionCreated }
        val createdEvent = created.event as Oc2Event.SessionCreated
        assertTrue(createdEvent.sessionID.startsWith("ses_"))
        assertEquals("api2 fixture probe", createdEvent.title)
        assertEquals("0.0.0-beta-18600", createdEvent.version)
        assertEquals(0L, created.durable?.seq)
        assertEquals(createdEvent.sessionID, created.durable?.aggregateID)
        assertEquals("/home/dev/projects/oc_app", created.location?.directory)
        assertTrue(created.isDurable)

        val events = envelopes.map { it.event }
        val enqueued = events.filterIsInstance<Oc2Event.SessionInbox>().first { it.phase == Oc2Phase.Enqueued }
        assertTrue(enqueued.inboxID.startsWith("msg_"))
        assertEquals("user", enqueued.item?.type)
        assertTrue("pong" in enqueued.item?.promptText!!)
        assertEquals(Oc2Delivery.Steer, enqueued.delivery)

        val steps = events.filterIsInstance<Oc2Event.SessionStep>()
        assertTrue(steps.any { it.phase == Oc2Phase.Streamed })
        val stepStarted = steps.first { it.phase == Oc2Phase.Started }
        assertTrue(stepStarted.assistantMessageID.startsWith("msg_"))
        assertEquals("build", stepStarted.agent)
        assertEquals("openai", stepStarted.model?.providerID)
        val stepEnded = steps.first { it.phase == Oc2Phase.Ended }
        assertNotNull(stepEnded.finish)
        assertTrue(stepEnded.tokens!!.input > 0)

        val text = events.filterIsInstance<Oc2Event.SessionText>()
        assertTrue(text.first { it.phase == Oc2Phase.Delta }.delta!!.isNotEmpty())
        assertTrue("pong" in text.first { it.phase == Oc2Phase.Ended }.text!!)
        assertFalse(envelopes.first { it.type == "session.text.delta" }.isDurable, "deltas are ephemeral")

        val input = events.filterIsInstance<Oc2Event.SessionToolInput>()
        assertTrue(input.any { it.phase == Oc2Phase.Started })
        assertNotNull(input.first { it.phase == Oc2Phase.Ended }.text)
        val called = events.filterIsInstance<Oc2Event.SessionToolCalled>().first()
        assertTrue(called.callID.isNotEmpty())
        assertEquals(JsonPrimitive("README.md"), called.input["path"])
        val success = events.filterIsInstance<Oc2Event.SessionToolResult>().first()
        assertTrue(success.succeeded)
        assertTrue("OpenCode for Android" in success.content.filterIsInstance<Oc2ToolResultItem.Text>().first().text)
        assertTrue(events.filterIsInstance<Oc2Event.SessionUsageUpdated>().first().tokens.input > 0)
        val execution = events.filterIsInstance<Oc2Event.SessionExecution>()
        assertEquals(Oc2Phase.Started, execution.first().phase)
        assertEquals(Oc2Phase.Succeeded, execution.last().phase)
        assertTrue(execution.last().finished)
        assertTrue(events.any { it is Oc2Event.SessionDeleted })
    }
}
