package io.github.eslamasabry.oc.ui.kitchat

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.eslamasabry.oc.ui.kit.platform.AndroidKitPlatform
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * S2-KCB renderer goldens under Robolectric native graphics, light/dark × en/ar (plan 06 §15.3: 412 × 915 dp at
 * 420 dpi, Graphite, text 1.0, motion off). Record: `:ui:kit-chat:recordRoborazziAndroidHostTest`; verify:
 * `:ui:kit-chat:verifyRoborazziAndroidHostTest`. Goldens live in `src/screenshots/<Part>/<scene>-<variant>.png`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w412dp-h915dp-420dpi")
class KitChatGoldensTest(private val variant: Variant) {
    data class Variant(val dark: Boolean, val localeTag: String) {
        val name: String = (if (dark) "dark" else "light") + "-" + localeTag
        override fun toString(): String = name
    }

    @get:Rule val compose = createComposeRule()

    private val words = ChatSceneWords.of(variant.localeTag)

    private fun show(content: @Composable () -> Unit) {
        compose.setContent {
            ChatTestTheme(
                dark = variant.dark,
                localeTag = variant.localeTag,
                platform = AndroidKitPlatform(View(ApplicationProvider.getApplicationContext())),
                content = content,
            )
        }
        compose.waitForIdle()
    }

    private fun capture(tag: String, part: String, scene: String) =
        compose.onNodeWithTag(tag).captureRoboImage("src/screenshots/$part/$scene-${variant.name}.png")

    @Test
    fun markdown() {
        show { MarkdownScene(words) }
        capture("markdown", "KitMarkdown", "reply")
    }

    @Test
    fun markdownLive() {
        show { MarkdownScene(words, live = true) }
        capture("markdown", "KitMarkdown", "reply-live")
    }

    @Test
    fun code() {
        show { CodeScene(words) }
        capture("code", "KitCodeBlock", "kinds")
    }

    @Test
    fun tools() {
        show { ToolScene(words) }
        capture("tools", "KitToolRow", "steps")
    }

    @Test
    fun diff() {
        show { DiffScene(words) }
        capture("diff", "KitDiffView", "edit-step")
    }

    @Test
    fun permission() {
        show { PermissionScene(words) }
        capture("permission", "KitPermissionCard", "ask")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<Variant> =
            listOf(Variant(false, "en"), Variant(true, "en"), Variant(false, "ar"), Variant(true, "ar"))
    }
}
