package io.github.eslamasabry.oc.engine.termuxhost

/**
 * Port of lib/ui/setup_commands.dart's HostScript/HostScripts (B-SEC-092): every host-side file the app tells people
 * to download is pinned to one published commit and checked against a SHA-256 before it is named or run. Nothing here
 * pipes a download into a shell.
 */
public data class HostScript(
    /** Path inside the repository, as `git` names it. */
    val path: String,
    /** Lower-case hex SHA-256 of the file at [HostScripts.COMMIT]. */
    val sha256: String,
    /** The file name the downloaded copy is saved under. */
    val saveAs: String,
) {
    /** The raw download, pinned to a commit (never a branch or a tag). */
    val url: String get() = "https://raw.githubusercontent.com/${HostScripts.REPOSITORY}/${HostScripts.COMMIT}/$path"

    /** Download to a side file, check it, and only then give it its real name. Each line ends in `&&`. */
    val verifiedDownload: String
        get() =
            "curl -fsSLo $saveAs.part \\\n" +
                "  $url &&\n" +
                "echo '$sha256  $saveAs.part' | sha256sum -c - &&\n" +
                "mv $saveAs.part $saveAs"
}

public object HostScripts {
    public const val REPOSITORY: String = "Eslamasabry/opencode-mobile-next"

    /** The commit of release [RELEASE] (tag `v1.0.44+50`), published on GitHub. */
    public const val COMMIT: String = "c62f159ae3c1741cb4ec0ef92b4941c0ddfc0a18"
    public const val RELEASE: String = "1.0.44"

    /** The AI Team front (docs/ai-team-host.md §5). */
    public val front: HostScript =
        HostScript(
            path = "tool/host/cp_front/front.py",
            sha256 = "b672254944c3d77cb18f85b2337773e330f82d29ed64690d57e4fdc26235cd47",
            saveAs = "opencode-mobile-front.py",
        )

    private const val GUIDE_BASE = "https://github.com/$REPOSITORY/blob/master/docs"
    public const val UBUNTU_GUIDE_URL: String = "$GUIDE_BASE/ubuntu-host.md"
    public const val TEAM_GUIDE_URL: String = "$GUIDE_BASE/ai-team-host.md"

    /** First-time install on the host: the official pinned OpenCode 2 package, listening on [port]. */
    public fun install(port: Int): String =
        "npm install -g @opencode/cli@${TermuxRuntime.OpenCode2.pinnedVersion} &&\n" +
            "opencode2 serve --hostname 127.0.0.1 --port $port"

    public const val TEAM_CHECK_TOOLS: String = "command -v gc bd dolt"

    /** Download the front, check it, and run the checked copy on the Tailscale address. */
    public val teamFront: String
        get() =
            "${front.verifiedDownload} &&\n" +
                "python3 ${front.saveAs} --supervisor http://127.0.0.1:8372 \\\n" +
                "  --bind \"\$(tailscale ip -4)\" --port 8373 \\\n" +
                "  --allow you@example.com"

    /** A curl or wget download fed straight into a shell: a download run unchecked. */
    public val PipedToShell: Regex = Regex("(curl|wget)\\b[^\\n|]*\\|\\s*(sudo\\s+)?(ba|z|da)?sh\\b")
}
