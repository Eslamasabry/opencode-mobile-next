package io.github.eslamasabry.oc.core.domain

import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.collections.immutable.ImmutableList

/** Safe setup metadata. Raw provider configuration and paths never leave the protocol adapter. */
public data class SetupInspection(
    val sourceKinds: ImmutableList<String>,
    val servers: ImmutableList<SetupMcpState>,
)

public data class SetupMcpState(val name: String, val status: String)

// SetupInspectionGateway (with inspectSetup) lives in SetupInspectionGateway.kt, shared with PROTO.
