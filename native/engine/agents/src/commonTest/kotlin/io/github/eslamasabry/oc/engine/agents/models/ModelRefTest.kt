package io.github.eslamasabry.oc.engine.agents.models

import io.github.eslamasabry.oc.engine.agents.json.AgentJson
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ModelRefTest {
    private fun obj(text: String) = AgentJson.parseOrNull(text) as JsonObject

    @Test
    fun `B-PROTO-062 bareModelId strips only this provider's prefix`() {
        assertEquals("gpt-5.6-sol", bareModelId("openai", "openai/gpt-5.6-sol"))
        assertEquals("gpt-5.6-sol", bareModelId("openai", "gpt-5.6-sol"))
        assertEquals("anthropic/claude", bareModelId("openai", "anthropic/claude"))
        assertEquals("openai/gpt", bareModelId("", "openai/gpt"))
        assertEquals("openai/", bareModelId("openai", "openai/"))
    }

    @Test
    fun `B-PROTO-062 normalized returns itself when already bare`() {
        val bare = ModelRef("openai", "gpt-5.6-sol")
        assertSame(bare, bare.normalized)
        val composite = ModelRef("openai", "openai/gpt-5.6-sol")
        assertEquals("gpt-5.6-sol", composite.normalized.modelId)
        assertEquals("openai", composite.normalized.providerId)
    }

    @Test
    fun `B-PROTO-062 a v2 model with a composite id and no modelID gets a bare modelID`() {
        val info = obj(
            """{"id":"openai/gpt-5.6-sol","providerID":"openai","name":"GPT-5.6 Sol",
            "capabilities":{"tools":true,"input":["text"],"output":["text"]},"variants":[],"time":{"released":0},
            "cost":{"input":0,"output":0,"cache":{"read":0,"write":0}},"status":"active","enabled":true,
            "limit":{"context":1,"output":1}}""",
        )
        assertEquals("gpt-5.6-sol", catalogEntryModelId(info))
        val explicit = obj("""{"id":"openai/gpt-5.6-sol","modelID":"gpt-5.6-sol","providerID":"openai","name":"GPT-5.6 Sol"}""")
        assertEquals("gpt-5.6-sol", catalogEntryModelId(explicit))
    }

    @Test
    fun `B-PROTO-062 a v2 model ref with a composite id is sent bare`() {
        val ref = WireModelRef.fromJson(obj("""{"id":"openai/gpt-5.6-sol","providerID":"openai"}"""))
        assertEquals("gpt-5.6-sol", ref?.id)
        assertEquals("""{"id":"gpt-5.6-sol","providerID":"openai"}""", AgentJson.encode(ref!!.toJson()))
    }

    @Test
    fun `B-SEC-103 error headline drops the class name and the stack trace`() {
        val raw = "ProviderModelNotFoundError: Model not found: openai/x. Did you mean: x?\n" +
            "    at <anonymous> (/chunk.js:1)\n    at SessionPrompt.run (/chunk.js:2)"
        assertEquals("Model not found: openai/x. Did you mean: x?", errorHeadline(raw))
        assertTrue(errorHasDetails(raw))
        assertEquals("plain message", errorHeadline("plain message"))
        assertFalse(errorHasDetails("plain message"))
        assertEquals(MessageErrorKind.ModelNotFound, MessageErrorKind.refineFromText(MessageErrorKind.Unknown, raw))
        assertEquals(MessageErrorKind.ProviderAuth, MessageErrorKind.refineFromText(MessageErrorKind.ProviderAuth, raw))
        assertEquals(MessageErrorKind.ModelNotFound, MessageErrorKind.fromName("ProviderModelNotFoundError"))
    }
}
