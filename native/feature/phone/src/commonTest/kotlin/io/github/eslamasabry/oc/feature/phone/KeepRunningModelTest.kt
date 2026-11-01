@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.eslamasabry.oc.feature.phone

import io.github.eslamasabry.oc.feature.phone.presentation.KeepAliveAdvice
import io.github.eslamasabry.oc.feature.phone.presentation.KeepAliveInfo
import io.github.eslamasabry.oc.feature.phone.presentation.KeepAliveSetting
import io.github.eslamasabry.oc.feature.phone.presentation.KeepAliveStepKind
import io.github.eslamasabry.oc.feature.phone.presentation.KeepRunningModel
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneMaker
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Keep running (plan 07 F15; plan 11 §8.4; port of keep_alive_advice.dart and keep_running_screen.dart). */
class KeepRunningModelTest {
    private fun info(manufacturer: String, brand: String = "", battery: Boolean = false, sdk: Int = 35) =
        KeepAliveInfo(manufacturer, brand, sdk, battery)

    private class Rig(scope: TestScope, info: KeepAliveInfo?) {
        val settings = FakeSettings(info)
        val nav = RecordingNav()
        val model = KeepRunningModel(settings, nav, scope.backgroundScope)
    }

    private fun TestScope.rig(info: KeepAliveInfo?): Rig =
        Rig(this, info).also {
            it.model.resumed()
            runCurrent()
        }

    @Test
    fun `B-ANDR-007 the maker is read from manufacturer and brand`() {
        assertEquals(PhoneMaker.Nubia, PhoneMaker.of("nubia"))
        assertEquals(PhoneMaker.Nubia, PhoneMaker.of("ZTE", "RedMagic"))
        assertEquals(PhoneMaker.Xiaomi, PhoneMaker.of("Xiaomi", "POCO"))
        assertEquals(PhoneMaker.Oppo, PhoneMaker.of("OnePlus"))
        assertEquals(PhoneMaker.Vivo, PhoneMaker.of("vivo", "iQOO"))
        assertEquals(PhoneMaker.Huawei, PhoneMaker.of("HONOR"))
        assertEquals(PhoneMaker.Samsung, PhoneMaker.of("samsung"))
        assertEquals(PhoneMaker.Other, PhoneMaker.of("Google", "google"))
        assertFalse(PhoneMaker.Other.closesOnSwipe)
        assertTrue(PhoneMaker.Nubia.closesOnSwipe)
    }

    @Test
    fun `B-ANDR-007 battery comes first everywhere, then each maker's own steps`() {
        val b = KeepAliveStepKind.Battery
        val l = KeepAliveStepKind.LockInRecents
        val a = KeepAliveStepKind.Autostart
        val g = KeepAliveStepKind.Background
        assertEquals(listOf(b, l, g, a), KeepAliveAdvice.steps(PhoneMaker.Nubia).map { it.kind })
        assertEquals(listOf(b, a, g, l), KeepAliveAdvice.steps(PhoneMaker.Xiaomi).map { it.kind })
        assertEquals(listOf(b, a, g, l), KeepAliveAdvice.steps(PhoneMaker.Oppo).map { it.kind })
        assertEquals(listOf(b, g, a, l), KeepAliveAdvice.steps(PhoneMaker.Vivo).map { it.kind })
        assertEquals(listOf(b, a, l), KeepAliveAdvice.steps(PhoneMaker.Huawei).map { it.kind })
        assertEquals(listOf(b, g, l), KeepAliveAdvice.steps(PhoneMaker.Samsung).map { it.kind })
        assertEquals(listOf(b, g), KeepAliveAdvice.steps(PhoneMaker.Other).map { it.kind })
        // Locking in Recents has no screen to open; the others do.
        assertEquals(
            listOf(KeepAliveSetting.Battery, null, KeepAliveSetting.AppDetails, KeepAliveSetting.Autostart),
            KeepAliveAdvice.steps(PhoneMaker.Nubia).map { it.setting },
        )
    }

    @Test
    fun `plan 07 F15 steps are ordered by what is left, a done step moves to the end and opens nothing`() =
        runTest {
            val rig = rig(info("nubia", battery = true))
            val ui = rig.model.state.value
            assertEquals(PhoneMaker.Nubia, ui.maker)
            assertEquals(
                listOf(KeepAliveStepKind.LockInRecents, KeepAliveStepKind.Background, KeepAliveStepKind.Autostart, KeepAliveStepKind.Battery),
                ui.steps.map { it.kind },
            )
            assertTrue(ui.steps.last().done)
            assertEquals(null, ui.steps.last().opens)
            assertTrue(ui.swipeWarning)
            assertFalse(ui.allSet)
        }

    @Test
    fun `plan 07 F15 stock Android with the battery allowed is set, other makers never are`() =
        runTest {
            val stock = rig(info("Google", battery = true)).model.state.value
            assertTrue(stock.allSet)
            assertEquals(listOf(KeepAliveStepKind.Battery), stock.steps.map { it.kind })
            assertFalse(stock.swipeWarning)
            assertFalse(rig(info("Google", battery = false)).model.state.value.allSet)
            assertFalse(rig(info("samsung", battery = true)).model.state.value.allSet)
        }

    @Test
    fun `plan 07 F15 Android's daily background limit is always stated, and a missing screen is said in place`() =
        runTest {
            val rig = rig(info("Xiaomi"))
            assertTrue(rig.model.state.value.dailyLimit)
            rig.settings.opens = false
            rig.model.open(KeepAliveStepKind.Autostart)
            runCurrent()
            assertEquals(listOf(KeepAliveSetting.Autostart), rig.settings.opened)
            assertTrue(rig.model.state.value.openFailed)
            // Coming back from Settings reads the battery switch again.
            rig.settings.info = info("Xiaomi", battery = true)
            rig.model.resumed()
            runCurrent()
            assertEquals(2, rig.settings.reads)
            assertTrue(rig.model.state.value.steps.single { it.kind == KeepAliveStepKind.Battery }.done)
            // A step with no screen (lock in Recents) opens nothing.
            rig.model.open(KeepAliveStepKind.LockInRecents)
            runCurrent()
            assertEquals(listOf(KeepAliveSetting.Autostart), rig.settings.opened)
        }

    @Test
    fun `plan 07 F15 nothing shows until the phone was read`() =
        runTest {
            val rig = rig(null)
            assertFalse(rig.model.state.value.loaded)
            assertTrue(rig.model.state.value.steps.isEmpty())
        }
}
