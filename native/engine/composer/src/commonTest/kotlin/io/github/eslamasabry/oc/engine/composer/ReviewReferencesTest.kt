package io.github.eslamasabry.oc.engine.composer

import io.github.eslamasabry.oc.engine.composer.references.ReviewReference
import io.github.eslamasabry.oc.engine.composer.references.ReviewReferenceKind
import io.github.eslamasabry.oc.engine.composer.references.ReviewReferenceScope
import io.github.eslamasabry.oc.engine.composer.references.ReviewReferences
import io.github.eslamasabry.oc.engine.composer.references.ReviewStageOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReviewReferencesTest {
    private fun ref(
        id: String,
        kind: ReviewReferenceKind = ReviewReferenceKind.Selection,
        path: String = "lib/client.dart",
        lineLabel: String? = null,
        snippet: String? = null,
        comment: String? = null,
        scope: ReviewReferenceScope = ReviewReferenceScope.None,
    ) = ReviewReference(id, kind, path, scope, lineLabel, snippet, comment)

    @Test
    fun `B-CHAT-098 references stage per session and duplicates are reported`() {
        val store = ReviewReferences()
        assertEquals(ReviewStageOutcome.Staged, store.stage("a", ref("1", lineLabel = "new line 8")))
        assertEquals(ReviewStageOutcome.Duplicate, store.stage("a", ref("2", lineLabel = "new line 8")))
        assertEquals(1, store.referencesFor("a").size)
        assertEquals(emptyList(), store.referencesFor("b"))
    }

    @Test
    fun `B-CHAT-098 two comments on the same range stay distinct`() {
        val store = ReviewReferences()
        for (note in listOf("first note", "second note")) {
            store.stage("a", ref(store.nextId("c"), ReviewReferenceKind.Comment, lineLabel = "new line 8", comment = note))
        }
        assertEquals(2, store.referencesFor("a").size)
    }

    @Test
    fun `B-CHAT-098 a session is capped and removing an unknown id is silent`() {
        val store = ReviewReferences()
        repeat(ReviewReferences.MAX_PER_SESSION) { assertEquals(ReviewStageOutcome.Staged, store.stage("a", ref("$it", lineLabel = "line $it"))) }
        assertEquals(ReviewStageOutcome.Full, store.stage("a", ref("extra", lineLabel = "line extra")))
        val before = store.staged.value
        store.remove("a", "missing")
        store.remove("other", "1")
        assertTrue(before === store.staged.value, "no change published")
        store.remove("a", "0")
        assertEquals(9, store.referencesFor("a").size)
        assertEquals(9, store.take("a").size)
        assertEquals(emptyList(), store.referencesFor("a"))
    }

    @Test
    fun `B-CHAT-098 a reference is labelled by file and line range`() {
        assertEquals("client.dart · new lines 8–12", ref("1", lineLabel = "new lines 8–12").label)
        assertEquals("client.dart", ref("1").label)
    }

    @Test
    fun `B-CHAT-099 structured markdown, singular and plural, empty stays empty`() {
        val block =
            ReviewReference.format(
                listOf(
                    ref("1", ReviewReferenceKind.Comment, lineLabel = "new line 8", snippet = "+new request", comment = "Keep the retry behavior explicit.", scope = ReviewReferenceScope.Session),
                    ref("2", ReviewReferenceKind.File, path = "lib/main.dart"),
                ),
            )
        assertTrue(block.startsWith("References:"))
        assertTrue("`lib/client.dart` (new line 8 · session" in block)
        assertTrue("Keep the retry behavior explicit." in block)
        assertTrue("```diff\n+new request\n```" in block)
        assertTrue("`lib/main.dart`" in block)
        assertTrue(ReviewReference.format(listOf(ref("1"))).startsWith("Reference:"))
        assertEquals("", ReviewReference.format(emptyList()))
    }

    @Test
    fun `B-CHAT-099 a snippet with fences gets a longer fence`() {
        val block = ReviewReference.format(listOf(ref("1", snippet = "+```dart\n+final x = 1;\n+```")))
        assertTrue("````diff\n+```dart" in block)
        assertTrue(block.endsWith("+```\n````"))
        assertTrue("``````diff\n" in ReviewReference.format(listOf(ref("1", snippet = "+`````\n+edge"))))
    }

    @Test
    fun `B-CHAT-099 the send-wide budget truncates whole lines and says so`() {
        val huge = (0 until 400).joinToString("\n") { "+line $it" }
        val block = ReviewReference.format(listOf(ref("1", snippet = huge)), budget = 200)
        assertTrue(block.length < 600)
        assertTrue("+line 0" in block)
        assertFalse("+line 399" in block)
        assertTrue("Snippet truncated to fit the prompt" in block)
        assertTrue("of 400 lines" in block)
        assertTrue("characters omitted" in block)
        assertTrue("Read `lib/client.dart` for the rest." in block)
        assertFalse("+line 1\n+li\n" in block)
    }

    @Test
    fun `B-CHAT-099 a small reference is never starved and zero budget keeps the pointer only`() {
        val huge = (0 until 400).joinToString("\n") { "+huge $it" }
        val block =
            ReviewReference.format(listOf(ref("1", path = "lib/small.dart", snippet = "+tiny change"), ref("2", path = "lib/huge.dart", snippet = huge)), budget = 400)
        assertTrue("+tiny change" in block)
        assertEquals(1, Regex("Snippet truncated").findAll(block).count())
        assertTrue("Read `lib/huge.dart` for the rest." in block)
        val zero = ReviewReference.format(listOf(ref("1", snippet = "+one\n+two")), budget = 0)
        assertTrue("`lib/client.dart`" in zero)
        assertFalse("+one" in zero)
        assertTrue("showing 0 of 2 lines" in zero)
    }

    @Test
    fun `B-CHAT-099 ten maximum references stay bounded by the default budget`() {
        val page = (0 until 4000).joinToString("\n") { "+line $it" }
        val block = ReviewReference.format((0 until ReviewReferences.MAX_PER_SESSION).map { ref("$it", path = "lib/file$it.dart", snippet = page) })
        assertTrue(block.length < ReviewReference.MAX_SNIPPET_CHARS + 4000)
    }
}
