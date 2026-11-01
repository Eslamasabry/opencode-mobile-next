package io.github.eslamasabry.oc.protocol.paseo.requests

import io.github.eslamasabry.oc.protocol.paseo.PaseoWireJson
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** The inlined fixture of the common tests equals the Flutter fixture on disk (no drift). */
class NativeQuestionFixtureTest {
    @Test
    fun `B-CHAT-072 the inlined native question fixture equals the Flutter fixture file`() {
        val root = System.getProperty("oc.repo.root") ?: error("oc.repo.root is not set")
        val disk = File(root, "test/fixtures/paseo/native_questions_0_9_2.json").readText()
        assertEquals(PaseoWireJson.parseToJsonElement(disk), PaseoWireJson.parseToJsonElement(NATIVE_QUESTIONS_0_9_2))
    }
}
