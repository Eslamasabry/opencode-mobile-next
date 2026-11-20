package io.github.eslamasabry.oc.feature.chat.ui

import io.github.eslamasabry.oc.feature.chat.presentation.LiveTurnUi
import io.github.eslamasabry.oc.feature.chat.world.PendingSend
import io.github.eslamasabry.oc.feature.chat.world.PendingState
import kotlinx.collections.immutable.ImmutableList

/**
 * F28: one status slot under a first send's bubble. While the newest pending bubble still says it is sending and its
 * run starts, the live line is not a second row beneath it (that pushed the bubble up a line in one frame, with both
 * lines showing for a moment): the bubble's own status line says the live words in turn ("Sending", "Thinking…").
 * When the bubble settles into the server's row, the live row stands in the same place at the same height.
 */
internal object LiveSlot {
    /** The pending send whose status line carries the live words, or null when the live line is its own row. */
    fun host(live: LiveTurnUi?, pending: ImmutableList<PendingSend>, signalStyle: Boolean): PendingSend? {
        val first = if (live == null || live.joinsTurn || signalStyle) null else pending.firstOrNull()
        return first?.takeIf { it.state == PendingState.Sending && it.setUp == null && it.signIn == null }
    }
}
