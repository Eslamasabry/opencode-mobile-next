package io.github.eslamasabry.opencode_mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetActionLeaseTest {
    private val payload = mapOf<String, Any?>("id" to "tap-1", "action" to "stop")

    @Test
    fun totalLeaseIncludesStartupAndCannotBeExtendedByAnotherTap() {
        val lease = WidgetActionLease("tap-1", 1000)
        assertEquals(30_000L, lease.remaining(1000))
        assertEquals(1000L, lease.remaining(30_000))
        assertEquals(0L, lease.remaining(31_000))
        assertEquals(0L, lease.remaining(99_000))
        assertEquals(31_000L, lease.deadline)
    }

    @Test
    fun settlementAndTimeoutRaceCompleteOnlyOnce() {
        val lease = WidgetActionLease("tap-1", 0)
        assertTrue(lease.settle())
        assertFalse(lease.settle())
        assertTrue(lease.settled)
    }

    @Test
    fun coldWhitelistExcludesTextAndAutomaticWork() {
        assertEquals(
            setOf("allowOnce", "reject", "stop", "approvePlan", "resume"),
            WidgetActionLease.actions,
        )
        assertFalse("voice" in WidgetActionLease.actions)
        assertFalse("steer" in WidgetActionLease.actions)
    }

    @Test
    fun validSettlementKeepsOnlyFixedTokens() {
        val response =
            payload +
                mapOf(
                    "status" to "rejected",
                    "error" to "unsupported",
                    "debug" to "private",
                    "text" to "prompt",
                )
        assertEquals(
            payload + mapOf("status" to "rejected", "error" to "unsupported"),
            WidgetActionLease.safeResult(payload, response),
        )
    }

    @Test
    fun mismatchedMissingOrMalformedSettlementIsUncertainNeverAccepted() {
        val accepted = payload + mapOf("status" to "accepted")
        val malformed =
            listOf(
                null,
                accepted + mapOf("id" to "other"),
                accepted + mapOf("action" to "resume"),
                accepted + mapOf("status" to "raw error"),
            )
        for (response in malformed) {
            assertEquals(
                payload + mapOf("status" to "uncertain", "error" to "uncertainDelivery"),
                WidgetActionLease.safeResult(payload, response),
            )
        }
    }

    @Test
    fun pluginErrorsCannotEnterTheSnapshot() {
        assertEquals(
            payload + mapOf("status" to "rejected"),
            WidgetActionLease.safeResult(
                payload,
                payload + mapOf("status" to "rejected", "error" to "Authorization: private"),
            ),
        )
    }

    @Test
    fun `widgets-MAJOR-6 tap uses immutable service intent and promotion stays behind ForegroundWork`() {
        // Architectural guard: replacing this with a receiver/job restart can
        // lose Android's user-tap exemption or silently enable permanent work.
        // Gradle runs unit tests from the app module; a run from the repo root
        // (as the native QA script does) sees the same files one level up.
        val base =
            listOf(
                    "src/main/kotlin/io/github/eslamasabry/opencode_mobile",
                    "android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile",
                )
                .map { java.io.File(it) }
                .first { it.isDirectory }
        val bridge =
            java.io
                .File(base, "WidgetV2Bridge.kt")
                .readText()
                .substringAfter("fun actionIntent(")
                .substringBefore("fun routeIntent(")
        assertTrue(bridge.contains("PendingIntent.getForegroundService("))
        assertTrue(bridge.contains("PendingIntent.FLAG_IMMUTABLE"))
        assertFalse(bridge.contains("getBroadcast("))
        // Promotion belongs to the shared foreground port, so callers never need a service-bound
        // controller.
        val transient =
            java.io.File(base.parentFile, "oc/android/widgets/WidgetActionAdmission.kt").readText()
        assertTrue(transient.contains("ForegroundKind.WidgetAction"))
        assertTrue(transient.contains("foreground.end(it)"))
        assertFalse(transient.contains("setPausedByUser"))
        assertFalse(transient.contains("startForegroundService("))
        assertFalse(transient.contains("startForeground("))
    }
}
