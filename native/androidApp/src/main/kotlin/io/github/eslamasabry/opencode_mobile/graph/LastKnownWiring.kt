package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesBinding
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
import io.github.eslamasabry.oc.core.common.pace.Visibility
import io.github.eslamasabry.oc.core.data.cache.CacheShelf
import io.github.eslamasabry.oc.core.data.cache.FileLastKnown
import io.github.eslamasabry.oc.core.data.cache.LastKnown
import io.github.eslamasabry.oc.core.data.cache.ServerMark
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedSnapshot
import io.github.eslamasabry.oc.engine.git.GitLastKnown
import io.github.eslamasabry.oc.engine.git.KeptGit
import io.github.eslamasabry.oc.engine.profiles.DeletionOptions
import io.github.eslamasabry.oc.engine.profiles.DeletionStep
import io.github.eslamasabry.oc.engine.profiles.LoadedProfile
import io.github.eslamasabry.oc.engine.profiles.ProfileResourceCleaner
import io.github.eslamasabry.oc.engine.profiles.ProfileStore
import io.github.eslamasabry.oc.engine.profiles.StoredProfile
import io.github.eslamasabry.oc.engine.runtime.terminal.KeptTerminals
import io.github.eslamasabry.oc.engine.runtime.terminal.TerminalLastKnown
import io.github.eslamasabry.oc.engine.team.KeptTeam
import io.github.eslamasabry.oc.engine.team.TeamLastKnown
import io.github.eslamasabry.oc.engine.transcript.KeptTranscript
import io.github.eslamasabry.oc.engine.transcript.TranscriptHandle
import io.github.eslamasabry.oc.engine.transcript.detachedTranscript
import io.github.eslamasabry.oc.feature.chat.world.ChatLastKnown
import io.github.eslamasabry.oc.feature.projects.presentation.FolderRowUi
import io.github.eslamasabry.oc.feature.projects.presentation.KeptFolderList
import io.github.eslamasabry.oc.feature.projects.presentation.KeptPageRows
import io.github.eslamasabry.oc.feature.projects.presentation.KeptProjectList
import io.github.eslamasabry.oc.feature.projects.presentation.KeptProjectPage
import io.github.eslamasabry.oc.feature.projects.presentation.NoProjectsLastKnown
import io.github.eslamasabry.oc.feature.projects.presentation.OpenFolderUi
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectsLastKnown
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Clock
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

/** Deleting a profile deletes everything the last-known store kept for it and its helper (AGENTS.md rule 9). */
@ContributesIntoSet(AppScope::class, binding = binding<ProfileResourceCleaner>())
@Inject
class LastKnownDeletion(
    private val store: LastKnown,
    private val stores: PreviewStores,
) : ProfileResourceCleaner {
    override val owner: String = ":core:data (last-known server data)"
    override val order: Int = DeletionStep.DEVICE_SURFACES

    override suspend fun clear(profileId: ProfileId, options: DeletionOptions): Outcome<Int> =
        clearProfileAndHelpers(store, stores.profiles, stores.prefs, profileId)
}

internal suspend fun clearProfileAndHelpers(
    store: LastKnown,
    profiles: ProfileStore?,
    prefs: KeyValueStore?,
    profileId: ProfileId,
): Outcome<Int> {
    val helpers =
        if (profiles != null && prefs != null) helperIdsForPhone(profiles, prefs, profileId)
        else emptyList()
    var total =
        when (val own = store.clear(profileId)) {
            is Outcome.Failed -> return own
            is Outcome.Ok -> own.value
        }
    for (helper in helpers) {
        when (val cleared = store.clear(helper)) {
            is Outcome.Failed -> return cleared
            is Outcome.Ok -> total += cleared.value
        }
    }
    return Outcome.Ok(total)
}

internal suspend fun sweepPhoneAgentLastKnown(
    store: LastKnown,
    stores: PreviewStores,
    owner: ProfileId? = null,
): Outcome<Int> = sweepPhoneAgentLastKnown(store, stores.profiles, stores.prefs, owner)

internal suspend fun sweepPhoneAgentLastKnown(
    store: LastKnown,
    profiles: ProfileStore,
    prefs: KeyValueStore,
    owner: ProfileId? = null,
): Outcome<Int> {
    val helpers = LinkedHashSet<ProfileId>()
    owner?.let { helpers += ProfileId("${it.raw}-agents") }
    for (loaded in profiles.profiles.value) {
        if (isHelperLoaded(loaded, prefs)) helpers += loaded.id
    }
    for (key in prefs.keys("oc.phoneAgentOwner.")) {
        helpers += ProfileId(key.removePrefix("oc.phoneAgentOwner."))
    }
    var total = 0
    for (helper in helpers) {
        when (val cleared = store.clear(helper)) {
            is Outcome.Failed -> return cleared
            is Outcome.Ok -> total += cleared.value
        }
    }
    return Outcome.Ok(total)
}

private fun helperIdsForPhone(
    profiles: ProfileStore,
    prefs: KeyValueStore,
    phoneId: ProfileId,
): List<ProfileId> {
    val saved = profiles.profiles.value.map { it.id.raw }
    val savedSet = saved.toSet()
    val builtinOwner = (prefs.get("oc.builtinServerOwner") as? PrefValue.Str)?.value
    return PhoneAgentRules.removedWith(phoneId.raw, saved) { id ->
            val savedOwner = (prefs.get("oc.phoneAgentOwner.$id") as? PrefValue.Str)?.value
            val isHelper =
                profiles.profile(ProfileId(id))?.profile?.backend == BackendKind.Paseo &&
                    PhoneAgentRules.isHelperProfile(id, savedOwner)
            if (!isHelper) null
            else PhoneAgentRules.phoneOf(id, savedOwner, builtinOwner, savedSet)
        }
        .map(::ProfileId)
}

private fun isHelperLoaded(loaded: LoadedProfile, prefs: KeyValueStore): Boolean =
    loaded.profile.backend == BackendKind.Paseo &&
        PhoneAgentRules.isHelperProfile(
            loaded.id.raw,
            (prefs.get("oc.phoneAgentOwner.${loaded.id.raw}") as? PrefValue.Str)?.value,
        )

/**
 * Per-profile kept transcript pages: 30 entries for an ordinary server, and for the phone agents' helper sized from
 * the number of phone-agent conversations with a hard cap of [AppChatLastKnown.MAX_HELPER_ENTRIES] (200) so thousands
 * of conversations never fill the disk.
 */
internal class TranscriptLastKnownStore(
    private val store: LastKnown,
    private val profiles: ProfileStore,
    private val prefs: KeyValueStore,
    private val ensureWarm: suspend () -> Unit = {},
    private val inventoryCount: (LoadedProfile) -> Int,
) {
    private val liveCounts = ConcurrentHashMap<ProfileId, Int>()

    fun noteConversationCount(profile: ProfileId, count: Int) {
        liveCounts[profile] = count
    }

    private fun mark(profile: ProfileId): ServerMark? =
        profiles.profile(profile)?.profile?.lastKnownMark()

    internal fun shelfFor(profile: ProfileId): CacheShelf<KeptTranscript> {
        val loaded = profiles.profile(profile) ?: return AppChatLastKnown.TRANSCRIPTS
        if (!isHelperLoaded(loaded, prefs)) return AppChatLastKnown.TRANSCRIPTS
        val known = maxOf(liveCounts[profile] ?: 0, inventoryCount(loaded))
        val entries = AppChatLastKnown.helperMaxEntries(known)
        return if (entries == AppChatLastKnown.TRANSCRIPTS.maxEntries) AppChatLastKnown.TRANSCRIPTS
        else
            CacheShelf(
                AppChatLastKnown.TRANSCRIPTS.name,
                KeptTranscript.serializer(),
                maxBytes = AppChatLastKnown.TRANSCRIPTS.maxBytes,
                maxEntries = entries,
            )
    }

    fun peek(ref: ConversationRef): KeptTranscript? =
        mark(ref.profileId)?.let { store.peek(ref.profileId, it, shelfFor(ref.profileId), ref.sessionId.raw) }

    suspend fun read(ref: ConversationRef): KeptTranscript? {
        if (profiles.profile(ref.profileId) == null) {
            ensureWarm()
            if (profiles.profile(ref.profileId) == null) profiles.load()
        }
        return mark(ref.profileId)?.let { store.read(ref.profileId, it, shelfFor(ref.profileId), ref.sessionId.raw) }
    }

    suspend fun keep(ref: ConversationRef, title: String?, messages: List<MessageWithParts>) {
        val mark = mark(ref.profileId) ?: return
        val shelf = shelfFor(ref.profileId)
        val kept = KeptTranscript.fit(title, messages, shelf.maxBytes - AppChatLastKnown.ENVELOPE) ?: return
        store.write(ref.profileId, mark, shelf, ref.sessionId.raw, kept)
    }
}

/** A conversation's last page, kept per profile and server; 30 conversations for servers, sized for phone agents. */
@ContributesIntoSet(AppScope::class, binding = binding<ChatLastKnown>())
@SingleIn(AppScope::class)
@Inject
class AppChatLastKnown(
    store: LastKnown,
    stores: PreviewStores,
    inventory: NativeSessionInventory,
    private val dispatchers: AppDispatchers,
    private val clock: Clock,
    private val appScope: AppCoroutineScope,
) : ChatLastKnown {
    private val pages =
        TranscriptLastKnownStore(
            store,
            stores.profiles,
            stores.prefs,
            ensureWarm = { stores.warm() },
        ) { loaded ->
            inventory.read(loaded.profile).size
        }

    internal fun noteConversationCount(profile: ProfileId, count: Int) {
        pages.noteConversationCount(profile, count)
    }

    override fun peek(ref: ConversationRef): KeptTranscript? = pages.peek(ref)

    override suspend fun read(ref: ConversationRef): KeptTranscript? = pages.read(ref)

    override suspend fun keep(ref: ConversationRef, title: String?, messages: List<MessageWithParts>) =
        pages.keep(ref, title, messages)

    override fun detached(ref: ConversationRef): TranscriptHandle =
        detachedTranscript(ref.sessionId.raw, dispatchers, clock, appScope)

    companion object {
        const val DEFAULT_ENTRIES: Int = 30
        const val HELPER_HEADROOM: Int = 10
        const val MAX_HELPER_ENTRIES: Int = CacheShelf.MAX_SHELF_ENTRIES
        const val ENVELOPE: Int = 256
        val TRANSCRIPTS =
            CacheShelf("transcript", KeptTranscript.serializer(), maxBytes = 512 * 1024, maxEntries = DEFAULT_ENTRIES)

        internal fun helperMaxEntries(conversationCount: Int): Int =
            maxOf(DEFAULT_ENTRIES, conversationCount + HELPER_HEADROOM).coerceAtMost(MAX_HELPER_ENTRIES)
    }
}

@ContributesTo(AppScope::class)
interface HomeTranscriptWarmerAccessors {
    val homeTranscriptWarmer: HomeTranscriptWarmer
}

/**
 * Reads the newest Home rows' kept pages into memory once the app is in front, so opening any top row is a memory
 * peek instead of a disk read.
 */
@SingleIn(AppScope::class)
@Inject
class HomeTranscriptWarmer(
    private val runtime: PreviewRuntime,
    private val visibility: AppVisibility,
    private val lastKnown: AppChatLastKnown,
    private val inventory: NativeSessionInventory,
    private val appScope: AppCoroutineScope,
) {
    private val started = AtomicBoolean(false)
    private val serial = Mutex()
    private val attempted = HashSet<ConversationRef>()

    fun start() {
        if (!started.compareAndSet(false, true)) return
        appScope.launch {
            combine(visibility.state, runtime.snapshot, runtime.profiles.profiles) { vis, feed, saved ->
                    Triple(vis, feed, saved)
                }
                .conflate()
                .collect { (vis, feed, saved) ->
                    updateHelperCounts(feed, saved)
                    if (vis != Visibility.Foreground) {
                        serial.withLock { attempted.clear() }
                        return@collect
                    }
                    val top = topHomeRefs(feed, saved, runtime::listSourceShown, inventory::read)
                    warmTopHomeRefs(top, lastKnown, attempted, serial) {
                        visibility.state.value == Visibility.Foreground
                    }
                }
        }
    }

    private fun updateHelperCounts(feed: ChatFeedSnapshot, saved: List<LoadedProfile>) {
        for (loaded in saved) {
            if (!runtime.isPhoneHelper(loaded.id)) continue
            val liveCount = feed.items.count { it.profileId == loaded.id && !it.isSubagent }
            if (liveCount > 0) lastKnown.noteConversationCount(loaded.id, liveCount)
        }
    }

    internal companion object {
        // 10 * 512 KiB maxBytes = 5 MiB < FileLastKnown.DEFAULT_MEMORY_BUDGET (6 MiB): never evicts itself on budget.
        const val WARM_TOP_HOME_ROWS: Int = 10

        internal fun topHomeRefs(
            feed: ChatFeedSnapshot,
            saved: List<LoadedProfile>,
            isShown: (ProfileId) -> Boolean,
            readInventory: (StoredProfile) -> List<Session>,
            limit: Int = WARM_TOP_HOME_ROWS,
        ): List<ConversationRef> {
            val ranked = ArrayList<Pair<ConversationRef, Long>>()
            val seenProfiles = HashSet<ProfileId>()
            for (item in feed.items) {
                if (item.isSubagent) continue
                seenProfiles += item.profileId
                ranked += item.ref to item.lastActivityMs
            }
            for (loaded in saved) {
                if (loaded.id in seenProfiles || !isShown(loaded.id)) continue
                for (session in readInventory(loaded.profile)) {
                    val at = session.time?.updatedMs ?: session.time?.createdMs ?: 0L
                    ranked += ConversationRef(loaded.id, session.id) to at
                }
            }
            return ranked
                .sortedByDescending { it.second }
                .map { it.first }
                .distinct()
                .take(limit)
        }

        internal suspend fun warmTopHomeRefs(
            top: List<ConversationRef>,
            lastKnown: ChatLastKnown,
            attempted: MutableSet<ConversationRef>,
            serial: Mutex,
            isForeground: () -> Boolean,
        ) {
            val pending =
                serial.withLock {
                    top.asReversed().filter { ref ->
                        if (lastKnown.peek(ref) != null) {
                            attempted += ref
                            false
                        } else {
                            attempted.add(ref)
                        }
                    }
                }
            for (ref in pending) {
                if (!isForeground()) break
                lastKnown.read(ref)
            }
        }
    }
}

/** The Git page's last snapshot, kept per profile and server and conversation; 20 per profile, 128 KB each at most. */
@ContributesIntoSet(AppScope::class, binding = binding<GitLastKnown>())
@SingleIn(AppScope::class)
@Inject
class AppGitLastKnown(store: LastKnown, stores: PreviewStores) : GitLastKnown {
    private val pages = ProfileShelf(store, stores, GIT)

    override fun peek(ref: ConversationRef): KeptGit? = pages.peek(ref.profileId, ref.sessionId.raw)

    override suspend fun read(ref: ConversationRef): KeptGit? = pages.read(ref.profileId, ref.sessionId.raw)

    override suspend fun keep(ref: ConversationRef, kept: KeptGit) = pages.keep(ref.profileId, ref.sessionId.raw, kept)

    companion object {
        val GIT = CacheShelf("git", KeptGit.serializer(), maxBytes = 128 * 1024, maxEntries = 20)
    }
}

/** The terminal list last seen, kept per profile and server and conversation. */
@ContributesIntoSet(AppScope::class, binding = binding<TerminalLastKnown>())
@SingleIn(AppScope::class)
@Inject
class AppTerminalLastKnown(store: LastKnown, stores: PreviewStores) : TerminalLastKnown {
    private val lists = ProfileShelf(store, stores, TERMINALS)

    override fun peek(ref: ConversationRef): KeptTerminals? = lists.peek(ref.profileId, ref.sessionId.raw)

    override suspend fun read(ref: ConversationRef): KeptTerminals? = lists.read(ref.profileId, ref.sessionId.raw)

    override suspend fun keep(ref: ConversationRef, kept: KeptTerminals) = lists.keep(ref.profileId, ref.sessionId.raw, kept)

    companion object {
        val TERMINALS = CacheShelf("terminals", KeptTerminals.serializer(), maxBytes = 32 * 1024, maxEntries = 20)
    }
}

/** The AI Team's workspace (projects, boards, tasks) last read, per profile and server; 200 KB at most. */
@ContributesIntoSet(AppScope::class, binding = binding<TeamLastKnown>())
@SingleIn(AppScope::class)
@Inject
class AppTeamLastKnown(store: LastKnown, stores: PreviewStores) : TeamLastKnown {
    private val spaces = ProfileShelf(store, stores, TEAM)

    override fun peek(profileId: String): KeptTeam? = spaces.peek(ProfileId(profileId))

    override suspend fun read(profileId: String): KeptTeam? = spaces.read(ProfileId(profileId))

    override suspend fun keep(profileId: String, kept: KeptTeam) = spaces.keep(ProfileId(profileId), value = kept)

    companion object {
        val TEAM = CacheShelf("team", KeptTeam.serializer(), maxBytes = 256 * 1024, maxEntries = 1)
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
