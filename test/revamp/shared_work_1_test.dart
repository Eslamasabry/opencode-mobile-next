// Revamp unit shared-work-1: "Open a project" (project-folder-browser) and
// the AI Team's door on Work (embedded-team-discover), rebuilt from kit
// parts. These tests cover what the map record asked the folder browser to
// gain: a word when a read is slow, a first step in an empty projects
// folder, and a way up from a folder the app may not read.
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/builtin_folders.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/kit/kit_icon_button.dart';
import 'package:opencode_mobile/ui/widgets/folder_browser.dart';

final _en = lookupAppLocalizations(const Locale('en'));

Finder _key(String key) => find.byKey(ValueKey(key));

/// Opens the sheet the way `ProjectFolderActions.openFolder` does and keeps
/// what it was closed with in [result].
Future<void> _open(
  WidgetTester tester,
  FolderLister list, {
  String start = '/root/projects',
  void Function(FolderBrowserChoice?)? onClosed,
}) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    MaterialApp(
      locale: const Locale('en'),
      supportedLocales: AppLocalizations.supportedLocales,
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      home: Builder(
        builder: (context) {
          WidgetsBinding.instance.addPostFrameCallback((_) async {
            final choice = await showModalBottomSheet<FolderBrowserChoice>(
              context: context,
              isScrollControlled: true,
              showDragHandle: true,
              builder: (_) => FolderBrowserSheet(list: list, start: start),
            );
            onClosed?.call(choice);
          });
          return const Scaffold();
        },
      ),
    ),
  );
  await tester.pump();
}

List<FolderEntry> _folders(String path, List<String> names) => [
  for (final name in names)
    FolderEntry(name: name, path: '$path/$name', isGit: false),
];

void main() {
  testWidgets('a slow read says so after 8 s, not before', (tester) async {
    final pending = Completer<List<FolderEntry>>();
    await _open(tester, (_) => pending.future);
    await tester.pump(const Duration(seconds: 1));
    expect(find.text(_en.folderBrowserSlowTitle), findsNothing);
    await tester.pump(const Duration(seconds: 8));
    expect(find.text(_en.folderBrowserSlowTitle), findsOneWidget);
    expect(find.text(_en.folderBrowserSlowBody), findsOneWidget);
    // The way out stays in the header's menu while it waits.
    await tester.tap(_key('kit-sheet-menu'));
    await tester.pumpAndSettle();
    expect(find.text(_en.projectFolderEnterPath), findsOneWidget);
    await tester.tapAt(const Offset(2, 2));
    await tester.pumpAndSettle();

    pending.complete(_folders('/root/projects', ['demo']));
    await tester.pumpAndSettle();
    expect(find.text(_en.folderBrowserSlowTitle), findsNothing);
    expect(find.text('demo'), findsOneWidget);
  });

  testWidgets('an empty projects folder offers its first step', (tester) async {
    await _open(tester, (_) async => const []);
    await tester.pumpAndSettle();
    expect(find.text(_en.folderBrowserNoProjectsTitle), findsOneWidget);
    // At the projects folder there is nothing to open as it is.
    expect(_key('folder-browser-open'), findsNothing);

    await tester.tap(_key('folder-browser-first-project'));
    await tester.pumpAndSettle();
    // The first step opens the name dialog.
    expect(_key('phone-new-folder-name'), findsOneWidget);
  });

  testWidgets('a folder the app may not read is left by going up', (
    tester,
  ) async {
    await _open(tester, (path) async {
      if (path == '/root/projects/secret') {
        throw FolderListException(
          FolderListProblem.denied,
          'Permission denied',
        );
      }
      return _folders(path, ['secret', 'notes']);
    });
    await tester.pumpAndSettle();
    await tester.tap(_key('folder-browse-secret'));
    await tester.pumpAndSettle();
    expect(find.text(_en.folderBrowserErrorDenied), findsOneWidget);

    // The header's back chevron is the way out; no second "Up" in the body.
    await tester.tap(_key('folder-browser-up'));
    await tester.pumpAndSettle();
    expect(find.text(_en.folderBrowserErrorTitle), findsNothing);
    expect(find.text('notes'), findsOneWidget);
  });

  testWidgets('a project opens with a tap; its chevron shows inside', (
    tester,
  ) async {
    FolderBrowserChoice? closed;
    await _open(
      tester,
      (path) async => path == '/root/projects'
          ? _folders(path, ['demo'])
          : _folders(path, ['lib']),
      onClosed: (choice) => closed = choice,
    );
    await tester.pumpAndSettle();
    // The chevron is the kit's labelled icon button.
    final chevron = tester.widget<KitIconButton>(_key('folder-browse-demo'));
    expect(chevron.tooltip, _en.folderBrowserShowInside('demo'));
    await tester.tap(_key('folder-browse-demo'));
    await tester.pumpAndSettle();
    expect(find.text('lib'), findsOneWidget);
    expect(find.text(_en.folderBrowserOpen('demo')), findsOneWidget);

    await tester.tap(find.text(_en.folderBrowserOpen('demo')));
    await tester.pumpAndSettle();
    expect(closed, isA<FolderBrowserOpen>());
    expect((closed! as FolderBrowserOpen).path, '/root/projects/demo');
  });

  testWidgets('a new project is named in a dialog and made here', (
    tester,
  ) async {
    FolderBrowserChoice? closed;
    await _open(
      tester,
      (path) async => _folders(path, ['demo']),
      onClosed: (choice) => closed = choice,
    );
    await tester.pumpAndSettle();
    await tester.tap(_key('phone-new-folder'));
    await tester.pumpAndSettle();
    await tester.enterText(_key('phone-new-folder-name'), 'fresh');
    await tester.tap(_key('phone-new-folder-create'));
    await tester.pumpAndSettle();
    expect(closed, isA<FolderBrowserCreate>());
    expect((closed! as FolderBrowserCreate).path, '/root/projects/fresh');
  });
}
