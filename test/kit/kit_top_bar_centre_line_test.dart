// The owner, 2026-10-27, on the chat's header: "Why aren't these aligned to
// the middle of each other?" The back arrow, the title's first line and the
// ⋯ share one vertical centre, whatever sits under the title (a subtitle, the
// project line), at 320 dp, at text size 2.0 and right to left.
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart' show RenderParagraph;
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit_buttons.dart';
import 'package:opencode_mobile/ui/kit/kit_context_ring.dart';
import 'package:opencode_mobile/ui/kit/kit_menu.dart';
import 'package:opencode_mobile/ui/kit/kit_top_bar.dart';

const _title = 'Continue the work from the previous conversation';

Future<void> _pump(
  WidgetTester tester,
  Widget bar, {
  double width = 412,
  double textScale = 1,
  TextDirection direction = TextDirection.ltr,
}) async {
  tester.view.physicalSize = Size(width, 915) * 3;
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
        ).copyWith(textScaler: TextScaler.linear(textScale)),
        child: Directionality(textDirection: direction, child: child!),
      ),
      home: Scaffold(
        body: Column(
          children: [
            bar,
            const Expanded(child: SizedBox()),
          ],
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

/// The vertical centre of the title's first line, on screen.
double _titleLineCentre(WidgetTester tester, String title) {
  final finder = find.text(title);
  final paragraph = tester.renderObject<RenderParagraph>(finder);
  final box = paragraph
      .getBoxesForSelection(const TextSelection(baseOffset: 0, extentOffset: 1))
      .first
      .toRect();
  final origin = tester.getTopLeft(finder);
  return origin.dy + box.center.dy;
}

double _glyphCentre(WidgetTester tester, Key key) => tester
    .getCenter(
      find.descendant(of: find.byKey(key), matching: find.byType(Icon)).first,
    )
    .dy;

void _expectOneLine(WidgetTester tester, String title, String what) {
  final line = _titleLineCentre(tester, title);
  final back = _glyphCentre(tester, const Key('back'));
  final more = _glyphCentre(tester, const Key('more'));
  final report = '$what: back $back, title line $line, more $more';
  expect((back - line).abs(), lessThanOrEqualTo(1), reason: report);
  expect((more - line).abs(), lessThanOrEqualTo(1), reason: report);
}

KitTopBar _chatBar({String title = _title, bool ringShown = false}) =>
    KitTopBar(
      titleKey: const Key('t'),
      title: title,
      exit: KitTopBarExit.back,
      exitKey: const Key('back'),
      onExit: () {},
      scope: KitTopBarScope(
        label: 'IPTV_King',
        suffix: 'Claude Code',
        semanticsLabel: 'Project IPTV_King',
        onTap: () {},
        branch: KitTopBarBranch(
          label: 'Git',
          semanticsLabel: 'Git',
          onTap: () {},
        ),
      ),
      actions: [
        KitAction(
          visible: false,
          key: const Key('collapse'),
          icon: AppIconography.unfoldLess,
          label: 'Collapse all steps',
          onPressed: () {},
        ),
      ],
      accessory: KitContextRing(used: .3, openDetails: () {}),
      accessoryShown: ringShown,
      menu: [KitMenuItem(label: 'Rename', onSelected: () {})],
      menuKey: const Key('more'),
    );

void main() {
  originTests();

  testWidgets('the chat header: arrow, title line and ⋯ share one centre', (
    tester,
  ) async {
    await _pump(tester, _chatBar());
    _expectOneLine(tester, _title, 'chat, project line');
    await _pump(tester, _chatBar(ringShown: true));
    _expectOneLine(tester, _title, 'chat, context ring shown');
  });

  testWidgets('a subtitle under the title keeps the title on the line', (
    tester,
  ) async {
    await _pump(
      tester,
      KitTopBar(
        title: 'plan',
        subtitle: 'Computer at 10.0.2.2',
        exit: KitTopBarExit.back,
        exitKey: const Key('back'),
        onExit: () {},
        menu: [KitMenuItem(label: 'Rename', onSelected: () {})],
        menuKey: const Key('more'),
      ),
    );
    _expectOneLine(tester, 'plan', 'subtitle');
  });

  testWidgets('a title alone and a switcher title stay on the line', (
    tester,
  ) async {
    await _pump(
      tester,
      KitTopBar(
        title: 'Settings',
        exit: KitTopBarExit.back,
        exitKey: const Key('back'),
        onExit: () {},
        menu: [KitMenuItem(label: 'Help', onSelected: () {})],
        menuKey: const Key('more'),
      ),
    );
    _expectOneLine(tester, 'Settings', 'title alone');
    await _pump(
      tester,
      KitTopBar(
        title: 'IPTV_King',
        subtitle: 'Working · 2 min',
        onTitleTap: () {},
        titleTapLabel: 'Switch project',
        exit: KitTopBarExit.back,
        exitKey: const Key('back'),
        onExit: () {},
        menu: [KitMenuItem(label: 'Help', onSelected: () {})],
        menuKey: const Key('more'),
      ),
    );
    _expectOneLine(tester, 'IPTV_King', 'switcher with subtitle');
  });

  testWidgets('320 dp, text size 2.0 and right to left keep the line', (
    tester,
  ) async {
    await _pump(tester, _chatBar(), width: 320);
    expect(tester.takeException(), isNull);
    _expectOneLine(tester, _title, '320 dp');
    await _pump(tester, _chatBar(title: 'plan'), textScale: 2);
    expect(tester.takeException(), isNull);
    _expectOneLine(tester, 'plan', 'text size 2.0');
    await _pump(tester, _chatBar(), direction: TextDirection.rtl);
    expect(tester.takeException(), isNull);
    _expectOneLine(tester, _title, 'right to left');
  });
}

KitTopBar _originBar({required VoidCallback onOrigin, bool gone = false}) =>
    KitTopBar(
      title: _title,
      exit: KitTopBarExit.back,
      exitKey: const Key('back'),
      onExit: () {},
      scope: KitTopBarScope(
        label: 'IPTV_King',
        suffix: 'Claude Code',
        semanticsLabel: 'Project IPTV_King',
        onTap: () {},
        branch: KitTopBarBranch(
          label: 'Git',
          semanticsLabel: 'Git',
          onTap: () {},
        ),
        origin: KitTopBarOrigin(
          originKey: const Key('origin'),
          label: 'from Design review of the settings page',
          semanticsLabel: 'Continued from Design review of the settings page',
          gone: gone,
          onTap: onOrigin,
        ),
      ),
      menu: [KitMenuItem(label: 'Rename', onSelected: () {})],
      menuKey: const Key('more'),
    );

void originTests() {
  testWidgets('the origin rides at the end of the project line, cut to fit, '
      'and opens the other conversation', (tester) async {
    var opened = 0;
    await _pump(tester, _originBar(onOrigin: () => opened++), width: 320);
    expect(tester.takeException(), isNull);
    final project = tester.getRect(find.text('IPTV_King').first);
    final origin = tester.getRect(find.byKey(const Key('origin')));
    // One line: no row of its own.
    expect((origin.center.dy - project.center.dy).abs(), lessThan(1));
    expect(origin.right, lessThanOrEqualTo(320));
    // Short of room, the server's name is left out whole, never cut in the
    // middle of a word (owner, 2026-10-27).
    expect(find.textContaining('Claude Code'), findsNothing);
    await tester.tap(find.byKey(const Key('origin')));
    expect(opened, 1);
    expect(
      find.bySemanticsLabel(
        'Continued from Design review of the settings page',
      ),
      findsOneWidget,
    );
  });

  testWidgets('with room, the server is said whole after the project', (
    tester,
  ) async {
    await _pump(tester, _chatBar(), width: 600);
    final server = find.textContaining('Claude Code');
    expect(server, findsOneWidget);
    expect(
      tester.renderObject<RenderParagraph>(server).didExceedMaxLines,
      isFalse,
    );
  });

  testWidgets('a gone origin is words only', (tester) async {
    var opened = 0;
    await _pump(tester, _originBar(onOrigin: () => opened++, gone: true));
    await tester.tap(find.byKey(const Key('origin')), warnIfMissed: false);
    expect(opened, 0);
  });

  testWidgets('at text size 2.0 the origin wraps with the line, no overflow', (
    tester,
  ) async {
    await _pump(tester, _originBar(onOrigin: () {}), textScale: 2);
    expect(tester.takeException(), isNull);
    expect(find.byKey(const Key('origin')), findsOneWidget);
  });
}
