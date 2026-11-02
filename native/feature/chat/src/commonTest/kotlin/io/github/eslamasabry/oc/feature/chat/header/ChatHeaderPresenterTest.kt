package io.github.eslamasabry.oc.feature.chat.header

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.feature.chat.FakeChatWorld
import io.github.eslamasabry.oc.feature.chat.Immediate
import io.github.eslamasabry.oc.feature.chat.RecordingNavigator
import io.github.eslamasabry.oc.feature.chat.block
import io.github.eslamasabry.oc.feature.chat.header.presentation.ChatHeaderPresenter
import io.github.eslamasabry.oc.feature.chat.header.presentation.ClipKind
import io.github.eslamasabry.oc.feature.chat.header.presentation.ContextLevel
import io.github.eslamasabry.oc.feature.chat.header.presentation.HeaderAction
import io.github.eslamasabry.oc.feature.chat.header.presentation.HeaderConfirm
import io.github.eslamasabry.oc.feature.chat.header.presentation.HeaderRules
import io.github.eslamasabry.oc.feature.chat.header.presentation.HeaderSheet
import io.github.eslamasabry.oc.feature.chat.header.presentation.TranscriptWords
import io.github.eslamasabry.oc.feature.chat.header.freemodel.FreeModel
import io.github.eslamasabry.oc.feature.chat.header.freemodel.FreeModelFacts
import io.github.eslamasabry.oc.feature.chat.header.freemodel.FreeNoteStore
import io.github.eslamasabry.oc.feature.chat.header.world.ChatHeaderPort
import io.github.eslamasabry.oc.feature.chat.header.world.HeaderFacts
import io.github.eslamasabry.oc.feature.chat.prompt
import io.github.eslamasabry.oc.feature.chat.stateOf
import io.github.eslamasabry.oc.feature.chat.thought
import io.github.eslamasabry.oc.feature.chat.world.ComposerModel
import io.github.eslamasabry.oc.feature.chat.world.ComposerModels
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeHeaderPort : ChatHeaderPort {
    override val facts = MutableStateFlow(HeaderFacts(serverName = "Home PC", directory = "/home/me/projects/app/", connected = true))
    val calls = mutableListOf<String>()
    var renameResult: Outcome<Unit> = Outcome.Ok(Unit)
    var shareResult: Outcome<String?> = Outcome.Ok("https://opncd.ai/s/abc")
    var forkResult: Outcome<SessionId> = Outcome.Ok(SessionId("s9"))
    var deleteResult: Outcome<Unit> = Outcome.Ok(Unit)
    var archiveResult: Outcome<Unit> = Outcome.Ok(Unit)

    override suspend fun refresh(): Outcome<Unit> = Outcome.Ok(Unit).also { calls += "refresh" }

    override suspend fun rename(title: String): Outcome<Unit> = renameResult.also { calls += "rename $title" }

    override suspend fun share(): Outcome<String?> = shareResult.also { calls += "share" }

    override suspend fun unshare(): Outcome<Unit> = Outcome.Ok(Unit).also { calls += "unshare" }

    override suspend fun fork(): Outcome<SessionId> = forkResult.also { calls += "fork" }

    override suspend fun archive(): Outcome<Unit> = archiveResult.also { calls += "archive" }

    override suspend fun delete(): Outcome<Unit> = deleteResult.also { calls += "delete" }
}

class ModelWorld : FakeChatWorld() {
    val modelFlow = MutableStateFlow(ComposerModels())
    override val models: StateFlow<ComposerModels> = modelFlow
    var selectResult: Outcome<Unit> = Outcome.Ok(Unit)
    var loadResult: Outcome<Unit> = Outcome.Ok(Unit)
    val picked = mutableListOf<String>()

    override suspend fun loadModels(): Outcome<Unit> {
        calls += "loadModels"
        if (loadResult is Outcome.Ok) modelFlow.value = ComposerModels(choices = listOf(Gpt, Claude), selected = Gpt)
        return loadResult
    }

    override suspend fun selectModel(choice: ComposerModel?, variant: String): Outcome<Unit> {
        picked += choice?.modelId ?: "default"
        if (selectResult is Outcome.Ok) modelFlow.value = modelFlow.value.copy(selected = choice)
        return selectResult
    }

    companion object {
        val Gpt = ComposerModel("openai", "gpt-5", "GPT-5", supportsAttachments = true)
        val Claude = ComposerModel("anthropic", "sonnet", "Sonnet", supportsAttachments = true)
    }
}

class ChatHeaderPresenterTest {
    private val world = ModelWorld()
    private val port = FakeHeaderPort()
    private val nav = RecordingNavigator()
    private val p = ChatHeaderPresenter(world, port, nav, Immediate)
    private val words = TranscriptWords("OpenCode conversation", "User", "Assistant", "Reasoning", "Only loaded messages")
    private val allCaps =
        ServerCapabilities(
            sessionShare = true,
            sessionFork = true,
            sessionArchive = true,
            sessionDelete = true,
            sessionModelProviderSwitching = true,
            serverCatalog = true,
        )

    @Test
    fun `B-CHAT-018 project name derives from a path ignoring trailing slashes and Windows separators`() {
        assertEquals("app", HeaderRules.projectName("/home/me/projects/app/"))
        assertEquals("app", HeaderRules.projectName("C:\\Users\\me\\app\\"))
        assertEquals("app", HeaderRules.projectName("app"))
        assertNull(HeaderRules.projectName("/"))
        assertNull(HeaderRules.projectName(null))
    }

    @Test
    fun `B-CHAT-019 context fraction is tokens over window and unknown when either is missing`() {
        assertEquals(50, HeaderRules.contextPercent(500, 1000))
        assertNull(HeaderRules.contextPercent(null, 1000))
        assertNull(HeaderRules.contextPercent(10, null))
        assertNull(HeaderRules.contextPercent(10, 0))
    }

    @Test
    fun `B-CHAT-057 context levels are calm under 70 warning to 94 full from 95 and percent is floored and clamped`() {
        assertEquals(69, HeaderRules.contextPercent(699, 1000))
        assertEquals(ContextLevel.Calm, HeaderRules.contextLevel(69))
        assertEquals(ContextLevel.Warning, HeaderRules.contextLevel(70))
        assertEquals(ContextLevel.Warning, HeaderRules.contextLevel(94))
        assertEquals(ContextLevel.Full, HeaderRules.contextLevel(95))
        assertEquals(100, HeaderRules.contextPercent(5000, 1000))
    }

    @Test
    fun `B-CHAT-020 header shows the title and server with project and the menu offers Continue with`() {
        world.capabilities.value = allCaps
        val ui = p.current()
        assertEquals("Fix the build", ui.title)
        assertEquals("Home PC", ui.serverName)
        assertEquals("app", ui.projectName)
        assertTrue(HeaderAction.ContinueWith in ui.menu)
        assertEquals(HeaderAction.Delete, ui.menu.last(), "destructive last")
        assertEquals(listOf(HeaderAction.Find, HeaderAction.Details), ui.menu.take(2), "Go to first")
    }

    @Test
    fun `B-CHAT-020 the menu offers only what this server supports`() {
        world.capabilities.value = ServerCapabilities(sessionShare = false, sessionFork = false, sessionArchive = false, sessionDelete = false)
        val menu = p.current().menu
        assertEquals(
            listOf(HeaderAction.Find, HeaderAction.Details, HeaderAction.Rename, HeaderAction.ContinueWith, HeaderAction.CopyTranscript),
            menu,
        )
        world.capabilities.value = allCaps
        port.facts.value = port.facts.value.copy(shareUrl = "https://x")
        assertTrue(HeaderAction.Unshare in p.current().menu)
        assertFalse(HeaderAction.Share in p.current().menu)
    }

    @Test
    fun `B-CHAT-020 Continue with lists this server's models current first and moves the conversation to the pick`() {
        world.capabilities.value = allCaps
        p.choose(HeaderAction.ContinueWith)
        assertEquals(listOf("loadModels"), world.calls)
        val sheet = assertIs<HeaderSheet.ContinueWith>(p.current().sheet)
        assertEquals(listOf("gpt-5" to true, "sonnet" to false), sheet.choices.map { it.modelId to it.current })
        p.continueWith(sheet.choices[1])
        assertEquals(listOf("sonnet"), world.picked)
        assertNull(p.current().sheet, "the sheet closes once the server acknowledged the model")
    }

    @Test
    fun `B-CHAT-020 a refused model change keeps the sheet open with the reason`() {
        p.choose(HeaderAction.ContinueWith)
        world.selectResult = Outcome.Failed(Failure.Rejected(status = 409))
        val sheet = assertIs<HeaderSheet.ContinueWith>(p.current().sheet)
        p.continueWith(sheet.choices[1])
        val after = assertIs<HeaderSheet.ContinueWith>(p.current().sheet)
        assertNotNull(after.failure)
        assertEquals("GPT-5", world.models.value.selected?.label, "the previous choice stays")
    }

    @Test
    fun `rename runs on the server and shows the new title at once`() {
        p.renameRequested()
        assertEquals("Fix the build", p.current().renaming)
        p.renameDone("  Ship it  ")
        assertEquals(listOf("rename Ship it"), port.calls.filter { it.startsWith("rename") })
        assertEquals("Ship it", p.current().title)
        assertNull(p.current().renaming)
        // The server's own later title wins.
        world.title.value = "Ship it (server)"
        assertEquals("Ship it (server)", p.current().title)
    }

    @Test
    fun `rename refuses blank and unchanged titles and a failure is one plain line`() {
        p.renameDone("   ")
        p.renameDone("Fix the build")
        p.renameDone(null)
        assertTrue(port.calls.none { it.startsWith("rename") })
        port.renameResult = Outcome.Failed(Failure.Network(NetworkWhy.Offline))
        p.renameDone("New")
        val failure = assertNotNull(p.current().failure)
        assertEquals(HeaderAction.Rename, failure.action)
        assertEquals("Fix the build", p.current().title, "a refused rename never shows")
        p.dismissFailure()
        assertNull(p.current().failure)
    }

    @Test
    fun `share copies the link and fork opens the new conversation`() {
        world.capabilities.value = allCaps
        p.choose(HeaderAction.Share)
        val clip = assertNotNull(p.current().clip)
        assertEquals(ClipKind.ShareLink, clip.kind)
        assertEquals("https://opncd.ai/s/abc", clip.text)
        p.clipDone(clip.id)
        assertNull(p.current().clip)
        p.choose(HeaderAction.Fork)
        assertEquals(listOf("openChat s9"), nav.calls)
    }

    @Test
    fun `delete asks first naming the conversation and leaves only after the server removed it`() {
        world.capabilities.value = allCaps
        p.choose(HeaderAction.Delete)
        assertEquals(HeaderConfirm.Delete("Fix the build"), p.current().confirm)
        assertTrue(port.calls.none { it == "delete" }, "nothing runs before the answer")
        port.deleteResult = Outcome.Failed(Failure.Unavailable(FeatureId("sessionDelete")))
        p.confirm()
        assertTrue(nav.calls.isEmpty(), "a refused delete stays in the conversation")
        assertEquals(HeaderAction.Delete, p.current().failure?.action)
        port.deleteResult = Outcome.Ok(Unit)
        p.choose(HeaderAction.Delete)
        p.confirm()
        assertEquals(listOf("back"), nav.calls)
    }

    @Test
    fun `archive asks first and cancel runs nothing`() {
        world.capabilities.value = allCaps
        p.choose(HeaderAction.Archive)
        assertIs<HeaderConfirm.Archive>(p.current().confirm)
        p.dismissConfirm()
        assertNull(p.current().confirm)
        assertTrue(port.calls.none { it == "archive" })
        p.choose(HeaderAction.Archive)
        p.confirm()
        assertEquals(listOf("back"), nav.calls)
    }

    @Test
    fun `B-CHAT-111 copy transcript keeps the person's prompts verbatim and masks everything else`() {
        world.transcript.value =
            stateOf(
                prompt("u1", "my key is sk-live-123456"),
                thought("u1", "a1", "r1", "thinking about sk-live-123456"),
                block("u1", "a1", "p1", 0, "printed sk-live-123456"),
            )
        p.copyTranscript(words) { it.replace("sk-live-123456", "••••") }
        val text = assertNotNull(p.current().clip).text
        assertTrue(text.startsWith("# Fix the build"))
        assertTrue("## User\n\nmy key is sk-live-123456" in text, text)
        assertTrue("## Assistant\n\nprinted ••••" in text, text)
        assertTrue("thinking about ••••" in text, text)
        assertFalse("printed sk-live" in text)
    }

    @Test
    fun `details shows server project model and context use`() {
        port.facts.value = port.facts.value.copy(contextTokens = 960, contextLimit = 1000, totalTokens = 1200, model = "gpt-5")
        p.choose(HeaderAction.Details)
        assertTrue("refresh" in port.calls)
        val d = assertIs<HeaderSheet.Details>(p.current().sheet).facts
        assertEquals("Home PC", d.serverName)
        assertEquals("app", d.projectName)
        assertEquals(96, d.contextPercent)
        assertEquals(ContextLevel.Full, d.contextLevel)
        assertEquals("gpt-5", d.model)
    }

    @Test
    fun `fork waits for the first prompt`() {
        assertFalse(p.current().hasPrompt)
        assertTrue(HeaderRules.needsPrompt(HeaderAction.Fork))
        world.transcript.value = stateOf(prompt("u1", "hi"))
        assertTrue(p.current().hasPrompt)
    }

    private fun freeCatalog() =
        listOf(
            io.github.eslamasabry.oc.core.model.CatalogModel(
                id = "big-pickle", providerId = "opencode", name = "Big Pickle", family = null, enabled = true, status = "active",
                contextLimit = 1000, outputLimit = 100, reasoning = false, attachments = false, tools = true,
                variants = kotlinx.collections.immutable.persistentListOf(),
                cost = io.github.eslamasabry.oc.core.model.ModelCost(0.0, 0.0, 0.0, 0.0), releasedAtMs = null,
            ),
        )

    @Test
    fun `B-CHAT-015 the free model is OpenCode's own free model with nothing else signed in`() {
        val models = freeCatalog()
        assertTrue(FreeModel.uses(listOf("opencode"), "opencode", "big-pickle", models))
        assertTrue(FreeModel.uses(emptyList(), "opencode", "opencode/big-pickle".removePrefix("opencode/"), models))
        assertFalse(FreeModel.uses(listOf("opencode", "anthropic"), "opencode", "big-pickle", models), "a signed-in provider")
        assertFalse(FreeModel.uses(emptyList(), "anthropic", "big-pickle", models))
        assertFalse(FreeModel.uses(emptyList(), "opencode", "unknown", models))
    }

    @Test
    fun `B-CHAT-015 dismissals are stored per server bounded in count newest kept`() {
        assertEquals("oc.freeModelNoteDismissed.p1", FreeModel.key(io.github.eslamasabry.oc.core.common.ids.ProfileId("p1")))
        val many = (1..FreeModel.MAX_DISMISSALS).map { "s$it" }
        val next = FreeModel.dismissed(many, "new")
        assertEquals(FreeModel.MAX_DISMISSALS, next.size)
        assertEquals("new", next.last())
        assertFalse("s1" in next, "the oldest falls out")
        assertEquals(listOf("b", "a"), FreeModel.dismissed(listOf("a", "b"), "a"), "a repeat moves to the newest end")
    }

    @Test
    fun `B-CHAT-015 the note shows once per conversation and a dismissal is kept for this server`() {
        val store =
            object : FreeNoteStore {
                val kept = mutableListOf<String>()

                override fun dismissed(profile: io.github.eslamasabry.oc.core.common.ids.ProfileId, session: String) = session in kept

                override suspend fun dismiss(profile: io.github.eslamasabry.oc.core.common.ids.ProfileId, session: String): Outcome<Unit> {
                    kept += session
                    return Outcome.Ok(Unit)
                }
            }
        val freePort =
            object : ChatHeaderPort by port {
                override suspend fun freeModelFacts() = Outcome.Ok(FreeModelFacts(listOf("opencode"), freeCatalog()))
            }
        port.facts.value = port.facts.value.copy(providerId = "opencode", model = "big-pickle")
        val presenter = ChatHeaderPresenter(world, freePort, nav, Immediate, store)
        presenter.states(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined), kotlinx.coroutines.flow.SharingStarted.Eagerly)
        assertTrue(presenter.current().freeModelNote)
        presenter.dismissFreeNote()
        assertFalse(presenter.current().freeModelNote)
        assertEquals(listOf("s1"), store.kept)
        val again = ChatHeaderPresenter(world, freePort, nav, Immediate, store)
        again.states(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined), kotlinx.coroutines.flow.SharingStarted.Eagerly)
        assertFalse(again.current().freeModelNote, "a dismissed conversation never shows it again")
    }
}
