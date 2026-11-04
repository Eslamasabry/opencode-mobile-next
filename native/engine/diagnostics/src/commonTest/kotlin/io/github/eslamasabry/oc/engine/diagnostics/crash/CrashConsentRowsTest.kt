package io.github.eslamasabry.oc.engine.diagnostics.crash

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.core.testing.TestDispatchers
import io.github.eslamasabry.oc.engine.diagnostics.Diagnostics
import io.github.eslamasabry.oc.engine.diagnostics.exit.ExitReason
import io.github.eslamasabry.oc.engine.diagnostics.exit.ExitRecord
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.ImmutableList
import kotlin.test.assertIs
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/** BUILD-DIAGNOSTICS-ATTENTION: the Flutter crash_diagnostics_test facts on the native crash engine. */
@OptIn(ExperimentalCoroutinesApi::class)
class CrashConsentRowsTest {
    private val clock = TestClock()
    private var diagnostics = Diagnostics(Redactor(), clock)
    private val now get() = clock.now().toEpochMilliseconds()

    private suspend fun TestScope.open(store: FakeCrashStore) = CrashDiagnostics.open(store, diagnostics, clock, backgroundScope)

    private fun FakeCrashStore.savedText() = files.values.joinToString("\n")

    private class HostileError : RuntimeException() {
        override val message: String get() = throw IllegalStateException("never read the message")

        override fun toString(): String = throw IllegalStateException("never serialize arbitrary errors")
    }

    @Test
    fun `B-SEC-042 capture defaults off and a legacy native record is discarded at open`() =
        runTest {
            val store = FakeCrashStore(CrashFile.NativeLastCrash to "legacy")
            val capture = open(store)
            capture.capture(IllegalStateException("private"), CrashSource.Flutter)
            assertFalse(capture.enabled)
            assertTrue(store.files.isEmpty())
            assertTrue(diagnostics.isEmpty)
        }

    @Test
    fun `B-SEC-042 reapplying enabled consent preserves saved evidence`() =
        runTest {
            val store = FakeCrashStore()
            val capture = open(store)
            assertTrue(capture.setEnabled(true))
            capture.capture(IllegalStateException("private"), CrashSource.Flutter)
            val before = store.files.toMap()
            assertTrue(capture.setEnabled(true))
            assertEquals(1, capture.savedCount)
            assertEquals(before, store.files)
        }

    @Test
    fun `B-SEC-042 disable erases Flutter and native evidence and consent across reopen`() =
        runTest {
            val store = FakeCrashStore()
            val capture = open(store)
            capture.setEnabled(true)
            capture.capture(IllegalStateException("private"), CrashSource.Flutter)
            store.files[CrashFile.NativeLastCrash] = "synthetic native"
            assertTrue(capture.setEnabled(false))
            assertTrue(store.files.isEmpty())
            assertTrue(diagnostics.isEmpty)
            capture.close()
            val reopened = open(store)
            assertFalse(reopened.enabled)
            assertEquals(0, reopened.savedCount)
        }

    @Test
    fun `B-SEC-042 App diagnostics clear removes native and saved crash evidence but keeps consent`() =
        runTest {
            val store = FakeCrashStore()
            val capture = open(store)
            capture.setEnabled(true)
            capture.capture(IllegalStateException("private"), CrashSource.Flutter)
            store.files[CrashFile.NativeLastCrash] = "synthetic native"
            diagnostics.clear()
            runCurrent()
            assertTrue(capture.enabled)
            assertEquals(0, capture.savedCount)
            assertFalse(CrashFile.Records in store.files)
            assertFalse(CrashFile.NativeLastCrash in store.files)
            assertTrue(CrashFile.Consent in store.files)
        }

    @Test
    fun `B-SEC-043 provider keys and exception or stack values never reach disk and hostile toString is not called`() =
        runTest {
            // Deliberately unregistered short key: regex redaction cannot identify it.
            val providerKey = "oak-cloud"
            val patternedKey = "sk-proj-fakeOnly01234567890123456789"
            val store = FakeCrashStore()
            val capture = open(store)
            assertTrue(capture.setEnabled(true))
            capture.capture(IllegalStateException("$providerKey $patternedKey"), CrashSource.Flutter)
            assertFalse(providerKey in store.savedText())
            assertFalse(patternedKey in store.savedText())
            assertTrue("Invalid state" in store.savedText())
            assertFalse(providerKey in diagnostics.reportText())
            assertEquals("", diagnostics.errors.value.single().stack)
            clock.advance(1.milliseconds)
            capture.capture(HostileError(), CrashSource.Platform) // returns normally
            assertTrue("Application error" in store.savedText())
        }

    @Test
    fun `B-SEC-043 records list saved categories newest first without values and are read-only`() =
        runTest {
            val store = FakeCrashStore(CrashFile.Consent to "${now - 1_000}")
            val capture = open(store)
            val anr = now
            capture.importAtStart(null, listOf(ExitRecord(reason = ExitReason.ANR, timestampMs = anr)))
            clock.advance(5.milliseconds)
            capture.capture(IllegalArgumentException("private-value"), CrashSource.Widget)
            val records = capture.state.value.records
            assertEquals(2, records.size)
            assertEquals(CrashSource.Widget, records.first().source)
            assertEquals("Invalid argument", records.first().category.wire)
            assertEquals(CrashSource.Anr, records.last().source)
            assertEquals(anr, records.last().at.toEpochMilliseconds())
            assertFalse("private-value" in records.toString())
            // A read-only copy: it cannot be changed through the list type.
            assertIs<ImmutableList<CrashRecord>>(records)
            assertFalse(records is MutableList<*>)
            assertEquals(2, capture.savedCount)
            capture.clear()
            assertTrue(capture.state.value.records.isEmpty())
        }

    @Test
    fun `B-SEC-043 corrupt restored categories fail closed without entering diagnostics`() =
        runTest {
            val store =
                FakeCrashStore(
                    CrashFile.Consent to "${now - 1_000}",
                    CrashFile.Records to """[{"source":"flutter","category":"oak-cloud","time":$now}]""",
                )
            val capture = open(store)
            assertEquals(0, capture.savedCount)
            assertFalse(CrashFile.Records in store.files)
            assertTrue(diagnostics.isEmpty)
        }

    @Test
    fun `B-SEC-043 storage failure never throws into error handling and refuses consent`() =
        runTest {
            val store = FakeCrashStore()
            val capture = open(store)
            capture.setEnabled(true)
            store.refuseWrites = true
            capture.capture(IllegalStateException("private"), CrashSource.Flutter) // returns normally
            assertTrue(capture.state.value.storageFailed)
            assertFalse(capture.setEnabled(true))
            assertFalse(capture.enabled)
        }

    @Test
    fun `B-SEC-043 a refused pending snapshot cannot redirect private evidence elsewhere`() =
        runTest {
            // The platform store refuses to follow a link at the pending path (android NativeCrashStore proves the
            // symlink case); here: a refused write leaves the other file untouched and fails closed.
            val store = FakeCrashStore()
            val capture = open(store)
            capture.setEnabled(true)
            store.files[CrashFile.NativeLastCrash] = "unchanged"
            store.refuseWrites = true
            capture.capture(IllegalStateException("private"), CrashSource.Flutter)
            assertTrue(capture.state.value.storageFailed)
            assertEquals("unchanged", store.files[CrashFile.NativeLastCrash])
            assertFalse(CrashFile.Records in store.files)
        }

    @Test
    fun `B-SEC-043 hooks delegate only categories, never keys`() =
        runTest {
            val key = "oak-cloud"
            val store = FakeCrashStore()
            val capture = open(store)
            capture.setEnabled(true)
            capture.capture(IllegalStateException(key), CrashSource.Flutter)
            clock.advance(1.milliseconds)
            capture.capture(IllegalStateException(key), CrashSource.Platform)
            assertEquals(listOf("Invalid state", "Invalid state"), diagnostics.errors.value.map { it.message })
            assertFalse(key in store.savedText())
            assertFalse(key in diagnostics.reportText())
        }

    @Test
    fun `B-SEC-044 a saved error survives reopen and imports into App diagnostics`() =
        runTest {
            val store = FakeCrashStore()
            val first = open(store)
            first.setEnabled(true)
            first.capture(IllegalStateException("private"), CrashSource.Flutter)
            first.close()
            diagnostics = Diagnostics(Redactor(), clock)
            val startup =
                CrashStartup(
                    { Outcome.Ok(CrashStartupEvidence(store)) },
                    diagnostics, clock, backgroundScope, TestDispatchers(StandardTestDispatcher(testScheduler)),
                )
            val capture = assertNotNull(startup.start().await())
            runCurrent()
            assertTrue(capture.enabled)
            assertEquals(1, capture.savedCount)
            assertEquals("Invalid state", diagnostics.errors.value.single().message)
            assertEquals("crash.flutter", diagnostics.errors.value.single().source)
        }

    @Test
    fun `B-SEC-044 ANR and native imports accept only times since consent and de-duplicate`() =
        runTest {
            val store = FakeCrashStore(CrashFile.Consent to "${now - 1_000}")
            val capture = open(store)
            capture.importAtStart(
                null,
                listOf(ExitRecord(reason = ExitReason.ANR, timestampMs = now - 2_000), ExitRecord(reason = ExitReason.ANR, timestampMs = now + 100_000)),
            )
            assertEquals(0, capture.savedCount)
            val anr = ExitRecord(reason = ExitReason.ANR, timestampMs = now - 10)
            capture.importAtStart(null, listOf(anr, anr))
            capture.importAtStart(
                null,
                listOf(ExitRecord(reason = ExitReason.CRASH_NATIVE, timestampMs = now - 5, description = "oak-cloud")),
            )
            assertEquals(2, capture.savedCount)
            assertFalse("oak-cloud" in store.savedText())
            assertTrue("stopped responding" in store.savedText())
            capture.clear()
            capture.importAtStart(null, listOf(anr))
            assertEquals(0, capture.savedCount)
        }

    @Test
    fun `B-SEC-044 the ring and snapshot byte budget stay bounded`() =
        runTest {
            val store = FakeCrashStore(CrashFile.Consent to "${now - 1_000}")
            val capture = open(store)
            capture.importAtStart(null, (0 until 45).map { ExitRecord(reason = ExitReason.ANR, timestampMs = now - 500 + it) })
            assertEquals(CrashDiagnostics.MAX_ENTRIES, capture.savedCount)
            assertTrue(store.files[CrashFile.Records]!!.encodeToByteArray().size <= CrashDiagnostics.MAX_BYTES)
            assertFalse(CrashFile.Pending in store.files)
        }
}
