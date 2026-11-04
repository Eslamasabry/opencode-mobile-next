package io.github.eslamasabry.oc.ui.routes

import kotlinx.serialization.Serializable

/** App-owned prompts kept during server removal, reachable without a selected server. */
@Serializable public data object KeptPromptsRoute : AppRoute
