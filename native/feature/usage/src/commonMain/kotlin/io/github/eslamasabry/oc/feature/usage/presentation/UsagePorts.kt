package io.github.eslamasabry.oc.feature.usage.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.StateFlow

/** A connected server whose usage can be read. */
public data class UsageServer(val id: ProfileId, val name: String)

/** A project the server knows, for the project filter. [id] is the server's project id, sent as `project`. */
public data class UsageProject(val id: String, val name: String)

/** What the server reports for one range and scope (Flutter `UsageStatistics`). Costs are the server's estimates. */
public data class UsageReport(
    val fromMs: Long,
    val toMs: Long,
    val sessions: Long,
    val subagents: Long,
    val prompts: Long,
    val steps: Long,
    val activeDays: Long,
    val streak: Long,
    val cost: Double,
    val tokens: UsageTokens,
    /** Null when the server reports no tool totals. */
    val tools: UsageTools?,
    val models: ImmutableList<UsageModel>,
    val activity: ImmutableList<UsageDay>,
) {
    val empty: Boolean
        get() = sessions == 0L && prompts == 0L && steps == 0L && tokens.total == 0L && cost == 0.0
}

public data class UsageTokens(
    val input: Long = 0,
    val output: Long = 0,
    val reasoning: Long = 0,
    val cacheRead: Long = 0,
    val cacheWrite: Long = 0,
) {
    val total: Long
        get() = input + output + reasoning + cacheRead + cacheWrite
}

public data class UsageTools(val calls: Long, val succeeded: Long, val failed: Long, val unfinished: Long) {
    /** Share of finished calls that succeeded, 0..1; null when nothing finished (unfinished calls are excluded). */
    val successRate: Double?
        get() = (succeeded + failed).takeIf { it > 0 }?.let { succeeded.toDouble() / it }
}

public data class UsageModel(
    val providerId: String,
    val modelId: String,
    val variant: String?,
    val steps: Long,
    val tokens: UsageTokens,
    val cost: Double,
)

/** One calendar day (`YYYY-MM-DD` in the requested time zone) with its agent steps. */
public data class UsageDay(val date: String, val steps: Long)

/** The server side of Usage, implemented by the app runtime over the live connection graph. Reads only. */
public interface UsageSource {
    public val servers: StateFlow<ImmutableList<UsageServer>>

    public val active: StateFlow<ProfileId?>

    /** Projects seen on this server (for the filter). */
    public suspend fun projects(profile: ProfileId): Outcome<ImmutableList<UsageProject>>

    /** Failure.Unavailable when the server has no usage statistics. */
    public suspend fun statistics(profile: ProfileId, query: UsageQuery): Outcome<UsageReport>
}

/** The device's time zone id (IANA), or null when it cannot be read: usage is never silently dated in UTC. */
public fun interface UsageTimeZone {
    public fun id(): String?
}
