package io.github.eslamasabry.oc.engine.connection

import io.github.eslamasabry.oc.core.common.CertificateWhy
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.IncompatibleKind
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.RedactorApi
import io.github.eslamasabry.oc.core.common.SignInWhy
import io.github.eslamasabry.oc.core.common.WireExtras
import io.github.eslamasabry.oc.core.common.WireTag
import io.github.eslamasabry.oc.core.common.ids.RequestId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.collections.immutable.persistentMapOf

/**
 * B-PROTO-095 / B-SEC-014 / B-SEC-130 / B-SEC-133, red first, for the frozen contract in
 * docs/native/server-errors-2026-11-02/contract.md: `connectionFailure` keeps the exact failure
 * subtype and fields and replaces `details` with generated stage + kind/reason/status diagnostics
 * that never copy the original raw details, URLs, credentials or server prose. Until the connection
 * builder lands the real annotator, the refusal stub returns failures unchanged and every test here
 * fails.
 */
class ConnectionFailureTest {
    /**
     * Raw, hostile details: a credentialed URL, a private host and transport prose. None of it may
     * survive.
     */
    private val raw =
        "GET http://opencode:hunter2@10.0.0.2:4096/api/session failed: ECONNRESET by peer"
    private val forbidden =
        listOf("hunter2", "opencode:hunter2", "://", "10.0.0.2", "ECONNRESET", "/api/session")

    private class Case(
        val label: String,
        val original: Failure,
        val tokens: List<String>,
        val check: (Failure) -> Unit = {},
    )

    private val cases =
        listOf(
            Case(
                "offline",
                Failure.Network(NetworkWhy.Offline, rawDetails()),
                listOf("Network", "Offline"),
            ) {
                assertEquals(NetworkWhy.Offline, assertIs<Failure.Network>(it).why)
            },
            Case("dns", Failure.Network(NetworkWhy.Dns, rawDetails()), listOf("Network", "Dns")) {
                assertEquals(NetworkWhy.Dns, assertIs<Failure.Network>(it).why)
            },
            Case(
                "refused",
                Failure.Network(NetworkWhy.Refused, rawDetails()),
                listOf("Network", "Refused"),
            ) {
                assertEquals(NetworkWhy.Refused, assertIs<Failure.Network>(it).why)
            },
            Case(
                "reset",
                Failure.Network(NetworkWhy.Reset, rawDetails()),
                listOf("Network", "Reset"),
            ) {
                assertEquals(NetworkWhy.Reset, assertIs<Failure.Network>(it).why)
            },
            Case(
                "no route",
                Failure.Network(NetworkWhy.NoRoute, rawDetails()),
                listOf("Network", "NoRoute"),
            ) {
                assertEquals(NetworkWhy.NoRoute, assertIs<Failure.Network>(it).why)
            },
            Case(
                "unknown network",
                Failure.Network(NetworkWhy.Unknown, rawDetails()),
                listOf("Network", "Unknown"),
            ) {
                assertEquals(NetworkWhy.Unknown, assertIs<Failure.Network>(it).why)
            },
            Case("timeout", Failure.TimedOut(rawDetails()), listOf("TimedOut")),
            Case(
                "changed pin",
                Failure.Certificate(CertificateWhy.Changed, rawDetails()),
                listOf("Certificate", "Changed"),
            ) {
                assertEquals(CertificateWhy.Changed, assertIs<Failure.Certificate>(it).why)
            },
            Case(
                "expired pin",
                Failure.Certificate(CertificateWhy.Expired, rawDetails()),
                listOf("Certificate", "Expired"),
            ) {
                assertEquals(CertificateWhy.Expired, assertIs<Failure.Certificate>(it).why)
            },
            Case(
                "untrusted",
                Failure.Certificate(CertificateWhy.Untrusted, rawDetails()),
                listOf("Certificate", "Untrusted"),
            ) {
                assertEquals(CertificateWhy.Untrusted, assertIs<Failure.Certificate>(it).why)
            },
            Case(
                "password rejected",
                Failure.SignIn(SignInWhy.PasswordRejected, rawDetails()),
                listOf("SignIn", "PasswordRejected"),
            ) {
                assertEquals(SignInWhy.PasswordRejected, assertIs<Failure.SignIn>(it).why)
            },
            Case(
                "token missing",
                Failure.SignIn(SignInWhy.TokenMissing, rawDetails()),
                listOf("SignIn", "TokenMissing"),
            ) {
                assertEquals(SignInWhy.TokenMissing, assertIs<Failure.SignIn>(it).why)
            },
            Case(
                "legacy server",
                Failure.Incompatible(IncompatibleKind.LegacyOpenCode1, rawDetails()),
                listOf("Incompatible", "LegacyOpenCode1"),
            ) {
                assertEquals(
                    IncompatibleKind.LegacyOpenCode1,
                    assertIs<Failure.Incompatible>(it).kind,
                )
            },
            Case(
                "unsupported version",
                Failure.Incompatible(IncompatibleKind.UnsupportedServerVersion, rawDetails()),
                listOf("Incompatible", "UnsupportedServerVersion"),
            ) {
                assertEquals(
                    IncompatibleKind.UnsupportedServerVersion,
                    assertIs<Failure.Incompatible>(it).kind,
                )
            },
            Case(
                "protocol mismatch",
                Failure.Incompatible(IncompatibleKind.ProtocolMismatch, rawDetails()),
                listOf("Incompatible", "ProtocolMismatch"),
            ) {
                assertEquals(
                    IncompatibleKind.ProtocolMismatch,
                    assertIs<Failure.Incompatible>(it).kind,
                )
            },
            Case(
                "unreadable reply",
                Failure.UnreadableReply(rawDetails()),
                listOf("UnreadableReply"),
            ),
            Case("storage refused", Failure.StorageRefused(rawDetails()), listOf("StorageRefused")),
            Case(
                "secret unavailable",
                Failure.SecretUnavailable(rawDetails()),
                listOf("SecretUnavailable"),
            ),
            Case(
                "server 502",
                Failure.Server(502, WireTag("session"), rawDetails()),
                listOf("Server", "502"),
            ) {
                val server = assertIs<Failure.Server>(it)
                assertEquals(502, server.status)
                assertEquals(WireTag("session"), server.tag)
            },
            Case(
                "server without status",
                Failure.Server(null, null, rawDetails()),
                listOf("Server"),
            ) {
                assertNull(assertIs<Failure.Server>(it).status)
            },
            Case(
                "rejected 403",
                Failure.Rejected(
                    403,
                    WireTag("form"),
                    WireExtras(persistentMapOf("field" to "address")),
                    rawDetails(),
                ),
                listOf("Rejected", "403"),
            ) {
                val rejected = assertIs<Failure.Rejected>(it)
                assertEquals(403, rejected.status)
                assertEquals(WireTag("form"), rejected.tag)
                assertEquals(WireExtras(persistentMapOf("field" to "address")), rejected.extras)
            },
            Case(
                "not found",
                Failure.NotFound(WireTag("session"), RequestId("r-1"), rawDetails()),
                listOf("NotFound"),
            ) {
                val notFound = assertIs<Failure.NotFound>(it)
                assertEquals(WireTag("session"), notFound.tag)
                assertEquals(RequestId("r-1"), notFound.requestId)
            },
        )

    private fun rawDetails(): Redacted = testRedacted(raw)

    /**
     * Identity redaction that records every string the helper hands it, so tests can see what was
     * generated.
     */
    private class RecordingRedact : (String) -> Redacted {
        val given = mutableListOf<String>()

        override fun invoke(text: String): Redacted {
            given += text
            return testRedacted(text)
        }
    }

    /**
     * The contract's details pin: the shown details are one of the strings handed to the redactor,
     * name the stage and kind tokens, and neither they nor anything the helper generated carries
     * the raw input.
     */
    private fun checkGenerated(
        label: String,
        redact: RecordingRedact,
        out: Failure,
        tokens: List<String>,
    ) {
        assertTrue(
            redact.given.isNotEmpty(),
            "no stage details were handed to the redactor for $label",
        )
        val shown = out.details?.text ?: fail("details are missing for $label")
        assertTrue(
            shown in redact.given,
            "shown details were not built through the redactor for $label: $shown",
        )
        for (token in tokens) assertTrue(
            token in shown,
            "$token missing from the details for $label: $shown",
        )
        val generated = redact.given.joinToString(" · ")
        for (secret in forbidden) {
            assertFalse(secret in generated, "$secret reached the redactor for $label: $generated")
            assertFalse(secret in shown, "$secret leaked into the details for $label: $shown")
        }
    }

    @Test
    fun `B-PROTO-095 stage annotation preserves the exact failure subtype and fields`() {
        for (case in cases) {
            val redact = RecordingRedact()
            val out = connectionFailure(case.original, ConnectionStage.OpenGateway, redact)
            assertEquals(
                case.original::class,
                out::class,
                "Failure subtype changed for ${case.label}",
            )
            case.check(out)
            checkGenerated(case.label, redact, out, case.tokens)
        }
    }

    @Test
    fun `B-PROTO-095 every stage is named in the generated details and no other stage is`() {
        for (stage in ConnectionStage.entries) {
            val redact = RecordingRedact()
            val out =
                connectionFailure(Failure.Network(NetworkWhy.Dns, rawDetails()), stage, redact)
            assertEquals(NetworkWhy.Dns, assertIs<Failure.Network>(out).why)
            checkGenerated("stage $stage", redact, out, listOf("Network", "Dns", stage.name))
            val generated = redact.given.joinToString(" · ")
            for (other in ConnectionStage.entries.filter { it != stage }) {
                assertFalse(
                    other.name in generated,
                    "$other leaked into the $stage details: $generated",
                )
            }
        }
    }

    @Test
    fun `B-SEC-014 server prose and internal URLs never survive into stage details`() {
        val prose = "<html>upstream 10.0.0.2:8080 says Bad Gateway for /api/session</html>"
        val redact = RecordingRedact()
        val out =
            connectionFailure(
                Failure.Server(502, details = testRedacted(prose)),
                ConnectionStage.LoadConversations,
                redact,
            )
        val server = assertIs<Failure.Server>(out)
        assertEquals(502, server.status)
        checkGenerated("server prose", redact, out, listOf("Server", "502", "LoadConversations"))
        val generated = redact.given.joinToString(" · ")
        for (bad in listOf("Bad Gateway", "<html>", "10.0.0.2:8080", "/api/session", "upstream")) {
            assertFalse(bad in generated, "server prose reached the redactor: $generated")
        }
    }

    @Test
    fun `B-SEC-130 failures that had no details still get generated stage details`() {
        listOf(
                Failure.TimedOut() to listOf("OpenGateway", "TimedOut"),
                Failure.Network(NetworkWhy.Refused) to listOf("OpenGateway", "Network", "Refused"),
                Failure.StorageRefused() to listOf("OpenGateway", "StorageRefused"),
                Failure.SecretUnavailable() to listOf("OpenGateway", "SecretUnavailable"),
                Failure.UnreadableReply() to listOf("OpenGateway", "UnreadableReply"),
                Failure.Incompatible(IncompatibleKind.UnsupportedServerVersion) to
                    listOf("OpenGateway", "Incompatible", "UnsupportedServerVersion"),
            )
            .forEach { (failure, tokens) ->
                val redact = RecordingRedact()
                val out = connectionFailure(failure, ConnectionStage.OpenGateway, redact)
                val shown =
                    assertNotNull(
                            out.details,
                            "${failure::class.simpleName} needs generated stage details",
                        )
                        .text
                for (token in tokens) assertTrue(token in shown, "$token missing: $shown")
                for (secret in forbidden) assertFalse(secret in shown, "$secret leaked: $shown")
            }
    }
}

/**
 * Test-only redacted text, as in the kit's test scenes; the app's only constructor stays in the
 * Redactor.
 */
@OptIn(RedactorApi::class) private fun testRedacted(text: String): Redacted = Redacted(text)
