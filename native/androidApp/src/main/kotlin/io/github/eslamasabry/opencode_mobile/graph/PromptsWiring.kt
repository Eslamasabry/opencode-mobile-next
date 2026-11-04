package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.composer.stash.PromptShelf
import io.github.eslamasabry.oc.engine.composer.store.ComposerPrefs
import io.github.eslamasabry.oc.engine.composer.vault.AttachmentVault
import io.github.eslamasabry.oc.engine.composer.vault.FilePayloadStore
import io.github.eslamasabry.oc.engine.composer.vault.PayloadStore
import io.github.eslamasabry.oc.engine.profiles.DeletionOptions
import io.github.eslamasabry.oc.engine.profiles.DeletionStep
import io.github.eslamasabry.oc.engine.profiles.ProfileCloser
import io.github.eslamasabry.oc.engine.profiles.ProfileResourceCleaner
import io.github.eslamasabry.oc.feature.prompts.presentation.SavedPromptsStore
import io.github.eslamasabry.oc.feature.prompts.presentation.ShelfReady
import io.github.eslamasabry.oc.feature.prompts.presentation.ShelfSavedPrompts
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlinx.io.files.Path

/**
 * P12-PROMPTS Android bindings: the composer engine's PromptShelf over the app's preference store
 * and the `prompt-stash-attachments-v1` vault under files/, the same keys and folder the Flutter
 * app uses, so a migrated shelf reads unchanged.
 *
 * Profile deletion: the preview's deletion command (PreviewRuntime) does not call the shelf yet, so
 * every shelf use first retires what a removed server left behind: its remaining shelf keys
 * (PromptShelf.clearForProfile, which also closes admission for that id) and stash files whose
 * owner is no live server. Integrator: call `PromptShelf.clearForProfile` inside the deletion
 * command, before the secret goes (B-SEC-172).
 */
@BindingContainer
@ContributesTo(AppScope::class)
object PromptsBindings {
    @Provides
    @SingleIn(AppScope::class)
    fun stashFiles(application: Application, dispatchers: AppDispatchers): StashPayloads =
        StashPayloads(
            FilePayloadStore(
                Path(application.filesDir.absolutePath, AttachmentVault.STASH_ROOT),
                dispatchers.io,
            )
        )

    @Provides
    @SingleIn(AppScope::class)
    fun promptShelf(stores: PreviewStores, files: StashPayloads, redactor: Redactor): PromptShelf =
        PromptShelf(
            ShelfPrefs(PreviewComposerPrefs(stores)),
            AttachmentVault(files.store),
            PreviewComposerProfiles(stores),
            redactor,
        )

    @Provides
    @SingleIn(AppScope::class)
    fun savedPrompts(
        stores: PreviewStores,
        shelf: PromptShelf,
        files: StashPayloads,
        clock: Clock,
    ): SavedPromptsStore =
        ShelfSavedPrompts(shelf, PreviewShelfReady(stores, shelf, files.store), clock)
}

/**
 * Profile deletion, step 6 (plan 05 §3.5; B-SEC-172, B-STATE-140): the removed server's saved
 * prompts, their attachment files and the owner marker go before the generic sweep. If anything
 * cannot be deleted the server is kept (the sweep is guarded, so the marker that finds orphan files
 * stays) and its shelf opens again.
 */
@ContributesIntoSet(AppScope::class, binding = binding<ProfileResourceCleaner>())
@Inject
class StashDeletionCleaner(private val shelf: PromptShelf) : ProfileResourceCleaner {
    override val owner: String = ":engine:composer (saved prompts)"
    override val order: Int = DeletionStep.STASH
    override val guardsSweep: Boolean = true

    override suspend fun clear(profileId: ProfileId, options: DeletionOptions): Outcome<Int> {
        if (shelf.clearForProfile(profileId.raw)) return 1.ok()
        return Failure.StorageRefused().failed()
    }
}

/** Fence shelf commands with the other profile owners before any deletion work can yield. */
@ContributesIntoSet(AppScope::class, binding = binding<ProfileCloser>())
@Inject
class StashDeletionCloser(private val shelf: PromptShelf) : ProfileCloser {
    override val owner: String = ":engine:composer (saved prompts)"

    override fun stopAdmission(profile: ProfileId) = shelf.stopAdmission(profile.raw)

    override suspend fun drain(profile: ProfileId): Outcome<Unit> {
        shelf.drainProfile(profile.raw)
        return Unit.ok()
    }

    override fun reopen(profile: ProfileId) = shelf.reopenProfile(profile.raw)
}

/**
 * The shelf's view of the preference file: an adopted shelf record of the wrong type (a Boolean
 * where Flutter wrote JSON text) reads as an unreadable shelf, named on the page with Retry,
 * instead of throwing out of the page's load (Codex prompts #7; Flutter catches the read failure).
 * Other keys behave exactly as [inner].
 */
internal class ShelfPrefs(private val inner: ComposerPrefs) : ComposerPrefs by inner {
    override fun string(key: String): String? =
        try {
            inner.string(key)
        } catch (e: IllegalStateException) {
            if (SHELF_PREFIXES.none { key.startsWith(it) }) throw e
            UNREADABLE // not JSON: the shelf's own parser reports the record unreadable
        }

    override fun bool(key: String): Boolean? =
        try {
            inner.bool(key)
        } catch (e: IllegalStateException) {
            if (SHELF_PREFIXES.none { key.startsWith(it) }) throw e
            null
        }

    private companion object {
        const val UNREADABLE = "\u0000"
    }
}

/** The stash vault's file store, typed so it is never confused with the draft vault's. */
class StashPayloads(val store: PayloadStore)

private val SHELF_PREFIXES =
    listOf("oc.promptStash.", "oc.promptHistory.", "oc.stashAttachmentVault.")

/** Warms the stores, then retires shelf data of servers that are gone. */
internal class PreviewShelfReady(
    private val stores: PreviewStores,
    private val shelf: PromptShelf,
    private val files: PayloadStore,
) : ShelfReady {
    override suspend fun prepare(): Outcome<Unit> {
        val warmed = stores.warm()
        if (warmed is Outcome.Failed) return warmed
        retireRemovedServers()
        return Unit.ok()
    }

    private suspend fun retireRemovedServers() {
        val left =
            stores.prefs
                .keys()
                .mapNotNull { key ->
                    SHELF_PREFIXES.firstOrNull { key.startsWith(it) }
                        ?.let { key.removePrefix(it).substringBefore('.') }
                }
                .filter { it.isNotEmpty() }
                .toSet()
        val liveIds = stores.profiles.profiles.value.map { it.id.raw }
        for (id in left) {
            // A dotted live id ("a.b") also starts with "a."; never touch a live server's records.
            val removed =
                stores.profiles.profile(ProfileId(id)) == null &&
                    liveIds.none { it == id || it.startsWith("$id.") }
            if (removed) shelf.clearForProfile(id)
        }
        val live = liveIds.map { AttachmentVault.ownerKey(it) }.toSet()
        // Best effort: a file that cannot be listed or deleted now is retried on the next use.
        val entries =
            try {
                files.list(null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return
            }
        for (entry in entries) {
            if (entry.ownerKey !in live) {
                try {
                    files.delete(entry.ownerKey, entry.id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Kept for the next pass.
                }
            }
        }
    }
}
