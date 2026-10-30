// Behaviour tests for KitSearchField and KitSearchNoMatch
// (docs/ux-system/kit-api/KitSearchField.md, "Tests required").
import 'kit_motion_still.dart';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit_buttons.dart';
import 'package:opencode_mobile/ui/kit/kit_menu.dart';
import 'package:opencode_mobile/ui/kit/kit_motion.dart';
import 'package:opencode_mobile/ui/kit/kit_search_field.dart';
import 'package:opencode_mobile/ui/kit/kit_tokens.dart';

const _fieldKey = ValueKey('field');
const _clearKey = ValueKey('clear');
const _filterKey = ValueKey('filter');

Widget _app(
  Widget child, {
  TextDirection direction = TextDirection.ltr,
  double textScale = 1,
  bool reduced = false,
}) => MaterialApp(
  theme: AppTheme.dark(),
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  builder: (context, inner) => MediaQuery(
    data: MediaQuery.of(context).copyWith(
      textScaler: TextScaler.linear(textScale),
      disableAnimations: reduced,
    ),
    child: Directionality(textDirection: direction, child: inner!),
  ),
  home: Scaffold(body: SafeArea(child: child)),
);

Widget _page(KitSearchField field) => Padding(
  padding: const EdgeInsets.all(16),
  child: Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    children: [
      field,
      TextButton(onPressed: () {}, child: const Text('First result')),
    ],
  ),
);

List<String> _captureAnnouncements() {
  final said = <String>[];
  TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
      .setMockDecodedMessageHandler<dynamic>(SystemChannels.accessibility, (
        message,
      ) async {
        final map = message as Map<Object?, Object?>;
        if (map['type'] == 'announce') {
          said.add(
            (map['data'] as Map<Object?, Object?>)['message']! as String,
          );
        }
        return null;
      });
  addTearDown(
    () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockDecodedMessageHandler<dynamic>(
          SystemChannels.accessibility,
          null,
        ),
  );
  return said;
}

/// A host that counts results like a real list would.
class _Host extends StatefulWidget {
  const _Host({this.partial = false, this.onChanged, this.onSubmitted});
  final bool partial;
  final ValueChanged<String>? onChanged;
  final ValueChanged<String>? onSubmitted;

  @override
  State<_Host> createState() => _HostState();
}

class _HostState extends State<_Host> {
  int? count;

  @override
  Widget build(BuildContext context) => _page(
    KitSearchField(
      label: 'Search settings',
      fieldKey: _fieldKey,
      clearKey: _clearKey,
      resultCount: count,
      partial: widget.partial,
      onSubmitted: widget.onSubmitted,
      onChanged: (q) {
        widget.onChanged?.call(q);
        setState(() => count = q.isEmpty ? null : 12);
      },
    ),
  );
}

void main() {
  kitMotionStillTests(
    'KitSearchField',
    builds: {
      'empty': () =>
          KitSearchField(label: 'Search projects', onChanged: (_) {}),
      'disabled': () => KitSearchField(
        label: 'Search projects',
        onChanged: (_) {},
        enabled: false,
        disabledReason: 'Connect first',
      ),
    },
    changes: {
      'filter applied': KitMotionChange(
        build: () =>
            KitSearchField(label: 'Search projects', onChanged: (_) {}),
        act: (tester, stage) => stage.rebuild(
          KitSearchField(
            label: 'Search projects',
            onChanged: (_) {},
            activeFilter: 'Archived',
            onClearFilter: () {},
          ),
        ),
        shows: 'Archived',
      ),
    },
  );
  kitMotionStillTests(
    'KitSearchNoMatch',
    builds: {
      'no matching projects': () =>
          KitSearchNoMatch(query: 'release', onClear: () {}),
    },
  );

  testWidgets('1. onChanged fires once after settling; clear fires at once', (
    tester,
  ) async {
    final calls = <String>[];
    await tester.pumpWidget(_app(_Host(onChanged: calls.add)));
    await tester.enterText(find.byKey(_fieldKey), 'o');
    await tester.pump(const Duration(milliseconds: 100));
    await tester.enterText(find.byKey(_fieldKey), 'op');
    await tester.pump(const Duration(milliseconds: 100));
    await tester.enterText(find.byKey(_fieldKey), 'opus');
    expect(calls, isEmpty);
    await tester.pump(KitMotion.typingSettle);
    expect(calls, ['opus']);
    await tester.tap(find.byKey(_clearKey));
    await tester.pump();
    expect(calls, ['opus', '']);
  });

  testWidgets('2. clear shows only with a query, 48 dp, keeps focus', (
    tester,
  ) async {
    await tester.pumpWidget(_app(const _Host()));
    expect(find.byKey(_clearKey), findsNothing);
    await tester.tap(find.byKey(_fieldKey));
    await tester.enterText(find.byKey(_fieldKey), 'dark');
    await tester.pumpAndSettle();
    expect(find.byKey(_clearKey), findsOneWidget);
    expect(find.byTooltip('Clear search'), findsOneWidget);
    final size = tester.getSize(find.byKey(_clearKey));
    expect(size.width, greaterThanOrEqualTo(48));
    expect(size.height, greaterThanOrEqualTo(48));
    await tester.tap(find.byKey(_clearKey));
    await tester.pumpAndSettle();
    expect(
      tester.widget<TextField>(find.byKey(_fieldKey)).controller!.text,
      '',
    );
    expect(find.byKey(_clearKey), findsNothing);
    final editable = tester.widget<TextField>(find.byKey(_fieldKey));
    expect(editable.focusNode!.hasFocus, isTrue);
  });

  testWidgets('3. Esc and back clear first, then leave', (tester) async {
    final navigator = GlobalKey<NavigatorState>();
    await tester.pumpWidget(
      MaterialApp(
        navigatorKey: navigator,
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: const Scaffold(body: Text('Home')),
      ),
    );
    Future<void> open() async {
      navigator.currentState!.push(
        // A modal route: an unconsumed Esc dismisses it.
        DialogRoute<void>(
          context: navigator.currentContext!,
          builder: (_) => Dialog(child: _page(_searchOnly())),
        ),
      );
      await tester.pumpAndSettle();
    }

    await open();
    await tester.tap(find.byKey(_fieldKey));
    await tester.enterText(find.byKey(_fieldKey), 'x');
    await tester.pumpAndSettle();
    await tester.sendKeyEvent(LogicalKeyboardKey.escape);
    await tester.pumpAndSettle();
    expect(find.byKey(_fieldKey), findsOneWidget, reason: 'Esc cleared only');
    expect(
      tester.widget<TextField>(find.byKey(_fieldKey)).controller!.text,
      '',
    );
    await tester.sendKeyEvent(LogicalKeyboardKey.escape);
    await tester.pumpAndSettle();
    expect(find.text('Home'), findsOneWidget, reason: 'empty Esc not consumed');

    await open();
    await tester.enterText(find.byKey(_fieldKey), 'y');
    await tester.pumpAndSettle();
    await tester.binding.handlePopRoute();
    await tester.pumpAndSettle();
    expect(find.byKey(_fieldKey), findsOneWidget, reason: 'back cleared only');
    expect(
      tester.widget<TextField>(find.byKey(_fieldKey)).controller!.text,
      '',
    );
    await tester.binding.handlePopRoute();
    await tester.pumpAndSettle();
    expect(find.text('Home'), findsOneWidget);
  });

  testWidgets('4. the count is announced once after settling', (tester) async {
    final said = _captureAnnouncements();
    await tester.pumpWidget(_app(const _Host()));
    for (final q in ['s', 'se', 'set']) {
      await tester.enterText(find.byKey(_fieldKey), q);
      await tester.pump(const Duration(milliseconds: 50));
    }
    expect(said, isEmpty);
    await tester.pump(KitMotion.typingSettle);
    await tester.pumpAndSettle();
    expect(said, ['12 results']);
    expect(find.text('12 results'), findsOneWidget);
  });

  testWidgets('4b. a partial count shows and announces so', (tester) async {
    final said = _captureAnnouncements();
    await tester.pumpWidget(_app(const _Host(partial: true)));
    await tester.enterText(find.byKey(_fieldKey), 'opus');
    await tester.pump(KitMotion.typingSettle);
    await tester.pumpAndSettle();
    const partial = '12 loaded · searching the server…';
    expect(find.text(partial), findsOneWidget);
    expect(said, [partial]);
  });

  testWidgets('5. KitSearchNoMatch: isolated query, clear, one action', (
    tester,
  ) async {
    final handle = tester.ensureSemantics();
    var cleared = 0;
    var searched = 0;
    await tester.pumpWidget(
      _app(
        KitSearchNoMatch(
          query: 'opus',
          what: 'models',
          onClear: () => cleared++,
          action: KitAction(
            label: 'Search all projects',
            onPressed: () => searched++,
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(
      find.text('Nothing in \u2068models\u2069 matches “\u2068opus\u2069”'),
      findsOneWidget,
    );
    await tester.tap(find.text('Clear search'));
    await tester.tap(find.text('Search all projects'));
    expect((cleared, searched), (1, 1));
    // Announced once when it replaces the list: one live region.
    expect(
      find.ancestor(
        of: find.textContaining('Nothing in'),
        matching: find.byWidgetPredicate(
          (w) => w is Semantics && w.properties.liveRegion == true,
        ),
      ),
      findsWidgets,
    );
    handle.dispose();
  });

  testWidgets('6. filters: labelled menu, chip removes, assert', (
    tester,
  ) async {
    // Compact: the filter is an icon button named "Filter".
    tester.view.physicalSize = const Size(412, 915);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    var chosen = '';
    var removed = 0;
    Widget field({String? active}) => _app(
      _page(
        KitSearchField(
          label: 'Search files',
          filterKey: _filterKey,
          onChanged: (_) {},
          filters: [
            KitMenuItem(label: 'Symbols', onSelected: () => chosen = 'S'),
            KitMenuItem(label: 'Files', onSelected: () => chosen = 'F'),
          ],
          activeFilter: active,
          onClearFilter: () => removed++,
        ),
      ),
    );
    await tester.pumpWidget(field());
    expect(find.byTooltip('Filter'), findsOneWidget);
    await tester.tap(find.byKey(_filterKey));
    await tester.pumpAndSettle();
    expect(find.text('Files'), findsOneWidget);
    await tester.tap(find.text('Symbols'));
    await tester.pumpAndSettle();
    expect(chosen, 'S');

    await tester.pumpWidget(field(active: 'Symbols'));
    await tester.pumpAndSettle();
    expect(find.text('Symbols'), findsOneWidget);
    expect(find.byTooltip('Filter: Symbols'), findsOneWidget);
    await tester.tap(find.byTooltip('Remove Symbols'));
    expect(removed, 1);

    expect(
      () => KitSearchField(
        label: 'x',
        onChanged: (_) {},
        activeFilter: 'Symbols',
      ),
      throwsAssertionError,
    );
  });

  testWidgets('7. disabled needs a reason and shows it', (tester) async {
    expect(
      () => KitSearchField(label: 'x', onChanged: (_) {}, enabled: false),
      throwsAssertionError,
    );
    await tester.pumpWidget(
      _app(
        _page(
          KitSearchField(
            label: 'Search sessions',
            fieldKey: _fieldKey,
            onChanged: (_) {},
            enabled: false,
            disabledReason: 'Connect to a server to search',
          ),
        ),
      ),
    );
    expect(find.text('Connect to a server to search'), findsOneWidget);
    expect(tester.widget<TextField>(find.byKey(_fieldKey)).enabled, isFalse);
  });

  testWidgets('8. Enter submits; Arrow Down moves to the first result', (
    tester,
  ) async {
    final submitted = <String>[];
    await tester.pumpWidget(_app(_Host(onSubmitted: submitted.add)));
    await tester.tap(find.byKey(_fieldKey));
    await tester.enterText(find.byKey(_fieldKey), 'dark');
    await tester.pumpAndSettle();
    await tester.testTextInput.receiveAction(TextInputAction.search);
    expect(submitted, ['dark']);
    // The search action hides the keyboard; focus the field again.
    await tester.tap(find.byKey(_fieldKey));
    await tester.pump();
    await tester.sendKeyEvent(LogicalKeyboardKey.arrowDown);
    await tester.pump();
    final focused = FocusManager.instance.primaryFocus!.context!;
    expect(
      find.descendant(
        of: find.byWidget(focused.widget),
        matching: find.text('First result'),
      ),
      findsOneWidget,
    );
  });

  testWidgets('8b. Enter before typing settles reports the query once', (
    tester,
  ) async {
    final changed = <String>[];
    final submitted = <String>[];
    await tester.pumpWidget(
      _app(_Host(onChanged: changed.add, onSubmitted: submitted.add)),
    );
    await tester.enterText(find.byKey(_fieldKey), 'dark');
    await tester.pump();
    expect(changed, isEmpty); // still waiting for typing to settle
    await tester.testTextInput.receiveAction(TextInputAction.search);
    // The host acts on what was typed at once, before its submit handler.
    expect(changed, ['dark']);
    expect(submitted, ['dark']);
    await tester.pumpAndSettle();
    await tester.pump(const Duration(seconds: 2));
    // The cancelled wait never reports the same query a second time.
    expect(changed, ['dark']);
  });

  testWidgets('9. RTL: magnifier at the start, clear at the end, no mirror', (
    tester,
  ) async {
    await tester.pumpWidget(_app(const _Host(), direction: TextDirection.rtl));
    await tester.enterText(find.byKey(_fieldKey), 'x');
    await tester.pumpAndSettle();
    final magnifier = find.byIcon(AppIconography.search);
    expect(
      tester.getCenter(magnifier).dx,
      greaterThan(tester.getCenter(find.byKey(_clearKey)).dx),
    );
    expect(AppIconography.search.matchTextDirection, isFalse);
  });

  test('10. worthShowing is above 8', () {
    expect(KitSearchField.worthShowing(8), isFalse);
    expect(KitSearchField.worthShowing(9), isTrue);
  });

  testWidgets('11. reduced motion: one pump settles', (tester) async {
    await tester.pumpWidget(_app(const _Host(), reduced: true));
    await tester.enterText(find.byKey(_fieldKey), 'x');
    await tester.pump();
    expect(tester.hasRunningAnimations, isFalse);
    await tester.pump(KitMotion.typingSettle);
    await tester.pump();
    expect(tester.hasRunningAnimations, isFalse);
  });

  testWidgets('12. no overflow across sizes, text scales and directions', (
    tester,
  ) async {
    addTearDown(tester.view.reset);
    for (final size in const [
      Size(320, 800),
      Size(915, 412),
      Size(1600, 1000),
    ]) {
      for (final scale in const [1.0, 1.3, 2.0]) {
        for (final dir in TextDirection.values) {
          tester.view.physicalSize = size;
          tester.view.devicePixelRatio = 1;
          await tester.pumpWidget(
            _app(
              _page(
                KitSearchField(
                  label: 'Search everything in this long list',
                  onChanged: (_) {},
                  controller: TextEditingController(text: 'opus model'),
                  resultCount: 1234,
                  partial: true,
                  filters: [KitMenuItem(label: 'Symbols', onSelected: () {})],
                  activeFilter: 'Symbols and definitions',
                  onClearFilter: () {},
                ),
              ),
              direction: dir,
              textScale: scale,
            ),
          );
          await tester.pumpAndSettle();
          expect(tester.takeException(), isNull, reason: '$size $scale $dir');
        }
      }
    }
  });

  testWidgets('prominent: a pill with no hairline until it has focus', (
    tester,
  ) async {
    await tester.pumpWidget(
      _app(
        _page(
          KitSearchField(
            label: 'Search settings',
            fieldKey: _fieldKey,
            prominent: true,
            onChanged: (_) {},
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    BoxDecoration frame() => tester
        .widgetList<DecoratedBox>(
          find.ancestor(
            of: find.byKey(_fieldKey),
            matching: find.byType(DecoratedBox),
          ),
        )
        .map((box) => box.decoration)
        .whereType<BoxDecoration>()
        .firstWhere((d) => d.border != null);
    final roles = KitTokens.of(tester.element(find.byKey(_fieldKey))).roles;
    final tokens = KitTokens.of(tester.element(find.byKey(_fieldKey)));
    expect(
      frame().borderRadius,
      BorderRadius.circular(tokens.buttonHeight / 2),
    );
    expect((frame().border! as Border).top.color, roles.surface1);
    await tester.tap(find.byKey(_fieldKey));
    await tester.pumpAndSettle();
    expect((frame().border! as Border).top.color, roles.accent);
  });
}

KitSearchField _searchOnly() => KitSearchField(
  label: 'Search settings',
  fieldKey: _fieldKey,
  onChanged: (_) {},
);
