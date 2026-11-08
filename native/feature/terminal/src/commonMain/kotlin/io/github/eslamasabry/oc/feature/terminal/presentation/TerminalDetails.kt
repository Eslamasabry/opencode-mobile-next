package io.github.eslamasabry.oc.feature.terminal.presentation

import io.github.eslamasabry.oc.core.model.TerminalProcess

/**
 * "Terminal details" (Flutter terminal_surface.dart `_details`): the server's word for the program (running or
 * ended), the command with its arguments, the folder when there is one, the process id and, once it ended, the exit
 * code. The screen masks every value through the kit's redaction before drawing it: a command line can carry a
 * token.
 */
public data class TerminalDetailsUi(
    val title: String,
    val running: Boolean,
    val command: String,
    val folder: String?,
    val pid: Int,
    val exitCode: Int?,
)

public object TerminalDetails {
    public fun of(process: TerminalProcess): TerminalDetailsUi =
        TerminalDetailsUi(
            title = process.title.ifBlank { process.command },
            running = process.running,
            command = (listOf(process.command) + process.arguments).joinToString(" "),
            folder = process.directory.takeIf { it.isNotEmpty() },
            pid = process.pid,
            exitCode = process.exitCode.takeUnless { process.running },
        )
}
