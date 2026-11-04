package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.voice.dictation.VoiceStart
import io.github.eslamasabry.oc.engine.voice.state.VoiceHost
import io.github.eslamasabry.oc.engine.voice.state.VoiceMode
import io.github.eslamasabry.oc.feature.voice.presentation.VoiceParts
import io.github.eslamasabry.oc.feature.widgetactions.presentation.WidgetVoicePort

@ContributesTo(AppScope::class)
interface WidgetOverlayAccessors {
    val widgetOverlayVoice: AndroidVoiceHub
}

/**
 * The shared on-device voice engine owns the microphone; text is returned only to the open field.
 */
internal class WidgetOverlayVoice(private val hub: AndroidVoiceHub) : WidgetVoicePort {
    private var parts: VoiceParts? = null

    override suspend fun start(): Boolean {
        val available = (hub.parts() as? Outcome.Ok)?.value ?: return false
        if (!available.micAvailable) return false
        parts = available
        available.dictation.onHostChanged(VoiceHost.Foreground)
        return available.dictation.start(VoiceStart(VoiceMode.Tap, stopOnSilence = false)) is
            Outcome.Ok
    }

    override suspend fun finish(): String? = (parts?.dictation?.finish() as? Outcome.Ok)?.value

    override suspend fun cancel() {
        parts?.dictation?.cancel(keepWords = false)
    }

    override fun foreground(active: Boolean) {
        parts?.dictation?.onHostChanged(if (active) VoiceHost.Foreground else VoiceHost.Background)
    }
}
