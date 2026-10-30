// Settings › Appearance for a returning person (owner, 2026-10-27: "looks
// empty but condensed"): every question on one page under the four headings,
// each control in place and one tap from a change; a question shows only
// while it applies; Saver says once per section why Style and Background
// wait; search and links scroll to their question; a refused save is said
// under the question it was for; wide windows set two columns.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
import 'package:opencode_mobile/ui/teaching/teaching.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/appearance_guide.dart';

/// Profiles in memory: `load` would otherwise wait on secure storage.
class _MemoryProfileStore extends ProfileStore {
  _MemoryProfileStore({required super.prefs});

  bool refuseEffects = false;

  @override
  List<ServerProfile> get profiles => const [];

  @override
  String? get activeId => null;

  @override
  Future<void> setEffects(KitEffects effects) async {
    if (refuseEffects) throw StateError('disk full');
    await super.setEffects(effects);
  }
}

/// A returning person: the guide was finished or skipped on this device.
Future<(ConnectionController, _MemoryProfileStore)> _controller([
  Map<String, Object> saved = const {},
]) async {
  SharedPreferences.setMockInitialValues({
    'oc.appearanceGuide': '{"done":true}',
    ...saved,
  });
  final store = _MemoryProfileStore(
    prefs: await SharedPreferences.getInstance(),
  );
  return (ConnectionController(store), store);
}

final _en = lookupAppLocalizations(const Locale('en'));

Future<void> _mount(
  WidgetTester tester,
  ConnectionController controller, {
  AppearanceSection? section,
  String? row,
  Size size = const Size(412, 915),
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  Widget page = AppearanceSettingsScreen(
    controller: controller,
    initialSection: section,
  );
  if (row != null) page = KitArrivalScope(rowId: row, child: page);
  await tester.pumpWidget(
    MaterialApp(
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      theme: AppTheme.dark(),
      home: page,
    ),
  );
  await tester.pumpAndSettle();
}

Future<void> _tap(WidgetTester tester, String key) async {
  final finder = find.byKey(ValueKey(key));
  await bringIntoView(tester, finder);
  await tester.tap(finder);
  await tester.pumpAndSettle();
}

/// Whether [finder]'s middle is on screen, under the top bar.
bool _inView(WidgetTester tester, Finder finder) {
  final centre = tester.getCenter(finder);
  return centre.dy > 0 && centre.dy < tester.view.physicalSize.height;
}

/// Each question's words and one of its controls.
final _questions = {
  _en.appearanceAskMode: 'appearance-mode-dark',
  _en.appearanceAskTheme: 'theme-pack-nord',
  _en.appearanceAskAccent: 'appearance-accent',
  _en.appearanceAskStyle: 'appearance-surface-soft',
  _en.appearanceAskShape: 'appearance-fine-tune-corners',
  _en.appearanceAskLook: 'live-style-aurora',
  _en.appearanceAskStrength: 'effects-live-intensity-bold',
  _en.appearanceAskText: 'text-size-large',
  _en.appearanceAskMotion: 'effects-motion-calm',
  _en.appearanceAskGlow: 'effects-glow-softRing',
};

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('every question and its control is on the page, with no '
      'chip or sub-page to open', (tester) async {
    final (controller, _) = await _controller({'oc.effectsBackgroundOn': true});
    addTearDown(controller.dispose);
    await _mount(tester, controller);

    expect(find.byType(KitGuide), findsNothing);
    expect(find.byKey(const ValueKey('appearance-guide-next')), findsNothing);
    // Every section is there (the tour's anchors), with no small group word
    // above its first question (impeccable audit 2026-10-28, #15: the
    // question is the heading; the guide's chips keep the group names).
    for (final anchor in MakeItYoursAnchors.all) {
      await bringIntoView(tester, find.byKey(anchor));
      expect(find.byKey(anchor), findsOneWidget, reason: '$anchor');
    }
    for (final group in [
      _en.appearanceGroupColours,
      _en.appearanceGroupStyle,
      _en.appearanceGroupBackground,
      _en.appearanceGroupComfort,
    ]) {
      expect(find.text(group), findsNothing, reason: group);
    }
    for (final MapEntry(key: words, value: control) in _questions.entries) {
      final ask = find.text(words);
      await bringIntoView(tester, ask);
      expect(ask, findsOneWidget, reason: words);
      final finder = find.byKey(ValueKey(control));
      await bringIntoView(tester, finder);
      expect(finder, findsOneWidget, reason: control);
      expect(_inView(tester, finder), isTrue, reason: control);
    }
    // The page ends with its two rarer acts.
    await bringIntoView(
      tester,
      find.byKey(const ValueKey('appearance-guide-again')),
    );
    expect(find.byKey(const ValueKey('appearance-reset')), findsOneWidget);
  });

  testWidgets('Signal is a Style with its own sample and words', (
    tester,
  ) async {
    final (controller, _) = await _controller();
    addTearDown(controller.dispose);
    await _mount(tester, controller);

    final card = find.byKey(const ValueKey('appearance-surface-signal'));
    await bringIntoView(tester, card);
    expect(
      find.descendant(of: card, matching: find.text(_en.signalStyleName)),
      findsOneWidget,
    );
    // Its sample shows the track and the signals, not a card and a chip.
    expect(
      find.descendant(
        of: card,
        matching: find.byKey(const ValueKey('kit-style-sample-signal')),
      ),
      findsOneWidget,
    );
    await _tap(tester, 'appearance-surface-signal');
    expect(controller.effects.value.surfaceStyle, KitSurfaceStyle.signal);
    // A signal box does not drift: the live background turns off with it.
    expect(controller.effects.value.liveBackground, isFalse);
    expect(
      lookupAppLocalizations(const Locale('ar')).signalStyleName,
      isNot(_en.signalStyleName),
    );
  });

  testWidgets('one tap changes a choice in place', (tester) async {
    final (controller, _) = await _controller();
    addTearDown(controller.dispose);
    await _mount(tester, controller);

    await _tap(tester, 'appearance-mode-light');
    expect(controller.appearance.value, AppAppearance.light);
    await _tap(tester, 'theme-pack-nord');
    expect(controller.themePack.value, ThemePackId.nord);
    await _tap(tester, 'appearance-surface-brutal');
    expect(controller.effects.value.surfaceStyle, KitSurfaceStyle.brutal);
    await _tap(tester, 'text-size-large');
    expect(controller.textSize.value, KitTextSize.large);
    await _tap(tester, 'effects-motion-calm');
    expect(controller.effects.value.motion, KitMotionLevel.calm);
    // The chosen level's words show under the segments.
    expect(find.text(_en.effectsMotionCalmHint), findsOneWidget);
    await _tap(tester, 'effects-glow-softRing');
    expect(controller.effects.value.glowStyle, KitGlowStyle.softRing);
    expect(find.text(_en.appearanceGlowSoftHint), findsOneWidget);
    expect(find.byType(KitGuide), findsNothing);
  });

  testWidgets('Strength shows only while a look is on', (tester) async {
    final (controller, _) = await _controller();
    addTearDown(controller.dispose);
    await _mount(tester, controller);
    final strength = find.text(_en.appearanceAskStrength);
    expect(controller.effects.value.liveBackground, isFalse);
    expect(strength, findsNothing);
    // No "choose a look first" detour on this page: it simply is not asked.
    expect(
      find.byKey(const ValueKey('appearance-strength-needs-look')),
      findsNothing,
    );

    await _tap(tester, 'live-style-aurora');
    expect(controller.effects.value.liveBackground, isTrue);
    expect(strength, findsOneWidget);
    await _tap(tester, 'effects-live-intensity-bold');
    expect(controller.effects.value.liveIntensity, KitLiveIntensity.bold);

    await _tap(tester, 'live-style-off');
    expect(strength, findsNothing);
  });

  testWidgets('Saver says once per section why Style and Background wait, '
      'and Turn off Saver brings them back', (tester) async {
    final (controller, _) = await _controller({'oc.appearance': 'saver'});
    addTearDown(controller.dispose);
    await _mount(tester, controller);

    final style = find.byKey(const ValueKey('appearance-saver-holds-style'));
    final background = find.byKey(
      const ValueKey('appearance-saver-holds-background'),
    );
    await bringIntoView(tester, style);
    expect(find.text(_en.appearanceSaverHoldsStyle), findsOneWidget);
    expect(find.byKey(const ValueKey('appearance-surfaces')), findsNothing);
    await bringIntoView(tester, background);
    expect(find.text(_en.appearanceSaverHoldsBackground), findsOneWidget);
    expect(find.byKey(const ValueKey('live-style-off')), findsNothing);
    // Colours and Text & motion stay.
    expect(find.text(_en.appearanceAskMotion), findsOneWidget);

    await _tap(tester, 'appearance-turn-off-saver-background');
    expect(controller.appearance.value, AppAppearance.dark);
    expect(background, findsNothing);
    expect(find.byKey(const ValueKey('appearance-surfaces')), findsOneWidget);
  });

  group('search and links scroll to their question', () {
    for (final (section, words) in [
      (AppearanceSection.theme, _en.appearanceAskTheme),
      (AppearanceSection.text, _en.appearanceAskText),
      (AppearanceSection.effects, _en.appearanceAskMotion),
    ]) {
      testWidgets('${section.name} shows "$words"', (tester) async {
        final (controller, _) = await _controller();
        addTearDown(controller.dispose);
        await _mount(tester, controller, section: section);
        expect(find.byType(KitGuide), findsNothing);
        expect(_inView(tester, find.text(words)), isTrue);
      });
    }

    testWidgets('the row effects-glow is marked and in view', (tester) async {
      final (controller, _) = await _controller();
      addTearDown(controller.dispose);
      await _mount(
        tester,
        controller,
        section: AppearanceSection.effects,
        row: 'effects-glow',
      );
      final glow = find.byKey(const ValueKey('effects-glow-softRing'));
      expect(_inView(tester, glow), isTrue);
      expect(
        find.ancestor(of: glow, matching: find.byType(KitArrival)),
        findsOneWidget,
      );
    });
  });

  testWidgets('a refused save is said under the question it was for', (
    tester,
  ) async {
    final (controller, store) = await _controller();
    addTearDown(controller.dispose);
    await _mount(tester, controller);
    store.refuseEffects = true;
    await _tap(tester, 'effects-motion-off');
    final failed = find.byKey(const ValueKey('appearance-display-failed'));
    expect(failed, findsOneWidget);
    expect(
      find.ancestor(
        of: failed,
        matching: find.byKey(const ValueKey('appearance-one-motion')),
      ),
      findsOneWidget,
    );
  });

  testWidgets('the tour\'s anchors are the four section headings', (
    tester,
  ) async {
    final (controller, _) = await _controller();
    addTearDown(controller.dispose);
    await _mount(tester, controller);
    for (final anchor in MakeItYoursAnchors.all) {
      await bringIntoView(tester, find.byKey(anchor));
      expect(find.byKey(anchor), findsOneWidget);
    }
  });

  testWidgets('a wide window sets the sections in two columns', (tester) async {
    final (controller, _) = await _controller();
    addTearDown(controller.dispose);
    await _mount(tester, controller, size: const Size(1280, 900));
    final colours = tester.getRect(
      find.byKey(const ValueKey('appearance-group-colours')),
    );
    final background = tester.getRect(
      find.byKey(const ValueKey('appearance-group-background')),
    );
    expect(background.left, greaterThan(colours.right));
    expect((background.top - colours.top).abs(), lessThan(1));
    expect(tester.takeException(), isNull);
  });

  // 3820 on the emulator: TalkBack read every question's note as one node
  // ("“Match” uses Graphite's own accent. / Corners / Spacing / …").
  testWidgets('each question is read on its own', (tester) async {
    final handle = tester.ensureSemantics();
    final (controller, _) = await _controller();
    addTearDown(controller.dispose);
    await _mount(tester, controller);

    final note = _en.appearanceAccentMatchNote('Graphite');
    final node = tester.getSemantics(find.text(note));
    expect(node.label, isNot(contains(_en.appearanceShapeSpacing)));
    handle.dispose();
  });
}
