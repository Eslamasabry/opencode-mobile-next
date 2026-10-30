// The chat bugs the owner found on the phone on 2026-10-22, one test each,
// kit level. The phrase chip ("Try again") is in
// test/phrase_chips_chat_test.dart.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

Widget _app(Widget child) => MaterialApp(
  debugShowCheckedModeBanner: false,
  theme: AppTheme.light(),
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  home: Scaffold(
    body: Align(alignment: Alignment.topLeft, child: child),
  ),
);

void main() {
  // Bug 5: after "You stopped this reply." the copy and more actions sat on
  // a line of their own, a wide empty gap.
  for (final meta in [null, 'Sonnet 4']) {
    testWidgets('the stopped note and the footer share one line '
        '(meta: $meta)', (tester) async {
      await tester.pumpWidget(
        _app(
          SizedBox(
            width: 412,
            child: KitTurn(
              phase: KitTurnPhase.stopped,
              latest: true,
              blocks: const [KitText('Half a reply')],
              copyKey: const ValueKey('copy'),
              footer: KitTurnFooter(
                copyText: () => 'Half a reply',
                meta: meta,
                menu: [KitMenuItem(label: 'Fork', onSelected: () {})],
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      final note = tester.getRect(find.text('You stopped this reply.'));
      final copy = tester.getRect(find.byKey(const ValueKey('copy')));
      expect(copy.top, lessThan(note.bottom));
      expect(copy.bottom, greaterThan(note.top));
    });
  }

  // Bug 7: a step line was cut mid-word ("…and retry w…").
  testWidgets('a long step line ends at a whole word', (tester) async {
    const words =
        'Ran the checkout tests on a clean cache and retry when the first '
        'run fails because the price refresh races the coupon check';
    await tester.pumpWidget(
      _app(
        SizedBox(
          width: 260,
          child: KitStepTimeline(
            label: words,
            icon: AppIconography.fileText,
            expanded: false,
            onPressed: () {},
            steps: const [],
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    final shown = tester
        .widgetList<Text>(find.byType(Text))
        .map((t) => t.data ?? t.textSpan?.toPlainText() ?? '')
        .firstWhere((t) => t.startsWith('Ran the'));
    expect(shown, endsWith('…'));
    final kept = shown.substring(0, shown.length - 1);
    expect(words.startsWith(kept), isTrue);
    expect(words[kept.length], ' ', reason: 'cut at "$kept"');
    // Read in full all the same.
    expect(find.bySemanticsLabel(words), findsOneWidget);
  });
}
