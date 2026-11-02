package io.github.eslamasabry.oc.protocol.paseo.requests

import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.protocol.paseo.PaseoException
import io.github.eslamasabry.oc.protocol.paseo.PaseoFailureKind
import io.github.eslamasabry.oc.protocol.paseo.PaseoWireJson
import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import io.github.eslamasabry.oc.protocol.paseo.obj
import io.github.eslamasabry.oc.protocol.paseo.str
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A gateway stand-in: records every frame, event and question signal; the test moves epoch/location by hand. */
class FakeRequestHost : PaseoRequestHost {
    override var epoch = 1
    override var connected = true
    override var scope: String? = "/work/app"
    override var locationEpoch = 0
    val providers = mutableMapOf("a1" to "claude")
    val realIds = mutableMapOf<String, String>()
    val sent = mutableListOf<JsonObject>()
    val requests = mutableListOf<JsonObject>()
    val events = mutableListOf<GatewayEvent>()
    val questionSignals = mutableListOf<PaseoQuestionSignal>()

    /** Request answers by type; a deferred lets a test hold the answer. */
    val answers = mutableMapOf<String, (JsonObject) -> JsonObject>()
    var held: CompletableDeferred<JsonObject>? = null

    override fun realId(appId: String): String = realIds[appId] ?: appId

    override fun providerOf(appId: String): String? = providers[appId]

    override suspend fun request(type: String, body: JsonObject, mutation: Boolean, expectedEpoch: Int): JsonObject {
        if (!connected || expectedEpoch != epoch) throw PaseoException(PaseoFailureKind.StaleRequest)
        requests += body + ("type" to type)
        held?.let { return it.await() }
        return answers[type]?.invoke(body) ?: throw PaseoException(PaseoFailureKind.Disconnected)
    }

    override suspend fun send(type: String, body: JsonObject, expectedEpoch: Int) {
        if (!connected || expectedEpoch != epoch) throw PaseoException(PaseoFailureKind.StaleRequest)
        sent += body + ("type" to type)
    }

    override fun emit(event: GatewayEvent) {
        events += event
    }

    override fun questionChanged(signal: PaseoQuestionSignal) {
        questionSignals += signal
    }

    fun responses(): List<JsonObject> = sent.filter { it.str("type") == "agent_permission_response" }

    fun lastResponse(): JsonObject = responses().last().obj("response")!!

    private operator fun JsonObject.plus(pair: Pair<String, String>): JsonObject =
        JsonObject(LinkedHashMap<String, JsonElement>(this).apply { put(pair.first, JsonPrimitive(pair.second)) })
}

fun json(text: String): JsonObject = PaseoWireJson.parseToJsonElement(text) as JsonObject

fun JsonObject.with(vararg pairs: Pair<String, Any?>): JsonObject = JsonObject(LinkedHashMap<String, JsonElement>(this).apply { putAll(jsonOf(*pairs)) })

fun JsonObject.without(key: String): JsonObject = JsonObject(this - key)

/** Replace the value at a path of object keys / array indexes. */
fun JsonElement.edit(path: List<Any>, value: JsonElement?): JsonElement {
    if (path.isEmpty()) return value ?: error("cannot remove root")
    val head = path.first()
    return when (this) {
        is JsonObject -> {
            val key = head as String
            val map = LinkedHashMap<String, JsonElement>(this)
            if (path.size == 1 && value == null) map.remove(key) else map[key] = (this[key] ?: JsonObject(emptyMap())).edit(path.drop(1), value)
            JsonObject(map)
        }
        is JsonArray -> {
            val index = head as Int
            JsonArray(mapIndexed { i, e -> if (i == index) e.edit(path.drop(1), value) else e })
        }
        else -> error("not a container")
    }
}

fun JsonObject.edit(vararg path: Any, value: Any?): JsonObject =
    (this as JsonElement).edit(path.toList(), if (value == null) null else io.github.eslamasabry.oc.protocol.paseo.toJson(value)) as JsonObject

const val DIR = "/work/app"

/** test/paseo_gateway_test.dart `writePermission`. */
fun writePermission(id: String): JsonObject =
    jsonOf(
        "id" to id,
        "provider" to "claude",
        "name" to "Write",
        "kind" to "tool",
        "input" to mapOf("file_path" to "$DIR/hello.txt", "content" to "hi"),
        "detail" to mapOf("type" to "write", "filePath" to "$DIR/hello.txt", "content" to "hi"),
        "suggestions" to listOf(mapOf("type" to "setMode", "mode" to "acceptEdits", "destination" to "session")),
        "metadata" to mapOf("toolUseId" to "toolu_1"),
    )

/**
 * test/fixtures/paseo/native_questions_0_9_2.json, inlined so the common tests run on every target.
 * `NativeQuestionFixtureTest` (desktopTest) proves it still equals the file on disk.
 */
const val NATIVE_QUESTIONS_0_9_2: String = """{
  "claude": {
    "id": "permission-question", "provider": "claude", "name": "AskUserQuestion", "kind": "question",
    "input": {"questions": [
      {"header": "Stack", "question": "Which stack should I use?", "options": [{"label": "Dart", "description": "Shared language"}, {"label": "Kotlin", "description": "Native"}], "multiSelect": false, "allowOther": true},
      {"header": "Features", "question": "Which features do you need?", "options": [{"label": "Search"}, {"label": "History"}], "multiSelect": true, "allowOther": true}
    ]}, "metadata": {"toolUseId": "tool-question"}
  },
  "pi": {
    "id": "ui-question", "provider": "pi", "name": "Pi ask_user", "kind": "question", "title": "Which stack?",
    "input": {"questions": [
      {"question": "Which stack?", "header": "Response", "options": [{"label": "Dart"}, {"label": "Kotlin"}], "multiSelect": false, "allowOther": true},
      {"question": "Optional comment", "header": "Comment", "options": [], "multiSelect": false, "placeholder": "Optional comment (press Enter to skip)...", "allowEmpty": true}
    ]}, "metadata": {"extensionUiMethod": "select", "answerHeader": "Response", "commentHeader": "Comment", "combinedAskUser": "ask_user_select_optional_comment", "selectOptions": ["Dart", "Kotlin"], "freeformSentinel": "✏️ Type custom response..."}
  },
  "plan": {
    "id": "permission-plan", "provider": "claude", "name": "ExitPlanMode", "kind": "plan",
    "input": {"plan": "# Plan\n\n1. Inspect the parser.\n2. Add focused tests.\n3. Implement and verify."},
    "metadata": {"planText": "# Plan\n\n1. Inspect the parser.\n2. Add focused tests.\n3. Implement and verify.", "toolUseId": "tool-plan"},
    "actions": [{"id": "reject", "label": "Reject", "behavior": "deny"}, {"id": "implement", "label": "Implement", "behavior": "allow"}, {"id": "implement_resume", "label": "Implement without asking", "behavior": "allow"}]
  }
}"""

fun questionFixture(kind: String): JsonObject = json(NATIVE_QUESTIONS_0_9_2).obj(kind)!!
