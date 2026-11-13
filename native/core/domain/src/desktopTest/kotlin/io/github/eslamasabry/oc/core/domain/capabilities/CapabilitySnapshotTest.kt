package io.github.eslamasabry.oc.core.domain.capabilities

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

    /**
     * Native-only flags, appended after Dart's 85 (`claudeAgent`, which the Flutter freeze tip had and the released
     * app that lib/ holds since the master merge does not; N-41 `projectFolderCreation`; Paseo agent features; the
     * project worktree inspection pane; B-169 per-prompt model choice). Dart has no twin for them; each defaults to
     * false.
     */
    private val nativeOnly =
        listOf("claudeAgent", "projectFolderCreation", "agentFeatures", "worktreeInspection", "promptModelChoice")

    @Test
    fun `capability snapshot - every Kotlin default equals the Dart vector file, in Dart order`() {
        // Dart's `const ServerCapabilities()` defaults are the constructor defaults, not the native-only Unknown.
        val kotlin = ServerCapabilities().flags().entries.map { it.key to it.value }
        val dart = snapshot()
        assertEquals(85, dart.size)
        val shared = kotlin.take(dart.size)
        assertEquals(dart.map { it.first }, shared.map { it.first }, "flag names or order differ")
        assertEquals(dart, shared)
        assertEquals(nativeOnly.map { it to false }, kotlin.drop(dart.size), "native-only flags differ")
    }

    @Test
    fun `flags() is the serial form, so names cannot drift from the properties`() {
        val encoded = Json { encodeDefaults = true }.encodeToJsonElement(ServerCapabilities.serializer(), ServerCapabilities()) as JsonObject
        assertEquals(encoded.keys.toList(), ServerCapabilities.Unknown.flags().keys.toList())
        assertTrue(ServerCapabilities().flags().getValue("promptAttachments"))
    }
}
