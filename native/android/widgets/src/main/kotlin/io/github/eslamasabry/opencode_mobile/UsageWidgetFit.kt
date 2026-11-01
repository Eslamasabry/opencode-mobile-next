package io.github.eslamasabry.opencode_mobile

/** One plan as the fit logic sees it: sizes and urgency only, no text. */
internal data class WidgetPlan(
    /** Setup or sign-in: the row carries an action instead of numbers. */
    val needsFix: Boolean,
    /** Two columns of numbers (true) or one (false); ignored for [needsFix]. */
    val hasNumbers: Boolean,
    /** Share left on the tightest window, 0..100. */
    val minLeft: Int,
    /** There is a small reset or "updated" line that could be shown. */
    val hasDetail: Boolean
)

internal data class PickedPlan(val index: Int, val showDetail: Boolean)

/**
 * Chooses which plans fit the widget without clipping a row. The widget
 * cannot measure itself, so heights are estimates in dp that err on the safe
 * side. Available measured limits come first, then rows that need setup;
 * within those groups, the tightest limit is kept first.
 * the result is back in the original order.
 */
internal object UsageWidgetFit {
    const val MIN_WIDTH_DP = 120
    const val TWO_COLUMNS_WIDTH_DP = 180

    /** Narrow launcher placements show the tightest window without squeezed columns. */
    fun columnIndices(left: List<Int>, widthDp: Int): List<Int> {
        if (left.isEmpty()) return emptyList()
        return if (widthDp < TWO_COLUMNS_WIDTH_DP) {
            listOf(left.indices.minBy { left[it] })
        } else {
            left.indices.take(2)
        }
    }

    const val FRAME_DP = 50
    const val SLACK_DP = 6
    const val NUMBERS_DP = 48
    const val SETUP_DP = 23
    const val DETAIL_DP = 13
    const val DEFAULT_HEIGHT_DP = 160

    fun pick(plans: List<WidgetPlan>, heightDp: Int, maxRows: Int): List<PickedPlan> {
        val height = if (heightDp > 0) heightDp else DEFAULT_HEIGHT_DP
        var budget = height - FRAME_DP - SLACK_DP
        val urgent = plans.indices.sortedWith(
            compareBy<Int>({ if (plans[it].hasNumbers) 0 else 1 }, { plans[it].minLeft }, { it })
        )
        val chosen = mutableListOf<Int>()
        for (i in urgent) {
            if (chosen.size >= maxRows) break
            val cost = if (plans[i].needsFix) SETUP_DP else NUMBERS_DP
            if (cost <= budget) {
                chosen += i
                budget -= cost
            }
        }
        val withDetail = HashSet<Int>()
        for (i in chosen) {
            if (plans[i].hasDetail && DETAIL_DP <= budget) {
                withDetail += i
                budget -= DETAIL_DP
            }
        }
        return chosen.sorted().map { PickedPlan(it, it in withDetail) }
    }
}
