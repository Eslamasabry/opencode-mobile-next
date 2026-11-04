package io.github.eslamasabry.oc.engine.agents.scripts

import io.github.eslamasabry.oc.engine.agents.catalog.AgentArchitecture
import io.github.eslamasabry.oc.engine.agents.catalog.AgentArtifact
import io.github.eslamasabry.oc.engine.agents.catalog.AgentArtifactFormat
import io.github.eslamasabry.oc.engine.agents.catalog.AgentAvailability
import io.github.eslamasabry.oc.engine.agents.catalog.AgentDescriptor
import io.github.eslamasabry.oc.engine.agents.catalog.AgentInstallRecipe
import io.github.eslamasabry.oc.engine.agents.catalog.AgentPins

private const val D = "$"

/**
 * Port of lib/builtin/agents/agent_scripts.dart (`AgentPhoneScripts`,
 * install/check/rollback/bootstrap; the auth probe is not part of this port), byte-faithful:
 * installs catalog pins as Linux user oc. Does not sign in, launch agents, or qualify phone
 * compatibility. Generation throws [IllegalArgumentException] for an unusable recipe.
 */
public object AgentPhoneScripts {
    private val VERSION = Regex("^[0-9][a-zA-Z0-9.+_-]*$")
    private val MEMBER = Regex("^(?:\\./)?[a-zA-Z0-9_./+-]+$")

    private fun recipe(agent: AgentDescriptor): AgentInstallRecipe {
        val recipe = agent.recipe
        require(
            recipe != null &&
                agent.availability != AgentAvailability.Hidden &&
                VERSION.matches(recipe.version) &&
                recipe.artifacts.values.none {
                    it.archiveMember != null && !MEMBER.matches(it.archiveMember)
                }
        ) {
            "This agent has no usable pinned installation."
        }
        return recipe
    }

    /** The Dart `_quote`: single quotes, each `'` as `'\''`. */
    internal fun quote(text: String): String = "'" + text.replace("'", "'\\''") + "'"

    /** Dart `RegExp.escape`. */
    private fun regexEscape(text: String): String = buildString {
        for (c in text) {
            if (c in "\\^$.|?*+()[]{}") append('\\')
            append(c)
        }
    }

    private val AgentArtifactFormat.wire: String
        get() =
            when (this) {
                AgentArtifactFormat.Executable -> "executable"
                AgentArtifactFormat.TarGz -> "tarGz"
                AgentArtifactFormat.TarBz2 -> "tarBz2"
                AgentArtifactFormat.Zip -> "zip"
                AgentArtifactFormat.NpmTarGz -> "npmTarGz"
            }

    private fun selection(agent: AgentDescriptor, recipe: AgentInstallRecipe): String {
        val branches =
            recipe.artifacts.entries.map { (arch, artifact) ->
                val patterns =
                    if (arch == AgentArchitecture.Arm64) "aarch64|arm64" else "x86_64|amd64"
                "$patterns) oc_arch=${arch.wire}; oc_url=${quote(artifact.url)}; oc_sha=${quote(artifact.sha256)}; " +
                    "oc_format=${artifact.format.wire}; oc_bytes=${artifact.downloadBytes ?: 0}; " +
                    "oc_member=${quote(artifact.archiveMember ?: recipe.executable)} ;;"
            }
        return """set -eu
[ "${D}(id -u)" = 1000 ] && [ "${D}HOME" = /home/oc ] || {
  echo '[oc] Agent setup needs its private Linux user' >&2; exit 1
}
umask 077
# Reject symlinked ancestors before installs, checks or rollback touch a tree.
for oc_parent in /home /home/oc /home/oc/.local /home/oc/.local/share /home/oc/.local/share/oc-agents /home/oc/.local/share/oc-agents/${agent.id} /home/oc/.local/bin; do
  [ ! -L "${D}oc_parent" ] || exit 1
  [ ! -e "${D}oc_parent" ] || [ -d "${D}oc_parent" ] || exit 1
done
export PATH=/home/oc/.local/bin:/usr/bin:/bin
case "${D}(uname -m)" in
${branches.joinToString("\n")}
*) echo '[oc] This agent is unavailable for this phone' >&2; exit 1 ;;
esac
oc_final=/home/oc/.local/share/oc-agents/${agent.id}/${recipe.version}
oc_bin=/home/oc/.local/bin/${recipe.executable}
oc_pin=${quote("${recipe.version}|")}"${D}oc_arch|${D}oc_sha"
"""
    }

    private fun versionPattern(version: String): String =
        quote("(^|[^0-9A-Za-z.])${regexEscape(version)}([^0-9A-Za-z.]|$D)")

    public fun check(agent: AgentDescriptor, recipeOverride: AgentInstallRecipe? = null): String {
        val recipe = recipeOverride ?: recipe(agent)
        val recovery =
            if (agent.id.raw == "claude" && recipe.executable == "claude") {
                """${SetupPrelude.COMPONENT_UPDATE_PRELUDE}
# Checks and installers hold the same lock across recovery and probes.
# BB owns stale-lock recovery; a successful mkdir proves only our admission.
oc_check_lock=/home/oc/.local/share/oc-agents/.lock-claude
mkdir "${D}oc_check_lock" 2>/dev/null || {
  echo '[oc] Another Claude install may still be running. Wait and try again.' >&2
  exit 1
}
trap 'rmdir "${D}oc_check_lock" >/dev/null 2>&1 || true' EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM
oc_update_recover "${D}oc_final"
oc_update_recover "${D}oc_bin"
"""
            } else {
                ""
            }
        return """${selection(agent, recipe)}
$recovery
oc_version_pattern=${versionPattern(recipe.version)}
$PROBE
[ -f "${D}oc_final/.oc-pin" ] && [ ! -L "${D}oc_final/.oc-pin" ]
[ "${D}(cat "${D}oc_final/.oc-pin")" = "${D}oc_pin" ]
[ "${D}(readlink "${D}oc_bin")" = "${D}oc_final/launch" ]
if [ "${D}oc_format" = executable ]; then
  [ "${D}(sha256sum "${D}oc_final/payload/${D}oc_member" | cut -d ' ' -f1)" = "${D}oc_sha" ]
fi
oc_probe_version "${D}oc_final"
printf '%s\n' ${quote(recipe.version)}
"""
    }

    /**
     * Previous app-authored Claude pin, retained solely for an explicit rollback (official 2.1.283
     * manifest).
     */
    public val previousClaudeRecipe: AgentInstallRecipe =
        AgentInstallRecipe(
            version = "2.1.283",
            executable = "claude",
            artifacts =
                linkedMapOf(
                    AgentArchitecture.Arm64 to
                        AgentArtifact(
                            url =
                                "https://downloads.claude.ai/claude-code-releases/2.1.283/linux-arm64/claude",
                            sha256 =
                                "346d294f0103d6fc0de11ac953579b5c62dfa90698a4cfc486b6f927c615e697",
                            format = AgentArtifactFormat.Executable,
                            downloadBytes = 240902136,
                        ),
                    AgentArchitecture.X64 to
                        AgentArtifact(
                            url =
                                "https://downloads.claude.ai/claude-code-releases/2.1.283/linux-x64/claude",
                            sha256 =
                                "1859583ce32920595c61ef868bee52e1b1594f7486db209935e01f1e5e804ae2",
                            format = AgentArtifactFormat.Executable,
                            downloadBytes = 241556664,
                        ),
                ),
            signInArgs = listOf("auth", "login", "--claudeai"),
        )

    private fun previousClaude(agent: AgentDescriptor, activate: Boolean): String {
        require(agent.id.raw == "claude" && agent.recipe?.executable == "claude") {
            "This agent has no previous pinned version."
        }
        val recipe = previousClaudeRecipe
        // The Dart source writes `printf '%s\n'` in a non-raw string here: a real newline inside
        // the quotes.
        return """${selection(agent, recipe)}
${SetupPrelude.COMPONENT_UPDATE_PRELUDE}
oc_check_lock=/home/oc/.local/share/oc-agents/.lock-claude
mkdir "${D}oc_check_lock" 2>/dev/null || exit 1
oc_link="${D}oc_bin.rollback.${D}${D}"
trap 'rm -f "${D}oc_link"; rmdir "${D}oc_check_lock" >/dev/null 2>&1 || true' EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM
oc_update_recover "${D}oc_final"
oc_update_recover "${D}oc_bin"
[ -f "${D}oc_final/.oc-pin" ] && [ ! -L "${D}oc_final/.oc-pin" ]
[ "${D}(cat "${D}oc_final/.oc-pin")" = "${D}oc_pin" ]
[ -f "${D}oc_final/payload/claude" ] && [ ! -L "${D}oc_final/payload/claude" ]
[ "${D}(sha256sum "${D}oc_final/payload/claude" | cut -d ' ' -f1)" = "${D}oc_sha" ]
oc_version_pattern=${versionPattern(recipe.version)}
$PROBE
oc_probe_version "${D}oc_final"
${if (activate) ROLLBACK_ACTIVATE else ""}
printf '%s
' ${quote(recipe.version)}
"""
    }

    public val previousClaudeVersion: String
        get() = previousClaudeRecipe.version

    /** Prints a version only after checking its retained on-disk official pin. */
    public fun previousVersion(agent: AgentDescriptor): String =
        previousClaude(agent, activate = false)

    /**
     * No download and no account/data changes. The host restarts its daemon and checks Ready again.
     */
    public fun usePreviousVersion(agent: AgentDescriptor, version: String): String {
        require(version == previousClaudeRecipe.version) {
            "This previous version is not a known pin."
        }
        return previousClaude(agent, activate = true)
    }

    /**
     * A small `/usr/local/bin` launcher that runs the agent as `oc` in the Terminal's folder; best
     * effort.
     */
    public fun terminalLauncher(executable: String): String =
        TERMINAL_LAUNCHER.replace("@EXE@", executable)

    public fun install(agent: AgentDescriptor): String {
        val recipe = recipe(agent)
        val version = quote(recipe.version)
        val claudeUpdate = agent.id.raw == "claude" && recipe.executable == "claude"
        val updatePrelude =
            if (claudeUpdate) "${SetupPrelude.COMPONENT_UPDATE_PRELUDE}\n$CLAUDE_UPDATE_PROBE"
            else ""
        val recoverAfterLock = if (claudeUpdate) RECOVER_AFTER_LOCK else ""
        val fastLinkUpdate = if (claudeUpdate) CLAUDE_FAST_LINK else FAST_LINK
        val activation = if (claudeUpdate) CLAUDE_ACTIVATION else ACTIVATION
        return """${selection(agent, recipe)}
oc_version_pattern=${versionPattern(recipe.version)}
$PROBE
$updatePrelude
command -v python3 >/dev/null && command -v timeout >/dev/null || {
  echo '[oc] Agent setup needs the Linux tools first' >&2; exit 1
}
if [ "${D}oc_format" = npmTarGz ]; then
  [ "${D}(/home/oc/.local/node/bin/node --version 2>/dev/null)" = '${AgentPins.builtIn.node.version}' ] || {
    echo '[oc] Agent setup needs its pinned Node.js first' >&2; exit 1
  }
fi
mkdir -p /home/oc/.local/bin /home/oc/.local/share/oc-agents/${agent.id}
oc_lock=/home/oc/.local/share/oc-agents/.lock-${recipe.executable}
mkdir "${D}oc_lock" 2>/dev/null || { echo '[oc] Another agent install is in progress' >&2; exit 1; }
oc_stage="${D}oc_final.new"
oc_old="${D}oc_final.old.${D}${D}"
oc_link="${D}oc_bin.new.${D}${D}"
oc_probe=
trap '[ -z "${D}oc_probe" ] || rm -rf "${D}oc_probe"; rm -rf "${D}oc_stage"; rm -f "${D}oc_link"; rmdir "${D}oc_lock"' EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM
# Keep verified-pin downloads outside the disposable extraction tree.
oc_download_dir=/home/oc/.local/share/oc-agents/${agent.id}/.downloads
[ ! -L "${D}oc_download_dir" ] || exit 1
oc_artifact="${D}oc_download_dir/${recipe.version}-${D}oc_arch-${D}oc_sha"
mkdir -p "${D}oc_download_dir"
[ ! -L "${D}oc_artifact" ] || exit 1
$recoverAfterLock
[ ! -L "${D}oc_final" ] && [ ! -L "${D}oc_stage" ] || exit 1
if [ -f "${D}oc_final/.oc-pin" ] && [ ! -L "${D}oc_final/.oc-pin" ] &&
   [ "${D}(cat "${D}oc_final/.oc-pin")" = "${D}oc_pin" ] &&
   { [ "${D}oc_format" != executable ] || [ "${D}(sha256sum "${D}oc_final/payload/${D}oc_member" | cut -d ' ' -f1)" = "${D}oc_sha" ]; } &&
   oc_probe_version "${D}oc_final"; then
$fastLinkUpdate
  ${terminalLauncher(recipe.executable)}
  oc_version $version
  exit 0
fi
[ ! -L "${D}oc_final" ] && [ ! -L "${D}oc_stage" ] || exit 1
rm -rf "${D}oc_stage"
mkdir -p "${D}oc_stage/payload"
oc_stage 'Downloading agent'
oc_download "${D}oc_url" "${D}oc_artifact" "${D}oc_sha" "${D}oc_bytes"
# Independently enforce the pin even if the shared downloader changes.
[ "${D}(sha256sum "${D}oc_artifact" | cut -d ' ' -f1)" = "${D}oc_sha" ] || {
  echo '[oc] The agent download did not match its checksum' >&2; exit 1
}
if ! python3 - "${D}oc_artifact" "${D}oc_stage/payload" "${D}oc_format" "${D}oc_member" <<'OC_ARCHIVE'
$EXTRACT_PY
OC_ARCHIVE
then
  echo '[oc] The agent archive could not be safely prepared' >&2; exit 1
fi
if [ "${D}oc_format" != npmTarGz ]; then chmod 700 "${D}oc_stage/payload/${D}oc_member"; fi
if ! oc_probe_version "${D}oc_stage"; then
  echo '[oc] The agent did not pass its version check' >&2; exit 1
fi
printf '%s' "${D}oc_pin" > "${D}oc_stage/.oc-pin"
if [ "${D}oc_format" = npmTarGz ]; then
  printf '#!/bin/sh\nexec /home/oc/.local/node/bin/node "%s/payload/%s" "${D}@"\n' "${D}oc_final" "${D}oc_member" > "${D}oc_stage/launch"
else
  printf '#!/bin/sh\nexec "%s/payload/%s" "${D}@"\n' "${D}oc_final" "${D}oc_member" > "${D}oc_stage/launch"
fi
chmod 700 "${D}oc_stage/launch"
$activation
rm -f "${D}oc_artifact"
${terminalLauncher(recipe.executable)}
oc_version $version
"""
    }

    public val bootstrapUser: String =
        """set -eu
[ "${'$'}(id -u)" = 0 ] || exit 1
if getent group oc >/dev/null; then
  [ "${'$'}(getent group oc | cut -d: -f3)" = 1000 ] || exit 1
else
  ! getent group 1000 >/dev/null || exit 1
  groupadd --gid 1000 oc
fi
if id oc >/dev/null 2>&1; then
  [ "${'$'}(id -u oc)" = 1000 ] && [ "${'$'}(id -g oc)" = 1000 ] || exit 1
  [ "${'$'}(getent passwd oc | cut -d: -f6)" = /home/oc ] || exit 1
else
  ! getent passwd 1000 >/dev/null || exit 1
  useradd --uid 1000 --gid 1000 --home-dir /home/oc --create-home --shell /bin/bash oc
fi
[ ! -L /home/oc ] && [ -d /home/oc ] || exit 1
# Root PRoot maps Android-owned files to UID 0; chown is virtual here.
# The native agent launch maps them to UID 1000 and the phone check proves RW.
for oc_path in /home/oc/.local /home/oc/.local/bin /home/oc/.local/share; do
  [ ! -L "${'$'}oc_path" ] || exit 1
done
install -d -o 1000 -g 1000 /home/oc/.local /home/oc/.local/bin /home/oc/.local/share
"""

    internal val PROBE: String =
        """oc_probe_version() {
  oc_tree=${'$'}1
  [ -d "${'$'}oc_tree" ] && [ ! -L "${'$'}oc_tree" ] &&
    [ -z "${'$'}(find "${'$'}oc_tree" -type l -print -quit)" ] || return 1
  oc_probe=${'$'}(mktemp -d /home/oc/.local/share/oc-agents/.probe.XXXXXX)
  if [ "${'$'}oc_format" = npmTarGz ]; then
    oc_output=${'$'}(cd "${'$'}oc_probe" && timeout 30s env -i HOME="${'$'}oc_probe" XDG_CONFIG_HOME="${'$'}oc_probe" PATH="${'$'}PATH" \
      /home/oc/.local/node/bin/node "${'$'}oc_tree/payload/${'$'}oc_member" --version 2>/dev/null) || oc_output=
  else
    oc_output=${'$'}(cd "${'$'}oc_probe" && timeout 30s env -i HOME="${'$'}oc_probe" XDG_CONFIG_HOME="${'$'}oc_probe" PATH="${'$'}PATH" \
      "${'$'}oc_tree/payload/${'$'}oc_member" --version 2>/dev/null) || oc_output=
  fi
  rm -rf "${'$'}oc_probe"
  printf '%s' "${'$'}oc_output" | grep -Eq "${'$'}oc_version_pattern"
}
"""

    internal val CLAUDE_UPDATE_PROBE: String =
        """oc_claude_probe_active() {
  oc_probe=${'$'}(mktemp -d /home/oc/.local/share/oc-agents/.probe.XXXXXX)
  oc_output=${'$'}(cd "${'$'}oc_probe" && timeout 30s env -i HOME="${'$'}oc_probe" XDG_CONFIG_HOME="${'$'}oc_probe" PATH="${'$'}PATH" \
    "${'$'}oc_bin" --version 2>/dev/null) || oc_output=
  rm -rf "${'$'}oc_probe"
  printf '%s' "${'$'}oc_output" | grep -Eq "${'$'}oc_version_pattern"
}
oc_claude_update_failed() {
  oc_restore_failed=0
  oc_update_recover "${'$'}oc_final" || oc_restore_failed=1
  oc_update_recover "${'$'}oc_bin" || oc_restore_failed=1
  if [ "${'$'}oc_restore_failed" != 0 ]; then
    echo '[oc] A component update could not be restored. Run setup again.' >&2
  else
    echo '[oc] Claude could not finish updating. Run setup again.' >&2
  fi
  exit 1
}
"""

    internal val TERMINAL_LAUNCHER: String =
        """oc_terminal_launcher=/usr/local/bin/@EXE@
if [ ! -e "${'$'}oc_terminal_launcher" ] || grep -q '^# oc-terminal-launcher${'$'}' "${'$'}oc_terminal_launcher" 2>/dev/null; then
  {
    mkdir -p /usr/local/bin &&
    cat > "${'$'}oc_terminal_launcher.new" <<'OC_LAUNCHER' &&
#!/bin/sh
# oc-terminal-launcher
if [ "${'$'}(id -un)" = oc ]; then exec /home/oc/.local/bin/@EXE@ "${'$'}@"; fi
exec su -l oc -s /bin/sh -c 'cd "${'$'}0" 2>/dev/null || cd; t=${'$'}1; shift; exec env TERM="${'$'}t" /home/oc/.local/bin/@EXE@ "${'$'}@"' "${'$'}PWD" "${'$'}{TERM:-xterm-256color}" "${'$'}@"
OC_LAUNCHER
    chmod 755 "${'$'}oc_terminal_launcher.new" &&
    mv -f "${'$'}oc_terminal_launcher.new" "${'$'}oc_terminal_launcher"
  } 2>/dev/null || rm -f "${'$'}oc_terminal_launcher.new"
fi
"""

    internal val EXTRACT_PY: String =
        """import os, pathlib, shutil, stat, sys, tarfile, zipfile
archive, root, kind, entry = sys.argv[1:]
def safe(name):
    if not name or len(name) > 4096 or '\\' in name or any(ord(c) < 32 for c in name):
        raise ValueError()
    if name.startswith('/') or '..' in name.split('/'):
        raise ValueError()
    value = pathlib.PurePosixPath(name)
    if str(value) == '.':
        return None
    return value
try:
    if kind == 'executable':
        target = pathlib.Path(root) / safe(entry)
        with open(archive, 'rb') as source, open(target, 'xb') as dest:
            shutil.copyfileobj(source, dest)
    else:
        if kind == 'zip':
            handle = zipfile.ZipFile(archive)
            rows = [(m.filename, m.is_dir(), m.file_size, (m.external_attr >> 16) & 0xffff, m) for m in handle.infolist()]
            opener = handle.open
        else:
            handle = tarfile.open(archive, mode='r:gz' if kind in ('tarGz', 'npmTarGz') else 'r:bz2')
            rows = [(m.name, m.isdir(), m.size, m.mode, m) for m in handle.getmembers()]
            if any(not (m.isdir() or m.isfile()) for m in handle.getmembers()):
                raise ValueError()
            opener = handle.extractfile
        if len(rows) > 20000 or sum(row[2] for row in rows) > 2147483648:
            raise ValueError()
        names = set()
        for name, directory, size, mode, member in rows:
            path = safe(name)
            if path is None:
                if not directory: raise ValueError()
                continue
            if path in names or size > 1073741824:
                raise ValueError()
            names.add(path)
            if kind == 'zip' and stat.S_IFMT(mode) not in (0, stat.S_IFREG, stat.S_IFDIR):
                raise ValueError()
        for name, directory, size, mode, member in rows:
            path = safe(name)
            if path is None: continue
            target = pathlib.Path(root) / path
            if directory:
                target.mkdir(parents=True, exist_ok=True)
            else:
                target.parent.mkdir(parents=True, exist_ok=True)
                with opener(member) as source, open(target, 'xb') as dest:
                    shutil.copyfileobj(source, dest)
                os.chmod(target, 0o700 if mode & 0o111 else 0o600)
        handle.close()
    candidate = pathlib.Path(root) / safe(entry)
    if not candidate.is_file() or candidate.is_symlink(): raise ValueError()
except Exception:
    sys.exit(1)
"""

    private val ROLLBACK_ACTIVATE: String =
        """# Rebuild the launcher from authored paths; never trust an editable old wrapper.
printf '#!/bin/sh\nexec "%s/payload/claude" "${'$'}@"\n' "${'$'}oc_final" > "${'$'}oc_final/launch.rollback"
chmod 700 "${'$'}oc_final/launch.rollback"
mv -f "${'$'}oc_final/launch.rollback" "${'$'}oc_final/launch"
ln -s "${'$'}oc_final/launch" "${'$'}oc_link"
oc_update_activate "${'$'}oc_bin" "${'$'}oc_link" || exit 1
oc_update_commit "${'$'}oc_bin" || { oc_update_recover "${'$'}oc_bin"; exit 1; }
"""

    private val RECOVER_AFTER_LOCK: String =
        """oc_update_recover "${'$'}oc_final"
oc_update_recover "${'$'}oc_bin"
"""

    private val CLAUDE_FAST_LINK: String =
        """  ln -s "${'$'}oc_final/launch" "${'$'}oc_link" || oc_claude_update_failed
  oc_update_activate "${'$'}oc_bin" "${'$'}oc_link" || oc_claude_update_failed
  oc_claude_probe_active || oc_claude_update_failed
  oc_update_commit "${'$'}oc_bin" || oc_claude_update_failed
"""

    private val FAST_LINK: String =
        """  ln -s "${'$'}oc_final/launch" "${'$'}oc_link"
  mv -Tf "${'$'}oc_link" "${'$'}oc_bin"
"""

    private val CLAUDE_ACTIVATION: String =
        """ln -s "${'$'}oc_final/launch" "${'$'}oc_link" || oc_claude_update_failed
oc_update_activate "${'$'}oc_final" "${'$'}oc_stage" || oc_claude_update_failed
oc_update_activate "${'$'}oc_bin" "${'$'}oc_link" || oc_claude_update_failed
oc_claude_probe_active || oc_claude_update_failed
oc_update_commit "${'$'}oc_final" || oc_claude_update_failed
oc_update_commit "${'$'}oc_bin" || oc_claude_update_failed
"""

    private val ACTIVATION: String =
        """if [ -e "${'$'}oc_final" ]; then mv "${'$'}oc_final" "${'$'}oc_old"; fi
if ! mv "${'$'}oc_stage" "${'$'}oc_final"; then
  [ ! -d "${'$'}oc_old" ] || mv "${'$'}oc_old" "${'$'}oc_final"
  exit 1
fi
if ! ln -s "${'$'}oc_final/launch" "${'$'}oc_link" || ! mv -Tf "${'$'}oc_link" "${'$'}oc_bin"; then
  rm -rf "${'$'}oc_final"
  [ ! -d "${'$'}oc_old" ] || mv "${'$'}oc_old" "${'$'}oc_final"
  exit 1
fi
rm -rf "${'$'}oc_old"
"""
}
