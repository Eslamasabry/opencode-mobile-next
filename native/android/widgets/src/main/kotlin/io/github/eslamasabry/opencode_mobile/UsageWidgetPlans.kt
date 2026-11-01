package io.github.eslamasabry.opencode_mobile

internal object UsageWidgetPlans {
    /** Three of the monitor's 15-minute background cycles. */
    const val STALE_AFTER_MS = 45L * 60 * 1000
    private const val FIVE_HOURS_SECONDS = 18_000L
    private const val WEEK_SECONDS = 604_800L

    /** The 5-hour and weekly windows win when space is short. */
    private fun isPreferred(row: UsageWidgetRow): Boolean =
        row.seconds == FIVE_HOURS_SECONDS || row.seconds == WEEK_SECONDS

    /** Rows of one plan (same provider and profile), in feed order. */
    fun groupPlans(rows: List<UsageWidgetRow>): List<List<UsageWidgetRow>> {
        val groups = LinkedHashMap<String, MutableList<UsageWidgetRow>>()
        for (row in rows) {
            groups.getOrPut("${row.provider}|${row.profileId}|${row.profileName}") { mutableListOf() } += row
        }
        return groups.values.toList()
    }

    /** Defaults show measured limits even while the unified feed is empty or
     * only has setup rows. Once the person saves any widget choices, that feed
     * is authoritative: never resurrect a hidden plan through the old feed. */
    fun chooseFeed(
        subscriptions: List<UsageWidgetRow>?,
        profiles: List<UsageWidgetRow>,
        hasSelection: Boolean
    ): List<UsageWidgetRow> {
        return when {
            hasSelection -> subscriptions.orEmpty()
            subscriptions == null -> profiles
            subscriptions.any { !it.signInOnly } -> subscriptions
            else -> withMeasuredProfiles(subscriptions, profiles)
        }
    }

    private fun withMeasuredProfiles(
        subscriptions: List<UsageWidgetRow>,
        profiles: List<UsageWidgetRow>
    ): List<UsageWidgetRow> {
        val measured = profiles.filter { !it.signInOnly }
        if (measured.isEmpty()) return subscriptions
        val providers = measured.map { it.provider }.toSet()
        return measured + subscriptions.filter { it.provider !in providers }
    }

    /** Compact placement shows the tightest window; wide keeps the preferred pair. */
    fun columnRows(plan: List<UsageWidgetRow>, widthDp: Int): List<UsageWidgetRow> {
        val preferred = plan.filter { isPreferred(it) }
        val candidates = if (widthDp < UsageWidgetFit.TWO_COLUMNS_WIDTH_DP) plan
            else preferred.ifEmpty { plan }
        return UsageWidgetFit.columnIndices(candidates.map { it.left }, widthDp).map { candidates[it] }
    }

    fun planFit(plan: List<UsageWidgetRow>, now: Long): WidgetPlan {
        val first = plan.first()
        val tight = plan.minOf { if (it.signInOnly) 0 else it.left }
        val hasDetail = if (first.signInOnly) true else {
            first.forceStale || now - first.fetchedAt > STALE_AFTER_MS ||
                plan.any { it.resetsAt > now }
        }
        return WidgetPlan(
            needsFix = first.signInOnly,
            hasNumbers = !first.signInOnly,
            minLeft = tight,
            hasDetail = hasDetail
        )
    }
}
