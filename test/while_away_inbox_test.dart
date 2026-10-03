// The automatic-activity history is per server: deleting a server drains and
// removes its stored acts.
import 'dart:convert';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/domain/while_away.dart';
import 'package:opencode_mobile/state/automatic_activity.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _Repository implements ProductRepository {
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _Controller extends ConnectionController {
  _Controller(super.store);

  @override
  bool get isConnected => status == StreamStatus.connected;
  @override
  Future<ServerOperationsGateway?> prepareActionRepository() async =>
      repository;
  @override
  Future<void> refreshSessions() async {}
  @override
  Future<void> refreshPendingPermissions() async {}
  @override
  Future<void> refreshPendingQuestions() async {}
  @override
  Future<void> refreshPendingForms() async {}
}

const _dir = '/work/app';

Future<_Controller> _controller() async {
  SharedPreferences.setMockInitialValues({
    'oc.profiles': jsonEncode([
      {
        'id': 'laptop',
        'name': 'Laptop',
        'baseUrl': 'http://192.168.1.20:4096',
        'username': '',
      },
      {
        'id': 'studio',
        'name': 'Studio',
        'baseUrl': 'http://192.168.1.30:4096',
        'username': '',
      },
    ]),
    'oc.activeProfile': 'laptop',
  });
  final store = ProfileStore(prefs: await SharedPreferences.getInstance());
  await store.load();
  final controller = _Controller(store)
    ..repository = _Repository()
    ..status = StreamStatus.connected
    ..directory = _dir;
  controller.sessionsById = {
    'ses_fix': Session(
      id: 'ses_fix',
      title: 'Fix login',
      directory: _dir,
      time: SessionTime(created: 1, updated: 2),
    ),
  };
  return controller;
}

Future<void> _serverAct(
  _Controller controller,
  String eventId,
  DateTime at, {
  AutomaticActKind kind = AutomaticActKind.reconnect,
  String profileId = 'laptop',
}) async {
  expect(
    await controller.recordServerAct(
      profileId: profileId,
      kind: kind,
      eventId: eventId,
      at: at,
    ),
    isTrue,
  );
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    AutomaticActivityController.resetShared();
    for (final channel in const [
      MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
      MethodChannel('oc/background'),
    ]) {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (_) async => null);
    }
  });
  tearDown(AutomaticActivityController.resetShared);

  testWidgets('deleting a server drains and removes its history', (
    tester,
  ) async {
    final controller = await _controller();
    addTearDown(controller.dispose);
    await _serverAct(
      controller,
      'studio-1',
      DateTime.now(),
      profileId: 'studio',
    );
    expect(
      controller.store.prefs.getString('oc.automaticActivity.studio'),
      isNotNull,
    );

    await controller.deleteProfileAndLocalData('studio');

    expect(
      controller.store.prefs.getString('oc.automaticActivity.studio'),
      isNull,
    );
    // Nothing can file an act for a server that is gone.
    expect(
      await controller.recordServerAct(
        profileId: 'studio',
        kind: AutomaticActKind.reconnect,
        eventId: 'late',
        at: DateTime.now(),
      ),
      isFalse,
    );
    expect(
      controller.store.prefs.getString('oc.automaticActivity.studio'),
      isNull,
    );
  });
}
