package io.github.eslamasabry.oc.ui.kit.rb.o06

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.*
import io.github.eslamasabry.oc.ui.design.type.*
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.foundation.*
import io.github.eslamasabry.oc.ui.kit.theme.*
import io.github.eslamasabry.oc.ui.kit.icon.*
import io.github.eslamasabry.oc.ui.kit.row.*
import io.github.eslamasabry.oc.ui.kit.chip.*
import io.github.eslamasabry.oc.ui.kit.input.*
import io.github.eslamasabry.oc.ui.kit.state.*
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import kotlin.test.Test

class KitKeyValueGateTest {
    @Composable private fun scene() {
        KitKeyValue(listOf(KitKeyValueRow("Duration", "4 min 12 s"), KitKeyValueRow("Files changed", "7"), KitKeyValueRow("Cost", "$0.42"), KitKeyValueRow("Branch", "feature/a-long-branch-name-that-wraps-and-is-never-cut")), Modifier.padding(horizontal = 16.dp))
    }
    @Test fun kit_key_value_default() { FlutterGoldenGate.check(wrap = { content -> FlutterCapture(content) }, name = "kit_key_value_default", modes = listOf("dark", "light")) { scene() } }
    @Test fun kit_key_value_default_1280x800() { FlutterGoldenGate.check(wrap = { content -> FlutterCapture(content) }, name = "kit_key_value_default_1280x800", modes = listOf("dark", "light")) { scene() } }
    @Test fun kit_key_value_default_ar() {
        FlutterGoldenGate.check(
            wrap = { content ->
                // kit_gallery.dart:64-88 keeps captureTheme's Geist primary face;
                // the Arabic locale changes direction and adds a fallback, without
                // calling AppTheme.forLocale. These fact strings are all Latin.
                val fonts = rememberKitFonts(arabic = false)
                val typography = remember(fonts) { KitTypography(fonts, arabic = false) }
                CompositionLocalProvider(LocalKitTypography provides typography) {
                    FlutterCapture(content)
                }
            },
            name = "kit_key_value_default_ar",
            modes = listOf("dark"),
        ) { scene() }
    }
    @Test fun kit_key_value_default_text2() { FlutterGoldenGate.check(wrap = { content -> FlutterCapture(content) }, name = "kit_key_value_default_text2", modes = listOf("dark")) { scene() } }
    @Test fun kit_key_value_default_text2_1280x800() { FlutterGoldenGate.check(wrap = { content -> FlutterCapture(content) }, name = "kit_key_value_default_text2_1280x800", modes = listOf("dark")) { scene() } }
    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_key_value", caps = mapOf("KitKeyValue" to "reading")) { scene() } }
}
