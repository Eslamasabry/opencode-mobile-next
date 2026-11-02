@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.eslamasabry.oc.protocol.paseo

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.protocol.paseo.transport.PaseoPush
import io.github.eslamasabry.oc.protocol.paseo.transport.PaseoTransport
import io.github.eslamasabry.oc.protocol.paseo.transport.PaseoTransportListener
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ported from test/paseo_hello_rejection_test.dart and the handshake tests of test/paseo_gateway_test.dart. */
class PaseoHandshakeTest {
    private suspend fun kotlinx.coroutines.test.TestScope.refusedWith(reason: String, password: String = ""): PaseoException {
        val daemon = FakeDaemon().apply { rejectReason = reason }
        val t = transport(daemon, password)
        return assertFailsWith<PaseoException> { t.connect() }
    }

    @Test
    fun `B-PROTO-071 a missing or wrong password reads as an authentication failure, the reason only for Details`() = runTest {
        for (reason in listOf("password_required", "incorrect_password")) {
            val failure = refusedWith(reason, password = "x1y2z3")
            assertEquals(PaseoFailureKind.Authentication, failure.kind)
            assertEquals(reason, failure.reason)
            assertFalse("x1y2z3" in failure.toString())
            assertFalse(reason in failure.message.orEmpty())
            val neutral = assertIs<Failure.Net.SignInNeeded>(failure.toFailure())
            assertEquals(
                if (reason == "password_required") Failure.Net.SignInNeeded.Why.PasswordMissing else Failure.Net.SignInNeeded.Why.PasswordRejected,
                neutral.why,
            )
            assertEquals(reason, neutral.details?.text)
        }
    }

    @Test
    fun `B-PROTO-071 an incompatible protocol is named plainly`() = runTest {
        val failure = refusedWith("incompatible_protocol")
        assertEquals(PaseoFailureKind.IncompatibleProtocol, failure.kind)
        assertEquals("incompatible_protocol", failure.reason)
        assertEquals(Failure.Net.Incompatible.Kind.ProtocolMismatch, assertIs<Failure.Net.Incompatible>(failure.toFailure()).kind)
    }

    @Test
    fun `B-PROTO-071 an unknown reason is a plain disconnect`() = runTest {
        assertEquals(PaseoFailureKind.Disconnected, refusedWith("something_new").kind)
    }

    @Test
    fun `B-PROTO-071 the close code alone still maps when no frame arrived`() = runTest {
        val incompatible = transport(FakeDaemon().apply { rejectWith = PaseoTransport.CLOSE_INCOMPATIBLE_PROTOCOL })
        assertEquals(PaseoFailureKind.IncompatibleProtocol, assertFailsWith<PaseoException> { incompatible.connect() }.kind)
        // The daemon accepts the upgrade and only then closes with 4401 (a rejected password).
        val wrong = transport(FakeDaemon().apply { rejectWith = PaseoTransport.CLOSE_AUTH_FAILED }, password = "wrong")
        assertEquals(PaseoFailureKind.Authentication, assertFailsWith<PaseoException> { wrong.connect() }.kind)
    }

    @Test
    fun `B-PROTO-071 the handshake advertises hello_rejection and the in-protocol password only when set`() = runTest {
        val daemon = FakeDaemon()
        val sockets = FakeSockets(daemon)
        transport(sockets, password = "x1y2z3").connect()
        val hello = daemon.hello!!
        assertEquals(
            jsonOf(
                "all_providers" to true,
                "hello_rejection" to true,
                "provider_subagents" to true,
                "projected_subagent_timeline" to true,
            ),
            hello.obj("capabilities"),
        )
        assertEquals(jsonOf("kind" to "password", "password" to "x1y2z3"), hello.obj("auth"))
        assertEquals("1", hello["protocolVersion"].toString())
        assertEquals("mobile", hello.str("clientType"))
        // Below 0.1.45 the daemon hides Pi and every other newer runtime.
        assertEquals(PaseoTransport.CLIENT_APP_VERSION, hello.str("appVersion"))
        assertTrue(hello.str("clientId")!!.matches(Regex("cid_[0-9a-f]{32}")))
        assertEquals("x1y2z3", sockets.opened.single().second)
        assertEquals("ws://127.0.0.1:6767/ws", sockets.opened.single().first.url)

        val bare = FakeDaemon()
        transport(bare).connect()
        assertFalse("auth" in bare.hello!!)
    }

    @Test
    fun `B-PROTO-071 frames this client does not know are ignored, not errors`() = runTest {
        val daemon = FakeDaemon()
        val t = transport(daemon)
        t.connect()
        val pushes = mutableListOf<PaseoPush>()
        t.addListener(object : PaseoTransportListener {
            override fun onPush(push: PaseoPush) {
                pushes += push
            }
        })
        daemon.pushRaw(jsonOf("type" to "brand.new.frame", "data" to 1))
        daemon.push("usage.list_reports.response", jsonOf("requestId" to "nobody", "reports" to emptyList<Any>()))
        daemon.push("usage.something_pushed", jsonOf("n" to 1))
        runCurrent()
        assertTrue(t.connected)
        assertEquals(listOf("usage.something_pushed"), pushes.map { it.type })
    }

    @Test
    fun `B-PROTO-071 the old and new daemons and the previous and current Claude Code pins are accepted`() {
        for (v in listOf("0.9.2", "0.11.2", "0.12.0", "0.13.2", "0.14.0")) assertTrue(PaseoTestedVersions.paseoSupports(v), v)
        for (v in listOf("0.13.0", "0.13.1", "0.14.1", "0.11.3", "0.9.3", null)) assertFalse(PaseoTestedVersions.paseoSupports(v), "$v")
        for (v in listOf("2.1.283", "2.1.294", "2.1.316")) assertTrue(PaseoTestedVersions.claudeCodeSupports(v), v)
        for (v in listOf("2.1.317", "2.1.282", null)) assertFalse(PaseoTestedVersions.claudeCodeSupports(v), "$v")
        assertTrue(PaseoTestedVersions.paseoSupports(PaseoTestedVersions.PHONE_PIN))
    }

    @Test
    fun `B-PROTO-071 server_info records the daemon version and writer support`() = runTest {
        val daemon = FakeDaemon().apply {
            version = "0.11.2"
            features = jsonOf("workspaceFileEditing" to true)
        }
        val t = transport(daemon)
        t.connect()
        assertEquals("0.11.2", t.serverVersion)
        assertTrue(t.workspaceFileEditing)
        val odd = FakeDaemon().apply { version = "v0.11.2 (internal)" }
        val t2 = transport(odd)
        t2.connect()
        assertNull(t2.serverVersion)
    }

    @Test
    fun `B-PROTO-071 a daemon that never answers hello fails the connect after 8 s`() = runTest {
        val t = transport(FakeDaemon().apply { silent = true })
        val failure = assertFailsWith<PaseoException> { t.connect() }
        assertEquals(PaseoFailureKind.Disconnected, failure.kind)
        assertEquals(PaseoTransport.HELLO_TIMEOUT_MS, testScheduler.currentTime)
        assertFalse(t.connected)
    }

    @Test
    fun `B-PROTO-071 a refused upgrade keeps the factory's sign-in kind`() = runTest {
        val sockets = FakeSockets(FakeDaemon()).apply { refuse = PaseoException(PaseoFailureKind.HostRefused) }
        val failure = assertFailsWith<PaseoException> { transport(sockets).connect() }
        assertEquals(PaseoFailureKind.HostRefused, failure.kind)
        assertEquals(Failure.Net.SignInNeeded.Why.HostNotAllowed, assertIs<Failure.Net.SignInNeeded>(failure.toFailure()).why)
    }
}
