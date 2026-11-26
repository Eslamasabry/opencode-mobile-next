package io.github.eslamasabry.oc.feature.review

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.GatewayFactory
import io.github.eslamasabry.oc.core.domain.GatewayPair
import io.github.eslamasabry.oc.core.domain.GatewaySpec
import io.github.eslamasabry.oc.core.domain.ProbeResult
import io.github.eslamasabry.oc.core.domain.ProbeTarget
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.domain.ServerOperationsGateway
import io.github.eslamasabry.oc.core.domain.StagedRevertGateway
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.FileDiff
import io.github.eslamasabry.oc.core.model.ProfileTrust
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.model.ServerPage
import io.github.eslamasabry.oc.core.model.ServerProfile
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionRevert
import io.github.eslamasabry.oc.core.model.VersionControlHealth
import io.github.eslamasabry.oc.core.model.VersionControlSetupState
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.MsgTime
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.core.testing.FakeOperationsGateway
import io.github.eslamasabry.oc.core.testing.FakeServerGateway
import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.core.testing.TestDispatchers
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.connection.ConnectionSession
import io.github.eslamasabry.oc.engine.delivery.ConnectionDelivery
import io.github.eslamasabry.oc.engine.review.ConversationReview
import io.github.eslamasabry.oc.engine.review.ReviewGateways
import io.github.eslamasabry.oc.feature.review.presentation.ChangesActions
import io.github.eslamasabry.oc.feature.review.presentation.ChangesPresenter
import io.github.eslamasabry.oc.feature.review.presentation.ReviewLoad
import io.github.eslamasabry.oc.feature.review.presentation.ReviewNavigation
import io.github.eslamasabry.oc.feature.review.presentation.ReviewProjectSetup
import io.github.eslamasabry.oc.feature.review.presentation.StagedViewModel
import io.github.eslamasabry.oc.feature.review.presentation.UndoFromHereActions
import io.github.eslamasabry.oc.feature.review.presentation.UndoFromHerePresenter
import io.github.eslamasabry.oc.feature.review.presentation.UndoFromHereUi
import io.github.eslamasabry.oc.feature.review.presentation.UndoFromHereViewModel
import io.github.eslamasabry.oc.feature.review.ui.ChangesScreen
import io.github.eslamasabry.oc.feature.review.ui.StagedScreen
import io.github.eslamasabry.oc.feature.review.ui.UndoFromHereScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.ReviewRoute
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * B-473 & B-475: honest undo confirmation (files + messages + redo), no-rollback agents never promise file
 * rollback, and non-Git undo offers one-tap "Start a Git repository for this project" on the active folder.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class UndoDirectTruthTest {
    private val ref = ConversationRef(ProfileId("p1"), SessionId("ses_1"))
    private val profile =
        ServerProfile(
            ref.profileId,
            "Phone",
            BackendKind.OpenCode2,
            ServerOrigin("http", "127.0.0.1", 4096),
            ProfileTrust(null, null),
            false,
            null,
        )
    private var previousLocale: Locale = Locale.getDefault()

    @BeforeTest
    fun setUp() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        Locale.setDefault(previousLocale)
    }

    @Composable
    private fun Themed(content: @Composable () -> Unit) {
        KitTheme(
            platform = DesktopKitPlatform(),
            appearance = KitAppearance(motion = MotionLevel.Off),
            localeTag = "en",
            stillFrame = true,
        ) {
            Box(Modifier.size(360.dp, 800.dp)) { content() }
        }
    }

    private fun prompt(id: String, at: Long, text: String = "make it blue"): MessageWithParts =
        MessageWithParts(
            MessageInfo(id, ref.sessionId.raw, Role.User, time = MsgTime(at, at)),
            persistentListOf(Part("p$id", id, ref.sessionId.raw, PartType.Text, "text", text = text)),
        )

    private fun reply(id: String, at: Long, path: String = "src/a.kt"): MessageWithParts {
        val diff =
            JsonObject(
                mapOf(
                    "before" to JsonPrimitive("a\n"),
                    "after" to JsonPrimitive("b\n"),
                    "additions" to JsonPrimitive(2),
                    "deletions" to JsonPrimitive(1),
                )
            )
        val tool =
            ToolState(
                ToolStatus.Completed,
                input = JsonObject(mapOf("filePath" to JsonPrimitive(path))),
                metadata = JsonObject(mapOf("filediff" to diff)),
            )
        val part = Part("t$id", id, ref.sessionId.raw, PartType.Tool, "tool", toolName = "edit", tool = tool)
        return MessageWithParts(
            MessageInfo(id, ref.sessionId.raw, Role.Assistant, time = MsgTime(at, at)),
            persistentListOf(part),
        )
    }

    private class StagedFake(
        val base: FakeServerGateway,
        val history: List<MessageWithParts>,
        var sessionDir: String = "/work/proj",
    ) : ServerGateway by base, StagedRevertGateway {
        var staged: SessionRevert? = null
        val calls = mutableListOf<String>()

        override suspend fun session(id: SessionId): Outcome<Session> =
            Session(id, directory = sessionDir, reverted = staged != null, stagedRevert = staged).ok()

        override suspend fun messagePage(
            id: SessionId,
            cursor: String?,
            limit: Int,
        ): Outcome<ServerPage<MessageWithParts>> = ServerPage(history.toImmutableList(), null).ok()

        override suspend fun sessionRevertPrompt(session: SessionId, message: MessageId): Outcome<String?> =
            history.firstOrNull { it.info.id == message.raw && it.info.role == Role.User }?.parts?.first()?.text.ok()

        override suspend fun stageSessionRevert(
            session: SessionId,
            message: MessageId,
            applyFiles: Boolean,
        ): Outcome<SessionRevert> {
            calls += "stage ${message.raw} $applyFiles"
            val revert = SessionRevert(message, null, null, persistentListOf())
            staged = revert
            return revert.ok()
        }

        override suspend fun clearSessionRevert(session: SessionId): Outcome<Unit> =
            Unit.ok().also { staged = null }

        override suspend fun commitSessionRevert(session: SessionId): Outcome<Unit> =
            Unit.ok().also { staged = null }
    }

    private class RecordingOps(var vcsState: VersionControlSetupState) : FakeOperationsGateway() {
        var gitInitCalls = 0

        override suspend fun versionControlHealth(): Outcome<VersionControlHealth> =
            VersionControlHealth(null, null, persistentListOf(), vcsState).ok()

        override suspend fun initializeGitRepository(): Outcome<Unit> {
            gitInitCalls++
            vcsState = VersionControlSetupState.Git
            return Unit.ok()
        }
    }

    private class Nav : ReviewNavigation {
        val went = mutableListOf<String>()

        override fun back() { went += "back" }

        override fun openFile(path: String, staged: Boolean) { went += "file $path $staged" }

        override fun openStaged() { went += "staged" }
    }

    private class TestAppNavigator : AppNavigator {
        override val top: StateFlow<AppRoute?> = MutableStateFlow(null)

        override fun open(route: AppRoute) = Unit

        override fun openChat(ref: ConversationRef, focusComposer: Boolean) = Unit

        override fun land() = Unit

        override fun replaceRoot(root: AppRoute) = Unit

        override fun back() = Unit
    }

    private val idleUndo =
        object : UndoFromHereActions {
            override fun back() = Unit

            override fun putFilesBack(apply: Boolean) = Unit

            override fun confirm() = Unit
        }

    private val idleChanges =
        object : ChangesActions {
            override fun back() = Unit

            override fun retry() = Unit

            override fun openFile(path: String) = Unit

            override fun keepAll() = Unit

            override fun undoAll() = Unit

            override fun confirmUndoAll() = Unit

            override fun dismissConfirm() = Unit

            override fun openStaged() = Unit

            override fun sendBack() = Unit

            override fun sendBackNote(note: String?) = Unit
        }

    private suspend fun TestScope.openHub(
        gateway: ServerGateway,
        ops: ServerOperationsGateway,
    ): ReviewHub {
        val dispatchers = TestDispatchers(UnconfinedTestDispatcher(testScheduler))
        val app = AppCoroutineScope(backgroundScope.coroutineContext)
        val clock = TestClock()
        val registry =
            ConnectionRegistry(
                setOf(
                    object : GatewayFactory {
                        override val backend = BackendKind.OpenCode2

                        override suspend fun probe(target: ProbeTarget): Outcome<ProbeResult> =
                            Outcome.Failed(Failure.Network(NetworkWhy.Refused))

                        override suspend fun connect(spec: GatewaySpec): Outcome<GatewayPair> =
                            Outcome.Ok(GatewayPair(gateway, ops))
                    }
                ),
                { p, pair, scope ->
                    object : ConnectionGraph, ReviewGraphAccessors {
                        override val profile = p
                        override val scope = scope
                        override val session = ConnectionSession(p, pair.live, scope)
                        override val reviewGateway = pair.live
                        override val reviewOperations = pair.operations
                        override val reviewDelivery =
                            ConnectionDelivery(pair.live, p, scope, clock, emptySet(), dispatchers)
                    }
                },
                app,
                dispatchers,
            )
        registry.open(profile)
        return ReviewHub(registry)
    }

    @Test
    fun `B-473 Changes Undo all on non-Git project does not promise file rollback or pass applyFiles true`() =
        runComposeUiTest {
            val history = listOf(prompt("u1", 1), reply("a1", 2, "src/a.kt"), reply("a2", 3, "src/b.kt"))
            val base = FakeServerGateway(listOf(Session(ref.sessionId))).apply {
                capabilities.value = capabilities.value.copy(sessionRevert = true)
            }
            val fake = StagedFake(base, history)
            val gateways = MutableStateFlow<ReviewGateways?>(ReviewGateways(fake, FakeOperationsGateway()))
            val nav = Nav()
            var confirmUi: io.github.eslamasabry.oc.feature.review.presentation.ChangesUi? = null
            runTest(UnconfinedTestDispatcher()) {
                val review = ConversationReview(ref.sessionId, gateways, backgroundScope)
                val p =
                    ChangesPresenter(
                        review,
                        null,
                        nav,
                        backgroundScope,
                        setup = ReviewProjectSetup { VersionControlSetupState.Absent },
                    )
                p.undoAll()
                confirmUi = p.state.value
                p.confirmUndoAll()
                assertEquals(listOf("stage u1 false"), fake.calls, "non-Git Undo all must pass applyFiles = false")
            }
            setContent { Themed { ChangesScreen(confirmUi!!, idleChanges) } }
            onNodeWithText("Undo 2 changed files?").assertDoesNotExist()
            onNodeWithText("puts your files back", substring = true, ignoreCase = true).assertDoesNotExist()
            onNodeWithText("files stay as they are", substring = true, ignoreCase = true).assertIsDisplayed()
        }

    @Test
    fun `B-473 Undo from here states truth about both files and messages for Git with and without file rollback`() =
        runComposeUiTest {
            setContent {
                Themed {
                    UndoFromHereScreen(
                        UndoFromHereUi(
                            load = ReviewLoad.Ready,
                            staged = true,
                            prompt = "make it blue",
                            applyFiles = true,
                        ),
                        idleUndo,
                    )
                }
            }
            onNodeWithText("files go back", substring = true, ignoreCase = true).assertIsDisplayed()
            onNodeWithText("put everything back", substring = true, ignoreCase = true).assertIsDisplayed()
        }

    @Test
    fun `B-473 Undo from here with file toggle off tells truth that files stay as they are`() =
        runComposeUiTest {
            setContent {
                Themed {
                    UndoFromHereScreen(
                        UndoFromHereUi(
                            load = ReviewLoad.Ready,
                            staged = true,
                            prompt = "make it blue",
                            applyFiles = false,
                        ),
                        idleUndo,
                    )
                }
            }
            onNodeWithText("files stay as they are", substring = true, ignoreCase = true).assertIsDisplayed()
            onNodeWithText("put the messages back", substring = true, ignoreCase = true).assertIsDisplayed()
        }

    @Test
    fun `B-473 Undo from here on non-Git one-step server never promises file rollback`() =
        runComposeUiTest {
            val history = listOf(prompt("u1", 1), reply("a1", 2, "src/a.kt"))
            val base = FakeServerGateway(listOf(Session(ref.sessionId))).apply {
                capabilities.value = capabilities.value.copy(sessionRevert = true, sessionDiff = true)
            }
            val plain =
                object : ServerGateway by base {
                    override suspend fun messagePage(
                        id: SessionId,
                        cursor: String?,
                        limit: Int,
                    ): Outcome<ServerPage<MessageWithParts>> = ServerPage(history.toImmutableList(), null).ok()
                }
            val gateways = MutableStateFlow<ReviewGateways?>(ReviewGateways(plain, FakeOperationsGateway()))
            var ui: UndoFromHereUi? = null
            runTest(UnconfinedTestDispatcher()) {
                val p =
                    UndoFromHerePresenter(
                        ConversationReview(ref.sessionId, gateways, backgroundScope),
                        "u1",
                        Nav(),
                        backgroundScope,
                        setup = ReviewProjectSetup { VersionControlSetupState.Absent },
                    )
                ui = p.state.value
                assertTrue(ui!!.noFileCopies, "one-step non-Git undo must set noFileCopies")
            }
            setContent { Themed { UndoFromHereScreen(ui!!, idleUndo) } }
            onNodeWithText("files go back", substring = true, ignoreCase = true).assertDoesNotExist()
            onNodeWithText("stay as they are", substring = true, ignoreCase = true).assertIsDisplayed()
        }

    @Test
    fun `B-473 Undo from here on no-rollback agent never promises file rollback`() =
        runComposeUiTest {
            val history = listOf(prompt("u1", 1), reply("a1", 2, "src/a.kt"))
            val base = FakeServerGateway(listOf(Session(ref.sessionId))).apply {
                capabilities.value = capabilities.value.copy(sessionRevert = true, sessionDiff = false)
            }
            val plain =
                object : ServerGateway by base {
                    override suspend fun messagePage(
                        id: SessionId,
                        cursor: String?,
                        limit: Int,
                    ): Outcome<ServerPage<MessageWithParts>> = ServerPage(history.toImmutableList(), null).ok()
                }
            val gateways = MutableStateFlow<ReviewGateways?>(ReviewGateways(plain, FakeOperationsGateway()))
            var ui: UndoFromHereUi? = null
            runTest(UnconfinedTestDispatcher()) {
                val p =
                    UndoFromHerePresenter(
                        ConversationReview(ref.sessionId, gateways, backgroundScope),
                        "u1",
                        Nav(),
                        backgroundScope,
                    )
                ui = p.state.value
            }
            setContent { Themed { UndoFromHereScreen(ui!!, idleUndo) } }
            onNodeWithText("files go back", substring = true, ignoreCase = true).assertDoesNotExist()
            onNodeWithText("can't put files back", substring = true, ignoreCase = true).assertIsDisplayed()
        }

    @Test
    fun `B-475 non-Git undo offers one-tap Start a Git repository for this project on active folder`() =
        runComposeUiTest {
            val history = listOf(prompt("u1", 1), reply("a1", 2))
            val base = FakeServerGateway(listOf(Session(ref.sessionId, directory = "/work/proj"))).apply {
                capabilities.value = capabilities.value.copy(sessionRevert = true, gitInit = true)
                setLocation(ServerLocation("/work/proj", null))
            }
            val fake = StagedFake(base, history, sessionDir = "/work/proj")
            val ops = RecordingOps(VersionControlSetupState.Absent)
            var vm: UndoFromHereViewModel? = null
            runTest(UnconfinedTestDispatcher()) {
                val hub = openHub(fake, ops)
                vm = UndoFromHereViewModel(ReviewRoute.UndoFromHere(ref, "u1"), hub, TestAppNavigator())
                runCurrent()
            }
            setContent {
                val state by vm!!.state.collectAsState()
                Themed { UndoFromHereScreen(state, vm!!) }
            }
            onNodeWithText("Start a Git repository for this project").assertIsDisplayed().performClick()
            waitForIdle()
            assertEquals(1, ops.gitInitCalls)
        }

    @Test
    fun `B-475 non-Git undo hides Git init when conversation is in another folder`() =
        runComposeUiTest {
            val history = listOf(prompt("u1", 1), reply("a1", 2))
            val base = FakeServerGateway(listOf(Session(ref.sessionId, directory = "/work/other"))).apply {
                capabilities.value = capabilities.value.copy(sessionRevert = true, gitInit = true)
                setLocation(ServerLocation("/work/proj", null))
            }
            val fake = StagedFake(base, history, sessionDir = "/work/other")
            val ops = RecordingOps(VersionControlSetupState.Absent)
            var vm: UndoFromHereViewModel? = null
            runTest(UnconfinedTestDispatcher()) {
                val hub = openHub(fake, ops)
                vm = UndoFromHereViewModel(ReviewRoute.UndoFromHere(ref, "u1"), hub, TestAppNavigator())
                runCurrent()
            }
            setContent {
                val state by vm!!.state.collectAsState()
                Themed { UndoFromHereScreen(state, vm!!) }
            }
            onNodeWithText("Start a Git repository for this project").assertDoesNotExist()
        }

    @Test
    fun `B-475 non-Git staged review offers one-tap Start a Git repository for this project`() =
        runComposeUiTest {
            val history = listOf(prompt("u1", 1), reply("a1", 2))
            val base = FakeServerGateway(listOf(Session(ref.sessionId, directory = "/work/proj"))).apply {
                capabilities.value = capabilities.value.copy(sessionRevert = true, gitInit = true)
                setLocation(ServerLocation("/work/proj", null))
            }
            val fake =
                StagedFake(base, history, sessionDir = "/work/proj").apply {
                    staged = SessionRevert(MessageId("u1"), null, null, persistentListOf())
                }
            val ops = RecordingOps(VersionControlSetupState.Absent)
            var vm: StagedViewModel? = null
            runTest(UnconfinedTestDispatcher()) {
                val hub = openHub(fake, ops)
                vm = StagedViewModel(ReviewRoute.Staged(ref), hub, TestAppNavigator())
                runCurrent()
            }
            setContent {
                val state by vm!!.state.collectAsState()
                Themed { StagedScreen(state, vm!!) }
            }
            onNodeWithText("Start a Git repository for this project").assertIsDisplayed().performClick()
            waitForIdle()
            assertEquals(1, ops.gitInitCalls)
        }
}
