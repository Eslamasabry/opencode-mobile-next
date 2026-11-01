package io.github.eslamasabry.oc.engine.delivery

import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.engine.delivery.queue.QueueRead
import io.github.eslamasabry.oc.engine.delivery.queue.QueuedPromptCodec
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * C15-1: the queue reads a real Flutter `oc.offlineQueue` blob unchanged.
 *
 * Two blobs: the `oc.offlineQueue` value of the S1 migration fixture (`synthetic-v1/shared_prefs/
 * FlutterSharedPreferences.xml`, the same file `:core:data` adopts), and `flutter/offline_queue.json`, which has the
 * byte shape Flutter's writer emits (`jsonEncode` of `QueuedPrompt.toJson`: every key, explicit nulls, raw non-ASCII,
 * escaped control characters) with online, review and other-server entries.
 */
class FlutterQueueFixtureTest {
    private val repo = File(requireNotNull(System.getProperty("oc.repo.root")))
    private val writerBlob: String = javaClass.getResource("/flutter/offline_queue.json")!!.readText()

    private fun xmlQueueBlob(): String {
        val xml = File(repo, "native/tools/flutter-migration-readers/src/test/resources/fixtures/synthetic-v1/shared_prefs/FlutterSharedPreferences.xml").readText()
        val raw = Regex("""<string name="flutter\.oc\.offlineQueue">(.*?)</string>""").find(xml)!!.groupValues[1]
        return raw.replace("&quot;", "\"").replace("&apos;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
    }

    @Test
    fun `B-STATE-033 the writer-shaped Flutter blob decodes and re-encodes byte for byte`() {
        val entries = QueuedPromptCodec.decodeQueue(writerBlob)
        assertEquals(5, entries.size)
        val first = entries.first()
        assertEquals("Reply with exactly the word: pong.\nThen read \"README.md\" — شكرا 🙏", first.text)
        assertEquals(listOf("shot.png", "notes.txt"), first.attachments.map { it.filename })
        assertEquals("anthropic", first.model?.providerId)
        assertEquals("high", first.variant)
        assertEquals(1_787_961_210_000, entries[1].dispatchedAt)
        assertEquals(QueuedPromptCodec.encodeQueue(entries), writerBlob, "a queue written by native reads the same in a byte diff")
    }

    @Test
    fun `B-STATE-033 the migration fixture's queue reads with its marker and is not rewritten by reading`() = runTest {
        val blob = xmlQueueBlob()
        val disk = Disk.withQueue(blob)
        val w = World(disk, this, clock = TestClock(Instant.fromEpochMilliseconds(1_730_457_003_000)))
        val entries = (w.store.load() as QueueRead.Readable).entries
        assertEquals(listOf("q1", "q2"), entries.map { it.id })
        assertNull(entries[0].dispatchedAt)
        assertEquals(1_730_457_002_000, entries[1].dispatchedAt)
        w.queue.entries()
        testScheduler.runCurrent()
        assertEquals(blob, disk.queueBlob(), "reading never tidies: no eviction, no marker clearing, no re-encode")
    }

    @Test
    fun `B-STATE-040 flushing the real blob sends only unmarked entries of the active server, once, and keeps the rest byte-identical`() = runTest {
        val disk = Disk.withQueue(writerBlob)
        val w = World(disk, this, clock = TestClock(Instant.fromEpochMilliseconds(1_787_961_300_000)))
        w.queue.flush()
        assertEquals(
            listOf("Reply with exactly the word: pong.\nThen read \"README.md\" — شكرا 🙏", "second conversation, plain"),
            w.link.deliveredTexts(),
            "the two marked entries (online, accepted-unrecorded) never auto-send; the other server waits",
        )
        val left = (w.restart().store.load() as QueueRead.Readable).entries
        assertEquals(listOf("online-1787961210000456", "1787961220000789", "1787961230000000"), left.map { it.id })
        val original = QueuedPromptCodec.decodeQueue(writerBlob).associateBy { it.id }
        for (entry in left) {
            assertEquals(QueuedPromptCodec.encodeQueue(listOf(original.getValue(entry.id))), QueuedPromptCodec.encodeQueue(listOf(entry)))
        }
        assertTrue(disk.queueBlob()!!.contains("\"dispatchedAt\":1787961220500"), "markers byte-for-byte")
        w.restart().queue.flush()
        assertEquals(2, w.link.delivered.size, "a restart sends nothing twice")
        assertEquals(SessionId("ses_other"), w.link.delivered.last().session)
    }
}
