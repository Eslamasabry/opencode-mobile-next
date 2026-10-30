// showKitSheet(stepListenable:) (docs/ux-system/kit-api/KitSheet.md,
// "Stepped sheet"): a second step replaces the first inside the one sheet.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit_buttons.dart';
import 'package:opencode_mobile/ui/kit/kit_sheet.dart';

void main() {
  testWidgets('a step renames the header, adds back and keeps one route', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(412, 915);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final step = ValueNotifier<KitSheetStep?>(
      const KitSheetStep(
        id: 'first',
        title: 'Model',
        height: KitSheetHeight.content,
        showClose: false,
      ),
    );
    addTearDown(step.dispose);
    await tester.pumpWidget(
      MaterialApp(
        theme: AppTheme.dark(),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Builder(
          builder: (context) => Scaffold(
            body: Center(
              child: TextButton(
                onPressed: () => showKitSheet<void>(
                  context,
                  title: 'Choose a model',
                  stepListenable: step,
                  body: (_) => const Text('body'),
                ),
                child: const Text('open'),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('open'));
    await tester.pumpAndSettle();
    expect(find.text('Model'), findsOneWidget);
    // The handle, swipe and back close a short bottom sheet: no X.
    expect(find.byKey(const ValueKey('kit-sheet-close')), findsNothing);

    var backs = 0;
    step.value = KitSheetStep(
      id: 'second',
      back: KitAction(
        key: const Key('back'),
        label: 'Back',
        onPressed: () => backs++,
      ),
      height: KitSheetHeight.full,
    );
    await tester.pumpAndSettle();
    // The sheet's own title, a back chevron, and still one sheet.
    expect(find.text('Choose a model'), findsOneWidget);
    expect(find.text('Model'), findsNothing);
    expect(find.text('body'), findsOneWidget);
    await tester.tap(find.byKey(const Key('back')));
    expect(backs, 1);
  });
}
