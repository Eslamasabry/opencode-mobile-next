package io.github.eslamasabry.oc.feature.factsbook.ui

import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.factsbook.FactsBookLoad
import io.github.eslamasabry.oc.engine.factsbook.ConversationOffer
import io.github.eslamasabry.oc.engine.factsbook.ProposalDecision
import io.github.eslamasabry.oc.engine.factsbook.ReminderProposal
import io.github.eslamasabry.oc.engine.factsbook.ReviewItem
import io.github.eslamasabry.oc.feature.factsbook.presentation.CareNotice
import io.github.eslamasabry.oc.feature.factsbook.presentation.NeedsYouActions
import io.github.eslamasabry.oc.feature.factsbook.presentation.NeedsYouUi
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.chip.KitChipWrap
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.foundation.KitPanel
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitInputOperationDialog
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.row.KitProvenance
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun classWords(value: String): String =
    stringResource(
        when (value) {
            "fact_update" -> Res.string.fbCareClassFactUpdate
            "fact_supersede" -> Res.string.fbCareClassFactSupersede
            "merge" -> Res.string.fbCareClassMerge
            "link" -> Res.string.fbCareClassLink
            "archive" -> Res.string.fbCareClassArchive
            "forget" -> Res.string.fbCareClassForget
            "watch" -> Res.string.fbCareClassWatch
            "reminder" -> Res.string.fbCareClassReminder
            "import_candidate" -> Res.string.fbCareClassImport
            "promotion" -> Res.string.fbCareClassPromotion
            else -> Res.string.fbCareClassFactAdd
        }
    )

/**
 * Needs you (F: memory_care_screen.dart): the changes waiting for the person's OK, with Approve and Reject on each card
 * and Change and Skip a tap away, then the notes not saved yet. A decision is signed by this phone's owner key after
 * the screen lock; until approval is set up the answers say why they are off.
 */
@Composable
public fun NeedsYouScreen(state: NeedsYouUi, actions: NeedsYouActions) {
    val title = stringResource(Res.string.fbNeedsYouTitle)
    val gutter = LocalKitTokens.current.gutter.dp
    var more by remember { mutableStateOf<ReviewItem?>(null) }
    var editing by remember { mutableStateOf<ReviewItem?>(null) }
    val waiting = state.waiting
    var moreProposal by remember { mutableStateOf<ReminderProposal?>(null) }
    KitSheetHost(Modifier.fillMaxSize()) {
        KitScreen(
            topBar = {
                KitTopBar(
                    title,
                    subtitle = if (waiting == 0) null else stringResource(Res.string.fbNeedsYouWaiting, waiting),
                    exit = KitTopBarExit.Back,
                    exitLabel = stringResource(Res.string.kitTopBarBack),
                    onExit = actions::back,
                )
            },
            width = KitScreenWidth.Reading,
            loading = state.load == FactsBookLoad.Loading,
            loadingLabel = title,
            edges = KitScreenEdges.Bleed,
        ) {
            when {
                state.load == FactsBookLoad.Failed -> {
                    val details = failureDetails(state.error)
                    KitStateView(
                        KitStateKind.Error,
                        stringResource(Res.string.fbErrorTitle),
                        body = failureWords(state.error) + (details?.let { "\n\n$it" } ?: ""),
                        primary = KitAction(stringResource(Res.string.commonRetry), actions::refresh),
                    )
                }
                state.load == FactsBookLoad.Loading && waiting == 0 -> KitSkeletonRows()
                else -> Body(state, actions, gutter, onMore = { more = it }, onMoreProposal = { moreProposal = it })
            }
        }
        val open = more
        if (open != null) {
            val claim = claimOf(open)
            KitSheet(
                visible = true,
                onDismissRequest = { more = null },
                title = claim,
                subtitle = classWords(open.reviewClass),
                closeLabel = stringResource(Res.string.fbDismiss),
            ) {
                KitRowGroup {
                    if (open.text != null) {
                        KitRow(
                            title = stringResource(Res.string.fbCareEditNamed, claim),
                            titleMaxLines = 2,
                            leading = KitRowLeading.Glyph(KitIcons.edit),
                            onClick = {
                                more = null
                                editing = open
                            },
                        )
                    }
                    KitRow(
                        title = stringResource(Res.string.fbCareSkipNamed, claim),
                        titleMaxLines = 2,
                        supporting = stringResource(Res.string.fbCareSkipDetail),
                        leading = KitRowLeading.Glyph(KitIcons.forward),
                        onClick = {
                            more = null
                            actions.skip(open)
                        },
                    )
                    state.fromEvent[open.id]?.let { event ->
                        KitRow(
                            title = stringResource(Res.string.fbWhyFired),
                            leading = KitRowLeading.Glyph(KitIcons.info),
                            onClick = {
                                more = null
                                actions.whyFired(event)
                            },
                        )
                    }
                }
            }
        }
        val proposal = moreProposal
        if (proposal != null) {
            val claim = proposalClaim(proposal)
            KitSheet(
                visible = true,
                onDismissRequest = { moreProposal = null },
                title = claim,
                subtitle = classWords("reminder"),
                closeLabel = stringResource(Res.string.fbDismiss),
            ) {
                KitRowGroup {
                    if (state.canChangeProposals && proposal.preview.schedule != null) {
                        KitRow(
                            title = stringResource(Res.string.fbCareEditNamed, claim),
                            titleMaxLines = 2,
                            leading = KitRowLeading.Glyph(KitIcons.edit),
                            onClick = {
                                moreProposal = null
                                actions.changeProposal(proposal)
                            },
                        )
                    }
                    KitRow(
                        title = stringResource(Res.string.fbCareSkipNamed, claim),
                        titleMaxLines = 2,
                        supporting = stringResource(Res.string.fbCareSkipDetail),
                        leading = KitRowLeading.Glyph(KitIcons.forward),
                        onClick = {
                            moreProposal = null
                            actions.decideProposal(proposal, ProposalDecision.Skip)
                        },
                    )
                    state.fromEvent[proposal.id]?.let { event ->
                        KitRow(
                            title = stringResource(Res.string.fbWhyFired),
                            leading = KitRowLeading.Glyph(KitIcons.info),
                            onClick = {
                                moreProposal = null
                                actions.whyFired(event)
                            },
                        )
                    }
                }
            }
        }
        val edit = editing
        KitInputOperationDialog(
            visible = edit != null,
            title = stringResource(Res.string.fbCareEditTitle),
            fieldLabel = stringResource(Res.string.fbCareEditLabel),
            confirmLabel = stringResource(Res.string.fbCareEditSave),
            initial = edit?.text.orEmpty(),
            onSubmit = { text ->
                editing = null
                edit?.let { actions.approveEdited(it, text) }
            },
            onDismissRequest = { editing = null },
        )
    }
}

@Composable
private fun claimOf(item: ReviewItem): String = item.text?.let { bidiName(it) } ?: classWords(item.reviewClass)

@Composable
private fun Body(
    state: NeedsYouUi,
    actions: NeedsYouActions,
    gutter: Dp,
    onMore: (ReviewItem) -> Unit,
    onMoreProposal: (ReminderProposal) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        if (!state.canApprove) {
            item("owner") {
                KitNotice(
                    stringResource(Res.string.fbCareNeedsOwner),
                    Modifier.padding(horizontal = gutter, vertical = Space.S2.dp),
                    icon = KitIcons.info,
                    actions = listOf(KitLineAction(stringResource(Res.string.fbCareSetupApproval), actions::setUpApproval)),
                )
            }
        }
        if (state.notice != null || state.failure != null) {
            item("notice") {
                val good = state.notice != null && state.notice !in setOf(CareNotice.Stale, CareNotice.OfferStale, CareNotice.OfferUnknown)
                KitNotice(
                    message =
                        when (state.notice) {
                            CareNotice.Approved -> stringResource(Res.string.fbCareApproved)
                            CareNotice.Rejected -> stringResource(Res.string.fbCareRejected)
                            CareNotice.Skipped -> stringResource(Res.string.fbCareSkipped)
                            CareNotice.Stale -> stringResource(Res.string.fbCareStale)
                            CareNotice.OfferStale -> stringResource(Res.string.fbOfferStale)
                            CareNotice.OfferUnknown -> stringResource(Res.string.fbOfferUnknown)
                            CareNotice.ProposalEdited -> stringResource(Res.string.fbCareProposalEdited)
                            null -> failureWords(state.failure)
                        },
                    modifier = Modifier.padding(horizontal = gutter, vertical = Space.S2.dp),
                    icon = if (good) KitIcons.check else KitIcons.info,
                    tone = if (good) StatusTone.Ok else StatusTone.Attention,
                    dismiss = KitLineAction(stringResource(Res.string.fbDismiss), actions::dismiss),
                )
            }
        }
        if (state.waiting == 0) {
            item("empty") {
                KitStateView(
                    KitStateKind.Calm,
                    stringResource(Res.string.fbNeedsYouNone),
                    body = stringResource(Res.string.fbNeedsYouNoneBody),
                    icon = KitIcons.checkCircle,
                )
            }
        }
        val changes = state.review.size + state.proposals.size
        if (changes > 0) {
            item("label") { KitSectionLabel(pluralStringResource(Res.plurals.fbCareWaiting, changes, changes)) }
            item("hint") {
                KitText(
                    stringResource(Res.string.fbCareTapHint),
                    Modifier.padding(horizontal = gutter),
                    role = TypeRole.Caption,
                    tone = TextTone.Secondary,
                )
            }
            state.proposals.forEach { p -> item(p.id) { ProposalCard(p, state, actions, gutter, onMoreProposal) } }
            state.review.forEach { item -> item(item.id) { Card(item, state, actions, gutter, onMore) } }
        }
        if (state.offers.isNotEmpty()) {
            item("offersLabel") { KitSectionLabel(pluralStringResource(Res.plurals.fbNeedsYouOffers, state.offers.size, state.offers.size)) }
            state.offers.forEach { offer -> item(offer.id) { OfferCard(offer, state, actions, gutter) } }
        }
        if (state.notSaved > 0) {
            item("notSaved") {
                KitRowGroup(label = stringResource(Res.string.fbNotSavedTitle)) {
                    KitRow(
                        title = pluralStringResource(Res.plurals.fbMemoryNotSaved, state.notSaved, state.notSaved),
                        supporting = stringResource(Res.string.fbMemoryNotSavedDetail),
                        leading = KitRowLeading.Glyph(KitIcons.notificationImportant),
                        trailing = KitRowTrailing.Chevron,
                        onClick = actions::openNotSaved,
                    )
                }
            }
        }
    }
}

/** Who suggested it: the person themself (with when and where), or an agent with nothing the person wrote behind it. */
@Composable
private fun sourceLine(item: ReviewItem, fromRule: Boolean): String {
    if (fromRule) return stringResource(Res.string.fbCareSourceRule)
    val evidence = item.evidence.firstOrNull() ?: return stringResource(Res.string.fbCareSourceAgent)
    return provenanceLine(
        if (evidence.source == "note") KitProvenance.Told else KitProvenance.Seen,
        observedWords(evidence.observedAt),
        sourceWords(evidence.source),
        waitingForOk = false,
    )
}

@Composable
private fun Card(item: ReviewItem, state: NeedsYouUi, actions: NeedsYouActions, gutter: Dp, onMore: (ReviewItem) -> Unit) {
    val kind = classWords(item.reviewClass)
    val claim = claimOf(item)
    val busy = item.id in state.working
    val fromRule = item.id in state.fromEvent
    val off = if (state.canApprove) KitEnabled.Enabled else KitEnabled.Disabled(stringResource(Res.string.fbCareNeedsOwnerShort))
    KitPanel(
        Modifier.padding(horizontal = gutter, vertical = Space.S2.dp),
        title = claim,
        onClick = if (state.canApprove && !busy) ({ onMore(item) }) else null,
    ) {
        if (item.text != null) KitText(kind, role = TypeRole.Caption, tone = TextTone.Secondary)
        if (item.area.isNotEmpty() || item.risk.isNotEmpty() || fromRule) {
            KitChipWrap {
                if (fromRule) KitChip(stringResource(Res.string.fbTriggerChip))
                if (item.area.isNotEmpty()) KitChip(areaName(item.area))
                item.risk.forEach { KitChip(riskWords(it)) }
            }
        }
        KitText(sourceLine(item, fromRule), role = TypeRole.Caption, tone = TextTone.Secondary)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
            KitButton(
                KitAction(
                    stringResource(Res.string.fbCareApprove),
                    { actions.approve(item) },
                    enabled = off,
                    working = busy,
                    semanticsLabel = stringResource(Res.string.fbCareApproveNamed, claim),
                ),
                role = KitButtonRole.Secondary,
            )
            KitButton(
                KitAction(
                    stringResource(Res.string.fbCareReject),
                    { if (!busy) actions.reject(item) },
                    enabled = off,
                    destructive = true,
                    semanticsLabel = stringResource(Res.string.fbCareRejectNamed, claim),
                ),
                role = KitButtonRole.Secondary,
            )
        }
    }
}

@Composable
private fun proposalClaim(proposal: ReminderProposal): String {
    val what = bidiName(proposal.preview.what)
    return proposal.preview.at?.let { stringResource(Res.string.fbCareProposalWhen, what, isoWhenWords(it.toString())) } ?: what
}

/** A reminder a rule proposes: confirm exactly what is shown or reject it; a tap skips it or says why it came. */
@Composable
private fun ProposalCard(proposal: ReminderProposal, state: NeedsYouUi, actions: NeedsYouActions, gutter: Dp, onMore: (ReminderProposal) -> Unit) {
    val claim = proposalClaim(proposal)
    val busy = proposal.id in state.working
    val off = if (state.canApprove) KitEnabled.Enabled else KitEnabled.Disabled(stringResource(Res.string.fbCareNeedsOwnerShort))
    KitPanel(
        Modifier.padding(horizontal = gutter, vertical = Space.S2.dp),
        title = claim,
        onClick = if (state.canApprove && !busy) ({ onMore(proposal) }) else null,
    ) {
        KitText(classWords("reminder"), role = TypeRole.Caption, tone = TextTone.Secondary)
        KitChipWrap {
            KitChip(stringResource(Res.string.fbTriggerChip))
            KitChip(areaName(proposal.area))
        }
        KitText(stringResource(Res.string.fbCareSourceRule), role = TypeRole.Caption, tone = TextTone.Secondary)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
            KitButton(
                KitAction(
                    stringResource(Res.string.fbCareApprove),
                    { actions.decideProposal(proposal, ProposalDecision.Confirm) },
                    enabled = off,
                    working = busy,
                    semanticsLabel = stringResource(Res.string.fbCareApproveNamed, claim),
                ),
                role = KitButtonRole.Secondary,
            )
            KitButton(
                KitAction(
                    stringResource(Res.string.fbCareReject),
                    { if (!busy) actions.decideProposal(proposal, ProposalDecision.Reject) },
                    enabled = off,
                    destructive = true,
                    semanticsLabel = stringResource(Res.string.fbCareRejectNamed, claim),
                ),
                role = KitButtonRole.Secondary,
            )
        }
    }
}

/**
 * "Discuss with your agent?" (F: offer_card.dart): the offer's short summary, where it came from, and two equal answers:
 * Talk about this opens a new conversation seeded with the summary only; Not now dismisses the offer.
 */
@Composable
private fun OfferCard(offer: ConversationOffer, state: NeedsYouUi, actions: NeedsYouActions, gutter: Dp) {
    val event = state.fromEvent[offer.id]
    KitPanel(Modifier.padding(horizontal = gutter, vertical = Space.S2.dp), title = stringResource(Res.string.fbOfferTitle)) {
        state.offerRule[offer.id]?.let { KitText(stringResource(Res.string.fbOfferFromRule, bidiName(it)), role = TypeRole.Caption, tone = TextTone.Secondary) }
        KitText(bidiName(offer.summary))
        event?.let {
            KitText(
                stringResource(Res.string.fbOfferSource, sourceKindWords(it.source.kind).lowercase(), observedWords(it.capturedAt)),
                role = TypeRole.Caption,
                tone = TextTone.Secondary,
            )
        }
        KitText(stringResource(Res.string.fbOfferExplain), role = TypeRole.Caption, tone = TextTone.Secondary)
        val leadIn = stringResource(Res.string.fbOfferChatLead)
        val working = offer.id in state.working
        Row(horizontalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
            KitButton(
                KitAction(stringResource(Res.string.fbOfferTalk), { actions.talk(offer, leadIn) }, working = working),
                role = KitButtonRole.Secondary,
            )
            KitButton(
                KitAction(stringResource(Res.string.fbOfferNotNow), { actions.notNow(offer) }, working = working),
                role = KitButtonRole.Tertiary,
            )
        }
    }
}
