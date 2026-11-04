package io.github.eslamasabry.oc.feature.servers.presentation

import io.github.eslamasabry.oc.core.model.BackendKind

/**
 * The commands a person runs on their computer, written once (port of Dart
 * `lib/ui/setup_commands.dart`). The connect screen shows the one that starts the chosen agent; a
 * copied command and a read one must never drift apart. Sources: `docs/paseo-connection.md`,
 * `docs/codex-connection.md`.
 */
public object SetupCommands {
    /** Starts OpenCode 2 and prints a pairing code. */
    public const val PAIR: String = "opencode2 pair"

    /** Starts OpenCode 2 on loopback with the password it generates. */
    public const val SERVE: String = "opencode2 serve --hostname 127.0.0.1 --port 4096"

    /** The Paseo daemon, kept off the relay. This app never uses the relay. */
    public const val PASEO_START: String = "paseo start --no-relay"

    /**
     * The Paseo daemon on the private network with a password (B-NAV-016): the password is asked
     * for privately (`read -rsp`, never echoed, never on the command line or in shell history) and
     * exported, so the copied command holds no secret and passes the redactor unchanged.
     */
    public const val PASEO_START_PRIVATE_NETWORK: String =
        "bash -c 'IFS= read -rsp \"Choose a daemon password > \" PASEO_PASSWORD &&\n" +
            "  printf \"\\n\" && test -n \"\$PASEO_PASSWORD\" &&\n" +
            "  export PASEO_PASSWORD &&\n" +
            "  exec paseo start --no-relay --listen \"\$(tailscale ip -4):6767\"'"

    /**
     * Starts Codex's app server (B-AGENT-241). One line, so it reads and copies as one command: a
     * block of `\` continuations shows a prompt on every line and is cut at the edge.
     */
    public const val CODEX_START: String =
        "codex app-server --listen ws://127.0.0.1:4141 --ws-auth capability-token " +
            "--ws-token-file /absolute/path/codex-capability-token"

    public const val CODEX_TOKEN: String =
        "umask 077\npython3 -c 'import secrets; print(secrets.token_urlsafe(32), end=\"\")' > /absolute/path/codex-capability-token\nchmod 600 /absolute/path/codex-capability-token"

    public const val CODEX_USB: String = "adb reverse tcp:4141 tcp:4141"

    /** Replaces an unsupported OpenCode 1 install (Dart `openCode2UpgradeCommand`). */
    public const val OPENCODE2_UPGRADE: String =
        "npm uninstall -g opencode-ai && npm install -g @opencode/cli@2.2.5"

    /**
     * The one command that starts each backend on the computer: data about the server, not a UI
     * branch.
     */
    private val START: Map<BackendKind, String> =
        mapOf(
            BackendKind.OpenCode2 to PAIR,
            BackendKind.Paseo to PASEO_START,
            BackendKind.Codex to CODEX_START,
        )

    public fun startFor(backend: BackendKind): String = START.getValue(backend)
}
