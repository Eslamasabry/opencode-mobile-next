package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * S1-KITA1 goldens: the part-A parts under Robolectric native graphics, light/dark × en/ar (plan 06 §15.3: 412 × 915 at
 * 420 dpi, Solid, text 1.0), the four hand-spelled packs, frame text at 200 %, and the panes at 1280 × 800.
 * Record: `:ui:kit:recordRoborazziAndroidHostTest`; verify: `:ui:kit:verifyRoborazziAndroidHostTest`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [KitRobolectric.SDK], qualifiers = KitRobolectric.PHONE)
class PartAGoldensTest(private val variant: KitGoldensTest.Variant) {
    @get:Rule val compose = createComposeRule()

    private val w = PartAWords.of(variant.localeTag)

    private val pack = mutableStateOf(PackId.Opencode)

    private fun show(content: @Composable () -> Unit) {
        compose.setContent {
            KitTheme(
                appearance = KitAppearance(
                    mode = if (variant.dark) AppearanceMode.Dark else AppearanceMode.Light,
                    motion = MotionLevel.Off,
                    pack = pack.value,
                ),
                platform = KitRobolectric.platform(),
                localeTag = variant.localeTag,
                stillFrame = true,
                content = content,
            )
        }
        compose.waitForIdle()
    }

    private fun shot(part: String, scene: String) = "src/screenshots/$part/$scene-${variant.name}.png"

    @Test
    fun kitScreen() {
        show { ScreenScene(w, status = reconnecting(w), loading = true) }
        compose.onRoot().captureRoboImage(shot("KitScreen", "status-loading"))
    }

    @Test
    fun kitRow() {
        show { Box(Modifier.testTag("rows")) { RowsScene(w) } }
        compose.onNodeWithTag("rows").captureRoboImage(shot("KitRow", "group"))
    }

    @Test
    fun kitStatusLine() {
        show { Box(Modifier.testTag("status")) { StatusScene(w) } }
        compose.onNodeWithTag("status").captureRoboImage(shot("KitStatusLine", "tones"))
    }

    @Test
    fun kitNotice() {
        show { Box(Modifier.testTag("notices")) { NoticeScene(w, open = true) } }
        compose.onNodeWithTag("notices").captureRoboImage(shot("KitNotice", "kinds-fold-open"))
    }

    @Test
    fun kitNav() {
        show { Box(Modifier.testTag("nav")) { NavScene(w) } }
        compose.onNodeWithTag("nav").captureRoboImage(shot("KitNav", "rail-sidebar"))
    }

    @Test
    fun kitPageHeading() {
        show { Box(Modifier.testTag("heading")) { HeadingScene(w) } }
        compose.onNodeWithTag("heading").captureRoboImage(shot("KitPageHeading", "default"))
    }

    @Test
    fun curatedPacks() {
        show { Box(Modifier.testTag("theme")) { ThemeScene(w) } }
        for (id in listOf(PackId.Opencode, PackId.Catppuccin, PackId.Gruvbox, PackId.Solarized)) {
            pack.value = id
            compose.waitForIdle()
            compose.onNodeWithTag("theme").captureRoboImage(shot("KitTheme", "pack-${id.storageName}"))
        }
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-160dpi")
    fun kitPaneScreen() {
        show { Box(Modifier.fillMaxSize()) { PaneScene(w, selected = true) } }
        compose.onRoot().captureRoboImage(shot("KitPaneScreen", "selected-1280x800"))
    }

    @Test
    @Config(fontScale = 2.0f)
    fun frameTextAt200() {
        show {
            Box(Modifier.testTag("large")) {
                androidx.compose.foundation.layout.Column {
                    KitTopBar(w.conversations, subtitle = w.connected, exit = KitTopBarExit.Back, exitLabel = w.back, onExit = {})
                    KitRow(w.rowTitle, supporting = w.rowSupporting, onClick = {})
                }
            }
        }
        compose.onNodeWithTag("large").captureRoboImage(shot("KitTopBar", "frame-cap-scale-2.0"))
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<Array<Any>> =
            listOf(false to "en", true to "en", false to "ar", true to "ar").map { (d, l) -> arrayOf<Any>(KitGoldensTest.Variant(d, l)) }
    }
}
