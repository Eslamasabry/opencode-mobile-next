package io.github.eslamasabry.oc.ui.kit.row

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics

/**
 * The kit's chosen face (the one a [KitRow] shows when `selected`, as the connected server in Servers) for a row part
 * that has no `selected` of its own: put it first on the row's modifier. Big screens: Home marks the conversation open
 * beside the list with it. Not chosen draws nothing, so a list without a choice (every phone) looks as before.
 */
@Composable
public fun Modifier.kitChosenRow(chosen: Boolean): Modifier =
    // One chain whether chosen or not, so the row keeps its nodes when the choice moves.
    kitChosenFace(chosen).semantics { if (chosen) selected = true }
