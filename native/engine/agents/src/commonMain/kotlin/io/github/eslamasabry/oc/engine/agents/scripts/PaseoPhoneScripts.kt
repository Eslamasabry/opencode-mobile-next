package io.github.eslamasabry.oc.engine.agents.scripts

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.io.encoding.Base64

private const val D = "$"

/**
 * The two byte transforms the Paseo install needs that common Kotlin lacks; the app binds them to the platform
 * (`java.security.MessageDigest` and `java.util.zip.GZIPOutputStream` on Android and desktop).
 */
public interface PaseoLockCodec {
    public fun sha256Hex(bytes: ByteArray): String

    public fun gzip(bytes: ByteArray): ByteArray
}

/** The shipped package lock does not match its pin or holds an unpinned package; no script is built. */
public class PaseoPinUnavailable : IllegalArgumentException("The agent host install pin is unavailable")

/**
 * Port of lib/builtin/agents/paseo_scripts.dart: pinned dependencies for the phone's private agent host. The scripts
 * fail before writing when executed as root and never configure provider accounts or launch a daemon. Text is
 * byte-faithful except the gzip bytes of the embedded lock, which come from [PaseoLockCodec].
 */
public object PaseoPhoneScripts {
    public const val VERSION: String = "0.17.3"
    public const val NODE_VERSION: String = "v24.22.3"
    public const val NODE_ARM64_SHA256: String = "261af2af250dfd7e9f25cf151d1b2713a0adf8f10a9ad116c4947f68627affe8"
    public const val NODE_X64_SHA256: String = "061ec3e3062115705b56b51fa9cc330ab50d34b6969387d4e4b1a50c6809acc2"
    public const val PACKAGE_LOCK_ASSET: String = "assets/agents/paseo-package-lock.json"
    public const val PACKAGE_LOCK_SHA256: String = "5fcd5dc3354c0b6ac915876597704e716eb5b9c32ba7ba3540fa6ef8821ca8cc"
    public const val INSTALL_DIRECTORY: String = "/home/oc/.local/share/oc-paseo/$VERSION-5fcd5dc3354c"

    // Registry artifact https://registry.npmjs.org/@getpaseo/cli/-/cli-0.17.3.tgz, checked against the lock's SRI.
    public const val CLI_RELATIVE_PATH: String = "node_modules/@getpaseo/cli/bin/paseo"
    public const val CLI_SHA256: String = "2b761f40e5a6416e4aaa733f38a47d5a4639b42cc1f88fc060256d2c4ed8cf94"
    public const val PAYLOAD_RELATIVE_PATH: String = "node_modules/@getpaseo/cli/dist/index.js"
    public const val PAYLOAD_SHA256: String = "8620d03c3e7e6776c3bfc4d22a8875f92209a563612fe7cda63dea7512f83c09"

    // SHA256 of the sorted sha256sum manifest for every regular bin/dist file, paths relative to the npm prefix.
    public const val CODE_TREE_SHA256: String = "dad765506bf0a72bffc5219157a8f89e994f177714eed1a5d5c96d51d039a669"

    private const val PACKAGE_JSON = """{
  "name": "oc-phone-agent-host",
  "version": "1.0.0",
  "private": true,
  "dependencies": {"@getpaseo/cli": "$VERSION"},
  "overrides": {
    "sherpa-onnx-linux-arm64": "1.12.28",
    "sherpa-onnx-linux-x64": "1.12.28",
    "sherpa-onnx-win-x64": "1.12.28",
    "sherpa-onnx-win-ia32": "1.12.28",
    "sherpa-onnx-darwin-x64": "1.12.28",
    "sherpa-onnx-darwin-arm64": "1.12.28"
  }
}
"""

    internal val REQUIRE_AGENT_USER: String = """[ "${D}(id -u)" = 1000 ] && [ "${D}HOME" = /home/oc ] || {
  echo '[oc] Agent setup needs its private Linux user' >&2
  exit 1
}
case "${D}(uname -m)" in
  aarch64|arm64|x86_64|amd64) ;;
  *) echo '[oc] Agent setup needs a 64-bit phone' >&2; exit 1 ;;
esac
umask 077
"""

    /**
     * If no `node` name exists (a kill between the two renames of the one-time switch from the old single folder),
     * point it at the newest versioned Node folder that runs. Every check and install starts with this, so a Node that
     * starts is back before anything else looks at it.
     */
    private val NODE_HEAL: String = """oc_node_heal() {
  [ ! -e /home/oc/.local/node ] && [ ! -L /home/oc/.local/node ] || return 0
  for oc_h in ${D}(ls -1A /home/oc/.local 2>/dev/null | grep -E '^node-v[0-9]+\.[0-9]+\.[0-9]+${D}' | sort -V -r); do
    [ -d "/home/oc/.local/${D}oc_h" ] && [ ! -L "/home/oc/.local/${D}oc_h" ] || continue
    "/home/oc/.local/${D}oc_h/bin/node" --version >/dev/null 2>&1 || continue
    rm -f "/home/oc/.local/node.heal.${D}${D}"
    ln -s "${D}oc_h" "/home/oc/.local/node.heal.${D}${D}" && mv -T "/home/oc/.local/node.heal.${D}${D}" /home/oc/.local/node || true
    return 0
  done
  return 0
}
"""

    /**
     * What may keep an old Node folder or an old helper tree. `oc_node_stock`: the folder holds only what Node's own
     * archive holds (`bin/{node,npm,npx,corepack}`, `lib/node_modules/{npm,corepack}`); anything else in it is
     * something a person installed with `npm install -g` into Node's own prefix, so it stays. `oc_no_old_node`: no
     * process runs on an older Node folder, which is how a helper started before the update shows itself; its tree
     * stays until it restarts.
     */
    private val NODE_GUARDS: String = """oc_node_stock() {
  for oc_s in ${D}(ls -1A "${D}1/bin" 2>/dev/null); do
    case "${D}oc_s" in node|npm|npx|corepack) ;; *) return 1 ;; esac
  done
  for oc_s in ${D}(ls -1A "${D}1/lib/node_modules" 2>/dev/null); do
    case "${D}oc_s" in npm|corepack) ;; *) return 1 ;; esac
  done
  return 0
}
oc_no_old_node() {
  oc_cur_node=${D}(readlink -f /home/oc/.local/node 2>/dev/null) || return 1
  for oc_d in ${D}(ls -1A /home/oc/.local 2>/dev/null | grep -E '^node-v[0-9]+\.[0-9]+\.[0-9]+${D}'); do
    [ "/home/oc/.local/${D}oc_d" != "${D}oc_cur_node" ] || continue
    if oc_in_use "/home/oc/.local/${D}oc_d"; then return 1; fi
  done
  return 0
}
oc_paseo_prune() {
  # A helper started before the update still runs on the older Node and its older tree: nothing of it goes then.
  if oc_no_old_node; then
    oc_prune_versions /home/oc/.local/share/oc-paseo '${INSTALL_DIRECTORY.substringAfterLast('/')}' \
      '^[0-9]+\.[0-9]+\.[0-9]+-[0-9a-f]{12}${D}' /home/oc/.local/bin/paseo
    oc_prune_stale /home/oc/.local/share/oc-paseo '${INSTALL_DIRECTORY.substringAfterLast('/')}' \
      '[0-9]+\.[0-9]+\.[0-9]+-[0-9a-f]{12}' /home/oc/.local/bin/paseo
  fi
  oc_prune_one ${INSTALL_DIRECTORY}.oc-good
}
oc_node_prune() {
  oc_prune_versions /home/oc/.local 'node-$NODE_VERSION' '^node-v[0-9]+\.[0-9]+\.[0-9]+${D}' \
    /home/oc/.local/bin/node oc_node_stock
  oc_prune_stale /home/oc/.local 'node-$NODE_VERSION' 'node-v[0-9]+\.[0-9]+\.[0-9]+' /home/oc/.local/bin/node
  oc_prune_node_parts /home/oc/.cache/oc-setup 'node-$NODE_VERSION-'
  oc_prune_one /home/oc/.local/node-$NODE_VERSION.old
  oc_prune_one /home/oc/.local/node.old
}
"""

    /**
     * The Node.js the agent host runs on, downloaded per ABI against its pin. Each version lives in its OWN folder
     * (`/home/oc/.local/node-<version>`) beside the old one and is proven there. `/home/oc/.local/node` (the name
     * every script and the helper use) is a link switched by one atomic rename: a kill at any point leaves a Node
     * that starts, old or new. A phone from before the folders were versioned has a real `node` folder: the first
     * switch renames it to its own versioned name (kept as the last good) and links, two renames healed by
     * [NODE_HEAL]. Old Node folders are removed only after the new one answered its version through the node name.
     */
    public val nodeInstall: String = """set -eu
$REQUIRE_AGENT_USER
case "${D}(uname -m)" in
  aarch64|arm64) node_arch=arm64; node_sha=$NODE_ARM64_SHA256 ;;
  x86_64|amd64) node_arch=x64; node_sha=$NODE_X64_SHA256 ;;
esac
node_dir=/home/oc/.local/node
node_home=/home/oc/.local/node-$NODE_VERSION
node_part=/home/oc/.cache/oc-setup/node-$NODE_VERSION-linux-${D}node_arch.tar.gz
mkdir -p /home/oc/.local/bin /home/oc/.cache/oc-setup
[ ! -L /home/oc/.local ] || exit 1
${ScriptPrune.FUNCTION}
$NODE_HEAL
$NODE_GUARDS
node_link_new="${D}node_dir.link.${D}${D}"
trap 'rm -rf "${D}node_home.new"; rm -f "${D}node_link_new"' EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM
oc_node_heal
# Points the node name at a versioned folder by one atomic rename (the old folder is untouched until this instant).
oc_node_point() {
  rm -f "${D}node_link_new"
  ln -s "${D}1" "${D}node_link_new"
  mv -T "${D}node_link_new" "${D}node_dir"
}
if ! { [ -d "${D}node_home" ] && [ ! -L "${D}node_home" ] &&
  [ "${D}("${D}node_home/bin/node" --version 2>/dev/null)" = '$NODE_VERSION' ]; }; then
  oc_stage 'Downloading Node.js $NODE_VERSION'
  oc_download "https://nodejs.org/dist/$NODE_VERSION/node-$NODE_VERSION-linux-${D}node_arch.tar.gz" \
    "${D}node_part" "${D}node_sha"
  oc_stage 'Preparing Node.js'
  rm -rf "${D}node_home.new"
  mkdir -p "${D}node_home.new"
  oc_untar "${D}node_part" "${D}node_home.new" 1
  # Proven here, beside the old Node, before anything is switched.
  [ "${D}("${D}node_home.new/bin/node" --version)" = '$NODE_VERSION' ]
  # A same-name folder that did not pass is set aside, never deleted before the new one is proven below.
  if [ -e "${D}node_home" ] || [ -L "${D}node_home" ]; then
    rm -rf "${D}node_home.old"
    mv -T "${D}node_home" "${D}node_home.old"
  fi
  mv -T "${D}node_home.new" "${D}node_home"
fi
node_before=
if [ -L "${D}node_dir" ]; then
  node_before=${D}(readlink "${D}node_dir")
elif [ -d "${D}node_dir" ]; then
  # From before the folders carried their version: this folder IS the old Node. It keeps its own versioned name.
  node_old=${D}("${D}node_dir/bin/node" --version 2>/dev/null || true)
  case "${D}node_old" in v[0-9]*.[0-9]*.[0-9]*) ;; *) node_old=v0.0.${D}${D} ;; esac
  [ ! -e "/home/oc/.local/node-${D}node_old" ] || node_old=v0.0.${D}${D}
  node_before="node-${D}node_old"
  mv -T "${D}node_dir" "/home/oc/.local/${D}node_before"
fi
oc_node_point "node-$NODE_VERSION"
if [ "${D}("${D}node_dir/bin/node" --version 2>/dev/null)" != '$NODE_VERSION' ]; then
  # Started through the name every script uses, or back to what was there.
  [ -z "${D}node_before" ] || oc_node_point "${D}node_before"
  exit 1
fi
for node_tool in node npm npx; do
  if [ "${D}(readlink "/home/oc/.local/bin/${D}node_tool" 2>/dev/null)" != "${D}node_dir/bin/${D}node_tool" ]; then
    ln -s "${D}node_dir/bin/${D}node_tool" "/home/oc/.local/bin/${D}node_tool.new.${D}${D}"
    mv -T "/home/oc/.local/bin/${D}node_tool.new.${D}${D}" "/home/oc/.local/bin/${D}node_tool"
  fi
done
# The new Node answered through the node name: only now do older Node folders go.
oc_node_prune
rm -f "${D}node_part"
oc_version '$NODE_VERSION'
"""

    public val nodeCheck: String = """set -eu
${ScriptPrune.FUNCTION}
$NODE_HEAL
$NODE_GUARDS
oc_node_heal
[ "${D}(/home/oc/.local/node/bin/node --version)" = '$NODE_VERSION' ]
# Old Node folders that an earlier install had to keep (something still ran on them) go here, once nothing does.
oc_node_prune
printf '%s\n' '$NODE_VERSION'
"""

    /**
     * Rejects an altered lock before constructing any script ([PaseoPinUnavailable]). Npm verifies the SRI digest of
     * every resolved tarball in this shipped lock; install scripts are disabled, including transitive package hooks.
     */
    public fun install(packageLock: String, codec: PaseoLockCodec): String {
        val bytes = packageLock.encodeToByteArray()
        if (codec.sha256Hex(bytes) != PACKAGE_LOCK_SHA256) throw PaseoPinUnavailable()
        val packages = (Json.parseToJsonElement(packageLock).jsonObject["packages"] as? JsonObject) ?: throw PaseoPinUnavailable()
        for ((key, value) in packages) {
            if (key.isEmpty()) continue
            val pkg = value as? JsonObject ?: throw PaseoPinUnavailable()
            val url = (pkg["resolved"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: ""
            val integrity = (pkg["integrity"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: ""
            if (!url.startsWith("https://registry.npmjs.org/") || !integrity.startsWith("sha512-")) throw PaseoPinUnavailable()
        }
        // Keep the command well below Linux's per-argument limit when the runner executes it with `/bin/sh -c`.
        val lockBase64 = Base64.encode(codec.gzip(bytes))
        val packageBase64 = Base64.encode(PACKAGE_JSON.encodeToByteArray())
        return """set -eu
${SetupPrelude.COMPONENT_UPDATE_PRELUDE}
${ScriptPrune.FUNCTION}
$NODE_GUARDS
$REQUIRE_AGENT_USER
export PATH=/home/oc/.local/node/bin:/home/oc/.local/bin:/usr/bin:/bin
[ "${D}(node --version)" = '$NODE_VERSION' ]
host_dir=$INSTALL_DIRECTORY
host_link=/home/oc/.local/bin/paseo
oc_update_recover "${D}host_dir" || exit 1
oc_update_recover "${D}host_link" || exit 1
host_new="${D}host_dir.new"
host_cache=/home/oc/.cache/oc-paseo-install
mkdir -p /home/oc/.local/bin "${D}(dirname "${D}host_dir")"
rm -rf "${D}host_new" "${D}host_cache"
mkdir -p "${D}host_new" "${D}host_cache"
trap 'rm -rf "${D}host_new" "${D}host_cache"' EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM
printf '%s' '$packageBase64' | base64 -d > "${D}host_new/package.json"
printf '%s' '$lockBase64' | base64 -d | gzip -d > "${D}host_new/package-lock.json"
: > "${D}host_new/npm-user.conf"
: > "${D}host_new/npm-global.conf"
[ "${D}(sha256sum "${D}host_new/package-lock.json" | cut -d ' ' -f 1)" = '$PACKAGE_LOCK_SHA256' ]
oc_stage 'Downloading Paseo $VERSION'
# Isolate npm from account configuration and inherited provider credentials.
# Details receive only a fixed failure reason, never npm's raw output.
if ! env -i HOME=/home/oc PATH="${D}PATH" \
  NODE_OPTIONS=--dns-result-order=ipv4first \
  NPM_CONFIG_USERCONFIG="${D}host_new/npm-user.conf" \
  NPM_CONFIG_GLOBALCONFIG="${D}host_new/npm-global.conf" \
  npm ci --prefix "${D}host_new" --cache "${D}host_cache" \
    --ignore-scripts --no-audit --no-fund --loglevel=error \
    --fetch-retries=5 --fetch-timeout=300000 \
    > "${D}host_cache/install.log" 2>&1; then
  echo '[oc] Paseo could not be installed. Check the connection and retry.' >&2
  exit 1
fi
oc_stage 'Checking Paseo checksum'
if [ "${D}(readlink "${D}host_new/node_modules/.bin/paseo")" != '../@getpaseo/cli/bin/paseo' ] ||
  [ ! -f "${D}host_new/$CLI_RELATIVE_PATH" ] ||
  [ -L "${D}host_new/$CLI_RELATIVE_PATH" ] ||
  [ "${D}(sha256sum "${D}host_new/$CLI_RELATIVE_PATH" | cut -d ' ' -f 1)" != '$CLI_SHA256' ] ||
  [ ! -f "${D}host_new/$PAYLOAD_RELATIVE_PATH" ] ||
  [ -L "${D}host_new/$PAYLOAD_RELATIVE_PATH" ] ||
  [ "${D}(sha256sum "${D}host_new/$PAYLOAD_RELATIVE_PATH" | cut -d ' ' -f 1)" != '$PAYLOAD_SHA256' ]; then
  echo '[oc] Paseo did not pass its checksum check. Run setup again.' >&2
  exit 1
fi
if [ -L "${D}host_new/node_modules/@getpaseo/cli/bin" ] ||
  [ -L "${D}host_new/node_modules/@getpaseo/cli/dist" ] ||
  [ -n "${D}(find "${D}host_new/node_modules/@getpaseo/cli/bin" \
    "${D}host_new/node_modules/@getpaseo/cli/dist" -type l -print 2>/dev/null)" ] ||
  [ "${D}(cd "${D}host_new" && \
    find node_modules/@getpaseo/cli/bin node_modules/@getpaseo/cli/dist -type f -print0 | \
    LC_ALL=C sort -z | xargs -0 sha256sum | sha256sum | cut -d ' ' -f 1)" != '$CODE_TREE_SHA256' ]; then
  echo '[oc] Paseo did not pass its checksum check. Run setup again.' >&2
  exit 1
fi
oc_stage 'Checking Paseo'
if ! (cd "${D}host_new" && node -e \
  'require("node-pty"); require("sherpa-onnx-node"); require("esbuild").transformSync("let a=1")') \
  > "${D}host_cache/check.log" 2>&1; then
  echo '[oc] Paseo cannot run on this phone yet.' >&2
  exit 1
fi
mkdir -p "${D}host_cache/probe-home"
oc_stage 'Checking Paseo launch command'
if ! env -i HOME="${D}host_cache/probe-home" PATH="${D}PATH" \
  timeout 30 "${D}host_new/node_modules/.bin/paseo" daemon run --help \
  > "${D}host_cache/launch-check.log" 2>&1 ||
  ! grep -q -- '--home' "${D}host_cache/launch-check.log"; then
  echo '[oc] Paseo did not pass its launch command check. Run setup again.' >&2
  exit 1
fi
oc_stage 'Checking Paseo version'
if ! host_version=${D}(env -i HOME="${D}host_cache/probe-home" PATH="${D}PATH" \
  timeout 30 "${D}host_new/node_modules/.bin/paseo" --version 2>/dev/null) ||
  [ "${D}host_version" != '$VERSION' ]; then
  echo '[oc] Paseo did not pass its version check. Run setup again.' >&2
  exit 1
fi
printf '%s' '$PACKAGE_LOCK_SHA256' > "${D}host_new/.oc-package-lock-sha256"
# Journal only after all candidate probes pass; keep one previous good tree.
oc_paseo_activation_failed() {
  oc_update_recover "${D}host_dir" || exit 1
  oc_update_recover "${D}host_link" || exit 1
  echo '[oc] Paseo could not finish updating. Run setup again.' >&2
  exit 1
}
rm -f "${D}host_link.new"
ln -s "${D}host_dir/node_modules/.bin/paseo" "${D}host_link.new"
oc_update_activate "${D}host_dir" "${D}host_new" || oc_paseo_activation_failed
oc_update_activate "${D}host_link" "${D}host_link.new" || oc_paseo_activation_failed
oc_stage 'Checking Paseo launch command'
if ! env -i HOME="${D}host_cache/probe-home" PATH="${D}PATH" \
  timeout 30 "${D}host_link" daemon run --help \
  > "${D}host_cache/launch-check.log" 2>&1 ||
  ! grep -q -- '--home' "${D}host_cache/launch-check.log"; then
  oc_paseo_activation_failed
fi
oc_stage 'Checking Paseo version'
if ! host_version=${D}(env -i HOME="${D}host_cache/probe-home" PATH="${D}PATH" \
  timeout 30 "${D}host_link" --version 2>/dev/null) ||
  [ "${D}host_version" != '$VERSION' ]; then
  oc_paseo_activation_failed
fi
oc_update_commit "${D}host_dir" || oc_paseo_activation_failed
oc_update_commit "${D}host_link" || oc_paseo_activation_failed
# The new helper answered its launch command and version through the link and is committed: older helper trees go.
oc_paseo_prune
oc_version '$VERSION'
"""
    }

    public val check: String = """set -eu
${SetupPrelude.COMPONENT_UPDATE_PRELUDE}
${ScriptPrune.FUNCTION}
$NODE_GUARDS
host_dir=$INSTALL_DIRECTORY
oc_update_recover "${D}host_dir"
oc_update_recover /home/oc/.local/bin/paseo
export PATH=/home/oc/.local/node/bin:/home/oc/.local/bin:/usr/bin:/bin
[ "${D}(/home/oc/.local/node/bin/node --version)" = '$NODE_VERSION' ]
[ "${D}(cat $INSTALL_DIRECTORY/.oc-package-lock-sha256)" = '$PACKAGE_LOCK_SHA256' ]
host_dir=$INSTALL_DIRECTORY
[ "${D}(readlink /home/oc/.local/bin/paseo)" = "${D}host_dir/node_modules/.bin/paseo" ]
[ "${D}(readlink "${D}host_dir/node_modules/.bin/paseo")" = '../@getpaseo/cli/bin/paseo' ]
[ -f "${D}host_dir/$CLI_RELATIVE_PATH" ] && [ ! -L "${D}host_dir/$CLI_RELATIVE_PATH" ]
[ "${D}(sha256sum "${D}host_dir/$CLI_RELATIVE_PATH" | cut -d ' ' -f 1)" = '$CLI_SHA256' ]
[ -f "${D}host_dir/$PAYLOAD_RELATIVE_PATH" ] && [ ! -L "${D}host_dir/$PAYLOAD_RELATIVE_PATH" ]
[ "${D}(sha256sum "${D}host_dir/$PAYLOAD_RELATIVE_PATH" | cut -d ' ' -f 1)" = '$PAYLOAD_SHA256' ]
[ ! -L "${D}host_dir/node_modules/@getpaseo/cli/bin" ]
[ ! -L "${D}host_dir/node_modules/@getpaseo/cli/dist" ]
[ -z "${D}(find "${D}host_dir/node_modules/@getpaseo/cli/bin" \
  "${D}host_dir/node_modules/@getpaseo/cli/dist" -type l -print 2>/dev/null)" ]
[ "${D}(cd "${D}host_dir" && \
  find node_modules/@getpaseo/cli/bin node_modules/@getpaseo/cli/dist -type f -print0 | \
  LC_ALL=C sort -z | xargs -0 sha256sum | sha256sum | cut -d ' ' -f 1)" = '$CODE_TREE_SHA256' ]
probe_dir=${D}(mktemp -d "${D}{TMPDIR:-/tmp}/oc-paseo-check.XXXXXX")
trap 'rm -rf "${D}probe_dir"' EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM
env -i HOME="${D}probe_dir" PATH="${D}PATH" timeout 30 /home/oc/.local/bin/paseo \
  daemon run --help > "${D}probe_dir/launch-check.log" 2>&1
grep -q -- '--home' "${D}probe_dir/launch-check.log"
[ "${D}(env -i HOME="${D}probe_dir" PATH="${D}PATH" timeout 30 \
  /home/oc/.local/bin/paseo --version 2>/dev/null)" = '$VERSION' ]
# Older helper trees that an earlier install had to keep (the old helper still ran) go here, once it does not.
oc_paseo_prune
printf '%s\n' '$VERSION'
"""
}
