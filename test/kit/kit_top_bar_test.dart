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
import 'package:opencode_mobile/ui/kit/kit_buttons.dart';
import 'package:opencode_mobile/ui/kit/kit_effects.dart';
import 'package:opencode_mobile/ui/kit/kit_icon_button.dart';
import 'package:opencode_mobile/ui/kit/kit_menu.dart';
import 'package:opencode_mobile/ui/kit/kit_text.dart';
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
  // A short name: in the test font (every glyph an em wide) a longer one
  // would be cut beside "Connected", and then the green dot alone says it
  // (impeccable audit 2026-10-28, #7).
  Widget motionControls(bool connected) => KitShellControls(
    server: 'PC',
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
        shows: 'Connected',
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

  testWidgets('subtitleTail: kept while the line fits, dropped before the '
      'state is cut; read whole either way', (tester) async {
    final semantics = tester.ensureSemantics();
    String shown() => tester
        .widgetList<RichText>(find.byType(RichText))
        .map((t) => t.text.toPlainText())
        .firstWhere((t) => t.startsWith('Now:'));
    await _pump(
      tester,
      const KitTopBar(
        title: 'Tasks',
        subtitle: 'Now: Sync',
        subtitleTail: 'Next',
        titleKey: ValueKey('title'),
      ),
    );
    expect(shown(), 'Now: Sync · Next');
    await _pump(
      tester,
      const KitTopBar(
        title: 'Tasks',
        subtitle: 'Now: Sync',
        subtitleTail: 'Next',
        titleKey: ValueKey('title'),
      ),
      size: const Size(150, 640),
    );
    expect(shown(), 'Now: Sync');
    expect(
      tester
          .getSemantics(find.byKey(const ValueKey('title')))
          .getSemanticsData()
          .label,
      'Tasks, Now: Sync, Next',
    );
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

  testWidgets('centred: the title at the true centre, Close at the end', (
    tester,
  ) async {
    await _pump(tester, const SizedBox.shrink());
    final nav = tester.state<NavigatorState>(find.byType(Navigator));
    nav.push(
      MaterialPageRoute<void>(
        fullscreenDialog: true,
        builder: (_) => const Scaffold(
          body: KitTopBar(title: 'New conversation', centred: true),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(
      tester.getCenter(find.text('New conversation')).dx,
      closeTo(412 / 2, .5),
    );
    expect(find.byTooltip('Close'), findsOneWidget);
    expect(
      tester.getCenter(find.byTooltip('Close')).dx,
      greaterThan(tester.getRect(find.text('New conversation')).right),
    );
    await tester.tap(find.byTooltip('Close'));
    await tester.pumpAndSettle();
    expect(find.text('New conversation'), findsNothing);
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

  testWidgets('shell: pill with status word, then search and the gear', (
    tester,
  ) async {
    final semantics = tester.ensureSemantics();
    var servers = 0;
    var searches = 0;
    var settings = 0;
    await _pump(
      tester,
      KitTopBar.shell(
        controls: KitShellControls(
          server: 'Laptop',
          serverStatus: 'Connected',
          serverTone: AppStatusTone.ok,
          onServer: () => servers++,
          onSearch: () => searches++,
          onSettings: () => settings++,
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
    await tester.tap(find.byTooltip('Settings'));
    expect((servers, searches, settings), (1, 1, 1));
    // The pill at the start; search, then the gear, at the end.
    final pill = tester.getRect(find.textContaining('Laptop'));
    final search = tester.getRect(find.byTooltip('Search'));
    final gear = tester.getRect(find.byTooltip('Settings'));
    expect(pill.right < search.left && search.right <= gear.left, isTrue);
    semantics.dispose();
  });

  // Emulator QA F8, then sweep 2310: at 2.0 the pill is one line. A name
  // too long for it ends in an ellipsis; "Connected" is left to the dot.
  testWidgets('shell at 200 % text: a long server name is one line with an '
      'ellipsis, not three lines', (tester) async {
    await _pump(
      tester,
      KitTopBar.shell(
        controls: KitShellControls(
          server: 'Computer at 10.0.2.2',
          serverStatus: 'Connected',
          serverTone: AppStatusTone.ok,
          onServer: () {},
          onSearch: () {},
        ),
      ),
      textScale: 2,
    );
    expect(tester.takeException(), isNull);
    final name = find.textContaining('Computer at');
    expect(name, findsOneWidget);
    final paragraph = tester.renderObject<RenderParagraph>(name);
    expect(paragraph.didExceedMaxLines, isTrue);
    expect(paragraph.size.height, lessThan(60));
    expect(find.text('Connected'), findsNothing);
  });

  // The owner's concepts (2026-10-22): one line, "name · state", at the
  // chips' size; the pill keeps the 48 dp target.
  for (final direction in TextDirection.values) {
    testWidgets('shell at 100 % text: name · status on one line '
        '(${direction.name})', (tester) async {
      await _pump(
        tester,
        KitTopBar.shell(
          controls: KitShellControls(
            // Short enough to share the line whole in the test font.
            server: 'PC',
            serverStatus: 'Connected',
            serverTone: AppStatusTone.ok,
            onServer: () {},
            onSearch: () {},
            serverKey: const ValueKey('pill'),
          ),
        ),
        direction: direction,
      );
      final name = find.textContaining('PC');
      final status = find.text('Connected');
      expect(status, findsOneWidget);
      expect(find.text(' · '), findsOneWidget);
      expect(tester.getCenter(status).dy, tester.getCenter(name).dy);
      final rtl = direction == TextDirection.rtl;
      expect(
        tester.getCenter(status).dx > tester.getCenter(name).dx,
        !rtl,
        reason: 'the status follows the name in reading order',
      );
      expect(tester.getSize(find.byKey(const ValueKey('pill'))).height, 48);
      expect(
        tester
            .widget<KitText>(
              find.byKey(const ValueKey('kit-shell-server-status')),
            )
            .role,
        KitTextRole.secondary,
      );
    });
  }

  testWidgets('shell: the pill sits space4 under the top, beside round search '
      'and gear one space4 apart', (tester) async {
    await _pump(
      tester,
      KitTopBar.shell(
        controls: KitShellControls(
          server: 'Laptop',
          serverStatus: 'Connected',
          onServer: () {},
          onSearch: () {},
          onSettings: () {},
          serverKey: const ValueKey('pill'),
          searchKey: const ValueKey('search'),
          settingsKey: const ValueKey('gear'),
        ),
      ),
    );
    final pill = tester.getRect(find.byKey(const ValueKey('pill')));
    final search = tester.getRect(find.byKey(const ValueKey('search')));
    final gear = tester.getRect(find.byKey(const ValueKey('gear')));
    expect(pill.top, 16);
    expect(search.size, const Size(48, 48));
    expect(gear.size, const Size(48, 48));
    expect(search.center.dy, pill.center.dy);
    expect(gear.left - search.right, 16);
    expect(
      find.byKey(const ValueKey('kit-icon-button-round')),
      findsNWidgets(2),
    );
  });

  testWidgets('shell without onSettings has no gear (a rail names Settings)', (
    tester,
  ) async {
    await _pump(
      tester,
      KitTopBar.shell(
        controls: KitShellControls(
          server: 'Laptop',
          serverStatus: 'Connected',
          onServer: () {},
          onSearch: () {},
        ),
      ),
    );
    expect(find.byTooltip('Settings'), findsNothing);
    expect(find.byTooltip('Search'), findsOneWidget);
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

  group('accessory', () {
    const key = Key('top-bar-accessory');
    final menuKey = const Key('top-bar-menu');

    for (final (name, size) in [
      ('phone', const Size(412, 915)),
      ('pc', const Size(1280, 800)),
    ]) {
      testWidgets('stays in the bar before the overflow on a $name', (
        tester,
      ) async {
        await _pump(
          tester,
          KitTopBar(
            title: 'Fix login',
            exit: KitTopBarExit.none,
            actions: _threeActions([]),
            accessory: const SizedBox(key: key, width: 40, height: 40),
            menu: [KitMenuItem(label: 'Rename', onSelected: () {})],
            menuKey: menuKey,
          ),
          size: size,
        );
        final accessory = tester.getCenter(find.byKey(key));
        final menu = tester.getCenter(find.byKey(menuKey));
        expect(accessory.dx, lessThan(menu.dx));
        expect(accessory.dy, closeTo(menu.dy, 6));
      });
    }

    testWidgets('mirrors under right-to-left', (tester) async {
      await _pump(
        tester,
        KitTopBar(
          title: 'Fix login',
          exit: KitTopBarExit.none,
          accessory: const SizedBox(key: key, width: 40, height: 40),
          menu: [KitMenuItem(label: 'Rename', onSelected: () {})],
          menuKey: menuKey,
        ),
        direction: TextDirection.rtl,
      );
      expect(
        tester.getCenter(find.byKey(key)).dx,
        greaterThan(tester.getCenter(find.byKey(menuKey)).dx),
      );
    });

    testWidgets('alone it still shows, with no actions or menu', (
      tester,
    ) async {
      await _pump(
        tester,
        const KitTopBar(
          title: 'Fix login',
          exit: KitTopBarExit.none,
          accessory: SizedBox(key: key, width: 40, height: 40),
        ),
      );
      expect(find.byKey(key), findsOneWidget);
    });
  });

  group('scope link', () {
    testWidgets('a link alone: mark, words, the act; one 48 dp target', (
      tester,
    ) async {
      var taps = 0;
      await _pump(
        tester,
        KitTopBar(
          title: 'Terminal',
          scope: KitTopBarScope(
            label: '',
            semanticsLabel: 'Runs on Laptop',
            link: KitTopBarLink(
              linkKey: const ValueKey('link'),
              label: 'Dev server running',
              action: 'Open preview',
              onTap: () => taps++,
            ),
          ),
        ),
      );
      expect(find.textContaining('Open preview'), findsOneWidget);
      expect(
        tester.getSize(find.byKey(const ValueKey('link'))).height,
        greaterThanOrEqualTo(48),
      );
      await tester.tap(find.byKey(const ValueKey('link')));
      expect(taps, 1);
    });

    testWidgets('a place with nothing to open has no chevron and no target', (
      tester,
    ) async {
      await _pump(
        tester,
        const KitTopBar(
          title: 'Terminal',
          scope: KitTopBarScope(
            label: 'Laptop',
            semanticsLabel: 'Runs on Laptop',
          ),
        ),
      );
      expect(find.text('Laptop'), findsOneWidget);
      expect(find.byIcon(AppIconography.chevronDown), findsNothing);
    });
  });

  testWidgets(
    'Paper sets the page title at its own size; Solid keeps the role',
    (tester) async {
      double size() {
        final text = tester.widget<Text>(find.text('Project'));
        return text.style!.fontSize!;
      }

      await _pump(tester, const KitTopBar(title: 'Project'));
      expect(size(), 16, reason: 'the headline role');
      await _pump(
        tester,
        const KitTopBar(title: 'Project'),
        effects: const KitEffects(surfaceStyle: KitSurfaceStyle.paper),
      );
      expect(size(), 24);
      // A card's headline elsewhere keeps the role's 16 under Paper.
      await _pump(
        tester,
        const KitText('Card', role: KitTextRole.headline),
        effects: const KitEffects(surfaceStyle: KitSurfaceStyle.paper),
      );
      expect(tester.widget<Text>(find.text('Card')).style!.fontSize, 16);
    },
  );
}
