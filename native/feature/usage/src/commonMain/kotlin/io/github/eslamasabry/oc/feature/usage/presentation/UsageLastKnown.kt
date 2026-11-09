package io.github.eslamasabry.oc.feature.usage.presentation

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.Serializable

/**
 * A usage report as last read: names and numbers only (no keys, no accounts), exactly the fields the real page
 * draws. Costs are the server's estimates.
 */
@Serializable
public data class KeptUsageReport(
    val fromMs: Long,
    val toMs: Long,
    val sessions: Long,
    val subagents: Long,
    val prompts: Long,
    val steps: Long,
    val activeDays: Long,
    val streak: Long,
    val cost: Double,
    val tokens: List<Long>,
    val tools: List<Long>?,
    val models: List<KeptModel>,
    val activity: List<Pair<String, Long>>,
    val coversRequest: Boolean = true,
) {
    @Serializable
    public data class KeptModel(
        val providerId: String,
        val modelId: String,
        val variant: String?,
        val steps: Long,
        val tokens: List<Long>,
        val cost: Double,
    )

    public fun report(): UsageReport =
        UsageReport(
            fromMs, toMs, sessions, subagents, prompts, steps, activeDays, streak, cost, tokens(this.tokens),
            tools?.takeIf { it.size == 4 }?.let { UsageTools(it[0], it[1], it[2], it[3]) },
            models.map { UsageModel(it.providerId, it.modelId, it.variant, it.steps, tokens(it.tokens), it.cost) }
                .toImmutableList(),
            activity.map { UsageDay(it.first, it.second) }.toImmutableList(),
            coversRequest,
        )

    public companion object {
        private fun list(t: UsageTokens) = listOf(t.input, t.output, t.reasoning, t.cacheRead, t.cacheWrite)

        private fun tokens(l: List<Long>) =
            UsageTokens(l.getOrElse(0) { 0 }, l.getOrElse(1) { 0 }, l.getOrElse(2) { 0 }, l.getOrElse(3) { 0 }, l.getOrElse(4) { 0 })

        public fun of(r: UsageReport): KeptUsageReport =
            KeptUsageReport(
                r.fromMs, r.toMs, r.sessions, r.subagents, r.prompts, r.steps, r.activeDays, r.streak, r.cost,
                list(r.tokens),
                r.tools?.let { listOf(it.calls, it.succeeded, it.failed, it.unfinished) },
                r.models.map { KeptModel(it.providerId, it.modelId, it.variant, it.steps, list(it.tokens), it.cost) },
                r.activity.map { it.date to it.steps },
                r.coversRequest,
            )
    }
}

/**
 * Cache-first (owner rule, 2026-11-09): Usage opens on the report it last read for the same server, range, project
 * and time zone, then the live read replaces it in place. The app binds this to its last-known store; without a
 * binding the page opens as before. [key] names range, project scope and zone within the profile.
 */
public interface UsageLastKnown {
    public fun peek(profile: ProfileId, key: String): KeptUsageReport?

    public suspend fun read(profile: ProfileId, key: String): KeptUsageReport?

    public suspend fun keep(profile: ProfileId, key: String, report: KeptUsageReport)
}
