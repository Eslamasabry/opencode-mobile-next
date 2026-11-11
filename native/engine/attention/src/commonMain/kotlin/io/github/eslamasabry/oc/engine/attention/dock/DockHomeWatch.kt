package io.github.eslamasabry.oc.engine.attention.dock

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId

/**
 * Notices conversations that leave the home list without a delete event (an archive: the server sends nothing the
 * dock hears, the home just stops listing the row). Pure: feed it each list snapshot; it returns [DockEvent.Left] for
 * every root conversation that was listed before and is not now.
 *
 * It speaks only when the list can be trusted to be whole: [complete] (no source failed or holds a first page only),
 * every project is covered, and nothing is still loading. A profile whose list is not [listed] (hidden by the person)
 * is forgotten without events, so hiding a server never empties its rings.
 */
public class DockHomeWatch {
    private val seen = mutableMapOf<ProfileId, Set<ConversationRef>>()

    public fun events(roots: Collection<ConversationRef>, complete: Boolean, listed: Set<ProfileId>): List<DockEvent> {
        seen.keys.retainAll(listed)
        if (!complete) {
            // Only learn: a partial list proves nothing about what left.
            for (profile in listed) seen[profile] = seen[profile].orEmpty() + roots.filter { it.profileId == profile }
            return emptyList()
        }
        val out = mutableListOf<DockEvent>()
        for (profile in listed) {
            val now = roots.filter { it.profileId == profile }.toSet()
            for (gone in seen[profile].orEmpty() - now) out += DockEvent.Left(gone)
            seen[profile] = now
        }
        return out
    }
}
