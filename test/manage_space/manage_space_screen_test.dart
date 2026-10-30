// The manage-space page (Android App info › Storage › Manage space) and
// This phone's Export projects page: what clearing deletes with counts and
// sizes, export success and failure, cache only, and a delete that happens
// only after a confirm naming the counts.
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/project_export.dart';
import 'package:opencode_mobile/builtin/project_export_controller.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/manage_space_main.dart';
import 'package:opencode_mobile/state/profiles.dart' show AppAppearance;
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/screens/manage_space_screen.dart';

class FakeExportPlatform implements ProjectExportPlatform {
  String? destination = 'content://downloads/1';
  ProjectExportResult exportResult = const ProjectExportResult.done(
    bytes: 3 * 1024 * 1024,
    files: 42,
  );
  Completer<ProjectExportResult>? hold;
  final calls = <String>[];
  String? pickedName;
  int cache = 5 * 1024 * 1024;
  bool clearAccepted = true;

  @override
  Future<String?> pickDestination(String suggestedName) async {
    calls.add('pick');
    pickedName = suggestedName;
    return destination;
  }

  @override
  Future<ProjectExportResult> export({
    required String destination,
    required String planPath,
    required void Function(int bytesDone) onProgress,
  }) async {
    calls.add('export');
    onProgress(1024 * 1024);
    return hold?.future ?? exportResult;
  }

  @override
  Future<void> cancel() async => calls.add('cancel');

  @override
  Future<int> cacheBytes() async => cache;

  @override
  Future<int> clearCache() async {
    calls.add('clearCache');
    final freed = cache;
    cache = 0;
    return freed;
  }

  @override
  Future<bool> clearAllData() async {
    calls.add('clearAllData');
    return clearAccepted;
  }

  @override
  Future<String?> filesDir() async => '/data/files';

  @override
  Future<int> savedServers() async => 2;
}

const _facts = AppStorageFacts(
  projects: [
    ProjectFacts(
      name: 'shop',
      bytes: 2 * 1024 * 1024,
      files: 10,
      privateFiles: 1,
    ),
    ProjectFacts(name: 'blog', bytes: 1024 * 1024, files: 5, privateFiles: 0),
  ],
  serverInstalled: true,
  serverBytes: 900 * 1024 * 1024,
  privateDataBytes: 2048,
);

ProjectExportController controllerFor(
  FakeExportPlatform platform, {
  AppStorageFacts facts = _facts,
}) => ProjectExportController(
  platform: platform,
  scan: (_) async => facts,
  plan: (_, private) async => [
    const ProjectExportEntry(
      name: 'projects/shop/a',
      source: '/data/files/projects/shop/a',
      bytes: 3 * 1024 * 1024,
    ),
  ],
  writePlan: (_) async => '/nonexistent/plan',
  now: () => DateTime(2026, 9, 28),
);

Future<void> pumpPage(WidgetTester tester, ProjectExportController c) async {
  tester.view.physicalSize = const Size(1080, 2400);
  tester.view.devicePixelRatio = 2.625;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(ManageSpaceApp(controller: c, onClose: () {}));
  await tester.pumpAndSettle();
}

Future<void> tapText(WidgetTester tester, String text) async {
  await tester.ensureVisible(find.text(text).first);
  await tester.pumpAndSettle();
  await tester.tap(find.text(text).first);
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('says what clearing deletes, with sizes, and what stays', (
    tester,
  ) async {
    await pumpPage(tester, controllerFor(FakeExportPlatform()));
    expect(find.text("Clear this app's storage"), findsOneWidget);
    expect(find.textContaining('cannot be undone'), findsOneWidget);
    expect(find.text('Export projects first'), findsOneWidget);
    expect(
      find.text('2 projects, 3.0 MB, as one zip file where you choose'),
      findsOneWidget,
    );
    expect(
      find.text('Frees 5.0 MB. Projects, servers and settings stay.'),
      findsOneWidget,
    );
    expect(find.text('Delete everything'), findsOneWidget);
    await tester.scrollUntilVisible(
      find.text('Anything you pushed to git'),
      200,
    );
    expect(find.text('On this phone'), findsOneWidget);
    expect(find.text('900.0 MB'), findsOneWidget);
    expect(find.text('shop'), findsOneWidget);
    expect(find.text('2.0 MB'), findsOneWidget);
    expect(find.text('blog'), findsOneWidget);
    expect(find.text('2 saved servers'), findsOneWidget);
    expect(find.text('Termux and the projects in it'), findsOneWidget);
  });

  testWidgets('export writes a zip and says how much, credentials left out', (
    tester,
  ) async {
    final platform = FakeExportPlatform();
    await pumpPage(tester, controllerFor(platform));
    await tapText(tester, 'Export projects first');
    expect(platform.calls, ['pick', 'export']);
    expect(platform.pickedName, 'opencode-projects-2026-09-28.zip');
    expect(find.text('Projects exported: 3.0 MB in 42 files.'), findsOneWidget);
    expect(
      find.text('1 file with sign-ins or keys was left out.'),
      findsOneWidget,
    );
    // Nothing was deleted.
    expect(platform.calls, isNot(contains('clearAllData')));
  });

  testWidgets('a private export is opt-in and named private', (tester) async {
    final platform = FakeExportPlatform();
    await pumpPage(tester, controllerFor(platform));
    await tapText(tester, 'Include sign-ins and conversations');
    await tapText(tester, 'Export projects first');
    expect(platform.pickedName, 'opencode-projects-private-2026-09-28.zip');
    expect(
      find.text('This file holds sign-ins. Keep it private.'),
      findsOneWidget,
    );
  });

  testWidgets('export progress shows, and Stop cancels', (tester) async {
    final platform = FakeExportPlatform()
      ..hold = Completer<ProjectExportResult>();
    await pumpPage(tester, controllerFor(platform));
    await tapText(tester, 'Export projects first');
    expect(find.text('Exporting projects'), findsOneWidget);
    expect(find.text('1.0 MB of 3.0 MB'), findsOneWidget);
    await tapText(tester, 'Stop the export');
    expect(platform.calls, contains('cancel'));
    platform.hold!.complete(
      const ProjectExportResult.failed(ProjectExportFailure.cancelled),
    );
    await tester.pumpAndSettle();
    expect(find.text('Export stopped. Nothing was saved.'), findsOneWidget);
  });

  testWidgets('a failed export says what failed in words, with Try again', (
    tester,
  ) async {
    final platform = FakeExportPlatform()
      ..exportResult = const ProjectExportResult.failed(
        ProjectExportFailure.space,
        'IOException',
      );
    await pumpPage(tester, controllerFor(platform));
    await tapText(tester, 'Export projects first');
    expect(
      find.text(
        'The place you chose is full. Free some space there or pick another place.',
      ),
      findsOneWidget,
    );
    expect(find.text('IOException'), findsNothing);
    platform.exportResult = const ProjectExportResult.done(bytes: 10, files: 1);
    await tapText(tester, 'Try again');
    expect(find.text('Projects exported: 10 B in 1 files.'), findsOneWidget);
  });

  testWidgets('backing out of the picker changes nothing', (tester) async {
    final platform = FakeExportPlatform()..destination = null;
    await pumpPage(tester, controllerFor(platform));
    await tapText(tester, 'Export projects first');
    expect(platform.calls, ['pick']);
    expect(find.text('Export projects first'), findsOneWidget);
  });

  testWidgets('clearing the cache only clears the cache', (tester) async {
    final platform = FakeExportPlatform();
    await pumpPage(tester, controllerFor(platform));
    await tapText(tester, "Clear the app's cache only");
    expect(platform.calls, ['clearCache']);
    expect(find.text('Cache cleared. 5.0 MB freed.'), findsOneWidget);
  });

  testWidgets('delete asks first, naming the counts; cancel deletes nothing', (
    tester,
  ) async {
    final platform = FakeExportPlatform();
    await pumpPage(tester, controllerFor(platform));
    await tapText(tester, 'Delete everything');
    expect(find.text('Delete everything?'), findsOneWidget);
    expect(find.text('2 projects (3.0 MB)'), findsOneWidget);
    expect(find.text('2 saved servers and all settings'), findsOneWidget);
    expect(find.text('On this phone and its conversations'), findsOneWidget);
    expect(platform.calls, isEmpty);
    await tapText(tester, 'Cancel');
    expect(platform.calls, isEmpty);

    await tapText(tester, 'Delete everything');
    await tester.tap(find.byKey(const ValueKey('manage-space-delete-confirm')));
    await tester.pumpAndSettle();
    expect(platform.calls, ['clearAllData']);
  });

  testWidgets('no projects: no export row, delete still names what goes', (
    tester,
  ) async {
    final platform = FakeExportPlatform();
    await pumpPage(
      tester,
      controllerFor(platform, facts: AppStorageFacts.empty),
    );
    expect(find.text('Export projects first'), findsNothing);
    expect(find.text('Delete everything'), findsOneWidget);
  });

  // Emulator QA F7: it said "Export your projects first" with no Export
  // on the page, sat against the window's edge, and was light in a dark app.
  testWidgets('nothing to export: the warning does not send to Export', (
    tester,
  ) async {
    await pumpPage(
      tester,
      controllerFor(FakeExportPlatform(), facts: AppStorageFacts.empty),
    );
    expect(find.textContaining('cannot be undone'), findsOneWidget);
    expect(find.textContaining('Export your projects'), findsNothing);
  });

  testWidgets('with projects, the warning names Export and the row is next', (
    tester,
  ) async {
    await pumpPage(tester, controllerFor(FakeExportPlatform()));
    final warning = find.textContaining('Export your projects first');
    expect(warning, findsOneWidget);
    final row = find.text('Export projects first');
    expect(
      tester.getTopLeft(row).dy,
      greaterThan(tester.getBottomLeft(warning).dy),
    );
  });

  testWidgets('the notices sit on the page gutter, not the window edge', (
    tester,
  ) async {
    final platform = FakeExportPlatform();
    await pumpPage(tester, controllerFor(platform));
    await tapText(tester, "Clear the app's cache only");
    for (final key in ['manage-space-intro', 'manage-space-cache-cleared']) {
      final notice = tester.getRect(find.byKey(ValueKey(key)));
      expect(notice.left, greaterThanOrEqualTo(16), reason: key);
      expect(
        notice.right,
        lessThanOrEqualTo(
          tester.view.physicalSize.width / tester.view.devicePixelRatio - 16,
        ),
        reason: key,
      );
    }
  });

  testWidgets("follows the app's saved appearance, dark by default", (
    tester,
  ) async {
    Future<Brightness> brightness(ManageSpaceApp app) async {
      // A fresh page each time (the page loads its controller once).
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pumpWidget(app);
      await tester.pumpAndSettle();
      return Theme.of(
        tester.element(find.byType(ManageSpaceScreen)),
      ).brightness;
    }

    final c = controllerFor(FakeExportPlatform());
    expect(
      await brightness(ManageSpaceApp(controller: c, onClose: () {})),
      Brightness.dark,
    );
    expect(
      await brightness(
        ManageSpaceApp(
          controller: controllerFor(FakeExportPlatform()),
          onClose: () {},
          appearance: AppAppearance.light,
        ),
      ),
      Brightness.light,
    );
    tester.platformDispatcher.platformBrightnessTestValue = Brightness.dark;
    addTearDown(tester.platformDispatcher.clearPlatformBrightnessTestValue);
    expect(
      await brightness(
        ManageSpaceApp(
          controller: controllerFor(FakeExportPlatform()),
          onClose: () {},
          appearance: AppAppearance.system,
        ),
      ),
      Brightness.dark,
    );
  });

  testWidgets('This phone export page lists projects and exports', (
    tester,
  ) async {
    final platform = FakeExportPlatform();
    tester.view.physicalSize = const Size(1080, 2400);
    tester.view.devicePixelRatio = 2.625;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(
      MaterialApp(
        theme: AppTheme.light(),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: ProjectExportScreen(controller: controllerFor(platform)),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Export projects'), findsOneWidget);
    expect(find.text('shop'), findsOneWidget);
    await tapText(tester, 'Save as a zip file');
    expect(platform.calls, ['pick', 'export']);
    expect(find.text('Projects exported: 3.0 MB in 42 files.'), findsOneWidget);
  });
}
