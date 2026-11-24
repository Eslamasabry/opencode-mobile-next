package io.github.eslamasabry.oc.feature.chat.world

import io.github.eslamasabry.oc.engine.transcript.state.TranscriptRow

/** Picks a replacement model when a chat's chosen model is no longer listed on the server. */
public object ModelFallback {
    /** Models that already answered in this chat, newest first, without repeats. */
    public fun answered(rows: Iterable<TranscriptRow>): List<ModelKey> = emptyList()

    /** Picks a usable replacement for [gone], preferring an answered model before same-provider fallback. */
    public fun pick(
        gone: ModelKey?,
        answered: List<ModelKey>,
        models: List<ComposerModel>,
    ): ComposerModel? = null
}
