package io.github.eslamasabry.oc.engine.settings.scope

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.data.kv.FlutterPrefsCodec
import io.github.eslamasabry.oc.core.data.kv.RawPref
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.data.lane.DefaultWriterLanes
import io.github.eslamasabry.oc.core.data.scope.ProfileKeyRule
import io.github.eslamasabry.oc.engine.settings.prefs.GatedStore
import io.github.eslamasabry.oc.engine.settings.prefs.TestDisk
import io.github.eslamasabry.oc.engine.settings.prefs.seed
import io.github.eslamasabry.oc.engine.settings.prefs.storeOver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SessionAgentSettingTest {
    @Test
    fun `B-STATE-129 a chat agent choice leaves other chats and the profile default alone and survives a restart`() = runTest {
        val disk = TestDisk()
        val store = storeOver(disk, StandardTestDispatcher(testScheduler))
        store.seed("oc.agent.laptop" to "build", "oc.agent.phone" to "build")
        val choices = ChatAgentChoices(store, DefaultWriterLanes())
        assertIs<Outcome.Ok<*>>(choices.set(LAPTOP, "a", "plan"))
        assertEquals("plan", choices.agentFor(LAPTOP, "a"))
        assertNull(choices.agentFor(LAPTOP, "b"))
        assertEquals("build", store.string("oc.agent.laptop"))
        assertEquals(mapOf("a" to "plan"), choices.choicesFor(LAPTOP))
        assertTrue(choices.choicesFor(PHONE).isEmpty())

        val restarted = ChatAgentChoices(storeOver(TestDisk(disk.persisted()), StandardTestDispatcher(testScheduler)), DefaultWriterLanes())
        assertEquals(mapOf("a" to "plan"), restarted.choicesFor(LAPTOP))
        assertNull(restarted.agentFor(PHONE, "a"))
    }

    @Test
    fun `B-STATE-129 a chat agent choice is not shown before storage acknowledges it`() = runTest {
        val gated = GatedStore(storeOver(TestDisk(), StandardTestDispatcher(testScheduler)))
        val choices = ChatAgentChoices(gated, DefaultWriterLanes())
        val release = CompletableDeferred<Unit>().also { gated.gate = it }
        val saving = async { choices.set(LAPTOP, "a", "plan") }
        gated.entered.await()
        assertNull(choices.agentFor(LAPTOP, "a"))
        release.complete(Unit)
        assertIs<Outcome.Ok<*>>(saving.await())
        assertEquals("plan", choices.agentFor(LAPTOP, "a"))
        assertNull(choices.agentFor(LAPTOP, "b"))
    }

    @Test
    fun `B-STATE-129 refused persistence keeps the previous choice`() = runTest {
        val disk = TestDisk()
        val choices = ChatAgentChoices(storeOver(disk, StandardTestDispatcher(testScheduler)), DefaultWriterLanes())
        choices.set(LAPTOP, "a", "plan").ok()
        disk.refuse = { key, _ -> key == "oc.sessionAgents.laptop" }
        assertReason(SettingsFailureReason.StorageFailed, choices.set(LAPTOP, "a", "review"))
        assertEquals("plan", choices.agentFor(LAPTOP, "a"))
        assertEquals(mapOf("a" to "plan"), choices.choicesFor(LAPTOP))
        val stored = disk.persisted()[FlutterPrefsCodec.physicalKey("oc.sessionAgents.laptop")] as RawPref.Str
        assertTrue("plan" in stored.value && "review" !in stored.value)
    }

    @Test
    fun `B-STATE-129 deleting a chat removes only its stored override`() = runTest {
        val store = storeOver(TestDisk(), StandardTestDispatcher(testScheduler))
        val choices = ChatAgentChoices(store, DefaultWriterLanes())
        choices.set(LAPTOP, "a", "plan").ok()
        choices.set(LAPTOP, "b", "review").ok()
        choices.forgetSession(LAPTOP, "a").ok()
        assertEquals(mapOf("b" to "review"), choices.choicesFor(LAPTOP))
        // Swept with the profile by the Flutter rule.
        assertTrue("oc.sessionAgents.laptop" in ProfileKeyRule.scopedKeys("laptop", store.keys("oc."), setOf("laptop", "phone")))
        assertTrue("oc.sessionAgents.laptop".startsWith(ChatAgentChoices.SHAPE.prefix))
        choices.forgetSession(LAPTOP, "b").ok()
        assertNull(store.get("oc.sessionAgents.laptop"), "an empty map removes the key")
    }

    @Test
    fun `B-STATE-129 profile deletion drains a pending chat-agent write without resurrection`() = runTest {
        val gated = GatedStore(storeOver(TestDisk(), StandardTestDispatcher(testScheduler)), inLane = true)
        val choices = ChatAgentChoices(gated, DefaultWriterLanes())
        choices.setAll(PHONE, mapOf("other" to "review")).ok()
        val release = CompletableDeferred<Unit>().also { gated.gate = it }
        val saving = async { choices.set(LAPTOP, "a", "plan") }
        gated.entered.await()
        val deletion = async { choices.forgetProfile(LAPTOP) }
        runCurrent()
        assertFalse(deletion.isCompleted, "deletion outran the pending write")
        release.complete(Unit)
        assertReason(SettingsFailureReason.StaleTarget, saving.await())
        assertIs<Outcome.Ok<*>>(deletion.await())
        assertNull(gated.get("oc.sessionAgents.laptop"))
        assertTrue(choices.choicesFor(LAPTOP).isEmpty())
        assertEquals(mapOf("other" to "review"), choices.choicesFor(PHONE))
        assertReason(SettingsFailureReason.StaleTarget, choices.set(LAPTOP, "a", "plan"))
        assertNull(gated.get("oc.sessionAgents.laptop"))
        assertIs<Outcome.Ok<*>>(choices.forgetProfile(LAPTOP), "idempotent")
    }
}
