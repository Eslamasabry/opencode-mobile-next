package io.github.eslamasabry.oc.feature.usage.presentation

import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.engine.usage.UsageCalendar
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

    /**
     * A provider's share of [total], 0..1; null when either cost cannot be stated or nothing was spent (a share of
     * zero dollars is not a number the page can show).
     */
    public fun share(cost: Double?, total: Double): Double? =
        if (cost == null || !cost.isFinite() || !total.isFinite() || total <= 0.0) null
        else (cost / total).coerceIn(0.0, 1.0)

    /**
     * Agent steps for every calendar day of the report, oldest first, with the days the server left out as zero
     * (B-160 chart). Only the server's day labels are used; nothing is spread or estimated. Empty when the zone
     * cannot be read or the report covers no whole day.
     */
    public fun dailySteps(report: UsageReport, zoneId: String): List<UsageDay> {
        val byDay = report.activity.groupBy { it.date }.mapValues { (_, v) -> v.sumOf { it.steps } }
        return UsageCalendar.daysBetween(report.fromMs, report.toMs, zoneId).map { UsageDay(it, byDay[it] ?: 0) }
    }

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
    public fun money(cost: Double, symbolAfter: Boolean = false): String {
        if (!cost.isFinite()) return "—"
        val value = abs(cost)
        val symbols = Numbers.symbols()
        val decimals = if (value >= 0.01 || value == 0.0) 2 else 6
        var amount = Numbers.decimal(value, decimals, symbols)
        if (decimals > 2) {
            val keep = amount.indexOf(symbols.decimal) + 3
            while (amount.length > keep && amount.last() == symbols.zeroDigit) amount = amount.dropLast(1)
        }
        val sign = if (cost < 0 && amount.any { it != symbols.zeroDigit && it != symbols.decimal }) symbols.minus.toString() else ""
        return if (symbolAfter) "$sign$amount $" else "$sign$$amount"
    }

    /**
     * A count with thousands grouped: 1234567 → "1,234,567", in the phone's digits (C09: Usage wrote Western digits
     * while Conversations and Schedules wrote the phone's, so one Arabic screen showed two kinds).
     */
    public fun count(value: Long): String = Numbers.format(value)

    /** Compact tokens: 950, 12.3K, 4.5M, 1.2B, in the phone's digits and decimal mark. */
    public fun compact(value: Long): String = phone(latinCompact(value))

    private fun latinCompact(value: Long): String {
        val v = abs(value)
        val sign = if (value < 0) "-" else ""
        return when {
            v < 1_000 -> sign + v
            v < 1_000_000 -> sign + oneDecimal(v, 1_000) + "K"
            v < 1_000_000_000 -> sign + oneDecimal(v, 1_000_000) + "M"
            else -> sign + oneDecimal(v, 1_000_000_000) + "B"
        }
    }

    /** A share 0..1 as a whole percent: 0.876 → "88%", in the phone's digits. */
    public fun percent(share: Double): String = Numbers.format((share * 100).roundToLong()) + "%"

    /** Latin figures ("12.3") in the phone's digits and decimal mark. */
    private fun phone(latin: String): String {
        val symbols = Numbers.symbols()
        return Numbers.digits(latin, symbols).replace('.', symbols.decimal).replace('-', symbols.minus)
    }

    private fun oneDecimal(value: Long, unit: Long): String {
        val tenths = (value * 10 + unit / 2) / unit
        val whole = tenths / 10
        val rest = tenths % 10
        return if (rest == 0L) whole.toString() else "$whole.$rest"
    }
}
