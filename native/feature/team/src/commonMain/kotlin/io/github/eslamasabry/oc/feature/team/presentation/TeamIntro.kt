package io.github.eslamasabry.oc.feature.team.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.ids.ProfileId

/**
 * Where an AI Team would run for this server (Flutter team_discover.dart `TeamServerKind`): OpenCode on this phone (the
 * team is the app's bundled engine, turned on per project) or a computer (Gas City runs there).
 */
public enum class TeamServerKind { ThisPhone, Computer }

/** The server as the off page names it: its kind and its saved name. */
public data class TeamServer(val kind: TeamServerKind, val name: String)

/** What the off page needs to know about a server; null while that server is not connected. */
public fun interface TeamServerFacts {
    public fun server(profile: ProfileId): TeamServer?
}

/**
 * The team page while the team is off (Flutter team_intro_screen.dart): what the team does, what it needs on this kind
 * of server, and one primary named after what it sets up.
 */
public data class TeamIntroUi(
    /** Null until the server is known (not connected yet): the page shows how it works and waits. */
    val server: TeamServer? = null,
    /** This phone can turn its team on here and now: "Turn on AI Team on this phone". */
    val canTurnOn: Boolean = false,
    val turningOn: Boolean = false,
    /** The last turn-on failed; the team stays off. */
    val failure: Failure? = null,
    /** The computer's set-up guide is open. */
    val guide: Boolean = false,
)

/** The off page's one primary, as Flutter picks it per kind. */
public enum class TeamIntroPrimary {
    /** This phone, ready to turn on: the bundled engine starts for this server. */
    TurnOnPhone,

    /** This phone, where the app cannot turn it on itself: Add tools, where AI Team is one of the tools. */
    SetUpPhone,

    /** A computer: the host guide (Gas City, then the address). */
    SetUpComputer,
}

public fun TeamIntroUi.primary(): TeamIntroPrimary? =
    when (server?.kind) {
        null -> null
        TeamServerKind.ThisPhone -> if (canTurnOn) TeamIntroPrimary.TurnOnPhone else TeamIntroPrimary.SetUpPhone
        TeamServerKind.Computer -> TeamIntroPrimary.SetUpComputer
    }

public interface TeamIntroActions {
    public fun turnOn() {}

    public fun setUpOnPhone() {}

    public fun openGuide() {}

    public fun closeGuide() {}
}
