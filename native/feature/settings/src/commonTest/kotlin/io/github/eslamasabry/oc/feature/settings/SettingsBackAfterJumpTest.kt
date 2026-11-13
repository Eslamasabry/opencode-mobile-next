package io.github.eslamasabry.oc.feature.settings

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.settings.walkthrough.AppearanceGuideMemory
import io.github.eslamasabry.oc.engine.settings.walkthrough.SettingsMemory
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsOutside
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPresenter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * Device pass 2026-11-13: Tips > Make it yours > Show me opens Settings on Appearance with its guide. Back must return
 * to Tips, one step, and leave nothing behind that a later Back can reach.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsBackAfterJumpTest {
    private class CountingMemory : SettingsMemory {
        var writes = 0

        override suspend fun read(key: String): Outcome<String?> = Outcome.Ok(null)

        override suspend fun write(key: String, value: String): Outcome<Unit> {
            writes++
            return Outcome.Ok(Unit)
        }
    }

    private class Visit(scope: TestScope, val memory: CountingMemory = CountingMemory()) {
        val openers = RecordingOpeners()
        var left = 0
        val p =
            SettingsPresenter(
                scope.backgroundScope,
                MutableStateFlow(phoneFacts().copy(localModelStorage = true)),
                FakeChoices(),
                openers,
                appearanceGuide = AppearanceGuideMemory(memory),
                exitWhatsNew = { left++ },
            )
    }

    private fun visit(body: suspend TestScope.(Visit) -> Unit) = runTest {
        val v = Visit(this)
        runCurrent()
        body(v)
    }

    @Test
    fun `B-NAV-070 Back from Appearance opened by Show me leaves Settings once, to Tips`() = visit { v ->
        v.p.openEntryTarget("settings-appearance", guide = true)
        runCurrent()
        assertEquals(SettingsPage.Appearance, v.p.state.value.page)
        assertTrue(v.p.onBack())
        assertEquals(1, v.left, "Back goes to the page the person came from")
        assertEquals(1, v.memory.writes, "the guide restarted once")
    }

    @Test
    fun `B-NAV-070 the jump leaves no stale target for the next visit to Settings`() = runTest {
        // Visit 1: Tips > Show me > Appearance > Back. Its view model is gone with its entry.
        val first = Visit(this)
        runCurrent()
        first.p.openEntryTarget("settings-appearance", guide = true)
        first.p.onBack()
        assertEquals(1, first.left)
        // Visit 2 (Settings from the shell, a new entry): This app > Storage > Open This phone > Back.
        val home = Visit(this)
        runCurrent()
        home.p.onOpen(SettingsPage.Storage)
        home.p.onOutside(SettingsOutside.ThisPhone)
        assertEquals(listOf(SettingsOutside.ThisPhone), home.openers.opened)
        // The outside page is its own route: Back there returns to this entry, still on Storage.
        runCurrent()
        assertEquals(SettingsPage.Storage, home.p.state.value.page)
        assertTrue(home.p.onBack())
        runCurrent()
        assertEquals(SettingsPage.Hub, home.p.state.value.page)
        assertEquals(0, home.left, "Back from Storage never leaves for Tips or reopens Appearance")
    }

    @Test
    fun `B-NAV-070 the target effect running twice opens the guide once`() = visit { v ->
        v.p.openEntryTarget("settings-appearance", guide = true)
        runCurrent()
        // The person walks on in Settings, then the screen effect runs again (a rotation, or coming back
        // from another page).
        v.p.onOpen(SettingsPage.Storage)
        runCurrent()
        v.p.openEntryTarget("settings-appearance", guide = true)
        runCurrent()
        assertEquals(SettingsPage.Storage, v.p.state.value.page, "the second run opened nothing")
        assertEquals(1, v.memory.writes, "and did not restart the guide")
    }

    @Test
    fun `B-NAV-070 walking on from the jumped-to page still steps back one page at a time`() = visit { v ->
        v.p.openEntryTarget("settings-appearance", guide = false)
        runCurrent()
        v.p.onOpen(SettingsPage.Storage)
        assertTrue(v.p.onBack())
        runCurrent()
        assertEquals(SettingsPage.Appearance, v.p.state.value.page)
        assertEquals(0, v.left)
        assertTrue(v.p.onBack())
        assertEquals(1, v.left)
    }
}
