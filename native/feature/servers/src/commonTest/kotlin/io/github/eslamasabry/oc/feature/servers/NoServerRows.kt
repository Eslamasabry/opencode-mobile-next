package io.github.eslamasabry.oc.feature.servers

import io.github.eslamasabry.oc.feature.servers.presentation.ServerRowsPort
import io.github.eslamasabry.oc.feature.servers.presentation.ServerRowsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A server list with no extra row facts, for ViewModel tests that do not look at the rows. */
internal object NoServerRows : ServerRowsPort {
    override val state: StateFlow<ServerRowsState> = MutableStateFlow(ServerRowsState())
}
