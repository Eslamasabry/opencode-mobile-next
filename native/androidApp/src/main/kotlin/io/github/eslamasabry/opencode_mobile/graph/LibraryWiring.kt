package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import dev.zacsweers.metro.*
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.domain.*
import io.github.eslamasabry.oc.core.model.*
import io.github.eslamasabry.oc.core.security.*
import io.github.eslamasabry.oc.engine.composer.drafts.ConversationDrafts
import io.github.eslamasabry.oc.engine.composer.drafts.DraftLocation
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.integrations.catalog.*
import io.github.eslamasabry.oc.engine.profiles.ProfileKeys
import io.github.eslamasabry.oc.feature.settings.presentation.LibraryHost
import io.github.eslamasabry.oc.feature.settings.presentation.LibraryLink
import java.io.File
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Real Tools binding, pinned to the chosen profile and exact project. No credentials enter
 * presentation.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<LibraryHost>())
class AndroidLibraryHost(
    private val application: Application,
    private val stores: PreviewStores,
    private val connections: ConnectionRegistry,
    private val dispatchers: AppDispatchers,
    private val clock: Clock,
    private val policy: ExternalLinkPolicy,
    private val opener: ExternalLinkOpener,
    private val drafts: Set<ConversationDrafts>,
    private val appScope: AppCoroutineScope,
    private val registryFactory: LibraryRegistryFactory,
) : LibraryHost {
    private data class ContextKey(
        val profile: ServerProfile,
        val location: ServerLocation,
        val origin: String,
    )

    private val sessions = mutableMapOf<ContextKey, LibrarySession>()
    private val recoveryLocks = mutableMapOf<ProfileId, Mutex>()

    override fun open(ref: ConversationRef?, scope: CoroutineScope): LibrarySession? {
        val profile =
            ref?.profileId
                ?: stores.profiles.activeId.value
                ?: connections.live.value.keys.firstOrNull()
                ?: return null
        return openProfile(profile, scope)
    }

    override fun openProfile(profile: ProfileId, scope: CoroutineScope): LibrarySession? {
        val first = connections.live.value[profile] ?: return null
        val captured = first.profile
        val capturedOrigin = stores.profiles.profile(profile)?.profile?.baseUrl ?: return null
        val pinnedLocation = first.session.server.location.value
        val context = ContextKey(captured.copy(name = ""), pinnedLocation, capturedOrigin)
        sessions[context]?.let {
            it.retain()
            return it
        }
        val lane = stores.lanes.forProfile(profile)
        val recoveryLock = recoveryLocks.getOrPut(profile) { Mutex() }
        fun profileCurrent(): Boolean =
            lane.isAdmitting &&
                stores.profiles.profile(profile)?.let {
                    it.toServerProfile(captured.managedOnPhone)?.copy(name = "") ==
                        captured.copy(name = "") && it.profile.baseUrl == capturedOrigin
                } == true
        fun sameSource(entry: LibraryAttempt): Boolean =
            entry.origin == capturedOrigin &&
                entry.directory == pinnedLocation.directory &&
                entry.workspace == pinnedLocation.workspace
        fun stored(): Outcome<String?> =
            when (val value = stores.prefs.get(ProfileKeys.PENDING_AUTH + profile.raw)) {
                null -> null.ok()
                is PrefValue.Str -> value.value.ok()
                else -> Failure.StorageRefused().failed()
            }
        val recovery =
            object : LibraryRecoveryStore {
                override suspend fun read(): Outcome<String?> = recoveryLock.withLock {
                    val ready = stores.warm()
                    if (ready is Outcome.Failed) return@withLock ready
                    if (!profileCurrent())
                        return@withLock Failure.Refused(RefusalReason.ProfileClosing).failed()
                    stored()
                }

                override suspend fun write(json: String): Outcome<Unit> = recoveryLock.withLock {
                    if (!profileCurrent())
                        return@withLock Failure.Refused(RefusalReason.ProfileClosing).failed()
                    val now = clock.now().toEpochMilliseconds()
                    val incoming =
                        LibraryRecoveryCodec.parse(json, profile.raw, now)
                            ?: return@withLock Failure.StorageRefused().failed()
                    if (incoming.any { !sameSource(it) })
                        return@withLock Failure.StorageRefused().failed()
                    val previous =
                        when (val read = stored()) {
                            is Outcome.Failed -> return@withLock read
                            is Outcome.Ok ->
                                if (read.value == null) emptyList()
                                else LibraryRecoveryCodec.parse(read.value!!, profile.raw, now)
                        } ?: return@withLock Failure.StorageRefused().failed()
                    // Other locations have independent serial owners; their acknowledged metadata
                    // must survive this write.
                    val merged = previous.filterNot(::sameSource) + incoming
                    val encoded = LibraryRecoveryCodec.encode(merged, profile.raw)
                    if (
                        LibraryRecoveryCodec.parse(encoded, profile.raw, now) == null ||
                            !profileCurrent()
                    )
                        return@withLock Failure.StorageRefused().failed()
                    stores.prefs.commit(lane) {
                        putString(ProfileKeys.PENDING_AUTH + profile.raw, encoded)
                    }
                }
            }
        return LibrarySession(
                source = {
                    val graph = connections.live.value[profile]
                    val operations = graph?.session?.server?.facet<ServerOperationsGateway>()
                    val saved = stores.profiles.profile(profile)
                    if (
                        graph == null ||
                            operations == null ||
                            saved == null ||
                            saved.toServerProfile(captured.managedOnPhone)?.copy(name = "") !=
                                captured.copy(name = "") ||
                            saved.profile.baseUrl != capturedOrigin ||
                            graph.profile.copy(name = "") != captured.copy(name = "") ||
                            graph.session.server.location.value != pinnedLocation
                    )
                        null
                    else
                        object : LibrarySource {
                            override val profile = captured.id
                            override val origin = capturedOrigin
                            override val location = pinnedLocation
                            override val capabilities
                                get() = graph.session.capabilities.value

                            override val server = graph.session.server
                            override val operations: ServerOperationsGateway =
                                requireNotNull(operations)

                            override fun current(): Boolean =
                                connections.live.value[profile] === graph &&
                                    profileCurrent() &&
                                    server.location.value == location
                        }
                },
                recovery,
                CoroutineScope(appScope.coroutineContext + dispatchers.main),
                clock,
            )
            .also {
                it.retain()
                sessions[context] = it
            }
    }

    override suspend fun registry(
        profile: ProfileId
    ): io.github.eslamasabry.oc.engine.setupassist.SetupRegistryStore? =
        registryFactory.open(profile)

    override fun closeRegistry(
        store: io.github.eslamasabry.oc.engine.setupassist.SetupRegistryStore
    ) {
        appScope.launch { store.close() }
    }

    override fun onPhone(ref: ConversationRef?): Boolean {
        val profile = ref?.profileId ?: stores.profiles.activeId.value ?: return false
        return connections.live.value[profile]?.profile?.managedOnPhone == true
    }

    override suspend fun copy(text: String): Outcome<Unit> =
        withContext(dispatchers.main) {
            attempt({ Failure.Device() }) {
                application
                    .getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("", text))
            }
        }

    override fun checkLink(url: String): LibraryLink? =
        when (val decision = policy.check(url, LinkOrigin.ServerContent)) {
            is LinkDecision.Open ->
                LibraryLink(
                    Uri.parse(decision.link.uri).let { uri ->
                        uri.host.orEmpty() + (if (uri.port != -1) ":${uri.port}" else "")
                    },
                    decision.link,
                )
            is LinkDecision.AskFirst -> LibraryLink(decision.shownHost, decision.link)
            is LinkDecision.Refuse -> null
        }

    override fun openLink(link: LibraryLink): Outcome<Unit> =
        (link.token as? ApprovedLink)?.let(opener::open) ?: Failure.UnreadableReply().failed()

    override suspend fun reference(ref: ConversationRef, reference: ReferenceInfo): Outcome<Unit> {
        val graph =
            connections.graphFor(ref) ?: return Failure.Refused(RefusalReason.StaleScope).failed()
        if (!graph.session.capabilities.value.fileBrowsing)
            return Failure.Unavailable(FeatureId("fileBrowsing")).failed()
        val owner =
            drafts.singleOrNull() ?: return Failure.Unavailable(FeatureId("drafts")).failed()
        val detail = graph.session.server.session(ref.sessionId)
        if (detail !is Outcome.Ok) return detail as Outcome.Failed
        if (connections.graphFor(ref) !== graph || stores.profiles.profile(ref.profileId) == null)
            return Failure.Refused(RefusalReason.StaleScope).failed()
        val place = detail.value.let { DraftLocation(it.directory, it.workspaceId?.raw) }
        val draft = owner.open(ref)
        try {
            draft.bindLocation(place)
            val ready =
                withTimeoutOrNull(5_000) { draft.state.first { it.ready || it.failure != null } }
                    ?: return Failure.StorageRefused().failed()
            if (!ready.ready || ready.failure != null || connections.graphFor(ref) !== graph)
                return Failure.StorageRefused().failed()
            val path = reference.path
            if (!path.startsWith('/') || path.any { it.code < 0x20 || it.code in 0x7f..0x9f })
                return Failure.UnreadableReply().failed()
            val previousText = draft.state.value.text.trimEnd()
            val nextText =
                if (previousText.isEmpty()) "@${reference.name}"
                else "$previousText @${reference.name}"
            val start = nextText.length - reference.name.length - 1
            val attachment =
                PromptAttachment(
                    "application/x-directory",
                    reference.name,
                    Uri.fromFile(File(path)).toString(),
                    path,
                    start,
                    nextText.length,
                )
            if (ready.attachments.any { it.mime == attachment.mime && it.url == attachment.url })
                return Unit.ok()
            val saved = draft.replaceAttachments(ready.attachments + attachment)
            if (saved is Outcome.Failed) return saved
            if (
                connections.graphFor(ref) !== graph ||
                    stores.profiles.profile(ref.profileId) == null
            )
                return Failure.Refused(RefusalReason.StaleScope).failed()
            draft.changed(nextText)
            return draft.flush()
        } finally {
            draft.close()
        }
    }
}
