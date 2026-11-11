package io.github.eslamasabry.oc.ui.kit.sweepb

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.eslamasabry.oc.ui.design.color.wcagContrast
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitRoles
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.ConfirmRules
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.modal.answerRoles
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateSize
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SweepBDialogsStatesTest {
    @Test
    fun `sweep-b 1 a destructive confirm draws the safe answer as the primary and the act as a quiet danger answer`() {
        for (kind in listOf(KitConfirmKind.Destructive, KitConfirmKind.Discard, KitConfirmKind.Stop)) {
            val r = ConfirmRules.answerRoles(kind)
            assertEquals(KitButtonRole.Primary, r.safe, "$kind safe")
            assertEquals(KitButtonRole.Tertiary, r.confirm, "$kind confirm")
            assertTrue(r.confirmIsDanger, "$kind danger")
        }
    }

    @Test
    fun `sweep-b 1 a normal confirm is unchanged`() {
        val r = ConfirmRules.answerRoles(KitConfirmKind.Neutral)
        assertEquals(KitButtonRole.Primary, r.confirm)
        assertEquals(KitButtonRole.Secondary, r.safe)
        assertTrue(!r.confirmIsDanger)
    }

    @Test
    fun `sweep-b 1 the danger words hold 4_5 to 1 on every pane in dark and light`() {
        for ((name, roles) in listOf("dark" to FlutterKitRoles.graphiteDark, "light" to FlutterKitRoles.graphiteLight)) {
            for ((pane, bg) in listOf("ground" to roles.ground, "surface1" to roles.surface1, "surface2" to roles.surface2)) {
                val ratio = wcagContrast(roles.attention, bg)
                assertTrue(ratio >= 4.5, "$name $pane contrast $ratio")
            }
        }
    }

    @Test
    fun `sweep-b 1 a destructive confirm still confirms and cancels from its two buttons`() =
        runDesktopComposeUiTest(412, 915) {
            var confirmed = 0
            var cancelled = 0
            setContent {
                KitTheme(platform = FakeKitPlatform(), localeTag = "en") {
                    KitSheetHost {
                        KitConfirm(
                            spec = KitConfirmSpec("Delete fox?", "Delete fox", KitConfirmKind.Destructive),
                            onConfirm = { confirmed++ },
                            onCancel = { cancelled++ },
                        )
                    }
                }
            }
            waitForIdle()
            println("sweep-b-tree " + onRoot().printToString().replace("\n", " | "))
            val del = onNode(hasText("Delete fox") and hasClickAction())
            val cancel = onNode(hasText("Cancel") and hasClickAction())
            assertTrue(del.getBoundsInRoot().top < cancel.getBoundsInRoot().top, "order stays: act first")
            del.performClick()
            cancel.performClick()
            assertEquals(1, confirmed)
            assertEquals(1, cancelled)
        }

    @Test
    fun `sweep-b 2 every kind draws and runs both its primary and its secondary action`() {
        for (kind in KitStateKind.entries) {
            if (kind == KitStateKind.Loading || kind == KitStateKind.Working) continue
            var p = 0
            var s = 0
            runDesktopComposeUiTest(412, 915) {
                setContent {
                    KitTheme(platform = FakeKitPlatform(), localeTag = "en") {
                        KitStateView(
                            kind = kind,
                            title = "Title $kind",
                            body = "One line",
                            prerequisite = false,
                            primary = KitAction("Primary act", { p++ }),
                            secondary = KitAction("Second act", { s++ }),
                        )
                    }
                }
                waitForIdle()
                onNode(hasText("Primary act") and hasClickAction()).assertIsDisplayed().performClick()
                onNode(hasText("Second act") and hasClickAction()).assertIsDisplayed().performClick()
            }
            assertEquals(1 to 1, p to s, "$kind")
        }
    }

    @Test
    fun `sweep-b 3 alone on a page the state is centred and inline it starts at the rail`() {
        var pageCentre = 0f
        var inlineLeft = 0f
        var width = 0f
        runDesktopComposeUiTest(412, 915) {
            setContent {
                KitTheme(platform = FakeKitPlatform(), localeTag = "en") {
                    Box(Modifier.fillMaxSize()) {
                        KitStateView(KitStateKind.Empty, "Nothing here yet", body = "Add one", icon = KitIcons.error)
                    }
                }
            }
            waitForIdle()
            val b = onNodeWithText("Nothing here yet").getBoundsInRoot()
            pageCentre = (b.left.value + b.right.value) / 2
            width = onRoot().getBoundsInRoot().let { it.right.value - it.left.value }
        }
        runDesktopComposeUiTest(412, 915) {
            setContent {
                KitTheme(platform = FakeKitPlatform(), localeTag = "en") {
                    KitStateView(KitStateKind.Empty, "Nothing here yet", size = KitStateSize.Inline)
                }
            }
            waitForIdle()
            inlineLeft = onNodeWithText("Nothing here yet").getBoundsInRoot().left.value
        }
        assertTrue(kotlin.math.abs(pageCentre - width / 2) < 2f, "page centre $pageCentre of $width")
        assertTrue(inlineLeft < 40f, "inline starts at the rail: $inlineLeft")
    }
}
