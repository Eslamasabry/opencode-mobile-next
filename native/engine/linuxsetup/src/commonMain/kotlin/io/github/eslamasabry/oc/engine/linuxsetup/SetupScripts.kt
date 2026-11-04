package io.github.eslamasabry.oc.engine.linuxsetup

/**
 * The scripts behind [setupComponentRegistry] for the OpenCode path (port of SetupScripts in
 * lib/builtin/setup/components.dart, verbatim text). Checks exit 0 only when the piece is there and works, and print
 * its version on their last line. Installs are idempotent and report with the setup prelude's `oc_*` helpers, which
 * live in :engine:setupscripts: a full install script is produced only through a [SetupScriptWrapper].
 *
 * AI Team, Claude and voice keep their scripts out of this module (AI Team's are in Flutter's aiteam_scripts.dart;
 * voice is an app-side component with no script).
 */
public object SetupScripts {
    public const val LINUX_CHECK: String = """[ -s /etc/os-release ] && . /etc/os-release && echo "${'$'}{VERSION%% *}""""

    public const val ESSENTIALS_CHECK: String = """set -e
command -v curl >/dev/null
command -v git >/dev/null
command -v ssh >/dev/null
[ -s /etc/ssl/certs/ca-certificates.crt ]
git --version | cut -d ' ' -f 3
"""

    public const val ESSENTIALS_INSTALL: String = """set -eu
oc_apt_install curl ca-certificates git openssh-client
oc_version "${'$'}(git --version | cut -d ' ' -f 3)"
"""

    public const val ESSENTIALS_PRESENCE: String =
        "command -v git >/dev/null 2>&1 || command -v curl >/dev/null 2>&1 || command -v ssh >/dev/null 2>&1"

    public const val PYTHON_CHECK: String = """set -e
python3 -c 'import ensurepip, venv' >/dev/null
python3 -m pip --version >/dev/null
python3 --version | cut -d ' ' -f 2
"""

    public const val PYTHON_INSTALL: String = """set -eu
oc_apt_install python3 python3-venv python3-pip
oc_version "${'$'}(python3 --version | cut -d ' ' -f 2)"
"""

    public const val PYTHON_REMOVE: String = """set -eu
export DEBIAN_FRONTEND=noninteractive
apt-get remove -y python3-venv python3-pip
apt-get autoremove -y
"""

    /** What [PYTHON_REMOVE] would delete, in kilobytes. */
    public const val PYTHON_SIZE: String = """set -eu
packages=${'$'}(apt-get -s --auto-remove remove python3-venv python3-pip 2>/dev/null | awk '/^Remv /{print ${'$'}2}')
[ -n "${'$'}packages" ] || { echo 0; exit 0; }
dpkg-query -W -f='${'$'}{Installed-Size}\n' ${'$'}packages 2>/dev/null | awk '{s+=${'$'}1} END {print s+0}'
"""

    public const val PYTHON_PRESENCE: String = """set -eu
[ -r /var/lib/dpkg/status ] || exit 2
packages=${'$'}(dpkg-query -W -f='${'$'}{Package} ${'$'}{Status}\n') || exit 2
printf '%s\n' "${'$'}packages" | grep -Eq '^python3-(pip|venv) install ok installed${'$'}'
"""

    /** Only the pinned Node counts. */
    public val NODE_CHECK: String = """set -e
[ "${'$'}(node --version)" = '${NodePins.VERSION}' ]
command -v npm >/dev/null
[ "${'$'}(npm config get prefix)" = /usr/local ]
node --version | sed 's/^v//'
"""

    /** The Termux host: the pinned Node, or an older Node from Ubuntu's own packages, as long as it and npm run. */
    public const val TERMUX_NODE_CHECK: String = """set -e
node --version >/dev/null
command -v npm >/dev/null
node --version | sed 's/^v//'
"""

    public const val NODE_PRESENCE: String = "[ -e /opt/node ] || [ -L /opt/node ] || command -v node >/dev/null 2>&1"

    /** Node from its official pinned download, resumable from /var/cache/oc-setup, deleted once unpacked. */
    public val NODE_INSTALL: String
        get() {
            val plain = NodePins.VERSION.replaceFirst("v", "")
            return """set -eu
case "${'$'}(uname -m)" in
  aarch64|arm64) node_arch=arm64; node_sha=${NodePins.SHA256_ARM64} ;;
  x86_64|amd64) node_arch=x64; node_sha=${NodePins.SHA256_X64} ;;
  *) echo "[oc] Unsupported CPU: ${'$'}(uname -m)" >&2; exit 64 ;;
esac
node_version=${NodePins.VERSION}
node_file=/var/cache/oc-setup/node-${'$'}node_version-linux-${'$'}node_arch.tar.gz
oc_stage 'Downloading Node.js ${plain}'
oc_download "${NodePins.BASE_URL}/${'$'}node_version/node-${'$'}node_version-linux-${'$'}node_arch.tar.gz" \
  "${'$'}node_file" "${'$'}node_sha"
oc_stage 'Unpacking Node.js'
rm -rf /opt/node.new
mkdir -p /opt/node.new
tar -xzf "${'$'}node_file" -C /opt/node.new --strip-components=1
rm -rf /opt/node
mv /opt/node.new /opt/node
for tool in node npm npx; do ln -sf /opt/node/bin/${'$'}tool /usr/local/bin/${'$'}tool; done
npm config set prefix /usr/local
rm -f "${'$'}node_file"
oc_version "${'$'}(node --version | sed 's/^v//')"
"""
        }

    public const val OPENCODE_PRESENCE: String =
        "command -v opencode >/dev/null 2>&1 || command -v opencode2 >/dev/null 2>&1 || " +
            "[ -e /opt/opencode ] || [ -e /opt/opencode2 ] || " +
            "[ -e /usr/local/lib/node_modules/opencode-ai ] || [ -e /usr/local/lib/node_modules/opencode ]"

    private const val HEALTH_PATHS: String = "/api/info /api/health"
    private val versionPattern = Regex("^[A-Za-z0-9._+-]+$")

    /** The requested OpenCode version, or the pin; null for text that is not a version (it never reaches a script). */
    public fun selectedVersion(version: String?): String? {
        val selected = version ?: OpenCodePins.VERSION
        return selected.takeIf { versionPattern.matches(it) }
    }

    /** The Termux host's check: passes only for OpenCode 2 at the requested version (any install of it). */
    public fun openCodeCheck(version: String? = null): String? {
        val selected = selectedVersion(version) ?: return null
        return """set -e
installed=${'$'}(command -v opencode2 >/dev/null 2>&1 && opencode2 --version)
installed=${'$'}{installed##* v}
[ "${'$'}installed" = '$selected' ]
echo "${'$'}installed"
"""
    }

    /**
     * Passes only when `opencode2` is the pinned native program this app installed, at the requested version, so
     * Continue replaces OpenCode and nothing else. [root] prefixes every path (script tests only).
     */
    public fun openCodeNativeCheck(version: String? = null, root: String = ""): String? {
        val selected = selectedVersion(version) ?: return null
        val command = OpenCodePins.COMMAND
        val directory = "$root${OpenCodePins.DIR}"
        val program = "$directory/bin/$command"
        return """$COMPONENT_UPDATE_PRELUDE
set -e
oc_update_recover '$directory'
oc_update_recover '$root/usr/local/bin/$command'
oc_bin=${'$'}(command -v $command)
[ "${'$'}(readlink -f "${'$'}oc_bin")" = '$program' ]
installed=${'$'}("${'$'}oc_bin" --version | tail -n 1)
installed=${'$'}{installed##* v}
[ "${'$'}installed" = '$selected' ]
echo "${'$'}installed"
"""
    }

    /**
     * Installs the pinned native OpenCode 2 program: checksum-checked download, unpack only the program, prove it
     * (`--version`, then a short `serve` until health answers), then journal activation and link it. A failure ends
     * with one of [OpenCodeInstallFailure]'s lines; a version other than the pin refuses with
     * [OpenCodeInstallFailure.UNPINNED]. Null for a requested version that is not a version at all.
     */
    public fun openCodeNativeInstall(
        version: String? = null,
        pins: OpenCodeAssets = OpenCodePins.assets,
        root: String = "",
        probeSeconds: Int = 120,
    ): String? {
        val selected = selectedVersion(version) ?: return null
        if (selected != OpenCodePins.VERSION) {
            return "echo '${OpenCodeInstallFailure.UNPINNED} (${OpenCodePins.VERSION}, not $selected)' >&2\nexit 64\n"
        }
        val command = OpenCodePins.COMMAND
        val dir = "$root${OpenCodePins.DIR}"
        val modules = "$root/usr/local/lib/node_modules"
        val leftovers = "\"$root/opt/oc2\" \"$modules/@opencode-ai/cli\" \"$modules/@opencode-ai/\"cli-linux-*"
        return """set -eu
oc_fail() { echo "${'$'}*" >&2; exit 1; }
case "${'$'}(uname -m)" in
  aarch64|arm64) oc_url='${pins.arm64.url}'; oc_sha=${pins.arm64.sha256}; oc_member='${pins.arm64.member}' ;;
  x86_64|amd64) oc_url='${pins.x64.url}'; oc_sha=${pins.x64.sha256}; oc_member='${pins.x64.member}' ;;
  *) echo "${OpenCodeInstallFailure.CPU} (${'$'}(uname -m))" >&2; exit 64 ;;
esac
oc_dir='${dir}'
oc_new="${'$'}oc_dir.new"
oc_link='${root}/usr/local/bin/${command}'
oc_link_new="${'$'}oc_link.new"
oc_update_recover "${'$'}oc_dir" || exit 1
oc_update_recover "${'$'}oc_link" || exit 1
oc_file="${root}/var/cache/oc-setup/opencode-${selected}-${'$'}oc_sha.archive"
oc_stage 'Downloading OpenCode ${selected}'
# Checked against the pinned SHA-256 before anything is unpacked; a file
# that does not match is deleted and the run stops here.
oc_download "${'$'}oc_url" "${'$'}oc_file" "${'$'}oc_sha"
oc_stage 'Unpacking OpenCode'
rm -rf "${'$'}oc_new"
mkdir -p "${'$'}oc_new/bin"
if ! tar -xzf "${'$'}oc_file" -C "${'$'}oc_new" "${'$'}oc_member" ||
  [ ! -f "${'$'}oc_new/${'$'}oc_member" ] || [ -L "${'$'}oc_new/${'$'}oc_member" ]; then
  rm -rf "${'$'}oc_new" "${'$'}oc_file"
  oc_fail "${OpenCodeInstallFailure.NO_PROGRAM}"
fi
mv -f "${'$'}oc_new/${'$'}oc_member" "${'$'}oc_new/bin/${command}.tmp"
mv -f "${'$'}oc_new/bin/${command}.tmp" "${'$'}oc_new/bin/${command}"
case "${'$'}oc_member" in */*) rm -rf "${'$'}oc_new/${'$'}{oc_member%%/*}" ;; esac
chmod 755 "${'$'}oc_new/bin/${command}"
oc_program="${'$'}oc_new/bin/${command}"
if ! oc_out=${'$'}("${'$'}oc_program" --version 2>&1); then
  printf '%s\n' "${'$'}oc_out" | tail -n 20
  rm -rf "${'$'}oc_new"
  oc_fail "${OpenCodeInstallFailure.WONT_RUN}"
fi
oc_got=${'$'}(printf '%s\n' "${'$'}oc_out" | tail -n 1)
oc_got=${'$'}{oc_got##* v}
if [ "${'$'}oc_got" != '${selected}' ]; then
  printf '%s\n' "${'$'}oc_out" | tail -n 5
  rm -rf "${'$'}oc_new"
  oc_fail "${OpenCodeInstallFailure.WRONG_VERSION} (${'$'}oc_got, not ${selected})"
fi
oc_stage 'Checking that OpenCode starts'
oc_probe=${'$'}(mktemp -d "${'$'}{TMPDIR:-/tmp}/oc-probe.XXXXXX")
# A busy port is not a broken OpenCode: try up to three ports.
oc_started=
for oc_try in 1 2 3; do
oc_port=${'$'}((30000 + (${'$'}${'$'} + oc_try * 7919 + ${'$'}(od -An -N2 -tu2 /dev/urandom | tr -d ' ')) % 20000))
oc_pw=${'$'}(od -An -N12 -tx1 /dev/urandom | tr -d ' \n')
(
  cd "${'$'}oc_probe"
  unset OPENCODE_CONFIG OPENCODE_CONFIG_CONTENT OPENCODE_DB
  export HOME="${'$'}oc_probe" XDG_DATA_HOME="${'$'}oc_probe/data" XDG_CACHE_HOME="${'$'}oc_probe/cache"
  export XDG_STATE_HOME="${'$'}oc_probe/state" XDG_CONFIG_HOME="${'$'}oc_probe/config"
  export OPENCODE_CONFIG_DIR="${'$'}oc_probe/config/opencode" OPENCODE_DISABLE_MODELS_FETCH=1
  export OPENCODE_SERVER_USERNAME=opencode OPENCODE_SERVER_PASSWORD="${'$'}oc_pw"
  exec "${'$'}oc_program" serve --hostname 127.0.0.1 --port "${'$'}oc_port"
) > "${'$'}oc_probe/log" 2>&1 </dev/null &
oc_pid=${'$'}!
oc_alive() {
  kill -0 "${'$'}oc_pid" 2>/dev/null || return 1
  case "${'$'}(sed 's/^.*) //' "/proc/${'$'}oc_pid/stat" 2>/dev/null)" in Z*|X*) return 1 ;; esac
}
oc_started=
oc_i=0
while [ "${'$'}oc_i" -lt ${probeSeconds} ]; do
  for oc_path in $HEALTH_PATHS; do
    oc_code=${'$'}(curl -s --max-time 3 -o /dev/null -w '%{http_code}' \
      -u "opencode:${'$'}oc_pw" "http://127.0.0.1:${'$'}oc_port${'$'}oc_path" 2>/dev/null) || true
    # The probe supplies its own fresh credential. A 401 may be another
    # listener on this port, and cannot prove this candidate started.
    case "${'$'}oc_code" in 200) oc_alive && oc_started=1 ;; esac
  done
  [ -z "${'$'}oc_started" ] || break
  oc_alive || break
  sleep "${'$'}{OC_PROBE_POLL:-1}"
  oc_i=${'$'}((oc_i + 1))
done
kill "${'$'}oc_pid" 2>/dev/null || true
oc_i=0
while [ "${'$'}oc_i" -lt 5 ] && oc_alive; do sleep "${'$'}{OC_PROBE_POLL:-1}"; oc_i=${'$'}((oc_i + 1)); done
kill -9 "${'$'}oc_pid" 2>/dev/null || true
wait "${'$'}oc_pid" 2>/dev/null || true
[ -z "${'$'}oc_started" ] || break
grep -qiE 'address already in use|EADDRINUSE' "${'$'}oc_probe/log" 2>/dev/null || break
done
if [ -z "${'$'}oc_started" ]; then
  echo "[oc] What OpenCode said:"
  tail -n 20 "${'$'}oc_probe/log" | sed 's/^/  /'
  rm -rf "${'$'}oc_probe" "${'$'}oc_new"
  oc_fail "${OpenCodeInstallFailure.NO_START}"
fi
rm -rf "${'$'}oc_probe"
mkdir -p "${root}/usr/local/bin"
rm -f "${'$'}oc_link_new"
ln -s "${'$'}oc_dir/bin/${command}" "${'$'}oc_link_new"
oc_activation_failed() {
  oc_restore_failed=0
  oc_update_recover "${'$'}oc_dir" || oc_restore_failed=1
  oc_update_recover "${'$'}oc_link" || oc_restore_failed=1
  if [ "${'$'}oc_restore_failed" != 0 ]; then
    oc_fail '[oc] A component update could not be restored. Run setup again.'
  fi
  oc_fail '[oc] OpenCode could not finish updating. Run setup again.'
}
oc_update_activate "${'$'}oc_dir" "${'$'}oc_new" || oc_activation_failed
oc_update_activate "${'$'}oc_link" "${'$'}oc_link_new" || oc_activation_failed
if ! oc_active_version=${'$'}("${'$'}oc_link" --version 2>/dev/null); then
  oc_activation_failed
fi
oc_active_version=${'$'}(printf '%s\n' "${'$'}oc_active_version" | tail -n 1)
oc_active_version=${'$'}{oc_active_version##* v}
[ "${'$'}oc_active_version" = '${selected}' ] || oc_activation_failed
# Commit code before its command link; never delete the retained good backup.
oc_update_commit "${'$'}oc_dir" || oc_activation_failed
oc_update_commit "${'$'}oc_link" || oc_activation_failed
# What npm installed before, wrapper and all.
rm -rf ${leftovers}
rm -f "${'$'}oc_file"
""" + "oc_version \"${'$'}oc_got\"\n"
    }

    /** Pinned component activation (component_updates.dart), verbatim. */
    public const val COMPONENT_UPDATE_PRELUDE: String = """
oc_update_exists() { [ -e "${'$'}1" ] || [ -L "${'$'}1" ]; }
oc_update_failure() {
  echo '[oc] A component update could not be restored. Run setup again.' >&2
  return 1
}
oc_update_target_ok() {
  case "${'$'}1" in /*) [ "${'$'}1" != / ] ;; *) return 1 ;; esac
}
oc_update_recover() (
  oc_update_target_ok "${'$'}1" || { oc_update_failure; exit 1; }
  oc_target=${'$'}1
  oc_pending="${'$'}oc_target.oc-pending"
  oc_good="${'$'}oc_target.oc-good"
  oc_update_exists "${'$'}oc_pending" || exit 0
  [ -f "${'$'}oc_pending" ] && [ ! -L "${'$'}oc_pending" ] || { oc_update_failure; exit 1; }
  oc_state=${'$'}(cat "${'$'}oc_pending") || { oc_update_failure; exit 1; }
  case "${'$'}oc_state" in
    existing)
      if oc_update_exists "${'$'}oc_good"; then
        rm -rf -- "${'$'}oc_target" || { oc_update_failure; exit 1; }
        mv -T -- "${'$'}oc_good" "${'$'}oc_target" || { oc_update_failure; exit 1; }
      else
        # Either publication had not moved the old target yet, or a previous
        # recovery restored it and was interrupted before clearing the marker.
        oc_update_exists "${'$'}oc_target" || { oc_update_failure; exit 1; }
      fi ;;
    new)
      rm -rf -- "${'$'}oc_target" || { oc_update_failure; exit 1; } ;;
    *) oc_update_failure; exit 1 ;;
  esac
  sync -f "${'$'}(dirname "${'$'}oc_target")" 2>/dev/null || { oc_update_failure; exit 1; }
  rm -f -- "${'$'}oc_pending" || { oc_update_failure; exit 1; }
  # The restored target was flushed before this commit point. Repeating an
  # old marker after a power loss remains safe; power-loss qualification is
  # separate from the process-interruption proof.
  sync -f "${'$'}(dirname "${'$'}oc_target")" 2>/dev/null || true
)
oc_update_activate() (
  [ "${'$'}#" = 2 ] && oc_update_target_ok "${'$'}1" && oc_update_target_ok "${'$'}2" &&
    [ "${'$'}1" != "${'$'}2" ] || { oc_update_failure; exit 1; }
  oc_target=${'$'}1 oc_candidate=${'$'}2
  oc_pending="${'$'}oc_target.oc-pending"
  oc_good="${'$'}oc_target.oc-good"
  oc_update_recover "${'$'}oc_target" || exit 1
  oc_update_exists "${'$'}oc_candidate" || { oc_update_failure; exit 1; }
  # No journal is active here, so the current target is still the last good
  # install even if preparation stops while removing an older backup.
  rm -rf -- "${'$'}oc_good" || { oc_update_failure; exit 1; }
  oc_state=new
  oc_update_exists "${'$'}oc_target" && oc_state=existing
  (umask 077; printf '%s\n' "${'$'}oc_state" > "${'$'}oc_pending.new") || { oc_update_failure; exit 1; }
  sync -f "${'$'}oc_pending.new" 2>/dev/null || { oc_update_failure; exit 1; }
  mv -T -- "${'$'}oc_pending.new" "${'$'}oc_pending" || { oc_update_failure; exit 1; }
  sync -f "${'$'}(dirname "${'$'}oc_target")" 2>/dev/null || { oc_update_failure; exit 1; }
  if [ "${'$'}oc_state" = existing ]; then
    mv -T -- "${'$'}oc_target" "${'$'}oc_good" || { oc_update_failure; exit 1; }
  fi
  mv -T -- "${'$'}oc_candidate" "${'$'}oc_target" || { oc_update_failure; exit 1; }
)
oc_update_commit() (
  oc_update_target_ok "${'$'}1" || { oc_update_failure; exit 1; }
  oc_target=${'$'}1
  oc_pending="${'$'}oc_target.oc-pending"
  [ -f "${'$'}oc_pending" ] && [ ! -L "${'$'}oc_pending" ] &&
    oc_update_exists "${'$'}oc_target" || { oc_update_failure; exit 1; }
  # Caller has probed the active program, not merely the staging candidate.
  # Flush all target changes before deleting the recovery receipt.
  sync -f "${'$'}oc_target" 2>/dev/null &&
    sync -f "${'$'}(dirname "${'$'}oc_target")" 2>/dev/null || { oc_update_failure; exit 1; }
  rm -f -- "${'$'}oc_pending" || { oc_update_failure; exit 1; }
  sync -f "${'$'}(dirname "${'$'}oc_target")" 2>/dev/null || true
  # Keep one .oc-good generation. Multi-target callers commit code before
  # the launch link: a killed link commit can always return to usable code.
)
"""
}

/** One pinned download for one CPU: where it is, its SHA-256, and the program's path inside the archive. */
public data class OpenCodeAsset(val url: String, val sha256: String, val member: String)

public data class OpenCodeAssets(val arm64: OpenCodeAsset, val x64: OpenCodeAsset)

/** The pinned OpenCode 2 program (`@opencode/cli` 2.2.5 per-platform packages; SHA-256 of those tarballs). */
public object OpenCodePins {
    public const val VERSION: String = "2.2.5"
    public const val RUNTIME: String = "opencode2"
    public const val COMMAND: String = "opencode2"
    public const val DIR: String = "/opt/opencode2"

    public val assets: OpenCodeAssets =
        OpenCodeAssets(
            arm64 =
                OpenCodeAsset(
                    url = "https://registry.npmjs.org/@opencode/cli-linux-arm64/-/cli-linux-arm64-2.2.5.tgz",
                    sha256 = "f8c5a82075fede9b5b044ed6c8a6d13ad807a7e62caea1cd0a878adef49eb053",
                    member = "package/bin/opencode",
                ),
            x64 =
                OpenCodeAsset(
                    url = "https://registry.npmjs.org/@opencode/cli-linux-x64-baseline/-/cli-linux-x64-baseline-2.2.5.tgz",
                    sha256 = "873856acbaa71c5ed8df6433e312dfa0c82a86e2cddfb9d80f0a31123a917a08",
                    member = "package/bin/opencode",
                ),
        )
}

/** The pinned Node.js (termuxLocalAgentsPins in lib/termux/scripts/local_agents_script.dart). */
public object NodePins {
    public const val VERSION: String = "v24.21.0"
    public const val BASE_URL: String = "https://nodejs.org/dist"
    public const val SHA256_ARM64: String = "724282c3b43aec998aa9527380465b45d229e021b58035f5f4f63095eabfe5d5"
    public const val SHA256_X64: String = "6e1db87ef58b8819e5d5402eff1536491b18edd8eb7bee5ef7897876e88dc5ff"
}

/** Wraps an install body in the setup prelude (`withSetupPrelude`, bound by the wiring to :engine:setupscripts). */
public fun interface SetupScriptWrapper {
    public fun wrap(body: String): String
}
