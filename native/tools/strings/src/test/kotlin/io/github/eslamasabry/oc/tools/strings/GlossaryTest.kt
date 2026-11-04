package io.github.eslamasabry.oc.tools.strings

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Dart `test/ui_glossary_test.dart` over the native English strings (B-L10N-021/022/023). */
class GlossaryTest {
    private val nativeRoot = File(assertNotNull(System.getProperty("strings.nativeRoot"), "run through Gradle"))
    private val strings by lazy { Glossary.englishStrings(File(nativeRoot, "ui/words/src/commonMain/composeResources/values")) }

    /** The committed baseline: `gate<TAB>key` lines. Shrinks only: every entry must still offend. */
    private val baseline: Map<String, Set<String>> by lazy {
        File(System.getProperty("strings.toolDir"), "src/test/resources/glossary-baseline.tsv").readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .groupBy({ it.substringBefore('\t') }, { it.substringAfter('\t') })
            .mapValues { it.value.toSet() }
    }

    private fun ratchet(gate: String, offenders: List<String>) {
        val keys = offenders.map { it.substringBefore(':') }.toSet()
        val allowed = baseline[gate].orEmpty()
        val fresh = offenders.filter { it.substringBefore(':') !in allowed }
        assertTrue(fresh.isEmpty(), "$gate: new offenders\n" + fresh.joinToString("\n"))
        val stale = allowed - keys
        assertTrue(stale.isEmpty(), "$gate: baseline entries no longer needed (drop them): $stale")
    }

    // ---- B-L10N-023: the scanning rules work ----

    @Test
    fun `B-L10N-023 the label rule separates labels from sentences`() {
        assertTrue(Glossary.isShortActionLabel("Retry"))
        assertTrue(Glossary.isShortActionLabel("Retry saving draft"))
        assertTrue(Glossary.isShortActionLabel("Retry {runtime}"))
        assertFalse(Glossary.isShortActionLabel("Free some space and retry."))
        assertFalse(Glossary.isShortActionLabel("Retry disabling recovery on this phone"))
        assertEquals("Retry", Glossary.bannedTermIn("Retry"))
        assertEquals("Retry", Glossary.bannedTermIn("Retry projects"))
        assertEquals("Check status", Glossary.bannedTermIn("Check status"))
        assertNull(Glossary.bannedTermIn("Retrying"))
        assertNull(Glossary.bannedTermIn("Try again"))
        assertNull(Glossary.bannedTermIn("Aborted"))
    }

    @Test
    fun `B-L10N-023 the noun rule matches standalone words only`() {
        assertEquals("session", Glossary.bannedNounIn("New session"))
        assertEquals("sessions", Glossary.bannedNounIn("All sessions"))
        assertEquals("chat", Glossary.bannedNounIn("Untitled chat"))
        assertEquals("chats", Glossary.bannedNounIn("New chats"))
        assertEquals("session", Glossary.bannedNounIn("SESSION"))
        assertNull(Glossary.bannedNounIn("New conversation"))
        assertNull(Glossary.bannedNounIn("Chatty"))
        assertNull(Glossary.bannedNounIn("Obsession"))
    }

    @Test
    fun `B-L10N-023 the engine-word rule catches Gas City terms, not plain words`() {
        val e = Glossary.ENGINE_WORDS
        assertTrue(e.containsMatchIn("Batch · convoy · 1 of 5 done"))
        assertTrue(e.containsMatchIn("Run · formula mol-upgrade"))
        assertTrue(e.containsMatchIn("city phone"))
        assertTrue(e.containsMatchIn("127.0.0.1 · This phone"))
        assertTrue(e.containsMatchIn("Gas City {version}"))
        assertTrue(e.containsMatchIn("Gas City 1.4.1"))
        assertTrue(e.containsMatchIn("gastown.polecat"))
        assertFalse(e.containsMatchIn("Working · 3 of 5 steps done"))
        assertFalse(e.containsMatchIn("On this phone · Not answering"))
        assertFalse(e.containsMatchIn("Reviewer (merges)"))
        assertFalse(e.containsMatchIn("Electricity"))
    }

    @Test
    fun `B-L10N-023 ICU messages expand to their plain wordings`() {
        val s = Glossary.SLOT
        assertEquals(listOf("Stop $s?"), Glossary.variants("Stop {name}?"))
        assertEquals(
            listOf("Stop 1 process?", "Stop $s processes?"),
            Glossary.variants("{count, plural, =1{Stop 1 process?} other{Stop # processes?}}"),
        )
        assertEquals(listOf("Delete", s, "now"), Glossary.wordsOf("Delete $s · now"))
    }

    @Test
    fun `B-L10N-023 the contradiction rule finds both halves of a pair`() {
        assertEquals("working" to "stopped", Glossary.contradictionIn("Working · stopped"))
        assertEquals("paused" to "idle", Glossary.contradictionIn("Paused, idle"))
        assertEquals("connected" to "reconnecting", Glossary.contradictionIn("Connected — reconnecting…"))
        assertNull(Glossary.contradictionIn("Working · 3 of 5 done"))
        assertNull(Glossary.contradictionIn("Stopped"))
    }

    @Test
    fun `B-L10N-023 the technical-word rule finds engine words, ports, paths and versions`() {
        val t = Glossary::technicalIn
        assertEquals(listOf("engine word \"convoy\""), t("Batch · convoy"))
        assertEquals(emptyList(), t("Gas City on this phone"))
        assertEquals(listOf("address \"127.0.0.1\"", "port \":4096\""), t("Open 127.0.0.1:4096"))
        assertEquals(listOf("address \"100.64.0.0/10\"", "address \"203.0.113.1\""), t("Blocks 100.64.0.0/10 and 203.0.113.1"))
        assertEquals(listOf("path \"docs/ai-team-host.md\""), t("Read docs/ai-team-host.md first"))
        assertEquals(emptyList(), t("Send and/or save"))
        assertEquals(listOf("version \"A2A 1.0\""), t("A2A 1.0 · JSON-RPC"))
        assertEquals(listOf("version \"Base 24.04\""), t("Ubuntu Base 24.04"))
        assertEquals(listOf("version \"version 2.1\"", "version \"v3.0\""), t("Needs version 2.1 or v3.0"))
        assertEquals(listOf("version \"1.4.1\"", "version \"1.4.1\""), t("Gas City 1.4.1, then 1.4.1 again"))
        assertEquals(emptyList(), t("Takes 2.5 minutes"))
        assertEquals(emptyList(), t("Free 1.5 GB"))
        assertEquals(listOf("path \"~/.config/opencode\""), t("Saved in ~/.config/opencode"))
        assertEquals(listOf("path \"/data/local/tmp\""), t("Files in /data/local/tmp"))
        assertEquals(emptyList(), t("Type /sessions to list them"))
        assertEquals(listOf("version \"1.4.2\""), t("Update to 1.4.2"))
        assertEquals(listOf("version \"{version}\""), t("Update to {version}"))
        assertEquals(listOf("status code \"HTTP 401\""), t("Server said HTTP 401"))
        assertEquals(emptyList(), t("Working · 3 of 5 steps done"))
        assertEquals(emptyList(), t("Starts at 10:30"))
    }

    @Test
    fun `B-L10N-023 every baseline exemption is still needed`() {
        // ratchet() fails on a stale entry; this proves the gates read real strings and the baseline file exists.
        assertTrue(strings.size > 1000, "read ${strings.size} native English strings")
        assertTrue(baseline.isNotEmpty())
    }

    // ---- B-L10N-021: copy follows the glossary ----

    @Test
    fun `B-L10N-021 action labels use the glossary word, not a banned synonym`() =
        ratchet("action", Glossary.actionLabelOffenders(strings, emptySet()))

    @Test
    fun `B-L10N-021 labels say conversation, not session or chat`() =
        ratchet("noun", Glossary.nounOffenders(strings, emptySet()))

    @Test
    fun `B-L10N-021 AI Team rows and cards use the person's words, not the engine's`() =
        ratchet("engine", Glossary.engineOffenders(strings, emptySet()))

    @Test
    fun `B-L10N-021 technical words appear only where the baseline reviewed them, and it only shrinks`() =
        ratchet("technical", Glossary.technicalCounts(strings).map { (k, n) -> "$k: $n" })

    // ---- B-L10N-022 ----

    @Test
    fun `B-L10N-022 no text says two contradicting states at once`() =
        ratchet("contradiction", Glossary.contradictionOffenders(strings, emptySet()))

    @Test
    fun `B-L10N-022 confirmation titles ask and their labels name the act`() {
        // Native confirmations follow the Dart key convention: <x>Title is a question, <x>Confirm names the act.
        val titles = strings.filterKeys { it.endsWith("ConfirmTitle") }
        assertTrue(titles.isNotEmpty())
        val offenders =
            titles.mapNotNull { (k, v) ->
                val label = strings[k.removeSuffix("Title") + "Label"] ?: strings[k.removeSuffix("ConfirmTitle") + "ConfirmLabel"]
                when {
                    !v.trim().endsWith("?") -> "$k: \"$v\" does not ask"
                    label != null && Glossary.wordsOf(label).size < 2 -> "$k label \"$label\" does not name the thing"
                    else -> null
                }
            }
        ratchet("confirm", offenders)
    }

    // ---- B-NAV-052 (copy half; the AI Team gate receipt has no native surface yet) ----

    @Test
    fun `B-NAV-052 Saved prompts no longer claims one server`() {
        val intro = assertNotNull(strings["promptStashIntro"])
        assertFalse("server" in intro.lowercase(), intro)
    }
}
