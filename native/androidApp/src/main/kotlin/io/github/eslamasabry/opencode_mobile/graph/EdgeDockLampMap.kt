package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.engine.attention.dock.DockRequestKind
import io.github.eslamasabry.oc.feature.chats.lamp.LampAnswerUi
import io.github.eslamasabry.oc.feature.chats.lamp.LampChildState
import io.github.eslamasabry.oc.feature.chats.lamp.LampChildUi
import io.github.eslamasabry.oc.feature.chats.lamp.LampGroupKind
import io.github.eslamasabry.oc.feature.chats.lamp.LampHeight
import io.github.eslamasabry.oc.feature.chats.lamp.LampPlace
import io.github.eslamasabry.oc.feature.chats.lamp.LampRequestUi
import io.github.eslamasabry.oc.feature.chats.lamp.LampRowUi
import io.github.eslamasabry.oc.feature.chats.lamp.LampSide
import io.github.eslamasabry.oc.feature.chats.lamp.LampState
import io.github.eslamasabry.oc.feature.chats.lamp.LampStateLine
import io.github.eslamasabry.oc.feature.chats.lamp.LampStopUi
import io.github.eslamasabry.oc.feature.chats.lamp.LampTreeKind
import io.github.eslamasabry.oc.feature.chats.lamp.LampUi
import io.github.eslamasabry.oc.feature.chats.presentation.HomeHeldReply
import io.github.eslamasabry.oc.feature.chats.presentation.RelativeAge
import io.github.eslamasabry.oc.ui.kit.lamp.KitLampGroup
import io.github.eslamasabry.oc.ui.kit.lamp.KitLampHeight
import io.github.eslamasabry.oc.ui.kit.lamp.KitLampPanelModel
import io.github.eslamasabry.oc.ui.kit.lamp.KitLampPlace
import io.github.eslamasabry.oc.ui.kit.lamp.KitLampRow
import io.github.eslamasabry.oc.ui.kit.lamp.KitLampRowMark
import io.github.eslamasabry.oc.ui.kit.lamp.KitLampSection
import io.github.eslamasabry.oc.ui.kit.lamp.KitLampSide
import io.github.eslamasabry.oc.ui.kit.lamp.KitLampTone
import io.github.eslamasabry.oc.ui.kit.request.KitHeld
import io.github.eslamasabry.oc.ui.kit.request.KitRequest
import io.github.eslamasabry.oc.ui.kit.request.KitRequestKind
import io.github.eslamasabry.oc.ui.kit.status.KitStepTreeKind
import io.github.eslamasabry.oc.ui.kit.status.KitTreeStep
import io.github.eslamasabry.oc.ui.kit.status.KitTreeStepState

/** Every sentence of the edge light's list. The screen fills them from the string keys; tests fill them with tokens. */
internal enum class LampWord {
    Title, Edit, Done, All, AllShort, NewConversation, Hidden, ShowAgain, More, Empty, Pin, Unpin, Rename, Hide, Stop,
    Untitled, GroupPinned, GroupNeedsYou, GroupFailed, GroupWorking, GroupDone, GroupRecent,
    TasksDone, For, Thinking, Working, DoneWord, IdleWord, FailedWord, WaitingMixed, WaitingToFinish, PausedAtStep,
    PausedUntilAnswer, SubAgentOf, MoreSubAgents, MoreInside, AsksRun, AsksEdit, AsksRead, AsksFolder, AsksPermission,
    AsksQuestion, AsksQuestionBare, AsksForm, AsksFormBare, Answer, Allowed, Denied, Stopping, Stopped, Reorder, MoveUp,
    MoveDown, YouAreIn, FactOf, ChildLabel, ChildLabelFact, AllowOnce, Deny, ActionOn, NotStarted, ShowSubAgents,
    HideSubAgents, AgeNow, AgeMinutes, AgeHours, AgeDays, EdgeLight, Move, TurnOff, PlaceTitle, PlaceBody,
    Left, Right, High, Middle, Low, OffNotice, RenameFailed, StopFailed, AnswerFailed, HiddenNotice, RenameTitle, Cancel, RenameConfirm,
}

/** The words by [LampWord]; templates hold `%1$s`, `%2$s`; [folded] is "N sub-agents" for each N the screen shows. */
internal class LampTexts(private val words: Map<LampWord, String>, val folded: Map<Int, String> = emptyMap()) {
    operator fun get(word: LampWord): String = words[word] ?: word.name

    /** The template of [word] with `%1$s`, `%2$s`... replaced by [args] in order. */
    fun fmt(word: LampWord, vararg args: String): String {
        var out = get(word)
        args.forEachIndexed { i, a -> out = out.replace("%${i + 1}\$s", a) }
        return out
    }

    fun count(n: Int): String = Numbers.format(n)
}

/** The panel's rows, found again by the key a tap names. */
internal class LampIndex(ui: LampUi) {
    val rows: Map<String, LampRowUi> = ui.groups.flatMap { it.rows }.associateBy { it.key }
    private val children: Map<String, Pair<LampRowUi, LampChildUi>> =
        rows.values.flatMap { row -> row.tree?.children.orEmpty().map { it.key to (row to it) } }.toMap()

    fun row(key: Any): LampRowUi? = rows[key as? String]

    fun child(key: Any): Pair<LampRowUi, LampChildUi>? = children[key as? String]

    /** A hidden row's key is its conversation. */
    fun hidden(key: Any): ConversationRef? = key as? ConversationRef
}

/** The list the light's panel draws: the presenter's [LampUi] as the kit's plain values. Pure. */
internal object LampPanelMap {
    fun model(ui: LampUi, t: LampTexts): KitLampPanelModel =
        KitLampPanelModel(
            sections =
                ui.groups
                    .map { g -> KitLampSection(group(g.kind), groupTitle(g.kind, t), g.rows.map { row(it, t) }) }
                    // The kit lists top to bottom with Needs you by the thumb, the opposite of the presenter's order.
                    .sortedBy { it.group.ordinal },
            hidden =
                ui.hidden?.items.orEmpty().map {
                    KitLampRow(key = it.ref, title = it.title ?: t[LampWord.Untitled], mark = mark(it.state), canEdit = false)
                },
            more = ui.more,
            all = ui.allCount,
            editing = ui.editing,
            youAreIn = ui.openTitle?.let { t.fmt(LampWord.YouAreIn, it) },
        )

    fun group(kind: LampGroupKind): KitLampGroup =
        when (kind) {
            LampGroupKind.Pinned -> KitLampGroup.Pinned
            LampGroupKind.NeedsYou -> KitLampGroup.NeedsYou
            LampGroupKind.Failed -> KitLampGroup.Failed
            LampGroupKind.Working -> KitLampGroup.Working
            LampGroupKind.Done -> KitLampGroup.Done
            LampGroupKind.Recent -> KitLampGroup.Recent
        }

    private fun groupTitle(kind: LampGroupKind, t: LampTexts): String =
        t[
            when (kind) {
                LampGroupKind.Pinned -> LampWord.GroupPinned
                LampGroupKind.NeedsYou -> LampWord.GroupNeedsYou
                LampGroupKind.Failed -> LampWord.GroupFailed
                LampGroupKind.Working -> LampWord.GroupWorking
                LampGroupKind.Done -> LampWord.GroupDone
                LampGroupKind.Recent -> LampWord.GroupRecent
            }
        ]

    private fun mark(state: LampState): KitLampRowMark =
        when (state) {
            LampState.NeedsYou -> KitLampRowMark.NeedsYou
            LampState.Failed -> KitLampRowMark.Failed
            LampState.Working -> KitLampRowMark.Working
            LampState.Done -> KitLampRowMark.Done
            LampState.Idle -> KitLampRowMark.Quiet
        }

    fun row(r: LampRowUi, t: LampTexts): KitLampRow {
        val title = r.title ?: t[LampWord.Untitled]
        val progress = (r.stateLine as? LampStateLine.TasksDone)?.takeIf { it.total > 0 }?.let { (it.done.toFloat() / it.total).coerceIn(0f, 1f) }
        val stopped = r.stop == LampStopUi.Stopped
        val stateText = if (stopped) t[LampWord.Stopped] else r.stateLine?.let { line(it, t) }
        return KitLampRow(
            key = r.key,
            title = title,
            mark = mark(r.state),
            progress = progress,
            state = stateText,
            stateTone =
                when {
                    r.state == LampState.NeedsYou -> KitLampTone.Attention
                    r.state == LampState.Failed -> KitLampTone.Quiet
                    else -> KitLampTone.Accent
                },
            newest = r.subAgentOf?.let { t.fmt(LampWord.SubAgentOf, it) } ?: r.newestLine,
            time = r.age?.let { age(it, t) },
            server = r.server,
            pinned = r.pinned,
            request = r.request?.let { request(it, t) },
            held = r.request?.let { held(it, t) },
            stopping = if (r.stop == LampStopUi.Holding) KitHeld(r.key, t.fmt(LampWord.Stopping, title)) else null,
            stoppable = r.stop == LampStopUi.Button,
            steps = r.tree?.let { if (it.kind == LampTreeKind.Sequence) KitStepTreeKind.Sequence else KitStepTreeKind.Parallel },
            children = r.tree?.children.orEmpty().map { child(it, t) },
            canEdit = r.subAgentOf == null,
        )
    }

    fun line(line: LampStateLine, t: LampTexts): String =
        when (line) {
            is LampStateLine.TasksDone -> t.fmt(LampWord.TasksDone, t.count(line.done), t.count(line.total))
            LampStateLine.Thinking -> t[LampWord.Thinking]
            LampStateLine.Working -> t[LampWord.Working]
            LampStateLine.Done -> t[LampWord.DoneWord]
            is LampStateLine.For -> t.fmt(LampWord.For, age(line.age, t))
            is LampStateLine.Waiting -> t.fmt(LampWord.WaitingMixed, t.count(line.waiting), t.count(line.working))
            LampStateLine.WaitingToFinish -> t[LampWord.WaitingToFinish]
            is LampStateLine.PausedAtStep -> t.fmt(LampWord.PausedAtStep, t.count(line.step), t.count(line.total))
        }

    /** The short age: "a moment", "4m", "2h", "3d". */
    fun age(age: RelativeAge, t: LampTexts): String =
        when (age) {
            RelativeAge.JustNow -> t[LampWord.AgeNow]
            is RelativeAge.Minutes -> t.fmt(LampWord.AgeMinutes, t.count(age.count))
            is RelativeAge.Hours -> t.fmt(LampWord.AgeHours, t.count(age.count))
            is RelativeAge.Days -> t.fmt(LampWord.AgeDays, t.count(age.count))
        }

    /** What a waiting request says, in the words of its kind. */
    fun requestWords(r: LampRequestUi, t: LampTexts): String =
        when (r.kind) {
            DockRequestKind.Permission ->
                when {
                    r.target.isBlank() -> t[LampWord.AsksPermission]
                    r.action == "run" -> t.fmt(LampWord.AsksRun, r.target)
                    r.action == "edit" -> t.fmt(LampWord.AsksEdit, r.target)
                    r.action == "read" -> t.fmt(LampWord.AsksRead, r.target)
                    r.action == "folder" -> t.fmt(LampWord.AsksFolder, r.target)
                    else -> t[LampWord.AsksPermission]
                }
            DockRequestKind.Question -> if (r.text.isBlank()) t[LampWord.AsksQuestionBare] else t.fmt(LampWord.AsksQuestion, r.text)
            DockRequestKind.Form -> if (r.text.isBlank()) t[LampWord.AsksFormBare] else t.fmt(LampWord.AsksForm, r.text)
        }

    fun request(r: LampRequestUi, t: LampTexts): KitRequest =
        KitRequest(
            key = r.requestId,
            words = requestWords(r, t),
            kind = if (r.answer == LampAnswerUi.Answer) KitRequestKind.Question else KitRequestKind.Permission,
        )

    fun held(r: LampRequestUi, t: LampTexts): KitHeld? =
        (r.answer as? LampAnswerUi.Held)?.let {
            val allowed = it.reply != HomeHeldReply.Rejected
            KitHeld(r.requestId, t[if (allowed) LampWord.Allowed else LampWord.Denied], allowed)
        }

    fun child(c: LampChildUi, t: LampTexts): KitTreeStep {
        val name = c.name ?: t[LampWord.Untitled]
        val stopHold = if (c.stop == LampStopUi.Holding) KitHeld(c.key, t.fmt(LampWord.Stopping, name)) else null
        return KitTreeStep(
            key = c.key,
            name = name,
            state =
                when (c.state) {
                    LampChildState.Working, LampChildState.Thinking -> KitTreeStepState.Working
                    LampChildState.NeedsYou -> KitTreeStepState.NeedsYou
                    LampChildState.Failed -> KitTreeStepState.Failed
                    LampChildState.Done, LampChildState.Idle -> KitTreeStepState.Done
                },
            fact = c.fact?.let { t.fmt(LampWord.FactOf, t.count(it.done), t.count(it.total)) },
            verb = if (c.state == LampChildState.Thinking) t[LampWord.Thinking] else null,
            progress = c.fact?.takeIf { it.total > 0 }?.let { (it.done.toFloat() / it.total).coerceIn(0f, 1f) },
            request = c.request?.let { request(it, t) },
            held = c.request?.let { held(it, t) } ?: stopHold,
            stoppable = c.stop == LampStopUi.Button,
        )
    }

    fun place(p: LampPlace): KitLampPlace =
        KitLampPlace(
            side = if (p.side == LampSide.Start) KitLampSide.Start else KitLampSide.End,
            height = when (p.height) { LampHeight.Low -> KitLampHeight.Low; LampHeight.Middle -> KitLampHeight.Middle; LampHeight.High -> KitLampHeight.High },
        )

    fun place(p: KitLampPlace): LampPlace =
        LampPlace(
            side = if (p.side == KitLampSide.Start) LampSide.Start else LampSide.End,
            height = when (p.height) { KitLampHeight.Low -> LampHeight.Low; KitLampHeight.Middle -> LampHeight.Middle; KitLampHeight.High -> LampHeight.High },
        )
}
