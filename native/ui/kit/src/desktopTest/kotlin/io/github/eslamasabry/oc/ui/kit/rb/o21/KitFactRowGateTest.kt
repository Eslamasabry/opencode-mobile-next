package io.github.eslamasabry.oc.ui.kit.rb.o21

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.rb.o06.FlutterCapture
import io.github.eslamasabry.oc.ui.kit.row.KitFactRow
import io.github.eslamasabry.oc.ui.kit.row.KitProvenance
import io.github.eslamasabry.oc.ui.kit.row.kitFactBorder
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * test/goldens/kit/kit_fact_row_golden_test.dart at a fixed clock of 2026-10-11: the dates are the caller's
 * (`KitFactRow.dateOf`, `DateFormat.MMMd`), so the scenes pass them as Flutter formats them.
 */
class KitFactRowGateTest {
    @Composable private fun facts() {
        Column(Modifier.fillMaxWidth()) {
            KitFactRow("Annual fee is [AMOUNT] CUR.", KitProvenance.Told, "Sep 12")
            KitFactRow("Statement is due on the 15th of each month.", KitProvenance.Seen, "Oct 3", source = "a shared message")
            KitFactRow("Used for groceries.", KitProvenance.Guessed, "Oct 9", source = "a chat you saved")
            KitFactRow("Total spend this year is [AMOUNT] CUR.", KitProvenance.Calculated, "Oct 9")
        }
    }

    @Composable private fun waiting() {
        KitFactRow(
            "Probably pays the electricity bill by card ••42.", KitProvenance.Guessed, "Oct 9",
            source = "a chat you saved", waitingForOk = true,
            answers = listOf(KitAction("Yes, keep it", onClick = {}), KitAction("Not right", onClick = {})),
        )
    }

    @Composable private fun hits() {
        Column(Modifier.fillMaxWidth()) {
            KitFactRow("Dentist appointment Wed 21 Oct 10:00. Bring the insurance card.", KitProvenance.Told, "Oct 8", title = "Dentist", onOpen = {})
            KitFactRow("Saved chat: \"book the dentist before the trip\".", KitProvenance.Seen, "Oct 3", source = "a chat you saved", title = "Planning the dentist visit", onOpen = {})
        }
    }

    @Composable private fun arabic() {
        Column(Modifier.fillMaxWidth()) {
            KitFactRow("الرسوم السنوية لبطاقة [Bank A] Visa ••42 هي [AMOUNT] CUR", KitProvenance.Told, "١٢ سبتمبر")
            KitFactRow("موعد طبيب الأسنان يوم 21/10 الساعة 10:00", KitProvenance.Seen, "٨ أكتوبر", source = "نص شاركته")
            KitFactRow("ربما يدفع سام فاتورة الكهرباء ببطاقة ••42", KitProvenance.Guessed, "٩ أكتوبر", source = "محادثة حفظتها", waitingForOk = true)
        }
    }

    private fun shot(name: String, modes: List<String> = listOf("light", "dark"), content: @Composable () -> Unit) =
        FlutterGoldenGate.check(wrap = { FlutterCapture(it) }, name = name, modes = modes, content = content)

    @Test fun kit_fact_row_default() { shot("kit_fact_row_default") { facts() }; shot("kit_fact_row_default_1280x800") { facts() } }
    @Test fun kit_fact_row_waiting() { shot("kit_fact_row_waiting") { waiting() }; shot("kit_fact_row_waiting_1280x800") { waiting() } }
    @Test fun kit_fact_row_hits() { shot("kit_fact_row_hits") { hits() }; shot("kit_fact_row_hits_1280x800") { hits() } }
    @Test fun kit_fact_row_default_text2() {
        shot("kit_fact_row_default_text2", listOf("dark")) { facts() }
        shot("kit_fact_row_default_text2_1280x800", listOf("dark")) { facts() }
    }
    @Test fun kit_fact_row_arabic() {
        shot("kit_fact_row_arabic_ar", listOf("dark")) { arabic() }
        shot("kit_fact_row_arabic_ar_1280x800", listOf("dark")) { arabic() }
    }

    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_fact_row", caps = mapOf("KitFactRow" to "list")) { waiting() }
    }

    /** kit_fact_row.dart:189-197: the border says the provenance a second way. */
    @Test fun border_per_provenance() {
        val accent = Color.Green; val text2 = Color.Gray; val hair = Color.DarkGray
        assertEquals(1.5f, kitFactBorder(KitProvenance.Told, accent, text2, hair).width)
        assertEquals(accent, kitFactBorder(KitProvenance.Told, accent, text2, hair).color)
        assertEquals(true, kitFactBorder(KitProvenance.Guessed, accent, text2, hair).dashed)
        assertEquals(text2, kitFactBorder(KitProvenance.Guessed, accent, text2, hair).color)
        assertEquals(1f, kitFactBorder(KitProvenance.Seen, accent, text2, hair).width)
        assertEquals(Color.Transparent, kitFactBorder(KitProvenance.Calculated, accent, text2, Color.Transparent).color)
    }
}
