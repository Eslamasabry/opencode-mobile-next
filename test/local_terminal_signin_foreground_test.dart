import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/local_terminal.dart';
import 'package:opencode_mobile/domain/agent_sign_in_foreground.dart';

import 'support/fake_local_terminal.dart';

class _Lease implements AgentSignInForegroundLease {
  _Lease({bool pending = false}) {
    if (!pending) admission.complete();
  }
  final admission = Completer<void>();
  final losses = StreamController<void>.broadcast();
  bool held = true;
  int releases = 0;
  @override
  Future<void> get ready => admission.future;
  @override
  bool get active => held && admission.isCompleted;
  @override
  Stream<void> get lost => losses.stream;
  @override
  Future<void> release() async {
    releases++;
    held = false;
  }

  Future<void> close() => losses.close();
}

class _Foreground implements AgentSignInForegroundPort {
  _Foreground(this.lease);
  final _Lease lease;
  int reservations = 0;
  @override
  AgentSignInForegroundLease reserveAgentSignInForeground() {
    reservations++;
    return lease;
  }
}

class _DrainBackend extends FakeLocalTerminalBackend {
  bool removeFails = false;
  Completer<void>? removeGate;
  @override
  Future<void> remove(int id) async {
    calls.add('drain-begins $id');
    await removeGate?.future;
    if (removeFails) throw StateError('private native drain text');
    await super.remove(id);
  }
}

void main() {
  test(
    'missing foreground binding refuses sign-in before native PTY start',
    () async {
      final backend = FakeLocalTerminalBackend();
      final sessions = LocalTerminalSessions(backend: backend);
      final shell = sessions.startSignIn(
        'no-registered-owner-foreground-test',
        ['fx', 'login'],
      );
      await pumpEventQueue();
      expect(
        backend.calls.where((call) => call.startsWith('sign-in ')),
        isEmpty,
      );
      expect(shell.state, LocalShellState.failed);
      expect(shell.failure, isNotEmpty);
      sessions.dispose();
      await backend.close();
    },
  );

  test(
    'reserves synchronously and waits for active foreground before PTY start',
    () async {
      final backend = FakeLocalTerminalBackend();
      final lease = _Lease(pending: true);
      final port = _Foreground(lease);
      final sessions = LocalTerminalSessions(
        backend: backend,
        signInForeground: port,
      );
      final shell = sessions.startSignIn('owner', ['fx', 'login']);
      expect(port.reservations, 1);
      await pumpEventQueue();
      expect(
        backend.calls.where((call) => call.startsWith('sign-in ')),
        isEmpty,
      );
      expect(shell.state, LocalShellState.starting);
      lease.admission.complete();
      await pumpEventQueue();
      expect(shell.state, LocalShellState.running);
      await sessions.endSignIn(shell);
      expect(lease.releases, 1);
      sessions.dispose();
      await backend.close();
      await lease.close();
    },
  );

  test(
    'ending pending native start drains its late exact ID before releasing foreground',
    () async {
      final backend = FakeLocalTerminalBackend()..startGate = Completer<void>();
      final lease = _Lease();
      final sessions = LocalTerminalSessions(
        backend: backend,
        signInForeground: _Foreground(lease),
      );
      final shell = sessions.startSignIn('owner', ['fx', 'login']);
      await pumpEventQueue();
      var ended = false;
      final closing = sessions.endSignIn(shell).then((_) => ended = true);
      await pumpEventQueue();
      // Capture all observations before resolving the fake native request,
      // including on the intentionally broken baseline.
      final endedEarly = ended;
      final releasedEarly = lease.releases;
      backend.startGate!.complete();
      await pumpEventQueue();
      await closing;
      expect(endedEarly, isFalse);
      expect(releasedEarly, 0);
      expect(backend.calls, contains('remove 1'));
      expect(lease.releases, 1);
      sessions.dispose();
      await backend.close();
      await lease.close();
    },
  );

  test(
    'disposing a pending sign-in drains late native ID without reopening URL',
    () async {
      final backend = FakeLocalTerminalBackend()..startGate = Completer<void>();
      final lease = _Lease();
      final sessions = LocalTerminalSessions(
        backend: backend,
        signInForeground: _Foreground(lease),
      );
      final shell = sessions.startSignIn('owner', ['fx', 'login']);
      final opened = <String>[];
      shell.onOpenUrl = opened.add;
      await pumpEventQueue();
      sessions.dispose();
      final releasedEarly = lease.releases;
      backend.startGate!.complete();
      await pumpEventQueue();
      backend.openUrl(1, 'https://vercel.com/oauth/device');
      await pumpEventQueue();
      expect(releasedEarly, 0);
      expect(backend.calls, contains('remove 1'));
      expect(lease.releases, 1);
      expect(opened, isEmpty);
      await backend.close();
      await lease.close();
    },
  );

  test(
    'cancel during foreground admission ends promptly without issuing native start',
    () async {
      final backend = FakeLocalTerminalBackend();
      final lease = _Lease(pending: true);
      final sessions = LocalTerminalSessions(
        backend: backend,
        signInForeground: _Foreground(lease),
      );
      final shell = sessions.startSignIn('owner', ['fx', 'login']);
      await sessions.endSignIn(shell);
      expect(lease.releases, 1);
      expect(
        backend.calls.where((call) => call.startsWith('sign-in ')),
        isEmpty,
      );
      lease.admission.complete();
      await pumpEventQueue();
      expect(
        backend.calls.where((call) => call.startsWith('sign-in ')),
        isEmpty,
      );
      sessions.dispose();
      await backend.close();
      await lease.close();
    },
  );

  test(
    'admission refusal uses fixed words and releases without native launch',
    () async {
      final backend = FakeLocalTerminalBackend();
      final lease = _Lease(pending: true);
      final sessions = LocalTerminalSessions(
        backend: backend,
        signInForeground: _Foreground(lease),
      );
      final shell = sessions.startSignIn('owner', ['fx', 'login']);
      lease.admission.completeError(
        const AgentSignInForegroundException(
          AgentSignInForegroundFailure.permission,
        ),
      );
      await pumpEventQueue();
      expect(
        shell.failure,
        'Allow notifications before signing in, then try again.',
      );
      expect(
        backend.calls.where((call) => call.startsWith('sign-in ')),
        isEmpty,
      );
      expect(lease.releases, 1);
      sessions.dispose();
      await backend.close();
      await lease.close();
    },
  );

  test('inactive admission cannot launch a PTY', () async {
    final backend = FakeLocalTerminalBackend();
    final lease = _Lease()..held = false;
    final sessions = LocalTerminalSessions(
      backend: backend,
      signInForeground: _Foreground(lease),
    );
    final shell = sessions.startSignIn('owner', ['fx', 'login']);
    await pumpEventQueue();
    expect(shell.failure, 'Could not keep sign-in running. Try again.');
    expect(backend.calls.where((call) => call.startsWith('sign-in ')), isEmpty);
    expect(lease.releases, 1);
    sessions.dispose();
    await backend.close();
    await lease.close();
  });

  test('native sign-in errors never expose platform or CLI text', () async {
    final backend = FakeLocalTerminalBackend()
      ..startFailure = 'private CLI credential text';
    final lease = _Lease();
    final sessions = LocalTerminalSessions(
      backend: backend,
      signInForeground: _Foreground(lease),
    );
    final shell = sessions.startSignIn('owner', ['fx', 'login']);
    await pumpEventQueue();
    expect(shell.failure, 'Could not keep sign-in running. Try again.');
    expect(lease.releases, 1);
    sessions.dispose();
    await backend.close();
    await lease.close();
  });

  test(
    'exit holds foreground through authentication checking until explicit end',
    () async {
      final backend = FakeLocalTerminalBackend();
      final lease = _Lease();
      final sessions = LocalTerminalSessions(
        backend: backend,
        signInForeground: _Foreground(lease),
      );
      final shell = sessions.startSignIn('owner', ['fx', 'login']);
      await pumpEventQueue();
      final opened = <String>[];
      shell.onOpenUrl = opened.add;
      backend.openUrl(1, 'https://vercel.com/oauth/device');
      backend.exit(1, 0);
      await pumpEventQueue();
      expect(opened, ['https://vercel.com/oauth/device']);
      expect(shell.state, LocalShellState.exited);
      expect(lease.active, isTrue);
      expect(lease.releases, 0);
      expect(backend.calls, isNot(contains('remove 1')));
      await sessions.endSignIn(shell);
      expect(lease.releases, 1);
      sessions.dispose();
      await backend.close();
      await lease.close();
    },
  );

  test(
    'foreground loss drains only its sign-in and leaves normal shells alone',
    () async {
      final backend = FakeLocalTerminalBackend();
      final lease = _Lease();
      final sessions = LocalTerminalSessions(
        backend: backend,
        signInForeground: _Foreground(lease),
      );
      final normal = sessions.startShell();
      await pumpEventQueue();
      final shell = sessions.startSignIn('owner', ['fx', 'login']);
      await pumpEventQueue();
      lease.held = false;
      lease.losses.add(null);
      await pumpEventQueue();
      expect(shell.failure, 'Could not keep sign-in running. Try again.');
      expect(backend.calls, contains('remove 2'));
      expect(backend.calls, isNot(contains('remove 1')));
      expect(normal.state, LocalShellState.running);
      expect(sessions.shells, [normal]);
      expect(lease.releases, 1);
      sessions.dispose();
      await backend.close();
      await lease.close();
    },
  );

  test(
    'URL handoff rechecks protection before a queued loss event arrives',
    () async {
      final backend = FakeLocalTerminalBackend();
      final lease = _Lease();
      final sessions = LocalTerminalSessions(
        backend: backend,
        signInForeground: _Foreground(lease),
      );
      final shell = sessions.startSignIn('owner', ['fx', 'login']);
      final opened = <String>[];
      shell.onOpenUrl = opened.add;
      await pumpEventQueue();
      lease.held = false;
      backend.openUrl(1, 'https://vercel.com/oauth/device');
      await pumpEventQueue();
      expect(opened, isEmpty);
      expect(backend.calls, contains('remove 1'));
      expect(lease.releases, 1);
      sessions.dispose();
      await backend.close();
      await lease.close();
    },
  );

  test(
    'duplicate ends share a drain and hold protection until native removal completes',
    () async {
      final backend = _DrainBackend()..removeGate = Completer<void>();
      final lease = _Lease();
      final sessions = LocalTerminalSessions(
        backend: backend,
        signInForeground: _Foreground(lease),
      );
      final shell = sessions.startSignIn('owner', ['fx', 'login']);
      await pumpEventQueue();
      final first = sessions.endSignIn(shell);
      final second = sessions.endSignIn(shell);
      expect(identical(first, second), isTrue);
      await pumpEventQueue();
      expect(lease.releases, 0);
      backend.removeGate!.complete();
      await first;
      expect(backend.calls.where((call) => call == 'remove 1'), hasLength(1));
      await sessions.endSignIn(shell);
      expect(lease.releases, 1);
      sessions.dispose();
      await backend.close();
      await lease.close();
    },
  );

  test(
    'failed drain retains protection and allows an exact-ID retry',
    () async {
      final backend = _DrainBackend()..removeFails = true;
      final lease = _Lease();
      final sessions = LocalTerminalSessions(
        backend: backend,
        signInForeground: _Foreground(lease),
      );
      final shell = sessions.startSignIn('owner', ['fx', 'login']);
      await pumpEventQueue();
      await expectLater(
        sessions.endSignIn(shell),
        throwsA(isA<AgentSignInForegroundException>()),
      );
      expect(lease.releases, 0);
      expect(lease.active, isTrue);
      backend.removeFails = false;
      await sessions.endSignIn(shell);
      expect(backend.calls, contains('remove 1'));
      expect(lease.releases, 1);
      sessions.dispose();
      await backend.close();
      await lease.close();
    },
  );

  test(
    'owner unbind drains registered pending PTY before releasing its delegate',
    () async {
      final backend = FakeLocalTerminalBackend()..startGate = Completer<void>();
      final lease = _Lease();
      final binding = AgentSignInForegroundRegistry.bind(
        'terminal-owner-delete-test',
        _Foreground(lease),
      );
      final sessions = LocalTerminalSessions(backend: backend);
      sessions.startSignIn('terminal-owner-delete-test', ['fx', 'login']);
      await pumpEventQueue();
      final unbinding = AgentSignInForegroundRegistry.unbind(binding);
      await pumpEventQueue();
      final releasesBeforeNativeCompletion = lease.releases;
      backend.startGate!.complete();
      await unbinding;
      expect(releasesBeforeNativeCompletion, 0);
      expect(backend.calls, contains('remove 1'));
      expect(lease.releases, 1);
      sessions.dispose();
      await backend.close();
      await lease.close();
    },
  );
}
