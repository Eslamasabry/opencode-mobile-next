import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/widgets/appearance_picker.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('appearance picker applies and persists a native theme', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({});
    final preferences = await SharedPreferences.getInstance();
    final controller = ConnectionController(ProfileStore(prefs: preferences));
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      MaterialApp(
        theme: AppTheme.light(),
        home: Builder(
          builder: (context) => Scaffold(
            body: FilledButton(
              onPressed: () =>
                  showAppearancePicker(context, controller: controller),
              child: const Text('Appearance'),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('Appearance'));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('appearance-picker')), findsOneWidget);
    expect(find.byKey(const Key('appearance-system')), findsOneWidget);
    expect(find.byKey(const Key('appearance-light')), findsOneWidget);
    expect(find.byKey(const Key('appearance-dark')), findsOneWidget);

    await tester.tap(find.byKey(const Key('appearance-light')));
    await tester.pumpAndSettle();

    expect(controller.appearance.value, AppAppearance.light);
    expect(preferences.getString('oc.appearance'), 'light');
    expect(find.text('Appearance set to Light'), findsOneWidget);
  });

  testWidgets(
    'appearance choices remain reachable on a compact large-text phone',
    (tester) async {
      tester.view.physicalSize = const Size(320, 480);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      SharedPreferences.setMockInitialValues({});
      final controller = ConnectionController(
        ProfileStore(prefs: await SharedPreferences.getInstance()),
      );
      addTearDown(controller.dispose);

      await tester.pumpWidget(
        MaterialApp(
          theme: AppTheme.light(),
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(
              context,
            ).copyWith(textScaler: const TextScaler.linear(2)),
            child: child!,
          ),
          home: Builder(
            builder: (context) => Scaffold(
              body: FilledButton(
                onPressed: () =>
                    showAppearancePicker(context, controller: controller),
                child: const Text('Appearance'),
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('Appearance'));
      await tester.pumpAndSettle();

      expect(find.byKey(const Key('appearance-picker')), findsOneWidget);
      expect(find.byKey(const Key('appearance-system')), findsOneWidget);
      await tester.scrollUntilVisible(
        find.byKey(const Key('appearance-dark')),
        180,
        scrollable: find.descendant(
          of: find.byKey(const Key('appearance-picker')),
          matching: find.byType(Scrollable),
        ),
      );
      expect(find.byKey(const Key('appearance-dark')), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );
}
