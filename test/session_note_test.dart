import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api2/events.dart';
import 'package:opencode_mobile/api2/gateway_events.dart';
import 'package:opencode_mobile/api2/gateway_mappers.dart';
import 'package:opencode_mobile/api2/gateway_operations.dart';
import 'package:opencode_mobile/api2/models.dart';
import 'package:opencode_mobile/api2/transport.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/session_note_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../tool/capture/fixtures.dart' show loadCaptureFonts, captureTheme;

import 'api2_interaction_gateway_test.dart'
    show withServer, gatewayFor, writeJson, writeNoContent;

class _Notes extends ProductRepository implements SessionNoteGateway {
  String? value = 'Keep changes focused';
  final writes = <String?>[];
  Completer<void>? readGate;
  Completer<void>? writeGate;
  Object? failure;
  @override
  bool get sessionNotesSupported => true;
  @override
  Future<String?> loadSessionNote(String id) async {
    await readGate?.future;
    return value;
  }

  @override
  Future<void> saveSessionNote(String id, String note) async {
    writes.add(note);
    await writeGate?.future;
    if (failure != null) throw failure!;
    value = note;
  }

  @override
  Future<void> removeSessionNote(String id) async {
    writes.add(null);
    await writeGate?.future;
    if (failure != null) throw failure!;
    value = null;
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _Legacy extends ProductRepository {
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
}

Future<({_Controller controller, _Notes notes})> _harness() async {
  final notes = _Notes();
  final controller = _Controller(
    ProfileStore(prefs: await SharedPreferences.getInstance()),
  )..repository = notes;
  addTearDown(controller.dispose);
  return (controller: controller, notes: notes);
}

Matcher failure(SessionNoteFailure reason) => isA<SessionNoteException>()
    .having((error) => error.failure, 'failure', reason);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUp(() => SharedPreferences.setMockInitialValues({}));

  test(
    'wire operations isolate the mobile-owned key, auth and location',
    () async {
      await withServer(
        handler: (request) {
          expect(
            request.headers.value('authorization'),
            'Basic ${base64Encode(utf8.encode('opencode:pw'))}',
          );
          return request.method == 'GET'
              ? writeJson(request, {
                  'data': [
                    {
                      'key': 'server.private',
                      'value': {'secret': 'never exposed'},
                    },
                    {'key': 'mobile.note', 'value': 'Use concise explanations'},
                  ],
                })
              : writeNoContent(request);
        },
        (server, requests) async {
          final gateway = gatewayFor(server);
          addTearDown(gateway.close);
          final notes = Api2OperationsGateway(client: gateway.client);
          expect(
            await notes.loadSessionNote('ses_1'),
            'Use concise explanations',
          );
          await notes.saveSessionNote('ses_1', 'Keep spacing\n🙂');
          await notes.removeSessionNote('ses_1');
          expect(requests.map((request) => request.method), [
            'GET',
            'PUT',
            'DELETE',
          ]);
          expect(requests[1].body, {'value': 'Keep spacing\n🙂'});
          expect(requests[2].body, isNull);
          for (final request in requests) {
            expect(
              request.uri.path,
              '/api/session/ses_1/instructions/entries${request.method == 'GET' ? '' : '/mobile.note'}',
            );
            expect(request.uri.queryParameters, {
              'location[directory]': '/home/dev/projects/oc_app',
            });
          }
        },
      );
    },
  );

  test('8192 encoded bytes includes quotes, escapes and Unicode', () async {
    expect(SessionNoteGateway.encodedBytes('🙂\n"'), 10);
    await withServer(handler: writeNoContent, (server, requests) async {
      final gateway = gatewayFor(server);
      addTearDown(gateway.close);
      final notes = Api2OperationsGateway(client: gateway.client);
      await notes.saveSessionNote('ses_1', 'a' * 8190);
      expect(requests, hasLength(1));
      await expectLater(
        notes.saveSessionNote('ses_1', 'a' * 8191),
        throwsA(failure(SessionNoteFailure.tooLarge)),
      );
      await expectLater(
        notes.saveSessionNote('ses_1', '🙂' * 2048),
        throwsA(failure(SessionNoteFailure.tooLarge)),
      );
      expect(requests, hasLength(1));
    });
  });

  for (final method in ['read', 'save', 'remove']) {
    test(
      '$method preserves authorization errors without disabling capability',
      () async {
        await withServer(
          handler: (request) => writeJson(request, {
            '_tag': 'UnauthorizedError',
            'message': 'Authentication required',
          }, status: 401),
          (server, requests) async {
            final gateway = gatewayFor(server);
            addTearDown(gateway.close);
            final notes = Api2OperationsGateway(client: gateway.client);
            final operation = switch (method) {
              'read' => notes.loadSessionNote('ses_1'),
              'save' => notes.saveSessionNote('ses_1', 'draft'),
              _ => notes.removeSessionNote('ses_1'),
            };
            await expectLater(operation, throwsA(isA<Api2AuthRequired>()));
            expect(notes.sessionNotesSupported, isTrue);
          },
        );
      },
    );
  }

  test(
    'unsupported endpoint hides capability, missing session does not',
    () async {
      var missingSession = true;
      await withServer(
        handler: (request) => writeJson(request, {
          '_tag': missingSession ? 'SessionNotFoundError' : 'NotFoundError',
          'message': 'Not found',
        }, status: 404),
        (server, _) async {
          final gateway = gatewayFor(server);
          addTearDown(gateway.close);
          final notes = Api2OperationsGateway(client: gateway.client);
          final h = await _harness();
          h.controller.repository = notes;
          await expectLater(
            notes.loadSessionNote('ses_1'),
            throwsA(isA<Api2RequestError>()),
          );
          expect(h.controller.supportsSessionNotes, isTrue);
          missingSession = false;
          await expectLater(
            notes.loadSessionNote('ses_1'),
            throwsA(failure(SessionNoteFailure.unsupported)),
          );
          expect(h.controller.supportsSessionNotes, isFalse);
          h.controller.repository = _Legacy();
          expect(h.controller.supportsSessionNotes, isFalse);
        },
      );
    },
  );

  test(
    'server 413 retains its byte limit and unsupported value stays untouched',
    () async {
      var invalid = false;
      await withServer(
        handler: (request) => invalid
            ? writeJson(request, {
                'data': [
                  {
                    'key': 'mobile.note',
                    'value': {'unknown': true},
                  },
                ],
              })
            : writeJson(request, {
                '_tag': 'InstructionEntryValueTooLargeError',
                'message': 'too large',
                'maxBytes': 100,
                'actualBytes': 102,
              }, status: 413),
        (server, requests) async {
          final gateway = gatewayFor(server);
          addTearDown(gateway.close);
          final notes = Api2OperationsGateway(client: gateway.client);
          await expectLater(
            notes.saveSessionNote('ses_1', 'a' * 100),
            throwsA(
              isA<SessionNoteException>().having(
                (e) => e.maxBytes,
                'maxBytes',
                100,
              ),
            ),
          );
          invalid = true;
          await expectLater(
            notes.loadSessionNote('ses_1'),
            throwsA(failure(SessionNoteFailure.invalidValue)),
          );
          expect(requests.map((r) => r.method), ['PUT', 'GET']);
        },
      );
    },
  );

  test(
    'save re-reads before mutation and rejects another client change',
    () async {
      final h = await _harness();
      final review = await h.controller.loadSessionNote('ses_1');
      h.notes.value = 'Changed remotely';
      await expectLater(
        h.controller.saveSessionNote(review, 'my draft'),
        throwsA(failure(SessionNoteFailure.changed)),
      );
      expect(h.notes.writes, isEmpty);
      expect(h.controller.sessionNoteReceipt('ses_1'), isNull);
    },
  );

  test(
    'wake and fresh-read scope changes never write into a new location',
    () async {
      final h = await _harness();
      final review = await h.controller.loadSessionNote('ses_1');
      h.controller.wake = Completer<void>();
      final pending = h.controller.saveSessionNote(review, 'my draft');
      final check = expectLater(
        pending,
        throwsA(failure(SessionNoteFailure.changed)),
      );
      h.controller.locationRevision++;
      h.controller.wake!.complete();
      await check;
      h.controller.wake = null;
      final next = await h.controller.loadSessionNote('ses_1');
      h.notes.readGate = Completer<void>();
      final nextPending = h.controller.saveSessionNote(next, 'my draft');
      final nextCheck = expectLater(
        nextPending,
        throwsA(failure(SessionNoteFailure.changed)),
      );
      await Future<void>.delayed(Duration.zero);
      h.controller.locationRevision++;
      h.notes.readGate!.complete();
      await nextCheck;
      expect(h.notes.writes, isEmpty);
    },
  );

  test(
    'duplicate decisions serialize and only successful writes create receipts',
    () async {
      final h = await _harness();
      final review = await h.controller.loadSessionNote('ses_1');
      h.notes.writeGate = Completer<void>();
      final first = h.controller.saveSessionNote(review, 'my draft');
      await expectLater(
        h.controller.saveSessionNote(review, null),
        throwsA(failure(SessionNoteFailure.busy)),
      );
      await Future<void>.delayed(Duration.zero);
      h.notes.writeGate!.complete();
      await first;
      expect(h.notes.writes, ['my draft']);
      expect(h.controller.sessionNoteReceipt('ses_1'), isTrue);
      await expectLater(
        h.controller.saveSessionNote(review, null),
        throwsA(failure(SessionNoteFailure.changed)),
      );
      final next = await h.controller.loadSessionNote('ses_1');
      h.notes.failure = const Api2AuthRequired('Authentication required');
      await expectLater(
        h.controller.saveSessionNote(next, null),
        throwsA(isA<Api2AuthRequired>()),
      );
      expect(h.controller.sessionNoteReceipt('ses_1'), isTrue);
      h.notes.failure = null;
      await h.controller.saveSessionNote(next, null);
      expect(h.controller.sessionNoteReceipt('ses_1'), isFalse);
    },
  );

  test(
    'live and reloaded transcript notices redact instruction keys and values',
    () async {
      final h = await _harness();
      final review = await h.controller.loadSessionNote('ses_1');
      final events = Api2EventAdapter().adapt(
        Api2EventEnvelope.fromJson({
          'id': 'evt_123',
          'created': 123,
          'type': 'session.instructions.updated',
          'data': {
            'sessionID': 'ses_1',
            'delta': {
              'server.secret': {'value': 'hidden'},
              'mobile.note': 'new',
            },
            'text': 'server.secret=hidden',
          },
        }),
      );
      h.controller.handleEventForTesting(events.first);
      expect(h.controller.isSessionNoteReviewCurrent(review), isFalse);
      expect(events.map((e) => e.type), [
        'session.instructions.updated',
        'message.updated',
        'message.part.updated',
      ]);
      final serialized = jsonEncode(events.map((e) => e.properties).toList());
      expect(serialized, isNot(contains('server.secret')));
      expect(serialized, isNot(contains('hidden')));
      final mapped = mapApi2Message(
        'ses_1',
        Api2SystemMessage(
          id: 'msg_123',
          time: Api2MessageTime(created: 123),
          text: 'server.secret=hidden',
          description: 'Instructions updated: server.secret, mobile.note',
        ),
      );
      final livePart = Part.fromJson(events.last.properties['part']);
      expect(mapped.info.id, events[1].properties['info']['id']);
      expect(mapped.parts.single.text, livePart.text);
      expect(mapped.parts.single.toolName, livePart.toolName);
      expect(mapped.parts.single.filename, livePart.filename);
    },
  );

  testWidgets(
    'failed save retains draft and refresh shows remote note before replacing',
    (tester) async {
      final h = await _harness();
      tester.view.physicalSize = const Size(411, 780);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final previewKey = GlobalKey();
      if (Platform.environment['OC_NOTE_CAPTURE'] != null) {
        await loadCaptureFonts();
      }
      await tester.pumpWidget(
        RepaintBoundary(
          key: previewKey,
          child: MaterialApp(
            debugShowCheckedModeBanner: false,
            theme: captureTheme(light: true),
            home: SessionNoteScreen(
              controller: h.controller,
              sessionID: 'ses_1',
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      final previewPath = Platform.environment['OC_NOTE_CAPTURE'];
      if (previewPath != null) {
        await tester.runAsync(() async {
          final boundary =
              previewKey.currentContext!.findRenderObject()
                  as RenderRepaintBoundary;
          final image = await boundary.toImage(pixelRatio: 1);
          final png = await image.toByteData(format: ui.ImageByteFormat.png);
          await File(previewPath).writeAsBytes(png!.buffer.asUint8List());
          image.dispose();
        });
      }
      final editor = find.byKey(const ValueKey('session-note-editor'));
      await tester.enterText(editor, 'my draft');
      await tester.pump();
      h.notes.value = 'Written on desktop';
      await tester.tap(find.byKey(const ValueKey('save-session-note')));
      await tester.pumpAndSettle();
      expect(find.text('my draft'), findsOneWidget);
      expect(h.notes.writes, isEmpty);
      await tester.ensureVisible(find.text('Refresh saved note'));
      await tester.tap(find.text('Refresh saved note'));
      await tester.pumpAndSettle();
      expect(find.text('Written on desktop'), findsOneWidget);
      expect(find.text('my draft'), findsOneWidget);
    },
  );

  testWidgets(
    'authorization failure keeps the draft and successful retry returns to chat',
    (tester) async {
      final h = await _harness();
      await tester.pumpWidget(
        MaterialApp(
          home: Builder(
            builder: (context) => Scaffold(
              body: TextButton(
                onPressed: () => Navigator.push(
                  context,
                  MaterialPageRoute<void>(
                    builder: (_) => SessionNoteScreen(
                      controller: h.controller,
                      sessionID: 'ses_1',
                    ),
                  ),
                ),
                child: const Text('Chat'),
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('Chat'));
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byKey(const ValueKey('session-note-editor')),
        'Keep this draft',
      );
      await tester.pump();
      h.notes.failure = const Api2AuthRequired('Unauthorized');
      await tester.tap(find.byKey(const ValueKey('save-session-note')));
      await tester.pumpAndSettle();
      expect(find.text('Keep this draft'), findsOneWidget);
      expect(
        find.textContaining("Check this server's password"),
        findsOneWidget,
      );
      expect(h.controller.sessionNoteReceipt('ses_1'), isNull);
      h.notes.failure = null;
      await tester.ensureVisible(
        find.byKey(const ValueKey('save-session-note')),
      );
      await tester.tap(find.byKey(const ValueKey('save-session-note')));
      await tester.pumpAndSettle();
      expect(find.text('Chat'), findsOneWidget);
      expect(find.byKey(const ValueKey('session-note-editor')), findsNothing);
      expect(h.notes.value, 'Keep this draft');
      expect(h.controller.sessionNoteReceipt('ses_1'), isTrue);
    },
  );

  testWidgets(
    'compact large text scrolls, oversize save disabled and back protects draft',
    (tester) async {
      final h = await _harness();
      tester.view.physicalSize = const Size(320, 640);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      await tester.pumpWidget(
        MaterialApp(
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(
              context,
            ).copyWith(textScaler: const TextScaler.linear(1.6)),
            child: child!,
          ),
          home: Builder(
            builder: (context) => Scaffold(
              body: TextButton(
                onPressed: () => Navigator.push(
                  context,
                  MaterialPageRoute<void>(
                    builder: (_) => SessionNoteScreen(
                      controller: h.controller,
                      sessionID: 'ses_1',
                    ),
                  ),
                ),
                child: const Text('Open'),
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('Open'));
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byKey(const ValueKey('session-note-editor')),
        'a' * 8191,
      );
      await tester.pump();
      final button = tester.widget<FilledButton>(
        find.byKey(const ValueKey('save-session-note')),
      );
      expect(button.onPressed, isNull);
      expect(tester.takeException(), isNull);
      await tester.pageBack();
      await tester.pumpAndSettle();
      expect(find.text('Discard your note changes?'), findsOneWidget);
      await tester.tap(find.text('Keep editing'));
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('session-note-editor')), findsOneWidget);
      expect(h.notes.writes, isEmpty);
    },
  );
}
