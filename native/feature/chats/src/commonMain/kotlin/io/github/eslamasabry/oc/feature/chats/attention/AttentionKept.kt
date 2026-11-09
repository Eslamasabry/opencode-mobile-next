package io.github.eslamasabry.oc.feature.chats.attention

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.engine.attention.activity.AutomaticActKind
import io.github.eslamasabry.oc.engine.attention.feed.AttentionKind
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.Serializable

/**
 * Cache-first (owner rule, 2026-11-09): Needs you opens on the rows it last drew, then the live feed replaces them in
 * place. Kept per profile (the selected one) and server by the app's last-known store.
 */
public interface AttentionLastKnown {
    public fun peek(profile: String): KeptAttention?

    public suspend fun read(profile: String): KeptAttention?

    public suspend fun keep(profile: String, kept: KeptAttention)
}

/** What Needs you drew: request rows, unread automatic actions and the monitor switches. No undo state, no prose. */
@Serializable
public data class KeptAttention(
    val requests: List<Request> = emptyList(),
    val history: List<Act> = emptyList(),
    val monitors: List<Monitor> = emptyList(),
) {
    @Serializable
    public data class Request(
        val key: String,
        val kind: String,
        val server: String,
        val title: String?,
        val fresh: Boolean,
        val profile: String?,
        val session: String?,
    )

    @Serializable
    public data class Act(val profile: String, val id: String, val kind: String, val target: String, val at: String)

    @Serializable public data class Monitor(val profile: String, val name: String, val enabled: Boolean)

    /** The screen's state before the live feed answers: the kept rows, not loading. */
    public fun toUi(): AttentionUi =
        AttentionUi(
            loading = false,
            requests =
                requests
                    .mapNotNull { r ->
                        val kind = AttentionKind.entries.firstOrNull { it.name == r.kind } ?: return@mapNotNull null
                        AttentionRow(
                            r.key,
                            kind,
                            r.server,
                            r.title,
                            r.fresh,
                            if (r.profile != null && r.session != null) ConversationRef(ProfileId(r.profile), SessionId(r.session)) else null,
                        )
                    }
                    .toImmutableList(),
            history =
                history
                    .mapNotNull { a ->
                        val kind = AutomaticActKind.entries.firstOrNull { it.name == a.kind } ?: return@mapNotNull null
                        HistoryRow(HistoryIdentity(a.profile, a.id), kind, a.target, a.at)
                    }
                    .toImmutableList(),
            monitors = monitors.map { MonitorRow(it.profile, it.name, it.enabled) }.toImmutableList(),
        )

    public companion object {
        public const val MAX_ROWS: Int = 100

        /** Null when the feed is still loading or incomplete: a partial read must not replace a good one. */
        public fun of(ui: AttentionUi): KeptAttention? {
            if (ui.loading || ui.incomplete || ui.corrupt) return null
            return KeptAttention(
                ui.requests.take(MAX_ROWS).map {
                    Request(it.key, it.kind.name, it.server, it.title, it.fresh, it.ref?.profileId?.raw, it.ref?.sessionId?.raw)
                },
                ui.history.take(MAX_ROWS).map { Act(it.identity.profile, it.identity.id, it.kind.name, it.target, it.at) },
                ui.monitors.take(MAX_ROWS).map { Monitor(it.profile, it.name, it.enabled) },
            )
        }
    }
}
