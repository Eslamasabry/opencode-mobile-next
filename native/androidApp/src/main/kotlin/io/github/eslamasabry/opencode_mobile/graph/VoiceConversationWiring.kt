package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.engine.voice.conversation.VoiceConversationSession
import io.github.eslamasabry.oc.feature.chat.world.ChatWorlds
import io.github.eslamasabry.oc.feature.voice.presentation.NoVoiceConversations
import io.github.eslamasabry.oc.feature.voice.presentation.VoiceConversationHandle
import io.github.eslamasabry.oc.feature.voice.presentation.VoiceConversations
import kotlinx.coroutines.sync.withLock

@Inject
@ContributesBinding(AppScope::class, replaces = [NoVoiceConversations::class])
class AppVoiceConversations(private val worlds: ChatWorlds) : VoiceConversations {
    override fun open(ref: ConversationRef): VoiceConversationHandle? {
        val world = worlds.open(ref)
        val session = world as? VoiceConversationSession
        if (session == null) {
            worlds.release(world)
            return null
        }
        return VoiceConversationHandle(session) { worlds.release(world) }
    }
}

@dev.zacsweers.metro.SingleIn(AppScope::class)
@Inject
@ContributesBinding(
    AppScope::class,
    replaces = [io.github.eslamasabry.oc.feature.voice.presentation.NoVoiceTeachingHub::class],
)
class AppVoiceTeaching(
    private val stores: PreviewStores,
    private val clock: kotlin.time.Clock,
    private val scope: io.github.eslamasabry.oc.core.common.AppCoroutineScope,
) : io.github.eslamasabry.oc.feature.voice.presentation.VoiceTeachingHub {
    private val lane = kotlinx.coroutines.sync.Mutex()
    private var tips: io.github.eslamasabry.oc.engine.settings.teaching.TipController? = null

    override suspend fun controller():
        io.github.eslamasabry.oc.engine.settings.teaching.TipController? = lane.withLock {
        if (stores.warm() is io.github.eslamasabry.oc.core.common.Outcome.Failed)
            return@withLock null
        tips
            ?: io.github.eslamasabry.oc.engine.settings.teaching
                .TipController(stores.prefs, stores.lanes, clock, scope)
                .also { tips = it }
    }
}
