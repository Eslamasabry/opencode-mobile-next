package io.github.eslamasabry.oc.engine.team.fleet

import io.github.eslamasabry.oc.engine.team.TeamConfig
import io.github.eslamasabry.oc.engine.team.TeamProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

private object MergeClock : Clock {
    override fun now(): Instant = Instant.parse("2026-11-11T10:00:00Z")
}

private val MERGE_CONFIG = TeamConfig(TeamProvider.GasCity, url = "http://100.101.102.103:8372", city = "main")
private const val MERGE_FRONT_DOC = """{"provider":"gascity","front":true,"capabilities":{"control":true},"identity":{"allowed":true}}"""
private const val MERGE_READY_DOC =
    """{"ready":true,"rig":"app","targetBranch":"main","files":2,"additions":10,"deletions":3,
    "lines":[{"key":"tests","ok":true},{"key":"review","ok":false,"detail":"1 open comment","pending":true}],
    "changes":[{"path":"a.kt","additions":"7","deletions":1}],
    "boundaries":[{"key":"require_approval","satisfied":false,"text":"Never merge without approval"}],
    "mergeRequest":{"id":"mr-1","title":"Login","approvedBy":"","approvedAt":"2026-11-11T09:00:00Z"},
    "branches":["feature/login"]}"""

/** The host front's merge roles (Dart `GasCityControl`): readiness, approve, merge. */
class FleetMergeRouteTest {
    private fun front(): FakeGasCity = FakeGasCity().also { it.route(FleetProbe.WELL_KNOWN, MERGE_FRONT_DOC) }

    @Test
    fun merge_readiness_reads_the_lines_the_boundaries_and_the_request() =
        runTest {
            val host = front()
            host.route("${FakeGasCity.CITY}/front/merge-readiness/r%201", MERGE_READY_DOC)
            val fleet = FleetEngine(host, MergeClock, newRequestId = { "req-1" })
            backgroundScope.launch { fleet.live("p1", MERGE_CONFIG) }
            runCurrent()
            val read = fleet.mergeReadiness("p1", "r 1") as FleetEngine.MergeRead.Found
            val r = read.readiness
            assertEquals("r 1", r.runId)
            assertTrue(r.ready)
            assertEquals("main", r.targetBranch)
            assertEquals(MergeLine("review", false, "1 open comment", true), r.firstMissing)
            assertEquals(MergeChange("a.kt", 7, 1), r.changes.single())
            assertEquals("Never merge without approval", r.firstBlocking?.text)
            // Ready is the host's word; a blocking boundary still turns Merge off.
            assertTrue(!r.canMerge)
            assertEquals("mr-1", r.mergeRequest?.id)
            assertTrue(r.mergeRequest?.isApproved == false)
            assertTrue(!r.alreadyMerged)
        }

    @Test
    fun an_unknown_run_or_a_host_without_merge_roles_reads_as_no_roles_and_a_dead_host_as_failed() =
        runTest {
            val host = front()
            val fleet = FleetEngine(host, MergeClock, newRequestId = { "req-1" })
            backgroundScope.launch { fleet.live("p1", MERGE_CONFIG) }
            runCurrent()
            assertEquals(FleetEngine.MergeRead.NoRoles, fleet.mergeReadiness("p1", "nope"))
            host.down = true
            assertEquals(FleetEngine.MergeRead.Failed, fleet.mergeReadiness("p1", "r-1"))
        }

    @Test
    fun without_a_front_there_are_no_merge_roles_and_a_merge_is_refused_without_a_request() =
        runTest {
            val host = FakeGasCity()
            val fleet = FleetEngine(host, MergeClock, newRequestId = { "req-1" })
            backgroundScope.launch { fleet.live("p1", MERGE_CONFIG) }
            runCurrent()
            assertEquals(FleetEngine.MergeRead.NoRoles, fleet.mergeReadiness("p1", "r-1"))
            val result = fleet.merge("p1", "r-1")
            assertEquals(ReceiptStatus.Rejected, result.receipt.status)
            assertEquals(FleetReceipt.FRONT_REQUIRED, result.receipt.message)
            assertTrue(host.sends.isEmpty())
        }

    @Test
    fun approving_and_merging_post_to_the_fronts_routes_with_an_idempotency_key() =
        runTest {
            val host = front()
            val fleet = FleetEngine(host, MergeClock, newRequestId = { "req-7" })
            backgroundScope.launch { fleet.live("p1", MERGE_CONFIG) }
            runCurrent()
            host.sendReply = FleetReply.Answered(200, """{"mergeCommit":"4f9c2a1deadbeef","branch":"main"}""")
            val approved = fleet.approveMerge("p1", "mr 1")
            val merged = fleet.merge("p1", "r-1")
            assertEquals(Triple("POST", "${FakeGasCity.CITY}/front/mr/mr%201/approve", "req-7"), host.sends[0])
            assertEquals(Triple("POST", "${FakeGasCity.CITY}/front/merge/r-1", "req-7"), host.sends[1])
            assertEquals(ReceiptStatus.Accepted, approved.receipt.status)
            assertEquals("4f9c2a1deadbeef", merged.mergeCommit)
            assertEquals("main", merged.branch)
            assertNull(merged.boundary)
            assertEquals(ReceiptStatus.Accepted, fleet.state("p1").value.receipts["req-7"]?.status)
        }

    @Test
    fun a_merge_refused_on_a_boundary_carries_the_boundary_text_apart_from_the_receipt() =
        runTest {
            val host = front()
            val fleet = FleetEngine(host, MergeClock, newRequestId = { "req-1" })
            backgroundScope.launch { fleet.live("p1", MERGE_CONFIG) }
            runCurrent()
            host.sendReply =
                FleetReply.Answered(
                    403,
                    """{"type":"urn:opencode-mobile:front:boundary","code":"boundary","status":403,"title":"Forbidden","detail":"Never merge without approval"}""",
                )
            val result = fleet.merge("p1", "r-1")
            assertEquals(ReceiptStatus.Rejected, result.receipt.status)
            assertEquals("Never merge without approval", result.boundary)
            assertNull(result.mergeCommit)
        }

    @Test
    fun a_merge_that_got_no_answer_may_have_landed_and_is_never_sent_again() =
        runTest {
            val host = front()
            val fleet = FleetEngine(host, MergeClock, newRequestId = { "req-1" })
            backgroundScope.launch { fleet.live("p1", MERGE_CONFIG) }
            runCurrent()
            host.sendReply = FleetReply.Unreachable(timedOut = true)
            val result = fleet.merge("p1", "r-1")
            assertEquals(ReceiptStatus.Pending, result.receipt.status)
            assertEquals(1, host.sends.size)
        }
}
