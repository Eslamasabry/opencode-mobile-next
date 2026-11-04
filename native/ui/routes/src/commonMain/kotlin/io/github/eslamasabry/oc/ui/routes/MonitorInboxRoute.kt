package io.github.eslamasabry.oc.ui.routes

import kotlinx.serialization.Serializable

/**
 * Optional opaque OS-alert identity, resolved afresh; no request text or server address is
 * serialized.
 */
@Serializable
public data class MonitorInboxRoute(
    val profileId: String? = null,
    val sessionId: String? = null,
    val token: String? = null,
) : AppRoute
