package io.github.eslamasabry.oc.ui.routes

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.serialization.Serializable

/** Settings › Agents › one Paseo computer: update its agents, import Claude Code conversations (:feature:computer). */
@Serializable
public data class ComputerRoute(val profile: ProfileId) : AppRoute
