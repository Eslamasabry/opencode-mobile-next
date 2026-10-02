import 'dart:convert';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api2/client.dart';
import 'package:opencode_mobile/api2/gateway.dart';
import 'package:opencode_mobile/api2/models.dart';
import 'package:opencode_mobile/api2/transport.dart';
import 'package:opencode_mobile/domain/command_receipts.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/session_creation_receipts.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _Client extends Api2Client {
  _Client({super.baseUrl = 'http://127.0.0.1:4097'})
    : super.connect(password: 'fake-test-password', directory: '/project');
  final createdIDs = <String>[];
  final lookups = <String>[];
  final admittedSessions = <String, Api2Session>{};
  bool loseResponse = true;
  bool missing = false;
  @override
  Future<Api2Session> createSession({
    String? id,
    String? title,
    String? agent,
    Api2ModelRef? model,
    Map<String, dynamic>? metadata,
  }) async {
    expect(
      id,
      isNotNull,
      reason: 'creation must supply a durable client session ID',
    );
    expect(id, startsWith('ses_'));
    createdIDs.add(id!);
    final session = Api2Session(id: id);
    admittedSessions[id] = session;
    if (loseResponse) throw const Api2NetworkError('response lost');
    return session;
  }

  @override
  Future<Api2Session> session(String sessionID) async {
    lookups.add(sessionID);
    if (missing || !admittedSessions.containsKey(sessionID)) {
      throw const Api2RequestError('not found', statusCode: 404);
    }
    return admittedSessions[sessionID]!;
  }
}

Future<ProfileStore> _store([String? snapshot]) async {
  SharedPreferences.setMockInitialValues({
    'oc.profiles': jsonEncode([
      {
        'id': 'profile_1',
        'name': 'Test server',
        'baseUrl': 'http://127.0.0.1:4097',
        'username': '',
        'flavor': 'v2',
      },
    ]),
    'oc.pendingCommands.profile_1': ?snapshot,
  });
  final store = ProfileStore(prefs: await SharedPreferences.getInstance());
  await store.load();
  return store;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
  });
  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          null,
        );
  });

  test(
    'lost create response is checked without creating another session',
    () async {
      final store = await _store();
      final client = _Client();
      addTearDown(client.close);
      final receipts = SessionCreationReceipts(
        store: store,
        gateway: Api2Gateway(client: client),
        profileID: 'profile_1',
      );
      expect(receipts.supported, isTrue);
      final sent = await receipts.send(commandID: 'create_1', tabID: 'tab_1');
      expect(sent.state, CommandReceiptState.uncertain);
      expect(client.createdIDs, hasLength(1));
      final found = await receipts.check('create_1');
      expect(found?.id, client.createdIDs.single);
      final repeated = await receipts.send(
        commandID: 'create_1',
        tabID: 'tab_1',
      );
      expect(repeated.state, CommandReceiptState.confirmed);
      expect(client.createdIDs, hasLength(1));
      expect(
        client.lookups.every((id) => id == client.createdIDs.single),
        isTrue,
      );
    },
  );

  test('restart preserves creation identity and owning tab', () async {
    final store = await _store();
    final client = _Client();
    addTearDown(client.close);
    final receipts = SessionCreationReceipts(
      store: store,
      gateway: Api2Gateway(client: client),
      profileID: 'profile_1',
    );
    await receipts.send(commandID: 'create_1', tabID: 'tab_1');
    final snapshot = store.prefs.getString('oc.pendingCommands.profile_1')!;
    final restartedStore = await _store(snapshot);
    final restarted = SessionCreationReceipts(
      store: restartedStore,
      gateway: Api2Gateway(client: client),
      profileID: 'profile_1',
    );
    await restarted.send(commandID: 'create_1', tabID: 'tab_1');
    expect((await restarted.check('create_1'))?.id, client.createdIDs.single);
    expect(client.createdIDs, hasLength(1));
    final persisted =
        jsonDecode(
              restartedStore.prefs.getString('oc.pendingCommands.profile_1')!,
            )
            as Map<String, dynamic>;
    final command = (persisted['commands'] as List).single as Map;
    expect(command['tabID'], 'tab_1');
    expect(command['state'], 'confirmed');
  });

  test(
    'not found stays uncertain and repeated send cannot create again',
    () async {
      final store = await _store();
      final client = _Client()..missing = true;
      addTearDown(client.close);
      final receipts = SessionCreationReceipts(
        store: store,
        gateway: Api2Gateway(client: client),
        profileID: 'profile_1',
      );
      await receipts.send(commandID: 'create_1', tabID: 'tab_1');
      expect(await receipts.check('create_1'), isNull);
      expect(
        (await receipts.send(commandID: 'create_1', tabID: 'tab_1')).state,
        CommandReceiptState.uncertain,
      );
      expect(client.createdIDs, hasLength(1));
    },
  );

  test('unsupported v1 refuses creation receipts', () async {
    final store = await _store();
    final gateway = OpenCodeApi(baseUrl: 'http://127.0.0.1:4097');
    addTearDown(gateway.close);
    final receipts = SessionCreationReceipts(
      store: store,
      gateway: gateway,
      profileID: 'profile_1',
    );
    expect(receipts.supported, isFalse);
    await expectLater(
      receipts.send(commandID: 'create_1', tabID: 'tab_1'),
      throwsA(isA<CommandReceiptException>()),
    );
    expect(store.prefs.getString('oc.pendingCommands.profile_1'), isNull);
  });

  test('another tab cannot borrow the same creation command', () async {
    final store = await _store();
    final client = _Client();
    addTearDown(client.close);
    final receipts = SessionCreationReceipts(
      store: store,
      gateway: Api2Gateway(client: client),
      profileID: 'profile_1',
    );
    await receipts.send(commandID: 'create_1', tabID: 'tab_1');
    await expectLater(
      receipts.send(commandID: 'create_1', tabID: 'tab_2'),
      throwsA(isA<CommandReceiptException>()),
    );
    expect(client.createdIDs, hasLength(1));
  });

  test(
    'profile endpoint and gateway endpoint mismatch refuses construction',
    () async {
      final store = await _store();
      final client = _Client(baseUrl: 'http://127.0.0.1:4098');
      addTearDown(client.close);
      expect(
        () => SessionCreationReceipts(
          store: store,
          gateway: Api2Gateway(client: client),
          profileID: 'profile_1',
        ),
        throwsA(isA<CommandReceiptException>()),
      );
      expect(client.createdIDs, isEmpty);
      expect(client.lookups, isEmpty);
      expect(store.prefs.getString('oc.pendingCommands.profile_1'), isNull);
    },
  );

  for (final change in ['endpoint', 'location']) {
    test(
      'changed $change cannot check an old receipt in the new scope',
      () async {
        final store = await _store();
        final client = _Client();
        addTearDown(client.close);
        final gateway = Api2Gateway(client: client);
        final receipts = SessionCreationReceipts(
          store: store,
          gateway: gateway,
          profileID: 'profile_1',
        );
        await receipts.send(commandID: 'create_1', tabID: 'tab_1');
        if (change == 'endpoint') {
          store.profiles.single.baseUrl = 'http://127.0.0.1:4098';
        } else {
          gateway.setLocation(directory: '/another-project');
        }
        expect(await receipts.check('create_1'), isNull);
        expect(client.lookups, isEmpty);
        expect(client.createdIDs, hasLength(1));
      },
    );
  }
}
