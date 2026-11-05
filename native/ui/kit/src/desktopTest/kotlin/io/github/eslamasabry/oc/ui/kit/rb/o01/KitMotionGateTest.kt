package io.github.eslamasabry.oc.ui.kit.rb.o01

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.motion.KitAnimatedBox
import io.github.eslamasabry.oc.ui.kit.motion.KitAnimatedValue
import io.github.eslamasabry.oc.ui.kit.motion.KitChevron
import io.github.eslamasabry.oc.ui.kit.motion.KitDimLevel
import io.github.eslamasabry.oc.ui.kit.motion.KitSpin
import io.github.eslamasabry.oc.ui.kit.motion.KitSwap
import io.github.eslamasabry.oc.ui.kit.motion.kitDim
import androidx.compose.ui.text.font.FontWeight
import io.github.eslamasabry.oc.ui.kit.theme.KitFontFamilies
import io.github.eslamasabry.oc.ui.kit.theme.KitTypography
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitRoles
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import kotlin.test.Test

private val MaterialTerminal: androidx.compose.ui.graphics.vector.ImageVector =
    androidx.compose.ui.graphics.vector.ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        addPath(
            androidx.compose.ui.graphics.vector.PathParser().parsePathString(
                "M20 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 14H4V8h16v10zm-2-1h-6v-2h6v2zM7.5 17l-1.41-1.41L8.67 13l-2.59-2.59L7.5 9l4 4-4 4z",
            ).toNodes(),
            fill = androidx.compose.ui.graphics.SolidColor(Color.Black),
        )
    }.build()

/** Every scene of test/goldens/kit/kit_motion_parts_golden_test.dart, with its inputs. */
class KitMotionGateTest {
    @Composable
    private fun Pill(label: String) {
        val roles = LocalKitRoles.current
        Box(Modifier.background(Color(roles.surface2.v), CircleShape).padding(horizontal = 14.dp, vertical = 8.dp)) {
            KitText(label, role = TypeRole.RowTitle)
        }
    }

    private val swapBefore: @Composable () -> Unit = { GalleryScene("KitSwap") { KitSwap("Sending…") { Pill(it) } } }
    private val swapAfter: @Composable () -> Unit = { GalleryScene("KitSwap") { KitSwap("Sent") { Pill(it) } } }
    private val swapAfterAr: @Composable () -> Unit = { GalleryScene("KitSwap") { KitSwap("أُرسلت") { Pill(it) } } }

    private fun chevron(expanded: Boolean, label: String): @Composable () -> Unit = {
        GalleryScene("KitSpin.chevron") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KitText(label, role = TypeRole.RowTitle)
                Spacer(Modifier.width(8.dp))
                KitChevron(expanded)
            }
        }
    }

    private val spinFixed: @Composable () -> Unit = {
        GalleryScene("KitSpin.fixed") {
            KitSpin(0.25f) {
                // The Dart scene's `Icons.terminal` is the Material glyph, not a Phosphor one: its outline, as the test input.
                val tint = Color(LocalKitRoles.current.text2.v)
                val glyph = androidx.compose.ui.graphics.vector.rememberVectorPainter(MaterialTerminal)
                androidx.compose.foundation.Image(glyph, null, Modifier.size(28.dp), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(tint))
            }
        }
    }

    private fun box(label: String, level: SurfaceLevel? = null, outlined: Boolean = false): @Composable () -> Unit = {
        GalleryScene("KitAnimatedBox ($label)") {
            KitAnimatedBox(level = level, outlined = outlined) { Spacer(Modifier.size(96.dp, 56.dp)) }
        }
    }

    private fun dim(label: String, level: KitDimLevel): @Composable () -> Unit = {
        GalleryScene("KitDim ($label)") {
            val roles = LocalKitRoles.current
            Row {
                Box(Modifier.kitDim(level = level).size(56.dp).background(Color(roles.surface3.v), RoundedCornerShape(12.dp)))
                Spacer(Modifier.width(16.dp))
                val accent = Color(roles.accent.v)
                Canvas(Modifier.kitDim(level = level).size(56.dp)) { drawCircle(accent) }
            }
        }
    }

    private fun value(v: Float): @Composable () -> Unit = {
        GalleryScene("KitAnimatedValue") {
            val roles = LocalKitRoles.current
            Box(Modifier.width(240.dp).height(8.dp).background(Color(roles.surface3.v), RoundedCornerShape(4.dp))) {
                KitAnimatedValue(v, jump = true) { shown ->
                    Box(Modifier.fillMaxWidth(shown.coerceIn(0f, 1f)).height(8.dp).background(Color(roles.accent.v), RoundedCornerShape(4.dp)))
                }
            }
        }
    }

    private fun shot(name: String, textScale: Float? = null, content: @Composable () -> Unit) { FlutterGoldenGate.check(name, textScale = textScale, content = content) }

    /** The `_ar` goldens: Flutter's test loads its Noto Sans Arabic fixture, so the kit's Arabic roles use it here too. */
    private fun shotAr(name: String, textScale: Float, content: @Composable () -> Unit) {
        FlutterGoldenGate.check(
            name,
            textScale = textScale,
            wrap = { inner ->
                val regular = androidx.compose.ui.text.platform.Font("NotoSansArabic-Regular", fixtureBytes("NotoSansArabic-Regular.ttf"), FontWeight.Normal)
                val bold = androidx.compose.ui.text.platform.Font("NotoSansArabic-Bold", fixtureBytes("NotoSansArabic-Bold.ttf"), FontWeight.Bold)
                val fixture = androidx.compose.ui.text.font.FontFamily(regular, bold)
                val families = androidx.compose.runtime.remember { KitFontFamilies(fixture, fixture) }
                androidx.compose.runtime.CompositionLocalProvider(LocalKitTypography provides KitTypography(families, true)) { inner() }
            },
            content = content,
        )
    }

    private fun fixtureBytes(name: String): ByteArray = checkNotNull(javaClass.getResourceAsStream("/fonts/$name")).readBytes()

    @Test fun swap_before() = shot("kit_motion_swap_before", content = swapBefore)
    @Test fun swap_after() = shot("kit_motion_swap_after", content = swapAfter)
    @Test fun spin_chevron_closed() = shot("kit_motion_spin_chevron_closed", content = chevron(false, "Show details"))
    @Test fun spin_chevron_open() = shot("kit_motion_spin_chevron_open", content = chevron(true, "Hide details"))
    @Test fun spin_fixed() = shot("kit_motion_spin_fixed", content = spinFixed)
    @Test fun box_default() = shot("kit_motion_box_default", content = box("default"))
    @Test fun box_surface1() = shot("kit_motion_box_surface1", content = box("surface1", SurfaceLevel.Surface1))
    @Test fun box_surface2() = shot("kit_motion_box_surface2", content = box("surface2", SurfaceLevel.Surface2))
    @Test fun box_surface3() = shot("kit_motion_box_surface3", content = box("surface3", SurfaceLevel.Surface3))
    @Test fun box_outlined() = shot("kit_motion_box_outlined", content = box("outlined", outlined = true))
    @Test fun dim_stale() = shot("kit_motion_dim_stale", content = dim("stale", KitDimLevel.Stale))
    @Test fun dim_disabled() = shot("kit_motion_dim_disabled", content = dim("disabled", KitDimLevel.Disabled))
    @Test fun value_030() = shot("kit_motion_value_030", content = value(.3f))
    @Test fun value_080() = shot("kit_motion_value_080", content = value(.8f))

    @Test fun box_default_sizes() {
        for (size in listOf("360x800", "915x412", "800x1280", "1280x800", "1600x1000")) shot("kit_motion_box_default_$size", content = box("default"))
    }

    @Test fun swap_text2_and_ar() {
        for (size in listOf("", "_1280x800")) {
            shot("kit_motion_swap_after_text2$size", content = swapAfter)
            shotAr("kit_motion_swap_after_ar$size", 1.3f, swapAfterAr)
            shot("kit_motion_spin_chevron_open_text2$size", content = chevron(true, "Hide details"))
            shotAr("kit_motion_spin_chevron_open_ar$size", 1.3f, chevron(true, "إخفاء التفاصيل"))
        }
    }

    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_motion") { GalleryScene("KitSwap") { KitSwap("Sent") { Pill(it) } } }
    }
}
