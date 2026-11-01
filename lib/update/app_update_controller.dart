import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:crypto/crypto.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:path_provider/path_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../platform/platform_capabilities.dart';
import '../state/connection.dart';
import 'app_release.dart';
import 'app_update_platform.dart';
import 'app_update_source.dart';

/// Where the app's own update is (docs/design/app-updates-2026-10-31.md).
enum AppUpdatePhase {
  none,
  checking,
  available,
  downloading,
  verifying,
  readyToInstall,
  installing,
  failed,

  /// A code-push patch is downloaded and waits for a restart. A newer
  /// release wins over it.
  restartToUpdate,
}

/// Why an update stopped. The words people read come from the UI; the
/// technical text is [AppUpdateController.detail].
enum AppUpdateFailure {
  network,
  noFile,
  checksum,
  signer,
  package,
  version,
  storage,
  installer,
}

class InstalledApp {
  const InstalledApp({
    required this.packageName,
    required this.version,
    required this.build,
  });

  final String packageName;
  final String version;
  final int build;
}

Future<InstalledApp> installedAppFromPlatform() async {
  final info = await PackageInfo.fromPlatform();
  return InstalledApp(
    packageName: info.packageName,
    version: info.version,
    build: int.tryParse(info.buildNumber) ?? 0,
  );
}

const appUpdateCheckInterval = Duration(hours: 6);
const _retryAfterFailure = Duration(minutes: 30);

const _enabledKey = 'oc.appUpdate.enabled';
const _lastCheckKey = 'oc.appUpdate.lastCheckMs';
const _releaseKey = 'oc.appUpdate.release';

/// The one app-wide holder of the app's update state; the notice, the
/// gear badge, the sheet and the Updates page all read it.
class AppUpdateController extends ChangeNotifier {
  AppUpdateController({
    required this.source,
    required this.platform,
    required Future<SharedPreferences> Function() prefs,
    required Future<InstalledApp> Function() installed,
    required Future<Directory> Function() cacheDir,
    this.supported = true,
    DateTime Function()? now,
  }) : _prefsFuture = prefs,
       _installed = installed,
       _cacheDir = cacheDir,
       _now = now ?? DateTime.now;

  final AppUpdateSource source;
  final AppUpdatePlatform platform;
  final bool supported;
  final Future<SharedPreferences> Function() _prefsFuture;
  final Future<InstalledApp> Function() _installed;
  final Future<Directory> Function() _cacheDir;
  final DateTime Function() _now;

  SharedPreferences? _prefs;
  InstalledApp? _app;
  AppUpdatePhase _phase = AppUpdatePhase.none;
  AppRelease? _release;
  AppUpdateFailure? _failure;
  String? _detail;
  double? _progress;
  int? _receivedBytes;
  bool _enabled = true;
  bool _patchReady = false;
  bool _notNow = false;
  bool _needsPermission = false;
  DateTime? _checkedAt;
  String? _verifiedPath;
  List<String> _abis = const [];
  DownloadCancel? _cancel;
  bool _disposed = false;
  bool _started = false;
  bool _pageOpen = false;

  /// The phase the person is in. A release in any phase wins over a ready
  /// patch.
  AppUpdatePhase get phase => _phase == AppUpdatePhase.none && _patchReady
      ? AppUpdatePhase.restartToUpdate
      : _phase;

  AppRelease? get release => _release;
  AppUpdateFailure? get failure => _failure;

  /// Technical text for the Details fold; never shown as copy.
  String? get detail => _detail;

  /// 0..1 while downloading; null when the size is not known.
  double? get progress => _progress;
  int? get receivedBytes => _receivedBytes;
  bool get enabled => _enabled;
  bool get needsPermission => _needsPermission;
  DateTime? get checkedAt => _checkedAt;
  InstalledApp? get installedApp => _app;

  /// A release newer than this build is known and not yet installed. Drives
  /// the gear badge, and stays on after Not now.
  bool get hasPendingRelease => _release != null;

  /// Bytes of the file this phone would download, once known.
  int? get downloadSize => _release?.apkFor(_abis)?.size;

  /// The status line offers or tracks the update. Not now silences only the
  /// offer; work the person started keeps its line.
  bool get noticeVisible =>
      _noticeWanted && (!_pageOpen || phase == AppUpdatePhase.restartToUpdate);

  /// The Updates page is open: its own row is the one place the update
  /// shows, so the app-wide status line steps aside for a release.
  void setPageOpen(bool open) {
    if (_pageOpen == open) return;
    _pageOpen = open;
    _poke();
  }

  bool get _noticeWanted => switch (phase) {
    AppUpdatePhase.available || AppUpdatePhase.readyToInstall => !_notNow,
    AppUpdatePhase.downloading ||
    AppUpdatePhase.verifying ||
    AppUpdatePhase.installing => true,
    AppUpdatePhase.restartToUpdate => true,
    _ => false,
  };

  bool get busy =>
      _phase == AppUpdatePhase.downloading ||
      _phase == AppUpdatePhase.verifying ||
      _phase == AppUpdatePhase.installing ||
      _phase == AppUpdatePhase.checking;

  void _set(AppUpdatePhase phase) {
    _phase = phase;
    if (!_disposed) notifyListeners();
  }

  void _poke() {
    if (!_disposed) notifyListeners();
  }

  /// Loads the saved choice and the release found last time, then checks
  /// when a check is due.
  Future<void> start() async {
    if (!supported || _started) return;
    _started = true;
    final prefs = _prefs = await _prefsFuture();
    _enabled = prefs.getBool(_enabledKey) ?? true;
    final last = prefs.getInt(_lastCheckKey);
    if (last != null) _checkedAt = DateTime.fromMillisecondsSinceEpoch(last);
    _app = await _installed();
    final saved = prefs.getString(_releaseKey);
    if (saved != null) {
      try {
        final release = AppRelease.fromJson(
          jsonDecode(saved) as Map<String, dynamic>,
        );
        if (release != null && release.build > _app!.build) {
          _release = release;
          _phase = AppUpdatePhase.available;
        } else {
          // Installed (or older): the finding and its download are done.
          await prefs.remove(_releaseKey);
          unawaited(_clearCache());
        }
      } on FormatException {
        await prefs.remove(_releaseKey);
      }
    }
    if (_release != null) _abis = await platform.supportedAbis();
    _poke();
    await checkIfDue();
  }

  Future<void> setEnabled(bool value) async {
    _enabled = value;
    await (_prefs ??= await _prefsFuture()).setBool(_enabledKey, value);
    _poke();
    if (value) await checkIfDue();
  }

  /// Start-up and resume: asks GitHub at most once every six hours.
  Future<void> checkIfDue() async {
    if (!supported || !_enabled || busy) return;
    final last = _checkedAt;
    if (last != null && _now().difference(last) < appUpdateCheckInterval) {
      return;
    }
    await _check();
  }

  /// "Check now": asks regardless of the clock.
  Future<void> checkNow() async {
    if (!supported || busy) return;
    await _check();
  }

  Future<void> _check() async {
    final before = _phase;
    final prefs = _prefs ??= await _prefsFuture();
    _app ??= await _installed();
    _failure = null;
    _detail = null;
    _set(AppUpdatePhase.checking);
    try {
      final found = await source.fetchLatest();
      _stamp(prefs, _now());
      _abis = await platform.supportedAbis();
      if (found != null && found.build > _app!.build) {
        if (_release?.tag != found.tag) _notNow = false;
        _release = found;
        await prefs.setString(_releaseKey, jsonEncode(found.json));
        _set(AppUpdatePhase.available);
      } else {
        _release = null;
        await prefs.remove(_releaseKey);
        _set(AppUpdatePhase.none);
      }
    } on Object catch (e) {
      // Look again sooner than six hours, but not on every resume.
      _stamp(
        prefs,
        _now().subtract(appUpdateCheckInterval - _retryAfterFailure),
      );
      _detail = '$e';
      // A failed check is not a failed update: go back to what it was.
      _set(before == AppUpdatePhase.checking ? AppUpdatePhase.none : before);
    }
  }

  void _stamp(SharedPreferences prefs, DateTime at) {
    _checkedAt = at;
    unawaited(prefs.setInt(_lastCheckKey, at.millisecondsSinceEpoch));
  }

  /// Not now: hides the offer for this version until the next app start.
  /// The gear badge stays.
  void notNow() {
    _notNow = true;
    _poke();
  }

  /// A code-push patch finished downloading (the older notice calls this).
  void patchReady() {
    if (_patchReady) return;
    _patchReady = true;
    _poke();
  }

  void cancel() => _cancel?.cancel();

  /// Update now: download, check, install. Every step that fails says why
  /// and stops; a file that does not match is deleted and never installed.
  Future<void> updateNow() async {
    final release = _release;
    if (!supported || release == null || busy) return;
    _failure = null;
    _detail = null;
    _progress = null;
    _receivedBytes = null;
    final kept = _verifiedPath;
    if (kept != null && await File(kept).exists()) {
      await _install(kept);
      return;
    }
    _verifiedPath = null;
    try {
      _abis = await platform.supportedAbis();
      final asset = release.apkFor(_abis);
      final sums = release.checksums;
      if (asset == null || sums == null) {
        return _fail(AppUpdateFailure.noFile, 'no file for this phone');
      }
      _set(AppUpdatePhase.downloading);
      final expected = parseSha256Sums(
        await source.fetchText(sums.url),
      )[asset.name];
      if (expected == null) {
        return _fail(
          AppUpdateFailure.checksum,
          'no checksum for ${asset.name}',
        );
      }
      final dir = Directory('${(await _cacheDir()).path}/app-update');
      final file = File('${dir.path}/${asset.name}');
      final cancel = _cancel = DownloadCancel();
      await source.download(
        asset.url,
        file,
        cancel: cancel,
        onProgress: (received, total) {
          _receivedBytes = received;
          final size = total > 0 ? total : asset.size;
          _progress = size > 0 ? (received / size).clamp(0.0, 1.0) : null;
          _poke();
        },
      );
      _cancel = null;
      _set(AppUpdatePhase.verifying);
      final actual = (await sha256.bind(file.openRead()).first).toString();
      if (actual != expected) {
        await _discard(file);
        return _fail(
          AppUpdateFailure.checksum,
          'sha256 $actual, expected $expected',
        );
      }
      final info = await platform.inspectApk(file.path);
      final own = await platform.ownSignerSha256();
      final app = _app ??= await _installed();
      if (info.packageName != app.packageName) {
        await _discard(file);
        return _fail(
          AppUpdateFailure.package,
          'package ${info.packageName}, expected ${app.packageName}',
        );
      }
      if (own.isEmpty || info.signerSha256.toUpperCase() != own.toUpperCase()) {
        await _discard(file);
        return _fail(
          AppUpdateFailure.signer,
          'signer ${info.signerSha256}, expected $own',
        );
      }
      if (info.versionCode <= app.build) {
        await _discard(file);
        return _fail(
          AppUpdateFailure.version,
          'versionCode ${info.versionCode} is not above ${app.build}',
        );
      }
      _verifiedPath = file.path;
      await _install(file.path);
    } on DownloadCancelled {
      _cancel = null;
      _set(AppUpdatePhase.available);
    } on FileSystemException catch (e) {
      _fail(AppUpdateFailure.storage, '$e');
    } on Object catch (e) {
      _fail(AppUpdateFailure.network, '$e');
    }
  }

  Future<void> _install(String path) async {
    if (!await platform.canInstall()) {
      // Ask once, plainly; the person returns and resume carries on.
      _needsPermission = true;
      _set(AppUpdatePhase.readyToInstall);
      await platform.openInstallPermission();
      return;
    }
    _needsPermission = false;
    _set(AppUpdatePhase.installing);
    try {
      await for (final event in platform.install(path)) {
        switch (event.state) {
          case AppInstallState.pendingUser:
            break;
          case AppInstallState.success:
            _set(AppUpdatePhase.installing);
          case AppInstallState.aborted:
            _set(AppUpdatePhase.readyToInstall);
          case AppInstallState.failure:
            _fail(AppUpdateFailure.installer, event.message);
        }
      }
    } on Object catch (e) {
      _fail(AppUpdateFailure.installer, '$e');
    }
  }

  /// Back in the app: continues an install that waited for the permission,
  /// then checks when due.
  Future<void> onResumed() async {
    if (!supported) return;
    final path = _verifiedPath;
    if (_phase == AppUpdatePhase.readyToInstall &&
        _needsPermission &&
        path != null &&
        await platform.canInstall()) {
      await _install(path);
      return;
    }
    await checkIfDue();
  }

  void _fail(AppUpdateFailure why, String? detail) {
    _failure = why;
    _detail = detail;
    _set(AppUpdatePhase.failed);
  }

  Future<void> _discard(File file) async {
    try {
      if (await file.exists()) await file.delete();
    } on FileSystemException {
      // Left behind: the next download starts over it.
    }
  }

  Future<void> _clearCache() async {
    try {
      final dir = Directory('${(await _cacheDir()).path}/app-update');
      if (await dir.exists()) await dir.delete(recursive: true);
    } on Object {
      // Cache only.
    }
  }

  @override
  void dispose() {
    _disposed = true;
    _cancel?.cancel();
    super.dispose();
  }
}

/// The app-wide update holder. Only Android installs its own updates.
final appUpdateProvider = Provider<AppUpdateController>((ref) {
  final controller = AppUpdateController(
    source: DioAppUpdateSource(),
    platform: ChannelAppUpdatePlatform(),
    prefs: () async => ref.read(connProvider).store.prefs,
    installed: installedAppFromPlatform,
    cacheDir: getTemporaryDirectory,
    supported: platformCapabilities.isAndroid,
  );
  ref.onDispose(controller.dispose);
  return controller;
});
