package io.github.eslamasabry.oc.ui.routes

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.serialization.Serializable

/** The AI Team (Flutter lib/ui/screens/team, team page P3.4). Owned by :feature:team. */
@Serializable
public sealed interface TeamRoute : AppRoute {
    /** The one team page for [profileId]'s server (Dart `openTeamPage`): the projects, or why there are none. */
    @Serializable
    public data class Home(val profileId: ProfileId) : TeamRoute

    /** One project (Dart `TeamProjectOverview`): what needs the person, the goal, milestones, lanes. */
    @Serializable
    public data class Project(val profileId: ProfileId, val projectId: String) : TeamRoute

    /** One project's board (Dart `TeamProjectBoard`); [milestone] opens it filtered to one milestone. */
    @Serializable
    public data class Board(val profileId: ProfileId, val projectId: String, val milestone: String? = null) : TeamRoute

    /** One task's conversation (Dart `TeamProjectConversation`): watching a worker, answering it, messaging it. */
    @Serializable
    public data class Task(val profileId: ProfileId, val projectId: String, val taskId: String) : TeamRoute
}
