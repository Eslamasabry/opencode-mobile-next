import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

/// Motion across the app (design standard §10, slice E): one page
/// transition, a tab switch that fades through, parts that arrive and
/// leave, rows that unfold and fold, a working button that crossfades, a
/// drawn pull to refresh and haptics on send and on a finish. Every one is
/// instant under reduced motion and leaves nothing running on a resting
/// screen.
Widget _app(
  Widget home, {
  bool reduced = false,
  ThemeData? theme,
  TargetPlatform platform = TargetPlatform.android,
}) => MaterialApp(
  theme: (theme ?? AppTheme.dark()).copyWith(platform: platform),
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  builder: (context, child) => MediaQuery(
    data: MediaQuery.of(context).copyWith(disableAnimations: reduced),
    child: child!,
  ),
  home: home,
);

/// The opacity painted over [finder]: the product of every Opacity and
/// FadeTransition above it, up to the nearest route.
double _opacityOf(WidgetTester tester, Finder finder) {
  var opacity = 1.0;
  final element = tester.element(finder);
  element.visitAncestorElements((ancestor) {
    final widget = ancestor.widget;
    if (widget is Opacity) opacity *= widget.opacity;
    if (widget is FadeTransition) opacity *= widget.opacity.value;
    return true;
  });
  return opacity;
}

List<MethodCall> _haptics(WidgetTester tester) {
  final calls = <MethodCall>[];
  tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
    SystemChannels.platform,
    (call) async {
      if (call.method == 'HapticFeedback.vibrate') calls.add(call);
      return null;
    },
  );
  addTearDown(
    () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      SystemChannels.platform,
      null,
    ),
  );
  return calls;
}

class _Opener extends StatelessWidget {
  const _Opener();

  @override
  Widget build(BuildContext context) => Scaffold(
    body: Center(
      child: TextButton(
        onPressed: () => Navigator.of(context).push(
          MaterialPageRoute<void>(
            builder: (_) => const Scaffold(body: Center(child: Text('Next'))),
          ),
        ),
        child: const Text('Open'),
      ),
    ),
  );
}

class _Probe extends StatefulWidget {
  const _Probe(this.label, {super.key});
  final String label;

  @override
  State<_Probe> createState() => _ProbeState();
}

class _ProbeState extends State<_Probe> {
  @override
  Widget build(BuildContext context) =>
      SizedBox(height: 48, child: Text(widget.label));
}

void main() {
  group('page transitions', () {
    test('Android moves the Android way; the rest but iOS use the kit '
        'transition', () {
      for (final theme in [AppTheme.dark(), AppTheme.light()]) {
        final builders = theme.pageTransitionsTheme.builders;
        // Owner rule 2026-10-26: native Android motion, predictive back.
        expect(
          builders[TargetPlatform.android],
          isA<KitAndroidPageTransitionsBuilder>(),
        );
        for (final platform in [
          TargetPlatform.linux,
          TargetPlatform.windows,
          TargetPlatform.macOS,
          TargetPlatform.fuchsia,
        ]) {
          expect(builders[platform], isA<KitPageTransitionsBuilder>());
        }
      }
      expect(
        const KitPageTransitionsBuilder().transitionDuration,
        KitMotion.standard,
      );
    });

    testWidgets('a page fades through: the old one clears before the new '
        'one slides in, and both rest still (off Android)', (tester) async {
      await tester.pumpWidget(
        _app(const _Opener(), platform: TargetPlatform.fuchsia),
      );
      await tester.tap(find.text('Open'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 40));
      // 16 % in: the page underneath is fading, the new one waits.
      expect(_opacityOf(tester, find.text('Next')), 0);
      final leaving = _opacityOf(tester, find.text('Open'));
      expect(leaving, inExclusiveRange(0, 1));

      await tester.pump(const Duration(milliseconds: 100));
      // 56 % in: the old page is gone, the new one fades in from the end
      // side, still short of its place.
      expect(_opacityOf(tester, find.text('Open')), 0);
      expect(_opacityOf(tester, find.text('Next')), inExclusiveRange(0, 1));
      final settledX = tester.getCenter(find.text('Next')).dx;
      await tester.pumpAndSettle();
      final restX = tester.getCenter(find.text('Next')).dx;
      expect(settledX, greaterThan(restX));
      expect(settledX - restX, lessThanOrEqualTo(30));
      expect(_opacityOf(tester, find.text('Next')), 1);
      expect(tester.binding.transientCallbackCount, 0);

      // Back plays the same in reverse.
      tester.state<NavigatorState>(find.byType(Navigator)).pop();
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 40));
      expect(_opacityOf(tester, find.text('Next')), inExclusiveRange(0, 1));
      await tester.pumpAndSettle();
      expect(find.text('Next'), findsNothing);
      expect(_opacityOf(tester, find.text('Open')), 1);
    });

    testWidgets('reduced motion shows the new page at once', (tester) async {
      await tester.pumpWidget(_app(const _Opener(), reduced: true));
      await tester.tap(find.text('Open'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 16));
      expect(_opacityOf(tester, find.text('Next')), 1);
      final x = tester.getCenter(find.text('Next')).dx;
      await tester.pumpAndSettle();
      expect(tester.getCenter(find.text('Next')).dx, x);
    });
  });

  group('tab switch', () {
    Widget tabs(ValueNotifier<int> selected, {bool reduced = false}) => _app(
      Scaffold(
        body: ValueListenableBuilder<int>(
          valueListenable: selected,
          builder: (context, index, _) => KitTabSwitcher(
            index: index,
            children: const [
              _Probe('Work tab', key: ValueKey('work')),
              _Probe('Inbox tab', key: ValueKey('inbox')),
            ],
          ),
        ),
      ),
      reduced: reduced,
      // The kit's own page transition around the tabs (off Android).
      platform: TargetPlatform.fuchsia,
    );

    testWidgets('the tab being left clears before the chosen one fades in, '
        'and both keep their state', (tester) async {
      final selected = ValueNotifier(0);
      addTearDown(selected.dispose);
      await tester.pumpWidget(tabs(selected));
      final work = tester.state(find.byKey(const ValueKey('work')));

      selected.value = 1;
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 40));
      // Never both half-visible: the chosen tab waits while Work leaves.
      expect(_opacityOf(tester, find.text('Inbox tab')), 0);
      expect(_opacityOf(tester, find.text('Work tab')), inExclusiveRange(0, 1));
      // The chosen tab takes touches from the first frame.
      expect(find.text('Inbox tab').hitTestable(), findsOneWidget);

      await tester.pump(const Duration(milliseconds: 60));
      expect(find.text('Work tab'), findsNothing); // offstage once clear
      expect(
        _opacityOf(tester, find.text('Inbox tab')),
        inExclusiveRange(0, 1),
      );
      await tester.pumpAndSettle();
      expect(_opacityOf(tester, find.text('Inbox tab')), 1);
      expect(
        tester.widget<Transform>(
          find
              .ancestor(
                of: find.text('Inbox tab'),
                matching: find.byType(Transform),
              )
              .first,
        ),
        isA<Transform>().having(
          (t) => t.transform.getMaxScaleOnAxis(),
          'scale',
          1,
        ),
      );

      selected.value = 0;
      await tester.pumpAndSettle();
      expect(tester.state(find.byKey(const ValueKey('work'))), same(work));
      expect(tester.binding.transientCallbackCount, 0);
    });

    testWidgets('reduced motion switches in the next frame', (tester) async {
      final selected = ValueNotifier(0);
      addTearDown(selected.dispose);
      await tester.pumpWidget(tabs(selected, reduced: true));
      selected.value = 1;
      await tester.pump();
      expect(find.text('Work tab'), findsNothing);
      expect(_opacityOf(tester, find.text('Inbox tab')), 1);
      expect(tester.binding.transientCallbackCount, 0);
    });
  });

  group('parts arrive and leave', () {
    Widget host(ValueNotifier<bool> shown, {bool reduced = false}) => _app(
      Scaffold(
        body: ValueListenableBuilder<bool>(
          valueListenable: shown,
          builder: (context, on, _) => Column(
            children: [
              KitReveal(
                child: on
                    ? const KitNotice(message: 'Saved', tone: AppStatusTone.ok)
                    : null,
              ),
              const Text('Below'),
            ],
          ),
        ),
      ),
      reduced: reduced,
    );

    testWidgets('a notice unfolds in, settles, and folds away when it goes', (
      tester,
    ) async {
      final shown = ValueNotifier(false);
      addTearDown(shown.dispose);
      await tester.pumpWidget(host(shown));
      final restY = tester.getTopLeft(find.text('Below')).dy;

      shown.value = true;
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));
      final midY = tester.getTopLeft(find.text('Below')).dy;
      expect(_opacityOf(tester, find.text('Saved')), inExclusiveRange(0, 1));
      await tester.pumpAndSettle();
      final openY = tester.getTopLeft(find.text('Below')).dy;
      // The content under it moves down smoothly, not in one frame.
      expect(midY, inExclusiveRange(restY, openY));
      expect(_opacityOf(tester, find.text('Saved')), 1);
      expect(tester.binding.transientCallbackCount, 0);

      shown.value = false;
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));
      // Leaving: still painted, but no longer touchable or announced.
      expect(find.text('Saved'), findsOneWidget);
      expect(find.text('Saved').hitTestable(), findsNothing);
      await tester.pumpAndSettle();
      expect(find.text('Saved'), findsNothing);
      expect(tester.getTopLeft(find.text('Below')).dy, restY);
    });

    testWidgets('the first build and reduced motion show it at once', (
      tester,
    ) async {
      final shown = ValueNotifier(true);
      addTearDown(shown.dispose);
      await tester.pumpWidget(host(shown, reduced: true));
      expect(_opacityOf(tester, find.text('Saved')), 1);
      shown.value = false;
      await tester.pump();
      expect(find.text('Saved'), findsNothing);
      shown.value = true;
      await tester.pump();
      expect(_opacityOf(tester, find.text('Saved')), 1);
      expect(tester.binding.transientCallbackCount, 0);
    });

    testWidgets('a notice shown on its own fades in once and settles', (
      tester,
    ) async {
      await tester.pumpWidget(
        _app(const Scaffold(body: KitNotice(message: 'Checked'))),
      );
      await tester.pump(const Duration(milliseconds: 50));
      expect(_opacityOf(tester, find.text('Checked')), inExclusiveRange(0, 1));
      await tester.pumpAndSettle();
      expect(_opacityOf(tester, find.text('Checked')), 1);
      expect(tester.binding.transientCallbackCount, 0);
    });

    testWidgets('a status line settles too', (tester) async {
      await tester.pumpWidget(
        _app(
          const Scaffold(
            body: KitStatusLine(icon: Icons.cloud_off, message: 'Offline'),
          ),
        ),
      );
      await tester.pump(const Duration(milliseconds: 50));
      expect(_opacityOf(tester, find.text('Offline')), inExclusiveRange(0, 1));
      await tester.pumpAndSettle();
      expect(_opacityOf(tester, find.text('Offline')), 1);
    });
  });

  group('state view', () {
    Widget state(
      ValueNotifier<(AppStatusTone, String)> value, {
      bool reduced = false,
    }) => _app(
      Scaffold(
        body: ValueListenableBuilder<(AppStatusTone, String)>(
          valueListenable: value,
          builder: (context, v, _) => KitStateView(
            icon: v.$1 == AppStatusTone.ok ? Icons.check : Icons.sync,
            tone: v.$1,
            title: v.$2,
            details: 'http://127.0.0.1:4096',
          ),
        ),
      ),
      reduced: reduced,
    );

    testWidgets('a new state arrives with a fade; new words in the same '
        'state do not replay it', (tester) async {
      final value = ValueNotifier((AppStatusTone.progress, 'Starting…'));
      addTearDown(value.dispose);
      final haptics = _haptics(tester);
      await tester.pumpWidget(state(value));
      await tester.pumpAndSettle();
      expect(haptics, isEmpty, reason: 'nothing finished on mount');

      value.value = (AppStatusTone.progress, 'Starting… 3 s');
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 50));
      expect(_opacityOf(tester, find.text('Starting… 3 s')), 1);

      value.value = (AppStatusTone.ok, 'Ready');
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 50));
      expect(_opacityOf(tester, find.text('Ready')), inExclusiveRange(0, 1));
      await tester.pumpAndSettle();
      expect(_opacityOf(tester, find.text('Ready')), 1);
      // Working turned into finished while the person watched.
      expect(haptics.map((call) => call.arguments), [
        'HapticFeedbackType.successNotification',
      ]);
    });

    testWidgets('details unfold and fold', (tester) async {
      final value = ValueNotifier((AppStatusTone.failure, 'Not answering'));
      addTearDown(value.dispose);
      await tester.pumpWidget(state(value));
      await tester.pumpAndSettle();
      final details = find.byKey(const ValueKey('kit-state-details-text'));
      await tester.tap(find.byKey(const ValueKey('kit-state-details')));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));
      final mid = tester.getSize(find.byType(KitReveal)).height;
      await tester.pumpAndSettle();
      final open = tester.getSize(find.byType(KitReveal)).height;
      expect(mid, inExclusiveRange(0, open));
      await tester.tap(find.byKey(const ValueKey('kit-state-details')));
      await tester.pumpAndSettle();
      expect(details, findsNothing);
    });

    testWidgets('reduced motion: no fade and no finish haptic', (tester) async {
      final value = ValueNotifier((AppStatusTone.progress, 'Starting…'));
      addTearDown(value.dispose);
      final haptics = _haptics(tester);
      await tester.pumpWidget(state(value, reduced: true));
      expect(_opacityOf(tester, find.text('Starting…')), 1);
      value.value = (AppStatusTone.ok, 'Ready');
      await tester.pump();
      expect(_opacityOf(tester, find.text('Ready')), 1);
      expect(haptics, isEmpty);
      expect(tester.binding.transientCallbackCount, 0);
    });
  });

  group('rows', () {
    Widget rows(ValueNotifier<List<String>> ids, {bool reduced = false}) =>
        _app(
          Scaffold(
            body: ValueListenableBuilder<List<String>>(
              valueListenable: ids,
              builder: (context, list, _) => KitAnimatedRows(
                children: [
                  for (final id in list) _Probe(id, key: ValueKey(id)),
                ],
              ),
            ),
          ),
          reduced: reduced,
        );

    testWidgets('a new row unfolds in, a gone row folds away where it was, '
        'rows that stay keep their state', (tester) async {
      final ids = ValueNotifier(['a', 'b', 'c']);
      addTearDown(ids.dispose);
      await tester.pumpWidget(rows(ids));
      // The first build shows the rows at once.
      expect(_opacityOf(tester, find.text('a')), 1);
      expect(tester.binding.transientCallbackCount, 0);
      final b = tester.state(find.byKey(const ValueKey('b')));

      ids.value = ['new', 'a', 'b', 'c'];
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));
      final aMid = tester.getTopLeft(find.text('a')).dy;
      expect(aMid, inExclusiveRange(0, 48));
      await tester.pumpAndSettle();
      expect(tester.getTopLeft(find.text('a')).dy, 48);
      expect(_opacityOf(tester, find.text('new')), 1);

      ids.value = ['new', 'a', 'c'];
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));
      // Still where it was, folding; not touchable.
      expect(find.text('b'), findsOneWidget);
      expect(find.text('b').hitTestable(), findsNothing);
      final cMid = tester.getTopLeft(find.text('c')).dy;
      expect(cMid, inExclusiveRange(96, 144));
      await tester.pumpAndSettle();
      expect(find.text('b'), findsNothing);
      expect(tester.getTopLeft(find.text('c')).dy, 96);

      // A row that stays keeps its state across the changes.
      ids.value = ['b', 'new', 'a', 'c'];
      await tester.pumpAndSettle();
      expect(tester.state(find.byKey(const ValueKey('b'))), isNot(same(b)));
      final a = tester.state(find.byKey(const ValueKey('a')));
      ids.value = ['b', 'a', 'c'];
      await tester.pumpAndSettle();
      expect(tester.state(find.byKey(const ValueKey('a'))), same(a));
      expect(tester.binding.transientCallbackCount, 0);
    });

    testWidgets('reduced motion: rows come and go at once', (tester) async {
      final ids = ValueNotifier(['a']);
      addTearDown(ids.dispose);
      await tester.pumpWidget(rows(ids, reduced: true));
      ids.value = ['a', 'b'];
      await tester.pump();
      expect(tester.getTopLeft(find.text('b')).dy, 48);
      ids.value = ['b'];
      await tester.pump();
      expect(find.text('a'), findsNothing);
      expect(tester.getTopLeft(find.text('b')).dy, 0);
      expect(tester.binding.transientCallbackCount, 0);
    });
  });

  group('working button', () {
    Widget button(
      ValueNotifier<bool> working, {
      IconData? icon,
      bool reduced = false,
    }) => _app(
      Scaffold(
        body: Center(
          child: ValueListenableBuilder<bool>(
            valueListenable: working,
            builder: (context, on, _) => KitButton.primary(
              label: 'Create',
              icon: icon,
              expand: false,
              working: on,
              onPressed: () {},
            ),
          ),
        ),
      ),
      reduced: reduced,
    );

    testWidgets('reduced motion: the spinner shows at once, without error', (
      tester,
    ) async {
      final working = ValueNotifier(false);
      addTearDown(working.dispose);
      await tester.pumpWidget(button(working, reduced: true));
      working.value = true;
      await tester.pump();
      expect(tester.takeException(), isNull);
      expect(find.byType(CircularProgressIndicator), findsOneWidget);
      working.value = false;
      await tester.pump();
      expect(tester.takeException(), isNull);
      expect(find.byType(CircularProgressIndicator), findsNothing);
    });

    testWidgets('the icon and the spinner crossfade in one slot', (
      tester,
    ) async {
      final working = ValueNotifier(false);
      addTearDown(working.dispose);
      await tester.pumpWidget(button(working, icon: Icons.add));
      final width = tester.getSize(find.byType(FilledButton)).width;
      working.value = true;
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 60));
      expect(find.byIcon(Icons.add), findsOneWidget);
      expect(find.byType(CircularProgressIndicator), findsOneWidget);
      expect(tester.getSize(find.byType(FilledButton)).width, width);
      await tester.pump(const Duration(milliseconds: 150));
      expect(find.byIcon(Icons.add), findsNothing);
      working.value = false;
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 200));
      expect(find.byType(CircularProgressIndicator), findsNothing);
      expect(tester.binding.transientCallbackCount, 0);
    });

    testWidgets('without an icon the spinner makes room smoothly', (
      tester,
    ) async {
      final working = ValueNotifier(false);
      addTearDown(working.dispose);
      await tester.pumpWidget(button(working));
      final idle = tester.getSize(find.byType(FilledButton)).width;
      working.value = true;
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 60));
      final mid = tester.getSize(find.byType(FilledButton)).width;
      await tester.pump(const Duration(milliseconds: 200));
      final busy = tester.getSize(find.byType(FilledButton)).width;
      expect(mid, inExclusiveRange(idle, busy));
      // The same button throughout: no rebuild into another widget type.
      expect(find.byType(FilledButton), findsOneWidget);
    });
  });

  group('expand row', () {
    testWidgets('unfolds its rows and turns its chevron', (tester) async {
      await tester.pumpWidget(
        _app(
          const Scaffold(
            body: KitExpandRow(
              title: 'Other versions',
              headerKey: ValueKey('header'),
              children: [Text('1.2.3')],
            ),
          ),
        ),
      );
      expect(find.text('1.2.3'), findsNothing);
      await tester.tap(find.byKey(const ValueKey('header')));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));
      final turns = tester
          .widget<AnimatedRotation>(find.byType(AnimatedRotation))
          .turns;
      expect(turns, .5);
      expect(_opacityOf(tester, find.text('1.2.3')), inExclusiveRange(0, 1));
      await tester.pumpAndSettle();
      expect(_opacityOf(tester, find.text('1.2.3')), 1);
      await tester.tap(find.byKey(const ValueKey('header')));
      await tester.pumpAndSettle();
      expect(find.text('1.2.3'), findsNothing);
    });
  });

  group('pull to refresh', () {
    testWidgets('draws the portal instead of the stock spinner and settles '
        'when the refresh ends', (tester) async {
      final done = Completer<void>();
      var refreshes = 0;
      await tester.pumpWidget(
        _app(
          Scaffold(
            body: KitRefresh(
              onRefresh: () {
                refreshes++;
                return done.future;
              },
              child: ListView(
                children: [for (var i = 0; i < 30; i++) Text('Row $i')],
              ),
            ),
          ),
        ),
      );
      expect(find.byKey(const ValueKey('kit-refresh-indicator')), findsNothing);
      await tester.fling(find.text('Row 0'), const Offset(0, 400), 1000);
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 30));
      // Mid-pull the disc is already there, drawing itself in.
      expect(
        find.byKey(const ValueKey('kit-refresh-indicator')),
        findsOneWidget,
      );
      // Loops are off under test: waiting on the refresh runs no ticker
      // of ours, so the screen settles while it waits.
      await tester.pumpAndSettle();
      expect(refreshes, 1);
      expect(
        find.byKey(const ValueKey('kit-refresh-indicator')),
        findsOneWidget,
      );
      expect(find.byType(RefreshProgressIndicator), findsNothing);
      done.complete();
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('kit-refresh-indicator')), findsNothing);
      expect(tester.binding.transientCallbackCount, 0);
    });
  });

  group('haptics', () {
    testWidgets('send ticks; done confirms unless motion is reduced', (
      tester,
    ) async {
      final haptics = _haptics(tester);
      KitHaptics.send();
      await tester.pumpWidget(_app(const SizedBox()));
      KitHaptics.done(tester.element(find.byType(SizedBox)));
      await tester.pumpWidget(_app(const SizedBox(), reduced: true));
      KitHaptics.done(tester.element(find.byType(SizedBox)));
      await tester.pump();
      expect(haptics.map((call) => call.arguments), [
        'HapticFeedbackType.lightImpact',
        'HapticFeedbackType.successNotification',
      ]);
    });
  });
}
