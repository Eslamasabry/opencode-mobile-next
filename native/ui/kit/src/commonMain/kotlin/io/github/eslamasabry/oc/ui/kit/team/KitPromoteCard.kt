package io.github.eslamasabry.oc.ui.kit.team

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth

/**
 * PromoteCard: a team card whose status line stays neutral whatever the state (Dart `KitPromoteCard`).
 *
 * States: loading, empty, error, working, disabled (stale), answered, needs you, stalled
 * Text: reading
 * Cap: Reading (720)
 * Motion: none of its own
 * Replaces: KitPromoteCard (lib/ui/kit/team/kit_promote_card.dart)
 */
@Composable
public fun KitPromoteCard(
    title: String,
    status: String,
    modifier: Modifier = Modifier,
    state: KitTeamState = KitTeamState.Done,
    summary: String? = null,
    items: List<KitTeamItem> = emptyList(),
    primary: KitAction? = null,
    actions: List<KitAction> = emptyList(),
) {
    Box(modifier.kitMaxWidth(CapRole.Reading).fillMaxWidth()) {
        KitTeamCard(title, status, state, summary = summary, items = items, primary = primary, actions = actions, neutralStatus = true)
    }
}
