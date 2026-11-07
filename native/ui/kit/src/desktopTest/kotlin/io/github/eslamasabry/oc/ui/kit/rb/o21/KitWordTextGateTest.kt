package io.github.eslamasabry.oc.ui.kit.rb.o21

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.rb.o06.FlutterCapture
import io.github.eslamasabry.oc.ui.kit.text.KitWordCut
import io.github.eslamasabry.oc.ui.kit.text.KitWordText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import io.github.eslamasabry.oc.ui.kit.theme.KitTypography
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import io.github.eslamasabry.oc.ui.kit.theme.rememberKitFonts
import kotlin.test.Test
import kotlin.test.assertEquals

/** test/goldens/kit/kit_word_text_golden_test.dart: `_lines`, three cut lines in a 20 dp padding. */
class KitWordTextGateTest {
    private val words =
        "Ran the checkout tests on a clean cache and retry when the first run " +
            "fails because the price refresh races the coupon check"

    @Composable
    private fun lines(ar: Boolean = false) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            KitWordText("Read 3 files", maxLines = 1)
            Spacer(Modifier.height(16.dp))
            KitWordText(
                if (ar) "شغّل اختبارات الدفع على ذاكرة نظيفة وأعد المحاولة عندما يفشل " +
                    "التشغيل الأول لأن تحديث السعر يسبق فحص القسيمة"
                else words,
                maxLines = 1,
                role = TypeRole.Secondary,
                tone = TextTone.Secondary,
            )
            Spacer(Modifier.height(16.dp))
            KitWordText(words, maxLines = 2, role = TypeRole.Secondary, tone = TextTone.Secondary)
        }
    }

    private fun shot(name: String, ar: Boolean = false) =
        FlutterGoldenGate.check(wrap = if (ar) geistAr else ({ c -> FlutterCapture(c) }), name = name) { lines(ar) }

    /** kit_gallery.dart:64-88 keeps captureTheme's Geist primary face under the Arabic locale (as rb/o06 KitKeyValueGateTest). */
    private val geistAr: @Composable (@Composable () -> Unit) -> Unit = { c ->
        val fonts = rememberKitFonts(arabic = false)
        val typography = remember(fonts) { KitTypography(fonts, arabic = false) }
        CompositionLocalProvider(LocalKitTypography provides typography) { FlutterCapture(c) }
    }


    @Test fun kit_word_text_cut() { shot("kit_word_text_cut") }
    @Test fun kit_word_text_cut_360x800() { shot("kit_word_text_cut_360x800") }
    @Test fun kit_word_text_cut_800x1280() { shot("kit_word_text_cut_800x1280") }
    @Test fun kit_word_text_cut_1280x800() { shot("kit_word_text_cut_1280x800") }
    @Test fun kit_word_text_cut_1600x1000() { shot("kit_word_text_cut_1600x1000") }
    @Test fun kit_word_text_cut_ar() { shot("kit_word_text_cut_ar", ar = true) }
    @Test fun kit_word_text_cut_text2() { shot("kit_word_text_cut_text2") }
    @Test fun kit_word_text_cut_text2_1280x800() { shot("kit_word_text_cut_text2_1280x800") }

    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_word_text", caps = mapOf("KitWordText" to "reading")) { lines() }
    }

    /** The cut rule of kit_word_text.dart:29-69, with a character budget standing in for the line test. */
    @Test fun cut_keeps_whole_words_and_drops_trailing_marks() {
        fun fitting(n: Int): (String) -> Boolean = { it.length <= n }
        assertEquals("Read 3 files", KitWordCut.cut("Read 3 files", fitting(40)))
        assertEquals("and retry…", KitWordCut.cut("and retry when it fails", fitting(12)))
        // The mark before the cut goes: "first," then "…" reads "first…".
        assertEquals("the first…", KitWordCut.cut("the first, then the second", fitting(12)))
        // No word fits: the words come back as given (the framework's ellipsis takes over).
        assertEquals("Supercalifragilistic", KitWordCut.cut("Supercalifragilistic", fitting(4)))
        // A leading gap is not a word end.
        assertEquals(" lead…", KitWordCut.cut(" lead word more", fitting(7)))
    }
}
