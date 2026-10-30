// Gate G5's own tests (docs/ux-system/revamp/STANDARDS.md §18,
// docs/qa/gate-G5-2026-09-26/README.md): each check fails on a small tree
// that breaks it, the baseline cannot grow past its ceiling or keep a stale
// entry, and a shot is checked in the theme its gallery did not ask for.
// No golden is compared here.
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/kit/style/kit_style_theme.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'kit_gallery.dart';

/// The one shot the ceiling holds; tests that exercise the baseline use it.
const _ceilingShot = 'kit_confirm_destructive_text2_1280x800_light';

/// Pumps [child] centred on a white 412×915 screen and returns the G5
/// failure message for it, or null when G5 passes.
Future<String?> _g5(
  WidgetTester tester,
  Widget child, {
  String shot = 'zz_g5_fixture_light',
  TextDirection direction = TextDirection.ltr,
  KitGalleryG5Baseline baseline = const {},
}) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  final semantics = tester.ensureSemantics();
  try {
    await tester.pumpWidget(
      MaterialApp(
        debugShowCheckedModeBanner: false,
        theme: ThemeData(scaffoldBackgroundColor: Colors.white),
        home: Directionality(
          textDirection: direction,
          child: Scaffold(body: Center(child: child)),
        ),
      ),
    );
    await expectKitGalleryAccessible(
      tester,
      shot: shot,
      direction: direction,
      baseline: baseline,
    );
    return null;
  } on TestFailure catch (error) {
    return error.message;
  } finally {
    semantics.dispose();
  }
}

/// A 20 dp tall labelled button.
Widget _smallButton() => TextButton(
  style: TextButton.styleFrom(
    minimumSize: const Size(0, 20),
    fixedSize: const Size(120, 20),
    tapTargetSize: MaterialTapTargetSize.shrinkWrap,
    padding: EdgeInsets.zero,
  ),
  onPressed: () {},
  child: const Text('Tiny'),
);

/// Text in its own semantics node, 2.3:1 on white.
Widget _faint(String label) => Semantics(
  container: true,
  child: Text(
    label,
    style: const TextStyle(color: Color(0xFFAAAAAA), fontSize: 14),
  ),
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('a clean tree passes', (tester) async {
    expect(
      await _g5(
        tester,
        Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text('Title', style: TextStyle(color: Colors.black)),
            FilledButton(onPressed: () {}, child: const Text('Go')),
          ],
        ),
      ),
      isNull,
    );
  });

  testWidgets('androidTapTarget: a 20 dp tall button fails', (tester) async {
    final message = await _g5(tester, _smallButton());
    expect(message, contains('[androidTapTarget] node "Tiny"'));
    expect(message, contains('expected tap target size of at least'));
  });

  testWidgets('labeledTapTarget: an icon button with no label fails', (
    tester,
  ) async {
    final message = await _g5(
      tester,
      IconButton(onPressed: () {}, icon: const Icon(Icons.add)),
    );
    expect(message, contains('[labeledTapTarget] node ""'));
    expect(message, contains('isButton'));
  });

  // S6 found Soft's permission card failing here. The framework's check
  // reads no colour from the text: it splits the pixels around the words at
  // their mean lightness and takes the most common colour on each side. The
  // command's glyphs are a pixel thick, so on Soft's shaded well both "most
  // common" colours were bands of the well (1.12:1). The words' own colour
  // on the well is what G5 measures now.
  group('textContrast on a shaded ground', () {
    setUpAll(loadKitGalleryFonts);
    setUp(() => SharedPreferences.setMockInitialValues({}));

    Widget card() => MaterialApp(
      debugShowCheckedModeBanner: false,
      theme: AppTheme.light(),
      home: KitEffectsScope(
        effects: const KitEffects(surfaceStyle: KitSurfaceStyle.soft),
        child: kitStyleTheme(
          child: Scaffold(
            body: Center(
              child: KitRequestCard.ask(
                kind: KitRequestKind.permission,
                title: 'Run a shell command',
                who: 'fox',
                reason: KitNeedsYouReason.decision,
                ifIgnored: 'The agent waits; nothing is lost.',
                announcement: 'Permission needed: Run a shell command',
                summary: 'flutter test --concurrency=1',
                answers: KitRequestDecide(
                  allowLabel: 'Run once',
                  rejectLabel: "Don't run",
                  onAllow: () {},
                  onReject: () {},
                ),
              ),
            ),
          ),
        ),
      ),
    );

    Future<String?> check(WidgetTester tester) async {
      tester.view.physicalSize = const Size(412, 915);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final semantics = tester.ensureSemantics();
      try {
        await tester.pumpWidget(card());
        await tester.pumpAndSettle();
        await expectKitGalleryAccessible(
          tester,
          shot: 'zz_g5_fixture_light',
          direction: TextDirection.ltr,
          baseline: const {},
        );
        return null;
      } on TestFailure catch (error) {
        return error.message;
      } finally {
        semantics.dispose();
      }
    }

    testWidgets("Soft's permission command passes", (tester) async {
      expect(await check(tester), isNull);
    });
  });

  testWidgets('textContrast: 2.3:1 text fails on its own node', (tester) async {
    final message = await _g5(tester, _faint('Faint'));
    expect(message, contains('[textContrast] node "Faint"'));
    expect(message, contains('Expected contrast ratio of at least 4.5'));
  });

  testWidgets('readingOrder: reversed sort keys fail (goes back up)', (
    tester,
  ) async {
    final message = await _g5(
      tester,
      Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Semantics(
            container: true,
            sortKey: const OrdinalSortKey(2),
            child: const Text(
              'Top line',
              style: TextStyle(color: Colors.black),
            ),
          ),
          const SizedBox(height: 24),
          Semantics(
            container: true,
            sortKey: const OrdinalSortKey(1),
            child: const Text(
              'Bottom line',
              style: TextStyle(color: Colors.black),
            ),
          ),
        ],
      ),
    );
    expect(message, contains('[readingOrder] node "Top line"'));
    expect(message, contains('goes back up'));
  });

  for (final reversed in [false, true]) {
    testWidgets('readingOrder: right to left, '
        '${reversed ? 'read left first fails' : 'read right first passes'}', (
      tester,
    ) async {
      Widget cell(String label, double key) => Semantics(
        container: true,
        sortKey: OrdinalSortKey(key),
        child: Text(label, style: const TextStyle(color: Colors.black)),
      );
      final message = await _g5(
        tester,
        direction: TextDirection.rtl,
        Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            // First child is at the right (the start) in RTL.
            cell('Start', reversed ? 2 : 1),
            const SizedBox(width: 24),
            cell('End', reversed ? 1 : 2),
          ],
        ),
      );
      if (reversed) {
        expect(message, contains('[readingOrder] node "Start"'));
        expect(message, contains('goes back towards the start of the line'));
      } else {
        expect(message, isNull);
      }
    });
  }

  for (final reversePanes in [false, true]) {
    testWidgets(
      'readingOrder: independent columns ${reversePanes ? "reject reversed panes" : "restart at top"}',
      (tester) async {
        Widget pane(String id, double order) => Expanded(
          child: Semantics(
            container: true,
            identifier: '${KitScreen.paneSemanticsPrefix}$id',
            sortKey: OrdinalSortKey(order),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text('$id top', style: const TextStyle(color: Colors.black)),
                const SizedBox(height: 80),
                Text('$id bottom', style: const TextStyle(color: Colors.black)),
              ],
            ),
          ),
        );
        final message = await _g5(
          tester,
          Row(
            children: [
              pane('list', reversePanes ? 2 : 1),
              pane('detail', reversePanes ? 1 : 2),
            ],
          ),
        );
        if (reversePanes) {
          expect(message, contains('adaptive panes go back'));
        } else {
          expect(message, isNull);
        }
      },
    );
  }

  testWidgets('readingOrder: reversed rows inside a pane still fail', (
    tester,
  ) async {
    final message = await _g5(
      tester,
      Semantics(
        container: true,
        identifier: '${KitScreen.paneSemanticsPrefix}detail',
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Semantics(
              container: true,
              sortKey: const OrdinalSortKey(2),
              child: const Text(
                'Top row',
                style: TextStyle(color: Colors.black),
              ),
            ),
            const SizedBox(height: 80),
            Semantics(
              container: true,
              sortKey: const OrdinalSortKey(1),
              child: const Text(
                'Bottom row',
                style: TextStyle(color: Colors.black),
              ),
            ),
          ],
        ),
      ),
    );
    expect(message, contains('goes back up'));
  });

  group('baseline', () {
    test('the committed baseline is within the ceiling', () {
      expect(kitGalleryG5OverCeiling(readKitGalleryG5Baseline()), isEmpty);
    });

    testWidgets('an entry outside the ceiling fails', (tester) async {
      final message = await _g5(
        tester,
        _faint('Faint'),
        baseline: {
          'zz_g5_fixture_light': {
            'textContrast': {'Faint'},
          },
        },
      );
      expect(message, contains('outside kitGalleryG5Ceiling'));
      expect(message, contains('zz_g5_fixture_light [textContrast] "Faint"'));
    });

    testWidgets('a listed node still failing passes', (tester) async {
      expect(
        await _g5(
          tester,
          _faint('Cancel'),
          shot: _ceilingShot,
          baseline: kitGalleryG5Ceiling,
        ),
        isNull,
      );
    });

    testWidgets('another node failing the listed check still fails', (
      tester,
    ) async {
      final message = await _g5(
        tester,
        Column(
          mainAxisSize: MainAxisSize.min,
          children: [_faint('Cancel'), _faint('Other')],
        ),
        shot: _ceilingShot,
        baseline: kitGalleryG5Ceiling,
      );
      expect(message, contains('[textContrast] node "Other"'));
      expect(message, isNot(contains('node "Cancel"')));
    });

    testWidgets('a listed node that now passes fails as stale', (tester) async {
      final message = await _g5(
        tester,
        const Text('Cancel', style: TextStyle(color: Colors.black)),
        shot: _ceilingShot,
        baseline: kitGalleryG5Ceiling,
      );
      expect(message, contains('Stale baseline'));
      expect(message, contains('[textContrast] "Cancel"'));
    });
  });

  group('both themes', () {
    testWidgets('a shot name must end in its theme', (tester) async {
      await expectLater(
        kitGalleryShot(
          tester,
          name: 'zz_g5_unthemed',
          size: const Size(412, 915),
          light: false,
          open: (_) {},
        ),
        throwsA(isA<ArgumentError>()),
      );
    });

    testWidgets('a dark-only gallery is still checked in light', (
      tester,
    ) async {
      // Readable on the dark dialog, 1.1:1 on the light one.
      String? message;
      try {
        await kitGalleryShot(
          tester,
          name: 'zz_g5_theme_dark',
          size: const Size(412, 915),
          light: false,
          open: (context) => showDialog<void>(
            context: context,
            builder: (_) => Dialog(
              child: Padding(
                padding: const EdgeInsets.all(24),
                child: Semantics(
                  container: true,
                  child: const Text(
                    'Pale',
                    style: TextStyle(color: Color(0xFFDDDDDD), fontSize: 14),
                  ),
                ),
              ),
            ),
          ),
        );
      } on TestFailure catch (error) {
        message = error.message;
      }
      // Fails at G5 in light, before any golden compare.
      expect(
        message,
        contains(
          'G5 (docs/ux-system/revamp/STANDARDS.md §18) failed for '
          'zz_g5_theme_light',
        ),
      );
      expect(message, contains('[textContrast] node "Pale"'));
    });
  });

  // Known limit, recorded under NOT proven: the guidelines judge semantics
  // nodes, not gesture areas. A bare GestureDetector adds its tap to the
  // nearest enclosing node, so a 20 dp tap zone inside a larger labelled
  // node is measured as that node. This is why the first "small tap target"
  // fixture of run 3 passed G5. If this starts failing, the gap closed:
  // delete this test and the NOT proven line.
  testWidgets('known gap: a bare 20 dp GestureDetector inside a larger '
      'labelled node is not measured', (tester) async {
    expect(
      await _g5(
        tester,
        Semantics(
          container: true,
          label: 'Card',
          child: SizedBox(
            width: 200,
            height: 100,
            child: Center(
              child: GestureDetector(
                onTap: () {},
                child: const SizedBox(width: 20, height: 20),
              ),
            ),
          ),
        ),
      ),
      isNull,
    );
  });
}
