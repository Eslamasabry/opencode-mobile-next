import 'dart:async';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/command_receipts.dart';
import 'package:opencode_mobile/state/pending_command_journal.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:shared_preferences_platform_interface/shared_preferences_platform_interface.dart';
import 'package:shared_preferences_platform_interface/types.dart';

const _key = 'oc.pendingCommands.phone';
final _scope = 'a' * 64;
final _failure = throwsA(isA<CommandReceiptException>());

CommandReceipt _receipt({
  String id = 'command_1',
  String tab = 'tab_1',
  CommandReceiptState state = CommandReceiptState.sent,
}) => CommandReceipt(
  commandID: id,
  receiptID: 'msg_$id',
  sessionID: 'ses_1',
  tabID: tab,
  scope: _scope,
  createdAt: 100,
  state: state,
);

String _encoded(List<CommandReceipt> commands) => jsonEncode({
  'schemaVersion': 1,
  'commands': commands.map((entry) => entry.toJson()).toList(),
});

class _Disk extends InMemorySharedPreferencesStore {
  _Disk(super.data) : super.withData();
  bool refuse = false;
  bool throwWrite = false;
  bool failReads = false;
  bool failReadback = false;
  bool discardWrite = false;
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
      if (failReadback) failReads = true;
      if (throwWrite) throw StateError('Synthetic failure');
      if (refuse) return false;
      if (discardWrite) return true;
    }
    return super.setValue(type, key, value);
  }
}

Future<(SharedPreferences, _Disk)> _setup({Object? raw}) async {
  SharedPreferences.setMockInitialValues({
    'oc.profiles': '[{"id":"phone"},{"id":"tablet"}]',
    _key: ?raw,
  });
  final disk = _Disk(await SharedPreferencesStorePlatform.instance.getAll());
  SharedPreferencesStorePlatform.instance = disk;
  SharedPreferences.resetStatic();
  return (await SharedPreferences.getInstance(), disk);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUp(() {
    final previous = SharedPreferencesStorePlatform.instance;
    addTearDown(() => SharedPreferencesStorePlatform.instance = previous);
  });

  test(
    'writes metadata only and retains the command through restart',
    () async {
      final (prefs, _) = await _setup();
      final journal = PendingCommandJournal.forProfile(prefs, 'phone');
      expect(journal.read(), isEmpty);
      expect(await journal.claim(_receipt()), isTrue);
      final envelope = jsonDecode(prefs.getString(_key)!) as Map;
      expect(envelope.keys.toSet(), {'schemaVersion', 'commands'});
      expect((envelope['commands'] as List).single, _receipt().toJson());
      SharedPreferences.resetStatic();
      final reopened = PendingCommandJournal.forProfile(
        await SharedPreferences.getInstance(),
        'phone',
      );
      expect(reopened.read().single.commandID, 'command_1');
      expect(await reopened.claim(_receipt()), isFalse);
      await reopened.update(_receipt(state: CommandReceiptState.confirmed));
      expect(reopened.read().single.state, CommandReceiptState.confirmed);
      expect(await reopened.claim(_receipt()), isFalse);
    },
  );

  test('shared writer grants one of simultaneous duplicate claims', () async {
    final (prefs, _) = await _setup();
    final journal = PendingCommandJournal.forProfile(prefs, 'phone');
    expect(
      identical(journal, PendingCommandJournal.forProfile(prefs, 'phone')),
      isTrue,
    );
    final claims = await Future.wait([
      for (var i = 0; i < 30; i++) journal.claim(_receipt()),
    ]);
    expect(claims.where((admitted) => admitted).length, 1);
    expect(journal.read().length, 1);
  });

  test('concurrent distinct claims survive each durable reload', () async {
    final (prefs, _) = await _setup();
    final journal = PendingCommandJournal.forProfile(prefs, 'phone');
    expect(
      await Future.wait([
        for (var i = 0; i < 15; i++) journal.claim(_receipt(id: 'command_$i')),
      ]),
      everyElement(isTrue),
    );
    expect(journal.read().length, 15);
  });

  test('duplicate claim cannot change tab or overwrite state', () async {
    final (prefs, _) = await _setup();
    final journal = PendingCommandJournal.forProfile(prefs, 'phone');
    await journal.claim(_receipt());
    await journal.update(_receipt(state: CommandReceiptState.uncertain));
    expect(await journal.claim(_receipt(tab: 'other_tab')), isFalse);
    expect(journal.read().single.tabID, 'tab_1');
    expect(journal.read().single.state, CommandReceiptState.uncertain);
    await expectLater(journal.update(_receipt(tab: 'other_tab')), _failure);
    await expectLater(journal.update(_receipt(id: 'absent')), _failure);
    expect(journal.read().single.tabID, 'tab_1');
  });

  test('uncertain and terminal receipts cannot become sent again', () async {
    final (prefs, _) = await _setup();
    final journal = PendingCommandJournal.forProfile(prefs, 'phone');
    await journal.claim(_receipt());
    await journal.update(_receipt(state: CommandReceiptState.uncertain));
    await expectLater(journal.update(_receipt()), _failure);
    await journal.update(_receipt(state: CommandReceiptState.confirmed));
    await expectLater(
      journal.update(_receipt(state: CommandReceiptState.uncertain)),
      _failure,
    );
    await expectLater(
      journal.update(_receipt(state: CommandReceiptState.failed)),
      _failure,
    );
    expect(journal.read().single.state, CommandReceiptState.confirmed);
    await journal.claim(_receipt(id: 'failed_command'));
    await journal.update(
      _receipt(id: 'failed_command', state: CommandReceiptState.failed),
    );
    await expectLater(journal.update(_receipt(id: 'failed_command')), _failure);
  });

  test(
    'unknown profile and malformed profile registry block admission',
    () async {
      final (prefs, _) = await _setup();
      expect(() => PendingCommandJournal.forProfile(prefs, 'absent'), _failure);
      expect(
        () => PendingCommandJournal.forProfile(prefs, 'phone.bad'),
        _failure,
      );
      expect(
        () => PendingCommandJournal.forProfile(prefs, 'phone\n'),
        _failure,
      );
      final journal = PendingCommandJournal.forProfile(prefs, 'phone');
      await prefs.setString('oc.profiles', '{invalid');
      expect(journal.read, _failure);
      await expectLater(journal.claim(_receipt()), _failure);
      expect(prefs.containsKey(_key), isFalse);
    },
  );

  test(
    'reload checks durable membership rather than optimistic cache',
    () async {
      final (prefs, disk) = await _setup();
      final journal = PendingCommandJournal.forProfile(prefs, 'phone');
      await disk.setValue('String', 'flutter.oc.profiles', '[]');
      await expectLater(journal.claim(_receipt()), _failure);
      expect(prefs.containsKey(_key), isFalse);
    },
  );

  test(
    'profiles keep independent journals even for matching command IDs',
    () async {
      final (prefs, _) = await _setup();
      final phone = PendingCommandJournal.forProfile(prefs, 'phone');
      final tablet = PendingCommandJournal.forProfile(prefs, 'tablet');
      expect(await phone.claim(_receipt()), isTrue);
      expect(await tablet.claim(_receipt(tab: 'tablet_tab')), isTrue);
      await phone.update(_receipt(state: CommandReceiptState.confirmed));
      expect(tablet.read().single.state, CommandReceiptState.sent);
      expect(prefs.containsKey('oc.pendingCommands.tablet'), isTrue);
      await PendingCommandJournal.closeProfile(prefs, 'phone');
      await prefs.remove(_key);
      expect(tablet.read().single.tabID, 'tablet_tab');
    },
  );

  for (final raw in <Object>[
    'not json',
    42,
    jsonEncode({'schemaVersion': 2, 'commands': []}),
    jsonEncode({'schemaVersion': 1, 'commands': [], 'payload': 'unexpected'}),
    jsonEncode({
      'schemaVersion': 1,
      'commands': ['invalid'],
    }),
    _encoded([_receipt(), _receipt()]),
    jsonEncode({
      'schemaVersion': 1,
      'commands': [
        {..._receipt().toJson(), 'prompt': 'unexpected'},
      ],
    }),
  ]) {
    test(
      'malformed journal fails closed: ${raw.runtimeType} #${raw.hashCode}',
      () async {
        final (prefs, _) = await _setup(raw: raw);
        final journal = PendingCommandJournal.forProfile(prefs, 'phone');
        expect(journal.read, _failure);
        await prefs.remove(_key);
        await expectLater(journal.claim(_receipt()), _failure);
      },
    );
  }

  test('capacity keeps tombstones and refuses another new command', () async {
    final receipts = [
      for (var i = 0; i < PendingCommandJournal.maxEntries; i++)
        _receipt(id: 'command_$i', state: CommandReceiptState.confirmed),
    ];
    final (prefs, _) = await _setup(raw: _encoded(receipts));
    final journal = PendingCommandJournal.forProfile(prefs, 'phone');
    expect(await journal.claim(_receipt(id: 'command_0')), isFalse);
    await expectLater(journal.claim(_receipt(id: 'another_command')), _failure);
    expect(journal.read().length, PendingCommandJournal.maxEntries);
    expect(journal.read().first.state, CommandReceiptState.confirmed);
  });

  test(
    'byte limit refuses a new claim without removing old tombstones',
    () async {
      final longID = 'a' * 240;
      CommandReceipt large(int index) => CommandReceipt(
        commandID: '${longID}_$index',
        receiptID: 'b' * 256,
        sessionID: 'c' * 256,
        tabID: 'd' * 256,
        scope: _scope,
        createdAt: 100,
        state: CommandReceiptState.confirmed,
      );
      final receipts = <CommandReceipt>[];
      while (utf8
              .encode(_encoded([...receipts, large(receipts.length)]))
              .length <=
          PendingCommandJournal.maxBytes) {
        receipts.add(large(receipts.length));
      }
      expect(receipts.length, lessThan(PendingCommandJournal.maxEntries));
      final original = _encoded(receipts);
      final (prefs, _) = await _setup(raw: original);
      final journal = PendingCommandJournal.forProfile(prefs, 'phone');
      await expectLater(
        journal.claim(
          large(receipts.length).copyWith(state: CommandReceiptState.sent),
        ),
        _failure,
      );
      expect(prefs.getString(_key), original);
      expect(journal.read().length, receipts.length);
      expect(
        await journal.claim(_receipt(id: receipts.first.commandID)),
        isFalse,
      );
    },
  );

  test(
    'oversized and over-capacity journals cannot be read or replaced',
    () async {
      final (prefs, _) = await _setup(
        raw: 'x' * (PendingCommandJournal.maxBytes + 1),
      );
      final journal = PendingCommandJournal.forProfile(prefs, 'phone');
      expect(journal.read, _failure);
      await expectLater(journal.claim(_receipt()), _failure);
      final (otherPrefs, _) = await _setup(
        raw: _encoded([
          for (var i = 0; i <= PendingCommandJournal.maxEntries; i++)
            _receipt(id: 'command_$i'),
        ]),
      );
      final other = PendingCommandJournal.forProfile(otherPrefs, 'phone');
      expect(other.read, _failure);
    },
  );

  for (final failure in ['refuse', 'throw', 'readback', 'discard']) {
    test(
      'unverified $failure write never grants admission or reopens',
      () async {
        final (prefs, disk) = await _setup();
        final journal = PendingCommandJournal.forProfile(prefs, 'phone');
        disk.refuse = failure == 'refuse';
        disk.throwWrite = failure == 'throw';
        disk.failReadback = failure == 'readback';
        disk.discardWrite = failure == 'discard';
        await expectLater(journal.claim(_receipt()), _failure);
        disk.refuse = false;
        disk.throwWrite = false;
        disk.failReadback = false;
        disk.discardWrite = false;
        disk.failReads = false;
        await prefs.reload();
        expect(journal.read, _failure);
        await expectLater(journal.claim(_receipt()), _failure);
        expect(
          () => PendingCommandJournal.forProfile(prefs, 'phone').read(),
          _failure,
        );
      },
    );
  }

  test('reload failure fails closed before a platform write', () async {
    final (prefs, disk) = await _setup();
    final journal = PendingCommandJournal.forProfile(prefs, 'phone');
    disk.failReads = true;
    await expectLater(journal.claim(_receipt()), _failure);
    disk.failReads = false;
    await prefs.reload();
    expect(prefs.containsKey(_key), isFalse);
    await expectLater(journal.claim(_receipt()), _failure);
  });

  test(
    'deletion closes immediately and drains in-flight and queued claims',
    () async {
      final (prefs, disk) = await _setup();
      final journal = PendingCommandJournal.forProfile(prefs, 'phone');
      disk.hold = true;
      final writing = journal.claim(_receipt());
      final rejectedWrite = expectLater(writing, _failure);
      await disk.entered.future;
      final queued = journal.claim(_receipt(id: 'queued_command'));
      final rejectedQueued = expectLater(queued, _failure);
      var drained = false;
      final draining = PendingCommandJournal.closeProfile(
        prefs,
        'phone',
      ).then((_) => drained = true);
      expect(drained, isFalse);
      expect(journal.read, _failure);
      expect(() => PendingCommandJournal.forProfile(prefs, 'phone'), _failure);
      await expectLater(journal.claim(_receipt(id: 'late_command')), _failure);
      disk.release.complete();
      await rejectedWrite;
      await rejectedQueued;
      await draining;
      expect(drained, isTrue);
      await prefs.remove(_key);
      await prefs.setString('oc.profiles', '[{"id":"tablet"}]');
      await prefs.reload();
      expect(prefs.containsKey(_key), isFalse);
      await expectLater(journal.claim(_receipt()), _failure);
    },
  );

  test('close before owner creation prevents all later admission', () async {
    final (prefs, _) = await _setup();
    await PendingCommandJournal.closeProfile(prefs, 'phone');
    expect(() => PendingCommandJournal.forProfile(prefs, 'phone'), _failure);
    expect(prefs.containsKey(_key), isFalse);
  });
}
