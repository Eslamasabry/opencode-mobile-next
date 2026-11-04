package io.github.eslamasabry.oc.engine.agents.phonecontext

/**
 * Port of lib/domain/phone_agent_context.dart: what an agent running on the person's phone is told about where it is.
 *
 * OpenCode loads a global `AGENTS.md` into every conversation. The phone servers (Termux and the built-in Ubuntu)
 * write this block into the global file of both OpenCode generations before each start. Only the text between the
 * markers is ours; anything the person wrote in the file stays. The block is agent instruction text (not app copy),
 * byte-identical to the Flutter build.
 */
public object PhoneAgentContext {
    public const val BEGIN: String = "<!-- opencode-mobile:phone-context:begin -->"
    public const val END: String = "<!-- opencode-mobile:phone-context:end -->"

    /** OpenCode 1 reads `~/.config/opencode/AGENTS.md`; OpenCode 2 runs with its config under `/root/.oc-opencode2`. */
    public val files: List<String> = listOf(
        "/root/.config/opencode/AGENTS.md",
        "/root/.oc-opencode2/config/opencode/AGENTS.md",
    )

    public enum class Host(internal val text: String) {
        Termux("Ubuntu running in Termux (under proot)"),
        Builtin("the OpenCode Mobile app's built-in Ubuntu"),
    }

    private const val INTRO =
        "## Where you are running\n" +
            "\n" +
            "Written by OpenCode Mobile; the text between these markers is " +
            "replaced on each server start.\n" +
            "\n" +
            "- You run on the person's Android phone, inside "

    private const val REST =
        ". The person talks to you through the OpenCode Mobile app on this " +
            "same phone. There is no desktop app, no display, no window you can " +
            "open, and no adb connection to this phone from here.\n" +
            "- Projects live in /root/projects. The person sees your replies, " +
            "changes and files in the app.\n" +
            "- A server you start on 127.0.0.1:PORT opens in the phone's own " +
            "browser at http://127.0.0.1:PORT; give the person that link to try a " +
            "web app. Bind servers to 127.0.0.1 only, never to every network " +
            "interface, unless the person asks for more.\n" +
            "- There is no visible browser here. Headless browsers (Playwright and " +
            "similar) work but are slow and use a lot of memory: prefer tests, " +
            "curl and logs, and close any browser you start as soon as you are " +
            "done.\n" +
            "- The phone's memory and CPU are limited and shared with the person's " +
            "apps. Android stops background processes when an app runs more than " +
            "about 32 of them, and can close this whole environment when memory " +
            "runs low. Avoid many parallel processes, file watchers and leftover " +
            "servers; stop what you start once it is not needed.\n" +
            "- Long builds (Gradle, the Android SDK, large installs) are slow here " +
            "and may be stopped by Android: say so before starting one.\n"

    /** The block for a server hosted in [host]. */
    public fun block(host: Host): String = "$BEGIN\n$INTRO${host.text}$REST$END\n"

    /**
     * POSIX sh, run inside the Ubuntu: writes the block into every file in [files], replacing an earlier block and
     * keeping everything else. `OC_CTX_ROOT` writes under another root (tests).
     */
    public fun script(host: Host): String = "${SCRIPT_HEAD}cat <<'OC_CTX_BODY'\n${block(host)}OC_CTX_BODY\n$SCRIPT_TAIL"

    public val termuxScript: String get() = script(Host.Termux)
    public val builtinScript: String get() = script(Host.Builtin)

    private const val SCRIPT_HEAD = "oc_ctx_body() {\n"

    private const val SCRIPT_TAIL =
        "}\n" +
            "oc_ctx() {\n" +
            "  f=\"\${OC_CTX_ROOT:-}\$1\"\n" +
            "  mkdir -p \"\$(dirname \"\$f\")\"\n" +
            "  tmp=\"\$f.oc-tmp.\$\$\"\n" +
            "  if [ -f \"\$f\" ]; then\n" +
            "    awk -v b='$BEGIN' -v e='$END' '\n" +
            "      \$0 == b { skip = 1; next }\n" +
            "      \$0 == e { skip = 0; next }\n" +
            "      skip { next }\n" +
            "      \$0 == \"\" { if (seen) blank++; next }\n" +
            "      { while (blank > 0) { print \"\"; blank-- } print; seen = 1 }\n" +
            "    ' \"\$f\" > \"\$tmp\"\n" +
            "  else\n" +
            "    : > \"\$tmp\"\n" +
            "  fi\n" +
            "  if [ -s \"\$tmp\" ]; then printf '\\n' >> \"\$tmp\"; fi\n" +
            "  oc_ctx_body >> \"\$tmp\"\n" +
            "  mv \"\$tmp\" \"\$f\"\n" +
            "}\n" +
            "oc_ctx /root/.config/opencode/AGENTS.md\n" +
            "oc_ctx /root/.oc-opencode2/config/opencode/AGENTS.md\n"
}
