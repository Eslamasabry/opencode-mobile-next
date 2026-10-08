// The reply watch (slice-builtin-speed): times each reply from the server's
// events (prompt → first output → end) and keeps the phone awake while a
// reply runs on the in-app server — renewed while it runs, released when it
// ends, and never for longer than the ceiling.
import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart' show EventEnvelope;
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/reply_watch.dart';
import 'package:opencode_mobile/diagnostics/perf_trace.dart';

class _Source extends ChangeNotifier implements ReplySource {
  final _events = StreamController<EventEnvelope>.broadcast(sync: true);

  @override
  Stream<EventEnvelope> get events => _events.stream;

  @override
  bool onInAppServer = true;

  @override
  Set<String> busySessions = {};

  void emit(String type, Map<String, dynamic> properties) =>
      _events.add(EventEnvelope(type: type, properties: properties));

  void busy(String sessionID, bool on) {
    on ? busySessions.add(sessionID) : busySessions.remove(sessionID);
    notifyListeners();
  }

  Future<void> close() => _events.close();
}

/// The events one OpenCode reply sends, in order.
void _prompt(_Source source, {String session = 's1', int created = 1000}) =>
    source.emit('message.updated', {
      'info': {
        'id': 'u-$session-$created',
        'sessionID': session,
        'role': 'user',
        'time': {'created': created},
      },
    });

void _assistant(_Source source, {String session = 's1'}) =>
    source.emit('message.updated', {
      'info': {
        'id': 'a-$session',
        'sessionID': session,
        'role': 'assistant',
        'providerID': 'opencode',
        'modelID': 'big-pickle',
      },
    });

void _text(_Source source, {String session = 's1', int? start}) =>
    source.emit('message.part.updated', {
      'part': {
        'id': 'p1',
        'sessionID': session,
        'messageID': 'a-$session',
        'type': 'text',
        'text': 'Hi',
        'time': {'start': ?start},
      },
    });

void _status(_Source source, String type, {String session = 's1'}) =>
    source.emit('session.status', {
      'sessionID': session,
      'status': {'type': type},
    });

void main() {
  late int micros;
  late List<(bool, Duration)> holds;

  ReplyWatch watch({bool held = true}) => ReplyWatch(
    setChatLease: (_, on, hold) async {
      holds.add((on, hold));
      return BuiltinWorkLeaseStatus(held: on && held);
    },
    nowMicros: () => micros,
  );

  setUp(() {
    micros = 0;
    holds = [];
    PerfTrace.clear();
  });

  test('times prompt → first output → end, by the app and the server', () {
    final source = _Source();
    final replies = watch()..attach(source);
    addTearDown(replies.dispose);

    _prompt(source, created: 50000);
    _status(source, 'busy');
    micros = 1200000;
    _assistant(source);
    // The user's own text part is not the reply's first output.
    source.emit('message.part.updated', {
      'part': {
        'sessionID': 's1',
        'messageID': 'u-s1-50000',
        'type': 'text',
        'text': 'hello',
      },
    });
    expect(
      PerfTrace.spans.where((s) => s.name == 'reply.first_token'),
      isEmpty,
    );
    micros = 3400000;
    _text(source, start: 53100);
    micros = 9000000;
    _text(source, start: 53100);
    _status(source, 'idle');

    final last = replies.lastInApp!;
    expect(last.firstToken, const Duration(milliseconds: 3400));
    expect(last.serverFirstToken, const Duration(milliseconds: 3100));
    expect(last.total, const Duration(seconds: 9));
    expect(last.model, 'opencode/big-pickle');
    expect(last.failed, isFalse);
    expect(replies.last, same(last));

    final first = PerfTrace.spans.singleWhere(
      (s) => s.name == 'reply.first_token',
    );
    expect(first.durationMs, 3400);
    expect(first.attrs['server'], 'in-app');
    expect(first.attrs['server_ms'], '3100');
    final done = PerfTrace.spans.singleWhere((s) => s.name == 'reply.done');
    expect(done.durationMs, 9000);
    expect(done.attrs['first_ms'], '3400');
  });

  test('a reply that fails before any output says so', () {
    final source = _Source();
    final replies = watch()..attach(source);
    addTearDown(replies.dispose);

    _prompt(source);
    micros = 2000000;
    source.emit('session.error', {'sessionID': 's1'});

    final last = replies.lastInApp!;
    expect(last.firstToken, isNull);
    expect(last.failed, isTrue);
    expect(last.total, const Duration(seconds: 2));
    expect(
      PerfTrace.spans.singleWhere((s) => s.name == 'reply.done').failed,
      isTrue,
    );
  });

  test('a reply on another server is timed but is not the in-app one', () {
    final source = _Source()..onInAppServer = false;
    final replies = watch()..attach(source);
    addTearDown(replies.dispose);

    _prompt(source);
    _assistant(source);
    micros = 500000;
    source.emit('message.part.delta', {
      'sessionID': 's1',
      'messageID': 'a-s1',
      'delta': 'H',
    });
    source.emit('session.idle', {'sessionID': 's1'});

    expect(replies.last!.firstToken, const Duration(milliseconds: 500));
    expect(replies.last!.inApp, isFalse);
    expect(replies.lastInApp, isNull);
  });

  test('a user message seen again after the reply does not time a new one', () {
    final source = _Source();
    final replies = watch()..attach(source);
    addTearDown(replies.dispose);

    _prompt(source);
    _status(source, 'busy');
    _status(source, 'idle');
    final first = replies.last;
    // OpenCode updates the user message (its summary) after the reply.
    _prompt(source);
    _status(source, 'idle');
    expect(replies.last, same(first));
  });

  // Widget tests run in a fake clock, so the renewal timer can be walked.
  testWidgets(
    'keeps the phone awake while an in-app reply runs, then lets go',
    (tester) async {
      final source = _Source();
      final replies = watch()..attach(source);

      source.busy('s1', true);
      await tester.pump();
      expect(holds, [(true, const Duration(minutes: 10))]);
      expect(replies.holdingAwake, isTrue);

      // Renewed while it runs, so no hold runs out under a long reply.
      await tester.pump(const Duration(minutes: 5));
      expect(holds.where((h) => h.$1), hasLength(2));

      // A second session finishing first changes nothing.
      source.busy('s2', true);
      source.busy('s2', false);
      await tester.pump();
      expect(holds.where((h) => !h.$1), isEmpty);

      source.busy('s1', false);
      await tester.pump();
      expect(holds.last.$1, isFalse);
      expect(replies.holdingAwake, isFalse);

      // No more renewals once idle.
      final count = holds.length;
      await tester.pump(const Duration(minutes: 30));
      expect(holds, hasLength(count));

      replies.dispose();
      await source.close();
    },
  );

  testWidgets('never keeps the phone awake past the ceiling in one stretch', (
    tester,
  ) async {
    final source = _Source();
    final replies = watch()..attach(source);

    source.busy('s1', true);
    for (var i = 0; i < 73; i++) {
      await tester.pump(const Duration(minutes: 5));
    }
    expect(holds.last.$1, isFalse);
    expect(replies.awakeCapped, isTrue);
    expect(replies.holdingAwake, isFalse);
    final count = holds.length;
    await tester.pump(const Duration(hours: 2));
    expect(holds, hasLength(count));

    // A new stretch after the replies stop may hold again.
    source.busy('s1', false);
    source.busy('s1', true);
    await tester.pump();
    expect(holds.last.$1, isTrue);
    expect(replies.awakeCapped, isFalse);

    replies.dispose();
    await tester.pump();
    expect(holds.last.$1, isFalse);
    await source.close();
  });

  testWidgets('only replies on the in-app server keep the phone awake', (
    tester,
  ) async {
    final source = _Source()..onInAppServer = false;
    final replies = watch()..attach(source);
    source.busy('s1', true);
    await tester.pump(const Duration(minutes: 10));
    expect(holds, isEmpty);
    replies.dispose();
    await source.close();
  });

  testWidgets('idle before queued acquire skips obsolete on', (tester) async {
    final source = _Source();
    final replies = watch()..attach(source);
    source.busy('s1', true);
    source.busy('s1', false);
    await tester.pump();
    expect(holds.map((call) => call.$1), [false]);
    expect(replies.holdingAwake, isFalse);
    replies.dispose();
    await tester.pump();
    await source.close();
  });

  testWidgets('late acquire after idle drains without reporting held', (
    tester,
  ) async {
    final source = _Source();
    final pending = Completer<BuiltinWorkLeaseStatus>();
    final calls = <(String, bool)>[];
    final active = <String>{};
    final reported = <bool>[];
    final replies = ReplyWatch(
      leaseId: 'chat.delayed',
      setChatLease: (id, on, _) async {
        calls.add((id, on));
        if (on) {
          final result = await pending.future;
          if (result.held) active.add(id);
          return result;
        }
        active.remove(id);
        return const BuiltinWorkLeaseStatus();
      },
    )..attach(source);
    replies.addListener(() => reported.add(replies.holdingAwake));
    source.busy('s1', true);
    await tester.pump();
    source.busy('s1', false);
    pending.complete(const BuiltinWorkLeaseStatus(held: true));
    await tester.pump();
    expect(active, isEmpty);
    expect(calls.first, ('chat.delayed', true));
    expect(
      calls.skip(1).every((call) => call == ('chat.delayed', false)),
      isTrue,
    );
    expect(reported, isNot(contains(true)));
    expect(replies.holdingAwake, isFalse);
    replies.dispose();
    await tester.pump();
    await source.close();
  });

  testWidgets('late acquire after disposal drains without notification', (
    tester,
  ) async {
    final source = _Source();
    final pending = Completer<BuiltinWorkLeaseStatus>();
    final calls = <(String, bool)>[];
    final active = <String>{};
    var notified = 0;
    final replies = ReplyWatch(
      leaseId: 'chat.disposed',
      setChatLease: (id, on, _) async {
        calls.add((id, on));
        if (on) {
          final result = await pending.future;
          if (result.held) active.add(id);
          return result;
        }
        active.remove(id);
        return const BuiltinWorkLeaseStatus();
      },
    )..attach(source);
    replies.addListener(() => notified++);
    source.busy('s1', true);
    await tester.pump();
    replies.dispose();
    pending.complete(const BuiltinWorkLeaseStatus(held: true));
    await tester.pump();
    expect(active, isEmpty);
    expect(calls.last, ('chat.disposed', false));
    expect(notified, 0);
    expect(tester.takeException(), isNull);
    await source.close();
  });

  testWidgets('rapid idle and busy preserves the required off boundary', (
    tester,
  ) async {
    final source = _Source();
    final replies = watch()..attach(source);
    source.busy('s1', true);
    await tester.pump();
    source.busy('s1', false);
    source.busy('s2', true);
    await tester.pump();
    expect(holds.map((call) => call.$1), [true, false, true]);
    expect(replies.holdingAwake, isTrue);
    replies.dispose();
    await tester.pump();
    await source.close();
  });

  testWidgets('native cap stops renewals without reopening a busy lease', (
    tester,
  ) async {
    final source = _Source();
    final calls = <bool>[];
    final replies = ReplyWatch(
      setChatLease: (_, on, _) async {
        calls.add(on);
        return on
            ? const BuiltinWorkLeaseStatus(capped: true)
            : const BuiltinWorkLeaseStatus();
      },
    )..attach(source);
    source.busy('s1', true);
    await tester.pump();
    expect(replies.awakeCapped, isTrue);
    expect(replies.holdingAwake, isFalse);
    source.busy('s2', true);
    await tester.pump(const Duration(hours: 7));
    expect(calls, [true]);
    source.busy('s1', false);
    source.busy('s2', false);
    await tester.pump();
    expect(calls, [true, false]);
    replies.dispose();
    await tester.pump();
    await source.close();
  });

  testWidgets('native cap during renewal reports loss of the chat hold', (
    tester,
  ) async {
    final source = _Source();
    var acquisitions = 0;
    final calls = <bool>[];
    final replies = ReplyWatch(
      setChatLease: (_, on, _) async {
        calls.add(on);
        return BuiltinWorkLeaseStatus(
          held: on && ++acquisitions == 1,
          capped: on && acquisitions > 1,
        );
      },
    )..attach(source);
    source.busy('s1', true);
    await tester.pump();
    expect(replies.holdingAwake, isTrue);
    await tester.pump(const Duration(minutes: 5));
    expect(replies.holdingAwake, isFalse);
    expect(replies.awakeCapped, isTrue);
    await tester.pump(const Duration(hours: 7));
    expect(calls, [true, true]);
    replies.dispose();
    await tester.pump();
    await source.close();
  });

  testWidgets('rejected renewal closes only its lease and stops renewing', (
    tester,
  ) async {
    final source = _Source();
    final leases = _Leases()..rejectOn = 2;
    final replies = ReplyWatch(setChatLease: leases.update)..attach(source);
    source.busy('s1', true);
    await tester.pump();
    await tester.pump(const Duration(minutes: 5));
    expect(replies.holdingAwake, isFalse);
    expect(replies.awakeCapped, isFalse);
    expect(leases.active, _Leases.otherWork);
    final count = leases.calls.length;
    await tester.pump(const Duration(hours: 1));
    expect(leases.calls, hasLength(count));
    source.busy('s1', false);
    source.busy('s2', true);
    await tester.pump();
    expect(replies.holdingAwake, isTrue);
    replies.dispose();
    await tester.pump();
    expect(leases.active, _Leases.otherWork);
    await source.close();
  });

  testWidgets(
    'acquire exception drains partial lease and leaves chain usable',
    (tester) async {
      final source = _Source();
      final leases = _Leases()..throwOn = 1;
      final replies = ReplyWatch(setChatLease: leases.update)..attach(source);
      source.busy('s1', true);
      await tester.pump();
      expect(leases.active, _Leases.otherWork);
      expect(replies.holdingAwake, isFalse);
      source.busy('s1', false);
      source.busy('s2', true);
      await tester.pump();
      expect(replies.holdingAwake, isTrue);
      replies.dispose();
      await tester.pump();
      expect(leases.active, _Leases.otherWork);
      expect(tester.takeException(), isNull);
      await source.close();
    },
  );

  testWidgets('release exception cannot poison later cleanup or acquisition', (
    tester,
  ) async {
    final source = _Source();
    final leases = _Leases()..throwOff = 1;
    final replies = ReplyWatch(setChatLease: leases.update)..attach(source);
    source.busy('s1', true);
    await tester.pump();
    source.busy('s1', false);
    await tester.pump();
    expect(replies.holdingAwake, isFalse);
    source.busy('s2', true);
    await tester.pump();
    expect(replies.holdingAwake, isTrue);
    replies.dispose();
    await tester.pump();
    expect(leases.active, _Leases.otherWork);
    expect(tester.takeException(), isNull);
    await source.close();
  });

  testWidgets('two watches use distinct IDs and cleanup preserves other work', (
    tester,
  ) async {
    final source1 = _Source();
    final source2 = _Source();
    final leases = _Leases();
    final first = ReplyWatch(setChatLease: leases.update)..attach(source1);
    final second = ReplyWatch(setChatLease: leases.update)..attach(source2);
    source1.busy('s1', true);
    source2.busy('s2', true);
    await tester.pump();
    final chatIds = leases.active.difference(_Leases.otherWork);
    expect(chatIds, hasLength(2));
    expect(
      chatIds.every((id) => RegExp(r'^[A-Za-z0-9_.-]{1,80}$').hasMatch(id)),
      isTrue,
    );
    first.dispose();
    await tester.pump();
    expect(leases.active.difference(_Leases.otherWork), hasLength(1));
    expect(second.holdingAwake, isTrue);
    second.dispose();
    await tester.pump();
    expect(leases.active, _Leases.otherWork);
    await source1.close();
    await source2.close();
  });

  testWidgets(
    'local ceiling shortens requested TTL before releasing its lease',
    (tester) async {
      final source = _Source();
      final leases = _Leases();
      final replies = ReplyWatch(
        setChatLease: leases.update,
        ceiling: const Duration(minutes: 7),
      )..attach(source);
      source.busy('s1', true);
      await tester.pump();
      expect(leases.calls.single.$3, const Duration(minutes: 7));
      await tester.pump(const Duration(minutes: 5));
      expect(leases.calls.last.$3, const Duration(minutes: 2));
      await tester.pump(const Duration(minutes: 5));
      expect(replies.awakeCapped, isTrue);
      expect(replies.holdingAwake, isFalse);
      expect(leases.active, _Leases.otherWork);
      replies.dispose();
      await tester.pump();
      await source.close();
    },
  );

  testWidgets(
    'leaving the in-app server releases chat without clearing other work',
    (tester) async {
      final source = _Source();
      final leases = _Leases();
      final replies = ReplyWatch(setChatLease: leases.update)..attach(source);
      source.busy('s1', true);
      await tester.pump();
      source.onInAppServer = false;
      source.notifyListeners();
      await tester.pump();
      expect(replies.holdingAwake, isFalse);
      expect(leases.active, _Leases.otherWork);
      replies.dispose();
      await tester.pump();
      await source.close();
    },
  );
}

class _Leases {
  static const otherWork = {'setup.job', 'sign-in.run', 'terminal.session'};
  final active = <String>{...otherWork};
  final calls = <(String, bool, Duration)>[];
  int rejectOn = -1;
  int throwOn = -1;
  int throwOff = -1;
  int _on = 0;
  int _off = 0;

  Future<BuiltinWorkLeaseStatus> update(
    String id,
    bool on,
    Duration hold,
  ) async {
    calls.add((id, on, hold));
    if (!on) {
      if (++_off == throwOff) throw StateError('synthetic release failure');
      active.remove(id);
      return const BuiltinWorkLeaseStatus();
    }
    _on++;
    active.add(id);
    if (_on == throwOn) throw StateError('synthetic acquire failure');
    return BuiltinWorkLeaseStatus(held: _on != rejectOn);
  }
}
