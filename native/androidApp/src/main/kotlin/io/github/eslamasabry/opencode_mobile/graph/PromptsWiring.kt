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
import io.github.eslamasabry.oc.engine.composer.drafts.Drafts
import io.github.eslamasabry.oc.engine.composer.stash.PromptShelf
import io.github.eslamasabry.oc.engine.composer.store.ComposerPrefs
import io.github.eslamasabry.oc.engine.composer.vault.AttachmentVault
import io.github.eslamasabry.oc.engine.composer.vault.FilePayloadStore
import io.github.eslamasabry.oc.engine.composer.vault.PayloadStore
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.profiles.DeletionOptions
import io.github.eslamasabry.oc.engine.profiles.DeletionStep
import io.github.eslamasabry.oc.engine.profiles.ProfileCloser
import io.github.eslamasabry.oc.engine.profiles.ProfileResourceCleaner
import io.github.eslamasabry.oc.feature.prompts.presentation.SavedPromptsStore
import io.github.eslamasabry.oc.feature.prompts.presentation.ShelfReady
import io.github.eslamasabry.oc.feature.prompts.presentation.ShelfSavedPrompts
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlinx.coroutines.sync.withLock
import kotlinx.io.files.Path

/**
 * P12-PROMPTS Android bindings: the composer engine's PromptShelf over the app's preference store
 * and the `prompt-stash-attachments-v1` vault under files/, the same keys and folder the Flutter
 * app uses, so a migrated shelf reads unchanged.
 *
 * Profile deletion calls StashDeletionCleaner before the generic preference sweep. Shelf
 * preparation also retries cleanup left by an interrupted deletion and migrates older unsent work
 * in the ordinary draft writer's lane.
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
        drafts: Drafts,
        redactor: Redactor,
        registry: ConnectionRegistry,
    ): SavedPromptsStore =
        ShelfSavedPrompts(
            shelf,
            PreviewShelfReady(stores, shelf, files.store, drafts, redactor, registry),
            clock,
        )
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
    private val drafts: Drafts,
    private val redactor: Redactor,
    private val registry: ConnectionRegistry,
) : ShelfReady {
    private val preparation = kotlinx.coroutines.sync.Mutex()
    private val pending = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    override fun migrationPending(profile: String): Boolean = profile in pending

    override fun sameLocation(profile: String, directory: String?, workspace: String?): Boolean {
        val place =
            registry.live.value[ProfileId(profile)]?.session?.server?.location?.value
                ?: return false
        return place.directory == directory && place.workspace == workspace
    }

    override suspend fun prepareProfile(profile: String): Outcome<Unit> = preparation.withLock {
        when (val prepared = prepare()) {
            is Outcome.Failed -> return@withLock prepared
            is Outcome.Ok -> Unit
        }
        return@withLock try {
            val migrated = drafts.migrateOlder(profile, shelf, redactor)
            val deferred = shelf.migrateAttachments(profile)
            if (!migrated.complete || deferred.isNotEmpty()) pending += profile
            else pending -= profile
            // A full shelf must remain readable so the person can remove an item, then retry the
            // older drafts.
            Unit.ok()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            pending += profile
            Failure.StorageRefused().failed()
        }
    }

    override suspend fun prepare(): Outcome<Unit> {
        val warmed = stores.warm()
        if (warmed is Outcome.Failed) return warmed
        retireRemovedServers()
        return Unit.ok()
    }

    private suspend fun retireRemovedServers() {
        // Only an acknowledged owner marker authorizes vault cleanup. Never scan/delete unmarked
        // files, and an
        // empty/text-only shelf performs no file I/O. The complete suffix preserves dotted
        // historical profile ids.
        val ownerPrefix = "oc.stashAttachmentVault."
        val owners = stores.prefs.keys(ownerPrefix).map { it.removePrefix(ownerPrefix) }
        for (id in owners) {
            if (id.isNotEmpty() && stores.profiles.profile(ProfileId(id)) == null)
                shelf.clearForProfile(id)
        }
    }
}
