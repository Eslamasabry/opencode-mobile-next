package io.github.eslamasabry.oc.feature.usage.presentation

import kotlin.math.abs
import kotlin.math.roundToLong

/** One provider's totals, rolled up from the server's model records (B-PROTO-121). */
public data class UsageProvider(
    val providerId: String,
    /** Distinct model ids: a variant of a model is not another model. */
    val models: Int,
    val steps: Long,
    val tokens: Long,
    /** Null when a subtotal cannot be stated (an overflow or a non-finite value). */
    val cost: Double?,
)

/** Pure numbers for the Usage page: rollups and plain formatting (no Compose, no locale objects). */
public object UsageFigures {
    /** Providers by the ids the server returned, most expensive first; no records = no providers. */
    public fun providers(models: List<UsageModel>): List<UsageProvider> =
        models
            .groupBy { it.providerId }
            .map { (provider, rows) ->
                val sum = rows.sumOf { it.cost }
                UsageProvider(
                    providerId = provider,
                    models = rows.map { it.modelId }.distinct().size,
                    steps = rows.sumOf { it.steps },
                    tokens = rows.sumOf { it.tokens.total },
                    cost = sum.takeIf { it.isFinite() && rows.all { row -> row.cost.isFinite() } },
                )
            }
            .sortedWith(compareByDescending<UsageProvider> { it.cost ?: -1.0 }.thenBy { it.providerId })

    /** Models most expensive first, then by steps. */
    public fun models(models: List<UsageModel>): List<UsageModel> =
        models.sortedWith(compareByDescending<UsageModel> { it.cost }.thenByDescending { it.steps })

    /** Days with activity, newest first, at most [limit]. */
    public fun activeDays(days: List<UsageDay>, limit: Int = 31): List<UsageDay> =
        days.filter { it.steps > 0 }.sortedByDescending { it.date }.take(limit)

    /** The smallest cost the page states as a number; below it (and above 0) the page says "Less than $0.000001". */
    public const val TINY_COST: Double = 0.000001

    public fun isTiny(cost: Double): Boolean = cost > 0 && cost < TINY_COST

    /**
     * US-dollar estimate: two decimals from one cent, otherwise up to six (so small runs are not shown as $0.00),
     * thousands grouped. The caller handles [isTiny] and non-finite values.
     */
    public fun money(cost: Double): String {
        if (!cost.isFinite()) return "—"
        val negative = cost < 0
        val value = abs(cost)
        val decimals = if (value >= 0.01 || value == 0.0) 2 else 6
        var scale = 1L
        repeat(decimals) { scale *= 10 }
        val scaled = (value * scale).roundToLong()
        val whole = scaled / scale
        var fraction = (scaled % scale).toString().padStart(decimals, '0')
        if (decimals > 2) fraction = fraction.trimEnd('0').padEnd(2, '0')
        return (if (negative) "-" else "") + "$" + grouped(whole) + "." + fraction
    }

    /** A count with thousands grouped: 1234567 → "1,234,567". */
    public fun count(value: Long): String = (if (value < 0) "-" else "") + grouped(abs(value))

    /** Compact tokens: 950, 12.3K, 4.5M, 1.2B. */
    public fun compact(value: Long): String {
        val v = abs(value)
        val sign = if (value < 0) "-" else ""
        return when {
            v < 1_000 -> sign + v
            v < 1_000_000 -> sign + oneDecimal(v, 1_000) + "K"
            v < 1_000_000_000 -> sign + oneDecimal(v, 1_000_000) + "M"
            else -> sign + oneDecimal(v, 1_000_000_000) + "B"
        }
    }

    /** A share 0..1 as a whole percent: 0.876 → "88%". */
    public fun percent(share: Double): String = (share * 100).roundToLong().toString() + "%"

    private fun oneDecimal(value: Long, unit: Long): String {
        val tenths = (value * 10 + unit / 2) / unit
        val whole = tenths / 10
        val rest = tenths % 10
        return if (rest == 0L) whole.toString() else "$whole.$rest"
    }

    private fun grouped(value: Long): String {
        val digits = value.toString()
        val out = StringBuilder()
        digits.forEachIndexed { i, c ->
            if (i > 0 && (digits.length - i) % 3 == 0) out.append(',')
            out.append(c)
        }
        return out.toString()
    }
}
