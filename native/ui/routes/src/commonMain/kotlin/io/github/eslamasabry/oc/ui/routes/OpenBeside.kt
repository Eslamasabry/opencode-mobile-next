package io.github.eslamasabry.oc.ui.routes

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.navigation3.runtime.NavKey

/**
 * The page open beside a list, as the list sees it (big screens): on a window that shows a list and the page opened
 * from it side by side, the shell provides that page's route to the list, so the list can show its row as the chosen
 * one (Home: the open conversation's row). Null on a phone and wherever the list stands alone: nothing is chosen.
 */
public val LocalOpenBeside: ProvidableCompositionLocal<NavKey?> = compositionLocalOf { null }
