// Gate G9 (docs/ux-system/kit-api/KitMenu.md) for KitMenu: ordering
// (destructive last, after one divider), icon/check slot, disabled items,
// KitMenuItem.copy (redaction, one announcement, no SnackBar), empty items,
// keyboard navigation with desktop capabilities, anchoring (point and
// context, RTL), row height at 2.0 text, no stacking on a confirm raised
// from a menu item, reduced motion, and the retired constructor (KIT-43).
import 'dart:async';
import 'dart:ui' show CheckedState, Tristate;

import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/scheduler.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit_effects.dart';
import 'package:opencode_mobile/ui/kit/kit_menu.dart';
import 'package:opencode_mobile/ui/kit/kit_motion.dart';
import 'package:opencode_mobile/ui/kit/kit_redact.dart';
import 'package:opencode_mobile/ui/kit/kit_sheet.dart';
import 'package:opencode_mobile/ui/kit/kit_tokens.dart';

import 'kit_harness.dart';
import 'kit_motion_still.dart';

/// Whether keyboard focus is on [target] itself or something inside it (the
/// same technique as `kit_keyboard_test.dart`'s `_focusIn`).
bool _focusIn(WidgetTester tester, Finder target) {
  final focused = FocusManager.instance.primaryFocus?.context;
  if (focused == null) return false;
  final element = tester.element(target);
  if (focused == element) return true;
  var found = false;
  (focused as Element).visitAncestorElements((ancestor) {
    if (ancestor == element) {
      found = true;
      return false;
    }
    return true;
  });
  return found;
}

bool _focusedOnLabel(WidgetTester tester, String label) => _focusIn(
  tester,
  find.ancestor(of: find.text(label), matching: find.byType(InkWell)).first,
);

/// A minimal host with its own [NavigatorObserver] so a test can inspect the
/// pushed route's identity (test 10: no stacking on the menu route).
class _RouteLog extends NavigatorObserver {
  final pushed = <Route<dynamic>>[];

  @override
  void didPush(Route<dynamic> route, Route<dynamic>? previousRoute) =>
      pushed.add(route);
}

Future<BuildContext> _pumpLoggedHost(WidgetTester tester, _RouteLog log) async {
  late BuildContext context;
  await tester.pumpWidget(
    MaterialApp(
      debugShowCheckedModeBanner: false,
      theme: AppTheme.dark(),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      navigatorObservers: [log],
      home: Scaffold(
        body: Builder(
          builder: (inner) {
            context = inner;
            return const SizedBox.expand();
          },
        ),
      ),
    ),
  );
  return context;
}

/// A focusable invoker that opens [items] the ways the spec names: Shift+F10,
/// the context-menu key or Enter from the keyboard, and a right-click with
/// the pointer's position (KitMenu.md Keyboard and §8.3).
class _Invoker {
  _Invoker(this.items);

  final List<KitMenuItem> items;
  final focus = FocusNode(debugLabel: 'invoker');
  final results = <KitMenuItem?>[];

  Widget build() => Builder(
    builder: (context) => Focus(
      focusNode: focus,
      onKeyEvent: (node, event) {
        if (event is! KeyDownEvent) return KeyEventResult.ignored;
        final key = event.logicalKey;
        final opens =
            (key == LogicalKeyboardKey.f10 &&
                HardwareKeyboard.instance.isShiftPressed) ||
            key == LogicalKeyboardKey.contextMenu ||
            key == LogicalKeyboardKey.enter;
        if (!opens) return KeyEventResult.ignored;
        unawaited(showKitMenu(context, items: items).then(results.add));
        return KeyEventResult.handled;
      },
      child: GestureDetector(
        onSecondaryTapUp: (details) => unawaited(
          showKitMenu(
            context,
            items: items,
            position: details.globalPosition,
          ).then(results.add),
        ),
        child: const SizedBox(width: 200, height: 48, child: Text('Invoker')),
      ),
    ),
  );
}

Future<void> _pumpInvokerHost(WidgetTester tester, _Invoker invoker) async {
  tester.view.physicalSize = const Size(1280, 800);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    MaterialApp(
      debugShowCheckedModeBanner: false,
      theme: AppTheme.dark(),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      home: Scaffold(
        body: Align(
          alignment: AlignmentDirectional.topStart,
          child: Padding(
            padding: const EdgeInsets.all(40),
            child: invoker.build(),
          ),
        ),
      ),
    ),
  );
  invoker.focus.requestFocus();
  await tester.pump();
}

/// The accent keyboard focus ring (KitMenu.md Tokens: `accent` for the
/// focus ring, `KitTokens.focusRingWidth`), wherever it is painted.
Finder _focusRing(WidgetTester tester) {
  final accent = KitTokens.of(
    tester.element(find.byType(KitMenuPanel)),
  ).roles.accent;
  return find.byWidgetPredicate((widget) {
    if (widget is! DecoratedBox) return false;
    final decoration = widget.decoration;
    if (decoration is! BoxDecoration) return false;
    final border = decoration.border;
    return border is Border && border.top.color == accent;
  });
}

Finder _tileOf(String label) =>
    find.ancestor(of: find.text(label), matching: find.byType(InkWell)).first;

Future<void> _shiftF10(WidgetTester tester) async {
  await tester.sendKeyDownEvent(LogicalKeyboardKey.shiftLeft);
  await tester.sendKeyEvent(LogicalKeyboardKey.f10);
  await tester.sendKeyUpEvent(LogicalKeyboardKey.shiftLeft);
}

void main() {
  kitMotionStillTests(
    'KitMenuPanel',
    builds: {
      'default': () => KitMenuPanel(
        items: [KitMenuItem(label: 'Archive', onSelected: () {})],
        onSelected: (_) {},
      ),
    },
  );
  kitMotionStillTests(
    'showKitMenu',
    opens: {
      'default': KitMotionOpen(
        (context) => showKitMenu(
          context,
          items: [KitMenuItem(label: 'Archive', onSelected: () {})],
        ),
        shows: 'Archive',
      ),
    },
  );

  TestWidgetsFlutterBinding.ensureInitialized();

  group('kitMenuLayout (ordering, structural)', () {
    KitMenuItem item(String label, {bool destructive = false, Object? group}) =>
        KitMenuItem(
          label: label,
          onSelected: () {},
          destructive: destructive,
          group: group,
        );

    test('keeps order within a group and groups in first-item order', () {
      final rows = kitMenuLayout([
        item('a', group: 1),
        item('b', group: 1),
        item('c', group: 2),
      ]);
      expect(rows.whereType<KitMenuItem>().map((i) => i.label), [
        'a',
        'b',
        'c',
      ]);
      // One divider, between the two groups.
      expect(rows.where((r) => r is! KitMenuItem), hasLength(1));
    });

    test('moves destructive items last, after one divider, even when '
        'passed first', () {
      final rows = kitMenuLayout([
        item('delete', destructive: true),
        item('a', group: 1),
        item('b', group: 1),
        item('c', group: 2),
      ]);
      expect(rows.whereType<KitMenuItem>().map((i) => i.label), [
        'a',
        'b',
        'c',
        'delete',
      ]);
      // One divider between the groups, one before the destructive block.
      expect(rows.where((r) => r is! KitMenuItem), hasLength(2));
    });

    test('a named group leads with its heading (slice-P10.1-2)', () {
      const goTo = KitMenuGroup('Go to');
      const act = KitMenuGroup('Do');
      final rows = kitMenuLayout([
        item('open'),
        item('find', group: goTo),
        item('details', group: goTo),
        item('fork', group: act),
        item('delete', destructive: true),
      ]);
      expect(rows.whereType<KitMenuGroup>().map((g) => g.label), [
        'Go to',
        'Do',
      ]);
      // open | divider, Go to, find, details | divider, Do, fork | divider,
      // delete: a heading follows its group's divider, never an item.
      expect(rows.indexOf(goTo), rows.indexOf(rows[1]) + 1);
      expect(rows[rows.indexOf(goTo) + 1], isA<KitMenuItem>());
      expect(rows[rows.indexOf(act) + 1], isA<KitMenuItem>());
      expect(rows.last, isA<KitMenuItem>());
    });

    test('no leading divider when every item is destructive', () {
      final rows = kitMenuLayout([
        item('delete', destructive: true),
        item('stop', destructive: true),
      ]);
      expect(rows.first, isA<KitMenuItem>());
      expect(rows.where((r) => r is! KitMenuItem), isEmpty);
    });
  });

  testWidgets('a named group heading is a header, never focused', (
    tester,
  ) async {
    final context = await pumpKitHost(tester);
    final handle = tester.ensureSemantics();
    var runs = 0;
    unawaited(
      showKitMenu(
        context,
        items: [
          KitMenuItem(
            label: 'Find',
            group: const KitMenuGroup('Go to'),
            onSelected: () => runs++,
          ),
          KitMenuItem(
            label: 'Fork conversation',
            group: const KitMenuGroup('Do'),
            onSelected: () => runs++,
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();
    final heading = find.byKey(const ValueKey('kit-menu-heading-Go to'));
    expect(heading, findsOneWidget);
    expect(
      tester.getSemantics(find.text('Go to')),
      isSemantics(isHeader: true, label: 'Go to'),
    );
    // Tapping a heading selects nothing and keeps the menu open.
    await tester.tap(heading);
    await tester.pumpAndSettle();
    expect(runs, 0);
    expect(find.text('Find'), findsOneWidget);
    // The first focusable entry is the first item, not the heading.
    await tester.sendKeyEvent(LogicalKeyboardKey.arrowDown);
    await tester.pumpAndSettle();
    await tester.sendKeyEvent(LogicalKeyboardKey.enter);
    await tester.pumpAndSettle();
    expect(runs, 1);
    handle.dispose();
  });

  testWidgets('1. returns the tapped item after onSelected ran exactly once', (
    tester,
  ) async {
    final context = await pumpKitHost(tester);
    var runs = 0;
    KitMenuItem? result;
    unawaited(
      showKitMenu(
        context,
        items: [KitMenuItem(label: 'Archive', onSelected: () => runs++)],
      ).then((v) => result = v),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Archive'));
    await tester.pumpAndSettle();

    expect(runs, 1);
    expect(result?.label, 'Archive');
  });

  group('1. dismissing runs nothing and resolves null', () {
    testWidgets('tap outside', (tester) async {
      final context = await pumpKitHost(tester);
      var runs = 0;
      KitMenuItem? result;
      var done = false;
      unawaited(
        showKitMenu(
          context,
          items: [KitMenuItem(label: 'Archive', onSelected: () => runs++)],
        ).then((v) {
          result = v;
          done = true;
        }),
      );
      await tester.pumpAndSettle();

      await tester.tapAt(const Offset(4, 4));
      await tester.pumpAndSettle();

      expect(done, isTrue);
      expect(result, isNull);
      expect(runs, 0);
    });

    testWidgets('Esc', (tester) async {
      final context = await pumpKitHost(tester);
      var runs = 0;
      KitMenuItem? result;
      unawaited(
        showKitMenu(
          context,
          items: [KitMenuItem(label: 'Archive', onSelected: () => runs++)],
        ).then((v) => result = v),
      );
      await tester.pumpAndSettle();

      await tester.sendKeyEvent(LogicalKeyboardKey.escape);
      await tester.pumpAndSettle();

      expect(result, isNull);
      expect(runs, 0);
      expect(find.text('Archive'), findsNothing);
    });

    testWidgets('Back', (tester) async {
      final context = await pumpKitHost(tester);
      var runs = 0;
      KitMenuItem? result;
      unawaited(
        showKitMenu(
          context,
          items: [KitMenuItem(label: 'Archive', onSelected: () => runs++)],
        ).then((v) => result = v),
      );
      await tester.pumpAndSettle();

      await tester.binding.handlePopRoute();
      await tester.pumpAndSettle();

      expect(result, isNull);
      expect(runs, 0);
    });
  });

  testWidgets(
    '2. destructive items render last after a divider; groups keep order',
    (tester) async {
      final context = await pumpKitHost(tester);
      unawaited(
        showKitMenu(
          context,
          items: [
            KitMenuItem(label: 'Delete', onSelected: () {}, destructive: true),
            KitMenuItem(label: 'Rename', onSelected: () {}, group: 'a'),
            KitMenuItem(label: 'Duplicate', onSelected: () {}, group: 'a'),
            KitMenuItem(label: 'Share', onSelected: () {}, group: 'b'),
          ],
        ),
      );
      await tester.pumpAndSettle();

      final ys = [
        'Rename',
        'Duplicate',
        'Share',
        'Delete',
      ].map((l) => tester.getTopLeft(find.text(l)).dy).toList();
      for (var i = 0; i < ys.length - 1; i++) {
        expect(
          ys[i],
          lessThan(ys[i + 1]),
          reason:
              'expected top-to-bottom order Rename, Duplicate, Share, '
              'Delete',
        );
      }
      // One divider between the two groups, one before the destructive
      // block, each carrying the frozen TEST-5 key.
      final dividers = find.byKey(const ValueKey('kit-menu-divider'));
      expect(dividers, findsNWidgets(2));
      final dividerYs = [
        for (final e in dividers.evaluate())
          tester.getTopLeft(find.byWidget(e.widget)).dy,
      ]..sort();
      expect(dividerYs.first, greaterThan(ys[1]));
      expect(dividerYs.first, lessThan(ys[2]));
      expect(dividerYs.last, greaterThan(ys[2]));
      expect(dividerYs.last, lessThan(ys[3]));
    },
  );

  group('3. checked', () {
    testWidgets('checked:true paints the check and exposes it', (tester) async {
      // Disposed inline, not via addTearDown: the end-of-test leak check
      // runs before addTearDown callbacks (test/goldens/kit/kit_gallery.dart
      // "Released before the test ends; a tear-down runs too late").
      final semantics = tester.ensureSemantics();
      final context = await pumpKitHost(tester);
      unawaited(
        showKitMenu(
          context,
          items: [
            KitMenuItem(label: 'On', onSelected: () {}, checked: true),
            KitMenuItem(label: 'Off', onSelected: () {}, checked: false),
          ],
        ),
      );
      await tester.pumpAndSettle();

      expect(
        find.descendant(
          of: find
              .ancestor(of: find.text('On'), matching: find.byType(InkWell))
              .first,
          matching: find.byIcon(AppIconography.check),
        ),
        findsOneWidget,
      );
      expect(
        find.descendant(
          of: find
              .ancestor(of: find.text('Off'), matching: find.byType(InkWell))
              .first,
          matching: find.byIcon(AppIconography.check),
        ),
        findsNothing,
      );

      final on = tester.getSemantics(find.text('On'));
      expect(on.flagsCollection.isChecked, isNot(CheckedState.none));
      expect(on.flagsCollection.isChecked, CheckedState.isTrue);
      final off = tester.getSemantics(find.text('Off'));
      expect(off.flagsCollection.isChecked, isNot(CheckedState.none));
      expect(off.flagsCollection.isChecked, CheckedState.isFalse);
      semantics.dispose();
    });

    testWidgets('a disabled checked item keeps its checked semantics', (
      tester,
    ) async {
      final semantics = tester.ensureSemantics();
      final context = await pumpKitHost(tester);
      unawaited(
        showKitMenu(
          context,
          items: [
            KitMenuItem(
              label: 'Sort by name',
              onSelected: () {},
              checked: true,
              enabled: false,
              disabledReason: 'Sorting is locked while syncing',
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();

      expect(find.byIcon(AppIconography.check), findsOneWidget);
      final data = tester.getSemantics(find.text('Sort by name'));
      expect(data.flagsCollection.isChecked, CheckedState.isTrue);
      expect(data.flagsCollection.isEnabled, Tristate.isFalse);
      semantics.dispose();
    });
  });

  testWidgets(
    '4. a disabled item shows its reason, cannot be selected, exposes the '
    'reason as its hint',
    (tester) async {
      final semantics = tester.ensureSemantics();
      final context = await pumpKitHost(tester);
      var runs = 0;
      unawaited(
        showKitMenu(
          context,
          items: [
            KitMenuItem(
              label: 'Restart',
              onSelected: () => runs++,
              enabled: false,
              disabledReason: 'No server connected',
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('No server connected'), findsOneWidget);
      expect(find.byType(InkWell), findsNothing);

      await tester.tap(find.text('Restart'), warnIfMissed: false);
      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await tester.pumpAndSettle();
      expect(runs, 0);

      final data = tester.getSemantics(find.text('Restart'));
      expect(data.flagsCollection.isEnabled, isNot(Tristate.none));
      expect(data.flagsCollection.isEnabled, Tristate.isFalse);
      expect(data.hint, 'No server connected');
      semantics.dispose();
    },
  );

  testWidgets('5. KitMenuItem.copy copies redacted text after the menu closes, '
      'announces once, no SnackBar', (tester) async {
    final platform = <MethodCall>[];
    final announcements = <Map<Object?, Object?>>[];
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(SystemChannels.platform, (call) async {
      platform.add(call);
      return null;
    });
    messenger.setMockDecodedMessageHandler<dynamic>(
      SystemChannels.accessibility,
      (message) async {
        final map = message as Map<Object?, Object?>;
        if (map['type'] == 'announce') announcements.add(map);
        return null;
      },
    );
    addTearDown(() {
      messenger.setMockMethodCallHandler(SystemChannels.platform, null);
      messenger.setMockDecodedMessageHandler<dynamic>(
        SystemChannels.accessibility,
        null,
      );
    });

    final context = await pumpKitHost(tester);
    KitMenuItem? result;
    unawaited(
      showKitMenu(
        context,
        items: [
          KitMenuItem.copy(
            label: 'Copy key',
            text: () => 'export KEY=sk-ant-api03-AbCdEfGhIjKlMnOp',
          ),
        ],
      ).then((v) => result = v),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Copy key'));
    await tester.pumpAndSettle();

    final setData = platform.singleWhere(
      (c) => c.method == 'Clipboard.setData',
    );
    expect(
      (setData.arguments as Map)['text'],
      'export KEY=sk-ant-${KitRedact.mask}',
    );
    expect(announcements, hasLength(1));
    expect((announcements.single['data'] as Map)['message'], 'Copied');
    expect(find.byType(SnackBar), findsNothing);
    expect(result?.label, 'Copy key');
  });

  testWidgets('5. KitMenuItem.copy(redact: false) copies verbatim (SEC-13)', (
    tester,
  ) async {
    final platform = <MethodCall>[];
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(SystemChannels.platform, (call) async {
      platform.add(call);
      return null;
    });
    addTearDown(
      () => messenger.setMockMethodCallHandler(SystemChannels.platform, null),
    );

    const raw = 'export KEY=sk-ant-api03-AbCdEfGhIjKlMnOp';
    final context = await pumpKitHost(tester);
    unawaited(
      showKitMenu(
        context,
        items: [
          KitMenuItem.copy(
            label: 'Copy message',
            text: () => raw,
            redact: false,
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('Copy message'));
    await tester.pumpAndSettle();

    final setData = platform.singleWhere(
      (c) => c.method == 'Clipboard.setData',
    );
    expect((setData.arguments as Map)['text'], raw);
  });

  testWidgets('5. KitMenuItem.copy still copies when the invoker unmounted '
      'while the menu was open', (tester) async {
    final platform = <MethodCall>[];
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(SystemChannels.platform, (call) async {
      platform.add(call);
      return null;
    });
    addTearDown(
      () => messenger.setMockMethodCallHandler(SystemChannels.platform, null),
    );

    final show = ValueNotifier(true);
    late BuildContext invoker;
    await tester.pumpWidget(
      MaterialApp(
        debugShowCheckedModeBanner: false,
        theme: AppTheme.dark(),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(
          body: ValueListenableBuilder<bool>(
            valueListenable: show,
            builder: (_, visible, _) => visible
                ? Builder(
                    builder: (inner) {
                      invoker = inner;
                      return const SizedBox(width: 100, height: 48);
                    },
                  )
                : const SizedBox.shrink(),
          ),
        ),
      ),
    );

    KitMenuItem? result;
    unawaited(
      showKitMenu(
        invoker,
        items: [
          KitMenuItem.copy(label: 'Copy name', text: () => 'fox den notes'),
        ],
      ).then((v) => result = v),
    );
    await tester.pumpAndSettle();

    // The row that opened the menu rebuilds away underneath it.
    show.value = false;
    await tester.pump();
    expect(invoker.mounted, isFalse);

    await tester.tap(find.text('Copy name'));
    await tester.pumpAndSettle();

    final setData = platform.singleWhere(
      (c) => c.method == 'Clipboard.setData',
    );
    expect((setData.arguments as Map)['text'], 'fox den notes');
    expect(result?.label, 'Copy name');
    show.dispose();
  });

  testWidgets('6. an empty item list resolves to null and pushes no route', (
    tester,
  ) async {
    final routes = RouteCounter();
    final context = await pumpKitHost(tester, routes: routes);
    final before = routes.pushes;

    final result = await showKitMenu(context, items: const []);

    expect(result, isNull);
    expect(routes.pushes, before);
  });

  group('7. keyboard, with desktop capabilities', () {
    setUp(() {
      debugPlatformCapabilities = const PlatformCapabilities.linuxDesktop();
    });
    tearDown(() {
      debugPlatformCapabilities = null;
    });

    List<KitMenuItem> items(List<String> ran) => [
      KitMenuItem(label: 'First', onSelected: () => ran.add('First')),
      KitMenuItem(
        label: 'Disabled',
        onSelected: () => ran.add('Disabled'),
        enabled: false,
        disabledReason: 'x',
      ),
      KitMenuItem(label: 'Middle', onSelected: () => ran.add('Middle')),
      KitMenuItem(label: 'Last', onSelected: () => ran.add('Last')),
    ];

    testWidgets(
      'Shift+F10 focuses the first enabled item with a visible ring; Down/Up '
      'skip disabled and wrap; End/Home jump; Esc closes and focus returns '
      'to the invoker',
      (tester) async {
        final ran = <String>[];
        final invoker = _Invoker(items(ran));
        await _pumpInvokerHost(tester, invoker);

        await _shiftF10(tester);
        await tester.pumpAndSettle();

        expect(find.byType(KitMenuPanel), findsOneWidget);
        expect(_focusedOnLabel(tester, 'First'), isTrue);
        // The accent ring marks the focused item, and only it.
        expect(
          find.descendant(of: _tileOf('First'), matching: _focusRing(tester)),
          findsOneWidget,
        );
        expect(_focusRing(tester), findsOneWidget);

        await tester.sendKeyEvent(LogicalKeyboardKey.arrowDown);
        await tester.pumpAndSettle();
        expect(
          _focusedOnLabel(tester, 'Middle'),
          isTrue,
          reason: 'Down skips the disabled item',
        );
        expect(
          find.descendant(of: _tileOf('Middle'), matching: _focusRing(tester)),
          findsOneWidget,
          reason: 'the ring follows focus',
        );

        await tester.sendKeyEvent(LogicalKeyboardKey.arrowDown);
        await tester.sendKeyEvent(LogicalKeyboardKey.arrowDown);
        await tester.pumpAndSettle();
        expect(
          _focusedOnLabel(tester, 'First'),
          isTrue,
          reason: 'Down from the last item wraps to the first',
        );

        await tester.sendKeyEvent(LogicalKeyboardKey.arrowUp);
        await tester.pumpAndSettle();
        expect(
          _focusedOnLabel(tester, 'Last'),
          isTrue,
          reason: 'Up from the first item wraps to the last',
        );

        await tester.sendKeyEvent(LogicalKeyboardKey.home);
        await tester.pumpAndSettle();
        expect(_focusedOnLabel(tester, 'First'), isTrue, reason: 'Home');

        await tester.sendKeyEvent(LogicalKeyboardKey.end);
        await tester.pumpAndSettle();
        expect(_focusedOnLabel(tester, 'Last'), isTrue, reason: 'End');

        await tester.sendKeyEvent(LogicalKeyboardKey.escape);
        await tester.pumpAndSettle();

        expect(find.byType(KitMenuPanel), findsNothing);
        expect(invoker.results, [null]);
        expect(ran, isEmpty);
        expect(invoker.focus.hasPrimaryFocus, isTrue);
        invoker.focus.dispose();
      },
    );

    testWidgets('Enter opens on the first enabled item; Enter selects', (
      tester,
    ) async {
      final ran = <String>[];
      final invoker = _Invoker(items(ran));
      await _pumpInvokerHost(tester, invoker);

      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await tester.pumpAndSettle();
      expect(_focusedOnLabel(tester, 'First'), isTrue);

      await tester.sendKeyEvent(LogicalKeyboardKey.end);
      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await tester.pumpAndSettle();

      expect(find.byType(KitMenuPanel), findsNothing);
      expect(ran, ['Last']);
      expect(invoker.results.single?.label, 'Last');
      expect(invoker.focus.hasPrimaryFocus, isTrue);
      invoker.focus.dispose();
    });

    testWidgets('the context-menu key opens it; Space selects', (tester) async {
      final ran = <String>[];
      final invoker = _Invoker(items(ran));
      await _pumpInvokerHost(tester, invoker);

      await tester.sendKeyEvent(LogicalKeyboardKey.contextMenu);
      await tester.pumpAndSettle();
      expect(_focusedOnLabel(tester, 'First'), isTrue);

      await tester.sendKeyEvent(LogicalKeyboardKey.arrowDown);
      await tester.sendKeyEvent(LogicalKeyboardKey.space);
      await tester.pumpAndSettle();

      expect(find.byType(KitMenuPanel), findsNothing);
      expect(ran, ['Middle']);
      expect(invoker.focus.hasPrimaryFocus, isTrue);
      invoker.focus.dispose();
    });

    testWidgets(
      'a right-click focuses the panel, not an item, and shows no ring; Down '
      'then reaches the first item',
      (tester) async {
        final ran = <String>[];
        final invoker = _Invoker(items(ran));
        await _pumpInvokerHost(tester, invoker);

        await tester.tap(
          find.text('Invoker'),
          buttons: kSecondaryMouseButton,
          kind: PointerDeviceKind.mouse,
        );
        await tester.pumpAndSettle();

        expect(find.byType(KitMenuPanel), findsOneWidget);
        expect(_focusIn(tester, find.byType(KitMenuPanel)), isTrue);
        for (final label in ['First', 'Middle', 'Last']) {
          expect(_focusedOnLabel(tester, label), isFalse, reason: label);
        }
        expect(_focusRing(tester), findsNothing);

        await tester.sendKeyEvent(LogicalKeyboardKey.arrowDown);
        await tester.pumpAndSettle();
        expect(_focusedOnLabel(tester, 'First'), isTrue);

        await tester.sendKeyEvent(LogicalKeyboardKey.escape);
        await tester.pumpAndSettle();
        expect(invoker.results, [null]);
        expect(invoker.focus.hasPrimaryFocus, isTrue);
        invoker.focus.dispose();
      },
    );
    testWidgets('Shift+right-click still counts as a pointer open', (
      tester,
    ) async {
      final invoker = _Invoker(items(<String>[]));
      await _pumpInvokerHost(tester, invoker);

      await tester.sendKeyDownEvent(LogicalKeyboardKey.shiftLeft);
      await tester.tap(
        find.text('Invoker'),
        buttons: kSecondaryMouseButton,
        kind: PointerDeviceKind.mouse,
      );
      await tester.sendKeyUpEvent(LogicalKeyboardKey.shiftLeft);
      await tester.pumpAndSettle();

      expect(find.byType(KitMenuPanel), findsOneWidget);
      expect(_focusedOnLabel(tester, 'First'), isFalse);
      expect(_focusRing(tester), findsNothing);
      await tester.sendKeyEvent(LogicalKeyboardKey.escape);
      await tester.pumpAndSettle();
      invoker.focus.dispose();
    });
  });

  group('opening motion', () {
    Future<BuildContext> host(
      WidgetTester tester, {
      KitMotionLevel motion = KitMotionLevel.full,
    }) async {
      tester.view.physicalSize = const Size(412, 915);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      late BuildContext context;
      await tester.pumpWidget(
        KitEffectsScope(
          effects: KitEffects(motion: motion),
          child: MaterialApp(
            debugShowCheckedModeBanner: false,
            theme: AppTheme.dark(),
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: Scaffold(
              body: Builder(
                builder: (inner) {
                  context = inner;
                  return const SizedBox.expand();
                },
              ),
            ),
          ),
        ),
      );
      return context;
    }

    final panel = find.byKey(const ValueKey('kit-menu'));

    testWidgets('drops a few dp from its anchor as it fades in', (
      tester,
    ) async {
      final context = await host(tester);
      unawaited(
        showKitMenu(
          context,
          items: [KitMenuItem(label: 'Only', onSelected: () {})],
          position: const Offset(40, 200),
        ),
      );
      await tester.pump();
      await tester.pump(KitMotion.quick ~/ 3);
      final early = tester.getTopLeft(panel).dy;
      expect(early, lessThan(200));
      expect(early, greaterThanOrEqualTo(200 - 8));
      await tester.pumpAndSettle();
      expect(tester.getTopLeft(panel).dy, 200);
    });

    testWidgets('under Animations: Calm it only fades', (tester) async {
      final context = await host(tester, motion: KitMotionLevel.calm);
      unawaited(
        showKitMenu(
          context,
          items: [KitMenuItem(label: 'Only', onSelected: () {})],
          position: const Offset(40, 200),
        ),
      );
      await tester.pump();
      await tester.pump(KitMotion.quick ~/ 3);
      expect(tester.getTopLeft(panel).dy, 200);
      await tester.pumpAndSettle();
    });
  });

  group('8. anchoring', () {
    testWidgets("with position, the panel's top-start corner is at the point", (
      tester,
    ) async {
      final context = await pumpKitHost(tester, size: const Size(412, 915));
      unawaited(
        showKitMenu(
          context,
          items: [KitMenuItem(label: 'Only', onSelected: () {})],
          position: const Offset(40, 40),
        ),
      );
      await tester.pumpAndSettle();

      expect(
        tester.getTopLeft(find.byKey(const ValueKey('kit-menu'))),
        const Offset(40, 40),
      );
    });

    testWidgets('clamped inside the window near an edge', (tester) async {
      final context = await pumpKitHost(tester, size: const Size(412, 915));
      unawaited(
        showKitMenu(
          context,
          items: [KitMenuItem(label: 'Only', onSelected: () {})],
          position: const Offset(410, 913),
        ),
      );
      await tester.pumpAndSettle();

      final topLeft = tester.getTopLeft(find.byKey(const ValueKey('kit-menu')));
      final size = tester.getSize(find.byKey(const ValueKey('kit-menu')));
      expect(topLeft.dx, greaterThanOrEqualTo(16));
      expect(topLeft.dy, greaterThanOrEqualTo(16));
      expect(topLeft.dx + size.width, lessThanOrEqualTo(412 - 16 + 0.5));
      expect(topLeft.dy + size.height, lessThanOrEqualTo(915 - 16 + 0.5));
    });

    testWidgets("anchored, aligns to the invoker's end edge in RTL", (
      tester,
    ) async {
      final context = await pumpKitHost(
        tester,
        size: const Size(412, 915),
        locale: const Locale('ar'),
      );
      unawaited(
        showKitMenu(
          context,
          items: [KitMenuItem(label: 'واحد', onSelected: () {})],
        ),
      );
      await tester.pumpAndSettle();

      // The anchor context fills the whole screen; its end (left in RTL)
      // edge is the screen's left edge, so the panel's own end edge sits
      // there too, clamped by the gutter.
      final topLeft = tester.getTopLeft(find.byKey(const ValueKey('kit-menu')));
      expect(topLeft.dx, closeTo(16, 0.5));
    });

    testWidgets('stays clear of the status bar and opens above the '
        'on-screen keyboard', (tester) async {
      tester.view.physicalSize = const Size(412, 915);
      tester.view.devicePixelRatio = 1;
      tester.view.padding = const FakeViewPadding(top: 24);
      tester.view.viewInsets = const FakeViewPadding(bottom: 400);
      addTearDown(tester.view.reset);
      late BuildContext chip;
      await tester.pumpWidget(
        MaterialApp(
          debugShowCheckedModeBanner: false,
          theme: AppTheme.dark(),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Scaffold(
            resizeToAvoidBottomInset: false,
            body: Align(
              alignment: AlignmentDirectional.topStart,
              child: Padding(
                // A composer chip just above the keyboard's top (515).
                padding: const EdgeInsetsDirectional.only(start: 100, top: 440),
                child: Builder(
                  builder: (inner) {
                    chip = inner;
                    return const SizedBox(width: 200, height: 48);
                  },
                ),
              ),
            ),
          ),
        ),
      );

      unawaited(
        showKitMenu(
          chip,
          items: [KitMenuItem(label: 'Only', onSelected: () {})],
        ),
      );
      await tester.pumpAndSettle();
      final panel = find.byKey(const ValueKey('kit-menu'));
      final rect = tester.getRect(panel);
      expect(
        rect.bottom,
        lessThanOrEqualTo(440),
        reason: 'no room below above the keyboard, so it opens above',
      );
      expect(rect.bottom, lessThanOrEqualTo(915 - 400 - 16));
      await tester.sendKeyEvent(LogicalKeyboardKey.escape);
      await tester.pumpAndSettle();

      unawaited(
        showKitMenu(
          chip,
          items: [KitMenuItem(label: 'Only', onSelected: () {})],
          position: const Offset(40, 2),
        ),
      );
      await tester.pumpAndSettle();
      expect(
        tester.getTopLeft(panel).dy,
        greaterThanOrEqualTo(24 + 16),
        reason: 'never under the status bar',
      );
    });
  });

  testWidgets(
    '9. at 320dp wide the panel stays inside the gutters, each item is 48dp '
    'or taller, and a long label wraps without an ellipsis, at text 1.0 '
    'and 2.0',
    (tester) async {
      tester.view.physicalSize = const Size(320, 700);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      const label =
          'A reasonably long menu label to force wrapping onto '
          'two lines';
      for (final scale in [1.0, 2.0]) {
        late BuildContext context;
        await tester.pumpWidget(const SizedBox.shrink());
        await tester.pumpWidget(
          MaterialApp(
            debugShowCheckedModeBanner: false,
            theme: AppTheme.dark(),
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            builder: (ctx, child) => MediaQuery(
              data: MediaQuery.of(
                ctx,
              ).copyWith(textScaler: TextScaler.linear(scale)),
              child: child!,
            ),
            home: Scaffold(
              body: Builder(
                builder: (inner) {
                  context = inner;
                  return const SizedBox.expand();
                },
              ),
            ),
          ),
        );

        unawaited(
          showKitMenu(
            context,
            items: [KitMenuItem(label: label, onSelected: () {})],
            position: const Offset(300, 100),
          ),
        );
        await tester.pumpAndSettle();
        expect(tester.takeException(), isNull, reason: 'text $scale');

        final panel = tester.getRect(find.byKey(const ValueKey('kit-menu')));
        expect(panel.left, greaterThanOrEqualTo(16), reason: 'text $scale');
        expect(panel.right, lessThanOrEqualTo(320 - 16), reason: 'text $scale');

        expect(
          tester.getSize(_tileOf(label)).height,
          greaterThanOrEqualTo(48),
          reason: 'text $scale',
        );
        final text = tester.widget<Text>(find.text(label));
        expect(text.overflow, isNot(TextOverflow.ellipsis));
        expect(text.maxLines, isNull, reason: 'wraps, never cut');
        expect(text.softWrap, isNot(false));
        await tester.sendKeyEvent(LogicalKeyboardKey.escape);
        await tester.pumpAndSettle();
      }
    },
  );

  testWidgets(
    "10. onSelected that opens showKitConfirm runs after the menu route "
    "is gone",
    (tester) async {
      final log = _RouteLog();
      final context = await _pumpLoggedHost(tester, log);

      unawaited(
        showKitMenu(
          context,
          items: [
            KitMenuItem(
              label: 'Delete',
              destructive: true,
              onSelected: () {
                unawaited(
                  showKitConfirm(
                    context,
                    title: 'Delete fox?',
                    body: 'Removed from the server.',
                    confirmLabel: 'Delete conversation',
                  ),
                );
              },
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();
      final menuRoute = log.pushed.last;
      expect(menuRoute.isActive, isTrue);

      await tester.tap(find.text('Delete'));
      await tester.pumpAndSettle();

      expect(find.text('Delete fox?'), findsOneWidget);
      expect(menuRoute.isActive, isFalse);
    },
  );

  testWidgets(
    '11. reduced motion opens and closes within one pump with no ticker '
    'running',
    (tester) async {
      final context = await pumpKitHost(
        tester,
        effects: const KitEffects(motion: KitMotionLevel.off),
      );
      KitMenuItem? result;
      unawaited(
        showKitMenu(
          context,
          items: [KitMenuItem(label: 'Only', onSelected: () {})],
        ).then((v) => result = v),
      );
      await tester.pump();

      expect(find.text('Only'), findsOneWidget);
      expect(SchedulerBinding.instance.transientCallbackCount, 0);

      await tester.tap(find.text('Only'));
      await tester.pump();

      expect(find.text('Only'), findsNothing);
      expect(SchedulerBinding.instance.transientCallbackCount, 0);
      expect(result?.label, 'Only');
    },
  );

  testWidgets(
    '12. the retired KitMenuItem(label:, onSelected:, key:, destructive:, '
    'enabled:) constructor still compiles and behaves the same',
    (tester) async {
      final context = await pumpKitHost(tester);
      var ran = false;
      final item = KitMenuItem(
        label: 'Legacy',
        onSelected: () => ran = true,
        key: const ValueKey('legacy-item'),
        destructive: true,
        enabled: true,
      );
      unawaited(showKitMenu(context, items: [item]));
      await tester.pumpAndSettle();

      expect(find.text('Legacy'), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('legacy-item')));
      await tester.pumpAndSettle();

      expect(ran, isTrue);
    },
  );
}
