package io.github.eslamasabry.oc.engine.integrations.catalog

import io.github.eslamasabry.oc.core.model.McpServerDraft
import io.github.eslamasabry.oc.core.model.McpServerKind
import io.github.eslamasabry.oc.core.security.Redactor
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap

// Flutter McpSetupScreen header rows (P0.1: header values are secrets) and AppDiagnosticsController redaction.

/** One header row of the Add-server form. The value is a secret: masked until the person reveals it. */
public data class McpHeaderRow(val key: String = "", val value: String = "", val revealed: Boolean = false) {
    /** True while the value is drawn as dots. */
    public val masked: Boolean get() = !revealed
}

/**
 * The Add-MCP-server form's header rows. Add-only: it always starts with one empty row and is never prefilled from a
 * saved server. A lone row has no Remove. Values reach only the draft sent to the server: every value is registered
 * with the [Redactor] before it is used, and [saveFailed] keeps a fixed reason, never the server's or the value's text.
 */
public class McpSetupHeaders(private val redactor: Redactor) {
    public var rows: List<McpHeaderRow> = listOf(McpHeaderRow())
        private set

    /** A fixed problem from the last save; never server prose. */
    public var saveFailed: McpDraftProblem? = null
        private set

    public fun canRemove(index: Int): Boolean = rows.size > 1 && index in rows.indices

    public fun add() {
        rows = rows + McpHeaderRow()
    }

    public fun remove(index: Int) {
        if (canRemove(index)) rows = rows.filterIndexed { i, _ -> i != index }
    }

    public fun edit(index: Int, key: String? = null, value: String? = null) {
        if (index !in rows.indices) return
        value?.let(redactor::registerSecret)
        rows = rows.mapIndexed { i, row -> if (i == index) row.copy(key = key ?: row.key, value = value ?: row.value) else row }
    }

    /** Reveal on press, hide on the next press. */
    public fun toggleReveal(index: Int) {
        rows = rows.mapIndexed { i, row -> if (i == index) row.copy(revealed = !row.revealed) else row }
    }

    /** The headers to send: rows with a name. Each value is (again) registered as a secret first. */
    public fun headers(): Map<String, String> =
        rows.filter { it.key.trim().isNotEmpty() }.associate { row ->
            redactor.registerSecret(row.value)
            row.key.trim() to row.value
        }

    /** The remote draft for [name] and [url] with these headers, or the fixed reason it cannot be sent. */
    public fun draft(name: String, url: String): Pair<McpServerDraft?, McpDraftProblem?> {
        val draft =
            McpServerDraft(
                name = name,
                kind = McpServerKind.Remote,
                url = url,
                command = persistentListOf(),
                cwd = null,
                headers = headers().toImmutableMap(),
                environment = persistentMapOf(),
                detectOAuth = true,
                timeoutMs = null,
            )
        return when (val check = mcpDraftConfig(draft)) {
            is McpDraftCheck.Valid -> {
                saveFailed = null
                draft to null
            }
            is McpDraftCheck.Invalid -> {
                saveFailed = check.problem
                null to check.problem
            }
        }
    }
}

/** A diagnostics line for an MCP save failure: redacted (Authorization=Bearer values included) and bounded. */
public fun mcpDiagnosticsLine(redactor: Redactor, text: String): String = redactor.diagnostics(text)
