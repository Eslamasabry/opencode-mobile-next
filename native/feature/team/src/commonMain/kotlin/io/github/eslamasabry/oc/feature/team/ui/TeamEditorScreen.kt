package io.github.eslamasabry.oc.feature.team.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.team.TeamPhase
import io.github.eslamasabry.oc.engine.team.TeamSpec
import io.github.eslamasabry.oc.feature.team.presentation.CostLine
import io.github.eslamasabry.oc.feature.team.presentation.EditorKind
import io.github.eslamasabry.oc.feature.team.presentation.TeamEditorActions
import io.github.eslamasabry.oc.feature.team.presentation.TeamEditorUi
import io.github.eslamasabry.oc.feature.team.presentation.TeamRules
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextRole
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.choice.KitChoice
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceGroup
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.row.KitExpandRow
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.state.KitTechnicalValue
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/**
 * One project editor (Dart `_Editor`, team_project_editors.dart), as a page: new project and quick task, the spec, the
 * plan, a project's settings, the team's roles and defaults. The pinned primary saves; what the last tap produced
 * (the progress line, a refusal and its way forward) sits at the top.
 */
@Composable
public fun TeamEditorScreen(state: TeamEditorUi, actions: TeamEditorActions) {
    val kind = state.kind
    val title =
        stringResource(
            when (kind) {
                EditorKind.Create -> Res.string.teamProjectEditorNewProject
                EditorKind.Quick -> Res.string.teamProjectEditorQuickTask
                EditorKind.Spec -> Res.string.teamProjectEditorSpec
                EditorKind.Plan -> Res.string.teamProjectEditorPlan
                EditorKind.Settings -> Res.string.teamProjectEditorSettings
                EditorKind.Roles -> Res.string.teamProjectEditorRoles
                EditorKind.Defaults -> Res.string.teamProjectEditorDefaults
            }
        )
    val creationError = state.creationError?.let { problemWords(it) }
    val blocked = if (state.blocked) stringResource(Res.string.phoneTeamBlocked) else null
    val busy = state.working || state.restoring
    KitScreen(
        topBar = {
            KitTopBar(
                title = title,
                exit = KitTopBarExit.Close,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
            )
        },
        loading = state.phase == TeamPhase.Loading || busy,
        loadingLabel = stringResource(Res.string.teamProjectLoad),
        width = KitScreenWidth.Reading,
        bottom =
            if (!state.hasPrimary || !state.found || state.phase != TeamPhase.Ready) null
            else
                ({
                    val reason = blocked ?: creationError
                    KitActionBlock(
                        Modifier.padding(vertical = Space.S2.dp),
                        primary =
                            KitAction(
                                primaryLabel(state),
                                actions::save,
                                enabled =
                                    when {
                                        reason != null -> KitEnabled.Disabled(reason)
                                        busy -> KitEnabled.Disabled(stringResource(Res.string.teamProjectLoad))
                                        else -> KitEnabled.Enabled
                                    },
                                working = state.working,
                            ),
                        secondary =
                            if (kind == EditorKind.Spec)
                                KitAction(
                                    stringResource(Res.string.teamProjectEditorSaveDraft),
                                    actions::saveDraft,
                                    enabled = if (busy) KitEnabled.Disabled(stringResource(Res.string.teamProjectLoad)) else KitEnabled.Enabled,
                                )
                            else null,
                    )
                }),
    ) {
        when {
            state.phase == TeamPhase.Loading -> Unit
            !state.found -> KitStateView(KitStateKind.Empty, stringResource(Res.string.teamProjectSelect))
            else -> Form(state, actions, blocked)
        }
    }
    KitConfirm(
        spec =
            if (!state.confirmReload) null
            else
                KitConfirmSpec(
                    title = stringResource(Res.string.teamProjectEditorReloadConfirmTitle),
                    confirmLabel = stringResource(Res.string.teamProjectEditorReload),
                    kind = KitConfirmKind.Discard,
                    body = stringResource(Res.string.teamProjectEditorDiscardDraft),
                    cancelLabel = stringResource(Res.string.kitConfirmCancel),
                ),
        onConfirm = actions::confirmReload,
        onCancel = actions::cancelReload,
    )
}

@Composable
private fun primaryLabel(state: TeamEditorUi): String =
    stringResource(
        when (state.kind) {
            EditorKind.Create -> Res.string.teamProjectEditorStartPlanning
            EditorKind.Quick ->
                if (state.form.planFirst) Res.string.teamProjectEditorStartPlanning else Res.string.teamProjectEditorStartTask
            EditorKind.Spec -> Res.string.teamProjectEditorApproveSpec
            EditorKind.Plan ->
                if (state.projectStatus == "plan") Res.string.teamProjectEditorApprovePlan else Res.string.teamProjectEditorApplyPlan
            else -> Res.string.teamProjectEditorSave
        }
    )

/** The form's text field under Dart's [key]; reseeded when the editor rewrites fields ([TeamEditorUi.generation]). */
@Composable
private fun Field(
    state: TeamEditorUi,
    actions: TeamEditorActions,
    key: String,
    label: String,
    kind: KitFieldKind = KitFieldKind.Text,
    helper: String? = null,
) {
    key(state.generation, key) {
        val field = remember { TextFieldState(state.form.text(key)) }
        KitField(
            field,
            label,
            Modifier.testTag("team-field-$key"),
            kind = kind,
            helper = helper,
            enabled = if (state.working) KitEnabled.Disabled(stringResource(Res.string.teamProjectLoad)) else KitEnabled.Enabled,
            onTextChanged = { actions.setText(key, it) },
        )
    }
}

/** Dart `_choice`: a label and one choice list, or the "no options" line. */
@Composable
private fun Choice(label: String, options: List<Pair<String, String>>, selected: String?, onSelect: (String) -> Unit) {
    KitSectionLabel(label)
    if (options.isEmpty()) {
        KitNotice(stringResource(Res.string.teamProjectEditorNoOptions))
    } else {
        KitChoiceGroup(
            choices = options.map { (id, title) -> KitChoice(id, title) },
            selected = setOfNotNull(selected),
            semanticsLabel = label,
            onSelect = onSelect,
        )
    }
}

@Composable
private fun Tertiary(label: String, state: TeamEditorUi, destructive: Boolean = false, run: () -> Unit) {
    KitButton(
        label,
        run,
        role = KitButtonRole.Tertiary,
        destructive = destructive,
        enabled = if (state.working) KitEnabled.Disabled(stringResource(Res.string.teamProjectLoad)) else KitEnabled.Enabled,
    )
}

@Composable
private fun Form(state: TeamEditorUi, actions: TeamEditorActions, blocked: String?) {
    val kind = state.kind
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = Space.S2.dp, bottom = Space.S6.dp),
        verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
    ) {
        if (blocked != null) KitNotice(blocked, title = stringResource(Res.string.phoneTeamBlockedTitle))
        Notices(state, actions)
        if (state.changedElsewhere) Tertiary(stringResource(Res.string.teamProjectEditorReload), state, run = actions::askReload)
        if (state.restoring) return@Column
        if (kind.creating) Creation(state, actions)
        if (kind.creating || kind == EditorKind.Settings || kind == EditorKind.Defaults) SettingsFields(state, actions)
        if (kind == EditorKind.Spec) SpecFields(state, actions)
        if (kind == EditorKind.Plan) PlanFields(state, actions)
        if (kind == EditorKind.Roles) RoleFields(state, actions)
    }
}

/** The refusal or problem, the progress line while starting, and the restored draft's Start fresh. */
@Composable
private fun Notices(state: TeamEditorUi, actions: TeamEditorActions) {
    val refusal = state.refusal
    if (refusal != null) {
        val words = refusalWords(refusal.action, refusal.code)
        KitNotice(
            words.message,
            Modifier.testTag("team-refusal"),
            notes =
                listOfNotNull(
                    words.next,
                    stringResource(Res.string.teamProjectEditorFirstCommitManual).takeIf { refusal.code == "repository_empty" },
                ),
            actions = listOf(KitLineAction(stringResource(Res.string.teamProjectRetry), actions::save)),
        )
        KitDetailsFold(
            label = stringResource(Res.string.kitDetails),
            copyLabel = stringResource(Res.string.kitCopyDetails),
            values = listOf(KitTechnicalValue(stringResource(Res.string.teamRefusalCode), LocalKitRedactor.current.details(words.code))),
        )
    } else {
        state.problem?.let { KitNotice(problemWords(it), Modifier.testTag("team-editor-problem")) }
    }
    if (state.working && state.kind.creating) {
        KitNotice(stringResource(Res.string.teamProjectEditorStarting), Modifier.testTag("team-starting"), tone = StatusTone.Progress)
    }
    if (state.draftRestored && !state.working) {
        KitNotice(
            stringResource(Res.string.teamProjectEditorDraftRestored),
            Modifier.testTag("team-draft-restored"),
            actions = listOf(KitLineAction(stringResource(Res.string.teamProjectEditorStartFresh), actions::startFresh)),
        )
    }
}

/** Dart `_creation`: the goal, the repos and where they run; for a quick task, its role and Plan first. */
@Composable
private fun Creation(state: TeamEditorUi, actions: TeamEditorActions) {
    val f = state.form
    Field(state, actions, "goal", stringResource(Res.string.teamProjectEditorGoalLabel), KitFieldKind.Multiline)
    KitSectionLabel(stringResource(Res.string.teamProjectEditorRepos))
    f.repos.forEach { r ->
        KitRow(
            title = r.name,
            supporting = state.servers.firstOrNull { it.id == r.serverId }?.name,
            trailing = KitRowTrailing.Action(KitLineAction(stringResource(Res.string.teamProjectEditorRemove), { actions.removeRepo(r.id) })),
        )
    }
    Field(state, actions, "repoName", stringResource(Res.string.teamProjectEditorRepoName))
    // The phone's folder browser is not in native yet: the path is typed (Flutter's second way, and its only one
    // where there is no browser).
    Field(state, actions, "repoPath", stringResource(Res.string.teamProjectEditorRepoPath), KitFieldKind.Path)
    Choice(stringResource(Res.string.teamProjectEditorWhereRuns), state.servers.map { it.id to it.name }, f.serverId, actions::chooseServer)
    Tertiary(stringResource(Res.string.teamProjectEditorAddRepo), state, run = actions::addRepo)
    if (state.kind == EditorKind.Quick) {
        Choice(stringResource(Res.string.teamProjectEditorRole), state.roles.map { it.id to it.name }, f.roleId, actions::chooseRole)
        KitSwitchRow(stringResource(Res.string.teamProjectEditorPlanFirst), f.planFirst, onCheckedChange = actions::setPlanFirst)
    }
}

@Composable
private fun costWords(cost: CostLine): String {
    val phone = stringResource(Res.string.teamProjectEditorThisPhone)
    return when (cost) {
        CostLine.NoHost -> stringResource(Res.string.teamProjectEditorCostNoHost)
        is CostLine.NotMeasured -> stringResource(Res.string.teamProjectEditorCostNotMeasured, if (cost.phone) phone else cost.name)
        is CostLine.Measured ->
            stringResource(Res.string.teamProjectEditorCostMeasured, if (cost.phone) phone else cost.name, cost.memoryMb.toString())
    }
}

/** Dart `_settingsFields`: a new project shows what it must decide first (mode, review, budget) and folds the rest. */
@Composable
private fun SettingsFields(state: TeamEditorUi, actions: TeamEditorActions) {
    val f = state.form
    val s = f.settings
    @Composable
    fun mode() {
        Choice(
            stringResource(Res.string.teamProjectEditorMode),
            listOf(
                "single" to stringResource(Res.string.teamProjectEditorSingle),
                "parallel" to stringResource(Res.string.teamProjectEditorParallel),
            ),
            s.mode.ifEmpty { null },
            actions::setMode,
        )
        if (s.mode == "parallel") Field(state, actions, "lanes", stringResource(Res.string.teamProjectEditorMaxLanes), KitFieldKind.Number)
        KitNotice(costWords(state.cost))
        KitSwitchRow(stringResource(Res.string.teamProjectEditorCharging), s.chargingOnly, onCheckedChange = actions::setCharging)
    }

    @Composable
    fun screen() {
        KitSwitchRow(stringResource(Res.string.teamProjectEditorScreenOff), s.keepWorkingScreenOff, onCheckedChange = actions::setScreenOff)
        KitNotice(stringResource(Res.string.teamProjectEditorScreenOffHelp))
    }

    @Composable
    fun review() {
        Choice(
            stringResource(Res.string.teamProjectEditorReview),
            listOf(
                "milestones" to stringResource(Res.string.teamProjectEditorMilestonesRisk),
                "every_step" to stringResource(Res.string.teamProjectEditorEveryStep),
            ),
            s.reviewLevel,
            actions::setReview,
        )
    }

    @Composable
    fun budget() {
        Choice(
            stringResource(Res.string.teamProjectEditorBudget),
            listOf(
                "limited" to stringResource(Res.string.teamProjectEditorSetLimits),
                "unlimited" to stringResource(Res.string.teamProjectEditorNoLimit),
            ),
            f.budgetChoice,
            actions::setBudget,
        )
        if (f.budgetChoice == "limited") {
            Field(state, actions, "daily", stringResource(Res.string.teamProjectEditorDailyBudget), KitFieldKind.Number)
            Field(state, actions, "total", stringResource(Res.string.teamProjectEditorTotalBudget), KitFieldKind.Number)
            KitNotice(stringResource(Res.string.teamProjectEditorBudgetHelp))
        }
    }

    @Composable
    fun limits() {
        Field(state, actions, "tokens", stringResource(Res.string.teamProjectEditorTaskTokens), KitFieldKind.Number)
        KitSwitchRow(stringResource(Res.string.teamProjectEditorAutoFix), s.autoFix, onCheckedChange = actions::setAutoFix)
        if (s.autoFix) Field(state, actions, "rounds", stringResource(Res.string.teamProjectEditorMaxRounds), KitFieldKind.Number)
    }
    if (state.kind.creating) {
        mode()
        review()
        budget()
        KitSectionLabel(stringResource(Res.string.teamProjectEditorMoreOptions))
        Field(state, actions, "name", stringResource(Res.string.teamProjectEditorName))
        KitNotice(stringResource(Res.string.teamProjectEditorNameHelp))
        Field(state, actions, "contextFiles", stringResource(Res.string.teamProjectEditorContextFiles), KitFieldKind.Multiline)
        KitNotice(contextHelp(state))
        screen()
        limits()
    } else {
        mode()
        screen()
        review()
        budget()
        limits()
    }
}

@Composable
private fun contextHelp(state: TeamEditorUi): String =
    stringResource(if (state.simulated) Res.string.teamProjectEditorContextFilesHelp else Res.string.teamProjectEditorContextFilesHelpReal)

/** Dart `_specFields`: ask for a change, the spec's parts, the milestones, and the approved versions. */
@Composable
private fun SpecFields(state: TeamEditorUi, actions: TeamEditorActions) {
    val f = state.form
    KitNotice(stringResource(Res.string.teamProjectEditorDraftApproval))
    if (state.starterSeeded) KitNotice(stringResource(Res.string.teamProjectEditorStarterNote), Modifier.testTag("team-starter-milestone"))
    Field(state, actions, "changeRequest", stringResource(Res.string.teamProjectEditorChangeRequest), KitFieldKind.Multiline)
    Tertiary(stringResource(Res.string.teamProjectEditorAskChange), state, run = actions::requestChange)
    Field(state, actions, "goal", stringResource(Res.string.teamProjectEditorGoal), KitFieldKind.Multiline)
    Field(state, actions, "contextFiles", stringResource(Res.string.teamProjectEditorContextFiles), KitFieldKind.Multiline)
    KitNotice(contextHelp(state))
    Field(state, actions, "constraints", stringResource(Res.string.teamProjectEditorConstraints), KitFieldKind.Multiline)
    Field(state, actions, "decisions", stringResource(Res.string.teamProjectEditorDecisions), KitFieldKind.Multiline)
    Field(state, actions, "outOfScope", stringResource(Res.string.teamProjectEditorOutOfScope), KitFieldKind.Multiline)
    KitSectionLabel(stringResource(Res.string.teamProjectEditorMilestones))
    f.milestones.forEachIndexed { i, m ->
        Field(state, actions, "milestone-${m.id}", stringResource(Res.string.teamProjectEditorMilestoneTitle))
        Field(state, actions, "milestoneCriteria-${m.id}", stringResource(Res.string.teamProjectEditorCriteria), KitFieldKind.Multiline)
        FlowRow {
            if (i > 0) Tertiary(stringResource(Res.string.teamProjectEditorMoveUp), state) { actions.moveMilestone(m.id, up = true) }
            if (i < f.milestones.lastIndex) {
                Tertiary(stringResource(Res.string.teamProjectEditorMoveDown), state) { actions.moveMilestone(m.id, up = false) }
            }
            Tertiary(stringResource(Res.string.teamProjectEditorRemove), state) { actions.removeMilestone(m.id) }
        }
    }
    Tertiary(stringResource(Res.string.teamProjectEditorAddMilestone), state, run = actions::addMilestone)
    Tertiary(stringResource(Res.string.teamProjectEditorHistory), state, run = actions::toggleHistory)
    if (state.history) state.specVersions.reversed().forEach { History(it, state) }
}

/** Dart `_specText`. */
@Composable
private fun specText(spec: TeamSpec): String =
    (
        listOf(
            stringResource(Res.string.teamProjectEditorGoal), spec.goal,
            stringResource(Res.string.teamProjectEditorConstraints), spec.constraints,
            stringResource(Res.string.teamProjectEditorDecisions), spec.decisions,
            stringResource(Res.string.teamProjectEditorOutOfScope), spec.outOfScope,
            stringResource(Res.string.teamProjectEditorContextFiles),
        ) + spec.contextFiles + stringResource(Res.string.teamProjectEditorMilestones) +
            spec.milestones.flatMap { listOf(it.title) + it.criteria }
    ).joinToString("\n")

/** Dart `_historyVersion`: one approved version, who approved it and when. */
@Composable
private fun History(spec: TeamSpec, state: TeamEditorUi) {
    val at = TeamRules.epochMs(spec.approvedAt)
    val age = ageWords(at, state.nowMs) ?: stringResource(Res.string.teamProjectEditorUnknownDate)
    val actor = if (spec.approvedBy == "person") stringResource(Res.string.teamProjectEditorYou) else spec.approvedBy
    var open by remember { mutableStateOf(false) }
    KitExpandRow(
        title = "${stringResource(Res.string.teamProjectEditorVersion)} ${spec.version}",
        expanded = open,
        supporting = "${stringResource(Res.string.teamProjectEditorApprovedBy)} $actor · $age",
        onExpandedChange = { open = it },
    ) {
        KitText(specText(spec), KitTextRole.Body)
    }
}

/** Dart `_planFields`: a failed plan's ways out, else each phase's tasks with their role, repo, server and order. */
@Composable
private fun PlanFields(state: TeamEditorUi, actions: TeamEditorActions) {
    val f = state.form
    if (state.projectStatus == "planFailed") {
        KitNotice(stringResource(Res.string.teamProjectEditorPlanFailed))
        Tertiary(stringResource(Res.string.teamProjectEditorUseAsTask), state) { actions.recoverPlan(useAsTask = true) }
        Tertiary(stringResource(Res.string.teamProjectEditorAskAgain), state) { actions.recoverPlan(useAsTask = false) }
        return
    }
    KitNotice(stringResource(Res.string.teamProjectEditorPlanHelp))
    f.phases.forEach { phase ->
        KitSectionLabel(phase.title)
        KitSwitchRow(stringResource(Res.string.teamProjectEditorRisky), phase.risky) { actions.setRisky(phase.id, it) }
        f.tasks.filter { it.phaseId == phase.id }.forEach { t ->
            Field(state, actions, "task-${t.id}", stringResource(Res.string.teamProjectEditorTaskTitle))
            Field(state, actions, "criteria-${t.id}", stringResource(Res.string.teamProjectEditorCriteria), KitFieldKind.Multiline)
            Choice(stringResource(Res.string.teamProjectEditorRole), state.roles.map { it.id to it.name }, t.roleId) { actions.setTaskRole(t.id, it) }
            Choice(stringResource(Res.string.teamProjectEditorRepo), f.repos.map { it.id to it.name }, t.repoId) { actions.setTaskRepo(t.id, it) }
            Choice(stringResource(Res.string.teamProjectEditorServer), state.servers.map { it.id to it.name }, t.serverId) {
                actions.setTaskServer(t.id, it)
            }
            if (f.tasks.size > 1) {
                val label = stringResource(Res.string.teamProjectEditorDependencies)
                KitSectionLabel(label)
                KitChoiceGroup(
                    choices = f.tasks.filter { it.id != t.id }.map { KitChoice(it.id, it.title) },
                    selected = t.dependsOn.toSet(),
                    semanticsLabel = label,
                    onSelect = { actions.toggleDependency(t.id, it) },
                    multiple = true,
                )
            }
            Tertiary(stringResource(Res.string.teamProjectEditorRemoveTask), state) { actions.removeTask(t.id) }
        }
    }
}

/** Dart `_roleFields`: the roles list and New role, or one role's form and the tasks given to it. */
@Composable
private fun RoleFields(state: TeamEditorUi, actions: TeamEditorActions) {
    val role = state.form.role
    if (role == null) {
        state.roles.forEach { r ->
            val note = listOf(r.model, if (r.readOnly) stringResource(Res.string.teamProjectEditorReadOnlyShort) else "")
                .filter { it.isNotEmpty() }
                .joinToString(" · ")
            KitRow(title = r.name, supporting = note.ifEmpty { null }, trailing = KitRowTrailing.Chevron, onClick = { actions.editRole(r.id) })
        }
        Tertiary(stringResource(Res.string.teamProjectEditorAddRole), state) { actions.editRole(null) }
        return
    }
    Field(state, actions, "roleName", stringResource(Res.string.teamProjectEditorRoleName))
    Field(state, actions, "roleInstructions", stringResource(Res.string.teamProjectEditorInstructions), KitFieldKind.Multiline)
    // Typed model names, as Flutter where no model catalog is bound (the catalog picker joins later).
    Field(state, actions, "roleModel", stringResource(Res.string.teamProjectEditorModel), helper = stringResource(Res.string.teamModelServerDefaultHint))
    KitSectionLabel(stringResource(Res.string.teamProjectEditorFallbackSection))
    Field(state, actions, "roleFallback", stringResource(Res.string.teamProjectEditorFallback), helper = stringResource(Res.string.teamProjectEditorNoFallbackHint))
    KitNotice(stringResource(Res.string.teamProjectEditorFallbackNote))
    if (role.readOnly) KitNotice(stringResource(Res.string.teamProjectEditorReadOnlyRole))
    state.roleTasks.forEach { (task, project) -> KitRow(title = task, supporting = project) }
    Tertiary(stringResource(Res.string.teamProjectEditorAllRoles), state, run = actions::allRoles)
}
