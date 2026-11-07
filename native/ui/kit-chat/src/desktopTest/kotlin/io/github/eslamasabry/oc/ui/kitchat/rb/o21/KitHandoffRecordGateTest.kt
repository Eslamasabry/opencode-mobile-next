package io.github.eslamasabry.oc.ui.kitchat.rb.o21

import androidx.compose.runtime.CompositionLocalProvider
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.ui.kitchat.rb.o06.FlutterCapture
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitHandoffRecord
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitHandoffRecordRules
import kotlin.test.Test
import kotlin.test.assertEquals

/** test/goldens/kit/kit_handoff_record_golden_test.dart: `_record`, folded and open. */
class KitHandoffRecordGateTest {
    private fun record(expanded: Boolean): @androidx.compose.runtime.Composable () -> Unit = {
        KitHandoffRecord(
            sizeBytes = 48200,
            omittedMessages = 12,
            redacted = true,
            markdown = "# Conversation record\n\nFix the login redirect on the finance hub.\n\n" +
                "- Checked the callback\n- Found the cause in the session cookie",
            path = ".opencode/handoff/finance-hub-login-redirect-record.md",
            initiallyExpanded = expanded,
        )
    }

    private fun shots(state: String, expanded: Boolean) {
        for (suffix in listOf("", "_1280x800", "_text2", "_text2_1280x800")) {
            FlutterGoldenGate.check(wrap = { c -> CompositionLocalProvider(LocalKitRedactor provides Redactor()) { FlutterCapture(c) } }, name = "kit_handoff_record_$state$suffix", content = record(expanded))
        }
    }

    @Test fun kit_handoff_record_folded() { shots("folded", expanded = false) }
    @Test fun kit_handoff_record_open() { shots("open", expanded = true) }

    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_handoff_record", caps = mapOf("KitHandoffRecord" to "conversation"), wrap = { c -> CompositionLocalProvider(LocalKitRedactor provides Redactor()) { c() } }, content = record(true))
    }

    /** kit_handoff_record.dart:38,68-71: the KB rounding and the preview cut. */
    @Test fun rules() {
        assertEquals(48, KitHandoffRecordRules.kilobytes(48200))
        assertEquals(1, KitHandoffRecordRules.kilobytes(0))
        assertEquals(1, KitHandoffRecordRules.kilobytes(1024))
        assertEquals(2, KitHandoffRecordRules.kilobytes(1025))
        assertEquals(1 shl 20, KitHandoffRecordRules.kilobytes(Int.MAX_VALUE))
        val long = "a".repeat(12001)
        assertEquals("a".repeat(12000) + "\n…", KitHandoffRecordRules.preview(long))
        assertEquals("short", KitHandoffRecordRules.preview("short"))
    }
}
