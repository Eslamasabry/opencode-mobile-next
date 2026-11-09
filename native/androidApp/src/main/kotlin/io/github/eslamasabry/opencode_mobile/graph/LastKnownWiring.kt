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
    private val store: LastKnown,
    private val stores: PreviewStores,
    private val dispatchers: AppDispatchers,
    private val clock: Clock,
    private val appScope: AppCoroutineScope,
) : ChatLastKnown {
    private fun mark(ref: ConversationRef): ServerMark? =
        stores.profiles.profile(ref.profileId)?.profile?.lastKnownMark()

    override fun peek(ref: ConversationRef): KeptTranscript? =
        mark(ref)?.let { store.peek(ref.profileId, it, TRANSCRIPTS, ref.sessionId.raw) }

    override suspend fun read(ref: ConversationRef): KeptTranscript? =
        mark(ref)?.let { store.read(ref.profileId, it, TRANSCRIPTS, ref.sessionId.raw) }

    override suspend fun keep(ref: ConversationRef, title: String?, messages: List<MessageWithParts>) {
        val mark = mark(ref) ?: return
        val kept = KeptTranscript.fit(title, messages, TRANSCRIPTS.maxBytes - ENVELOPE) ?: return
        store.write(ref.profileId, mark, TRANSCRIPTS, ref.sessionId.raw, kept)
    }

    override fun detached(ref: ConversationRef): TranscriptHandle =
        detachedTranscript(ref.sessionId.raw, dispatchers, clock, appScope)

    companion object {
        val TRANSCRIPTS = CacheShelf("transcript", KeptTranscript.serializer(), maxBytes = 512 * 1024, maxEntries = 30)
        private const val ENVELOPE = 256
    }
}
