package io.github.eslamasabry.oc.ui.routes

import kotlinx.serialization.Serializable

/** Settings › Report a problem › Everything this phone kept (owned by :feature:diagnostics). */
@Serializable
public sealed interface DiagnosticsRoute : AppRoute {
    @Serializable public data object Kept : DiagnosticsRoute
}
