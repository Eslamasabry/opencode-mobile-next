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

  /// Installs opencode + prerequisites, then launches the server detached
  /// so it survives after this script exits.
  static String installAndServeScript({int port = 4096}) => """
set -e
command -v opencode >/dev/null 2>&1 || {
  pkg update -y >/dev/null 2>&1 || true
  pkg install -y nodejs-lts git >/dev/null 2>&1
  npm i -g opencode-ai >/dev/null 2>&1
}
mkdir -p $termuxHome/.oc
nohup opencode serve --hostname 127.0.0.1 --port $port > $termuxHome/.oc/server.log 2>&1 &
echo server-starting
""";

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
