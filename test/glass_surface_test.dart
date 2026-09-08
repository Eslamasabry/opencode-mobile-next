import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/widgets/glass_surface.dart';
import 'package:opencode_mobile/ui/widgets/product_states.dart';

void main() {
  for (final media in <String, MediaQueryData>{
    'high contrast': const MediaQueryData(highContrast: true),
    'accessible navigation': const MediaQueryData(accessibleNavigation: true),
    'reduced motion': const MediaQueryData(disableAnimations: true),
  }.entries) {
    testWidgets(
      '${media.key} uses opaque navigation without decorative depth',
      (tester) async {
        await tester.pumpWidget(
          MaterialApp(
            theme: AppTheme.dark(),
            home: MediaQuery(
              data: media.value,
              child: const GlassSurface(child: Text('Workspace')),
            ),
          ),
        );
        final decorations = tester
            .widgetList<DecoratedBox>(
              find.descendant(
                of: find.byType(GlassSurface),
                matching: find.byType(DecoratedBox),
              ),
            )
            .map((widget) => widget.decoration)
            .whereType<BoxDecoration>();
        expect(decorations.where((box) => box.color != null), isNotEmpty);
        for (final box in decorations) {
          if (box.color != null) expect(box.color!.a, 1);
          expect(box.boxShadow ?? [], isEmpty);
        }
        expect(find.text('Workspace'), findsOneWidget);
      },
    );
  }

  for (final dark in [false, true]) {
    testWidgets('error snackbar has readable ${dark ? 'dark' : 'light'} text', (
      tester,
    ) async {
      final theme = dark ? AppTheme.dark() : AppTheme.light();
      await tester.pumpWidget(
        MaterialApp(
          theme: theme,
          home: Scaffold(
            body: Builder(
              builder: (context) => TextButton(
                onPressed: () => showProductError(context, 'Unable to save'),
                child: const Text('Save'),
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('Save'));
      await tester.pumpAndSettle();
      final snackbar = tester.widget<SnackBar>(find.byType(SnackBar));
      final copy = tester.widget<Text>(find.text('Unable to save'));
      expect(snackbar.backgroundColor, theme.colorScheme.error);
      expect(copy.style?.color, theme.colorScheme.onError);
      final luminances = [
        snackbar.backgroundColor!.computeLuminance(),
        copy.style!.color!.computeLuminance(),
      ]..sort();
      expect(
        (luminances.last + .05) / (luminances.first + .05),
        greaterThan(4.5),
      );
    });
  }
}
