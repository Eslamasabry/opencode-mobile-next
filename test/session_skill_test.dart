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
import 'package:opencode_mobile/api2/gateway_operations.dart';
import 'package:opencode_mobile/api2/transport.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/library_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../tool/capture/fixtures.dart' show captureTheme, loadCaptureFonts;
import 'api2_interaction_gateway_test.dart'
    show withServer, gatewayFor, writeJson, writeNoContent;

const _skill = SkillInfo(
  id: 'review-id',
  name: 'Focused review',
  description: 'Review a change for correctness and readability.',
  location: '/project/.opencode/skills/review/SKILL.md',
  content:
      '# Focused review\n\nReview the current change.\n\n- Explain concrete issues.\n- Keep the patch focused.\n- Preserve the author’s intent.',
  slashCommand: false,
);

class _Skills extends ProductRepository implements SessionSkillGateway {
  final calls = <(String, String, bool)>[];
  Object? failure;
  Completer<void>? gate;
  Completer<void>? readGate;
  Session session = Session(id: 'ses_test', title: 'Composer improvements');
  @override
  bool get sessionSkillsSupported => true;
  @override
  Future<List<SkillInfo>> listSkills() async => [_skill];
  @override
  Future<Session> getSessionDetails(String id) async {
    await readGate?.future;
    return session;
  }

  @override
  Future<void> activateSessionSkill(
    String sessionID,
    String skillID, {
    required bool resume,
  }) async {
    calls.add((sessionID, skillID, resume));
    await gate?.future;
    if (failure != null) throw failure!;
  }

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

Future<(_Controller, _Skills)> _setup() async {
  final repo = _Skills();
  final c = _Controller(
    ProfileStore(prefs: await SharedPreferences.getInstance()),
  )..repository = repo;
  c.sessionsById['ses_test'] = repo.session;
  addTearDown(c.dispose);
  return (c, repo);
}

Future<void> _open(
  WidgetTester tester,
  _Controller c, {
  double width = 411,
}) async {
  tester.view.physicalSize = Size(width, 891);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
  if (Platform.environment['OC_SKILL_PREVIEW'] != null) {
    await tester.runAsync(loadCaptureFonts);
  }
  await tester.pumpWidget(
    RepaintBoundary(
      key: const Key('skill-preview-root'),
      child: MaterialApp(
        theme: captureTheme(),
        debugShowCheckedModeBanner: false,
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(
            context,
          ).copyWith(textScaler: TextScaler.linear(width == 320 ? 1.7 : 1)),
          child: child!,
        ),
        home: Builder(
          builder: (context) => Scaffold(
            body: TextButton(
              onPressed: () => Navigator.of(context).push(
                MaterialPageRoute<bool>(
                  builder: (_) =>
                      SkillsScreen(controller: c, sessionID: 'ses_test'),
                ),
              ),
              child: const Text('Open skills'),
            ),
          ),
        ),
      ),
    ),
  );
  await tester.tap(find.text('Open skills'));
  await tester.pumpAndSettle();
  await tester.tap(find.text('Focused review'));
  await tester.pumpAndSettle();
}

Matcher _failure(SessionSkillFailure value) =>
    isA<SessionSkillException>().having((e) => e.failure, 'failure', value);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUp(() => SharedPreferences.setMockInitialValues({}));

  test(
    'catalog identity and activation body retain scope and explicit resume',
    () async {
      await withServer(
        (server, requests) async {
          final gateway = gatewayFor(server);
          addTearDown(gateway.close);
          final ops = Api2OperationsGateway(client: gateway.client);
          final skill = (await ops.listSkills()).single;
          expect(skill.id, 'wire-id');
          expect(skill.name, 'Display name');
          await ops.activateSessionSkill(
            'ses_target',
            skill.id!,
            resume: false,
          );
          await ops.activateSessionSkill('ses_target', skill.id!, resume: true);
          expect(
            requests.first.uri.queryParameters['location[directory]'],
            '/home/dev/projects/oc_app',
          );
          for (final request in requests.skip(1)) {
            expect(request.uri.path, '/api/session/ses_target/skill');
            expect(request.uri.queryParameters, isEmpty);
          }
          expect(requests[1].body, {'skill': 'wire-id', 'resume': false});
          expect(requests[2].body, {'skill': 'wire-id', 'resume': true});
        },
        handler: (request) {
          expect(
            request.headers.value('authorization'),
            'Basic ${base64Encode(utf8.encode('opencode:pw'))}',
          );
          return request.method == 'GET'
              ? writeJson(request, {
                  'data': [
                    {'id': 'wire-id', 'name': 'Display name'},
                  ],
                })
              : writeNoContent(request);
        },
      );
    },
  );

  test(
    'known missing skill does not disable activation; server failures are uncertain',
    () async {
      var status = 404;
      var tag = 'SkillNotFoundError';
      await withServer(
        (server, requests) async {
          final gateway = gatewayFor(server);
          addTearDown(gateway.close);
          final ops = Api2OperationsGateway(client: gateway.client);
          Future<void> send() =>
              ops.activateSessionSkill('ses_test', 'review', resume: false);
          await expectLater(send(), throwsA(isA<Api2Error>()));
          expect(ops.sessionSkillsSupported, isTrue);
          status = 500;
          tag = 'InternalError';
          await expectLater(
            send(),
            throwsA(_failure(SessionSkillFailure.uncertain)),
          );
          expect(ops.sessionSkillsSupported, isTrue);
          status = 405;
          await expectLater(
            send(),
            throwsA(_failure(SessionSkillFailure.unsupported)),
          );
          expect(ops.sessionSkillsSupported, isFalse);
          expect(requests.length, 3);
        },
        handler: (request) => writeJson(request, {'_tag': tag}, status: status),
      );
    },
  );

  test(
    'location change during wake or session read prevents dispatch',
    () async {
      final (c, repo) = await _setup();
      c.wake = Completer<void>();
      final pending = c.activateSessionSkill(
        'ses_test',
        'review',
        resume: false,
        expectedLocation: c.locationRevision,
      );
      final expected = expectLater(
        pending,
        throwsA(_failure(SessionSkillFailure.changed)),
      );
      c.locationRevision++;
      c.wake!.complete();
      await expected;
      c.wake = null;
      repo.readGate = Completer<void>();
      final read = c.activateSessionSkill(
        'ses_test',
        'review',
        resume: false,
        expectedLocation: c.locationRevision,
      );
      final readExpected = expectLater(
        read,
        throwsA(_failure(SessionSkillFailure.changed)),
      );
      await Future<void>.delayed(Duration.zero);
      c.locationRevision++;
      repo.readGate!.complete();
      await readExpected;
      expect(repo.calls, isEmpty);
    },
  );

  test('fresh staged revert and concurrent activation are guarded', () async {
    final (c, repo) = await _setup();
    repo.session = Session(id: 'ses_test', reverted: true);
    Future<void> send() => c.activateSessionSkill(
      'ses_test',
      'review',
      resume: false,
      expectedLocation: c.locationRevision,
    );
    await expectLater(send(), throwsA(_failure(SessionSkillFailure.staged)));
    expect(repo.calls, isEmpty);
    repo.session = Session(id: 'ses_test');
    repo.gate = Completer<void>();
    final pending = send();
    await expectLater(send(), throwsA(_failure(SessionSkillFailure.busy)));
    repo.gate!.complete();
    await pending;
    expect(repo.calls, [('ses_test', 'review', false)]);
  });

  test(
    'skill events request a refresh without exposing skill instruction text',
    () {
      final envelope = Api2EventEnvelope.fromJson({
        'type': 'session.skill.activated',
        'data': {
          'sessionID': 'ses_test',
          'name': 'review',
          'text': 'private instructions',
        },
      });
      final event = Api2EventAdapter().adapt(envelope).single;
      expect(event.type, 'session.skill.changed');
      expect(event.properties, {'sessionID': 'ses_test'});
    },
  );

  testWidgets(
    'review permits add without run and returns to the conversation',
    (tester) async {
      final (c, repo) = await _setup();
      await _open(tester, c);
      expect(find.text('Composer improvements'), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('skill-resume')));
      await tester.tap(find.byKey(const ValueKey('skill-activate')));
      await tester.pumpAndSettle();
      expect(repo.calls, [('ses_test', 'review-id', false)]);
      expect(find.text('Open skills'), findsOneWidget);
      await tester.pumpWidget(const SizedBox());
    },
  );

  testWidgets(
    'uncertain activation keeps review and disables repeat submission',
    (tester) async {
      final (c, repo) = await _setup();
      repo.failure = const SessionSkillException(SessionSkillFailure.uncertain);
      await _open(tester, c);
      await tester.tap(find.byKey(const ValueKey('skill-activate')));
      await tester.pumpAndSettle();
      expect(
        find.textContaining('The skill may have been added.'),
        findsOneWidget,
      );
      expect(
        tester
            .widget<FilledButton>(find.byKey(const ValueKey('skill-activate')))
            .onPressed,
        isNull,
      );
      expect(repo.calls.length, 1);
      await tester.pumpWidget(const SizedBox());
    },
  );

  testWidgets('compact enlarged-text review keeps activation reachable', (
    tester,
  ) async {
    final (c, repo) = await _setup();
    await _open(tester, c, width: 320);
    final button = find.byKey(const ValueKey('skill-activate'));
    await tester.ensureVisible(button);
    await tester.pumpAndSettle();
    expect(button.hitTestable(), findsOneWidget);
    await tester.tap(button);
    await tester.pumpAndSettle();
    expect(repo.calls.single.$3, isTrue);
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox());
  });

  testWidgets('phone skill preview', (tester) async {
    final (c, _) = await _setup();
    await _open(tester, c);
    final path = Platform.environment['OC_SKILL_PREVIEW'];
    if (path != null) {
      await tester.runAsync(() async {
        final boundary = tester.renderObject<RenderRepaintBoundary>(
          find.byKey(const Key('skill-preview-root')),
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
