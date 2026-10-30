// Settings › Appearance, one decision at a time (owner approved 2026-10-23,
// /launch-footage/appearance-focus/appearance-guided.html): four chips that
// show their values, each opening its questions; a guided first visit (a
// Next bar, a dot on the next chip, checks, Finish, All set) and a returning
// one remembered on this device: every question on one page (owner,
// 2026-10-27), Guide me again, Reset with an in-page confirm; Saver's held
// chips and sections;
// Look Off and Strength; a new Style and Shape; search and links landing on
// their question. No preview: the page itself wears the choices.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/appearance_guide.dart';
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

  @override
  List<ServerProfile> get profiles => const [];

  @override
  String? get activeId => null;
}

Future<(ConnectionController, SharedPreferences)> _controller([
  Map<String, Object> saved = const {},
]) async {
  SharedPreferences.setMockInitialValues(saved);
  final prefs = await SharedPreferences.getInstance();
  return (ConnectionController(_MemoryProfileStore(prefs: prefs)), prefs);
}

final _en = lookupAppLocalizations(const Locale('en'));

Widget _page(
  ConnectionController controller, {
  AppearanceSection? section,
  String? row,
}) {
  Widget page = AppearanceSettingsScreen(
    controller: controller,
    initialSection: section,
  );
  if (row != null) page = KitArrivalScope(rowId: row, child: page);
  return MaterialApp(
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    theme: AppTheme.dark(),
    home: page,
  );
}

Future<void> _mount(
  WidgetTester tester,
  ConnectionController controller, {
  AppearanceSection? section,
  String? row,
}) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(_page(controller, section: section, row: row));
  await tester.pumpAndSettle();
}

Future<void> _tap(WidgetTester tester, String key) async {
  final finder = find.byKey(ValueKey(key));
  await bringIntoView(tester, finder);
  await tester.tap(finder);
  await tester.pumpAndSettle();
}

KitGuide _guide(WidgetTester tester) =>
    tester.widget<KitGuide>(find.byType(KitGuide));

KitGuideItem _item(WidgetTester tester, String id) =>
    _guide(tester).items.firstWhere((item) => item.id == id);

/// The Next bar's step, or null when it shows none.
String? _nextLabel(WidgetTester tester) => _guide(tester).next?.label;

final _nextBar = find.byKey(const ValueKey('appearance-guide-next'));
final _onePage = find.byKey(const ValueKey('appearance-one-page'));

Future<void> _next(WidgetTester tester) async {
  await tester.tap(
    find.descendant(of: _nextBar, matching: find.byType(KitButton)),
  );
  await tester.pumpAndSettle();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('first visit, guided', () {
    testWidgets('starts with Colours: the Next bar, a dot on Colours and '
        'Skip the guide', (tester) async {
      final (controller, _) = await _controller();
      addTearDown(controller.dispose);
      await _mount(tester, controller);

      expect(_guide(tester).question, isNull);
      for (final group in ['colours', 'style', 'background', 'comfort']) {
        expect(find.byKey(ValueKey('appearance-group-$group')), findsOneWidget);
      }
      expect(_nextLabel(tester), _en.appearanceGuideStartButton);
      expect(find.text(_en.appearanceGuideCountIntro(10)), findsOneWidget);
      expect(_item(tester, 'colours').next, isTrue);
      expect(_item(tester, 'style').next, isFalse);
      // Each chip shows what is set now.
      expect(_item(tester, 'colours').value, 'Dark, Graphite');
      expect(_item(tester, 'comfort').value, 'Default, full motion');
      expect(find.text(_en.appearanceGuideStart), findsOneWidget);
      expect(
        find.byKey(const ValueKey('appearance-guide-skip')),
        findsOneWidget,
      );
      // The words name what is on screen (rows) and Skip starts where the
      // sentence above it starts.
      expect(_en.appearanceGuideStart, isNot(contains('chip')));
      expect(
        tester.getTopLeft(find.text(_en.appearanceGuideSkip)).dx,
        tester.getTopLeft(find.text(_en.appearanceGuideStart)).dx,
      );
    });

    testWidgets('walks every question in order, across the groups, to '
        'Finish and All set', (tester) async {
      final (controller, prefs) = await _controller();
      addTearDown(controller.dispose);
      await _mount(tester, controller);

      await _next(tester);
      expect(_guide(tester).question?.id, 'mode');
      expect(find.text(_en.appearanceAskMode), findsOneWidget);
      expect(_nextLabel(tester), _en.appearanceGuideNext('Theme'));
      expect(_guide(tester).next!.ready, isFalse);
      expect(_item(tester, 'theme').next, isFalse);

      // An answer fills the Next button in and puts the dot on Theme.
      await _tap(tester, 'appearance-mode-light');
      expect(controller.appearance.value, AppAppearance.light);
      expect(_guide(tester).next!.ready, isTrue);
      expect(_item(tester, 'theme').next, isTrue);

      await _next(tester);
      expect(_guide(tester).question?.id, 'theme');
      expect(_item(tester, 'mode').done, isTrue);
      await _next(tester);
      expect(_guide(tester).question?.id, 'accentColour');
      // The last question of a group names the next group.
      expect(_nextLabel(tester), _en.appearanceGuideNext('Style'));

      final asked = <String>[];
      while (_nextLabel(tester) != _en.appearanceGuideFinish) {
        await _next(tester);
        asked.add(_guide(tester).question!.id);
      }
      expect(asked, [
        'style',
        'shape',
        'look',
        'strength',
        'text',
        'motion',
        'glow',
      ]);
      expect(find.text(_en.appearanceGuideCountAt(10, 10)), findsOneWidget);

      await _next(tester);
      expect(_guide(tester).question, isNull);
      expect(
        find.byKey(const ValueKey('appearance-guide-all-set')),
        findsOneWidget,
      );
      for (final group in AppearanceGroup.values) {
        expect(_item(tester, group.name).done, isTrue, reason: group.name);
      }
      expect(_nextLabel(tester), isNull);
      expect(find.text(_en.appearanceGuideSavesAsYouGo), findsOneWidget);
      // Finished is remembered on this device.
      expect(AppearanceGuideStore(prefs).read().done, isTrue);

      // A chip tapped now opens the returning page: every question at once,
      // no bar.
      await _tap(tester, 'appearance-group-colours');
      expect(_onePage, findsOneWidget);
      expect(_nextBar, findsNothing);
      expect(find.byKey(const ValueKey('theme-pack-nord')), findsOneWidget);
    });

    testWidgets('Skip the guide hides it for good', (tester) async {
      final (controller, prefs) = await _controller();
      addTearDown(controller.dispose);
      await _mount(tester, controller);
      await _tap(tester, 'appearance-guide-skip');
      expect(_nextBar, findsNothing);
      expect(find.byType(KitGuide), findsNothing);
      expect(_onePage, findsOneWidget);
      expect(AppearanceGuideStore(prefs).read().done, isTrue);
    });

    testWidgets('a chip opens its first question at once; All goes back', (
      tester,
    ) async {
      final (controller, _) = await _controller();
      addTearDown(controller.dispose);
      await _mount(tester, controller);
      await _tap(tester, 'appearance-group-background');
      expect(_guide(tester).question?.id, 'look');
      expect(_guide(tester).group?.label, _en.appearanceGroupBackground);
      expect(find.text(_en.appearanceChangesLook), findsNothing);
      expect(find.textContaining(_en.appearanceChangesLook), findsOneWidget);
      await _tap(tester, 'appearance-guide-back');
      expect(_guide(tester).question, isNull);
    });
  });

  group('returning', () {
    testWidgets('remembered across a restart: one page, no bar, '
        'Guide me again brings the guide back', (tester) async {
      final (controller, prefs) = await _controller();
      await _mount(tester, controller);
      await _tap(tester, 'appearance-guide-skip');
      // Theme is changed in place, one tap.
      await _tap(tester, 'theme-pack-nord');
      expect(controller.themePack.value, ThemePackId.nord);
      controller.dispose();

      // A restart: a new controller over the same device storage.
      await tester.pumpWidget(const SizedBox());
      final again = ConnectionController(_MemoryProfileStore(prefs: prefs));
      addTearDown(again.dispose);
      await _mount(tester, again);
      expect(_nextBar, findsNothing);
      expect(find.byType(KitGuide), findsNothing);
      expect(_onePage, findsOneWidget);
      // No Changed last row: the page itself shows every choice.
      expect(
        find.byKey(const ValueKey('appearance-changed-last')),
        findsNothing,
      );

      // Guide me again: the chips, the bar and the dot come back.
      await _tap(tester, 'appearance-guide-again');
      expect(_onePage, findsNothing);
      expect(_nextLabel(tester), _en.appearanceGuideStartButton);
      expect(_item(tester, 'colours').next, isTrue);
      expect(AppearanceGuideStore(prefs).read().done, isFalse);
    });

    testWidgets('Reset to defaults asks in the page, names what resets, and '
        'Keep my choices keeps them', (tester) async {
      final (controller, _) = await _controller({
        'oc.appearanceGuide': '{"done":true}',
        'oc.themePack': 'nord',
        'oc.appearance': 'light',
        'oc.effectsSurfaceStyle': 'brutal',
        'oc.textSize': 'large',
        'oc.appearanceAccent': 'pink',
      });
      addTearDown(controller.dispose);
      await _mount(tester, controller);

      await _tap(tester, 'appearance-reset');
      expect(find.text(_en.appearanceGuideResetConfirm), findsOneWidget);
      await _tap(tester, 'appearance-reset-keep');
      expect(find.text(_en.appearanceGuideResetConfirm), findsNothing);
      expect(controller.themePack.value, ThemePackId.nord);

      await _tap(tester, 'appearance-reset');
      await _tap(tester, 'appearance-reset-do');
      expect(controller.themePack.value, ThemePackId.opencode);
      expect(controller.appearance.value, AppAppearance.dark);
      expect(controller.appearanceAccent.value, isNull);
      expect(controller.textSize.value, KitTextSize.normal);
      expect(controller.effects.value.surfaceStyle, KitSurfaceStyle.solid);
      // The default background is the page's plain colour.
      expect(controller.effects.value.liveBackground, isFalse);
    });
  });

  group('Saver', () {
    testWidgets('Style and Background read Off in Saver, Next skips them, '
        'and opening one says why with Turn off Saver', (tester) async {
      final (controller, _) = await _controller({'oc.appearance': 'saver'});
      addTearDown(controller.dispose);
      await _mount(tester, controller);
      expect(_item(tester, 'style').value, _en.appearanceOffInSaver);
      expect(_item(tester, 'background').value, _en.appearanceOffInSaver);
      expect(_item(tester, 'style').held, isTrue);

      // Accent's Next goes past Style and Background to Text & motion.
      await _tap(tester, 'appearance-group-colours');
      await _tap(tester, 'appearance-question-accentColour');
      expect(
        _nextLabel(tester),
        _en.appearanceGuideNext(_en.appearanceGroupComfort),
      );
      await _next(tester);
      expect(_guide(tester).question?.id, 'text');

      await _tap(tester, 'appearance-guide-back');
      await _tap(tester, 'appearance-group-background');
      expect(find.text(_en.appearanceSaverHoldsBackground), findsOneWidget);
      expect(find.byKey(const ValueKey('live-style-off')), findsNothing);
      await _tap(tester, 'appearance-turn-off-saver');
      expect(controller.appearance.value, AppAppearance.dark);
      expect(find.byKey(const ValueKey('live-style-off')), findsOneWidget);
    });
  });

  testWidgets('with Look Off, Strength says to choose a look first and '
      'links to Look', (tester) async {
    final (controller, _) = await _controller();
    addTearDown(controller.dispose);
    await _mount(tester, controller);
    await _tap(tester, 'appearance-group-background');
    await _tap(tester, 'live-style-off');
    expect(controller.effects.value.liveBackground, isFalse);
    expect(_item(tester, 'look').value, _en.appearanceLookOff);
    expect(_item(tester, 'strength').value, _en.appearanceStrengthNoLook);

    await _tap(tester, 'appearance-question-strength');
    expect(find.text(_en.appearanceStrengthNeedsLook), findsOneWidget);
    expect(
      find.byKey(const ValueKey('effects-live-intensity-bold')),
      findsNothing,
    );
    await _tap(tester, 'appearance-choose-look');
    expect(_guide(tester).question?.id, 'look');
  });

  testWidgets('a new Style resets Shape, and the Shape chip states the shape', (
    tester,
  ) async {
    final (controller, _) = await _controller({'oc.effectsCorners': 'sharp'});
    addTearDown(controller.dispose);
    await _mount(tester, controller);
    await _tap(tester, 'appearance-group-style');
    await _tap(tester, 'appearance-surface-soft');
    expect(controller.effects.value.fineTune.isNone, isTrue);
    final shape = _item(tester, 'shape').value;
    expect(shape, _en.appearanceShapeValue('Round', 'Roomy'));
    expect(shape, isNot(contains('Reset')));
    await _tap(tester, 'appearance-question-shape');
    expect(_item(tester, 'shape').value, shape);
  });

  group('search and links', () {
    for (final (section, question) in [
      (AppearanceSection.mode, 'mode'),
      (AppearanceSection.theme, 'theme'),
      (AppearanceSection.effects, 'motion'),
      (AppearanceSection.text, 'text'),
      (AppearanceSection.background, 'look'),
      (AppearanceSection.style, 'style'),
    ]) {
      testWidgets('${section.name} lands on $question', (tester) async {
        final (controller, _) = await _controller();
        addTearDown(controller.dispose);
        await _mount(tester, controller, section: section);
        expect(_guide(tester).question?.id, question);
      });
    }

    for (final (row, question) in [
      ('effects-glow', 'glow'),
      ('effects-motion', 'motion'),
      ('effects-live-background', 'look'),
      ('effects-text', 'text'),
    ]) {
      testWidgets('the row $row lands on $question', (tester) async {
        final (controller, _) = await _controller();
        addTearDown(controller.dispose);
        await _mount(
          tester,
          controller,
          section: AppearanceSection.effects,
          row: row,
        );
        expect(_guide(tester).question?.id, question);
      });
    }

    testWidgets('Back from a linked question returns to where it came from; '
        'Back from a question opened here returns to the chips', (
      tester,
    ) async {
      final (controller, _) = await _controller();
      addTearDown(controller.dispose);
      tester.view.physicalSize = const Size(412, 915);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      Widget host(AppearanceSection? section) => MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        theme: AppTheme.dark(),
        home: Builder(
          builder: (context) => Center(
            child: KitButton.primary(
              label: 'Open',
              onPressed: () => pushKitPage<void>(
                context,
                (_) => AppearanceSettingsScreen(
                  controller: controller,
                  initialSection: section,
                ),
              ),
            ),
          ),
        ),
      );

      await tester.pumpWidget(host(AppearanceSection.theme));
      await tester.tap(find.text('Open'));
      await tester.pumpAndSettle();
      expect(_guide(tester).question?.id, 'theme');
      await tester.binding.handlePopRoute();
      await tester.pumpAndSettle();
      expect(find.byType(AppearanceSettingsScreen), findsNothing);

      await tester.pumpWidget(const SizedBox());
      await tester.pumpWidget(host(null));
      await tester.tap(find.text('Open'));
      await tester.pumpAndSettle();
      await _tap(tester, 'appearance-group-colours');
      await tester.binding.handlePopRoute();
      await tester.pumpAndSettle();
      expect(find.byType(AppearanceSettingsScreen), findsOneWidget);
      expect(_guide(tester).question, isNull);
      await tester.binding.handlePopRoute();
      await tester.pumpAndSettle();
      expect(find.byType(AppearanceSettingsScreen), findsNothing);
    });
  });

  testWidgets('the tour\'s anchors are the four chips of the start view', (
    tester,
  ) async {
    final (controller, _) = await _controller();
    addTearDown(controller.dispose);
    await _mount(tester, controller);
    for (final anchor in MakeItYoursAnchors.all) {
      expect(find.byKey(anchor), findsOneWidget);
    }
  });

  testWidgets('only one question is built at a time', (tester) async {
    final (controller, _) = await _controller();
    addTearDown(controller.dispose);
    await _mount(tester, controller);
    await _tap(tester, 'appearance-group-colours');
    expect(find.byKey(const ValueKey('appearance-settings-entry')), findsOne);
    expect(find.byKey(const ValueKey('appearance-accent')), findsNothing);
    expect(find.byKey(const ValueKey('theme-pack-nord')), findsNothing);
  });

  testWidgets('a wide window has no preview: the chips lead and the question '
      'follows them', (tester) async {
    final (controller, _) = await _controller();
    addTearDown(controller.dispose);
    tester.view.physicalSize = const Size(1280, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(_page(controller));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('appearance-group-colours')));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('appearance-preview')), findsNothing);
    final chip = tester.getRect(
      find.byKey(const ValueKey('appearance-question-mode')),
    );
    final question = tester.getRect(find.text(_en.appearanceAskMode));
    expect(chip.bottom, lessThanOrEqualTo(question.top));
    expect(tester.takeException(), isNull);
  });
}
