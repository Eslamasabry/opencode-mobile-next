package io.github.eslamasabry.oc.feature.servers.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.RefusalReason

/**
 * The safe category of a servers-screen failure (docs/native/server-errors-2026-11-02/contract.md).
 * Each kind gets one plain localized sentence, so a sign-in, cleartext-consent, address,
 * certificate, incompatible, unreadable or server-side failure is never presented as a missing
 * network. [Other] keeps the existing generic issue text as the null/unknown fallback. The list is
 * frozen by the contract; no invented causes.
 */
public enum class ServerFailureKind {
    Network,
    Timeout,
    Certificate,
    SignIn,
    CleartextConsent,
    InvalidAddress,
    Incompatible,
    UnreadableReply,
    Server,
    Rejected,
    Storage,
    Secret,
    Other,
}

/**
 * Classifies the flat [Failure] list. Credentials ([Failure.SignIn]), consent, address,
 * certificate, compatibility, reply-shape, storage and secret failures each name their own
 * category; null, unknown, unexpected and app-side refusals stay [ServerFailureKind.Other] instead
 * of blaming the network.
 */
public fun serverFailureKind(failure: Failure?): ServerFailureKind =
    when (failure) {
        is Failure.Network -> ServerFailureKind.Network
        is Failure.TimedOut -> ServerFailureKind.Timeout
        is Failure.Certificate -> ServerFailureKind.Certificate
        is Failure.SignIn -> ServerFailureKind.SignIn
        is Failure.Incompatible -> ServerFailureKind.Incompatible
        is Failure.UnreadableReply -> ServerFailureKind.UnreadableReply
        is Failure.Server -> ServerFailureKind.Server
        is Failure.Rejected -> ServerFailureKind.Rejected
        is Failure.StorageRefused -> ServerFailureKind.Storage
        is Failure.SecretUnavailable -> ServerFailureKind.Secret
        is Failure.Refused ->
            when (failure.reason) {
                RefusalReason.CleartextNotConfirmed -> ServerFailureKind.CleartextConsent
                RefusalReason.InvalidAddress -> ServerFailureKind.InvalidAddress
                else -> ServerFailureKind.Other
            }
        else -> ServerFailureKind.Other
    }
