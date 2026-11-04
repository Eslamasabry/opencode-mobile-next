package io.github.eslamasabry.oc.engine.runtime.install

/** Published Flutter host-command pin, carried unchanged into the native host guide. */
public object PinnedHostCommands {
    public const val COMMIT: String = "c62f159ae3c1741cb4ec0ef92b4941c0ddfc0a18"
    public const val FRONT_SHA256: String =
        "b672254944c3d77cb18f85b2337773e330f82d29ed64690d57e4fdc26235cd47"
    public const val FRONT_NAME: String = "opencode-mobile-front.py"
    public const val FRONT_URL: String =
        "https://raw.githubusercontent.com/Eslamasabry/opencode-mobile-next/$COMMIT/tool/host/cp_front/front.py"

    /** A failed download or checksum never renames or executes the partial file. */
    public val verifiedFrontDownload: String =
        "curl -fsSLo $FRONT_NAME.part '$FRONT_URL' &&\n" +
            "echo '$FRONT_SHA256  $FRONT_NAME.part' | sha256sum -c - &&\n" +
            "mv $FRONT_NAME.part $FRONT_NAME"

    public val teamFront: String =
        verifiedFrontDownload +
            " &&\n" +
            "python3 $FRONT_NAME --supervisor http://127.0.0.1:8372 \\\n" +
            "  --bind \"\$(tailscale ip -4)\" --port 8373 \\\n" +
            "  --allow you@example.com"

    /** Installs the official pinned package; this only builds a command for the person's host. */
    public fun install(port: Int = 4096): String {
        require(port in 1..65535)
        return "npm install -g @opencode/cli@${UbuntuHostScript.PINNED_VERSION} &&\n" +
            "opencode2 serve --hostname 127.0.0.1 --port $port"
    }
}
