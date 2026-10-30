// The kit's spatial rails (impeccable layout pass, 2026-10-28; audit in the
// session's impeccable-audit/REPORT.md, findings 6, 8, 9, 10, 11, 13).
//
// One page gutter (16), one text rail for a list's words (56: the gutter, a
// tile-wide icon slot and space3), one right rail where every trailing
// glyph's ink ends (the gutter), same-role rows at one height, and spacing
// on the 4 dp scale. Ink is measured against Phosphor's own margins (read
// from assets/fonts/phosphor/Phosphor.ttf), so a glyph placed by its box
// instead of its ink fails here.
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart' show RenderParagraph;
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_composer_status_strip.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_phrase_chips.dart';
import 'package:opencode_mobile/ui/kit/kit_chip.dart';
import 'package:opencode_mobile/ui/kit/kit_code_block.dart';
import 'package:opencode_mobile/ui/kit/kit_filter_chips.dart';
import 'package:opencode_mobile/ui/kit/kit_notice.dart';
import 'package:opencode_mobile/ui/kit/kit_row.dart';
import 'package:opencode_mobile/ui/kit/kit_row_parts.dart';
import 'package:opencode_mobile/ui/kit/kit_tokens.dart';
import 'package:opencode_mobile/ui/kit/kit_top_bar.dart';

import '../goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;

const _width = 412.0;

/// Phosphor's ink margins at the end of each glyph's em square (fraction of
/// its size), measured from the bundled font.
const _chevronEnd = .28125;
const _moreEnd = .1875;
const _copyEnd = .125;

Future<void> _pump(
  WidgetTester tester,
  Widget child, {
  double textScale = 1,
  bool scroll = true,
}) async {
  tester.view.physicalSize = const Size(_width, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    MaterialApp(
      debugShowCheckedModeBanner: false,
      theme: AppTheme.dark(),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      builder: (context, inner) => MediaQuery(
        data: MediaQuery.of(
          context,
        ).copyWith(textScaler: TextScaler.linear(textScale)),
        child: inner!,
      ),
      home: Scaffold(
        body: scroll
            ? SingleChildScrollView(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [child],
                ),
              )
            : child,
      ),
    ),
  );
  await tester.pumpAndSettle();
}

/// Where [icon]'s ink ends, from its glyph's box and its end margin.
double _inkRight(WidgetTester tester, IconData icon, double endMargin) {
  final rect = tester.getRect(find.byIcon(icon).first);
  return rect.right - rect.width * endMargin;
}

void main() {
  group('the spacing scale', () {
    test('every spacing, height and slot token is on the 4 dp scale', () {
      final t = KitTokens.fallback(AppTheme.dark());
      final values = {
        'space1': t.space1,
        'space2': t.space2,
        'space3': t.space3,
        'space4': t.space4,
        'space5': t.space5,
        'space6': t.space6,
        'gutter': t.gutter,
        'sectionGap': t.sectionGap,
        'labelGap': t.labelGap,
        'rowHeight': t.rowHeight,
        'rowHeightTwoLine': t.rowHeightTwoLine,
        'buttonHeight': t.buttonHeight,
        'iconTileSize': t.iconTileSize,
        'minTarget': t.minTarget,
      };
      for (final MapEntry(:key, :value) in values.entries) {
        expect(value % 4, 0, reason: '$key is $value');
      }
      // A row holds a 48 dp target without growing; a button is a target.
      expect(t.rowHeight, greaterThanOrEqualTo(t.minTarget));
      expect(t.buttonHeight, greaterThanOrEqualTo(t.minTarget));
      // The section rhythm: 24 above a section, 8 under its label.
      expect(t.sectionGap, 24);
      expect(t.labelGap, 8);
    });
  });

  group('one text rail (56 dp on a page)', () {
    testWidgets("a row's words start on the rail, with a tile or a bare "
        '20 dp glyph', (tester) async {
      await _pump(
        tester,
        Builder(
          builder: (context) => KitRowGroup(
            children: [
              KitRow(
                leading: KitRow.icon(context, AppIconography.settings),
                title: 'Tiled',
                trailing: const KitChevron(),
                onTap: () {},
              ),
              const KitRow(
                leading: Icon(AppIconography.info, size: 20),
                title: 'Bare',
              ),
            ],
          ),
        ),
      );
      expect(tester.getTopLeft(find.text('Tiled')).dx, moreOrLessEquals(56));
      expect(tester.getTopLeft(find.text('Bare')).dx, moreOrLessEquals(56));
    });

    testWidgets("a notice on the gutter starts its words on the rows' rail", (
      tester,
    ) async {
      await _pump(
        tester,
        const Padding(
          padding: EdgeInsets.symmetric(horizontal: 16),
          child: KitNotice(message: 'Claude Code signed out'),
        ),
      );
      expect(
        tester.getTopLeft(find.text('Claude Code signed out')).dx,
        moreOrLessEquals(56),
      );
    });

    testWidgets("the top bar's title starts on the rail beside Back", (
      tester,
    ) async {
      await _pump(
        tester,
        KitTopBar(title: 'Settings', exit: KitTopBarExit.back, onExit: () {}),
        scroll: false,
      );
      expect(tester.getTopLeft(find.text('Settings')).dx, moreOrLessEquals(56));
    });
  });

  group('one right rail (the gutter) for trailing ink', () {
    testWidgets("a row's chevron and a value's chevron end on it", (
      tester,
    ) async {
      await _pump(
        tester,
        KitRowGroup(
          children: [
            KitRow(
              key: const ValueKey('lone'),
              title: 'Agent',
              trailing: const KitChevron(),
              onTap: () {},
            ),
          ],
        ),
      );
      expect(
        _inkRight(tester, AppIconography.chevronRight, _chevronEnd),
        moreOrLessEquals(_width - 16, epsilon: .5),
      );
      await _pump(
        tester,
        KitRowGroup(
          children: [
            KitRow(
              title: 'Model',
              trailing: const KitRowValue('Sonnet'),
              onTap: () {},
            ),
          ],
        ),
      );
      expect(
        _inkRight(tester, AppIconography.chevronRight, _chevronEnd),
        moreOrLessEquals(_width - 16, epsilon: .5),
      );
    });

    testWidgets("a value without a chevron ends on it", (tester) async {
      await _pump(
        tester,
        const KitRowGroup(
          children: [
            KitRow(
              title: 'Start',
              trailing: KitRowValue('22:00', chevron: false),
            ),
          ],
        ),
      );
      expect(
        tester.getTopRight(find.text('22:00')).dx,
        moreOrLessEquals(_width - 16, epsilon: .5),
      );
    });

    testWidgets("the top bar's ⋯ ends on it", (tester) async {
      await _pump(
        tester,
        KitTopBar(
          title: 'Running uptime command',
          exit: KitTopBarExit.back,
          onExit: () {},
          menu: [KitMenuItem(label: 'Rename', onSelected: () {})],
        ),
        scroll: false,
      );
      expect(
        _inkRight(tester, AppIconography.more, _moreEnd),
        moreOrLessEquals(_width - 16, epsilon: 1),
      );
    });

    testWidgets("a code block's Copy ends space4 inside the block, where "
        'its text starts', (tester) async {
      await _pump(
        tester,
        const Padding(
          padding: EdgeInsets.symmetric(horizontal: 16),
          child: KitCodeBlock(text: 'localhost'),
        ),
      );
      expect(
        _inkRight(tester, AppIconography.copy, _copyEnd),
        moreOrLessEquals(_width - 16 - 16, epsilon: .5),
      );
    });
  });

  group('same-role rows keep one height whatever trails them', () {
    testWidgets('one line: 56 with or without a chevron; two lines: 64 '
        'with or without a switch', (tester) async {
      await _pump(
        tester,
        KitRowGroup(
          children: [
            const KitRow(key: ValueKey('plain-1'), title: 'This app'),
            KitRow(
              key: const ValueKey('chevron-1'),
              title: 'Help',
              trailing: const KitChevron(),
              onTap: () {},
            ),
            const KitRow(
              key: ValueKey('plain-2'),
              title: 'Alerts',
              supporting: TextSpan(text: 'One line.'),
            ),
            KitSwitchRow(
              key: const ValueKey('switch-2'),
              title: 'Alerts',
              supporting: 'One line.',
              value: true,
              onChanged: (_) {},
            ),
          ],
        ),
      );
      double height(String key) =>
          tester.getSize(find.byKey(ValueKey(key))).height;
      expect(height('plain-1'), 56);
      expect(height('chevron-1'), 56);
      // Two lines: at least 64, and the switch adds nothing (the test
      // font's taller lines may grow both alike).
      expect(height('plain-2'), greaterThanOrEqualTo(64));
      expect(height('switch-2'), height('plain-2'));
    });
  });

  group('chip lines', () {
    testWidgets('at 1.3x text a status chip running past the line fades '
        'there, never a hard cut', (tester) async {
      await _pump(
        tester,
        KitComposerStatusStrip(
          stripKey: const ValueKey('strip'),
          chips: [
            KitChip.action(
              label: 'Ask first',
              icon: AppIconography.shield,
              onPressed: () {},
            ),
            KitChip.action(
              label: 'Auto Accept',
              icon: AppIconography.settings,
              onPressed: () {},
            ),
          ],
          model: KitChip.action(
            label: 'Kilo Gateway / Gemini 3 Pro',
            onPressed: () {},
          ),
        ),
        textScale: 1.3,
      );
      final position = tester
          .state<ScrollableState>(
            find.descendant(
              of: find.byKey(const ValueKey('strip')),
              matching: find.byType(Scrollable),
            ),
          )
          .position;
      // The chips do not fit at this size: the line scrolls...
      expect(position.maxScrollExtent, greaterThan(0));
      // ...and its edge is masked, so the cut chip melts away.
      expect(
        find.ancestor(
          of: find.byKey(const ValueKey('strip')),
          matching: find.byType(ShaderMask),
        ),
        findsOneWidget,
      );
    });

    testWidgets("the phrase row's targets keep space2 from the line under "
        'it', (tester) async {
      await _pump(
        tester,
        Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            KitPhraseChips(
              phrases: const ['pong'],
              onChoose: (_) {},
              onEdit: (_) {},
              onHide: (_) {},
            ),
            const SizedBox(key: ValueKey('under'), height: 48),
          ],
        ),
      );
      // The row is its chips' 48 dp targets.
      final row = tester.getRect(find.byType(KitFilterChips));
      expect(row.height, greaterThanOrEqualTo(48));
      final under = tester.getTopLeft(find.byKey(const ValueKey('under'))).dy;
      expect(under - row.bottom, greaterThanOrEqualTo(8));
    });
  });

  group('the server pill', () {
    setUpAll(loadKitGalleryFonts);

    testWidgets('Connected and tight: the name stays whole and the green '
        'dot says Connected', (tester) async {
      // The audit's emulator: 438 dp wide at Android's font scale 1.3, which
      // arrives as the float 1.2999999523 ("On this ph… · Connected").
      tester.view.physicalSize = const Size(438, 972) * 3;
      tester.view.devicePixelRatio = 3;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(
        MaterialApp(
          theme: AppTheme.dark(),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(
              context,
            ).copyWith(textScaler: const TextScaler.linear(1.2999999523162842)),
            child: child!,
          ),
          home: Scaffold(
            body: Column(
              children: [
                KitTopBar.shell(
                  controls: KitShellControls(
                    server: 'On this phone',
                    serverStatus: 'Connected',
                    serverTone: AppStatusTone.ok,
                    onServer: () {},
                    onSearch: () {},
                    onSettings: () {},
                  ),
                ),
                const Expanded(child: SizedBox()),
              ],
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      final name = find.textContaining('On this phone');
      expect(
        tester.renderObject<RenderParagraph>(name.first).didExceedMaxLines,
        isFalse,
      );
      expect(find.text('Connected'), findsNothing);
    });
  });
}
