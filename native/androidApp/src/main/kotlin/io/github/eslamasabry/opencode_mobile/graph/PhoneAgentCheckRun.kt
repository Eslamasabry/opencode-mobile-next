package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.agents.check.PhoneCheckFailure

/** The things the phone check does on this phone, one call each, so the order and the stops are testable alone. */
internal interface PhoneCheckSteps {
    /** Drops the stored proof for the agent before checking; false when that could not be saved. */
    suspend fun clearProof(): Boolean

    /** Installs or refreshes what the agent needs and waits for it. */
    suspend fun install(): InstallEnd

    /** The pinned version runs and the shared project folder is usable. */
    suspend fun pinnedRuns(): Boolean

    /** Starts the helper and asks it to answer: the Paseo version it reported, or why not. */
    suspend fun helperAnswer(): Outcome<String?>

    /** Stores the proof (`oc.agentPhoneGate.<owner>`). */
    suspend fun saveProof(): Boolean
}

internal enum class InstallEnd { Done, Busy, Space, Failed }

/**
 * Flutter `runAgentPhoneCheck` / engine `PhoneCheck`, over the steps this phone has: install, version, connection,
 * answer, then the proof. It stops at the first step that fails and says which; only a full pass stores the proof.
 */
internal class PhoneAgentCheckRun(
    private val steps: PhoneCheckSteps,
    /** The Paseo version the app pins; the helper must report exactly it. */
    private val pinnedHelper: String,
    /** Whether the helper failure means it needs the app's newer helper rather than another try. */
    private val helperTooOld: (Outcome.Failed) -> Boolean = { false },
) {
    suspend fun run(): PhoneCheckFailure? {
        if (!steps.clearProof()) return PhoneCheckFailure.Storage
        when (steps.install()) {
            InstallEnd.Done -> Unit
            InstallEnd.Busy -> return PhoneCheckFailure.Busy
            InstallEnd.Space -> return PhoneCheckFailure.Storage
            InstallEnd.Failed -> return PhoneCheckFailure.Install
        }
        if (!steps.pinnedRuns()) return PhoneCheckFailure.Version
        val observed =
            when (val answer = steps.helperAnswer()) {
                is Outcome.Failed -> return if (helperTooOld(answer)) PhoneCheckFailure.HelperVersion else PhoneCheckFailure.Daemon
                is Outcome.Ok -> answer.value
            }
        if (observed != pinnedHelper) return PhoneCheckFailure.HelperVersion
        if (!steps.saveProof()) return PhoneCheckFailure.Storage
        return null
    }
}
