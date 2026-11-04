package io.github.eslamasabry.oc.engine.cards

import kotlin.test.Test
import kotlin.test.assertEquals

/** Flutter `gen_ui_show_permission_test.dart` 268-324 (B-SEC-080): the cases that must never auto-allow. */
class ShowCardNeverAutoAllowTest {
    private fun request(
        id: String = "request-1",
        name: String? = ShowCardPermission.CLAUDE_SHOW,
        kind: String = "tool",
        provider: String? = "claude",
        session: String? = "claude",
        inLocation: Boolean = true,
        daemonTested: Boolean = true,
    ) = ShowCardPermission.Request(id, "agent-1", name, kind, provider, session, inLocation, daemonTested)

    @Test
    fun `B-SEC-080 an unqualified gateway never auto-allows`() {
        val rule = ShowCardPermission(qualified = null)
        assertEquals(ShowCardPermission.Decision.Ask, rule.decide(request()))
    }

    @Test
    fun `B-SEC-080 an unqualified daemon version never auto-allows`() {
        val rule = ShowCardPermission { true }
        assertEquals(ShowCardPermission.Decision.Ask, rule.decide(request(daemonTested = false)))
        assertEquals(ShowCardPermission.Decision.AllowOnce, rule.decide(request(id = "tested")))
    }

    @Test
    fun `B-SEC-080 the exact raw name and tool kind are required, never normalized names`() {
        val rule = ShowCardPermission { true }
        val requests =
            listOf(
                request(name = "MCP__OC-UI__SHOW"),
                request(name = "mcp__oc-ui__show "),
                request(name = "mcp__oc-ui__show_more"),
                request(name = "mcp__other__show"),
                request(name = "Bash"),
                request(kind = "question"),
                request(kind = "plan"),
                request(provider = "codex"),
            )
        requests.forEachIndexed { i, r ->
            assertEquals(ShowCardPermission.Decision.Ask, rule.decide(r.copy(id = "request-$i")), r.toString())
        }
    }

    @Test
    fun `B-SEC-080 foreign, unknown and non-Claude sessions never auto-allow`() {
        val rule = ShowCardPermission { true }
        assertEquals(ShowCardPermission.Decision.Ask, rule.decide(request(id = "foreign", inLocation = false)))
        assertEquals(ShowCardPermission.Decision.Ask, rule.decide(request(id = "unknown", session = null)))
        assertEquals(ShowCardPermission.Decision.Ask, rule.decide(request(id = "other-provider", session = "pi")))
    }
}
