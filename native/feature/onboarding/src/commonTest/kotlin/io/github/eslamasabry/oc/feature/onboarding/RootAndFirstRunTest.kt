package io.github.eslamasabry.oc.feature.onboarding

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.feature.onboarding.presentation.FirstRun
import io.github.eslamasabry.oc.feature.onboarding.presentation.FirstRunPreferences
import io.github.eslamasabry.oc.feature.onboarding.presentation.RootChoice
import io.github.eslamasabry.oc.feature.onboarding.presentation.RootDecision
import io.github.eslamasabry.oc.feature.onboarding.presentation.RootWorld
import io.github.eslamasabry.oc.feature.onboarding.presentation.ServerAddress
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class MemoryPrefs : FirstRunPreferences {
    val values = mutableMapOf<String, String>()
    override suspend fun read(key: String): String? = values[key]
    override suspend fun write(key: String, value: String) { values[key] = value }
}

class RootAndFirstRunTest {
    private val p1 = ProfileId("p1")

    @Test
    fun `B-NAV-024 a cold start with an active profile opens on Conversations, whatever its connection does`() {
        assertEquals(RootChoice.Conversations, RootDecision.decide(RootWorld(activeProfileId = p1, savedProfileIds = setOf(p1))))
    }

    @Test
    fun `SCREENS-NAV-02 the root rows apply in order and row 1 is no active profile, not nothing saved`() {
        assertEquals(RootChoice.Servers(welcome = true), RootDecision.decide(RootWorld()))
        // After Leave or Disconnect the saved list stays: the saved list, never the welcome.
        assertEquals(RootChoice.Servers(welcome = false), RootDecision.decide(RootWorld(savedProfileIds = setOf(p1))))
        val active = RootWorld(activeProfileId = p1, savedProfileIds = setOf(p1))
        assertEquals(RootChoice.ManagedMove, RootDecision.decide(active.copy(needsManagedMove = true, needsReentry = true)))
        assertEquals(RootChoice.Reentry(p1), RootDecision.decide(active.copy(needsReentry = true)))
        // Only a kind change replaces the root (07 §2.6).
        assertTrue(RootDecision.sameKind(RootChoice.Servers(true), RootChoice.Servers(false)))
        assertFalse(RootDecision.sameKind(RootChoice.Conversations, RootChoice.Reentry(p1)))
    }

    @Test
    fun `B-NAV-017 only a device that started on the welcome counts as new`() = runTest {
        val upgraded = FirstRun(MemoryPrefs())
        assertFalse(upgraded.landingPending())
        upgraded.observeServers(hasServers = true)
        assertFalse(upgraded.landingPending())
        upgraded.observeServers(hasServers = false) // removing every server later does not make it new
        assertFalse(upgraded.landingPending())

        val prefs = MemoryPrefs()
        val fresh = FirstRun(prefs)
        fresh.observeServers(hasServers = false)
        assertTrue(fresh.landingPending())
        assertFalse(fresh.notifyAskPending())
        fresh.markLanded()
        assertFalse(fresh.landingPending())
        assertTrue(fresh.notifyAskPending())
        fresh.answerNotifyAsk()
        assertFalse(fresh.notifyAskPending())
        assertEquals(setOf(FirstRun.STATE_KEY, FirstRun.NOTIFY_ASK_KEY), prefs.values.keys) // Flutter's keys, migrated as-is

        val returning = FirstRun(MemoryPrefs())
        returning.markReturning()
        returning.observeServers(hasServers = false)
        assertFalse(returning.landingPending())
    }

    @Test
    fun `B-NAV-018 bare pasted server addresses gain the right scheme`() {
        assertEquals("https://192.0.2.7:4096", ServerAddress.normalize("192.0.2.7:4096"))
        assertEquals("http://localhost:4096", ServerAddress.normalize("localhost:4096"))
        assertEquals("http://127.0.0.1", ServerAddress.normalize("127.0.0.1"))
        assertEquals("https://box.example.net", ServerAddress.normalize("box.example.net"))
        assertEquals("http://[::1]:4096", ServerAddress.normalize("[::1]:4096"))
        assertEquals("http://[::1]", ServerAddress.normalize("::1"))
        // Already-schemed and implausible values pass through untouched.
        assertEquals("https://host:4096", ServerAddress.normalize("https://host:4096"))
        assertEquals("not a url", ServerAddress.normalize("not a url"))
        assertEquals("", ServerAddress.normalize(""))
        assertEquals("https://host:4096", ServerAddress.normalize("  host:4096  "))
    }
}
