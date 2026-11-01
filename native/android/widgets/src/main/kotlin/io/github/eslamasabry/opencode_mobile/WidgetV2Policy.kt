package io.github.eslamasabry.opencode_mobile

/** Pure policy shared by rendering tests. No network, clock or Android state. */
object WidgetV2Policy {
    const val BACKGROUND_SETTINGS_ACTION = "background_settings"
    /** TINY 2x1, STRIP 4x1, CARD 2x2, WIDE 4x2, LIST 4x4 and taller. */
    enum class Size { TINY, STRIP, CARD, WIDE, LIST }
    enum class Connection { CONNECTED, ON_DEMAND, PENDING, OFFLINE, BACKGROUND_SETTINGS }

    private val onDemandActions = setOf("allowOnce", "reject", "stop", "approvePlan", "resume")

    /** Cached scope/turn proof permits a tap to request a fresh connection, but
     * never makes cached state live. The service owns the bounded action lease. */
    data class ConnectionState(
        val version: Int,
        val connected: Boolean,
        val serviceActive: Boolean,
        val onDemandAvailable: Boolean = false,
        val actionPending: Boolean = false,
        val recoveryRequired: Boolean = false,
    )

    fun connection(state: ConnectionState): Connection = with(state) {
        when {
            version != 2 -> if (serviceActive) Connection.OFFLINE else Connection.BACKGROUND_SETTINGS
            actionPending -> Connection.PENDING
            serviceActive && connected -> Connection.CONNECTED
            onDemandAvailable && !recoveryRequired -> Connection.ON_DEMAND
            !serviceActive || recoveryRequired -> Connection.BACKGROUND_SETTINGS
            else -> Connection.OFFLINE
        }
    }

    /** Connection presentation survives settlement of the last action. An idle
     * conversation has no buttons, but its saved target still supports on-demand
     * connections. Actual controls remain gated separately by actionEnabled. */
    fun onDemandAvailable(version: Int, hasDescriptor: Boolean): Boolean =
        version == 2 && hasDescriptor

    fun actionEnabled(
        connection: Connection,
        action: String,
        advertisedActions: Set<String>,
        identityValid: Boolean,
    ): Boolean = identityValid && when (connection) {
        Connection.CONNECTED -> true
        // Keep the original immutable tap available if Android kills the
        // process without onDestroy. Native lease/ledger rejects overlap and
        // replay; renderer state must not strand the launcher without controls.
        Connection.ON_DEMAND, Connection.PENDING -> action in onDemandActions && action in advertisedActions
        else -> false
    }

    /** A temporary connection failure keeps proven on-demand controls retryable.
     * Unsupported denotes a hard platform/transport limitation; missing safe
     * descriptor proof already selects settings through connection(). */
    fun needsBackgroundSettings(status: String?, error: String?): Boolean =
        status == "rejected" && error == "unsupported"

    /** A successful dispatch is settled, so leave the header to the live state.
     * Only failures or an unknown outcome need an enduring explanation. This
     * also suppresses accepted receipts restored from older saved snapshots. */
    fun actionResultNotice(status: String?): String? = when (status) {
        "rejected", "uncertain" -> status
        else -> null
    }
}
