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
    public const val VERSION: String = "0.11.2"
    public const val NODE_VERSION: String = "v24.21.0"
    public const val NODE_ARM64_SHA256: String = "724282c3b43aec998aa9527380465b45d229e021b58035f5f4f63095eabfe5d5"
    public const val NODE_X64_SHA256: String = "6e1db87ef58b8819e5d5402eff1536491b18edd8eb7bee5ef7897876e88dc5ff"
    public const val PACKAGE_LOCK_ASSET: String = "assets/agents/paseo-package-lock.json"
    public const val PACKAGE_LOCK_SHA256: String = "b0bcc4f6191bd5c2daeda8ae398d636f3575173e699fa3868ab9e24f2e1fcdaa"
    public const val INSTALL_DIRECTORY: String = "/home/oc/.local/share/oc-paseo/$VERSION-b0bcc4f6191b"

    // Registry artifact https://registry.npmjs.org/@getpaseo/cli/-/cli-0.11.2.tgz, checked against the lock's SRI.
    public const val CLI_RELATIVE_PATH: String = "node_modules/@getpaseo/cli/bin/paseo"
    public const val CLI_SHA256: String = "2b761f40e5a6416e4aaa733f38a47d5a4639b42cc1f88fc060256d2c4ed8cf94"
    public const val PAYLOAD_RELATIVE_PATH: String = "node_modules/@getpaseo/cli/dist/index.js"
    public const val PAYLOAD_SHA256: String = "8620d03c3e7e6776c3bfc4d22a8875f92209a563612fe7cda63dea7512f83c09"

    // SHA256 of the sorted sha256sum manifest for every regular bin/dist file, paths relative to the npm prefix.
    public const val CODE_TREE_SHA256: String = "aa0467db317397425785bafc5999e9b5287b0ef4e8d799718cf509cce82a7f04"

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

    /** The Node.js the agent host runs on, downloaded per ABI against its pin. */
    public val nodeInstall: String = """set -eu
$REQUIRE_AGENT_USER
case "${D}(uname -m)" in
  aarch64|arm64) node_arch=arm64; node_sha=$NODE_ARM64_SHA256 ;;
  x86_64|amd64) node_arch=x64; node_sha=$NODE_X64_SHA256 ;;
esac
node_dir=/home/oc/.local/node
node_part=/home/oc/.cache/oc-setup/node-$NODE_VERSION-linux-${D}node_arch.tar.gz
mkdir -p /home/oc/.local/bin /home/oc/.cache/oc-setup
trap 'rm -rf "${D}node_dir.new"' EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM
oc_stage 'Downloading Node.js $NODE_VERSION'
oc_download "https://nodejs.org/dist/$NODE_VERSION/node-$NODE_VERSION-linux-${D}node_arch.tar.gz" \
  "${D}node_part" "${D}node_sha"
oc_stage 'Preparing Node.js'
rm -rf "${D}node_dir.new"
mkdir -p "${D}node_dir.new"
tar -xzf "${D}node_part" -C "${D}node_dir.new" --strip-components=1
[ "${D}("${D}node_dir.new/bin/node" --version)" = '$NODE_VERSION' ]
rm -rf "${D}node_dir.old"
if [ -d "${D}node_dir" ]; then mv "${D}node_dir" "${D}node_dir.old"; fi
if ! mv "${D}node_dir.new" "${D}node_dir"; then
  [ ! -d "${D}node_dir.old" ] || mv "${D}node_dir.old" "${D}node_dir"
  exit 1
fi
for node_tool in node npm npx; do
  ln -sf "${D}node_dir/bin/${D}node_tool" "/home/oc/.local/bin/${D}node_tool"
done
rm -rf "${D}node_dir.old"
rm -f "${D}node_part"
oc_version '$NODE_VERSION'
"""

    public val nodeCheck: String = """set -eu
[ "${D}(/home/oc/.local/node/bin/node --version)" = '$NODE_VERSION' ]
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
oc_version '$VERSION'
"""
    }

    public val check: String = """set -eu
${SetupPrelude.COMPONENT_UPDATE_PRELUDE}
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
printf '%s\n' '$VERSION'
"""
}
