package io.github.eslamasabry.oc.engine.agents.phoneagents

import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.catalog.AgentDescriptor
import io.github.eslamasabry.oc.engine.agents.catalog.AgentSignInMethod
import io.github.eslamasabry.oc.engine.agents.host.AuthProjection
import io.github.eslamasabry.oc.engine.agents.host.AuthState
import io.github.eslamasabry.oc.engine.agents.readiness.AgentSignInPhase
import io.github.eslamasabry.oc.engine.agents.readiness.PhoneAgentRuntime
import io.github.eslamasabry.oc.engine.agents.readiness.toSignInPhase

/**
 * The sign-in step of a row refresh (port of the `_paRefreshRows` inspection branch, lib/state/connection/
 * phone_agents.dart). The row's sign-in comes from the agent itself, not from the host's inspection:
 *
 * - An auth-capable host is always asked for an installed agent that signs in, even when the inspection already says
 *   signed in (that phase may be stale).
 * - A legacy host (no auth bridge) is asked only when its inspection has no sign-in truth (`signInPhase == null`);
 *   the read then is the native status ([io.github.eslamasabry.oc.engine.agents.auth.AgentAuth] falls back to
 *   `legacySignInStatus` for Claude when the bridge answers `ProbeUnsupported`). A legacy host that inspected a phase
 *   keeps it without any native read.
 * - A signed-in answer keeps an inspected usage limit (`LimitReached`); otherwise the answer's phase wins.
 *
 * [read] is the caller's auth read (`AgentAuth.refresh` for a list refresh, which owns reuse of recent answers).
 */
public object PhoneAgentSignInRead {
    public fun needsRead(descriptor: AgentDescriptor, runtime: PhoneAgentRuntime, hostAnswersAuth: Boolean): Boolean =
        runtime.installed && descriptor.signInMethod != AgentSignInMethod.None &&
            (hostAnswersAuth || runtime.signInPhase == null)

    public fun phase(auth: AuthProjection, inspected: AgentSignInPhase?): AgentSignInPhase =
        if (auth.state == AuthState.SignedIn && inspected == AgentSignInPhase.LimitReached) {
            AgentSignInPhase.LimitReached
        } else {
            auth.state.toSignInPhase()
        }

    public suspend fun apply(
        descriptor: AgentDescriptor,
        runtime: PhoneAgentRuntime,
        hostAnswersAuth: Boolean,
        read: suspend (AgentId) -> AuthProjection,
    ): PhoneAgentRuntime {
        if (!needsRead(descriptor, runtime, hostAnswersAuth)) return runtime
        val auth = read(descriptor.id)
        return runtime.copy(signInPhase = phase(auth, runtime.signInPhase))
    }
}
