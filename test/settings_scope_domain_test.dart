import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/settings_scope.dart';
import 'package:opencode_mobile/domain/settings_scope_fixture.dart';

const _device = SettingsTarget.device();
const _phone = SettingsTarget.connection('phone');
const _laptop = SettingsTarget.connection('laptop');

Matcher _fails(SettingsFailureReason reason) => throwsA(
  isA<SettingsFailure>().having((error) => error.reason, 'reason', reason),
);

EffectiveSetting _consent(SettingsTarget target, {int revision = 0}) =>
    EffectiveSetting(
      id: 'shareReadState',
      target: target,
      value: false,
      writable: true,
      revision: revision,
    );

void main() {
  test(
    'target identities separate owners, connections, projects and chats',
    () {
      expect(const SettingsTarget.device().ownerId, 'device');
      expect(_phone, const SettingsTarget.connection('phone'));
      final targets = {_phone};
      targets.add(const SettingsTarget.connection('phone'));
      expect(targets, hasLength(1));
      expect(_phone, isNot(const SettingsTarget.host('phone')));
      expect(
        const SettingsTarget.project('phone', 'project'),
        isNot(const SettingsTarget.project('laptop', 'project')),
      );
      expect(
        const SettingsTarget.chat('phone', 'chat', projectId: 'one'),
        isNot(const SettingsTarget.chat('phone', 'chat', projectId: 'two')),
      );
      expect(() => SettingsTarget.connection(''), throwsAssertionError);
      expect(() => SettingsTarget.project('phone', ''), throwsAssertionError);
      expect(() => SettingsTarget.chat('phone', ''), throwsAssertionError);
    },
  );

  test('all 67 settings doors have immutable ownership without navigation', () {
    final rows = SettingsScopeCatalog.rows;
    expect(rows, hasLength(67));
    expect(rows.map((row) => row.rowId).toSet(), hasLength(67));
    expect(rows.every((row) => row.owners.isNotEmpty), isTrue);
    expect(() => rows.clear(), throwsUnsupportedError);
    expect(() => rows.first.owners.clear(), throwsUnsupportedError);
    for (final id in [
      'tab-chats',
      'project-terminal',
      'library-import-session',
    ]) {
      expect(SettingsScopeCatalog.forRow(id), isNull);
    }
    for (final id in ['settings-models', 'settings-model-and-mode']) {
      final row = SettingsScopeCatalog.forRow(id)!;
      expect(row.owners, {SettingOwnerKind.connection});
      expect(row.labelCode, 'newChats');
    }
    expect(SettingsScopeCatalog.forRow('settings-external-agents')!.owners, {
      SettingOwnerKind.device,
    });
    expect(SettingsScopeCatalog.forRow('inside-phone-crash-recovery')!.owners, {
      SettingOwnerKind.host,
    });
    expect(SettingsScopeCatalog.forRow('inside-usage-remaining')!.owners, {
      SettingOwnerKind.connection,
      SettingOwnerKind.providerAccount,
    });
  });

  test(
    'absent scenes are unsupported; metadata does not grant writes',
    () async {
      final access = MemorySettingsAccess(
        initial: [
          const EffectiveSetting(id: 'model', target: _phone, value: null),
        ],
      );
      addTearDown(access.dispose);
      final absent = await access.readSetting('appearance', _device);
      expect(absent.state, SettingValueState.unsupported);
      expect(absent.value, isNull);
      expect(absent.writable, isFalse);
      await expectLater(
        access.beginEdit('appearance', _device),
        _fails(SettingsFailureReason.unsupported),
      );
      await expectLater(
        access.beginEdit('model', _phone),
        _fails(SettingsFailureReason.unsupported),
      );
    },
  );

  test(
    'captured edits write only their owner and return committed state',
    () async {
      final access = MemorySettingsAccess(
        initial: [_consent(_phone), _consent(_laptop)],
      );
      addTearDown(access.dispose);
      final phoneEdit = await access.beginEdit('shareReadState', _phone);
      await access.readSetting('shareReadState', _laptop);
      final receipt = await access.writeSetting(phoneEdit, true);
      expect(receipt.confirmed.target, _phone);
      expect(receipt.confirmed.value, isTrue);
      expect(receipt.confirmed.origin, SettingOriginKind.explicitValue);
      expect(receipt.confirmed.revision, 1);
      expect(
        (await access.readSetting('shareReadState', _laptop)).value,
        isFalse,
      );
    },
  );

  test('forged, foreign and replayed tokens cannot write', () async {
    final first = MemorySettingsAccess(initial: [_consent(_phone)]);
    final second = MemorySettingsAccess(initial: [_consent(_phone)]);
    addTearDown(first.dispose);
    addTearDown(second.dispose);
    final edit = await first.beginEdit('shareReadState', _phone);
    final forged = SettingsEditToken(
      before: edit.before,
      ownerFingerprint: edit.ownerFingerprint,
    );
    await expectLater(
      first.writeSetting(forged, true),
      _fails(SettingsFailureReason.staleTarget),
    );
    await expectLater(
      second.writeSetting(edit, true),
      _fails(SettingsFailureReason.staleTarget),
    );
    await first.writeSetting(edit, true);
    await expectLater(
      first.writeSetting(edit, false),
      _fails(SettingsFailureReason.staleTarget),
    );
  });

  test(
    'new observations and competing saves conflict; deletion stays stale',
    () async {
      final access = MemorySettingsAccess(
        initial: [_consent(_phone, revision: 5)],
      );
      addTearDown(access.dispose);
      final old = await access.beginEdit('shareReadState', _phone);
      access.publish(_consent(_phone));
      expect((await access.readSetting('shareReadState', _phone)).revision, 6);
      await expectLater(
        access.writeSetting(old, true),
        _fails(SettingsFailureReason.conflict),
      );
      final one = await access.beginEdit('shareReadState', _phone);
      final two = await access.beginEdit('shareReadState', _phone);
      await access.writeSetting(one, true);
      await expectLater(
        access.writeSetting(two, false),
        _fails(SettingsFailureReason.conflict),
      );
      final deleted = await access.beginEdit('shareReadState', _phone);
      access.publish(
        const EffectiveSetting(
          id: 'shareReadState',
          target: _phone,
          state: SettingValueState.stale,
          reason: SettingsFailureReason.staleTarget,
        ),
      );
      await expectLater(
        access.writeSetting(deleted, false),
        _fails(SettingsFailureReason.staleTarget),
      );
      access.publish(_consent(_phone));
      await expectLater(
        access.writeSetting(deleted, true),
        _fails(SettingsFailureReason.staleTarget),
      );
    },
  );

  test(
    'watches replay latest per subscriber then changes and stale states',
    () async {
      final access = MemorySettingsAccess(initial: [_consent(_phone)]);
      final stream = access.watchSetting('shareReadState', _phone);
      final one = StreamIterator(stream);
      expect(await one.moveNext(), isTrue);
      expect(one.current.value, isFalse);
      final edit = await access.beginEdit('shareReadState', _phone);
      final next = one.moveNext();
      await access.writeSetting(edit, true);
      expect(await next, isTrue);
      expect(one.current.value, isTrue);
      final two = StreamIterator(stream);
      expect(await two.moveNext(), isTrue);
      expect(two.current.value, isTrue);
      await one.cancel();
      final stale = two.moveNext();
      access.publish(
        const EffectiveSetting(
          id: 'shareReadState',
          target: _phone,
          state: SettingValueState.stale,
        ),
      );
      expect(await stale, isTrue);
      expect(two.current.state, SettingValueState.stale);
      final done = two.moveNext();
      access.dispose();
      expect(await done, isFalse);
      access.dispose();
      await expectLater(
        access.readSetting('shareReadState', _phone),
        _fails(SettingsFailureReason.disposed),
      );
      await expectLater(
        access.beginEdit('shareReadState', _phone),
        _fails(SettingsFailureReason.disposed),
      );
      await expectLater(
        access.writeSetting(edit, false),
        _fails(SettingsFailureReason.disposed),
      );
      await expectLater(
        access.watchSetting('shareReadState', _phone).first,
        _fails(SettingsFailureReason.disposed),
      );
      expect(
        () => access.publish(_consent(_phone)),
        _fails(SettingsFailureReason.disposed),
      );
    },
  );

  test(
    'malformed values cannot publish a success or mutate snapshots',
    () async {
      final effects = <String, Object>{
        'motion': 'full',
        'activityGlow': true,
        'glowStyle': 'classic',
        'glowColours': 'one',
        'glowSpeed': 'normal',
      };
      final access = MemorySettingsAccess(
        initial: [
          EffectiveSetting(
            id: 'effects',
            target: _device,
            value: effects,
            writable: true,
          ),
          const EffectiveSetting(
            id: 'locale',
            target: _device,
            value: 'ar',
            writable: true,
          ),
        ],
      );
      addTearDown(access.dispose);
      effects['motion'] = 'off';
      final initial = await access.readSetting('effects', _device);
      expect((initial.value as Map)['motion'], 'full');
      expect(
        () => (initial.value as Map)['motion'] = 'off',
        throwsUnsupportedError,
      );
      final edit = await access.beginEdit('effects', _device);
      await expectLater(
        access.writeSetting(edit, {'motion': 'off'}),
        _fails(SettingsFailureReason.invalidValue),
      );
      expect((await access.readSetting('effects', _device)).revision, 0);
      final receipt = await access.writeSetting(edit, effects);
      effects['motion'] = 'full';
      expect((receipt.confirmed.value as Map)['motion'], 'off');
      final locale = await access.beginEdit('locale', _device);
      await expectLater(
        access.writeSetting(locale, 'not-a-language'),
        _fails(SettingsFailureReason.invalidValue),
      );
      expect((await access.writeSetting(locale, null)).confirmed.value, isNull);
      expect(
        const SettingsFailure(SettingsFailureReason.invalidValue).toString(),
        'SettingsFailure(invalidValue)',
      );
    },
  );
}
