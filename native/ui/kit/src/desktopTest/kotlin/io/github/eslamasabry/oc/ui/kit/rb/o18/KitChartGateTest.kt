package io.github.eslamasabry.oc.ui.kit.rb.o18

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.state.KitChart
import io.github.eslamasabry.oc.ui.kit.state.KitChartKind
import io.github.eslamasabry.oc.ui.kit.state.KitChartSeries
import kotlin.test.Test

/** The scenes of test/goldens/kit/kit_chart_golden_test.dart at 8302de537, each padded 16 dp on both sides (`_pad`). */
class KitChartGateTest {
    private val labels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    private fun pad(chart: @Composable (Modifier) -> Unit): @Composable () -> Unit = { chart(Modifier.padding(horizontal = 16.dp)) }

    private val default = pad {
        KitChart(
            kind = KitChartKind.Bar,
            unit = "ms",
            labels = labels,
            series = listOf(
                KitChartSeries("API", listOf(12.0, 18.0, 9.0, 22.0, 30.0, 16.0)),
                KitChartSeries("Database", listOf(8.0, 11.0, 14.0, 10.0, 19.0, 7.0)),
                KitChartSeries("Cache", listOf(2.0, 3.0, 2.0, 4.0, 3.0, 2.0)),
            ),
            modifier = it,
        )
    }
    private val line = pad {
        KitChart(
            kind = KitChartKind.Line,
            unit = "files",
            labels = labels,
            series = listOf(
                KitChartSeries("Added", listOf(3.0, 7.0, 4.0, 10.0, 6.0, 12.0)),
                KitChartSeries("Removed", listOf(1.0, 2.0, 6.0, 3.0, 2.0, 4.0)),
            ),
            modifier = it,
        )
    }
    private val negative = pad {
        KitChart(
            kind = KitChartKind.Bar,
            unit = "%",
            labels = labels,
            series = listOf(KitChartSeries("Change", listOf(4.0, -3.0, 7.0, -8.0, 2.0, -1.0))),
            modifier = it,
        )
    }
    private val single = pad {
        KitChart(kind = KitChartKind.Line, labels = listOf("Today"), series = listOf(KitChartSeries("Value", listOf(5.0))), modifier = it)
    }
    private val zero = pad {
        KitChart(kind = KitChartKind.Bar, labels = labels, series = listOf(KitChartSeries("Value", List(6) { 0.0 })), modifier = it)
    }

    @Test fun kit_chart_default() { FlutterGoldenGate.check("kit_chart_default", content = default) }
    @Test fun kit_chart_default_1280x800() { FlutterGoldenGate.check("kit_chart_default_1280x800", content = default) }
    @Test fun kit_chart_default_ar() { FlutterGoldenGate.check("kit_chart_default_ar", modes = listOf("dark"), content = default) }
    @Test fun kit_chart_default_text2() { FlutterGoldenGate.check("kit_chart_default_text2", modes = listOf("dark"), content = default) }
    @Test fun kit_chart_default_text2_1280x800() { FlutterGoldenGate.check("kit_chart_default_text2_1280x800", modes = listOf("dark"), content = default) }
    @Test fun kit_chart_line() { FlutterGoldenGate.check("kit_chart_line", content = line) }
    @Test fun kit_chart_negative() { FlutterGoldenGate.check("kit_chart_negative", content = negative) }
    @Test fun kit_chart_single() { FlutterGoldenGate.check("kit_chart_single", content = single) }
    @Test fun kit_chart_zero() { FlutterGoldenGate.check("kit_chart_zero", content = zero) }
    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_chart", caps = mapOf("KitChart" to "reading"), content = default) }
}
