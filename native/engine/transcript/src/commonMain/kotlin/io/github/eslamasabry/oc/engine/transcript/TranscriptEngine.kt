package io.github.eslamasabry.oc.engine.transcript

import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.ConnectionScope
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph

/**
 * P1 placeholder for the connection-scoped transcript engine (slice 2). It exists in slice 0 only to prove the S4
 * accessor pattern across modules: Metro merges [TranscriptAccessors] into the ConnectionGraph extension that the app
 * graph generates, although :engine:connection never sees this module.
 */
@SingleIn(ConnectionScope::class)
@Inject
public class TranscriptEngine(private val gateway: ServerGateway) {
    public suspend fun session(id: SessionId): Outcome<Session> = gateway.session(id)
}

@ContributesTo(ConnectionScope::class)
public interface TranscriptAccessors {
    public val transcriptEngine: TranscriptEngine
}

public val ConnectionGraph.transcript: TranscriptEngine
    get() = (this as TranscriptAccessors).transcriptEngine
