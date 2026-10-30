// KitPageRoute contract tests (docs/ux-system/kit-api/KitPageRoute.md "Tests
// required").
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/kit/kit_motion.dart';
import 'package:opencode_mobile/ui/kit/kit_page_route.dart';

import 'kit_motion_still.dart';

/// Pumps an empty [MaterialApp] at [theme] and [locale] and returns a
/// context under its Navigator to push from. [disableAnimations] seeds
/// `MediaQuery.disableAnimationsOf` (the "remove animations" setting);
/// [toggleAnimations], when given, drives it instead, so a test can flip the
/// setting while pages are open: the same app rebuilds with the new value,
/// the way the system setting reaches a running app.
Future<BuildContext> _pumpApp(
  WidgetTester tester, {
  ThemeData? theme,
  Locale locale = const Locale('en'),
  bool disableAnimations = false,
  ValueNotifier<bool>? toggleAnimations,
}) async {
  late BuildContext context;
  Widget app(bool disable) => MaterialApp(
    debugShowCheckedModeBanner: false,
    theme: theme,
    locale: locale,
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    builder: (context, child) => MediaQuery(
      data: MediaQuery.of(context).copyWith(disableAnimations: disable),
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
  );
  if (toggleAnimations != null) {
    await tester.pumpWidget(
      ValueListenableBuilder<bool>(
        valueListenable: toggleAnimations,
        builder: (context, disable, child) => app(disable),
      ),
    );
  } else {
    await tester.pumpWidget(app(disableAnimations));
  }
  return context;
}

/// Every [Transform] in the tree under [finder] whose matrix has moved (a
/// non-zero x translation): the signature of the kit's `_Axis` widget mid
/// transition (Zoom and Cupertino's builders use `ScaleTransition` /
/// `SlideTransition`, never a bare translating `Transform`).
Iterable<double> _translationsX(WidgetTester tester, Finder finder) => tester
    .widgetList<Transform>(finder)
    .map((t) => t.transform.getTranslation().x)
    .where((dx) => dx != 0);

/// [matching] among the ancestors of the arriving page's own content, not
/// the whole tree — `MaterialApp`'s own initial (implicit) route keeps its
/// platform `ZoomPageTransitionsBuilder` wrapper (a settled `ScaleTransition`
/// at scale 1.0) for as long as it is mounted, which is not what these tests
/// are about.
Finder _underB(Finder matching) =>
    find.ancestor(of: find.text('B'), matching: matching);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  for (final still in KitStill.values) {
    testWidgets('page push and pop settle in one pump under ${still.name}', (
      tester,
    ) async {
      late BuildContext context;
      await tester.pumpWidget(
        kitStillApp(
          Builder(
            builder: (inner) {
              context = inner;
              return const Text('Home');
            },
          ),
          still,
        ),
      );
      final navigator = Navigator.of(context);
      final route = KitPageRoute<void>(
        builder: (_) => const Scaffold(body: Text('Details page')),
      );
      unawaited(navigator.push(route));
      await tester.pump();
      expect(find.text('Details page'), findsOneWidget);
      expect(tester.hasRunningAnimations, isFalse);
      expect(route.transitionDuration, Duration.zero);
      navigator.pop();
      await tester.pump();
      expect(find.text('Details page'), findsNothing);
      expect(find.text('Home'), findsOneWidget);
      expect(tester.hasRunningAnimations, isFalse);
    });
  }

  testWidgets(
    'turning reduced motion on while a page is open settles its exit',
    (tester) async {
      final reduced = ValueNotifier(false);
      addTearDown(reduced.dispose);
      final context = await _pumpApp(tester, toggleAnimations: reduced);
      final navigator = Navigator.of(context);
      unawaited(pushKitPage<void>(context, (_) => const Text('Details page')));
      await tester.pumpAndSettle();
      reduced.value = true;
      await tester.pump();
      navigator.pop();
      await tester.pump();
      expect(find.text('Details page'), findsNothing);
      expect(tester.hasRunningAnimations, isFalse);
    },
  );

  test(
    'transitionDuration and reverseTransitionDuration are KitMotion.standard',
    () {
      final route = KitPageRoute<void>(builder: (_) => const SizedBox.shrink());
      expect(route.transitionDuration, KitMotion.standard);
      expect(route.reverseTransitionDuration, KitMotion.standard);
    },
  );

  testWidgets(
    'under a bare ThemeData the kit\'s own transition still plays, not the '
    'platform default (off Android: Android has its own since 2026-10-26)',
    (tester) async {
      final context = await _pumpApp(
        tester,
        theme: ThemeData(platform: TargetPlatform.fuchsia),
      );
      unawaited(pushKitPage<void>(context, (_) => const Text('B')));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 125));

      expect(_underB(find.byType(ScaleTransition)), findsNothing);
      expect(
        _translationsX(tester, _underB(find.byType(Transform))),
        isNotEmpty,
        reason: 'the kit\'s axis widget should be sliding the arriving page',
      );
    },
  );

  testWidgets('mid-transition there is no scale or blur (MOT-2)', (
    tester,
  ) async {
    final context = await _pumpApp(tester);
    unawaited(pushKitPage<void>(context, (_) => const Text('B')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 125));

    expect(_underB(find.byType(ScaleTransition)), findsNothing);
    expect(_underB(find.byType(ImageFiltered)), findsNothing);
    expect(_underB(find.byType(BackdropFilter)), findsNothing);
    final scaled = tester
        .widgetList<Transform>(_underB(find.byType(Transform)))
        .where((t) => t.transform.getMaxScaleOnAxis() != 1.0);
    expect(scaled, isEmpty);
  });

  testWidgets(
    'under disableAnimations the new page is opaque and in place after one '
    'pump (G8x)',
    (tester) async {
      final context = await _pumpApp(tester, disableAnimations: true);
      unawaited(pushKitPage<void>(context, (_) => const Text('B')));
      await tester.pump();

      // Onstage (the default finder skips offstage widgets): the app's
      // HeroController must not hold the page back for a measuring frame.
      expect(find.text('B'), findsOneWidget);
      for (final o in tester.widgetList<Opacity>(
        find.ancestor(of: find.text('B'), matching: find.byType(Opacity)),
      )) {
        expect(o.opacity, 1.0);
      }
      for (final t in tester.widgetList<Transform>(
        find.ancestor(of: find.text('B'), matching: find.byType(Transform)),
      )) {
        expect(t.transform.getTranslation().x, 0);
      }
    },
  );

  testWidgets(
    'with motion on, a pushed page keeps the hero measuring frame the '
    'framework gives it',
    (tester) async {
      final context = await _pumpApp(tester);
      unawaited(pushKitPage<void>(context, (_) => const Text('B')));
      await tester.pump();

      // Reduced motion alone skips that frame (the test above); with motion
      // on, heroes still measure the page's settled layout first.
      expect(find.text('B'), findsNothing);
      expect(find.text('B', skipOffstage: false), findsOneWidget);
      await tester.pump();
      expect(find.text('B'), findsOneWidget);
    },
  );

  testWidgets('RTL: the arriving page slides in from the left (negative x)', (
    tester,
  ) async {
    // The kit's own transition (off Android; Android uses its own).
    final context = await _pumpApp(
      tester,
      locale: const Locale('ar'),
      theme: ThemeData(platform: TargetPlatform.fuchsia),
    );
    unawaited(pushKitPage<void>(context, (_) => const Text('B')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 125));

    final dxs = _translationsX(
      tester,
      find.ancestor(of: find.text('B'), matching: find.byType(Transform)),
    ).toList();
    expect(dxs, isNotEmpty);
    for (final dx in dxs) {
      expect(dx, lessThan(0));
    }
  });

  testWidgets(
    'pushKitPage resolves with the popped value and keeps settings.name',
    (tester) async {
      final context = await _pumpApp(tester);
      String? nameInsidePage;
      final result = pushKitPage<String>(context, (inner) {
        nameInsidePage = ModalRoute.of(inner)?.settings.name;
        return const SizedBox.shrink();
      }, settings: const RouteSettings(name: '/b'));
      await tester.pumpAndSettle();
      expect(nameInsidePage, '/b');

      Navigator.of(context).pop('done');
      await tester.pumpAndSettle();
      expect(await result, 'done');
    },
  );

  testWidgets('fullscreenDialog is visible on ModalRoute.of inside the page', (
    tester,
  ) async {
    final context = await _pumpApp(tester);
    bool? fullscreen;
    unawaited(
      pushKitPage<void>(context, (inner) {
        fullscreen = ModalRoute.of(inner)!.fullscreenDialog;
        return const SizedBox.shrink();
      }, fullscreenDialog: true),
    );
    await tester.pumpAndSettle();
    expect(fullscreen, isTrue);
  });

  testWidgets(
    'a draft in a page underneath survives push, pop and a reduced-motion '
    'toggle while the page above is open',
    (tester) async {
      final toggle = ValueNotifier<bool>(false);
      addTearDown(toggle.dispose);
      final context = await _pumpApp(tester, toggleAnimations: toggle);

      // Page A holds its draft in widget state only: a TextField with no
      // controller keeps the text in its own State, so the draft is still
      // there only if page A's element subtree was never torn down.
      late BuildContext pageA;
      unawaited(
        pushKitPage<void>(context, (inner) {
          pageA = inner;
          return const Material(child: TextField());
        }),
      );
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField), 'draft text');
      await tester.pump();
      final draftState = tester.state(find.byType(EditableText));

      unawaited(pushKitPage<void>(pageA, (_) => const Text('B')));
      await tester.pumpAndSettle();
      expect(find.text('B'), findsOneWidget);
      expect(
        find.text('draft text', skipOffstage: false),
        findsOneWidget,
        reason: 'page A is covered but kept (maintainState)',
      );

      // Reduced motion on and off again while page B is open over page A.
      toggle.value = true;
      await tester.pumpAndSettle();
      toggle.value = false;
      await tester.pumpAndSettle();
      expect(find.text('B'), findsOneWidget);

      Navigator.of(context).pop();
      await tester.pumpAndSettle();
      expect(find.text('B'), findsNothing);
      expect(find.text('draft text'), findsOneWidget);
      expect(
        tester.state(find.byType(EditableText)),
        same(draftState),
        reason: 'page A was kept, not rebuilt with an empty field',
      );

      // And once more with page A itself on top.
      toggle.value = true;
      await tester.pumpAndSettle();
      expect(find.text('draft text'), findsOneWidget);
      expect(tester.state(find.byType(EditableText)), same(draftState));
    },
  );

  testWidgets('replaceWithKitPage removes the route it replaces', (
    tester,
  ) async {
    final context = await _pumpApp(tester);
    late BuildContext pageAContext;
    unawaited(
      pushKitPage<void>(context, (inner) {
        pageAContext = inner;
        return const Text('A');
      }),
    );
    await tester.pumpAndSettle();
    expect(find.text('A'), findsOneWidget);

    final result = replaceWithKitPage<String, void>(
      pageAContext,
      (_) => const Text('B'),
    );
    await tester.pumpAndSettle();

    expect(find.text('B'), findsOneWidget);
    expect(
      find.text('A'),
      findsNothing,
      reason: 'the replaced route must be gone, not merely covered',
    );

    Navigator.of(context).pop('bye');
    await tester.pumpAndSettle();
    expect(find.text('B'), findsNothing);
    expect(await result, 'bye');
  });
}
