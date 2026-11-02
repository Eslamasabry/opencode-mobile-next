package io.github.eslamasabry.oc.feature.servers.presentation

import io.github.eslamasabry.oc.core.common.CertificateWhy
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.IncompatibleKind
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.SignInWhy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * B-PROTO-095 / B-SEC-014 / B-SEC-133, red first, for the frozen contract in
 * docs/native/server-errors-2026-11-02/contract.md: `serverFailureKind` names the actual safe
 * failure category from the flat Failure list so probes explain plainly — a TLS, sign-in,
 * cleartext-consent, invalid-address, incompatible or unreadable-reply failure is never presented
 * as a missing server — and null/unknown keep the generic issue fallback. The refusal stub answers
 * Other to everything, so every test here fails until the UI builder lands the real mapper.
 */
class ServerFailureKindTest {
    @Test
    fun `B-PROTO-095 every connection failure names its own category and credentials or TLS never claim network`() {
        assertEquals(
            listOf(
                "Network",
                "Timeout",
                "Certificate",
                "SignIn",
                "CleartextConsent",
                "InvalidAddress",
                "Incompatible",
                "UnreadableReply",
                "Server",
                "Rejected",
                "Storage",
                "Secret",
                "Other",
            ),
            ServerFailureKind.entries.map { it.name },
            "the frozen kind list is closed",
        )
        listOf(
                Failure.Network(NetworkWhy.Offline) to ServerFailureKind.Network,
                Failure.Network(NetworkWhy.Dns) to ServerFailureKind.Network,
                Failure.Network(NetworkWhy.Refused) to ServerFailureKind.Network,
                Failure.Network(NetworkWhy.Reset) to ServerFailureKind.Network,
                Failure.Network(NetworkWhy.NoRoute) to ServerFailureKind.Network,
                Failure.Network(NetworkWhy.Unknown) to ServerFailureKind.Network,
                Failure.TimedOut() to ServerFailureKind.Timeout,
                Failure.Certificate(CertificateWhy.Changed) to ServerFailureKind.Certificate,
                Failure.Certificate(CertificateWhy.Expired) to ServerFailureKind.Certificate,
                Failure.Certificate(CertificateWhy.Untrusted) to ServerFailureKind.Certificate,
                Failure.SignIn(SignInWhy.PasswordMissing) to ServerFailureKind.SignIn,
                Failure.SignIn(SignInWhy.PasswordRejected) to ServerFailureKind.SignIn,
                Failure.SignIn(SignInWhy.TokenMissing) to ServerFailureKind.SignIn,
                Failure.SignIn(SignInWhy.TokenRejected) to ServerFailureKind.SignIn,
                Failure.SignIn(SignInWhy.HostNotAllowed) to ServerFailureKind.SignIn,
                Failure.SignIn(SignInWhy.Forbidden) to ServerFailureKind.SignIn,
                Failure.Incompatible(IncompatibleKind.LegacyOpenCode1) to
                    ServerFailureKind.Incompatible,
                Failure.Incompatible(IncompatibleKind.UnsupportedServerVersion) to
                    ServerFailureKind.Incompatible,
                Failure.Incompatible(IncompatibleKind.ProtocolMismatch) to
                    ServerFailureKind.Incompatible,
                Failure.UnreadableReply() to ServerFailureKind.UnreadableReply,
                Failure.Server(502, null) to ServerFailureKind.Server,
                Failure.Server(null, null) to ServerFailureKind.Server,
                Failure.Rejected(403, null) to ServerFailureKind.Rejected,
                Failure.Rejected(null, null) to ServerFailureKind.Rejected,
                Failure.StorageRefused() to ServerFailureKind.Storage,
                Failure.SecretUnavailable() to ServerFailureKind.Secret,
            )
            .forEach { (failure, expected) ->
                assertEquals(expected, serverFailureKind(failure), "wrong category for $failure")
                if (expected != ServerFailureKind.Network) {
                    assertNotEquals(
                        ServerFailureKind.Network,
                        serverFailureKind(failure),
                        "$failure claims a missing server",
                    )
                }
            }
    }

    @Test
    fun `B-SEC-133 null unknown and app refusals stay on the generic issue fallback`() {
        assertEquals(ServerFailureKind.Other, serverFailureKind(null))
        assertEquals(ServerFailureKind.Other, serverFailureKind(Failure.Unknown()))
        assertEquals(ServerFailureKind.Other, serverFailureKind(Failure.Unexpected()))
        assertEquals(
            ServerFailureKind.Other,
            serverFailureKind(Failure.Refused(RefusalReason.ProfileClosing)),
        )
        assertEquals(
            ServerFailureKind.Other,
            serverFailureKind(Failure.Refused(RefusalReason.MigrationPending)),
        )
        assertEquals(
            ServerFailureKind.Other,
            serverFailureKind(Failure.Unavailable(FeatureId("pty"))),
        )
    }

    @Test
    fun `B-SEC-014 cleartext consent and an invalid address are distinct categories and neither is network`() {
        assertEquals(
            ServerFailureKind.CleartextConsent,
            serverFailureKind(Failure.Refused(RefusalReason.CleartextNotConfirmed)),
        )
        assertEquals(
            ServerFailureKind.InvalidAddress,
            serverFailureKind(Failure.Refused(RefusalReason.InvalidAddress)),
        )
        assertNotEquals(
            serverFailureKind(Failure.Refused(RefusalReason.CleartextNotConfirmed)),
            serverFailureKind(Failure.Refused(RefusalReason.InvalidAddress)),
        )
        assertNotEquals(
            ServerFailureKind.Network,
            serverFailureKind(Failure.Refused(RefusalReason.CleartextNotConfirmed)),
            "a consent refusal is not connectivity",
        )
        assertNotEquals(
            ServerFailureKind.Network,
            serverFailureKind(Failure.Refused(RefusalReason.InvalidAddress)),
            "a spelling refusal is not connectivity",
        )
    }
}
