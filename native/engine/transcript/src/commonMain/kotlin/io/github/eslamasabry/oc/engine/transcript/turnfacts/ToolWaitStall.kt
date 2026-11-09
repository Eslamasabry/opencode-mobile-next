package io.github.eslamasabry.oc.engine.transcript.turnfacts

import io.github.eslamasabry.oc.engine.transcript.ledger.StepState
import io.github.eslamasabry.oc.engine.transcript.ledger.TurnWork

/**
 * Phase 0 (first reply): a turn that sits on one tool call for minutes with no word from it reads as a hang. The stall
 * tracker deliberately treats a running tool as "the agent is working", and the stuck-command rule only knows
 * foreground shell commands past 15 minutes, so a stuck read, search, fetch or pending call never said anything. This
 * names the tool once it has waited [AFTER_MS], so the chat can say "Still waiting for <tool>" with Stop.
 *
 * Not a stall: a subagent task (long on purpose), a question or todo (waiting for a person or bookkeeping), and a
 * shell command (its own rule). The tool name is the server's display name; the caller maps it to words.
 */
public data class ToolWaitStall(val toolName: String, val kind: ToolKind, val waitedMs: Long)

public object ToolWaitStallRules {
    /** Three minutes: long for any single read/search/fetch, short enough that nobody has given up by then. */
    public const val AFTER_MS: Long = 3 * 60 * 1000L

    private val exempt = setOf(ToolKind.Shell, ToolKind.Task, ToolKind.Question, ToolKind.Todo)

    /** The newest step in hand, when it is a tool call (running or pending) past [AFTER_MS]; null otherwise. */
    public fun of(work: Map<String, TurnWork>, nowMs: Long): ToolWaitStall? {
        val step = work.values.lastOrNull { it.now != null }?.now ?: return null
        if (step.state != StepState.Running && step.state != StepState.Pending) return null
        if (step.facts.kind in exempt) return null
        val started = step.startedAtMs ?: return null
        val waited = nowMs - started
        if (waited < AFTER_MS) return null
        return ToolWaitStall(step.facts.toolName, step.facts.kind, waited)
    }
}
