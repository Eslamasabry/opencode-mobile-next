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
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.cache.CacheShelf
import io.github.eslamasabry.oc.core.data.cache.FileLastKnown
import io.github.eslamasabry.oc.core.data.cache.LastKnown
import io.github.eslamasabry.oc.core.data.cache.ServerMark
import io.github.eslamasabry.oc.engine.profiles.DeletionOptions
import io.github.eslamasabry.oc.engine.profiles.DeletionStep
import io.github.eslamasabry.oc.engine.profiles.ProfileResourceCleaner
import io.github.eslamasabry.oc.engine.profiles.StoredProfile
import io.github.eslamasabry.oc.engine.transcript.KeptTranscript
import io.github.eslamasabry.oc.engine.transcript.TranscriptHandle
import io.github.eslamasabry.oc.engine.transcript.detachedTranscript
import io.github.eslamasabry.oc.engine.transcript.state.MessageWithParts
import io.github.eslamasabry.oc.feature.chat.world.ChatLastKnown
import io.github.eslamasabry.oc.feature.projects.presentation.FolderRowUi
import io.github.eslamasabry.oc.feature.projects.presentation.KeptFolderList
import io.github.eslamasabry.oc.feature.projects.presentation.KeptPageRows
import io.github.eslamasabry.oc.feature.projects.presentation.KeptProjectList
import io.github.eslamasabry.oc.feature.projects.presentation.KeptProjectPage
import io.github.eslamasabry.oc.feature.projects.presentation.NoProjectsLastKnown
import io.github.eslamasabry.oc.feature.projects.presentation.OpenFolderUi
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectsLastKnown
import dev.zacsweers.metro.ContributesBinding
import kotlin.time.Clock
import kotlinx.io.files.Path

/*
 * Cache-first (owner rule, 2026-11-09): the one last-known store. Every screen that reads a server keeps what it
 * showed on a shelf here and draws it first next time; see docs/native/qa/cache-first-2026-11-09/README.md.
 * The store lives in the app's cache folder: it is only a cache (losing it costs one cold load), it is never backed
 * up, and the whole profile's part goes with the profile (LastKnownDeletion).
 */
@BindingContainer
@ContributesTo(AppScope::class)
object LastKnownWiring {
    @Provides
    @SingleIn(AppScope::class)
    fun lastKnown(app: Application, dispatchers: AppDispatchers, clock: Clock): LastKnown =
        FileLastKnown(Path(app.cacheDir.absolutePath, "last-known"), dispatchers.io, clock)
}

/** Which server a profile's data came from: data read from another address, user, backend or folder never shows. */
internal fun StoredProfile.lastKnownMark(): ServerMark =
    ServerMark.of(backend.name, baseUrl, username, codexDirectory)

/**
 * One shelf for a feature's port: entries keyed by profile (and an item, such as a session id or a folder path), the
 * server mark looked up from the saved profile, so data read from another server never shows. A profile that no
 * longer exists keeps and shows nothing. Every cache-first port in the app is a few lines over one of these.
 */
class ProfileShelf<T>(private val store: LastKnown, private val stores: PreviewStores, val shelf: CacheShelf<T>) {
    private fun mark(profile: ProfileId): ServerMark? = stores.profiles.profile(profile)?.profile?.lastKnownMark()

    /** Memory only, for a first frame. */
    fun peek(profile: ProfileId, item: String = ""): T? = mark(profile)?.let { store.peek(profile, it, shelf, item) }

    suspend fun read(profile: ProfileId, item: String = ""): T? =
        mark(profile)?.let { store.read(profile, it, shelf, item) }

    suspend fun keep(profile: ProfileId, item: String = "", value: T) {
        val mark = mark(profile) ?: return
        store.write(profile, mark, shelf, item, value)
    }

    /** Loads the newest entries into memory, so the next screen's first frame needs no read. */
    suspend fun warm(profile: ProfileId) {
        mark(profile)?.let { store.warm(profile, it, shelf) }
    }
}

/** Deleting a profile deletes everything the last-known store kept for it (AGENTS.md rule 9). */
@ContributesIntoSet(AppScope::class, binding = binding<ProfileResourceCleaner>())
@Inject
class LastKnownDeletion(private val store: LastKnown) : ProfileResourceCleaner {
    override val owner: String = ":core:data (last-known server data)"
    override val order: Int = DeletionStep.DEVICE_SURFACES

    override suspend fun clear(profileId: ProfileId, options: DeletionOptions): Outcome<Int> = store.clear(profileId)
}

/** A conversation's last page, kept per profile and server; 30 conversations, half a megabyte each at most. */
@ContributesIntoSet(AppScope::class, binding = binding<ChatLastKnown>())
@SingleIn(AppScope::class)
@Inject
class AppChatLastKnown(
    store: LastKnown,
    stores: PreviewStores,
    private val dispatchers: AppDispatchers,
    private val clock: Clock,
    private val appScope: AppCoroutineScope,
) : ChatLastKnown {
    private val pages = ProfileShelf(store, stores, TRANSCRIPTS)

    override fun peek(ref: ConversationRef): KeptTranscript? = pages.peek(ref.profileId, ref.sessionId.raw)

    override suspend fun read(ref: ConversationRef): KeptTranscript? = pages.read(ref.profileId, ref.sessionId.raw)

    override suspend fun keep(ref: ConversationRef, title: String?, messages: List<MessageWithParts>) {
        val kept = KeptTranscript.fit(title, messages, TRANSCRIPTS.maxBytes - ENVELOPE) ?: return
        pages.keep(ref.profileId, ref.sessionId.raw, kept)
    }

    override fun detached(ref: ConversationRef): TranscriptHandle =
        detachedTranscript(ref.sessionId.raw, dispatchers, clock, appScope)

    companion object {
        val TRANSCRIPTS = CacheShelf("transcript", KeptTranscript.serializer(), maxBytes = 512 * 1024, maxEntries = 30)
        private const val ENVELOPE = 256
    }
}

/**
 * The projects pages' last rows (picker group, project page, open-project listings), kept per profile and server.
 * The kept shapes live in :feature:projects (the serialization plugin is not applied in :androidApp).
 */
@ContributesBinding(AppScope::class, replaces = [NoProjectsLastKnown::class])
@SingleIn(AppScope::class)
@Inject
class AppProjectsLastKnown(store: LastKnown, stores: PreviewStores) : ProjectsLastKnown {
    private val projects = ProfileShelf(store, stores, PROJECTS)
    private val pages = ProfileShelf(store, stores, PAGES)
    private val folders = ProfileShelf(store, stores, FOLDERS)

    private fun key(path: String?) = path ?: "@projects"

    override fun peekProjects(profile: ProfileId): List<FolderRowUi>? = projects.peek(profile)?.toRows()

    override suspend fun readProjects(profile: ProfileId): List<FolderRowUi>? = projects.read(profile)?.toRows()

    override suspend fun keepProjects(profile: ProfileId, rows: List<FolderRowUi>) =
        projects.keep(profile, value = KeptProjectList.of(rows.take(MAX_ROWS)))

    override fun peekPage(profile: ProfileId, directory: String): KeptProjectPage? =
        pages.peek(profile, directory)?.toPage(profile)

    override suspend fun readPage(profile: ProfileId, directory: String): KeptProjectPage? =
        pages.read(profile, directory)?.toPage(profile)

    override suspend fun keepPage(profile: ProfileId, directory: String, page: KeptProjectPage) =
        pages.keep(
            profile,
            directory,
            KeptPageRows.of(KeptProjectPage(page.conversations.take(MAX_ROWS), page.elsewhere.take(MAX_ROWS))),
        )

    override fun peekFolders(profile: ProfileId, path: String?): List<OpenFolderUi>? =
        folders.peek(profile, key(path))?.toFolders()

    override suspend fun readFolders(profile: ProfileId, path: String?): List<OpenFolderUi>? =
        folders.read(profile, key(path))?.toFolders()

    override suspend fun keepFolders(profile: ProfileId, path: String?, folders: List<OpenFolderUi>) =
        this.folders.keep(profile, key(path), KeptFolderList.of(folders.take(MAX_ROWS)))

    companion object {
        const val MAX_ROWS = 200
        val PROJECTS = CacheShelf("projects", KeptProjectList.serializer(), maxBytes = 128 * 1024, maxEntries = 1)
        val PAGES = CacheShelf("projectPage", KeptPageRows.serializer(), maxBytes = 128 * 1024, maxEntries = 40)
        val FOLDERS = CacheShelf("folder", KeptFolderList.serializer(), maxBytes = 64 * 1024, maxEntries = 40)
    }
}
