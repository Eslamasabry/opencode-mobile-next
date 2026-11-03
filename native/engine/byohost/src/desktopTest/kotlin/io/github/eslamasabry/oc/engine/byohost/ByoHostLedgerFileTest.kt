package io.github.eslamasabry.oc.engine.byohost

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ByoHostLedgerFileTest {
    @Test
    fun `B-AGENT-044 the Kotlin ledger equals the reviewed scripts byo-host release-pins json`() {
        val file = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "scripts/byo-host/release-pins.json") }
            .first { it.isFile }
        val json = Json.parseToJsonElement(file.readText()).jsonObject
        val ledger = ByoHostReleasePins.Ledger
        assertEquals(json.getValue("schemaVersion").jsonPrimitive.int, ledger.schemaVersion)
        assertEquals(json.getValue("openCodeVersion").jsonPrimitive.content, ledger.openCodeVersion)
        assertEquals(json.getValue("bundleVersion").jsonPrimitive.content, ledger.bundleVersion)
        val artifacts = json.getValue("artifacts").jsonObject
        assertEquals(artifacts.keys, ledger.artifacts.keys)
        for ((arch, entry) in artifacts) {
            val pinned = ledger.artifacts.getValue(arch)
            assertEquals(entry.jsonObject.getValue("url").jsonPrimitive.content, pinned.url)
            assertEquals(entry.jsonObject.getValue("sha256").jsonPrimitive.content, pinned.sha256)
        }
        assertTrue(json.getValue("appVersions").jsonObject.isEmpty())
        assertTrue(ledger.appVersions.isEmpty())
    }
}
