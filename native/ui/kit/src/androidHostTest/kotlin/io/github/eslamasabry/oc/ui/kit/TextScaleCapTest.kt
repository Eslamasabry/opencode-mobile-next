package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.text.TextScalePolicy
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * Plan 06 §9.4 `TextScaleCapTest` (B-KIT-059) at the plan's SDK under Robolectric. The platform's curve comes from
 * `Density(context)` — Compose's Android density, which carries Android's non-linear `FontScaleConverter` — so the
 * expectations are the platform's numbers, not a copy of its table.
 *
 * Each kit text is compared with a reference text drawn at the expected size in dp (a density with font scale 1, where
 * 1 sp = 1 dp): the same words in the same font at the same size have the same width. Width is used because it scales
 * linearly with the glyph size, while a node's height also depends on how Compose rescales sp line heights.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TextScaleCapTest {
    @get:Rule val compose = createComposeRule()

    private val words = "Stop and send"

    /** What the platform makes of [sp] at the system's own scale. */
    private fun platformDp(sp: Int): Float =
        with(Density(ApplicationProvider.getApplicationContext())) { sp.sp.toDp().value }


    /** The kit text, then the same words drawn at [expectedDp] with no scaling. */
    @Composable
    private fun Pair(tag: String, role: TypeRole, kind: KitTextKind, expectedDp: Float) {
        KitText(words, Modifier.testTag(tag), role, kind = kind, maxLines = 1)
        val style = LocalKitTypography.current.style(role)
        val unscaled = Density(LocalDensity.current.density, 1f)
        CompositionLocalProvider(LocalDensity provides unscaled) {
            BasicText(words, Modifier.testTag("$tag-ref"), style.copy(fontSize = expectedDp.sp), maxLines = 1)
        }
    }

    private fun show(content: @Composable () -> Unit) {
        compose.setContent {
            KitTheme(appearance = KitAppearance(motion = MotionLevel.Off), platform = KitRobolectric.platform(), localeTag = "en") {
                Column { content() }
            }
        }
    }

    private fun assertSameSize(tag: String) {
        val kit = compose.onNodeWithTag(tag).fetchSemanticsNode().size.width
        val ref = compose.onNodeWithTag("$tag-ref").fetchSemanticsNode().size.width
        assertTrue("$tag: kit text is $kit px wide, the reference $ref px", abs(kit - ref) <= 2)
    }

    @Test
    @Config(sdk = [KitRobolectric.SDK], qualifiers = KitRobolectric.PHONE, fontScale = 2.0f)
    fun `B-KIT-059 at 200 percent frame text stops at 1_3x and reading text keeps the non-linear curve`() {
        val body = platformDp(16)
        val title = platformDp(22)
        assertTrue("the platform is non-linear at 2.0: 16 sp → $body dp", body < 32f - 0.5f)
        show {
            Pair("frameButton", TypeRole.Button, KitTextKind.Frame, TextScalePolicy.frameDp(16f, body)) // 20.8
            Pair("frameTitle", TypeRole.Title, KitTextKind.Frame, TextScalePolicy.frameDp(22f, title)) // 28.6
            Pair("readingBody", TypeRole.Body, KitTextKind.Reading, body)
            Pair("readingTitle", TypeRole.Title, KitTextKind.Reading, title)
        }
        listOf("frameButton", "frameTitle", "readingBody", "readingTitle").forEach(::assertSameSize)
        compose.onNodeWithTag("frameButton").captureRoboImage("src/screenshots/KitFrameText/button-scale-2.0-light-en.png")
    }

    @Test
    @Config(sdk = [KitRobolectric.SDK], qualifiers = KitRobolectric.PHONE, fontScale = 1.3f)
    fun `B-KIT-059 below the cap frame text keeps the platform's non-linear size`() {
        val title = platformDp(22)
        val small = platformDp(13)
        assertTrue("the platform shrinks 22 sp at 1.3: $title < ${22 * 1.3f}", title < 22 * 1.3f - 0.3f)
        assertTrue("the platform grows 13 sp past 1.3x at 1.3: $small", small > 13 * 1.3f)
        show {
            Pair("frameTitle", TypeRole.Title, KitTextKind.Frame, title) // the platform's own 25.0
            Pair("readingTitle", TypeRole.Title, KitTextKind.Reading, title)
            Pair("frameSecondary", TypeRole.Secondary, KitTextKind.Frame, 13 * 1.3f) // clamped 17.2 → 16.9
        }
        listOf("frameTitle", "readingTitle", "frameSecondary").forEach(::assertSameSize)
    }

    @Test
    @Config(sdk = [KitRobolectric.SDK], qualifiers = KitRobolectric.PHONE)
    fun `B-KIT-052 at 100 percent frame and reading text render at the spec size`() {
        show {
            Pair("frameButton", TypeRole.Button, KitTextKind.Frame, 16f)
            Pair("readingTitle", TypeRole.Title, KitTextKind.Reading, 22f)
        }
        assertSameSize("frameButton")
        assertSameSize("readingTitle")
    }
}
