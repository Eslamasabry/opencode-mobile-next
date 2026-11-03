package io.github.eslamasabry.oc.ui.routes

import kotlinx.serialization.Serializable

/**
 * Settings opened at one row or one place inside a page (B-NAV-070): [tag] is the same stable id settings search uses
 * (`settings-language`, `inside-notifications-quiet`, …). A tag that does not exist on this phone or server opens the
 * hub, never a dead page.
 */
@Serializable
public data class SettingsPlaceRoute(val tag: String) : AppRoute
