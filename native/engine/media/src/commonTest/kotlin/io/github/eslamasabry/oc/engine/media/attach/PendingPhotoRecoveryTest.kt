package io.github.eslamasabry.oc.engine.media.attach

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Plan 10 §10.5 steps 4–6 over the composer's record and join (B-MEDIA-008, B-STATE-088). */
class PendingPhotoRecoveryTest {
    private val files = FakeFiles()
    private val captures = FakeCaptures(files)
    private val codec = FakeCodec(files)
    private val store = FakePhotoStore("alpha")
    private val recovery = PendingPhotoRecovery(store, store, captures, codec)

    private suspend fun begun(session: String = "s1"): String =
        (store.begin("alpha", session, "/srv/app", "w") as Outcome.Ok).value

    private fun draft(session: String = "s1") = store.drafts["alpha/$session"].orEmpty()

    @Test
    fun `B-STATE-088 a camera photo returned after the app was killed joins its own conversation's draft once`() =
        runTest {
            val id = begun()
            files.bytes[captures.uri(id)] = png(1)
            assertEquals(RecoveryResult.Attached, recovery.recover(LauncherOutcome.Captured))
            assertEquals(1, draft().size)
            assertEquals("/srv/app", store.directories["alpha/s1"], "the origin location travels with the record")
            assertNull(store.pending)
            assertTrue(id in captures.deleted, "the capture file is deleted after use")
            assertEquals(RecoveryResult.NothingPending, recovery.recover())
        }

    @Test
    fun `B-STATE-088 a launcher result and the bootstrap call race and the photo attaches once`() =
        runTest {
            val id = begun()
            files.bytes[captures.uri(id)] = png(2)
            val results = listOf(async { recovery.recover(LauncherOutcome.Captured) }, async { recovery.recover() }).awaitAll()
            assertEquals(1, results.count { it == RecoveryResult.Attached })
            assertEquals(1, draft().size)
        }

    @Test
    fun `B-STATE-088 bytes already in the recovery vault join without reading the platform again`() =
        runTest {
            val id = begun()
            store.complete(id, "kept.png", png(3))
            assertEquals(RecoveryResult.Attached, recovery.recover())
            assertTrue(codec.prepared.isEmpty())
            assertEquals(1, draft().size)
        }

    @Test
    fun `B-MEDIA-008 a gallery result after process death attaches the first picked image`() =
        runTest {
            begun()
            val first = files.file("content://first", png(3), "first.png")
            val second = files.file("content://second", png(4), "second.png")
            assertEquals(RecoveryResult.Attached, recovery.recover(LauncherOutcome.Picked(persistentListOf(first, second))))
            assertEquals(listOf("content://first"), codec.prepared)
            assertEquals(1, draft().size)
        }

    @Test
    fun `B-MEDIA-008 cancel leaves no blocking photo and deletes the capture`() =
        runTest {
            val id = begun()
            files.bytes[captures.uri(id)] = png(1)
            assertEquals(RecoveryResult.NothingPending, recovery.recover(LauncherOutcome.Cancelled))
            assertNull(store.pending)
            assertTrue(id in captures.deleted)
            assertTrue(draft().isEmpty())
            assertEquals(RecoveryResult.NothingPending, recovery.recover(LauncherOutcome.Picked(persistentListOf())))
        }

    @Test
    fun `B-MEDIA-008 a captured result with no file is reported once and dropped`() =
        runTest {
            begun()
            assertEquals(RecoveryResult.Unavailable, recovery.recover(LauncherOutcome.Captured))
            assertNull(store.pending)
            assertEquals(RecoveryResult.NothingPending, recovery.recover())
        }

    @Test
    fun `B-MEDIA-008 an empty capture with no outcome waits for the launchers, then is dropped`() =
        runTest {
            val id = begun()
            files.bytes[captures.uri(id)] = ByteArray(0)
            assertEquals(RecoveryResult.RetryRequired, recovery.recover(), "the launcher may still deliver")
            assertEquals(id, store.pending?.id)
            assertEquals(RecoveryResult.Unavailable, recovery.recover(launchersSettled = true))
            assertNull(store.pending)
        }

    @Test
    fun `B-MEDIA-008 a record already marked failed is shown once and discarded`() =
        runTest {
            val id = begun()
            store.markFailed(id)
            assertEquals(RecoveryResult.Unavailable, recovery.recover())
            assertNull(store.pending)
        }

    @Test
    fun `B-MEDIA-008 a late result cannot resurrect a removed profile's photo`() =
        runTest {
            val id = begun()
            files.bytes[captures.uri(id)] = png(5)
            store.profiles.remove("alpha")
            assertEquals(RecoveryResult.ProfileRemoved, recovery.recover(LauncherOutcome.Captured))
            assertNull(store.pending)
            assertTrue(draft().isEmpty())
        }

    @Test
    fun `B-MEDIA-008 fake or undecodable pictures are dropped as unavailable, storage trouble keeps the record`() =
        runTest {
            val first = begun()
            files.bytes[captures.uri(first)] = "fake".encodeToByteArray()
            assertEquals(RecoveryResult.Unavailable, recovery.recover(LauncherOutcome.Captured))
            assertNull(store.pending)

            val second = begun()
            files.bytes[captures.uri(second)] = png(6)
            codec.fail = Failure.Media(MediaProblem.PhotoUnsupported)
            assertEquals(RecoveryResult.Unavailable, recovery.recover(LauncherOutcome.Captured))
            codec.fail = null

            val third = begun()
            files.bytes[captures.uri(third)] = png(7)
            store.vaultFails = true
            assertEquals(RecoveryResult.RetryRequired, recovery.recover(LauncherOutcome.Captured))
            assertEquals(third, store.pending?.id, "storage trouble keeps the record for a retry")
        }
}
