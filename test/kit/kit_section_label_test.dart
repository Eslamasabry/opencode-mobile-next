// KitSectionLabel (lib/ui/kit/kit_section_label.dart, slice-R4): a
// section's name on the one inset every label shares, with the section gap
// above it except as the first thing in a scroll view, and a caller's older
// spacer collapsing into that gap instead of doubling it.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit_field.dart';
import 'package:opencode_mobile/ui/kit/kit_section_label.dart';
import 'package:opencode_mobile/ui/kit/kit_term.dart';
import 'package:opencode_mobile/ui/kit/kit_text.dart';

import 'kit_motion_still.dart';

const _sectionGap = 22.0;
const _gutter = 16.0;

Widget _app(Widget body) => MaterialApp(
  theme: AppTheme.dark(),
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  home: Scaffold(body: body),
);

double _top(WidgetTester tester, String text) =>
    tester.getTopLeft(find.text(text)).dy;

double _bottom(WidgetTester tester, String text) =>
    tester.getBottomLeft(find.text(text)).dy;

void main() {
  kitMotionStillTests(
    'KitSectionLabel',
    builds: {
      'default': () => const KitSectionLabel('Recent conversations'),
      'explained': () => const KitSectionLabel(
        'MCP servers',
        explanation: 'Tools the agent can call on this server.',
      ),
      'with trailing': () => const KitSectionLabel(
        'Queued',
        trailing: KitText('3 waiting', role: KitTextRole.caption),
      ),
    },
  );

  testWidgets('inline label uses the host gutter once', (tester) async {
    await tester.pumpWidget(
      _app(
        const Padding(
          padding: EdgeInsets.symmetric(horizontal: 16),
          child: KitSectionLabel.inline('Milestones'),
        ),
      ),
    );
    expect(tester.getTopLeft(find.text('Milestones')).dx, 16);
  });

  testWidgets('names its section as a heading, in sentence case', (
    tester,
  ) async {
    final semantics = tester.ensureSemantics();
    await tester.pumpWidget(
      _app(const SingleChildScrollView(child: KitSectionLabel('Providers'))),
    );
    expect(find.text('Providers'), findsOneWidget);
    expect(
      tester.getSemantics(find.text('Providers')),
      matchesSemantics(label: 'Providers', isHeader: true),
    );
    semantics.dispose();
  });

  testWidgets('starts at the gutter on a page and at the padding in a sheet, '
      'on the same line as a KitField label', (tester) async {
    await tester.pumpWidget(
      _app(
        SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const KitSectionLabel('On the page'),
              Padding(
                padding: const EdgeInsets.all(24),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: const [
                    KitSectionLabel('In the sheet', margin: EdgeInsets.zero),
                    KitField(label: 'Supervision'),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
    expect(tester.getTopLeft(find.text('On the page')).dx, _gutter);
    expect(tester.getTopLeft(find.text('In the sheet')).dx, 24);
    expect(
      tester.getTopLeft(find.text('Supervision')).dx,
      tester.getTopLeft(find.text('In the sheet')).dx,
    );
  });

  testWidgets('keeps the section gap from what is above it, none as the '
      'first thing, and a spacer above collapses into the gap', (tester) async {
    await tester.pumpWidget(
      _app(
        SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: const [
              KitSectionLabel('First'),
              Text('one'),
              KitSectionLabel('Second'),
              Text('two'),
              SizedBox(height: 16),
              KitSectionLabel('Third'),
              Text('three'),
              SizedBox(height: 40),
              KitSectionLabel('Fourth'),
              Text('four'),
              KitSectionLabel('Fifth', gapBefore: 0),
            ],
          ),
        ),
      ),
    );
    expect(_top(tester, 'First'), 0);
    expect(_top(tester, 'Second') - _bottom(tester, 'one'), _sectionGap);
    // 16 of spacer + 6 of gap: still one section gap, not 38.
    expect(_top(tester, 'Third') - _bottom(tester, 'two'), _sectionGap);
    // A spacer taller than the gap is kept as it is.
    expect(_top(tester, 'Fourth') - _bottom(tester, 'three'), 40);
    expect(_top(tester, 'Fifth'), _bottom(tester, 'four'));
  });

  testWidgets('in a list, the first item has no gap and a spacer item '
      'collapses into the next one', (tester) async {
    await tester.pumpWidget(
      _app(
        ListView(
          children: const [
            KitSectionLabel('First'),
            Text('one'),
            SizedBox(height: 12),
            KitSectionLabel('Second'),
            Text('two'),
            KitSectionLabel('Third'),
          ],
        ),
      ),
    );
    expect(_top(tester, 'First'), 0);
    expect(_top(tester, 'Second') - _bottom(tester, 'one'), _sectionGap);
    expect(_top(tester, 'Third') - _bottom(tester, 'two'), _sectionGap);
  });

  testWidgets('padding the caller put above the section collapses into the '
      'gap too (Work\'s SliverPadding, a padded column)', (tester) async {
    await tester.pumpWidget(
      _app(
        CustomScrollView(
          slivers: [
            const SliverToBoxAdapter(child: Text('head')),
            SliverPadding(
              padding: const EdgeInsets.only(top: _sectionGap),
              sliver: SliverToBoxAdapter(
                child: Column(
                  children: const [KitSectionLabel('Other projects')],
                ),
              ),
            ),
            const SliverToBoxAdapter(child: Text('mid')),
            const SliverToBoxAdapter(
              child: Padding(
                padding: EdgeInsets.only(top: 16),
                child: KitSectionLabel('Padded'),
              ),
            ),
          ],
        ),
      ),
    );
    expect(
      _top(tester, 'Other projects') - _bottom(tester, 'head'),
      _sectionGap,
    );
    expect(_top(tester, 'Padded') - _bottom(tester, 'mid'), _sectionGap);
  });

  testWidgets('with an explanation the words are a KitTerm on the same line', (
    tester,
  ) async {
    await tester.pumpWidget(
      _app(
        const SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              KitSectionLabel('Plain'),
              KitSectionLabel(
                'Resources',
                explanation: 'Files and data the server shares with agents.',
              ),
            ],
          ),
        ),
      ),
    );
    expect(find.byType(KitTerm), findsOneWidget);
    expect(
      tester.getTopLeft(find.text('Resources')).dx,
      moreOrLessEquals(tester.getTopLeft(find.text('Plain')).dx, epsilon: 1),
    );
  });

  testWidgets('first on a page that does not scroll: no gap either', (
    tester,
  ) async {
    await tester.pumpWidget(
      _app(
        const Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [KitSectionLabel('Top'), KitSectionLabel('Next')],
        ),
      ),
    );
    expect(_top(tester, 'Top'), 0);
    expect(
      _top(tester, 'Next') - _bottom(tester, 'Top'),
      moreOrLessEquals(_sectionGap + 8, epsilon: 0.5),
    );
  });

  testWidgets('an explained label keeps a plain label\'s place: its 48 dp '
      'target reaches into the gaps instead of pushing the section apart', (
    tester,
  ) async {
    await tester.pumpWidget(
      _app(
        const SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              KitSectionLabel('First'),
              KitSectionLabel('Plain'),
              KitSectionLabel(
                'Resources',
                explanation: 'Files and data the server shares with agents.',
              ),
              KitSectionLabel('After'),
            ],
          ),
        ),
      ),
    );
    final plainStep = _top(tester, 'Plain') - _bottom(tester, 'First');
    // From the words above to the explained words: the same as between two
    // plain labels.
    expect(
      _top(tester, 'Resources') - _bottom(tester, 'Plain'),
      moreOrLessEquals(plainStep, epsilon: 0.5),
    );
    // The target is still 48 dp tall.
    expect(tester.getSize(find.byType(KitTerm)).height, greaterThan(47.9));
    // Below the explained words the next section is no further than a
    // plain label's gap plus what the target needs past the label gap.
    final below = _top(tester, 'After') - _bottom(tester, 'Resources');
    expect(below, lessThan(plainStep + 8));
  });

  testWidgets('a trailing part drops under the words at large text instead '
      'of overflowing', (tester) async {
    tester.view.physicalSize = const Size(320, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(
      MaterialApp(
        theme: AppTheme.dark(),
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(
            context,
          ).copyWith(textScaler: const TextScaler.linear(2)),
          child: child!,
        ),
        home: const Scaffold(
          body: SingleChildScrollView(
            child: KitSectionLabel(
              'Conversations on this server',
              trailing: KitText('12 finished today'),
            ),
          ),
        ),
      ),
    );
    expect(tester.takeException(), isNull);
    expect(
      _top(tester, '12 finished today'),
      greaterThan(_top(tester, 'Conversations on this server')),
    );
  });
}
