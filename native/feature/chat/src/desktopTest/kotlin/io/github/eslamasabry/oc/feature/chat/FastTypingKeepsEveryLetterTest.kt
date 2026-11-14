package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.engine.composer.drafts.ConversationDrafts
import io.github.eslamasabry.oc.engine.composer.drafts.Drafts
import io.github.eslamasabry.oc.engine.composer.store.ComposerPrefs
import io.github.eslamasabry.oc.engine.composer.store.ComposerProfiles
import io.github.eslamasabry.oc.engine.composer.store.MemoryComposerPrefs
import io.github.eslamasabry.oc.engine.composer.store.PrefsEdit
import io.github.eslamasabry.oc.engine.composer.vault.AttachmentVault
import io.github.eslamasabry.oc.engine.composer.vault.MemoryPayloadStore
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.feature.chat.composer.BasicComposer
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerActions
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerUi
import io.github.eslamasabry.oc.feature.chat.world.ChatWorld
import io.github.eslamasabry.oc.feature.chat.world.GraphChatWorlds
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay

/**
 * Audit 1 (2026-11-14): text typed fast into the message box lost the last letters of the word in progress, or put
 * two letters in the wrong order ("create" became "rceate"). The suspect was a stale copy of the words written back
 * into the box while the person types: the draft's save and its echo, a state round trip, the phrase chips.
 *
 * The box here is wired to the real draft owner (the conversation's world over [ConversationDrafts]) on a store that
 * answers each save after 50-300 ms, with the echoes arriving on another thread, out of step with the keys. While 60
 * letters go in three at a time, the page keeps changing under the box: a reply starts and stops (Stop comes and
 * goes in the box), phrase chips come and go above it, a streaming reply grows above it, and the saves land and echo
 * back. The box must hold exactly what was typed, in order, with the cursor at the end, and the saved draft must end
 * as the same words. The box keeps focus throughout: a box rebuilt by a change around it loses focus, and the next
 * hardware keys then go to the page, not to the words.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class FastTypingKeepsEveryLetterTest {
    private val typed = "This is a draft that wraps; create c10.txt, count 1 to 60 ok"

    /** A real store that takes its time; every save answers after 50-300 ms. */
    private class SlowPrefs(private val inner: MemoryComposerPrefs = MemoryComposerPrefs()) : ComposerPrefs by inner {
        private val random = Random(14)

        override suspend fun commit(edit: PrefsEdit): Boolean {
            delay(random.nextLong(50, 301))
            return inner.commit(edit)
        }
    }

    /** Real threads: saves run on their own lane, and the draft's echo reaches the page from another thread. */
    private val lanes =
        object : AppDispatchers {
            override val main: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1)
            override val default: CoroutineDispatcher = Dispatchers.Default
            override val io: CoroutineDispatcher = Dispatchers.IO

            override fun serial(name: String): CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1)
        }

    private val profiles =
        object : ComposerProfiles {
            override fun exists(profileId: String): Boolean = profileId == "p1"

            override fun count(): Int = 1
        }

    @Composable
    private fun Theme(content: @Composable () -> Unit) {
        KitTheme(
            platform = DesktopKitPlatform(),
            appearance = KitAppearance(motion = MotionLevel.Off),
            stillFrame = true,
            localeTag = "en",
            content = content,
        )
    }

    private fun typeIntoComposer(wholeBox: Boolean) = runComposeUiTest {
        val job = SupervisorJob()
        val app = AppCoroutineScope(job + Dispatchers.Default)
        try {
            val drafts = Drafts(SlowPrefs(), AttachmentVault(MemoryPayloadStore()), profiles, TestClock())
            val owner = ConversationDrafts(drafts, app, lanes)
            val registry =
                ConnectionRegistry(emptySet(), { _, _, _ -> error("no server is opened by a typing test") }, app, lanes)
            val world: ChatWorld =
                GraphChatWorlds(registry, setOf(owner)).open(ConversationRef(ProfileId("p1"), SessionId("same")))
            val busy = mutableStateOf(false)
            val beat = mutableIntStateOf(0)
            val stream = mutableStateOf("")
            val chrome = ComposerChrome({ busy.value }, { false }, {}, { null }, {}, { null }, {}, {}, { false })
            val actions =
                object : BasicComposerActions {
                    override fun draftChanged(text: String) = world.draftChanged(text)

                    override suspend fun send(text: String): Boolean = true

                    override suspend fun sendAgain(text: String, expectedRevision: Long): Boolean = true
                }
            setContent {
                val draft by world.draft.collectAsState()
                val ui =
                    BasicComposerUi(
                        available = true,
                        sending = false,
                        steers = busy.value,
                        draft = draft,
                        targetKey = "p1/same",
                        phrases = if (beat.intValue % 3 == 0) listOf("Go on") else emptyList(),
                    )
                Theme {
                    Column {
                        KitText("Reply so far: ${stream.value}")
                        BasicComposer(ui, actions, chrome)
                    }
                }
            }
            // The box takes typing once the stored draft (none) has been read.
            awaitReal("the draft is read") { onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size == 1 }
            onNode(hasSetTextAction()).performTouchInput { click() }
            typeFast({ onNode(hasSetTextAction()) }, wholeBox) { chunk ->
                busy.value = chunk % 2 == 0
                beat.intValue = chunk
                stream.value += "word "
            }
            awaitReal("the words are saved") { world.draft.value.saved == typed }
            waitForIdle()
            assertBoxHoldsTyped(onNode(hasSetTextAction()))
            // A box rebuilt mid-typing loses focus, and the next hardware keys go to the page instead of the words.
            onNode(hasSetTextAction()).assertIsFocused()
        } finally {
            job.cancel()
        }
    }

    /**
     * Three letters per step without waiting for the page in between, then the page's own changes, then the next
     * three. Every few steps the keys pause long enough for a save to go out and its echo to come back mid-word.
     * [wholeBox]: the keyboard rewrites the whole box on every key (as a keyboard rewrites the word it composes);
     * otherwise each key commits one letter at the cursor.
     */
    private fun ComposeUiTest.typeFast(
        field: () -> SemanticsNodeInteraction,
        wholeBox: Boolean,
        between: (chunk: Int) -> Unit,
    ) {
        var sofar = ""
        typed.chunked(3).forEachIndexed { chunk, letters ->
            val node = field().fetchSemanticsNode()
            val insert = node.config[SemanticsActions.InsertTextAtCursor].action!!
            val replace = node.config[SemanticsActions.SetText].action!!
            runOnUiThread {
                letters.forEach { letter ->
                    sofar += letter
                    if (wholeBox) replace(AnnotatedString(sofar)) else insert(AnnotatedString(letter.toString()))
                }
                between(chunk)
            }
            if (chunk % 5 == 4) Thread.sleep(700)
        }
    }

    /** Waits in real time (the store and the echoes run on real threads), letting the page settle between looks. */
    private fun ComposeUiTest.awaitReal(what: String, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + 10_000
        while (!condition()) {
            check(System.currentTimeMillis() < end) { "timed out waiting until $what" }
            Thread.sleep(20)
            waitForIdle()
        }
    }

    private fun assertBoxHoldsTyped(field: SemanticsNodeInteraction) {
        val node = field.fetchSemanticsNode()
        assertEquals(typed, node.config[SemanticsProperties.EditableText].text, "every letter, in order")
        assertEquals(TextRange(typed.length), node.config[SemanticsProperties.TextSelectionRange], "cursor at the end")
    }

    @Test
    fun `A1-TYPE-001 sixty letters typed key by key reach the message box in order while saves echo back`() =
        typeIntoComposer(wholeBox = false)

    @Test
    fun `A1-TYPE-002 sixty letters typed as a keyboard rewriting its word reach the message box in order`() =
        typeIntoComposer(wholeBox = true)
}
