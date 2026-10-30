// The teaching system (docs/ux-system/teaching.md, owner rule 2026-10-21):
// the tip controller's rules, the registry's promises (every tip and
// tutorial has a Help › Tips row and en and ar copy), Help › Tips itself,
// and the "Make it yours" mini tutorial offered once after the first reply.
import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/tips.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/screens/settings/tips_screen.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
import 'package:opencode_mobile/ui/teaching/teaching.dart';
import 'package:shared_preferences/shared_preferences.dart';

final _en = lookupAppLocalizations(const Locale('en'));

/// Profiles in memory: `load` would otherwise wait on secure storage.
class _Store extends ProfileStore {
  _Store({required super.prefs});
  @override
  List<ServerProfile> get profiles => const [];
  @override
  String? get activeId => null;
}

Future<ConnectionController> _controller([
  Map<String, Object> saved = const {'oc.nudges.firstReplySeen': true},
]) async {
  SharedPreferences.setMockInitialValues(saved);
  final controller = ConnectionController(
    _Store(prefs: await SharedPreferences.getInstance()),
  );
  return controller;
}

Widget _app(Widget home, {bool reduce = false}) => MaterialApp(
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  theme: AppTheme.dark(),
  builder: (context, child) => MediaQuery(
    data: MediaQuery.of(context).copyWith(disableAnimations: reduce),
    child: child!,
  ),
  home: home,
);

void _phone(WidgetTester tester) {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
}

Future<void> _frames(WidgetTester tester, [int n = 8]) async {
  for (var i = 0; i < n; i++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('TipController', () {
    late DateTime now;
    Future<TipController> tips([Map<String, Object> saved = const {}]) async {
      SharedPreferences.setMockInitialValues(saved);
      now = DateTime.utc(2026, 10, 21, 9);
      return TipController(
        await SharedPreferences.getInstance(),
        now: () => now,
      );
    }

    test('shows a tip at most twice, a quiet gap apart', () async {
      final c = await tips();
      expect(c.offer('a'), isTrue);
      c.dismiss('a');
      expect(c.offer('a'), isFalse, reason: 'cooldown');
      now = now.add(TipController.cooldown);
      expect(c.offer('a'), isTrue);
      c.dismiss('a');
      now = now.add(TipController.cooldown);
      expect(c.offer('a'), isFalse, reason: 'shown twice');
      expect(c.record('a').count, 2);
    });

    test('one tip at a time; a higher priority takes the place', () async {
      final c = await tips();
      expect(c.offer('a', priority: 1), isTrue);
      expect(c.offer('b', priority: 1), isFalse);
      expect(c.record('b').count, 0, reason: 'a refusal consumes nothing');
      expect(c.offer('c', priority: 2), isTrue);
      expect(c.active, 'c');
    });

    test('never again once used', () async {
      final c = await tips();
      c.markUsed('a');
      expect(c.offer('a'), isFalse);
      expect(c.due('a'), isFalse);
    });

    test('never while a question waits, the keyboard covers the anchor or '
        'during onboarding', () async {
      final c = await tips();
      for (final moment in const [
        TipMoment(questionWaiting: true),
        TipMoment(keyboardOverAnchor: true),
        TipMoment(onboarding: true),
      ]) {
        expect(c.offer('a', moment: moment), isFalse);
      }
      expect(c.record('a').count, 0);
    });

    test('one new tip per screen visit', () async {
      final c = await tips();
      final visit = Object();
      expect(c.offer('a', visit: visit), isTrue);
      c.dismiss('a');
      now = now.add(TipController.cooldown);
      expect(c.offer('b', visit: visit), isFalse);
      expect(c.offer('b', visit: Object()), isTrue);
    });

    test('is stored app-wide as oc.tips and read back', () async {
      final c = await tips();
      c
        ..offer('a')
        ..markUsed('b');
      await Future<void>.delayed(Duration.zero);
      final saved = c.preferences.getString(TipController.storageKey)!;
      final again = TipController(c.preferences);
      expect(again.record('a').count, 1);
      expect(again.record('b').done, isTrue);
      expect(jsonDecode(saved), contains('a'));
    });

    test('a replay counts nothing; Show tips again resets all', () async {
      final c = await tips();
      c.replay('a');
      expect(c.isActive('a'), isTrue);
      expect(c.activeIsReplay, isTrue);
      expect(c.record('a').count, 0);
      expect(c.offer('a'), isFalse, reason: 'a replay is not its moment');
      c.markUsed('b');
      await c.reset();
      expect(c.active, isNull);
      expect(c.record('b').done, isFalse);
      expect(c.preferences.getString(TipController.storageKey), isNull);
    });
  });

  group('registry', () {
    Map<String, dynamic> arb(String locale) =>
        jsonDecode(File('lib/l10n/app_$locale.arb').readAsStringSync())
            as Map<String, dynamic>;

    test('ids are unique, tutorials have 2–4 steps', () {
      final ids = [for (final e in teachingRegistry) e.id];
      expect(ids.toSet(), hasLength(ids.length));
      for (final e in teachingRegistry) {
        expect(e.maxCount, inInclusiveRange(1, 2), reason: e.id);
        expect(e.anchors, isNotEmpty, reason: e.id);
        expect(e.trigger, isNotEmpty, reason: e.id);
        if (e.kind == TeachingKind.tour) {
          expect(e.steps!(_en).length, inInclusiveRange(2, 4), reason: e.id);
        }
      }
    });

    test('every tip and tutorial has its copy in en and ar', () {
      final en = arb('en');
      final ar = arb('ar');
      for (final e in teachingRegistry) {
        for (final key in e.copyKeys) {
          expect(
            (en[key] as String?)?.trim(),
            isNotEmpty,
            reason: '${e.id}: $key en',
          );
          expect(
            (ar[key] as String?)?.trim(),
            isNotEmpty,
            reason: '${e.id}: $key ar',
          );
        }
        // The words it shows are among the keys it declares.
        final declared = {for (final key in e.copyKeys) en[key]};
        expect(declared, contains(e.title(_en)), reason: e.id);
        expect(declared, contains(e.message(_en)), reason: e.id);
      }
    });

    testWidgets('every tip and tutorial has a Help › Tips row with Show me', (
      tester,
    ) async {
      _phone(tester);
      final controller = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(TipsScreen(controller: controller)));
      await _frames(tester);
      for (final e in teachingRegistry) {
        final row = find.byKey(ValueKey('tips-row-${e.id}'));
        await tester.scrollUntilVisible(row, 100);
        expect(row, findsOneWidget, reason: e.id);
        expect(
          find.byKey(ValueKey('tips-show-me-${e.id}')),
          findsOneWidget,
          reason: e.id,
        );
        expect(find.text(e.title(_en)), findsWidgets);
      }
    });
  });

  group('Help › Tips', () {
    testWidgets('Show me replays a tip in place, counting nothing', (
      tester,
    ) async {
      _phone(tester);
      final controller = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_app(TipsScreen(controller: controller)));
      await _frames(tester);
      await tester.tap(
        find.byKey(const ValueKey('tips-show-me-${TeachingIds.stopAfterStep}')),
      );
      await _frames(tester);
      expect(
        find.byKey(const ValueKey('tips-replay-${TeachingIds.stopAfterStep}')),
        findsOneWidget,
      );
      expect(find.text(_en.tipStopAfterStep), findsWidgets);
      expect(controller.tips.record(TeachingIds.stopAfterStep).count, 0);
      // It fades by itself.
      await tester.pump(const Duration(seconds: 5));
      await _frames(tester);
      expect(
        find.byKey(const ValueKey('tips-replay-${TeachingIds.stopAfterStep}')),
        findsNothing,
      );
    });

    testWidgets('Show tips again resets every tip, tutorial and nudge', (
      tester,
    ) async {
      _phone(tester);
      final controller = await _controller();
      addTearDown(controller.dispose);
      controller.tips.markUsed(TeachingIds.sendLater);
      await tester.pumpWidget(_app(TipsScreen(controller: controller)));
      await _frames(tester);
      final row = find.byKey(const ValueKey('tips-show-again'));
      await tester.scrollUntilVisible(row, 100);
      await tester.tap(row);
      await _frames(tester);
      expect(controller.tips.record(TeachingIds.sendLater).done, isFalse);
      expect(find.text(_en.discoverShowTipsDone), findsOneWidget);
    });

    testWidgets('reads in Arabic, right to left, at 2× text', (tester) async {
      _phone(tester);
      final controller = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        MaterialApp(
          locale: const Locale('ar'),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          theme: AppTheme.dark(),
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(
              context,
            ).copyWith(textScaler: const TextScaler.linear(2)),
            child: child!,
          ),
          home: TipsScreen(controller: controller),
        ),
      );
      await _frames(tester);
      final ar = lookupAppLocalizations(const Locale('ar'));
      expect(find.text(ar.tipsTitle), findsWidgets);
      await tester.tap(
        find.byKey(const ValueKey('tips-show-me-${TeachingIds.sendLater}')),
      );
      await _frames(tester);
      expect(find.text(ar.tipSendLater), findsWidgets);
      expect(tester.takeException(), isNull);
    });
  });

  group('Make it yours', () {
    final offer = find.byKey(const ValueKey('tour-offer-make-it-yours'));
    final next = find.byKey(const ValueKey('kit-tour-next'));
    final steps = teachingEntry(TeachingIds.makeItYours).steps!(_en);

    /// The step's control is on screen, inside the tour's cut-out.
    void anchored(WidgetTester tester, int i) {
      final control = find.byKey(steps[i].anchors.single);
      expect(control, findsOneWidget, reason: 'step ${i + 1}');
      final rect = tester.getRect(control);
      final screen = Offset.zero & tester.view.physicalSize;
      expect(screen.contains(rect.center), isTrue, reason: 'step ${i + 1}');
      expect(find.text(steps[i].message), findsOneWidget);
      expect(find.text(_en.kitTourStep(i + 1, 4)), findsWidgets);
    }

    testWidgets('has four steps, in order: the four chips, Colours, Style, '
        'Background, Text & motion', (tester) async {
      expect(
        [for (final s in steps) s.message],
        [
          _en.tourMakeItYoursColours,
          _en.tourMakeItYoursStyle,
          _en.tourMakeItYoursBackground,
          _en.tourMakeItYoursComfort,
        ],
      );
      expect(MakeItYoursAnchors.all, [
        const ValueKey('appearance-group-colours'),
        const ValueKey('appearance-group-style'),
        const ValueKey('appearance-group-background'),
        const ValueKey('appearance-group-comfort'),
      ]);
      expect(teachingEntry(TeachingIds.makeItYours).anchors, [
        for (final s in steps) ...s.anchors,
      ]);
      // One tour, not two: the older Appearance tour is gone.
      expect(
        teachingRegistry.where((e) => e.kind == TeachingKind.tour),
        hasLength(1),
      );
    });

    testWidgets('is offered once on the first visit to Appearance, and each '
        'step points at its real control', (tester) async {
      _phone(tester);
      final controller = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        _app(AppearanceSettingsScreen(controller: controller)),
      );
      await _frames(tester);
      expect(offer, findsOneWidget);
      expect(find.text(_en.tourMakeItYoursOffer), findsOneWidget);
      // It never blocks: the screen works under the offer.
      await tester.tap(find.byKey(MakeItYoursAnchors.comfort));
      await _frames(tester);
      expect(find.byKey(const ValueKey('text-size-row')), findsOneWidget);
      expect(offer, findsOneWidget);
      await tester.tap(find.text(_en.tourAppearanceStart));
      await _frames(tester);
      expect(offer, findsNothing);
      // Back on the four chips, where every step's control lives.
      for (var i = 0; i < steps.length; i++) {
        anchored(tester, i);
        await tester.tap(next);
        await _frames(tester);
      }
      expect(find.text(steps.last.message), findsNothing);
      expect(controller.tips.record(TeachingIds.makeItYours).done, isTrue);

      // The second visit offers nothing.
      await tester.pumpWidget(const SizedBox());
      await tester.pumpWidget(
        _app(AppearanceSettingsScreen(controller: controller)),
      );
      await _frames(tester);
      expect(offer, findsNothing);
    });

    testWidgets('Not now is an answer: never offered again, anywhere', (
      tester,
    ) async {
      _phone(tester);
      final controller = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        _app(AppearanceSettingsScreen(controller: controller)),
      );
      await _frames(tester);
      await tester.tap(find.text(_en.tourNotNow));
      await _frames(tester);
      expect(offer, findsNothing);
      expect(find.text(steps.first.message), findsNothing);
      expect(controller.tips.record(TeachingIds.makeItYours).done, isTrue);
      // Remembered across a restart.
      await tester.pump();
      final again = ConnectionController(
        _Store(prefs: controller.tips.preferences),
      );
      addTearDown(again.dispose);
      await tester.pumpWidget(const SizedBox());
      await tester.pumpWidget(
        _app(AppearanceSettingsScreen(controller: again)),
      );
      await _frames(tester);
      expect(offer, findsNothing);
    });

    testWidgets('an answer to the earlier Appearance tour stands', (
      tester,
    ) async {
      _phone(tester);
      final controller = await _controller();
      addTearDown(controller.dispose);
      controller.tips.markUsed(TeachingIds.legacyAppearanceTour);
      await tester.pumpWidget(
        _app(AppearanceSettingsScreen(controller: controller)),
      );
      await _frames(tester);
      expect(offer, findsNothing);
    });

    testWidgets('waits for the first reply ever', (tester) async {
      _phone(tester);
      final controller = await _controller(const {});
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        _app(AppearanceSettingsScreen(controller: controller)),
      );
      await _frames(tester);
      expect(offer, findsNothing);
      expect(controller.tips.record(TeachingIds.makeItYours).count, 0);
    });

    testWidgets('from another screen, Show me opens Appearance and runs it', (
      tester,
    ) async {
      _phone(tester);
      final controller = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        _app(
          Builder(
            builder: (context) => Center(
              child: TextButton(
                onPressed: () => offerMakeItYours(context, controller),
                child: const Text('reply arrived'),
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('reply arrived'));
      await _frames(tester);
      expect(offer, findsOneWidget);
      await tester.tap(find.text(_en.tourAppearanceStart));
      await _frames(tester, 12);
      expect(find.byType(AppearanceSettingsScreen), findsOneWidget);
      // The page's own first-visit offer stays quiet: one offer, once.
      expect(offer, findsNothing);
      anchored(tester, 0);
      await tester.tap(find.byKey(const ValueKey('kit-tour-skip')));
      await _frames(tester);
      expect(controller.tips.record(TeachingIds.makeItYours).done, isTrue);
    });

    testWidgets('opened at a question, the tour brings the chips back', (
      tester,
    ) async {
      _phone(tester);
      final controller = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        _app(
          AppearanceSettingsScreen(
            controller: controller,
            initialSection: AppearanceSection.effects,
          ),
        ),
      );
      await _frames(tester);
      expect(find.byKey(MakeItYoursAnchors.colours), findsNothing);
      await tester.tap(find.text(_en.tourAppearanceStart));
      await _frames(tester);
      anchored(tester, 0);
    });

    testWidgets('reduced motion: each step is there at once, nothing glides', (
      tester,
    ) async {
      _phone(tester);
      final controller = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        _app(AppearanceSettingsScreen(controller: controller), reduce: true),
      );
      await _frames(tester);
      await tester.tap(find.text(_en.tourAppearanceStart));
      await _frames(tester);
      anchored(tester, 0);
      for (var i = 1; i < steps.length; i++) {
        await tester.tap(next);
        // No scroll or highlight animation to wait for: two frames.
        await tester.pump();
        await tester.pump();
        anchored(tester, i);
        await _frames(tester, 3);
      }
      await tester.tap(next);
      await _frames(tester);
      expect(find.text(steps.last.message), findsNothing);
      expect(controller.tips.record(TeachingIds.makeItYours).done, isTrue);
    });

    testWidgets('reads in Arabic, right to left, at 2× text', (tester) async {
      _phone(tester);
      final controller = await _controller();
      addTearDown(controller.dispose);
      final ar = lookupAppLocalizations(const Locale('ar'));
      await tester.pumpWidget(
        MaterialApp(
          locale: const Locale('ar'),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          theme: AppTheme.dark(),
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(
              context,
            ).copyWith(textScaler: const TextScaler.linear(2)),
            child: child!,
          ),
          home: AppearanceSettingsScreen(controller: controller),
        ),
      );
      await _frames(tester);
      await tester.tap(find.text(ar.tourAppearanceStart));
      await _frames(tester);
      for (final message in [
        ar.tourMakeItYoursColours,
        ar.tourMakeItYoursStyle,
        ar.tourMakeItYoursBackground,
        ar.tourMakeItYoursComfort,
      ]) {
        expect(find.text(message), findsOneWidget);
        // The card stays on the screen, even under a tall control.
        final screen = Offset.zero & tester.view.physicalSize;
        expect(
          screen.contains(tester.getCenter(next)),
          isTrue,
          reason: message,
        );
        await tester.tap(next);
        await _frames(tester);
      }
      expect(tester.takeException(), isNull);
    });

    testWidgets('Show me in Help › Tips opens Appearance and runs it again', (
      tester,
    ) async {
      _phone(tester);
      final controller = await _controller();
      addTearDown(controller.dispose);
      controller.tips.markUsed(TeachingIds.makeItYours);
      await tester.pumpWidget(_app(TipsScreen(controller: controller)));
      await _frames(tester);
      final showMe = find.byKey(
        const ValueKey('tips-show-me-${TeachingIds.makeItYours}'),
      );
      await tester.scrollUntilVisible(showMe, 100);
      expect(find.text(_en.tourMakeItYoursTitle), findsWidgets);
      await tester.tap(showMe);
      await _frames(tester, 12);
      expect(find.byType(AppearanceSettingsScreen), findsOneWidget);
      expect(offer, findsNothing);
      for (var i = 0; i < steps.length; i++) {
        anchored(tester, i);
        await tester.tap(next);
        await _frames(tester);
      }
      expect(controller.tips.record(TeachingIds.makeItYours).done, isTrue);
    });
  });
}
