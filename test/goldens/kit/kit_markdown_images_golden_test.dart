// Gallery (gate G4) for the pictures KitMarkdown draws
// (docs/ux-system/kit-api/KitMarkdown.md, "Pictures"): a reply with three
// screenshots the host can read (thumbnails with their file names), a web
// picture that is never loaded, and a `data:` picture; the same words with
// pictures in the middle of a sentence; the chips a host with no file reader
// gives (still openable). Phone size, light and dark, plus Arabic (right to left) for the
// reply.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/kit/kit_markdown_images_golden_test.dart
// and look at every changed image before committing it.
import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_markdown.dart';
import 'package:opencode_mobile/ui/kit/kit_layout.dart';
import 'package:opencode_mobile/ui/kit/kit_tokens.dart';

import 'kit_gallery.dart';

/// The reply on the page's own background at the screen gutter, capped at
/// the conversation pane width as the chat host lays it out.
Widget _onGround(Widget child) => Builder(
  builder: (context) {
    final tokens = KitTokens.of(context);
    return ColoredBox(
      color: tokens.roles.ground,
      child: SingleChildScrollView(
        padding: EdgeInsets.all(tokens.gutter),
        child: Align(
          alignment: AlignmentDirectional.topStart,
          child: ConstrainedBox(
            constraints: const BoxConstraints(
              maxWidth: KitLayout.paneDetailMaxWidth,
            ),
            child: child,
          ),
        ),
      ),
    );
  },
);

const _reply = '''
I captured the three screens you asked about:

![Image](/tmp/shots/home.png)
![Image](/tmp/shots/settings.png)
![Image](/tmp/shots/chat.png)

The architecture diagram comes from the docs, so it is only named:

![Architecture](https://docs.example.com/architecture.png)

This one is inline data, which cannot be opened:

![Inline pixel](data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==)
''';

const _replyArabic = '''
التقطتُ الشاشات الثلاث التي طلبتها:

![Image](/tmp/shots/home.png)
![Image](/tmp/shots/settings.png)
![Image](/tmp/shots/chat.png)

مخطط البنية من الوثائق، لذا يُذكر اسمه فقط:

![Architecture](https://docs.example.com/architecture.png)

وهذه بيانات مضمّنة لا يمكن فتحها:

![](data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==)
''';

const _inline = '''
Compare ![the old layout](/tmp/shots/home.png) with ![the new one](/tmp/shots/settings.png), then read ![](https://example.com/spec.png) before you merge.''';

const _unread = '''
The host has no way to read these, so they are named:

![Image](/tmp/shots/home.png)
![Home settings](/tmp/shots/settings.png)
![Image](/tmp/shots/chat.png)''';

const _shots = {
  '/tmp/shots/home.png': (Color(0xFF1E3A8A), Color(0xFF60A5FA)),
  '/tmp/shots/settings.png': (Color(0xFF14532D), Color(0xFF4ADE80)),
  '/tmp/shots/chat.png': (Color(0xFF581C87), Color(0xFFC084FC)),
};

/// A deterministic 240x180 screenshot stand-in: a gradient with a header bar
/// and three rows, so the three thumbnails read as different screens.
Future<Uint8List> _screenshot(Color top, Color bottom) async {
  const width = 240.0;
  const height = 180.0;
  final recorder = ui.PictureRecorder();
  final canvas = Canvas(recorder);
  canvas.drawRect(
    const Rect.fromLTWH(0, 0, width, height),
    Paint()
      ..shader = ui.Gradient.linear(Offset.zero, const Offset(0, height), [
        top,
        bottom,
      ]),
  );
  final light = Paint()..color = const Color(0xCCFFFFFF);
  canvas.drawRRect(
    RRect.fromRectAndRadius(
      const Rect.fromLTWH(16, 16, 120, 18),
      const Radius.circular(9),
    ),
    light,
  );
  for (var row = 0; row < 3; row++) {
    canvas.drawRRect(
      RRect.fromRectAndRadius(
        Rect.fromLTWH(16, 54 + row * 38.0, width - 32, 28),
        const Radius.circular(8),
      ),
      Paint()..color = const Color(0x55FFFFFF),
    );
  }
  final image = await recorder.endRecording().toImage(240, 180);
  final data = await image.toByteData(format: ui.ImageByteFormat.png);
  image.dispose();
  return data!.buffer.asUint8List();
}

/// The host's file links: every screenshot validates, and its bytes come from
/// [pictures] ([pictures] empty: the host has no file reader).
KitMarkdownFileLinks _host(Map<String, Uint8List> pictures) {
  return KitMarkdownFileLinks(
    validate: (path) async => _shots.containsKey(path),
    open: (_) {},
    readImage: pictures.isEmpty ? null : (path) async => pictures[path],
  );
}

Future<void> Function(BuildContext) _push(Widget scene) =>
    (context) => Navigator.of(context).push<void>(
      PageRouteBuilder<void>(
        transitionDuration: Duration.zero,
        reverseTransitionDuration: Duration.zero,
        pageBuilder: (_, _, _) => Scaffold(body: _onGround(scene)),
      ),
    );

/// Gives the thumbnails time to decode (the engine decodes off the test's
/// fake clock).
Future<void> _decode(WidgetTester tester) async {
  for (var i = 0; i < 20; i++) {
    await tester.runAsync(
      () => Future<void>.delayed(const Duration(milliseconds: 20)),
    );
    await tester.pump();
  }
}

void main() {
  setUpAll(loadKitGalleryFonts);

  const phone = Size(412, 915);

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';

    Future<Map<String, Uint8List>> pictures(WidgetTester tester) async {
      final made = await tester.runAsync(
        () async => {
          for (final MapEntry(key: path, value: colors) in _shots.entries)
            path: await _screenshot(colors.$1, colors.$2),
        },
      );
      return made!;
    }

    testWidgets('kit_markdown_images reply · $mode', (tester) async {
      final made = await pictures(tester);
      await kitGalleryShot(
        tester,
        name: kitGalleryName('kit_markdown_images_reply', phone, light: light),
        size: phone,
        light: light,
        open: _push(KitMarkdown(_reply, fileLinks: _host(made))),
        then: _decode,
      );
    });

    testWidgets('kit_markdown_images reply · ar · $mode', (tester) async {
      final made = await pictures(tester);
      await kitGalleryShot(
        tester,
        name: kitGalleryName(
          'kit_markdown_images_reply',
          phone,
          light: light,
          ar: true,
        ),
        size: phone,
        light: light,
        locale: const Locale('ar'),
        open: _push(KitMarkdown(_replyArabic, fileLinks: _host(made))),
        then: _decode,
      );
    });

    testWidgets('kit_markdown_images inline · $mode', (tester) async {
      final made = await pictures(tester);
      await kitGalleryShot(
        tester,
        name: kitGalleryName('kit_markdown_images_inline', phone, light: light),
        size: phone,
        light: light,
        open: _push(KitMarkdown(_inline, fileLinks: _host(made))),
        then: _decode,
      );
    });

    testWidgets('kit_markdown_images unread · $mode', (tester) async {
      await kitGalleryShot(
        tester,
        name: kitGalleryName('kit_markdown_images_unread', phone, light: light),
        size: phone,
        light: light,
        open: _push(KitMarkdown(_unread, fileLinks: _host(const {}))),
      );
    });
  }
}
