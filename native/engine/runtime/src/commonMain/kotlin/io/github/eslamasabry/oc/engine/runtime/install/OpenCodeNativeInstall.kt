package io.github.eslamasabry.oc.engine.runtime.install

/**
 * Port of lib/builtin/setup/components.dart (`OpenCodeAsset`, `OpenCodePins`, `OpenCodeInstallFailure`,
 * `SetupScripts.openCodeNativeInstall` / `openCodeNativeCheck` / `openCodeCheck` / `openCodeInstall`) and the OpenCode
 * row of `setupComponents`: the in-app Ubuntu installs the pinned native OpenCode program, never npm; Termux keeps the
 * shared npm text its own manager runs ([OpenCodeUbuntuSetup]).
 */
public data class OpenCodeAsset(val url: String, val sha256: String, val member: String)

/** One pinned download per CPU. */
public data class OpenCodeAssets(val arm64: OpenCodeAsset, val x64: OpenCodeAsset)

/** Flutter `OpenCodePins`: native builds, checked against these SHA-256s before anything is unpacked or run. */
public object OpenCodePins {
    public val v2Arm64: OpenCodeAsset =
        OpenCodeAsset(
            url = "https://registry.npmjs.org/@opencode/cli-linux-arm64/-/cli-linux-arm64-2.2.5.tgz",
            sha256 = "f8c5a82075fede9b5b044ed6c8a6d13ad807a7e62caea1cd0a878adef49eb053",
            member = "package/bin/opencode",
        )
    public val v2X64: OpenCodeAsset =
        OpenCodeAsset(
            url = "https://registry.npmjs.org/@opencode/cli-linux-x64-baseline/-/cli-linux-x64-baseline-2.2.5.tgz",
            sha256 = "873856acbaa71c5ed8df6433e312dfa0c82a86e2cddfb9d80f0a31123a917a08",
            member = "package/bin/opencode",
        )

    public val pinned: OpenCodeAssets = OpenCodeAssets(arm64 = v2Arm64, x64 = v2X64)

    /** Where the program lives in Ubuntu; `/usr/local/bin/<command>` links it. */
    public const val DIR: String = "/opt/opencode2"
    public const val COMMAND: String = "opencode2"
}

/**
 * Flutter `OpenCodeInstallFailure`: the last line a failed native install ends with (script data). [reasonOf] turns a
 * failed component's last line into the reason the setup page words (Flutter `describeSetupFailure`).
 */
public enum class OpenCodeInstallFailure(public val line: String) {
    /** l10n `phoneSetupErrorOpenCodeNoProgram`. */
    NoProgram("[oc] OpenCode's download did not contain its program"),

    /** l10n `phoneSetupErrorOpenCodeWontRun`. */
    WontRun("[oc] OpenCode's program does not run on this phone"),
    WrongVersion("[oc] OpenCode's program reports another version"),

    /** l10n `phoneSetupErrorOpenCodeNoStart`. */
    NoStart("[oc] OpenCode was installed but did not start"),
    Unpinned("[oc] This app can only install its own pinned OpenCode"),
    Cpu("[oc] OpenCode has no build for this phone's processor"),
}

/** Why a native OpenCode install failed, as the setup page says it (one l10n key each). */
public enum class OpenCodeInstallReason {
    /** l10n `phoneSetupErrorOpenCodeNoProgram`. */
    NoProgram,

    /** l10n `phoneSetupErrorOpenCodeWontRun`. */
    WontRun,

    /** l10n `phoneSetupErrorOpenCodeNoStart`. */
    NoStart,

    /** l10n `phoneSetupErrorChecksum(title)`. */
    Checksum,

    /** l10n `phoneSetupErrorInstall(title)`. */
    Other,
    ;

    public companion object {
        private val checksum = Regex("checksum|did not match its checksum", RegexOption.IGNORE_CASE)

        /** The install's own reason line first (startsWith), then a checksum mention, else [Other]. */
        public fun of(lastLine: String): OpenCodeInstallReason =
            when {
                lastLine.startsWith(OpenCodeInstallFailure.NoProgram.line) -> NoProgram
                lastLine.startsWith(OpenCodeInstallFailure.WontRun.line) -> WontRun
                lastLine.startsWith(OpenCodeInstallFailure.NoStart.line) -> NoStart
                checksum.containsMatchIn(lastLine) -> Checksum
                else -> Other
            }
    }
}

/** Which host's Ubuntu the setup installs into (Flutter `SetupHostKind`). */
public enum class SetupHost { Builtin, Termux }

/** The OpenCode component's scripts for one host (Flutter `setupComponents(...)` row `opencode`). */
public data class OpenCodeComponentScripts(val checkScript: String, val installScript: String)

public object OpenCodeInstallScripts {
    private val versionShape = Regex("^[A-Za-z0-9._+-]+$")

    private fun selected(version: String?): String {
        val chosen = version ?: ManagedRuntime.OpenCode2.pinnedVersion
        require(versionShape.matches(chosen)) { "Invalid package version." }
        return chosen
    }

    /** The in-app host installs the pinned native program; Termux keeps the shared npm text. Only OpenCode 2 installs. */
    public fun component(host: SetupHost, version: String? = null): OpenCodeComponentScripts =
        when (host) {
            SetupHost.Builtin -> OpenCodeComponentScripts(nativeCheck(version), nativeInstall(version))
            SetupHost.Termux -> OpenCodeComponentScripts(termuxCheck(version), termuxInstall(version))
        }

    /** Flutter `BuiltinLinux.versionScript(opencode2)` inside `openCodeCheck`. */
    private const val VERSION_SCRIPT: String = "opencode2 --version"

    /** Flutter `SetupScripts.openCodeCheck`: passes only for OpenCode 2 at the requested version. */
    public fun termuxCheck(version: String? = null): String {
        val chosen = selected(version)
        return "set -e\n" +
            "installed=${'$'}($VERSION_SCRIPT)\n" +
            "installed=${'$'}{installed##* v}\n" +
            "[ \"${'$'}installed\" = '$chosen' ]\n" +
            "echo \"${'$'}installed\"\n"
    }

    /** Flutter `SetupScripts.openCodeInstall`: the shared text the Termux manager also runs, fed to `bash -s`. */
    public fun termuxInstall(version: String? = null): String {
        val chosen = selected(version)
        return "set -eu\n" +
            "oc_stage 'Installing OpenCode $chosen'\n" +
            "export OC_REQUESTED_VERSION='$chosen'\n" +
            "export OC_RUNTIME='${ManagedRuntime.OpenCode2.wireName}'\n" +
            "bash -s <<'OC_PROOT_SETUP'\n" +
            "${OpenCodeUbuntuSetup.SCRIPT}OC_PROOT_SETUP\n" +
            "installed=${'$'}(opencode2 --version)\n" +
            "oc_version \"${'$'}{installed##* v}\"\n"
    }

    /**
     * Flutter `SetupScripts.openCodeNativeCheck`: passes only when `opencode2` resolves to the pinned native program this
     * app installed (not npm's wrapper from an older build) at the requested version. [root] prefixes every path (tests).
     */
    public fun nativeCheck(version: String? = null, root: String = ""): String {
        val chosen = selected(version)
        val directory = "$root${OpenCodePins.DIR}"
        val program = "$directory/bin/${OpenCodePins.COMMAND}"
        val c = OpenCodePins.COMMAND
        return "${InstallPrelude.COMPONENT_UPDATE}\nset -e\n" +
            "oc_update_recover '$directory'\n" +
            "oc_update_recover '$root/usr/local/bin/$c'\n" +
            "oc_bin=${'$'}(command -v $c)\n" +
            "[ \"${'$'}(readlink -f \"${'$'}oc_bin\")\" = '$program' ]\n" +
            "installed=${'$'}(\"${'$'}oc_bin\" --version | tail -n 1)\n" +
            "installed=${'$'}{installed##* v}\n" +
            "[ \"${'$'}installed\" = '$chosen' ]\n" +
            "echo \"${'$'}installed\"\n"
    }

    /**
     * Flutter `SetupScripts.openCodeNativeInstall`: picks the archive for this CPU and downloads it with `oc_download`
     * (a file that does not match its SHA-256 is deleted and the run stops before anything is unpacked); unpacks only
     * the program next to the current install; proves it (`--version`, then a short authenticated `serve`); only then
     * journals activation and links `/usr/local/bin/opencode2`, restoring the previous program and link on any failure
     * after activation. A failure ends with one of [OpenCodeInstallFailure]'s lines. A version other than the pin is
     * refused with exit 64. [assets], [root] and [probeSeconds] exist for the script tests only.
     */
    public fun nativeInstall(
        version: String? = null,
        assets: OpenCodeAssets? = null,
        root: String = "",
        probeSeconds: Int = 120,
    ): String {
        val chosen = selected(version)
        val pinned = ManagedRuntime.OpenCode2.pinnedVersion
        if (chosen != pinned) {
            return "echo '${OpenCodeInstallFailure.Unpinned.line} ($pinned, not $chosen)' >&2\nexit 64\n"
        }
        val pins = assets ?: OpenCodePins.pinned
        val command = OpenCodePins.COMMAND
        val dir = "$root${OpenCodePins.DIR}"
        val health = "/api/info /api/health"
        val modules = "$root/usr/local/lib/node_modules"
        val leftovers = "\"$root/opt/oc2\" \"$modules/@opencode-ai/cli\" \"$modules/@opencode-ai/\"cli-linux-*"
        val d = "$"
        return """set -eu
oc_fail() { echo "${d}*" >&2; exit 1; }
case "${d}(uname -m)" in
  aarch64|arm64) oc_url='${pins.arm64.url}'; oc_sha=${pins.arm64.sha256}; oc_member='${pins.arm64.member}' ;;
  x86_64|amd64) oc_url='${pins.x64.url}'; oc_sha=${pins.x64.sha256}; oc_member='${pins.x64.member}' ;;
  *) echo "${OpenCodeInstallFailure.Cpu.line} (${d}(uname -m))" >&2; exit 64 ;;
esac
oc_dir='$dir'
oc_new="${d}oc_dir.new"
oc_link='$root/usr/local/bin/$command'
oc_link_new="${d}oc_link.new"
oc_update_recover "${d}oc_dir" || exit 1
oc_update_recover "${d}oc_link" || exit 1
oc_file="$root/var/cache/oc-setup/opencode-$chosen-${d}oc_sha.archive"
oc_stage 'Downloading OpenCode $chosen'
# Checked against the pinned SHA-256 before anything is unpacked; a file
# that does not match is deleted and the run stops here.
oc_download "${d}oc_url" "${d}oc_file" "${d}oc_sha"
oc_stage 'Unpacking OpenCode'
rm -rf "${d}oc_new"
mkdir -p "${d}oc_new/bin"
if ! tar -xzf "${d}oc_file" -C "${d}oc_new" "${d}oc_member" ||
  [ ! -f "${d}oc_new/${d}oc_member" ] || [ -L "${d}oc_new/${d}oc_member" ]; then
  rm -rf "${d}oc_new" "${d}oc_file"
  oc_fail "${OpenCodeInstallFailure.NoProgram.line}"
fi
mv -f "${d}oc_new/${d}oc_member" "${d}oc_new/bin/$command.tmp"
mv -f "${d}oc_new/bin/$command.tmp" "${d}oc_new/bin/$command"
case "${d}oc_member" in */*) rm -rf "${d}oc_new/${d}{oc_member%%/*}" ;; esac
chmod 755 "${d}oc_new/bin/$command"
oc_program="${d}oc_new/bin/$command"
if ! oc_out=${d}("${d}oc_program" --version 2>&1); then
  printf '%s\n' "${d}oc_out" | tail -n 20
  rm -rf "${d}oc_new"
  oc_fail "${OpenCodeInstallFailure.WontRun.line}"
fi
oc_got=${d}(printf '%s\n' "${d}oc_out" | tail -n 1)
oc_got=${d}{oc_got##* v}
if [ "${d}oc_got" != '$chosen' ]; then
  printf '%s\n' "${d}oc_out" | tail -n 5
  rm -rf "${d}oc_new"
  oc_fail "${OpenCodeInstallFailure.WrongVersion.line} (${d}oc_got, not $chosen)"
fi
oc_stage 'Checking that OpenCode starts'
oc_probe=${d}(mktemp -d "${d}{TMPDIR:-/tmp}/oc-probe.XXXXXX")
# A busy port is not a broken OpenCode: try up to three ports.
oc_started=
for oc_try in 1 2 3; do
oc_port=${d}((30000 + (${d}${d} + oc_try * 7919 + ${d}(od -An -N2 -tu2 /dev/urandom | tr -d ' ')) % 20000))
oc_pw=${d}(od -An -N12 -tx1 /dev/urandom | tr -d ' \n')
(
  cd "${d}oc_probe"
  unset OPENCODE_CONFIG OPENCODE_CONFIG_CONTENT OPENCODE_DB
  export HOME="${d}oc_probe" XDG_DATA_HOME="${d}oc_probe/data" XDG_CACHE_HOME="${d}oc_probe/cache"
  export XDG_STATE_HOME="${d}oc_probe/state" XDG_CONFIG_HOME="${d}oc_probe/config"
  export OPENCODE_CONFIG_DIR="${d}oc_probe/config/opencode" OPENCODE_DISABLE_MODELS_FETCH=1
  export OPENCODE_SERVER_USERNAME=opencode OPENCODE_SERVER_PASSWORD="${d}oc_pw"
  exec "${d}oc_program" serve --hostname 127.0.0.1 --port "${d}oc_port"
) > "${d}oc_probe/log" 2>&1 </dev/null &
oc_pid=${d}!
oc_alive() {
  kill -0 "${d}oc_pid" 2>/dev/null || return 1
  case "${d}(sed 's/^.*) //' "/proc/${d}oc_pid/stat" 2>/dev/null)" in Z*|X*) return 1 ;; esac
}
oc_started=
oc_i=0
while [ "${d}oc_i" -lt $probeSeconds ]; do
  for oc_path in $health; do
    oc_code=${d}(curl -s --max-time 3 -o /dev/null -w '%{http_code}' \
      -u "opencode:${d}oc_pw" "http://127.0.0.1:${d}oc_port${d}oc_path" 2>/dev/null) || true
    # The probe supplies its own fresh credential. A 401 may be another
    # listener on this port, and cannot prove this candidate started.
    case "${d}oc_code" in 200) oc_alive && oc_started=1 ;; esac
  done
  [ -z "${d}oc_started" ] || break
  oc_alive || break
  sleep "${d}{OC_PROBE_POLL:-1}"
  oc_i=${d}((oc_i + 1))
done
kill "${d}oc_pid" 2>/dev/null || true
oc_i=0
while [ "${d}oc_i" -lt 5 ] && oc_alive; do sleep "${d}{OC_PROBE_POLL:-1}"; oc_i=${d}((oc_i + 1)); done
kill -9 "${d}oc_pid" 2>/dev/null || true
wait "${d}oc_pid" 2>/dev/null || true
[ -z "${d}oc_started" ] || break
grep -qiE 'address already in use|EADDRINUSE' "${d}oc_probe/log" 2>/dev/null || break
done
if [ -z "${d}oc_started" ]; then
  echo "[oc] What OpenCode said:"
  tail -n 20 "${d}oc_probe/log" | sed 's/^/  /'
  rm -rf "${d}oc_probe" "${d}oc_new"
  oc_fail "${OpenCodeInstallFailure.NoStart.line}"
fi
rm -rf "${d}oc_probe"
mkdir -p "$root/usr/local/bin"
rm -f "${d}oc_link_new"
ln -s "${d}oc_dir/bin/$command" "${d}oc_link_new"
oc_activation_failed() {
  oc_restore_failed=0
  oc_update_recover "${d}oc_dir" || oc_restore_failed=1
  oc_update_recover "${d}oc_link" || oc_restore_failed=1
  if [ "${d}oc_restore_failed" != 0 ]; then
    oc_fail '[oc] A component update could not be restored. Run setup again.'
  fi
  oc_fail '[oc] OpenCode could not finish updating. Run setup again.'
}
oc_update_activate "${d}oc_dir" "${d}oc_new" || oc_activation_failed
oc_update_activate "${d}oc_link" "${d}oc_link_new" || oc_activation_failed
if ! oc_active_version=${d}("${d}oc_link" --version 2>/dev/null); then
  oc_activation_failed
fi
oc_active_version=${d}(printf '%s\n' "${d}oc_active_version" | tail -n 1)
oc_active_version=${d}{oc_active_version##* v}
[ "${d}oc_active_version" = '$chosen' ] || oc_activation_failed
# Commit code before its command link; never delete the retained good backup.
oc_update_commit "${d}oc_dir" || oc_activation_failed
oc_update_commit "${d}oc_link" || oc_activation_failed
# What npm installed before, wrapper and all.
rm -rf $leftovers
rm -f "${d}oc_file"
""" + "oc_version \"${'$'}oc_got\"\n"
    }
}
