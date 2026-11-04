package io.github.eslamasabry.oc.feature.chat.requests.ui

import androidx.compose.runtime.Composable
import kotlin.time.Duration.Companion.milliseconds
import io.github.eslamasabry.oc.ui.words.res.kitDurationHoursMinutes
import io.github.eslamasabry.oc.ui.words.res.kitDurationHours
import io.github.eslamasabry.oc.ui.words.res.kitDurationDaysHours
import io.github.eslamasabry.oc.ui.words.res.kitDurationDays
import io.github.eslamasabry.oc.ui.kitchat.turn.KitElapsed
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.engine.requests.ask.AlwaysGrant
import io.github.eslamasabry.oc.engine.requests.ask.AskTarget
import io.github.eslamasabry.oc.engine.requests.ask.AskVerb
import io.github.eslamasabry.oc.engine.requests.ask.AskView
import io.github.eslamasabry.oc.feature.chat.requests.presentation.AnswerWords
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.chatRequestAlwaysAllowAnyCommand
import io.github.eslamasabry.oc.ui.words.res.chatRequestAlwaysAllowCommands
import io.github.eslamasabry.oc.ui.words.res.chatRequestAlwaysAllowEdits
import io.github.eslamasabry.oc.ui.words.res.chatRequestAlwaysAllowNamed
import io.github.eslamasabry.oc.ui.words.res.chatRequestAlwaysAnyCommandWhat
import io.github.eslamasabry.oc.ui.words.res.chatRequestAlwaysConfirm
import io.github.eslamasabry.oc.ui.words.res.chatRequestAlwaysEditsWhat
import io.github.eslamasabry.oc.ui.words.res.chatRequestAlwaysInProject
import io.github.eslamasabry.oc.ui.words.res.chatRequestAlwaysOn
import io.github.eslamasabry.oc.ui.words.res.chatRequestAlwaysScope
import io.github.eslamasabry.oc.ui.words.res.chatRequestAlwaysTitle
import io.github.eslamasabry.oc.ui.words.res.chatRequestAnswerAllowed
import io.github.eslamasabry.oc.ui.words.res.chatRequestAnswerRejected
import io.github.eslamasabry.oc.ui.words.res.chatRequestAskCommand
import io.github.eslamasabry.oc.ui.words.res.chatRequestAskCreateFiles
import io.github.eslamasabry.oc.ui.words.res.chatRequestAskCreateNamed
import io.github.eslamasabry.oc.ui.words.res.chatRequestAskDeleteFiles
import io.github.eslamasabry.oc.ui.words.res.chatRequestAskDeleteNamed
import io.github.eslamasabry.oc.ui.words.res.chatRequestAskDirectory
import io.github.eslamasabry.oc.ui.words.res.chatRequestAskEdit
import io.github.eslamasabry.oc.ui.words.res.chatRequestAskEditFiles
import io.github.eslamasabry.oc.ui.words.res.chatRequestAskEditNamed
import io.github.eslamasabry.oc.ui.words.res.chatRequestAskFetch
import io.github.eslamasabry.oc.ui.words.res.chatRequestAskOutsideRead
import io.github.eslamasabry.oc.ui.words.res.chatRequestAskOutsideRun
import io.github.eslamasabry.oc.ui.words.res.chatRequestAskOutsideWrite
import io.github.eslamasabry.oc.ui.words.res.chatRequestAskRead
import io.github.eslamasabry.oc.ui.words.res.chatRequestAskReadNamed
import io.github.eslamasabry.oc.ui.words.res.chatRequestAskRunAnyIn
import io.github.eslamasabry.oc.ui.words.res.chatRequestAskRunIn
import io.github.eslamasabry.oc.ui.words.res.chatRequestAskRunNamed
import io.github.eslamasabry.oc.ui.words.res.chatRequestTitleMode
import io.github.eslamasabry.oc.ui.words.res.chatRequestTitleSearch
import io.github.eslamasabry.oc.ui.words.res.chatRequestTitleSkill
import io.github.eslamasabry.oc.ui.words.res.chatRequestTitleTask
import io.github.eslamasabry.oc.ui.words.res.chatRequestTitleWebSearch
import io.github.eslamasabry.oc.ui.words.res.e7PermissionAction5
import io.github.eslamasabry.oc.ui.words.res.e7PermissionAction6
import io.github.eslamasabry.oc.ui.words.res.kitRequestChangeSize
import io.github.eslamasabry.oc.ui.words.res.kitToolTookMinutes
import io.github.eslamasabry.oc.ui.words.res.kitToolTookSeconds
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

// The request card's sentences, chosen from the engine's wording data (plan 09 §5.2: the engine says what, the words
// layer says it). Every string already exists in :ui:words (ported from app_en.arb / app_ar.arb).

/** Left-to-right isolate for a command or a file name inside a sentence (Dart `KitBidi.ltr`). */
private fun ltr(text: String): String = "⁦$text⁩"

/** First-strong isolate for a project or folder name (Dart `KitBidi.auto`). */
private fun auto(text: String): String = "⁨$text⁩"

/** The ask as one question: "Run npm install in neon-breaker?", "Edit 5 files?", "Write outside this project?". */
@Composable
internal fun askQuestion(ask: AskView): String {
    val name = ask.name
    val where = ask.where
    return when (ask.verb) {
        AskVerb.Run ->
            when {
                name != null && where != null -> stringResource(Res.string.chatRequestAskRunIn, ltr(name), auto(where))
                name != null -> stringResource(Res.string.chatRequestAskRunNamed, ltr(name))
                where != null -> stringResource(Res.string.chatRequestAskRunAnyIn, auto(where))
                else -> stringResource(Res.string.chatRequestAskCommand)
            }
        AskVerb.Edit ->
            when {
                ask.count > 1 -> pluralStringResource(Res.plurals.chatRequestAskEditFiles, ask.count, Numbers.format(ask.count))
                name != null -> stringResource(Res.string.chatRequestAskEditNamed, ltr(name))
                else -> stringResource(Res.string.chatRequestAskEdit)
            }
        AskVerb.Create ->
            if (ask.count > 1 || name == null) {
                pluralStringResource(Res.plurals.chatRequestAskCreateFiles, ask.count, Numbers.format(ask.count))
            } else {
                stringResource(Res.string.chatRequestAskCreateNamed, ltr(name))
            }
        AskVerb.Delete ->
            if (ask.count > 1 || name == null) {
                pluralStringResource(Res.plurals.chatRequestAskDeleteFiles, ask.count, Numbers.format(ask.count))
            } else {
                stringResource(Res.string.chatRequestAskDeleteNamed, ltr(name))
            }
        AskVerb.Read -> if (name != null) stringResource(Res.string.chatRequestAskReadNamed, ltr(name)) else stringResource(Res.string.chatRequestAskRead)
        AskVerb.Fetch -> stringResource(Res.string.chatRequestAskFetch)
        AskVerb.WebSearch -> stringResource(Res.string.chatRequestTitleWebSearch)
        AskVerb.Search -> stringResource(Res.string.chatRequestTitleSearch)
        AskVerb.Task -> stringResource(Res.string.chatRequestTitleTask)
        AskVerb.Skill -> stringResource(Res.string.chatRequestTitleSkill)
        AskVerb.Mode -> stringResource(Res.string.chatRequestTitleMode)
        AskVerb.OutsideWrite -> stringResource(Res.string.chatRequestAskOutsideWrite)
        AskVerb.OutsideRead -> stringResource(Res.string.chatRequestAskOutsideRead)
        AskVerb.OutsideRun -> stringResource(Res.string.chatRequestAskOutsideRun)
        AskVerb.Outside -> stringResource(Res.string.chatRequestAskDirectory)
        AskVerb.DoomLoop -> stringResource(Res.string.e7PermissionAction5)
        // A tool without its own words: "Permission needed" until ToolPresentation (S2-MODEL) names tools in words;
        // the tool id itself is never the question (B-CHAT-111).
        AskVerb.Use, AskVerb.Generic -> stringResource(Res.string.e7PermissionAction6)
    }
}

/** The mono block: every command or path on its own line, a file's change size after it. */
@Composable
internal fun askSubject(targets: List<AskTarget>): String? {
    if (targets.isEmpty()) return null
    val lines = targets.map { target ->
        val added = target.added
        val removed = target.removed
        if (added != null && removed != null) "${target.text}  ${stringResource(Res.string.kitRequestChangeSize, added, removed)}" else target.text
    }
    return lines.joinToString("\n")
}

/** The quiet action's name: "Always allow npm install", "Always allow edits", "Always allow commands like this". */
@Composable
internal fun alwaysLabel(grant: AlwaysGrant?): String =
    when (grant) {
        is AlwaysGrant.Named -> stringResource(Res.string.chatRequestAlwaysAllowNamed, ltr(grant.what))
        AlwaysGrant.Edits -> stringResource(Res.string.chatRequestAlwaysAllowEdits)
        AlwaysGrant.AnyCommand -> stringResource(Res.string.chatRequestAlwaysAllowAnyCommand)
        AlwaysGrant.Commands, null -> stringResource(Res.string.chatRequestAlwaysAllowCommands)
    }

/** What the grant covers, for the confirm; null when the request names nothing more exact than "these requests". */
@Composable
private fun alwaysWhat(grant: AlwaysGrant?): String? =
    when (grant) {
        is AlwaysGrant.Named -> ltr(grant.what)
        AlwaysGrant.Edits -> stringResource(Res.string.chatRequestAlwaysEditsWhat)
        AlwaysGrant.AnyCommand -> stringResource(Res.string.chatRequestAlwaysAnyCommandWhat)
        AlwaysGrant.Commands, null -> null
    }

/** The confirm's question: "Always allow npm install in this project?" (what and where). */
@Composable
internal fun alwaysConfirmTitle(grant: AlwaysGrant?): String =
    alwaysWhat(grant)?.let { stringResource(Res.string.chatRequestAlwaysConfirm, it) } ?: stringResource(Res.string.chatRequestAlwaysTitle)

/** The confirm's body states the grant's scope first. */
@Composable
internal fun alwaysConfirmBody(grant: AlwaysGrant?): String? =
    alwaysWhat(grant)?.let { stringResource(Res.string.chatRequestAlwaysScope, it, stringResource(Res.string.chatRequestAlwaysInProject)) }

/** The collapsed line's words ("Allowed", "Always allowed", "Rejected", the chosen answer). */
@Composable
internal fun answerWords(words: AnswerWords): String =
    when (words) {
        AnswerWords.Allowed -> stringResource(Res.string.chatRequestAnswerAllowed)
        AnswerWords.AlwaysAllowed -> stringResource(Res.string.chatRequestAlwaysOn)
        AnswerWords.Rejected, AnswerWords.Dismissed -> stringResource(Res.string.chatRequestAnswerRejected)
        is AnswerWords.Chose -> words.answer
    }

/**
 * How long the card has waited on this phone (B-KIT-033): seconds under a minute, minutes under an hour, then hours and
 * minutes, then days and hours (never "2,715 minutes").
 */
@Composable
internal fun waitedWords(waitedMs: Long): String {
    val seconds = (waitedMs / 1_000).coerceAtLeast(0).toInt()
    if (seconds < 60) return pluralStringResource(Res.plurals.kitToolTookSeconds, seconds, Numbers.format(seconds))
    return when (val e = KitElapsed.of(waitedMs.milliseconds)) {
        is KitElapsed.Minutes -> pluralStringResource(Res.plurals.kitToolTookMinutes, e.minutes, Numbers.format(e.minutes))
        is KitElapsed.Hours ->
            if (e.minutes == 0) {
                stringResource(Res.string.kitDurationHours, Numbers.format(e.hours))
            } else {
                stringResource(Res.string.kitDurationHoursMinutes, Numbers.format(e.hours), Numbers.format(e.minutes))
            }
        is KitElapsed.Days ->
            if (e.hours == 0) {
                stringResource(Res.string.kitDurationDays, Numbers.format(e.days))
            } else {
                stringResource(Res.string.kitDurationDaysHours, Numbers.format(e.days), Numbers.format(e.hours))
            }
    }
}
