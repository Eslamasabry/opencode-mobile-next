package io.github.eslamasabry.oc.engine.diagnostics.crash

import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.engine.diagnostics.CrashCategory
import io.github.eslamasabry.oc.engine.diagnostics.Diagnostics
import io.github.eslamasabry.oc.engine.diagnostics.exit.ExitReason
import io.github.eslamasabry.oc.engine.diagnostics.exit.ExitRecord
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class CrashDiagnosticsTest {
    private val clock = TestClock()
    private val diagnostics = Diagnostics(Redactor(), clock)
    private val now get() = clock.now().toEpochMilliseconds()

    private suspend fun TestScope.open(store: FakeCrashStore) = CrashDiagnostics.open(store, diagnostics, clock, backgroundScope)

    @Test
    fun `B-SEC-045 without consent every evidence file is erased at open`() =
        runTest {
            val store =
                FakeCrashStore(
                    CrashFile.Consent to "${now + 60_000}", // a future consent is no consent
                    CrashFile.Records to "[]",
                    CrashFile.Pending to "x",
                    CrashFile.NativeLastCrash to "class=java.lang.IllegalStateException",
                )
            val controller = open(store)
            assertFalse(controller.enabled)
            assertTrue(store.files.isEmpty())
            controller.capture(IllegalStateException("x"))
            assertEquals(0, controller.savedCount)
            assertTrue(store.files.isEmpty())
        }

    @Test
    fun `B-SEC-045 a corrupt, foreign or oversize record file is dropped and never restored`() =
        runTest {
            val since = now - 1_000
            for (bad in listOf(
                "not json",
                """[{"source":"native","category":"Native application error"}]""",
                """[{"source":"native","category":"Native application error","time":"$now"}]""",
                """[{"source":"elsewhere","category":"Native application error","time":$now}]""",
                """[{"source":"native","category":"secret text","time":$now}]""",
                """[{"source":"native","category":"Native application error","time":${since - 1}}]""",
                """[{"source":"native","category":"Native application error","time":$now,"extra":1}]""",
                "[" + List(21) { """{"source":"anr","category":"Native application error","time":$now}""" }.joinToString(",") + "]",
                "x".repeat(CrashDiagnostics.MAX_BYTES + 1),
            )) {
                val store = FakeCrashStore(CrashFile.Consent to "$since", CrashFile.Records to bad)
                val controller = open(store)
                assertTrue(controller.enabled)
                assertEquals(0, controller.savedCount, bad)
                assertNull(store.files[CrashFile.Records], bad)
            }
        }

    @Test
    fun `B-SEC-045 a Flutter-era record file is restored as written`() =
        runTest {
            val since = now - 1_000
            val text = """[{"source":"flutter","category":"Invalid state","time":${now - 10}},{"source":"anr","category":"Android reported that the app stopped responding","time":$now}]"""
            val store = FakeCrashStore(CrashFile.Consent to "$since", CrashFile.Records to text, CrashFile.Pending to "half")
            val controller = open(store)
            assertEquals(2, controller.savedCount)
            assertNull(store.files[CrashFile.Pending])
            val records = controller.state.value.records
            assertEquals(listOf(CrashSource.Anr, CrashSource.Flutter), records.map { it.source }) // newest first
            assertEquals(listOf(null, CrashCategory.Kotlin), records.map { it.crashCategory })
            assertTrue(diagnostics.isEmpty) // opening never writes to the ring
        }

    @Test
    fun `B-SEC-045 consent on, off and clear keep the epoch and erase evidence`() =
        runTest {
            val store = FakeCrashStore()
            val controller = open(store)
            assertTrue(controller.setEnabled(true))
            assertEquals("$now", store.files[CrashFile.Consent])
            controller.capture(IllegalArgumentException("synthetic-provider-secret"))
            assertEquals(1, controller.savedCount)
            assertEquals("crash.platform", diagnostics.errors.value.single().source)
            assertEquals("Invalid argument", diagnostics.errors.value.single().message)
            val epoch = controller.state.value.consentEpoch
            assertTrue(controller.clear())
            assertEquals(0, controller.savedCount)
            assertTrue(controller.enabled)
            assertTrue(diagnostics.isEmpty)
            assertEquals(epoch + 1, controller.state.value.consentEpoch)
            assertTrue(controller.setEnabled(false))
            assertFalse(controller.enabled)
            assertEquals(emptySet(), store.files.keys)
        }

    @Test
    fun `B-SEC-045 a refused write is storageFailed, never saved`() =
        runTest {
            val store = FakeCrashStore(CrashFile.Consent to "${now - 1_000}")
            val controller = open(store)
            store.refuseWrites = true
            controller.capture(IllegalStateException())
            assertEquals(0, controller.savedCount)
            assertTrue(controller.state.value.storageFailed)
            assertTrue(controller.state.value.records.isEmpty())
            assertFalse(controller.setEnabled(true))
            assertFalse(controller.enabled)
        }

    @Test
    fun `B-SEC-045 records stay within 20 entries and 8 KiB, oldest dropped`() =
        runTest {
            val store = FakeCrashStore(CrashFile.Consent to "${now - 1_000}")
            val controller = open(store)
            repeat(30) {
                clock.advance(1.seconds)
                controller.capture(IllegalStateException())
            }
            assertEquals(CrashDiagnostics.MAX_ENTRIES, controller.savedCount)
            assertTrue(store.files[CrashFile.Records]!!.encodeToByteArray().size <= CrashDiagnostics.MAX_BYTES)
            assertEquals(now, controller.state.value.records.first().at.toEpochMilliseconds())
        }

    @Test
    fun `B-SEC-045 imports keep native and ANR exits since consent, category only, once`() =
        runTest {
            val since = now - 1_000
            val store = FakeCrashStore(CrashFile.Consent to "$since")
            val controller = open(store)
            val exits =
                listOf(
                    ExitRecord(reason = ExitReason.CRASH_NATIVE, timestampMs = since - 1, description = "before consent"),
                    ExitRecord(reason = ExitReason.CRASH_NATIVE, timestampMs = now - 10, description = "synthetic-provider-secret"),
                    ExitRecord(reason = ExitReason.ANR, timestampMs = now),
                    ExitRecord(reason = ExitReason.LOW_MEMORY, timestampMs = now),
                    ExitRecord(reason = ExitReason.ANR, timestampMs = now + 5_000), // from the future
                )
            controller.importAtStart(null, exits)
            controller.importAtStart(null, exits)
            assertEquals(
                listOf(CrashSource.Native to CrashRecordCategory.NativeApplicationError, CrashSource.Anr to CrashRecordCategory.NotResponding),
                controller.storedRecords().map { it.source to it.category },
            )
            assertFalse("synthetic-provider-secret" in store.files[CrashFile.Records]!!)
            assertTrue(diagnostics.isEmpty)
        }

    @Test
    fun `B-SEC-045 clearing the diagnostics ring erases restored crash evidence`() =
        runTest {
            val store = FakeCrashStore(CrashFile.Consent to "${now - 1_000}")
            val controller = open(store)
            controller.capture(IllegalStateException())
            assertEquals(1, controller.savedCount)
            diagnostics.clear()
            runCurrent()
            assertEquals(0, controller.savedCount)
            assertTrue(controller.enabled)
            controller.close()
            assertFalse(controller.state.value.available)
            assertFalse(controller.clear())
        }
}
