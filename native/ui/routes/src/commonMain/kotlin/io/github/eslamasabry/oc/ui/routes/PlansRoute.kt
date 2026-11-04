package io.github.eslamasabry.oc.ui.routes

import kotlinx.serialization.Serializable

/**
 * Usage › Plans: every subscription (Codex, Claude, GLM, MiniMax…) across every connection, with
 * what is left and when it resets (unit P12-BUILD-INTEGRATIONS-B, `:feature:plans`; Flutter
 * `usage_plans_page.dart`).
 */
@Serializable public data object PlansRoute : AppRoute

/** The shared measured-quota alert rules. */
@Serializable public data object PlansAlertsRoute : AppRoute
