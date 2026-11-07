package io.github.eslamasabry.oc.ui.kit.rb.o13

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.nav.KitBreadcrumb
import kotlin.test.Test

/** test/goldens/kit/kit_breadcrumb_golden_test.dart: the root "oc_app", the trail in the 16 dp rails. */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class KitBreadcrumbGateTest {
    private fun trail(segments: List<String>): @Composable () -> Unit = {
        Box(Modifier.padding(horizontal = 16.dp)) {
            KitBreadcrumb(rootLabel = "oc_app", segments = segments, onSelected = {})
        }
    }

    private val default = trail(listOf("lib", "ui", "screens"))
    private val collapsed = trail(listOf("lib", "ui", "screens", "settings", "advanced", "network", "proxy"))
    private val truncated = trail(listOf("a-very-long-folder-name-somebody-chose-for-this-work", "advanced"))

    @Test fun kit_breadcrumb_default() { FlutterGoldenGate.check("kit_breadcrumb_default", content = default) }

    @Test fun kit_breadcrumb_default_1280x800() { FlutterGoldenGate.check("kit_breadcrumb_default_1280x800", content = default) }

    @Test fun kit_breadcrumb_default_text2() { FlutterGoldenGate.check("kit_breadcrumb_default_text2", content = default) }

    @Test fun kit_breadcrumb_default_text2_1280x800() {
        FlutterGoldenGate.check("kit_breadcrumb_default_text2_1280x800", content = default)
    }

    @Test fun kit_breadcrumb_collapsed() { FlutterGoldenGate.check("kit_breadcrumb_collapsed", content = collapsed) }

    @Test fun kit_breadcrumb_root_only() { FlutterGoldenGate.check("kit_breadcrumb_root_only", content = trail(emptyList())) }

    @Test fun kit_breadcrumb_truncated() { FlutterGoldenGate.check("kit_breadcrumb_truncated", content = truncated) }

    // Keyboard focus on the "ui" crumb (index 1): Tab passes the root and "lib" first.
    @Test fun kit_breadcrumb_focused() {
        FlutterGoldenGate.check(
            "kit_breadcrumb_focused",
            act = { onRoot().performKeyInput { pressKey(Key.Tab); pressKey(Key.Tab); pressKey(Key.Tab) } },
            content = default,
        )
    }

    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_breadcrumb", caps = mapOf("KitBreadcrumb" to "list"), content = default) }
}
