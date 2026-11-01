package io.github.eslamasabry.oc.ui.kit

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * S2-KITB goldens: settled frames of the motion parts and the K2 parts under Robolectric native graphics, light/dark ×
 * en/ar (plan 06 §15.3: 412 × 915 dp at 420 dpi, Solid, Graphite, text 1.0, motion Off, still frame). Record:
 * `:ui:kit:recordRoborazziAndroidHostTest --tests '*KitbGoldensTest*'`; verify: `:ui:kit:verifyRoborazziAndroidHostTest`.
 * Recorded in the container, not on the VPS (report: open problems).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [KitRobolectric.SDK], qualifiers = KitRobolectric.PHONE)
class KitbGoldensTest(private val variant: KitGoldensTest.Variant) {
    @get:Rule val compose = createComposeRule()

    private val words = KitbWords.of(variant.localeTag)
    private val savedLocale: java.util.Locale = java.util.Locale.getDefault()

    @org.junit.After
    fun restoreLocale() = java.util.Locale.setDefault(savedLocale)

    private fun show(content: @Composable () -> Unit) {
        if (variant.localeTag.startsWith("ar")) {
            RuntimeEnvironment.setQualifiers("+ar")
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ar"))
        }
        compose.setContent {
            KitTheme(
                appearance = KitAppearance(mode = if (variant.dark) AppearanceMode.Dark else AppearanceMode.Light, motion = MotionLevel.Off),
                platform = KitRobolectric.platform(),
                localeTag = variant.localeTag,
                stillFrame = true,
                content = content,
            )
        }
        compose.waitForIdle()
    }

    private fun tag(tag: String, part: String, scene: String) =
        compose.onNodeWithTag(tag).captureRoboImage("src/screenshots/$part/$scene-${variant.name}.png")

    private fun root(part: String, scene: String) = compose.onRoot().captureRoboImage("src/screenshots/$part/$scene-${variant.name}.png")

    @Test
    fun motionParts() {
        show { MotionPartsScene(words) }
        tag("motion", "KitMotionParts", "settled")
    }

    @Test
    fun tabs() {
        show { TabsScene(words, index = 2) }
        tag("tabs", "KitTabSwitcher", "counts-needs-you")
    }

    @Test
    fun search() {
        show { SearchScene(words) }
        tag("search", "KitSearchField", "states")
    }

    @Test
    fun filterChips() {
        show { FilterChipsScene(words) }
        tag("chips", "KitFilterChips", "overflow-fade")
    }

    @Test
    fun jumpPill() {
        show { JumpPillScene(words) }
        tag("jump", "KitJumpPill", "both-edges")
    }

    @Test
    fun tip() {
        show { TipScene(words) }
        tag("tip", "KitTip", "bubble")
    }

    @Test
    fun refresh() {
        show { RefreshScene(words) }
        tag("refresh", "KitRefresh", "refreshing-still")
    }

    @Test
    fun sheetStep() {
        show { SheetStepScene(words) }
        tag("sheet-step", "KitSheetStep", "second")
    }

    @Test
    fun sideSheet() {
        show { SideSheetScene(words) }
        root("KitSideSheet", "open")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<Array<Any>> = KitGoldensTest.variants()
    }
}
