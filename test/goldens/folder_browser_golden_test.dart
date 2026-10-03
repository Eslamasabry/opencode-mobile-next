// Golden renders of "Open a project" for OpenCode inside the app, the
// folder browser (lib/ui/widgets/folder_browser.dart, design standard §6
// and §10), rebuilt from kit parts by revamp unit shared-work-1: at the
// projects folder, inside a folder, loading, a slow read (past 8 s), the
// empty projects folder (first run) and a folder that cannot be shown, on
// a phone (412x915) and a wide window (1280x800), dark and light, with the
// app's real fonts; and its drawing, a folder opening, on its own.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/folder_browser_golden_test.dart
// and look at every changed image before committing it.
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/builtin_folders.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/kit/scenes/folders_open_scene.dart';
import 'package:opencode_mobile/ui/widgets/folder_browser.dart';

import '../../tool/capture/fixtures.dart' show captureTheme, loadCaptureFonts;

enum FolderBrowserScene { projects, inside, loading, slow, empty, error }

List<FolderEntry> _entries(String path, List<(String, bool)> names) => [
  for (final (name, git) in names)
    FolderEntry(name: name, path: '$path/$name', isGit: git),
];

FolderLister _lister(FolderBrowserScene scene) => switch (scene) {
  FolderBrowserScene.projects => (path) async => _entries(path, [
    ('demo', true),
    ('landing-page', true),
    ('notes', false),
    ('opencode-mobile', true),
    ('scratch', false),
  ]),
  FolderBrowserScene.inside => (path) async => _entries(path, [
    ('android', false),
    ('assets', false),
    ('docs', false),
    ('lib', false),
    ('packages', false),
    ('test', false),
  ]),
  FolderBrowserScene.loading ||
  FolderBrowserScene.slow => (_) => Completer<List<FolderEntry>>().future,
  FolderBrowserScene.empty => (_) async => const [],
  FolderBrowserScene.error => (path) async => throw FolderListException(
    FolderListProblem.denied,
    "PathAccessException: Directory listing failed, path = '$path' "
    '(OS Error: Permission denied, errno = 13)',
  ),
};

/// Mounts the sheet over an empty screen, as the Work tab shows it.
Future<void> mountFolderBrowser(
  WidgetTester tester,
  FolderBrowserScene scene, {
  required bool light,
  required GlobalKey boundary,
  Size size = const Size(412, 915),
  double textScale = 1,
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  final start = scene == FolderBrowserScene.inside
      ? '/root/projects/opencode-mobile'
      : '/root/projects';
  await tester.pumpWidget(
    RepaintBoundary(
      key: boundary,
      child: MaterialApp(
        debugShowCheckedModeBanner: false,
        theme: captureTheme(light: light),
        locale: const Locale('en'),
        supportedLocales: AppLocalizations.supportedLocales,
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(
            context,
          ).copyWith(textScaler: TextScaler.linear(textScale)),
          child: child!,
        ),
        home: Builder(
          builder: (context) {
            WidgetsBinding.instance.addPostFrameCallback((_) {
              showModalBottomSheet<FolderBrowserChoice>(
                context: context,
                isScrollControlled: true,
                showDragHandle: true,
                builder: (_) => FolderBrowserSheet(
                  list: _lister(scene),
                  knownProjects: () async => {'/root/projects/demo'},
                  start: start,
                ),
              );
            });
            return const Scaffold();
          },
        ),
      ),
    ),
  );
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 400));
  // The sheet opens on its start page; these scenes are the browser.
  await tester.tap(find.byKey(const ValueKey('open-project-browse')));
  await tester.pump();
  if (scene == FolderBrowserScene.loading) {
    // The sheet slides up; the skeleton shows once listing is slow.
    await tester.pump(const Duration(seconds: 1));
  } else if (scene == FolderBrowserScene.slow) {
    // Past KitMotion.escalateAfter the sheet says it is still reading.
    await tester.pump(const Duration(seconds: 1));
    await tester.pump(const Duration(seconds: 8));
    await tester.pump(const Duration(seconds: 1));
  } else {
    await tester.pumpAndSettle();
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);

  const sizes = {'': Size(412, 915), '_wide': Size(1280, 800)};
  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';
    for (final MapEntry(key: suffix, value: size) in sizes.entries) {
      for (final scene in FolderBrowserScene.values) {
        final name = 'folder_browser_${scene.name}$suffix';
        testWidgets('$name · $mode', (tester) async {
          final boundary = GlobalKey();
          await mountFolderBrowser(
            tester,
            scene,
            light: light,
            boundary: boundary,
            size: size,
          );
          expect(tester.takeException(), isNull);
          await expectLater(
            find.byKey(boundary),
            matchesGoldenFile('${name}_$mode.png'),
          );
        });
      }
    }

    testWidgets('kit_folders_open · $mode', (tester) async {
      final theme = light ? AppTheme.light() : AppTheme.dark();
      await tester.pumpWidget(
        MaterialApp(
          theme: theme,
          home: Scaffold(
            body: Center(
              child: RepaintBoundary(
                key: const ValueKey('scene'),
                child: ColoredBox(
                  color: theme.colorScheme.surface,
                  child: const Padding(
                    padding: EdgeInsets.all(16),
                    child: KitIllustration(scene: KitFoldersOpenScene()),
                  ),
                ),
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      await expectLater(
        find.byKey(const ValueKey('scene')),
        matchesGoldenFile('kit_folders_open_$mode.png'),
      );
    });
  }
}
