package io.github.eslamasabry.oc.ui.kit.rb.o13

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.test.ExperimentalTestApi
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.nav.KitBreadcrumb
import kotlin.test.Test

/** The default scenes of test/goldens/kit/kit_breadcrumb_golden_test.dart: oc_app › lib › ui › screens. */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class KitBreadcrumbGateTest {
    private val trail: @Composable () -> Unit = {
        KitBreadcrumb(rootLabel = "oc_app", segments = listOf("lib", "ui", "screens"), onSelected = {})
    }

    @Test fun kit_breadcrumb_default() { FlutterGoldenGate.check("kit_breadcrumb_default", content = trail) }

    @Test fun kit_breadcrumb_default_1280x800() { FlutterGoldenGate.check("kit_breadcrumb_default_1280x800", content = trail) }

    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_breadcrumb", caps = mapOf("KitBreadcrumb" to "list"), content = trail) }
}
