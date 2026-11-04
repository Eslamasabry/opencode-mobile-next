package io.github.eslamasabry.oc.ui.routes

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import kotlinx.serialization.Serializable

/** The connected server's tools; a conversation target also permits skill activation. */
@Serializable
public data class ToolsRoute(val profile: ProfileId, val session: SessionId? = null) : AppRoute
