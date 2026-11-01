// Backing up and restoring Linux, Dart side (lib/builtin/linux_backup):
// the pure-Dart zip reader on zips Python writes (stored and deflated), and
// the controller over a temporary rootfs with a fake job, a fake zip writer
// and a fake picker: what goes into the saved zip, the manifest, the
// last-backup stamp, the clean-up, every way it can end, and a restore that
// never extracts a name a backup does not hold.

@TestOn('linux')
library;

import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/linux_backup/linux_backup.dart';
import 'package:opencode_mobile/builtin/linux_backup/linux_backup_plan.dart';
import 'package:opencode_mobile/builtin/linux_backup/zip_reader.dart';
import 'package:opencode_mobile/builtin/linux_manager/linux_manager.dart';
import 'package:opencode_mobile/builtin/linux_manager/linux_packages.dart';
import 'package:opencode_mobile/builtin/project_export.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Writes a zip with Python's zipfile: [files] name → text; deflated unless
/// the name ends with `.stored`.
Future<void> _zip(String path, Map<String, String> files) async {
  final script = StringBuffer(
    'import zipfile,sys,json\n'
    'files=json.loads(sys.argv[2])\n'
    'with zipfile.ZipFile(sys.argv[1],"w") as z:\n'
    '  for n,t in files.items():\n'
    '    z.writestr(n,t,compress_type=zipfile.ZIP_STORED if n.endswith(".stored") else zipfile.ZIP_DEFLATED)\n',
  );
  final r = await Process.run('python3', [
    '-c',
    script.toString(),
    path,
    jsonEncode(files),
  ]);
  expect(r.exitCode, 0, reason: '${r.stderr}');
}

/// The job: on start it does what the script would (writes the stage, or
/// reads the restore folder), then ends as [ending] says.
class _Jobs extends LinuxJobController {
  _Jobs(this.onStart) : super(LinuxManager(BuiltinLinux()));

  final Future<String> Function(LinuxJobKind kind) onStart;
  LinuxJobStatus ending = LinuxJobStatus.done;
  final started = <LinuxJobKind>[];
  LinuxJobState _now = LinuxJobState.none;
  int dismissed = 0;

  @override
  LinuxJobState get state => _now;

  @override
  Future<void> startScript(
    LinuxJobKind kind,
    String script, {
    required String notice,
  }) async {
    started.add(kind);
    _now = LinuxJobState(kind: kind, status: LinuxJobStatus.running);
    notifyListeners();
    final tail = await onStart(kind);
    Future<void>.microtask(() {
      _now = LinuxJobState(
        kind: kind,
        status: ending,
        exitCode: ending == LinuxJobStatus.done ? 0 : 1,
        tail: tail,
      );
      notifyListeners();
    });
  }

  @override
  void dismiss() => dismissed++;
}

/// Counts "Save to" questions, otherwise [inner].
class _CountingExporter extends _Exporter {
  _CountingExporter(this.inner, this.onPick);
  final _Exporter inner;
  final void Function() onPick;
  @override
  Future<String?> pickDestination(String suggestedName) async {
    onPick();
    return inner.pickDestination(suggestedName);
  }
}

class _Exporter implements ProjectExportPlatform {
  String? destination = 'content://downloads/backup.zip';
  ProjectExportResult result = const ProjectExportResult.done(
    bytes: 1,
    files: 1,
  );
  final plans = <List<String>>[];
  final sources = <String, String>{};

  @override
  Future<String?> pickDestination(String suggestedName) async => destination;

  @override
  Future<ProjectExportResult> export({
    required String destination,
    required String planPath,
    required void Function(int bytesDone) onProgress,
  }) async {
    final lines = File(planPath).readAsLinesSync();
    plans.add(lines);
    for (final line in lines) {
      final [name, source] = line.split('\t');
      sources[name] = File(source).readAsStringSync();
    }
    onProgress(1);
    return result;
  }

  @override
  Future<void> cancel() async {}
  @override
  Future<int> clearCache() async => 0;
  @override
  Future<int> cacheBytes() async => 0;
  @override
  Future<bool> clearAllData() async => false;
  @override
  Future<String?> filesDir() async => null;
  @override
  Future<int> savedServers() async => 0;
}

void main() {
  late Directory tmp;
  late Directory rootfs;
  late Directory temp;

  setUp(() async {
    tmp = await Directory.systemTemp.createTemp('oc-backup-dart-');
    rootfs = Directory('${tmp.path}/ubuntu')..createSync();
    Directory('${rootfs.path}/root').createSync();
    temp = Directory('${tmp.path}/cache')..createSync();
    SharedPreferences.setMockInitialValues({});
  });

  tearDown(() => tmp.delete(recursive: true));

  group('zip reader', () {
    test('stored and deflated entries come out as they went in', () async {
      final path = '${tmp.path}/a.zip';
      final big = List.generate(5000, (i) => 'line $i').join('\n');
      await _zip(path, {
        'manifest.json': '{"a":1}',
        'big.txt': big,
        'x.stored': 'raw',
      });
      final entries = await readZipEntries(path);
      expect(entries.map((e) => e.name), [
        'manifest.json',
        'big.txt',
        'x.stored',
      ]);
      expect(entries[1].method, 8);
      expect(entries[2].method, 0);
      for (final (i, text) in [(1, big), (2, 'raw')]) {
        final out = File('${tmp.path}/out$i');
        await extractZipEntry(path, entries[i], out);
        expect(out.readAsStringSync(), text);
      }
    });

    test('not a zip is refused', () async {
      final path = '${tmp.path}/x.zip';
      File(path).writeAsStringSync('hello, this is not a zip file at all');
      await expectLater(
        readZipEntries(path),
        throwsA(isA<ZipFormatException>()),
      );
    });
  });

  Future<LinuxBackupController> controller({
    required _Jobs jobs,
    required _Exporter exporter,
    String? picked,
    VoidCallback? onRestore,
  }) async => LinuxBackupController(
    jobs: jobs,
    exporter: exporter,
    prefs: await SharedPreferences.getInstance(),
    notice: (_) => 'Working',
    pickBackupFile: () async => picked,
    afterRestore: () async => onRestore?.call(),
    appVersion: () async => '2.1.0',
    abi: () async => 'arm64-v8a',
    locateRootfs: () async => rootfs,
    tempDir: () async => temp,
    clock: () => DateTime.utc(2026, 11, 1, 9),
  );

  String stage(String name) =>
      '${rootfs.path}/root/.oc-builtin/backup/stage/$name';

  _Jobs backupJob() => _Jobs((kind) async {
    for (final name in ['home.tar.gz', 'services.tar.gz', 'apt-manual.txt']) {
      File(stage(name))
        ..parent.createSync(recursive: true)
        ..writeAsStringSync('part $name');
    }
    return '::oc backup-size 3\n';
  });

  group('back up', () {
    test('the zip holds the manifest first, then every part; the stage is '
        'cleaned and the time remembered', () async {
      final exporter = _Exporter();
      final c = await controller(jobs: backupJob(), exporter: exporter);
      await c.backUp(LinuxBackupKind.setup);
      expect(
        c.phase,
        LinuxBackupPhase.saved,
        reason: '${c.problem} ${c.details}',
      );
      expect(c.fileName, 'opencode-linux-setup-2026-11-01.zip');
      final names = [
        for (final l in exporter.plans.single) l.split('\t').first,
      ];
      expect(names.first, 'manifest.json');
      expect(
        names,
        containsAll(['home.tar.gz', 'services.tar.gz', 'apt-manual.txt']),
      );
      final m = LinuxBackupManifest.decode(exporter.sources['manifest.json']!)!;
      expect(m.kind, LinuxBackupKind.setup);
      expect(m.appVersion, '2.1.0');
      expect(m.abi, 'arm64-v8a');
      expect(m.entries, containsAll(['home.tar.gz', 'services.tar.gz']));
      expect(c.lastBackupAt, DateTime.utc(2026, 11, 1, 9).toLocal());
      expect(Directory(stage('')).existsSync(), isFalse);
      expect(temp.listSync(), isEmpty, reason: 'the plan file is removed');
    });

    test('backing out of "Save to" saves nothing and leaves nothing', () async {
      final exporter = _Exporter()..destination = null;
      final c = await controller(jobs: backupJob(), exporter: exporter);
      await c.backUp(LinuxBackupKind.setup);
      expect(c.phase, LinuxBackupPhase.idle);
      expect(exporter.plans, isEmpty);
      expect(c.lastBackupAt, isNull);
      expect(Directory(stage('')).existsSync(), isFalse);
    });

    test('a failed job asks nowhere to save and leaves no parts', () async {
      final exporter = _Exporter();
      var asked = 0;
      final jobs = backupJob()..ending = LinuxJobStatus.failed;
      final c = await controller(
        jobs: jobs,
        exporter: _CountingExporter(exporter, () => asked++),
      );
      await c.backUp(LinuxBackupKind.setup);
      expect(c.problem, LinuxBackupProblem.jobFailed);
      expect(asked, 0, reason: 'no empty file is ever created');
      expect(Directory(stage('')).existsSync(), isFalse);
    });

    test('a failed job and a failed save are told apart', () async {
      final jobs = backupJob()..ending = LinuxJobStatus.failed;
      final exporter = _Exporter();
      final c = await controller(jobs: jobs, exporter: exporter);
      await c.backUp(LinuxBackupKind.setup);
      expect(c.phase, LinuxBackupPhase.failed);
      expect(c.problem, LinuxBackupProblem.jobFailed);
      expect(exporter.plans, isEmpty);
      expect(c.lastBackupAt, isNull);
      final saving = _Exporter()
        ..result = const ProjectExportResult.failed(
          ProjectExportFailure.failed,
          'no space',
        );
      final c2 = await controller(jobs: backupJob(), exporter: saving);
      await c2.backUp(LinuxBackupKind.setup);
      expect(c2.problem, LinuxBackupProblem.saveFailed);
      expect(c2.details, 'no space');
      expect(Directory(stage('')).existsSync(), isFalse);
    });
  });

  group('restore', () {
    String restored(String name) =>
        '${rootfs.path}/root/.oc-builtin/restore/$name';

    test('reads the backup, asks, extracts only backup names, restores, '
        'then the services start', () async {
      final zip = '${tmp.path}/backup.zip';
      await _zip(zip, {
        'manifest.json': LinuxBackupManifest(
          kind: LinuxBackupKind.setupAndProjects,
          created: DateTime.utc(2026, 10, 30),
          entries: const ['home.tar.gz', 'projects.tar.gz'],
        ).encode(),
        'home.tar.gz': 'home',
        'projects.tar.gz': 'projects',
        '../../evil.sh': 'bad',
        'other.txt': 'no',
      });
      var seenByJob = <String>[];
      final jobs = _Jobs((kind) async {
        seenByJob = [
          for (final f in Directory(restored('')).listSync())
            f.path.split('/').last,
        ]..sort();
        return '::oc restored projects 2\n::oc restored services 1\n'
            '::oc skipped gone-pkg\n';
      });
      var afterRestore = 0;
      final c = await controller(
        jobs: jobs,
        exporter: _Exporter(),
        picked: zip,
        onRestore: () => afterRestore++,
      );
      await c.chooseBackup();
      expect(c.phase, LinuxBackupPhase.ready);
      expect(c.manifest!.hasProjects, isTrue);
      expect(jobs.started, isEmpty, reason: 'nothing happens before yes');
      await c.restore();
      expect(c.phase, LinuxBackupPhase.restored, reason: '${c.details}');
      expect(seenByJob, ['home.tar.gz', 'manifest.json', 'projects.tar.gz']);
      expect(File('${tmp.path}/evil.sh').existsSync(), isFalse);
      expect(c.summary!.counts, {'projects': 2, 'services': 1});
      expect(c.summary!.skipped, ['gone-pkg']);
      expect(afterRestore, 1);
    });

    test('a zip that is not a backup, or a newer one, is refused', () async {
      final plain = '${tmp.path}/plain.zip';
      await _zip(plain, {'readme.txt': 'hi'});
      final c = await controller(
        jobs: backupJob(),
        exporter: _Exporter(),
        picked: plain,
      );
      await c.chooseBackup();
      expect(c.problem, LinuxBackupProblem.notABackup);
      final newer = '${tmp.path}/newer.zip';
      await _zip(newer, {
        'manifest.json': '{"format": 9, "kind": "setup", "created": "x"}',
      });
      final c2 = await controller(
        jobs: backupJob(),
        exporter: _Exporter(),
        picked: newer,
      );
      await c2.chooseBackup();
      expect(c2.problem, LinuxBackupProblem.newerBackup);
      final c3 = await controller(
        jobs: backupJob(),
        exporter: _Exporter(),
        picked: '${tmp.path}/missing.zip',
      );
      await c3.chooseBackup();
      expect(c3.problem, LinuxBackupProblem.notABackup);
    });

    test('a link where the restore folder goes is refused', () async {
      final zip = '${tmp.path}/backup.zip';
      await _zip(zip, {
        'manifest.json': LinuxBackupManifest(
          kind: LinuxBackupKind.setup,
          created: DateTime.utc(2026, 10, 30),
        ).encode(),
        'home.tar.gz': 'home',
      });
      final outside = Directory('${tmp.path}/outside')..createSync();
      Directory('${rootfs.path}/root/.oc-builtin').createSync();
      Link(restored('').replaceAll(RegExp(r'/$'), '')).createSync(outside.path);
      final jobs = backupJob();
      final c = await controller(
        jobs: jobs,
        exporter: _Exporter(),
        picked: zip,
      );
      await c.chooseBackup();
      await c.restore();
      expect(c.phase, LinuxBackupPhase.failed);
      expect(outside.listSync(), isEmpty);
      expect(jobs.started, isEmpty);
    });
  });

  group('device settings of Linux', () {
    test(
      'removing OpenCode forgets every oc.linux key and nothing else',
      () async {
        SharedPreferences.setMockInitialValues({
          linuxLastBackupKey: 1700000000000,
          'oc.linux.other': 'x',
          // A reset that is still being finished must survive: it is not a
          // device setting of Linux, and its name only looks similar.
          'oc.linuxReset.pending': 'erasing 1',
          'oc.replySpeed': 5,
          'oc.draft.phone': 'kept',
        });
        final prefs = await SharedPreferences.getInstance();

        expect(await forgetLinuxDevicePreferences(prefs), isTrue);

        expect(prefs.getInt(linuxLastBackupKey), isNull);
        expect(prefs.containsKey('oc.linux.other'), isFalse);
        expect(prefs.getString('oc.linuxReset.pending'), 'erasing 1');
        expect(prefs.getInt('oc.replySpeed'), 5);
        expect(prefs.getString('oc.draft.phone'), 'kept');
      },
    );

    test('with nothing saved it changes nothing and says so', () async {
      SharedPreferences.setMockInitialValues({'oc.draft.phone': 'kept'});
      final prefs = await SharedPreferences.getInstance();
      expect(await forgetLinuxDevicePreferences(prefs), isTrue);
      expect(prefs.getKeys(), {'oc.draft.phone'});
    });

    test('the last-backup key lives under the reserved prefix', () {
      expect(linuxLastBackupKey, startsWith(linuxDevicePreferencePrefix));
      expect(linuxDevicePreferencePrefix, 'oc.linux.');
    });
  });
}
