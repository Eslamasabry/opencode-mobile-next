import 'dart:async';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/session_address_link.dart';
import 'package:opencode_mobile/state/session_link_bindings.dart';
import 'package:opencode_mobile/ui/kit/kit_redact.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:shared_preferences_platform_interface/shared_preferences_platform_interface.dart';
import 'package:shared_preferences_platform_interface/types.dart';

const _profile = 'phone';
const _key = 'oc.sessionLinkBinding.phone';
const _origin = 'https://workstation.example-tailnet.ts.net';
const _instance = '9e30af6d-422d-4d89-baad-006ac07cb9d1';

SessionLinkBinding _binding({String origin = _origin, String id = _instance}) =>
    SessionLinkBinding(
      origin: origin,
      instanceId: id,
      verifiedAt: DateTime.utc(2026, 9, 28),
    );

Matcher _failure(SessionAddressFailureCode code) => throwsA(
  isA<SessionAddressFailure>().having((error) => error.code, 'code', code),
);

class _Disk extends InMemorySharedPreferencesStore {
  _Disk(super.data) : super.withData();
  bool refuse = false;
  bool throwWrite = false;
  bool failReads = false;
  bool failReadsAfterWrite = false;
  bool hold = false;
  final entered = Completer<void>();
  final release = Completer<void>();

  @override
  Future<Map<String, Object>> getAll() {
    if (failReads) throw StateError('Synthetic failure');
    return super.getAll();
  }

  @override
  Future<Map<String, Object>> getAllWithParameters(
    GetAllParameters parameters,
  ) {
    if (failReads) throw StateError('Synthetic failure');
    return super.getAllWithParameters(parameters);
  }

  @override
  Future<bool> setValue(String type, String key, Object value) async {
    if (key == 'flutter.$_key') {
      if (hold) {
        hold = false;
        entered.complete();
        await release.future;
      }
      if (failReadsAfterWrite) failReads = true;
      if (throwWrite) throw StateError('Synthetic failure');
      if (refuse) return false;
    }
    return super.setValue(type, key, value);
  }
}

Future<(SharedPreferences, _Disk)> _setup({String? existing}) async {
  SharedPreferences.setMockInitialValues({
    'oc.profiles': '[{"id":"phone"}]',
    _key: ?existing,
  });
  final disk = _Disk(await SharedPreferencesStorePlatform.instance.getAll());
  SharedPreferencesStorePlatform.instance = disk;
  SharedPreferences.resetStatic();
  return (await SharedPreferences.getInstance(), disk);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('shared owner persists only the canonical verified binding', () async {
    final (prefs, _) = await _setup();
    final owner = SessionLinkBindings.forProfile(prefs, _profile);
    expect(
      identical(owner, SessionLinkBindings.forProfile(prefs, _profile)),
      isTrue,
    );
    expect(owner.read(), isNull);
    await owner.save(
      _binding(origin: 'https://WORKSTATION.example-tailnet.ts.net:443'),
    );
    await prefs.reload();
    final stored = jsonDecode(prefs.getString(_key)!) as Map;
    expect(stored.keys.toSet(), {
      'schemaVersion',
      'origin',
      'instanceId',
      'verifiedAt',
    });
    expect(stored['origin'], _origin);
    expect(owner.read()!.instanceId, _instance);
    expect(owner.read()!.verifiedAt.isUtc, isTrue);
    expect(owner.revision, 1);
    await owner.save(_binding());
    expect(owner.revision, 2);
  });

  test('different origin or installation cannot overwrite a binding', () async {
    final (prefs, _) = await _setup();
    final owner = SessionLinkBindings.forProfile(prefs, _profile);
    await owner.save(_binding());
    await expectLater(
      owner.save(_binding(origin: 'https://other.example-tailnet.ts.net')),
      _failure(SessionAddressFailureCode.instanceMismatch),
    );
    await expectLater(
      owner.save(_binding(id: '9e30af6d-422d-4d89-baad-006ac07cb9d2')),
      _failure(SessionAddressFailureCode.instanceMismatch),
    );
    expect(owner.read()!.instanceId, _instance);
    expect(owner.read()!.origin, _origin);
  });

  test('absent profile cannot load or save through a stale owner', () async {
    final (prefs, _) = await _setup();
    final owner = SessionLinkBindings.forProfile(prefs, _profile);
    expect(
      () => SessionLinkBindings.forProfile(prefs, 'absent'),
      _failure(SessionAddressFailureCode.profileMissing),
    );
    await prefs.setString('oc.profiles', '[]');
    expect(owner.isAvailable, isFalse);
    await expectLater(
      owner.save(_binding()),
      _failure(SessionAddressFailureCode.profileMissing),
    );
    expect(prefs.containsKey(_key), isFalse);
  });

  test(
    'close invalidates synchronously and drains a real pending disk write',
    () async {
      final (prefs, disk) = await _setup();
      disk.hold = true;
      final owner = SessionLinkBindings.forProfile(prefs, _profile);
      var notifications = 0;
      owner.addListener(() => notifications++);
      final saving = owner.save(_binding());
      final rejected = expectLater(
        saving,
        _failure(SessionAddressFailureCode.profileMissing),
      );
      await disk.entered.future;
      var drained = false;
      final closing = SessionLinkBindings.closeProfile(
        prefs,
        _profile,
      ).then((_) => drained = true);
      expect(owner.isAvailable, isFalse);
      expect(notifications, 1);
      expect(drained, isFalse);
      expect(
        () => SessionLinkBindings.forProfile(prefs, _profile),
        _failure(SessionAddressFailureCode.profileMissing),
      );
      disk.release.complete();
      await rejected;
      await closing;
      await prefs.remove(_key);
      await prefs.setString('oc.profiles', '[]');
      await prefs.reload();
      expect(prefs.containsKey(_key), isFalse);
      await expectLater(
        owner.save(_binding()),
        _failure(SessionAddressFailureCode.profileMissing),
      );
      await SessionLinkBindings.cancelDeletion(prefs, _profile);
      expect(
        () => SessionLinkBindings.forProfile(prefs, _profile),
        _failure(SessionAddressFailureCode.profileMissing),
      );
    },
  );

  test(
    'aborted deletion reopens only a fresh owner after durable membership',
    () async {
      final (prefs, disk) = await _setup();
      final old = SessionLinkBindings.forProfile(prefs, _profile);
      await old.save(_binding());
      await SessionLinkBindings.closeProfile(prefs, _profile);
      disk.failReads = true;
      await SessionLinkBindings.cancelDeletion(prefs, _profile);
      expect(
        () => SessionLinkBindings.forProfile(prefs, _profile),
        _failure(SessionAddressFailureCode.profileMissing),
      );
      disk.failReads = false;
      await SessionLinkBindings.cancelDeletion(prefs, _profile);
      final fresh = SessionLinkBindings.forProfile(prefs, _profile);
      expect(identical(fresh, old), isFalse);
      expect(fresh.read()!.instanceId, _instance);
      await fresh.save(_binding());
      await expectLater(
        old.save(_binding()),
        _failure(SessionAddressFailureCode.profileMissing),
      );
    },
  );

  for (final throws in [false, true]) {
    test(
      '${throws ? 'thrown' : 'refused'} persistence never exposes optimistic binding',
      () async {
        final (prefs, disk) = await _setup();
        final owner = SessionLinkBindings.forProfile(prefs, _profile);
        disk.refuse = !throws;
        disk.throwWrite = throws;
        await expectLater(
          owner.save(_binding()),
          _failure(SessionAddressFailureCode.storage),
        );
        expect(owner.isAvailable, isFalse);
        expect(() => owner.read(), _failure(SessionAddressFailureCode.storage));
        await prefs.reload();
        expect(prefs.containsKey(_key), isFalse);
        await expectLater(
          owner.save(_binding()),
          _failure(SessionAddressFailureCode.storage),
        );
      },
    );
  }

  test(
    'failed write and failed reload never publish optimistic cache',
    () async {
      final (prefs, disk) = await _setup();
      final owner = SessionLinkBindings.forProfile(prefs, _profile);
      disk.refuse = true;
      disk.failReadsAfterWrite = true;
      await expectLater(
        owner.save(_binding()),
        _failure(SessionAddressFailureCode.storage),
      );
      // The plugin may retain its proposed value; consumers must use the owner.
      expect(prefs.containsKey(_key), isTrue);
      expect(owner.isAvailable, isFalse);
      expect(() => owner.read(), _failure(SessionAddressFailureCode.storage));
      disk.failReads = false;
      await prefs.reload();
      expect(prefs.containsKey(_key), isFalse);
    },
  );

  test('unreadable storage is fail closed before any write', () async {
    final (prefs, disk) = await _setup();
    final owner = SessionLinkBindings.forProfile(prefs, _profile);
    disk.failReads = true;
    await expectLater(
      owner.save(_binding()),
      _failure(SessionAddressFailureCode.storage),
    );
    expect(owner.isAvailable, isFalse);
    expect(() => owner.read(), _failure(SessionAddressFailureCode.storage));
  });

  for (final raw in [
    '{',
    '{"schemaVersion":2}',
    jsonEncode({
      'schemaVersion': 1,
      'origin': _origin,
      'instanceId': _instance,
      'verifiedAt': 0,
      'extra': 'forbidden',
    }),
  ]) {
    test(
      'corrupt or unknown persisted shape is unavailable (${raw.length})',
      () async {
        final (prefs, _) = await _setup(existing: raw);
        final owner = SessionLinkBindings.forProfile(prefs, _profile);
        expect(() => owner.read(), _failure(SessionAddressFailureCode.storage));
        await expectLater(
          owner.save(_binding()),
          _failure(SessionAddressFailureCode.storage),
        );
        expect(prefs.getString(_key), raw);
      },
    );
  }

  test(
    'credentials and recognized secrets never enter binding storage',
    () async {
      final (prefs, _) = await _setup();
      final owner = SessionLinkBindings.forProfile(prefs, _profile);
      await expectLater(
        owner.save(
          _binding(
            origin: 'https://user:password@workstation.example-tailnet.ts.net',
          ),
        ),
        _failure(SessionAddressFailureCode.credentials),
      );
      KitRedact.registerKnownSecret(_instance);
      addTearDown(KitRedact.clearKnownSecrets);
      await expectLater(
        owner.save(_binding()),
        _failure(SessionAddressFailureCode.invalidLink),
      );
      expect(prefs.containsKey(_key), isFalse);
    },
  );
}
