import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/kit/kit_code_block.dart';
import 'package:opencode_mobile/ui/kit/kit_image.dart';
import 'package:opencode_mobile/ui/kit/kit_motion.dart';
import 'package:opencode_mobile/ui/kit/kit_viewer.dart';
import 'package:opencode_mobile/ui/screens/review_workspace.dart';
import 'package:opencode_mobile/ui/screens/files_screen.dart';
import 'package:opencode_mobile/ui/widgets/file_preview.dart';

import 'support/product_ui_regression_fixtures.dart';

class _SymbolRepository extends LocationRepository {
  final queries = <String>[];
  List<WorkspaceSymbol> symbols = const [];
  Object? symbolError;

  @override
  Future<List<WorkspaceSymbol>> findWorkspaceSymbols(String query) async {
    queries.add(query);
    if (symbolError case final error?) throw error;
    return symbols;
  }
}

Future<void> _openViewerMenu(WidgetTester tester) async {
  await tester.tap(find.byKey(const ValueKey('kit-viewer-more')));
  await tester.pumpAndSettle();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    ReviewWorkspace.clearCache();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
  });

  testWidgets('unsupported binary files show preview metadata', (tester) async {
    final api = TestApi(
      files: (_) async => [
        FileNode(name: 'image.bin', path: 'image.bin', isDir: false),
      ],
      contents: const {
        'image.bin': FileContent(
          'AAEC',
          type: 'binary',
          encoding: 'base64',
          mimeType: 'application/octet-stream',
        ),
      },
    );
    final controller = await regressionController(
      api: api,
      repository: LocationRepository(),
    );
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(body: FilesScreen(controller: controller)),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('image.bin'));
    await tester.pumpAndSettle();

    expect(find.text("Can't show this file"), findsOneWidget);
    expect(find.textContaining('application/octet-stream'), findsOneWidget);
    expect(find.text('AAEC'), findsNothing);
  });

  testWidgets('image files open as a zoomable preview', (tester) async {
    final api = TestApi(
      files: (_) async => [
        FileNode(name: 'pixel.png', path: 'pixel.png', isDir: false),
      ],
      contents: const {
        'pixel.png': FileContent(
          'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl2ZKgAAAAASUVORK5CYII=',
          type: 'binary',
          encoding: 'base64',
        ),
      },
    );
    final controller = await regressionController(
      api: api,
      repository: LocationRepository(),
    );
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(body: FilesScreen(controller: controller)),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('pixel.png'));
    await tester.pumpAndSettle();

    expect(find.byType(KitZoom), findsOneWidget);
    expect(find.byKey(const ValueKey('kit-viewer-image')), findsOneWidget);
    await _openViewerMenu(tester);
    expect(find.byKey(const Key('project-file-download')), findsOneWidget);
    await tester.tapAt(const Offset(4, 4));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('project-file-attach')), findsNothing);
  });

  testWidgets(
    'project CSV uses table preview and retains full truncated source for attach',
    (tester) async {
      final original = 'name,value\n${List.filled(26000, 'entry,1\n').join()}';
      final api = TestApi(
        files: (_) async => [
          FileNode(name: 'small.csv', path: 'small.csv', isDir: false),
          FileNode(name: 'large.csv', path: 'large.csv', isDir: false),
        ],
        contents: {
          'small.csv': const FileContent(
            'name,value\nentry,1\n',
            mimeType: 'text/csv',
          ),
          'large.csv': FileContent(original, mimeType: 'text/csv'),
        },
      );
      final controller = await regressionController(
        api: api,
        repository: LocationRepository(),
      );
      addTearDown(controller.dispose);
      FilePreviewData? attached;
      await tester.pumpWidget(
        MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Scaffold(
            body: FilesScreen(
              controller: controller,
              onAttachFile: (_, data) async => attached = data,
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('small.csv'));
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('kit-viewer-table')), findsOneWidget);
      expect(find.textContaining('entry'), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('kit-viewer-close')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('large.csv'));
      await tester.pumpAndSettle();
      expect(find.textContaining('Showing'), findsOneWidget);
      // Files now parses the complete CSV and caps rows in KitViewer; the
      // source itself is intact, so its truncated table is still honest.
      expect(find.byKey(const ValueKey('kit-viewer-table')), findsOneWidget);
      await tester.tap(find.byKey(const Key('project-file-attach')));
      await tester.pumpAndSettle();
      expect(attached?.copyText, original);
      expect(attached?.copyText, isNot(contains('... truncated')));
    },
  );

  testWidgets('chat file viewer can attach the original project file', (
    tester,
  ) async {
    const path = 'docs/review.md';
    const body = '# Review\n\nAdd a focused follow-up comment.';
    final api = TestApi(
      files: (_) async => [
        FileNode(name: 'review.md', path: path, isDir: false),
      ],
      contents: const {path: FileContent(body, mimeType: 'text/markdown')},
    );
    final controller = await regressionController(
      api: api,
      repository: LocationRepository(),
    );
    addTearDown(controller.dispose);
    String? attachedPath;
    FilePreviewData? attachedData;

    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(
          body: FilesScreen(
            controller: controller,
            onAttachFile: (path, data) async {
              attachedPath = path;
              attachedData = data;
            },
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('review.md'));
    await tester.pumpAndSettle();

    await _openViewerMenu(tester);
    expect(find.byKey(const Key('project-file-download')), findsOneWidget);
    await tester.tapAt(const Offset(4, 4));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('project-file-attach')), findsOneWidget);
    await tester.tap(find.byKey(const Key('project-file-attach')));
    await tester.pumpAndSettle();

    expect(attachedPath, path);
    expect(attachedData?.name, 'review.md');
    expect(attachedData?.mimeType, 'text/markdown');
    expect(attachedData?.text, body);
    expect(
      find.text(
        'review.md attached. Return to the conversation to add your comment.',
      ),
      findsOneWidget,
    );
  });

  testWidgets('symbol search opens the exact source line in file preview', (
    tester,
  ) async {
    final source = List.generate(
      70,
      (index) => index == 41
          ? 'class ProjectHealthScreen extends StatefulWidget {'
          : '// line ${index + 1}',
    ).join('\n');
    final api = TestApi(
      files: (_) async => const [],
      contents: {
        'lib/ui/project_health_screen.dart': FileContent(
          source,
          type: 'text',
          mimeType: 'text/plain',
        ),
      },
    );
    final repository = _SymbolRepository()
      ..symbols = const [
        WorkspaceSymbol(
          name: 'ProjectHealthScreen',
          kind: 5,
          path: 'lib/ui/project_health_screen.dart',
          line: 42,
          column: 7,
        ),
      ];
    final controller = await regressionController(
      api: api,
      repository: repository,
    );
    addTearDown(controller.dispose);
    tester.view.physicalSize = const Size(320, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Builder(
          builder: (context) => MediaQuery(
            data: MediaQuery.of(
              context,
            ).copyWith(textScaler: const TextScaler.linear(2)),
            child: Scaffold(body: FilesScreen(controller: controller)),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Symbols'), findsNothing);
    await tester.tap(find.byKey(const ValueKey('file-surface-selector')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('file-surface-symbols')));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField), 'ProjectHealth');
    await tester.testTextInput.receiveAction(TextInputAction.search);
    await tester.pumpAndSettle();

    expect(repository.queries, ['ProjectHealth']);
    expect(find.text('ProjectHealthScreen'), findsOneWidget);
    expect(
      find.textContaining('lib/ui/project_health_screen.dart:42:7'),
      findsOneWidget,
    );

    await tester.tap(find.text('ProjectHealthScreen'));
    await tester.pumpAndSettle();

    expect(find.textContaining('Line 42'), findsOneWidget);
    expect(find.byType(KitViewer), findsOneWidget);
    expect(
      tester.widget<KitCodeBlock>(find.byType(KitCodeBlock)).initialLine,
      42,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('symbol search debounces typing and explains empty results', (
    tester,
  ) async {
    final api = TestApi(files: (_) async => const []);
    final repository = _SymbolRepository();
    final controller = await regressionController(
      api: api,
      repository: repository,
    );
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(body: FilesScreen(controller: controller)),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('file-surface-selector')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('file-surface-symbols')));
    await tester.pumpAndSettle();

    await tester.enterText(find.byType(TextField), 'MissingSymbol');
    await tester.pump(KitMotion.typingSettle - const Duration(milliseconds: 1));
    expect(repository.queries, isEmpty);
    await tester.pump(const Duration(milliseconds: 1));
    await tester.pumpAndSettle();

    expect(repository.queries, ['MissingSymbol']);
    expect(find.text('No symbols found'), findsOneWidget);
    expect(
      find.textContaining('do not support project-wide symbol search'),
      findsOneWidget,
    );
  });

  testWidgets('unavailable symbol search leaves file browsing intact', (
    tester,
  ) async {
    final api = TestApi(
      files: (_) async => [
        FileNode(name: 'README.md', path: 'README.md', isDir: false),
      ],
    );
    final repository = _SymbolRepository()
      ..symbolError = const ProductException(
        'Symbol search is unavailable on this server',
      );
    final controller = await regressionController(
      api: api,
      repository: repository,
    );
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(body: FilesScreen(controller: controller)),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('README.md'), findsOneWidget);

    await tester.tap(find.byKey(const ValueKey('file-surface-selector')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('file-surface-symbols')));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField), 'Missing');
    await tester.testTextInput.receiveAction(TextInputAction.search);
    await tester.pumpAndSettle();
    expect(
      find.text('Symbol search is unavailable on this server'),
      findsOneWidget,
    );

    await tester.tap(find.byKey(const ValueKey('file-surface-selector')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('file-surface-files')));
    await tester.pumpAndSettle();
    expect(find.text('README.md'), findsOneWidget);
  });
}
