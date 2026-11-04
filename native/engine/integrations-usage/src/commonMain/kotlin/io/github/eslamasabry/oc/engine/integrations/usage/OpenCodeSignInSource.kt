package io.github.eslamasabry.oc.engine.integrations.usage

import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** A saved OpenCode server as the sign-in source sees it. */
public data class SignInServer(
    val id: String,
    val isOpenCode: Boolean,
    /** The stored password could not be read back: the server is skipped, never contacted. */
    val requiresPasswordReentry: Boolean = false,
)

/** One connected provider as the server lists it (id and display name only; never keys). */
public data class ConnectedProvider(val id: String, val name: String)

/**
 * OpenCode's connected providers as sign-ins (Flutter `OpenCodeSignInSource`). A server that answered is
 * authoritative (a provider missing from its list is signed out there); one that did not answer is unreached; a server
 * that needs its password again is skipped without a request.
 */
public class OpenCodeSignInSource(
    private val servers: () -> List<SignInServer>,
    private val isReadable: (String) -> Boolean,
    private val providers: suspend (SignInServer) -> Outcome<List<ConnectedProvider>>,
    private val timeout: Duration = 10.seconds,
) : UsageSource {
    override suspend fun read(context: UsageReadContext): Outcome<UsageSourceResult> {
        val signIns = ArrayList<UsageSignIn>()
        val authoritative = LinkedHashSet<String>()
        val unreached = LinkedHashSet<String>()
        for (server in servers()) {
            if (!server.isOpenCode || !isReadable(server.id) || server.requiresPasswordReentry) continue
            val answer = withTimeoutOrNull(timeout) { providers(server) }
            if (answer !is Outcome.Ok) {
                unreached += server.id
                continue
            }
            for (p in answer.value) {
                val id = canonicalUsageProviderId(p.id) ?: continue
                signIns += UsageSignIn(providerId = id, connectionId = server.id, displayName = display(id, p.name))
            }
            authoritative += server.id
        }
        return Outcome.Ok(UsageSourceResult(signIns = signIns, authoritativeSignIns = authoritative, unreached = unreached))
    }

    private fun display(id: String, name: String): String =
        when (id) {
            "codex" -> "Codex"
            "glm" -> "GLM"
            "minimax" -> "MiniMax"
            "claude" -> "Claude"
            else -> cleanUsageText(name, 24) ?: id
        }
}
