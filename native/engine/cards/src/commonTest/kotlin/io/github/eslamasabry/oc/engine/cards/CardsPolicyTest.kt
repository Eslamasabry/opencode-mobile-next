package io.github.eslamasabry.oc.engine.cards

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.engine.cards.CardFixtures.assistant
import io.github.eslamasabry.oc.engine.cards.CardFixtures.scope
import io.github.eslamasabry.oc.engine.cards.CardFixtures.user
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CardsPolicyTest {
    // ------------------------------------------------------------------ availability

    private class Prefs(var fail: Boolean = false) : CardsPrefs {
        val values = HashMap<String, Boolean>()

        override fun enabled(profileId: String): Boolean? = values[profileId]

        override suspend fun setEnabled(profileId: String, on: Boolean): Outcome<Unit> {
            if (fail) return Outcome.Failed(Failure.StorageRefused())
            values[profileId] = on
            return Outcome.Ok(Unit)
        }
    }

    private class Qualifier(var managed: Boolean = true, var here: List<CardAgent> = listOf(CardAgent.Claude, CardAgent.OpenCode2)) : CardsQualifier {
        var verdict: Outcome<CardsVerdict> = Outcome.Ok(CardsVerdict(listOf(CardAgent.Claude)))
        var installs = 0
        var verifies = 0
        var gate: CompletableDeferred<Unit>? = null

        override fun managed(profileId: String): Boolean = managed

        override fun agentsHere(profileId: String): List<CardAgent> = here

        override suspend fun verify(profileId: String): Outcome<CardsVerdict> {
            verifies++
            gate?.await()
            return verdict
        }

        override suspend fun install(profileId: String): Outcome<CardsVerdict> {
            installs++
            gate?.await()
            return verdict
        }
    }

    @Test
    fun `B-CHAT-034 cards default to off even when the server could, and desired stays apart from effective`() = runTest {
        val q = Qualifier()
        val setup = CardsSetup(Prefs(), q)
        assertEquals(CardsStatus.Off, setup.status("p"))
        assertFalse(setup.effective("p"))
        q.verdict = Outcome.Ok(CardsVerdict(emptyList(), CardsProblem.InstallationFailed))
        setup.setEnabled("p", true)
        assertTrue(setup.desired("p"))
        assertFalse(setup.effective("p"))
        assertEquals(CardsStatus.Unavailable(CardsProblem.InstallationFailed), setup.status("p"))
    }

    @Test
    fun `B-CHAT-034 a recheck after a runtime switch qualifies and ignores retired runtimes`() = runTest {
        val q = Qualifier(here = listOf(CardAgent.OpenCode2))
        val setup = CardsSetup(Prefs(), q)
        setup.setEnabled("p", true)
        // Claude alone was proven; this connection has only OpenCode 2 → not qualified here.
        assertEquals(CardsStatus.Unavailable(CardsProblem.NotQualified, listOf(CardAgent.OpenCode2)), setup.status("p"))
        q.verdict = Outcome.Ok(CardsVerdict(listOf(CardAgent.OpenCode2)))
        setup.recheck("p")
        assertEquals(CardsStatus.On(listOf(CardAgent.OpenCode2)), setup.status("p"))
        assertTrue(setup.effective("p", CardAgent.OpenCode2))
    }

    @Test
    fun `B-CHAT-035 setup words name only this connection's agents and partial keeps OpenCode 2 unavailable beside Claude`() = runTest {
        val q = Qualifier(here = listOf(CardAgent.Claude, CardAgent.OpenCode2))
        q.verdict = Outcome.Ok(CardsVerdict(listOf(CardAgent.Claude), CardsProblem.RegistrationFailed, listOf(CardAgent.OpenCode2)))
        val setup = CardsSetup(Prefs(), q)
        setup.setEnabled("p", true)
        assertEquals(CardsStatus.Partial(listOf(CardAgent.Claude), CardsProblem.RegistrationFailed, listOf(CardAgent.OpenCode2)), setup.status("p"))
        assertTrue(setup.effective("p", CardAgent.Claude))
        assertFalse(setup.effective("p", CardAgent.OpenCode2))
        q.here = listOf(CardAgent.Claude) // the problem is about an agent not on this connection
        assertEquals(CardsStatus.On(listOf(CardAgent.Claude)), setup.status("p"))
    }

    @Test
    fun `B-CHAT-036 explicit setup runs once, unmanaged profiles never call the installer`() = runTest {
        val q = Qualifier(managed = true)
        val setup = CardsSetup(Prefs(), q)
        setup.setEnabled("p", true)
        assertEquals(1, q.installs)
        val remote = Qualifier(managed = false)
        remote.verdict = Outcome.Ok(CardsVerdict(emptyList()))
        val other = CardsSetup(Prefs(), remote)
        other.setEnabled("p", true)
        assertEquals(0, remote.installs)
        assertEquals(CardsStatus.Unavailable(CardsProblem.UnsupportedHost), other.status("p"))
    }

    @Test
    fun `B-CHAT-036 a pending enable cannot override a newer disable, and automatic checks coalesce`() = runTest {
        val q = Qualifier()
        val gate = CompletableDeferred<Unit>()
        q.gate = gate
        val setup = CardsSetup(Prefs(), q)
        val enable = launch { setup.setEnabled("p", true) }
        runCurrent()
        assertEquals(CardsStatus.Checking, setup.status("p"))
        launch { setup.recheck("p") }
        runCurrent()
        assertEquals(0, q.verifies, "a second automatic check joins the running one")
        setup.setEnabled("p", false)
        assertFalse(setup.effective("p"), "disabling blocks actions before removal finishes")
        gate.complete(Unit)
        enable.join()
        assertEquals(CardsStatus.Off, setup.status("p"))
        assertFalse(setup.effective("p"))
    }

    @Test
    fun `B-CHAT-034 a setting that cannot be stored keeps the capability off`() = runTest {
        val setup = CardsSetup(Prefs(fail = true), Qualifier())
        assertIs<Outcome.Failed>(setup.setEnabled("p", true))
        assertFalse(setup.effective("p"))
    }

    // ------------------------------------------------------------------ history

    private class Pages(vararg val pages: CardHistoryPage) : CardHistorySource {
        val calls = ArrayList<Pair<String?, Int>>()

        override suspend fun page(sessionId: String, cursor: String?, limit: Int): Outcome<CardHistoryPage> {
            calls += cursor to limit
            return Outcome.Ok(pages[calls.size - 1])
        }
    }

    private fun msg(id: String, role: Role = Role.Assistant) = MessageWithParts(MessageInfo(id, "session", role), persistentListOf())

    @Test
    fun `B-CHAT-038 history reads are bounded, newest page first, returned oldest first, invalid limits send nothing`() = runTest {
        val src = Pages(CardHistoryPage(listOf(msg("c"), msg("d")), "older", true), CardHistoryPage(listOf(msg("a"), msg("b")), "oldest", true))
        val tail = (CardHistory.readTail(src, "session") as Outcome.Ok).value
        assertEquals(listOf("a", "b", "c", "d"), tail.messages.map { it.info.id })
        assertEquals(listOf(null to 50, "older" to 50), src.calls)
        assertFalse(tail.complete, "two pages and no user boundary is not a whole tail")
        val none = Pages()
        assertIs<Outcome.Failed>(CardHistory.readTail(none, "session", limit = 0))
        assertIs<Outcome.Failed>(CardHistory.readTail(none, "session", limit = 51))
        assertTrue(none.calls.isEmpty())
        val one = Pages(CardHistoryPage(listOf(msg("u", Role.User), msg("a")), "older", true))
        val whole = (CardHistory.readTail(one, "session") as Outcome.Ok).value
        assertTrue(whole.complete)
        assertEquals(1, one.calls.size, "one page with a user boundary is enough")
    }

    @Test
    fun `B-CHAT-039 idle is accepted only with a fresh snapshot in the same directory`() {
        val ok = IdleSnapshot("s", "/p", exists = true, active = false, takenAtMs = 1_000)
        assertTrue(IdleProof.idle(ok, "s", "/p", 2_000))
        assertFalse(IdleProof.idle(ok, "s", "/other", 2_000))
        assertFalse(IdleProof.idle(ok.copy(active = true), "s", "/p", 2_000))
        assertFalse(IdleProof.idle(ok.copy(exists = false), "s", "/p", 2_000))
        assertFalse(IdleProof.idle(ok, "s", "/p", 10_000))
        assertFalse(IdleProof.idle(null, "s", "/p", 2_000))
    }

    // ------------------------------------------------------------------ coverage

    private class Store : CoverageStore {
        val data = HashMap<String, String?>()

        override fun read(profileId: String): String? = data[profileId]

        override suspend fun write(profileId: String, json: String?): Outcome<Unit> {
            data[profileId] = json
            return Outcome.Ok(Unit)
        }
    }

    private fun target(session: String = "s", idle: Boolean? = true, stamp: String? = "p1", mayLoad: Boolean = true, journaled: Boolean = false, endpoint: String = "e") =
        RecoveryTarget(scope, session, endpoint, mayLoad, idle, stamp, journaled)

    @Test
    fun `B-CHAT-043 covered conversations are skipped until the prompt changes, and coverage is bounded to 200`() = runTest {
        val store = Store()
        val coverage = CardCoverage(store)
        assertTrue(CardRecovery.needsRead(target(), coverage))
        coverage.remember("profile", CardCoverage.identity("e", scope, "s"), "p1")
        assertFalse(CardRecovery.needsRead(target(), coverage))
        assertFalse(CardRecovery.needsRead(target(), CardCoverage(store)), "survives a restart")
        assertTrue(CardRecovery.needsRead(target(stamp = "p2"), coverage), "a changed prompt is read")
        assertTrue(CardRecovery.needsRead(target(idle = false), coverage), "a running turn is never covered")
        assertTrue(CardRecovery.needsRead(target(endpoint = "other"), coverage), "an endpoint change resets")
        assertFalse(CardRecovery.needsRead(target(session = "x", mayLoad = false), coverage), "never wakes a sleeping agent")
        assertTrue(CardRecovery.needsRead(target(session = "x", mayLoad = false), coverage, explicit = true))
        for (i in 0 until 250) coverage.remember("profile", CardCoverage.identity("e", scope, "s$i"), "p")
        assertEquals(200, (CardJson.parse(store.data["profile"]!!) as List<*>).size)
        assertFalse(CardRecovery.needsRead(target(session = "s249", stamp = "p"), coverage))
        assertTrue(CardRecovery.needsRead(target(session = "s0", stamp = "p"), coverage))
    }

    @Test
    fun `B-CHAT-044 a journaled card is read again, corrupt coverage falls back to history, a closed profile is fenced`() = runTest {
        val store = Store()
        val coverage = CardCoverage(store)
        coverage.remember("profile", CardCoverage.identity("e", scope, "s"), "p1")
        assertTrue(CardRecovery.needsRead(target(journaled = true), coverage))
        store.data["profile"] = "{not json"
        assertTrue(CardRecovery.needsRead(target(), coverage))
        coverage.closeProfile("profile")
        coverage.remember("profile", CardCoverage.identity("e", scope, "s"), "p1")
        assertEquals("{not json", store.data["profile"])
    }

    @Test
    fun `B-CHAT-052 a pass reads at most 20 conversations and never covers a read that held a card`() {
        val coverage = CardCoverage(Store())
        val (read, more) = CardRecovery.pass((0 until 25).map { target(session = "s$it") }, coverage)
        assertEquals(20, read.size)
        assertTrue(more)
        val withCard = CardTail(listOf(assistant()), complete = true)
        assertFalse(CardRecovery.mayCover(withCard, target(), target()))
        val plain = CardTail(listOf(user("hi")), complete = true)
        assertTrue(CardRecovery.mayCover(plain, target(), target()))
        assertFalse(CardRecovery.mayCover(plain, target(), target(stamp = "p2")))
        assertFalse(CardRecovery.mayCover(plain.copy(complete = false), target(), target()))
    }

    // ------------------------------------------------------------------ show permission

    private fun request(id: String = "r1", name: String? = "mcp__oc-ui__show", kind: String = "tool", provider: String? = "claude", session: String? = "claude", inLocation: Boolean = true) =
        ShowCardPermission.Request(id, "s", name, kind, provider, session, inLocation)

    @Test
    fun `B-CHAT-049 a qualified exact show is allowed once without an approval card`() {
        var allowed = true
        val rule = ShowCardPermission { allowed }
        assertEquals(ShowCardPermission.Decision.AllowOnce, rule.decide(request()))
        rule.replied("r1")
        assertEquals(ShowCardPermission.Decision.AlreadyAllowed, rule.decide(request()))
        assertEquals(ShowCardPermission.Decision.AllowOnce, rule.decide(request(id = "r0", provider = null)), "Claude without provider")
        allowed = false
        assertEquals(ShowCardPermission.Decision.Ask, rule.decide(request(id = "r2")), "disable is rechecked per request")
        for (r in listOf(
            request(id = "a", name = "MCP__OC-UI__SHOW"),
            request(id = "b", name = "mcp__oc-ui__show "),
            request(id = "c", name = "mcp__oc-ui__show_more"),
            request(id = "d", name = "Bash"),
            request(id = "e", kind = "question"),
            request(id = "f", provider = "codex"),
            request(id = "g", session = null),
            request(id = "h", session = "pi"),
            request(id = "i", inLocation = false),
        )) {
            allowed = true
            assertEquals(ShowCardPermission.Decision.Ask, rule.decide(r), r.toString())
        }
    }

    @Test
    fun `B-CHAT-049 qualification sweeps queued requests, a location change retires them, a failure is not retried`() {
        var allowed = false
        val rule = ShowCardPermission { allowed }
        assertEquals(ShowCardPermission.Decision.Ask, rule.decide(request()))
        allowed = true
        assertEquals(listOf("r1"), rule.sweep().map { it.id })
        allowed = false
        rule.decide(request(id = "r2"))
        rule.locationChanged()
        allowed = true
        assertTrue(rule.sweep().isEmpty())
        rule.replyFailed("r3")
        assertEquals(ShowCardPermission.Decision.Ask, rule.decide(request(id = "r3")))
        val throwing = ShowCardPermission { error("qualification failed") }
        assertEquals(ShowCardPermission.Decision.Ask, throwing.decide(request()))
    }
}
