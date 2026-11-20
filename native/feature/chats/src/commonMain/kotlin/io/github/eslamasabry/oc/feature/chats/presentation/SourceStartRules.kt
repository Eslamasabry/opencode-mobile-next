package io.github.eslamasabry.oc.feature.chats.presentation

/**
 * A list source the app itself starts: This phone's own OpenCode, or the agents on this phone (their helper). Unlike a
 * computer, the person cannot connect it by hand; while it is not connected the app is starting it.
 */
public enum class AppStart {
    Phone,
    Agents,

    /**
     * The agents are being updated by the app (the automatic agent update installs): their helper is down and starts
     * again when the update is done.
     */
    AgentsUpdating,
}

/** The words the connections chip shows. */
public sealed interface SourcesChipLabel {
    public data object All : SourcesChipLabel

    public data class Some(val shown: Int, val total: Int) : SourcesChipLabel

    /** A shown source the app starts is starting: the chip says so ("Starting Claude Code…"). */
    public data class Starting(val kind: AppStart) : SourcesChipLabel

    /** The app is updating the agents ([agent]: the one being installed, else the source's name): "Updating Claude Code…". */
    public data class Updating(val agent: String) : SourcesChipLabel
}

/** What a saved row of a source that is not connected says, and whether it takes a tap. */
public enum class SavedRowLine {
    /** A computer the person has to connect: "Saved list. Connect to open this conversation.", no tap. */
    MustConnect,

    /** This phone's own OpenCode is starting: "Starting OpenCode on this phone…", the tap is kept. */
    PhoneStarting,

    /** The agents on this phone are starting: "Starting Claude Code…", the tap is kept. */
    AgentsStarting,

    /** The app is updating the agents: "Updating your agents on this phone…", the tap is kept. */
    AgentsUpdating,
}

public object SourceStartRules {
    /**
     * The connections chip: a shown source the app is starting says so (the first one in the list's order), else all
     * or "N of M" as before.
     */
    public fun chip(sources: List<ChatsListSourceUi>): SourcesChipLabel {
        // An agent update says so first: it is why the agents' conversations wait, for minutes.
        sources.firstOrNull { it.shown && it.starting == AppStart.AgentsUpdating }?.let {
            return SourcesChipLabel.Updating(it.updatingAgent ?: it.name)
        }
        return sources.firstOrNull { it.shown && it.starting != null }?.starting?.let { SourcesChipLabel.Starting(it) }
            ?: if (sources.all { it.shown }) SourcesChipLabel.All
            else SourcesChipLabel.Some(sources.count { it.shown }, sources.size)
    }

    /** A saved row's line: a source the app starts is starting (and opens when ready); a computer must be connected. */
    public fun savedLine(appStart: AppStart?): SavedRowLine =
        when (appStart) {
            null -> SavedRowLine.MustConnect
            AppStart.Phone -> SavedRowLine.PhoneStarting
            AppStart.Agents -> SavedRowLine.AgentsStarting
            AppStart.AgentsUpdating -> SavedRowLine.AgentsUpdating
        }

    /** A saved row takes a tap (kept until its source connects) only when the app is starting that source. */
    public fun takesTap(appStart: AppStart?): Boolean = savedLine(appStart) != SavedRowLine.MustConnect

    /** What a row waiting for its source (a kept tap) says: updating while the agents update, else connecting. */
    public fun waitingWord(appStart: AppStart?): WaitingWord =
        if (appStart == AppStart.AgentsUpdating) WaitingWord.Updating else WaitingWord.Connecting
}

/** The word on a row whose tap is kept. */
public enum class WaitingWord {
    Connecting,
    Updating,

    /** The source did not connect within the wait's limit: the tap was dropped. */
    NotAnswering,
}
