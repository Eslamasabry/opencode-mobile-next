// The composer's empty-field hint at the largest system text: the frame caps
// it at 1.3x, so it stays one line and is not cut to "Ask Open…".
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

const _hint = 'Ask OpenCode…';

Future<void> _pump(WidgetTester tester, {required double scale}) async {
  tester.view.physicalSize = const Size(320, 640);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    MaterialApp(
      theme: AppTheme.light(),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      home: MediaQuery(
        data: MediaQueryData(
          size: const Size(320, 640),
          textScaler: TextScaler.linear(scale),
        ),
        child: Scaffold(
          body: Align(
            alignment: Alignment.bottomCenter,
            child: KitComposer(
              controller: TextEditingController(),
              focusNode: FocusNode(),
              hint: _hint,
              onSend: () {},
            ),
          ),
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('the frame caps the hint at 1.3x when the system says 2x', (
    tester,
  ) async {
    await _pump(tester, scale: 2.0);
    final render = tester.renderObject<RenderParagraph>(find.text(_hint));
    expect(render.textScaler.scale(10), moreOrLessEquals(13));
  });

  testWidgets('the hint is the system size below the cap', (tester) async {
    await _pump(tester, scale: 1.0);
    final render = tester.renderObject<RenderParagraph>(find.text(_hint));
    expect(render.textScaler.scale(10), moreOrLessEquals(10));
    expect(render.size.height, lessThan(30));
  });
}
