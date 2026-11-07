package io.github.eslamasabry.oc.ui.kit.rb.o08

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.RedactorApi
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurface
import io.github.eslamasabry.oc.ui.kit.state.KitCostNotice
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitOfferNotice
import kotlin.test.Test

/**
 * The scenes of test/goldens/kit/kit_notice_golden_test.dart: each notice in a surface1 section on the host's rails
 * (`_Section`: the gutter outside, `space4` inside), as a form or a list holds one.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class, RedactorApi::class)
class KitNoticeGateTest {
    @Composable
    private fun Section(content: @Composable () -> Unit) {
        KitSurface(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) { Column { content() } }
    }

    private fun t(ar: Boolean, en: String, arabic: String) = if (ar) arabic else en

    private fun default(ar: Boolean = false): @Composable () -> Unit = {
        Section {
            KitNotice(
                title = t(ar, "The key was not accepted", "لم يُقبل المفتاح"),
                message = t(ar, "The provider said the key has expired. Paste a new one.", "قال المزوّد إن المفتاح منتهٍ. الصق مفتاحًا جديدًا."),
                notes = listOf(
                    t(ar, "Nothing was saved", "لم يُحفظ شيء"),
                    t(ar, "The old key stays on the server until you replace it", "يبقى المفتاح القديم على الخادم حتى تستبدله"),
                ),
                actions = listOf(KitLineAction(t(ar, "Paste a key", "لصق مفتاح"), onClick = {})),
                dismiss = KitLineAction(t(ar, "Dismiss", "إخفاء"), onClick = {}),
            )
        }
    }

    private fun tone(tone: StatusTone, message: String): @Composable () -> Unit = { Section { KitNotice(message, tone = tone) } }

    private val errorNetwork: @Composable () -> Unit = {
        Section {
            KitErrorNotice(
                "Couldn’t reach the laptop",
                failure = Failure.TimedOut(),
                details = Redacted("GET /session timed out after 10 s"),
                retry = KitLineAction("Try again", onClick = {}),
                switchServer = KitLineAction("Switch server", onClick = {}),
            )
        }
    }

    private fun errorOther(ar: Boolean = false): @Composable () -> Unit = {
        Section {
            KitErrorNotice(
                t(ar, "The server sent something the app can’t read.", "أرسل الخادم شيئًا لا يستطيع التطبيق قراءته."),
                title = t(ar, "Couldn’t load files", "تعذّر تحميل الملفات"),
                failure = Failure.Unexpected(),
                details = Redacted("FormatException: unexpected character at 12"),
                copyLabel = t(ar, "Copy details", "نسخ التفاصيل"),
                retry = KitLineAction(t(ar, "Try again", "إعادة المحاولة"), onClick = {}),
                // The gallery registers a report handler for this shot, so Report a problem shows.
                report = KitLineAction(t(ar, "Report a problem", "الإبلاغ عن مشكلة"), onClick = {}),
            )
        }
    }

    private val cost: @Composable () -> Unit = {
        Section {
            KitCostNotice(listOf("About 208 MB", "about 4 min", "uses battery while it installs"), title = "Before you start")
            Spacer(Modifier.height(12.dp))
            KitButton("Install", {}, Modifier.fillMaxWidth(), role = KitButtonRole.Primary)
        }
    }

    private fun offer(ar: Boolean = false, wrapped: Boolean = false): @Composable () -> Unit = {
        Section {
            KitOfferNotice(
                message =
                    if (wrapped) t(ar, "This server also runs an AI team. Turn it on to hand it bigger jobs?", "يشغّل هذا الخادم فريق ذكاء اصطناعي أيضًا. هل تريد تشغيله لتسليمه مهام أكبر؟")
                    else t(ar, "Pin it to Work?", "تثبيته في العمل؟"),
                action = KitLineAction(if (wrapped) t(ar, "Turn on", "تشغيل") else t(ar, "Pin", "تثبيت"), onClick = {}),
                dismiss = KitLineAction(t(ar, "Not now", "ليس الآن"), onClick = {}),
            )
        }
    }

    @Test fun kit_notice_default() { FlutterGoldenGate.check("kit_notice_default", content = default()) }
    @Test fun kit_notice_ok() { FlutterGoldenGate.check("kit_notice_ok", content = tone(StatusTone.Ok, "The server answered in 120 ms.")) }
    @Test fun kit_notice_warning() {
        FlutterGoldenGate.check(
            "kit_notice_warning",
            content = tone(StatusTone.Attention, "Battery saver is on. Android may pause agents after a few minutes in the background."),
        )
    }
    @Test fun kit_notice_working() { FlutterGoldenGate.check("kit_notice_working", content = tone(StatusTone.Progress, "Checking the server…")) }
    @Test fun kit_notice_error_network() { FlutterGoldenGate.check("kit_notice_error_network", content = errorNetwork) }
    @Test fun kit_notice_error_other() { FlutterGoldenGate.check("kit_notice_error_other", content = errorOther()) }
    @Test fun kit_notice_cost() { FlutterGoldenGate.check("kit_notice_cost", content = cost) }
    @Test fun kit_notice_offer() { FlutterGoldenGate.check("kit_notice_offer", content = offer()) }
    @Test fun kit_notice_offer_wrapped() { FlutterGoldenGate.check("kit_notice_offer_wrapped", content = offer(wrapped = true)) }

    @Test fun kit_notice_default_sizes() {
        for (size in listOf("360x800", "800x1280", "1280x800", "1600x1000", "915x412")) {
            FlutterGoldenGate.check("kit_notice_default_$size", content = default())
        }
    }

    @Test fun kit_notice_text2() {
        for (suffix in listOf("", "_1280x800")) {
            FlutterGoldenGate.check("kit_notice_default_text2$suffix", content = default())
            FlutterGoldenGate.check("kit_notice_error_other_text2$suffix", content = errorOther())
            FlutterGoldenGate.check("kit_notice_offer_text2$suffix", content = offer())
        }
    }

    @Test fun kit_notice_arabic() {
        for (suffix in listOf("", "_1280x800")) {
            FlutterGoldenGate.check("kit_notice_default_ar$suffix", content = default(ar = true))
            FlutterGoldenGate.check("kit_notice_error_other_ar$suffix", content = errorOther(ar = true))
            FlutterGoldenGate.check("kit_notice_offer_ar$suffix", content = offer(ar = true))
        }
    }

    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_notice", caps = mapOf("KitNotice" to "reading"), content = default()) }
}
