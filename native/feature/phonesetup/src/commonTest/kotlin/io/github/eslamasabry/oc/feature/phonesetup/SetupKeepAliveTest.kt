@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.eslamasabry.oc.feature.phonesetup

import io.github.eslamasabry.oc.core.common.BusyKind
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.engine.runtime.setup.SetupProgress
import io.github.eslamasabry.oc.engine.runtime.setup.SetupState
import io.github.eslamasabry.oc.feature.phonesetup.presentation.BatteryCard
import io.github.eslamasabry.oc.feature.phonesetup.presentation.BatteryFacts
import io.github.eslamasabry.oc.feature.phonesetup.presentation.BatteryGuide
import io.github.eslamasabry.oc.feature.phonesetup.presentation.BatteryOpen
import io.github.eslamasabry.oc.feature.phonesetup.presentation.BatteryStep
import io.github.eslamasabry.oc.feature.phonesetup.presentation.ExitFact
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupJourneySource
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupJourneyViewModel
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupKeepAlive
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupMaker
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupOptions
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SettingsOpener
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SettingsTarget
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SystemKill
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SystemKillMessage
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.PhoneSetupRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Issue #138: the phone's battery manager closes the app mid-setup; the page says so and shows the maker's steps. */
class SetupKeepAliveTest {
    @BeforeTest fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun reset() = Dispatchers.resetMain()

    @Test
    fun `OEM-KILL-001 an exit reason maps to a closed kind and a message`() {
        // REASON_OTHER (13) is what issue #138 recorded, at foreground importance.
        assertEquals("system_kill_other", SystemKill.of(13)?.kind)
        assertEquals(SystemKillMessage.Closed, SystemKill.of(13)?.message)
        assertEquals("low_memory", SystemKill.of(3)?.kind)
        assertEquals(SystemKillMessage.OutOfMemory, SystemKill.of(3)?.message)
        assertEquals("excessive_resources", SystemKill.of(9)?.kind)
        assertEquals("freezer", SystemKill.of(14)?.kind)
        assertEquals("signaled", SystemKill.of(2)?.kind)
        // A memory-pressure sub-reason under REASON_OTHER is a low-memory kill.
        assertEquals("low_memory", SystemKill.of(13, memoryPressure = true)?.kind)
        // The person's own end, a crash and a normal exit are not the phone's doing.
        for (reason in listOf(0, 1, 4, 5, 6, 10, 11, 16)) assertNull(SystemKill.of(reason), "reason $reason")
        assertTrue(SystemKill.entries.all { Regex("[a-z_]+").matches(it.kind) })
    }

    @Test
    fun `OEM-KILL-002 only the newest recent exit is blamed`() {
        val now = 10 * SystemKill.WINDOW_MS
        assertEquals(SystemKill.Other, SystemKill.pick(listOf(ExitFact(13, now - 1000)), now))
        // The app ran on and ended normally after the kill: not the setup's fault.
        assertNull(SystemKill.pick(listOf(ExitFact(1, now - 500), ExitFact(13, now - 1000)), now))
        assertNull(SystemKill.pick(listOf(ExitFact(13, now - SystemKill.WINDOW_MS - 1)), now))
        assertNull(SystemKill.pick(emptyList(), now))
    }

    @Test
    fun `OEM-KILL-003 a restricted battery shows the card and an unrestricted one hides it`() {
        val restricted = BatteryFacts(SetupMaker.Xiaomi, ignoringOptimizations = false, backgroundRestricted = false)
        assertNotNull(BatteryCard.decide(restricted, afterKill = false))
        assertNotNull(BatteryCard.decide(restricted, afterKill = true))
        val free = BatteryFacts(SetupMaker.Xiaomi, ignoringOptimizations = true, backgroundRestricted = false)
        assertNull(BatteryCard.decide(free, afterKill = false))
        assertNull(BatteryCard.decide(free, afterKill = true))
        // Background-restricted shows for every maker, before and after.
        val bg = BatteryFacts(SetupMaker.Other, ignoringOptimizations = true, backgroundRestricted = true)
        assertNotNull(BatteryCard.decide(bg, afterKill = false))
        // A stock phone on the default battery is not nagged before it fails, but is helped after a kill.
        val stock = BatteryFacts(SetupMaker.Other, ignoringOptimizations = false, backgroundRestricted = false)
        assertNull(BatteryCard.decide(stock, afterKill = false))
        assertNotNull(BatteryCard.decide(stock, afterKill = true))
        // Unreadable: help only after a kill.
        val unknown = BatteryFacts(SetupMaker.Samsung, null, null)
        assertNull(BatteryCard.decide(unknown, afterKill = false))
        assertNotNull(BatteryCard.decide(unknown, afterKill = true))
    }

    @Test
    fun `OEM-KILL-004 the manufacturer chooses the steps`() {
        assertEquals(SetupMaker.Xiaomi, SetupMaker.of("Xiaomi", "Redmi"))
        assertEquals(SetupMaker.Xiaomi, SetupMaker.of("Xiaomi", "POCO"))
        assertEquals(SetupMaker.Oppo, SetupMaker.of("OPPO", "OPPO"))
        assertEquals(SetupMaker.Oppo, SetupMaker.of("OnePlus", "OnePlus"))
        assertEquals(SetupMaker.Realme, SetupMaker.of("realme", "realme"))
        assertEquals(SetupMaker.Vivo, SetupMaker.of("vivo", "iQOO"))
        assertEquals(SetupMaker.Samsung, SetupMaker.of("samsung", "samsung"))
        assertEquals(SetupMaker.Other, SetupMaker.of("Google", "google"))
        val withAutostart = listOf(BatteryStep.Unrestricted, BatteryStep.Autostart)
        for (maker in listOf(SetupMaker.Xiaomi, SetupMaker.Oppo, SetupMaker.Realme, SetupMaker.Vivo))
            assertEquals(withAutostart, BatteryGuide.steps(maker), "$maker")
        assertEquals(listOf(BatteryStep.Unrestricted, BatteryStep.NeverSleeping), BatteryGuide.steps(SetupMaker.Samsung))
        assertEquals(listOf(BatteryStep.Unrestricted), BatteryGuide.steps(SetupMaker.Other))
        assertTrue(BatteryGuide.makerScreens(SetupMaker.Other).isEmpty())
        assertTrue(BatteryGuide.makerScreens(SetupMaker.Xiaomi).isNotEmpty())
    }

    @Test
    fun `OEM-KILL-005 a maker screen that does not resolve falls back to the app settings, never a crash`() {
        val targets = BatteryGuide.targets(SetupMaker.Oppo, BatteryOpen.Maker)
        assertEquals(SettingsTarget.AppDetails, targets.last())
        assertTrue(targets.size >= 2)
        val tried = mutableListOf<SettingsTarget>()
        // The maker's screens throw or refuse; the app's own page opens.
        val opened =
            SettingsOpener.open(targets) { target ->
                tried += target
                if (target == SettingsTarget.AppDetails) true else throw SecurityException("not exported")
            }
        assertTrue(opened)
        assertEquals(targets, tried)
        // Nothing opens: false, not an exception.
        assertFalse(SettingsOpener.open(targets) { error("no activity") })
        assertFalse(SettingsOpener.open(emptyList()) { true })
        // The app button never tries a maker screen.
        assertEquals(listOf<SettingsTarget>(SettingsTarget.AppDetails), BatteryGuide.targets(SetupMaker.Xiaomi, BatteryOpen.App))
    }

    private class Source(state: SetupState) : SetupJourneySource {
        override val progress = MutableStateFlow(SetupProgress(state, emptyList(), 0.5, jobId = "setup-1"))
        override val toolsChanged = MutableStateFlow(0)
        override val selection = MutableStateFlow(emptySet<String>())

        override fun details(progress: SetupProgress): Redacted? = null

        override suspend fun restore(): Outcome<Unit> = Outcome.Ok(Unit)

        override suspend fun options(adding: Boolean): Outcome<SetupOptions> = Outcome.Failed(Failure.Busy(BusyKind.SessionBusy))

        override suspend fun choose(ids: Set<String>): Outcome<Unit> = Outcome.Ok(Unit)

        override suspend fun run(ids: Set<String>, adding: Boolean): Outcome<Unit> = Outcome.Ok(Unit)

        override suspend fun resume(): Outcome<Unit> = Outcome.Ok(Unit)

        override suspend fun cancel(jobId: String): Outcome<Unit> = Outcome.Ok(Unit)

        override suspend fun removeVoice(): Outcome<Unit> = Outcome.Ok(Unit)

        override fun openStorage(): Boolean = true
    }

    private class Nav : AppNavigator {
        override val top: StateFlow<AppRoute?> = MutableStateFlow(null)

        override fun open(route: AppRoute) {}

        override fun openChat(ref: ConversationRef, focusComposer: Boolean) {}

        override fun land() {}

        override fun replaceRoot(root: AppRoute) {}

        override fun back() {}
    }

    private class Phone(
        var kill: SystemKill? = null,
        var facts: BatteryFacts? = null,
        var opens: (SettingsTarget) -> Boolean = { true },
    ) : SetupKeepAlive {
        val tried = mutableListOf<SettingsTarget>()

        override suspend fun interruption(): SystemKill? = kill

        override suspend fun facts(): BatteryFacts? = facts

        override fun tryOpen(target: SettingsTarget): Boolean {
            tried += target
            return opens(target)
        }
    }

    private val xiaomi = BatteryFacts(SetupMaker.Xiaomi, ignoringOptimizations = false, backgroundRestricted = false)

    private fun vm(state: SetupState, phone: Phone) =
        SetupJourneyViewModel(PhoneSetupRoute.Setup, Source(state), Nav(), keepAlive = phone)

    @Test
    fun `OEM-KILL-006 a stopped setup that the phone killed says so and shows the card`() {
        val model = vm(SetupState.Failed, Phone(kill = SystemKill.Other, facts = xiaomi))
        assertEquals(SystemKill.Other, model.state.value.systemKill)
        assertEquals(SetupMaker.Xiaomi, model.state.value.batteryCard?.maker)
        assertTrue(model.state.value.progress.canContinue)
    }

    @Test
    fun `OEM-KILL-007 a stopped setup nobody killed has no kill words`() {
        val model = vm(SetupState.Failed, Phone(kill = null, facts = xiaomi))
        assertNull(model.state.value.systemKill)
        // Still a restricted maker on the default battery: the card helps, in the same words as before the setup.
        assertNotNull(model.state.value.batteryCard)
        assertEquals(false, model.state.value.batteryCard?.afterKill)
    }

    @Test
    fun `OEM-KILL-008 a running or finished setup shows no card`() {
        for (state in listOf(SetupState.Running, SetupState.Done)) {
            val model = vm(state, Phone(kill = SystemKill.Other, facts = xiaomi))
            assertNull(model.state.value.batteryCard, "$state")
            assertNull(model.state.value.systemKill, "$state")
        }
        // Before the start, the restricted battery shows the card.
        assertNotNull(vm(SetupState.Idle, Phone(facts = xiaomi)).state.value.batteryCard)
        val free = xiaomi.copy(ignoringOptimizations = true)
        assertNull(vm(SetupState.Idle, Phone(facts = free)).state.value.batteryCard)
    }

    @Test
    fun `OEM-KILL-009 the button opens the maker's screen, falls back, and says when nothing opened`() {
        val phone = Phone(kill = SystemKill.Other, facts = xiaomi, opens = { it == SettingsTarget.AppDetails })
        val model = vm(SetupState.Failed, phone)
        model.openBatterySettings(BatteryOpen.Maker)
        assertEquals(SettingsTarget.AppDetails, phone.tried.last())
        assertTrue(phone.tried.size >= 2)
        assertFalse(model.state.value.batteryOpenFailed)
        phone.opens = { false }
        model.openBatterySettings(BatteryOpen.App)
        assertTrue(model.state.value.batteryOpenFailed)
    }
}
