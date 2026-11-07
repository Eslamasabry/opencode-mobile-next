package io.github.eslamasabry.oc.engine.composer

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.composer.stash.PromptShelf
import io.github.eslamasabry.oc.engine.composer.stash.StashedPrompt
import io.github.eslamasabry.oc.engine.composer.store.MemoryComposerPrefs
import io.github.eslamasabry.oc.engine.composer.vault.AttachmentVault
import io.github.eslamasabry.oc.engine.composer.vault.MemoryPayloadStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** B-151: the saved prompts Flutter 2.1.4 kept ("Save prompt for later") are the native Saved prompts. */
class FlutterStashImportTest {
    @Test
    fun `B-151 a prompt saved for later on 2_1_4 is listed on native, newest first, per server`() {
        // Exactly what 2.1.4 `PromptShelfStore.stash` writes: one key per prompt, `StashedPrompt.toJson`.
        val prefs =
            MemoryComposerPrefs(
                mapOf(
                    "oc.promptStash.fx-oc2.s1" to
                        """{"id":"s1","text":"upgrade saved prompt","createdAt":1762500000000,""" +
                        """"directory":"/root/app","workspace":null,"attachments":[],"references":[]}""",
                    "oc.promptStash.fx-oc2.s0" to
                        """{"id":"s0","text":"older one","createdAt":1762400000000,"directory":null,""" +
                        """"workspace":null,"attachments":[],"references":[]}""",
                    "oc.promptStash.fx-oc2-b.s2" to
                        """{"id":"s2","text":"other server","createdAt":1762600000000,"directory":null,""" +
                        """"workspace":null,"attachments":[],"references":[]}""",
                )
            )
        val shelf =
            PromptShelf(prefs, AttachmentVault(MemoryPayloadStore()), TestProfiles("fx-oc2", "fx-oc2-b"), Redactor())
        val listed = assertIs<Outcome.Ok<List<StashedPrompt>>>(shelf.stashes("fx-oc2")).value
        assertEquals(listOf("upgrade saved prompt", "older one"), listed.map { it.text })
        assertEquals("/root/app", listed.first().directory)
    }
}
