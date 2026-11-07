package io.github.eslamasabry.oc.ui.kit.rb.o09

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.rb.o06.FlutterCapture
import io.github.eslamasabry.oc.ui.kit.state.KitLastKnown
import io.github.eslamasabry.oc.ui.kit.state.KitLastKnownRow
import kotlin.test.Test

/** test/goldens/kit/kit_last_known_golden_test.dart `_page`: the remembered list, 16 dp above and below. */
class KitLastKnownGateTest {
    private fun page(refreshing: Boolean = true): @Composable () -> Unit = {
        Box(Modifier.padding(vertical = 16.dp)) {
            KitLastKnown(
                updated = "Updated 12m ago",
                refreshing = refreshing,
                rows = listOf(
                    KitLastKnownRow("Fix the login redirect loop", "12m ago"),
                    KitLastKnownRow("Release notes for 1.0.45 and the store listing copy", "1h ago"),
                    KitLastKnownRow("Why is the build slow on CI?", "3h ago"),
                    KitLastKnownRow("Voice model download retries", "1d ago"),
                    KitLastKnownRow("New conversation"),
                ),
            )
        }
    }

    @Test fun kit_last_known_loading() { FlutterGoldenGate.check("kit_last_known_loading", wrap = { FlutterCapture(it) }, content = page()) }
    @Test fun kit_last_known_loading_1280x800() { FlutterGoldenGate.check("kit_last_known_loading_1280x800", wrap = { FlutterCapture(it) }, content = page()) }
    @Test fun kit_last_known_settled() { FlutterGoldenGate.check("kit_last_known_settled", wrap = { FlutterCapture(it) }, content = page(refreshing = false)) }
    @Test fun kit_last_known_loading_text2() {
        FlutterGoldenGate.check("kit_last_known_loading_text2", modes = listOf("dark"), wrap = { FlutterCapture(it) }, content = page())
    }
    @Test fun kit_last_known_loading_text2_1280x800() {
        FlutterGoldenGate.check("kit_last_known_loading_text2_1280x800", modes = listOf("dark"), wrap = { FlutterCapture(it) }, content = page())
    }

    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_last_known", caps = mapOf("KitLastKnown" to "list"), wrap = { FlutterCapture(it) }, content = page()) }
}
