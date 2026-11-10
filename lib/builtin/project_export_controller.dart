import 'dart:async';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:path_provider/path_provider.dart';

import 'project_export.dart';

enum ProjectExportPhase { idle, preparing, running, done, failed }

enum CacheClearPhase { idle, clearing, cleared, failed }

/// State for "Export projects" (This phone and the manage-space page) and
/// the manage-space page's cache and delete actions. Everything happens
/// while a page is open; nothing is left running in the background.
class ProjectExportController extends ChangeNotifier {
  ProjectExportController({
    required this.platform,
    Future<AppStorageFacts> Function(String filesDir)? scan,
    Future<List<ProjectExportEntry>> Function(
      String filesDir,
      bool includePrivate,
    )?
    plan,
    Future<String> Function(List<ProjectExportEntry> entries)? writePlan,
    DateTime Function()? now,
  }) : _scan = scan ?? ProjectStorageScanner.scanInBackground,
       _plan =
           plan ??
           ((dir, private) => ProjectStorageScanner.planInBackground(
             dir,
             includePrivate: private,
           )),
       _writePlan = writePlan ?? _writePlanToCache,
       _now = now ?? DateTime.now;

  final ProjectExportPlatform platform;
  final Future<AppStorageFacts> Function(String) _scan;
  final Future<List<ProjectExportEntry>> Function(String, bool) _plan;
  final Future<String> Function(List<ProjectExportEntry>) _writePlan;

  static Future<String> _writePlanToCache(
    List<ProjectExportEntry> entries,
  ) async =>
      (await writeExportPlan(await getTemporaryDirectory(), entries)).path;
  final DateTime Function() _now;

  String? _filesDir;

  /// Null while measuring.
  AppStorageFacts? facts;
  bool get measuring => facts == null;

  /// The app's cache, when measured.
  int? cacheBytes;

  bool includePrivate = false;

  ProjectExportPhase phase = ProjectExportPhase.idle;
  int bytesDone = 0;
  int bytesTotal = 0;
  ProjectExportResult? result;

  /// Whether the last finished export held sign-ins.
  bool exportedPrivate = false;

  CacheClearPhase cachePhase = CacheClearPhase.idle;
  int cacheFreed = 0;

  bool _disposed = false;

  bool get exporting =>
      phase == ProjectExportPhase.preparing ||
      phase == ProjectExportPhase.running;

  void _changed() {
    if (!_disposed) notifyListeners();
  }

  Future<void> load() async {
    facts = null;
    _changed();
    final dir = await platform.filesDir();
    _filesDir = dir;
    var measured = AppStorageFacts.empty;
    if (dir != null) {
      try {
        measured = await _scan(dir);
      } catch (_) {
        measured = AppStorageFacts.empty;
      }
    }
    final servers = await platform.savedServers();
    facts = measured.withSavedServers(servers);
    try {
      cacheBytes = await platform.cacheBytes();
    } catch (_) {
      cacheBytes = null;
    }
    _changed();
  }

  void setIncludePrivate(bool value) {
    if (exporting) return;
    includePrivate = value;
    _changed();
  }

  /// Picks where to save, then writes one zip of every project. Returns
  /// when it has finished, failed, or the person backed out of the picker.
  Future<void> export() async {
    final dir = _filesDir;
    if (exporting || dir == null) return;
    final private = includePrivate;
    final String? destination;
    try {
      destination = await platform.pickDestination(
        suggestedExportName(_now(), private: private),
      );
    } catch (_) {
      _fail(ProjectExportFailure.destination, 'picker');
      return;
    }
    if (destination == null) return;
    phase = ProjectExportPhase.preparing;
    bytesDone = 0;
    bytesTotal = 0;
    result = null;
    _changed();
    final String planPath;
    try {
      final entries = await _plan(dir, private);
      bytesTotal = entries.fold(0, (sum, e) => sum + e.bytes);
      planPath = await _writePlan(entries);
    } catch (e) {
      _fail(ProjectExportFailure.source, e.runtimeType.toString());
      return;
    }
    phase = ProjectExportPhase.running;
    _changed();
    final ProjectExportResult outcome;
    try {
      outcome = await platform.export(
        destination: destination,
        planPath: planPath,
        onProgress: (bytes) {
          bytesDone = bytes;
          _changed();
        },
      );
    } catch (e) {
      _fail(ProjectExportFailure.failed, e.runtimeType.toString());
      return;
    } finally {
      try {
        final planFile = File(planPath);
        if (planFile.existsSync()) planFile.deleteSync();
      } catch (_) {}
    }
    result = outcome;
    exportedPrivate = private;
    phase = outcome.ok ? ProjectExportPhase.done : ProjectExportPhase.failed;
    if (outcome.ok) bytesDone = outcome.bytes;
    _changed();
  }

  void _fail(ProjectExportFailure failure, String detail) {
    result = ProjectExportResult.failed(failure, detail);
    phase = ProjectExportPhase.failed;
    _changed();
  }

  Future<void> cancel() async {
    if (!exporting) return;
    try {
      await platform.cancel();
    } catch (_) {}
  }

  Future<void> clearCache() async {
    if (cachePhase == CacheClearPhase.clearing) return;
    cachePhase = CacheClearPhase.clearing;
    _changed();
    try {
      cacheFreed = await platform.clearCache();
      cacheBytes = await platform.cacheBytes();
      cachePhase = CacheClearPhase.cleared;
    } catch (_) {
      cachePhase = CacheClearPhase.failed;
    }
    _changed();
  }

  /// Clears all the app's data. True when Android accepted; the app then
  /// ends, so a caller only sees false.
  Future<bool> deleteEverything() async {
    try {
      return await platform.clearAllData();
    } catch (_) {
      return false;
    }
  }

  @override
  void dispose() {
    _disposed = true;
    if (exporting) unawaited(cancel());
    super.dispose();
  }
}
