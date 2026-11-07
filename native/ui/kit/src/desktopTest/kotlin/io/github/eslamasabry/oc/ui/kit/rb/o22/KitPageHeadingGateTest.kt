package io.github.eslamasabry.oc.ui.kit.rb.o22

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.frame.KitPageHeading
import kotlin.test.Test

/** test/goldens/kit/kit_page_heading_golden_test.dart: the switcher line and the plain line. */
class KitPageHeadingGateTest {
    private val switcher: @Composable () -> Unit = {
        Column {
            KitPageHeading(
                title = "Files",
                line = "neon-breaker",
                onLine = {},
                lineTapLabel = "Project neon-breaker. Switch project",
            )
        }
    }
    private val plain: @Composable () -> Unit = { Column { KitPageHeading(title = "levels", line = "neon-breaker / src") } }

    @Test fun kit_page_heading_switcher() { FlutterGoldenGate.check("kit_page_heading_switcher", content = switcher) }
    @Test fun kit_page_heading_switcher_1280x800() { FlutterGoldenGate.check("kit_page_heading_switcher_1280x800", content = switcher) }
    @Test fun kit_page_heading_switcher_text2() { FlutterGoldenGate.check("kit_page_heading_switcher_text2", content = switcher) }
    @Test fun kit_page_heading_disabled() { FlutterGoldenGate.check("kit_page_heading_disabled", content = plain) }

    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_page_heading", caps = mapOf("KitPageHeading" to "reading"), content = switcher)
    }
}
