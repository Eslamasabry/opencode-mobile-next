// KitFeedItem (one conversation in a feed: title, then one meta line: state, project, time): its behaviour, and the still-motion registration G8
// requires of every kit part.
import 'kit_motion_still.dart';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

extension on String {
  String get isolated => KitBidi.auto(this);
}

Widget _host(Widget child, {double textScale = 1, bool scroll = true}) =>
    MaterialApp(
      theme: AppTheme.dark(),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      builder: (context, app) => MediaQuery(
        data: MediaQuery.of(
          context,
        ).copyWith(textScaler: TextScaler.linear(textScale)),
        child: app!,
      ),
      home: Scaffold(body: scroll ? ListView(children: [child]) : child),
    );

/// The meta line's text, whole (it is one rich paragraph).
Finder _meta(String part) => find.byWidgetPredicate(
  // No-break spaces keep "2 h ago" and "Working ·" whole when the line
  // wraps; they read as spaces.
  (w) =>
      w is RichText &&
      w.text.toPlainText().replaceAll('\u00a0', ' ').contains(part),
);

void main() {
  kitMotionStillTests(
    'KitFeedItem',
    builds: {
      'default': () => KitRowGroup(
        leadingIcons: false,
        children: [
          KitFeedItem(
            project: 'alpha',
            title: 'Fix the login bug',
            state: 'Needs you',
            stateTone: KitFeedStateTone.needsYou,
            time: '2 h ago',
            onTap: () {},
          ),
          KitFeedItem(
            project: 'beta',
            title: 'Add dark mode',
            state: 'Working',
            stateTone: KitFeedStateTone.running,
            time: 'now',
            onTap: () {},
          ),
          KitFeedItem(
            project: 'alpha',
            agent: 'Claude Code',
            title: 'Rename the settings page and move its tests next to it',
            time: '3 d ago',
            onTap: () {},
          ),
          KitFeedItem(
            project: 'gamma',
            title: 'Plan the release notes',
            state: 'Failed',
            time: 'Sep 24',
            onTap: () {},
          ),
        ],
      ),
    },
  );

  testWidgets('says title, state, project and time, and taps once', (
    tester,
  ) async {
    var taps = 0;
    await tester.pumpWidget(
      _host(
        KitFeedItem(
          project: 'alpha',
          title: 'Fix the login bug',
          state: 'Needs you',
          stateTone: KitFeedStateTone.needsYou,
          time: '2 h ago',
          onTap: () => taps++,
        ),
      ),
    );
    expect(find.text('Fix the login bug'.isolated), findsOneWidget);
    expect(_meta('Needs you'), findsOneWidget);
    expect(_meta('alpha'), findsOneWidget);
    expect(_meta('2 h ago'), findsOneWidget);
    await tester.tap(find.text('Fix the login bug'.isolated));
    expect(taps, 1);
  });

  testWidgets('a done state is muted text with the Phosphor check, not '
      "Running's green, in the state slot, no new line", (tester) async {
    await tester.pumpWidget(
      _host(
        KitFeedItem(
          project: 'alpha',
          title: 'Explain the build',
          state: 'Done',
          stateTone: KitFeedStateTone.done,
          time: '3 h ago',
          onTap: () {},
        ),
      ),
    );
    expect(_meta('Done'), findsOneWidget);
    final rich = tester.widget<RichText>(
      find.byWidgetPredicate(
        (w) => w is RichText && w.text.toPlainText().contains('Done'),
      ),
    );
    TextSpan? done;
    rich.text.visitChildren((span) {
      if (span is TextSpan && (span.text ?? '').startsWith('Done')) done = span;
      return true;
    });
    final roles = KitTokens.of(
      tester.element(find.text('Explain the build'.isolated)),
    ).roles;
    // Unslop 2026-10-30: only Running and Needs you carry colour; Done is
    // told from Failed by its check, not by the success green.
    expect(done!.style!.color, roles.text2);
    expect(done!.style!.color, isNot(roles.accent));
    expect(done!.style!.color, isNot(roles.success));
    final check = tester.widget<Icon>(find.byIcon(AppIconography.check));
    expect(check.color, roles.text2);
    expect(find.byIcon(Icons.check_rounded), findsNothing);
  });

  testWidgets('Done carries a check, Working sweeps and stills when reduced', (
    tester,
  ) async {
    addTearDown(() => KitMotion.loops = false);
    KitMotion.loops = true;
    KitFeedItem row(String state, KitFeedStateTone tone) => KitFeedItem(
      project: 'alpha',
      title: 'Row $state',
      state: state,
      stateTone: tone,
      onTap: () {},
    );
    await tester.pumpWidget(
      _host(row('Done', KitFeedStateTone.done), scroll: false),
    );
    expect(find.byIcon(AppIconography.check), findsOneWidget);
    expect(find.byKey(const ValueKey('kit-feed-state-sweep')), findsNothing);
    await tester.pumpWidget(
      _host(row('Working', KitFeedStateTone.running), scroll: false),
    );
    await tester.pump(const Duration(milliseconds: 300));
    expect(find.byIcon(AppIconography.check), findsNothing);
    expect(find.byKey(const ValueKey('kit-feed-state-sweep')), findsOneWidget);
    // Reduced motion: the word is still green, nothing sweeps.
    await tester.pumpWidget(
      MediaQuery(
        data: const MediaQueryData(disableAnimations: true),
        child: _host(row('Working', KitFeedStateTone.running), scroll: false),
      ),
    );
    await tester.pump(const Duration(milliseconds: 300));
    expect(find.byKey(const ValueKey('kit-feed-state-sweep')), findsNothing);
    expect(find.text('Working'), findsOneWidget);
  });

  testWidgets('a row is two lines: no preview, no Git mark, no ring', (
    tester,
  ) async {
    await tester.pumpWidget(
      _host(
        KitFeedItem(
          project: 'beta',
          title: 'Plan',
          time: '3 d ago',
          onTap: () {},
        ),
      ),
    );
    expect(find.byType(Text), findsNWidgets(2)); // the title and the meta line
    expect(find.byType(KitContextRing), findsNothing);
    expect(find.byType(KitStatusTag), findsNothing);
    expect(_meta('beta'), findsOneWidget);
    expect(_meta('Needs you'), findsNothing);
  });

  testWidgets('the agent leads the meta line, never a chip', (tester) async {
    await tester.pumpWidget(
      _host(
        KitFeedItem(
          project: 'shopfront',
          agent: 'Claude Code',
          title: 'Plan',
          time: '2 h ago',
          onTap: () {},
        ),
      ),
    );
    final meta = tester
        .widget<RichText>(_meta('Claude Code'))
        .text
        .toPlainText();
    expect(meta.indexOf('Claude Code'), lessThan(meta.indexOf('shopfront')));
  });

  testWidgets('reads as one sentence to a screen reader', (tester) async {
    final handle = tester.ensureSemantics();
    await tester.pumpWidget(
      _host(
        KitFeedItem(
          project: 'alpha',
          title: 'Fix',
          state: 'Needs you',
          time: '5 min ago',
          onTap: () {},
        ),
      ),
    );
    expect(
      find.bySemanticsLabel('Fix, Needs you, alpha, 5 min ago'),
      findsOneWidget,
    );
    handle.dispose();
  });

  testWidgets('large text takes second lines instead of cutting', (
    tester,
  ) async {
    await tester.pumpWidget(
      _host(
        KitFeedItem(
          project: 'opencode-mobile',
          title: 'Rename the settings page and move its tests next to it',
          state: 'Needs you',
          stateTone: KitFeedStateTone.needsYou,
          time: '2 h ago',
          onTap: () {},
        ),
        textScale: 2,
      ),
    );
    expect(tester.takeException(), isNull);
    expect(_meta('2 h ago'), findsOneWidget);
  });
}
