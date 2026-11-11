package io.github.eslamasabry.opencode_mobile.graph

/**
 * Which second sentence of the "the app was closed" notice says where the phone's OpenCode stands: running again,
 * starting by itself, or down until the person starts it. The first sentence says what happened; this one says the
 * state, once, so the two never contradict each other.
 */
internal enum class ExitSentence { Back, BackWithTeam, Starting, StartingWithTeam, Down, DownWithTeam }

internal fun exitSentence(serverBack: Boolean, recoveryAllowed: Boolean, teamStopped: Boolean): ExitSentence =
    when {
        serverBack -> if (teamStopped) ExitSentence.BackWithTeam else ExitSentence.Back
        recoveryAllowed -> if (teamStopped) ExitSentence.StartingWithTeam else ExitSentence.Starting
        else -> if (teamStopped) ExitSentence.DownWithTeam else ExitSentence.Down
    }

/**
 * The notice's actions, first the lead: still down, the lead opens This phone (where the phone's OpenCode is started)
 * and Keep it running follows; running or starting again, only Keep it running. A crash offers no Keep it running.
 */
internal fun exitActions(sentence: ExitSentence, offersKeepAlive: Boolean): List<io.github.eslamasabry.oc.core.domain.platform.AppNoticeAction> =
    buildList {
        if (sentence == ExitSentence.Down || sentence == ExitSentence.DownWithTeam)
            add(io.github.eslamasabry.oc.core.domain.platform.AppNoticeAction.OpenThisPhone)
        if (offersKeepAlive) add(io.github.eslamasabry.oc.core.domain.platform.AppNoticeAction.KeepRunning)
    }
