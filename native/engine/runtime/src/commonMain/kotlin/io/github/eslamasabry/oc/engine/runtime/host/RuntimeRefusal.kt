package io.github.eslamasabry.oc.engine.runtime.host

import io.github.eslamasabry.oc.core.common.Redacted

/**
 * The runtime's failure vocabulary (plan 11 §5.5, CR11-1). The plan puts this enum next to `Failure` in :core:common
 * as `Failure.Runtime(problem)`; until the core contract carries it, the runtime ports answer [RuntimeCall] and this
 * module keeps the enum (catalog request in docs/native/units/S3-RTENG.md). The values and their order are the plan's.
 */
public enum class RuntimeProblem {
    /** `runtime_ownership_unknown`: "Open app settings, force stop OpenCode, then open it again." */
    OwnershipUnknown,

    /** "Another phone task is running": busy, never broken (map T4). */
    SlotBusy,
    RecoveryUnavailable,
    IdleResumeUnavailable,
    IdleResumeStale,
    SetupPersistence,
    InvalidProfile,
    ConfirmationRequired,
    StorageUnavailable,
    RemovalFailed,
    WorkLeaseInvalid,
    ServerStartUnavailable,
    NotInstalled,
    Installing,
    ComponentRecoveryRequired,
    ProtectionUnavailable,

    /** `builtin_linux`: the generic refusal. */
    Generic,
}

/**
 * The answer of one runtime port call: a value, or the phone's refusal with its problem and Details-only text.
 * Becomes `Outcome<T>` with `Failure.Runtime` once CR11-1 lands in :core:common; nothing else changes.
 */
public sealed interface RuntimeCall<out T> {
    public data class Done<out T>(val value: T) : RuntimeCall<T>

    public data class Refused(val problem: RuntimeProblem, val details: Redacted? = null) : RuntimeCall<Nothing>
}

public fun <T> T.done(): RuntimeCall<T> = RuntimeCall.Done(this)

public fun RuntimeProblem.refused(details: Redacted? = null): RuntimeCall<Nothing> = RuntimeCall.Refused(this, details)
