package io.github.eslamasabry.oc.ui.kit.state

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitLastKnownHint
import io.github.eslamasabry.oc.ui.words.res.kitLastKnownRefreshing
import org.jetbrains.compose.resources.stringResource

/**
 * One row of [KitLastKnown] (Dart `KitLastKnownRow`): a label remembered from the last time the list was read (shown as
 * written: it was redacted when saved), and an optional short fact under it ("5m ago"). [tag] is a test handle.
 */
@Immutable
public data class KitLastKnownRow(val title: String, val detail: String? = null, val tag: String? = null)

/** The label of a [KitLastKnown] group: how old the rows are, plus "Refreshing" while a fresh read is under way. */
public object KitLastKnownRules {
    public fun label(updated: String, refreshing: Boolean, refreshingWords: (String) -> String): String =
        if (refreshing) refreshingWords(updated) else updated
}

/**
 * What a list held the last time it was read, shown read-only while the live answer is on its way (Dart
 * `KitLastKnown`), so a page opens with the person's own labels instead of placeholders. A labelled [KitRowGroup]: the
 * label says how old the rows are ([updated], "Updated 5 minutes ago") and, while [refreshing], that a fresh read is
 * under way. The rows have no tap, menu or swipe and no running, waiting or pinned marks: remembered labels prove
 * nothing about what exists or runs now. The screen's own loading bar carries progress; this part draws no spinner and
 * runs no ticker. The live list replaces it as soon as it arrives, including a live list that is empty.
 *
 * States: loading
 * Text: reading
 * Cap: List
 * Motion: none
 * Hidden: none
 * Replaces: KitLastKnown, KitLastKnownRow (lib/ui/kit/kit_last_known.dart)
 */
@Composable
public fun KitLastKnown(
    rows: List<KitLastKnownRow>,
    updated: String,
    modifier: Modifier = Modifier,
    refreshing: Boolean = true,
) {
    val refreshingWords = stringResource(Res.string.kitLastKnownRefreshing, updated)
    val label = KitLastKnownRules.label(updated, refreshing) { refreshingWords }
    // Said once for the group: these rows are a memory, not the list.
    val hint = stringResource(Res.string.kitLastKnownHint)
    KitRowGroup(
        modifier.kitMaxWidth(CapRole.List).fillMaxWidth().semantics { stateDescription = hint }.testTag("kit-last-known"),
        label = label,
    ) {
        rows.forEach { row ->
            KitRow(
                row.title,
                if (row.tag != null) Modifier.testTag(row.tag) else Modifier,
                supporting = row.detail,
                titleMaxLines = 2, // F: lib/ui/kit/kit_last_known.dart:80
            )
        }
    }
}
