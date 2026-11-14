package io.github.eslamasabry.oc.feature.chat.presentation

/** One part of the quiet line under a finished reply (D1, owner 2026-11-14), in the order it is said. */
public sealed interface ReplyFooterPart {
    /** When the reply finished, on the phone's clock. */
    public data class Clock(val atMs: Long) : ReplyFooterPart

    /** How long the reply took, from the prompt to its end. */
    public data class Took(val ms: Long) : ReplyFooterPart

    /** The model that wrote it, named only when it is not the conversation's model now. */
    public data class Model(val name: String) : ReplyFooterPart

    /** The tokens the server counted for it. */
    public data class Tokens(val amount: TokenAmount) : ReplyFooterPart

    /** What the server says it cost. */
    public data class Cost(val words: CostWords) : ReplyFooterPart
}

/** A token count said short: [whole] under a thousand, else [value] thousands ([Scale.K]) or millions ([Scale.M]). */
public data class TokenAmount(val whole: Long, val value: Double, val decimals: Int, val scale: Scale) {
    public enum class Scale { None, K, M }
}

/** A cost said in dollars: [Dollars] to the cent, or [UnderACent] for a cost that would read "$0.00". */
public sealed interface CostWords {
    public data class Dollars(val usd: Double) : CostWords

    public data object UnderACent : CostWords
}

/**
 * The footer under each finished reply (D1): "10:38 · 1m 12s · 19.3k tokens · $0.06", each part only when the data has
 * it, never a dash or an empty slot for a missing one, so the short form "10:38 · 1m 12s" reads finished. Clock time and
 * how long come from the messages' own times (every server sends them); the model only when it differs from the
 * conversation's newest reply's model; tokens and cost only when the server counted them (a free model counts no cost:
 * nothing is said, never "$0.00").
 */
public object ReplyFooterRules {
    public fun parts(meta: TurnMetadataUi): List<ReplyFooterPart> =
        buildList {
            meta.endedAtMs?.let { add(ReplyFooterPart.Clock(it)) }
            meta.tookMs?.takeIf { it > 0 }?.let { add(ReplyFooterPart.Took(it)) }
            meta.otherModel?.takeIf { it.isNotBlank() }?.let { add(ReplyFooterPart.Model(it)) }
            if (meta.tokens > 0) add(ReplyFooterPart.Tokens(tokens(meta.tokens)))
            if (meta.cost > 0.0) add(ReplyFooterPart.Cost(cost(meta.cost)))
        }

    /** 950 → 950; 19 340 → 19.3k; 193 400 → 193k; 1 250 000 → 1.3M. A whole value drops its ".0". */
    public fun tokens(n: Long): TokenAmount {
        if (n < THOUSAND) return TokenAmount(n, n.toDouble(), 0, TokenAmount.Scale.None)
        val (value, scale) = if (n < MILLION) n / THOUSAND.toDouble() to TokenAmount.Scale.K else n / MILLION.toDouble() to TokenAmount.Scale.M
        val rounded = kotlin.math.round(value * TEN) / TEN
        val decimals = if (rounded >= HUNDRED || rounded == kotlin.math.floor(rounded)) 0 else 1
        return TokenAmount(n, if (decimals == 0) kotlin.math.round(value) else rounded, decimals, scale)
    }

    /** A cost that rounds to less than a cent is "under $0.01", never "$0.00". */
    public fun cost(usd: Double): CostWords = if (usd < HALF_CENT) CostWords.UnderACent else CostWords.Dollars(usd)

    private const val THOUSAND = 1_000L
    private const val MILLION = 1_000_000L
    private const val TEN = 10.0
    private const val HUNDRED = 100.0
    private const val HALF_CENT = 0.005
}
