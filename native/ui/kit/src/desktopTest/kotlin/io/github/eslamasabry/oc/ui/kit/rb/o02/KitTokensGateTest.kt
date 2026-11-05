package io.github.eslamasabry.oc.ui.kit.rb.o02

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.type.*
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.*
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.row.*
import io.github.eslamasabry.oc.ui.kit.state.KitSummaryCard
import kotlin.test.Test

/** Exact copy and scene ordering from kit_foundation_golden_test.dart at 8302de537.
 * Notice.card/RequestCard are not on this branch: the existing summary card is their native home.
 * Their painting remains order 11's responsibility; buttons are order 07 and rows order 08.
 */
class KitTokensGateTest {
    @Composable private fun TypeScene() {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            for (role in TypeRole.entries) {
                val name = role.name.replaceFirstChar { it.lowercase() }
                KitText(if (role == TypeRole.Mono) "${'$'} flutter test test/checkout_test.dart" else "$name · Fix flaky checkout test", role = role)
                Spacer(Modifier.height(10.dp))
            }
            KitText("Needs you · 40 s ago", role = TypeRole.Caption, tone = TextTone.Attention)
            Spacer(Modifier.height(10.dp))
            KitText("Stop removes the queued work", role = TypeRole.Secondary, tone = TextTone.Primary)
            Spacer(Modifier.height(20.dp))
            KitButton("Allow once", {}, fullWidth = true)
            Spacer(Modifier.height(12.dp))
            KitButton("Open", {}, role = KitButtonRole.Secondary, fullWidth = true)
            Spacer(Modifier.height(12.dp))
            KitButton("Stop task", {}, destructive = true, fullWidth = true)
            Spacer(Modifier.height(12.dp))
            KitButton("Not available now", {}, enabled = KitEnabled.Disabled("Not available now"), fullWidth = true)
            Spacer(Modifier.height(4.dp))
            KitButton("See all", {}, role = KitButtonRole.Tertiary)
        }
    }

    @Composable private fun WorkScene(ar: Boolean = false) {
        Column(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                KitText("shopfront", role = TypeRole.LargeTitle)
                Spacer(Modifier.height(4.dp))
                KitText(if (ar) "main · اثنان يعملان · واحد يحتاجك" else "main · 2 working · 1 needs you", role = TypeRole.Secondary)
            }
            Spacer(Modifier.height(20.dp))
            Box(Modifier.padding(horizontal = 16.dp)) {
                KitSummaryCard(
                    kicker = if (ar) "يحتاجك · قبل 40 ثانية" else "Needs you · 40 s ago",
                    title = if (ar) "إصلاح اختبار الدفع يريد تشغيل أمر" else "Fix flaky checkout test wants to run a command",
                    detail = "flutter test test/checkout_test.dart",
                    attention = true,
                    secondary = KitAction(if (ar) "افتح" else "Open", {}),
                    primary = KitAction(if (ar) "اسمح مرة" else "Allow once", {}),
                )
            }
            Spacer(Modifier.height(22.dp))
            KitRowGroup(label = if (ar) "يعمل الآن" else "Working now") {
                KitRow(if (ar) "تسريع خط التكامل" else "Speed up the CI pipeline",
                    supporting = if (ar) "تعديل ملفات سير العمل · 4 دقائق" else "Editing workflow files · 4 min",
                    leading = KitRowLeading.Glyph(KitIcons.terminal), trailing = KitRowTrailing.Chevron, onClick = {})
                KitRow(if (ar) "شرح وحدة الدفع" else "Explain the payments module",
                    supporting = if (ar) "تمت الإجابة · أمس" else "Answered · yesterday",
                    leading = KitRowLeading.Glyph(KitIcons.info), trailing = KitRowTrailing.Chevron, onClick = {})
            }
            Spacer(Modifier.height(22.dp))
            KitRowGroup(label = if (ar) "الحاسوب" else "Laptop") {
                KitRow(if (ar) "النموذج" else "Model", leading = KitRowLeading.Glyph(KitIcons.star), trailing = KitRowTrailing.Value("Claude Sonnet 4"), onClick = {})
                KitRow(if (ar) "ما يمكن للوكلاء فعله" else "What agents may do", leading = KitRowLeading.Glyph(KitIcons.shield), trailing = KitRowTrailing.Value(if (ar) "اسأل أولاً" else "Ask first"), onClick = {})
                KitRow(if (ar) "الصوت" else "Voice", leading = KitRowLeading.Glyph(KitIcons.mic), trailing = KitRowTrailing.Value(if (ar) "العربية" else "English"), onClick = {})
            }
            Spacer(Modifier.height(22.dp))
            KitSummaryCard(
                kicker = if (ar) "تشغيل هذا الأمر؟" else "Run this command?",
                title = if (ar) "تشغيل هذا الأمر؟" else "Run this command?",
                supporting = if (ar) "للتحقق من الإصلاح · في shopfront" else "To check the fix · in shopfront",
                detail = "${'$'} flutter test test/checkout_test.dart",
                attention = true,
                secondary = KitAction(if (ar) "لا تشغّل" else "Don’t run", {}),
                primary = KitAction(if (ar) "شغّل مرة" else "Run once", {}),
            )
        }
    }

    @Test fun foundationType() { FlutterGoldenGate.check("kit_foundation_type") { TypeScene() } }
    @Test fun foundationWork() { FlutterGoldenGate.check("kit_foundation_work") { WorkScene() } }
    @Test fun foundationWorkWide() { FlutterGoldenGate.check("kit_foundation_work_1280x800") { WorkScene() } }
    @Test fun foundationWorkArabic() { FlutterGoldenGate.check("kit_foundation_work_ar", textScale = 1.3f) { WorkScene(true) } }
    @Test fun foundationWorkText2() { FlutterGoldenGate.check("kit_foundation_work_text2") { WorkScene() } }
}
