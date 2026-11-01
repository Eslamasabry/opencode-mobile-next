package io.github.eslamasabry.oc.core.domain.capabilities

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Capability snapshot (plan 04 §11.4, PROTOCOLS-38). `default.json` is the parity record of Dart's
 * `const ServerCapabilities()` defaults, in Dart's field order. The Kotlin defaults must equal it flag for flag,
 * and while the Flutter tree is in the repo the record must still equal the Dart source (drift check).
 */
class CapabilitySnapshotTest {
    private fun snapshot(): List<Pair<String, Boolean>> {
        val text = checkNotNull(javaClass.getResource("/capabilities/default.json")).readText()
        return Json.parseToJsonElement(text).jsonObject.entries.map { (k, v) -> k to v.jsonPrimitive.boolean }
    }

    @Test
    fun `capability snapshot - every Kotlin default equals the Dart vector file, in Dart order`() {
        val kotlin = ServerCapabilities.Unknown.flags().entries.map { it.key to it.value }
        val dart = snapshot()
        assertEquals(dart.map { it.first }, kotlin.map { it.first }, "flag names or order differ")
        assertEquals(dart, kotlin)
        assertEquals(86, kotlin.size)
    }

    @Test
    fun `capability snapshot - the vector file still matches the Dart source`() {
        val dartFile = File("../../../lib/domain/server_gateway/capabilities.dart")
        if (!dartFile.isFile) return // Flutter tree removed: the JSON is the parity record from then on.
        val source = dartFile.readText()
        val ctor = source.substring(source.indexOf("const ServerCapabilities({")).substringBefore("});")
        val defaults = Regex("""this\.(\w+)\s*=\s*(true|false)""").findAll(ctor).map { it.groupValues[1] to (it.groupValues[2] == "true") }.toList()
        val fields = Regex("""(?m)^  final bool (\w+);""").findAll(source).map { it.groupValues[1] }.toList()
        assertEquals(fields, defaults.map { it.first }, "Dart constructor and field order differ")
        assertEquals(defaults, snapshot(), "default.json is stale: regenerate it from capabilities.dart")
    }

    @Test
    fun `flags() is the serial form, so names cannot drift from the properties`() {
        val encoded = Json { encodeDefaults = true }.encodeToJsonElement(ServerCapabilities.serializer(), ServerCapabilities()) as JsonObject
        assertEquals(encoded.keys.toList(), ServerCapabilities.Unknown.flags().keys.toList())
        assertTrue(ServerCapabilities.Unknown.flags().getValue("promptAttachments"))
    }
}
