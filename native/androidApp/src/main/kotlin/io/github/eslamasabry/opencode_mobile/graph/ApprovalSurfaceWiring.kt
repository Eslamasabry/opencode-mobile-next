package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.analytics.health.AppJourneys

/*
 * Where an approval was answered, for the approval meter's `surface` word (chat, home, notification, widget). Words only:
 * the request id is kept in memory until the answer is counted and never sent.
 */

/**
 * Sends the answer to [id] through [send], telling the approval meter first that it comes from [surface] (`home` or
 * `notification`; the chat is the default). The word is taken back when the send does not go.
 */
internal suspend fun <T> withApprovalSurface(id: String, surface: String, send: suspend () -> Outcome<T>): Outcome<T> {
    val meter = AppJourneys.approvalsOrNull
    meter?.surface(id, surface)
    val result = send()
    if (result !is Outcome.Ok) meter?.surfaceUnused(id)
    return result
}
