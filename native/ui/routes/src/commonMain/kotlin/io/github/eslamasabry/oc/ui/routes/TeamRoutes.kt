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

    /**
     * One project editor (Dart `openTeamNewProject`, `openTeamSpecEditor`, ...): [kind] is Dart's editor name
     * (`create`, `quick`, `spec`, `plan`, `settings`, `roles`, `defaults`); [projectId] is empty for the team-wide ones.
     */
    @Serializable
    public data class Editor(val profileId: ProfileId, val kind: String, val projectId: String = "") : TeamRoute

    /** One project's servers and lanes (Dart `TeamProjectServers`): what runs where, moving a task. */
    @Serializable
    public data class Servers(val profileId: ProfileId, val projectId: String) : TeamRoute

    /** One project's timeline (Dart `TeamProjectTimeline`): what the team did, newest first. */
    @Serializable
    public data class Timeline(val profileId: ProfileId, val projectId: String) : TeamRoute

    /**
     * "Turn on AI Team on this phone" (Dart `openPhoneTeamSetup`): the four steps of the one-tap set-up with their
     * times. It starts the run when it opens and none is going; a run that already is going is shown, not doubled.
     */
    @Serializable
    public data class TurnOn(val profileId: ProfileId) : TeamRoute

    /** Team settings (Dart `TeamSettingsScreen`): the agents, how the team runs here, Turn off. */
    @Serializable
    public data class Settings(val profileId: ProfileId) : TeamRoute

    /** The agents of a computer's team (Dart `TeamAgentsScreen`, over Gas City): most urgent first. */
    @Serializable
    public data class FleetAgents(val profileId: ProfileId) : TeamRoute

    /** One agent of a computer's team (Dart `AgentScreen`): Pause, Nudge, Restart, Stop, Force stop and its live output. */
    @Serializable
    public data class FleetAgent(val profileId: ProfileId, val agentId: String) : TeamRoute

}
