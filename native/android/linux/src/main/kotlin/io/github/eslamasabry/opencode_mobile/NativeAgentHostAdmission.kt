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

    private val PROFILE = Regex("[A-Za-z0-9_-]{1,80}")

    fun admitted(ticket: Ticket, current: Snapshot): Boolean {
        val token = ticket.idleGeneration
        return commonAdmitted(ticket, current) &&
            if (token != null) tokenAdmitted(ticket, current, token) else tokenlessAdmitted(ticket, current)
    }

    private fun commonAdmitted(ticket: Ticket, current: Snapshot): Boolean {
        val active = current.available && current.activityResumed
        val sameIntent = current.activityEpoch == ticket.epoch &&
            current.wantedRevision == ticket.revision
        return PROFILE.matches(ticket.profile) && active && sameIntent && ticket.owner == current.owner
    }

    private fun tokenAdmitted(ticket: Ticket, current: Snapshot, token: Long): Boolean {
        val state = current.idle
        val owned = ticket.owner != null && state.owner == ticket.owner && state.generation == token
        val owedHelper = !state.stopped && state.helperStopped && state.helper == ticket.profile
        val wanted = current.wanted && !current.userStopped
        val supervised = current.serverLive && current.supervisionEnabled &&
            current.migrationValid && current.policyValid
        return token > 0 && owned && owedHelper && wanted && supervised
    }

    private fun tokenlessAdmitted(ticket: Ticket, current: Snapshot): Boolean {
        val state = current.idle
        // Independent foreground helper setup remains available without a server.
        // A completed idle token grants only its owner's canonical helper.
        val canonical = state.generation > 0 && state.owner == ticket.owner &&
            ticket.owner != null && current.helperOwner == ticket.profile
        return !state.stopped && !state.helperStopped && (state.generation == 0L || canonical)
    }
}
