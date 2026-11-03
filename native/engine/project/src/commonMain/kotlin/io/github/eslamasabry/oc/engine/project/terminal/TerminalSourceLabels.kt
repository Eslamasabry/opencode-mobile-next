package io.github.eslamasabry.oc.engine.project.terminal

/**
 * A label for where a terminal's shell runs. The UI turns [BuiltInLinux] and [OpenCodeServer] into its own words
 * (`localTerminalSourcePhone`, `localTerminalSourceServer`); [english] is the English text for tests and fallbacks.
 */
public sealed interface TerminalLabel {
    public val english: String

    /** The app's own Linux on this phone. */
    public data object BuiltInLinux : TerminalLabel {
        override val english: String = "Built-in Linux"
    }

    /** A server without a name of its own. */
    public data object OpenCodeServer : TerminalLabel {
        override val english: String = "OpenCode server"
    }

    /** A server by its profile name, as typed (trimmed). */
    public data class Named(val name: String) : TerminalLabel {
        override val english: String get() = name
    }

    /** A server name that reads like the built-in label: the runtime goes after it ("name · OpenCode server"). */
    public data class NamedWithRuntime(val name: String) : TerminalLabel {
        override val english: String get() = "$name$SEPARATOR${OpenCodeServer.english}"
    }

    public companion object {
        public const val SEPARATOR: String = " · "
    }
}

/** The two labels of a terminal's host switch. */
public data class TerminalSourceLabels(val phone: TerminalLabel, val server: TerminalLabel)

/**
 * The host switch's labels: the built-in Linux, and the connected server by its own name
 * ([TerminalLabel.OpenCodeServer] when it has none). They never read the same: a server called "This phone" sits next
 * to "Built-in Linux", and a name equal (ignoring case) to [phoneWords] — the words the UI shows for
 * [TerminalLabel.BuiltInLinux] — gets the runtime added.
 */
public fun terminalSourceLabels(
    serverName: String?,
    phoneWords: String = TerminalLabel.BuiltInLinux.english,
): TerminalSourceLabels {
    val name = serverName?.trim()
    val server =
        when {
            name.isNullOrEmpty() -> TerminalLabel.OpenCodeServer
            name.equals(phoneWords, ignoreCase = true) -> TerminalLabel.NamedWithRuntime(name)
            else -> TerminalLabel.Named(name)
        }
    return TerminalSourceLabels(phone = TerminalLabel.BuiltInLinux, server = server)
}
