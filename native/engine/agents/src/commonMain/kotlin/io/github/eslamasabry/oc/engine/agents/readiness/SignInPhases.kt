package io.github.eslamasabry.oc.engine.agents.readiness

import io.github.eslamasabry.oc.engine.agents.host.AuthState

/**
 * The row phase an explicit auth probe outcome gives (test/host_agent_providers_test.dart `phase`). A probe that could
 * not run is not an outcome: callers keep the last answer instead of calling this (plan 17 §1.5).
 */
public fun AuthState.toSignInPhase(): AgentSignInPhase =
    when (this) {
        AuthState.SignedIn -> AgentSignInPhase.SignedIn
        AuthState.SignedOut -> AgentSignInPhase.SignedOut
        AuthState.Error -> AgentSignInPhase.Failed
    }
