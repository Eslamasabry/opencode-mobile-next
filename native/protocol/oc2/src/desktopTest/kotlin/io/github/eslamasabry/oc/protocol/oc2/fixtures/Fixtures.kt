package io.github.eslamasabry.oc.protocol.oc2.fixtures

import io.github.eslamasabry.oc.protocol.oc2.events.Oc2Envelope
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2Wire
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.File

/** native/fixtures/oc2 (system property set by the module's build file). */
internal object Fixtures {
    val root: File = File(requireNotNull(System.getProperty("oc.fixtures.oc2")) { "oc.fixtures.oc2 not set" })
    val repoRoot: File = File(requireNotNull(System.getProperty("oc.repo.root")) { "oc.repo.root not set" })

    fun file(rel: String): File = File(root, "flutter/$rel")

    fun text(rel: String): String = file(rel).readText()

    fun json(rel: String): JsonElement = Oc2Wire.json.parseToJsonElement(text(rel))

    fun data(rel: String): JsonObject = json(rel).jsonObject.getValue("data").jsonObject

    fun dataList(rel: String): List<JsonObject> = json(rel).jsonObject.getValue("data").jsonArray.map { it.jsonObject }

    /** `data:` lines of an SSE capture, each decoded as one envelope. */
    fun sse(rel: String): List<Oc2Envelope> =
        text(rel).lines().filter { it.startsWith("data:") }.map { Oc2Envelope.parse(it.removePrefix("data:").trim()) }

    /** The coverage sample sets: `{cases:[{id, payload:[...]}]}`. */
    fun cases(rel: String): Map<String, JsonArray> =
        json(rel).jsonObject.getValue("cases").jsonArray.associate {
            val case = it.jsonObject
            case.getValue("id").toString().trim('"') to case.getValue("payload").jsonArray
        }
}
