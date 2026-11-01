package io.github.eslamasabry.oc.engine.agents.approval

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.engine.agents.agentsBackend
import io.github.eslamasabry.oc.engine.agents.testing.FakeAgentPrefs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Port of test/session_auto_approval_store_test.dart. */
class AutoApprovalStoreTest {
    private val a = ProfileId("server-a")
    private val auto = SessionAutoApproval(AutoApprovalMode.AutoOnce)
    private val autoInherit = SessionAutoApproval.AutoInherit
    private fun s(id: String) = SessionId(id)

    private val parents = mapOf("child" to "parent", "grandchild" to "child", "loop-a" to "loop-b", "loop-b" to "loop-a")
    private val parentOf: (SessionId) -> SessionId? = { id -> parents[id.raw]?.let(::SessionId) }

    @Test
    fun `B-STATE-099 a setting round-trips under the profile-scoped key`() = runTest {
        val prefs = FakeAgentPrefs()
        val store = AutoApprovalStore(prefs)
        assertNull(store.explicitFor(a, s("ses_1")))
        store.set(a, s("ses_1"), autoInherit)
        assertEquals(autoInherit, store.explicitFor(a, s("ses_1")))
        assertEquals("""{"ses_1":{"mode":"autoOnce","inheritToChildren":true}}""", prefs.string("oc.autoApprove.server-a"))
        val reopened = AutoApprovalStore(prefs)
        assertEquals(autoInherit, reopened.explicitFor(a, s("ses_1")))
        assertNull(reopened.explicitFor(ProfileId("server-b"), s("ses_1")))
    }

    @Test
    fun `B-STATE-099 the agents backend keeps its own key`() = runTest {
        val prefs = FakeAgentPrefs()
        AutoApprovalStore(prefs).set(ProfileId("phone").agentsBackend(), s("ses_1"), auto)
        assertTrue(prefs.string("oc.autoApprove.phone.agents") != null)
        assertNull(prefs.string("oc.autoApprove.phone"))
    }

    @Test
    fun `B-STATE-099 clearing removes the session entry so it follows its parent`() = runTest {
        val prefs = FakeAgentPrefs()
        val store = AutoApprovalStore(prefs)
        store.set(a, s("ses_1"), auto)
        store.set(a, s("ses_2"), SessionAutoApproval.Ask)
        store.set(a, s("ses_1"), null)
        assertNull(store.explicitFor(a, s("ses_1")))
        assertEquals(SessionAutoApproval.Ask, store.explicitFor(a, s("ses_2")))
        assertEquals("""{"ses_2":{"mode":"ask","inheritToChildren":false}}""", prefs.string("oc.autoApprove.server-a"))
    }

    @Test
    fun `B-STATE-099 a refused write fails and leaves the last saved choice`() = runTest {
        val prefs = FakeAgentPrefs()
        val store = AutoApprovalStore(prefs)
        store.set(a, s("ses_1"), auto)
        prefs.refuseWrites = true
        assertIs<Outcome.Failed>(store.set(a, s("ses_1"), SessionAutoApproval.Ask))
        assertEquals(auto, store.explicitFor(a, s("ses_1")))
        assertEquals(auto, AutoApprovalStore(prefs).explicitFor(a, s("ses_1")))
    }

    @Test
    fun `B-STATE-099 malformed storage reads as no settings`() {
        val store = AutoApprovalStore(
            FakeAgentPrefs(mapOf("oc.autoApprove.server-a" to """{"ses_1": {"mode": "always"}, "": {"mode": "ask"}, "ses_2": 4}""")),
        )
        assertTrue(store.settingsFor(a).isEmpty())
        val broken = AutoApprovalStore(FakeAgentPrefs(mapOf("oc.autoApprove.server-a" to "nope")))
        assertTrue(broken.settingsFor(a).isEmpty())
        assertFalse(broken.effectiveFor(a, s("ses_1")) { null }.automatic)
    }

    @Test
    fun `B-STATE-099 an empty profile never reads or writes`() = runTest {
        val prefs = FakeAgentPrefs()
        val store = AutoApprovalStore(prefs)
        val empty = ProfileId("")
        assertNull(store.explicitFor(empty, s("ses_1")))
        assertEquals(EffectiveAutoApproval.AskByDefault, store.effectiveFor(empty, s("ses_1")) { null })
        assertEquals(Outcome.Failed(Failure.Refused(RefusalReason.StaleScope)), store.set(empty, s("ses_1"), auto))
        assertEquals(0, prefs.commits)
    }

    @Test
    fun `B-STATE-099 deletion drains accepted writes before the sweep and forget drops the cache`() = runTest {
        val prefs = FakeAgentPrefs()
        val store = AutoApprovalStore(prefs)
        store.set(a, s("ses_1"), autoInherit)
        store.drain(a)
        prefs.values.remove("oc.autoApprove.server-a") // the profile sweep
        store.forget(a)
        assertNull(store.explicitFor(a, s("ses_1")))
    }

    @Test
    fun `B-AGENT-221 new sessions ask`() {
        val effective = AutoApprovalStore(FakeAgentPrefs()).effectiveFor(a, s("parent"), parentOf)
        assertFalse(effective.automatic)
        assertFalse(effective.explicit)
        assertFalse(effective.inherited)
    }

    @Test
    fun `B-AGENT-223 inheritance reaches child and grandchild`() = runTest {
        val store = AutoApprovalStore(FakeAgentPrefs())
        store.set(a, s("parent"), autoInherit)
        for (id in listOf("child", "grandchild")) {
            val effective = store.effectiveFor(a, s(id), parentOf)
            assertTrue(effective.automatic, id)
            assertFalse(effective.explicit, id)
            assertEquals(s("parent"), effective.inheritedFrom, id)
        }
        val own = store.effectiveFor(a, s("parent"), parentOf)
        assertTrue(own.explicit)
        assertFalse(own.inherited)
    }

    @Test
    fun `B-AGENT-223 without inherit the choice stays on the parent`() = runTest {
        val store = AutoApprovalStore(FakeAgentPrefs())
        store.set(a, s("parent"), auto)
        assertTrue(store.effectiveFor(a, s("parent"), parentOf).automatic)
        assertFalse(store.effectiveFor(a, s("child"), parentOf).automatic)
        assertFalse(store.effectiveFor(a, s("grandchild"), parentOf).automatic)
    }

    @Test
    fun `B-AGENT-223 an explicit child override wins and shields its subtree`() = runTest {
        val store = AutoApprovalStore(FakeAgentPrefs())
        store.set(a, s("parent"), autoInherit)
        store.set(a, s("child"), SessionAutoApproval.Ask)
        val child = store.effectiveFor(a, s("child"), parentOf)
        assertFalse(child.automatic)
        assertTrue(child.explicit)
        val grandchild = store.effectiveFor(a, s("grandchild"), parentOf)
        assertFalse(grandchild.automatic)
        assertFalse(grandchild.inherited)
        store.set(a, s("child"), null)
        assertEquals(s("parent"), store.effectiveFor(a, s("child"), parentOf).inheritedFrom)
        assertEquals(s("parent"), store.effectiveFor(a, s("grandchild"), parentOf).inheritedFrom)
    }

    @Test
    fun `B-AGENT-223 a parent cycle ends the walk`() {
        assertFalse(AutoApprovalStore(FakeAgentPrefs()).effectiveFor(a, s("loop-a"), parentOf).automatic)
    }

    @Test
    fun `B-AGENT-225 approve everything reaches every conversation, including ones never seen`() = runTest {
        val prefs = FakeAgentPrefs()
        val store = AutoApprovalStore(prefs)
        val p = ProfileId("p")
        assertFalse(store.effectiveFor(p, s("new")) { null }.automatic)
        store.setApprovesEverything(p, true)
        val effective = store.effectiveFor(p, s("new")) { null }
        assertTrue(effective.automatic)
        assertTrue(effective.serverWide)
        assertFalse(effective.explicit)
        assertFalse(store.effectiveFor(ProfileId("other"), s("new")) { null }.automatic)
        val reopened = AutoApprovalStore(prefs)
        assertTrue(reopened.approvesEverything(p))
        reopened.setApprovesEverything(p, false)
        assertFalse(reopened.effectiveFor(p, s("new")) { null }.automatic)
    }

    @Test
    fun `B-AGENT-225 a conversation set to ask keeps asking and approve everything is not listed`() = runTest {
        val store = AutoApprovalStore(FakeAgentPrefs())
        val p = ProfileId("p")
        store.setApprovesEverything(p, true)
        store.set(p, s("careful"), SessionAutoApproval.Ask)
        store.set(p, s("s1"), auto)
        val careful = store.effectiveFor(p, s("careful")) { null }
        assertFalse(careful.automatic)
        assertTrue(careful.explicit)
        assertEquals(listOf(s("careful"), s("s1")), store.settingsFor(p).keys.toList())
    }
}
