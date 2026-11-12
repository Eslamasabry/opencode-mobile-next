package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.android.notifications.AlertKind
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.entry.EntryTarget
import io.github.eslamasabry.oc.ui.routes.TeamRoute

/**
 * The page an AI Team alert's tap opens. A computer team's alert names its page in the id it carries (the entry
 * router's [EntryTarget.TeamPage]: `gate:<id>` is the Gate page of one question, `run:<id>` the task page of one run).
 * A phone team's alert carries a bare request, task or project id, which is no conversation: it opens that server's
 * team page, as a team link does. Null for any other alert (a conversation's alert opens its chat).
 */
internal fun teamAlertRoute(kind: AlertKind?, session: String?, profile: String?): TeamRoute? {
    if (kind == null || !kind.isTeam) return null
    val owner = profile?.takeIf { it.isNotBlank() }?.let(::ProfileId) ?: return null
    return teamPageOf(owner, session.orEmpty())?.route() ?: TeamRoute.Home(owner)
}

/** `gate:<id>` / `run:<id>` as the entry router reads them; null for a bare id. */
internal fun teamPageOf(owner: ProfileId, session: String): EntryTarget.TeamPage? {
    val gate = session.startsWith("gate:")
    if (!gate && !session.startsWith("run:")) return null
    val id = session.substringAfter(':')
    return if (id.isEmpty()) null else EntryTarget.TeamPage(owner, gate, id)
}

/** [EntryTarget.TeamPage] to its route: the Gate page, or the task page of a computer team's run. */
internal fun EntryTarget.TeamPage.route(): TeamRoute =
    if (gate) TeamRoute.Gate(profileId, id) else TeamRoute.FleetRun(profileId, id)
