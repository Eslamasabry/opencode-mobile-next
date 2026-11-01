package io.github.eslamasabry.oc.feature.servers

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Failure.Net
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.RedactorApi
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.feature.servers.presentation.ConnectionDiagnosis
import io.github.eslamasabry.oc.feature.servers.presentation.ConnectionFailureUi
import io.github.eslamasabry.oc.feature.servers.presentation.DiagnosisContext
import io.github.eslamasabry.oc.feature.servers.presentation.FailureAction
import io.github.eslamasabry.oc.feature.servers.presentation.FailureCheck
import io.github.eslamasabry.oc.feature.servers.presentation.FailureKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ported from test/connection_failure_test.dart; the input is a typed Failure, not an error string (ADR rule 5). */
class ConnectionDiagnosisTest {
    private val loopback = ServerOrigin("http", "127.0.0.1", 4096)
    private val remote = ServerOrigin("https", "dev.tail.net", 4096)

    private fun d(
        failure: Failure,
        origin: ServerOrigin = loopback,
        termux: Boolean = true,
        codex: Boolean = false,
        attempts: Int = 1,
        managedPhoneServer: Boolean = false,
        inAppServer: Boolean = false,
        inAppStartFailed: Boolean = false,
    ): ConnectionFailureUi =
        ConnectionDiagnosis.diagnose(
            failure,
            origin,
            DiagnosisContext(
                backend = if (codex) BackendKind.Codex else BackendKind.OpenCode2,
                supportsTermux = termux,
                managedPhoneServer = managedPhoneServer,
                inAppServer = inAppServer,
                inAppStartFailed = inAppStartFailed,
                attempts = attempts,
            ),
            RulesAddressPolicy,
        )

    private val refused = Net.Unreachable(Net.Unreachable.Why.Refused)
    private val timedOut = Net.Unreachable(Net.Unreachable.Why.Timeout)

    // ---- B-NAV-007

    @Test
    fun `B-NAV-007 loopback offers Retry and names Termux and adb as checks, not as the action`() {
        val f = d(refused)
        assertEquals(FailureKind.NothingAnsweredOnPhone, f.kind)
        assertEquals(FailureAction.Retry, f.primary)
        assertTrue(FailureCheck.TermuxRunning in f.checks)
        assertTrue(FailureCheck.TunnelConnected in f.checks)
    }

    @Test
    fun `B-NAV-007 loopback without Termux support keeps the generic Retry`() {
        val f = d(refused, termux = false)
        assertEquals(FailureAction.Retry, f.primary)
        assertFalse(FailureCheck.TermuxRunning in f.checks)
    }

    @Test
    fun `B-NAV-007 an unclassified loopback failure still gets the loopback advice`() {
        assertEquals(FailureKind.NothingAnsweredOnPhone, d(Failure.Unknown()).kind)
    }

    @Test
    fun `B-NAV-007 a rejected password asks for the password, not a retry`() {
        val f = d(Net.SignInNeeded(Net.SignInNeeded.Why.PasswordRejected), origin = remote)
        assertEquals(FailureKind.PasswordRejected, f.kind)
        assertEquals(FailureAction.UpdatePassword, f.primary)
    }

    @Test
    fun `B-NAV-007 a missing Codex token asks for the token without a connection guess`() {
        val f = d(Net.SignInNeeded(Net.SignInNeeded.Why.TokenMissing), codex = true)
        assertEquals(FailureKind.TokenRequired, f.kind)
        assertEquals(FailureAction.UpdateToken, f.primary)
        assertEquals(listOf<FailureCheck>(FailureCheck.EnterCodexToken), f.checks)
    }

    @Test
    fun `B-NAV-007 a rejected Codex token has a token recovery action and no password words`() {
        val f = d(Net.SignInNeeded(Net.SignInNeeded.Why.TokenRejected), origin = ServerOrigin("https", "codex.example", 443), codex = true)
        assertEquals(FailureKind.TokenRejected, f.kind)
        assertEquals(FailureAction.UpdateToken, f.primary)
        assertFalse(FailureCheck.PairAgainForPassword in f.checks)
    }

    @Test
    fun `B-NAV-007 a local Codex refusal points at its listener or tunnel`() {
        val f = d(refused, codex = true)
        assertEquals(FailureKind.CodexListenerUnavailable, f.kind)
        assertEquals(FailureAction.Retry, f.primary)
        assertEquals(listOf<FailureCheck>(FailureCheck.StartCodexListener, FailureCheck.CodexTunnel), f.checks)
    }

    @Test
    fun `B-NAV-007 a remote Codex refusal recommends a wss endpoint`() {
        val f = d(refused, origin = ServerOrigin("https", "codex.example", 443), codex = true)
        assertEquals(FailureKind.CodexEndpointUnreachable, f.kind)
        assertTrue(FailureCheck.CodexWss in f.checks)
    }

    @Test
    fun `B-NAV-007 an unknown Codex loopback failure stays an honest generic failure`() {
        val f = d(Failure.Unknown(), codex = true)
        assertEquals(FailureKind.Unknown, f.kind)
        assertEquals(listOf<FailureCheck>(FailureCheck.AgentServerRunning), f.checks)
    }

    // ---- B-NAV-008

    @Test
    fun `B-NAV-008 a changed certificate blocks and asks for explicit review`() {
        val f = d(Net.TrustRefused(Net.TrustRefused.Why.CertificateChanged), origin = remote)
        assertEquals(FailureKind.CertificateChanged, f.kind)
        assertEquals(FailureAction.ReviewCertificate, f.primary)
        assertEquals(listOf<FailureCheck>(FailureCheck.RecheckCertificate), f.checks)
    }

    @Test
    fun `B-NAV-008 certificate problems name the certificate, and expired differs from untrusted`() {
        val untrusted = d(Net.TrustRefused(Net.TrustRefused.Why.CertificateUntrusted), origin = remote)
        val expired = d(Net.TrustRefused(Net.TrustRefused.Why.CertificateExpired), origin = remote)
        assertEquals(FailureKind.CertificateNotTrusted, untrusted.kind)
        assertEquals(FailureAction.ReviewCertificate, untrusted.primary)
        assertEquals(FailureKind.CertificateExpired, expired.kind)
    }

    @Test
    fun `B-NAV-008 a refusing remote host is not reachable, with network checks, and the address stays under Details`() {
        val f = d(refused, origin = remote)
        assertEquals(FailureKind.NotReachable, f.kind)
        assertFalse(f.tailnet)
        assertEquals(FailureAction.Retry, f.primary)
        assertEquals(
            listOf<FailureCheck>(FailureCheck.ServeRunning, FailureCheck.SameNetworkOrVpn, FailureCheck.AddressChanged),
            f.checks,
        )
    }

    @Test
    fun `B-NAV-008 timeouts blame the network in between and name the port`() {
        val f = d(timedOut, origin = remote)
        assertEquals(FailureKind.TimedOut, f.kind)
        assertTrue(FailureCheck.Firewall(4096) in f.checks)
    }

    @Test
    fun `B-NAV-008 an unhealthy server is a server problem`() {
        assertEquals(FailureKind.ServerError, d(Net.ServerFailed(503), origin = remote).kind)
        assertEquals(FailureKind.ServerError, d(Net.ServerBusy(Net.ServerBusy.Kind.Failed), origin = remote).kind)
    }

    @Test
    fun `B-NAV-008 a Tailscale address that did not answer says to check Tailscale first`() {
        val f = d(refused, origin = ServerOrigin("http", "100.101.102.103", 4096))
        assertTrue(f.tailnet)
        assertEquals(FailureCheck.TailscaleOn, f.checks.first())
        // A refused sign-in means Tailscale did its part.
        val signIn = d(Net.SignInNeeded(Net.SignInNeeded.Why.PasswordRejected), origin = ServerOrigin("https", "laptop.tail1234.ts.net", 443))
        assertFalse(signIn.tailnet)
        assertFalse(d(refused, origin = ServerOrigin("http", "100.63.0.1", 4096)).tailnet)
    }

    @Test
    fun `B-NAV-008 an unconfirmed plain-HTTP address asks to review the HTTP warning, never to retry`() {
        val f = d(Net.TrustRefused(Net.TrustRefused.Why.CleartextNotConfirmed), origin = ServerOrigin("http", "192.168.1.20", 4096))
        assertEquals(FailureKind.HttpUnconfirmed, f.kind)
        assertEquals(FailureAction.EditServer, f.primary)
    }

    // ---- B-NAV-009

    @Test
    fun `B-NAV-009 three attempts add the line that retrying will not start a server`() {
        assertEquals(FailureCheck.TriedTimes(3), d(refused, attempts = 3).checks.last())
        assertFalse(d(refused, attempts = 1).checks.any { it is FailureCheck.TriedTimes })
    }

    @OptIn(RedactorApi::class)
    @Test
    fun `B-NAV-009 the technical text is kept verbatim only for Details`() {
        val raw = Redacted("UnknownHostException")
        val f = d(Net.Unreachable(Net.Unreachable.Why.Dns, raw), origin = remote)
        assertEquals(raw, f.details)
        assertNull(d(refused, origin = remote).details)
    }

    @Test
    fun `B-NAV-009 the app-managed phone server that does not answer is stopped, and starting it is the fix`() {
        val f = d(refused, managedPhoneServer = true)
        assertEquals(FailureAction.StartPhoneServer, f.primary)
        assertEquals(FailureKind.PhoneServerStopped, f.kind)
        assertFalse(FailureCheck.TunnelConnected in f.checks)
        assertEquals(FailureAction.Retry, d(refused).primary)
    }

    @Test
    fun `B-NAV-009 OpenCode inside the app - starting it is the fix and nothing is said about Termux, tunnels or passwords`() {
        val inApp = ServerOrigin("http", "127.0.0.1", 4097)
        for (failure in listOf(refused, timedOut, Net.SignInNeeded(Net.SignInNeeded.Why.PasswordRejected))) {
            val f = d(failure, origin = inApp, inAppServer = true, attempts = 4)
            assertEquals(FailureAction.StartPhoneServer, f.primary, "$failure")
            assertTrue(f.checks.isEmpty(), "$failure")
        }
        assertEquals(FailureKind.InAppStopped, d(refused, origin = inApp, inAppServer = true).kind)
        assertEquals(FailureKind.InAppNotResponding, d(Net.SignInNeeded(Net.SignInNeeded.Why.PasswordRejected), origin = inApp, inAppServer = true).kind)
        assertEquals(FailureKind.InAppStartFailed, d(Failure.Unknown(), origin = inApp, inAppServer = true, inAppStartFailed = true).kind)
    }

    @Test
    fun `B-NAV-009 a legacy OpenCode 1 server gets the upgrade instruction`() {
        val f = d(Net.Incompatible(Net.Incompatible.Kind.LegacyOpenCode1), origin = remote)
        assertEquals(FailureKind.OpenCode1, f.kind)
        assertEquals(FailureAction.ChangeServer, f.primary)
        assertEquals(listOf<FailureCheck>(FailureCheck.UpgradeCommand), f.checks)
    }
}
