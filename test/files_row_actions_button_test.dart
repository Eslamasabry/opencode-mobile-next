import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'support/fake_server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/review_handoff.dart';
import 'package:opencode_mobile/ui/screens/files_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// screen-files-1 (map files-row-actions-sheet → merge-into:files, and
/// files-file-viewer-sheet → the kit's one viewer): a row's actions are its
/// KitRowMenu (long-press, right-click, semantic actions) instead of a per-row
/// "..." button and a bespoke sheet; a file opens in KitViewer.
class _FilesApi extends FakeServerGateway {
  _FilesApi() : super(baseUrl: 'http://localhost');

  @override
  Future<List<FileNode>> listFiles([String path = '']) async => [
    FileNode(name: 'lib', path: 'lib', isDir: true),
    FileNode(name: '.env', path: '.env', isDir: false),
    FileNode(name: 'README.md', path: 'README.md', isDir: false),
  ];

  @override
  Future<List<String>> findFile(String query) async => const [];

  @override
  Future<FileContent> fileContent(String path) async =>
      const FileContent('readme body', mimeType: 'text/plain');

  @override
  Future<List<Session>> sessions() async => const [];

  @override
  Future<Map<String, String>> sessionStatuses() async => const {};
}

Future<ConnectionController> _controller() async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  return ConnectionController(ProfileStore(prefs: prefs))
    ..api = _FilesApi()
    ..status = StreamStatus.connected;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  Future<void> pump(
    WidgetTester tester, {
    ProjectFileAttachment? onAttachFile,
    ReviewHandoffSession? handoff,
  }) async {
    final controller = await _controller();
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(
          body: FilesScreen(
            controller: controller,
            onAttachFile: onAttachFile,
            handoff: handoff,
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  String? mockClipboard(WidgetTester tester) {
    String? copied;
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      SystemChannels.platform,
      (call) async {
        if (call.method == 'Clipboard.setData') {
          copied = (call.arguments as Map)['text'] as String?;
        }
        return null;
      },
    );
    addTearDown(
      () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
        SystemChannels.platform,
        null,
      ),
    );
    return copied;
  }

  testWidgets('rows carry no per-row button; long-press opens the row menu', (
    tester,
  ) async {
    await pump(tester, onAttachFile: (_, _) async {});

    expect(
      find.byKey(const ValueKey('project-file-actions-lib')),
      findsNothing,
    );
    expect(find.byType(IconButton), findsNothing);
    expect(find.byKey(const ValueKey('file-row-actions-sheet')), findsNothing);

    await tester.longPress(find.text('README.md'));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('file-menu-attach')), findsOneWidget);
    expect(find.byKey(const ValueKey('file-menu-copy-path')), findsOneWidget);
    expect(find.byKey(const ValueKey('file-menu-copy-name')), findsOneWidget);
    // Opening is the row's own tap, so the menu does not repeat it.
    expect(find.byKey(const ValueKey('file-menu-open')), findsNothing);
    await tester.tapAt(Offset.zero);
    await tester.pumpAndSettle();

    // A folder: nothing to attach, but its path and name can be copied.
    await tester.longPress(find.text('lib'));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('file-menu-attach')), findsNothing);
    expect(find.byKey(const ValueKey('file-menu-copy-path')), findsOneWidget);
  });

  testWidgets('menu actions run: attach says so in place, copy copies', (
    tester,
  ) async {
    String? attached;
    await pump(tester, onAttachFile: (path, _) async => attached = path);
    String? copied;
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      SystemChannels.platform,
      (call) async {
        if (call.method == 'Clipboard.setData') {
          copied = (call.arguments as Map)['text'] as String?;
        }
        return null;
      },
    );
    addTearDown(
      () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
        SystemChannels.platform,
        null,
      ),
    );

    await tester.longPress(find.text('README.md'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('file-menu-attach')));
    await tester.pumpAndSettle();
    expect(attached, 'README.md');
    // No snackbar: an in-place notice under the search field.
    expect(find.byType(SnackBar), findsNothing);
    expect(find.byKey(const ValueKey('files-notice')), findsOneWidget);
    expect(find.text('README.md attached.'), findsOneWidget);

    await tester.longPress(find.text('lib'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('file-menu-copy-name')));
    await tester.pumpAndSettle();
    expect(copied, 'lib');

    // The menu did not open the file or enter the folder.
    expect(find.byKey(const ValueKey('project-file-lib')), findsOneWidget);
  });

  testWidgets('dot entries stay hidden until Show hidden files', (
    tester,
  ) async {
    await pump(tester);
    expect(find.text('.env'), findsNothing);

    await tester.tap(find.byKey(const ValueKey('file-surface-selector')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('files-show-hidden')));
    await tester.pumpAndSettle();
    expect(find.text('.env'), findsOneWidget);
  });

  testWidgets('a file opens in the kit viewer, with no second viewer sheet', (
    tester,
  ) async {
    mockClipboard(tester);
    await pump(tester, onAttachFile: (_, _) async {});

    await tester.tap(find.text('README.md'));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('files-viewer')), findsOneWidget);
    expect(find.textContaining('readme body'), findsWidgets);
    // "Ask about this file" is the reader's one main action (vision
    // 2026-10-20); "Attach to prompt" waits in its ⋯, in the same words as
    // the row menu (never a bare "Attach").
    expect(
      find.descendant(
        of: find.byKey(const Key('project-file-ask')),
        matching: find.text('Ask about this file'),
      ),
      findsOneWidget,
    );
    expect(find.text('Attach'), findsNothing);
    await tester.tap(find.byKey(const ValueKey('kit-viewer-more')));
    await tester.pumpAndSettle();
    expect(
      find.descendant(
        of: find.byKey(const Key('project-file-attach')),
        matching: find.text('Attach to prompt'),
      ),
      findsOneWidget,
    );
  });

  testWidgets('Ask about this file stages a reference with Undo, not a '
      'snackbar', (tester) async {
    final store = ReviewHandoffStore();
    final handoff = ReviewHandoffSession(store: store, sessionID: 's1');
    await pump(tester, handoff: handoff);

    await tester.longPress(find.text('README.md'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('file-menu-ask')));
    await tester.pumpAndSettle();
    expect(handoff.references, hasLength(1));
    expect(find.byType(SnackBar), findsNothing);
    expect(find.byKey(const Key('files-staged-notice')), findsOneWidget);

    await tester.tap(find.text('Undo'));
    await tester.pumpAndSettle();
    expect(handoff.references, isEmpty);
  });
}
