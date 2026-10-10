// Search inside files (Files › search filter › Text in files): the result
// rows, the tap that opens the file at the line, and the servers that cannot
// do it. Pictures for the look gate: build/coverage/od-paseo-text-*.png.
import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/screens/files_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../tool/capture/fixtures.dart' show loadCaptureFonts;
import 'goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;

class _Api extends OpenCodeApi {
  _Api(this._caps) : super(baseUrl: 'http://localhost');

  final ServerCapabilities _caps;
  final searches = <String>[];
  final reads = <String>[];
  List<FindMatch> matches = const [];
  Object? failure;

  @override
  ServerCapabilities get capabilities => _caps;

  @override
  Future<List<FileNode>> listFiles([String path = '']) async => [
    FileNode(name: 'lib', path: 'lib', isDir: true),
  ];

  @override
  Future<List<FindMatch>> findText(String pattern) async {
    searches.add(pattern);
    if (failure != null) throw failure!;
    return matches;
  }

  @override
  Future<FileContent> fileContent(String path) async {
    reads.add(path);
    return FileContent(
      List.generate(60, (i) => 'line ${i + 1} of the file').join('\n'),
    );
  }
}

const _root = ValueKey('text-search-root');

Widget _app(Widget home) => RepaintBoundary(
  key: _root,
  child: MaterialApp(
    theme: AppTheme.dark(),
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    home: Scaffold(body: home),
  ),
);

Future<ConnectionController> _controller(_Api api) async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  return ConnectionController(ProfileStore(prefs: prefs))
    ..api = api
    ..status = StreamStatus.connected;
}

Future<void> _shoot(WidgetTester tester, String name) async {
  final render =
      tester.renderObject(find.byKey(_root)) as RenderRepaintBoundary;
  await tester.runAsync(() async {
    final image = await render.toImage();
    final png = await image.toByteData(format: ui.ImageByteFormat.png);
    Directory('build/coverage').createSync(recursive: true);
    File(
      'build/coverage/od-paseo-text-$name.png',
    ).writeAsBytesSync(png!.buffer.asUint8List());
  });
}

Future<void> _openText(WidgetTester tester) async {
  await tester.tap(find.byKey(const ValueKey('file-surface-selector')));
  await tester.pumpAndSettle();
  await tester.tap(find.byKey(const ValueKey('file-surface-text')));
  await tester.pumpAndSettle();
}

Future<void> _type(WidgetTester tester, String text) async {
  await tester.enterText(
    find.byKey(const ValueKey('files-search-field')),
    text,
  );
  await tester.pump(const Duration(seconds: 1));
  await tester.pumpAndSettle();
}

void _phone(WidgetTester tester) {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() async {
    await loadKitGalleryFonts();
    await loadCaptureFonts();
  });

  testWidgets('the filter offers Text in files and says what to type', (
    tester,
  ) async {
    _phone(tester);
    final api = _Api(ServerCapabilities.allV1);
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(FilesScreen(controller: controller)));
    await tester.pumpAndSettle();
    await _openText(tester);
    expect(find.text('Search text in files'), findsOneWidget);
    expect(find.text('Search inside files'), findsOneWidget);
    expect(api.searches, isEmpty, reason: 'nothing typed, nothing asked');
    await _shoot(tester, 'hint');
  });

  testWidgets('a typed word lists each line with its file and line number', (
    tester,
  ) async {
    _phone(tester);
    final api = _Api(ServerCapabilities.allV1)
      ..matches = [
        FindMatch(
          path: 'lib/cart_total.dart',
          lineNumber: 42,
          snippet: '  final total = sum.roundToDouble();\n',
        ),
        FindMatch(
          path: 'test/cart_total_test.dart',
          lineNumber: 7,
          snippet: "  expect(total, roundToDouble(3.4));",
        ),
      ];
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(FilesScreen(controller: controller)));
    await tester.pumpAndSettle();
    await _openText(tester);
    await _type(tester, 'roundToDouble');
    expect(api.searches, ['roundToDouble']);
    expect(find.text('final total = sum.roundToDouble();'), findsOneWidget);
    expect(find.textContaining('Line 42', findRichText: true), findsOneWidget);
    expect(find.textContaining('Line 7', findRichText: true), findsOneWidget);
    await _shoot(tester, 'results');
  });

  testWidgets('tapping a result opens that file at its line', (tester) async {
    _phone(tester);
    final api = _Api(ServerCapabilities.allV1)
      ..matches = [
        FindMatch(
          path: 'lib/cart_total.dart',
          lineNumber: 42,
          snippet: '  final total = sum.roundToDouble();',
        ),
      ];
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(FilesScreen(controller: controller)));
    await tester.pumpAndSettle();
    await _openText(tester);
    await _type(tester, 'round');
    await tester.tap(
      find.byKey(const ValueKey('text-match-lib/cart_total.dart:42')),
    );
    await tester.pumpAndSettle();
    expect(api.reads, ['lib/cart_total.dart']);
    expect(find.textContaining('line 42 of the file'), findsOneWidget);
    // The viewer's own header names the line it opened at.
    expect(find.text('lib/ · Line 42'), findsOneWidget);
    await _shoot(tester, 'opened-at-line');
  });

  testWidgets('no match says so, and a failure says it plainly', (
    tester,
  ) async {
    _phone(tester);
    final api = _Api(ServerCapabilities.allV1);
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(FilesScreen(controller: controller)));
    await tester.pumpAndSettle();
    await _openText(tester);
    await _type(tester, 'zzz');
    expect(find.text('No matches'), findsOneWidget);
    await _shoot(tester, 'none');

    api.failure = const ProductException('The server did not answer.');
    await _type(tester, 'zzzz');
    expect(find.text("Couldn't search the files"), findsOneWidget);
    expect(find.text('Try again'), findsOneWidget);
    await _shoot(tester, 'failed');
  });

  testWidgets('a long result list is capped and says so', (tester) async {
    _phone(tester);
    final api = _Api(ServerCapabilities.allV1)
      ..matches = [
        for (var i = 1; i <= 260; i++)
          FindMatch(path: 'lib/f$i.dart', lineNumber: i, snippet: 'match $i'),
      ];
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(FilesScreen(controller: controller)));
    await tester.pumpAndSettle();
    await _openText(tester);
    await _type(tester, 'match');
    await tester.dragUntilVisible(
      find.byKey(const ValueKey('files-text-limited')),
      find.byType(ListView).first,
      const Offset(0, -600),
    );
    expect(
      find.text('Showing the first 200 matches. Type more to narrow them.'),
      findsOneWidget,
    );
  });

  testWidgets('a server that cannot search text does not offer it', (
    tester,
  ) async {
    _phone(tester);
    final api = _Api(const ServerCapabilities(textSearch: false));
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(FilesScreen(controller: controller)));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('file-surface-selector')));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('file-surface-text')), findsNothing);
    expect(find.byKey(const ValueKey('file-surface-symbols')), findsOneWidget);
  });
}
