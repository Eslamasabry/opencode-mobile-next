package io.github.eslamasabry.oc.engine.composer

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.getOrNull
import io.github.eslamasabry.oc.core.model.PromptAttachment
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.composer.drafts.Drafts
import io.github.eslamasabry.oc.engine.composer.stash.DraftMigrationBlocker
import io.github.eslamasabry.oc.engine.composer.stash.PromptShelf
import io.github.eslamasabry.oc.engine.composer.stash.SavedPromptMigration
import io.github.eslamasabry.oc.engine.composer.stash.StashedPrompt
import io.github.eslamasabry.oc.engine.composer.vault.AttachmentRef
import io.github.eslamasabry.oc.engine.composer.vault.AttachmentVault
import io.github.eslamasabry.oc.engine.composer.vault.MemoryPayloadStore
import io.github.eslamasabry.oc.engine.composer.vault.toJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Flutter `test/saved_prompts_migration_test.dart` on the native migration (B-STATE-087). */
class SavedPromptMigrationTest {
    private val prefs = SelectivePrefs()
    private val redactor = Redactor().apply { registerSecret("fake-credential") }
    private val savedPayloads = MemoryPayloadStore()
    private val draftPayloads = MemoryPayloadStore()
    private val draftVault = AttachmentVault(draftPayloads)
    private val shelf = PromptShelf(prefs, AttachmentVault(savedPayloads), TestProfiles("a", "b"), redactor)

    private fun runner(vararg known: String = arrayOf("a", "b")) =
        SavedPromptMigration(prefs, shelf, draftVault, TestProfiles(*known), redactor)

    private fun draft(
        profile: String = "",
        session: String = "conversation-1",
        text: String = "Older unsent prompt",
        refs: List<AttachmentRef> = emptyList(),
    ): JsonObject =
        buildJsonObject {
            put("sessionID", JsonPrimitive(session))
            if (profile.isNotEmpty()) put("profileID", JsonPrimitive(profile))
            put("text", JsonPrimitive(text))
            put("updatedAt", JsonPrimitive(123))
            if (refs.isNotEmpty()) {
                put("attachments", JsonArray(refs.map { it.toJson() }))
                put("directory", JsonPrimitive("/project"))
                put("workspace", JsonPrimitive("main"))
            }
        }

    private fun putDrafts(vararg rows: JsonObject): String {
        val raw = JsonArray(rows.toList()).toString()
        prefs.inner.rawPut(Drafts.KEY, raw)
        return raw
    }

    private fun saved(profile: String) = (shelf.stashes(profile) as Outcome.Ok).value

    @Test
    fun `B-STATE-087 older drafts move once into Saved prompts and drafts naming a server stay`() = runTest {
        putDrafts(
            draft(text = "Use password=fake-credential in example"),
            draft(profile = "a", session = "live-a", text = "Live on A"),
            draft(profile = "b", session = "live-b", text = "Live on B"),
        )
        val first = runner().runForProfile("a")
        assertTrue(first.complete)
        assertEquals(1, first.migrated)
        val prompt = saved("a").single()
        assertEquals("Use password=${Redactor.MASK} in example", prompt.text)
        assertEquals(123, prompt.createdAt)
        assertEquals(emptyList(), saved("b"))
        val left = assertNotNull(prefs.string(Drafts.KEY))
        assertTrue("Live on A" in left && "Live on B" in left)
        assertFalse("Older unsent" in left || "fake-credential" in left)
        assertEquals(true, prefs.bool(SavedPromptMigration.markerKey("a")))
        val backup = assertNotNull(prefs.string(SavedPromptMigration.backupKey("a")))
        assertTrue("Use password=${Redactor.MASK} in example" in backup)
        assertFalse("fake-credential" in backup)
        // Both keys are per-profile `oc.<what>.<profileId>` keys the deletion sweep finds.
        assertTrue(SavedPromptMigration.markerKey("a").endsWith(".a") && SavedPromptMigration.backupKey("a").endsWith(".a"))

        assertTrue(runner().runForProfile("a").alreadyComplete)
        assertEquals(1, saved("a").size)
        val other = runner().runForProfile("b")
        assertEquals(true to 0, other.complete to other.migrated)
        assertEquals(emptyList(), saved("b"))
        val keysBefore = prefs.keys()
        assertEquals(DraftMigrationBlocker.UnknownOwner, runner().runForProfile("deleted-profile").blocker)
        assertEquals(keysBefore, prefs.keys())
    }

    @Test
    fun `B-STATE-087 copied attachments survive collection of the older drafts' files and a live draft keeps its own`() = runTest {
        val photo = PromptAttachment("image/png", "draft-photo.png", "data:image/png;base64,ZmFrZS1waG90bw==")
        val live = PromptAttachment("image/png", "live.png", "data:image/png;base64,bGl2ZQ==")
        val refs = (draftVault.store("", listOf(photo)) as Outcome.Ok).value
        val liveRefs = (draftVault.store("a", listOf(live)) as Outcome.Ok).value
        putDrafts(draft(text = "", refs = refs), draft(profile = "a", session = "live", text = "live", refs = liveRefs))
        val result = runner("a").runForProfile("a")
        assertTrue(result.complete)
        val prompt = saved("a").single()
        assertEquals(emptyList(), prompt.attachments)
        assertEquals(1, prompt.attachmentRefs.size)
        assertEquals("/project", prompt.directory)
        val restored = shelf.restoreAttachments("a", prompt.id, sameLocation = true).getOrNull()!!
        assertEquals(photo.url, restored.attachments.single().url)
        assertEquals(emptyList(), restored.unavailable)
        assertEquals(emptyList(), draftVault.restore("", refs, sameLocation = true).attachments, "older draft's file collected")
        assertEquals(live.url, draftVault.restore("a", liveRefs, sameLocation = true).attachments.single().url)
        assertTrue("\"live\"" in prefs.string(Drafts.KEY)!!)
    }

    @Test
    fun `B-STATE-087 failed source removal retries without duplicating saved prompts`() = runTest {
        val raw = putDrafts(draft())
        prefs.refused += Drafts.KEY
        assertFalse(runner("a").runForProfile("a").complete)
        assertEquals(raw, prefs.string(Drafts.KEY))
        assertFalse(prefs.contains(SavedPromptMigration.markerKey("a")))
        prefs.refused.clear()
        assertTrue(runner("a").runForProfile("a").complete)
        assertEquals(1, saved("a").size)
        assertFalse(prefs.contains(Drafts.KEY))
        assertEquals(1, (kotlinx.serialization.json.Json.parseToJsonElement(prefs.string(SavedPromptMigration.backupKey("a"))!!) as JsonArray).size)
    }

    @Test
    fun `B-STATE-087 a corrupt draft index stays intact`() = runTest {
        prefs.inner.rawPut(Drafts.KEY, "not json")
        assertEquals(DraftMigrationBlocker.CorruptDrafts, runner("a").runForProfile("a").blocker)
        assertEquals("not json", prefs.string(Drafts.KEY))
        assertFalse(prefs.contains(SavedPromptMigration.markerKey("a")))
        assertEquals(emptyList(), saved("a"))
    }

    @Test
    fun `B-STATE-087 a missing attachment file does not hold back the text`() = runTest {
        putDrafts(draft(refs = listOf(AttachmentRef("missing.png", "image/png", blob = "0".repeat(64), bytes = 20))))
        assertTrue(runner("a").runForProfile("a").complete)
        val prompt = saved("a").single()
        assertEquals("Older unsent prompt", prompt.text)
        assertEquals(0, prompt.attachmentCount)
        assertTrue("missing.png" in prefs.string(SavedPromptMigration.backupKey("a"))!!)
    }

    @Test
    fun `B-STATE-087 a full Saved prompts keeps the older drafts where they are`() = runTest {
        val raw = putDrafts(draft())
        repeat(PromptShelf.CAPACITY) { shelf.stash("a", StashedPrompt(id = "kept-$it", text = "kept $it", createdAt = it.toLong())) }
        assertEquals(DraftMigrationBlocker.Full, runner("a").runForProfile("a").blocker)
        assertEquals(raw, prefs.string(Drafts.KEY))
        assertNull(prefs.bool(SavedPromptMigration.markerKey("a")))
        shelf.remove("a", "kept-0")
        assertEquals(1, runner("a").runForProfile("a").migrated)
        assertFalse(prefs.contains(Drafts.KEY))
    }
}
