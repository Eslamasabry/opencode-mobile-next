package io.github.eslamasabry.oc.feature.integrations.presentation

import io.github.eslamasabry.oc.engine.integrations.host.ByoHostFailureCode
import io.github.eslamasabry.oc.engine.integrations.host.ByoHostPhase
import io.github.eslamasabry.oc.engine.integrations.host.HostResult
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

public data class HostVerificationUi(val phase: ByoHostPhase, val fingerprint: String?)

public data class HostMachineUi(val id: String, val phase: ByoHostPhase)

public data class HostPreparationStepUi(val id: String, val commands: String)

public data class HostPanelUi(
    val machines: ImmutableList<HostMachineUi> = persistentListOf(),
    val selected: String? = null,
    val phase: ByoHostPhase = ByoHostPhase.Idle,
    val fingerprint: String? = null,
    val publicKey: String? = null,
    val preparation: ImmutableList<HostPreparationStepUi> = persistentListOf(),
    val busy: Boolean = false,
    val failure: ByoHostFailureCode? = null,
)

/** A native host owner. No login password, private key or device token reaches presentation. */
public interface HostPanelSource {
    public suspend fun machines(): HostResult<ImmutableList<HostMachineUi>>

    public fun newId(): String

    public suspend fun inspect(id: String, address: String, port: String): HostResult<String>

    public suspend fun prepareIdentity(id: String, fingerprint: String): HostResult<String>

    public suspend fun resume(id: String): HostResult<HostVerificationUi>

    public suspend fun remove(id: String, revoke: Boolean): HostResult<Unit>
}

public interface HostPanelActions {
    public fun refreshHosts()

    public fun chooseHost(id: String)

    public fun inspectHost(address: String, port: String)

    public fun prepareHostIdentity()

    public fun resumeHost()

    public fun removeHost(revoke: Boolean)
}
