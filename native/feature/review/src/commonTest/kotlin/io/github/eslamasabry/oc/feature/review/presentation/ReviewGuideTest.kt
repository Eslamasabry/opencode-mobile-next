package io.github.eslamasabry.oc.feature.review.presentation

import io.github.eslamasabry.oc.core.common.BusyKind
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.domain.events.RunOutcome
import io.github.eslamasabry.oc.core.model.ServerPage
import io.github.eslamasabry.oc.core.model.Session
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
import io.github.eslamasabry.oc.engine.review.ConversationReview
import io.github.eslamasabry.oc.engine.review.ReviewGateways
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val G = SessionId("ses_g")

private fun said(id: String, at: Long, role: Role, text: String) =
    MessageWithParts(MessageInfo(id, G.raw, role, time = MsgTime(at, at)), persistentListOf(Part("p$id", id, G.raw, PartType.Text, "text", text = text)))

private fun edit(id: String, at: Long, path: String, after: String = "new\n") =
    MessageWithParts(
        MessageInfo(id, G.raw, Role.Assistant, time = MsgTime(at, at)),
        persistentListOf(
            Part(
                "t$id", id, G.raw, PartType.Tool, "tool", toolName = "edit",
                tool =
                    ToolState(
                        ToolStatus.Completed,
                        input = JsonObject(mapOf("filePath" to JsonPrimitive(path))),
                        metadata =
                            JsonObject(
                                mapOf(
                                    "filediff" to
                                        JsonObject(
                                            mapOf(
                                                "before" to JsonPrimitive("old\n"),
                                                "after" to JsonPrimitive(after),
                                                "additions" to JsonPrimitive(1),
                                                "deletions" to JsonPrimitive(1),
                                            ),
                                        ),
                                ),
                            ),
                        completedAtMs = 1,
                    ),
            ),
        ),
    )

private class GuideFake(val base: FakeServerGateway = FakeServerGateway(listOf(Session(G)))) : ServerGateway by base {
    var history: List<MessageWithParts> = emptyList()

    override suspend fun session(id: SessionId) = Session(id).ok()

    override suspend fun messagePage(id: SessionId, cursor: String?, limit: Int): Outcome<ServerPage<MessageWithParts>> =
        ServerPage(history.toImmutableList(), null).ok()
}

private class Asked(var reply: (String) -> Outcome<String>) : ReviewGuideAsk {
    val prompts = mutableListOf<String>()

    override suspend fun ask(prompt: String, marker: String): Outcome<String> {
        prompts += prompt
        assertTrue(prompt.startsWith(marker))
        return reply(prompt)
    }
}

private const val GUIDE =
    """Here you go:
{"sections":[
 {"title":"Retry the upload","why":"Uploads failed on flaky networks, so they now retry three times.","files":["src/upload.kt"],"glue":false},
 {"title":"Tests","why":"Cover the retry.","files":["test/upload_test.kt"],"glue":true},
 {"title":"Made up","why":"x","files":["not/changed.kt"]}
]}"""

class ReviewGuideTest {
    private val paths = listOf("src/upload.kt", "src/imports.kt", "test/upload_test.kt")

    @Test
    fun `B-155 the guide prompt is fixed and bounded and names paths only`() {
        val many = (1..80).map { "f$it.kt" }
        val prompt = ReviewGuidePrompt.prompt("[review-guide k]", many)
        assertTrue(prompt.startsWith("[review-guide k] Do not change any files"))
        assertTrue("- f50.kt" in prompt)
        assertFalse("- f51.kt" in prompt, "at most ${ReviewGuidePrompt.MAX_PATHS} paths")
        assertTrue("\"glue\"" in prompt)
    }

    @Test
    fun `B-155 the reply becomes core sections first, glue and unnamed files under Also changed`() {
        val guide = ReviewGuidePrompt.parse(GUIDE, paths)!!
        assertEquals(listOf("Retry the upload"), guide.sections.map { it.title })
        assertEquals(listOf("src/upload.kt"), guide.sections.single().files)
        assertEquals("Uploads failed on flaky networks, so they now retry three times.", guide.sections.single().why)
        assertEquals(listOf("test/upload_test.kt", "src/imports.kt"), guide.alsoChanged)
    }

    @Test
    fun `B-155 an unusable reply gives no guide`() {
        assertNull(ReviewGuidePrompt.parse("I can't do that", paths))
        assertNull(ReviewGuidePrompt.parse("{\"sections\": \"no\"}", paths))
        assertNull(ReviewGuidePrompt.parse("{not json}", paths))
        assertNull(ReviewGuidePrompt.parse("""{"sections":[{"title":"T","files":["elsewhere.kt"]}]}""", paths))
        val long = ReviewGuidePrompt.parse("""{"sections":[{"title":"${"t".repeat(200)}","why":"a\nb","files":["src/upload.kt"]}]}""", paths)!!
        assertEquals(ReviewGuidePrompt.MAX_TITLE, long.sections.single().title.length)
        assertEquals("a b", long.sections.single().why)
    }

    private val fake = GuideFake()
    private val gateways = MutableStateFlow<ReviewGateways?>(ReviewGateways(fake, FakeOperationsGateway()))

    private fun TestScope.review() = ConversationReview(G, gateways, backgroundScope)

    private fun changed() {
        fake.history = listOf(said("u1", 1, Role.User, "fix uploads"), edit("a1", 2, "src/upload.kt"), edit("a2", 3, "src/imports.kt"), edit("a3", 4, "test/upload_test.kt"))
    }

    @Test
    fun `B-155 Guided asks once per change set and shows the cached guide again`() = runTest(UnconfinedTestDispatcher()) {
        changed()
        val ask = Asked { GUIDE.ok() }
        val page = ChangesPresenter(review(), null, NoNav, backgroundScope, guideAsk = ask)
        assertTrue(page.state.value.canGuide)
        assertEquals(GuideUi.Off, page.state.value.guide)
        page.guided(true)
        val ready = assertIs<GuideUi.Ready>(page.state.value.guide)
        assertEquals(listOf("src/upload.kt"), ready.guide.sections.single().files)
        assertFalse("old\n" in ask.prompts.single() || "new\n" in ask.prompts.single(), "no file contents in the question")
        page.guided(false)
        assertEquals(GuideUi.Off, page.state.value.guide)
        page.guided(true)
        assertIs<GuideUi.Ready>(page.state.value.guide)
        assertEquals(1, ask.prompts.size, "cached for the same change set")
    }

    @Test
    fun `B-155 a failed guide falls back to the plain list`() = runTest(UnconfinedTestDispatcher()) {
        changed()
        val page = ChangesPresenter(review(), null, NoNav, backgroundScope, guideAsk = Asked { Outcome.Failed(Failure.Server(500)) })
        page.guided(true)
        assertEquals(GuideUi.Fallback(Failure.Server(500)), page.state.value.guide)
        assertEquals(3, page.state.value.files.size)
        val unreadable = ChangesPresenter(review(), null, NoNav, backgroundScope, guideAsk = Asked { "sorry".ok() })
        unreadable.guided(true)
        assertIs<Failure.UnreadableReply>(assertIs<GuideUi.Fallback>(unreadable.state.value.guide).failure)
    }

    @Test
    fun `B-155 no guide where the agent cannot be asked`() = runTest(UnconfinedTestDispatcher()) {
        changed()
        val page = ChangesPresenter(review(), null, NoNav, backgroundScope)
        assertFalse(page.state.value.canGuide)
        page.guided(true)
        assertEquals(GuideUi.Off, page.state.value.guide)
    }

    @Test
    fun `B-155 the guide is made again when the files change`() = runTest(UnconfinedTestDispatcher()) {
        changed()
        val ask = Asked { GUIDE.ok() }
        val page = ChangesPresenter(review(), null, NoNav, backgroundScope, guideAsk = ask)
        page.guided(true)
        fake.history += edit("a4", 5, "src/upload.kt", after = "newer\n")
        fake.base.events.emit(GatewayEvent.RunEnded(G, RunOutcome.Succeeded))
        runCurrent()
        assertEquals(2, ask.prompts.size)
        assertIs<GuideUi.Ready>(page.state.value.guide)
    }

    @Test
    fun `B-155 the guide question never joins a running turn`() = runTest(UnconfinedTestDispatcher()) {
        changed()
        val r = review()
        fake.base.events.emit(GatewayEvent.RunStarted(G))
        runCurrent()
        val sent = mutableListOf<String>()
        val result = AgentGuideAsk(r, { text -> sent += text; Unit.ok() }).ask("[m] q", "[m]")
        assertEquals(Failure.Busy(BusyKind.SessionBusy), (result as Outcome.Failed).failure)
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `B-155 the agent's answer is read after its turn ends`() = runTest(UnconfinedTestDispatcher()) {
        changed()
        val r = review()
        val answer =
            backgroundScope.async {
                AgentGuideAsk(r, { text ->
                    fake.history += said("u9", 9, Role.User, text)
                    Unit.ok()
                }).ask("[review-guide k] group it", "[review-guide k]")
            }
        runCurrent()
        assertFalse(answer.isCompleted)
        fake.history += said("a9", 10, Role.Assistant, GUIDE)
        fake.base.events.emit(GatewayEvent.RunEnded(G, RunOutcome.Succeeded))
        runCurrent()
        assertEquals(GUIDE, (answer.await() as Outcome.Ok).value)
    }
}
