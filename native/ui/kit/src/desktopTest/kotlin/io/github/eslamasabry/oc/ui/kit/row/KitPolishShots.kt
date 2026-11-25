package io.github.eslamasabry.oc.ui.kit.row

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.InWindow
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.choice.KitAccentRow
import io.github.eslamasabry.oc.ui.kit.choice.KitAccentSwatch
import io.github.eslamasabry.oc.ui.kit.choice.KitSegment
import io.github.eslamasabry.oc.ui.kit.choice.KitSegmented
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.nav.KitTab
import io.github.eslamasabry.oc.ui.kit.nav.KitTabStrip
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

private const val PHASE = "after"

/**
 * Polish sweep kit shots (#6, #14, #16, #22) at 360 dp, light and dark, text 1.0 and 1.3, in `build/polish-render/`.
 * Not a check: a person compares the before and after pictures.
 */
@OptIn(ExperimentalTestApi::class)
class KitPolishShots {
    private data class Variant(val dark: Boolean, val locale: String, val scale: Float)

    private val variants =
        listOf(false, true).flatMap { dark ->
            listOf("en", "ar").flatMap { locale -> listOf(1f, 1.3f).map { scale -> Variant(dark, locale, scale) } }
        }

    private fun shot(out: File, name: String, v: Variant, height: Int = 2600, body: @Composable () -> Unit) {
        runDesktopComposeUiTest(360, height) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f, v.scale)) {
                    KitTheme(
                        appearance =
                            KitAppearance(
                                mode = if (v.dark) AppearanceMode.Dark else AppearanceMode.Light,
                                motion = MotionLevel.Off,
                            ),
                        platform = FakeKitPlatform(),
                        localeTag = v.locale,
                        stillFrame = true,
                    ) {
                        InWindow(360, height) {
                            Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) { body() }
                        }
                    }
                }
            }
            waitForIdle()
            mainClock.advanceTimeBy(1_000)
            waitForIdle()
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, name))
        }
    }

    @Test
    fun `6 a value row and a chevron row`() {
        val out = File("build/polish-render").also { it.mkdirs() }
        for (v in variants) {
            val name = "6-$PHASE-rows-${if (v.dark) "dark" else "light"}-${v.locale}-${v.scale}.png"
            shot(out, name, v) {
                Column {
                    KitRow("Project scope", trailing = KitRowTrailing.Value("All projects"), onClick = {})
                    KitRow("Notifications", trailing = KitRowTrailing.Chevron, onClick = {})
                }
            }
        }
    }

    @Test
    fun `16 arabic range segmented and metric grid`() {
        val out = File("build/polish-render").also { it.mkdirs() }
        val arSegments =
            listOf(
                KitSegment("24h", "٢٤ س"),
                KitSegment("7d", "٧ أيام"),
                KitSegment("30d", "٣٠ يومًا"),
                KitSegment("90d", "٩٠ يومًا"),
            )
        for (dark in listOf(false, true)) for (scale in listOf(1f, 1.3f)) {
            val v = Variant(dark, "ar", scale)
            val tag = "${if (dark) "dark" else "light"}-ar-$scale"
            shot(out, "16-$PHASE-segmented-$tag.png", v, height = 400) {
                Box(Modifier.fillMaxWidth().padding(16.dp)) {
                    KitSegmented(arSegments, "7d", "الفترة", onChange = {})
                }
            }
            val en = Variant(dark, "en", scale)
            val enTag = "${if (dark) "dark" else "light"}-en-$scale"
            shot(out, "16-$PHASE-totals-$enTag.png", en, height = 400) {
                Box(Modifier.fillMaxWidth().padding(16.dp)) {
                    KitMetricGrid(
                        listOf(
                            "Conversations" to "31",
                            "Prompts" to "88",
                            "Agent steps" to "176",
                            "Subagent conversations" to "4",
                        )
                    )
                }
            }
        }
    }

    @Test
    fun `22 appearance tabs and arabic swatches and 14 notice`() {
        val out = File("build/polish-render").also { it.mkdirs() }
        val tabs = listOf("Colours", "Style", "Background", "Text & motion").map { KitTab(it) }
        val swatches =
            listOf(
                KitAccentSwatch("mint", Color(0xFF3DDC97), "نعناعي"),
                KitAccentSwatch("teal", Color(0xFF3CCFCF), "فيروزي"),
                KitAccentSwatch("blue", Color(0xFF5AB0FF), "أزرق"),
                KitAccentSwatch("violet", Color(0xFFB39DFF), "بنفسجي"),
                KitAccentSwatch("pink", Color(0xFFF39AC9), "وردي"),
                KitAccentSwatch("mono", Color(0xFFE0E0E0), "أحادي"),
                KitAccentSwatch("silver", Color(0xFFA0A8B0), "فضي"),
            )
        for (dark in listOf(false, true)) for (scale in listOf(1f, 1.3f)) {
            val mode = if (dark) "dark" else "light"
            shot(out, "22-$PHASE-tabs-$mode-en-$scale.png", Variant(dark, "en", scale), height = 160) {
                KitTabStrip(tabs, selected = 0, onSelected = {})
            }
            shot(out, "22-$PHASE-swatches-$mode-ar-$scale.png", Variant(dark, "ar", scale), height = 360) {
                Box(Modifier.fillMaxWidth().padding(16.dp)) {
                    KitAccentRow("لون التمييز", "مطابقة", Color(0xFF3DDC97), swatches, null, {}, grid = true)
                }
            }
            shot(out, "14-$PHASE-notice-$mode-en-$scale.png", Variant(dark, "en", scale), height = 360) {
                Box(Modifier.fillMaxWidth().padding(16.dp)) {
                    KitNotice(
                        "Voice typing asks Android to recognise speech on the phone itself.",
                        icon = KitIcons.locked,
                        liveRegion = false,
                    )
                }
            }
        }
    }
}
