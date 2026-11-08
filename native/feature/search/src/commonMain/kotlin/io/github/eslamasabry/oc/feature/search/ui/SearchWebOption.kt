package io.github.eslamasabry.oc.feature.search.ui

/** The Web chip: shown only when the server can search the web (or its panel is open), and what tapping it does. */
public data class SearchWebOption(val open: Boolean, val onToggle: () -> Unit)
