// Linux › Backups (lib/ui/screens/linux_manager/linux_backups_page.dart):
// what to back up, the primary that names it, "Projects only" opening the
// former Export projects, the restore row, and every way it ends, in words.

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/builtin_server.dart';
import 'package:opencode_mobile/builtin/linux_backup/linux_backup.dart';
import 'package:opencode_mobile/builtin/linux_backup/linux_backup_plan.dart';
import 'package:opencode_mobile/builtin/linux_manager/linux_manager.dart';
import 'package:opencode_mobile/builtin/project_export.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/screens/linux_manager/linux_backups_page.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _Exporter implements ProjectExportPlatform {
  @override
  Future<String?> pickDestination(String suggestedName) async => null;
  @override
  Future<ProjectExportResult> export({
    required String destination,
    required String planPath,
    required void Function(int bytesDone) onProgress,
  }) async => const ProjectExportResult.done(bytes: 0, files: 0);
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

/// Shows any state the page must draw; records what was asked of it.
class _Controller extends LinuxBackupController {
  _Controller(SharedPreferences prefs)
    : super(
        jobs: LinuxJobController(LinuxManager(BuiltinLinux())),
        exporter: _Exporter(),
        prefs: prefs,
        notice: (_) => '',
        pickBackupFile: () async => null,
      );

  final asked = <String>[];

  void show(LinuxBackupPhase next, {LinuxBackupProblem? why}) {
    phase = next;
    problem = why;
    notifyListeners();
  }

  @override
  Future<void> backUp(LinuxBackupKind kind) async => asked.add(kind.name);

  @override
  Future<void> chooseBackup() async => asked.add('choose');
}

class _Linux extends BuiltinLinux {
  @override
  Future<BuiltinLinuxStatus> status() async =>
      const BuiltinLinuxStatus(installed: true, phase: BuiltinLinuxPhase.ready);
}

void main() {
  late _Controller c;
  var projects = 0;

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    c = _Controller(await SharedPreferences.getInstance());
    projects = 0;
  });

  Future<void> mount(WidgetTester tester, {String locale = 'en'}) async {
    tester.view.physicalSize = const Size(412, 915);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [builtinLinuxProvider.overrideWithValue(_Linux())],
        child: MaterialApp(
          locale: Locale(locale),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: LinuxBackupsPage(
            controller: c,
            openProjects: (_) async => projects++,
          ),
        ),
      ),
    );
    await tester.pump(const Duration(milliseconds: 300));
  }

  testWidgets('three choices; the primary names the one chosen', (
    tester,
  ) async {
    await mount(tester);
    expect(find.text('Setup'), findsOneWidget);
    expect(find.text('Setup and projects'), findsOneWidget);
    expect(find.text('Projects only'), findsOneWidget);
    expect(find.text('Back up setup and projects'), findsOneWidget);
    expect(
      find.textContaining('never hold sign-ins, passwords or private SSH keys'),
      findsOneWidget,
    );
    await tester.tap(find.text('Back up setup and projects'));
    await tester.pump();
    expect(c.asked, ['setupAndProjects']);
    await tester.tap(find.text('Setup'));
    await tester.pump(const Duration(milliseconds: 300));
    expect(find.text('Back up setup'), findsOneWidget);
    await tester.tap(find.text('Back up setup'));
    await tester.pump();
    expect(c.asked.last, 'setup');
  });

  testWidgets('Projects only opens the projects export', (tester) async {
    await mount(tester);
    await tester.tap(find.text('Projects only'));
    await tester.pump(const Duration(milliseconds: 300));
    await tester.tap(find.text('Back up projects'));
    await tester.pump();
    expect(projects, 1);
    expect(c.asked, isEmpty);
  });

  testWidgets('Restore from a backup is a visible row', (tester) async {
    await mount(tester);
    await tester.tap(find.text('Restore from a backup'));
    await tester.pump();
    expect(c.asked, ['choose']);
  });

  testWidgets('how it ends, in words', (tester) async {
    await mount(tester);
    c
      ..fileName = 'opencode-linux-setup-2026-11-01.zip'
      ..totalBytes = 39845888
      ..show(LinuxBackupPhase.saved);
    await tester.pump(const Duration(milliseconds: 300));
    expect(
      find.text(
        'Backup saved: 38.0 MB, as opencode-linux-setup-2026-11-01.zip',
      ),
      findsOneWidget,
    );
    c
      ..summary = const LinuxRestoreSummary(
        counts: {'projects': 2, 'services': 1, 'settings': 5, 'packages': 3},
        skipped: ['gone-pkg'],
      )
      ..show(LinuxBackupPhase.restored);
    await tester.pump(const Duration(milliseconds: 300));
    expect(find.text('Linux is restored.'), findsOneWidget);
    expect(
      find.text('Back: projects 2, services 1, settings files 5, packages 3'),
      findsOneWidget,
    );
    expect(find.text('Ubuntu no longer has: gone-pkg'), findsOneWidget);
    c.show(LinuxBackupPhase.failed, why: LinuxBackupProblem.newerBackup);
    await tester.pump(const Duration(milliseconds: 300));
    expect(
      find.text(
        'That backup was made by a newer version of the app. Update the app '
        'first.',
      ),
      findsOneWidget,
    );
    // The phone's own words never show; only under Details.
    c
      ..details = 'tar: Cannot open: No such file'
      ..show(LinuxBackupPhase.failed, why: LinuxBackupProblem.saveFailed);
    await tester.pump(const Duration(milliseconds: 300));
    expect(find.textContaining('tar:'), findsNothing);
    expect(
      find.text(
        "The backup couldn't be saved there. There may not be enough room.",
      ),
      findsOneWidget,
    );
  });

  testWidgets('nothing can start while one runs', (tester) async {
    await mount(tester);
    c.show(LinuxBackupPhase.preparing);
    await tester.pump(const Duration(milliseconds: 300));
    await tester.tap(find.text('Back up setup and projects'));
    await tester.tap(find.text('Restore from a backup'));
    await tester.pump();
    expect(c.asked, isEmpty);
  });

  testWidgets('Arabic', (tester) async {
    await mount(tester, locale: 'ar');
    expect(find.text('النسخ الاحتياطية'), findsOneWidget);
    expect(find.text('المشاريع فقط'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
