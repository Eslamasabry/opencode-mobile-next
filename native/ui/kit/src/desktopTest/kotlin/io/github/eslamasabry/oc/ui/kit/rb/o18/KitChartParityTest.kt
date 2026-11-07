package io.github.eslamasabry.oc.ui.kit.rb.o18

import io.github.eslamasabry.oc.ui.kit.state.ChartScale
import io.github.eslamasabry.oc.ui.kit.state.KitChart
import io.github.eslamasabry.oc.ui.kit.state.KitChartKind
import io.github.eslamasabry.oc.ui.kit.state.KitChartSeries
import io.github.eslamasabry.oc.ui.kit.state.KitChartWords
import io.github.eslamasabry.oc.ui.kit.state.fixed
import kotlin.test.Test
import kotlin.test.assertEquals

/** lib/ui/kit/kit_chart.dart at 8302de537: `_Scale`, `summaryOf`, `_plain`, `_pointsOf` and the contract caps. */
class KitChartParityTest {
    private val english = KitChartWords(
        barChart = "Bar chart",
        lineChart = "Line chart",
        seriesCount = { "$it series" },
        pointCount = { if (it == 1) "1 point" else "$it points" },
        highest = { w, v -> "highest $w: $v" },
        noData = "no data",
    )
    private val labels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    private val three = listOf(
        KitChartSeries("API", listOf(12.0, 18.0, 9.0, 22.0, 30.0, 16.0)),
        KitChartSeries("Database", listOf(8.0, 11.0, 14.0, 10.0, 19.0, 7.0)),
        KitChartSeries("Cache", listOf(2.0, 3.0, 2.0, 4.0, 3.0, 2.0)),
    )

    @Test fun caps() {
        assertEquals(3, KitChart.MAX_SERIES)
        assertEquals(30, KitChart.MAX_POINTS)
        assertEquals(30, KitChart.pointsOf(List(40) { "x" }, emptyList()))
        assertEquals(8, KitChart.pointsOf(labels, listOf(KitChartSeries("a", List(8) { 1.0 }))))
    }

    @Test fun scale_always_holds_zero_and_steps_one_two_five() {
        val bars = ChartScale.of(three.map { it.values })
        assertEquals(listOf(0.0, 10.0, 20.0, 30.0), bars.ticks)
        assertEquals(listOf("0", "10", "20", "30"), bars.ticks.map(bars::format))
        val neg = ChartScale.of(listOf(listOf(4.0, -3.0, 7.0, -8.0, 2.0, -1.0)))
        assertEquals(listOf(-10.0, -5.0, 0.0, 5.0, 10.0), neg.ticks)
        val zero = ChartScale.of(listOf(List(6) { 0.0 }))
        assertEquals(listOf("0.0", "0.5", "1.0"), zero.ticks.map(zero::format))
        val single = ChartScale.of(listOf(listOf(5.0)))
        assertEquals(listOf(0.0, 2.0, 4.0, 6.0), single.ticks)
        val nan = ChartScale.of(listOf(listOf(Double.NaN, Double.POSITIVE_INFINITY)))
        assertEquals(listOf("0.0", "0.5", "1.0"), nan.ticks.map(nan::format))
    }

    @Test fun thousands_read_as_k() {
        assertEquals("2k", ChartScale(0.0, 4000.0, 1000.0).format(2000.0))
        assertEquals("2.5k", ChartScale(0.0, 5000.0, 2500.0).format(2500.0))
        assertEquals("500", ChartScale(0.0, 5000.0, 2500.0).format(500.0))
        assertEquals("0.0", ChartScale(-1.0, 1.0, 0.5).format(-0.0001))
    }

    @Test fun summary_names_kind_series_points_and_highest() {
        assertEquals(
            "Bar chart, 3 series, 6 points; highest API, Fri: 30 ms",
            KitChart.summaryOf(KitChartKind.Bar, labels, three, "ms", english),
        )
        assertEquals(
            "Bar chart, 1 series, 6 points; highest Wed: 7%",
            KitChart.summaryOf(KitChartKind.Bar, labels, listOf(KitChartSeries("Change", listOf(4.0, -3.0, 7.0, -8.0, 2.0, -1.0))), "%", english),
        )
        assertEquals(
            "Line chart, 1 series, 1 point; highest Today: 5",
            KitChart.summaryOf(KitChartKind.Line, listOf("Today"), listOf(KitChartSeries("Value", listOf(5.0))), null, english),
        )
        assertEquals(
            "Line chart, 1 series, 2 points; highest 2: 1.5",
            KitChart.summaryOf(KitChartKind.Line, emptyList(), listOf(KitChartSeries("v", listOf(1.0, 1.5))), "", english),
        )
        assertEquals(
            "Bar chart, 1 series, 1 point; no data",
            KitChart.summaryOf(KitChartKind.Bar, listOf("a"), listOf(KitChartSeries("v", listOf(Double.NaN))), "ms", english),
        )
    }

    @Test fun plain_numbers() {
        assertEquals("30", KitChart.plain(30.0))
        assertEquals("12.5", KitChart.plain(12.5))
        assertEquals("1.23", KitChart.plain(1.234))
        assertEquals("0.1", KitChart.plain(0.1))
        assertEquals("-3", KitChart.plain(-3.0))
        assertEquals("-0.5", fixed(-0.5, 1))
    }
}
