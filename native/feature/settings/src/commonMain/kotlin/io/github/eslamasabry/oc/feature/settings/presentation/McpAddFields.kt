package io.github.eslamasabry.oc.feature.settings.presentation

import io.github.eslamasabry.oc.engine.integrations.catalog.McpAddForm
import io.github.eslamasabry.oc.engine.integrations.catalog.McpAddInput
import io.github.eslamasabry.oc.engine.integrations.catalog.McpAddResult
import io.github.eslamasabry.oc.engine.integrations.catalog.McpPairRow

/**
 * s23-projects hook: the Library's Add MCP sheet sends what [McpAddForm] makes of its fields, as Flutter's
 * mcp_setup_screen.dart `_save` does: names and values trimmed, headers only with an address and environment only with
 * a command, the timeout typed in whole seconds and sent as milliseconds. The command field holds the program on its
 * first line and one argument per line after it. The folder and the sign-in detection stay the sheet's own.
 */
public object McpAddFields {
    public fun draft(
        name: String,
        local: Boolean,
        url: String,
        command: String,
        variables: List<Pair<String, String>>,
        timeoutSeconds: String,
        cwd: String,
        detectOAuth: Boolean,
    ): McpAddResult {
        val lines = command.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val rows = variables.map { (k, v) -> McpPairRow(k, v) }
        val input =
            McpAddInput(
                name = name,
                url = if (local) "" else url,
                executable = if (local) lines.firstOrNull().orEmpty() else "",
                arguments = if (local) lines.drop(1).joinToString("\n") else "",
                headers = if (local) emptyList() else rows,
                environment = if (local) rows else emptyList(),
                timeoutSeconds = timeoutSeconds,
            )
        return when (val result = McpAddForm.draft(input)) {
            is McpAddResult.Invalid -> result
            is McpAddResult.Ready ->
                McpAddResult.Ready(
                    result.draft.copy(
                        cwd = cwd.trim().takeIf { local && it.isNotEmpty() },
                        detectOAuth = !local && detectOAuth,
                    )
                )
        }
    }

    /** A saved timeout (milliseconds) as the seconds the field shows; blank when none. */
    public fun secondsOf(timeoutMs: Int?): String = timeoutMs?.takeIf { it > 0 }?.let { (it / 1000).coerceAtLeast(1).toString() }.orEmpty()
}
