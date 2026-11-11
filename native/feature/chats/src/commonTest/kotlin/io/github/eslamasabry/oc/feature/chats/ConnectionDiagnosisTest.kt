package io.github.eslamasabry.oc.feature.chats

import io.github.eslamasabry.oc.core.common.CertificateWhy
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.SignInWhy
import io.github.eslamasabry.oc.feature.chats.presentation.ConnectionDiagnosis
import io.github.eslamasabry.oc.feature.chats.presentation.DiagnosisAction
import io.github.eslamasabry.oc.feature.chats.presentation.DiagnosisCause
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConnectionDiagnosisTest {
    private fun of(failure: Failure?, url: String = "https://laptop.example:4096", token: Boolean = false, tries: Int = 1) =
        ConnectionDiagnosis.of(failure, url, token, tries)

    @Test
    fun noFailureNoDiagnosis() = assertNull(of(null))

    @Test
    fun aRejectedPasswordLeadsWithUpdatePassword() {
        val d = of(Failure.SignIn(SignInWhy.PasswordRejected))!!
        assertEquals(DiagnosisCause.PasswordRejected, d.cause)
        assertEquals(DiagnosisAction.UpdatePassword, d.lead)
    }

    @Test
    fun tokenServersAskForTheTokenNotThePassword() {
        assertEquals(DiagnosisCause.TokenRequired, of(Failure.SignIn(SignInWhy.TokenMissing), token = true)!!.cause)
        val rejected = of(Failure.SignIn(SignInWhy.Forbidden), token = true)!!
        assertEquals(DiagnosisCause.TokenRejected, rejected.cause)
        assertEquals(DiagnosisAction.UpdateToken, rejected.lead)
    }

    @Test
    fun aChangedCertificateOffersTheRecheck() {
        val d = of(Failure.Certificate(CertificateWhy.Changed))!!
        assertEquals(DiagnosisCause.CertificateChanged, d.cause)
        assertEquals(DiagnosisAction.ReviewCertificate, d.lead)
        assertEquals(DiagnosisCause.CertificateUntrusted, of(Failure.Certificate(CertificateWhy.Untrusted))!!.cause)
    }

    @Test
    fun loopbackAndCodexAreToldApart() {
        assertEquals(DiagnosisCause.Loopback, of(Failure.Network(NetworkWhy.Refused), "http://127.0.0.1:4096")!!.cause)
        assertEquals(DiagnosisCause.CodexLocal, of(Failure.Network(NetworkWhy.Refused), "http://localhost:9000", token = true)!!.cause)
        assertEquals(DiagnosisCause.CodexRemote, of(Failure.TimedOut(), token = true)!!.cause)
    }

    @Test
    fun aTailscaleAddressThatDidNotAnswerOffersTailscale() {
        assertTrue(of(Failure.TimedOut(), "http://100.100.1.2:4096")!!.tailnet)
        assertTrue(of(Failure.Network(NetworkWhy.Refused), "https://box.tail1234.ts.net")!!.tailnet)
        assertFalse(of(Failure.TimedOut(), "http://100.200.1.2:4096")!!.tailnet)
        assertFalse(of(Failure.SignIn(SignInWhy.PasswordRejected), "http://100.100.1.2:4096")!!.tailnet)
        assertFalse(of(Failure.Network(NetworkWhy.Refused), "http://127.0.0.1:4096")!!.tailnet)
    }

    @Test
    fun theWrongPortIsNamed() {
        assertEquals(8080, of(Failure.TimedOut(), "http://10.0.0.5:8080/x")!!.port)
        assertEquals(4096, of(Failure.TimedOut(), "https://laptop.example")!!.port)
    }

    @Test
    fun afterThreeTriesTheWayOutLeads() {
        val d = of(Failure.Network(NetworkWhy.Refused), tries = 3)!!
        assertTrue(d.escalated)
        assertEquals(DiagnosisAction.ChangeServer, d.lead)
        // A sign-in problem is still fixed by signing in again, however often it was tried.
        assertEquals(DiagnosisAction.UpdatePassword, of(Failure.SignIn(SignInWhy.PasswordRejected), tries = 5)!!.lead)
        assertFalse(of(Failure.Network(NetworkWhy.Refused), tries = 2)!!.escalated)
    }
}
