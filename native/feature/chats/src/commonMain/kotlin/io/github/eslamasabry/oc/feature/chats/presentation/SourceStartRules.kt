package io.github.eslamasabry.oc.feature.chats.presentation

/**
 * A list source the app itself starts: This phone's own OpenCode, or the agents on this phone (their helper). Unlike a
 * computer, the person cannot connect it by hand; while it is not connected the app is starting it.
 */
public enum class AppStart {
    Phone,
    Agents,
}

/** The words the connections chip shows. */
public sealed interface SourcesChipLabel {
    public data object All : SourcesChipLabel

    public data class Some(val shown: Int, val total: Int) : SourcesChipLabel

    /** A shown source the app starts is starting: the chip says so ("Starting Claude Code…"). */
    public data class Starting(val kind: AppStart) : SourcesChipLabel
}

/** What a saved row of a source that is not connected says, and whether it takes a tap. */
public enum class SavedRowLine {
    /** A computer the person has to connect: "Saved list. Connect to open this conversation.", no tap. */
    MustConnect,

    /** This phone's own OpenCode is starting: "Starting OpenCode on this phone…", the tap is kept. */
    PhoneStarting,

    /** The agents on this phone are starting: "Starting Claude Code…", the tap is kept. */
    AgentsStarting,
}

public object SourceStartRules {
    /**
     * The connections chip: a shown source the app is starting says so (the first one in the list's order), else all
     * or "N of M" as before.
     */
    public fun chip(sources: List<ChatsListSourceUi>): SourcesChipLabel =
        sources.firstOrNull { it.shown && it.starting != null }?.starting?.let { SourcesChipLabel.Starting(it) }
            ?: if (sources.all { it.shown }) SourcesChipLabel.All
            else SourcesChipLabel.Some(sources.count { it.shown }, sources.size)

    /** A saved row's line: a source the app starts is starting (and opens when ready); a computer must be connected. */
    public fun savedLine(appStart: AppStart?): SavedRowLine =
        when (appStart) {
            null -> SavedRowLine.MustConnect
            AppStart.Phone -> SavedRowLine.PhoneStarting
            AppStart.Agents -> SavedRowLine.AgentsStarting
        }

    /** A saved row takes a tap (kept until its source connects) only when the app is starting that source. */
    public fun takesTap(appStart: AppStart?): Boolean = savedLine(appStart) != SavedRowLine.MustConnect
}
