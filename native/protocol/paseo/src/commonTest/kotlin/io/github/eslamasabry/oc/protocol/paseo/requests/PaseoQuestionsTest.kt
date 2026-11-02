package io.github.eslamasabry.oc.protocol.paseo.requests

import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.protocol.paseo.PaseoException
import io.github.eslamasabry.oc.protocol.paseo.PaseoFailureKind
import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import io.github.eslamasabry.oc.protocol.paseo.obj
import io.github.eslamasabry.oc.protocol.paseo.str
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Ported from test/paseo_native_questions_test.dart (B-CHAT-072), fixture native_questions_0_9_2.json inlined. */
class PaseoQuestionsTest {
    private val host = FakeRequestHost()
    private val book = PaseoRequestBook(host)

    private fun asked() = host.questionSignals.filterIsInstance<PaseoQuestionSignal.Asked>()

    private fun settled() = host.questionSignals.filterIsInstance<PaseoQuestionSignal.Settled>()

    @Test
    fun `B-CHAT-072 Claude native questions become typed questions, never permissions`() {
        book.onPermissionRequest("a1", questionFixture("claude"))
        assertTrue(book.pendingPermissions().isEmpty())
        val question = book.pendingQuestions().single()
        assertEquals("permission-question", question.id.raw)
        assertEquals("a1", question.sessionId.raw)
        assertEquals("Stack", question.prompts.first().title)
        assertEquals("Which stack should I use?", question.prompts.first().question)
        assertEquals("Shared language", question.prompts.first().choices.first().description)
        assertTrue(question.prompts.first().custom)
        assertTrue(question.prompts.last().multiple)
        assertEquals(1, asked().size)
        assertTrue(host.events.none { it is GatewayEvent.PermissionAsked })
    }

    @Test
    fun `B-CHAT-072 Claude answers use exact question keys and string multi and custom values`() = runTest {
        book.onPermissionRequest("a1", questionFixture("claude"))
        book.answerQuestion("a1", "permission-question", listOf(listOf("Rust"), listOf("Search", "History", "Offline")))
        assertEquals(
            jsonOf(
                "behavior" to "allow",
                "updatedInput" to mapOf(
                    "answers" to mapOf("Which stack should I use?" to "Rust", "Which features do you need?" to "Search, History, Offline"),
                ),
            ),
            host.lastResponse(),
        )
        assertTrue(book.pendingQuestions().isEmpty())
        assertFalse(assertIs<PaseoQuestionSignal.Settled>(host.questionSignals.last()).rejected)
        // An answered question does not come back from a stale push.
        book.onPermissionRequest("a1", questionFixture("claude"))
        assertTrue(book.pendingQuestions().isEmpty())
    }

    @Test
    fun `B-CHAT-072 Pi and Oh My Pi use header keys and keep an optional comment with an empty answer`() = runTest {
        for (provider in listOf("pi", "omp")) {
            val host = FakeRequestHost().apply { providers["a1"] = provider }
            val book = PaseoRequestBook(host)
            book.onPermissionRequest("a1", questionFixture("pi").with("provider" to provider, "name" to "$provider ask_user"))
            val question = book.pendingQuestions().single()
            assertEquals(2, question.prompts.size)
            assertTrue(question.prompts.last().optional, provider)
            assertTrue(question.prompts.last().custom, provider)
            book.answerQuestion("a1", "ui-question", listOf(listOf("Rust"), emptyList()))
            assertEquals(
                jsonOf("behavior" to "allow", "updatedInput" to mapOf("answers" to mapOf("Response" to "Rust", "Comment" to ""))),
                host.lastResponse(),
                provider,
            )
        }
    }

    @Test
    fun `B-CHAT-072 a plan shows the full plan and approval never restores bypass mode`() = runTest {
        book.onPermissionRequest("a1", questionFixture("plan"))
        val prompt = book.pendingQuestions().single().prompts.single()
        assertEquals(questionFixture("plan").obj("input")!!.str("plan"), prompt.question)
        assertEquals(listOf("Approve", "Keep planning"), prompt.choices.map { it.label })
        assertFalse(prompt.custom)
        assertTrue(prompt.markdown)
        assertTrue(asked().single().plan)
        book.answerQuestion("a1", "permission-plan", listOf(listOf("Approve")))
        assertEquals(jsonOf("behavior" to "allow", "selectedActionId" to "implement"), host.lastResponse())
    }

    @Test
    fun `B-CHAT-072 keep planning denies without interrupt or persistent permission`() = runTest {
        book.onPermissionRequest("a1", questionFixture("plan"))
        book.answerQuestion("a1", "permission-plan", listOf(listOf("Keep planning")))
        assertEquals(jsonOf("behavior" to "deny", "selectedActionId" to "reject"), host.lastResponse())
        assertTrue(assertIs<PaseoQuestionSignal.Settled>(host.questionSignals.last()).rejected)
    }

    @Test
    fun `B-CHAT-072 a plan without the reject and implement actions is not a question`() {
        val actions = JsonArray(listOf(jsonOf("id" to "implement_resume", "behavior" to "allow")))
        book.onPermissionRequest("a1", questionFixture("plan").with("actions" to actions))
        assertTrue(book.pendingQuestions().isEmpty())
        assertTrue(book.pendingPermissions().isEmpty())
    }

    @Test
    fun `B-CHAT-072 question reject sends deny and legacy permission replies cannot approve it`() = runTest {
        book.onPermissionRequest("a1", questionFixture("claude"))
        assertFailsWith<PaseoException> { book.replyPermission("permission-question", PaseoPermissionAnswer.Once) }
        book.rejectQuestion("a1", "permission-question")
        assertEquals(jsonOf("behavior" to "deny"), host.lastResponse())
        assertTrue(assertIs<PaseoQuestionSignal.Settled>(host.questionSignals.last()).rejected)
    }

    @Test
    fun `B-CHAT-072 wrong session, invalid values and disconnected answers never send`() = runTest {
        book.onPermissionRequest("a1", questionFixture("claude"))
        assertEquals(
            PaseoFailureKind.StaleRequest,
            assertFailsWith<PaseoException> { book.answerQuestion("other", "permission-question", listOf(listOf("Dart"), listOf("Search"))) }.kind,
        )
        assertEquals(
            PaseoFailureKind.InvalidResponse,
            assertFailsWith<PaseoException> {
                book.answerQuestion("a1", "permission-question", listOf(listOf("Dart", "Kotlin"), listOf("Search")))
            }.kind,
        )
        assertFailsWith<PaseoException> { book.answerQuestion("a1", "permission-question", listOf(listOf("Dart", "Dart"), listOf("Search"))) }
        assertFailsWith<PaseoException> { book.answerQuestion("a1", "permission-question", listOf(listOf("Dart"))) }
        host.connected = false
        assertFailsWith<PaseoException> { book.answerQuestion("a1", "permission-question", listOf(listOf("Dart"), listOf("Search"))) }
        assertTrue(host.responses().isEmpty())
    }

    @Test
    fun `B-CHAT-072 snapshots recover pending questions and retire resolved ones`() {
        book.syncSnapshot("a1", JsonArray(listOf(questionFixture("claude"))))
        assertEquals(1, book.pendingQuestions().size)
        book.syncSnapshot("a1", JsonArray(emptyList()))
        assertTrue(book.pendingQuestions().isEmpty())
        assertEquals(1, settled().size)
        assertFalse(settled().single().rejected)
    }

    @Test
    fun `B-CHAT-072 a malformed question is not turned into a generic approval`() {
        book.onPermissionRequest("a1", questionFixture("claude").with("input" to mapOf("questions" to listOf(mapOf("header" to "Bad")))))
        assertTrue(book.pendingQuestions().isEmpty())
        assertTrue(book.pendingPermissions().isEmpty())
        book.onPermissionRequest("a1", writePermission("write"))
        assertEquals("write", book.pendingPermissions().single().id.raw)
    }

    @Test
    fun `B-CHAT-072 the feed shows needs-you until the question is replied`() = runTest {
        val start = book.revision.value
        book.onPermissionRequest("a1", questionFixture("claude"))
        assertTrue(book.hasQuestion("a1"))
        assertTrue(book.revision.value > start)
        book.rejectQuestion("a1", "permission-question")
        assertFalse(book.hasQuestion("a1"))
    }

    @Test
    fun `B-CHAT-072 unknown or mismatched provider questions never become approvals`() {
        book.onPermissionRequest("a1", questionFixture("claude").with("provider" to "codex"))
        host.providers["a3"] = "gemini"
        book.onPermissionRequest("a3", questionFixture("claude").with("provider" to "gemini", "id" to "q3"))
        assertTrue(book.pendingQuestions().isEmpty())
        assertTrue(book.pendingPermissions().isEmpty())
        assertTrue(host.responses().isEmpty())
    }

    @Test
    fun `B-CHAT-072 Claude duplicate headers keep distinct full-question answer keys`() = runTest {
        book.onPermissionRequest("a1", questionFixture("claude").edit("input", "questions", 1, "header", value = "Stack"))
        book.answerQuestion("a1", "permission-question", listOf(listOf("Dart"), listOf("History")))
        assertEquals(
            jsonOf("Which stack should I use?" to "Dart", "Which features do you need?" to "History"),
            host.lastResponse().obj("updatedInput")!!.obj("answers"),
        )
    }

    @Test
    fun `B-CHAT-072 options-free native input permits custom text and an optional empty string`() = runTest {
        host.providers["a1"] = "pi"
        val raw = questionFixture("pi").with(
            "input" to mapOf("questions" to listOf(mapOf("header" to "Response", "question" to "Optional note", "options" to emptyList<Any>(), "allowEmpty" to true))),
        )
        book.onPermissionRequest("a1", raw)
        val prompt = book.pendingQuestions().single().prompts.single()
        assertTrue(prompt.custom)
        assertTrue(prompt.optional)
        book.answerQuestion("a1", "ui-question", listOf(emptyList()))
        assertEquals(jsonOf("Response" to ""), host.lastResponse().obj("updatedInput")!!.obj("answers"))
    }

    @Test
    fun `B-CHAT-072 a required answer cannot be empty and closed choices cannot be invented`() = runTest {
        book.onPermissionRequest("a1", questionFixture("claude").edit("input", "questions", 0, "allowOther", value = false))
        assertFailsWith<PaseoException> { book.answerQuestion("a1", "permission-question", listOf(emptyList(), listOf("Search"))) }
        assertFailsWith<PaseoException> { book.answerQuestion("a1", "permission-question", listOf(listOf("Unknown"), listOf("Search"))) }
        assertFailsWith<PaseoException> { book.answerQuestion("a1", "permission-question", listOf(listOf("  "), listOf("Search"))) }
        assertTrue(host.responses().isEmpty())
    }

    @Test
    fun `B-CHAT-072 resolved and scope-changed question replies never send`() = runTest {
        book.onPermissionRequest("a1", questionFixture("claude"))
        book.onPermissionResolved("permission-question")
        assertTrue(book.pendingQuestions().isEmpty())
        assertFailsWith<PaseoException> { book.rejectQuestion("a1", "permission-question") }
        book.onPermissionRequest("a1", questionFixture("claude").with("id" to "second"))
        book.clear()
        assertTrue(book.pendingQuestions().isEmpty())
        assertFailsWith<PaseoException> { book.rejectQuestion("a1", "second") }
        assertTrue(host.responses().isEmpty())
    }

    @Test
    fun `B-CHAT-072 the isOther alias permits custom text and malformed flags never approve`() = runTest {
        val raw = questionFixture("claude").edit("input", "questions", 0, "allowOther", value = null)
            .edit("input", "questions", 0, "isOther", value = true)
        book.onPermissionRequest("a1", raw)
        assertTrue(book.pendingQuestions().single().prompts.first().custom)
        book.answerQuestion("a1", "permission-question", listOf(listOf("Rust"), listOf("Search")))
        val malformed = questionFixture("claude").with("id" to "malformed").edit("input", "questions", 0, "isOther", value = "true")
        book.onPermissionRequest("a1", malformed)
        assertTrue(book.pendingQuestions().isEmpty())
    }

    @Test
    fun `B-CHAT-072 a changed revision replaces the visible prompt and rejects the old closed choice`() = runTest {
        val raw = questionFixture("claude").edit("input", "questions", 0, "allowOther", value = false)
        book.onPermissionRequest("a1", raw)
        val changed = raw.edit("input", "questions", 0, "question", value = "Which language instead?")
            .edit("input", "questions", 0, "options", value = listOf(mapOf("label" to "Rust")))
        book.onPermissionRequest("a1", changed)
        assertEquals("Which language instead?", book.pendingQuestions().single().prompts.first().question)
        assertFailsWith<PaseoException> { book.answerQuestion("a1", "permission-question", listOf(listOf("Dart"), listOf("Search"))) }
        assertTrue(host.responses().isEmpty())
        assertEquals(2, asked().size)
        // An identical push is not announced again.
        book.onPermissionRequest("a1", changed)
        assertEquals(2, asked().size)
    }

    @Test
    fun `B-CHAT-072 a malformed same-session replacement invalidates the previous question`() = runTest {
        book.onPermissionRequest("a1", questionFixture("claude"))
        val snapshot = assertNotNull(book.questionSnapshot("a1"))
        book.onPermissionRequest("a1", questionFixture("claude").edit("input", "questions", 0, "multiSelect", value = "yes"))
        assertTrue(book.pendingQuestions().isEmpty())
        assertFalse(book.isQuestionCurrent("a1", "permission-question", snapshot.revision))
        assertFailsWith<PaseoException> { book.answerQuestion("a1", "permission-question", listOf(listOf("Dart"), listOf("Search"))) }
        assertTrue(host.responses().isEmpty())
    }

    @Test
    fun `B-CHAT-072 a colliding request in another session cannot invalidate a question`() = runTest {
        book.onPermissionRequest("a1", questionFixture("claude"))
        host.providers["a2"] = "claude"
        book.onPermissionRequest("a2", questionFixture("claude").with("input" to mapOf("questions" to emptyList<Any>())))
        val question = book.pendingQuestions().single()
        assertEquals("a1", question.sessionId.raw)
        book.answerQuestion("a1", "permission-question", listOf(listOf("Dart"), listOf("Search")))
        assertEquals("allow", host.lastResponse().str("behavior"))
    }

    @Test
    fun `B-CHAT-072 question snapshots carry an exact revision and disconnect retires them`() {
        book.onPermissionRequest("a1", questionFixture("claude"))
        val snapshot = assertNotNull(book.questionSnapshot("a1"))
        assertTrue(book.isQuestionCurrent("a1", "permission-question", snapshot.revision))
        assertFalse(book.isQuestionCurrent("a2", "permission-question", snapshot.revision))
        book.disconnect()
        assertFalse(book.isQuestionCurrent("a1", "permission-question", snapshot.revision))
        assertTrue(book.pendingQuestions().isEmpty())
        assertTrue(host.responses().isEmpty())
    }
}
