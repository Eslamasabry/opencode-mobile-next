package io.github.eslamasabry.oc.feature.chat.presentation

import io.github.eslamasabry.oc.engine.integrations.usage.COMPOSER_LIMIT_APPROACHING
import io.github.eslamasabry.oc.engine.integrations.usage.composerLimitProvider
import io.github.eslamasabry.oc.engine.transcript.turnfacts.agentRunFailure
import io.github.eslamasabry.oc.feature.chat.world.ComposerModel
import io.github.eslamasabry.oc.feature.chat.world.ModelKey

// B-169 (docs/designs/b169-quota-switch.md, owner-approved 2026-11-08): when a limit stops a reply in an OpenCode 2
// conversation, Switch model picks another model and sends the stopped prompt again with it. Pure rules; the words
// live in the screen.

/** What a failed reply offers for its limit (design §1, §5, §6). */
public enum class LimitSwitchOffer {
    /** Not a lasting limit, or not a conversation whose prompts carry their own model: today's fixes. */
    None,

    /** "Switch model". */
    Switch,

    /** This stopped prompt was switched [LimitSwitchRules.MAX_SWITCHES] times: only the reset and Connect a provider. */
    Spent,
}

public object LimitSwitchRules {
    /** Each stopped prompt is switched at most this often (the loop guard, design §5). */
    public const val MAX_SWITCHES: Int = 3

    /**
     * The offer for a failed reply whose server text is [reason]. [promptModelChoice] is the server's
     * `ServerCapabilities.promptModelChoice` (never the flavor); [switches] is how often this prompt was switched
     * already.
     */
    public fun offer(reason: String?, nowMs: Long?, promptModelChoice: Boolean, switches: Int): LimitSwitchOffer =
        when {
            !promptModelChoice -> LimitSwitchOffer.None
            !agentRunFailure(reason, nowMs).out -> LimitSwitchOffer.None
            switches >= MAX_SWITCHES -> LimitSwitchOffer.Spent
            else -> LimitSwitchOffer.Switch
        }

    /** The stopped prompt's identity across switches: its words (each switch sends the same words again). */
    public fun promptKey(text: String): String = text.trim()
}

/**
 * Models a limit stopped in this conversation (design §5). A mark lasts until its reset when one is known, otherwise
 * for as long as this holder lives (the app session's open conversation).
 */
public class UsedUpModels {
    private val marks = LinkedHashMap<ModelKey, Long?>()

    public fun mark(model: ModelKey, untilMs: Long?) {
        marks[model] = untilMs
    }

    public fun isUsedUp(model: ModelKey, nowMs: Long?): Boolean {
        if (model !in marks) return false
        val until = marks[model] ?: return true
        return nowMs == null || nowMs < until
    }

    public fun all(nowMs: Long?): Set<ModelKey> = marks.keys.filterTo(LinkedHashSet()) { isUsedUp(it, nowMs) }
}

/** One row of the switch sheet. */
public data class SwitchRow(
    val model: ComposerModel,
    /** The catalog says this model costs exactly nothing ("Free · may use your prompts"). */
    val free: Boolean,
    /** The conversation is already longer than this model takes: shown, but not choosable. */
    val tooLong: Boolean,
    /** The provider's plan reading (percent used), when Usage has one. */
    val usedPercent: Double?,
)

/** The switch sheet (design §3): one pre-selected [suggestion] and the rows in picker order. */
public data class SwitchSheet(val suggestion: ModelKey?, val rows: List<SwitchRow>)

public object SwitchModelRules {
    /**
     * Free only when the catalog says both prices are exactly 0. An unknown price (null) is never free.
     */
    public fun isFree(model: ComposerModel): Boolean = model.inputCost == 0.0 && model.outputCost == 0.0

    /** A known context window ([ComposerModel.contextLimit] > 0) smaller than the conversation already is. */
    public fun tooLong(model: ComposerModel, contextTokens: Long?): Boolean =
        model.contextLimit > 0 && contextTokens != null && contextTokens > model.contextLimit

    /**
     * The sheet for a reply [failed] on. [choices] is the catalog in picker order; [usedUp] the models marked in this
     * conversation; [recent] the person's recent models, newest first; [readings] the plan readings by Usage's
     * provider id ([composerLimitProvider]); [contextTokens] the conversation's current size.
     */
    public fun sheet(
        choices: List<ComposerModel>,
        failed: ModelKey?,
        usedUp: Set<ModelKey>,
        recent: List<ComposerModel> = emptyList(),
        readings: Map<String, Double> = emptyMap(),
        contextTokens: Long? = null,
    ): SwitchSheet {
        fun excluded(m: ComposerModel): Boolean {
            val key = ModelKey.of(m)
            return !m.enabled || key == failed || key in usedUp
        }
        val rows =
            choices
                .filterNot(::excluded)
                .map {
                    SwitchRow(
                        model = it,
                        free = isFree(it),
                        tooLong = tooLong(it, contextTokens),
                        usedPercent = readings[composerLimitProvider(it.providerId)],
                    )
                }
        val usable = rows.filterNot { it.tooLong }
        val failedProvider = failed?.providerId?.let(::composerLimitProvider)
        // 1. Another provider whose plan reading is under the "approaching" line.
        val roomy =
            usable.firstOrNull { row ->
                val percent = row.usedPercent
                percent != null &&
                    percent < COMPOSER_LIMIT_APPROACHING &&
                    composerLimitProvider(row.model.providerId) != failedProvider
            }
        // 2. The person's recent models, in their order.
        val usableKeys = usable.mapTo(HashSet()) { ModelKey.of(it.model) }
        val recentPick = recent.map(ModelKey::of).firstOrNull { it in usableKeys }
        // 3. Unranked: picker order.
        val suggestion = roomy?.model?.let(ModelKey::of) ?: recentPick ?: usable.firstOrNull()?.model?.let(ModelKey::of)
        return SwitchSheet(suggestion, rows)
    }
}
