package io.github.eslamasabry.oc.engine.agents.scripts

private const val ESSENTIALS_DOWNLOAD_BYTES = 45_000_000L
private const val PYTHON_DOWNLOAD_BYTES = 25_000_000L

/** Ubuntu packages an agent install may need. Estimates cover a whole missing group, as in first setup. */
public enum class AgentPackageStep(
    public val id: String,
    public val downloadBytes: Long,
    private val packages: String,
    private val version: String,
) {
    Essentials(
        "essentials", ESSENTIALS_DOWNLOAD_BYTES, "curl ca-certificates git openssh-client",
        "git --version | cut -d ' ' -f 3",
    ),
    Python("python", PYTHON_DOWNLOAD_BYTES, "python3 python3-venv python3-pip", "python3 --version | cut -d ' ' -f 2");

    // A held package is installed too. Ignore dpkg's selection (install/hold), but check its error and install states.
    private val statusCheck: String
        get() = $$"""
            present() {
              state=$(dpkg-query -W -f='${db:Status-Eflag} ${db:Status-Status}' "$1" 2>/dev/null) || return 1
              [ "$state" = 'ok installed' ]
            }
        """.trimIndent()

    public val check: String
        get() = $$"""
            $$statusCheck
            for pkg in $$packages; do present "$pkg" || exit 1; done
        """.trimIndent()

    /** Recheck at execution. Dependencies that require an upgrade belong in Update system. */
    public val install: String
        get() = $$"""
            set -eu
            $$statusCheck
            set --
            for pkg in $$packages; do
              present "$pkg" || set -- "$@" "$pkg"
            done
            if [ "$#" -gt 0 ]; then
              export LC_ALL=C DEBIAN_FRONTEND=noninteractive
              oc_apt_lists_fresh || oc_apt_update
              set -- install -y --no-upgrade --no-remove --no-install-recommends -o Acquire::Retries=5 "$@"
              plan=$(apt-get --simulate "$@")
              if printf '%s\n' "$plan" | grep -q '^Inst [^ ]* \['; then
                echo '[oc] These packages require a system update.' >&2
                exit 1
              fi
              oc_apt_run '' "$@"
              for pkg in $$packages; do present "$pkg" || exit 1; done
            fi
            oc_version "$($$version)"
        """.trimIndent()
}
