import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/background/live_background.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/first_reply_notify_offer.dart';
import 'package:opencode_mobile/state/first_run.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Who is asked "Notify you when the agent needs you?" (UX plan 5.6 step 6;
/// the preset of P6.7). The line in the chat and its answers:
/// test/chat_notify_offer_test.dart; per-server recording:
/// test/consent_in_flow_test.dart.

class _Native {
  final calls = <String>[];

  Future<Map<String, dynamic>> call(
    String method, [
    Map<String, dynamic>? arguments,
  ]) async {
    if (method == 'getBackgroundPause') {
      return const {
        'supported': true,
        'active': false,
        'paused': false,
        'reason': 'none',
        'at': null,
        'canResume': false,
      };
    }
    calls.add(method);
    return {'enabled': false, 'active': false};
  }
}

Future<(ConnectionController, _Native)> _controller(
  Map<String, Object> prefs,
) async {
  SharedPreferences.setMockInitialValues(prefs);
  final preferences = await SharedPreferences.getInstance();
  final native = _Native();
  final controller =
      ConnectionController(
          ProfileStore(prefs: preferences),
          backgroundLive: BackgroundLiveController(
            preferences: preferences,
            invoke: native.call,
          ),
        )
        ..api = OpenCodeApi(baseUrl: 'http://localhost')
        ..status = StreamStatus.connected;
  addTearDown(controller.dispose);
  return (controller, native);
}

const _pending = <String, Object>{
  FirstRun.stateKey: 'done',
  FirstRun.notifyAskKey: 'pending',
};

Future<bool> _shows(
  ConnectionController c, {
  bool replyCompleted = true,
}) async {
  final offer = FirstReplyNotifyOffer(c);
  addTearDown(offer.dispose);
  offer.showFor(replyCompleted: replyCompleted);
  await pumpEventQueue();
  return offer.showFor(replyCompleted: replyCompleted);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUp(() => debugPlatformCapabilities = const PlatformCapabilities.android());
  tearDown(() => debugPlatformCapabilities = null);

  test('asked once a reply has completed, never before', () async {
    final (c, native) = await _controller(_pending);
    expect(await _shows(c, replyCompleted: false), isFalse);
    expect(await _shows(c), isTrue);
    expect(FirstReplyNotifyOffer.pendingFor(c), isTrue);
    // Asking asks Android for nothing.
    expect(native.calls, isEmpty);
  });

  for (final MapEntry(key: name, value: prefs) in <String, Map<String, Object>>{
    'never came through first run': {},
    'returning, never asked': {FirstRun.stateKey: 'done'},
    'already answered': {
      FirstRun.stateKey: 'done',
      FirstRun.notifyAskKey: 'answered',
    },
    'first run not finished': {FirstRun.stateKey: 'armed'},
  }.entries) {
    test('not asked of a person who is $name', () async {
      final (c, native) = await _controller(prefs);
      expect(await _shows(c), isFalse);
      expect(native.calls, isEmpty);
    });
  }

  test('not asked when the background connection is already on, and that '
      'answers it', () async {
    final (c, native) = await _controller({
      ..._pending,
      BackgroundLiveController.preferenceKey: true,
    });
    expect(await _shows(c), isFalse);
    await pumpEventQueue();
    expect(native.calls, isEmpty);
    expect(FirstRun(c.store.prefs).notifyAskPending, isFalse);
  });

  test('not asked where the device cannot notify; still owed', () async {
    debugPlatformCapabilities = const PlatformCapabilities.linuxDesktop();
    final (c, _) = await _controller(_pending);
    expect(await _shows(c), isFalse);
    expect(FirstRun(c.store.prefs).notifyAskPending, isTrue);
  });

  test('a refusal at Android\'s prompt is an answer, with a reason', () async {
    final (c, _) = await _controller(_pending);
    final offer = FirstReplyNotifyOffer(c);
    addTearDown(offer.dispose);
    expect(offer.showFor(replyCompleted: true), isTrue);
    await offer.accept();
    expect(offer.failure, FirstReplyNotifyFailure.notEnabled);
    expect(offer.showFor(replyCompleted: true), isFalse);
    expect(FirstRun(c.store.prefs).notifyAskPending, isFalse);
    offer.dismissFailure();
    expect(offer.failure, isNull);
  });
}
