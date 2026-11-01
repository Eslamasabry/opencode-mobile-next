/// Backing up and restoring the app's own Linux (S3): the steps around the
/// job scripts of linux_backup_plan.dart.
///
/// Back up: the person picks where the zip goes (Android's "Save to"), the
/// backup job makes the parts inside Linux, this writes the manifest, and
/// the existing zip writer (`oc/project_export`, ProjectExportBridge.kt)
/// streams the parts into the chosen file. Nothing new on the native side,
/// so it ships as a patch.
///
/// Restore: the person picks a backup zip, this reads it with the pure-Dart
/// [readZipEntries] (only the entries a backup holds, never a path from the
/// zip), the restore job puts it back, and the supervisor starts the
/// services that were on.
library;

import 'dart:async';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../builtin_folders.dart';
import '../linux_manager/linux_manager.dart';
import '../linux_manager/linux_packages.dart';
import '../project_export.dart';
import 'linux_backup_plan.dart';
import 'zip_reader.dart';

/// Every setting the in-app Linux keeps for the phone starts with this.
/// Device scope: the in-app Linux belongs to the phone, not a connection,
/// so these keys are not `oc.<what>.<profileId>` and the profile sweep
/// (`ProfileStore.profileScopedPreferenceKeys`) never finds them;
/// [forgetLinuxDevicePreferences] does, when OpenCode is removed.
const linuxDevicePreferencePrefix = 'oc.linux.';

/// When the last backup was saved (device scope, see above).
const linuxLastBackupKey = '${linuxDevicePreferencePrefix}lastBackupAt';

/// Removing OpenCode from the phone erases the in-app Linux, so what its
/// settings remembered about it goes too ("Last backup 3 days ago" must
/// not greet a Linux that was set up afterwards). Reset keeps them: it
/// does not call this. The backup files themselves are never touched; they
/// live where the person put them.
///
/// Returns false when a key could not be removed (the caller carries on:
/// the phone is clean either way, only a hub line would be stale).
Future<bool> forgetLinuxDevicePreferences(SharedPreferences prefs) async {
  var all = true;
  final keys = prefs
      .getKeys()
      .where((key) => key.startsWith(linuxDevicePreferencePrefix))
      .toList();
  for (final key in keys) {
    if (!await prefs.remove(key)) all = false;
  }
  return all;
}

enum LinuxBackupPhase {
  idle,

  /// The job is making the parts inside Linux.
  preparing,

  /// The zip is being written where the person chose.
  saving,
  saved,

  /// Reading a chosen backup.
  checking,

  /// A backup was read; it waits for the person's yes.
  ready,
  restoring,
  restored,
  failed,
}

enum LinuxBackupProblem {
  /// Another job or setup is running.
  busy,

  /// Linux is not there.
  linuxMissing,

  /// The job inside Linux ended badly.
  jobFailed,

  /// The zip could not be written (often: no room, or the place went away).
  saveFailed,

  /// The chosen file is not a backup of this app.
  notABackup,

  /// The backup was made by a newer app.
  newerBackup,
}

class LinuxBackupController extends ChangeNotifier {
  LinuxBackupController({
    required this.jobs,
    required this.exporter,
    required this.prefs,
    required this.notice,
    required this.pickBackupFile,
    this.afterRestore,
    this.appVersion,
    this.abi,
    Future<Directory?> Function()? locateRootfs,
    Future<Directory> Function()? tempDir,
    this.paths = const LinuxBackupPaths(),
    DateTime Function()? clock,
  }) : _locate = locateRootfs ?? BuiltinRootfsFolders.locateRootfs,
       _tempDir = tempDir ?? (() async => Directory.systemTemp),
       _clock = clock ?? DateTime.now;

  final LinuxJobController jobs;
  final ProjectExportPlatform exporter;
  final SharedPreferences prefs;

  /// What the ongoing notification says while a job runs.
  final String Function(LinuxJobKind kind) notice;

  /// The system file picker; the chosen file's local path, or null.
  final Future<String?> Function() pickBackupFile;

  /// Runs after a restore (the services that were on start again).
  final Future<void> Function()? afterRestore;
  final Future<String> Function()? appVersion;
  final Future<String> Function()? abi;
  final LinuxBackupPaths paths;
  final Future<Directory?> Function() _locate;
  final Future<Directory> Function() _tempDir;
  final DateTime Function() _clock;

  LinuxBackupPhase phase = LinuxBackupPhase.idle;
  LinuxBackupProblem? problem;

  /// Technical text for Details only.
  String? details;

  /// While saving: bytes written of [totalBytes].
  int savedBytes = 0;
  int totalBytes = 0;

  /// The name the zip was saved under.
  String? fileName;

  /// The backup chosen to restore, once read.
  LinuxBackupManifest? manifest;
  LinuxRestoreSummary? summary;
  String? _chosen;
  bool _disposed = false;

  bool get busy => const {
    LinuxBackupPhase.preparing,
    LinuxBackupPhase.saving,
    LinuxBackupPhase.checking,
    LinuxBackupPhase.restoring,
  }.contains(phase);

  DateTime? get lastBackupAt {
    final ms = prefs.getInt(linuxLastBackupKey);
    return ms == null ? null : DateTime.fromMillisecondsSinceEpoch(ms);
  }

  void _go(LinuxBackupPhase next) {
    phase = next;
    if (!_disposed) notifyListeners();
  }

  void _fail(LinuxBackupProblem why, [String? technical]) {
    problem = why;
    details = technical;
    _go(LinuxBackupPhase.failed);
  }

  /// Back to the start (after the person read how it ended).
  void reset() {
    problem = null;
    details = null;
    manifest = null;
    summary = null;
    _chosen = null;
    _go(LinuxBackupPhase.idle);
  }

  Future<Directory> _root() async {
    final root = await _locate();
    if (root == null) {
      throw const _Stop(LinuxBackupProblem.linuxMissing);
    }
    return root;
  }

  /// A folder under the rootfs, refusing links on the way (a link made
  /// inside Ubuntu would lead outside it from Android's side).
  static Future<Directory> _safeDir(String root, String guest) async {
    var at = root;
    for (final part in guest.split('/')) {
      if (part.isEmpty) continue;
      at = '$at/$part';
      final type = await FileSystemEntity.type(at, followLinks: false);
      if (type == FileSystemEntityType.notFound) {
        await Directory(at).create();
      } else if (type != FileSystemEntityType.directory) {
        throw _Stop(LinuxBackupProblem.jobFailed, 'not a folder: $at');
      }
    }
    return Directory(at);
  }

  Future<LinuxJobState> _runJob(LinuxJobKind kind, String script) async {
    try {
      await jobs.startScript(kind, script, notice: notice(kind));
    } on LinuxManagerException catch (error) {
      throw _Stop(
        error.problem == LinuxProblem.unavailable
            ? LinuxBackupProblem.linuxMissing
            : LinuxBackupProblem.busy,
        error.details,
      );
    }
    final done = Completer<LinuxJobState>();
    void check() {
      final state = jobs.state;
      if (!state.running && !done.isCompleted) done.complete(state);
    }

    jobs.addListener(check);
    try {
      check();
      return await done.future;
    } finally {
      jobs.removeListener(check);
    }
  }

  /// Backs up [kind] into a zip the person places. The parts are made
  /// first and "Save to" asked after, so backing out (or a failed job)
  /// never leaves an empty file behind.
  Future<void> backUp(LinuxBackupKind kind) async {
    if (busy) return;
    reset();
    final name = linuxBackupFileName(kind, _clock());
    fileName = name;
    _go(LinuxBackupPhase.preparing);
    String? stagePath;
    File? plan;
    try {
      final root = await _root();
      stagePath = '${root.path}${paths.stage}';
      final state = await _runJob(
        LinuxJobKind.backup,
        linuxBackupJobScript(kind),
      );
      if (state.status != LinuxJobStatus.done) {
        throw _Stop(
          state.wasBusy
              ? LinuxBackupProblem.busy
              : LinuxBackupProblem.jobFailed,
          state.tail,
        );
      }
      jobs.dismiss();
      final stage = await _safeDir(root.path, paths.stage);
      final parts = <ProjectExportEntry>[];
      for (final entry in linuxBackupEntries) {
        if (entry == 'manifest.json') continue;
        final file = File('${stage.path}/$entry');
        if (await FileSystemEntity.type(file.path, followLinks: false) ==
            FileSystemEntityType.file) {
          parts.add(
            ProjectExportEntry(
              name: entry,
              source: file.path,
              bytes: await file.length(),
            ),
          );
        }
      }
      final manifestFile = File('${stage.path}/manifest.json');
      await manifestFile.writeAsString(
        LinuxBackupManifest(
          kind: kind,
          created: _clock(),
          appVersion: await appVersion?.call() ?? '',
          abi: await abi?.call() ?? '',
          entries: [for (final p in parts) p.name],
        ).encode(),
      );
      parts.insert(
        0,
        ProjectExportEntry(
          name: 'manifest.json',
          source: manifestFile.path,
          bytes: await manifestFile.length(),
        ),
      );
      totalBytes = parts.fold(0, (n, p) => n + p.bytes);
      savedBytes = 0;
      final destination = await exporter.pickDestination(name);
      if (destination == null) {
        // Backed out of "Save to": nothing was saved, nothing to say.
        fileName = null;
        _go(LinuxBackupPhase.idle);
        return;
      }
      _go(LinuxBackupPhase.saving);
      plan = await writeExportPlan(await _tempDir(), parts);
      final result = await exporter.export(
        destination: destination,
        planPath: plan.path,
        onProgress: (bytes) {
          savedBytes = bytes;
          if (!_disposed) notifyListeners();
        },
      );
      if (!result.ok) {
        throw _Stop(LinuxBackupProblem.saveFailed, result.detail);
      }
      savedBytes = totalBytes;
      await prefs.setInt(linuxLastBackupKey, _clock().millisecondsSinceEpoch);
      _go(LinuxBackupPhase.saved);
    } on _Stop catch (stop) {
      _fail(stop.problem, stop.details);
    } on Object catch (error) {
      _fail(LinuxBackupProblem.saveFailed, '$error');
    } finally {
      if (plan != null && await plan.exists()) await plan.delete();
      // The parts go whatever happened, a failed job's half-made ones too;
      // never through a link.
      final at = stagePath;
      if (at != null &&
          await FileSystemEntity.type(at, followLinks: false) ==
              FileSystemEntityType.directory) {
        await Directory(at).delete(recursive: true);
      }
    }
  }

  /// Lets the person choose a backup and reads what it holds; then
  /// [restore] puts it back once they agree.
  Future<void> chooseBackup() async {
    if (busy) return;
    reset();
    final path = await pickBackupFile();
    if (path == null) return;
    _go(LinuxBackupPhase.checking);
    try {
      final entries = await readZipEntries(path);
      final entry = entries.where((e) => e.name == 'manifest.json').firstOrNull;
      if (entry == null || entry.size > 65536) {
        throw const _Stop(LinuxBackupProblem.notABackup);
      }
      final temp = File(
        '${(await _tempDir()).path}/oc-backup-manifest-'
        '${_clock().microsecondsSinceEpoch}.json',
      );
      await extractZipEntry(path, entry, temp);
      final text = await temp.readAsString();
      await temp.delete();
      final read = LinuxBackupManifest.decode(text);
      if (read == null) {
        throw _Stop(
          _newer(text)
              ? LinuxBackupProblem.newerBackup
              : LinuxBackupProblem.notABackup,
        );
      }
      manifest = read;
      _chosen = path;
      _go(LinuxBackupPhase.ready);
    } on _Stop catch (stop) {
      _fail(stop.problem, stop.details);
    } on Object catch (error) {
      _fail(LinuxBackupProblem.notABackup, '$error');
    }
  }

  static bool _newer(String text) {
    final m = RegExp(r'"format"\s*:\s*(\d+)').firstMatch(text);
    return m != null && (int.tryParse(m[1]!) ?? 0) > linuxBackupFormat;
  }

  /// Puts back the backup [chooseBackup] read.
  Future<void> restore() async {
    final path = _chosen;
    final read = manifest;
    if (path == null || read == null || busy) return;
    _go(LinuxBackupPhase.restoring);
    try {
      final root = await _root();
      final into = await _safeDir(root.path, paths.restore);
      final entries = await readZipEntries(path);
      for (final entry in entries) {
        // Only the names a backup holds, written by their own name: a path
        // inside the zip is never followed.
        if (!linuxBackupEntries.contains(entry.name)) continue;
        await extractZipEntry(path, entry, File('${into.path}/${entry.name}'));
      }
      final state = await _runJob(
        LinuxJobKind.restore,
        linuxRestoreJobScript(),
      );
      if (state.status != LinuxJobStatus.done) {
        throw _Stop(
          state.wasBusy
              ? LinuxBackupProblem.busy
              : LinuxBackupProblem.jobFailed,
          state.tail,
        );
      }
      summary = LinuxRestoreSummary.parse(state.tail);
      jobs.dismiss();
      await afterRestore?.call();
      _go(LinuxBackupPhase.restored);
    } on _Stop catch (stop) {
      _fail(stop.problem, stop.details);
    } on Object catch (error) {
      _fail(LinuxBackupProblem.jobFailed, '$error');
    }
  }

  @override
  void dispose() {
    _disposed = true;
    super.dispose();
  }
}

class _Stop implements Exception {
  const _Stop(this.problem, [this.details]);
  final LinuxBackupProblem problem;
  final String? details;
}
