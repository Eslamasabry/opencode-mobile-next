package io.github.eslamasabry.oc.ui.kit.rb.o08

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurface
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.test.ExperimentalTestApi
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.state.KitCostNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitOfferNotice
import kotlin.test.Test

/**
 * test/goldens/kit/kit_notice_golden_test.dart: each scene in its `_Section` (a surface1 panel on the gutter, space4
 * inside) on `_page` (16 above, centred, at most 720 wide: the gate's Part frame).
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class KitNoticeGateTest {
    /** Flutter `_Section`: the gutter on both sides only (the page already gives 16 above), then a surface1 panel. */
    @Composable
    private fun Section(content: @Composable () -> Unit) {
        KitSurface(Modifier.padding(horizontal = 16.dp)) { Column { content() } } // F: kit_notice_golden_test.dart:30-37
    }

    private fun t(ar: Boolean, en: String, arabic: String) = if (ar) arabic else en

    private fun default(ar: Boolean = false): @Composable () -> Unit = {
        Section {
            KitNotice(
                message = t(ar, "The provider said the key has expired. Paste a new one.", "قال المزوّد إن المفتاح منتهٍ. الصق مفتاحًا جديدًا."),
                title = t(ar, "The key was not accepted", "لم يُقبل المفتاح"),
                notes = listOf(
                    t(ar, "Nothing was saved", "لم يُحفظ شيء"),
                    t(ar, "The old key stays on the server until you replace it", "يبقى المفتاح القديم على الخادم حتى تستبدله"),
                ),
                actions = listOf(KitLineAction(t(ar, "Paste a key", "لصق مفتاح"), onClick = {})),
                dismiss = KitLineAction("Dismiss", onClick = {}),
            )
        }
    }

    private fun tone(tone: StatusTone, message: String): @Composable () -> Unit = {
        Section { KitNotice(message = message, tone = tone) }
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

    private val cost: @Composable () -> Unit = {
        Section {
            KitCostNotice(listOf("About 208 MB", "about 4 min", "uses battery while it installs"), title = "Before you start")
            Spacer(Modifier.height(12.dp)) // F: kit_notice_golden_test.dart:121
            KitButton("Install", onClick = {}, Modifier.fillMaxWidth(), role = KitButtonRole.Primary) // the column stretches it
        }
    }

    @Test fun kit_notice_default() { FlutterGoldenGate.check("kit_notice_default", content = default()) }
    @Test fun kit_notice_default_360x800() { FlutterGoldenGate.check("kit_notice_default_360x800", content = default()) }
    @Test fun kit_notice_default_800x1280() { FlutterGoldenGate.check("kit_notice_default_800x1280", content = default()) }
    @Test fun kit_notice_default_915x412() { FlutterGoldenGate.check("kit_notice_default_915x412", content = default()) }
    @Test fun kit_notice_default_1280x800() { FlutterGoldenGate.check("kit_notice_default_1280x800", content = default()) }
    @Test fun kit_notice_default_1600x1000() { FlutterGoldenGate.check("kit_notice_default_1600x1000", content = default()) }
    @Test fun kit_notice_default_text2() { FlutterGoldenGate.check("kit_notice_default_text2", content = default()) }
    @Test fun kit_notice_default_text2_1280x800() { FlutterGoldenGate.check("kit_notice_default_text2_1280x800", content = default()) }
    @Test fun kit_notice_default_ar() { FlutterGoldenGate.check("kit_notice_default_ar", content = default(ar = true)) }
    @Test fun kit_notice_default_ar_1280x800() { FlutterGoldenGate.check("kit_notice_default_ar_1280x800", content = default(ar = true)) }

    @Test fun kit_notice_ok() { FlutterGoldenGate.check("kit_notice_ok", content = tone(StatusTone.Ok, "The server answered in 120 ms.")) }
    @Test fun kit_notice_warning() {
        FlutterGoldenGate.check(
            "kit_notice_warning",
            content = tone(StatusTone.Attention, "Battery saver is on. Android may pause agents after a few minutes in the background."),
        )
    }
    @Test fun kit_notice_working() { FlutterGoldenGate.check("kit_notice_working", content = tone(StatusTone.Progress, "Checking the server…")) }

    @Test fun kit_notice_offer() { FlutterGoldenGate.check("kit_notice_offer", content = offer()) }
    @Test fun kit_notice_offer_wrapped() { FlutterGoldenGate.check("kit_notice_offer_wrapped", content = offer(wrapped = true)) }
    @Test fun kit_notice_offer_text2() { FlutterGoldenGate.check("kit_notice_offer_text2", content = offer()) }
    @Test fun kit_notice_offer_text2_1280x800() { FlutterGoldenGate.check("kit_notice_offer_text2_1280x800", content = offer()) }
    @Test fun kit_notice_offer_ar() { FlutterGoldenGate.check("kit_notice_offer_ar", content = offer(ar = true)) }
    @Test fun kit_notice_offer_ar_1280x800() { FlutterGoldenGate.check("kit_notice_offer_ar_1280x800", content = offer(ar = true)) }

    @Test fun kit_notice_cost() { FlutterGoldenGate.check("kit_notice_cost", content = cost) }

    // The contract's cap is `reading` (08.gate.tsv `cap part-kit_notice KitNotice reading`).
    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_notice", caps = mapOf("KitNotice" to "reading"), content = default()) }
}
