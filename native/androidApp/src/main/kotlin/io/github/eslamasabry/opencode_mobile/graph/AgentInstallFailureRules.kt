package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.engine.agents.install.AgentSetupFailure

/**
 * Why a phone agent install failed, from the setup job's record: its [error] (the failed step's fixed sentence or its
 * last line) and the job's [logTail]. The kind picks the words the Agents page shows; the text itself only ever goes
 * under Details.
 *
 * The error decides first. The log tail only speaks for the unmistakable signs (a full disk, a name that did not
 * resolve): it holds every line of the run, and apt prints "disk space will be used" on every install, which made any
 * failed package step read as "short of space" before.
 */
internal object AgentInstallFailureRules {
    private val storage =
        listOf("free space", "no space", "not enough space", "enospc", "disk full", "disk quota", "storage")
    private val download =
        listOf(
            "download",
            "connection",
            "could not resolve",
            "temporary failure resolving",
            "eai_again",
            "network",
            "timed out",
            "etimedout",
            "econnreset",
            "econnrefused",
        )
    private val tailStorage = listOf("no space left on device", "enospc")
    private val tailDownload =
        listOf("temporary failure resolving", "could not resolve host", "eai_again", "network is unreachable")

    /** The setup job's own code when it could not write its record: on a phone that is a full disk. */
    const val PERSISTENCE_CODE: String = "setup_persistence"

    fun of(error: String?, logTail: String?, code: String? = null): AgentSetupFailure {
        val said = error.orEmpty().lowercase()
        val tail = logTail.orEmpty().lowercase()
        return when {
            code == PERSISTENCE_CODE -> AgentSetupFailure.Storage
            "terminated with signal" in said -> AgentSetupFailure.Interrupted
            "checksum" in said -> AgentSetupFailure.Checksum
            storage.any { it in said } -> AgentSetupFailure.Storage
            "version" in said -> AgentSetupFailure.Version
            download.any { it in said } -> AgentSetupFailure.Download
            tailStorage.any { it in tail } -> AgentSetupFailure.Storage
            tailDownload.any { it in tail } -> AgentSetupFailure.Download
            // After the clear signs only: a proot signal an earlier, retried attempt left in the log is not the cause
            // of a later full disk or failed lookup.
            "terminated with signal" in tail -> AgentSetupFailure.Interrupted
            "checksum" in tail -> AgentSetupFailure.Checksum
            else -> AgentSetupFailure.Unknown
        }
    }
}
