package io.github.eslamasabry.opencode_mobile

import java.security.MessageDigest

/**
 * The pure parts of installing the app's own update (channel `oc/app_update`,
 * docs/design/app-updates-2026-10-31.md): which Dart state a PackageInstaller
 * status means, and how signing certificates are written down.
 */
internal object AppUpdatePolicy {
    private const val STATUS_PENDING_USER_ACTION = -1
    private const val STATUS_SUCCESS = 0
    private const val STATUS_FAILURE_ABORTED = 3

    /** The `state` the channel reports for a PackageInstaller status code. */
    fun stateFor(status: Int): String = when (status) {
        STATUS_PENDING_USER_ACTION -> "pendingUser"
        STATUS_SUCCESS -> "success"
        STATUS_FAILURE_ABORTED -> "aborted"
        else -> "failure"
    }

    /** Uppercase hex of the SHA-256 of one certificate, no separators. */
    fun sha256Hex(certificate: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(certificate)
            .joinToString("") { "%02X".format(it) }

    /** Several signers are sorted and joined with a comma; one is itself. */
    fun signerString(certificates: List<ByteArray>): String =
        certificates.map(::sha256Hex).sorted().joinToString(",")
}
