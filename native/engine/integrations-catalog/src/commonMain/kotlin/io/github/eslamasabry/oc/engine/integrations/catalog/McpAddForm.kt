package io.github.eslamasabry.oc.engine.integrations.catalog

import io.github.eslamasabry.oc.core.model.McpServerDraft
import io.github.eslamasabry.oc.core.model.McpServerKind
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap

// Flutter lib/ui/screens/mcp_setup_screen.dart (`_pairs`, `_timeoutError`, `_save`): the add form's
// fields as the person typed them, turned into one McpServerDraft or the first reason it can't be.
// Header and environment values are secrets: no problem carries a value, only a 1-based row or a name.

/** One name/value row of the Headers (remote) or Environment variables (local) list. */
public data class McpPairRow(val name: String, val value: String) {
    override fun toString(): String = "McpPairRow(name=$name, value=<hidden>)"
}

/** What the person typed in the add form. Rows left entirely empty are ignored. */
public data class McpAddInput(
    val name: String,
    val url: String = "",
    val executable: String = "",
    /** One argument per line. */
    val arguments: String = "",
    val headers: List<McpPairRow> = emptyList(),
    val environment: List<McpPairRow> = emptyList(),
    /** Whole seconds; blank = the server's default. */
    val timeoutSeconds: String = "",
) {
    override fun toString(): String =
        "McpAddInput(name=$name, remote=${url.isNotBlank()}, headers=${headers.size}, " +
            "environment=${environment.size}, timeout=$timeoutSeconds)"
}

/** Why the form can't be sent; :ui:words words each (Flutter e7Library* / mcpSetup* strings). */
public sealed interface McpAddProblem {
    /** Blank, too long, or has a control character. */
    public data object Name : McpAddProblem

    /** Both or neither of address and command. */
    public data object Kind : McpAddProblem

    /** e7LibraryEnterAValueGreaterThanZero */
    public data object Timeout : McpAddProblem

    /** e7LibraryInvalidNameOnLine(label, row): a value with no name, or a name with `=` or a line break. */
    public data class PairName(val headers: Boolean, val row: Int) : McpAddProblem

    /** A line break inside a value (the server would split it). */
    public data class PairValue(val headers: Boolean, val row: Int) : McpAddProblem

    /** e7LibraryDuplicateName(label, name) */
    public data class Duplicate(val headers: Boolean, val name: String) : McpAddProblem
}

public sealed interface McpAddResult {
    public data class Ready(val draft: McpServerDraft) : McpAddResult

    public data class Invalid(val problem: McpAddProblem) : McpAddResult
}

public object McpAddForm {
    public const val MAX_NAME: Int = 200

    /** The first problem in [input], or the draft to send. Headers go only with an address; environment only with a command. */
    public fun draft(input: McpAddInput): McpAddResult {
        val name = input.name.trim()
        if (name.isEmpty() || name.length > MAX_NAME || name.any { it.isISOControl() })
            return McpAddResult.Invalid(McpAddProblem.Name)
        val remote = input.url.isNotBlank()
        if (remote == input.executable.isNotBlank()) return McpAddResult.Invalid(McpAddProblem.Kind)
        val timeoutMs =
            when (val t = timeoutMs(input.timeoutSeconds)) {
                is Timeout.Bad -> return McpAddResult.Invalid(McpAddProblem.Timeout)
                is Timeout.Ok -> t.ms
            }
        val headers =
            if (remote)
                when (val p = pairs(input.headers, headers = true)) {
                    is Pairs.Bad -> return McpAddResult.Invalid(p.problem)
                    is Pairs.Ok -> p.values
                }
            else emptyMap()
        val environment =
            if (!remote)
                when (val p = pairs(input.environment, headers = false)) {
                    is Pairs.Bad -> return McpAddResult.Invalid(p.problem)
                    is Pairs.Ok -> p.values
                }
            else emptyMap()
        val command: ImmutableList<String> =
            if (remote) persistentListOf()
            else
                (listOf(input.executable.trim()) +
                        input.arguments.lines().map { it.trim() }.filter { it.isNotEmpty() })
                    .toImmutableList()
        return McpAddResult.Ready(
            McpServerDraft(
                name = name,
                kind = if (remote) McpServerKind.Remote else McpServerKind.Local,
                url = input.url.trim().takeIf { remote },
                command = command,
                cwd = null,
                headers = headers.toImmutableMap(),
                environment = environment.toImmutableMap(),
                detectOAuth = false,
                timeoutMs = timeoutMs,
            )
        )
    }

    /** Flutter `_timeoutError`: blank is no timeout; otherwise a whole number of seconds above zero. */
    public fun timeoutProblem(text: String): Boolean = timeoutMs(text) is Timeout.Bad

    private sealed interface Timeout {
        data class Ok(val ms: Int?) : Timeout

        data object Bad : Timeout
    }

    private fun timeoutMs(text: String): Timeout {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return Timeout.Ok(null)
        val seconds = trimmed.toIntOrNull() ?: return Timeout.Bad
        if (seconds <= 0 || seconds > Int.MAX_VALUE / 1000) return Timeout.Bad
        return Timeout.Ok(seconds * 1000)
    }

    private sealed interface Pairs {
        data class Ok(val values: Map<String, String>) : Pairs

        data class Bad(val problem: McpAddProblem) : Pairs
    }

    private val NAME_BAD = Regex("[\r\n=]")
    private val LINE_BREAK = Regex("[\r\n]")

    /** Flutter `_pairs`: names trimmed, values trimmed, a fully empty row skipped, the first problem wins. */
    private fun pairs(rows: List<McpPairRow>, headers: Boolean): Pairs {
        val out = LinkedHashMap<String, String>()
        rows.forEachIndexed { index, row ->
            val key = row.name.trim()
            val value = row.value.trim()
            if (key.isEmpty() && value.isEmpty()) return@forEachIndexed
            if (key.isEmpty() || NAME_BAD.containsMatchIn(key))
                return Pairs.Bad(McpAddProblem.PairName(headers, index + 1))
            if (LINE_BREAK.containsMatchIn(value))
                return Pairs.Bad(McpAddProblem.PairValue(headers, index + 1))
            if (key in out) return Pairs.Bad(McpAddProblem.Duplicate(headers, key))
            out[key] = value
        }
        return Pairs.Ok(out)
    }
}
