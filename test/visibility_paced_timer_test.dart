import 'package:flutter/foundation.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/platform/app_visibility.dart';

void main() {
  const visible = Duration(seconds: 10);
  const hidden = Duration(seconds: 60);

  VisibilityPacedTimer timer(
    ValueNotifier<bool> isHidden,
    void Function() on,
  ) => VisibilityPacedTimer(
    visible: visible,
    hidden: hidden,
    onTick: on,
    isHidden: isHidden,
  );

  testWidgets('ticks at the foreground pace while the app is in sight', (
    tester,
  ) async {
    final isHidden = ValueNotifier<bool>(false);
    var ticks = 0;
    final watch = timer(isHidden, () => ticks++)..start();

    await tester.pump(const Duration(seconds: 35));
    expect(ticks, 3);
    watch.cancel();
  });

  testWidgets('ticks every 60 s while out of sight', (tester) async {
    final isHidden = ValueNotifier<bool>(true);
    var ticks = 0;
    final watch = timer(isHidden, () => ticks++)..start();

    await tester.pump(const Duration(seconds: 59));
    expect(ticks, 0);
    await tester.pump(const Duration(seconds: 2));
    expect(ticks, 1);
    watch.cancel();
  });

  testWidgets('the foreground pace is back at once when the app returns', (
    tester,
  ) async {
    final isHidden = ValueNotifier<bool>(true);
    var ticks = 0;
    final watch = timer(isHidden, () => ticks++)..start();

    await tester.pump(const Duration(seconds: 30));
    isHidden.value = false;
    // A 60 s timer left running would not tick for another 30 s.
    await tester.pump(const Duration(seconds: 11));
    expect(ticks, 1);
    await tester.pump(const Duration(seconds: 10));
    expect(ticks, 2);
    watch.cancel();
  });

  testWidgets('going out of sight slows it down at once', (tester) async {
    final isHidden = ValueNotifier<bool>(false);
    var ticks = 0;
    final watch = timer(isHidden, () => ticks++)..start();

    await tester.pump(const Duration(seconds: 12));
    expect(ticks, 1);
    isHidden.value = true;
    await tester.pump(const Duration(seconds: 50));
    expect(ticks, 1);
    await tester.pump(const Duration(seconds: 11));
    expect(ticks, 2);
    watch.cancel();
  });

  testWidgets('cancel stops it and lets go of the visibility listener', (
    tester,
  ) async {
    final isHidden = ValueNotifier<bool>(false);
    var ticks = 0;
    final watch = timer(isHidden, () => ticks++)..start();
    expect(watch.isActive, isTrue);

    await tester.pump(const Duration(seconds: 11));
    expect(ticks, 1);
    watch.cancel();
    expect(watch.isActive, isFalse);

    // A change after cancel must not start it again.
    isHidden.value = true;
    isHidden.value = false;
    await tester.pump(const Duration(seconds: 130));
    expect(ticks, 1);
  });

  testWidgets('start twice keeps one timer', (tester) async {
    final isHidden = ValueNotifier<bool>(false);
    var ticks = 0;
    final watch = timer(isHidden, () => ticks++)
      ..start()
      ..start();

    await tester.pump(const Duration(seconds: 11));
    expect(ticks, 1);
    watch.cancel();
  });
}
