package io.github.eslamasabry.oc.ui.kit.team

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons

/** The rules of [KitPlanCard] that are not drawing. */
public object KitPlanRules {
    /** Above this text scale a task's "who" moves under its words instead of beside them. */
    public const val LARGE_TEXT: Float = 1.3f // F: lib/ui/kit/team/kit_plan_card.dart:92

    /** The phase heading: its title, then the worded review point when there is one. */
    public fun phaseHeading(phase: KitPlanPhase): String =
        phase.flagLabel?.let { phase.title + " · " + it } ?: phase.title

    /** A flagged phase reads as needing attention; a plain one stays secondary. */
    public fun phaseTone(phase: KitPlanPhase): TextTone = if (phase.flagLabel == null) TextTone.Secondary else TextTone.Attention
}

/**
 * PlanCard: the plan awaiting a person's answer, or a plain summary card (Dart `KitPlanCard`). With [phases] it shows
 * numbered tasks grouped by phase, each with a one-line detail and who does it, then [more] when the list is cut short.
 * [primary] is the card's one primary; [secondary] is the neutral "not now" answer beside it.
 *
 * States: loading, empty, error, working, disabled (stale), answered, needs you, stalled
 * Text: reading
 * Cap: Reading (720)
 * Motion: press feedback only (KitTappable)
 * Replaces: KitPlanCard (lib/ui/kit/team/kit_plan_card.dart)
 */
@Composable
public fun KitPlanCard(
    title: String,
    status: String,
    modifier: Modifier = Modifier,
    state: KitTeamState = KitTeamState.Done,
    summary: String? = null,
    items: List<KitTeamItem> = emptyList(),
    phases: List<KitPlanPhase> = emptyList(),
    more: String? = null,
    primary: KitAction? = null,
    secondary: KitAction? = null,
    actions: List<KitAction> = emptyList(),
) {
    Box(modifier.kitMaxWidth(CapRole.Reading).fillMaxWidth()) {
        TeamCard(
            title,
            status,
            state,
            summary,
            items,
            primary,
            secondary,
            actions,
            flat = false,
            neutralStatus = phases.isNotEmpty(),
        ) {
            phases.forEach { phase ->
                Spacer(Modifier.height(Space.S3.dp))
                KitText(KitPlanRules.phaseHeading(phase), role = TypeRole.Label, tone = KitPlanRules.phaseTone(phase))
                phase.tasks.forEach { PlanTaskRow(it) }
            }
            if (more != null) {
                Spacer(Modifier.height(Space.S2.dp))
                KitText(more, role = TypeRole.Secondary)
            }
        }
    }
}

@Composable
private fun PlanTaskRow(task: KitPlanTask) {
    val large = LocalDensity.current.fontScale > KitPlanRules.LARGE_TEXT
    val name = task.who
    val align = if (large) TextAlign.Start else TextAlign.End
    val who: (@Composable (Modifier) -> Unit)? =
        if (name == null) null
        else @Composable { modifier: Modifier -> PlanWho(name, task.onChangeWho, align, modifier) }
    Row(Modifier.fillMaxWidth().padding(vertical = Space.S2.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.width(Space.S5.dp)) { KitText(task.number.toString(), role = TypeRole.Secondary) }
        Column(Modifier.weight(1f)) {
            KitText(task.title, role = TypeRole.RowTitle)
            task.detail?.let { KitText(it, role = TypeRole.Secondary) }
            if (large && who != null) who(Modifier)
        }
        if (!large && who != null) {
            Spacer(Modifier.width(Space.S2.dp))
            who(Modifier.weight(1f, fill = false))
        }
    }
}

/** Who does a task: plain words, or a neutral button with a chevron when [onChange] lets the person choose. */
@Composable
private fun PlanWho(name: String, onChange: (() -> Unit)?, align: TextAlign, modifier: Modifier) {
    if (onChange == null) {
        KitText(name, modifier, role = TypeRole.Secondary, textAlign = align)
        return
    }
    KitTappable(onChange, modifier.semantics { contentDescription = name }) {
        Row(Modifier.padding(vertical = Space.S2.dp), verticalAlignment = Alignment.CenterVertically) {
            KitText(name, Modifier.weight(1f, fill = false), role = TypeRole.Secondary, textAlign = align)
            Spacer(Modifier.width(Space.S1.dp))
            KitIcon(KitIcons.chevronDown, size = KitIconSize.Small)
        }
    }
}
