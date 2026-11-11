package io.github.eslamasabry.oc.feature.chat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ContextBreakdownKind
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ContextLevel
import io.github.eslamasabry.oc.feature.chat.presentation.ContextUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kitchat.turn.KitContextLevel
import io.github.eslamasabry.oc.ui.kitchat.turn.KitContextRing
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.e7SharedAccumulatedCostReportedByServer
import io.github.eslamasabry.oc.ui.words.res.e7SharedAssistantText
import io.github.eslamasabry.oc.ui.words.res.e7SharedSessionTokensReportedByServer
import io.github.eslamasabry.oc.ui.words.res.e7SharedSessionTotals
import io.github.eslamasabry.oc.ui.words.res.historyLoadedCost
import io.github.eslamasabry.oc.ui.words.res.historyServerTotalsNote
import io.github.eslamasabry.oc.ui.words.res.e7SharedContextLimit
import io.github.eslamasabry.oc.ui.words.res.e7SharedContextLimitUnavailable
import io.github.eslamasabry.oc.ui.words.res.e7SharedEstimatedInputMakeup
import io.github.eslamasabry.oc.ui.words.res.e7SharedLatestAssistantRequestIncludingCacheActivity
import io.github.eslamasabry.oc.ui.words.res.e7SharedMessages
import io.github.eslamasabry.oc.ui.words.res.e7SharedOtherContext
import io.github.eslamasabry.oc.ui.words.res.e7SharedSessionContext
import io.github.eslamasabry.oc.ui.words.res.e7SharedToolCallsAndResults
import io.github.eslamasabry.oc.ui.words.res.e7SharedUserPrompts
import io.github.eslamasabry.oc.ui.words.res.kitContextRingSemantics
import io.github.eslamasabry.oc.ui.words.res.kitContextSheetModel
import io.github.eslamasabry.oc.ui.words.res.kitContextSheetTitle
import io.github.eslamasabry.oc.ui.words.res.kitSheetDismiss
import io.github.eslamasabry.oc.ui.words.res.sessionContextMessagesSplit
import io.github.eslamasabry.oc.ui.words.res.sessionContextPercent
import io.github.eslamasabry.oc.ui.words.res.sessionContextTokens
import io.github.eslamasabry.oc.ui.words.res.sessionContextVerdictFull
import io.github.eslamasabry.oc.ui.words.res.sessionContextVerdictNear
import io.github.eslamasabry.oc.ui.words.res.sessionContextVerdictPlenty
import io.github.eslamasabry.oc.ui.words.res.usageCacheRead
import io.github.eslamasabry.oc.ui.words.res.usageCacheWrite
import io.github.eslamasabry.oc.ui.words.res.usageInput
import io.github.eslamasabry.oc.ui.words.res.usageOutput
import io.github.eslamasabry.oc.ui.words.res.usageReasoning
import org.jetbrains.compose.resources.stringResource

internal fun ContextLevel.toKit(): KitContextLevel =
    when (this) {
        ContextLevel.Calm -> KitContextLevel.Calm
        ContextLevel.Warning -> KitContextLevel.Warning
        ContextLevel.Full -> KitContextLevel.Full
    }

/** "42 % used · plenty left" (Flutter session context verdicts, by level). */
@Composable
internal fun contextVerdict(context: ContextUi): String {
    val percent = Numbers.format(context.percent)
    return when (context.level) {
        ContextLevel.Calm -> stringResource(Res.string.sessionContextVerdictPlenty, percent)
        ContextLevel.Warning -> stringResource(Res.string.sessionContextVerdictNear, percent)
        ContextLevel.Full -> stringResource(Res.string.sessionContextVerdictFull, percent)
    }
}

/**
 * The conversation's context (B-CHAT-019/057/102; Flutter `SessionContextScreen`, here a sheet over the chat so Back
 * returns to the conversation): the ring with its verdict, the latest reply's tokens including cache activity, the
 * window, messages, and the input makeup labelled as an estimate.
 */
@Composable
internal fun SessionContextDetailsSheet(context: ContextUi?, visible: Boolean, onDismiss: () -> Unit) {
    KitSheet(
        visible = visible && context != null,
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.e7SharedSessionContext),
        closeLabel = stringResource(Res.string.kitSheetDismiss),
    ) {
        if (context == null) return@KitSheet
        val sheet = context.sheet
        val verdict = contextVerdict(context)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.S4.dp, vertical = Space.S2.dp).testTag("chat-context-verdict"),
            horizontalArrangement = Arrangement.spacedBy(Space.S3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KitContextRing(
                context.percent,
                context.level.toKit(),
                stringResource(Res.string.kitContextRingSemantics, Numbers.format(context.percent)),
                size = 40.dp,
            )
            KitText(verdict, role = TypeRole.Title)
        }
        KitText(
            stringResource(Res.string.e7SharedLatestAssistantRequestIncludingCacheActivity),
            role = TypeRole.Caption,
            tone = TextTone.Secondary,
            modifier = Modifier.padding(horizontal = Space.S4.dp),
        )
        TokenRow(stringResource(Res.string.usageInput), sheet.tokens.input)
        TokenRow(stringResource(Res.string.usageOutput), sheet.tokens.output)
        if (sheet.tokens.reasoning > 0) TokenRow(stringResource(Res.string.usageReasoning), sheet.tokens.reasoning)
        TokenRow(stringResource(Res.string.usageCacheRead), sheet.tokens.cacheRead)
        TokenRow(stringResource(Res.string.usageCacheWrite), sheet.tokens.cacheWrite)
        KitRow(
            title = stringResource(Res.string.e7SharedContextLimit),
            trailing =
                KitRowTrailing.Value(
                    sheet.contextLimit?.let { stringResource(Res.string.sessionContextTokens, Numbers.format(it)) }
                        ?: stringResource(Res.string.e7SharedContextLimitUnavailable),
                    chevron = false,
                ),
            modifier = Modifier.testTag("chat-context-limit"),
        )
        val model = listOfNotNull(sheet.providerId, sheet.modelId).joinToString("/")
        if (model.isNotEmpty())
            KitRow(title = stringResource(Res.string.kitContextSheetModel), trailing = KitRowTrailing.Value(model, chevron = false))
        KitRow(
            title = stringResource(Res.string.e7SharedMessages),
            trailing =
                KitRowTrailing.Value(
                    stringResource(
                        Res.string.sessionContextMessagesSplit,
                        Numbers.format(sheet.userMessages + sheet.assistantMessages),
                        Numbers.format(sheet.userMessages),
                        Numbers.format(sheet.assistantMessages),
                    ),
                    chevron = false,
                ),
        )
        if (sheet.estimated) {
            KitSectionLabel(stringResource(Res.string.e7SharedEstimatedInputMakeup), Modifier.testTag("chat-context-estimated"))
            for (segment in sheet.breakdown) {
                KitRow(
                    title = stringResource(
                        when (segment.kind) {
                            ContextBreakdownKind.User -> Res.string.e7SharedUserPrompts
                            ContextBreakdownKind.Assistant -> Res.string.e7SharedAssistantText
                            ContextBreakdownKind.Tool -> Res.string.e7SharedToolCallsAndResults
                            ContextBreakdownKind.Other -> Res.string.e7SharedOtherContext
                        }
                    ),
                    trailing =
                        KitRowTrailing.Value(
                            stringResource(Res.string.sessionContextPercent, Numbers.format(segment.percent.toLong())),
                            chevron = false,
                        ),
                )
            }
        }
    }
}

@Composable
private fun TokenRow(title: String, tokens: Long) {
    KitRow(
        title = title,
        trailing = KitRowTrailing.Value(stringResource(Res.string.sessionContextTokens, Numbers.format(tokens)), chevron = false),
    )
}

/**
 * C4: what the sheet says about the conversation's totals. The server's figures cover the whole conversation; the
 * loaded cost covers the messages on the phone (Flutter `historyLoadedCost`, `historyServerTotalsNote`).
 */
internal class ContextTotals(val serverCost: Double?, val serverTokens: Long?, val loadedCost: Double) {
    val reportedByServer: Boolean
        get() = serverCost != null || serverTokens != null

    /** The one cost to show: the server's, else the loaded sum; null when there is nothing worth saying. */
    val cost: Double?
        get() = (serverCost ?: loadedCost).takeIf { it > 0.0 }
}

/** The bar opens the context-window summary and, below it, what the conversation has cost so far. */
@Composable
internal fun ChatContextSheet(
    context: ContextUi?,
    visible: Boolean,
    totals: ContextTotals? = null,
    onDismiss: () -> Unit,
) {
    KitSheet(
        visible = visible && context?.sheet?.contextLimit != null,
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.kitContextSheetTitle),
        closeLabel = stringResource(Res.string.kitSheetDismiss),
    ) {
        if (context != null) {
            val limit = context.sheet.contextLimit
            if (limit != null) io.github.eslamasabry.oc.ui.kitchat.turn.KitContextSheet(
                tokens = context.sheet.contextTokens,
                limit = limit,
                modelName = context.sheet.modelId.orEmpty(),
                percent = context.percent,
                level = context.level.toKit(),
            )
            ContextTotalsRows(totals)
        }
    }
}

@Composable
private fun ContextTotalsRows(totals: ContextTotals?) {
    if (totals == null) return
    val cost = totals.cost
    if (cost == null && totals.serverTokens == null) return
    KitSectionLabel(stringResource(Res.string.e7SharedSessionTotals), Modifier.testTag("chat-context-totals"))
    if (cost != null)
        KitRow(
            title =
                stringResource(
                    if (totals.serverCost != null) Res.string.e7SharedAccumulatedCostReportedByServer
                    else Res.string.historyLoadedCost
                ),
            trailing = KitRowTrailing.Value("$" + Numbers.decimal(cost, 4), chevron = false),
            modifier = Modifier.testTag("chat-context-cost"),
        )
    totals.serverTokens?.let {
        KitRow(
            title = stringResource(Res.string.e7SharedSessionTokensReportedByServer),
            trailing = KitRowTrailing.Value(stringResource(Res.string.sessionContextTokens, Numbers.format(it)), chevron = false),
            modifier = Modifier.testTag("chat-context-server-tokens"),
        )
    }
    if (totals.reportedByServer)
        KitText(
            stringResource(Res.string.historyServerTotalsNote),
            role = TypeRole.Caption,
            tone = TextTone.Secondary,
            modifier = Modifier.padding(horizontal = Space.S4.dp),
        )
}
