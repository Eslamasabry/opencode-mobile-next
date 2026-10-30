// slice-R4: row groups on one inset. KitRowGroup's label starts where a
// KitField label and a KitDetailsFold title start (the page gutter, or a
// sheet's own padding with margin: EdgeInsets.zero); a labelled group keeps
// the section gap from what is above it except as the first thing in a
// scroll view; labelTerm explains the label; KitExpandRow's title may take
// two lines.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit_arrival.dart';
import 'package:opencode_mobile/ui/kit/kit_field.dart';
import 'package:opencode_mobile/ui/kit/kit_row.dart';
import 'package:opencode_mobile/ui/kit/kit_row_parts.dart';
import 'package:opencode_mobile/ui/kit/kit_technical_value.dart';
import 'package:opencode_mobile/ui/kit/kit_term.dart';
import 'package:opencode_mobile/ui/kit/kit_tokens.dart';

KitRow _row(String title) => KitRow(
  leading: const KitRowIcon(AppIconography.agent),
  title: title,
  onTap: () {},
);

Widget _app(Widget body) => MaterialApp(
  theme: AppTheme.dark(),
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  home: Scaffold(body: body),
);

double _x(WidgetTester tester, String text) =>
    tester.getTopLeft(find.text(text)).dx;

void main() {
  testWidgets("in a sheet, 'Planner' and 'Supervision' share one x with the "
      'Details title', (tester) async {
    await tester.pumpWidget(
      _app(
        // A sheet's body: narrower than the reading cap, as a sheet is.
        SizedBox(
          width: 600,
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const KitField(label: 'Supervision'),
                const SizedBox(height: 16),
                KitRowGroup(
                  label: 'Planner',
                  margin: EdgeInsets.zero,
                  children: [_row('Mayor')],
                ),
                const SizedBox(height: 16),
                const KitDetailsFold(
                  label: 'Details',
                  values: [KitTechnicalValue('Agent', 'mayor-1')],
                ),
              ],
            ),
          ),
        ),
      ),
    );
    expect(_x(tester, 'Supervision'), 24);
    expect(_x(tester, 'Planner'), _x(tester, 'Supervision'));
    expect(_x(tester, 'Details'), _x(tester, 'Supervision'));
  });

  testWidgets('on a page the label starts at the gutter', (tester) async {
    await tester.pumpWidget(
      _app(
        SingleChildScrollView(
          child: KitRowGroup(label: 'Server', children: [_row('Address')]),
        ),
      ),
    );
    final tokens = KitTokens.of(tester.element(find.text('Server')));
    expect(_x(tester, 'Server'), tokens.gutter);
  });

  testWidgets('three labelled groups: no gap above the first, the section '
      'gap between the others', (tester) async {
    await tester.pumpWidget(
      _app(
        SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              for (final name in ['Model', 'Agents', 'Server'])
                KitRowGroup(
                  key: ValueKey(name),
                  label: name,
                  children: [_row('$name row one'), _row('$name row two')],
                ),
            ],
          ),
        ),
      ),
    );
    final tokens = KitTokens.of(tester.element(find.text('Model')));
    double top(String label) => tester.getTopLeft(find.text(label)).dy;
    double bottom(String group) =>
        tester.getBottomLeft(find.byKey(ValueKey(group))).dy;
    expect(top('Model'), 0);
    expect(top('Agents') - bottom('Model'), tokens.sectionGap);
    expect(top('Server') - bottom('Agents'), tokens.sectionGap);
    // The label keeps its own 8 above the panel.
    expect(
      tester.getTopLeft(find.text('Agents row one')).dy,
      greaterThan(tester.getBottomLeft(find.text('Agents')).dy),
    );
  });

  testWidgets('a spacer the caller left above a group is not doubled; '
      'gapBefore and unlabelled groups', (tester) async {
    await tester.pumpWidget(
      _app(
        SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              KitRowGroup(key: const ValueKey('a'), children: [_row('One')]),
              const SizedBox(height: 16),
              KitRowGroup(
                key: const ValueKey('b'),
                label: 'Spaced',
                children: [_row('Two')],
              ),
              KitRowGroup(key: const ValueKey('c'), children: [_row('Three')]),
              KitRowGroup(
                key: const ValueKey('d'),
                label: 'Tight',
                gapBefore: 4,
                children: [_row('Four')],
              ),
            ],
          ),
        ),
      ),
    );
    final tokens = KitTokens.of(tester.element(find.text('Spaced')));
    double top(Key key) => tester.getTopLeft(find.byKey(key)).dy;
    double bottom(Key key) => tester.getBottomLeft(find.byKey(key)).dy;
    expect(top(const ValueKey('a')), 0);
    expect(
      tester.getTopLeft(find.text('Spaced')).dy - bottom(const ValueKey('a')),
      tokens.sectionGap,
    );
    // Unlabelled: no gap of its own.
    expect(top(const ValueKey('c')), bottom(const ValueKey('b')));
    expect(
      tester.getTopLeft(find.text('Tight')).dy - bottom(const ValueKey('c')),
      4,
    );
  });

  testWidgets('labelTerm explains the label with a KitTerm', (tester) async {
    await tester.pumpWidget(
      _app(
        SingleChildScrollView(
          child: KitRowGroup(
            label: 'MCP servers',
            labelTerm: 'Tools the agent can call, run by this server.',
            children: [_row('github')],
          ),
        ),
      ),
    );
    expect(find.byType(KitTerm), findsOneWidget);
    expect(find.text('MCP servers'), findsOneWidget);
    await tester.tap(find.text('MCP servers'));
    await tester.pumpAndSettle();
    expect(
      find.text('Tools the agent can call, run by this server.'),
      findsOneWidget,
    );
  });

  testWidgets('KitExpandRow titleMaxLines lets an error title take two '
      'lines; one by default', (tester) async {
    const long =
        "Couldn't reach the server at the office after three tries this "
        'morning';
    await tester.pumpWidget(
      _app(
        const SingleChildScrollView(
          child: Column(
            children: [
              KitExpandRow(
                key: ValueKey('one'),
                title: long,
                children: [Text('inside one')],
              ),
              KitExpandRow(
                key: ValueKey('two'),
                title: long,
                titleMaxLines: 2,
                children: [Text('inside two')],
              ),
            ],
          ),
        ),
      ),
    );
    final texts = tester.widgetList<Text>(find.text(long)).toList();
    expect(texts.map((t) => t.maxLines), [1, 2]);
  });

  // Flat polish (sweep 3730, 41-agents / 24-sync): rows with no icon drew
  // their separators from the icon column (x 110 of 838) while their words
  // start at the gutter (x 31).
  group('separators start where the words do', () {
    final line = find.byKey(const ValueKey('kit-divider-line'));

    Future<void> pump(WidgetTester tester, List<Widget> rows) =>
        tester.pumpWidget(
          _app(SingleChildScrollView(child: KitRowGroup(children: rows))),
        );

    testWidgets('rows without icons: at the words, not an icon column', (
      tester,
    ) async {
      await pump(tester, [
        KitRow(title: 'AI Team', onTap: () {}),
        // A row wrapped as a search result's arrival still counts.
        KitArrival(
          id: 'phone',
          child: KitRow(title: 'Agents on this phone', onTap: () {}),
        ),
        KitSwitchRow(title: 'Watch', value: false, onChanged: (_) {}),
      ]);
      expect(tester.getTopLeft(line.first).dx, _x(tester, 'AI Team'));
      expect(tester.getTopLeft(line.last).dx, _x(tester, 'AI Team'));
    });

    testWidgets('rows with icons: past the icons, at the words', (
      tester,
    ) async {
      await pump(tester, [_row('Mayor'), _row('Deacon')]);
      expect(tester.getTopLeft(line).dx, _x(tester, 'Mayor'));
    });

    testWidgets('rowsLead: any leading row leads; other widgets do not '
        'count', (tester) async {
      expect(KitRowGroup.rowsLead([const SizedBox()]), isTrue);
      expect(
        KitRowGroup.rowsLead([const SizedBox(), KitRow(title: 'A')]),
        isFalse,
      );
      expect(KitRowGroup.rowsLead([KitRow(title: 'A'), _row('B')]), isTrue);
    });
  });
}
