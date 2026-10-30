// KitInkLayer (owner rule 2026-10-26, "Yes, max Android native animation
// and support."; docs/ux-system/kit-v2.md top note): on Android a press on
// a kit control draws the native Material ripple (InkSparkle) over its
// face, finished by a tap and faded by a scroll; other platforms and
// reduced motion keep the flat pressed fill.
import 'package:flutter/gestures.dart' show kPressTimeout;
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

import 'kit_motion_still.dart';

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

/// The ripple surfaces the kit put over its parts.
final _inkSurfaces = find.descendant(
  of: find.byType(KitInkLayer),
  matching: find.byWidgetPredicate(
    (w) => w is Material && w.type == MaterialType.transparency,
  ),
);

/// The ink features drawn now on the ripple surface of the part that
/// shows [text] (the first part when null).
List<InkFeature> _ripples(WidgetTester tester, [String? text]) {
  final surface = text == null
      ? _inkSurfaces.first
      : find
            .descendant(
              of: find
                  .ancestor(
                    of: find.text(text),
                    matching: find.byType(KitInkLayer),
                  )
                  .first,
              matching: find.byWidgetPredicate(
                (w) => w is Material && w.type == MaterialType.transparency,
              ),
            )
            .last;
  final under = find.descendant(of: surface, matching: find.byType(SizedBox));
  final controller = Material.of(tester.element(under.first));
  return List.of(
    (controller as dynamic).debugInkFeatures as List? ?? [],
  ).cast<InkFeature>();
}

void main() {
  group('press feedback is the native ripple on Android', () {
    testWidgets('a row press draws an InkSparkle that a tap finishes', (
      tester,
    ) async {
      var taps = 0;
      await _pump(
        tester,
        SizedBox(
          width: 360,
          child: KitRow(title: 'Open project', onTap: () => taps++),
        ),
      );
      final press = await tester.startGesture(
        tester.getCenter(find.text('Open project')),
      );
      await tester.pump();
      expect(_ripples(tester), [isA<InkSparkle>()]);
      await press.up();
      await tester.pumpAndSettle();
      expect(taps, 1);
      expect(_ripples(tester), isEmpty, reason: 'the ripple faded out');
    });

    testWidgets('a scroll that takes the touch fades the ripple at once', (
      tester,
    ) async {
      await _pump(
        tester,
        SizedBox(
          height: 400,
          width: 360,
          child: ListView(
            children: [
              for (var i = 0; i < 30; i++)
                KitRow(title: 'Row $i', onTap: () {}),
            ],
          ),
        ),
      );
      final press = await tester.startGesture(
        tester.getCenter(find.text('Row 2')),
      );
      // Inside a scrollable the press waits the platform's tap timeout.
      await tester.pump(kPressTimeout);
      await tester.pump();
      expect(_ripples(tester, 'Row 2'), [isA<InkSparkle>()]);
      await press.moveBy(const Offset(0, -80));
      await press.up();
      await tester.pumpAndSettle();
      expect(_ripples(tester, 'Row 2'), isEmpty);
    });

    testWidgets('buttons, icon buttons and chips draw it too', (tester) async {
      await _pump(
        tester,
        Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            KitButton.primary(
              label: 'Connect',
              onPressed: () {},
              expand: false,
            ),
            KitIconButton(
              icon: Icons.refresh,
              tooltip: 'Reload',
              onPressed: () {},
            ),
            KitChip.action(label: 'Filter', onPressed: () {}),
          ],
        ),
      );
      for (final target in [
        find.text('Connect'),
        find.byIcon(Icons.refresh),
        find.text('Filter'),
      ]) {
        final press = await tester.startGesture(tester.getCenter(target));
        await tester.pump();
        final layer = find.ancestor(
          of: target,
          matching: find.byType(KitInkLayer),
        );
        final surface = find.descendant(
          of: layer.first,
          matching: find.byWidgetPredicate(
            (w) => w is Material && w.type == MaterialType.transparency,
          ),
        );
        final controller = Material.of(
          tester.element(
            find.descendant(of: surface.last, matching: find.byType(SizedBox)),
          ),
        );
        expect(
          (controller as dynamic).debugInkFeatures,
          contains(isA<InkSparkle>()),
          reason: '$target',
        );
        await press.up();
        await tester.pumpAndSettle();
      }
    });

    testWidgets('under reduced motion a press keeps the flat fill', (
      tester,
    ) async {
      await _pump(
        tester,
        SizedBox(
          width: 360,
          child: KitRow(title: 'Still', onTap: () {}),
        ),
        disableAnimations: true,
      );
      expect(find.byType(KitInkLayer), findsWidgets);
      expect(_inkSurfaces, findsNothing, reason: 'no ripple surface');
      final press = await tester.startGesture(
        tester.getCenter(find.text('Still')),
      );
      await tester.pump();
      expect(tester.binding.hasScheduledFrame, isFalse);
      await press.up();
      await tester.pump(KitMotion.pressHold);
    });

    testWidgets(
      'off Android there is no ripple surface',
      variant: TargetPlatformVariant.only(TargetPlatform.iOS),
      (tester) async {
        await _pump(
          tester,
          SizedBox(
            width: 360,
            child: KitRow(title: 'Row', onTap: () {}),
          ),
        );
        expect(find.byType(KitInkLayer), findsWidgets);
        expect(_inkSurfaces, findsNothing);
      },
    );
  });

  kitMotionStillTests(
    'KitInkLayer',
    builds: {
      'at rest': () => KitInkLayer(
        shape: const RoundedRectangleBorder(),
        child: const SizedBox(width: 200, height: 48, child: Text('Row')),
      ),
    },
    changes: {
      'a row pressed then released': KitMotionChange(
        build: () => KitRow(title: 'Row', onTap: () {}),
        act: (tester, stage) async {
          await tester.tap(find.text('Row'));
        },
        shows: 'Row',
      ),
    },
  );
}
