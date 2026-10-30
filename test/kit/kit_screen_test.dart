// Behaviour tests for KitScreen v2 (docs/ux-system/kit-api/KitScreen.md
// "Tests required"; K2 §2.12, §2.7, §8.2). Owner decision 2026-09-27:
// Arabic is dropped, so the RTL check is one pane-order case, not a sweep.
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit_bottom_inset.dart';
import 'package:opencode_mobile/ui/kit/kit_buttons.dart';
import 'package:opencode_mobile/ui/kit/kit_layout.dart';
import 'package:opencode_mobile/ui/kit/kit_nav.dart';
import 'package:opencode_mobile/ui/kit/kit_page_route.dart';
import 'package:opencode_mobile/ui/kit/kit_progress.dart';
import 'package:opencode_mobile/ui/kit/kit_screen.dart';
import 'package:opencode_mobile/ui/kit/kit_search_field.dart';
import 'package:opencode_mobile/ui/kit/kit_status_line.dart';
import 'package:opencode_mobile/ui/kit/kit_status_slot.dart';
import 'package:opencode_mobile/ui/kit/kit_text.dart';
import 'package:opencode_mobile/ui/kit/kit_top_bar.dart';
import 'package:opencode_mobile/ui/kit/motion/kit_motion_parts.dart';

KitStatus _status(KitStatusKind kind, String message) => KitStatus(
  kind: kind,
  id: '${kind.name}:$message',
  icon: AppIconography.activity,
  message: message,
);

Widget _rows([int count = 3]) =>
    ListView(children: [for (var i = 0; i < count; i++) KitText('Row $i')]);

Future<void> _pump(
  WidgetTester tester,
  Widget home, {
  Size size = const Size(412, 915),
  TextDirection direction = TextDirection.ltr,
  List<KitStatus> appWide = const [],
  bool settle = true,
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  final conditions = ValueNotifier<List<KitStatus>>(appWide);
  addTearDown(conditions.dispose);
  await tester.pumpWidget(
    KitStatusScope(
      conditions: conditions,
      child: MaterialApp(
        debugShowCheckedModeBanner: false,
        theme: AppTheme.dark(),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        builder: (context, child) => Directionality(
          textDirection: direction,
          child: MediaQuery(
            data: MediaQuery.of(context).copyWith(disableAnimations: true),
            child: child!,
          ),
        ),
        home: home,
      ),
    ),
  );
  if (settle) {
    await tester.pumpAndSettle();
  } else {
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 500));
  }
}

KitTopBar _bar([String title = 'Page']) => KitTopBar(title: title);

void main() {
  testWidgets('a page blurs nothing behind its parts', (tester) async {
    await _pump(tester, KitScreen(topBar: _bar(), body: _rows()));
    // The bar and a chat's composer share one backdrop read (KitFrost):
    // the group is there, but nothing blurs at rest.
    expect(find.byType(BackdropFilter), findsNothing);
  });

  testWidgets('v1 parameters build as before, bottom keeps its key', (
    tester,
  ) async {
    await _pump(
      tester,
      Scaffold(
        body: KitScreen(
          header: const [KitText('Header')],
          loading: true,
          loadingLabel: 'Loading',
          body: _rows(),
          bottom: KitButton.primary(label: 'Go', onPressed: () {}),
        ),
      ),
      settle: false,
    );
    expect(find.text('Header'), findsOneWidget);
    expect(find.byType(KitLoadingBar), findsOneWidget);
    expect(find.byKey(const ValueKey('kit-screen-bottom')), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('order: bar, status, search, header, loading, body, bottom', (
    tester,
  ) async {
    await _pump(
      tester,
      KitScreen(
        topBar: _bar(),
        status: _status(KitStatusKind.info, 'Info line'),
        search: KitSearchField(label: 'Search rows', onChanged: (_) {}),
        header: const [KitText('Header row')],
        loading: true,
        body: _rows(),
        bottom: KitButton.primary(label: 'Go', onPressed: () {}),
      ),
      settle: false,
    );
    final bar = tester.getRect(find.text('Page'));
    final line = tester.getRect(find.text('Info line'));
    final search = tester.getRect(find.byType(KitSearchField));
    final header = tester.getRect(find.text('Header row'));
    final loading = tester.getRect(find.byType(KitLoadingBar));
    final body = tester.getRect(find.text('Row 0'));
    final bottom = tester.getRect(find.text('Go'));
    expect(bar.bottom, lessThanOrEqualTo(line.top));
    expect(line.bottom, lessThanOrEqualTo(search.top));
    expect(search.bottom, lessThanOrEqualTo(header.top));
    expect(header.bottom, lessThanOrEqualTo(loading.top));
    expect(loading.bottom, lessThanOrEqualTo(body.top));
    expect(body.bottom, lessThanOrEqualTo(bottom.top));
  });

  group('status slot', () {
    testWidgets('own status; app-wide connection wins; nothing when gone', (
      tester,
    ) async {
      await _pump(
        tester,
        KitScreen(
          topBar: _bar(),
          status: _status(KitStatusKind.work, 'Own line'),
          body: _rows(),
        ),
      );
      expect(find.text('Own line'), findsOneWidget);

      await _pump(
        tester,
        KitScreen(
          topBar: _bar(),
          status: _status(KitStatusKind.work, 'Own line'),
          body: _rows(),
        ),
        appWide: [_status(KitStatusKind.connection, 'Reconnecting')],
      );
      expect(find.text('Reconnecting'), findsOneWidget);
      expect(find.text('Own line'), findsNothing);

      await _pump(tester, KitScreen(topBar: _bar(), body: _rows()));
      expect(find.byType(KitStatusLine), findsNothing);
    });

    testWidgets('priority follows KitStatusKind order', (tester) async {
      final kinds = KitStatusKind.values;
      for (var i = 0; i < kinds.length - 1; i++) {
        await _pump(
          tester,
          KitScreen(
            topBar: _bar(),
            status: _status(kinds[i + 1], 'Lower'),
            body: _rows(),
          ),
          appWide: [_status(kinds[i], 'Higher')],
        );
        expect(find.text('Higher'), findsOneWidget, reason: '${kinds[i]}');
        expect(find.text('Lower'), findsNothing);
      }
    });

    testWidgets('a tab body contributes to the shell slot, draws none', (
      tester,
    ) async {
      await _pump(
        tester,
        KitScreen(
          topBar: _bar('Shell'),
          body: Column(
            children: [
              Expanded(
                child: KitScreen(
                  status: _status(KitStatusKind.info, 'Tab A line'),
                  body: _rows(1),
                ),
              ),
              Expanded(
                child: KitScreen(
                  status: _status(KitStatusKind.work, 'Tab B line'),
                  body: _rows(1),
                ),
              ),
            ],
          ),
        ),
      );
      expect(find.byType(KitStatusLine), findsOneWidget);
      expect(find.text('Tab B line'), findsOneWidget);
      expect(find.text('Tab A line'), findsNothing);
      // The line is the shell's: above both tab bodies.
      expect(
        tester.getRect(find.text('Tab B line')).bottom,
        lessThanOrEqualTo(tester.getRect(find.text('Row 0').first).top),
      );
    });

    testWidgets('an offstage tab stops contributing', (tester) async {
      Widget shell(bool onstage) => KitScreen(
        topBar: _bar('Shell'),
        body: TickerMode(
          enabled: onstage,
          child: KitScreen(
            status: _status(KitStatusKind.work, 'Tab line'),
            body: _rows(1),
          ),
        ),
      );
      await _pump(tester, shell(true));
      expect(find.text('Tab line'), findsOneWidget);
      await _pump(tester, shell(false));
      expect(find.text('Tab line'), findsNothing);
    });

    testWidgets('the drawn line is the one live region', (tester) async {
      final semantics = tester.ensureSemantics();
      await _pump(
        tester,
        KitScreen(
          topBar: _bar(),
          status: _status(KitStatusKind.connection, 'Offline now'),
          body: _rows(),
        ),
      );
      final live = find.byWidgetPredicate(
        (w) => w is Semantics && (w.properties.liveRegion ?? false),
      );
      expect(live, findsOneWidget);
      semantics.dispose();
    });
  });

  group('bottom block', () {
    testWidgets('lifts above the keyboard: bottom edge at height - 308', (
      tester,
    ) async {
      tester.view.viewInsets = const FakeViewPadding(bottom: 300);
      await _pump(
        tester,
        KitScreen(
          topBar: _bar(),
          body: _rows(),
          bottom: KitButton.primary(label: 'Save', onPressed: () {}),
        ),
      );
      final button = find.ancestor(
        of: find.text('Save'),
        matching: find.byType(KitButton),
      );
      expect(tester.getRect(button).bottom, 915 - 308);
    });

    testWidgets('publishes its height; endPadding clears it', (tester) async {
      late BuildContext inner;
      await _pump(
        tester,
        Scaffold(
          body: KitBottomInset.add(
            extraBottom: 40,
            child: KitScreen(
              body: Builder(
                builder: (context) {
                  inner = context;
                  return _rows();
                },
              ),
              bottom: KitButton.primary(label: 'Save', onPressed: () {}),
            ),
          ),
        ),
      );
      final block = tester.getRect(
        find.byKey(const ValueKey('kit-screen-bottom')),
      );
      // The block's own height, without the lift under it (40).
      final measured = block.height - 40;
      expect(KitBottomInset.of(inner).bottom, 40 + measured);
      expect(KitScreen.endPadding(inner), 16 + 40 + measured);
    });
  });

  group('width', () {
    Future<double> bodyWidth(
      WidgetTester tester,
      KitScreenWidth width,
      Size size,
    ) async {
      await _pump(
        tester,
        KitScreen(
          topBar: _bar(),
          width: width,
          body: const SizedBox.expand(key: ValueKey('body')),
        ),
        size: size,
      );
      return tester.getSize(find.byKey(const ValueKey('body'))).width;
    }

    testWidgets('1280: reading 720, list 960, full the whole width', (
      tester,
    ) async {
      const size = Size(1280, 800);
      expect(await bodyWidth(tester, KitScreenWidth.reading, size), 720);
      final list = tester.getRect(find.byKey(const ValueKey('body')));
      expect(list.center.dx, 640);
      expect(await bodyWidth(tester, KitScreenWidth.list, size), 960);
      expect(await bodyWidth(tester, KitScreenWidth.full, size), 1280);
    });

    testWidgets('412: every width is full, padding has 16 gutters', (
      tester,
    ) async {
      const size = Size(412, 915);
      for (final width in KitScreenWidth.values) {
        expect(await bodyWidth(tester, width, size), 412);
      }
      final context = tester.element(find.byKey(const ValueKey('body')));
      final padding = KitScreen.padding(context);
      expect(padding.start, 16);
      expect(padding.end, 16);
      expect(padding.bottom, KitScreen.endPadding(context));
    });
  });

  group('twoPane', () {
    Widget two({Widget? detail, VoidCallback? onSelect}) => KitScreen.twoPane(
      topBar: _bar('Conversations'),
      list: Builder(
        builder: (context) => ListView(
          children: [
            KitButton.secondary(
              label: 'Open row',
              onPressed: () async {
                final result = await KitScreen.openDetail<String>(
                  context,
                  select: onSelect ?? () {},
                  page: (_) => const _DetailPage(),
                );
                if (result != null) _lastResult = result;
              },
            ),
          ],
        ),
      ),
      detail: detail,
      emptyDetail: const Center(child: KitText('Pick a conversation')),
      listPaneKey: const ValueKey('list-pane'),
      detailPaneKey: const ValueKey('detail-pane'),
    );

    testWidgets('412: list only; openDetail pushes a KitPageRoute', (
      tester,
    ) async {
      _lastResult = null;
      var selected = 0;
      await _pump(tester, two(onSelect: () => selected++));
      expect(find.text('Pick a conversation'), findsNothing);
      await tester.tap(find.text('Open row'));
      await tester.pumpAndSettle();
      expect(selected, 0);
      expect(find.text('Detail page'), findsOneWidget);
      expect(
        ModalRoute.of(tester.element(find.text('Detail page'))),
        isA<KitPageRoute<String>>(),
      );
      await tester.tap(find.text('Done'));
      await tester.pumpAndSettle();
      expect(_lastResult, 'done');
    });

    testWidgets('1280: list 296, detail at most 700, select not push', (
      tester,
    ) async {
      var selected = 0;
      await _pump(
        tester,
        two(onSelect: () => selected++),
        size: const Size(1280, 800),
      );
      expect(
        tester.getSize(find.byKey(const ValueKey('list-pane'))).width,
        KitLayout.paneListWidth,
      );
      expect(find.text('Pick a conversation'), findsOneWidget);
      await tester.tap(find.text('Open row'));
      await tester.pumpAndSettle();
      expect(selected, 1);
      expect(find.text('Detail page'), findsNothing);

      await _pump(
        tester,
        two(detail: const SizedBox.expand(key: ValueKey('detail'))),
        size: const Size(1280, 800),
      );
      expect(find.text('Pick a conversation'), findsNothing);
      expect(
        tester.getSize(find.byKey(const ValueKey('detail'))).width,
        lessThanOrEqualTo(KitLayout.paneDetailMaxWidth),
      );
    });

    testWidgets('915x412 (short): list only', (tester) async {
      await _pump(tester, two(), size: const Size(915, 412));
      expect(find.byKey(const ValueKey('detail-pane')), findsNothing);
      expect(find.text('Open row'), findsOneWidget);
    });

    testWidgets('a detail pane is a pane: KitScreen.inPane and a nested page', (
      tester,
    ) async {
      await _pump(
        tester,
        two(
          detail: KitScreen(
            topBar: _bar('Detail'),
            body: const SizedBox.expand(key: ValueKey('detail')),
          ),
        ),
        size: const Size(1280, 800),
      );
      expect(tester.takeException(), isNull);
      expect(KitScreen.inPane(tester.element(find.text('Detail'))), isTrue);
      expect(KitScreen.inPane(tester.element(find.text('Open row'))), isFalse);
    });

    testWidgets('inside a KitNav sidebar that hosts the pane: no list pane', (
      tester,
    ) async {
      await _pump(
        tester,
        KitNav(
          destinations: const [
            KitNavDestination(
              label: 'Work',
              icon: AppIconography.workspace,
              pane: _sidebarList,
            ),
            KitNavDestination(label: 'Settings', icon: AppIconography.activity),
          ],
          selected: 0,
          onSelected: (_) {},
          child: two(detail: const SizedBox.expand(key: ValueKey('detail'))),
        ),
        size: const Size(1280, 800),
      );
      expect(find.byKey(const ValueKey('list-pane')), findsNothing);
      expect(find.byKey(const ValueKey('detail')), findsOneWidget);
    });

    testWidgets('behind a sidebar that holds no list, panes follow the body', (
      tester,
    ) async {
      Widget inNav() => KitNav(
        destinations: const [
          KitNavDestination(label: 'Work', icon: AppIconography.workspace),
          KitNavDestination(label: 'Settings', icon: AppIconography.activity),
        ],
        selected: 0,
        onSelected: (_) {},
        child: two(detail: const SizedBox.expand(key: ValueKey('detail'))),
      );
      // 840 - 296 sidebar - 296 list leaves 248: one column, not a squeeze.
      await _pump(tester, inNav(), size: const Size(840, 800));
      expect(find.byKey(const ValueKey('list-pane')), findsNothing);
      expect(find.byKey(const ValueKey('detail')), findsNothing);
      // 1200 - 296 - 296 leaves 608: both panes, the detail wide enough.
      await _pump(tester, inNav(), size: const Size(1200, 800));
      expect(find.byKey(const ValueKey('list-pane')), findsOneWidget);
      expect(
        tester.getSize(find.byKey(const ValueKey('detail'))).width,
        greaterThanOrEqualTo(KitLayout.paneDetailMinWidth),
      );
    });

    testWidgets('RTL: the list pane is on the right', (tester) async {
      await _pump(
        tester,
        two(),
        size: const Size(1280, 800),
        direction: TextDirection.rtl,
      );
      expect(
        tester.getRect(find.byKey(const ValueKey('list-pane'))).right,
        1280,
      );
    });
  });

  group('threePane', () {
    Widget three() => KitScreen.threePane(
      list: _rows(),
      detail: null,
      emptyDetail: const Center(child: KitText('Pick one')),
      side: const SizedBox.expand(key: ValueKey('side')),
      sidePaneKey: const ValueKey('side-pane'),
      detailPaneKey: const ValueKey('detail-pane'),
    );

    // KitScreen.md test 9 says "absent at 1280×800", but 1280 is the large
    // class (§8.1: large ≥ 1200) and the spec's Adaptive section shows the
    // side pane on large ("between 1200 and 1336 dp the detail takes the
    // rest"). Reported as a spec problem; the expanded 1100 case is tested.
    testWidgets('side 340 on large, absent on expanded', (tester) async {
      await _pump(tester, three(), size: const Size(1600, 1000));
      expect(
        tester.getSize(find.byKey(const ValueKey('side'))).width,
        KitLayout.paneSideWidth,
      );
      expect(
        KitScreen.showsSide(tester.element(find.text('Pick one'))),
        isTrue,
      );
      await _pump(tester, three(), size: const Size(1280, 800));
      expect(find.byKey(const ValueKey('side')), findsOneWidget);
      expect(
        tester.getSize(find.byKey(const ValueKey('detail-pane'))).width,
        lessThan(KitLayout.paneDetailMaxWidth),
      );
      await _pump(tester, three(), size: const Size(1100, 800));
      expect(find.byKey(const ValueKey('side')), findsNothing);
      expect(
        KitScreen.showsSide(tester.element(find.text('Pick one'))),
        isFalse,
      );
    });
  });

  group('one of each (debug)', () {
    testWidgets('two visible primaries assert', (tester) async {
      await _pump(
        tester,
        KitScreen(
          topBar: _bar(),
          body: ListView(
            children: [KitButton.primary(label: 'One', onPressed: () {})],
          ),
          bottom: KitButton.primary(label: 'Two', onPressed: () {}),
        ),
      );
      expect(tester.takeException(), isA<AssertionError>());
    });

    testWidgets('two drawn status lines assert', (tester) async {
      await _pump(
        tester,
        KitScreen(
          topBar: _bar(),
          status: _status(KitStatusKind.info, 'Slot line'),
          header: [KitStatusLine.of(_status(KitStatusKind.info, 'Hand line'))],
          body: _rows(),
        ),
      );
      expect(tester.takeException(), isA<AssertionError>());
    });

    testWidgets('a page nested in a body asserts', (tester) async {
      await _pump(
        tester,
        KitScreen(
          topBar: _bar('Outer'),
          body: KitScreen(topBar: _bar('Inner'), body: _rows()),
        ),
      );
      expect(tester.takeException(), isA<AssertionError>());
    });

    testWidgets('a primary a KitSwap is fading out does not count', (
      tester,
    ) async {
      // A page that swaps one state for another (phone setup's start
      // screen) briefly draws both; the leaving one is not the page's.
      final state = ValueNotifier(0);
      addTearDown(state.dispose);
      await tester.pumpWidget(
        MaterialApp(
          theme: AppTheme.dark(),
          // The page rebuilds with its state, as a screen's builder does,
          // so its one-of-each check runs mid-swap.
          home: ValueListenableBuilder<int>(
            valueListenable: state,
            builder: (context, value, _) => KitScreen(
              topBar: _bar(),
              body: KitSwap(
                child: KeyedSubtree(
                  key: ValueKey(value),
                  child: KitButton.primary(
                    label: 'State $value',
                    onPressed: () {},
                  ),
                ),
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      state.value = 1;
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 16));
      // Both are drawn mid-swap; only the arriving one counts.
      expect(find.text('State 0'), findsOneWidget);
      expect(find.text('State 1'), findsOneWidget);
      expect(tester.takeException(), isNull);
      await tester.pumpAndSettle();
      expect(find.text('State 0'), findsNothing);
      expect(tester.takeException(), isNull);
    });

    testWidgets('a primary in an offstage tab does not count', (tester) async {
      await _pump(
        tester,
        KitScreen(
          topBar: _bar(),
          body: Offstage(
            child: KitButton.primary(label: 'Hidden', onPressed: () {}),
          ),
          bottom: KitButton.primary(label: 'Shown', onPressed: () {}),
        ),
      );
      expect(tester.takeException(), isNull);
    });
  });

  testWidgets('a tab snack bar shows inside the shell', (tester) async {
    // The shell (HomeScreen) is a KitScreen page inside KitNav with no
    // Scaffold; tabs that still call ScaffoldMessenger.showSnackBar must
    // show their snack bar there, floating above the glass dock.
    late BuildContext tabContext;
    await _pump(
      tester,
      KitNav(
        destinations: const [
          KitNavDestination(label: 'Work', icon: AppIconography.workspace),
          KitNavDestination(label: 'Settings', icon: AppIconography.activity),
        ],
        selected: 0,
        onSelected: (_) {},
        child: KitScreen(
          topBar: _bar('Shell'),
          body: Builder(
            builder: (context) {
              tabContext = context;
              return _rows();
            },
          ),
        ),
      ),
    );
    ScaffoldMessenger.of(
      tabContext,
    ).showSnackBar(const SnackBar(content: Text('Copied')));
    await tester.pumpAndSettle();

    final snack = find.text('Copied');
    expect(snack, findsOneWidget);
    expect(tester.getRect(snack).height, greaterThan(0));
    expect(
      tester.getRect(find.byType(SnackBar)).bottom,
      // No dock on a phone (owner, 2026-10-20): inside the window.
      lessThanOrEqualTo(
        tester.view.physicalSize.height / tester.view.devicePixelRatio,
      ),
    );
    // The page itself keeps its own MediaQuery: its body is not lifted.
    expect(tester.getRect(find.text('Row 0')).top, lessThan(200));
  });

  testWidgets('no overflow across sizes and text scales', (tester) async {
    const sizes = [
      Size(320, 640),
      Size(360, 800),
      Size(412, 915),
      Size(600, 900),
      Size(840, 900),
      Size(1280, 800),
      Size(1600, 1000),
      Size(915, 412),
    ];
    for (final size in sizes) {
      for (final scale in [1.0, 1.3, 2.0]) {
        tester.view.physicalSize = size;
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.reset);
        await tester.pumpWidget(
          MaterialApp(
            theme: AppTheme.dark(),
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            builder: (context, child) => MediaQuery(
              data: MediaQuery.of(context).copyWith(
                textScaler: TextScaler.linear(scale),
                disableAnimations: true,
              ),
              child: child!,
            ),
            home: KitScreen(
              topBar: _bar(),
              status: _status(KitStatusKind.info, 'A line'),
              search: KitSearchField(label: 'Search', onChanged: (_) {}),
              body: _rows(),
              bottom: KitButton.primary(label: 'Go', onPressed: () {}),
            ),
          ),
        );
        await tester.pumpAndSettle();
        expect(tester.takeException(), isNull, reason: '$size × $scale');
      }
    }
  });

  test('G15: kit_screen.dart compares no width to a literal', () {
    final source = File('lib/ui/kit/kit_screen.dart').readAsStringSync();
    expect(RegExp(r'width\s*[<>]=?\s*\d').hasMatch(source), isFalse);
  });
}

String? _lastResult;

class _DetailPage extends StatelessWidget {
  const _DetailPage();

  @override
  Widget build(BuildContext context) => KitScreen(
    topBar: _bar('Detail'),
    body: const Center(child: KitText('Detail page')),
    bottom: KitButton.primary(
      label: 'Done',
      onPressed: () => Navigator.of(context).pop('done'),
    ),
  );
}

Widget _sidebarList(BuildContext context) => const KitText('Sidebar list');
