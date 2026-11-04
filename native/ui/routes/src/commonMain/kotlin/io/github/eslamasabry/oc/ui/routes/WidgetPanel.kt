package io.github.eslamasabry.oc.ui.routes

import kotlinx.serialization.Serializable

/** Isolated home-screen widget windows (plan 07 M01–M03). */
@Serializable
public sealed interface WidgetPanel : AppRoute {
    public val widgetId: Int

    @Serializable public data class Style(override val widgetId: Int) : WidgetPanel

    @Serializable public data class Project(override val widgetId: Int) : WidgetPanel

    @Serializable
    public data class Steer(override val widgetId: Int, val voice: Boolean = false) : WidgetPanel
}
