import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:math';

import 'package:crypto/crypto.dart' show sha256;
import 'package:flutter/services.dart' show rootBundle;
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:path_provider/path_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../quota/provider_quota_client.dart' show inAppQuotaPort;
import '../state/profiles.dart';
import 'builtin_linux.dart';
import 'builtin_server.dart' show looksLikeInAppServer;

/// Why setting up usage on In-app Ubuntu stopped. The page words it; [detail]
/// is technical text for Details only. It never holds a token or a key.
enum QuotaSetupFailure {
  /// In-app Ubuntu is not installed or not ready yet.
  linuxNotReady,

  /// There is no Node in In-app Ubuntu.
  noNode,

  /// Node is there but older than 20.
  nodeTooOld,

  /// Copying the collector or its helper into In-app Ubuntu failed.
  copy,

  /// The token or the key files could not be prepared.
  prepare,

  /// The collector did not start, or did not answer in time.
  start,

  /// Turning it off did not finish.
  remove,
}

class QuotaSetupException implements Exception {
  const QuotaSetupException(this.failure, [this.detail]);

  final QuotaSetupFailure failure;
  final String? detail;

  @override
  String toString() => 'QuotaSetupException($failure)';
}

/// Where set-up is, for the progress the page shows.
enum QuotaSetupStep { checking, copying, signIns, starting, reading }

/// Runs the optional quota collector (`tool/quota/collector.mjs`) inside the
/// app's own In-app Ubuntu, so Usage and the Usage widget can show Codex, GLM
/// and MiniMax for that server without the person doing anything by hand.
///
/// Security (tool/quota/README.md, AGENTS.md):
///  - The read token is made here with `Random.secure`, kept in secure
///    storage under `oc.quotaToken.<profileId>` and staged as a 0600 file the
///    collector reads. It is never in a command line or a log.
///  - Provider keys never reach Dart. A helper inside In-app Ubuntu reads
///    OpenCode's own sign-in file and writes 0600 key files; Dart only sees
///    counts ("glm=1").
///  - Codex comes from OpenCode's own sign-in (`openai`, OAuth), read only,
///    format `opencode`. Only when OpenCode has none does the helper fall back
///    to the first Codex sign-in an agent on this phone keeps under
///    `/home/oc/.oc-profiles/<id>/codex/auth.json` (format `codex`). The
///    collector never writes or refreshes either file.
///  - It listens on 127.0.0.1:4195 only.
class InAppQuotaCollector {
  InAppQuotaCollector({
    required this.prefs,
    BuiltinLinux? linux,
    FlutterSecureStorage? secure,
    Random? random,
    Future<String> Function()? loadCollector,
    Future<Directory> Function()? rootfs,
    Future<bool> Function(String token)? probe,
    bool? supported,
    this.probeAttempts = 20,
    this.probeDelay = const Duration(seconds: 1),
  }) : _linux = linux ?? BuiltinLinux(),
       _secure = secure ?? const FlutterSecureStorage(),
       _random = random ?? Random.secure(),
       _loadCollector = loadCollector ?? _bundledCollector,
       _rootfsOverride = rootfs,
       _supported = supported,
       _probe = probe ?? _defaultProbe;

  final SharedPreferences prefs;
  final BuiltinLinux _linux;
  final FlutterSecureStorage _secure;
  final Random _random;
  final Future<String> Function() _loadCollector;
  final Future<Directory> Function()? _rootfsOverride;
  final bool? _supported;
  final Future<bool> Function(String token) _probe;
  final int probeAttempts;
  final Duration probeDelay;

  /// The collector ships with the app, so it matches this build.
  static const collectorAsset = 'tool/quota/collector.mjs';

  static const port = inAppQuotaPort;
  static const serviceName = 'quota';
  static const dir = '/root/.oc-builtin/quota';
  static const collectorPath = '$dir/collector.mjs';
  static const keysPath = '$dir/keys.mjs';
  static const tokenPath = '$dir/read.token';
  static const authEnvPath = '$dir/auth.env';
  static const glmKeyPath = '$dir/glm.key';
  static const minimaxKeyPath = '$dir/minimax.key';

  /// The agents' Codex sign-ins, used only when OpenCode has none.
  static const agentProfilesRoot = '/home/oc/.oc-profiles';

  /// OpenCode's own sign-in file for each server version (the file the app's
  /// OpenCode in In-app Ubuntu writes; BuiltinLinux.serverScript).
  static const openCode1AuthPath = '/root/.local/share/opencode/auth.json';
  static const openCode2AuthPath =
      '/root/.oc-opencode2/data/opencode/auth.json';

  /// Secure-storage slot for a profile's read token.
  static String tokenKey(String profileId) =>
      '${ProfileStore.quotaTokenKeyPrefix}$profileId';

  /// Preference flag: set up here and not turned off. A key under
  /// `oc.<what>.<profileId>` so the deletion sweep removes it.
  static String enabledKey(String profileId) => 'oc.quotaCollector.$profileId';

  static Future<String> _bundledCollector() =>
      rootBundle.loadString(collectorAsset);

  /// The token for [profileId], or null when usage was never set up.
  static Future<String?> readToken(
    String profileId, {
    FlutterSecureStorage? secure,
  }) async {
    try {
      final value = await (secure ?? const FlutterSecureStorage()).read(
        key: tokenKey(profileId),
      );
      return value == null || value.isEmpty ? null : value;
    } on Object {
      return null;
    }
  }

  /// A token reader for the quota gateway when [profile] is the app's own
  /// server; null for every other server, which keeps today's behaviour.
  static Future<String?> Function()? tokenReaderFor(ServerProfile profile) =>
      looksLikeInAppServer(profile) ? () => readToken(profile.id) : null;

  bool get supported => _supported ?? BuiltinLinux.supported;

  /// Whether usage on In-app Ubuntu applies to [profile]: the app's own
  /// server on a runner that has In-app Ubuntu.
  bool appliesTo(ServerProfile? profile) =>
      supported && looksLikeInAppServer(profile);

  /// Set up for [profileId] and not turned off.
  bool isEnabled(String profileId) =>
      prefs.getBool(enabledKey(profileId)) ?? false;

  Future<Directory> _rootfs() async {
    final override = _rootfsOverride;
    if (override != null) return override();
    return Directory(
      '${(await getApplicationSupportDirectory()).path}/linux/ubuntu',
    );
  }

  static Future<bool> _defaultProbe(String token) async {
    final client = HttpClient()..connectionTimeout = const Duration(seconds: 3);
    try {
      final request = await client.getUrl(
        Uri.parse('http://127.0.0.1:$inAppQuotaPort/ocmn/quota/v1'),
      );
      request.headers.set('Authorization', 'Bearer $token');
      final response = await request.close().timeout(
        const Duration(seconds: 12),
      );
      await response.drain<void>();
      // Any answer proves it listens; the readings say the rest.
      return true;
    } on Object {
      return false;
    } finally {
      client.close(force: true);
    }
  }

  String _newToken() {
    // 48 random bytes: 64 Bearer-safe characters.
    final bytes = List<int>.generate(48, (_) => _random.nextInt(256));
    return base64Url.encode(bytes).replaceAll('=', '');
  }

  /// The paths of OpenCode's sign-in file, the profile's own version first.
  List<String> authCandidates(ServerProfile profile) => const [
    openCode2AuthPath,
    openCode1AuthPath,
  ];

  /// The script the service runs: refresh the key files from OpenCode's
  /// sign-ins, read the file paths it chose, start the collector. Only paths
  /// appear here, never a value.
  String serviceScript(ServerProfile profile) {
    final candidates = authCandidates(profile).join(' ');
    return 'set -eu\n'
        'umask 077\n'
        'cd $dir\n'
        'node $keysPath $dir $agentProfilesRoot $candidates >/dev/null 2>&1 '
        '|| true\n'
        'if [ -f $authEnvPath ]; then . $authEnvPath; fi\n'
        'if [ -s $glmKeyPath ]; then export OCMN_GLM_KEY_FILE=$glmKeyPath; fi\n'
        'if [ -s $minimaxKeyPath ]; then '
        'export OCMN_MINIMAX_KEY_FILE=$minimaxKeyPath; fi\n'
        'export OCMN_QUOTA_READ_TOKEN_FILE=$tokenPath\n'
        'export OCMN_QUOTA_PORT=$port\n'
        'exec node $collectorPath\n';
  }

  /// Copies [content] into In-app Ubuntu when it is missing or its hash
  /// changed. True when the file was written.
  Future<bool> _place(Directory root, String path, String content) async {
    final file = File('${root.path}$path');
    final bytes = utf8.encode(content);
    if (await file.exists()) {
      final current = sha256.convert(await file.readAsBytes());
      if (current == sha256.convert(bytes)) return false;
    }
    await file.parent.create(recursive: true);
    await file.writeAsBytes(bytes, flush: true);
    return true;
  }

  /// Puts the collector and its helper in place; true when either changed.
  Future<bool> _installFiles(Directory root) async {
    final collector = await _loadCollector();
    final a = await _place(root, collectorPath, collector);
    final b = await _place(root, keysPath, keysHelperSource);
    return a || b;
  }

  Future<void> _stageToken(Directory root, String token) async {
    final file = File('${root.path}$tokenPath');
    await file.parent.create(recursive: true);
    await file.writeAsString('$token\n', flush: true);
  }

  Future<QuotaSetupException?> _checkLinux() async {
    final status = await _linux.status();
    if (!status.installed || status.phase != BuiltinLinuxPhase.ready) {
      return const QuotaSetupException(QuotaSetupFailure.linuxNotReady);
    }
    return null;
  }

  /// The Node major version in In-app Ubuntu; null when there is none.
  static int? parseNodeMajor(String output) {
    final match = RegExp(r'qc node=(\d+)').firstMatch(output);
    return match == null ? null : int.tryParse(match.group(1)!);
  }

  /// Sets usage up for [profile]: checks Node, copies the collector, makes
  /// and stages the token, prepares the key files, starts the service and
  /// waits for it to answer. Safe to run again: it only changes what differs.
  Future<void> setUp(
    ServerProfile profile, {
    void Function(QuotaSetupStep step)? onStep,
  }) async {
    final id = profile.id;
    onStep?.call(QuotaSetupStep.checking);
    final linuxProblem = await _checkLinux();
    if (linuxProblem != null) throw linuxProblem;
    final node = await _linux.run(
      'v=\$(node -p "process.versions.node" 2>/dev/null) || v=; '
      'echo "qc node=\${v%%.*}"',
      timeout: const Duration(seconds: 30),
    );
    final major = parseNodeMajor(node.output);
    if (major == null) {
      throw const QuotaSetupException(QuotaSetupFailure.noNode);
    }
    if (major < 20) {
      throw QuotaSetupException(
        QuotaSetupFailure.nodeTooOld,
        'Node $major is installed; the collector needs 20 or later.',
      );
    }

    onStep?.call(QuotaSetupStep.copying);
    final Directory root;
    final String token;
    try {
      root = await _rootfs();
      await _installFiles(root);
      var stored = await _secure.read(key: tokenKey(id));
      if (stored == null || stored.length < 48) {
        stored = _newToken();
        await _secure.write(key: tokenKey(id), value: stored);
      }
      token = stored;
      await _stageToken(root, token);
    } on Object catch (error) {
      throw QuotaSetupException(
        QuotaSetupFailure.copy,
        'Copying into In-app Ubuntu failed (${error.runtimeType}).',
      );
    }
    final modes = await _linux.run(
      'umask 077; chmod 700 $dir && chmod 600 $tokenPath $collectorPath '
      '$keysPath && echo "qc files=ok"',
      timeout: const Duration(seconds: 30),
    );
    if (!modes.output.contains('qc files=ok')) {
      throw const QuotaSetupException(
        QuotaSetupFailure.prepare,
        'The files in In-app Ubuntu could not be locked down.',
      );
    }

    onStep?.call(QuotaSetupStep.signIns);
    final keys = await _linux.run(
      'umask 077; node $keysPath $dir $agentProfilesRoot '
      '${authCandidates(profile).join(' ')}',
      timeout: const Duration(seconds: 30),
    );
    if (!keys.output.contains('qc keys=ok')) {
      throw const QuotaSetupException(
        QuotaSetupFailure.prepare,
        'The sign-in helper did not finish.',
      );
    }

    onStep?.call(QuotaSetupStep.starting);
    try {
      await _linux.startService(
        serviceName,
        serviceScript(profile),
        port: port,
        notice: null,
      );
    } on Object catch (error) {
      throw QuotaSetupException(
        QuotaSetupFailure.start,
        'The collector did not start (${error.runtimeType}).',
      );
    }
    var answered = false;
    for (var i = 0; i < probeAttempts && !answered; i++) {
      answered = await _probe(token);
      if (!answered) await Future<void>.delayed(probeDelay);
    }
    if (!answered) {
      String log = '';
      try {
        log = await _linux.serviceLog(serviceName);
      } on Object {
        // The log is a help, not a need.
      }
      throw QuotaSetupException(
        QuotaSetupFailure.start,
        log.trim().isEmpty ? 'The collector did not answer in time.' : log,
      );
    }
    await prefs.setBool(enabledKey(id), true);
  }

  /// When the app opens or In-app Ubuntu starts: a collector that was set up
  /// and not turned off comes back. Quiet; false when it did not.
  Future<bool> ensureRunning(ServerProfile profile) async {
    if (!appliesTo(profile) || !isEnabled(profile.id)) return false;
    try {
      if (await _checkLinux() != null) return false;
      final token = await readToken(profile.id, secure: _secure);
      if (token == null) return false;
      final root = await _rootfs();
      final changed = await _installFiles(root);
      final status = await _linux.status();
      if (!changed && status.services.contains(serviceName)) return true;
      await _stageToken(root, token);
      await _linux.run(
        'chmod 700 $dir && chmod 600 $tokenPath $collectorPath $keysPath',
        timeout: const Duration(seconds: 30),
      );
      await _linux.startService(
        serviceName,
        serviceScript(profile),
        port: port,
        notice: null,
      );
      return true;
    } on Object {
      return false;
    }
  }

  /// The end of the collector's log, for Details. It holds fixed codes only.
  Future<String> log() => _linux.serviceLog(serviceName);

  /// Turns usage off: stops the service and removes its files, key copies
  /// and the token.
  Future<void> turnOff(String profileId) async {
    await _linux.stopService(serviceName);
    final result = await _linux.run(
      'rm -rf $dir && echo "qc removed"',
      timeout: const Duration(minutes: 1),
    );
    if (!result.output.contains('qc removed')) {
      throw const QuotaSetupException(QuotaSetupFailure.remove);
    }
    await _secure.delete(key: tokenKey(profileId));
    await prefs.remove(enabledKey(profileId));
  }

  /// Removes everything for a deleted server; quiet when there is nothing.
  /// The preference flag goes with the profile sweep; this does the rest.
  static Future<void> forget(
    String profileId, {
    required SharedPreferences prefs,
    BuiltinLinux? linux,
    FlutterSecureStorage? secure,
  }) async {
    final host = InAppQuotaCollector(
      prefs: prefs,
      linux: linux,
      secure: secure,
      supported: true,
    );
    final wasOn = host.isEnabled(profileId);
    try {
      if (wasOn) await host.turnOff(profileId);
    } on Object {
      // The token still goes below; a leftover folder holds no secret the
      // token does not already guard.
    }
    await (secure ?? const FlutterSecureStorage()).delete(
      key: tokenKey(profileId),
    );
  }

  /// Starts a collector that was set up, when In-app Ubuntu has just started.
  /// Needs no screen. Quiet.
  static Future<void> restartIfSetUp(
    ServerProfile profile, {
    BuiltinLinux? linux,
  }) async {
    if (!looksLikeInAppServer(profile)) return;
    try {
      final prefs = await SharedPreferences.getInstance();
      final host = InAppQuotaCollector(prefs: prefs, linux: linux);
      await host.ensureRunning(profile);
    } on Object {
      // A failure shows on the Usage page, not as a popup.
    }
  }

  /// Runs inside In-app Ubuntu (Node >= 20). Reads OpenCode's sign-in file
  /// and writes 0600 key files for GLM and MiniMax plus `auth.env` (file
  /// paths only) for Codex. It prints counts, never a value.
  static const keysHelperSource = r'''
import { chmodSync, existsSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs';

const [dir, agentRoot, ...candidates] = process.argv.slice(2);
const read = (path) => {
  try {
    const value = JSON.parse(readFileSync(path, 'utf8'));
    return value && typeof value === 'object' ? value : null;
  } catch {
    return null;
  }
};
const docs = candidates.map((path) => [path, read(path)]).filter(([, doc]) => doc);
const apiKey = (ids) => {
  for (const [, doc] of docs) {
    for (const id of ids) {
      const entry = doc[id];
      if (entry && typeof entry.key === 'string' && entry.key.trim()) return entry.key.trim();
    }
  }
  return null;
};
const put = (name, value) => {
  const file = `${dir}/${name}`;
  rmSync(file, { force: true });
  if (!value) return 0;
  writeFileSync(file, `${value}\n`, { mode: 0o600 });
  chmodSync(file, 0o600);
  return 1;
};
const quote = (text) => `'${String(text).replaceAll("'", "'\\''")}'`;

const glm = put('glm.key', apiKey(['zai-coding-plan']));
const minimax = put('minimax.key', apiKey(['minimax-coding-plan', 'minimax']));

let env = '';
let codex = 'none';
const oauth = (entry) => entry && entry.type === 'oauth' && typeof entry.access === 'string';
const own = docs.find(([, doc]) => oauth(doc.openai) || oauth(doc.codex));
if (own) {
  env = `export OCMN_QUOTA_AUTH_FILE=${quote(own[0])}\nexport OCMN_QUOTA_AUTH_FORMAT=opencode\n`;
  codex = 'opencode';
} else if (existsSync(agentRoot)) {
  for (const name of readdirSync(agentRoot).sort()) {
    const file = `${agentRoot}/${name}/codex/auth.json`;
    const doc = read(file);
    if (doc && doc.tokens && doc.tokens.access_token && doc.tokens.account_id) {
      env = `export OCMN_QUOTA_AUTH_FILE=${quote(file)}\nexport OCMN_QUOTA_AUTH_FORMAT=codex\n`;
      codex = 'agent';
      break;
    }
  }
}
rmSync(`${dir}/auth.env`, { force: true });
writeFileSync(`${dir}/auth.env`, env, { mode: 0o600 });
console.log(`qc keys=ok codex=${codex} glm=${glm} minimax=${minimax}`);
''';
}
