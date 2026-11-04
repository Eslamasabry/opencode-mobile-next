package io.github.eslamasabry.oc.engine.diagnostics.crashreport

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.engine.diagnostics.Diagnostics
import io.github.eslamasabry.oc.engine.diagnostics.crash.CrashDiagnostics
import io.github.eslamasabry.oc.engine.diagnostics.crash.CrashFile
import io.github.eslamasabry.oc.engine.diagnostics.crash.CrashSource
import io.github.eslamasabry.oc.engine.diagnostics.crash.CrashStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.ImmutableList
import kotlin.test.assertIs
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/** An in-memory crash directory (the crash package's fake is internal to its own tests). */
internal class MemoryCrashStore(vararg initial: Pair<CrashFile, String>) : CrashStore {
    val files: MutableMap<CrashFile, String> = mutableMapOf(*initial)
    var refuseWrites = false

    override suspend fun read(file: CrashFile, maxBytes: Int): Outcome<String?> = Outcome.Ok(files[file])

    override suspend fun write(file: CrashFile, text: String): Outcome<Unit> {
        if (refuseWrites) return Outcome.Failed(Failure.StorageRefused())
        files[file] = text
        return Outcome.Ok(Unit)
    }

    override suspend fun delete(file: CrashFile): Outcome<Unit> {
        files.remove(file)
        return Outcome.Ok(Unit)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CrashReportBuilderTest {
    private val clock = TestClock()
    private val diagnostics = Diagnostics(Redactor(), clock)
    private val disk = MemoryCrashStore()
    private lateinit var store: CrashDiagnostics
    private val shared = mutableListOf<String>()
    private val sheet = CrashShareSheet { shared += it; true }
    private val now get() = clock.now().toEpochMilliseconds()

    private suspend fun TestScope.openStore(): CrashDiagnostics =
        CrashDiagnostics.open(disk, diagnostics, clock, backgroundScope).also { store = it }

    private fun builder(sheet: CrashShareSheet? = this.sheet) = CrashReportBuilder({ store }, sheet)

    private suspend fun capture() {
        assertTrue(store.setEnabled(true))
        store.capture(IllegalStateException("private-provider-key https://private-host/secret"), CrashSource.Flutter)
        clock.advance(1.milliseconds)
    }

    private suspend fun CrashReportBuilder.issued(): CrashReportPreview {
        val result = preview()
        assertNull(result.error)
        return assertNotNull(result.preview)
    }

    @Test
    fun `B-SEC-042 capture stays off and a broad diagnostics entry is not crash evidence`() =
        runTest {
            openStore()
            diagnostics.record("app", "private-provider-key")
            val result = builder().preview()
            assertEquals(DiagnosticsError.CaptureDisabled, result.error)
            assertNull(result.preview)
            assertFalse(store.enabled)
            assertEquals(0, store.savedCount)
            assertTrue(shared.isEmpty())
            assertTrue(disk.files.isEmpty())
        }

    @Test
    fun `B-SEC-046 enabled capture without a saved record returns noCapture`() =
        runTest {
            openStore()
            store.setEnabled(true)
            diagnostics.record("app", "private-provider-key")
            val result = builder().preview()
            assertEquals(DiagnosticsError.NoCapture, result.error)
            assertNull(result.preview)
            assertTrue(shared.isEmpty())
        }

    @Test
    fun `B-SEC-047 the preview holds validated evidence only and never writes or shares`() =
        runTest {
            openStore()
            capture()
            store.capture(RuntimeException("private-provider-key"), CrashSource.Platform)
            diagnostics.record("unrelated-private-profile", "unrelated-conversation-secret", "unrelated-private-stack")
            val before = disk.files.toMap()
            val report = builder().issued()
            assertEquals(2, report.recordCount)
            assertTrue("flutter | Invalid state" in report.text)
            assertTrue("platform | Application error" in report.text)
            assertTrue(store.state.value.records.first().at.toString() in report.text)
            for (secret in listOf("private-provider-key", "private-host", "/secret", "unrelated-private", "conversation-secret")) {
                assertFalse(secret in report.text, secret)
            }
            assertEquals(report.text.encodeToByteArray().size, report.byteCount)
            assertEquals(before, disk.files)
            assertTrue(shared.isEmpty())
            assertTrue(store.enabled)
        }

    @Test
    fun `B-SEC-048 a safe snapshot is immutable and stays a point-in-time record`() =
        runTest {
            openStore()
            capture()
            val snapshot = store.state.value
            assertTrue(snapshot.available)
            assertTrue(snapshot.records.single().at.toString().endsWith("Z")) // UTC
            assertIs<ImmutableList<*>>(snapshot.records)
            assertFalse(snapshot.records is MutableList<*>)
            store.capture(IllegalArgumentException("private"), CrashSource.Platform)
            assertEquals(1, snapshot.records.size)
            assertEquals(2, store.state.value.records.size)
            assertTrue(store.state.value.revision > snapshot.revision)
            val epoch = store.state.value.consentEpoch
            store.setEnabled(true)
            assertEquals(epoch, store.state.value.consentEpoch)
            store.clear()
            assertTrue(store.state.value.consentEpoch > epoch)
        }

    @Test
    fun `B-SEC-049 the report stays within 20 records and its UTF-8 byte budget`() =
        runTest {
            disk.files[CrashFile.Consent] = "${now - 10_000}"
            openStore()
            repeat(45) {
                store.capture(IllegalStateException(), CrashSource.Platform)
                clock.advance(1.milliseconds)
            }
            val report = builder().issued()
            assertEquals(20, report.recordCount)
            assertEquals(report.text.encodeToByteArray().size, report.byteCount)
            assertTrue(report.byteCount <= CrashReportBuilder.MAX_BYTES)
            assertTrue(report.recordCount <= CrashReportBuilder.MAX_ENTRIES)
            assertEquals(20, report.text.split('\n').count { " | platform | " in it })
        }

    @Test
    fun `B-SEC-050 an explicit share opens the sheet with the exact preview and writes nothing`() =
        runTest {
            openStore()
            capture()
            val builder = builder()
            val report = builder.issued()
            val before = disk.files.toMap()
            val result = builder.share(report)
            assertTrue(result.opened)
            assertNull(result.error)
            assertEquals(listOf(report.text), shared)
            assertEquals(before, disk.files)
        }

    @Test
    fun `B-SEC-050 a platform share channel gets exactly the text only after the explicit share`() =
        runTest {
            openStore()
            capture()
            val calls = mutableListOf<String>()
            val production = CrashReportBuilder({ store }, CrashShareSheet { calls += it; true })
            val report = production.issued()
            assertTrue(calls.isEmpty())
            assertTrue(production.share(report).opened)
            assertEquals(listOf(report.text), calls)
        }

    @Test
    fun `B-SEC-051 new objects and another builder cannot authorize a preview`() =
        runTest {
            openStore()
            capture()
            val builder = builder()
            val report = builder.issued()
            for (text in listOf(report.text, "private-forged-report")) {
                val foreign = CrashReportPreview(text, report.byteCount, report.recordCount)
                val result = builder.share(foreign)
                assertEquals(DiagnosticsError.StalePreview, result.error)
                assertFalse(result.opened)
            }
            assertEquals(DiagnosticsError.StalePreview, builder().share(report).error)
            assertTrue(shared.isEmpty())
        }

    @Test
    fun `B-SEC-051 changed evidence requires another preview before sharing`() =
        runTest {
            openStore()
            capture()
            val builder = builder()
            val old = builder.issued()
            store.capture(IllegalArgumentException("private"), CrashSource.Platform)
            val stale = builder.share(old)
            assertFalse(stale.opened)
            assertEquals(DiagnosticsError.StalePreview, stale.error)
            assertTrue(shared.isEmpty())
            val fresh = builder.issued()
            assertEquals(2, fresh.recordCount)
            assertTrue(builder.share(fresh).opened)
            assertEquals(listOf(fresh.text), shared)
        }

    @Test
    fun `B-SEC-051 disable, clear, or disable then re-enable invalidate an issued preview`() =
        runTest {
            for (change in listOf("disable", "clear", "disable then re-enable")) {
                openStore()
                capture()
                val builder = builder()
                val report = builder.issued()
                when (change) {
                    "disable" -> store.setEnabled(false)
                    "clear" -> store.clear()
                    else -> {
                        store.setEnabled(false)
                        store.setEnabled(true)
                        store.capture(IllegalStateException("private"), CrashSource.Flutter)
                    }
                }
                val result = builder.share(report)
                assertEquals(DiagnosticsError.StalePreview, result.error, change)
                assertFalse(result.opened)
                assertTrue(shared.isEmpty())
                store.close()
            }
        }

    @Test
    fun `B-SEC-051 a controller replacement invalidates even identical restored records`() =
        runTest {
            openStore()
            capture()
            val builder = builder()
            val report = builder.issued()
            store.close()
            openStore()
            assertEquals(report.recordCount, store.savedCount)
            assertEquals(DiagnosticsError.StalePreview, builder.share(report).error)
            assertTrue(shared.isEmpty())
        }

    @Test
    fun `B-SEC-051 a storage failure is typed and makes an old preview stale`() =
        runTest {
            openStore()
            capture()
            val builder = builder()
            val report = builder.issued()
            disk.refuseWrites = true
            store.capture(IllegalArgumentException("private"), CrashSource.Platform)
            assertTrue(store.state.value.storageFailed)
            assertEquals(DiagnosticsError.StorageFailed, builder.preview().error)
            assertEquals(DiagnosticsError.StalePreview, builder.share(report).error)
            assertTrue(shared.isEmpty())
        }

    @Test
    fun `B-SEC-052 capture cleared during the awaited loader is not shared`() =
        runTest {
            openStore()
            capture()
            val pending = CompletableDeferred<CrashDiagnostics?>()
            var waitForShare = false
            val delayed = CrashReportBuilder({ if (waitForShare) pending.await() else store }, sheet)
            val report = delayed.issued()
            waitForShare = true
            val sharing = async { delayed.share(report) }
            runCurrent()
            store.clear()
            pending.complete(store)
            assertEquals(DiagnosticsError.StalePreview, sharing.await().error)
            assertTrue(shared.isEmpty())
        }

    @Test
    fun `B-SEC-053 an unavailable loader gives a fixed outcome and no report`() =
        runTest {
            for (load in listOf<suspend () -> CrashDiagnostics?>({ null }, { error("private-provider-key /private/path") })) {
                val result = CrashReportBuilder(load, null).preview()
                assertEquals(DiagnosticsError.Unavailable, result.error)
                assertNull(result.preview)
            }
            openStore()
            capture()
            val builder = builder()
            val report = builder.issued()
            store.close()
            openStore() // replaced, the issued controller stays closed
            assertEquals(DiagnosticsError.StalePreview, builder.share(report).error)
        }

    @Test
    fun `B-SEC-053 a share rejection or a private exception returns shareUnavailable`() =
        runTest {
            openStore()
            capture()
            for (sheet in listOf(CrashShareSheet { false }, CrashShareSheet { error("private-provider-key /private/path") })) {
                val rejected = CrashReportBuilder({ store }, sheet)
                val result = rejected.share(rejected.issued())
                assertFalse(result.opened)
                assertEquals(DiagnosticsError.ShareUnavailable, result.error)
            }
        }

    @Test
    fun `B-SEC-053 sharing is unavailable on a platform without a share channel`() =
        runTest {
            openStore()
            capture()
            val production = CrashReportBuilder({ store }, null)
            val report = production.issued()
            assertFalse(production.shareSupported)
            assertEquals(DiagnosticsError.ShareUnavailable, production.share(report).error)
        }

    @Test
    fun `B-SEC-054 overlapping shares return busy without opening twice`() =
        runTest {
            openStore()
            capture()
            val opened = CompletableDeferred<Boolean>()
            val entered = CompletableDeferred<Unit>()
            val delayed =
                CrashReportBuilder({ store }) {
                    shared += it
                    entered.complete(Unit)
                    opened.await()
                }
            val report = delayed.issued()
            val first = async { delayed.share(report) }
            entered.await()
            assertEquals(DiagnosticsError.Busy, delayed.share(report).error)
            opened.complete(true)
            assertTrue(first.await().opened)
            assertEquals(listOf(report.text), shared)
        }
}
