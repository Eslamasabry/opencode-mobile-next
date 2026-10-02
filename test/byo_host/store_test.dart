import 'dart:convert';

import 'package:crypto/crypto.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/byo_host.dart';
import 'package:opencode_mobile/state/byo_host_store.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:shared_preferences_platform_interface/shared_preferences_platform_interface.dart';

const _sensitiveError = 'fixture-private-key-and-device-token';

class _Vault extends FlutterSecureStorage {
  final values = <String, String>{};
  String? failOperation;
  bool retainDeletedValue = false;
  int calls = 0;

  void _check(String operation) {
    calls++;
    if (failOperation == operation) throw StateError(_sensitiveError);
  }

  @override
  Future<String?> read({
    required String key,
    AppleOptions? iOptions,
    AndroidOptions? aOptions,
    LinuxOptions? lOptions,
    WebOptions? webOptions,
    AppleOptions? mOptions,
    WindowsOptions? wOptions,
  }) async {
    _check('read');
    return values[key];
  }

  @override
  Future<void> write({
    required String key,
    required String? value,
    AppleOptions? iOptions,
    AndroidOptions? aOptions,
    LinuxOptions? lOptions,
    WebOptions? webOptions,
    AppleOptions? mOptions,
    WindowsOptions? wOptions,
  }) async {
    _check('write');
    if (value == null) {
      values.remove(key);
    } else {
      values[key] = value;
    }
  }

  @override
  Future<void> delete({
    required String key,
    AppleOptions? iOptions,
    AndroidOptions? aOptions,
    LinuxOptions? lOptions,
    WebOptions? webOptions,
    AppleOptions? mOptions,
    WindowsOptions? wOptions,
  }) async {
    _check('delete');
    if (!retainDeletedValue) values.remove(key);
  }
}

class _Disk extends InMemorySharedPreferencesStore {
  _Disk() : super.withData({});

  bool refuseWrite = false;
  bool refuseRemove = false;
  bool failRead = false;

  @override
  Future<bool> setValue(String type, String key, Object value) async {
    if (refuseWrite) throw StateError(_sensitiveError);
    return super.setValue(type, key, value);
  }

  @override
  Future<bool> remove(String key) async =>
      refuseRemove ? false : super.remove(key);

  @override
  Future<Map<String, Object>> getAll() async {
    if (failRead) throw StateError(_sensitiveError);
    return super.getAll();
  }
}

final _hostKeyWire = [
  0,
  0,
  0,
  11,
  ...ascii.encode('ssh-ed25519'),
  0,
  0,
  0,
  32,
  ...List.filled(32, 1),
];

ByoHostRecord _record(String id) => ByoHostRecord(
  profileId: id,
  target: ByoHostTarget(user: 'ubuntu', host: 'machine.tailnet.ts.net'),
  hostKey: ByoHostKey(
    publicKey: 'ssh-ed25519 ${base64Encode(_hostKeyWire)}',
    fingerprint:
        'SHA256:${base64Encode(sha256.convert(_hostKeyWire).bytes).replaceAll('=', '')}',
  ),
  phase: ByoHostPhase.ready,
  hostId: 'host-$id',
  bundleVersion: '1.0.0',
  openCodeVersion: '1.18.32',
  remotePort: 4096,
);

ByoHostSecrets _secrets(String id) => ByoHostSecrets(
  identity: ByoHostIdentity(
    privateKey: 'fixture-private-$id',
    publicKey: 'fixture-public-$id',
  ),
  deviceToken: 'fixture-token-$id',
);

final _safeStorageFailure = isA<ByoHostFailure>()
    .having((error) => error.code, 'code', ByoHostFailureCode.storage)
    .having(
      (error) => error.toString(),
      'safe diagnostic',
      isNot(contains(_sensitiveError)),
    )
    .having(
      (error) => error.message,
      'safe message',
      isNot(contains(_sensitiveError)),
    );

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late SharedPreferences prefs;
  late _Vault vault;
  late _Disk disk;
  late PersistentByoHostStore store;

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    disk = _Disk();
    SharedPreferencesStorePlatform.instance = disk;
    prefs = await SharedPreferences.getInstance();
    vault = _Vault();
    store = PersistentByoHostStore(prefs: prefs, secure: vault);
  });

  test('round trips after reopening with secrets only in the vault', () async {
    await store.saveRecord(_record('one'));
    await store.saveSecrets('one', _secrets('one'));
    await store.saveRecord(_record('one'));
    await store.saveSecrets('one', _secrets('one'));
    final metadata = prefs.getString('oc.byoHost.one')!;
    expect(metadata, isNot(contains('fixture-private-one')));
    expect(metadata, isNot(contains('fixture-token-one')));
    expect(jsonDecode(metadata), isNot(contains('privateKey')));
    expect(prefs.getKeys(), {'oc.byoHost.one'});
    expect(vault.values.keys, ['oc.byoHostSecrets.one']);

    final reopened = PersistentByoHostStore(prefs: prefs, secure: vault);
    expect(
      (await reopened.readRecord('one'))!.toJson(),
      _record('one').toJson(),
    );
    expect(
      (await reopened.readSecrets('one'))!.identity.privateKey,
      'fixture-private-one',
    );
    expect(
      (await reopened.readSecrets('one'))!.deviceToken,
      'fixture-token-one',
    );
  });

  test('removal is idempotent and leaves another profile untouched', () async {
    for (final id in ['one', 'two']) {
      await store.saveRecord(_record(id));
      await store.saveSecrets(id, _secrets(id));
    }
    await prefs.setString('unrelated', 'keep');
    vault.values['unrelated'] = 'keep';
    await store.remove('one');
    await store.remove('one');
    expect(await store.readRecord('one'), isNull);
    expect(await store.readSecrets('one'), isNull);
    expect((await store.readRecord('two'))!.profileId, 'two');
    expect((await store.readSecrets('two'))!.deviceToken, 'fixture-token-two');
    expect(prefs.getString('unrelated'), 'keep');
    expect(vault.values['unrelated'], 'keep');
  });

  test(
    'enumerates immutable records sorted by profile without opening vault',
    () async {
      expect(await store.listRecords(), isEmpty);
      await store.saveRecord(_record('two'));
      await store.saveRecord(_record('one'));
      await prefs.setString('oc.byoHostSecrets.other', 'unrelated');
      await prefs.setString('unrelated', 'keep');
      vault.failOperation = 'read';
      final records = await store.listRecords();
      expect(records.map((record) => record.profileId), ['one', 'two']);
      expect(() => records.clear(), throwsUnsupportedError);
      expect(vault.calls, 0);
      await store.saveRecord(_record('three'));
      expect(records.length, 2);
    },
  );

  test(
    'enumeration fails closed instead of hiding corrupted profiles',
    () async {
      await store.saveRecord(_record('one'));
      for (final value in [
        _sensitiveError,
        jsonEncode(_record('one').toJson()),
      ]) {
        await prefs.setString('oc.byoHost.two', value);
        await expectLater(store.listRecords(), throwsA(_safeStorageFailure));
      }
      await prefs.remove('oc.byoHost.two');
      await prefs.setString(
        'oc.byoHost.invalid.id',
        jsonEncode(_record('one').toJson()),
      );
      await expectLater(store.listRecords(), throwsA(_safeStorageFailure));
    },
  );

  test('rejects invalid profile ids before either store is touched', () async {
    for (final id in ['', '../one', 'one.two', 'a' * 65]) {
      await expectLater(store.readRecord(id), throwsA(_safeStorageFailure));
      await expectLater(store.readSecrets(id), throwsA(_safeStorageFailure));
      await expectLater(
        store.saveRecord(_record(id)),
        throwsA(_safeStorageFailure),
      );
      await expectLater(
        store.saveSecrets(id, _secrets(id)),
        throwsA(_safeStorageFailure),
      );
      await expectLater(store.remove(id), throwsA(_safeStorageFailure));
    }
    expect(vault.calls, 0);
    expect(prefs.getKeys(), isEmpty);
  });

  test(
    'rejects corrupt and cross-profile metadata with safe failures',
    () async {
      for (final value in [
        _sensitiveError,
        jsonEncode({'schema': 99, 'profileId': 'one'}),
        jsonEncode(_record('two').toJson()),
        jsonEncode({..._record('one').toJson(), 'phase': _sensitiveError}),
      ]) {
        await prefs.setString('oc.byoHost.one', value);
        await expectLater(
          store.readRecord('one'),
          throwsA(_safeStorageFailure),
        );
      }
      vault.values['oc.byoHostSecrets.one'] = _sensitiveError;
      await expectLater(store.readSecrets('one'), throwsA(_safeStorageFailure));
      vault.values['oc.byoHostSecrets.one'] = '{}';
      await expectLater(store.readSecrets('one'), throwsA(_safeStorageFailure));
    },
  );

  test('vault read and write errors reveal no plugin details', () async {
    vault.failOperation = 'write';
    await expectLater(
      store.saveSecrets('one', _secrets('one')),
      throwsA(_safeStorageFailure),
    );
    vault.failOperation = 'read';
    await expectLater(store.readSecrets('one'), throwsA(_safeStorageFailure));
    vault.failOperation = null;
    await store.saveSecrets('one', _secrets('one'));
    expect((await store.readSecrets('one'))!.deviceToken, 'fixture-token-one');
  });

  test('preferences errors are safe and failed writes restore cache', () async {
    disk.refuseWrite = true;
    await expectLater(
      store.saveRecord(_record('one')),
      throwsA(_safeStorageFailure),
    );
    expect(prefs.containsKey('oc.byoHost.one'), isFalse);
    disk.refuseWrite = false;
    disk.failRead = true;
    await expectLater(store.readRecord('one'), throwsA(_safeStorageFailure));
    disk.failRead = false;
    await store.saveRecord(_record('one'));
    expect((await store.readRecord('one'))!.profileId, 'one');
  });

  test('vault deletion error preserves metadata for a later retry', () async {
    await store.saveRecord(_record('one'));
    await store.saveSecrets('one', _secrets('one'));
    vault.failOperation = 'delete';
    await expectLater(store.remove('one'), throwsA(_safeStorageFailure));
    expect((await store.readRecord('one'))!.profileId, 'one');
    expect(vault.values.containsKey('oc.byoHostSecrets.one'), isTrue);
    vault.failOperation = null;
    await store.remove('one');
    expect(await store.readRecord('one'), isNull);
  });

  test('unconfirmed vault deletion keeps the cleanup record', () async {
    await store.saveRecord(_record('one'));
    await store.saveSecrets('one', _secrets('one'));
    vault.retainDeletedValue = true;
    await expectLater(store.remove('one'), throwsA(_safeStorageFailure));
    expect((await store.readRecord('one'))!.profileId, 'one');
  });

  test(
    'metadata deletion refusal retries after secrets already removed',
    () async {
      await store.saveRecord(_record('one'));
      await store.saveSecrets('one', _secrets('one'));
      disk.refuseRemove = true;
      await expectLater(store.remove('one'), throwsA(_safeStorageFailure));
      expect(await store.readSecrets('one'), isNull);
      expect((await store.readRecord('one'))!.profileId, 'one');
      disk.refuseRemove = false;
      await store.remove('one');
      expect(await store.readRecord('one'), isNull);
    },
  );
}
