package io.github.eslamasabry.oc.feature.chat

import kotlinx.collections.immutable.toImmutableMap
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import io.github.eslamasabry.oc.core.domain.platform.AppNotices
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.settings.teaching.TeachingSource
import io.github.eslamasabry.oc.engine.transcript.state.BlockRow
import io.github.eslamasabry.oc.engine.transcript.state.PromptRow
import io.github.eslamasabry.oc.feature.chat.cards.ChatCards
import io.github.eslamasabry.oc.feature.chat.cards.LocalChatCards
import io.github.eslamasabry.oc.feature.chat.cards.ModelCardSlotHost
import io.github.eslamasabry.oc.feature.chat.composer.ChatPromptsDoor
import io.github.eslamasabry.oc.feature.chat.composer.ComposerHost
import io.github.eslamasabry.oc.feature.chat.context.ActiveContextModel
import io.github.eslamasabry.oc.feature.chat.forms.*
import io.github.eslamasabry.oc.feature.chat.forms.ChatFormSlot
import io.github.eslamasabry.oc.feature.chat.forms.ChatForms
import io.github.eslamasabry.oc.feature.chat.handoff.StateHandoffSheet
import io.github.eslamasabry.oc.feature.chat.handoff.StateHandoffViewModel
import io.github.eslamasabry.oc.feature.chat.header.presentation.ChatHeaderViewModel
import io.github.eslamasabry.oc.feature.chat.header.ui.ApprovalChipSlot
import io.github.eslamasabry.oc.feature.chat.header.ui.ChatHeaderSlot
import io.github.eslamasabry.oc.feature.chat.inbox.presentation.ChatInboxViewModel
import io.github.eslamasabry.oc.feature.chat.inbox.ui.ChatInboxSlot
import io.github.eslamasabry.oc.feature.chat.notes.SessionNoteViewModel
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPhase
import io.github.eslamasabry.oc.feature.chat.presentation.ChatViewModel
import io.github.eslamasabry.oc.feature.chat.presentation.shownInChat
import io.github.eslamasabry.oc.feature.chat.queuemove.QueueMoveScreen
import io.github.eslamasabry.oc.feature.chat.queuemove.QueueMoveViewModel
import io.github.eslamasabry.oc.feature.chat.relations.presentation.SessionRelationsViewModel
import io.github.eslamasabry.oc.feature.chat.relations.ui.SessionRelationsScreen
import io.github.eslamasabry.oc.feature.chat.sessiontransfer.presentation.SessionTransferViewModel
import io.github.eslamasabry.oc.feature.chat.sessiontransfer.ui.SessionTransferSheet
import io.github.eslamasabry.oc.feature.chat.start.presentation.ChatStartViewModel
import io.github.eslamasabry.oc.feature.chat.ui.ChatScreen
import io.github.eslamasabry.oc.feature.chat.ui.MessageTeachingScope
import io.github.eslamasabry.oc.feature.chat.ui.rememberBottomInsetOpen
import io.github.eslamasabry.oc.feature.chat.world.ChatGraphAccessors
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.motion.KitGrowPage
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.status.KitStatus
import io.github.eslamasabry.oc.ui.kit.status.KitStatusKind
import io.github.eslamasabry.oc.ui.kitchat.KitChatEnvironment
import io.github.eslamasabry.oc.ui.kitchat.composer.ComposerInbox
import io.github.eslamasabry.oc.ui.kitchat.composer.KitReplyReader
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.CardsSettingsRoute
import io.github.eslamasabry.oc.ui.routes.ChatRoute
import io.github.eslamasabry.oc.ui.routes.ConnectionFormRoute
import io.github.eslamasabry.oc.ui.routes.EntryInstaller
import io.github.eslamasabry.oc.ui.routes.PreviewRoute
import io.github.eslamasabry.oc.ui.routes.SessionRelationsRoute
import io.github.eslamasabry.oc.ui.routes.TerminalRoute
import io.github.eslamasabry.oc.ui.routes.ToolsRoute
import io.github.eslamasabry.oc.ui.routes.WebSourcesRoute
import io.github.eslamasabry.oc.ui.words.res.continuedFrom
import io.github.eslamasabry.oc.ui.words.res.continuedIn
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.activeContextTitle
import io.github.eslamasabry.oc.ui.words.res.chatQueueMoveTitle
import io.github.eslamasabry.oc.ui.words.res.devPreviewMenu
import io.github.eslamasabry.oc.ui.words.res.firstRunNotifyTitle
import io.github.eslamasabry.oc.ui.words.res.nativePendingCount
import io.github.eslamasabry.oc.ui.words.res.nativePendingFailed
import io.github.eslamasabry.oc.ui.words.res.nativePendingReview
import io.github.eslamasabry.oc.ui.words.res.protoToolsTitle
import io.github.eslamasabry.oc.ui.words.res.remoteTerminalTitle
import io.github.eslamasabry.oc.ui.words.res.r40RelationsTitle
import io.github.eslamasabry.oc.ui.words.res.sessionTransferTitle
import io.github.eslamasabry.oc.ui.words.res.stateHandoffTitle
import io.github.eslamasabry.oc.ui.words.res.webSourcesTitle
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The chat's entry (07 C01): one ChatViewModel per ChatRoute entry, the composer slot injected
 * (plan 09 §6.3). The ONLY file in the module that imports androidx.navigation3.runtime (Konsist
 * K1).
 *
 * Merge note: `:feature:chats` still installs its P2 placeholder for ChatRoute; the coordinator
 * removes that entry in the merge that adds `:feature:chat` to the app (one `entry<ChatRoute>` per
 * graph).
 */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<EntryInstaller>())
public class ChatEntries(
    private val appNotices: AppNotices,
    private val composerHost: ComposerHost,
    private val environment: KitChatEnvironment,
    private val inbox: ComposerInbox,
    private val cards: ChatCards,
    private val registry: ConnectionRegistry,
    private val forms: ChatForms,
    private val navigator: AppNavigator,
    private val readers: Set<KitReplyReader>,
    private val photoSources:
        Set<io.github.eslamasabry.oc.feature.chat.composer.presentation.ComposerAttachSource>,
    private val teaching: TeachingSource,
    /** S28 story 14: the one nudge registry, where the app has bound one. */
    private val nudges: io.github.eslamasabry.oc.feature.chat.nudges.ChatNudgeSource,
    private val transcriptPreferences:
        Set<io.github.eslamasabry.oc.feature.chat.presentation.TranscriptDisplayPreferences> =
        emptySet(),
    private val pictureEncoders: Set<io.github.eslamasabry.oc.feature.chat.ui.ChatPictureEncoder> =
        emptySet(),
    private val activeContextKept: Set<io.github.eslamasabry.oc.feature.chat.context.ActiveContextLastKnown> =
        emptySet(),
) : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<ConnectionFormRoute> { route ->
            val vm =
                assistedMetroViewModel<ConnectionFormViewModel, ConnectionFormViewModel.Factory> {
                    create(route)
                }
            val state by vm.state.collectAsStateWithLifecycle()
            ConnectionFormScreen(state, vm)
        }
        entry<SessionRelationsRoute> { route ->
            val vm =
                assistedMetroViewModel<
                    SessionRelationsViewModel,
                    SessionRelationsViewModel.Factory,
                > {
                    create(route)
                }
            val state by vm.state.collectAsStateWithLifecycle()
            SessionRelationsScreen(state, vm)
        }
        entry<ChatRoute> { route ->
            val vm = assistedMetroViewModel<ChatViewModel, ChatViewModel.Factory> { create(route) }
            val state by vm.state.collectAsStateWithLifecycle()
            val liveGraphs by registry.live.collectAsStateWithLifecycle()
            val gateway = (liveGraphs[route.ref.profileId] as? ChatGraphAccessors)?.chatGateway
            val serverCapabilities = gateway?.capabilities?.collectAsStateWithLifecycle()?.value
            val errorModels by vm.models.collectAsStateWithLifecycle()
            val workUi by vm.work.state.collectAsStateWithLifecycle()
            val serverCommandList by vm.serverCommands.collectAsStateWithLifecycle()
            val pendingVm =
                assistedMetroViewModel<ChatInboxViewModel, ChatInboxViewModel.Factory> {
                    create(route)
                }
            val pendingSends by pendingVm.state.collectAsStateWithLifecycle()
            var pendingOpen by remember(route.ref) { mutableStateOf(false) }
            val startVm =
                assistedMetroViewModel<ChatStartViewModel, ChatStartViewModel.Factory> {
                    create(route)
                }
            val start by startVm.state.collectAsStateWithLifecycle()
            val emptyChat =
                state.phase == ChatPhase.Live &&
                    state.rows.isEmpty() &&
                    state.pending.isEmpty() &&
                    state.live == null
            // Older history can finish loading after the chat turned empty; re-check then too.
            val startVisible = emptyChat && start.emptyEligible
            LaunchedEffect(startVisible) { startVm.visible(startVisible) }
            val appNotice by appNotices.status.collectAsStateWithLifecycle()
            val replyOffer by appNotices.replyOffer.collectAsStateWithLifecycle()
            val appNoticeStatus = appNotice.shownInChat()?.let { notice ->
                KitStatus(
                    if (notice.stoppedApp) KitStatusKind.AppStopped else KitStatusKind.Info,
                    notice.message,
                    id = notice.id,
                    action =
                        notice.action?.let { action ->
                            notice.actionLabel?.let { label ->
                                KitLineAction(label, { appNotices.act(action) })
                            }
                        },
                    onDismiss = { appNotices.dismiss(notice.id) },
                    dismissLabel = notice.dismissLabel,
                    more =
                        listOfNotNull(
                            notice.secondAction?.let { second ->
                                notice.secondActionLabel?.let { label ->
                                    KitLineAction(label, { appNotices.act(second) })
                                }
                            }
                        ),
                )
            }
            var offerDetails by remember(route) { mutableStateOf(false) }
            val keyboardOpen by rememberBottomInsetOpen(WindowInsets.ime)
            val offerStatus =
                replyOffer
                    ?.takeUnless { keyboardOpen }
                    ?.let { offer ->
                        KitStatus(
                            KitStatusKind.Info,
                            if (offer.failed) offer.message
                            else stringResource(Res.string.firstRunNotifyTitle),
                            id = "chat-reply-notification-offer",
                            action =
                                if (offer.busy || offer.failed) null
                                else KitLineAction(offer.acceptLabel, { offerDetails = true }),
                            onDismiss =
                                if (offer.busy) null else ({ appNotices.answerReplyOffer(false) }),
                            dismissLabel = offer.declineLabel,
                        )
                    }
            // N-48: a send that may already have arrived is said once, under its own bubble (with Review);
            // the status line counts only what the bubbles do not already say (its Send again copy included).
            val unsaid =
                io.github.eslamasabry.oc.feature.chat.inbox.presentation.PendingNotice.unsaid(
                    pendingSends.rows,
                    state.pending,
                )
            val pendingStatus =
                if (unsaid.isEmpty() && !pendingSends.failed) null
                else
                    KitStatus(
                        KitStatusKind.Info,
                        if (unsaid.isEmpty())
                            stringResource(Res.string.nativePendingFailed)
                        else
                            pluralStringResource(
                                Res.plurals.nativePendingCount,
                                unsaid.size,
                                unsaid.size.toString(),
                            ),
                        id = "chat-pending-sends",
                        tone =
                            if (pendingSends.failed)
                                io.github.eslamasabry.oc.ui.design.color.StatusTone.Failure
                            else io.github.eslamasabry.oc.ui.design.color.StatusTone.Attention,
                        action =
                            KitLineAction(
                                stringResource(Res.string.nativePendingReview),
                                { pendingOpen = true },
                            ),
                    )
            val appStatus = KitStatus.highest(listOf(appNoticeStatus, pendingStatus, offerStatus))
            // The explanation and opt-in belong in a sheet, never a tall transcript banner.
            // The compact strip only opens the review; enabling still requires its explicit action.
            val offerContent: (@androidx.compose.runtime.Composable () -> Unit)? =
                replyOffer?.let { offer ->
                    {
                        KitSheet(
                            visible = offerDetails,
                            onDismissRequest = { offerDetails = false },
                            title = stringResource(Res.string.firstRunNotifyTitle),
                            closeLabel = offer.declineLabel,
                        ) {
                            KitNotice(
                                message = offer.message,
                                actions =
                                    if (offer.busy || offer.failed) emptyList()
                                    else
                                        listOf(
                                            KitLineAction(
                                                offer.acceptLabel,
                                                { appNotices.answerReplyOffer(true) },
                                            )
                                        ),
                                dismiss =
                                    if (offer.busy) null
                                    else
                                        KitLineAction(
                                            offer.declineLabel,
                                            {
                                                offerDetails = false
                                                appNotices.answerReplyOffer(false)
                                            },
                                        ),
                            )
                        }
                    }
                }
            LaunchedEffect(replyOffer) {
                if (replyOffer == null) offerDetails = false
            }
            val requests by vm.requests.collectAsStateWithLifecycle()
            val slot =
                requests?.state?.collectAsStateWithLifecycle()?.value
                    ?: io.github.eslamasabry.oc.feature.chat.requests.presentation.RequestSlotUi()
            val facts by vm.facts.collectAsStateWithLifecycle()
            val display =
                transcriptPreferences.singleOrNull()?.value?.collectAsStateWithLifecycle()?.value
                    ?: io.github.eslamasabry.oc.feature.chat.presentation.TranscriptDisplayChoices()
            val headerVm =
                assistedMetroViewModel<ChatHeaderViewModel, ChatHeaderViewModel.Factory> {
                    create(route)
                }
            val header by headerVm.state.collectAsStateWithLifecycle()

            // A list row's act (Share, Details, Copy handoff, Continue here) runs once the page is up.
            var thenDone by rememberSaveable(route) { mutableStateOf(false) }
            LaunchedEffect(route) {
                val act =
                    route.thenAction?.let { name ->
                        io.github.eslamasabry.oc.feature.chat.header.presentation.HeaderAction
                            .entries
                            .firstOrNull { it.name == name }
                    }
                if (act != null && !thenDone) {
                    thenDone = true
                    headerVm.choose(act)
                }
            }
            val noteVm =
                assistedMetroViewModel<SessionNoteViewModel, SessionNoteViewModel.Factory> {
                    create(route)
                }
            val note by noteVm.state.collectAsStateWithLifecycle()
            val handoffVm =
                assistedMetroViewModel<StateHandoffViewModel, StateHandoffViewModel.Factory> {
                    create(route)
                }
            val handoff by handoffVm.state.collectAsStateWithLifecycle()
            val handoffLineState by handoffVm.line.collectAsStateWithLifecycle()
            val handoffLine: (@androidx.compose.runtime.Composable () -> Unit)? =
                handoffLineState?.let { line ->
                    {
                        io.github.eslamasabry.oc.ui.kitchat.transcript.KitContinuedLine(
                            text =
                                if (line.outgoing)
                                    stringResource(
                                        Res.string.continuedIn,
                                        io.github.eslamasabry.oc.ui.kit.text.KitBidi.auto(line.words),
                                    )
                                else
                                    stringResource(
                                        Res.string.continuedFrom,
                                        io.github.eslamasabry.oc.ui.kit.text.KitBidi.auto(line.words),
                                    ),
                            onOpen = handoffVm::openLine,
                            gone = line.gone,
                        )
                    }
                }
            val approval by headerVm.approval.collectAsStateWithLifecycle()
            // PREVIEW-QR: the dev servers this conversation mentioned (rows are newest first; the
            // scan wants oldest first).
            val preview =
                KitLineAction(
                    stringResource(Res.string.devPreviewMenu),
                    {
                        val texts =
                            state.rows.asReversed().mapNotNull { row ->
                                when (row) {
                                    is PromptRow -> row.text
                                    is BlockRow -> row.text
                                    else -> null
                                }
                            }
                        navigator.open(PreviewRoute.Ports.from(route.ref, texts))
                    },
                    testTag = "chat-dev-preview",
                )
            // B-CHAT-001..003: the inspector of what the next turn reads; offered only where the
            // server has the route.
            val activeContext = remember(route.ref) { ActiveContextModel(route.ref, registry, activeContextKept.firstOrNull()) }
            DisposableEffect(activeContext) { onDispose { activeContext.close() } }
            val inspect =
                KitLineAction(
                    stringResource(Res.string.activeContextTitle),
                    activeContext::open,
                    testTag = "chat-active-context",
                )
            val transfer =
                assistedMetroViewModel<SessionTransferViewModel, SessionTransferViewModel.Factory> {
                    create(route)
                }
            val transferState by transfer.state.collectAsStateWithLifecycle()
            val transferAction =
                KitLineAction(stringResource(Res.string.sessionTransferTitle), transfer::open)
            val toolsAction =
                KitLineAction(
                    stringResource(Res.string.protoToolsTitle),
                    {
                        navigator.open(ToolsRoute(route.ref.profileId, route.ref.sessionId))
                    },
                )
            val queueMove =
                assistedMetroViewModel<QueueMoveViewModel, QueueMoveViewModel.Factory> {
                    create(route)
                }
            val queueMoveState by queueMove.state.collectAsStateWithLifecycle()
            val queueMoveAvailable by queueMove.available.collectAsStateWithLifecycle()
            val formModel = remember(route.ref) { forms.open(route.ref) }
            val formState by formModel.state.collectAsStateWithLifecycle()
            DisposableEffect(formModel) { onDispose { formModel.close() } }
            // S28 story 14 (Flutter nudge_slot.dart): one-time nudges read this conversation's facts.
            var nudgeModel by
                remember(route.ref) {
                    mutableStateOf<io.github.eslamasabry.oc.feature.chat.nudges.ChatNudgeModel?>(null)
                }
            val nudgeScope = androidx.compose.runtime.rememberCoroutineScope()
            LaunchedEffect(route.ref) {
                nudges.registry()?.let { registry ->
                    nudgeModel =
                        io.github.eslamasabry.oc.feature.chat.nudges.ChatNudgeModel(
                            registry,
                            route.ref.sessionId.raw,
                            nudgeScope,
                        )
                }
            }
            DisposableEffect(nudgeModel) { onDispose { nudgeModel?.close() } }
            val activeNudge =
                nudgeModel?.active?.collectAsStateWithLifecycle(null)?.value
            val latestTurn = facts.results.values.lastOrNull()
            val nudgeInputs =
                io.github.eslamasabry.oc.feature.chat.nudges.ChatNudgeInputs(
                    busy = state.live != null,
                    hasReply = state.rows.any { it is BlockRow },
                    pendingPermissions =
                        slot.cards
                            .filterIsInstance<io.github.eslamasabry.oc.feature.chat.requests.presentation.RequestCardUi.Permission>()
                            .mapNotNull { card -> card.captured?.request?.let { card.id.raw to it.permission } }
                            .toMap()
                            .toImmutableMap(),
                    approvalsAvailable =
                        state.capabilities.approvals && approval.offered &&
                            approval.choice ==
                                io.github.eslamasabry.oc.feature.chat.header.approval.ApprovalChoice.Ask,
                    runChangedFiles = latestTurn?.files?.isNotEmpty() == true,
                    reviewAvailable = state.capabilities.changes,
                    contextPercent = facts.context?.percent,
                    compactAvailable = state.capabilities.compact && state.live == null,
                    suppressed = slot.card != null || formState.form != null,
                )
            LaunchedEffect(nudgeModel, nudgeInputs) { nudgeModel?.observe(nudgeInputs) }
            val nudgeSlot: @androidx.compose.runtime.Composable () -> Unit = {
                val model = nudgeModel
                if (model != null && !nudgeInputs.suppressed)
                    io.github.eslamasabry.oc.feature.chat.nudges.ChatNudgeSlot(
                        activeNudge,
                        filesChanged = latestTurn?.files?.size ?: 0,
                        actions =
                            io.github.eslamasabry.oc.feature.chat.nudges.ChatNudgeActions(
                                approvals = headerVm::openApprovalMenu,
                                review = vm::openChanges,
                                compact = vm::compactRequested,
                                done = model::done,
                            ),
                    )
            }
            val prompts = remember(route.ref) { ChatPromptsDoor(route.ref, inbox, navigator) }
            // P12-CHAT-CARDS: the conversation's agent cards, read by the transcript's card slot.
            val cardModel = remember(route.ref) { cards.open(route.ref) }
            DisposableEffect(cardModel) { onDispose { cardModel.close() } }
            val cardSlot =
                remember(cardModel) {
                    ModelCardSlotHost(
                        cardModel,
                        vm::openLink,
                        { navigator.open(CardsSettingsRoute) },
                        catalogue = { cardModel.catalogueRoute()?.let(navigator::open) },
                    )
                }
            MessageTeachingScope(
                source = teaching,
                eligible = state.rows.any { it is BlockRow } && facts.reading.metadata.isNotEmpty(),
                blocked = slot.card != null || state.live != null || state.menu != null,
            ) {
                CompositionLocalProvider(LocalChatCards provides cardSlot) {
                    environment.Provide {
                        KitSheetHost {
                            KitGrowPage(growKey = route.ref) {
                                ChatScreen(
                                    state,
                                    vm,
                                    composerHost,
                                    requests = slot,
                                    requestActions = requests,
                                    formWaiting = formState.form != null,
                                    modelChoices = errorModels.choices,
                                    work = workUi,
                                    workModel = vm.work,
                                    serverCommands = serverCommandList,
                                    onLoadServerCommands = vm::loadServerCommands,
                                    onReviewPending = { pendingOpen = true },
                                    formSlot = { modifier ->
                                        ChatFormSlot(formState, formModel, vm::openLink, modifier)
                                    },
                                    extraActions =
                                        listOfNotNull(
                                            preview,
                                            inspect.takeIf { activeContext.available() },
                                            transferAction,
                                            toolsAction,
                                            // N-24: the server terminal in this conversation's project.
                                            KitLineAction(
                                                    stringResource(Res.string.remoteTerminalTitle),
                                                    {
                                                        navigator.open(
                                                            TerminalRoute.Conversation(route.ref)
                                                        )
                                                    },
                                                    testTag = "chat-terminal-open",
                                                )
                                                .takeIf { serverCapabilities?.terminal == true },
                                            KitLineAction(
                                                    stringResource(Res.string.chatQueueMoveTitle),
                                                    queueMove::open,
                                                )
                                                .takeIf { queueMoveAvailable },
                                            KitLineAction(
                                                    stringResource(Res.string.webSourcesTitle),
                                                    { navigator.open(WebSourcesRoute(route.ref)) },
                                                )
                                                .takeIf { serverCapabilities?.webSearch == true },
                                            KitLineAction(
                                                    stringResource(Res.string.nativePendingReview),
                                                    { pendingOpen = true },
                                                )
                                                .takeIf {
                                                    pendingSends.rows.isNotEmpty() ||
                                                        pendingSends.failed
                                                },
                                            // STATE: continue this conversation elsewhere.
                                            KitLineAction(
                                                stringResource(Res.string.stateHandoffTitle),
                                                handoffVm::open,
                                            ),
                                            KitLineAction(
                                                    stringResource(Res.string.r40RelationsTitle),
                                                    {
                                                        navigator.open(
                                                            SessionRelationsRoute(route.ref)
                                                        )
                                                    },
                                                    testTag = "session-relations-open",
                                                )
                                                // S30 story 3: where the server manages projects the ⋯ menu's
                                                // Go to › Subagents opens this page; this stays for the rest.
                                                .takeIf {
                                                    serverCapabilities?.subagentSessions == true &&
                                                        serverCapabilities.projectManagement != true
                                                },
                                        ),
                                    activeContext = activeContext,
                                    prompts = prompts,
                                    facts = facts,
                                    transcriptDisplay = display,
                                    onOpenSession = vm::openSession,
                                    header =
                                        ChatHeaderSlot(
                                            header,
                                            headerVm,
                                            ApprovalChipSlot(approval, headerVm),
                                            note,
                                            noteVm,
                                        ),
                                    replyReader = readers.firstOrNull(),
                                    start = start,
                                    startActions = startVm,
                                    appStatus = appStatus,
                                    handoffLine = handoffLine,
                                    replyOffer = {
                                        nudgeSlot()
                                        offerContent?.invoke()
                                    },
                                    encodePng = pictureEncoders.firstOrNull()?.encodePng,
                                )
                            }
                            ChatInboxSlot(
                                pendingSends,
                                pendingVm,
                                { text ->
                                    inbox.offer(
                                        ComposerInbox.target(
                                            route.ref.profileId.raw,
                                            route.ref.sessionId.raw,
                                        ),
                                        text,
                                    )
                                },
                                open = pendingOpen,
                                onDismiss = { pendingOpen = false },
                            )
                            QueueMoveScreen(queueMoveState, queueMove)
                            SessionTransferSheet(transferState, transfer)
                            StateHandoffSheet(handoff, handoffVm)
                        }
                    }
                }
            }
        }
    }
}
