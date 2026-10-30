// The words on a floating glass piece keep their picture while the page
// around them repaints (scroll-perf, 2026-10-23). A scroll's stretch at an
// end and a fading scrollbar repaint the page around the list every frame;
// words recorded again with it re-blurred their plate shadows each frame
// under Glass and Crystal (about 38 ms a frame on the shell's server pill on
// the emulator).
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/glass/kit_glass.dart';
import 'package:opencode_mobile/ui/kit/kit_screen.dart';
import 'package:opencode_mobile/ui/kit/kit_text.dart';
import 'package:opencode_mobile/ui/kit/kit_top_bar.dart';

/// Paints on every tick of [repaint], with no layer of its own, so each tick
/// repaints the page up to its nearest repaint boundary.
class _Ticking extends CustomPainter {
  _Ticking(this.ticks) : super(repaint: ticks);

  final ValueNotifier<int> ticks;
  int paints = 0;

  @override
  void paint(Canvas canvas, Size size) => paints++;

  @override
  bool shouldRepaint(_Ticking old) => false;
}

RenderRepaintBoundary _boundaryAbove(WidgetTester tester, Finder finder) {
  RenderObject? node = tester.renderObject(finder);
  while (node is! RenderRepaintBoundary) {
    node = node!.parent;
  }
  return node;
}

/// The pictures recorded under [boundary]'s layer: a boundary painted again
/// records new ones; one kept while its parent repaints keeps the old.
Set<PictureLayer> _pictures(RenderRepaintBoundary boundary) {
  final found = <PictureLayer>{};
  void walk(Layer? layer) {
    for (; layer != null; layer = layer.nextSibling) {
      if (layer is PictureLayer) found.add(layer);
      if (layer is ContainerLayer) walk(layer.firstChild);
    }
  }

  walk(boundary.debugLayer!.firstChild);
  return found;
}

Future<void> _pump(WidgetTester tester, Widget home) async {
  await tester.pumpWidget(
    MaterialApp(
      theme: AppTheme.light(),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      home: home,
    ),
  );
  await tester.pumpAndSettle();
}

/// Repaints the page five times through [painter] and expects the words
/// [words] to keep their picture.
Future<void> _expectKept(
  WidgetTester tester,
  _Ticking painter,
  Finder words,
) async {
  final boundary = _boundaryAbove(tester, words);
  final before = _pictures(boundary);
  final bodyBefore = painter.paints;
  for (var i = 1; i <= 5; i++) {
    painter.ticks.value = i;
    await tester.pump(const Duration(milliseconds: 16));
  }
  expect(painter.paints, bodyBefore + 5, reason: 'the page repainted');
  expect(_pictures(boundary), before, reason: 'the words kept their picture');
}

void main() {
  testWidgets('the shell pill keeps its words when the page body repaints', (
    tester,
  ) async {
    final ticks = ValueNotifier<int>(0);
    addTearDown(ticks.dispose);
    final painter = _Ticking(ticks);
    await _pump(
      tester,
      KitScreen(
        topBar: KitTopBar.shell(
          controls: KitShellControls(
            server: 'On this phone',
            serverStatus: 'Connected',
            serverTone: AppStatusTone.ok,
            onServer: () {},
            onSearch: () {},
            onSettings: () {},
          ),
        ),
        body: CustomPaint(painter: painter, child: const SizedBox()),
      ),
    );
    await _expectKept(tester, painter, find.textContaining('On this phone'));
  });

  testWidgets('a glass piece keeps its words when the page beside it '
      'repaints', (tester) async {
    final ticks = ValueNotifier<int>(0);
    addTearDown(ticks.dispose);
    final painter = _Ticking(ticks);
    await _pump(
      tester,
      Column(
        children: [
          const KitGlass(child: KitText('Draft')),
          Expanded(
            child: CustomPaint(painter: painter, child: const SizedBox()),
          ),
        ],
      ),
    );
    await _expectKept(tester, painter, find.text('Draft'));
  });
}
