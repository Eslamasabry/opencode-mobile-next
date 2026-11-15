package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.model.ModelSelection
import io.github.eslamasabry.oc.core.model.NewSession
import io.github.eslamasabry.oc.core.model.OutgoingPrompt
import io.github.eslamasabry.oc.protocol.paseo.FakeDaemon
import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import io.github.eslamasabry.oc.protocol.paseo.obj
import io.github.eslamasabry.oc.protocol.paseo.str
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Issue 147: a phone agent started in a folder of the phone's storage is told, at its start, what that storage cannot
 * do (links, running files directly), so it installs and runs things the way that works there.
 */
class PaseoStorageNoteTest {
    private fun kotlinx.coroutines.test.TestScope.startIn(directory: String, version: String): GatewayHarness {
        val daemon = FakeDaemon().seedDefaults().apply { this.version = version }
        return GatewayHarness(this, daemon, directory = directory)
    }

    private suspend fun GatewayHarness.firstAgentConfig(): kotlinx.serialization.json.JsonObject {
        daemon.handlers["create_agent_request"] = {
            "status" to jsonOf("status" to "agent_created", "agent" to agentJson("real-1", status = "initializing"))
        }
        val draft = assertIs<Outcome.Ok<io.github.eslamasabry.oc.core.model.Session>>(gateway.createSession(NewSession())).value.id
        gateway.prompt(draft, OutgoingPrompt("hello", ModelSelection(ModelRef("claude", "haiku"))))
        return daemon.of("create_agent_request").single().obj("config")!!
    }

    @Test
    fun `I147-STORAGE an agent started in the phone's storage is told what that storage can't do`() = runTest {
        val config = startIn("/storage/emulated/0/MyProj", "0.15.4").firstAgentConfig()
        assertEquals("/storage/emulated/0/MyProj", config.str("cwd"))
        val note = config.str("systemPrompt")
        assertTrue(note != null && "--no-bin-links" in note && "sh script.sh" in note, "note: $note")
    }

    @Test
    fun `I147-STORAGE an agent in the app's own projects, or on a helper too old to append it, gets nothing extra`() = runTest {
        assertNull(startIn("/root/projects/app", "0.15.4").firstAgentConfig().str("systemPrompt"))
        assertNull(startIn("/sdcard/MyProj", "0.14.0").firstAgentConfig().str("systemPrompt"))
        assertEquals(PaseoStorageNote.NOTE, PaseoStorageNote.forFolder("/sdcard/MyProj", "0.16.0"))
        assertNull(PaseoStorageNote.forFolder("/sdcard/MyProj", "dev"))
    }
}
