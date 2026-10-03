import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/session_pins.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:shared_preferences_platform_interface/shared_preferences_platform_interface.dart';

class _RefusingStore extends InMemorySharedPreferencesStore {
  _RefusingStore() : super.withData({});
  bool refuse = true;
  @override
  Future<bool> setValue(String valueType, String key, Object value) async =>
      refuse ? false : super.setValue(valueType, key, value);
}

class _Controller extends ConnectionController {
  _Controller(super.store);
  @override
  ServerProfile get profile =>
      ServerProfile(id: 'server-a', name: 'A', baseUrl: 'http://localhost');
  @override
  Future<ServerOperationsGateway?> prepareActionRepository() async =>
      repository;
}

class _Api extends OpenCodeApi {
  _Api() : super(baseUrl: 'http://localhost');
  final requests = <String>[];
  Completer<Session>? delayed;
  Object? error;
  @override
  Future<ServerPage<Session>> sessionPage({
    String? cursor,
    int limit = 100,
  }) async =>
      ServerPage(items: [_session('new', 100)], nextCursor: 'older-page');
  @override
  Future<Map<String, String>> sessionStatuses() async => {};
  @override
  Future<Session> session(String id) async {
    requests.add(id);
    if (error != null) throw error!;
    return delayed == null ? _session(id, 1) : await delayed!.future;
  }
}

Session _session(String id, int updated) => Session(
  id: id,
  title: id,
  directory: '/one',
  time: SessionTime(created: 1, updated: updated),
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUp(() => SharedPreferences.setMockInitialValues({}));

  test(
    'pins survive reload and serialize writes without crossing profile or location',
    () async {
      final prefs = await SharedPreferences.getInstance();
      final pins = SessionPinStore(prefs);
      final scope = SessionPinStore.scope('/one', null);
      final other = SessionPinStore.scope('/two', null);
      await Future.wait([
        pins.setPinned('a', scope, 'ses_first', true),
        pins.setPinned('a', scope, 'ses_second', true),
        pins.setPinned('a', scope, 'ses_first', false),
        pins.setPinned('a', other, 'ses_elsewhere', true),
        pins.setPinned('b', scope, 'ses_other_server', true),
      ]);
      final reloaded = SessionPinStore(prefs);
      expect(reloaded.ids('a', scope), {'ses_second'});
      expect(reloaded.ids('a', other), {'ses_elsewhere'});
      expect(reloaded.ids('b', scope), {'ses_other_server'});
      final profiles = ProfileStore(prefs: prefs);
      expect(await profiles.removeScopedPreferences('a'), isEmpty);
      reloaded.forget('a');
      expect(reloaded.ids('a', scope), isEmpty);
      expect(reloaded.ids('b', scope), {'ses_other_server'});
    },
  );

  test(
    'failed storage is not shown as saved and later writes recover',
    () async {
      final backend = _RefusingStore();
      SharedPreferencesStorePlatform.instance = backend;
      final pins = SessionPinStore(await SharedPreferences.getInstance());
      await expectLater(
        pins.setPinned('a', 'scope', 'ses_one', true),
        throwsStateError,
      );
      expect(pins.ids('a', 'scope'), isEmpty);
      backend.refuse = false;
      await pins.setPinned('a', 'scope', 'ses_two', true);
      expect(pins.ids('a', 'scope'), {'ses_two'});
    },
  );

  Future<_Controller> controller() async {
    final result = _Controller(
      ProfileStore(prefs: await SharedPreferences.getInstance()),
    )..directory = '/one';
    addTearDown(result.dispose);
    return result;
  }

  test(
    'pins sort first with recency retained and unpin restores the ordinary order',
    () async {
      final c = await controller();
      c.sessionsById.addAll({
        'old': _session('old', 1),
        'new': _session('new', 10),
      });
      await c.setSessionPinned(
        'old',
        true,
        locationRevision: c.locationRevision,
      );
      expect(c.sortedSessions().map((s) => s.id), ['old', 'new']);
      await c.setSessionPinned(
        'old',
        false,
        locationRevision: c.locationRevision,
      );
      expect(c.sortedSessions().map((s) => s.id), ['new', 'old']);
      await expectLater(
        c.setSessionPinned(
          'old',
          true,
          locationRevision: c.locationRevision - 1,
        ),
        throwsStateError,
      );
      expect(c.isSessionPinned('old'), isFalse);
    },
  );

  test(
    'pinned history outside the head page is hydrated once and stays reachable',
    () async {
      final c = await controller();
      final api = _Api();
      c.api = api;
      await c.setSessionPinned(
        'old',
        true,
        locationRevision: c.locationRevision,
      );
      await c.refreshSessions();
      await c.ensureSession('old');
      expect(c.sortedSessions().map((s) => s.id), ['old', 'new']);
      final reads = api.requests.length;
      await c.refreshSessions();
      expect(api.requests.length, reads);
      expect(c.hasMoreSessions, isTrue);
    },
  );

  test(
    'failed pinned reads remain retryable and do not drop the preference',
    () async {
      final c = await controller();
      final api = _Api()..error = ApiException('offline');
      c.api = api;
      await c.setSessionPinned(
        'old',
        true,
        locationRevision: c.locationRevision,
      );
      await c.refreshSessions();
      await c.ensureSession('old');
      expect(c.pinnedSessionsLoadFailed, isTrue);
      expect(c.isSessionPinned('old'), isTrue);
      api.error = null;
      await c.refreshSessions();
      await c.ensureSession('old');
      expect(c.pinnedSessionsLoadFailed, isFalse);
      expect(c.sortedSessions().first.id, 'old');
    },
  );
}
