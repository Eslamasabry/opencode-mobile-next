import 'dart:async';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/managed_shell.dart';
import 'package:opencode_mobile/state/shell_output.dart';
import 'support/managed_shell_fakes.dart';

void main() {
  test('a malformed reset page leaves already loaded output intact', () async {
    final repo = FakeManagedShellRepository()..output = 'Original';
    final output = ShellOutputController(
      gateway: repo,
      shell: repo.shells.first,
    );
    addTearDown(output.dispose);
    await output.refresh();
    repo.pages.addAll({
      8: const ManagedShellOutput(
        text: '',
        cursor: 0,
        size: 0,
        truncated: false,
      ),
      0: const ManagedShellOutput(
        text: 'invalid',
        cursor: 9,
        size: 2,
        truncated: false,
      ),
    });
    await output.refresh();
    expect(output.error, isA<FormatException>());
    expect(output.text, 'Original');
    expect(output.cursor, 8);
  });

  test(
    'uses server byte cursors and never duplicates an unchanged page',
    () async {
      final repo = FakeManagedShellRepository()
        ..pages.addAll({
          0: const ManagedShellOutput(
            text: 'é',
            cursor: 2,
            size: 6,
            truncated: true,
          ),
          2: const ManagedShellOutput(
            text: '🙂',
            cursor: 6,
            size: 6,
            truncated: false,
          ),
          6: const ManagedShellOutput(
            text: '',
            cursor: 6,
            size: 6,
            truncated: false,
          ),
        });
      final output = ShellOutputController(
        gateway: repo,
        shell: repo.shells.first,
      );
      addTearDown(output.dispose);
      await output.refresh();
      expect(output.hasMore, isTrue);
      await output.refresh();
      await output.refresh();
      expect(repo.cursors, [0, 2, 6]);
      expect(output.text, 'é🙂');
      expect(output.hasMore, isFalse);
    },
  );

  test(
    'restarts a reset capture from zero instead of duplicating stale output',
    () async {
      final repo = FakeManagedShellRepository()..output = 'Old long output';
      final output = ShellOutputController(
        gateway: repo,
        shell: repo.shells.first,
      );
      addTearDown(output.dispose);
      await output.refresh();
      repo.output = 'New';
      await output.refresh(reconcileServer: true);
      expect(repo.cursors, [0, 15, 0]);
      expect(output.text, 'New');
      expect(output.cursor, 3);
    },
  );

  test(
    'bounded output preserves complete surrogate pairs and strips terminal escapes',
    () async {
      final repo = FakeManagedShellRepository()
        ..output =
            '${'a' * ShellOutputController.maxCharacters}🙂\u001b[32mOK\u001b[0m';
      final output = ShellOutputController(
        gateway: repo,
        shell: repo.shells.first,
      );
      addTearDown(output.dispose);
      await output.refresh();
      expect(output.trimmed, isTrue);
      expect(
        output.text.length,
        lessThanOrEqualTo(ShellOutputController.maxCharacters),
      );
      expect(output.displayText, endsWith('🙂OK'));
      expect(output.displayText, isNot(contains('\u001b')));
    },
  );

  test(
    'a missing shell after reconnect is not called a restart without evidence',
    () async {
      final repo = FakeManagedShellRepository();
      final output = ShellOutputController(
        gateway: repo,
        shell: repo.shells.first,
      );
      addTearDown(output.dispose);
      await output.refresh();
      final text = output.text;
      repo.shells = [];
      await output.refresh(reconcileServer: true);
      expect(output.available, isFalse);
      expect(output.serverRestarted, isFalse);
      expect(output.text, text);
      repo.identity = 'server-2';
      await output.refresh(reconcileServer: true);
      expect(output.serverRestarted, isTrue);
    },
  );

  test(
    'a retired repository response cannot replace the new connection state',
    () async {
      final old = FakeManagedShellRepository()
        ..pendingInfo = Completer<ManagedShell?>();
      final output = ShellOutputController(
        gateway: old,
        shell: old.shells.first,
      );
      addTearDown(output.dispose);
      final oldRead = output.refresh();
      await Future<void>.delayed(Duration.zero);
      final next = FakeManagedShellRepository()..output = 'New connection';
      output.bind(next);
      await output.refresh(reconcileServer: true);
      old.pendingInfo!.complete(null);
      await oldRead;
      expect(output.text, 'New connection');
      expect(output.available, isTrue);
    },
  );

  test(
    'concurrent refreshes coalesce and closing a viewer discards late results',
    () async {
      final repo = FakeManagedShellRepository()
        ..pendingInfo = Completer<ManagedShell?>();
      final output = ShellOutputController(
        gateway: repo,
        shell: repo.shells.first,
      );
      var changes = 0;
      output.addListener(() => changes++);
      final request = output.refresh();
      await Future<void>.delayed(Duration.zero);
      await output.refresh();
      expect(repo.infoReads, 1);
      output.dispose();
      final changesAtClose = changes;
      repo.pendingInfo!.complete(repo.shells.first);
      await request;
      expect(changes, changesAtClose);
      expect(repo.outputReads, 0);
    },
  );

  test('errors preserve visible output and a retry clears the error', () async {
    final repo = FakeManagedShellRepository();
    final output = ShellOutputController(
      gateway: repo,
      shell: repo.shells.first,
    );
    addTearDown(output.dispose);
    await output.refresh();
    final text = output.text;
    repo.failure = StateError('Offline');
    await output.refresh();
    expect(output.error, isNotNull);
    expect(output.text, text);
    repo.failure = null;
    await output.refresh();
    expect(output.error, isNull);
  });
}
