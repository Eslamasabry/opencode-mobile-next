// Native Android motion (owner rule 2026-10-26, "Yes, max Android native
// animation and support."; docs/ux-system/kit-v2.md top note): the Material
// ripple on a press, the predictive back page transition, the stretch
// overscroll, platform haptics on long-press and selection, and the system
// bars from the theme. Other platforms and reduced motion keep the kit's
// still look.
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

import 'kit_harness.dart';

/// Pumps [child] centred on a dark app screen (the test platform is
/// Android unless a variant says otherwise).
Future<void> _pump(
  WidgetTester tester,
  Widget child, {
  bool disableAnimations = false,
}) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    MaterialApp(
      debugShowCheckedModeBanner: false,
      theme: AppTheme.dark(),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      builder: (context, child) => MediaQuery(
        data: MediaQuery.of(
          context,
        ).copyWith(disableAnimations: disableAnimations),
        child: child!,
      ),
      home: Scaffold(body: Center(child: child)),
    ),
  );
}

void main() {
  group('pages use Android predictive back', () {
    testWidgets('the theme and KitPageRoute pick the Android transition', (
      tester,
    ) async {
      expect(
        AppTheme.dark().pageTransitionsTheme.builders[TargetPlatform.android],
        isA<KitAndroidPageTransitionsBuilder>(),
      );
      final context = await pumpKitHost(tester);
      pushKitPage<void>(context, (_) => const Scaffold(body: Text('B')));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));
      expect(
        find.ancestor(
          of: find.text('B'),
          matching: find.byWidgetPredicate(
            (w) => '${w.runtimeType}' == '_PredictiveBackGestureDetector',
          ),
        ),
        findsOneWidget,
      );
      await tester.pumpAndSettle();
    });

    testWidgets('the back gesture previews the page underneath', (
      tester,
    ) async {
      final context = await pumpKitHost(tester);
      Navigator.of(context).push(
        KitPageRoute<void>(builder: (_) => const Scaffold(body: Text('A'))),
      );
      await tester.pumpAndSettle();
      pushKitPage<void>(context, (_) => const Scaffold(body: Text('B')));
      await tester.pumpAndSettle();
      expect(find.text('A'), findsNothing, reason: 'covered: offstage');

      Future<void> send(String method, [Object? arguments]) =>
          tester.binding.defaultBinaryMessenger.handlePlatformMessage(
            SystemChannels.backGesture.name,
            SystemChannels.backGesture.codec.encodeMethodCall(
              MethodCall(method, arguments),
            ),
            (_) {},
          );
      Map<String, Object> event(double progress) => {
        'touchOffset': [5.0, 300.0],
        'progress': progress,
        'swipeEdge': 0,
      };
      await send('startBackGesture', event(0));
      await send('updateBackGestureProgress', event(.4));
      await tester.pump();
      expect(find.text('A'), findsOneWidget, reason: 'previewed under B');
      expect(find.text('B'), findsOneWidget);

      await send('commitBackGesture');
      await tester.pumpAndSettle();
      expect(find.text('B'), findsNothing);
      expect(find.text('A'), findsOneWidget);
    });

    testWidgets('under reduced motion a page arrives in one pump', (
      tester,
    ) async {
      final context = await pumpKitHost(
        tester,
        effects: const KitEffects(motion: KitMotionLevel.off),
      );
      pushKitPage<void>(context, (_) => const Scaffold(body: Text('B')));
      await tester.pump();
      await tester.pump();
      expect(find.text('B'), findsOneWidget);
      expect(tester.binding.hasScheduledFrame, isFalse);
      expect(
        find.byWidgetPredicate(
          (w) => '${w.runtimeType}' == '_PredictiveBackGestureDetector',
        ),
        findsNothing,
        reason: 'no preview, no slide',
      );
    });
  });

  testWidgets('lists stretch at their ends (Android 12+ overscroll)', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: AppTheme.dark(),
        scrollBehavior: const KitScrollBehavior(),
        home: ListView(children: const [Text('one'), Text('two')]),
      ),
    );
    expect(find.byType(StretchingOverscrollIndicator), findsOneWidget);
    expect(find.byType(GlowingOverscrollIndicator), findsNothing);
  });

  group('haptics like a native app', () {
    testWidgets('a long-press that opens a menu ticks; a tap does not', (
      tester,
    ) async {
      await _pump(
        tester,
        SizedBox(
          width: 360,
          child: KitRow(
            title: 'Session',
            onTap: () {},
            menu: [KitMenuItem(label: 'Rename', onSelected: () {})],
          ),
        ),
      );
      final haptics = recordHaptics(tester);
      await tester.tap(find.text('Session'));
      await tester.pumpAndSettle();
      expect(haptics, isEmpty, reason: 'taps are silent');
      await tester.longPress(find.text('Session'));
      await tester.pumpAndSettle();
      expect(find.text('Rename'), findsOneWidget);
      expect(haptics, ['null'], reason: 'the platform long-press tick');
    });

    testWidgets('a switch flip is a selection click', (tester) async {
      var on = false;
      await _pump(
        tester,
        StatefulBuilder(
          builder: (context, setState) => SizedBox(
            width: 360,
            child: KitSwitchRow(
              title: 'Notifications',
              value: on,
              onChanged: (value) => setState(() => on = value),
            ),
          ),
        ),
      );
      final haptics = recordHaptics(tester);
      await tester.tap(find.text('Notifications'));
      await tester.pumpAndSettle();
      expect(on, isTrue);
      expect(haptics, ['HapticFeedbackType.selectionClick']);
    });

    testWidgets('Vibration off silences them', (tester) async {
      await pumpKitHost(tester, effects: const KitEffects(haptics: false));
      final haptics = recordHaptics(tester);
      KitHaptics.longPress(tester.element(find.byType(Scaffold)));
      KitHaptics.select(tester.element(find.byType(Scaffold)));
      await tester.pump();
      expect(haptics, isEmpty);
    });
  });

  testWidgets('a page sets the system bars from the theme', (tester) async {
    await _pump(tester, const KitScreen(page: true, body: Text('Body')));
    final region = tester.widget<AnnotatedRegion<SystemUiOverlayStyle>>(
      find.byType(AnnotatedRegion<SystemUiOverlayStyle>).last,
    );
    final roles = ThemeRoles.of(tester.element(find.text('Body')));
    expect(region.value.statusBarColor, Colors.transparent);
    expect(region.value.statusBarIconBrightness, Brightness.light);
    expect(region.value.systemNavigationBarColor, roles.ground);
    expect(region.value.systemNavigationBarIconBrightness, Brightness.light);
  });
}
