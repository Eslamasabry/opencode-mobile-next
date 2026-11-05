package io.github.eslamasabry.oc.ui.kit.rb.o01

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurface
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInset
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfacePadding
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfacePanel
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceTile
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import kotlin.test.Test

/** Every scene of test/goldens/kit/kit_surface_golden_test.dart, with its inputs (English and Arabic copy). */
class KitSurfaceGateTest {
    private class Copy(
        val cardTitle: String, val cardMeta: String, val plain: String, val sheetTitle: String, val sheetConsequence: String,
        val terminal: String, val warning: String, val done: String,
    )

    private val en = Copy(
        "Fix flaky checkout test", "Editing workflow files · 4 min", "No header here, just the child content.",
        "Delete workspace fox?", "3 queued messages will be discarded.", "Terminal", "Warning", "Done",
    )
    private val ar = Copy(
        "إصلاح اختبار الدفع المتقطع", "تعديل ملفات سير العمل · 4 د", "لا عنوان هنا، محتوى فرعي فقط.",
        "حذف مساحة العمل fox؟", "ستُحذف 3 رسائل في قائمة الانتظار.", "طرفية", "تحذير", "تم",
    )

    private val levels: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth()) {
            for (level in SurfaceLevel.entries) {
                KitText(level.name.replaceFirstChar { it.lowercase() }, role = TypeRole.Label)
                Spacer(Modifier.height(8.dp))
                Row {
                    KitSurface(Modifier.size(96.dp, 56.dp), level = level, outlined = false, padding = KitSurfacePadding.None) {}
                    Spacer(Modifier.width(12.dp))
                    KitSurface(Modifier.size(96.dp, 56.dp), level = level, outlined = true, padding = KitSurfacePadding.None) {}
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    private val shapes: @Composable () -> Unit = {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (shape in KitShapeId.entries) {
                KitSurface(Modifier.size(72.dp), level = SurfaceLevel.Surface2, shape = shape, padding = KitSurfacePadding.None) {}
            }
        }
    }

    private fun panel(c: Copy): @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth()) {
            KitSurfacePanel(Modifier.fillMaxWidth(), title = c.cardTitle, icon = KitIcons.terminal) { KitText(c.cardMeta, role = TypeRole.Secondary) }
            Spacer(Modifier.height(16.dp))
            KitSurfacePanel(Modifier.fillMaxWidth()) { KitText(c.plain, role = TypeRole.Body) }
        }
    }

    private fun inset(c: Copy): @Composable () -> Unit = {
        KitSurface(Modifier.fillMaxWidth(), level = SurfaceLevel.Surface2, shape = KitShapeId.Sheet) {
            Column(Modifier.fillMaxWidth()) {
                KitText(c.sheetTitle, role = TypeRole.Title)
                Spacer(Modifier.height(12.dp))
                KitSurfaceInset(Modifier.fillMaxWidth()) { KitText(c.sheetConsequence, role = TypeRole.Secondary) }
            }
        }
    }

    private fun tiles(c: Copy): @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            KitSurfaceTile(KitIcons.terminal, semanticsLabel = c.terminal)
            Spacer(Modifier.width(12.dp))
            KitSurfaceTile(KitIcons.warning, tone = TextTone.Primary, semanticsLabel = c.warning)
            Spacer(Modifier.width(12.dp))
            KitSurfaceTile(KitIcons.check, tone = TextTone.Success, semanticsLabel = c.done)
        }
    }

    private fun shot(name: String, textScale: Float? = null, content: @Composable () -> Unit) {
        FlutterGoldenGate.check(name, textScale = textScale, content = content)
    }

    @Test fun levels() = shot("kit_surface_levels", content = levels)
    @Test fun shapes() = shot("kit_surface_shapes", content = shapes)
    @Test fun panel() = shot("kit_surface_panel", content = panel(en))
    @Test fun inset() = shot("kit_surface_inset", content = inset(en))
    @Test fun tile() = shot("kit_surface_tile", content = tiles(en))

    @Test fun panel_sizes() {
        for (size in listOf("360x800", "800x1280", "1280x800", "1600x1000")) shot("kit_surface_panel_$size", content = panel(en))
    }

    @Test fun text2_and_ar() {
        for (size in listOf("", "_1280x800")) {
            shot("kit_surface_panel_text2$size", content = panel(en))
            shot("kit_surface_tile_text2$size", content = tiles(en))
            shot("kit_surface_panel_ar$size", 1.3f, panel(ar))
            shot("kit_surface_tile_ar$size", 1.3f, tiles(ar))
        }
    }

    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_surface", caps = mapOf("KitSurface" to "wrapper"), content = panel(en))
    }
}
