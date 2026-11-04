package io.github.eslamasabry.oc.core.domain

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId

/**
 * Session permission modes supported by a server. The connection installs its saved approval
 * resolver before a conversation dispatches; each prompt, command and shell reads it again before
 * preparing the server policy. Switching modes during a reply also reconciles the server policy,
 * with readback before success is reported.
 */
public interface SessionPermissionModeGateway {
    public fun setAutomaticApprovalResolver(resolver: (SessionId) -> Boolean)

    public suspend fun syncSessionPermissions(session: SessionId): Outcome<Unit>
}
