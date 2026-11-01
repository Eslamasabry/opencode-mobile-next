package io.github.eslamasabry.oc.ui.kitchat.markdown

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.kitchat.ChatTestTheme
import io.github.eslamasabry.oc.ui.kitchat.stream.KitMotionLevel
import io.github.eslamasabry.oc.ui.kitchat.stream.RevealRegistry
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Streaming-append tests on the P3 fixtures (S2-KCB): the 6,000-word `perf:stream-6000` reply and the real OC2
 * recording, appended delta by delta, drawn by the renderers. They prove the renderer half of "block keys stable while
 * streaming" (plan 09 §3.8, §9.1): a settled block keeps its key and position, its text is built once for its
 * instance, and only the tail is rebuilt per publish.
 */
@OptIn(ExperimentalTestApi::class)
class MarkdownStreamingTest {
    private val actions = KitMarkdownActions(onLink = {})

    @Test
    fun `B-CHAT-096 P3 stream-6000 - settled keys never move and settled text is built once while the tail grows`() =
        runComposeUiTest {
            val reply = P3Fixtures.reply()
            val deltas = P3Fixtures.deltas(reply)
            assertTrue(deltas.size > 5_000, "the P3 stream has ${deltas.size} deltas")
            val md = TestStreamingMarkdown("text-0")
            var blocks: ImmutableList<KitMdBlock> by mutableStateOf(persistentListOf())
            setContent {
                ChatTestTheme {
                    // One reply in one item, keyed blocks: the shape cards and the reader use.
                    LazyColumn(Modifier.fillMaxSize()) { item { KitMarkdown(blocks, actions, live = true) } }
                }
            }
            val firstSeen = HashMap<String, KitMdBlock>()
            var previousKeys = emptyList<String>()
            var publishes = 0
            var buildsBefore = KitMdCounters.inlineBuilds
            // Publish at most every 50 ms of stream time, like the engine: here every 25 deltas (≈ 1/3 s at 75 per s).
            deltas.chunked(25).forEach { chunk ->
                chunk.forEach(md::append)
                val next = md.blocks
                val keys = next.map { it.key }
                // Keys are stable: the old settled prefix is a prefix of the new key list.
                val settledBefore = previousKeys.dropLast(1)
                assertEquals(settledBefore, keys.take(settledBefore.size), "settled keys moved at publish $publishes")
                // Settled blocks keep their instance (the parser contract the renderer's remember relies on).
                next.dropLast(1).forEach { b -> firstSeen.getOrPut(b.key) { b }.let { assertSame(it, b, b.key) } }
                blocks = next
                waitForIdle()
                previousKeys = keys
                publishes++
            }
            val builds = KitMdCounters.inlineBuilds - buildsBefore
            val textBlocks = md.blocks.count { it !is KitMdBlock.Code && it !is KitMdBlock.Table && it !is KitMdBlock.Rule }
            // Each publish rebuilds at most the tail and the block that just settled; settled text is never rebuilt.
            assertTrue(builds <= textBlocks + 2 * publishes, "inline builds $builds for $textBlocks text blocks over $publishes publishes")
            assertEquals(reply.length, md.source.length)
            onNodeWithText("Plan").assertExists()
            assertEquals(1, onAllNodesWithTag(STREAM_CARET_TAG).fetchSemanticsNodes().size)
        }

    @Test
    fun `B-CHAT-096 P3 stream-6000 in a lazy list - one row per block, rows keyed by block key, settled rows not rebuilt`() =
        runComposeUiTest {
            val deltas = P3Fixtures.deltas()
            val md = TestStreamingMarkdown("text-0")
            var blocks: ImmutableList<KitMdBlock> by mutableStateOf(persistentListOf())
            val registry = RevealRegistry()
            setContent {
                ChatTestTheme {
                    // The transcript shape: newest at the bottom, each block its own keyed lazy row (plan 09 §7.2).
                    LazyColumn(Modifier.fillMaxSize(), reverseLayout = true) {
                        items(blocks.asReversed(), key = { it.key }, contentType = { it::class }) { b ->
                            KitMarkdownBlock(
                                b, actions, live = b === blocks.last(), caret = b === blocks.last(),
                                reveal = KitMdReveal(registry, "text-0", KitMotionLevel.Off),
                            )
                        }
                    }
                }
            }
            var publishes = 0
            val before = KitMdCounters.inlineBuilds
            deltas.take(3_000).chunked(25).forEach { chunk ->
                chunk.forEach(md::append)
                blocks = md.blocks
                waitForIdle()
                publishes++
            }
            val builds = KitMdCounters.inlineBuilds - before
            // Only the newest rows are composed in a reversed lazy list; each publish rebuilds ≤ 2 of them.
            assertTrue(builds <= 2 * publishes + 40, "inline builds $builds over $publishes publishes")
            assertEquals(0, registry.activeCount, "motion off: nothing left revealing")
        }

    @Test
    fun `P3 the real OC2 recording streams into one prose block per reply`() =
        runComposeUiTest {
            val sse = checkNotNull(javaClass.getResource("/oc2/events.sse")).readText()
            val deltas = P3Fixtures.sseTextDeltas(sse)
            assertTrue(deltas.isNotEmpty())
            val byMessage = LinkedHashMap<String, TestStreamingMarkdown>()
            var replies: List<ImmutableList<KitMdBlock>> by mutableStateOf(emptyList())
            setContent { ChatTestTheme { Column { replies.forEach { KitMarkdown(it, actions) } } } }
            deltas.forEach { (message, delta) ->
                byMessage.getOrPut(message) { TestStreamingMarkdown(message) }.append(delta)
                replies = byMessage.values.map { it.blocks }
                waitForIdle()
            }
            replies.forEach { assertEquals(listOf("pong."), it.map { b -> (b as KitMdBlock.Prose).inlines.text }) }
            assertEquals(2, byMessage.size, "the recording holds two assistant replies")
            assertEquals(2, onAllNodesWithText("pong.").fetchSemanticsNodes().size)
            assertEquals(0, onAllNodesWithTag(STREAM_CARET_TAG).fetchSemanticsNodes().size, "settled replies show no caret")
        }
}
