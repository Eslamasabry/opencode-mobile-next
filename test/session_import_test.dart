import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api2/gateway_operations.dart';
import 'package:opencode_mobile/api2/transport.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/session_import_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'api2_interaction_gateway_test.dart'
    show withServer, gatewayFor, writeJson;
import '../tool/capture/fixtures.dart' show loadCaptureFonts, captureTheme;

Map<String, dynamic> transfer({bool redacted = false, String? parent}) => {
  'info': {
    'id': 'ses_transfer',
    'projectID': 'source_project',
    'title': 'Transfer العربية',
    'location': {'directory': '/source/private'},
    'time': {'created': 1, 'updated': 2},
    'cost': 0,
    'tokens': {
      'input': 0,
      'output': 0,
      'reasoning': 0,
      'cache': {'read': 0, 'write': 0},
    },
    'parentID': ?parent,
  },
  'messages': [
    {
      'id': 'msg_user',
      'type': 'user',
      'time': {'created': 1},
      'text': redacted ? '[redacted:text:msg_user]' : 'Hello العربية',
    },
    {
      'id': 'msg_location',
      'type': 'location-switched',
      'time': {'created': 2},
      'location': {'directory': '/source/private'},
      'subpath': 'src',
    },
  ],
};

class _Imports extends ProductRepository implements SessionImportGateway {
  final writes = <Map<String, dynamic>>[];
  Completer<void>? gate;
  Object? error;
  @override
  bool get sessionImportSupported => true;
  @override
  Future<Session> importSession(
    SessionImportDocument document,
    SessionImportDestination destination,
  ) async {
    writes.add(document.requestBody(destination));
    await gate?.future;
    if (error != null) throw error!;
    return Session(
      id: document.id,
      title: document.title,
      directory: destination.directory,
      workspaceID: destination.workspaceID,
    );
  }

  @override
  Future<List<WorkspaceProject>> listProjects() async => [
    const WorkspaceProject(
      id: 'dest',
      name: 'Destination project',
      directory: '/destination',
      worktrees: ['/worktree'],
      updatedAt: 1,
    ),
  ];
  @override
  Future<List<WorkspaceInfo>> listWorkspaces() async => [];
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _Controller extends ConnectionController {
  _Controller(super.store);
  Completer<void>? wake;
  @override
  Future<ServerOperationsGateway?> prepareActionRepository() async {
    await wake?.future;
    return repository;
  }

  @override
  Future<void> refreshSessions() async {}
  @override
  Future<void> selectLocation({String? directory, String? workspace}) async {
    this.directory = directory;
    this.workspace = workspace;
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUp(() => SharedPreferences.setMockInitialValues({}));

  test(
    'streamed export envelope and bare payload preserve Unicode and all records',
    () async {
      final payload = transfer();
      final bytes = utf8.encode(jsonEncode({'data': payload}));
      final doc = await SessionImportDocument.read(
        Stream.fromIterable([
          for (var i = 0; i < bytes.length; i++) [bytes[i]],
        ]),
      );
      final bare = SessionImportDocument.fromJson(payload);
      expect(doc.title, 'Transfer العربية');
      expect(doc.messageCount, 2);
      expect(bare.id, doc.id);
      final request = doc.requestBody(
        const SessionImportDestination(
          directory: '/destination',
          workspaceID: 'wrk_dest',
        ),
      );
      expect(request['messages'], payload['messages']);
      expect(request['info'], payload['info']);
      expect(request['location'], {
        'directory': '/destination',
        'workspaceID': 'wrk_dest',
      });
      expect((payload['info'] as Map)['location'], {
        'directory': '/source/private',
      });
    },
  );

  test('identifies redaction and parent without discarding the source', () {
    final doc = SessionImportDocument.fromJson(
      transfer(redacted: true, parent: 'ses_parent'),
    );
    expect(doc.hasRedactions, isTrue);
    expect(doc.parentID, 'ses_parent');
    expect(doc.messageCount, 2);
  });

  test(
    'malformed identifying data and duplicate messages are rejected',
    () async {
      final bad = transfer();
      (bad['messages'] as List).add((bad['messages'] as List).first);
      for (final value in [
        null,
        [],
        {'messages': []},
        {'data': transfer(), 'extra': true},
        bad,
      ]) {
        expect(
          () => SessionImportDocument.fromJson(value),
          throwsA(isA<SessionImportInvalid>()),
        );
      }
      await expectLater(
        SessionImportDocument.read(Stream.value(utf8.encode('# Markdown'))),
        throwsA(isA<SessionImportInvalid>()),
      );
      expect(
        () => SessionImportDocument.fromJson(
          transfer(),
        ).requestBody(const SessionImportDestination(directory: ' ')),
        throwsA(isA<SessionImportInvalid>()),
      );
    },
  );

  test(
    'import wire body retains source IDs and explicit destination with no query',
    () async {
      await withServer(
        (server, requests) async {
          final connection = gatewayFor(server);
          addTearDown(connection.close);
          final gateway = Api2OperationsGateway(client: connection.client);
          final session = await gateway.importSession(
            SessionImportDocument.fromJson(transfer()),
            const SessionImportDestination(
              directory: '/destination',
              workspaceID: 'wrk_dest',
            ),
          );
          expect(session.id, 'ses_transfer');
          expect(session.directory, '/destination');
          expect(requests.single.method, 'POST');
          expect(requests.single.uri.path, '/api/session/import');
          expect(requests.single.uri.query, isEmpty);
          expect(requests.single.body['location'], {
            'directory': '/destination',
            'workspaceID': 'wrk_dest',
          });
          expect(requests.single.body['info'], transfer()['info']);
          expect(requests.single.body['messages'], transfer()['messages']);
        },
        handler: (request) => writeJson(request, {
          'data': {
            ...(transfer()['info'] as Map<String, dynamic>),
            'projectID': 'target',
            'location': {
              'directory': '/destination',
              'workspaceID': 'wrk_dest',
            },
          },
        }),
      );
    },
  );

  for (final status in [400, 401, 404, 409, 500]) {
    test(
      'import $status keeps capability and never retries or mutates IDs',
      () async {
        await withServer(
          (server, requests) async {
            final connection = gatewayFor(server);
            addTearDown(connection.close);
            final gateway = Api2OperationsGateway(client: connection.client);
            await expectLater(
              gateway.importSession(
                SessionImportDocument.fromJson(transfer()),
                const SessionImportDestination(directory: '/destination'),
              ),
              throwsA(
                isA<Api2Error>().having((e) => e.statusCode, 'status', status),
              ),
            );
            expect(gateway.sessionImportSupported, isTrue);
            expect(requests, hasLength(1));
          },
          handler: (request) => writeJson(request, {
            '_tag': status == 404 ? 'SessionNotFoundError' : 'Failure',
          }, status: status),
        );
      },
    );
  }

  Future<(_Controller, _Imports)> screen(
    WidgetTester tester, {
    bool redacted = false,
    double? width,
    Future<SessionImportFile?> Function()? pick,
  }) async {
    if (width != null) {
      tester.view.physicalSize = Size(width, 891);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      await tester.runAsync(loadCaptureFonts);
    }
    final repo = _Imports();
    final controller =
        _Controller(ProfileStore(prefs: await SharedPreferences.getInstance()))
          ..repository = repo
          ..directory = '/current';
    addTearDown(controller.dispose);
    final bytes = utf8.encode(
      jsonEncode({'data': transfer(redacted: redacted)}),
    );
    await tester.pumpWidget(
      MaterialApp(
        theme: width == null ? null : captureTheme(light: true),
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(
            context,
          ).copyWith(textScaler: TextScaler.linear(width == 320 ? 1.7 : 1)),
          child: child!,
        ),
        routes: {
          '/chat/ses_transfer': (_) =>
              const Scaffold(body: Text('Imported chat')),
        },
        home: RepaintBoundary(
          key: const ValueKey('import-preview'),
          child: SessionImportScreen(
            controller: controller,
            pickFile:
                pick ??
                () async => SessionImportFile(
                  name: 'conversation.json',
                  length: () async => bytes.length,
                  read: () => Stream.value(bytes),
                ),
          ),
        ),
      ),
    );
    return (controller, repo);
  }

  Future<void> choose(WidgetTester tester) async {
    await tester.tap(find.text('Choose JSON file'));
    await tester.pumpAndSettle();
  }

  testWidgets(
    'review precedes import, conflicts retain file and chosen destination',
    (tester) async {
      final (_, repo) = await screen(tester, redacted: true);
      await choose(tester);
      expect(repo.writes, isEmpty);
      final reviewScroll = find
          .descendant(
            of: find.byKey(const ValueKey('import-review-scroll')),
            matching: find.byType(Scrollable),
          )
          .first;
      await tester.scrollUntilVisible(
        find.text('Change destination'),
        200,
        scrollable: reviewScroll,
      );
      await tester.ensureVisible(find.text('Change destination'));
      await tester.tap(find.text('Change destination'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('/destination'));
      await tester.pumpAndSettle();
      repo.error = const Api2RequestError('Conflict', statusCode: 409);
      await tester.tap(
        find.widgetWithText(FilledButton, 'Import conversation'),
      );
      await tester.pumpAndSettle();
      expect(repo.writes.single['location'], {'directory': '/destination'});
      expect(find.textContaining('already exists'), findsOneWidget);
      await tester.drag(reviewScroll, const Offset(0, 1200));
      await tester.pumpAndSettle();
      expect(find.text('conversation.json'), findsOneWidget);
      expect(find.textContaining('redacted placeholders'), findsOneWidget);
    },
  );

  testWidgets(
    'successful import cannot be submitted twice and opens returned location',
    (tester) async {
      final (controller, repo) = await screen(tester);
      await choose(tester);
      await tester.tap(
        find.widgetWithText(FilledButton, 'Import conversation'),
      );
      await tester.pumpAndSettle();
      expect(find.text('Conversation imported'), findsOneWidget);
      expect(
        find.widgetWithText(FilledButton, 'Import conversation'),
        findsNothing,
      );
      await tester.tap(find.text('Open conversation'));
      await tester.pumpAndSettle();
      expect(find.text('Imported chat'), findsOneWidget);
      expect(controller.directory, '/current');
      expect(repo.writes, hasLength(1));
    },
  );

  testWidgets(
    'connection change during wake prevents a write and retains file',
    (tester) async {
      final (controller, repo) = await screen(tester);
      await choose(tester);
      controller.wake = Completer<void>();
      await tester.tap(
        find.widgetWithText(FilledButton, 'Import conversation'),
      );
      await tester.pump();
      controller.repository = _Imports();
      controller.wake!.complete();
      await tester.pumpAndSettle();
      expect(repo.writes, isEmpty);
      expect(find.text('conversation.json'), findsOneWidget);
      expect(
        find.textContaining('connection or location changed'),
        findsOneWidget,
      );
    },
  );

  testWidgets('oversize file is never read or uploaded', (tester) async {
    final (_, repo) = await screen(
      tester,
      pick: () async => SessionImportFile(
        name: 'large.json',
        length: () async => SessionImportDocument.maxBytes + 1,
        read: () => throw StateError('Must not read'),
      ),
    );
    await choose(tester);
    expect(find.textContaining('128 MiB'), findsOneWidget);
    expect(repo.writes, isEmpty);
    expect(
      tester
          .widget<FilledButton>(
            find.widgetWithText(FilledButton, 'Import conversation'),
          )
          .onPressed,
      isNull,
    );
  });

  for (final width in [411.0, 320.0]) {
    testWidgets('import review and action fit at $width', (tester) async {
      await screen(tester, width: width, redacted: true);
      await choose(tester);
      expect(
        find.widgetWithText(FilledButton, 'Import conversation').hitTestable(),
        findsOneWidget,
      );
      final preview = Platform.environment['OC_IMPORT_PREVIEW'];
      if (preview != null && width == 411) {
        await tester.runAsync(() async {
          final boundary = tester.renderObject<RenderRepaintBoundary>(
            find.byKey(const ValueKey('import-preview')),
          );
          final image = await boundary.toImage(pixelRatio: 1);
          final data = await image.toByteData(format: ui.ImageByteFormat.png);
          await File(preview).writeAsBytes(data!.buffer.asUint8List());
          image.dispose();
        });
      }
      final scroll = find
          .descendant(
            of: find.byKey(const ValueKey('import-review-scroll')),
            matching: find.byType(Scrollable),
          )
          .first;
      await tester.scrollUntilVisible(
        find.text('Change destination'),
        200,
        scrollable: scroll,
      );
      await tester.ensureVisible(find.text('Change destination'));
      await tester.pumpAndSettle();
      expect(find.text('Change destination').hitTestable(), findsOneWidget);
      expect(tester.takeException(), isNull);
    });
  }
}
