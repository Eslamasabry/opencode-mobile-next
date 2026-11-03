package io.github.eslamasabry.oc.ui.routes

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.serialization.Serializable

/**
 * Owned by the :feature:projects agent (P12). The projects picker and one project's page. A project is a folder
 * on the server, named by its absolute path (plan 07 §2.2 `ProjectDir`; held as a String until that value class
 * lands in :core:model).
 */
@Serializable
public sealed interface ProjectsRoute : AppRoute {
    /** "Projects": New project, Open a folder, Opened before, then this server's projects. Null = active server. */
    @Serializable public data class Picker(val profile: ProfileId? = null) : ProjectsRoute

    /** One project: New conversation here, its conversations, and "In other projects". */
    @Serializable public data class Project(val profile: ProfileId, val directory: String) : ProjectsRoute
}
