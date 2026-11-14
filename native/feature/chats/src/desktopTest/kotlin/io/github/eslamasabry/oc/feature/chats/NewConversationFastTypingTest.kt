package io.github.eslamasabry.oc.feature.chats

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.ImportableConversation
import io.github.eslamasabry.oc.core.domain.ImportableConversations
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationAttempt
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationHost
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationServer
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationSetup
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationViewModel
import io.github.eslamasabry.oc.feature.chats.ui.NewConversationScreen
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import java.util.Collections
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext

/**
 * Audit 1 (2026-11-14), the New conversation prompt: 60 letters typed fast, three at a time, while the page gets
 * ready, the server's projects arrive in the middle of a word, an older stored draft exists for the project, and
 * every save answers after 50-300 ms. The field must hold exactly what was typed, in order, with the cursor at the
 * end, and the project's draft must end as the same words (the older stored draft never comes back over them).
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class NewConversationFastTypingTest {
    private val phone = ProfileId("phone")
    private val dir = "/root/projects/kept"
    private val typed = "This is a draft that wraps; create c10.txt, count 1 to 60 ok"

    private class Nav : AppNavigator {
        override val top: StateFlow<AppRoute?> = MutableStateFlow(null)

        override fun open(route: AppRoute) {}

        override fun openChat(ref: ConversationRef, focusComposer: Boolean) {}

        override fun land() {}

        override fun replaceRoot(root: AppRoute) {}

        override fun back() {}
    }

    /** The server answers when the test says; the draft store answers every read and save after 50-300 ms. */
    private inner class SlowHost : NewConversationHost {
        val drafts: MutableMap<String?, String> = Collections.synchronizedMap(mutableMapOf())
        val reading = CompletableDeferred<Outcome<NewConversationSetup>>()
        private val random = Random(14)
        override val servers: StateFlow<List<NewConversationServer>> =
            MutableStateFlow(listOf(NewConversationServer(phone, "This phone", connected = true)))

        private suspend fun slow() = withContext(Dispatchers.Default) { delay(random.nextLong(50, 301)) }

        override suspend fun setup(profile: ProfileId): Outcome<NewConversationSetup> = reading.await()

        override suspend fun folderExists(profile: ProfileId, directory: String): Boolean? = true

        override suspend fun draft(profile: ProfileId, directory: String?): Outcome<String> {
            slow()
            return Outcome.Ok(drafts[directory].orEmpty())
        }

        override suspend fun saveDraft(profile: ProfileId, directory: String?, text: String): Outcome<Unit> {
            slow()
            if (text.isEmpty()) drafts.remove(directory) else drafts[directory] = text
            return Outcome.Ok(Unit)
        }

        override suspend fun start(
            profile: ProfileId,
            directory: String,
            text: String,
            model: ModelRef?,
            agent: String?,
            separateCopy: Boolean,
            previous: NewConversationAttempt?,
            onStarted: (ConversationRef) -> Unit,
        ): NewConversationAttempt = NewConversationAttempt(directory, failure = Failure.Unavailable(FeatureId("test")))

        override suspend fun imports(profile: ProfileId, directory: String): Outcome<ImportableConversations> =
            Outcome.Failed(Failure.Unavailable(FeatureId("test")))

        override suspend fun import(
            profile: ProfileId,
            directory: String,
            conversation: ImportableConversation,
        ): Outcome<ConversationRef> = Outcome.Failed(Failure.Unavailable(FeatureId("test")))
    }

    @BeforeTest fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun reset() = Dispatchers.resetMain()

    /** Waits in real time (the store answers on real threads), letting the page settle between looks. */
    private fun ComposeUiTest.awaitReal(what: String, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + 10_000
        while (!condition()) {
            check(System.currentTimeMillis() < end) { "timed out waiting until $what" }
            Thread.sleep(20)
            waitForIdle()
        }
    }

    @Test
    fun `A1-TYPE-003 sixty letters typed fast while the page gets ready reach the New conversation field in order`() =
        runComposeUiTest {
            val host = SlowHost().apply { drafts[dir] = "older words" }
            val vm = NewConversationViewModel(ChatsRoute.New(phone), host, Nav(), Clock.System, ListenedReports())
            setContent {
                val state by vm.state.collectAsState()
                Themed { NewConversationScreen(state, vm) }
            }
            awaitReal("the field is shown") { onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size == 1 }
            onNode(hasSetTextAction()).performTouchInput { click() }
            typed.chunked(3).forEachIndexed { chunk, letters ->
                val node = onNode(hasSetTextAction()).fetchSemanticsNode()
                val insert = node.config[SemanticsActions.InsertTextAtCursor].action!!
                runOnUiThread {
                    letters.forEach { insert(AnnotatedString(it.toString())) }
                    // The server's projects and models arrive in the middle of a word.
                    if (chunk == 4) host.reading.complete(Outcome.Ok(NewConversationSetup(dir, emptyList(), emptyList(), emptyList())))
                }
                if (chunk % 5 == 4) Thread.sleep(400)
            }
            awaitReal("the project's draft is the typed words") { host.drafts[dir] == typed }
            val node = onNode(hasSetTextAction()).fetchSemanticsNode()
            assertEquals(typed, node.config[SemanticsProperties.EditableText].text, "every letter, in order")
            assertEquals(TextRange(typed.length), node.config[SemanticsProperties.TextSelectionRange], "cursor at the end")
            assertEquals(typed, vm.state.value.text)
            onNode(hasSetTextAction()).assertIsFocused()
        }
}
