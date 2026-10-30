// Settings › Appearance › Style › Shape (guided Appearance, owner approved
// 2026-10-23): Corners and Spacing over the chosen style; Depth and Edges
// are gone, each style keeps its own. Each choice is saved and shows on the
// page itself at once; a choice equal to the style's own is stored as "the
// style's default"; a new Style starts Shape over and its chip says so; the
// reset shows only while something differs; Saver holds it with its reason.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/kit/style/kit_style.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
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

Widget _page(ConnectionController controller, {bool highContrast = false}) =>
    MaterialApp(
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      theme: AppTheme.dark(),
      builder: (context, child) => MediaQuery(
        data: MediaQuery.of(context).copyWith(highContrast: highContrast),
        child: child!,
      ),
      home: AppearanceSettingsScreen(controller: controller),
    );

/// Opens Appearance at Style › Shape.
Future<void> _open(
  WidgetTester tester,
  ConnectionController controller, {
  bool highContrast = false,
}) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  addTearDown(controller.dispose);
  await tester.pumpWidget(_page(controller, highContrast: highContrast));
  await tester.pumpAndSettle();
  await openAppearance(tester, 'style', 'shape');
}

Future<void> _tap(WidgetTester tester, String key) async {
  final finder = find.byKey(ValueKey(key));
  await tester.ensureVisible(finder);
  await tester.pumpAndSettle();
  await tester.tap(finder);
  await tester.pumpAndSettle();
}

/// The knobs the live preview is drawn with: inside its window (the page's
/// controls around it stay plain, KitPlainScope).
/// The knobs in force on the page itself.
KitKnobs _shown(WidgetTester tester) => KitStyles.knobsOf(
  tester.element(find.byKey(const ValueKey('appearance-settings'))),
);

Finder get _reset => find.byKey(const ValueKey('appearance-shape-reset'));

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('Corners and Spacing are saved and show in the preview at once', (
    tester,
  ) async {
    final (controller, prefs) = await _controller();
    await _open(tester, controller);
    expect(_shown(tester), const KitKnobs());

    await _tap(tester, 'appearance-fine-tune-corners-sharp');
    expect(prefs.getString('oc.effectsCorners'), 'sharp');
    expect(_shown(tester).corners, KitCorners.sharp);

    await _tap(tester, 'appearance-fine-tune-density-compact');
    expect(prefs.getString('oc.effectsDensity'), 'compact');
    expect(
      _shown(tester),
      const KitKnobs(corners: KitCorners.sharp, density: KitDensity.compact),
    );
    expect(
      controller.effects.value.fineTune,
      const KitFineTune(corners: KitCorners.sharp, density: KitDensity.compact),
    );
  });

  testWidgets('Depth and Edges are gone: each style keeps its own', (
    tester,
  ) async {
    final (controller, prefs) = await _controller({
      'oc.effectsSurfaceStyle': 'soft',
      'oc.effectsDepth': 'flat',
      'oc.effectsEdges': 'none',
    });
    await _open(tester, controller);
    expect(find.text(_en.appearanceFineTuneDepth), findsNothing);
    expect(find.text(_en.appearanceFineTuneEdges), findsNothing);
    // What an earlier version stored is ignored: Soft's own depth and edges.
    final soft = KitFineTunePanel.read(
      const KitEffects(surfaceStyle: KitSurfaceStyle.soft),
    ).defaults;
    expect(_shown(tester), soft);
    expect(controller.effects.value.fineTune.depth, isNull);
    expect(controller.effects.value.fineTune.edges, isNull);
    expect(prefs.getString('oc.effectsSurfaceStyle'), 'soft');
  });

  testWidgets('the style\'s own shape is named, and choosing it again stores '
      '"the style\'s default"', (tester) async {
    final (controller, prefs) = await _controller({
      'oc.effectsSurfaceStyle': 'brutal',
    });
    await _open(tester, controller);
    // Brutal starts at Soft corners and roomy.
    expect(
      find.text(_en.appearanceShapeNote('Brutal', 'Soft', 'Roomy')),
      findsOneWidget,
    );

    await _tap(tester, 'appearance-fine-tune-corners-round');
    expect(prefs.getString('oc.effectsCorners'), 'round');
    await _tap(tester, 'appearance-fine-tune-corners-soft');
    expect(prefs.containsKey('oc.effectsCorners'), isFalse);
    expect(controller.effects.value.fineTune.isNone, isTrue);
    expect(_reset, findsNothing);
  });

  testWidgets('a new Style starts Shape over, and the Shape chip states the '
      'style\'s own shape', (tester) async {
    final (controller, prefs) = await _controller();
    await _open(tester, controller);
    await _tap(tester, 'appearance-fine-tune-corners-sharp');
    await _tap(tester, 'appearance-fine-tune-density-compact');
    expect(controller.effects.value.fineTune.isNone, isFalse);

    await _tap(tester, 'appearance-question-style');
    await _tap(tester, 'appearance-surface-paper');
    expect(controller.effects.value.surfaceStyle, KitSurfaceStyle.paper);
    expect(controller.effects.value.fineTune.isNone, isTrue);
    expect(prefs.containsKey('oc.effectsCorners'), isFalse);
    expect(prefs.containsKey('oc.effectsDensity'), isFalse);
    // Paper's own: sharp corners, comfortable.
    expect(_shown(tester), const KitKnobs(corners: KitCorners.sharp));
    final shape = find.byKey(const ValueKey('appearance-question-shape'));
    expect(
      find.descendant(
        of: shape,
        matching: find.text(_en.appearanceShapeValue('Sharp', 'Roomy')),
      ),
      findsOneWidget,
    );

    await _tap(tester, 'appearance-question-shape');
    // The chip states the shape, never "Reset to Paper defaults".
    expect(
      find.descendant(
        of: shape,
        matching: find.text(_en.appearanceFineTuneReset('Paper')),
      ),
      findsNothing,
    );
    expect(
      find.descendant(
        of: shape,
        matching: find.text(_en.appearanceShapeValue('Sharp', 'Roomy')),
      ),
      findsOneWidget,
    );
    expect(_reset, findsNothing);
  });

  testWidgets('the reset shows only while a choice differs, and puts the '
      'style\'s own shape back', (tester) async {
    final (controller, prefs) = await _controller({
      'oc.effectsSurfaceStyle': 'soft',
    });
    await _open(tester, controller);
    expect(_reset, findsNothing);

    await _tap(tester, 'appearance-fine-tune-corners-sharp');
    await _tap(tester, 'appearance-fine-tune-density-compact');
    expect(_reset, findsOneWidget);
    expect(find.text(_en.appearanceFineTuneReset('Soft')), findsOneWidget);

    await _tap(tester, 'appearance-shape-reset');
    expect(controller.effects.value.fineTune, KitFineTune.none);
    expect(controller.effects.value.surfaceStyle, KitSurfaceStyle.soft);
    expect(prefs.containsKey('oc.effectsCorners'), isFalse);
    expect(prefs.containsKey('oc.effectsDensity'), isFalse);
    expect(
      _shown(tester),
      const KitKnobs(depth: KitDepth.raised, edges: KitEdges.none),
    );
    expect(_reset, findsNothing);
  });

  testWidgets('a stored choice equal to the style\'s own offers no reset', (
    tester,
  ) async {
    final (controller, _) = await _controller({
      'oc.effectsSurfaceStyle': 'paper',
      'oc.effectsCorners': 'sharp',
    });
    await _open(tester, controller);
    expect(controller.effects.value.fineTune.isNone, isFalse);
    expect(_reset, findsNothing);
  });

  testWidgets('high contrast still lets Corners change', (tester) async {
    final (controller, _) = await _controller();
    await _open(tester, controller, highContrast: true);
    await _tap(tester, 'appearance-fine-tune-corners-soft');
    expect(controller.effects.value.fineTune.corners, KitCorners.soft);
  });
}
