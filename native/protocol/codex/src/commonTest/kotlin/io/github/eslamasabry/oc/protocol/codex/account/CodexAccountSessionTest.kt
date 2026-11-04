package io.github.eslamasabry.oc.protocol.codex.account

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failureOrNull
import io.github.eslamasabry.oc.protocol.codex.FakeAppServer
import io.github.eslamasabry.oc.protocol.codex.FakeSockets
import io.github.eslamasabry.oc.protocol.codex.jsonOf
import io.github.eslamasabry.oc.protocol.codex.params
import io.github.eslamasabry.oc.protocol.codex.transport
import io.github.eslamasabry.oc.protocol.codex.transport.CodexTransport
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Port of test/codex_account_session_test.dart. */
class CodexAccountSessionTest {
    private val login = jsonOf(
        "type" to "chatgptDeviceCode",
        "loginId" to "owned-fixture-login",
        "verificationUrl" to "https" + "://auth.openai.com/codex/device",
        "userCode" to "TEST-1234",
    )

    private inner class Fixture(val scope: TestScope) {
        var scopeValid = true
        val server = FakeAppServer(userAgent = "codex_cli_rs/0.153.4 (fixture)").apply {
            answers["account/read"] = { jsonOf("account" to null, "requiresOpenaiAuth" to true) }
            answers["account/login/start"] = { login }
            answers["account/login/cancel"] = { jsonOf("status" to "canceled") }
        }
        val transport: CodexTransport = scope.transport(FakeSockets(server), requestTimeoutMs = 150)
        lateinit var session: CodexAccountSession
        val events = mutableListOf<AccountEvent>()

        suspend fun start(): Fixture {
            transport.connect()
            session = CodexAccountSession(transport) { scopeValid }
            session.addListener { events += it }
            return this
        }
    }

    private suspend fun TestScope.fixture() = Fixture(this).start()

    private fun <T> Outcome<T>.kind() = failureOrNull()?.accountKind()

    @Test
    fun `B-AGENT-051 account reads never refresh tokens and a login owns only the returned handle`() = runTest {
        val f = fixture()
        assertTrue(f.session.supported)
        assertFalse(assertIs<Outcome.Ok<AgentAccount>>(f.session.read()).value.signedIn)
        assertEquals(jsonOf("refreshToken" to false), f.server.calls("account/read").last().params())
        val code = assertIs<Outcome.Ok<AccountDeviceCode>>(f.session.startDeviceLogin()).value
        assertEquals("TEST-1234", code.userCode)
        assertFalse(code.userCode in code.toString())
        assertEquals(jsonOf("type" to "chatgptDeviceCode"), f.server.calls("account/login/start").single().params())
        assertTrue(f.session.cancelLogin())
        assertEquals(jsonOf("loginId" to "owned-fixture-login"), f.server.calls("account/login/cancel").single().params())
        f.session.close()
        f.transport.close()
    }

    @Test
    fun `B-AGENT-051 an unsupported runtime dispatches no account operation`() = runTest {
        val f = fixture()
        f.transport.accountApiSupported = false
        assertEquals(AccountFailureKind.Unavailable, f.session.startDeviceLogin().kind())
        assertTrue(f.server.calls("account/read").isEmpty())
        assertTrue(f.server.calls("account/login/start").isEmpty())
        f.transport.close()
    }

    @Test
    fun `B-AGENT-051 a missing-method error stays fixed and never exposes remote detail`() = runTest {
        val f = fixture()
        f.server.errors["account/usage/read"] = -32601
        val failure = f.session.usage().failureOrNull()!!
        assertEquals(AccountFailureKind.Unavailable, failure.accountKind())
        assertNull(failure.details)
        assertFalse("Synthetic" in failure.toString())
        f.transport.close()
    }

    @Test
    fun `B-AGENT-051 a wrong-version handshake disables account actions without breaking the connection`() = runTest {
        val other = FakeAppServer(userAgent = "codex_cli_rs/0.154.0 (fixture)")
        val t = transport(FakeSockets(other))
        t.connect()
        val account = CodexAccountSession(t) { true }
        assertTrue(t.connected)
        assertFalse(account.supported)
        assertTrue(account.read() is Outcome.Failed)
        assertTrue(other.calls("account/read").isEmpty())
        account.close()
        t.close()
    }

    @Test
    fun `B-AGENT-052 a second start cannot replace the outstanding login`() = runTest {
        val f = fixture()
        assertIs<Outcome.Ok<*>>(f.session.startDeviceLogin())
        assertTrue(f.session.startDeviceLogin() is Outcome.Failed)
        assertEquals(1, f.server.calls("account/login/start").size)
        f.transport.close()
    }

    @Test
    fun `B-AGENT-052 a foreign completion is ignored and the matching one accepted once`() = runTest {
        val f = fixture()
        f.session.startDeviceLogin()
        f.server.event("account/login/completed", jsonOf("loginId" to "foreign", "success" to true))
        runCurrent()
        assertTrue(f.events.isEmpty())
        f.server.event("account/login/completed", jsonOf("loginId" to "owned-fixture-login", "success" to true))
        f.server.event("account/login/completed", jsonOf("loginId" to "owned-fixture-login", "success" to true))
        runCurrent()
        assertEquals(1, f.events.count { it.kind == AccountEventKind.LoginCompleted })
        f.transport.close()
    }

    @Test
    fun `B-AGENT-052 an early owned completion settles the flow before a late start response can show a code`() = runTest {
        val f = fixture()
        f.server.answers["account/login/start"] = {
            f.server.event("account/login/completed", jsonOf("loginId" to "foreign", "success" to false))
            f.server.event("account/login/completed", jsonOf("loginId" to "owned-fixture-login", "success" to true))
            login
        }
        assertTrue(f.session.startDeviceLogin() is Outcome.Failed)
        assertEquals(AccountEvent(AccountEventKind.LoginCompleted, success = true), f.events.single())
        assertTrue(f.server.calls("account/login/cancel").isEmpty())
        f.transport.close()
    }

    @Test
    fun `B-AGENT-053 an old epoch cancel cannot reopen the transport or mutate a replacement`() = runTest {
        val f = fixture()
        f.session.startDeviceLogin()
        f.transport.close()
        assertFalse(f.session.cancelLogin())
        assertTrue(f.server.calls("account/login/cancel").isEmpty())
        assertEquals(1, f.server.calls("initialize").size)
    }

    @Test
    fun `B-AGENT-053 a late start after cancel or close cancels only on the original scope`() = runTest {
        for (close in listOf(false, true)) {
            val f = fixture()
            f.server.gated += "account/login/start"
            val start = async { f.session.startDeviceLogin() }
            runCurrent()
            assertTrue(f.server.isHeld("account/login/start"))
            if (close) f.session.close() else assertFalse(f.session.cancelLogin())
            f.server.release("account/login/start")
            runCurrent()
            assertTrue(start.await() is Outcome.Failed)
            assertEquals(1, f.server.calls("account/login/cancel").size)
            if (!close) assertEquals(AccountEvent(AccountEventKind.LoginCancelled, success = true), f.events.last())
            f.transport.close()
        }
    }

    @Test
    fun `B-AGENT-053 scope loss during a held start cannot cancel a different scope`() = runTest {
        val f = fixture()
        f.server.gated += "account/login/start"
        val start = async { f.session.startDeviceLogin() }
        runCurrent()
        f.scopeValid = false
        f.server.release("account/login/start")
        runCurrent()
        assertTrue(start.await() is Outcome.Failed)
        assertTrue(f.server.calls("account/login/cancel").isEmpty())
        f.transport.close()
    }

    @Test
    fun `B-AGENT-053 an account update during preflight stops the login dispatch`() = runTest {
        val f = fixture()
        f.server.beforeAnswer = { frame ->
            if (frame["method"].toString() == "\"account/read\"") f.server.event("account/updated", jsonOf("authMode" to "chatgpt"))
        }
        assertTrue(f.session.startDeviceLogin() is Outcome.Failed)
        assertTrue(f.server.calls("account/login/start").isEmpty())
        f.transport.close()
    }

    @Test
    fun `B-AGENT-053 an unconfirmed cancellation keeps ownership for an explicit retry`() = runTest {
        val f = fixture()
        f.session.startDeviceLogin()
        f.server.answers["account/login/cancel"] = { jsonOf("status" to "notFound") }
        assertFalse(f.session.cancelLogin())
        assertTrue(f.session.startDeviceLogin() is Outcome.Failed)
        f.server.answers["account/login/cancel"] = { jsonOf("status" to "canceled") }
        assertTrue(f.session.cancelLogin())
        assertEquals(2, f.server.calls("account/login/cancel").size)
        f.transport.close()
    }

    @Test
    fun `B-AGENT-053 a lost start receipt cannot become a confirmed cancellation or a replay`() = runTest {
        val f = fixture()
        f.server.answers["account/login/start"] = { null }
        val first = async { f.session.startDeviceLogin() }
        advanceTimeBy(200)
        runCurrent()
        assertEquals(AccountFailureKind.Uncertain, first.await().kind())
        assertFalse(f.session.cancelLogin())
        assertTrue(f.session.startDeviceLogin() is Outcome.Failed)
        assertEquals(1, f.server.calls("account/login/start").size)
        f.transport.close()
    }

    @Test
    fun `B-AGENT-054 a malformed account is never treated as a signed-out preflight`() = runTest {
        val f = fixture()
        f.server.answers["account/read"] = { jsonOf("requiresOpenaiAuth" to true) }
        assertEquals(AccountFailureKind.InvalidResponse, f.session.startDeviceLogin().kind())
        assertTrue(f.server.calls("account/login/start").isEmpty())
        f.transport.close()
    }

    @Test
    fun `B-AGENT-054 a lookalike login URL is never exposed and the owned login is canceled`() = runTest {
        val f = fixture()
        f.server.answers["account/login/start"] = {
            JsonObject(login + ("verificationUrl" to kotlinx.serialization.json.JsonPrimitive("https" + "://auth.openai.com.evil.example/codex/device")))
        }
        assertTrue(f.session.startDeviceLogin() is Outcome.Failed)
        assertEquals(1, f.server.calls("account/login/cancel").size)
        f.transport.close()
    }

    @Test
    fun `B-AGENT-054 reported null usage stays unavailable and reported zero stays zero`() = runTest {
        val f = fixture()
        f.server.answers["account/usage/read"] = { jsonOf("summary" to mapOf("lifetimeTokens" to 0, "peakDailyTokens" to null)) }
        f.server.answers["account/rateLimits/read"] = {
            jsonOf(
                "rateLimits" to mapOf(
                    "limitId" to "codex",
                    "primary" to mapOf("usedPercent" to 0, "windowDurationMins" to null, "resetsAt" to null),
                    "secondary" to null,
                ),
            )
        }
        val usage = assertIs<Outcome.Ok<AccountTokenUsage>>(f.session.usage()).value
        assertEquals(0L, usage.lifetimeTokens)
        assertNull(usage.peakDailyTokens)
        val bucket = assertIs<Outcome.Ok<List<AccountRateBucket>>>(f.session.rateLimits()).value.single()
        assertEquals(0L, bucket.primary!!.usedPercent)
        assertNull(bucket.primary!!.resetsAtMs)
        assertNull(bucket.secondary)
        f.transport.close()
    }
}
