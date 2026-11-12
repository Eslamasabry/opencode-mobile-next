package io.github.eslamasabry.oc.feature.team.presentation

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
    /** Opens the four-step set-up page, which starts the run (Flutter `openPhoneTeamSetup`). */
    public fun turnOn() {}

    public fun setUpOnPhone() {}

    public fun openGuide() {}

    public fun closeGuide() {}

    /** The guide's next step: the computer's address (Flutter `enterAddress`). */
    public fun enterAddress() {}
}

/**
 * The phone's team is on but not answering (an app update stops it), as the team page says it (Dart `PhoneTeamOffPage`):
 * one headline, one action and the progress of a check that is already going.
 */
public enum class PhoneOffKind {
    /** Nothing is running: "AI Team is off" and Turn on. */
    Off,

    /** A check is going: "Step n of 4". */
    Working,

    /** The check waits for the person's yes: Review and choose when. */
    Waiting,
}

public data class PhoneOffUi(val kind: PhoneOffKind = PhoneOffKind.Off, val step: Int = 1, val of: Int = 4)
