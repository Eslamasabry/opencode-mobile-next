package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.capability.KitCapabilities
import io.github.eslamasabry.oc.ui.kit.capability.KitCapabilityOffer
import io.github.eslamasabry.oc.ui.kit.capability.KitCapabilityRow
import io.github.eslamasabry.oc.ui.kit.capability.KitCapabilityState
import io.github.eslamasabry.oc.ui.kit.capability.KitCapabilityWords
import io.github.eslamasabry.oc.ui.kit.capability.KitEnableFlowHandler
import io.github.eslamasabry.oc.ui.kit.capability.KitEnableFlowRegistry
import io.github.eslamasabry.oc.ui.kit.capability.KitEnableFlows
import io.github.eslamasabry.oc.ui.kit.capability.KitEnableRequest
import io.github.eslamasabry.oc.ui.kit.capability.KitHost
import io.github.eslamasabry.oc.ui.kit.capability.LocalKitEnableFlows
import io.github.eslamasabry.oc.ui.kit.foundation.KitRuleBroken
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The capability explainer (Flutter `test/kit/kit_capability_explainer_test.dart` and its `_r10` sibling, B-KIT-013). */
@OptIn(ExperimentalTestApi::class)
class KitCapabilityExplainerTest {
    private val repo: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "docs/ux-system/capabilities.json").isFile }
    private val json: JsonObject = Json.parseToJsonElement(File(repo, "docs/ux-system/capabilities.json").readText()).jsonObject
    private val matrix = json.getValue("matrix").jsonArray.map { it.jsonObject }
    private val flowCapabilities = json.getValue("enableFlows").jsonArray.map { it.jsonObject.getValue("capability").jsonPrimitive.content }
    private val columns = mapOf(
        "builtin" to KitHost.ThisPhone, "termux" to KitHost.Termux, "oc2Computer" to KitHost.OpenCode2,
        "codex" to KitHost.Codex, "paseo" to KitHost.Paseo, "demo" to KitHost.Demo,
    )

    private fun hostsWith(row: JsonObject, status: String): Set<KitHost> =
        row.getValue("hosts").jsonObject.filter { (_, v) -> v.jsonObject.getValue("status").jsonPrimitive.content == status }
            .keys.map { columns.getValue(it) }.toSet()

    private fun KitEnableFlowRegistry.record(capability: String): MutableList<KitEnableRequest> {
        val requests = mutableListOf<KitEnableRequest>()
        register(KitCapabilities.byId(capability)!!.enableFlow!!) { requests += it }
        return requests
    }

    @Composable
    private fun Stage(flows: KitEnableFlowRegistry, content: @Composable () -> Unit) {
        KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en", stillFrame = true) {
            CompositionLocalProvider(LocalKitEnableFlows provides flows) { Column { content() } }
        }
    }

    // ---- registry parity -------------------------------------------------------------------------------------------

    @Test
    fun `B-KIT-013 every enable flow has an entry with a flow id, 21 and distinct`() {
        assertEquals(21, KitEnableFlows.all.size)
        assertEquals(21, KitEnableFlows.all.toSet().size)
        assertEquals(21, flowCapabilities.size)
        for (capability in flowCapabilities) {
            val entry = assertNotNull(KitCapabilities.byId(capability), capability)
            assertTrue(entry.enableFlow in KitEnableFlows.all, capability)
        }
        val withFlow = KitCapabilities.all.filter { it.enableFlow != null }
        assertEquals(flowCapabilities.toSet(), withFlow.map { it.id }.toSet())
        assertEquals(KitEnableFlows.all.toSet(), withFlow.map { it.enableFlow }.toSet())
    }

    @Test
    fun `B-KIT-013 every matrix capability has the same supported and partly hosts`() {
        assertEquals(34, matrix.size)
        assertEquals(matrix.size, KitCapabilities.all.size)
        for (row in matrix) {
            val id = row.getValue("capability").jsonPrimitive.content
            val entry = assertNotNull(KitCapabilities.byId(id), id)
            assertEquals(hostsWith(row, "supported"), entry.supported, id)
            assertEquals(hostsWith(row, "partly"), entry.partly, id)
        }
        assertTrue(KitCapabilities.byId("flag:toolInventory")!!.supported.isEmpty())
        assertNull(KitCapabilities.byId("flag:toolInventory")!!.enableFlow)
        assertNull(KitCapabilities.byId("flag:terminal")!!.enableFlow)
    }

    // ---- registration ----------------------------------------------------------------------------------------------

    @Test
    fun `B-KIT-013 enabling needs a registered handler, and with none it is a no-op returning false`() = runTest {
        val flows = KitEnableFlowRegistry()
        assertFalse(flows.canEnable("voice.model"))
        assertEquals(21, flows.unregisteredFlows.size)
        assertFalse(flows.enable("voice.model", host = KitHost.Codex))
        flows.register(KitEnableFlows.VOICE_MODEL_SETUP) {}
        assertTrue(flows.canEnable("voice.model"))
        assertFalse(KitEnableFlows.VOICE_MODEL_SETUP in flows.unregisteredFlows)
        assertEquals(20, flows.unregisteredFlows.size)
        assertFalse(flows.canEnable("flag:sessionDiff"), "no flow at all: never enable")
        assertFalse(flows.enable("flag:sessionDiff"))
        assertFalse(flows.canEnable("no.such"))
        assertTrue(flows.enable("voice.model"))
        assertFailsWith<IllegalArgumentException> { flows.register("no-such-flow") {} }
    }

    @Test
    fun `B-KIT-013 without a handler the row and the state only explain`() = runComposeUiTest {
        setContent {
            Stage(KitEnableFlowRegistry()) {
                KitCapabilityRow("voice.model")
                KitCapabilityState("voice.model")
            }
        }
        onAllNodesWithText("Voice typing").assertCountEquals(2)
        onAllNodesWithText("Needs a voice model on this phone.", substring = true).assertCountEquals(2)
        onAllNodesWithText("Download voice model").assertCountEquals(0)
    }

    @Test
    fun `B-KIT-013 each of the 21 enable flows runs from its row with the request`() = runComposeUiTest {
        val flows = KitEnableFlowRegistry()
        val requests = mutableListOf<KitEnableRequest>()
        for (flow in KitEnableFlows.all) flows.register(flow, KitEnableFlowHandler { requests += it })
        val shown = mutableStateOf(flowCapabilities.first())
        val labels = mutableMapOf<String, String>()
        setContent {
            Stage(flows) {
                val id = shown.value
                labels[id] = KitCapabilityWords.enableLabelOf(id).orEmpty()
                labels["$id.title"] = KitCapabilityWords.titleOf(id)
                KitCapabilityRow(id, host = KitHost.OpenCode2, serverName = "laptop", source = "test-page")
            }
        }
        for (capability in flowCapabilities) {
            shown.value = capability
            waitForIdle()
            val label = labels.getValue(capability)
            assertTrue(label.isNotEmpty(), capability)
            assertTrue(labels.getValue("$capability.title").isNotEmpty(), capability)
            onNodeWithText(labels.getValue("$capability.title")).assertExists()
            onNode(hasText(label) and hasClickAction()).performClick()
            waitForIdle()
            assertEquals(
                KitEnableRequest(capability, KitCapabilities.byId(capability)!!.enableFlow!!, KitHost.OpenCode2, "laptop", "test-page"),
                requests.last(),
                capability,
            )
        }
        assertEquals(21, requests.size)
        assertEquals(21, requests.map { it.flow }.toSet().size)
    }

    // ---- enable ----------------------------------------------------------------------------------------------------

    @Test
    fun `B-KIT-013 tapping enable on the state calls the handler once with the request`() = runComposeUiTest {
        val flows = KitEnableFlowRegistry()
        val requests = flows.record("team.on")
        setContent { Stage(flows) { KitCapabilityState("team.on", host = KitHost.ThisPhone, serverName = "Pixel", source = "work-home") } }
        onNodeWithText("Turn on AI Team").performClick()
        waitForIdle()
        assertEquals(listOf(KitEnableRequest("team.on", KitEnableFlows.TEAM_TURN_ON, KitHost.ThisPhone, "Pixel", "work-home")), requests)
    }

    @Test
    fun `B-KIT-013 while the handler runs the action is working and a second tap does not run it again`() = runComposeUiTest {
        val flows = KitEnableFlowRegistry()
        val done = CompletableDeferred<Unit>()
        var calls = 0
        flows.register(KitEnableFlows.VOICE_MODEL_SETUP) {
            calls++
            done.await()
        }
        setContent { Stage(flows) { KitCapabilityState("voice.model") } }
        onNode(hasText("Download voice model") and hasClickAction()).performClick()
        waitForIdle()
        onNode(hasText("Download voice model") and hasClickAction()).assertIsNotEnabled()
        onNodeWithText("Download voice model").performClick()
        waitForIdle()
        assertEquals(1, calls)
        done.complete(Unit)
        waitForIdle()
        onNode(hasText("Download voice model") and hasClickAction()).assertExists()
        onNode(hasText("Download voice model") and hasClickAction()).performClick()
        waitForIdle()
        assertEquals(2, calls)
    }

    @Test
    fun `B-KIT-013 a failing handler does not crash and the explainer stays`() = runComposeUiTest {
        val flows = KitEnableFlowRegistry()
        flows.register(KitEnableFlows.VOICE_MODEL_SETUP) { error("download failed") }
        setContent { Stage(flows) { KitCapabilityRow("voice.model") } }
        onNodeWithText("Download voice model").performClick()
        waitForIdle()
        onNodeWithText("Voice typing").assertExists()
        onNodeWithText("Download voice model").assertExists()
    }

    // ---- why wording -----------------------------------------------------------------------------------------------

    @Test
    fun `B-KIT-013 why names the host and the hosts that can, without engine words`() = runComposeUiTest {
        val seen = mutableMapOf<String, String>()
        val all = mutableListOf<Pair<String, String>>()
        setContent {
            Stage(KitEnableFlowRegistry()) {
                seen["files"] = KitCapabilityWords.whyOf("flag:fileBrowsing+terminal", KitHost.Codex)
                seen["voiceHosts"] = KitCapabilityWords.hostsLineOf("voice.model")
                seen["named"] = KitCapabilityWords.whyOf("flag:sessionDiff", KitHost.Paseo, serverName = "laptop")
                seen["can"] = KitCapabilityWords.whyOf("voice.model", KitHost.Codex)
                seen["tools"] = KitCapabilityWords.whyOf("flag:toolInventory")
                seen["toolsOc2"] = KitCapabilityWords.whyOf("flag:toolInventory", KitHost.OpenCode2)
                seen["toolsTitle"] = KitCapabilityWords.titleOf("flag:toolInventory")
                seen["mcp"] = KitCapabilityWords.whyOf("mcp.any")
                seen["mcpOffer"] = KitCapabilityWords.offerOf("mcp.any").orEmpty()
                for (entry in KitCapabilities.all) {
                    for (host in listOf(null) + KitHost.entries) {
                        val words = listOfNotNull(
                            KitCapabilityWords.titleOf(entry.id),
                            KitCapabilityWords.whyOf(entry.id, host),
                            KitCapabilityWords.enableLabelOf(entry.id),
                        ).joinToString(" ")
                        all += "${entry.id} on $host" to words.lowercase()
                    }
                }
            }
        }
        waitForIdle()
        val files = seen.getValue("files")
        assertTrue(files.contains("Codex") && files.contains("aren't available"), files)
        assertTrue(files.contains("this phone") && files.contains("computers with OpenCode"), files)
        assertEquals(
            "Works on ⁨this phone⁩, ⁨computers with OpenCode 2⁩, ⁨Codex⁩ and ⁨Paseo⁩",
            seen.getValue("voiceHosts"),
        )
        assertTrue(seen.getValue("named").contains("⁨laptop⁩"), seen.getValue("named"))
        assertTrue(seen.getValue("named").contains("isn't available"), seen.getValue("named"))
        assertEquals("Needs a voice model on this phone.", seen.getValue("can"))
        assertEquals("Tool list", seen.getValue("toolsTitle"))
        assertEquals("This server doesn't list the tools its agent can use.", seen.getValue("tools"))
        assertTrue(seen.getValue("toolsOc2").contains("isn't available"))
        assertFalse(seen.getValue("toolsOc2").contains("OpenCode 1"))
        assertFalse(seen.getValue("toolsOc2").contains("Works on"))
        assertEquals("This server can't add extra tools from the app.", seen.getValue("mcp"))
        assertFalse(seen.getValue("mcp") == seen.getValue("mcpOffer"))
        assertFalse(seen.getValue("mcp").lowercase().let { it.contains("yet") || it.contains("mcp") })
        val engineWords = listOf(
            "convoy", "formula", "bead", "rig", "city", "polecat", "refinery", "sling", "wisp", "mayor", "gastown", "pty",
            "sse", listOf(127, 0, 0, 1).joinToString("."), // the loopback address, built so no address sits in the source
        )
        assertEquals(7 * 34, all.size)
        for ((where, words) in all) {
            for (engine in engineWords) assertFalse(Regex("\\b${Regex.escape(engine)}\\b").containsMatchIn(words), "$where says $engine")
        }
    }

    @Test
    fun `B-KIT-013 no capability or host wording mentions the setup assistant`() {
        val values = File(repo, "native/ui/words/src/commonMain/composeResources/values").listFiles().orEmpty()
            .filter { it.extension == "xml" }
            .flatMap { file ->
                Regex("""<string name="((?:kitCap|kitHost)[^"]*)">([^<]*)<""").findAll(file.readText()).map { it.groupValues[1] to it.groupValues[2] }
            }
        assertTrue(values.size > 100, "found ${values.size}")
        for ((key, value) in values) assertFalse(value.lowercase().contains("assistant"), key)
    }

    // ---- offer -----------------------------------------------------------------------------------------------------

    @Test
    fun `B-KIT-013 Not now calls once without running the flow, and folded shows the quiet row`() = runComposeUiTest {
        val flows = KitEnableFlowRegistry()
        val requests = flows.record("team.on")
        var notNow = 0
        val folded = mutableStateOf(false)
        setContent { Stage(flows) { KitCapabilityOffer("team.on", onNotNow = { notNow++ }, folded = folded.value) } }
        onNodeWithTag("kit-notice-offer").assertExists()
        onNodeWithText("This server can also run an AI team. Turn it on?").assertExists()
        onNodeWithTag("kit-notice-dismiss", useUnmergedTree = true).performClick()
        waitForIdle()
        assertEquals(1, notNow)
        assertTrue(requests.isEmpty())
        folded.value = true
        waitForIdle()
        onNodeWithTag("kit-notice-offer").assertDoesNotExist()
        onNodeWithTag("kit-capability-row").assertExists()
        onNodeWithText("AI Team").assertExists()
        onNodeWithText("Turn on AI Team").assertExists()
    }

    @Test
    fun `B-KIT-013 the offer runs the flow, and without a handler it explains`() = runComposeUiTest {
        val flows = mutableStateOf(KitEnableFlowRegistry())
        setContent { Stage(flows.value) { KitCapabilityOffer("voice.model", onNotNow = {}, message = "Talk instead of typing?") } }
        onNodeWithTag("kit-notice-offer").assertDoesNotExist()
        onNodeWithText("Needs a voice model on this phone.").assertExists()
        val withHandler = KitEnableFlowRegistry()
        val requests = withHandler.record("voice.model")
        flows.value = withHandler
        waitForIdle()
        onNodeWithText("Talk instead of typing?").assertExists()
        onNodeWithText("Download voice model", useUnmergedTree = true).performClick()
        waitForIdle()
        assertEquals(1, requests.size)
    }

    // ---- asserts ---------------------------------------------------------------------------------------------------

    @Test
    fun `B-KIT-013 a prerequisite with no flow or no handler, and an unknown id, are refused in debug`() {
        assertFailsWith<KitRuleBroken> {
            runComposeUiTest { setContent { Stage(KitEnableFlowRegistry()) { KitCapabilityState("flag:sessionDiff", prerequisite = true) } } }
        }
        assertFailsWith<KitRuleBroken> {
            runComposeUiTest { setContent { Stage(KitEnableFlowRegistry()) { KitCapabilityState("project.open", prerequisite = true) } } }
        }
        assertFailsWith<KitRuleBroken> {
            runComposeUiTest { setContent { Stage(KitEnableFlowRegistry()) { KitCapabilityRow("no.such") } } }
        }
    }

    @Test
    fun `B-KIT-013 a prerequisite with a handler shows its button and its cost`() = runComposeUiTest {
        val flows = KitEnableFlowRegistry()
        flows.record("project.open")
        setContent { Stage(flows) { KitCapabilityState("project.open", prerequisite = true, cost = listOf("Takes seconds")) } }
        onNodeWithText("Choose a project").assertExists()
        onNodeWithTag("kit-state-view").assertExists()
        onNodeWithText("Takes seconds", substring = true).assertExists()
    }

    // ---- keyboard --------------------------------------------------------------------------------------------------

    @Test
    fun `B-KIT-013 Tab reaches the enable action and Enter runs it`() = runComposeUiTest {
        val flows = KitEnableFlowRegistry()
        val requests = flows.record("voice.model")
        setContent { Stage(flows) { KitCapabilityRow("voice.model") } }
        onRoot().performKeyInput {
            pressKey(Key.Tab)
            pressKey(Key.Enter)
        }
        waitForIdle()
        assertEquals(1, requests.size)
    }

    @Test
    fun `B-KIT-013 the offer puts Not now after the enable action in Tab order`() = runComposeUiTest {
        val flows = KitEnableFlowRegistry()
        val requests = flows.record("team.on")
        var notNow = 0
        setContent { Stage(flows) { KitCapabilityOffer("team.on", onNotNow = { notNow++ }, message = "Turn it on.") } }
        onRoot().performKeyInput {
            pressKey(Key.Tab)
            pressKey(Key.Tab)
            pressKey(Key.Enter)
        }
        waitForIdle()
        assertEquals(1, notNow)
        assertTrue(requests.isEmpty())
    }
}
