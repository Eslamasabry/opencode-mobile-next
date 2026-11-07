package io.github.eslamasabry.oc.ui.kitchat.rb.o15

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitWorkingMark
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.ui.kitchat.tool.KitStepFold
import io.github.eslamasabry.oc.ui.kitchat.tool.KitStepTimeline
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolKind
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolRow
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolStatus
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolView
import io.github.eslamasabry.oc.ui.kitchat.tool.kitWorkIcon
import kotlin.test.Test

/**
 * Every scene of test/goldens/kit/kit_step_timeline_golden_test.dart with the turn of kit_step_timeline_scenes.dart:
 * a sentence, 8 dp, the work as one timeline, 8 dp, a sentence, 16 dp from the sides, inside Flutter's
 * `kitGalleryPart` frame ([FlutterGoldenGate.Frame.Part]). Until 2026-11-07 the case used `Frame.Screen` and left out
 * both 8 dp gaps: that was the "9 px less before, 7 px less after the line" the scoreboard measured (B3).
 *
 * Words are the scene's own (English or Arabic, `StepWords`); the summary is `KitWorkLine.summaryOf` of
 * `stepTimelineCounts` in the ARB strings of each locale; durations are the ARB `kitToolTookSeconds` words.
 *
 * Mismatch: the Write step's preview card (`KitStepPreview.fromText`, kit_step_timeline_scenes.dart:104) has no
 * native counterpart, so the expanded shots miss it.
 */
class KitStepTimelineGateTest {
    private class Words(val ar: Boolean) {
        fun t(en: String, ar: String) = if (this.ar) ar else en
    }

    private fun view(id: String, kind: KitToolKind, title: String, subject: String?, detail: String?, status: KitToolStatus, took: String?, added: Int? = null) =
        KitToolView(id, kind, title, subject, detail, status, took, added, null, "")

    private fun scene(scene: String, ar: Boolean = false): @Composable () -> Unit = {
        val w = Words(ar)
        val running = scene.startsWith("running")
        val open = scene == "expanded" || scene == "running-open"
        val live = running && !open
        // KitWorkLine.summaryOf(stepTimelineCounts(running)): read 1, searched 1, edited 1 or 2, ran 1.
        val summary = w.t(
            "Read 1 file · searched once · edited ${if (running) "2 files" else "1 file"} · ran 1 command",
            "قرأ ملفًا واحدًا · بحث مرة واحدة · عدّل ${if (running) "2 من الملفات" else "ملفًا واحدًا"} · شغّل أمرًا واحدًا",
        )
        val second = w.t("1 second", "ثانية واحدة")
        val accent = LocalKitColors.current.accent
        CompositionLocalProvider(LocalKitRedactor provides Redactor()) {
            Column(Modifier.padding(horizontal = 16.dp)) { // F: test/goldens/kit/kit_step_timeline_golden_test.dart:21
                KitText(w.t("I will add a home screen and check it with a test.", "سأضيف شاشة رئيسية وأتحقق منها باختبار."), role = TypeRole.Body, tone = TextTone.Primary)
                Spacer(Modifier.height(8.dp)) // F: test/goldens/kit/kit_step_timeline_golden_test.dart:33
                KitStepTimeline(
                    label = if (live) w.t("Editing lib/ui/router.dart", "تعديل lib/ui/router.dart") else summary,
                    icon = kitWorkIcon(read = 1, searched = 1, edited = if (running) 2 else 1, ran = 1),
                    expanded = open,
                    onPressed = {},
                    mark = if (live) {
                        { KitWorkingMark(accent) }
                    } else null,
                ) {
                    KitStepFold(w.t("Looking at how the app starts", "أنظر كيف يبدأ التطبيق"), expanded = false, onToggle = {}) {
                        KitText(w.t("The router is in lib/ui/router.dart; the home route is missing.", "المسار الرئيسي غير موجود في الموجّه."), role = TypeRole.Body, tone = TextTone.Secondary)
                    }
                    KitToolRow(view("read", KitToolKind.Read, w.t("Read", "قراءة"), "lib/ui/router.dart", null, KitToolStatus.Done, second), opened = false, onToggle = {})
                    KitToolRow(view("search", KitToolKind.Search, w.t("Search", "بحث"), null, w.t("AppRouter · 6 matches", "AppRouter · 6 نتائج"), KitToolStatus.Done, null), opened = false, onToggle = {})
                    KitStepFold(
                        w.t("The home route needs its own screen and a test", "يحتاج المسار الرئيسي في التطبيق إلى شاشة خاصة به واختبار يغطي سلوكه"),
                        expanded = false,
                        onToggle = {},
                    ) {
                        KitText(w.t("I will add the screen first, then wire the route.", "سأضيف الشاشة أولاً ثم أربط المسار."), role = TypeRole.Body, tone = TextTone.Secondary)
                    }
                    KitToolRow(view("write", KitToolKind.Write, w.t("Write", "كتابة"), "lib/ui/home_screen.dart", null, KitToolStatus.Done, second, added = 12), opened = false, onToggle = {})
                    KitToolRow(
                        view("run", KitToolKind.Shell, w.t("Run", "تشغيل"), "flutter test test/home_test.dart", null, KitToolStatus.Done, w.t("38 seconds", "38 من الثواني")),
                        opened = false,
                        onToggle = {},
                    )
                    if (running) {
                        KitToolRow(view("edit", KitToolKind.Edit, w.t("Edit", "تعديل"), "lib/ui/router.dart", null, KitToolStatus.Running, null), opened = false, onToggle = {})
                    }
                }
                Spacer(Modifier.height(8.dp)) // F: test/goldens/kit/kit_step_timeline_golden_test.dart:35
                KitText(w.t("The screen is in and the route points to it.", "الشاشة جاهزة والمسار يشير إليها."), role = TypeRole.Body, tone = TextTone.Primary)
            }
        }
    }

    @Test fun scenes() {
        FlutterGoldenGate.check("kit_step_timeline_collapsed", motion = MotionLevel.Off, content = scene("collapsed"))
        FlutterGoldenGate.check("kit_step_timeline_expanded", motion = MotionLevel.Off, content = scene("expanded"))
        FlutterGoldenGate.check("kit_step_timeline_running", motion = MotionLevel.Off, content = scene("running"))
        FlutterGoldenGate.check("kit_step_timeline_running_open", motion = MotionLevel.Off, content = scene("running-open"))
    }

    @Test fun expanded_sizes() {
        FlutterGoldenGate.check("kit_step_timeline_expanded_ar", motion = MotionLevel.Off, content = scene("expanded", ar = true))
        for (suffix in listOf("_1280x800", "_text2", "_text2_1280x800")) {
            FlutterGoldenGate.check("kit_step_timeline_expanded$suffix", motion = MotionLevel.Off, content = scene("expanded"))
        }
    }

    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_step_timeline", caps = mapOf("KitStepTimeline" to "conversation"), content = scene("collapsed"))
    }
}
