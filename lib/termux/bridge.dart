import 'package:flutter/services.dart';

/// Bridge to drive the Termux app on this device:
/// detect it, launch it, and execute shell commands inside it through
/// Termux's documented RUN_COMMAND service.
class TermuxBridge {
  static const _channel = MethodChannel('oc/termux');

  static const termuxPackage = 'com.termux';
  static const termuxHome = '/data/data/com.termux/files/home';

  /// True when the Termux app is installed (any build).
  static Future<bool> isInstalled() async =>
      await _channel.invokeMethod<bool>('isTermuxInstalled') ?? false;

  /// Opens the Termux terminal app. Returns false if it cannot be launched.
  static Future<bool> openTermux() async =>
      await _channel.invokeMethod<bool>('openTermux') ?? false;

  /// Runs [script] inside Termux's bash. Throws [PlatformException]-style
  /// [TermuxBridgeException] when Termux refuses the command (bridge not
  /// unlocked via allow-external-apps yet, or RUN_COMMAND permission missing).
  static Future<void> run(
    String script, {
    bool background = true,
    String? workdir,
  }) async {
    final ok = await _channel.invokeMethod<bool>(
      'runInTermux',
      {
        'script': script,
        'background': background,
        'workdir': workdir ?? termuxHome,
      },
    );
    if (ok != true) {
      throw const TermuxBridgeException(
          'Termux rejected the command. Finish the bridge unlock step first.');
    }
  }

  // ----- canned scripts -----

  /// One-time paste that unlocks the bridge: enables allow-external-apps
  /// and reloads Termux settings.
  static const unlockCommand =
      "mkdir -p ~/.termux && grep -q '^allow-external-apps=true' "
      "~/.termux/termux.properties 2>/dev/null || "
      "echo 'allow-external-apps=true' >> ~/.termux/termux.properties; "
      "termux-reload-settings 2>/dev/null; echo bridge-unlocked";

  /// Installs opencode inside an Ubuntu proot-distro chroot and launches the
  /// server detached.
  ///
  /// Plain-Termux npm installs of opencode-ai are broken upstream
  /// (npm sees os=android -> EBADPLATFORM / missing opencode-android-arm64,
  /// see anomalyco/opencode#12515 and #10504). The reliable path is a real
  /// glibc userland via proot-distro; its network namespace is shared, so
  /// `127.0.0.1:PORT` remains reachable from the Android side.
  static String installAndServeScript({int port = 4096}) {
    final home = termuxHome;
    return """
mkdir -p $home/.oc
termux-wake-lock >/dev/null 2>&1 || true

{
# --- heal broken Termux mirrors (fresh installs often point at dead hosts) ---
command -v proot-distro >/dev/null 2>&1 || {
  pkg update -y >/dev/null 2>&1 || {
    printf 'deb https://packages.termux.dev/apt/termux-main stable main\\n' \\
      > \$PREFIX/etc/apt/sources.list
    pkg update -y >/dev/null 2>&1 || true
  }
  pkg install -y proot-distro
}

[ -d \$PREFIX/var/lib/proot-distro/installed-rootfs/ubuntu ] \\
  || proot-distro install ubuntu

proot-distro login ubuntu -- bash -lc '
  command -v opencode >/dev/null 2>&1 && exit 0
  export DEBIAN_FRONTEND=noninteractive
  apt-get update -y -o Acquire::Retries=5
  apt-get install -y nodejs npm -o Acquire::Retries=5
  npm i -g opencode-ai || { sleep 8; npm i -g opencode-ai; }
'

pkill -f 'opencode serve --hostname 127.0.0.1 --port $port' 2>/dev/null || true
setsid nohup proot-distro login ubuntu -- bash -lc \\
  'opencode serve --hostname 127.0.0.1 --port $port' \\
  > $home/.oc/server.log 2>&1 &
} >> $home/.oc/install.log 2>&1
echo setup-finished
""";
  }

  /// Full diagnostics dump shown in a visible Termux session: component
  /// states first (so it's useful even before any logs exist), then logs.
  static String logScript({int port = 4096}) {
    final home = termuxHome;
    return """
mkdir -p $home/.oc
touch $home/.oc/install.log $home/.oc/server.log
clear
echo ''
echo '════════ OpenCode on-device diagnostics ════════'
if command -v proot-distro >/dev/null 2>&1; then
  echo '[ok]   proot-distro installed'
else
  echo '[FAIL] proot-distro missing'
fi
if [ -d \$PREFIX/var/lib/proot-distro/installed-rootfs/ubuntu ]; then
  echo '[ok]   ubuntu chroot downloaded'
else
  echo '[....] ubuntu chroot not present yet'
fi
proot-distro login ubuntu -- bash -lc \\
  'command -v opencode >/dev/null 2>&1 && echo "[ok]   opencode installed" || echo "[....] opencode not installed yet"' \\
  2>/dev/null || echo '[....] opencode not installed yet'
if (echo > /dev/tcp/127.0.0.1/$port) 2>/dev/null; then
  echo '[ok]   server LISTENING on 127.0.0.1:$port'
else
  echo '[....] server not responding on 127.0.0.1:$port'
fi
echo ''
echo '───── install.log (last 100 lines) ─────'
tail -n 100 $home/.oc/install.log
echo ''
echo '───── server.log ─────'
tail -n 40 $home/.oc/server.log
echo ''
echo '══════ end of diagnostics ══════'
""";
  }

  /// Stops any server we started on [port].
  static String stopScript({int port = 4096}) =>
      "pkill -f 'opencode serve --hostname 127.0.0.1 --port $port' 2>/dev/null; true";
}

class TermuxBridgeException implements Exception {
  final String message;
  const TermuxBridgeException(this.message);

  @override
  String toString() => message;
}
