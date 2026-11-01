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
 * S1-KITA2 goldens: every part-B part under Robolectric native graphics, light/dark × en/ar (plan 06 §15.3: 412 × 915
 * dp at 420 dpi, Solid, Graphite, text 1.0, motion Off). Record: `:ui:kit:recordRoborazziAndroidHostTest --tests
 * '*PartBGoldensTest*'`; verify: `:ui:kit:verifyRoborazziAndroidHostTest`. Goldens live in
 * `src/screenshots/<Part>/<scene>-<variant>.png`. Plan 06 §15.3 says record on the VPS; these were recorded in the
 * container (report: open problems).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [KitRobolectric.SDK], qualifiers = KitRobolectric.PHONE)
class PartBGoldensTest(private val variant: KitGoldensTest.Variant) {
    @get:Rule val compose = createComposeRule()

    private val words = PartBWords.of(variant.localeTag)
    private val savedLocale: java.util.Locale = java.util.Locale.getDefault()

    @org.junit.After
    fun restoreLocale() = java.util.Locale.setDefault(savedLocale)

    private fun show(content: @Composable () -> Unit) {
        // The kit's own words come from :ui:words through the platform configuration's locale, as the app's
        // AppLocaleApplier sets it; Robolectric starts in English, so the Arabic variants switch it first.
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
    fun actions() {
        show { ActionsScene(words) }
        tag("actions", "KitActionBlock", "roles")
    }

    @Test
    fun chips() {
        show { ChipsScene(words) }
        tag("chips", "KitChip", "kinds")
    }

    @Test
    fun fields() {
        show { FieldsScene(words) }
        tag("fields", "KitField", "kinds")
    }

    @Test
    fun choices() {
        show { ChoicesScene(words) }
        tag("choices", "KitChoiceList", "segmented-choices-switches")
    }

    @Test
    fun states() {
        show { StatesScene(words) }
        tag("states", "KitStateView", "progress-receipt-error")
    }

    @Test
    fun sheet() {
        show { SheetV2Scene(words) }
        root("KitSheet", "v2-actions")
    }

    @Test
    fun confirm() {
        show { ConfirmScene(words) }
        root("KitConfirm", "destructive-typed-name")
    }

    @Test
    fun inputDialog() {
        show { InputDialogScene(words) }
        root("KitInputDialog", "open")
    }

    @Test
    fun menu() {
        show { MenuScene(words) }
        tag("menu", "KitMenu", "panel")
    }

    @Test
    fun undo() {
        show { UndoScene(words) }
        tag("undo", "KitUndoBar", "offer")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<Array<Any>> = KitGoldensTest.variants()
    }
}
