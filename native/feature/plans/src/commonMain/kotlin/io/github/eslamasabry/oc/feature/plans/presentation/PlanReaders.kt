package io.github.eslamasabry.oc.feature.plans.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.StateFlow

public enum class PlanReaderKind {
    InAppUbuntu,
    Computer,
}

public enum class PlanReaderPhase {
    Idle,
    Checking,
    Copying,
    SignIns,
    Starting,
    Reading,
    Ready,
}

public enum class PlanReaderProblem {
    Gone,
    Unavailable,
    LinuxNotReady,
    NodeMissing,
    NodeTooOld,
    Prepare,
    Start,
    Remove,
    ServerTooOld,
    Unreachable,
    NoAnswer,
    SaveFailed,
}

public data class PlanReaderStatus(
    val id: String,
    val kind: PlanReaderKind,
    val enabled: Boolean = false,
    val busy: Boolean = false,
    val phase: PlanReaderPhase = PlanReaderPhase.Idle,
    val problem: PlanReaderProblem? = null,
    val foundProviderIds: ImmutableList<String> = persistentListOf(),
)

/** User-consented usage reads; commands name the exact saved connection. */
public interface PlanReaders {
    public val states: StateFlow<ImmutableMap<String, PlanReaderStatus>>

    public suspend fun read(id: String): Outcome<Unit>

    public suspend fun setUp(id: String): Outcome<Unit>

    public suspend fun turnOff(id: String): Outcome<Unit>
}
