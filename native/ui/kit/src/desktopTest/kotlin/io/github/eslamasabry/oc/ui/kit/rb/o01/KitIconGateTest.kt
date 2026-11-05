package io.github.eslamasabry.oc.ui.kit.rb.o01

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.icon.KitBrandMark
import io.github.eslamasabry.oc.ui.kit.icon.KitBrandMarkSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.icon.KitStatusIcon
import kotlin.test.Test

/** Every scene of test/goldens/kit/kit_icon_golden_test.dart, with its inputs (English and Arabic). */
@OptIn(ExperimentalLayoutApi::class)
class KitIconGateTest {
    @Composable
    private fun Gutter(content: @Composable () -> Unit) {
        Column(Modifier.padding(horizontal = 16.dp)) { content() }
    }

    private class ToneRow(val tone: TextTone, val en: String, val ar: String)

    private val toneRows = listOf(
        ToneRow(TextTone.Primary, "Primary", "أساسي"), ToneRow(TextTone.Secondary, "Secondary", "ثانوي"),
        ToneRow(TextTone.Accent, "Accent", "مميز"), ToneRow(TextTone.Success, "Success", "نجاح"),
    )

    private fun sizeLabel(size: KitIconSize, arabic: Boolean) = when (size) {
        KitIconSize.Small -> if (arabic) "صغير · 20" else "Small · 20"
        KitIconSize.Medium -> if (arabic) "متوسط · 22" else "Medium · 22"
        KitIconSize.Large -> if (arabic) "كبير · 24" else "Large · 24"
    }

    private class Dir(val glyph: ImageVector, val en: String, val ar: String)

    private val directionRows = listOf(
        Dir(KitIcons.back, "Back", "رجوع"), Dir(KitIcons.chevronRight, "Open", "فتح"),
        Dir(KitIcons.forward, "Forward", "تقدّم"), Dir(KitIcons.check, "Done (never mirrors)", "تم (لا ينعكس)"),
    )

    @Composable
    private fun DirectionRow(arabic: Boolean) {
        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
            KitText(if (arabic) "الاتجاه" else "Direction", role = TypeRole.Label)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (d in directionRows) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        KitIcon(d.glyph)
                        Spacer(Modifier.height(4.dp))
                        KitText(if (arabic) d.ar else d.en, role = TypeRole.Caption, tone = TextTone.Secondary)
                    }
                }
            }
        }
    }

    private fun sizes(arabic: Boolean = false): @Composable () -> Unit = {
        Gutter {
            Column(Modifier.fillMaxWidth()) {
                DirectionRow(arabic)
                for (size in KitIconSize.entries) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                        KitText(sizeLabel(size, arabic), role = TypeRole.Label)
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            for (t in toneRows) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    KitIcon(KitIcons.star, size = size, tone = t.tone)
                                    Spacer(Modifier.height(4.dp))
                                    KitText(if (arabic) t.ar else t.en, role = TypeRole.Caption, tone = TextTone.Secondary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private class StatusRow(val status: StatusTone, val en: String, val ar: String)

    private val statusRows = listOf(
        StatusRow(StatusTone.Neutral, "Idle", "خامل"), StatusRow(StatusTone.Progress, "Connecting…", "جارٍ الاتصال…"),
        StatusRow(StatusTone.Ok, "Connected", "متصل"), StatusRow(StatusTone.Attention, "Needs a key", "يحتاج مفتاحاً"),
        StatusRow(StatusTone.Failure, "Could not connect", "تعذّر الاتصال"),
    )

    private fun statusScene(arabic: Boolean = false): @Composable () -> Unit = {
        Gutter {
            Column(Modifier.fillMaxWidth()) {
                DirectionRow(arabic)
                for (r in statusRows) {
                    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        KitStatusIcon(r.status)
                        Spacer(Modifier.width(10.dp))
                        KitText(if (arabic) r.ar else r.en, role = TypeRole.Body)
                    }
                }
            }
        }
    }

    private val duotone: @Composable () -> Unit = {
        Gutter {
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KitIcon(KitIcons.workspaceSelected)
                    Spacer(Modifier.width(12.dp))
                    KitText("Selected (two layers)", role = TypeRole.Body)
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KitIcon(KitIcons.workspaceSelected)
                    Spacer(Modifier.width(12.dp))
                    KitText("High contrast (one layer)", role = TypeRole.Body)
                }
            }
        }
    }

    private val brand: @Composable () -> Unit = {
        Gutter {
            Column(Modifier.fillMaxWidth()) {
                for ((size, words) in listOf(KitBrandMarkSize.Tile to "Tile · 30", KitBrandMarkSize.Mark to "Mark · 44")) {
                    Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        KitBrandMark(size = size)
                        Spacer(Modifier.width(12.dp))
                        KitText(words, role = TypeRole.Body)
                    }
                }
            }
        }
    }

    private fun shot(name: String, content: @Composable () -> Unit) { FlutterGoldenGate.check(name, content = content) }

    @Test fun sizes_every_window() {
        for (size in listOf("", "_360x800", "_800x1280", "_1280x800", "_1600x1000")) shot("kit_icon_sizes$size", sizes())
    }
    @Test fun status() = shot("kit_icon_status", statusScene())
    @Test fun brandShot() = shot("kit_icon_brand", brand)
    @Test fun duotoneShot() = shot("kit_icon_duotone", duotone)

    @Test fun text2_and_ar() {
        for (size in listOf("", "_1280x800")) {
            shot("kit_icon_sizes_text2$size", sizes())
            shot("kit_icon_sizes_ar$size", sizes(arabic = true))
            shot("kit_icon_status_text2$size", statusScene())
            shot("kit_icon_status_ar$size", statusScene(arabic = true))
        }
    }

    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_icon", caps = mapOf("KitBrandMark" to "glyph", "KitIcon" to "glyph")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KitIcon(KitIcons.star)
                Spacer(Modifier.width(12.dp))
                KitBrandMark(size = KitBrandMarkSize.Mark)
            }
        }
    }
}
