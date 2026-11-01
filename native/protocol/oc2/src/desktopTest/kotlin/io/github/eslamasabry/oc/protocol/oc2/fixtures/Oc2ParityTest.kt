package io.github.eslamasabry.oc.protocol.oc2.fixtures

import io.github.eslamasabry.oc.protocol.oc2.events.Oc2Envelope
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2AgentInfo
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2Command
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2ErrorEnvelope
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2FormInfo
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2FormState
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2FsEntry
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2InboxItem
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2Location
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2Message
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2ModelInfo
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2Page
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2ProviderInfo
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2Session
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2Skill
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2UsageStatistics
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2Wire
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Every fixture decodes to the same variants and fields as the Dart client. The expected side,
 * native/fixtures/oc2/parity/dart-variants.json, is written by the Flutter parsers themselves
 * (native/fixtures/oc2/tool/dart_variants.dart); this test never edits it.
 */
class Oc2ParityTest {
    private val golden: JsonObject =
        Oc2Wire.json.parseToJsonElement(File(Fixtures.root, "parity/dart-variants.json").readText()).jsonObject

    private fun kotlinSide(): Map<String, JsonElement?> {
        val d = ParityDescriber
        val out = linkedMapOf<String, JsonElement?>()
        fun list(rel: String, describe: (JsonObject) -> JsonElement?) =
            JsonArray(Fixtures.dataList(rel).map { describe(it) ?: JsonNull })

        out["api2/sessions_page.json"] = d.page(Oc2Page.decode(Fixtures.json("api2/sessions_page.json"), Oc2Session::fromJson), d::session)
        out["api2/session_create.json"] = d.session(Oc2Session.fromJson(Fixtures.data("api2/session_create.json")))
        for (name in listOf("messages.json", "messages_2_1_1.json", "messages_page1.json", "messages_page2.json", "messages_variants.json")) {
            out["api2/$name"] = d.page(Oc2Page.decode(Fixtures.json("api2/$name"), Oc2Message::fromJson), d::message)
        }
        out["api2/message_assistant.json"] = d.message(Oc2Message.fromJson(Fixtures.data("api2/message_assistant.json")))
        out["api2/prompt_receipt.json"] = d.inbox(Oc2InboxItem.fromJson(Fixtures.data("api2/prompt_receipt.json")))
        out["api2/inbox_list.json"] = list("api2/inbox_list.json") { d.inbox(Oc2InboxItem.fromJson(it)) }
        out["api2/forms_session.json"] = list("api2/forms_session.json") { d.form(Oc2FormInfo.fromJson(it)) }
        out["api2/form_state_pending.json"] =
            d.formState(Oc2FormState.fromJson(Fixtures.json("api2/form_state_pending.json").jsonObject["data"]))
        out["api2/models_sample.json"] = list("api2/models_sample.json") { d.modelInfo(Oc2ModelInfo.fromJson(it)) }
        out["api2/model_default.json"] = d.modelInfo(Oc2ModelInfo.fromJson(Fixtures.data("api2/model_default.json")))
        out["api2/providers.json"] = list("api2/providers.json") { d.provider(Oc2ProviderInfo.fromJson(it)) }
        out["api2/agents.json"] = list("api2/agents.json") { d.agent(Oc2AgentInfo.fromJson(it)) }
        out["api2/commands.json"] = list("api2/commands.json") { d.command(Oc2Command.fromJson(it)) }
        out["api2/skills_sample.json"] = list("api2/skills_sample.json") { d.skill(Oc2Skill.fromJson(it)) }
        out["api2/location.json"] = d.location(Oc2Location.fromJson(Fixtures.json("api2/location.json")))
        out["api2/fs_list.json"] = list("api2/fs_list.json") { d.fsEntry(Oc2FsEntry.fromJson(it)) }
        for (name in listOf("error_not_found.json", "error_form_invalid_answer.json")) {
            out["api2/$name"] = d.errorEnvelope(Oc2ErrorEnvelope.fromJson(Fixtures.json("api2/$name").jsonObject))
        }
        out["api2/session_stats.json"] =
            d.usage(requireNotNull(Oc2UsageStatistics.fromJson(Fixtures.data("api2/session_stats.json"))))
        out["api2/events.sse"] = JsonArray(Fixtures.sse("api2/events.sse").map(d::envelope))
        out["coverage/oc2_events_samples.json"] =
            JsonObject(
                Fixtures.cases("coverage/oc2_events_samples.json").mapValues { (_, payload) ->
                    JsonArray(payload.map { d.envelope(Oc2Envelope.decode(it)) })
                },
            )
        out["coverage/oc2_messages_samples.json"] =
            JsonObject(
                Fixtures.cases("coverage/oc2_messages_samples.json").mapValues { (_, payload) ->
                    JsonArray(payload.map { d.message(Oc2Message.fromJson(it.jsonObject)) ?: JsonNull })
                },
            )
        return out
    }

    @Test
    fun `B-PROTO-027 every OpenCode 2 fixture decodes to the same variants and fields as the Dart client`() {
        val mine = kotlinSide()
        assertEquals(golden.keys, mine.keys, "the Dart golden and the Kotlin side list different fixtures")
        assertTrue(mine.keys.count { it.startsWith("api2/") } >= 24)
        val problems = mutableListOf<String>()
        for ((name, expected) in golden) diff(name, expected, mine[name] ?: JsonNull, problems)
        if (problems.isNotEmpty()) fail("${problems.size} differences from Dart:\n" + problems.take(40).joinToString("\n"))
    }

    @Test
    fun `B-PROTO-014 every captured and sampled event decodes to the Dart event variant and phase`() {
        // A narrower restatement of the test above, so a failure names the event rows directly.
        val dart = golden.getValue("coverage/oc2_events_samples.json").jsonObject
        val kotlin = kotlinSide().getValue("coverage/oc2_events_samples.json")!!.jsonObject
        for ((case, events) in dart) {
            val expected = (events as JsonArray).map { it.jsonObject.getValue("event").jsonObject.let { e -> e["v"] to e["phase"] } }
            val actual = (kotlin.getValue(case) as JsonArray).map { it.jsonObject.getValue("event").jsonObject.let { e -> e["v"] to e["phase"] } }
            assertEquals(expected, actual, case)
        }
    }

    /** Structural equality; numbers compare by value (Dart writes 3 where Kotlin may hold 3.0). */
    private fun diff(path: String, expected: JsonElement, actual: JsonElement, out: MutableList<String>) {
        when {
            expected is JsonObject && actual is JsonObject -> {
                for (key in expected.keys + actual.keys) {
                    diff("$path.$key", expected[key] ?: JsonNull, actual[key] ?: JsonNull, out)
                }
            }
            expected is JsonArray && actual is JsonArray -> {
                if (expected.size != actual.size) out += "$path: size ${expected.size} (Dart) vs ${actual.size}"
                expected.zip(actual).forEachIndexed { i, (e, a) -> diff("$path[$i]", e, a, out) }
            }
            expected is JsonPrimitive && actual is JsonPrimitive && !expected.isString && !actual.isString &&
                expected.doubleOrNull != null && actual.doubleOrNull != null -> {
                if (expected.doubleOrNull != actual.doubleOrNull) out += "$path: $expected (Dart) vs $actual"
            }
            expected != actual -> out += "$path: ${expected.toString().take(120)} (Dart) vs ${actual.toString().take(120)}"
        }
    }
}
