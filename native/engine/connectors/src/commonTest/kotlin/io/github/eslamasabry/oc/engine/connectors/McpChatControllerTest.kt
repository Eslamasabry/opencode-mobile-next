package io.github.eslamasabry.oc.engine.connectors

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.McpGateway
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.model.McpAuthLaunch
import io.github.eslamasabry.oc.core.model.McpConfigScope
import io.github.eslamasabry.oc.core.model.McpServerDraft
import io.github.eslamasabry.oc.core.model.McpServerInfo
import io.github.eslamasabry.oc.engine.connectors.catalog.McpCatalogItem
import io.github.eslamasabry.oc.engine.connectors.chat.McpChatController
import io.github.eslamasabry.oc.engine.connectors.chat.McpChatFailure
import io.github.eslamasabry.oc.engine.connectors.chat.McpChatPhase
import io.github.eslamasabry.oc.engine.connectors.chat.ParsedCode
import io.github.eslamasabry.oc.engine.connectors.chat.mcpLoopbackRedirect
import io.github.eslamasabry.oc.engine.connectors.chat.parseMcpAuthorizationCode
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/** The Dart test's `Gateway` fake over the core MCP gateway. */
internal open class FakeMcp : McpGateway {
    var inventory: List<McpServerInfo> = emptyList()
    var adds = 0
    var lists = 0
    var auths = 0
    var completions = 0
    var cancels = 0
    var failure: Failure? = null
    var addGate: CompletableDeferred<Unit>? = null
    var listGate: CompletableDeferred<Unit>? = null
    var statusAfterAdd = "connected"
    var launch = McpAuthLaunch("https://login.example.com/authorize?state=test-state", "test-state")

    override suspend fun mcpServers(): Outcome<ImmutableList<McpServerInfo>> {
        lists++
        failure?.let { return it.failed() }
        listGate?.await()
        return inventory.toImmutableList().ok()
    }

    override suspend fun addMcpServer(draft: McpServerDraft, scope: McpConfigScope): Outcome<Unit> {
        adds++
        check(scope == McpConfigScope.RuntimeLocation)
        addGate?.await()
        inventory = listOf(McpServerInfo(draft.name, statusAfterAdd, null))
        return Unit.ok()
    }

    override suspend fun startMcpAuthentication(name: String): Outcome<McpAuthLaunch> {
        auths++
        return launch.ok()
    }

    override suspend fun completeMcpAuthentication(name: String, code: String): Outcome<McpServerInfo> {
        completions++
        inventory = listOf(McpServerInfo(name, "connected", null))
        return inventory.single().ok()
    }

    override suspend fun cancelMcpAuthentication(name: String): Outcome<Unit> {
        cancels++
        return Unit.ok()
    }
}

internal fun chatCaps(enabled: Boolean = true, refresh: Boolean = true, oauth: Boolean = true) =
    ServerCapabilities(
        mcpRuntimeAdds = true,
        mcpChatConnect = enabled,
        mcpChatToolRefresh = refresh,
        mcpChatOAuth = oauth,
    )

class McpChatControllerTest {
    private val gateway = FakeMcp()
    private var current = true

    private fun CoroutineScope.make(caps: ServerCapabilities = chatCaps(), item: McpCatalogItem = hostedItem()) =
        McpChatController(item, { gateway.ok() }, { caps }, { current }, this)

    @Test
    fun `B-AGENT-135 runtime add followed by authoritative status makes next-turn tools ready`() = runTest {
        val controller = backgroundScope.make()
        controller.connect("design")
        assertEquals(1, gateway.adds)
        assertEquals(McpChatPhase.ToolsReady, controller.snapshot.value.phase)
        assertNull(controller.snapshot.value.authorizationUrl)
    }

    @Test
    fun `B-AGENT-135 double tap coalesces and a successful connect never adds again`() = runTest {
        val controller = backgroundScope.make()
        gateway.addGate = CompletableDeferred()
        val a = async { controller.connect("design") }
        val b = async { controller.connect("design") }
        runCurrent()
        assertEquals(1, gateway.adds)
        gateway.addGate!!.complete(Unit)
        a.await()
        b.await()
        controller.connect("design")
        assertEquals(1, gateway.adds)
    }

    @Test
    fun `B-AGENT-135 an unrelated existing same name is never reused or overwritten`() = runTest {
        gateway.inventory = listOf(McpServerInfo("design", "connected", null))
        val controller = backgroundScope.make()
        controller.connect("design")
        assertEquals(McpChatFailure.NameConflict, controller.snapshot.value.failure)
        assertEquals(0, gateway.adds)
        controller.refresh()
        assertNotEquals(McpChatPhase.ToolsReady, controller.snapshot.value.phase)
    }

    @Test
    fun `B-AGENT-135 an unsupported runtime makes no calls`() = runTest {
        val controller = backgroundScope.make(chatCaps(enabled = false))
        controller.connect("design")
        assertEquals(McpChatFailure.Unavailable, controller.snapshot.value.failure)
        assertEquals(McpChatPhase.Unavailable, controller.snapshot.value.phase)
        assertEquals(0, gateway.adds + gateway.lists)
    }

    @Test
    fun `B-AGENT-135 a wrong requested server cannot mutate`() = runTest {
        val controller = backgroundScope.make()
        controller.connect("other")
        assertEquals(McpChatFailure.InvalidSuggestion, controller.snapshot.value.failure)
        assertEquals(0, gateway.adds)
    }

    @Test
    fun `B-AGENT-135 required configuration stays in Tools`() = runTest {
        val controller = backgroundScope.make(item = hostedItem(key = true))
        controller.connect("design")
        assertEquals(McpChatFailure.SetupRequired, controller.snapshot.value.failure)
        assertEquals(0, gateway.adds)
    }

    @Test
    fun `B-AGENT-135 no refresh contract never claims ready`() = runTest {
        val controller = backgroundScope.make(chatCaps(refresh = false))
        controller.connect("design")
        assertEquals(McpChatPhase.ConnectedReadinessUnknown, controller.snapshot.value.phase)
    }

    @Test
    fun `B-AGENT-135 source changes during add cannot publish success`() = runTest {
        val controller = backgroundScope.make()
        gateway.addGate = CompletableDeferred()
        val work = async { controller.connect("design") }
        runCurrent()
        current = false
        gateway.addGate!!.complete(Unit)
        work.await()
        assertEquals(McpChatFailure.SourceChanged, controller.snapshot.value.failure)
        assertEquals(0, gateway.auths)
    }

    @Test
    fun `B-AGENT-135 gateway errors never escape or expose raw credential text`() = runTest {
        gateway.failure = Failure.Server(500)
        val controller = backgroundScope.make()
        controller.connect("design")
        assertEquals(McpChatFailure.ConnectFailed, controller.snapshot.value.failure)
        assertFalse("secret" in controller.snapshot.value.toString())
    }

    @Test
    fun `B-AGENT-135 a server needing sign-in moves to authorizing with a pasted code, then ready`() = runTest {
        gateway.statusAfterAdd = "needs_auth"
        val controller = backgroundScope.make()
        controller.connect("design")
        assertEquals(McpChatPhase.NeedsAuthentication, controller.snapshot.value.phase)
        controller.startOAuth()
        assertEquals(McpChatPhase.Authorizing, controller.snapshot.value.phase)
        assertEquals(true, controller.snapshot.value.manualCodeRequired)
        controller.completeOAuth("https://cb.example/?state=test-state&code=abc")
        assertEquals(1, gateway.completions)
        assertEquals(McpChatPhase.ToolsReady, controller.snapshot.value.phase)
        assertEquals(ParsedCode.Code("raw"), parseMcpAuthorizationCode("raw", "s"))
        assertIs<ParsedCode.Refused>(parseMcpAuthorizationCode("https://cb/?state=x&code=1", "s"))
        assertEquals(
            8123,
            mcpLoopbackRedirect("https://a.example/auth?redirect_uri=http%3A%2F%2F127.0.0.1%3A8123%2Fcb")?.port,
        )
    }

    @Test
    fun `B-AGENT-135 OpenCode 2 without chat sign-in refuses OAuth without a call`() = runTest {
        gateway.statusAfterAdd = "needs_auth"
        val controller = backgroundScope.make(chatCaps(oauth = false))
        controller.connect("design")
        controller.startOAuth()
        assertEquals(McpChatFailure.OAuthUnavailable, controller.snapshot.value.failure)
        assertEquals(0, gateway.auths)
        assertEquals(persistentListOf<String>(), persistentListOf())
    }
}
