import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/main.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';

Future<Widget> _app({required ConnectionController controller}) async =>
    ProviderScope(
      overrides: [
        bootstrapProvider.overrideWithValue(AppBootstrap(controller.store)),
        connProvider.overrideWithValue(controller),
      ],
      child: const OcApp(),
    );

Future<ConnectionController> _controller() async {
  SharedPreferences.setMockInitialValues({});
  final preferences = await SharedPreferences.getInstance();
  return ConnectionController(ProfileStore(prefs: preferences));
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('system text scale 3.0 renders app text at the 2.0 clamp', (
    tester,
  ) async {
    tester.platformDispatcher.textScaleFactorTestValue = 3.0;
    addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);
    final controller = await _controller();
    addTearDown(controller.dispose);

    await tester.pumpWidget(await _app(controller: controller));
    await tester.pump();

    // Below the MaterialApp builder every screen sees the clamped scaler.
    final context = tester.element(find.byType(Navigator).first);
    expect(MediaQuery.textScalerOf(context).scale(10), 20);

    // And rendered text picks the clamp up: a Text's RichText scales at 2.0.
    final text = find.byType(Text).first;
    final richText = tester.widget<RichText>(
      find.descendant(of: text, matching: find.byType(RichText)).first,
    );
    expect(richText.textScaler.scale(10), 20);
    expect(tester.takeException(), isNull);
  });

  testWidgets('moderate system text scale passes through unclamped', (
    tester,
  ) async {
    tester.platformDispatcher.textScaleFactorTestValue = 1.3;
    addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);
    final controller = await _controller();
    addTearDown(controller.dispose);

    await tester.pumpWidget(await _app(controller: controller));
    await tester.pump();

    final context = tester.element(find.byType(Navigator).first);
    expect(MediaQuery.textScalerOf(context).scale(10), closeTo(13, .001));
  });
}
