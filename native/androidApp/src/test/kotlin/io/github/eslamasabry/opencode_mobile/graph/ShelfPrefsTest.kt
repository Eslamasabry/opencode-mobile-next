package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.composer.stash.PromptShelf
import io.github.eslamasabry.oc.engine.composer.store.ComposerPrefs
import io.github.eslamasabry.oc.engine.composer.store.ComposerProfiles
import io.github.eslamasabry.oc.engine.composer.store.MemoryComposerPrefs
import io.github.eslamasabry.oc.engine.composer.vault.AttachmentVault
import io.github.eslamasabry.oc.engine.composer.vault.MemoryPayloadStore
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/** The production adapter's behaviour for a value of the wrong type: it throws (PreviewComposerPrefs `error(...)`). */
private class WrongTypePrefs(private val inner: MemoryComposerPrefs, private val wrong: String) : ComposerPrefs by inner {
    override fun keys(): Set<String> = inner.keys() + wrong

    override fun string(key: String): String? = if (key == wrong) error("unreadable composer preference") else inner.string(key)
}

class ShelfPrefsTest {
    private val profiles =
        object : ComposerProfiles {
            override fun exists(profileId: String) = true

            override fun count() = 1
        }

    @Test
    fun `a Boolean where a saved prompt should be reads as an unreadable shelf, never a crash`() {
        val prefs = WrongTypePrefs(MemoryComposerPrefs(), "oc.promptStash.p1.1")
        val shelf = PromptShelf(ShelfPrefs(prefs), AttachmentVault(MemoryPayloadStore()), profiles, Redactor())
        assertIs<Outcome.Failed>(shelf.stashes("p1"))
        // Without the guard the production adapter's error escapes the page's load.
        val raw = PromptShelf(prefs, AttachmentVault(MemoryPayloadStore()), profiles, Redactor())
        assertFailsWith<IllegalStateException> { raw.stashes("p1") }
    }

    @Test
    fun `keys outside the shelf keep the adapter's own behaviour`() {
        val prefs = ShelfPrefs(WrongTypePrefs(MemoryComposerPrefs(), "oc.draft.note.s1.p1"))
        assertFailsWith<IllegalStateException> { prefs.string("oc.draft.note.s1.p1") }
    }
}
