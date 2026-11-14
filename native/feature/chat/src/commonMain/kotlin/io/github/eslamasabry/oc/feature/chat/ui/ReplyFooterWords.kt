package io.github.eslamasabry.oc.feature.chat.ui

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.feature.chat.presentation.CostWords
import io.github.eslamasabry.oc.feature.chat.presentation.ReplyFooterPart
import io.github.eslamasabry.oc.feature.chat.presentation.ReplyFooterRules
import io.github.eslamasabry.oc.feature.chat.presentation.TokenAmount
import io.github.eslamasabry.oc.feature.chat.presentation.TurnMetadataUi
import io.github.eslamasabry.oc.ui.kit.text.kitClock
import io.github.eslamasabry.oc.ui.kitchat.header.rememberKitMask
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.chatReplyFooterCost
import io.github.eslamasabry.oc.ui.words.res.chatReplyFooterCostUnderCent
import io.github.eslamasabry.oc.ui.words.res.chatReplyFooterTokensK
import io.github.eslamasabry.oc.ui.words.res.chatReplyFooterTokensM
import io.github.eslamasabry.oc.ui.words.res.chatTranscriptTokens
import io.github.eslamasabry.oc.ui.words.res.kitWorkSeparator
import org.jetbrains.compose.resources.stringResource

/**
 * The quiet line under a finished reply (D1, owner 2026-11-14) in the phone's words: "10:38 · 1m 12s · 19.3k tokens ·
 * $0.06", each part only when [ReplyFooterRules] has it; empty when it has none. The clock is the phone's own (12 or 24
 * hours, its digits); the model name reads masked as every server word does.
 */
@Composable
internal fun replyFooterWords(meta: TurnMetadataUi): String {
    val parts = ReplyFooterRules.parts(meta)
    if (parts.isEmpty()) return ""
    val sep = stringResource(Res.string.kitWorkSeparator)
    val mask = rememberKitMask()
    return parts
        .map { part ->
            when (part) {
                is ReplyFooterPart.Clock -> kitClock(part.atMs).orEmpty()
                is ReplyFooterPart.Took -> durationWords(part.ms)
                is ReplyFooterPart.Model -> mask(part.name)
                is ReplyFooterPart.Tokens -> tokenWords(part.amount)
                is ReplyFooterPart.Cost -> costWords(part.words)
            }
        }
        .filter { it.isNotBlank() }
        .joinToString(sep)
}

@Composable
private fun tokenWords(amount: TokenAmount): String {
    val number = if (amount.decimals == 0) Numbers.format(amount.value.toLong()) else Numbers.decimal(amount.value, amount.decimals)
    return when (amount.scale) {
        TokenAmount.Scale.None -> stringResource(Res.string.chatTranscriptTokens, Numbers.format(amount.whole))
        TokenAmount.Scale.K -> stringResource(Res.string.chatReplyFooterTokensK, number)
        TokenAmount.Scale.M -> stringResource(Res.string.chatReplyFooterTokensM, number)
    }
}

@Composable
private fun costWords(words: CostWords): String =
    when (words) {
        is CostWords.Dollars -> stringResource(Res.string.chatReplyFooterCost, Numbers.decimal(words.usd, 2))
        CostWords.UnderACent -> stringResource(Res.string.chatReplyFooterCostUnderCent)
    }
