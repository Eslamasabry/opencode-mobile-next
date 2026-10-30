import 'dart:async';

import 'gen_ui_state_test.dart' show tool;

import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:opencode_mobile/domain/genui/gen_ui.dart';
import 'package:opencode_mobile/domain/genui/gen_ui_history.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/delayed_answers.dart';
import 'package:opencode_mobile/state/gen_ui_state.dart';

class _Gateway
    implements
        ServerGateway,
        GenUiHistoryGateway,
        GenUiRecoverySnapshotGateway,
        GenUiLoadGuardGateway {
  bool mayLoad = true;
  @override
  bool genUiMayLoad(String sessionID) => mayLoad;
  String stamp = 'prompt-1';
  bool idle = true, fail = false, more = false;
  int reads = 0;
  List<MessageWithParts> history = [];
  Completer<void>? pause;
  @override
  ({String? promptStamp, bool idle})? genUiRecoverySnapshot(String sessionID) =>
      (promptStamp: stamp, idle: idle);
  @override
  Future<bool> genUiSessionIdle(String sessionID) async => idle;
  @override
  Future<GenUiHistoryPage> genUiHistoryPage(
    String sessionID, {
    String? cursor,
    int limit = 50,
  }) async {
    reads++;
    await pause?.future;
    if (fail) throw StateError('history unavailable');
    return GenUiHistoryPage(
      items: history,
      hasMore: more,
      olderCursor: more ? 'older' : null,
    );
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const scope = GenUiScope(
    profileID: 'phone',
    sourceId: 'paseo:/work',
    directory: '/work',
  );
  const target = GenUiTarget(scope, 's');
  late SharedPreferences prefs;
  late _Gateway gateway;
  late DelayedAnswers delayed;
  late GenUiStateController state;
  void register({String endpoint = 'endpoint', GenUiScope location = scope}) =>
      state.register(
        location,
        gateway,
        endpoint: endpoint,
        current: () => true,
        ready: true,
      );
  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    prefs = await SharedPreferences.getInstance();
    gateway = _Gateway();
    delayed = DelayedAnswers();
    state = GenUiStateController(prefs, delayedAnswers: delayed);
    register();
  });
  tearDown(() {
    state.dispose();
    delayed.dispose();
  });

  test(
    'idle coverage skips repeated recovery and survives controller restart',
    () async {
      await state.recover([target]);
      expect(gateway.reads, 1);
      expect(prefs.containsKey('oc.genui.covered.phone'), isTrue);
      await state.recover([target]);
      expect(gateway.reads, 1);
      state.dispose();
      state = GenUiStateController(prefs, delayedAnswers: delayed);
      register();
      await state.recover([target]);
      expect(gateway.reads, 1);
      expect(state.recoveryIncomplete, isFalse);
      gateway.stamp = 'prompt-2';
      await state.recover([target]);
      expect(gateway.reads, 2);
    },
  );
  test('background recovery never reads a session that is asleep', () async {
    // Reading history wakes a stored Paseo agent (process, new updatedAt).
    gateway.mayLoad = false;
    await state.recover([target]);
    await state.recover([target]);
    expect(gateway.reads, 0);
    expect(state.recoveryIncomplete, isFalse);
    // Answering a card is the person's act, so it may still read.
    await state.recoverOne(target);
    expect(gateway.reads, 1);
    // Once the session is awake the background pass reads it again.
    gateway.mayLoad = true;
    gateway.stamp = 'prompt-2';
    await state.recover([target]);
    expect(gateway.reads, 2);
  });
  test('running recovery never covers the later idle completion', () async {
    gateway.idle = false;
    await state.recover([target]);
    await state.recover([target]);
    expect(gateway.reads, 2);
    gateway.idle = true;
    await state.recover([target]);
    await state.recover([target]);
    expect(gateway.reads, 3);
  });
  test('a source registered again settles without another refresh', () async {
    // Starting a phone agent's chat connects a new gateway for the same
    // place: the list said "couldn't load" until the person tapped Try again.
    await state.recover([target]);
    expect(state.recoveryIncomplete, isFalse);
    gateway = _Gateway();
    register();
    expect(state.recoveryIncomplete, isTrue);
    await Future<void>.delayed(const Duration(milliseconds: 500));
    expect(state.recoveryIncomplete, isFalse);
  });
  test('a read that failed is tried again without another event', () async {
    // A chat the agent has just started has no history for its first
    // seconds; a quiet one sends nothing that would ask again.
    gateway.fail = true;
    await state.recover([target]);
    expect(state.recoveryIncomplete, isTrue);
    gateway.fail = false;
    await Future<void>.delayed(const Duration(milliseconds: 2500));
    expect(state.recoveryIncomplete, isFalse);
  });
  test('failed and incomplete history stay eligible', () async {
    gateway.fail = true;
    await state.recover([target]);
    expect(state.recoveryIncomplete, isTrue);
    gateway.fail = false;
    gateway.more = true;
    await state.recover([target]);
    expect(state.recoveryIncomplete, isTrue);
    expect(prefs.containsKey('oc.genui.covered.phone'), isFalse);
    gateway.more = false;
    await state.recover([target]);
    expect(state.recoveryIncomplete, isFalse);
    final reads = gateway.reads;
    await state.recover([target]);
    expect(gateway.reads, reads);
  });
  test('prompt changing during history is not recorded as covered', () async {
    final pause = gateway.pause = Completer<void>();
    final read = state.recover([target]);
    await Future<void>.delayed(Duration.zero);
    gateway.stamp = 'prompt-2';
    pause.complete();
    await read;
    gateway.pause = null;
    await state.recover([target]);
    expect(gateway.reads, 2);
  });
  test('endpoint change does not reuse coverage', () async {
    await state.recover([target]);
    register(endpoint: 'other-endpoint');
    await state.recover([target]);
    expect(gateway.reads, 2);
  });
  test('explicit recovery still reads a covered conversation', () async {
    await state.recover([target]);
    await state.recoverOne(target);
    expect(gateway.reads, 2);
  });
  test('OpenCode recovery is unchanged even with snapshot metadata', () async {
    const oc = GenUiScope(
      profileID: 'phone',
      sourceId: 'opencode',
      directory: '/work',
    );
    register(location: oc);
    await state.recover([const GenUiTarget(oc, 's')]);
    await state.recover([const GenUiTarget(oc, 's')]);
    expect(gateway.reads, 2);
  });
  test('a turn starting during history is not covered', () async {
    final pause = gateway.pause = Completer<void>();
    final read = state.recover([target]);
    await Future<void>.delayed(Duration.zero);
    gateway.idle = false;
    pause.complete();
    await read;
    expect(prefs.containsKey('oc.genui.covered.phone'), isFalse);
    gateway.pause = null;
    gateway.idle = true;
    await state.recover([target]);
    expect(gateway.reads, 2);
  });
  test('journaled card overrides an earlier covered prompt', () async {
    await state.recover([target]);
    gateway.history = [tool()];
    await state.recoverOne(target);
    expect(state.waiting(scope, 's'), hasLength(1));
    await state.recover([target]);
    expect(gateway.reads, 3);
  });
  test(
    'waiting cards do not depend on successful coverage persistence',
    () async {
      gateway.history = [tool()];
      await state.recover([target]);
      expect(state.waiting(scope, 's'), hasLength(1));
      expect(prefs.containsKey('oc.genui.covered.phone'), isFalse);
    },
  );
  test(
    'coverage retains only the 200 most recently covered sessions',
    () async {
      for (var i = 0; i < 201; i++) {
        await state.recover([GenUiTarget(scope, 's$i')]);
      }
      expect(gateway.reads, 201);
      await state.recover([const GenUiTarget(scope, 's200')]);
      expect(gateway.reads, 201);
      await state.recover([const GenUiTarget(scope, 's0')]);
      expect(gateway.reads, 202);
    },
  );
  test('corrupt coverage falls back to history recovery', () async {
    await prefs.setString('oc.genui.covered.phone', '{broken');
    await state.recover([target]);
    expect(gateway.reads, 1);
    await state.recover([target]);
    expect(gateway.reads, 1);
  });
  test('closing a profile fences an in-flight coverage write', () async {
    final pause = gateway.pause = Completer<void>();
    final read = state.recover([target]);
    await Future<void>.delayed(Duration.zero);
    await state.closeProfile('phone');
    await prefs.remove('oc.genui.covered.phone');
    pause.complete();
    await read;
    expect(prefs.containsKey('oc.genui.covered.phone'), isFalse);
  });
}
