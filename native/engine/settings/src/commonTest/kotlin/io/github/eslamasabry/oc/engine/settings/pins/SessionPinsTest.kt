package io.github.eslamasabry.oc.engine.settings.pins

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.data.lane.DefaultWriterLanes
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import io.github.eslamasabry.oc.engine.settings.prefs.GatedStore
import io.github.eslamasabry.oc.engine.settings.prefs.TestDisk
import io.github.eslamasabry.oc.engine.settings.prefs.storeOver
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Ports test/perf_work_lists_test.dart (B-STATE-055). */
class SessionPinsTest {
    private val a = ProfileId("a")
    private val b = ProfileId("b")

    @Test
    fun `B-STATE-055 the scope memo preserves JSON encoding and separates every location`() {
        val pairs = listOf(null to null, "" to null, null to "", "/project" to "workspace", "/project/quoted\"" to "workspace\\nested", "/other" to "workspace")
        for ((directory, workspace) in pairs) {
            val key = SessionPinStore.scope(directory, workspace)
            val decoded = Json.parseToJsonElement(key) as JsonArray
            assertEquals(listOf(directory?.let(::JsonPrimitive) ?: JsonNull, workspace?.let(::JsonPrimitive) ?: JsonNull), decoded.toList())
            assertSame(key, SessionPinStore.scope(directory, workspace))
        }
        assertEquals("[\"/project\",null]", SessionPinStore.scope("/project", null))
    }

    @Test
    fun `B-STATE-055 pin sets are immutable snapshots across writes and forget`() = runTest {
        val kv = storeOver(TestDisk(), StandardTestDispatcher(testScheduler))
        val lanes = DefaultWriterLanes()
        val pins = SessionPinStore(kv, lanes)
        val scope = SessionPinStore.scope("/project", null)
        val other = SessionPinStore.scope("/other", null)
        val empty = pins.ids(a, scope)
        assertIs<ImmutableSet<String>>(empty)
        pins.setPinned(a, scope, "first", true)
        val first = pins.ids(a, scope)
        assertSame(first, pins.ids(a, scope))
        pins.setPinned(a, scope, "second", true)
        assertTrue(empty.isEmpty())
        assertEquals(setOf("first"), first)
        assertEquals(setOf("first", "second"), pins.ids(a, scope))
        assertTrue(pins.ids(b, scope).isEmpty())
        assertTrue(pins.ids(a, other).isEmpty())
        pins.setPinned(a, scope, "first", false)
        assertEquals(setOf("first"), first)
        assertEquals(setOf("second"), pins.ids(a, scope))

        kv.commit(WriterLane("test")) { remove(SessionPinStore.keyFor(a)) }
        val lastScope = SessionPinStore.scope("/project", null)
        pins.forget(a)
        val freshScope = SessionPinStore.scope("/project", null)
        assertEquals(lastScope, freshScope)
        assertNotSame(lastScope, freshScope)
        assertTrue(pins.ids(a, scope).isEmpty())
        assertEquals(setOf("first"), first)

        pins.setPinned(b, other, "different", true)
        val reloaded = SessionPinStore(kv, lanes)
        val restored = reloaded.ids(b, other)
        assertEquals(setOf("different"), restored)
        assertSame(restored, reloaded.ids(b, other))
        assertIs<ImmutableSet<String>>(restored)
        assertIs<PrefValue.Str>(kv.get("oc.sessionPins.b"))
    }

    @Test
    fun `B-STATE-055 pending and refused writes keep the old visible pins`() = runTest {
        val disk = TestDisk()
        val kv = storeOver(disk, StandardTestDispatcher(testScheduler))
        val gated = GatedStore(kv)
        val pins = SessionPinStore(gated, DefaultWriterLanes())
        assertIs<Outcome.Ok<Unit>>(pins.setPinned(a, "scope", "saved", true))
        val before = pins.ids(a, "scope")

        gated.gate = CompletableDeferred()
        val writing = async { pins.setPinned(a, "scope", "pending", true) }
        testScheduler.runCurrent()
        assertSame(before, pins.ids(a, "scope"))
        gated.gate!!.complete(Unit)
        assertIs<Outcome.Ok<Unit>>(writing.await())
        val saved = pins.ids(a, "scope")
        assertEquals(setOf("saved", "pending"), saved)
        assertEquals(setOf("saved"), before)

        gated.gate = null
        disk.refuse = { _, _ -> true }
        assertIs<Outcome.Failed>(pins.setPinned(a, "scope", "refused", true))
        assertSame(saved, pins.ids(a, "scope"))
        disk.refuse = null
        pins.setPinned(a, "scope", "recovered", true)
        assertEquals(setOf("saved", "pending", "recovered"), pins.ids(a, "scope"))
    }
}
