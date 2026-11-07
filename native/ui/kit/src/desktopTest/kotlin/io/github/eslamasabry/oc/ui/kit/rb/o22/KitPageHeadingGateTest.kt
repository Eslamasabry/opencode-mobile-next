package io.github.eslamasabry.oc.ui.kit.rb.o22

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.frame.KitPageHeading
import kotlin.test.Test

/**
 * The scenes of test/goldens/kit/kit_page_heading_golden_test.dart: the Files heading with "neon-breaker" as its
 * project-switcher line (`_switcher()`), and a folder's plain line "neon-breaker / src" (`_plain()`, golden name
 * `kit_page_heading_disabled`).
 *
 * Approximation: native [KitPageHeading] has no `lineTapLabel` ("Project neon-breaker. Switch project") and draws a
 * tappable line as the needs-you link (KitHeadingLine); the Dart switcher line is the quiet, secondary one.
 */
class KitPageHeadingGateTest {
    private val switcher: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth()) { KitPageHeading("Files", line = "neon-breaker", onLine = {}) }
    }

    private val plain: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth()) { KitPageHeading("levels", line = "neon-breaker / src") }
    }

    @Test fun kit_page_heading_disabled() { FlutterGoldenGate.check("kit_page_heading_disabled", content = plain) }

    @Test fun kit_page_heading_switcher() { FlutterGoldenGate.check("kit_page_heading_switcher", content = switcher) }

    @Test fun kit_page_heading_switcher_1280x800() { FlutterGoldenGate.check("kit_page_heading_switcher_1280x800", content = switcher) }

    @Test fun kit_page_heading_switcher_text2() { FlutterGoldenGate.check("kit_page_heading_switcher_text2", content = switcher) }

    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_page_heading", caps = mapOf("KitPageHeading" to "reading"), content = switcher)
    }
}
