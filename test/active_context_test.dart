import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart' show Session, SessionRevert;
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api2/active_context_mapper.dart';
import 'package:opencode_mobile/api2/gateway_operations.dart';
import 'package:opencode_mobile/api2/models.dart';
import 'package:opencode_mobile/api2/transport.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/active_context_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'api2_interaction_gateway_test.dart'
    show withServer, gatewayFor, writeJson;
import '../tool/capture/fixtures.dart' show loadCaptureFonts, captureTheme;

ActiveContextMessage _map(String type, Map<String, dynamic> values) =>
    mapActiveContext(
      Api2Message.fromJson({'id': 'msg_$type', 'type': type, ...values})!,
    );

const _rows = [
  ActiveContextMessage(
    id: 'msg_01',
    type: 'system',
    content: [
      ContextContent(
        ContextContentKind.text,
        'Follow the project instructions.',
      ),
    ],
  ),
  ActiveContextMessage(
    id: 'msg_02',
    type: 'user',
    content: [
      ContextContent(
        ContextContentKind.text,
        'Find the missing Arabic text: مرحبا 👋',
      ),
    ],
  ),
  ActiveContextMessage(
    id: 'msg_03',
    type: 'assistant',
    content: [
      ContextContent(
        ContextContentKind.toolOutput,
        'Found مرحبا in the document.',
        name: 'read',
      ),
    ],
  ),
];

class _Repository extends ProductRepository implements ActiveContextGateway {
  @override
  bool get activeContextSupported => true;
  List<ActiveContextMessage> rows = _rows;
  Object? failure;
  Completer<List<ActiveContextMessage>>? pending;
  int calls = 0;
  @override
  Future<List<ActiveContextMessage>> loadActiveContext(String sessionID) async {
    calls++;
    if (pending != null) return pending!.future;
    if (failure != null) throw failure!;
    return rows;
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _Controller extends ConnectionController {
  _Controller(super.store);
  int history = 0;
  @override
  int sessionHistoryRevision(String id) => history;
  @override
  Future<ServerOperationsGateway?> prepareActionRepository() async =>
      repository;
}

Future<(_Controller, _Repository)> _setup() async {
  final repo = _Repository();
  final controller = _Controller(
    ProfileStore(prefs: await SharedPreferences.getInstance()),
  )..repository = repo;
  addTearDown(controller.dispose);
  return (controller, repo);
}

Future<void> _open(
  WidgetTester tester,
  _Controller c, {
  bool compact = false,
}) async {
  tester.view.physicalSize = Size(compact ? 320 : 411, compact ? 640 : 891);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
  if (Platform.environment['OC_CONTEXT_PREVIEW'] != null) {
    await tester.runAsync(loadCaptureFonts);
  }
  await tester.pumpWidget(
    MaterialApp(
      theme: captureTheme(),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      builder: (context, child) => MediaQuery(
        data: MediaQuery.of(
          context,
        ).copyWith(textScaler: TextScaler.linear(compact ? 1.7 : 1)),
        child: child!,
      ),
      home: RepaintBoundary(
        key: const ValueKey('context-capture'),
        child: ActiveContextScreen(controller: c, sessionID: 'ses_test'),
      ),
    ),
  );
  await tester.pump();
}

Future<void> _capture(WidgetTester tester, String name) async {
  if (Platform.environment['OC_CONTEXT_PREVIEW'] == null) return;
  await tester.pumpAndSettle();
  await tester.runAsync(() async {
    final boundary = tester.renderObject<RenderRepaintBoundary>(
      find.byKey(const ValueKey('context-capture')),
    );
    final image = await boundary.toImage();
    final bytes = (await image.toByteData(format: ui.ImageByteFormat.png))!;
    await File(
      '.dart_tool/active-context-$name.png',
    ).writeAsBytes(bytes.buffer.asUint8List());
    image.dispose();
  });
}

Matcher _failure(ActiveContextFailure value) =>
    isA<ActiveContextException>().having((e) => e.failure, 'failure', value);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUp(() => SharedPreferences.setMockInitialValues({}));

  testWidgets('phone context preview', (tester) async {
    final (c, repo) = await _setup();
    repo.rows = const [
      ActiveContextMessage(
        id: 'msg_01',
        type: 'compaction',
        content: [
          ContextContent(
            ContextContentKind.text,
            'The conversation so far: improve the composer, keep drafts recoverable, and verify the Android build.',
          ),
        ],
      ),
      ActiveContextMessage(
        id: 'msg_02',
        type: 'user',
        content: [
          ContextContent(
            ContextContentKind.text,
            'Review the composer and explain what changed.',
          ),
        ],
      ),
      ActiveContextMessage(
        id: 'msg_03',
        type: 'assistant',
        content: [
          ContextContent(
            ContextContentKind.toolOutput,
            'Read chat_screen.dart and checked the draft recovery flow.',
            name: 'read',
          ),
        ],
      ),
    ];
    await _open(tester, c);
    await _capture(tester, 'phone');
    await tester.pumpWidget(const SizedBox());
  }, skip: Platform.environment['OC_CONTEXT_PREVIEW'] == null);

  test(
    'context GET uses session identity and authentication, retaining more than 5000 messages',
    () async {
      await withServer(
        (server, requests) async {
          final gateway = gatewayFor(server);
          addTearDown(gateway.close);
          final ops = Api2OperationsGateway(client: gateway.client);
          final rows = await ops.loadActiveContext('ses_test');
          expect(rows, hasLength(5001));
          expect(rows.last.content.single.text, 'مرحبا 👋 5000');
          expect(requests.single.uri.path, '/api/session/ses_test/context');
          expect(requests.single.uri.queryParameters, isEmpty);
          expect(requests.single.method, 'GET');
        },
        handler: (request) {
          expect(
            request.headers.value('authorization'),
            'Basic ${base64Encode(utf8.encode('opencode:pw'))}',
          );
          return writeJson(request, {
            'data': List.generate(
              5001,
              (i) => {'id': 'msg_$i', 'type': 'user', 'text': 'مرحبا 👋 $i'},
            ),
          });
        },
      );
    },
  );

  test(
    'malformed and duplicate snapshots fail visibly without disabling context',
    () async {
      Object? response;
      await withServer((server, requests) async {
        final gateway = gatewayFor(server);
        addTearDown(gateway.close);
        final ops = Api2OperationsGateway(client: gateway.client);
        for (final invalid in [
          <String, dynamic>{},
          {
            'data': [null],
          },
          {
            'data': [
              {'id': 'msg_a'},
            ],
          },
          {
            'data': [
              {'id': 'msg_a', 'type': 'user'},
              {'id': 'msg_a', 'type': 'user'},
            ],
          },
        ]) {
          response = invalid;
          await expectLater(
            ops.loadActiveContext('ses_test'),
            throwsA(_failure(ActiveContextFailure.invalidResponse)),
          );
          expect(ops.activeContextSupported, isTrue);
        }
        response = {
          'data': [
            {'id': 'msg_unknown', 'type': 'future-kind', 'private': 'hidden'},
          ],
        };
        final unknown = (await ops.loadActiveContext('ses_test')).single;
        expect(unknown.type, 'future-kind');
        expect(unknown.content.single.kind, ContextContentKind.notice);
        expect(unknown.matches('hidden'), isFalse);
      }, handler: (request) => writeJson(request, response));
    },
  );

  test(
    'missing session and auth remain recoverable; missing route is capability failure',
    () async {
      var status = 404;
      var tag = 'SessionNotFoundError';
      await withServer(
        (server, requests) async {
          final gateway = gatewayFor(server);
          addTearDown(gateway.close);
          final ops = Api2OperationsGateway(client: gateway.client);
          for (final code in [404, 401, 500]) {
            status = code;
            await expectLater(
              ops.loadActiveContext('ses_test'),
              throwsA(isA<Api2Error>()),
            );
            expect(ops.activeContextSupported, isTrue);
          }
          status = 404;
          tag = 'RouteNotFound';
          await expectLater(
            ops.loadActiveContext('ses_test'),
            throwsA(_failure(ActiveContextFailure.unsupported)),
          );
          expect(ops.activeContextSupported, isFalse);
          final count = requests.length;
          await expectLater(
            ops.loadActiveContext('ses_test'),
            throwsA(_failure(ActiveContextFailure.unsupported)),
          );
          expect(requests.length, count);
        },
        handler: (request) => writeJson(request, {'_tag': tag}, status: status),
      );
    },
  );

  test(
    'explicit inspector retains instruction text and files without binary bodies or metadata',
    () {
      for (final type in ['system', 'synthetic', 'skill', 'user']) {
        final row = _map(type, {
          'text': 'Instruction مرحبا 👋',
          'name': 'Review',
          'metadata': {'secret': 'hidden'},
          'files': [
            {
              'name': 'photo.png',
              'mime': 'image/png',
              'data': 'BINARY_HIDDEN',
              'source': {'uri': 'https://hidden.invalid'},
            },
          ],
        });
        expect(row.matches('مرحبا'), isTrue);
        expect(row.matches('hidden'), isFalse);
        if (type == 'user') expect(row.matches('photo.png'), isTrue);
      }
    },
  );

  test(
    'tool content preserves all text results and distinguishes pruned and truncated output',
    () {
      final row = _map('assistant', {
        'content': [
          {
            'type': 'reasoning',
            'text': 'private reasoning',
            'time': {'pruned': 1},
          },
          {
            'type': 'tool',
            'name': 'read',
            'state': {
              'status': 'completed',
              'input': {'path': 'notes.md'},
              'content': [
                {'type': 'text', 'text': 'first result'},
                {'type': 'text', 'text': 'second مرحبا result'},
                {
                  'type': 'file',
                  'name': 'report.pdf',
                  'mime': 'application/pdf',
                  'uri': 'https://hidden.invalid',
                },
              ],
              'metadata': {'hidden': 'secret'},
            },
          },
          {
            'type': 'tool',
            'name': 'bash',
            'time': {'pruned': 2},
            'state': {
              'status': 'completed',
              'input': {'command': 'pwd'},
              'content': [
                {'type': 'text', 'text': 'pruned secret'},
              ],
            },
          },
          {'type': 'future-content', 'secret': 'hidden'},
        ],
      });
      expect(
        row.content
            .where((p) => p.kind == ContextContentKind.toolOutput)
            .map((p) => p.text),
        ['first result', 'second مرحبا result'],
      );
      expect(
        row.content.where((p) => p.kind == ContextContentKind.pruned),
        hasLength(2),
      );
      expect(row.matches('notes.md'), isTrue);
      expect(row.matches('report.pdf'), isTrue);
      for (final hidden in ['hidden', 'private reasoning', 'pruned secret']) {
        expect(row.matches(hidden), isFalse);
      }
      final shell = _map('shell', {
        'command': 'pwd',
        'output': {'output': '/project', 'truncated': true},
      });
      expect(shell.content.last.kind, ContextContentKind.truncated);
    },
  );

  test(
    'search preview centers a distant literal match without slicing emoji pairs',
    () {
      final row = _map('user', {
        'text': '${'👋 ' * 150}literal [match] مرحبا${' 👋' * 150}',
      });
      final preview = row.previewFor('[match]');
      expect(preview, contains('[match]'));
      expect(preview, startsWith('…'));
      expect(preview, endsWith('…'));
      expect(utf8.decode(utf8.encode(preview)), preview);
    },
  );

  testWidgets(
    'search, type filter, full preview and copy act on the selected content',
    (tester) async {
      final (c, _) = await _setup();
      await _open(tester, c);
      await tester.enterText(
        find.byKey(const ValueKey('active-context-search')),
        'مرحبا',
      );
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('active-context-msg_01')), findsNothing);
      expect(find.text('2 of 3 messages'), findsOneWidget);
      await tester.ensureVisible(find.text('Assistant · 1'));
      await tester.tap(find.text('Assistant · 1'));
      await tester.pumpAndSettle();
      expect(find.text('1 of 3 messages'), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('active-context-msg_03')));
      await tester.pumpAndSettle();
      expect(
        find.widgetWithText(SelectableText, 'Found مرحبا in the document.'),
        findsOneWidget,
      );
      String? copied;
      tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
        SystemChannels.platform,
        (call) async {
          if (call.method == 'Clipboard.setData') {
            copied = (call.arguments as Map)['text'] as String;
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
      await tester.tap(find.byTooltip('Copy'));
      expect(copied, 'Found مرحبا in the document.');
      c.history++;
      c.notifyListeners();
      await tester.pumpAndSettle();
      expect(
        find.widgetWithText(SelectableText, 'Found مرحبا in the document.'),
        findsNothing,
      );
      expect(find.textContaining('Reopen this inspector'), findsOneWidget);
    },
  );

  testWidgets(
    'failed refresh retains labeled snapshot and later refresh recovers',
    (tester) async {
      final (c, repo) = await _setup();
      await _open(tester, c);
      repo.failure = const ActiveContextException(
        ActiveContextFailure.invalidResponse,
      );
      await tester.tap(find.byTooltip('Refresh active context'));
      await tester.pumpAndSettle();
      expect(
        find.textContaining('Showing the previous snapshot.'),
        findsOneWidget,
      );
      expect(
        find.byKey(const ValueKey('active-context-msg_01')),
        findsOneWidget,
      );
      repo.failure = null;
      repo.rows = [];
      await tester.tap(find.byTooltip('Refresh active context'));
      await tester.pumpAndSettle();
      expect(
        find.text('The server returned no active context messages.'),
        findsOneWidget,
      );
      expect(
        find.textContaining('Showing the previous snapshot.'),
        findsNothing,
      );
    },
  );

  testWidgets('late context from a previous location never appears', (
    tester,
  ) async {
    final (c, repo) = await _setup();
    repo.pending = Completer<List<ActiveContextMessage>>();
    await _open(tester, c);
    c.locationRevision++;
    c.notifyListeners();
    repo.pending!.complete(_rows);
    await tester.pumpAndSettle();
    expect(find.textContaining('Reopen this inspector'), findsOneWidget);
    expect(find.text('Follow the project instructions.'), findsNothing);
    expect(repo.calls, 1);
  });

  testWidgets(
    'compact large text with open keyboard stays scrollable to results',
    (tester) async {
      final (c, _) = await _setup();
      await _open(tester, c, compact: true);
      tester.view.viewInsets = const FakeViewPadding(bottom: 260);
      addTearDown(tester.view.resetViewInsets);
      await tester.enterText(
        find.byKey(const ValueKey('active-context-search')),
        'مرحبا',
      );
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      await tester.scrollUntilVisible(
        find.byKey(const ValueKey('active-context-msg_02')),
        150,
        scrollable: find
            .descendant(
              of: find.byKey(const ValueKey('active-context-list')),
              matching: find.byType(Scrollable),
            )
            .first,
      );
      await tester.ensureVisible(
        find.byKey(const ValueKey('active-context-msg_02')),
      );
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('active-context-msg_02')).hitTestable(),
        findsOneWidget,
      );
      expect(tester.takeException(), isNull);
    },
  );
  testWidgets(
    'staged revert excludes hidden messages and history invalidation refreshes',
    (tester) async {
      final (c, repo) = await _setup();
      c.sessionsById['ses_test'] = Session(
        id: 'ses_test',
        stagedRevert: SessionRevert(messageID: 'msg_02'),
      );
      await _open(tester, c);
      expect(find.text('1 of 1 messages'), findsOneWidget);
      expect(find.byKey(const ValueKey('active-context-msg_02')), findsNothing);
      c.sessionsById['ses_test'] = Session(id: 'ses_test');
      c.history++;
      c.notifyListeners();
      await tester.pumpAndSettle();
      expect(find.text('3 of 3 messages'), findsOneWidget);
      expect(repo.calls, 2);
    },
  );
}
