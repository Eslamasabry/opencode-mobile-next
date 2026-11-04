package io.github.eslamasabry.oc.engine.integrations.quota

import kotlinx.collections.immutable.ImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/** Ports test/provider_quota_test.dart (rows B-PROTO-085, B-PROTO-086, B-PROTO-087). */
class ProviderQuotaSnapshotTest {
    private fun rejects(value: Any?, reason: String = "") = assertNull(parse(value), "expected rejection $reason")

    // ---- B-PROTO-085

    @Test
    fun `B-PROTO-085 MiniMax preserves source-bound percentages and rejects invented eligibility`() {
        val value = quotaFixture(provider = QuotaProvider.Minimax)
        val snapshot = parseOk(value)
        assertEquals(QuotaProvider.Minimax, snapshot.provider)
        assertTrue(snapshot.canShowWindows)
        assertEquals(QuotaAccountStatus.SourceBound, snapshot.account.status)
        assertEquals(74.5, snapshot.windows.first().remainingPercent)
        assertNull(snapshot.windows.last().remainingPercent)
        assertTrue(quotaCollectionAvailable(QuotaProvider.Minimax))
        value["ordinaryUsageAllowed"] = true
        rejects(value)
    }

    @Test
    fun `B-PROTO-085 Claude source-bound snapshots do not claim independently matched account identity`() {
        val snapshot = parseOk(quotaFixture(provider = QuotaProvider.Claude))
        assertEquals(QuotaProvider.Claude, snapshot.provider)
        assertEquals(QuotaAccountStatus.SourceBound, snapshot.account.status)
        assertNull(snapshot.account.plan)
        assertTrue(snapshot.canShowWindows)
        assertNull(snapshot.ordinaryUsageAllowed)
        assertEquals("/ocmn/quota/v1/claude", quotaPathFor(QuotaProvider.Claude))
        assertEquals(PROVIDER_QUOTA_PATH, quotaPathFor(QuotaProvider.Codex))
    }

    @Test
    fun `B-PROTO-085 provider-source mismatches and invented Claude eligibility are rejected`() {
        val changes: List<(MutableMap<String, Any?>) -> Unit> =
            listOf(
                { it["source"] = "codex.wham" },
                { it["ordinaryUsageAllowed"] = true },
                { it.account().remove("ref") },
                { it["status"] = "authRequired" },
            )
        for (change in changes) {
            val value = quotaFixture(provider = QuotaProvider.Claude)
            change(value)
            rejects(value)
        }
        val codex = quotaFixture()
        codex.account()["status"] = "sourceBound"
        rejects(codex)
    }

    @Test
    fun `B-PROTO-085 parses reported and missing windows without invented allowances`() {
        val snapshot = parseOk(quotaFixture())
        assertEquals(ProviderQuotaStatus.Ok, snapshot.status)
        assertEquals(QuotaFreshness.Fresh, snapshot.freshness)
        assertEquals(QuotaAccountStatus.Matched, snapshot.account.status)
        assertEquals("plus", snapshot.account.plan)
        assertEquals("a".repeat(64), snapshot.account.ref)
        assertEquals(true, snapshot.ordinaryUsageAllowed)
        val first = snapshot.windows.first()
        assertEquals(25.5, first.usedPercent)
        assertEquals(74.5, first.remainingPercent)
        assertEquals(18000L, first.durationSeconds)
        assertNotNull(first.resetsAt) // kotlin.time.Instant is always UTC
        val last = snapshot.windows.last()
        assertEquals(QuotaWindowStatus.Missing, last.status)
        assertNull(last.usedPercent)
        assertNull(last.remainingPercent)
        assertNull(last.durationSeconds)
        assertNull(last.resetsAt)
        assertEquals(1_060_000L, snapshot.expiresAt.toEpochMilliseconds())
    }

    @Test
    fun `B-PROTO-085 optional ordinary usage remains unknown, and false stays false`() {
        for (allowed in listOf(null, false, true)) {
            val value = quotaFixture().also { it["ordinaryUsageAllowed"] = allowed }
            assertEquals(allowed, parseOk(value).ordinaryUsageAllowed)
        }
        val value = quotaFixture().also { it.remove("ordinaryUsageAllowed") }
        assertNull(parseOk(value).ordinaryUsageAllowed)
    }

    @Test
    fun `B-PROTO-085 zero and fully used are real values, not missing windows`() {
        for (percent in listOf(0, 100)) {
            val value = quotaFixture()
            value.firstWindow()["usedPercent"] = percent
            val window = parseOk(value).windows.first()
            assertEquals(percent.toDouble(), window.usedPercent)
            assertEquals((100 - percent).toDouble(), window.remainingPercent)
        }
    }

    @Test
    fun `B-PROTO-085 expiry is inclusive and a passed reset never replenishes a window`() {
        val value = parseOk(quotaFixture())
        assertFalse(value.isStale(value.expiresAt - 1.milliseconds))
        assertTrue(value.isStale(value.expiresAt))
        assertTrue(value.isStale(value.windows.first().resetsAt!!))
        assertEquals(25.5, value.windows.first().usedPercent)
        assertEquals(74.5, value.windows.first().remainingPercent)
        val stale = parseOk(quotaFixture().also { it["freshness"] = "stale" })
        assertTrue(stale.isStale(stale.fetchedAt))
    }

    // ---- B-PROTO-086

    @Test
    fun `B-PROTO-086 the route is explicitly separate from upstream OpenCode APIs`() {
        assertEquals("/ocmn/quota/v1", PROVIDER_QUOTA_PATH)
    }

    @Test
    fun `B-PROTO-086 snapshot is detached from mutable input and its windows are immutable`() {
        val value = quotaFixture()
        val snapshot = parseOk(value)
        value.firstWindow()["usedPercent"] = 99
        value.account()["plan"] = "pro"
        value.windowList().clear()
        assertEquals(2, snapshot.windows.size)
        assertEquals(25.5, snapshot.windows.first().usedPercent)
        assertEquals("plus", snapshot.account.plan)
        assertTrue(snapshot.windows is ImmutableList<*>)
    }

    @Test
    fun `B-PROTO-086 accepts each declared no-measurement status without making up quotas`() {
        for (status in ProviderQuotaStatus.entries) {
            for (accountStatus in QuotaAccountStatus.entries.filter { it != QuotaAccountStatus.SourceBound }) {
                val value = quotaFixture()
                value["status"] = status.wire
                value["freshness"] = "none"
                value["ordinaryUsageAllowed"] = null
                value["windows"] = mutableListOf<Any?>()
                value["account"] =
                    linkedMapOf<String, Any?>("status" to accountStatus.wire).also {
                        if (accountStatus == QuotaAccountStatus.Matched) it["ref"] = "b".repeat(64)
                    }
                val snapshot = parseOk(value)
                assertEquals(status, snapshot.status)
                assertEquals(accountStatus, snapshot.account.status)
                assertTrue(snapshot.windows.isEmpty())
                assertNull(snapshot.ordinaryUsageAllowed)
            }
        }
    }

    @Test
    fun `B-PROTO-086 no error status or unverified or mismatched account may carry measurements`() {
        for (status in ProviderQuotaStatus.entries.filter { it != ProviderQuotaStatus.Ok }) {
            val value = quotaFixture().also { it["status"] = status.wire }
            rejects(value, status.wire)
            value["windows"] = mutableListOf<Any?>()
            rejects(value, status.wire) // ordinaryUsageAllowed is still a measurement
        }
        for (accountStatus in listOf(QuotaAccountStatus.Unverified, QuotaAccountStatus.Mismatch)) {
            val value = quotaFixture()
            value.account()["status"] = accountStatus.wire
            rejects(value)
            value["windows"] = mutableListOf<Any?>()
            rejects(value)
        }
    }

    @Test
    fun `B-PROTO-086 all known plan labels are accepted and arbitrary plan copy is discarded`() {
        for (plan in listOf("free", "go", "plus", "pro", "team", "business", "enterprise", "edu")) {
            val value = quotaFixture()
            value.account()["plan"] = plan
            assertEquals(plan, parseOk(value).account.plan)
        }
        for (plan in listOf(null, 12, emptyMap<String, Any?>(), "unknown-plan", "https://untrusted.example", "fixture-private-copy")) {
            val value = quotaFixture()
            value.account()["plan"] = plan
            assertNull(parseOk(value).account.plan)
        }
    }

    @Test
    fun `B-PROTO-086 rejects wrong version, provider or source, missing keys, and non-objects`() {
        for (value in listOf<Any?>(null, emptyList<Any?>(), "text", 1, true)) rejects(value)
        for (key in listOf("schemaVersion", "provider", "source", "status", "freshness", "fetchedAtMs", "expiresAtMs", "account", "windows")) {
            rejects(quotaFixture().also { it.remove(key) }, key)
        }
        val changes =
            mapOf<String, Any?>(
                "schemaVersion" to 2,
                "provider" to "different-provider",
                "source" to "upstream-opencode",
                "status" to "unknown",
                "freshness" to "unknown",
                "account" to emptyList<Any?>(),
                "windows" to emptyMap<String, Any?>(),
                "ordinaryUsageAllowed" to 1,
            )
        for ((key, change) in changes) rejects(quotaFixture().also { it[key] = change }, key)
    }

    // ---- B-PROTO-087

    @Test
    fun `B-PROTO-087 timestamp bounds and ordering are strict, including window resets`() {
        for (timestamp in listOf<Any>(-1, 8_640_000_000_000_001L, 1.5, "100", Double.NaN, Double.POSITIVE_INFINITY)) {
            for (key in listOf("fetchedAtMs", "expiresAtMs")) rejects(quotaFixture().also { it[key] = timestamp }, "$key=$timestamp")
            val value = quotaFixture()
            value.firstWindow()["resetsAtMs"] = timestamp
            rejects(value, "reset=$timestamp")
        }
        rejects(quotaFixture().also { it["expiresAtMs"] = 999_999 })
        val bounds = quotaFixture(fetchedAtMs = 0).also { it["expiresAtMs"] = 8_640_000_000_000_000L }
        assertEquals(0L, parseOk(bounds).fetchedAt.toEpochMilliseconds())
    }

    @Test
    fun `B-PROTO-087 account references must be opaque lower-case hex and matched needs one`() {
        for (ref in listOf<Any?>(null, "", "a".repeat(63), "a".repeat(65), "A".repeat(64), "g".repeat(64), "person@example.test", 123)) {
            val value = quotaFixture()
            value.account()["ref"] = ref
            rejects(value, "ref")
        }
        val value = quotaFixture()
        value.account()["status"] = "other"
        rejects(value)
    }

    @Test
    fun `B-PROTO-087 rejects non-finite or out-of-range percentages and status contradictions`() {
        for (percent in listOf<Any?>(null, -0.1, 100.1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, "25", true)) {
            val value = quotaFixture()
            value.firstWindow()["usedPercent"] = percent
            rejects(value, "percent=$percent")
        }
        val missing = quotaFixture()
        missing.firstWindow()["status"] = "missing"
        rejects(missing)
        val unknown = quotaFixture()
        unknown.firstWindow()["status"] = "other"
        rejects(unknown)
    }

    @Test
    fun `B-PROTO-087 duration and window identifiers are bounded and unique`() {
        for (duration in listOf<Any>(0, -1, 315_360_001, 1.5, "18000", false)) {
            val value = quotaFixture()
            value.firstWindow()["durationSeconds"] = duration
            rejects(value, "duration=$duration")
        }
        for (id in listOf<Any?>(null, "", "a".repeat(65), "with space", "https://host", "<b>", 1)) {
            val value = quotaFixture()
            value.firstWindow()["id"] = id
            rejects(value, "id=$id")
        }
        val duplicate = quotaFixture()
        duplicate.windowList().add(LinkedHashMap(duplicate.firstWindow()))
        rejects(duplicate)
        rejects(quotaFixture().also { it["windows"] = mutableListOf<Any?>(null) })
        for (count in listOf(64, 65)) {
            val value = quotaFixture()
            value["windows"] = MutableList<Any?>(count) { linkedMapOf("id" to "window/$it", "status" to "missing") }
            if (count == 64) assertEquals(64, parseOk(value).windows.size) else rejects(value)
        }
    }

    @Test
    fun `B-PROTO-087 diagnostic strings and parse failures do not echo input or account refs`() {
        val value = quotaFixture().also { it["ignored"] = "fixture-private-copy" }
        val snapshot = parseOk(value)
        assertEquals("ProviderQuotaSnapshot(ok, 2 windows)", snapshot.toString())
        assertEquals("ProviderQuotaAccount(matched)", snapshot.account.toString())
        for (kind in QuotaFailureKind.entries) {
            assertEquals("ProviderQuotaFailure(${kind.wire})", ProviderQuotaFailure(kind).toString())
        }
        // A parse failure is a plain null: no exception, message or source can carry the input.
        assertNull(parse("fixture-private-copy"))
    }
}
