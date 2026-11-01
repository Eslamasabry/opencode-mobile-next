import 'dart:math';

/// How long an event stream waits before trying again (perf audit
/// 2026-11-01, slice S4).
///
/// In the foreground nothing changes: 0.5 s doubling to 16 s, so a lost
/// connection comes back fast under the person's eyes. Out of sight, the
/// first two tries keep that pace (a blip heals quickly); after that each
/// try waits 60 to 120 seconds, chosen at random so many streams do not
/// wake the radio together. Coming back to the app tries at once.
class ReconnectBackoff {
  const ReconnectBackoff._();

  static const foregroundCap = Duration(seconds: 16);

  /// The first tries out of sight that keep the foreground pace.
  static const hiddenQuickTries = 2;
  static const hiddenMin = Duration(seconds: 60);
  static const hiddenMax = Duration(seconds: 120);

  /// [attempt] counts failures so far (1 after the first failure).
  static Duration delay(int attempt, {required bool hidden, Random? random}) {
    if (hidden && attempt > hiddenQuickTries) {
      final span = hiddenMax.inMilliseconds - hiddenMin.inMilliseconds;
      final jitter = (random ?? Random()).nextInt(span + 1);
      return Duration(milliseconds: hiddenMin.inMilliseconds + jitter);
    }
    final ms = (500 * (1 << attempt.clamp(0, 5))).clamp(
      500,
      foregroundCap.inMilliseconds,
    );
    return Duration(milliseconds: ms);
  }
}
