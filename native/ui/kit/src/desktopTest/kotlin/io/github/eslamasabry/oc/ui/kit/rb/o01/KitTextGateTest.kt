package io.github.eslamasabry.oc.ui.kit.rb.o01

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitMonoCut
import io.github.eslamasabry.oc.ui.kit.KitMonoText
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitRoles
import kotlin.test.Test

/** Every scene of test/goldens/kit/kit_text_golden_test.dart (type, mono), with its inputs. */
class KitTextGateTest {
    private class Copy(val phrase: String, val tones: List<String>, val mono: String, val host: String, val path: String)

    private val en = Copy(
        "Fix the flaky checkout test before the release",
        listOf(
            "Primary text, the ambient colour", "Secondary text, a row's meta line", "Tertiary text, disabled", "Accent text, a link",
            "Text on the accent fill", "Attention: needs you", "Danger: this stops something", "Success: it worked",
        ),
        "git push origin release/2026-09-26 --force-with-lease --no-verify",
        "workstation-office-network-4096.internal.example.com",
        "/home/user/projects/very/long/path/to/checkout/main.dart",
    )
    private val ar = Copy(
        "إصلاح اختبار الدفع غير المستقر قبل الإصدار",
        listOf(
            "نص أساسي، اللون المحيط", "نص ثانوي، سطر التفاصيل", "نص ثالثي، معطّل", "نص التمييز، رابط", "نص على تعبئة التمييز",
            "يحتاجك الانتباه", "خطر: هذا يوقف شيئاً", "نجاح: نجحت العملية",
        ),
        "git push origin release/2026-09-26 --force-with-lease --no-verify",
        "workstation-office-network-4096.internal.example.com",
        "/home/user/projects/very/long/path/to/checkout/main.dart",
    )

    private val tones = listOf(TextTone.Primary, TextTone.Secondary, TextTone.Tertiary, TextTone.Accent, TextTone.OnAccent, TextTone.Attention, TextTone.Success)

    @Composable
    private fun ToneLine(index: Int, tone: TextTone, c: Copy) {
        if (tone != TextTone.OnAccent) {
            KitText(c.tones[index], tone = tone)
        } else {
            Box(Modifier.fillMaxWidth().background(Color(LocalKitRoles.current.accent.v))) {
                Box(Modifier.padding(8.dp)) { KitText(c.tones[index], tone = tone) }
            }
        }
    }

    private fun type(c: Copy): @Composable () -> Unit = {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp).fillMaxWidth()) {
            for (role in TypeRole.entries) {
                KitText(
                    if (role == TypeRole.Mono) c.mono else "${role.name.replaceFirstChar { it.lowercase() }} · ${c.phrase}",
                    role = role,
                )
                Spacer(Modifier.height(10.dp))
            }
            for ((index, tone) in tones.withIndex()) {
                Box(Modifier.padding(bottom = 6.dp)) { ToneLine(index, tone, c) }
            }
        }
    }

    @Composable
    private fun FixedWidth(content: @Composable () -> Unit) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) { Box(Modifier.width(260.dp)) { content() } }
    }

    private fun mono(c: Copy): @Composable () -> Unit = {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp).fillMaxWidth()) {
            KitText("wrap", role = TypeRole.Label)
            Spacer(Modifier.height(4.dp))
            KitMonoText(c.mono)
            Spacer(Modifier.height(20.dp))
            KitText("end", role = TypeRole.Label)
            Spacer(Modifier.height(4.dp))
            FixedWidth { KitMonoText(c.host, cut = KitMonoCut.End) }
            Spacer(Modifier.height(20.dp))
            KitText("middle", role = TypeRole.Label)
            Spacer(Modifier.height(4.dp))
            FixedWidth { KitMonoText(c.path, cut = KitMonoCut.Middle) }
        }
    }

    private fun shot(name: String, content: @Composable () -> Unit) { FlutterGoldenGate.check(name, content = content) }

    @Test fun type_every_window() {
        for (size in listOf("", "_360x800", "_915x412", "_800x1280", "_1280x800", "_1600x1000")) shot("kit_text_type$size", type(en))
    }
    @Test fun mono_default() = shot("kit_text_mono", mono(en))

    @Test fun text2_and_ar() {
        for (size in listOf("", "_1280x800")) {
            shot("kit_text_type_text2$size", type(en))
            shot("kit_text_type_ar$size", type(ar))
            shot("kit_text_mono_text2$size", mono(en))
            shot("kit_text_mono_ar$size", mono(ar))
        }
    }

    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_text", caps = mapOf("KitLtr" to "wrapper", "KitSelectable" to "wrapper", "KitText" to "reading"), content = type(en))
    }
}
