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

/**
 * True for the page that rests beside a list while nothing from the list is open (big screens: Home's New
 * conversation). It is not on the back stack: it has nothing to close to, so it shows no Close and does not take Back
 * (Back leaves, as Home does). False everywhere else, including the same page opened over a conversation.
 */
public val LocalRestingBeside: ProvidableCompositionLocal<Boolean> = compositionLocalOf { false }
