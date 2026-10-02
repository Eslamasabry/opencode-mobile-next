import 'dart:async';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/command_receipts.dart';
import 'package:opencode_mobile/state/pending_command_journal.dart';
import 'package:shared_preferences/shared_preferences.dart';

const _scope =
    'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa';
const _receipt = CommandReceipt(
  commandID: 'command_1',
  receiptID: 'msg_1',
  sessionID: 'ses_1',
  tabID: 'tab_1',
  scope: _scope,
  createdAt: 1,
  state: CommandReceiptState.sent,
);

Future<SharedPreferences> _preferences([String? snapshot]) async {
  SharedPreferences.setMockInitialValues({
    'oc.profiles': jsonEncode([
      {'id': 'profile_1'},
    ]),
    'oc.pendingCommands.profile_1': ?snapshot,
  });
  return SharedPreferences.getInstance();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test(
    'lost response then Retry looks up admission without another send',
    () async {
      final prefs = await _preferences();
      final journal = PendingCommandJournal.forProfile(prefs, 'profile_1');
      final controller = CommandReceiptController(journal);
      var sends = 0;
      var lookups = 0;
      final result = await controller.send(_receipt, (id) async {
        expect(id, _receipt.receiptID);
        // Admission happened before the connection dropped.
        sends++;
        throw StateError('response lost');
      });
      expect(result.state, CommandReceiptState.uncertain);
      final retried = await controller.retry(_receipt.commandID, (
        receipt,
      ) async {
        lookups++;
        expect(receipt.receiptID, _receipt.receiptID);
        return true;
      });
      expect(retried.state, CommandReceiptState.confirmed);
      await controller.send(_receipt, (_) async {
        sends++;
      });
      await controller.retry(_receipt.commandID, (_) async {
        lookups++;
        return true;
      });
      expect(sends, 1);
      expect(lookups, 1);
    },
  );

  for (final pendingState in [
    CommandReceiptState.sent,
    CommandReceiptState.uncertain,
  ]) {
    test(
      'restart reconciles ${pendingState.name} receipt and preserves tab',
      () async {
        final prefs = await _preferences();
        final journal = PendingCommandJournal.forProfile(prefs, 'profile_1');
        await journal.claim(_receipt);
        if (pendingState == CommandReceiptState.uncertain) {
          await journal.update(_receipt.copyWith(state: pendingState));
        }
        final snapshot = prefs.getString('oc.pendingCommands.profile_1')!;
        // New preferences instance and owner emulate a new process.
        final restartedPrefs = await _preferences(snapshot);
        final restarted = PendingCommandJournal.forProfile(
          restartedPrefs,
          'profile_1',
        );
        final controller = CommandReceiptController(restarted);
        var sends = 0;
        await controller.send(_receipt, (_) async {
          sends++;
        });
        expect(sends, 0);
        final confirmed = await controller.retry(_receipt.commandID, (
          receipt,
        ) async {
          expect(receipt.tabID, 'tab_1');
          expect(receipt.sessionID, 'ses_1');
          expect(receipt.state, pendingState);
          return true;
        });
        expect(confirmed.state, CommandReceiptState.confirmed);
        expect(restarted.read().single.state, CommandReceiptState.confirmed);
      },
    );
  }

  test('simultaneous sends with the same command dispatch only once', () async {
    final prefs = await _preferences();
    final controller = CommandReceiptController(
      PendingCommandJournal.forProfile(prefs, 'profile_1'),
    );
    final entered = Completer<void>();
    final release = Completer<void>();
    var sends = 0;
    final first = controller.send(_receipt, (_) async {
      sends++;
      entered.complete();
      await release.future;
    });
    await entered.future;
    final second = controller.send(_receipt, (_) async {
      sends++;
    });
    expect((await second).state, CommandReceiptState.sent);
    release.complete();
    expect((await first).state, CommandReceiptState.confirmed);
    expect(sends, 1);
  });

  test('missing lookup and lookup failure both stay uncertain', () async {
    final prefs = await _preferences();
    final journal = PendingCommandJournal.forProfile(prefs, 'profile_1');
    await journal.claim(_receipt);
    final controller = CommandReceiptController(journal);
    expect(
      (await controller.retry(_receipt.commandID, (_) async => false)).state,
      CommandReceiptState.uncertain,
    );
    expect(
      (await controller.retry(
        _receipt.commandID,
        (_) async => throw StateError('offline'),
      )).state,
      CommandReceiptState.uncertain,
    );
  });

  test('journal must be durably claimed before any dispatch', () async {
    final prefs = await _preferences('{corrupt');
    final controller = CommandReceiptController(
      PendingCommandJournal.forProfile(prefs, 'profile_1'),
    );
    var sends = 0;
    await expectLater(
      controller.send(_receipt, (_) async {
        sends++;
      }),
      throwsA(isA<CommandReceiptException>()),
    );
    expect(sends, 0);
  });

  test('same command cannot be borrowed by another tab or scope', () async {
    final prefs = await _preferences();
    final journal = PendingCommandJournal.forProfile(prefs, 'profile_1');
    await journal.claim(_receipt);
    final controller = CommandReceiptController(journal);
    var sends = 0;
    final borrowed = CommandReceipt(
      commandID: _receipt.commandID,
      receiptID: _receipt.receiptID,
      sessionID: _receipt.sessionID,
      tabID: 'tab_2',
      scope: _receipt.scope,
      createdAt: _receipt.createdAt,
      state: CommandReceiptState.sent,
    );
    await expectLater(
      controller.send(borrowed, (_) async {
        sends++;
      }),
      throwsA(isA<CommandReceiptException>()),
    );
    expect(sends, 0);
  });

  test(
    'failed receipt is a tombstone and cannot dispatch or query again',
    () async {
      final prefs = await _preferences();
      final journal = PendingCommandJournal.forProfile(prefs, 'profile_1');
      await journal.claim(_receipt);
      await journal.update(
        _receipt.copyWith(state: CommandReceiptState.failed),
      );
      final controller = CommandReceiptController(journal);
      var sends = 0;
      var lookups = 0;
      expect(
        (await controller.send(_receipt, (_) async {
          sends++;
        })).state,
        CommandReceiptState.failed,
      );
      expect(
        (await controller.retry(_receipt.commandID, (_) async {
          lookups++;
          return true;
        })).state,
        CommandReceiptState.failed,
      );
      expect(sends, 0);
      expect(lookups, 0);
    },
  );
}
