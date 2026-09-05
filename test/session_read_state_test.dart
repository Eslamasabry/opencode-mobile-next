import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api2/events.dart';
import 'package:opencode_mobile/api2/gateway_events.dart';
import 'package:opencode_mobile/api2/gateway_mappers.dart';
import 'package:opencode_mobile/api2/models.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
import 'package:opencode_mobile/ui/widgets/session_read_state.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _Repository extends ProductRepository implements SessionReadStateGateway {
  final views = <(String, int)>[];
  Completer<void>? write;
  bool fail = false;
  @override
  Future<void> viewSession(String id, int idle) async {
    views.add((id, idle));
    await write?.future;
    if (fail) throw ApiException('Read sync unavailable');
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _Api extends OpenCodeApi {
  _Api() : super(baseUrl: 'http://localhost');
  Session value = _session();
  @override
  Future<Session> session(String id) async => value;
  @override
  Future<List<Session>> sessions() async => [value];
  @override
  Future<Map<String, String>> sessionStatuses() async => {'ses_1': 'idle'};
}

class _Controller extends ConnectionController {
  _Controller(super.store);
  Completer<void>? wake;
  void recover(_Repository next) {
    repository = next;
    connectionRevision++;
    notifyListeners();
  }

  @override
  Future<ServerOperationsGateway?> prepareActionRepository() async {
    await wake?.future;
    return repository;
  }
}

Session _session({int idle = 100, int viewed = 20}) => Session(
  id: 'ses_1',
  title: 'A completed run',
  directory: '/work',
  time: SessionTime(created: 1, updated: 100, idle: idle, viewed: viewed),
);

Future<({_Controller controller, _Repository repository, _Api api})>
_harness() async {
  final repository = _Repository();
  final api = _Api();
  final controller =
      _Controller(ProfileStore(prefs: await SharedPreferences.getInstance()))
        ..repository = repository
        ..api = api
        ..directory = '/work';
  controller.sessionsById['ses_1'] = _session();
  addTearDown(controller.dispose);
  return (controller: controller, repository: repository, api: api);
}

void _viewed(ConnectionController controller, int idle) =>
    controller.handleEventForTesting(
      EventEnvelope(
        type: 'session.viewed',
        properties: {'sessionID': 'ses_1', 'idle': idle},
      ),
    );

Future<void> _flush() => Future<void>.delayed(Duration.zero);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUp(() => SharedPreferences.setMockInitialValues({}));

  testWidgets('an unread transcript waits until its visible content is ready', (
    tester,
  ) async {
    final h = await _harness();
    Widget app(bool ready) => MaterialApp(
      home: SessionViewObserver(
        controller: h.controller,
        sessionID: 'ses_1',
        ready: ready,
        child: const Scaffold(body: Text('Transcript')),
      ),
    );
    await tester.pumpWidget(app(false));
    await tester.pumpAndSettle();
    expect(h.repository.views, isEmpty);
    await tester.pumpWidget(app(true));
    await tester.pumpAndSettle();
    expect(h.repository.views, [('ses_1', 100)]);
  });

  test(
    'a deferred rendered receipt retains its completion and location',
    () async {
      final h = await _harness();
      h.controller.sessionsById['ses_1'] = _session(idle: 200);
      await h.controller.viewSession(
        'ses_1',
        isForeground: () => true,
        observedIdle: 100,
        expectedLocationRevision: h.controller.locationRevision,
      );
      expect(h.repository.views, [('ses_1', 100)]);
      expect(
        h.controller.isSessionUnread(h.controller.sessionsById['ses_1']!),
        isTrue,
      );
      final oldLocation = h.controller.locationRevision++;
      await h.controller.viewSession(
        'ses_1',
        isForeground: () => true,
        observedIdle: 200,
        expectedLocationRevision: oldLocation,
      );
      expect(h.repository.views, hasLength(1));
      expect(
        h.controller.isSessionUnread(h.controller.sessionsById['ses_1']!),
        isTrue,
      );
    },
  );

  testWidgets('a visible chat retries read sync after reconnect', (
    tester,
  ) async {
    final h = await _harness();
    h.repository.fail = true;
    await tester.pumpWidget(
      MaterialApp(
        home: SessionViewObserver(
          controller: h.controller,
          sessionID: 'ses_1',
          ready: true,
          child: const Scaffold(body: Text('Transcript')),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(h.repository.views, hasLength(1));
    final replacement = _Repository();
    h.controller.recover(replacement);
    await tester.pumpAndSettle();
    expect(replacement.views, [('ses_1', 100)]);
    expect(h.controller.sessionsById['ses_1']!.time!.viewed, 100);
  });

  test('v2 mapping and viewed events retain the exact idle watermark', () {
    final mapped = mapApi2Session(
      Api2Session.fromJson({
        'id': 'ses_1',
        'time': {'created': 1, 'updated': 200, 'idle': 180, 'viewed': 140},
      })!,
    );
    expect(mapped.time!.idle, 180);
    expect(mapped.time!.viewed, 140);
    final event = Api2EventAdapter()
        .adapt(
          Api2EventEnvelope.fromJson({
            'type': 'session.viewed',
            'data': {'sessionID': 'ses_1', 'idle': 180},
          }),
        )
        .single;
    expect(event.type, 'session.viewed');
    expect(event.properties, {'sessionID': 'ses_1', 'idle': 180});
  });

  test(
    'refresh and remote viewed events synchronize without sending receipts',
    () async {
      final h = await _harness();
      await h.controller.refreshSessions();
      expect(
        h.controller.isSessionUnread(h.controller.sessionsById['ses_1']!),
        isTrue,
      );
      _viewed(h.controller, 100);
      expect(
        h.controller.isSessionUnread(_session()),
        isFalse,
        reason: 'a cached global result follows a newer matching viewed event',
      );
      await h.controller.refreshSessions();
      expect(
        h.controller.sessionsById['ses_1']!.time!.viewed,
        100,
        reason: 'a stale snapshot cannot rewind an acknowledged watermark',
      );
      expect(h.repository.views, isEmpty);
    },
  );

  test(
    'a receipt acknowledges only the observed run when a newer one finishes',
    () async {
      final h = await _harness();
      final write = Completer<void>();
      h.repository.write = write;
      final view = h.controller.viewSession('ses_1', isForeground: () => true);
      await _flush();
      h.controller.sessionsById['ses_1'] = _session(idle: 200);
      write.complete();
      await view;
      expect(h.repository.views, [('ses_1', 100)]);
      expect(h.controller.sessionsById['ses_1']!.time!.viewed, 100);
      expect(
        h.controller.isSessionUnread(h.controller.sessionsById['ses_1']!),
        isTrue,
      );
    },
  );

  for (final change in ['background', 'location', 'privacy', 'busy']) {
    test('$change during wake prevents a server receipt', () async {
      final h = await _harness();
      final wake = Completer<void>();
      h.controller.wake = wake;
      var visible = true;
      final view = h.controller.viewSession(
        'ses_1',
        isForeground: () => visible,
      );
      await _flush();
      switch (change) {
        case 'background':
          visible = false;
        case 'location':
          h.controller.locationRevision++;
        case 'privacy':
          await h.controller.setShareSessionViews(false);
        case 'busy':
          h.controller.busySessions.add('ses_1');
      }
      wake.complete();
      await view;
      expect(h.repository.views, isEmpty);
    });
  }

  test(
    'private read state persists locally and ignores another client receipts',
    () async {
      final h = await _harness();
      await h.controller.setShareSessionViews(false);
      _viewed(h.controller, 100);
      expect(
        h.controller.isSessionUnread(h.controller.sessionsById['ses_1']!),
        isTrue,
      );
      await h.controller.viewSession('ses_1', isForeground: () => true);
      expect(h.repository.views, isEmpty);
      final reopened = await _harness();
      expect(reopened.controller.shareSessionViews, isFalse);
      expect(reopened.controller.isSessionUnread(_session()), isFalse);
      expect(reopened.controller.isSessionUnread(_session(idle: 200)), isTrue);
      reopened.controller.directory = '/different';
      expect(
        reopened.controller.isSessionUnread(
          Session(
            id: 'ses_1',
            directory: '/different',
            time: SessionTime(idle: 100),
          ),
        ),
        isTrue,
      );
    },
  );

  test(
    'a newly opened viewer can finish after a covered viewer cancels',
    () async {
      final h = await _harness();
      final wake = Completer<void>();
      h.controller.wake = wake;
      var firstVisible = true;
      final first = h.controller.viewSession(
        'ses_1',
        isForeground: () => firstVisible,
      );
      await _flush();
      firstVisible = false;
      final second = h.controller.viewSession(
        'ses_1',
        isForeground: () => true,
      );
      wake.complete();
      await Future.wait([first, second]);
      expect(h.repository.views, [('ses_1', 100)]);
    },
  );

  test(
    'failed server sync keeps local read state and retries only on explicit observation',
    () async {
      final h = await _harness();
      h.repository.fail = true;
      await expectLater(
        h.controller.viewSession('ses_1', isForeground: () => true),
        throwsA(isA<ApiException>()),
      );
      expect(h.controller.isSessionUnread(_session()), isFalse);
      await h.controller.refreshSessions();
      expect(h.repository.views, hasLength(1));
      h.repository.fail = false;
      await h.controller.viewSession('ses_1', isForeground: () => true);
      expect(h.repository.views, hasLength(2));
      expect(h.controller.sessionsById['ses_1']!.time!.viewed, 100);
    },
  );

  testWidgets(
    'covered chat stays unread until it becomes the foreground route',
    (tester) async {
      final h = await _harness();
      final navigator = GlobalKey<NavigatorState>();
      await tester.pumpWidget(
        MaterialApp(navigatorKey: navigator, home: const Scaffold()),
      );
      navigator.currentState!.push(
        MaterialPageRoute<void>(
          builder: (_) => SessionViewObserver(
            controller: h.controller,
            sessionID: 'ses_1',
            ready: true,
            child: const Scaffold(body: Text('Transcript')),
          ),
        ),
      );
      navigator.currentState!.push(
        MaterialPageRoute<void>(
          builder: (_) => const Scaffold(body: Text('Settings')),
        ),
      );
      await tester.pumpAndSettle();
      expect(h.repository.views, isEmpty);
      navigator.currentState!.pop();
      await tester.pumpAndSettle();
      expect(h.repository.views, [('ses_1', 100)]);
    },
  );

  testWidgets('backgrounded chat sends nothing and observes on resume', (
    tester,
  ) async {
    final h = await _harness();
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
    addTearDown(
      () => tester.binding.handleAppLifecycleStateChanged(
        AppLifecycleState.resumed,
      ),
    );
    await tester.pumpWidget(
      MaterialApp(
        home: SessionViewObserver(
          controller: h.controller,
          sessionID: 'ses_1',
          ready: true,
          child: const Scaffold(body: Text('Transcript')),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(h.repository.views, isEmpty);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    await tester.pumpAndSettle();
    expect(h.repository.views, [('ses_1', 100)]);
  });

  testWidgets('unread status is text and reacts to cross-client receipts', (
    tester,
  ) async {
    final h = await _harness();
    final semantics = tester.ensureSemantics();
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SessionUnreadBadge(
            controller: h.controller,
            session: _session(),
          ),
        ),
      ),
    );
    expect(find.bySemanticsLabel('Unread result'), findsOneWidget);
    _viewed(h.controller, 100);
    await tester.pump();
    expect(find.text('Unread result'), findsNothing);
    semantics.dispose();
  });

  testWidgets('privacy control is available only with the v2 capability', (
    tester,
  ) async {
    final h = await _harness();
    await tester.pumpWidget(
      MaterialApp(home: PrivacySettingsScreen(controller: h.controller)),
    );
    expect(find.byKey(const ValueKey('share-session-views')), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('share-session-views')));
    await tester.pumpAndSettle();
    expect(h.controller.shareSessionViews, isFalse);
    h.controller.repository = null;
    _viewed(h.controller, 100);
    await tester.pump();
    expect(find.byKey(const ValueKey('share-session-views')), findsNothing);
  });
}
