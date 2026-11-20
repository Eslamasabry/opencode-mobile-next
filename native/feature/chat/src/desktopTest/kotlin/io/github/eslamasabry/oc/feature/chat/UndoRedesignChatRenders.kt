package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.PrefEdit
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.data.lane.DefaultWriterLanes
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.settings.teaching.TeachingIds
import io.github.eslamasabry.oc.engine.settings.teaching.TipController
import io.github.eslamasabry.oc.engine.settings.teaching.TipMoment
import io.github.eslamasabry.oc.engine.settings.teaching.TipVisit
import io.github.eslamasabry.oc.engine.transcript.state.WorkLineRow
import io.github.eslamasabry.oc.engine.transcript.state.WorkState
import io.github.eslamasabry.oc.feature.chat.composer.BasicComposer
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerActions
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerUi
import io.github.eslamasabry.oc.feature.chat.header.approval.ApprovalChipActions
import io.github.eslamasabry.oc.feature.chat.header.approval.ApprovalChipUi
import io.github.eslamasabry.oc.feature.chat.header.approval.ApprovalChoice
import io.github.eslamasabry.oc.feature.chat.header.presentation.ChatHeaderUi
import io.github.eslamasabry.oc.feature.chat.header.ui.ApprovalChip
import io.github.eslamasabry.oc.feature.chat.header.ui.ApprovalChipSlot
import io.github.eslamasabry.oc.feature.chat.header.ui.ChatHeaderSlot
import io.github.eslamasabry.oc.feature.chat.header.ui.ChatHeaderTopBar
import io.github.eslamasabry.oc.feature.chat.presentation.ChatCapabilitiesUi
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPhase
import io.github.eslamasabry.oc.feature.chat.presentation.ChatStatusKind
import io.github.eslamasabry.oc.feature.chat.presentation.ChatStatusUi
import io.github.eslamasabry.oc.feature.chat.presentation.ChatUi
import io.github.eslamasabry.oc.feature.chat.presentation.MessageAction
import io.github.eslamasabry.oc.feature.chat.presentation.MessageMenuUi
import io.github.eslamasabry.oc.feature.chat.presentation.MessageTarget
import io.github.eslamasabry.oc.feature.chat.presentation.TargetKind
import io.github.eslamasabry.oc.feature.chat.presentation.TranscriptReadingUi
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsUi
import io.github.eslamasabry.oc.feature.chat.presentation.TurnMetadataUi
import io.github.eslamasabry.oc.feature.chat.rb.o42.ChatScene
import io.github.eslamasabry.oc.feature.chat.ui.LocalMessageTeaching
import io.github.eslamasabry.oc.feature.chat.ui.MessageTeaching
import io.github.eslamasabry.oc.feature.chat.world.ComposerModel
import io.github.eslamasabry.oc.feature.chat.world.ComposerModels
import io.github.eslamasabry.oc.feature.chat.world.PendingSend
import io.github.eslamasabry.oc.feature.chat.world.PendingState
import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.foundation.kitFrostSource
import io.github.eslamasabry.oc.ui.kit.foundation.rememberKitFrostState
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import io.github.eslamasabry.oc.ui.kit.frame.LocalKitScreenInsets
import io.github.eslamasabry.oc.ui.kit.frame.kitScreenChromePadding
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.state.KitSummaryCard
import io.github.eslamasabry.oc.ui.kit.status.KitStatus
import io.github.eslamasabry.oc.ui.kit.status.KitStatusKind
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerMic
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerVoice
import io.github.eslamasabry.oc.ui.kitchat.composer.KitVoiceField
import io.github.eslamasabry.oc.ui.kitchat.stream.KitMotionLevel
import io.github.eslamasabry.oc.ui.kitchat.stream.rememberRevealRegistry
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitBlockKind
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitPromptBubble
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitReplyBlock
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitReplyFooter
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Capture-only phone-width (360 dp) renders for the OpenCode 2 Go back redesign chat screens:
 * 01 prompt with bubble teaching tip, 01b prompt's own actions menu with Go back to here first,
 * 03 compact waiting state after going back, 06 send refused while waiting.
 */
@OptIn(ExperimentalTestApi::class)
class UndoRedesignChatRenders {
    private val out = File("build/undo-redesign-renders").also(File::mkdirs)

    private data class Variant(val tag: String, val style: Style, val dark: Boolean, val fontScale: Float)

    private val solidVariants = listOf(
        Variant("solid-dark-1.0", Style.Solid, dark = true, fontScale = 1.0f),
        Variant("solid-light-1.0", Style.Solid, dark = false, fontScale = 1.0f),
        Variant("solid-dark-1.3", Style.Solid, dark = true, fontScale = 1.3f),
        Variant("solid-light-1.3", Style.Solid, dark = false, fontScale = 1.3f),
    )

    private val pairVariants = listOf(
        Variant("solid-dark-1.0", Style.Solid, dark = true, fontScale = 1.0f),
        Variant("solid-light-1.0", Style.Solid, dark = false, fontScale = 1.0f),
    )

    private val glassDark = listOf(Variant("glass-dark-1.0", Style.Glass, dark = true, fontScale = 1.0f))
    private val glassLight = listOf(Variant("glass-light-1.0", Style.Glass, dark = false, fontScale = 1.0f))

    private val caps = ChatCapabilitiesUi(
        undo = true,
        fork = true,
        changes = true,
        deleteMessage = true,
    )

    private fun twoTurnState(): ChatUi =
        ChatUi.loading(Ref).copy(
            title = "Fix flaky checkout test",
            phase = ChatPhase.Live,
            capabilities = caps,
            // Newest first (reversed LazyColumn); sized so Turn 1 stays clearly below the floating header.
            rows = persistentListOf(
                footer(TURN_2),
                block(
                    TURN_2,
                    "a2",
                    "p2",
                    0,
                    "Switched the retry test to await the settled cart state. All 18 checkout tests pass.",
                ),
                WorkLineRow("work:$TURN_2", TURN_2, calls = 4, failed = 0, state = WorkState.Done),
                prompt(
                    TURN_2,
                    "Wait for the settled cart state instead of sleeping 200 ms, then rerun the tests.",
                ),
                footer(TURN_1),
                block(
                    TURN_1,
                    "a1",
                    "p1",
                    0,
                    "`applyCoupon()` reads the cart total before the price refresh settles.",
                ),
                prompt(TURN_1, "Fix the flaky checkout test"),
            ),
        )

    private fun promptMenuState(): ChatUi =
        twoTurnState().copy(
            menu = MessageMenuUi(
                target = MessageTarget(TURN_2, "p:$TURN_2", TargetKind.Prompt, TURN_2),
                actions = persistentListOf(
                    MessageAction.UndoFromHere,
                    MessageAction.Copy,
                    MessageAction.EditAndResend,
                    MessageAction.SendAgain,
                    MessageAction.Fork,
                    MessageAction.Delete,
                ),
            ),
        )

    private fun waitingState(): ChatUi =
        ChatUi.loading(Ref).copy(
            title = "Fix flaky checkout test",
            phase = ChatPhase.Live,
            capabilities = caps,
            canPutBack = true,
            status = ChatStatusUi("staged-undo", ChatStatusKind.StagedUndo),
            rows = persistentListOf(
                footer(TURN_1),
                block(
                    TURN_1,
                    "a1",
                    "p1",
                    0,
                    "`applyCoupon()` reads the cart total before the price refresh settles.",
                ),
                prompt(TURN_1, "Fix the flaky checkout test"),
            ),
        )

    private fun refusedWhileWaitingState(): ChatUi =
        waitingState().copy(
            pending = persistentListOf(
                PendingSend(
                    localId = 1L,
                    text = "Also check discount rounding when two coupons stack",
                    state = PendingState.Failed,
                    failure = Failure.Refused(RefusalReason.UndoUnresolved),
                ),
            ),
        )

    private fun facts(): TurnFactsUi =
        TurnFactsUi(
            reading = TranscriptReadingUi(
                metadata = mapOf(
                    TURN_1 to TurnMetadataUi(listOf(MODEL), 0, 0.0, latest = false, footerModel = "Sonnet 4"),
                    TURN_2 to TurnMetadataUi(listOf(MODEL), 0, 0.0, latest = true, footerModel = "Sonnet 4"),
                ),
            ),
        )

    private fun activePromptTip(): MessageTeaching {
        val store = object : KeyValueStore {
            override fun get(key: String): PrefValue? = null
            override fun keys(prefix: String): Set<String> = emptySet()
            override val changes: SharedFlow<Set<String>> = MutableSharedFlow()
            override suspend fun commit(lane: WriterLane, edit: PrefEdit.() -> Unit): Outcome<Unit> = Outcome.Ok(Unit)
            override suspend fun compareAndSet(lane: WriterLane, key: String, expected: String?, next: String?): Outcome<Boolean> = Outcome.Ok(true)
        }
        val clock = object : Clock {
            override fun now(): Instant = Instant.fromEpochMilliseconds(1_000_000L)
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val controller = TipController(store, DefaultWriterLanes(), clock, scope)
        val visit = TipVisit()
        controller.offer(TeachingIds.PromptGoBack, visit = visit, maxCount = 2, moment = TipMoment())
        return MessageTeaching(controller, visit)
    }

    private fun shoot(name: String, variants: List<Variant>, state: ChatUi, showPromptTip: Boolean = false) {
        Locale.setDefault(Locale.ENGLISH)
        for (v in variants) {
            val teaching = if (showPromptTip) activePromptTip() else null
            runDesktopComposeUiTest(360 * 2, 780 * 2) {
                setContent {
                    val appearance =
                        if (v.style == Style.Glass) {
                            KitAppearance(
                                mode = if (v.dark) AppearanceMode.Dark else AppearanceMode.Light,
                                style = Style.Glass,
                                motion = MotionLevel.Off,
                                pack = PackId.Midnight,
                                liveBackground = true,
                                liveStyle = "aurora",
                            )
                        } else {
                            KitAppearance(
                                mode = if (v.dark) AppearanceMode.Dark else AppearanceMode.Light,
                                style = Style.Solid,
                                motion = MotionLevel.Off,
                            )
                        }
                    CompositionLocalProvider(
                        LocalDensity provides Density(2f, v.fontScale),
                        LocalKitScreenInsets provides WindowInsets(top = 28.dp, bottom = 20.dp),
                    ) {
                        KitTheme(
                            appearance = appearance,
                            platform = DesktopKitPlatform(),
                            localeTag = "en",
                            stillFrame = true,
                        ) {
                            CompositionLocalProvider(
                                LocalKitRedactor provides Redactor(),
                                LocalMessageTeaching provides teaching,
                            ) {
                                Box(Modifier.fillMaxSize()) {
                                    ChatScene().Screen(
                                        state = state,
                                        canGoBack = true,
                                        facts = facts(),
                                        projectName = "shopfront",
                                        serverName = "Work laptop",
                                    )
                                }
                            }
                        }
                    }
                }
                mainClock.autoAdvance = false
                mainClock.advanceTimeByFrame()
                mainClock.advanceTimeByFrame()
                waitForIdle()
                ImageIO.write(
                    onRoot().captureToImage().toAwtImage(),
                    "png",
                    File(out, "$name-${v.tag}.png"),
                )
            }
        }
    }

    @Test fun `render 01 prompt action solid`() = shoot("01-prompt-action", solidVariants, twoTurnState(), showPromptTip = true)
    @Test fun `render 01 prompt action glass dark`() = shoot("01-prompt-action", glassDark, twoTurnState(), showPromptTip = true)
    @Test fun `render 01 prompt action glass light`() = shoot("01-prompt-action", glassLight, twoTurnState(), showPromptTip = true)

    @Test fun `render 01b prompt menu`() = shoot("01b-prompt-menu", pairVariants, promptMenuState())

    @Test fun `render 03 chat waiting solid`() = shoot("03-chat-waiting", solidVariants, waitingState())
    @Test fun `render 03 chat waiting glass dark`() = shoot("03-chat-waiting", glassDark, waitingState())
    @Test fun `render 03 chat waiting glass light`() = shoot("03-chat-waiting", glassLight, waitingState())

    @Test fun `render 06 send refused waiting`() = shoot("06-send-refused-waiting", solidVariants, refusedWhileWaitingState())

    /** Taste Pass B (Final Round 3) — Inline Time-Travel Checkpoint: real floating pill chrome (frost) + KitSummaryCard at timeline cut. */
    @Composable
    private fun TasteWaitingB() {
        val scene = ChatScene()
        val reveal = rememberRevealRegistry()
        val frost = rememberKitFrostState()
        val composerActions = object : BasicComposerActions {
            override fun draftChanged(text: String) {}
            override suspend fun send(text: String) = true
            override suspend fun selectModel(choice: ComposerModel?, variant: String) = true
        }
        val approvalActions = object : ApprovalChipActions {
            override fun openApprovalMenu() = Unit
            override fun closeApprovalMenu() = Unit
            override fun chooseApproval(choice: ApprovalChoice) = Unit
            override fun confirmEverything() = Unit
            override fun cancelEverything() = Unit
            override fun dismissApprovalFailure() = Unit
        }
        val approvalUi = ApprovalChipUi(offered = true, choice = ApprovalChoice.Ask)
        val voice = object : KitComposerVoice {
            @Composable override fun Mic(field: KitVoiceField, modifier: Modifier) {
                KitComposerMic("Talk instead of typing", onClick = {}, modifier = modifier)
            }
            @Composable override fun Panel(field: KitVoiceField, modifier: Modifier) = Unit
        }
        KitScreen(
            modifier = Modifier.fillMaxSize(),
            page = true,
            overlayChrome = true,
            floating = true,
            frost = frost,
            edges = KitScreenEdges.Bleed,
            topBar = {
                ChatHeaderTopBar(
                    slot = ChatHeaderSlot(
                        ChatHeaderUi(
                            title = "Fix flaky checkout test",
                            projectName = "shopfront",
                            serverName = "Work laptop",
                        ),
                        scene.headerActions,
                        approval = ApprovalChipSlot(approvalUi, approvalActions),
                    ),
                    onBack = {},
                    onFind = {},
                    canGoBack = true,
                )
            },
            status = KitStatus(
                KitStatusKind.Info,
                "Went back",
                id = "chat-status-staged-undo",
                tone = StatusTone.Attention,
                icon = KitIcons.undo,
                action = KitLineAction("Bring back", onClick = {}),
                more = listOf(
                    KitLineAction("Review what went back", onClick = {}),
                    KitLineAction("Keep this point", onClick = {}),
                ),
            ),
            bottom = {
                BasicComposer(
                    BasicComposerUi(
                        available = true,
                        sending = false,
                        steers = false,
                        targetKey = "checkout",
                        models = ComposerModels(usesDefault = true),
                        filesAvailable = true,
                    ),
                    composerActions,
                    ComposerChrome(
                        busy = { false },
                        requestWaiting = { false },
                        onSent = {},
                        pendingInsert = { null },
                        insertDone = {},
                        focusRequest = { null },
                        focusDone = {},
                        onStop = {},
                        stopping = { false },
                        rowChips = { ApprovalChip(ApprovalChipSlot(approvalUi, approvalActions)) },
                    ),
                    Modifier.fillMaxWidth().padding(top = Space.S2.dp),
                    voice = voice,
                )
            },
        ) {
            Box(
                Modifier.fillMaxSize().kitFrostSource(frost).padding(kitScreenChromePadding()),
                contentAlignment = Alignment.BottomStart,
            ) {
                Column(
                    Modifier.fillMaxWidth()
                        .verticalScroll(rememberScrollState(), reverseScrolling = true)
                        .padding(bottom = Space.S3.dp),
                    verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
                ) {
                    KitPromptBubble("Fix the flaky checkout test")
                    KitReplyBlock(
                        text = "applyCoupon() reads the cart total before the price refresh settles.",
                        kind = KitBlockKind.Prose,
                        live = false,
                        streamed = false,
                        revealKey = "taste-b-r1",
                        revealGroup = "taste-b-g1",
                        registry = reveal,
                        motion = KitMotionLevel.Off,
                    )
                    KitReplyFooter(
                        copyLabel = "Copy",
                        moreLabel = "More",
                        onCopy = {},
                        onMore = {},
                    )
                    Spacer(Modifier.height(Space.S1.dp))
                    KitSummaryCard(
                        kicker = "Hidden from here",
                        attention = true,
                        title = "4 messages and 3 files",
                        supporting = "You can bring them back until you keep this point.",
                        secondary = KitAction("Review what went back", onClick = {}),
                        more = KitLineAction("Keep this point", onClick = {}),
                    )
                }
            }
        }
    }

    private fun shootCustom(name: String, variants: List<Variant>, content: @Composable () -> Unit) {
        Locale.setDefault(Locale.ENGLISH)
        for (v in variants) {
            runDesktopComposeUiTest(360 * 2, 780 * 2) {
                setContent {
                    CompositionLocalProvider(
                        LocalDensity provides Density(2f, v.fontScale),
                        LocalKitScreenInsets provides WindowInsets(top = 28.dp, bottom = 20.dp),
                    ) {
                        KitTheme(
                            appearance = KitAppearance(
                                mode = if (v.dark) AppearanceMode.Dark else AppearanceMode.Light,
                                style = Style.Solid,
                                motion = MotionLevel.Off,
                            ),
                            platform = DesktopKitPlatform(),
                            localeTag = "en",
                            stillFrame = true,
                        ) {
                            CompositionLocalProvider(LocalKitRedactor provides Redactor()) {
                                Box(Modifier.fillMaxSize()) { content() }
                            }
                        }
                    }
                }
                mainClock.autoAdvance = false
                mainClock.advanceTimeByFrame()
                mainClock.advanceTimeByFrame()
                waitForIdle()
                ImageIO.write(
                    onRoot().captureToImage().toAwtImage(),
                    "png",
                    File(out, "$name-${v.tag}.png"),
                )
            }
        }
    }

    @Test fun `render taste B waiting`() = shootCustom("taste-B-waiting", solidVariants) { TasteWaitingB() }

    private companion object {
        const val TURN_1 = "msg_user_1"
        const val TURN_2 = "msg_user_2"
        const val MODEL = "anthropic/claude-sonnet-4"
    }
}
