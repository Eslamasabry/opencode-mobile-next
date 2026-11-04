package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.engine.settings.teaching.TeachingSource
import io.github.eslamasabry.oc.engine.settings.teaching.TipController
import io.github.eslamasabry.oc.feature.voice.presentation.VoiceTeachingHub

/** Share exactly the controller used by Help, including replay, counts, cooldown and reset. */
@Inject
@ContributesBinding(AppScope::class)
class TerminalTeachingSource(private val hub: VoiceTeachingHub) : TeachingSource {
    override suspend fun controller(): TipController? = hub.controller()
}
