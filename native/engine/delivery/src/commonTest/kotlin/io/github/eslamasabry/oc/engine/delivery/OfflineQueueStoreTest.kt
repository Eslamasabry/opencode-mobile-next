package io.github.eslamasabry.oc.engine.delivery

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.data.kv.FlutterPrefsCodec
import io.github.eslamasabry.oc.core.data.kv.RawPref
import io.github.eslamasabry.oc.core.model.PromptAgentMention
import io.github.eslamasabry.oc.core.model.PromptAttachment
import io.github.eslamasabry.oc.engine.delivery.queue.OfflineQueueStore
import io.github.eslamasabry.oc.engine.delivery.queue.QueueLimits
import io.github.eslamasabry.oc.engine.delivery.queue.QueueRead
import io.github.eslamasabry.oc.engine.delivery.queue.QueueRefusal
import io.github.eslamasabry.oc.engine.delivery.queue.QueuedPrompt
import io.github.eslamasabry.oc.engine.delivery.queue.QueuedPromptCodec
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OfflineQueueStoreTest {
    @Test
    fun `B-STATE-033 queued prompts survive a restart with attachments and mentions, and caller edits do not reach them`() = runTest {
        val disk = Disk()
        val world = World(disk, this)
        val attachments = mutableListOf(PromptAttachment("image/png", "a.png", "data:image/png;base64,AA==", mentionPath = "x"))
        val mentions = mutableListOf(PromptAgentMention("helper", "@helper", 0, 7))
        val entry = QueuedPrompt(
            id = "q1", profileId = P1, sessionId = S1, text = "مرحبا @helper", attachments = attachments, mentions = mentions,
            modelProviderId = "anthropic", modelId = "claude", agent = "build", variant = "high", createdAt = NOW_MS,
        )
        attachments.clear()
        mentions += PromptAgentMention("late", "@late", 1, 2)
        assertEquals(Outcome.Ok(null), world.queue.queuePrompt(entry))

        val reloaded = (world.restart().store.load() as QueueRead.Readable).entries.single()
        assertEquals(entry, reloaded)
        assertEquals("data:image/png;base64,AA==", reloaded.attachments.single().url)
        assertNull(reloaded.attachments.single().mentionPath, "only mime, filename and url persist, as in Flutter")
        assertEquals(listOf("helper"), reloaded.mentions.map { it.name })
        assertEquals("anthropic", reloaded.model?.providerId)
    }

    @Test
    fun `B-STATE-034 a corrupt or wrongly typed queue fails closed and keeps its bytes until cleared`() = runTest {
        for (raw in listOf("{not json", "{\"id\":\"q1\"}", "[{\"id\":\"q1\",\"profileID\":\"p\"}]", "[1]",
            "[{\"id\":\"q1\",\"profileID\":\"p\",\"sessionID\":\"s\"},{\"id\":\"q1\",\"profileID\":\"p\",\"sessionID\":\"s\"}]",
            "[{\"id\":\"q1\",\"profileID\":\"p\",\"sessionID\":\"s\",\"createdAt\":\"yesterday\"}]")) {
            val disk = Disk.withQueue(raw)
            val world = World(disk, this)
            assertEquals(QueueRead.Unreadable, world.store.load(), raw)
            assertFalse(world.store.save(listOf(prompt("new"))), "a non-empty save never overwrites unknown work")
            assertEquals(raw, disk.queueBlob(), "bytes kept")
            world.queue.flush()
            assertTrue(world.link.attempts.isEmpty(), "nothing sent")
            assertTrue(world.store.save(emptyList()), "the explicit clear is the recovery")
            assertNull(disk.queueBlob())
        }
        // A preference of the wrong platform type is unreadable too.
        val typed = Disk(mapOf(FlutterPrefsCodec.physicalKey(OfflineQueueStore.KEY) to RawPref.Int64(42)))
        val world = World(typed, this)
        assertEquals(QueueRead.Unreadable, world.store.load())
        world.queue.entries()
        assertFalse(world.queue.state.value.readable, "the screens see an unreadable queue, never an empty one")
        assertEquals(0, world.store.storedBytes())
    }

    @Test
    fun `B-STATE-035 a remove racing an in-flight save wins, and a failed write leaves the queue untouched`() = runTest {
        val disk = Disk()
        val world = World(disk, this)
        world.queue.queuePrompt(prompt("a"))
        // A stale save (still holding "b") is in flight when the remove of "b" is asked for: the remove lands after it.
        val stale = async { world.store.save(listOf(prompt("a"), prompt("b"))) }
        val removal = async { world.store.save(listOf(prompt("a"))) }
        assertTrue(stale.await() && removal.await())
        assertEquals(listOf("a"), (world.restart().store.load() as QueueRead.Readable).entries.map { it.id })

        disk.refuseQueueWrites()
        val before = disk.queueBlob()
        assertIs<Outcome.Failed>(world.queue.queuePrompt(prompt("c")))
        assertIs<Outcome.Failed>(world.queue.remove("a"))
        assertEquals(before, disk.queueBlob(), "the stored queue lost nothing")
        assertEquals(listOf("a"), world.queue.entries().map { it.id }, "memory unchanged too")
    }

    @Test
    fun `B-STATE-036 a draft that is too large is refused instead of queued`() = runTest {
        val world = World(Disk(), this)
        val big = prompt("big", text = "x", attachmentUrls = listOf("d".repeat(QueueLimits.MAX_ENTRY_BYTES.toInt())))
        assertEquals(Outcome.Ok(QueueRefusal.TooLarge), world.queue.queuePrompt(big))
        assertTrue(world.queue.entries().isEmpty())
        // payloadBytes counts UTF-16 code units, not bytes: an Arabic text just under the cap is accepted.
        val arabic = "ب".repeat(1000)
        val under = prompt("under", text = arabic, attachmentUrls = listOf("d".repeat(QueueLimits.MAX_ENTRY_BYTES.toInt() - 1000)))
        assertEquals(QueueLimits.MAX_ENTRY_BYTES, under.payloadBytes)
        assertEquals(Outcome.Ok(null), world.queue.queuePrompt(under))
    }

    @Test
    fun `B-STATE-046 limits drop expired, overflowing and oversized entries oldest first and say so`() = runTest {
        val day = 24 * 60 * 60 * 1000L
        val old = prompt("old", createdAt = NOW_MS - 15 * day)
        val unknownAge = prompt("unknown", createdAt = 0)
        val fresh = (1..52).map { prompt("f$it", createdAt = NOW_MS - 1000L + it) }
        val limited = QueueLimits.enforce(listOf(old, unknownAge) + fresh, NOW_MS)
        assertEquals(1, limited.expired)
        assertEquals(3, limited.overflowed, "51 fresh + unknown age = 53 → 50 kept, the oldest go first")
        assertFalse(limited.kept.any { it.id == "unknown" }, "the unknown-age entry sorts first and is the oldest")
        assertEquals("f52", limited.kept.last().id)

        val third = (QueueLimits.MAX_TOTAL_BYTES / 3 + 1).toInt()
        val bytes = QueueLimits.enforce((1..3).map { prompt("b$it", createdAt = NOW_MS - 10 + it, attachmentUrls = listOf("d".repeat(third))) }, NOW_MS)
        assertEquals(1, bytes.oversized)
        assertEquals(listOf("b2", "b3"), bytes.kept.map { it.id })
        val single = prompt("huge", attachmentUrls = listOf("d".repeat((QueueLimits.MAX_TOTAL_BYTES + 5).toInt())))
        assertEquals(listOf("huge"), QueueLimits.enforce(listOf(single), NOW_MS).kept.map { it.id }, "never evicted into nothing")
        assertEquals(listOf("unknown"), QueueLimits.enforce(listOf(unknownAge), NOW_MS).kept.map { it.id }, "unreadable timestamps are kept")
    }

    @Test
    fun `B-STATE-046 a stale queue is trimmed on first read and written back, with a one-time notice`() = runTest {
        val day = 24 * 60 * 60 * 1000L
        val disk = Disk.withQueue(QueuedPromptCodec.encodeQueue(listOf(prompt("ancient", createdAt = NOW_MS - 20 * day), prompt("ok"))))
        val world = World(disk, this)
        assertEquals(listOf("ok"), world.queue.entries().map { it.id })
        testScheduler.runCurrent()
        assertFalse(disk.queueBlob()!!.contains("ancient"))
        assertEquals(1, world.queue.takeEviction()?.expired)
        assertNull(world.queue.takeEviction(), "shown once")
    }

    @Test
    fun `B-STATE-046 queuing past the cap reports the eviction`() = runTest {
        val world = World(Disk(), this)
        repeat(QueueLimits.MAX_ENTRIES) { world.queue.queuePrompt(prompt("e$it", createdAt = NOW_MS - 10_000 + it)) }
        assertEquals(Outcome.Ok(null), world.queue.queuePrompt(prompt("new", createdAt = NOW_MS)))
        assertEquals(1, world.queue.takeEviction()?.overflowed)
        assertFalse(world.queue.entries().any { it.id == "e0" })
        assertEquals(QueueLimits.MAX_ENTRIES, world.queue.entries().size)
    }

    @Test
    fun `B-STATE-046 bulk clear reports the size it freed`() = runTest {
        val world = World(Disk(), this)
        world.queue.queuePrompt(prompt("a"))
        assertTrue(world.queue.storedBytes() > 0)
        assertTrue(world.queue.clearAll())
        assertEquals(0, world.queue.storedBytes())
    }
}
