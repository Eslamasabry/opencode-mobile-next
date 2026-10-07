import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/agent_tools/browser_claude_launch.dart';

BrowserEnrollmentTarget target({
  String profile = 'profile',
  String source = 'paseo:project',
  String session = 'session',
  String daemon = 'daemon',
}) => BrowserEnrollmentTarget(
  profileId: profile,
  sourceId: source,
  sessionId: session,
  daemonAgentId: daemon,
);

BrowserLaunchReservation reservation(
  BrowserEnrollmentTarget target,
  String id,
) => BrowserLaunchReservation(
  id: id,
  launchId: '0123456789abcdef0123456789abcdef',
  target: target,
);

class _Port implements BrowserClaudeLaunchPort {
  final requests = <BrowserEnrollmentTarget>[];
  final replies = <Completer<BrowserLaunchReservation?>>[];
  final revocations = <BrowserLaunchReservation>[];
  bool failRevoke = false;
  Completer<void>? heldRevoke;

  @override
  Future<BrowserLaunchReservation?> beforeBrowserClaudeLaunch({
    required String profileId,
    required String sourceId,
    required String sessionId,
    required String daemonAgentId,
  }) {
    requests.add(
      BrowserEnrollmentTarget(
        profileId: profileId,
        sourceId: sourceId,
        sessionId: sessionId,
        daemonAgentId: daemonAgentId,
      ),
    );
    final reply = Completer<BrowserLaunchReservation?>();
    replies.add(reply);
    return reply.future;
  }

  @override
  Future<void> revokeBrowserClaudeLaunch(
    BrowserLaunchReservation reservation,
  ) async {
    revocations.add(reservation);
    if (failRevoke) throw StateError('private native error');
    await heldRevoke?.future;
  }
}

Future<void> flush() => Future<void>.delayed(Duration.zero);

Future<BrowserLaunchReservation?> _accept(
  BrowserClaudeLaunchRegistry registry,
  _Port port,
  BrowserEnrollmentTarget scope,
  String id,
) async {
  final result = registry.reserve(scope);
  await flush();
  port.replies.last.complete(reservation(scope, id));
  return result;
}

void main() {
  test(
    'shared requested choice follows draft aliases and session lifecycle',
    () async {
      final registry = BrowserClaudeLaunchRegistry();
      expect(
        registry.setRequested(
          profileId: 'profile',
          sourceId: 'paseo:project',
          sessionId: 'draft',
          requested: true,
        ),
        isTrue,
      );
      registry.moveRequest(
        profileId: 'profile',
        sourceId: 'paseo:project',
        oldSessionId: 'draft',
        newSessionId: 'daemon',
      );
      expect(
        registry.isRequested(
          profileId: 'profile',
          sourceId: 'paseo:project',
          sessionId: 'draft',
        ),
        isFalse,
      );
      expect(
        registry.isRequested(
          profileId: 'profile',
          sourceId: 'paseo:project',
          sessionId: 'daemon',
        ),
        isTrue,
      );
      // A different gateway observes the same canonical choice after abort.
      await registry.revokeSession(
        profileId: 'profile',
        sourceId: 'paseo:project',
        sessionId: 'daemon',
      );
      expect(
        registry.isRequested(
          profileId: 'profile',
          sourceId: 'paseo:project',
          sessionId: 'daemon',
        ),
        isTrue,
      );
      registry.moveRequest(
        profileId: 'profile',
        sourceId: 'paseo:project',
        oldSessionId: 'unknown',
        newSessionId: 'other',
      );
      expect(
        registry.isRequested(
          profileId: 'profile',
          sourceId: 'paseo:project',
          sessionId: 'other',
        ),
        isFalse,
      );
      await registry.revokeSource(
        profileId: 'profile',
        sourceId: 'paseo:project',
      );
      expect(
        registry.isRequested(
          profileId: 'profile',
          sourceId: 'paseo:project',
          sessionId: 'daemon',
        ),
        isFalse,
      );
      await registry.close();
    },
  );

  test(
    'requested choice is bounded and bulk cleanup releases admission',
    () async {
      final registry = BrowserClaudeLaunchRegistry();
      bool enable(String profile, String session) => registry.setRequested(
        profileId: profile,
        sourceId: 'source',
        sessionId: session,
        requested: true,
      );
      expect(enable('', 'invalid'), isFalse);
      for (var i = 0; i < 128; i++) {
        expect(enable('profile', 's$i'), isTrue);
      }
      expect(enable('profile', 's0'), isTrue);
      expect(enable('profile', 'overflow'), isFalse);
      registry.moveRequest(
        profileId: 'profile',
        sourceId: 'source',
        oldSessionId: 's0',
        newSessionId: 'moved',
      );
      expect(
        registry.isRequested(
          profileId: 'profile',
          sourceId: 'source',
          sessionId: 'moved',
        ),
        isTrue,
      );
      expect(
        registry.setRequested(
          profileId: 'profile',
          sourceId: 'source',
          sessionId: 'moved',
          requested: false,
        ),
        isTrue,
      );
      expect(enable('other', 'other'), isTrue);
      await registry.revokeProfile(profileId: 'profile');
      expect(
        registry.isRequested(
          profileId: 'other',
          sourceId: 'source',
          sessionId: 'other',
        ),
        isTrue,
      );
      expect(enable('profile', 'new'), isTrue);
      await registry.close();
      expect(
        registry.isRequested(
          profileId: 'other',
          sourceId: 'source',
          sessionId: 'other',
        ),
        isFalse,
      );
      expect(enable('profile', 'new'), isFalse);
      expect(
        registry.setRequested(
          profileId: 'profile',
          sourceId: 'source',
          sessionId: 'new',
          requested: false,
        ),
        isTrue,
      );
    },
  );

  test('default port grants nothing and lifecycle cleanup succeeds', () async {
    final registry = BrowserClaudeLaunchRegistry();
    expect(await registry.reserve(target()), isNull);
    await registry.revokeProfile(profileId: 'profile');
    await registry.close();
    expect(await registry.reserve(target()), isNull);
  });

  test('scope validation follows byte limits and exact trusted mapping', () {
    expect(target(), target());
    expect(target().hashCode, target().hashCode);
    expect(target().matches(target(daemon: 'different')), isFalse);
    expect(target(profile: '').valid, isFalse);
    expect(target(source: 'bad\nsource').valid, isFalse);
    expect(target(session: List.filled(65, 'é').join()).valid, isFalse);
    final scope = target();
    expect(reservation(scope, 'native-id').matches(scope), isTrue);
    expect(reservation(scope, 'native/id').matches(scope), isFalse);
    expect(
      reservation(scope, 'native').matches(target(source: 'other')),
      isFalse,
    );
    expect(
      BrowserLaunchReservation(
        id: 'native',
        launchId: 'ABC',
        target: scope,
      ).matches(scope),
      isFalse,
    );
  });

  test('invalid scopes never reach the native port', () async {
    final port = _Port();
    final registry = BrowserClaudeLaunchRegistry(port: port);
    expect(await registry.reserve(target(daemon: '')), isNull);
    expect(port.requests, isEmpty);
  });

  test(
    'transport retirement revokes grants while preserving browser choice',
    () async {
      final port = _Port();
      final registry = BrowserClaudeLaunchRegistry(port: port);
      registry.setRequested(
        profileId: 'profile',
        sourceId: 'paseo:project',
        sessionId: 'session',
        requested: true,
      );
      final grant = (await _accept(registry, port, target(), 'native'))!;
      await registry.revokeSource(
        profileId: 'profile',
        sourceId: 'paseo:project',
        clearRequests: false,
      );
      expect(registry.isCurrent(grant), isFalse);
      expect(port.revocations, [grant]);
      expect(
        registry.isRequested(
          profileId: 'profile',
          sourceId: 'paseo:project',
          sessionId: 'session',
        ),
        isTrue,
      );
      await registry.revokeSource(
        profileId: 'profile',
        sourceId: 'paseo:project',
      );
      expect(
        registry.isRequested(
          profileId: 'profile',
          sourceId: 'paseo:project',
          sessionId: 'session',
        ),
        isFalse,
      );
      await registry.close();
    },
  );

  test(
    'generation fence rejects revoked, replaced and closed grants',
    () async {
      final port = _Port();
      final registry = BrowserClaudeLaunchRegistry(port: port);
      final first = (await _accept(registry, port, target(), 'first'))!;
      expect(registry.isCurrent(first), isTrue);
      await registry.revokeSession(
        profileId: 'profile',
        sourceId: 'paseo:project',
        sessionId: 'session',
      );
      expect(registry.isCurrent(first), isFalse);
      final second = (await _accept(registry, port, target(), 'second'))!;
      expect(registry.isCurrent(second), isTrue);
      final replacement = target(daemon: 'replacement');
      final next = registry.reserve(replacement);
      expect(registry.isCurrent(second), isFalse);
      await flush();
      final third = reservation(replacement, 'third');
      port.replies.last.complete(third);
      expect(await next, same(third));
      expect(registry.isCurrent(third), isTrue);
      expect(registry.isCurrent(reservation(replacement, 'foreign')), isFalse);
      await registry.close();
      expect(registry.isCurrent(third), isFalse);
    },
  );

  test(
    'concurrent gateways and later turns reuse the same generation',
    () async {
      final port = _Port();
      final registry = BrowserClaudeLaunchRegistry(port: port);
      final first = registry.reserve(target());
      final second = registry.reserve(target());
      await flush();
      expect(port.requests, hasLength(1));
      final grant = reservation(target(), 'native-1');
      port.replies.single.complete(grant);
      expect(await first, same(grant));
      expect(await second, same(grant));
      expect(await registry.reserve(target()), same(grant));
      expect(port.requests, hasLength(1));
      await registry.close();
      expect(port.revocations, [grant]);
    },
  );

  test(
    'replacement retires exact old generation before reserving new',
    () async {
      final port = _Port();
      final registry = BrowserClaudeLaunchRegistry(port: port);
      final old = await _accept(registry, port, target(), 'native-old');
      port.heldRevoke = Completer<void>();
      final replacement = target(daemon: 'replacement');
      final next = registry.reserve(replacement);
      await flush();
      expect(port.revocations, [old]);
      expect(port.requests, hasLength(1));
      port.heldRevoke!.complete();
      await flush();
      expect(port.requests.last, replacement);
      port.replies.last.complete(reservation(replacement, 'native-new'));
      expect((await next)!.id, 'native-new');
      await registry.close();
    },
  );

  test(
    'late ACK after deletion is revoked without disturbing replacement',
    () async {
      final port = _Port();
      final registry = BrowserClaudeLaunchRegistry(port: port);
      final first = registry.reserve(target());
      await flush();
      await registry.revokeSession(
        profileId: 'profile',
        sourceId: 'paseo:project',
        sessionId: 'session',
      );
      final replacement = registry.reserve(target());
      await flush();
      final latest = reservation(target(), 'native-new');
      port.replies[1].complete(latest);
      expect(await replacement, same(latest));
      final stale = reservation(target(), 'native-old');
      port.replies[0].complete(stale);
      expect(await first, isNull);
      expect(port.revocations, [stale]);
      expect(await registry.reserve(target()), same(latest));
      await registry.close();
    },
  );

  test('close fences pending ACKs and new calls', () async {
    final port = _Port();
    final registry = BrowserClaudeLaunchRegistry(port: port);
    final pending = registry.reserve(target());
    await flush();
    await registry.close();
    final stale = reservation(target(), 'late');
    port.replies.single.complete(stale);
    expect(await pending, isNull);
    expect(port.revocations, [stale]);
    expect(await registry.reserve(target()), isNull);
  });

  test('mismatched ACK is retired and never used', () async {
    final port = _Port();
    final registry = BrowserClaudeLaunchRegistry(port: port);
    final pending = registry.reserve(target());
    await flush();
    final wrong = reservation(target(profile: 'other'), 'wrong-scope');
    port.replies.single.complete(wrong);
    expect(await pending, isNull);
    expect(port.revocations, [wrong]);
    await registry.close();
  });

  test(
    'bulk source and profile revocation preserve unrelated scopes',
    () async {
      final port = _Port();
      final registry = BrowserClaudeLaunchRegistry(port: port);
      final a = await _accept(registry, port, target(), 'a');
      final bScope = target(source: 'paseo:other');
      final b = await _accept(registry, port, bScope, 'b');
      final cScope = target(profile: 'other-profile');
      final c = await _accept(registry, port, cScope, 'c');
      await registry.revokeSource(
        profileId: 'profile',
        sourceId: 'paseo:project',
      );
      expect(port.revocations, [a]);
      expect(await registry.reserve(bScope), same(b));
      await registry.revokeProfile(profileId: 'profile');
      expect(port.revocations, [a, b]);
      expect(await registry.reserve(cScope), same(c));
      await registry.close();
      expect(port.revocations, [a, b, c]);
    },
  );

  test(
    'failed cleanup quarantines enrollment and close retries cleanup',
    () async {
      final port = _Port();
      final registry = BrowserClaudeLaunchRegistry(port: port);
      final grant = await _accept(registry, port, target(), 'native');
      expect(
        registry.setRequested(
          profileId: 'profile',
          sourceId: 'paseo:project',
          sessionId: 'session',
          requested: true,
        ),
        isTrue,
      );
      port.failRevoke = true;
      await registry.revokeSession(
        profileId: 'profile',
        sourceId: 'paseo:project',
        sessionId: 'session',
      );
      expect(await registry.reserve(target()), isNull);
      expect(
        registry.isRequested(
          profileId: 'profile',
          sourceId: 'paseo:project',
          sessionId: 'session',
        ),
        isTrue,
      );
      expect(
        registry.setRequested(
          profileId: 'profile',
          sourceId: 'paseo:project',
          sessionId: 'session',
          requested: true,
        ),
        isFalse,
      );
      expect(port.requests, hasLength(1));
      port.failRevoke = false;
      await registry.close();
      expect(port.revocations, [grant, grant]);
    },
  );

  test(
    'eight timed-out native requests retain slots until late settlement',
    () async {
      final port = _Port();
      final registry = BrowserClaudeLaunchRegistry(
        port: port,
        timeout: const Duration(milliseconds: 20),
      );
      final pending = [
        for (var i = 0; i < 8; i++) registry.reserve(target(session: 's$i')),
      ];
      expect(await Future.wait(pending), everyElement(isNull));
      expect(await registry.reserve(target(session: 'ninth')), isNull);
      expect(port.requests, hasLength(8));
      final stale = reservation(target(session: 's0'), 'late');
      port.replies.first.complete(stale);
      await flush();
      expect(port.revocations, [stale]);
      final next = registry.reserve(target(session: 'ninth'));
      await flush();
      expect(port.requests, hasLength(9));
      port.replies.last.complete(null);
      expect(await next, isNull);
      for (final reply in port.replies.skip(1).take(7)) {
        reply.complete(null);
      }
      await registry.close();
    },
  );

  test('revoke timeout is bounded and keeps registry quarantined', () async {
    final port = _Port();
    final registry = BrowserClaudeLaunchRegistry(
      port: port,
      timeout: const Duration(milliseconds: 20),
    );
    await _accept(registry, port, target(), 'native');
    port.heldRevoke = Completer<void>();
    await registry.revokeProfile(profileId: 'profile');
    expect(await registry.reserve(target()), isNull);
    await registry.close();
    expect(port.revocations, hasLength(1));
    port.heldRevoke!.complete();
    await flush();
    expect(await registry.reserve(target()), isNull);
  });

  test(
    'native failures are sanitized and reused native IDs quarantine',
    () async {
      final port = _Port();
      final registry = BrowserClaudeLaunchRegistry(port: port);
      final failed = registry.reserve(target());
      await flush();
      port.replies.single.completeError(StateError('private native error'));
      expect(await failed, isNull);
      await _accept(registry, port, target(), 'reused');
      await registry.revokeProfile(profileId: 'profile');
      expect(await _accept(registry, port, target(), 'reused'), isNull);
      expect(await registry.reserve(target(session: 'different')), isNull);
      await registry.close();
    },
  );
}
