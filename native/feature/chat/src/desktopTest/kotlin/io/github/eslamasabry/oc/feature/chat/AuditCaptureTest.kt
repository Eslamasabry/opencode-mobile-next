package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.PermissionRequest
import io.github.eslamasabry.oc.core.model.transcript.MessageErrorKind
import io.github.eslamasabry.oc.engine.composer.phrases.ChatStartFacts
import io.github.eslamasabry.oc.engine.composer.phrases.StarterKind
import io.github.eslamasabry.oc.engine.requests.PendingRequest
import io.github.eslamasabry.oc.engine.requests.ask.PermissionAsk
import io.github.eslamasabry.oc.engine.transcript.state.BlockRow
import io.github.eslamasabry.oc.engine.transcript.state.FooterRow
import io.github.eslamasabry.oc.engine.transcript.state.Origin
import io.github.eslamasabry.oc.engine.transcript.state.RowKind
import io.github.eslamasabry.oc.engine.transcript.state.WorkLineRow
import io.github.eslamasabry.oc.engine.transcript.state.WorkState
import io.github.eslamasabry.oc.feature.chat.composer.BasicComposer
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.ComposerSlashCommand
import io.github.eslamasabry.oc.feature.chat.composer.presentation.AttachKind
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerActions
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerUi
import io.github.eslamasabry.oc.feature.chat.header.presentation.ChatHeaderUi
import io.github.eslamasabry.oc.feature.chat.header.presentation.HeaderAction
import io.github.eslamasabry.oc.feature.chat.header.ui.ChatHeaderSlot
import io.github.eslamasabry.oc.feature.chat.presentation.ChatConfirm
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPhase
import io.github.eslamasabry.oc.feature.chat.presentation.ChatStatusKind
import io.github.eslamasabry.oc.feature.chat.presentation.ChatStatusUi
import io.github.eslamasabry.oc.feature.chat.presentation.ChatUi
import io.github.eslamasabry.oc.feature.chat.presentation.LiveTurnUi
import io.github.eslamasabry.oc.feature.chat.presentation.MessageAction
import io.github.eslamasabry.oc.feature.chat.presentation.MessageMenuUi
import io.github.eslamasabry.oc.feature.chat.presentation.MessageTarget
import io.github.eslamasabry.oc.feature.chat.presentation.TargetKind
import io.github.eslamasabry.oc.feature.chat.rb.o42.ChatScene
import io.github.eslamasabry.oc.feature.chat.requests.presentation.CardState
import io.github.eslamasabry.oc.feature.chat.requests.presentation.ChatRequestActions
import io.github.eslamasabry.oc.feature.chat.requests.presentation.RequestCardUi
import io.github.eslamasabry.oc.feature.chat.requests.presentation.RequestSlotUi
import io.github.eslamasabry.oc.feature.chat.start.presentation.ChatStartActions
import io.github.eslamasabry.oc.feature.chat.start.presentation.ChatStartUi
import io.github.eslamasabry.oc.feature.chat.ui.ChatScreen
import io.github.eslamasabry.oc.feature.chat.world.ComposerModel
import io.github.eslamasabry.oc.feature.chat.world.ComposerModels
import io.github.eslamasabry.oc.feature.chat.world.DraftFacts
import io.github.eslamasabry.oc.feature.chat.world.PendingSend
import io.github.eslamasabry.oc.feature.chat.world.PendingState
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kitchat.code.KitStandaloneRedaction
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test

/**
 * Audit capture only (throwaway): the chat screen in the states its model allows, over the fixtures of
 * [ChatScene], [LooksCaptureTest], [SlashMenuComposerTest] and [E20S20ComposerTrayTest]. Nothing is asserted.
 *
 * Skipped: the "Select text" sheet and the picture viewer (need engine pictures), Switch model sheet (needs the
 * limit-switch pipeline), Find bar.
 */
@OptIn(ExperimentalTestApi::class)
class AuditCaptureTest {
    private val m = "chat"
    private val session = SessionId("ses_1")

    // ---- fixtures -------------------------------------------------------------------------------------------------

    private object NoRequestActions : ChatRequestActions {
        override fun shown(id: RequestId) = Unit
        override fun allowOnce(id: RequestId) = Unit
        override fun reject(id: RequestId) = Unit
        override fun askAlwaysAllow(id: RequestId) = Unit
        override fun confirmAlwaysAllow() = Unit
        override fun cancelAlwaysAllow() = Unit
        override fun keepAsking(id: RequestId) = Unit
        override fun choose(id: RequestId, option: String) = Unit
        override fun openOther(id: RequestId) = Unit
        override fun editOther(id: RequestId, text: String) = Unit
        override fun sendOther(id: RequestId) = Unit
        override fun openSheet(id: RequestId) = Unit
        override fun closeSheet(id: RequestId) = Unit
        override fun chooseInSheet(id: RequestId, prompt: Int, label: String) = Unit
        override fun editSheetOther(id: RequestId, prompt: Int, text: String) = Unit
        override fun sendSheet(id: RequestId) = Unit
        override fun undo(id: RequestId) = Unit
        override fun retry(id: RequestId) = Unit
    }

    private object NoStartActions : ChatStartActions {
        override fun chooseStarter(kind: StarterKind, label: String) = Unit
        override fun insertDone(id: Long) = Unit
    }

    private fun permission(id: String, permission: String, pattern: String, state: CardState = CardState.Waiting): RequestCardUi.Permission {
        val request =
            PermissionRequest(
                RequestId(id), session, permission, persistentListOf(pattern),
                JsonObject(mapOf("cwd" to JsonPrimitive("/home/me/shopfront"))),
                persistentListOf("$pattern *"), null, true, null,
            )
        return RequestCardUi.Permission(
            id = request.id,
            waitingSinceMs = null,
            state = state,
            ask = PermissionAsk.of(request, project = "shopfront", directory = "/home/me/shopfront"),
            canAlwaysAllow = true,
            captured = PendingRequest.Permission(request),
        )
    }

    private fun question(id: String): RequestCardUi.Question =
        RequestCardUi.Question(
            id = RequestId(id),
            waitingSinceMs = null,
            state = CardState.Waiting,
            title = "Deploy",
            question = "Ship the fix to staging now?",
            options = persistentListOf("Yes", "Not yet"),
            oneAnswer = true,
            custom = false,
        )

    private fun cm(p: String, pn: String, id: String, label: String, v: List<String> = emptyList()) =
        ComposerModel(p, id, label, true, variants = v, providerName = pn, reasoning = true, tools = true, contextLimit = 200_000)

    private val sonnet = cm("anthropic", "Anthropic", "sonnet-5", "Claude Sonnet 5", listOf("low", "high"))
    private val models = ComposerModels(
        choices = listOf(
            sonnet,
            cm("anthropic", "Anthropic", "opus-5", "Claude Opus 5"),
            cm("openai", "OpenAI", "gpt-6", "GPT-6 Sol"),
            cm("openai", "OpenAI", "gpt-6-mini", "GPT-6 Mini"),
            cm("google", "Google", "gemini-4", "Gemini 4 Pro"),
        ),
        selected = sonnet,
        recent = listOf(cm("openai", "OpenAI", "gpt-6", "GPT-6 Sol")),
    )

    private fun code(start: Int, text: String) =
        BlockRow("b:a1/p:$start", "u1", RowKind.Code, "p", start, text, false, false, Origin.Loaded)

    private val headerMenu = persistentListOf(
        HeaderAction.Find, HeaderAction.Details, HeaderAction.Share, HeaderAction.Reload, HeaderAction.Compact,
        HeaderAction.Fork, HeaderAction.Rename, HeaderAction.CopyTranscript, HeaderAction.Archive, HeaderAction.Delete,
    )

    @Composable
    private fun Chat(
        scene: ChatScene,
        state: ChatUi,
        header: ChatHeaderUi = ChatHeaderUi(title = state.title, projectName = "shopfront"),
        requests: RequestSlotUi = RequestSlotUi(),
        start: ChatStartUi = ChatStartUi(),
        facts: io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsUi =
            io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsUi.Empty,
    ) {
        KitStandaloneRedaction {
            KitSheetHost {
                ChatScreen(
                    state, scene.actions, scene.host,
                    requests = requests,
                    requestActions = NoRequestActions,
                    header = ChatHeaderSlot(header, scene.headerActions),
                    facts = facts,
                    start = start,
                    startActions = NoStartActions,
                )
            }
        }
    }

    private fun chat(
        screen: String,
        state: String,
        ui: (ChatScene) -> ChatUi,
        header: ChatHeaderUi? = null,
        requests: RequestSlotUi = RequestSlotUi(),
        start: ChatStartUi = ChatStartUi(),
        useSceneFacts: Boolean = false,
        steers: Boolean = false,
        withModels: Boolean = false,
        setup: ComposeUiTest.() -> Unit = {},
    ) {
        val scene = ChatScene()
        scene.composer = scene.composer.copy(steers = steers, models = if (withModels) models else scene.composer.models)
        val chatUi = ui(scene)
        val facts = if (useSceneFacts) scene.facts() else io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsUi.Empty
        Audit.shoot(m, screen, state, advanceMs = 1_500, setup = setup) {
            Chat(scene, chatUi, header ?: ChatHeaderUi(title = chatUi.title, projectName = "shopfront"), requests, start, facts)
        }
    }

    private fun waited(block: ComposeUiTest.() -> Unit): ComposeUiTest.() -> Unit = {
        block()
        waitForIdle()
        mainClock.advanceTimeBy(800)
        waitForIdle()
    }

    // ---- transcript states ----------------------------------------------------------------------------------------

    @Test
    fun transcriptStates() = Audit.guard("chat__transcript__build") {
        chat("transcript", "idle-with-transcript", { it.live() }, useSceneFacts = true)

        chat("transcript", "long-title", {
            it.live().copy(title = "Investigate why the checkout coupon price refresh races the cart total settle step on slow connections in the staging environment")
        }, useSceneFacts = true)

        chat("transcript", "running", { s ->
            s.live().copy(
                rows = persistentListOf(
                    block("u1", "a2", "p2", 0, "Running the auth tests now.", live = true),
                    code(200, "```kotlin\nfun remember(route: String) {\n    if (route.startsWith(\"/login\")) return\n    target.value = route\n}\n```"),
                    WorkLineRow("work:u1", "u1", calls = 3, failed = 0, state = WorkState.Running),
                    block("u1", "a1", "p", 0, "I found it. `LoginRedirect.remember` stores **/login** itself as the next route, so after sign-in the app redirects to the login page again. I am guarding that case and adding a test."),
                    prompt("u1", "Fix the login redirect loop. After signing in the app sends me straight back to /login."),
                ),
                live = LiveTurnUi(stopping = false, canStopAfterStep = true),
            )
        }, steers = true)

        chat("transcript", "running-stopping", { s ->
            s.live().copy(
                rows = persistentListOf(
                    WorkLineRow("work:u1", "u1", calls = 2, failed = 1, state = WorkState.Running),
                    prompt("u1", "Run the whole suite and fix whatever fails."),
                ),
                live = LiveTurnUi(stopping = true),
            )
        }, steers = true)

        chat("transcript", "failed-calls-work-line", { s ->
            s.live().copy(
                rows = persistentListOf(
                    footer("u1"),
                    block("u1", "a1", "p", 0, "Two of the five commands failed, the rest passed."),
                    WorkLineRow("work:u1", "u1", calls = 5, failed = 2, state = WorkState.Done),
                    prompt("u1", "Run the checks."),
                ),
            )
        })

        chat("transcript", "long-unbroken-text", { s ->
            s.live().copy(
                rows = persistentListOf(
                    footer("u1"),
                    block("u1", "a1", "p", 0, "Path: /home/me/shopfront/src/main/kotlin/io/github/shopfront/checkout/CouponPriceRefreshCoordinatorFactoryImplementation.kt and a token abcdefghijklmnopqrstuvwxyz0123456789abcdefghijklmnopqrstuvwxyz0123456789 that never breaks."),
                    prompt("u1", "Where is the coordinator? ".repeat(12)),
                ),
            )
        })
    }

    @Test
    fun emptyAndLoadStates() = Audit.guard("chat__loadstates__build") {
        chat("new-chat", "empty-starters", { it.live().copy(rows = persistentListOf(), title = null) },
            start = ChatStartUi(
                facts = ChatStartFacts(directory = "/home/me/shopfront", entries = 12, git = true, hasHistory = false, changes = 0, settled = true),
                showSuggestions = true,
                emptyEligible = true,
            ),
            header = ChatHeaderUi(title = null, projectName = "shopfront"),
        )
        chat("new-chat", "empty-no-starters", { it.live().copy(rows = persistentListOf(), title = null) })
        chat("loading", "skeleton", { ChatUi.loading(io.github.eslamasabry.oc.feature.chat.Ref) })
        chat("load-failed", "offline", {
            ChatUi.loading(io.github.eslamasabry.oc.feature.chat.Ref).copy(phase = ChatPhase.Failed(Failure.Network(NetworkWhy.Offline)))
        })
        chat("status", "offline-banner", { it.live().copy(status = ChatStatusUi("offline", ChatStatusKind.Offline)) }, useSceneFacts = true)
        chat("status", "connecting-banner", { it.live().copy(status = ChatStatusUi("connecting", ChatStatusKind.Connecting, retryInSeconds = 8)) }, useSceneFacts = true)
    }

    @Test
    fun errorAndUnsentStates() = Audit.guard("chat__errors__build") {
        chat("errors", "unsent-unconfirmed", { s ->
            s.live().copy(
                rows = persistentListOf(footer("u1"), block("u1", "a1", "p", 0, "Ready when you are."), prompt("u1", "Fix the flaky checkout test")),
                pending = persistentListOf(PendingSend(1L, "Run the full test suite", PendingState.Unconfirmed)),
                status = ChatStatusUi("send-unconfirmed", ChatStatusKind.SendUnconfirmed, localId = 1L),
            )
        })
        chat("errors", "send-failed", { s ->
            s.live().copy(
                rows = persistentListOf(footer("u1"), block("u1", "a1", "p", 0, "Ready when you are."), prompt("u1", "Fix the flaky checkout test")),
                pending = persistentListOf(PendingSend(2L, "Run the full test suite", PendingState.Failed, Failure.Network(NetworkWhy.Offline))),
                status = ChatStatusUi("send-failed", ChatStatusKind.SendFailed, Failure.Network(NetworkWhy.Offline), localId = 2L),
            )
        })
        chat("errors", "sending", { s ->
            s.live().copy(
                rows = persistentListOf(footer("u1"), block("u1", "a1", "p", 0, "Ready when you are."), prompt("u1", "Fix the flaky checkout test")),
                pending = persistentListOf(PendingSend(3L, "Run the full test suite", PendingState.Sending)),
            )
        })
        chat("errors", "model-not-found", { s ->
            s.live().copy(
                rows = persistentListOf(
                    FooterRow(
                        "footer:u1", "u1", failed = true,
                        reason = "ProviderModelNotFoundError: Model not found: openai/gpt-5.6. Did you mean: gpt-5.6-pro?",
                        errorKind = MessageErrorKind.ModelNotFound,
                    ),
                    prompt("u1", "Fix the flaky checkout test"),
                ),
            )
        })
        chat("errors", "provider-auth", { s ->
            s.live().copy(
                rows = persistentListOf(
                    FooterRow("footer:u1", "u1", failed = true, reason = "Authentication failed for provider", errorKind = MessageErrorKind.ProviderAuth),
                    prompt("u1", "Fix the flaky checkout test"),
                ),
            )
        })
        chat("errors", "unknown", { s ->
            s.live().copy(
                rows = persistentListOf(
                    FooterRow("footer:u1", "u1", failed = true, reason = "Something unexpected happened", errorKind = MessageErrorKind.Unknown),
                    prompt("u1", "Fix the flaky checkout test"),
                ),
            )
        })
        chat("errors", "stopped-footer", { s ->
            s.live().copy(
                rows = persistentListOf(
                    FooterRow("footer:u1", "u1", failed = false, stopped = true),
                    block("u1", "a1", "p", 0, "I was starting to look at the coupon code when"),
                    prompt("u1", "Fix the flaky checkout test"),
                ),
            )
        })
    }

    // ---- requests -------------------------------------------------------------------------------------------------

    @Test
    fun requestStates() = Audit.guard("chat__requests__build") {
        val shell = permission("p1", "bash", "npm test")
        val edit = permission("p2", "edit", "src/game.js")
        val fetch = permission("p3", "webfetch", "https://example.com/docs")
        chat("approval-card", "one-permission", { it.live() }, requests = RequestSlotUi(listOf<RequestCardUi>(shell).toImmutableList()), useSceneFacts = true)
        chat("approval-card", "four-cards", { it.live() },
            requests = RequestSlotUi(listOf(shell, question("q1"), edit, fetch).toImmutableList()), useSceneFacts = true)
        chat("approval-card", "no-longer-needed", { it.live() },
            requests = RequestSlotUi(listOf<RequestCardUi>(permission("p1", "bash", "npm test", CardState.NoLongerNeeded), edit).toImmutableList()),
            useSceneFacts = true)
        chat("permission-details", "sheet-open", { it.live() },
            requests = RequestSlotUi(listOf<RequestCardUi>(shell).toImmutableList()), useSceneFacts = true,
            setup = waited { onAllNodesWithText("Details")[0].performClick() })
    }

    // ---- menus and sheets -----------------------------------------------------------------------------------------

    @Test
    fun menuStates() = Audit.guard("chat__menus__build") {
        chat("header-menu", "open", { it.live() }, header = ChatHeaderUi(title = "Fix flaky checkout test", projectName = "shopfront", menu = headerMenu, menuOpen = true, hasPrompt = true), useSceneFacts = true)

        val prompt = MessageTarget("msg_user", "prompt:msg_user", TargetKind.Prompt, "msg_user")
        val reply = MessageTarget("msg_user", "footer:msg_user", TargetKind.Reply, "msg_assistant")
        chat("message-menu", "prompt", {
            it.live().copy(menu = MessageMenuUi(prompt, persistentListOf(MessageAction.Copy, MessageAction.SelectText, MessageAction.EditAndResend, MessageAction.Fork, MessageAction.Delete)))
        }, useSceneFacts = true)
        chat("message-menu", "reply", {
            it.live().copy(menu = MessageMenuUi(reply, persistentListOf(MessageAction.Copy, MessageAction.Share, MessageAction.ReadAloud, MessageAction.Fork, MessageAction.Delete)))
        }, useSceneFacts = true)
        chat("message-menu", "delete-confirm", {
            it.live().copy(confirm = ChatConfirm.DeleteMessage(prompt))
        }, useSceneFacts = true)

        chat("model-picker", "sheet-open", { it.live() }, useSceneFacts = true, withModels = true,
            setup = waited { onNodeWithContentDescription("Model").performClick() })
    }

    // ---- composer inside the chat package: slash list and attach tray ----------------------------------------------

    private object ComposerActions : BasicComposerActions {
        override fun draftChanged(text: String) {}
        override suspend fun send(text: String) = true
        override suspend fun selectModel(choice: ComposerModel?, variant: String) = true
        override fun attachFile() {}
        override fun attach(kind: AttachKind) {}
    }

    private fun app(slash: String, title: String) = ComposerSlashCommand(slash, slash, title, "$title here", onRun = {})

    private fun chrome(withCommands: Boolean) =
        ComposerChrome(
            { false }, { false }, {}, { null }, {}, { null }, {}, {}, { false },
            openCommands = if (withCommands) ({}) else null,
            slashCommands = {
                listOf(
                    app("agents", "Agent"), app("approvals", "Approvals"), app("connect", "Connect provider"),
                    app("new", "New session"), app("timeline", "Message timeline"), app("find", "Find"),
                    ComposerSlashCommand("server-init", "init", "/init", "Create AGENTS.md", inserts = true, onRun = {}),
                )
            },
        )

    private fun composerShot(screen: String, state: String, ui: BasicComposerUi, withCommands: Boolean = true, setup: ComposeUiTest.() -> Unit = {}) {
        Audit.shoot(m, screen, state, setup = setup) {
            KitSheetHost {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    BasicComposer(ui, ComposerActions, chrome(withCommands))
                }
            }
        }
    }

    private val baseUi = BasicComposerUi(
        available = true, sending = false, steers = true, draft = DraftFacts(), targetKey = "p/s",
        pickingFile = false, attachmentFailure = null, filesAvailable = true,
        attachKinds = setOf(AttachKind.Photos, AttachKind.Camera, AttachKind.Clipboard), models = models,
    )

    private fun ComposeUiTest.type(text: String) {
        onNode(
            hasSetTextAction() and hasAnyAncestor(hasTestTag("chat-composer-field")),
            useUnmergedTree = true,
        ).performTextInput(text)
        waitForIdle()
    }

    @Test
    fun composerStates() = Audit.guard("chat__composer__build") {
        composerShot("composer", "idle", baseUi)
        composerShot("composer", "tray-open", baseUi, setup = waited { onNodeWithContentDescription("Attach and more").performClick() })
        composerShot("composer", "slash-list", baseUi, setup = waited { type("/") })
        composerShot("composer", "slash-filtered", baseUi, setup = waited { type("/appr") })
        composerShot("composer", "sending", baseUi.copy(sending = true))
        composerShot("composer", "no-files", baseUi.copy(filesAvailable = false, attachKinds = emptySet()))
    }
}
