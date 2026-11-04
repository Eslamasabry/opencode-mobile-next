package io.github.eslamasabry.oc.engine.connection

import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** Device-local read truth. Sharing to the server is a separate, consent-gated operation. */
public data class SessionRead(val idleMs: Long = 0, val viewedMs: Long = 0) {
    public val finishedUnseen: Boolean
        get() = idleMs > viewedMs
}

public class ConnectionReadState {
    private val mutable = MutableStateFlow<Map<SessionId, SessionRead>>(emptyMap())
    public val state: StateFlow<Map<SessionId, SessionRead>> = mutable

    private val localViewed = HashMap<SessionId, Long>()

    public fun observe(session: Session, sharing: Boolean = false) {
        mutable.update { all ->
            val old = all[session.id] ?: SessionRead()
            all +
                (session.id to
                    SessionRead(
                        maxOf(old.idleMs, session.time?.idleMs ?: 0),
                        maxOf(
                            localViewed[session.id] ?: 0,
                            if (sharing) session.time?.viewedMs ?: 0 else 0,
                        ),
                    ))
        }
    }

    /** Acknowledge exactly the displayed idle transition, never a wall-clock guess. */
    public fun markRead(session: SessionId, idleMs: Long) {
        localViewed[session] =
            maxOf(
                localViewed[session] ?: 0,
                idleMs.coerceAtMost(mutable.value[session]?.idleMs ?: 0),
            )
        mutable.update { all ->
            val old = all[session] ?: SessionRead()
            all +
                (session to
                    old.copy(viewedMs = maxOf(old.viewedMs, idleMs.coerceAtMost(old.idleMs))))
        }
    }
}
