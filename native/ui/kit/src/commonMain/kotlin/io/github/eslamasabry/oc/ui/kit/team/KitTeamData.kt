package io.github.eslamasabry.oc.ui.kit.team

import androidx.compose.runtime.Immutable

/**
 * The presentation state of a team part (Dart `KitTeamState`, lib/ui/kit/team/kit_team_data.dart); the caller supplies
 * the localized explanation and age. The order is Flutter's.
 */
public enum class KitTeamState { Empty, Loading, Running, NeedsYou, Stalled, Failed, Done, Stale }

/**
 * One row inside a team card (Dart `KitTeamItem`). [trailing] is a short value at the row's end ("See changes"); the
 * chevron follows it when the row opens something ([onPressed] set).
 */
@Immutable
public data class KitTeamItem(
    val title: String,
    val detail: String? = null,
    val meta: String? = null,
    val trailing: String? = null,
    val state: KitTeamState = KitTeamState.Done,
    val onPressed: (() -> Unit)? = null,
)

/** How serious a review finding is (Dart `KitFindingSeverity`), in Flutter's order. */
public enum class KitFindingSeverity { Critical, Major, Minor }

/** One review finding the person can pick (Dart `KitTeamFinding`); [severityLabel] is the caller's localized word. */
@Immutable
public data class KitTeamFinding(
    val id: String,
    val title: String,
    val severityLabel: String,
    val severity: KitFindingSeverity = KitFindingSeverity.Minor,
    val detail: String? = null,
    val location: String? = null,
    val selected: Boolean = false,
    val onChanged: ((Boolean) -> Unit)? = null,
)

/**
 * One task line of a plan: number, title, a one-line detail, and who does it (Dart `KitPlanTask`). When
 * [onChangeWho] is given, [who] is drawn as a neutral button that calls it (for example to choose the task's server);
 * null leaves it plain text.
 */
@Immutable
public data class KitPlanTask(
    val number: Int,
    val title: String,
    val detail: String? = null,
    val who: String? = null,
    val onChangeWho: (() -> Unit)? = null,
)

/** A named group of plan tasks; [flagLabel] carries the worded review point (Dart `KitPlanPhase`). */
@Immutable
public data class KitPlanPhase(
    val title: String,
    val tasks: List<KitPlanTask>,
    val flagLabel: String? = null,
)
