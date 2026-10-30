// Brutal and Paper set labels and captions in mono (hook H1, monoLabels).
// Geist Mono's space and no-break space are a full 0.6 em cell against
// Geist's 0.25 em, so "19 s" and "29 seconds" read with a wide gap on the
// phone. The words stay mono; the spaces between them take the sans width.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/kit/style/kit_style_theme.dart';

Future<void> _pump(WidgetTester tester, KitSurfaceStyle style, Widget child) =>
    tester.pumpWidget(
      MaterialApp(
        theme: AppTheme.light(),
        home: KitEffectsScope(
          effects: KitEffects(surfaceStyle: style),
          child: kitStyleTheme(
            child: Scaffold(body: Center(child: child)),
          ),
        ),
      ),
    );

List<InlineSpan> _spans(WidgetTester tester, String text) {
  final rich = tester.widget<RichText>(
    find.byWidgetPredicate(
      (w) => w is RichText && w.text.toPlainText() == text,
    ),
  );
  final all = <InlineSpan>[];
  rich.text.visitChildren((span) {
    all.add(span);
    return true;
  });
  return all;
}

void main() {
  for (final style in [KitSurfaceStyle.brutal, KitSurfaceStyle.paper]) {
    testWidgets('${style.name}: a mono caption keeps narrow spaces', (
      tester,
    ) async {
      const text = '19 s ago';
      await _pump(
        tester,
        style,
        const KitText(text, role: KitTextRole.caption),
      );
      final spans = _spans(tester, text).whereType<TextSpan>().toList();
      final spaces = spans.where(
        (s) => s.text != null && RegExp(r'^[  ]+$').hasMatch(s.text!),
      );
      expect(spaces, isNotEmpty);
      for (final s in spaces) {
        expect(s.style?.fontFamily, 'AppSans');
      }
      // The words themselves stay in the style's mono face.
      final root = tester
          .widget<RichText>(
            find.byWidgetPredicate(
              (w) => w is RichText && w.text.toPlainText() == text,
            ),
          )
          .text;
      expect(root.style?.fontFamily, 'AppMono');
    });
  }

  testWidgets('solid: a caption is one plain run, as before', (tester) async {
    const text = '19 s ago';
    await _pump(
      tester,
      KitSurfaceStyle.solid,
      const KitText(text, role: KitTextRole.caption),
    );
    final spans = _spans(tester, text).whereType<TextSpan>();
    expect(spans.where((s) => s.text != null), hasLength(1));
  });
}
