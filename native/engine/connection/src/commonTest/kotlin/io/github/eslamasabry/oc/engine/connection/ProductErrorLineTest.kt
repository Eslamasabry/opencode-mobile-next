package io.github.eslamasabry.oc.engine.connection

import io.github.eslamasabry.oc.core.common.BusyKind
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.RedactorApi
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.SignInWhy
import io.github.eslamasabry.oc.core.common.WireTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Ports of `test/product_error_text_test.dart` and `test/no_raw_error_text_test.dart` (B-SEC-104, 131, 132). */
@OptIn(RedactorApi::class)
class ProductErrorLineTest {
    private val prose = Redacted("Invalid apiKey=•••")
    private val revertTag = WireTag(SESSION_REVERT_PENDING_TAG)

    @Test
    fun `B-SEC-104 the mapper never promotes protocol text - prose stays in Details for every status`() {
        for (status in listOf(400, 401, 404, 409, 422, 429, 500, 503)) {
            for (failure in listOf<Failure>(
                Failure.Rejected(status, null, details = prose),
                Failure.Rejected(status, revertTag, details = prose),
                Failure.Server(status, details = prose),
                Failure.Conflict(revertTag, details = prose),
            )) {
                val text = productErrorText(failure)
                // The line is a closed category with a words key; the server's prose is only the redacted details.
                assertEquals(prose, text.details, "$failure")
                assertEquals(ProductErrorLine.entries.single { it == text.line }.wordsKey, text.line.wordsKey)
            }
        }
    }

    @Test
    fun `B-SEC-104 the product error mapper depends on domain categories only`() {
        // Every flat Failure subtype has a line (the `when` is exhaustive with no else), and the result type holds no
        // free text: only a category, an HTTP status and already-redacted details.
        assertEquals(setOf("line", "status", "details"), ProductErrorText(ProductErrorLine.Unknown).toString()
            .substringAfter('(').substringBefore(')').split(", ").map { it.substringBefore('=') }.toSet())
    }

    @Test
    fun `B-SEC-131 the app's own staged-revert sentence is kept`() {
        assertEquals(ProductErrorLine.StagedRevert, productErrorText(Failure.StagedRevert()).line)
        assertEquals("productErrorStagedRevert", ProductErrorLine.StagedRevert.wordsKey)
    }

    @Test
    fun `B-SEC-131 a staged-revert tag never permits arbitrary protocol prose`() {
        val text = productErrorText(Failure.Conflict(revertTag, details = prose))
        assertEquals(ProductErrorLine.StagedRevert, text.line, "the app's sentence, never the server's words")
        assertEquals(prose, text.details)
        assertEquals(ProductErrorLine.StagedRevert, productErrorText(Failure.Rejected(409, revertTag, details = prose)).line)
    }

    @Test
    fun `B-SEC-132 server statuses, timeouts and transport failures have their own words`() {
        val server = productErrorText(Failure.Server(503))
        assertEquals(ProductErrorLine.Server, server.line)
        assertEquals(503, server.status, "the line says (error 503)")
        assertEquals(ProductErrorLine.TimedOut, productErrorText(Failure.TimedOut()).line)
        for (why in NetworkWhy.entries) {
            assertEquals(ProductErrorLine.Unreachable, productErrorText(Failure.Network(why)).line, "socket errors collapse to one line")
        }
        assertEquals(ProductErrorLine.Busy, productErrorText(Failure.Busy(BusyKind.Starting)).line)
        assertEquals(ProductErrorLine.SignIn, productErrorText(Failure.SignIn(SignInWhy.PasswordRejected)).line)
    }

    @Test
    fun `B-SEC-132 invalid state and unknown errors are said plainly without blaming the network`() {
        for (failure in listOf(Failure.Unexpected(prose), Failure.Unknown(prose), Failure.Refused(RefusalReason.StaleScope))) {
            val text = productErrorText(failure)
            assertEquals(ProductErrorLine.Unknown, text.line, "$failure")
        }
        assertNull(productErrorText(Failure.Server(null)).status)
    }

    @Test
    fun `B-SEC-132 storage, keyring, device and native failures have their own words and hide native text`() {
        assertEquals(ProductErrorLine.Storage, productErrorText(Failure.StorageRefused()).line)
        assertEquals(ProductErrorLine.Device, productErrorText(Failure.SecretUnavailable()).line)
        val device = productErrorText(Failure.Device(Redacted("Libsecret error: Failed to unlock the keyring")))
        assertEquals(ProductErrorLine.Device, device.line)
        assertEquals("Libsecret error: Failed to unlock the keyring", device.details?.text, "native text is only the details")
        assertEquals(ProductErrorLine.Termux, productErrorText(Failure.Termux(Redacted("bash: line 3: opencode: command not found"))).line)
        assertEquals(ProductErrorLine.Computer, productErrorText(Failure.ComputerDidNotAnswer()).line)
    }
}
