import 'dart:async';
import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/domain/transcript_search.dart';
import 'package:opencode_mobile/domain/server_gateway.dart' show ServerPage;
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/desktop/shortcuts.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/widgets/markdown.dart';
import 'package:opencode_mobile/ui/widgets/transcript_highlight.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../tool/capture/fixtures.dart' show captureTheme, loadCaptureFonts;

MessageWithParts _message(String id, String role, List<Part> parts) =>
    MessageWithParts(
      info: MessageInfo(
        id: id,
        sessionID: 's',
        role: role,
        time: MsgTime(created: 1, completed: 2),
      ),
      parts: parts,
    );

class _Api extends OpenCodeApi {
  _Api() : super(baseUrl: 'http://localhost');
  bool longReply = false;
  bool failOlder = false;
  Completer<ServerPage<MessageWithParts>>? olderGate;
  final cursors = <String?>[];
  @override
  Future<ServerPage<MessageWithParts>> messagePage(
    String id, {
    String? cursor,
    int limit = 100,
  }) async {
    cursors.add(cursor);
    if (cursor != null && failOlder) {
      throw ApiException('Older history unavailable', statusCode: 503);
    }
    if (cursor == 'older') {
      return olderGate?.future ?? ServerPage(items: [], nextCursor: 'oldest');
    }
    if (longReply) {
      return ServerPage(
        items: [
          _message('m3', 'assistant', [
            Part(
              type: 'text',
              text:
                  '${List.filled(180, 'A long explanation.').join('\n')}\ncache target',
            ),
          ]),
        ],
      );
    }
    return cursor == null
        ? ServerPage(
            items: [
              _message('m2', 'user', [
                Part(type: 'text', text: 'Check cache and cache expiry.'),
              ]),
              _message('m3', 'assistant', [
                Part(
                  type: 'text',
                  text:
                      '**Cache** is scoped to the project.\n\n```dart\nfinal cache = <String>[];\n```',
                ),
              ]),
            ],
            nextCursor: 'older',
          )
        : ServerPage(
            items: [
              _message('m0', 'assistant', [
                Part(
                  type: 'tool',
                  toolName: 'read',
                  toolState: ToolState(
                    status: 'completed',
                    output: 'cache report',
                  ),
                ),
              ]),
              _message('m1', 'assistant', [
                Part(type: 'reasoning', text: 'Consider cache expiry.'),
              ]),
            ],
          );
  }

  @override
  Future<List<PermissionRequest>> pendingPermissions() async => [];
  @override
  Future<List<PermissionRequest>> pendingPermissionsV2() async => [];
}

Future<void> _pump(
  WidgetTester tester,
  _Api api, {
  double width = 411,
  bool keyboard = false,
}) async {
  tester.view.physicalSize = Size(width, 891);
  tester.view.devicePixelRatio = 1;
  if (keyboard) tester.view.viewInsets = const FakeViewPadding(bottom: 280);
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
  addTearDown(tester.view.resetViewInsets);
  if (Platform.environment['OC_FIND_PREVIEW'] != null) {
    await tester.runAsync(loadCaptureFonts);
  }
  final c =
      ConnectionController(
          ProfileStore(prefs: await SharedPreferences.getInstance()),
        )
        ..api = api
        ..status = StreamStatus.connected;
  c.sessionsById['s'] = Session(id: 's', title: 'Cache review');
  addTearDown(c.dispose);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [connProvider.overrideWithValue(c)],
      child: RepaintBoundary(
        key: const ValueKey('find-preview'),
        child: MaterialApp(
          debugShowCheckedModeBanner: false,
          theme: captureTheme(),
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(
              context,
            ).copyWith(textScaler: TextScaler.linear(width == 320 ? 1.7 : 1)),
            child: child!,
          ),
          home: const ChatScreen(sessionID: 's'),
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
  Actions.invoke(
    tester.element(find.byKey(const Key('chat-composer-field'))),
    const FindInSurfaceIntent(),
  );
  await tester.pumpAndSettle();
}

Future<void> _query(WidgetTester tester, String query) async {
  await tester.enterText(
    find.byKey(const ValueKey('transcript-find-input')),
    query,
  );
  await tester.pump(const Duration(milliseconds: 250));
  await tester.pumpAndSettle();
}

Iterable<TextSpan> _spans(InlineSpan span) sync* {
  if (span is TextSpan) {
    yield span;
    for (final child in span.children ?? const <InlineSpan>[]) {
      yield* _spans(child);
    }
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUp(() => SharedPreferences.setMockInitialValues({}));

  test(
    'literal occurrence counts preserve offsets and include reasoning/tool/file data',
    () {
      final index = TranscriptSearchIndex();
      final messages = [
        _message('m', 'assistant', [
          Part(type: 'text', text: 'Cache CACHE [x] مرحبا'),
          Part(type: 'reasoning', text: 'cache reasoning'),
          Part(
            type: 'tool',
            toolName: 'read',
            toolState: ToolState(
              status: 'completed',
              input: {'path': 'cache.dart'},
              output: 'cache output',
            ),
          ),
          Part(type: 'file', filename: 'cache.txt'),
          Part(type: 'text', text: 'cache hidden', synthetic: true),
          Part(type: 'v2:instruction', text: 'cache redacted'),
          Part(
            type: 'tool',
            toolName: 'read',
            toolState: ToolState(
              status: 'completed',
              output: 'cache pruned',
              pruned: true,
            ),
          ),
        ]),
      ];
      expect(index.search(messages, 'cache').length, 6);
      final exact = index.search(messages, '[x]').single;
      expect(exact.text.substring(exact.start, exact.end), '[x]');
      expect(index.search(messages, 'مرحبا').single.start, 16);
      expect(index.search(messages, '  '), isEmpty);
    },
  );

  test('streaming, older-page prepend and deletion update cached matches', () {
    final index = TranscriptSearchIndex();
    final stable = _message('b', 'user', [Part(type: 'text', text: 'cache')]);
    final first = index.search([stable], 'cache').single;
    final extended = index.search([
      _message('a', 'assistant', [Part(type: 'text', text: 'cache cache')]),
      stable,
    ], 'cache');
    expect(extended.length, 3);
    expect(identical(extended.last, first), isTrue);
    final changed = _message('b', 'user', [
      Part(type: 'text', text: 'cache cache'),
    ]);
    expect(index.search([changed], 'cache').length, 2);
    expect(index.search([], 'cache'), isEmpty);
  });

  testWidgets(
    'Markdown and code highlight without changing content or reparsing blocks',
    (tester) async {
      Widget render(String query) => MaterialApp(
        home: Scaffold(
          body: TranscriptHighlight(
            query: query,
            child: const MarkdownText(
              '**Cache** and cache\n\n```dart\nfinal cache = 1;\n```',
            ),
          ),
        ),
      );
      MarkdownText.debugParseCount = 0;
      await tester.pumpWidget(render('cache'));
      await tester.pumpAndSettle();
      final parsed = MarkdownText.debugParseCount;
      final highlighted =
          [
                ...tester
                    .widgetList<RichText>(find.byType(RichText))
                    .map((w) => w.text),
                ...tester
                    .widgetList<SelectableText>(find.byType(SelectableText))
                    .map((w) => w.textSpan)
                    .whereType<TextSpan>(),
              ]
              .expand(_spans)
              .where((s) => s.style?.backgroundColor != null && s.text != null)
              .map((s) => s.text!)
              .join();
      expect(highlighted.toLowerCase(), contains('cache'));
      await tester.pumpWidget(render('missing'));
      await tester.pumpAndSettle();
      expect(MarkdownText.debugParseCount, parsed);
      expect(
        tester
            .widgetList<SelectableText>(find.byType(SelectableText))
            .any(
              (w) => w.textSpan?.toPlainText().contains('final cache') == true,
            ),
        isTrue,
      );
    },
  );

  testWidgets(
    'count, next/previous, older recovery and close work in the real chat',
    (tester) async {
      final api = _Api();
      await _pump(tester, api);
      await _query(tester, 'cache');
      expect(find.text('1 of 4 matches'), findsWidgets);
      expect(find.textContaining('Loaded messages only.'), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('transcript-find-previous')));
      await tester.pumpAndSettle();
      expect(find.text('4 of 4 matches'), findsWidgets);
      api.failOlder = true;
      await tester.tap(find.byKey(const ValueKey('transcript-find-older')));
      await tester.pumpAndSettle();
      expect(find.text('Older history unavailable'), findsWidgets);
      expect(find.text('4 of 4 matches'), findsWidgets);
      api.failOlder = false;
      await tester.tap(find.byKey(const ValueKey('transcript-find-older')));
      await tester.pumpAndSettle();
      expect(find.text('6 of 6 matches'), findsWidgets);
      expect(
        find.text('All available message content searched.'),
        findsOneWidget,
      );
      await tester.tap(find.byKey(const ValueKey('transcript-find-next')));
      await tester.pumpAndSettle();
      expect(find.text('1 of 6 matches'), findsOneWidget);
      expect(find.text('1 of 6 matches · Tool data'), findsOneWidget);
      await tester.sendKeyEvent(LogicalKeyboardKey.escape);
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('transcript-find-bar')), findsNothing);
      await tester.pumpWidget(const SizedBox());
    },
  );

  testWidgets('session menu find and timeline selection use the same search', (
    tester,
  ) async {
    await _pump(tester, _Api());
    await tester.tap(find.byTooltip('Close search'));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Session menu'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Find in conversation'));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('transcript-find-input')), findsOneWidget);
    await tester.tap(find.byTooltip('Close search'));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Session menu'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Timeline'));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const ValueKey('timeline-search')),
      'cache',
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('timeline-row-m3')));
    await tester.pumpAndSettle();
    expect(
      tester
          .widget<TextField>(
            find.byKey(const ValueKey('transcript-find-input')),
          )
          .controller!
          .text,
      'cache',
    );
    expect(find.text('3 of 4 matches'), findsWidgets);
    await tester.pumpWidget(const SizedBox());
  });

  testWidgets('cancelling full-history search stops before the next page', (
    tester,
  ) async {
    final api = _Api()..olderGate = Completer<ServerPage<MessageWithParts>>();
    await _pump(tester, api);
    await _query(tester, 'cache');
    await tester.tap(find.byKey(const ValueKey('transcript-find-older')));
    await tester.pump();
    await tester.tap(find.text('Cancel'));
    await tester.pump();
    api.olderGate!.complete(ServerPage(items: [], nextCursor: 'oldest'));
    await tester.pumpAndSettle();
    expect(api.cursors, [null, 'older']);
    expect(find.textContaining('Loaded messages only.'), findsOneWidget);
    await tester.pumpWidget(const SizedBox());
  });

  testWidgets('active occurrence excerpt is visible in a very long reply', (
    tester,
  ) async {
    await _pump(tester, _Api()..longReply = true);
    await _query(tester, 'cache');
    expect(find.byType(TranscriptMatchExcerpt).hitTestable(), findsOneWidget);
    await tester.pumpWidget(const SizedBox());
  });

  testWidgets(
    'compact enlarged-text search keeps navigation reachable above a keyboard',
    (tester) async {
      await _pump(tester, _Api(), width: 320, keyboard: true);
      await _query(tester, 'cache');
      final next = find.byKey(const ValueKey('transcript-find-next'));
      await tester.ensureVisible(next);
      await tester.pumpAndSettle();
      expect(next.hitTestable(), findsOneWidget);
      await tester.tap(next);
      await tester.pumpAndSettle();
      expect(find.text('2 of 4 matches'), findsWidgets);
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox());
    },
  );

  testWidgets('phone search preview', (tester) async {
    await _pump(tester, _Api());
    await _query(tester, 'cache');
    final path = Platform.environment['OC_FIND_PREVIEW'];
    if (path != null) {
      await tester.runAsync(() async {
        final boundary = tester.renderObject<RenderRepaintBoundary>(
          find.byKey(const ValueKey('find-preview')),
        );
        final image = await boundary.toImage(pixelRatio: 1);
        final bytes = await image.toByteData(format: ui.ImageByteFormat.png);
        await File(path).writeAsBytes(bytes!.buffer.asUint8List());
        image.dispose();
      });
    }
    await tester.pumpWidget(const SizedBox());
  });
}
