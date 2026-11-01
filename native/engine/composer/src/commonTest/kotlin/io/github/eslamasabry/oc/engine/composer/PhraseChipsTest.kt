package io.github.eslamasabry.oc.engine.composer

import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.composer.phrases.PhraseChip
import io.github.eslamasabry.oc.engine.composer.phrases.PhraseChipStore
import io.github.eslamasabry.oc.engine.composer.phrases.PhraseChipStores
import io.github.eslamasabry.oc.engine.composer.phrases.PhraseMoment
import io.github.eslamasabry.oc.engine.composer.phrases.PhraseRules
import io.github.eslamasabry.oc.engine.composer.store.MemoryComposerPrefs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhraseChipsTest {
    private val prefs = MemoryComposerPrefs()
    private val clock = TestClock(1_760_259_600_000)
    private val redactor = Redactor()
    private val store = PhraseChipStore(prefs, "alpha", clock, redactor)

    private suspend fun send(text: String, moment: PhraseMoment, times: Int = 1) {
        repeat(times) {
            store.record(text, moment)
            clock.millis += 60_000
        }
    }

    private fun chips(moment: PhraseMoment, starters: List<String> = emptyList(), exclude: Set<String> = emptySet()) =
        store.chipsFor(moment, starters, exclude).map { it.text }

    @Test
    fun `B-CHAT-077 case, spacing and trailing punctuation are one phrase`() {
        assertEquals("run the tests", PhraseRules.normalize("  Run  the TESTS. "))
        assertEquals("run the tests", PhraseRules.normalize("Run the tests?!"))
        assertEquals("ما المستجدات", PhraseRules.normalize("ما المستجدات؟"))
        assertEquals("run the tests", PhraseRules.normalize("Run\u00A0the tests"))
    }

    @Test
    fun `B-CHAT-077 the latest spelling is shown`() = runTest {
        send("run the tests", PhraseMoment.Idle)
        send("Run the tests!", PhraseMoment.Idle)
        assertEquals(listOf("Run the tests!"), chips(PhraseMoment.Idle))
    }

    @Test
    fun `B-CHAT-078 long, multi-line, command, shell and mention text is never recorded`() {
        for (text in listOf("x".repeat(41), "one\ntwo", "/compact", "!ls -la", "ask @reviewer", " ", "a")) {
            assertFalse(PhraseRules.isRecordable(text, redactor), text)
        }
        assertTrue(PhraseRules.isRecordable("abcdefghi abcdefghi abcdefghi abcdefghij", redactor))
    }

    @Test
    fun `B-CHAT-078 a message that carried files is not recorded`() = runTest {
        assertFalse(store.record("Look at this", PhraseMoment.Idle, hasAttachments = true))
        assertEquals(0, store.learnedCount)
    }

    @Test
    fun `B-CHAT-078 anything that looks like a secret is never recorded or shown`() = runTest {
        for (text in listOf(
            "sk-ant-api03-abcdefghijklmnopqrstuvwxyz",
            "ghp_abcdefghijklmnopqrstuvwxyz0123",
            "AKIAABCDEFGHIJKLMNOPQRST",
            "use abcdefghijklmnopqrstuvwxyz now",
        )) {
            assertTrue(PhraseRules.looksSecret(text, redactor), text)
            send(text, PhraseMoment.Idle, 3)
        }
        assertEquals(0, store.learnedCount)
        assertEquals(emptyList(), chips(PhraseMoment.Idle))
        assertNull(prefs.string(PhraseChipStore.keyFor("alpha")))
    }

    @Test
    fun `B-CHAT-079 a phrase needs two sends`() = runTest {
        send("Run the tests", PhraseMoment.Idle)
        assertEquals(emptyList(), chips(PhraseMoment.Idle))
        send("run the tests.", PhraseMoment.Idle)
        assertEquals(listOf("run the tests."), chips(PhraseMoment.Idle))
    }

    @Test
    fun `B-CHAT-079 chips are chosen for the moment and a phrase sent in two moments counts for both`() = runTest {
        send("Commit it", PhraseMoment.FinishedWithChanges, 2)
        send("What's the update?", PhraseMoment.Working, 2)
        assertEquals(listOf("Commit it"), chips(PhraseMoment.FinishedWithChanges))
        assertEquals(listOf("What's the update?"), chips(PhraseMoment.Working))
        assertEquals(emptyList(), chips(PhraseMoment.Idle))
        send("Run the tests", PhraseMoment.Idle)
        send("Run the tests", PhraseMoment.FinishedWithChanges)
        assertEquals(listOf("Run the tests"), chips(PhraseMoment.Idle))
        assertEquals(listOf("Commit it", "Run the tests"), chips(PhraseMoment.FinishedWithChanges))
    }

    @Test
    fun `B-CHAT-080 more sends rank first and at most four are shown`() = runTest {
        send("one", PhraseMoment.Idle, 2)
        send("two", PhraseMoment.Idle, 3)
        send("three", PhraseMoment.Idle, 4)
        send("four", PhraseMoment.Idle, 5)
        send("five", PhraseMoment.Idle, 6)
        assertEquals(listOf("five", "four", "three", "two"), chips(PhraseMoment.Idle))
    }

    @Test
    fun `B-CHAT-080 recency decays a stale favourite below a fresh one`() = runTest {
        send("old favourite", PhraseMoment.Idle, 4)
        clock.millis += 60L * 86_400_000
        send("fresh one", PhraseMoment.Idle, 2)
        assertEquals(listOf("fresh one", "old favourite"), chips(PhraseMoment.Idle))
    }

    @Test
    fun `B-CHAT-080 without decay time the bigger count wins`() = runTest {
        send("old favourite", PhraseMoment.Idle, 4)
        clock.millis += 86_400_000
        send("fresh one", PhraseMoment.Idle, 2)
        assertEquals(listOf("old favourite", "fresh one"), chips(PhraseMoment.Idle))
    }

    @Test
    fun `B-CHAT-081 the moment's starter leads, shows alone, is not repeated and can be excluded`() = runTest {
        assertEquals(listOf(PhraseChip("Continue", starter = true)), store.chipsFor(PhraseMoment.Idle, listOf("Continue")))
        send("continue.", PhraseMoment.StoppedPartway, 2)
        assertEquals(listOf(PhraseChip("continue.")), store.chipsFor(PhraseMoment.StoppedPartway, listOf("Continue")))
        send("Run the tests", PhraseMoment.StoppedPartway, 3)
        assertEquals(listOf("continue.", "Run the tests"), chips(PhraseMoment.StoppedPartway, listOf("Continue")))
        assertEquals(listOf("Run the tests"), chips(PhraseMoment.StoppedPartway, listOf("Continue"), setOf("Continue")))
    }

    @Test
    fun `B-CHAT-082 a hidden phrase and its starter are never suggested until Show hidden phrases`() = runTest {
        send("Commit it", PhraseMoment.Idle, 3)
        store.hide("commit it!")
        assertEquals(emptyList(), chips(PhraseMoment.Idle))
        store.hide("Continue")
        assertEquals(emptyList(), chips(PhraseMoment.StoppedPartway, listOf("Continue")))
        assertEquals(2, store.hiddenCount)
        assertFalse(store.record("Commit it", PhraseMoment.Idle))
        store.showHidden()
        assertEquals(0, store.hiddenCount)
        assertEquals(listOf("Commit it"), chips(PhraseMoment.Idle))
    }

    @Test
    fun `B-CHAT-083 only the 50 most recent phrases and 100 hidden ones are kept`() = runTest {
        repeat(PhraseRules.MAX_ENTRIES + 5) { send("phrase $it", PhraseMoment.Idle) }
        assertEquals(PhraseRules.MAX_ENTRIES, store.learnedCount)
        send("phrase 0", PhraseMoment.Idle)
        assertEquals(PhraseRules.MAX_ENTRIES, store.learnedCount)
        assertFalse("phrase 0" in chips(PhraseMoment.Idle))
        repeat(PhraseRules.MAX_HIDDEN + 10) { store.hide("hidden $it") }
        assertEquals(PhraseRules.MAX_HIDDEN, store.hiddenCount)
    }

    @Test
    fun `B-CHAT-084 the switch is device-wide and defaults on`() = runTest {
        val stores = PhraseChipStores(prefs, clock, redactor)
        assertTrue(stores.enabled())
        stores.setEnabled(false)
        assertFalse(stores.enabled())
        assertEquals("oc.phraseChips.enabled", PhraseChipStore.ENABLED_KEY)
        assertFalse(PhraseChipStore.ENABLED_KEY.endsWith(".alpha"))
    }

    @Test
    fun `B-CHAT-084 redo phrases are never offered while the agent works`() = runTest {
        send("Try again", PhraseMoment.Working, 3)
        send("Run the tests", PhraseMoment.Working, 3)
        send("Try again", PhraseMoment.StoppedPartway, 3)
        assertEquals(listOf("Run the tests"), chips(PhraseMoment.Working))
        assertEquals(listOf("Try again"), chips(PhraseMoment.StoppedPartway))
    }

    @Test
    fun `B-STATE-059 learned phrases survive a restart and stay separate per profile`() = runTest {
        send("Run the tests", PhraseMoment.Idle, 2)
        assertEquals(listOf("Run the tests"), PhraseChipStore(prefs, "alpha", clock, redactor).chipsFor(PhraseMoment.Idle).map { it.text })
        assertEquals(emptyList(), PhraseChipStore(prefs, "beta", clock, redactor).chipsFor(PhraseMoment.Idle))
    }

    @Test
    fun `B-STATE-059 a corrupt blob reads as nothing learned`() = runTest {
        prefs.rawPut(PhraseChipStore.keyFor("alpha"), "{not json")
        assertEquals(emptyList(), chips(PhraseMoment.Idle))
        send("Run the tests", PhraseMoment.Idle, 2)
        assertEquals(listOf("Run the tests"), chips(PhraseMoment.Idle))
    }

    @Test
    fun `B-STATE-059 a Flutter blob reads unchanged and writes keep its shape`() = runTest {
        prefs.rawPut(
            PhraseChipStore.keyFor("alpha"),
            """{"v":1,"hidden":["nope"],"entries":{"commit it":{"t":"Commit it","n":3,"l":1760259500000,""" +
                """"m":{"finishedWithChanges":{"n":3,"l":1760259500000}}}}}""",
        )
        assertEquals(listOf("Commit it"), chips(PhraseMoment.FinishedWithChanges))
        assertEquals(1, store.hiddenCount)
        store.record("Commit it", PhraseMoment.Idle)
        assertEquals(
            """{"v":1,"hidden":["nope"],"entries":{"commit it":{"t":"Commit it","n":4,"l":1760259600000,""" +
                """"m":{"finishedWithChanges":{"n":3,"l":1760259500000},"idle":{"n":1,"l":1760259600000}}}}}""",
            prefs.string(PhraseChipStore.keyFor("alpha")),
        )
    }

    @Test
    fun `B-STATE-059 after the profile is closed and swept a late write cannot bring it back`() = runTest {
        val stores = PhraseChipStores(prefs, clock, redactor)
        val owner = stores.forProfile("alpha")
        owner.record("Run the tests", PhraseMoment.Idle)
        stores.closeProfile("alpha")
        prefs.rawPut(PhraseChipStore.keyFor("alpha"), null) // the profile sweep
        owner.record("Run the tests", PhraseMoment.Idle)
        owner.hide("Run the tests")
        assertFalse(prefs.contains(PhraseChipStore.keyFor("alpha")))
        stores.reopenProfile("alpha")
        assertTrue(stores.forProfile("alpha").record("Run the tests", PhraseMoment.Idle), "a re-added server learns again")
    }
}
