// Behaviour of the chat header's glass (KitFrostHeader, owner 2026-10-30):
// a page's bar is painted over its body; a composer layer whose body opts in
// (reads KitFrostHeader.topClearanceOf) runs it up under the bar, and the
// bar frosts only while content is scrolled under it. A body that never
// opts in is never run under the bar, so its first item is never hidden.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

const _list = ValueKey('transcript');

/// A reversed transcript; [optIn] pads its top by the header clearance.
Widget _transcript({required bool optIn}) => Builder(
  builder: (context) {
    final top = optIn ? KitFrostHeader.topClearanceOf(context) : 0.0;
    return ListView.builder(
      key: _list,
      reverse: true,
      padding: EdgeInsets.only(
        top: 8 + top,
        bottom: 8 + KitBottomInset.of(context).bottom,
      ),
      itemCount: 60,
      itemBuilder: (_, i) => SizedBox(
        height: 40,
        child: Text('Line $i', key: ValueKey('line-$i')),
      ),
    );
  },
);

Future<void> _pump(
  WidgetTester tester, {
  bool optIn = true,
  bool loading = false,
  KitEffects effects = KitEffects.defaults,
}) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  final conditions = ValueNotifier<List<KitStatus>>(const []);
  addTearDown(conditions.dispose);
  await tester.pumpWidget(
    KitStatusScope(
      conditions: conditions,
      child: KitEffectsScope(
        effects: effects,
        child: MaterialApp(
          debugShowCheckedModeBanner: false,
          theme: AppTheme.light(),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: KitScreen(
            topBar: const KitTopBar(title: 'Fix the focus ring'),
            loading: loading,
            body: KitComposer.layer(
              body: _transcript(optIn: optIn),
              composer: const KitGlass(
                child: SizedBox(height: 56, child: Text('Steer')),
              ),
            ),
          ),
        ),
      ),
    ),
  );
  if (loading) {
    // The loading bar runs for ever.
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 500));
  } else {
    await tester.pumpAndSettle();
  }
}

double _barBottom(WidgetTester tester) =>
    tester.getBottomLeft(find.byType(KitTopBar)).dy;

double _listTop(WidgetTester tester) => tester.getTopLeft(find.byKey(_list)).dy;

Future<void> _toOldest(WidgetTester tester) async {
  await tester.drag(find.byKey(_list), const Offset(0, 4000));
  await tester.pumpAndSettle();
}

Future<void> _toMiddle(WidgetTester tester) async {
  await tester.drag(find.byKey(_list), const Offset(0, -300));
  await tester.pumpAndSettle();
}

void main() {
  setUp(GlassChoiceStore.debugReset);

  testWidgets('an opted-in transcript runs up under the bar', (tester) async {
    await _pump(tester);
    final barTop = tester.getTopLeft(find.byType(KitTopBar)).dy;
    expect(_listTop(tester), closeTo(barTop, .5));
  });

  testWidgets('a body that does not opt in stays below the bar', (
    tester,
  ) async {
    await _pump(tester, optIn: false);
    expect(_listTop(tester), greaterThanOrEqualTo(_barBottom(tester) - .5));
    await _toOldest(tester);
    await _toMiddle(tester);
    // Only the composer's glass; the bar stays as it is.
    expect(find.byType(BackdropFilter), findsOneWidget);
  });

  testWidgets('solid at the oldest line; the first line clears the bar', (
    tester,
  ) async {
    await _pump(tester);
    await _toOldest(tester);
    expect(
      tester.getTopLeft(find.byKey(const ValueKey('line-59'))).dy,
      greaterThanOrEqualTo(_barBottom(tester)),
    );
    // Nothing above the viewport: the header has no blur.
    expect(
      find.descendant(
        of: find.ancestor(
          of: find.byType(KitTopBar),
          matching: find.byType(KitFrost),
        ),
        matching: find.byType(BackdropFilter),
      ),
      findsNothing,
    );
  });

  testWidgets('frosts once content is under it, fading in', (tester) async {
    await _pump(tester);
    await _toOldest(tester);
    await tester.drag(find.byKey(_list), const Offset(0, -300));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 60));
    final header = find.descendant(
      of: find.ancestor(
        of: find.byType(KitTopBar),
        matching: find.byType(KitFrost),
      ),
      matching: find.byType(BackdropFilter),
    );
    expect(header, findsOneWidget);
    await tester.pumpAndSettle();
    expect(header, findsOneWidget);
  });

  testWidgets('stays solid while the page loads', (tester) async {
    await _pump(tester, loading: true);
    expect(_listTop(tester), greaterThanOrEqualTo(_barBottom(tester) - .5));
  });

  testWidgets('stays solid (below the bar) when glass is off', (tester) async {
    GlassChoiceStore.choice.value = KitGlassChoice.off;
    await _pump(tester);
    expect(_listTop(tester), greaterThanOrEqualTo(_barBottom(tester) - .5));
    await _toMiddle(tester);
    expect(find.byType(BackdropFilter), findsNothing);
  });

  testWidgets('the bar answers taps over a body run under it', (tester) async {
    await _pump(tester);
    await _toOldest(tester);
    await _toMiddle(tester);
    // The bar's back control is hit-tested before the body under it.
    final hit = tester.hitTestOnBinding(
      tester.getCenter(find.text('Fix the focus ring')),
    );
    final barRender = tester.renderObject(find.byType(KitTopBar));
    expect(hit.path.any((e) => e.target == barRender), isTrue);
  });
}
