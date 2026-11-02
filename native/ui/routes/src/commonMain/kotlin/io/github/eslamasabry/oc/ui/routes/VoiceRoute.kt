package io.github.eslamasabry.oc.ui.routes

import kotlinx.serialization.Serializable

/** Voice typing and read-aloud settings (P12-VOICE; plan 07 I11 "Settings › Voice typing"). */
@Serializable
public sealed interface VoiceRoute : AppRoute {
    @Serializable public data object Settings : VoiceRoute
}
