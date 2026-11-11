package io.github.eslamasabry.oc.feature.servers.presentation

import io.github.eslamasabry.oc.core.common.CertificateWhy
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.SignInWhy
import io.github.eslamasabry.oc.core.model.ServerOrigin

/**
 * The technical text under "Details" for a failed connection check (Flutter shows the raw socket
 * message there): the address that was tried, what was tried, and what happened in words, with the
 * error's type last. It is for the Details fold only, and the caller passes it through the
 * Redactor. [origin] is the normalized host and port, never a typed address, so no credential can
 * be in it.
 */
internal fun checkDetailsText(origin: ServerOrigin?, failure: Failure): String =
    buildList {
            if (origin != null) {
                add("Address: ${origin.host.trim('[', ']')}:${origin.port}")
                add("Tried: a connection over ${origin.scheme} and the server's version check")
            }
            add("Result: ${checkResultWords(failure)}")
            failure.details?.text?.takeIf { it.isNotBlank() }?.let { add("Error: $it") }
        }
        .joinToString("\n")

private fun checkResultWords(failure: Failure): String =
    when (failure) {
        is Failure.TimedOut -> "the server did not answer in time"
        is Failure.Network ->
            when (failure.why) {
                NetworkWhy.Offline -> "this phone has no network connection"
                NetworkWhy.Dns -> "the host name could not be found"
                NetworkWhy.Refused -> "nothing accepted the connection at that address and port"
                NetworkWhy.Reset -> "the connection was cut off before the server answered"
                NetworkWhy.NoRoute -> "there is no route from this phone to that address"
                NetworkWhy.Unknown -> "the server could not be reached"
            }
        is Failure.Certificate ->
            when (failure.why) {
                CertificateWhy.Changed -> "the server's certificate is not the one that was trusted"
                CertificateWhy.Expired -> "the server's certificate is outside its valid dates"
                CertificateWhy.Untrusted -> "the server's certificate is not trusted"
            }
        is Failure.SignIn ->
            when (failure.why) {
                SignInWhy.PasswordMissing,
                SignInWhy.TokenMissing -> "the server asked for a password or token"
                SignInWhy.PasswordRejected,
                SignInWhy.TokenRejected -> "the server did not accept the password or token"
                SignInWhy.HostNotAllowed -> "the server does not allow this host name"
                SignInWhy.Forbidden -> "the server refused this account"
            }
        is Failure.Incompatible -> "the server answered, but not as an OpenCode 2 server"
        is Failure.UnreadableReply -> "the server answered with something the app could not read"
        is Failure.Server -> "the server reported an error" + (failure.status?.let { " (HTTP $it)" } ?: "")
        is Failure.Rejected -> "the server refused the request" + (failure.status?.let { " (HTTP $it)" } ?: "")
        else -> "the check did not finish"
    }
