package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.model.PromptAttachment
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.composer.stash.PromptShelf
import io.github.eslamasabry.oc.engine.composer.stash.StashedPrompt
import io.github.eslamasabry.oc.engine.composer.store.ComposerProfiles
import io.github.eslamasabry.oc.engine.composer.store.MemoryComposerPrefs
import io.github.eslamasabry.oc.engine.composer.vault.AttachmentVault
import io.github.eslamasabry.oc.engine.composer.vault.MemoryPayloadStore
import io.github.eslamasabry.oc.engine.profiles.DeletionOptions
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class StashDeletionCleanerTest {
    private val prefs = MemoryComposerPrefs()
    private val payloads = MemoryPayloadStore()
    private val profiles =
        object : ComposerProfiles {
            override fun exists(profileId: String) = profileId == "p1"

            override fun count() = 1
        }
    private val shelf = PromptShelf(prefs, AttachmentVault(payloads), profiles, Redactor())
    private val photo = PromptAttachment(mime = "text/plain", filename = "notes.txt", url = "data:text/plain;base64,${Base64.encode("hi".encodeToByteArray())}")

    @Test
    fun `B-SEC-172 deleting a server removes its saved prompts and their attachment bytes`() = runTest {
        assertIs<Outcome.Ok<Unit>>(shelf.stash("p1", StashedPrompt(id = "1", text = "with a file", createdAt = 1, attachments = listOf(photo))))
        assertTrue(payloads.size > 0)
        val cleared = StashDeletionCleaner(shelf).clear(ProfileId("p1"), DeletionOptions())
        assertEquals(Outcome.Ok(1), cleared)
        assertTrue(prefs.keys().none { it.startsWith("oc.promptStash.p1.") || it == "oc.stashAttachmentVault.p1" })
        assertEquals(0, payloads.size)
    }

    @Test
    fun `B-SEC-172 a shelf that cannot be cleared keeps the server and opens again`() = runTest {
        shelf.stash("p1", StashedPrompt(id = "1", text = "kept", createdAt = 1))
        prefs.refuse = true
        assertIs<Outcome.Failed>(StashDeletionCleaner(shelf).clear(ProfileId("p1"), DeletionOptions()))
        prefs.refuse = false
        assertIs<Outcome.Ok<Unit>>(shelf.stash("p1", StashedPrompt(id = "2", text = "still usable", createdAt = 2)))
    }
}
