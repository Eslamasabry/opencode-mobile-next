package io.github.eslamasabry.oc.ui.kitchat.tool

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * What kind of step it was (plan 06 §7.8, plan 09 §0.4 C5). It picks the glyph only; the words are the caller's
 * [KitToolView.title]. `:feature:chat` maps the engine's `ToolView` onto it.
 */
public enum class KitToolKind { Shell, Read, List, Edit, Write, Patch, Search, WebFetch, SubAgent, Todo, Question, Card, Other }

/**
 * How the step went. The caller maps the server's status and gives the words ([KitToolView.statusLabel]); the row
 * draws them. Stopped is the person's or the server's stop, never an error; Failed is drawn neutral (no danger colour,
 * plan 09 R8 and §9.5).
 */
/**
 * How a step stands. [WaitingForYou]: the step is blocked on the person (an approval or a question); it is drawn with
 * the attention mark and no clock, never as running (P-105).
 */
public enum class KitToolStatus { Pending, Running, Done, Failed, Stopped, WaitingForYou }

/**
 * One step of the agent's work, as the kit draws it (kit-local UI model, plan 09 §0.4 C5). Every word is the caller's:
 *
 * @param id the step's call id (a stable key for the caller's list).
 * @param title plain words: "Read main.dart", "Ran a command".
 * @param subject the path, command or address the step worked on: drawn mono, left to right, redacted.
 * @param detail one short line of what came out ("3 matches"): drawn redacted.
 * @param durationLabel how long it took, in words ("2 s").
 * @param added lines added (shown "+n"); @param removed lines removed (shown "−n").
 * @param statusLabel the status in words ("Stopped"), always given: a screen reader reads it.
 * @param icon the glyph when the caller knows more than [kind] (owner 2026-11-14: a command that ran tests draws the
 *   test glyph, a search the magnifier, git the branch); null draws the kind's own glyph.
 * @param still draw a running step's light still (e.g. a command an ended turn left running).
 */
@Suppress("LongParameterList")
@Immutable
public data class KitToolView(
    val id: String,
    val kind: KitToolKind,
    val title: String,
    val subject: String?,
    val detail: String?,
    val status: KitToolStatus,
    val durationLabel: String?,
    val added: Int?,
    val removed: Int?,
    val statusLabel: String,
    val icon: ImageVector? = null,
    val still: Boolean = false,
)
