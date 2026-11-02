package io.github.eslamasabry.oc.feature.servers.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.connection.SavedServer
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** Credentials and editable field values deliberately never enter observable screen state. */
public data class ServersUi(
    val servers: ImmutableList<SavedServer> = persistentListOf(),
    val busy: Boolean = false,
    val newConversation: Boolean = false,
    val cleartextAllowed: Boolean = false,
    val issue: ServerIssue? = null,
    val failure: Failure? = null,
    val savedGeneration: Int = 0,
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
    public fun back()

    public fun allowCleartext(allowed: Boolean, address: String)

    public fun save(name: String, address: String, username: String, password: String)

    public fun connect(id: ProfileId)

    public fun create(id: ProfileId)

    public fun remove(id: ProfileId)

    /** The server's scheduled prompts (:feature:schedules). */
    public fun openSchedules(id: ProfileId) {}
}
