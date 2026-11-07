package io.github.eslamasabry.oc.ui.kit.team

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth

/**
 * ProjectRow (Dart `KitProjectRow`). A flat row with actual counted progress, never guessed progress: the bar shows only for
 * 0 ≤ [completed] ≤ [total] with [total] above zero.
 *
 * States: disabled (stale), loading, empty, error, working, answered, needs you, stalled
 * Text: reading
 * Cap: List (960)
 * Motion: press feedback only (KitTappable)
 * Replaces: KitProjectRow (lib/ui/kit/team/kit_project_row.dart)
 */
@Composable
public fun KitProjectRow(
    title: String,
    status: String,
    modifier: Modifier = Modifier,
    state: KitTeamState = KitTeamState.Done,
    detail: String? = null,
    meta: String? = null,
    onPressed: (() -> Unit)? = null,
    completed: Int? = null,
    total: Int? = null,
) {
    Box(modifier.kitMaxWidth(CapRole.List).fillMaxWidth()) {
        KitTeamRow(title, status, state = state, detail = detail, meta = meta, onPressed = onPressed, completed = completed, total = total)
    }
}
