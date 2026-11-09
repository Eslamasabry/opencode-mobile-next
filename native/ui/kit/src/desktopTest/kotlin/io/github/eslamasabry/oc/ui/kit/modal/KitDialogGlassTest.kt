@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.modal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.effects.LiquidRole
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.effects.KitLiquidBudget
import io.github.eslamasabry.oc.ui.kit.effects.LocalKitLiquidBudget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Confirms and input dialogs wear the same Glass face as sheets (owner 2026-11-09, "Why isn't this glass?"): the same
 * liquid claim on the window's budget, so a dialog over a sheet leaves the sheet liquid, and every other style keeps
 * its solid face (no claim at all).
 */
class KitDialogGlassTest {
    private val confirm =
        KitConfirmSpec(
            title = "Approve everything?",
            body = "Every request is allowed.",
            confirmLabel = "Approve",
            kind = KitConfirmKind.Neutral,
        )

    /** The roles asking for the window's liquid glass, and how many were granted, once the scene has settled. */
    private fun roles(style: Style, sheet: Boolean, confirmOpen: Boolean, input: Boolean): Pair<List<LiquidRole>, Int> {
        var budget: KitLiquidBudget? = null
        var out: Pair<List<LiquidRole>, Int> = emptyList<LiquidRole>() to 0
        runDesktopComposeUiTest(412, 915) {
            setContent {
                KitTheme(
                    KitAppearance(mode = AppearanceMode.Dark, style = style, motion = MotionLevel.Full),
                    FakeKitPlatform(liquidGlass = true),
                    "en",
                ) {
                    KitSheetHost {
                        Box(Modifier.fillMaxSize())
                        budget = LocalKitLiquidBudget.current
                        if (sheet) KitSheet(visible = true, onDismissRequest = {}, title = "Permissions") { KitText("Body") }
                        if (confirmOpen) KitConfirm(spec = confirm, onConfirm = {}, onCancel = {})
                        if (input) KitInputDialog(visible = true, title = "Rename", fieldLabel = "Name", confirmLabel = "Save", onResult = {})
                    }
                }
            }
            waitForIdle()
            waitForIdle()
            budget?.let { b -> out = b.claims.map { it.role } to b.granted.size }
        }
        return out
    }

    @Test
    fun `B-KIT-018 in Glass a confirm claims the same liquid face as a sheet`() {
        assertEquals(listOf(LiquidRole.Sheet), roles(Style.Glass, sheet = true, confirmOpen = false, input = false).first)
        assertEquals(listOf(LiquidRole.Sheet), roles(Style.Glass, sheet = false, confirmOpen = true, input = false).first)
    }

    @Test
    fun `B-KIT-018 in Glass an input dialog claims the same liquid face as a sheet`() {
        assertEquals(listOf(LiquidRole.Sheet), roles(Style.Glass, sheet = false, confirmOpen = false, input = true).first)
    }

    @Test
    fun `B-KIT-018 a confirm opened over a sheet leaves the sheet liquid`() {
        val (claimed, granted) = roles(Style.Glass, sheet = true, confirmOpen = true, input = false)
        assertEquals(2, claimed.size, "the sheet and the confirm both ask for the glass")
        assertTrue(granted >= 2, "both are granted: the confirm does not strip the sheet")
    }

    @Test
    fun `B-KIT-037 in Solid a confirm keeps its own face and claims no glass`() {
        assertTrue(roles(Style.Solid, sheet = false, confirmOpen = true, input = false).first.isEmpty())
    }
}
