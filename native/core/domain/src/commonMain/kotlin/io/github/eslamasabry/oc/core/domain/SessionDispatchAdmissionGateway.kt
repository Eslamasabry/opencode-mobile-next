package io.github.eslamasabry.oc.core.domain

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId

/**
 * Runtime admission for a human conversation on an app-owned server. Admission finishes before any
 * session preparation request; a refusal sends nothing. Every admitted dispatch settles once,
 * including an uncertain reply. Settlement is bookkeeping only and must never replay the dispatch
 * or declare that the server is idle.
 */
public interface SessionDispatchAdmissionGateway {
    public fun setSessionDispatchAdmission(
        admit: suspend (SessionId) -> Outcome<Unit>,
        settled: (SessionId) -> Unit,
    )
}
