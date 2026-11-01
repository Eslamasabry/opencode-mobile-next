package io.github.eslamasabry.oc.engine.diagnostics.exit

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.StorageProblem
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private const val MAIN = "private.package"
private const val SECRET = "private-token /private/path https://private.example"

internal class FakeLifecycle(
    var records: List<ExitRecord> = emptyList(),
    override val exitHistorySupported: Boolean = true,
) : LifecycleSource {
    override val mainProcessName: String = MAIN
    var reads = 0
    var launchReads = 0
    var exitsFail: (() -> Outcome<List<ExitRecord>>)? = null
    var launch: () -> Outcome<RawLaunchReport> = { Outcome.Ok(RawLaunchReport()) }

    override suspend fun retainedExits(): Outcome<List<ExitRecord>> {
        reads++
        return exitsFail?.invoke() ?: Outcome.Ok(records)
    }

    override suspend fun launchReport(): Outcome<RawLaunchReport> {
        launchReads++
        return launch()
    }
}

private fun exit(at: Long, reason: Int = ExitReason.CRASH, sub: Int = -1, process: String? = MAIN) =
    ExitRecord(reason = reason, timestampMs = at, subReason = sub, importance = 100, processName = process, description = SECRET)

class ExitHistoryTest {
    @Test
    fun `B-ANDR-003 invalid limits make no request`() =
        runTest {
            val source = FakeLifecycle(records = listOf(exit(1000)))
            val history = PolicyExitHistory(source)
            for (limit in listOf(-1, 0, 51, 1_000_000, Int.MAX_VALUE)) {
                val result = history.history(limit)
                assertEquals(ExitHistoryError.InvalidLimit, result.error)
                assertTrue(result.entries.isEmpty())
            }
            assertEquals(0, source.reads)
        }

    @Test
    fun `B-ANDR-003 supported empty, unsupported and absent are distinct`() =
        runTest {
            val empty = PolicyExitHistory(FakeLifecycle()).history()
            assertTrue(empty.supported)
            assertNull(empty.error)
            assertTrue(empty.entries.isEmpty())

            val unsupportedSource = FakeLifecycle(records = listOf(exit(1000)), exitHistorySupported = false)
            val unsupported = PolicyExitHistory(unsupportedSource).history()
            assertFalse(unsupported.supported)
            assertNull(unsupported.error)
            assertEquals(0, unsupportedSource.reads)

            val absent = PolicyExitHistory(LifecycleSource.Absent).history()
            assertFalse(absent.supported)
            assertNull(absent.error)
        }

    @Test
    fun `B-ANDR-003 read errors are typed without native text`() =
        runTest {
            val source = FakeLifecycle()
            source.exitsFail = { Outcome.Failed(Failure.Storage(StorageProblem.Unreadable)) }
            val failed = PolicyExitHistory(source).history()
            assertTrue(failed.supported)
            assertEquals(ExitHistoryError.Unavailable, failed.error)
            assertTrue(failed.entries.isEmpty())

            source.exitsFail = { throw IllegalStateException(SECRET) }
            val thrown = PolicyExitHistory(source).history()
            assertTrue(thrown.supported)
            assertEquals(ExitHistoryError.Unavailable, thrown.error)
            assertFalse("private" in thrown.toString())
        }

    @Test
    fun `B-ANDR-003 history is newest first, capped, immutable and UTC`() =
        runTest {
            val source = FakeLifecycle(records = listOf(exit(1000), exit(3000), exit(2000)))
            val history = PolicyExitHistory(source).history(limit = 2)
            assertEquals(listOf(3000L, 2000L), history.entries.map { it.at.toEpochMilliseconds() })
            assertEquals(Instant.fromEpochMilliseconds(3000), history.entries.first().at) // Instant is UTC by type
            assertEquals(100, history.entries.first().importance)
            assertFalse(SECRET in history.toString())
        }

    @Test
    fun `B-ANDR-003 default and boundary limits are honoured`() =
        runTest {
            val source = FakeLifecycle(records = (1L..60L).map { exit(it) })
            val history = PolicyExitHistory(source)
            assertEquals(ExitHistory.DEFAULT_LIMIT, history.history().entries.size)
            assertEquals(1, history.history(1).entries.size)
            assertEquals(50, history.history(50).entries.size)
            assertEquals(3, source.reads)
        }

    @Test
    fun `B-ANDR-003 classification reuses recovery including update subreasons`() =
        runTest {
            val cases =
                listOf(
                    Triple(0, -1, ExitKind.Normal), Triple(1, -1, ExitKind.Normal),
                    Triple(16, -1, ExitKind.Update), Triple(15, -1, ExitKind.Update), Triple(10, 25, ExitKind.Update),
                    Triple(10, 21, ExitKind.ForceStop), Triple(11, -1, ExitKind.ForceStop), Triple(3, -1, ExitKind.LowMemory),
                    Triple(4, -1, ExitKind.Crash), Triple(5, -1, ExitKind.Crash), Triple(6, -1, ExitKind.Crash),
                    Triple(7, -1, ExitKind.Crash), Triple(2, 6, ExitKind.LowMemory), Triple(13, 22, ExitKind.ForceStop),
                    Triple(13, 25, ExitKind.Update), Triple(9, -1, ExitKind.Killed),
                )
            for ((reason, sub, expected) in cases) {
                val source = FakeLifecycle(records = listOf(exit(1000, reason, sub)))
                val entry = PolicyExitHistory(source).history().entries.single()
                assertEquals(expected, entry.kind, "$reason/$sub")
                assertEquals(expected, ExitClassifier.classify(reason, sub), "$reason/$sub")
            }
        }

    @Test
    fun `B-ANDR-003 malformed metadata is never an empty success`() =
        runTest {
            for (bad in listOf(listOf(exit(0)), listOf(exit(-1)), listOf(exit(8_640_000_000_000_001L)))) {
                val history = PolicyExitHistory(FakeLifecycle(records = bad)).history()
                assertTrue(history.entries.isEmpty())
                assertEquals(ExitHistoryError.Unavailable, history.error)
            }
        }

    @Test
    fun `B-ANDR-003 native policy keeps the main process only and never consumes records`() =
        runTest {
            val source =
                FakeLifecycle(
                    records =
                        listOf(
                            exit(100, reason = 1), exit(999, process = "$MAIN:worker"), exit(998, process = null),
                            exit(997, process = "other.package"), exit(300, reason = 3), exit(200, reason = 10), exit(0),
                        ),
                )
            val history = PolicyExitHistory(source)
            val first = history.history(2)
            val second = history.history(2)
            assertEquals(first, second)
            assertEquals(listOf(300L, 200L), first.entries.map { it.at.toEpochMilliseconds() })
            assertEquals(listOf(3, 10), first.entries.map { it.reason })
            assertNull(first.error)
            assertFalse(MAIN in first.toString())
        }

    @Test
    fun `B-ANDR-003 history reads leave the startup report independent`() =
        runTest {
            val source = FakeLifecycle(records = listOf(exit(1000)))
            source.launch = { Outcome.Ok(RawLaunchReport(exit = exit(1000), previousServices = listOf("aiteam", "", null))) }
            val history = PolicyExitHistory(source)
            assertEquals(1, history.history().entries.size)
            assertEquals(1, history.history().entries.size)
            val startup = history.launchReport()
            assertEquals(ExitReason.CRASH, startup.exit?.reason)
            assertEquals(listOf("aiteam"), startup.previousServices)
            assertEquals(2, source.reads)
            assertEquals(1, source.launchReads)
        }

    @Test
    fun `B-ANDR-006 a missing or failing lifecycle bridge answers safely with no exit`() =
        runTest {
            assertEquals(LaunchReport.Empty, PolicyExitHistory(LifecycleSource.Absent).launchReport())
            val source = FakeLifecycle()
            source.launch = { Outcome.Failed(Failure.Device()) }
            assertNull(PolicyExitHistory(source).launchReport().exit)
            source.launch = { throw IllegalStateException(SECRET) }
            val thrown = PolicyExitHistory(source).launchReport()
            assertNull(thrown.exit)
            assertTrue(thrown.previousServices.isEmpty())
            assertNull(thrown.lastCrash)
        }

    @Test
    fun `B-ANDR-006 the launch report reads the exit and what was running`() =
        runTest {
            val owner = ExitRecord(reason = 10, timestampMs = 1_790_380_793_000L, subReason = 21, importance = 125)
            val source = FakeLifecycle()
            source.launch = { Outcome.Ok(RawLaunchReport(exit = owner, previousServices = listOf("server", "aiteam"))) }
            val report = PolicyExitHistory(source).launchReport()
            assertEquals(ExitKind.ForceStop, report.exit?.kind)
            assertEquals(listOf("server", "aiteam"), report.previousServices)
        }
}
