package io.github.eslamasabry.oc.engine.schedules

import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * E00-S05: failed-run receipts the Flutter worker left (`oc.scheduleReceipts.<profile>`, ScheduledPromptReceipts.kt)
 * show in the schedule's history on native, as Flutter's list showed them (controller.dart `_mergeReceipts`).
 */
class ScheduleReceiptsTest {
    private val h = Harness()
    private val due = T0 + HOUR // 2026-11-02T09:00:00Z
    private val day = 86_400_000L

    /** Flutter's envelope: schedule a1 due at 09:00 and the run its Dart side had started when the worker died. */
    private val envelope =
        """{"schemaVersion":1,"schedules":[{"id":"a1","profileId":"p1","profileFingerprint":"n1:server-a",""" +
            """"prompt":"Check the nightly build","projectPath":"/work/app","workspace":null,"sessionId":null,"agent":null,""" +
            """"modelProviderId":null,"modelId":null,"recurrence":{"kind":"daily","at":null,"hour":9,"minute":0,"hours":1},""" +
            """"missedPolicy":"catchUp","enabled":true,"nextRunAt":"2026-11-02T09:00:00.000Z","createdAt":"2026-11-02T08:00:00.000Z",""" +
            """"revision":1}],"runs":[{"id":"a1:1:1793610000000","scheduleId":"a1","scheduledAt":"2026-11-02T09:00:00.000Z",""" +
            """"startedAt":"2026-11-02T09:00:01.000Z","status":"running","late":false,"finishedAt":null,"sessionId":"ses_1",""" +
            """"errorCode":null}],"digest":{"enabled":false,"hour":8,"minute":0,"lastDeliveredAt":null}}"""

    /** stageFailedReceipt's JSON (org.json key order) for that occurrence, finished 8 minutes later. */
    private fun receipt(id: String = "a1:1:1793610000000", scheduleId: String = "a1", error: String = "timeout") =
        """{"id":"$id","scheduleId":"$scheduleId","revision":1,"scheduledAtMs":$due,"finishedAtMs":${due + 8 * MIN},""" +
            """"status":"failed","error":"$error"}"""

    private fun install(vararg receipts: String) {
        h.backend.values["oc.scheduledPrompts.p1"] = envelope
        h.backend.values["oc.scheduleReceipts.p1"] = receipts.joinToString(",", "[", "]")
        h.clock.now = due + 10 * MIN
    }

    @Test
    fun `B-MIGR-013 a failed run Flutter left as a receipt shows in the history and the schedule moves on`() = runTest {
        install(receipt())
        assertIs<Outcome.Ok<*>>(h.engine.load(P))
        val run = h.engine.state(P).value.runs.single()
        assertEquals(RunStatus.Failed, run.status)
        assertEquals("timeout", run.errorCode)
        assertEquals(due + 1_000, run.startedAtMs) // the started record is closed, not replaced
        assertEquals("ses_1", run.sessionId)
        assertEquals(due + 8 * MIN, run.finishedAtMs)
        assertEquals(due + day, h.engine.store.load(P).single().nextRunAtMs)
        assertEquals("[]", h.backend.values["oc.scheduleReceipts.p1"]) // acknowledged, in the form Flutter reads
        Flutter214Schedules.receipts(h.backend.values.getValue("oc.scheduleReceipts.p1"))
    }

    @Test
    fun `B-MIGR-013 an unknown receipt error reads as interrupted and a removed schedule's receipt is only acknowledged`() = runTest {
        install(receipt(error = "worker_crashed"), receipt(id = "__digest:0:$due", scheduleId = "__digest"))
        h.engine.load(P)
        assertEquals("interrupted", h.engine.state(P).value.runs.single().errorCode)
        assertEquals("[]", h.backend.values["oc.scheduleReceipts.p1"])
    }

    @Test
    fun `B-MIGR-013 a run already recorded as finished is kept as it is`() = runTest {
        install(receipt())
        h.backend.values["oc.scheduledPrompts.p1"] =
            envelope.replace(""""status":"running","late":false,"finishedAt":null""", """"status":"ran","late":false,"finishedAt":"2026-11-02T09:00:05.000Z"""")
        h.engine.load(P)
        assertEquals(RunStatus.Ran, h.engine.state(P).value.runs.single().status)
        assertEquals("[]", h.backend.values["oc.scheduleReceipts.p1"])
    }

    @Test
    fun `B-MIGR-013 a malformed receipt is reported as unreadable storage, as in Flutter`() = runTest {
        install("""{"id":"a1:1:1","scheduleId":"a1"}""")
        assertIs<Outcome.Failed>(h.engine.load(P))
        assertEquals(ScheduleCode.StorageCorrupt, h.engine.state(P).value.error)
        assertTrue(h.backend.values.getValue("oc.scheduleReceipts.p1").contains("a1:1:1"))
    }
}
