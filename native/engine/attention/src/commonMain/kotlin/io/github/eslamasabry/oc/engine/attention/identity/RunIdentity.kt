package io.github.eslamasabry.oc.engine.attention.identity

/** The transcript facts a run identity needs: one message's id, role, creation time and its parts' types. */
public data class RunMessage(val id: String, val role: String, val createdAt: Long?, val partTypes: List<String>)

/**
 * Which turn a widget action or status ticket belongs to (port of `widgetRunMarker`, B-PROTO-127). OpenCode 2
 * timeline records (notice, switch, compaction, the stable `idle` record and any future `v2:` part) use role=user in
 * the protocol-neutral transcript; they are not new prompts and must not invalidate a completed turn. OpenCode 1 user
 * parts and attachment-only prompts stay valid identities.
 */
public object RunIdentity {
    public fun marker(messages: Iterable<RunMessage>): String? {
        val prompts = messages.filter { m -> m.role == "user" && m.partTypes.none { it.startsWith("v2:") } }
        val last = prompts.sortedBy { it.createdAt ?: 0L }.lastOrNull() ?: return null
        return last.id.ifEmpty { null }
    }

    /** Real prompts only: how many turns a run has had. */
    public fun promptCount(messages: Iterable<RunMessage>): Int =
        messages.count { m -> m.role == "user" && m.partTypes.none { it.startsWith("v2:") } }
}
