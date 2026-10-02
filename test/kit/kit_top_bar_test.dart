// Behaviour tests for KitTopBar, docs/ux-system/kit-api/KitTopBar.md
// ("Tests required"). Owner decision 2026-09-27: Arabic dropped, so the RTL
// check is a plain direction check, not an Arabic review.
import 'kit_motion_still.dart';

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart' show RenderParagraph;
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/glass/kit_glass.dart';
import 'package:opencode_mobile/ui/kit/kit_buttons.dart';
import 'package:opencode_mobile/ui/kit/kit_effects.dart';
import 'package:opencode_mobile/ui/kit/kit_icon_button.dart';
import 'package:opencode_mobile/ui/kit/kit_menu.dart';
import 'package:opencode_mobile/ui/kit/kit_top_bar.dart';

Future<void> _pump(
  WidgetTester tester,
  Widget bar, {
  Size size = const Size(412, 915),
  double textScale = 1,
  bool reduced = false,
  TextDirection direction = TextDirection.ltr,
  KitEffects effects = KitEffects.defaults,
}) async {
  tester.view.physicalSize = size * 3;
  tester.view.devicePixelRatio = 3;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    MaterialApp(
      theme: AppTheme.light(),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      builder: (context, child) => MediaQuery(
        data: MediaQuery.of(context).copyWith(
          textScaler: TextScaler.linear(textScale),
          disableAnimations: reduced,
        ),
        child: Directionality(
          textDirection: direction,
          child: KitEffectsScope(effects: effects, child: child!),
        ),
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

List<KitAction> _threeActions(List<String> log) => [
  for (final (i, name) in ['Stop task', 'Share', 'Rename'].indexed)
    KitAction(
      label: name,
      icon: [AppIcons.stop, AppIcons.copy, AppIcons.run][i],
      onPressed: () => log.add(name),
    ),
];

KitTopBar _busy(List<String> log) => KitTopBar(
  title: 'Fix login',
  actions: _threeActions(log),
  menu: [KitMenuItem(label: 'Archive', onSelected: () => log.add('Archive'))],
  menuKey: const ValueKey('more'),
);

void main() {
  kitMotionStillTests(
    'KitTopBar',
    builds: {
      'title and status': () =>
          const KitTopBar(title: 'Project', subtitle: 'Connected'),
      'actions': () => _busy([]),
    },
    changes: {
      'status changes': KitMotionChange(
        build: () => const KitTopBar(title: 'Project', subtitle: 'Connecting'),
        act: (tester, stage) => stage.rebuild(
          const KitTopBar(title: 'Project', subtitle: 'Connected'),
        ),
        shows: 'Connected',
      ),
    },
  );
  Widget motionControls(bool connected) => KitShellControls(
    server: 'Office computer',
    serverStatus: connected ? 'Connected' : 'Reconnecting',
    serverTone: connected ? AppStatusTone.ok : AppStatusTone.progress,
    onServer: () {},
    needsYou: 2,
  );
  kitMotionStillTests(
    'KitShellControls',
    builds: {'reconnecting': () => motionControls(false)},
    changes: {
      'connects': KitMotionChange(
        build: () => motionControls(false),
        act: (tester, stage) => stage.rebuild(motionControls(true)),
        shows: ' · Connected',
      ),
    },
  );

  testWidgets('title is a header naming the route; subtitle in its label', (
    tester,
  ) async {
    final semantics = tester.ensureSemantics();
    await _pump(
      tester,
      const KitTopBar(
        title: 'Fix login',
        subtitle: 'Working · 2 min',
        titleKey: ValueKey('title'),
      ),
    );
    final data = tester
        .getSemantics(find.byKey(const ValueKey('title')))
        .getSemanticsData();
    expect(data.label, 'Fix login, Working · 2 min');
    expect(data.flagsCollection.isHeader, isTrue);
    expect(data.flagsCollection.namesRoute, isTrue);
    semantics.dispose();
  });

  testWidgets('exit auto: none at root, Back when pushed, Close in a dialog', (
    tester,
  ) async {
    await _pump(tester, const KitTopBar(title: 'Home'));
    expect(find.byTooltip('Back'), findsNothing);
    expect(find.byTooltip('Close'), findsNothing);

    final nav = tester.state<NavigatorState>(find.byType(Navigator));
    nav.push(
      MaterialPageRoute<void>(
        builder: (_) => const Scaffold(body: KitTopBar(title: 'Detail')),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.byTooltip('Back'), findsOneWidget);
    await tester.tap(find.byTooltip('Back'));
    await tester.pumpAndSettle();
    expect(find.text('Detail'), findsNothing);

    nav.push(
      MaterialPageRoute<void>(
        fullscreenDialog: true,
        builder: (_) => const Scaffold(body: KitTopBar(title: 'Edit')),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.byTooltip('Close'), findsOneWidget);
    expect(find.byTooltip('Back'), findsNothing);
    // Close sits at the end.
    expect(
      tester.getCenter(find.byTooltip('Close')).dx,
      greaterThan(tester.getCenter(find.text('Edit')).dx),
    );
  });

  testWidgets('onExit overrides the pop', (tester) async {
    var exits = 0;
    await _pump(
      tester,
      KitTopBar(
        title: 'Files',
        exit: KitTopBarExit.back,
        onExit: () => exits++,
      ),
    );
    await tester.tap(find.byTooltip('Back'));
    await tester.pumpAndSettle();
    expect(exits, 1);
  });

  testWidgets('compact: first action plus overflow; overflow order', (
    tester,
  ) async {
    final log = <String>[];
    await _pump(tester, _busy(log));
    expect(find.byType(KitIconButton), findsNWidgets(2));
    expect(find.byTooltip('Stop task'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('more')));
    await tester.pumpAndSettle();
    final panel = tester.widget<KitMenuPanel>(find.byType(KitMenuPanel));
    expect(panel.items.map((i) => i.label), ['Share', 'Rename', 'Archive']);
    await tester.tap(find.text('Rename'));
    await tester.pumpAndSettle();
    expect(log, ['Rename']);
  });

  testWidgets('compact: one action and no menu is one icon', (tester) async {
    await _pump(
      tester,
      KitTopBar(
        title: 'Files',
        actions: [
          KitAction(label: 'Search', icon: AppIcons.copy, onPressed: () {}),
        ],
      ),
    );
    expect(find.byType(KitIconButton), findsOneWidget);
  });

  testWidgets('medium: two actions plus overflow', (tester) async {
    await _pump(tester, _busy([]), size: const Size(700, 900));
    expect(find.byType(KitIconButton), findsNWidgets(3));
    expect(find.byTooltip('Share'), findsOneWidget);
    expect(find.byTooltip('Rename'), findsNothing);
  });

  testWidgets('expanded: labelled buttons; title keeps half the bar', (
    tester,
  ) async {
    await _pump(tester, _busy([]), size: const Size(1280, 800));
    expect(find.byType(KitButton), findsNWidgets(3));
    expect(find.byKey(const ValueKey('more')), findsOneWidget);
    final many = [
      for (var i = 0; i < 12; i++)
        KitAction(
          label: 'Long action name $i',
          icon: AppIcons.run,
          onPressed: () {},
        ),
    ];
    await _pump(
      tester,
      KitTopBar(title: 'Fix login', actions: many),
      size: const Size(1280, 800),
    );
    final shown = find.byType(KitButton).evaluate().length;
    expect(shown, lessThan(12));
    expect(shown, greaterThan(0));
    final buttonsStart = tester.getTopLeft(find.byType(KitButton).first).dx;
    expect(buttonsStart, greaterThanOrEqualTo(1280 / 2 - 24));
  });

  testWidgets('a disabled action waits in the overflow with its reason', (
    tester,
  ) async {
    await _pump(
      tester,
      const KitTopBar(
        title: 'Fix login',
        actions: [
          KitAction(
            label: 'Stop task',
            icon: AppIcons.stop,
            onPressed: null,
            disabledReason: 'Nothing is running.',
          ),
        ],
        menuKey: ValueKey('more'),
      ),
    );
    expect(find.byTooltip('Stop task'), findsNothing);
    await tester.tap(find.byKey(const ValueKey('more')));
    await tester.pumpAndSettle();
    final item = tester
        .widget<KitMenuPanel>(find.byType(KitMenuPanel))
        .items
        .single;
    expect(item.enabled, isFalse);
    expect(item.disabledReason, 'Nothing is running.');
    // Esc closes the overflow.
    await tester.sendKeyEvent(LogicalKeyboardKey.escape);
    await tester.pumpAndSettle();
    expect(find.byType(KitMenuPanel), findsNothing);
  });

  testWidgets('asserts: no icon, destructive, switcher without label', (
    tester,
  ) async {
    await _pump(tester, const SizedBox());
    final context = tester.element(find.byType(Scaffold));
    expect(
      () => KitTopBar(
        title: 'x',
        actions: [KitAction(label: 'Go', onPressed: () {})],
      ).build(context),
      throwsAssertionError,
    );
    expect(
      () => KitTopBar(
        title: 'x',
        actions: [
          KitAction(
            label: 'Delete',
            icon: AppIcons.stop,
            destructive: true,
            onPressed: () {},
          ),
        ],
      ).build(context),
      throwsAssertionError,
    );
    expect(
      () => KitTopBar(title: 'x', onTitleTap: () {}),
      throwsAssertionError,
    );
  });

  testWidgets('switcher: tap, label and needs-you badge', (tester) async {
    final semantics = tester.ensureSemantics();
    var taps = 0;
    await _pump(
      tester,
      KitTopBar(
        title: 'shopfront',
        onTitleTap: () => taps++,
        titleTapLabel: 'Switch project',
        needsYou: 2,
        titleKey: const ValueKey('title'),
      ),
    );
    await tester.tap(find.text('shopfront'));
    await tester.pumpAndSettle();
    expect(taps, 1);
    expect(find.text('2'), findsOneWidget);
    final node = tester.getSemantics(find.byKey(const ValueKey('title')));
    expect(
      node.getSemanticsData().label,
      matches(RegExp(r'^shopfront, Switch project\s*, 2 need you$')),
    );
    expect(node.getSemanticsData().flagsCollection.isHeader, isTrue);
    semantics.dispose();
  });

  testWidgets('shell: pill with status word, search, dim glass', (
    tester,
  ) async {
    final semantics = tester.ensureSemantics();
    var servers = 0;
    var searches = 0;
    await _pump(
      tester,
      KitTopBar.shell(
        controls: KitShellControls(
          server: 'Laptop',
          serverStatus: 'Connected',
          serverTone: AppStatusTone.ok,
          onServer: () => servers++,
          onSearch: () => searches++,
        ),
      ),
    );
    expect(
      find.textContaining('Connected', findRichText: true),
      findsOneWidget,
    );
    expect(
      find.bySemanticsLabel(RegExp('Laptop.*, Connected, Switch server')),
      findsOneWidget,
    );
    await tester.tap(find.textContaining('Connected', findRichText: true));
    await tester.tap(find.byTooltip('Search'));
    expect((servers, searches), (1, 1));
    // Pill and search are one solid pair that sits together while the page
    // is scrolled.
    final glass = tester.widgetList<KitGlass>(find.byType(KitGlass));
    expect(glass.length, 1);
    expect(glass.single.trailing, isNotNull);
    semantics.dispose();
  });

  // Emulator QA F8: at 2.0 the pill cut "127.0.0.1" to "127.…" to keep
  // " · Connected" beside it.
  testWidgets('shell at 200 % text: the server name is whole, the status '
      'goes under it', (tester) async {
    await _pump(
      tester,
      KitTopBar.shell(
        controls: KitShellControls(
          server: '127.0.0.1',
          serverStatus: 'Connected',
          serverTone: AppStatusTone.ok,
          onServer: () {},
          onSearch: () {},
        ),
      ),
      textScale: 2,
    );
    expect(tester.takeException(), isNull);
    final name = find.textContaining('127.0.0.1');
    expect(name, findsOneWidget);
    final painter = tester.renderObject<RenderParagraph>(name);
    expect(painter.didExceedMaxLines, isFalse);
    final status = find.text('Connected');
    expect(status, findsOneWidget);
    expect(
      tester.getTopLeft(status).dy,
      greaterThan(tester.getBottomLeft(name).dy - 1),
    );
  });

  testWidgets('shell at 100 % text: name and status share one line', (
    tester,
  ) async {
    await _pump(
      tester,
      KitTopBar.shell(
        controls: KitShellControls(
          server: '127.0.0.1',
          serverStatus: 'Connected',
          onServer: () {},
          onSearch: () {},
        ),
      ),
    );
    expect(find.text(' · Connected'), findsOneWidget);
  });

  testWidgets('sidebar: pill, project, search stacked; no project row '
      'without onProject', (tester) async {
    Widget sidebar({VoidCallback? onProject}) => SizedBox(
      width: 280,
      child: KitShellControls(
        server: 'Laptop',
        serverStatus: 'Connected',
        onServer: () {},
        project: 'shopfront',
        onProject: onProject,
        onSearch: () {},
        layout: KitShellControlsLayout.sidebar,
      ),
    );
    await _pump(tester, sidebar(onProject: () {}), size: const Size(1280, 800));
    // R1: the sidebar leaves "Connected" to the green dot; the name marks
    // the pill.
    final pillY = tester.getCenter(find.textContaining('Laptop')).dy;
    final projectY = tester.getCenter(find.textContaining('shopfront')).dy;
    final searchY = tester.getCenter(find.text('Search')).dy;
    expect(pillY < projectY && projectY < searchY, isTrue);
    await _pump(tester, sidebar(), size: const Size(1280, 800));
    expect(find.textContaining('shopfront'), findsNothing);
  });

  testWidgets('200 % text at 320 dp: no overflow, title wraps', (tester) async {
    await _pump(
      tester,
      KitTopBar(
        title: 'A rather long conversation title that wraps',
        subtitle: 'Working · 2 min',
        exit: KitTopBarExit.back,
        actions: _threeActions([]),
      ),
      size: const Size(320, 800),
      textScale: 2,
    );
    expect(tester.takeException(), isNull);
    final titleBox = tester.getSize(
      find.text('A rather long conversation title that wraps'),
    );
    expect(titleBox.height, greaterThan(17 * 2 * 1.2));
    expect(find.byType(KitIconButton), findsNWidgets(3));
  });

  testWidgets('RTL: Back at the right, actions at the left', (tester) async {
    await _pump(
      tester,
      KitTopBar(
        title: 'Detail',
        exit: KitTopBarExit.back,
        actions: [
          KitAction(label: 'Share', icon: AppIcons.copy, onPressed: () {}),
        ],
      ),
      direction: TextDirection.rtl,
    );
    final back = tester.getCenter(find.byTooltip('Back')).dx;
    final share = tester.getCenter(find.byTooltip('Share')).dx;
    expect(back, greaterThan(412 / 2));
    expect(share, lessThan(412 / 2));
  });

  testWidgets('reduced motion: a subtitle change settles in one pump', (
    tester,
  ) async {
    await _pump(
      tester,
      const KitTopBar(title: 'Fix login', subtitle: 'Working'),
      reduced: true,
    );
    await tester.pumpWidget(
      MaterialApp(
        theme: AppTheme.light(),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(context).copyWith(disableAnimations: true),
          child: child!,
        ),
        home: const Scaffold(
          body: KitTopBar(title: 'Fix login', subtitle: 'Not answering'),
        ),
      ),
    );
    await tester.pump();
    expect(find.text('Working', findRichText: true), findsNothing);
    expect(find.text('Not answering', findRichText: true), findsOneWidget);
  });
}
