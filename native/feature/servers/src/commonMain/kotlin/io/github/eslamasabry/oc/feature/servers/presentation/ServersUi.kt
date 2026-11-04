package io.github.eslamasabry.oc.feature.servers.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.connection.SavedServer
import io.github.eslamasabry.oc.feature.servers.pairing.PairingFill
import io.github.eslamasabry.oc.feature.servers.pairing.PairingProblem
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** Credentials and editable field values deliberately never enter observable screen state. */
public data class ServersUi(
    val servers: ImmutableList<SavedServer> = persistentListOf(),
    val rows: ServerRowsState = ServerRowsState(),
    val busy: Boolean = false,
    val newConversation: Boolean = false,
    val cleartextAllowed: Boolean = false,
    val issue: ServerIssue? = null,
    val failure: Failure? = null,
    val savedGeneration: Int = 0,
    /**
     * Ticks when a pairing code or share link waits in the inbox (PREVIEW-QR); the form takes it
     * once.
     */
    val pairingArrival: Int = 0,
    val pairingProblem: PairingProblem? = null,
)

public enum class ServerIssue {
    InputRequired,
    SaveFailed,
    ConnectFailed,
    CreateFailed,
    RemoveFailed,
    Connected,
}

public interface ServersActions {
    public fun moveQueued(id: ProfileId) {}

    public fun edit(id: ProfileId?) {}

    public fun settings(id: ProfileId) {}

    public fun back()

    public fun allowCleartext(allowed: Boolean, address: String)

    public fun save(name: String, address: String, username: String, password: String)

    public fun connect(id: ProfileId)

    public fun create(id: ProfileId)

    public fun remove(id: ProfileId)

    /** Settings › Agents (agents on this phone and on Paseo computers). */
    public fun openAgents() {}

    /** Opens the pairing scanner (PREVIEW-QR). */
    public fun scanPairing() {}

    /**
     * Pasted text: a pairing code or a share link fills the form; anything else is a plain problem.
     */
    public fun pastePairing(text: String?) {}

    /** The waiting pairing, once; it carries the password, so it never enters [ServersUi]. */
    public fun takePairing(): PairingFill? = null

    /**
     * The unsaved password, kept in memory only while the form is under another page (never saved
     * state).
     */
    public fun holdPassword(text: String) {}

    /** What [holdPassword] kept; empty when nothing was typed. */
    public fun heldPassword(): String = ""

    /** The server's scheduled prompts (:feature:schedules). */
    public fun openSchedules(id: ProfileId) {}

    /** Usage and cost this connected server reports (unit P12-SEARCH-USAGE). */
    public fun openUsage(id: ProfileId) {}

    /** Usage › Plans across every connection (:feature:plans, unit P12-BUILD-INTEGRATIONS-B). */
    public fun openPlans() {}
}
