// Gallery (gate G4) for KitImageMarkup, docs/ux-system/kit-api/
// KitImageMarkup.md: one shot per declared state, dark and light, at phone
// and PC size, and a 2.0 text shot at both.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/kit/kit_image_markup_golden_test.dart
// and look at every changed image before committing it.
import 'dart:async';
import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

import 'kit_gallery.dart';

Future<Uint8List> _picture() async {
  final recorder = ui.PictureRecorder();
  final canvas = Canvas(recorder);
  canvas.drawRect(
    const Rect.fromLTWH(0, 0, 800, 600),
    Paint()..color = const Color(0xFF2F5D8A),
  );
  canvas.drawCircle(
    const Offset(400, 300),
    140,
    Paint()..color = const Color(0xFFE8B84A),
  );
  final image = await recorder.endRecording().toImage(800, 600);
  final data = await image.toByteData(format: ui.ImageByteFormat.png);
  return data!.buffer.asUint8List();
}

Future<void> _decoded(WidgetTester tester) async {
  await tester.runAsync(
    () => Future<void>.delayed(const Duration(milliseconds: 200)),
  );
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 300));
}

/// Waits, on the real clock, until the editor has decoded the picture and
/// drawn its canvas. A fixed pause in [_decoded] is only a guess at how long
/// the engine takes; a busy machine can need longer.
Future<void> _waitFor(WidgetTester tester, Finder finder) async {
  for (var i = 0; i < 100 && finder.evaluate().isEmpty; i++) {
    await tester.runAsync(
      () => Future<void>.delayed(const Duration(milliseconds: 100)),
    );
    await tester.pump();
  }
  await tester.pump(const Duration(milliseconds: 300));
}

void main() {
  late Uint8List picture;
  setUpAll(() async {
    await loadKitGalleryFonts();
    picture = await _picture();
  });

  Map<
    String,
    (
      FutureOr<void> Function(BuildContext),
      Future<void> Function(WidgetTester)?,
    )
  >
  states() {
    Future<void> open(BuildContext context, Uint8List bytes) =>
        showKitImageMarkup(context, bytes: bytes, name: 'screen.png');
    return {
      // Before the picture has been read.
      'loading': ((c) => open(c, picture), null),
      // A file that is not a picture.
      'error': ((c) => open(c, Uint8List.fromList(const [1, 2, 3])), _decoded),
      // A mark drawn and Send pressed; the result is still being drawn.
      'working': (
        (c) => open(c, picture),
        (tester) async {
          await _decoded(tester);
          await _waitFor(
            tester,
            find.byKey(const ValueKey('kit-markup-canvas')),
          );
          final canvas = tester.getCenter(
            find.byKey(const ValueKey('kit-markup-canvas')),
          );
          final g = await tester.startGesture(canvas);
          await g.moveBy(const Offset(80, 30));
          await g.moveBy(const Offset(40, -60));
          await g.up();
          await tester.pump();
          await tester.tap(find.byKey(const ValueKey('kit-markup-send')));
          await tester.pump();
        },
      ),
    };
  }

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';
    for (final size in kitGalleryScaledSizes) {
      final at = kitGallerySize(size);
      testWidgets('kit_image_markup loading · $at · $mode', (tester) async {
        final (open, then) = states()['loading']!;
        await kitGalleryShot(
          tester,
          name: kitGalleryName('kit_image_markup_loading', size, light: light),
          size: size,
          light: light,
          open: open,
          then: then,
          settleAfterThen: false,
        );
      });
      testWidgets('kit_image_markup error · $at · $mode', (tester) async {
        final (open, then) = states()['error']!;
        await kitGalleryShot(
          tester,
          name: kitGalleryName('kit_image_markup_error', size, light: light),
          size: size,
          light: light,
          open: open,
          then: then,
          settleAfterThen: false,
        );
      });
      testWidgets('kit_image_markup working · $at · $mode', (tester) async {
        final (open, then) = states()['working']!;
        await kitGalleryShot(
          tester,
          name: kitGalleryName('kit_image_markup_working', size, light: light),
          size: size,
          light: light,
          open: open,
          then: then,
          settleAfterThen: false,
        );
      });
      testWidgets('kit_image_markup working · text 2.0 · $at · $mode', (
        tester,
      ) async {
        final (open, then) = states()['working']!;
        await kitGalleryShot(
          tester,
          name: kitGalleryName(
            'kit_image_markup_working',
            size,
            light: light,
            text2: true,
          ),
          size: size,
          light: light,
          textScale: 2,
          open: open,
          then: then,
          settleAfterThen: false,
        );
      });
    }
  }
}
