package io.github.eslamasabry.oc.ui.kitchat.turn

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.row.KitExpandRow

/**
 * Where a turn's work stands (Dart `KitWorkState`, KIT-12). The host decides; the line only says it.
 * EndedFailed opens by default ([opensByDefault]); WaitingForYou is never a spinner.
 */
public enum class KitWorkState {
    Running,
    WaitingForYou,
    Done,
    EndedFailed,
    Stopped;

    /** Dart `KitWorkLine.opensByDefault`: only work that ended on a failure opens by itself. */
    public val opensByDefault: Boolean
        get() = this == EndedFailed
}

/** Dart `KitWorkLine.stepCap`: an open line shows at most this many steps before "Show earlier steps". */
public const val KIT_WORK_STEP_CAP: Int = 25

/**
 * The one line a turn's work folds under (Dart `KitWorkLine`, plan 09 chat-transcript-turn-model: "work folded under
 * ONE line"). [label] is the summary in the caller's words ("Read 3 files · edited 1", or what is happening now while
 * it runs); [state] reads out with it. Tapping the line (ripple, 48 dp row) or the screen reader's expand / collapse
 * action asks [onToggle]; the caller owns [expanded]. [steps] are drawn only while open, revealed with the kit's one
 * layout motion (reduced motion: instant). [earlier] is "Show N earlier steps" when the open line hides older ones.
 *
 * Text: reading role through [KitExpandRow] (frame-capped by the kit); no frame, no fill: work is quiet.
 */
@Composable
public fun KitWorkLine(
    label: String,
    stateWords: String?,
    state: KitWorkState,
    expanded: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    earlier: KitLineAction? = null,
    steps: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxWidth().widthIn(max = 700.dp).semantics {
            stateDescription = stateWords ?: label
        }
    ) {
        KitExpandRow(
            title = label,
            expanded = expanded,
            supporting = stateWords,
            onExpandedChange = onToggle,
        ) {
            if (earlier != null) KitButton(earlier.label, earlier.onClick, role = KitButtonRole.Tertiary)
            steps()
        }
    }
}
