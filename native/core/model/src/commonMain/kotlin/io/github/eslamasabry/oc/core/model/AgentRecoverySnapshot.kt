package io.github.eslamasabry.oc.core.model

/**
 * Cached recovery facts for one agent conversation (B-AGENT-166), never a reason to dispatch.
 *
 * [promptStamp] is the last actual user prompt's nonblank timestamp, falling back to creation time
 * for a never-prompted conversation. A title or other metadata edit does not change it. [idle]
 * requires a known idle, closed or failed agent with no active or pending turn; an unknown phase
 * cannot establish idle coverage. Draft and daemon aliases share these facts.
 */
public data class AgentRecoverySnapshot(val promptStamp: String?, val idle: Boolean)
