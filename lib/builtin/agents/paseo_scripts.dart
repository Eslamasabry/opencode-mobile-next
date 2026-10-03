import 'dart:convert';
import 'dart:io';

import 'package:crypto/crypto.dart';

/// Pinned dependencies for the phone's private agent host.
///
/// Install components must opt into the native setup runner's `agentUser`
/// execution mode. These scripts fail before writing when executed as root.
/// They never configure provider accounts or launch a daemon.
abstract final class PaseoPhoneScripts {
  static const version = '0.9.2';
  static const nodeVersion = 'v24.21.0';
  static const nodeArm64Sha256 =
      '724282c3b43aec998aa9527380465b45d229e021b58035f5f4f63095eabfe5d5';
  static const nodeX64Sha256 =
      '6e1db87ef58b8819e5d5402eff1536491b18edd8eb7bee5ef7897876e88dc5ff';
  static const packageLockAsset = 'assets/agents/paseo-package-lock.json';
  static const packageLockSha256 =
      '82d16f9c432dcaacaf881045a7f4da83806775bdfdfe409efd4cf21377fe1fea';
  static const installDirectory =
      '/home/oc/.local/share/oc-paseo/$version-82d16f9c432d';

  static const _packageJson =
      '''{
  "name": "oc-phone-agent-host",
  "version": "1.0.0",
  "private": true,
  "dependencies": {"@getpaseo/cli": "$version"},
  "overrides": {
    "sherpa-onnx-linux-arm64": "1.12.28",
    "sherpa-onnx-linux-x64": "1.12.28",
    "sherpa-onnx-win-x64": "1.12.28",
    "sherpa-onnx-win-ia32": "1.12.28",
    "sherpa-onnx-darwin-x64": "1.12.28",
    "sherpa-onnx-darwin-arm64": "1.12.28"
  }
}
''';

  static const _requireAgentUser = r'''
[ "$(id -u)" = 1000 ] && [ "$HOME" = /home/oc ] || {
  echo '[oc] Agent setup needs its private Linux user' >&2
  exit 1
}
case "$(uname -m)" in
  aarch64|arm64|x86_64|amd64) ;;
  *) echo '[oc] Agent setup needs a 64-bit phone' >&2; exit 1 ;;
esac
umask 077
''';

  /// No Node is installed by the agent catalog itself. The Paseo component is
  /// the dependency that selects this component when Node is needed.
  static const nodeInstall =
      '''
set -eu
$_requireAgentUser
case "\$(uname -m)" in
  aarch64|arm64) node_arch=arm64; node_sha=$nodeArm64Sha256 ;;
  x86_64|amd64) node_arch=x64; node_sha=$nodeX64Sha256 ;;
esac
node_dir=/home/oc/.local/node
node_part=/home/oc/.cache/oc-setup/node-$nodeVersion-linux-\$node_arch.tar.gz
mkdir -p /home/oc/.local/bin /home/oc/.cache/oc-setup
trap 'rm -rf "\$node_dir.new"' EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM
oc_stage 'Downloading Node.js $nodeVersion'
oc_download "https://nodejs.org/dist/$nodeVersion/node-$nodeVersion-linux-\$node_arch.tar.gz" \\
  "\$node_part" "\$node_sha"
oc_stage 'Preparing Node.js'
rm -rf "\$node_dir.new"
mkdir -p "\$node_dir.new"
tar -xzf "\$node_part" -C "\$node_dir.new" --strip-components=1
[ "\$("\$node_dir.new/bin/node" --version)" = '$nodeVersion' ]
rm -rf "\$node_dir.old"
if [ -d "\$node_dir" ]; then mv "\$node_dir" "\$node_dir.old"; fi
if ! mv "\$node_dir.new" "\$node_dir"; then
  [ ! -d "\$node_dir.old" ] || mv "\$node_dir.old" "\$node_dir"
  exit 1
fi
for node_tool in node npm npx; do
  ln -sf "\$node_dir/bin/\$node_tool" "/home/oc/.local/bin/\$node_tool"
done
rm -rf "\$node_dir.old"
rm -f "\$node_part"
oc_version '$nodeVersion'
''';

  static const nodeCheck =
      '''set -eu
[ "\$(/home/oc/.local/node/bin/node --version)" = '$nodeVersion' ]
printf '%s\\n' '$nodeVersion'
''';

  /// Reject an altered asset before constructing any script. Npm verifies
  /// the SRI digest of every resolved tarball in this shipped lock; install
  /// scripts are disabled, including transitive package hooks.
  static String install({required String packageLock}) {
    if (sha256.convert(utf8.encode(packageLock)).toString() !=
        packageLockSha256) {
      throw const FormatException('The agent host install pin is unavailable');
    }
    final lock = jsonDecode(packageLock) as Map<String, dynamic>;
    final packages = lock['packages'] as Map<String, dynamic>;
    for (final entry in packages.entries) {
      if (entry.key.isEmpty) continue;
      final package = entry.value as Map<String, dynamic>;
      final url = Uri.tryParse(package['resolved'] as String? ?? '');
      final integrity = package['integrity'] as String? ?? '';
      if (url == null ||
          url.scheme != 'https' ||
          url.host != 'registry.npmjs.org' ||
          url.userInfo.isNotEmpty ||
          !integrity.startsWith('sha512-')) {
        throw const FormatException(
          'The agent host install pin is unavailable',
        );
      }
    }
    // Keep the command well below Linux's per-argument limit when the durable
    // runner executes it with `/bin/sh -c`; only decoded JSON is trusted.
    final lockBase64 = base64.encode(gzip.encode(utf8.encode(packageLock)));
    final packageBase64 = base64.encode(utf8.encode(_packageJson));
    return '''
set -eu
$_requireAgentUser
export PATH=/home/oc/.local/node/bin:/home/oc/.local/bin:/usr/bin:/bin
[ "\$(node --version)" = '$nodeVersion' ]
host_dir=$installDirectory
host_new="\$host_dir.new"
host_cache=/home/oc/.cache/oc-paseo-install
mkdir -p /home/oc/.local/bin "\$(dirname "\$host_dir")"
rm -rf "\$host_new" "\$host_cache"
mkdir -p "\$host_new" "\$host_cache"
trap 'rm -rf "\$host_new" "\$host_cache"' EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM
printf '%s' '$packageBase64' | base64 -d > "\$host_new/package.json"
printf '%s' '$lockBase64' | base64 -d | gzip -d > "\$host_new/package-lock.json"
: > "\$host_new/npm-user.conf"
: > "\$host_new/npm-global.conf"
[ "\$(sha256sum "\$host_new/package-lock.json" | cut -d ' ' -f 1)" = '$packageLockSha256' ]
oc_stage 'Downloading Paseo $version'
# Isolate npm from account configuration and inherited provider credentials.
# Details receive only a fixed failure reason, never npm's raw output.
if ! env -i HOME=/home/oc PATH="\$PATH" \\
  NODE_OPTIONS=--dns-result-order=ipv4first \\
  NPM_CONFIG_USERCONFIG="\$host_new/npm-user.conf" \\
  NPM_CONFIG_GLOBALCONFIG="\$host_new/npm-global.conf" \\
  npm ci --prefix "\$host_new" --cache "\$host_cache" \\
    --ignore-scripts --no-audit --no-fund --loglevel=error \\
    --fetch-retries=5 --fetch-timeout=300000 \\
    > "\$host_cache/install.log" 2>&1; then
  echo '[oc] Paseo could not be installed. Check the connection and retry.' >&2
  exit 1
fi
oc_stage 'Checking Paseo'
if ! (cd "\$host_new" && node -e \\
  'require("node-pty"); require("sherpa-onnx-node"); require("esbuild").transformSync("let a=1")') \\
  > "\$host_cache/check.log" 2>&1; then
  echo '[oc] Paseo cannot run on this phone yet.' >&2
  exit 1
fi
if [ "\$("\$host_new/node_modules/.bin/paseo" --version 2>/dev/null)" != '$version' ]; then
  echo '[oc] Paseo did not pass its version check.' >&2
  exit 1
fi
printf '%s' '$packageLockSha256' > "\$host_new/.oc-package-lock-sha256"
# Do not replace an existing tree until the candidate has passed all probes.
rm -rf "\$host_dir.old"
if [ -d "\$host_dir" ]; then mv "\$host_dir" "\$host_dir.old"; fi
if ! mv "\$host_new" "\$host_dir"; then
  [ ! -d "\$host_dir.old" ] || mv "\$host_dir.old" "\$host_dir"
  exit 1
fi
ln -sf "\$host_dir/node_modules/.bin/paseo" /home/oc/.local/bin/paseo
rm -rf "\$host_dir.old"
oc_version '$version'
''';
  }

  static const check =
      '''set -eu
export PATH=/home/oc/.local/node/bin:/home/oc/.local/bin:/usr/bin:/bin
[ "\$(/home/oc/.local/node/bin/node --version)" = '$nodeVersion' ]
[ "\$(cat $installDirectory/.oc-package-lock-sha256)" = '$packageLockSha256' ]
[ "\$(/home/oc/.local/bin/paseo --version 2>/dev/null)" = '$version' ]
printf '%s\\n' '$version'
''';
}
