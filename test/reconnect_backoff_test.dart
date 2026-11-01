import 'dart:math';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api2/reconnect_backoff.dart';
import 'package:opencode_mobile/api2/sse2.dart';
import 'package:opencode_mobile/api2/transport.dart';

class _FailingTransport extends Api2Transport {
  _FailingTransport() : super(baseUrl: 'http://localhost', password: 'fixture');
  int calls = 0;
  @override
  Future<Response<ResponseBody>> openStream(
    String path, {
    Map<String, dynamic>? query,
    CancelToken? cancelToken,
  }) {
    calls++;
    return Future.error(StateError('unavailable'));
  }
}

void main() {
  group('ReconnectBackoff', () {
    test('foreground pace is unchanged: 1 s doubling to 16 s', () {
      final steps = [
        for (var a = 1; a <= 8; a++)
          ReconnectBackoff.delay(a, hidden: false).inMilliseconds,
      ];
      expect(steps, [1000, 2000, 4000, 8000, 16000, 16000, 16000, 16000]);
    });

    test('out of sight, the first two tries keep the quick pace', () {
      expect(ReconnectBackoff.delay(1, hidden: true).inSeconds, 1);
      expect(ReconnectBackoff.delay(2, hidden: true).inSeconds, 2);
    });

    test('out of sight, later tries wait 60 to 120 s, at random', () {
      final random = Random(7);
      final seen = <int>{
        for (var i = 0; i < 40; i++)
          ReconnectBackoff.delay(3 + i, hidden: true, random: random).inSeconds,
      };
      expect(seen.every((s) => s >= 60 && s <= 120), isTrue);
      expect(seen.length, greaterThan(10), reason: 'jittered, not fixed');
    });
  });

  group('Api2EventStream out of sight', () {
    testWidgets('waits a long, jittered pause after the quick tries', (
      tester,
    ) async {
      final transport = _FailingTransport();
      final stream = Api2EventStream(
        transport: transport,
        onEvent: (_, _) {},
        onStatus: (_) {},
        isHidden: () => true,
        isOffline: () async => false,
        random: Random(1),
      );
      stream.start();
      await tester.pump();
      // Failures 1 and 2 retry after 1 s and 2 s; the third waits 60 s+.
      await tester.pump(const Duration(milliseconds: 1100));
      await tester.pump(const Duration(milliseconds: 2100));
      await tester.pump();
      expect(transport.calls, 3);
      final wait = stream.nextAttempt.value!.difference(DateTime.now());
      expect(wait.inSeconds, inInclusiveRange(55, 120));
      await tester.pump(const Duration(seconds: 50));
      expect(transport.calls, 3, reason: 'no wake-up inside the pause');
      await stream.dispose();
      transport.close();
    });

    testWidgets('no network: the try is skipped and the pause repeats', (
      tester,
    ) async {
      final transport = _FailingTransport();
      var asked = 0;
      final stream = Api2EventStream(
        transport: transport,
        onEvent: (_, _) {},
        onStatus: (_) {},
        isHidden: () => true,
        isOffline: () async {
          asked++;
          return true;
        },
        random: Random(2),
      );
      stream.start();
      await tester.pump();
      await tester.pump(const Duration(seconds: 2));
      expect(transport.calls, 1, reason: 'the retry found no network');
      expect(asked, greaterThanOrEqualTo(1));
      await stream.dispose();
      transport.close();
    });
  });
}
