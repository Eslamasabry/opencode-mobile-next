package io.github.eslamasabry.opencode_mobile

/** Pure helper launch admission. Process creation and tracking recheck this same ticket. */
internal object NativeAgentHostAdmission {
    data class Ticket(
        val profile: String,
        val owner: String?,
        val revision: Long,
        val epoch: Long,
        val idleGeneration: Long?,
    )

    data class Snapshot(
        val available: Boolean,
        val activityResumed: Boolean,
        val activityEpoch: Long,
        val wantedRevision: Long,
        val owner: String?,
        val wanted: Boolean,
        val userStopped: Boolean,
        val supervisionEnabled: Boolean,
        val serverLive: Boolean,
        val migrationValid: Boolean,
        val policyValid: Boolean,
        val helperOwner: String?,
        val idle: NativeIdleState.Snapshot,
    )

    fun admitted(ticket: Ticket, current: Snapshot): Boolean {
        if (!Regex("[A-Za-z0-9_-]{1,80}").matches(ticket.profile) ||
            !current.available || !current.activityResumed ||
            current.activityEpoch != ticket.epoch ||
            current.wantedRevision != ticket.revision || ticket.owner != current.owner) return false
        val state = current.idle
        val token = ticket.idleGeneration
        if (token != null) return token > 0 && ticket.owner != null &&
            current.wanted && !current.userStopped && state.owner == ticket.owner &&
            state.generation == token && !state.stopped && state.helperStopped &&
            state.helper == ticket.profile && current.serverLive && current.supervisionEnabled &&
            current.migrationValid && current.policyValid
        if (state.stopped || state.helperStopped) return false
        // Independent foreground helper setup remains available without a server.
        // A completed idle token grants only its owner's canonical helper.
        return state.generation == 0L || (state.generation > 0 &&
            state.owner == ticket.owner && ticket.owner != null &&
            current.helperOwner == ticket.profile)
    }
}
