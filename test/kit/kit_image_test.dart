// Behaviour tests for KitImage, KitAvatar and KitZoom
// (docs/ux-system/kit-api/KitImage.md).
import 'dart:ui' as ui;

import 'package:flutter/foundation.dart';
import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import '../support/no_red.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit_icon_button.dart';
import 'package:opencode_mobile/ui/kit/kit_image.dart';
import 'package:opencode_mobile/ui/kit/kit_tokens.dart';

import 'kit_motion_still.dart';

/// A valid 1x1 opaque PNG so a source can decode without the network (the
/// same fixture test/provider_logo_test.dart uses).
final Uint8List _onePixelPng = Uint8List.fromList(const [
  0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, // signature
  0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52, // IHDR
  0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x08, 0x02, 0x00, 0x00, 0x00,
  0x90, 0x77, 0x53, 0xDE,
  0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41, 0x54, // IDAT
  0x08, 0xD7, 0x63, 0xF8, 0xCF, 0xC0, 0x00, 0x00, 0x03, 0x01, 0x01, 0x00,
  0x18, 0xDD, 0x8D, 0xB0,
  0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, 0xAE, 0x42, 0x60, 0x82, //
]);

final ImageProvider _placeholderProvider = MemoryImage(
  Uint8List.fromList(const []),
);

/// An [ImageProvider] whose stream always errors, so [KitImage]'s failure
/// state and [KitAvatar]'s "keeps showing the initials" behaviour are
/// deterministic and need no network.
class _FailingProvider extends ImageProvider<_FailingProvider> {
  const _FailingProvider();

  @override
  Future<_FailingProvider> obtainKey(ImageConfiguration configuration) =>
      SynchronousFuture(this);

  @override
  ImageStreamCompleter loadImage(
    _FailingProvider key,
    ImageDecoderCallback decode,
  ) => OneFrameImageStreamCompleter(
    Future<ImageInfo>.error(StateError('kit_image_test: forced failure')),
  );

  @override
  bool operator ==(Object other) => other is _FailingProvider;

  @override
  int get hashCode => runtimeType.hashCode;
}

/// A deterministic, network-free PNG of [width] × [height] px, so the
/// decode tests can use a non-square source.
Future<Uint8List> _pngOf(WidgetTester tester, int width, int height) async {
  final bytes = await tester.runAsync(() async {
    final recorder = ui.PictureRecorder();
    Canvas(recorder).drawRect(
      Rect.fromLTWH(0, 0, width.toDouble(), height.toDouble()),
      Paint()..color = const Color(0xFF3D6BFF),
    );
    final image = await recorder.endRecording().toImage(width, height);
    final data = await image.toByteData(format: ui.ImageByteFormat.png);
    image.dispose();
    return data!.buffer.asUint8List();
  });
  return bytes!;
}

/// The size, in device px, [of]'s image was actually decoded at: what the
/// engine handed back, not what the widget asked for.
Future<Size> _decodedSize(WidgetTester tester, Finder of) async {
  final raw = find.descendant(of: of, matching: find.byType(RawImage));
  for (var i = 0; i < 50; i++) {
    await tester.runAsync(
      () => Future<void>.delayed(const Duration(milliseconds: 20)),
    );
    await tester.pump();
    // Avatars show initials until the first decoded frame is available.
    if (raw.evaluate().isEmpty) continue;
    final image = tester.widget<RawImage>(raw).image;
    if (image != null) {
      return Size(image.width.toDouble(), image.height.toDouble());
    }
  }
  fail('the image never decoded');
}

Future<BuildContext> _pump(
  WidgetTester tester,
  Widget child, {
  double dpr = 1,
  Size physicalSize = const Size(800, 800),
  Locale locale = const Locale('en'),
  bool disableAnimations = false,
  TextScaler textScaler = TextScaler.noScaling,
}) async {
  tester.view.physicalSize = physicalSize;
  tester.view.devicePixelRatio = dpr;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    MaterialApp(
      debugShowCheckedModeBanner: false,
      theme: AppTheme.dark(),
      locale: locale,
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      builder: (context, child) => MediaQuery(
        data: MediaQuery.of(context).copyWith(
          disableAnimations: disableAnimations,
          textScaler: textScaler,
        ),
        child: child!,
      ),
      home: Scaffold(body: Center(child: child)),
    ),
  );
  return tester.element(find.byType(Scaffold));
}

/// The [Semantics] widget KitImage/KitAvatar/KitZoom builds for itself, or
/// null when it built none (a decorative or unlabeled image excludes
/// itself, so this is the direct, version-stable way to check that).
Semantics? _ownSemantics(WidgetTester tester, Finder of) {
  final found = find.descendant(of: of, matching: find.byType(Semantics));
  final widgets = tester.widgetList<Semantics>(found).toList();
  return widgets.isEmpty ? null : widgets.first;
}

const _unavailable = "Can't show this image";

void main() {
  kitMotionStillTests(
    'KitImage',
    builds: {
      'loaded': () => SizedBox(
        width: 120,
        height: 90,
        child: KitImage(
          source: KitImageSource.memory(_onePixelPng),
          semanticsLabel: 'A photo',
        ),
      ),
    },
  );
  kitMotionStillTests(
    'KitAvatar',
    builds: {'initials': () => const KitAvatar(name: 'Open AI')},
  );
  kitMotionStillTests(
    'KitZoom',
    builds: {
      'rest': () => const SizedBox(
        width: 280,
        height: 280,
        child: KitZoom(
          label: 'Screenshot.png',
          child: SizedBox(
            width: 160,
            height: 120,
            child: ColoredBox(color: Color(0xFF3D6BFF)),
          ),
        ),
      ),
    },
  );

  group('KitImage decode size (KitImage.md "Decode size")', () {
    testWidgets('a 100dp box at DPR 3 requests a decode width of 300', (
      tester,
    ) async {
      await _pump(
        tester,
        KitImage(
          source: KitImageSource.provider(_placeholderProvider),
          semanticsLabel: 'photo',
          width: 100,
          height: 100,
        ),
        dpr: 3,
      );
      final image = tester.widget<Image>(find.byType(Image));
      expect((image.image as ResizeImage).width, 300);
    });

    testWidgets('at DPR 2.625 it requests 263', (tester) async {
      await _pump(
        tester,
        KitImage(
          source: KitImageSource.provider(_placeholderProvider),
          semanticsLabel: 'photo',
          width: 100,
          height: 100,
        ),
        dpr: 2.625,
      );
      final image = tester.widget<Image>(find.byType(Image));
      expect((image.image as ResizeImage).width, 263);
    });

    testWidgets('contain in a height-bound box decodes to the box height', (
      tester,
    ) async {
      // A 1:4 portrait source in a wide, short box: the height binds, so a
      // width-only decode (600 px wide, 2400 tall) would waste memory.
      final png = await _pngOf(tester, 400, 1600);
      await _pump(
        tester,
        KitImage(
          source: KitImageSource.memory(png),
          semanticsLabel: 'photo',
          width: 300,
          height: 50,
        ),
        dpr: 2,
      );
      final decoded = await _decodedSize(tester, find.byType(KitImage));
      expect(decoded, const Size(25, 100));
    });

    testWidgets('cover decodes so the shorter side still covers the box', (
      tester,
    ) async {
      // A 4:1 landscape source in a 100 x 200 dp cover box at DPR 2 (200 x
      // 400 device px) is drawn 400 px tall; decoding 200 px wide would
      // leave it 50 px tall, upscaled eightfold and blurry.
      final png = await _pngOf(tester, 2000, 500);
      await _pump(
        tester,
        KitImage(
          source: KitImageSource.memory(png),
          semanticsLabel: 'photo',
          fit: KitImageFit.cover,
          width: 100,
          height: 200,
        ),
        dpr: 2,
      );
      final decoded = await _decodedSize(tester, find.byType(KitImage));
      expect(decoded, const Size(1600, 400));
    });

    testWidgets('a wide avatar image covers the circle without upscaling', (
      tester,
    ) async {
      final png = await _pngOf(tester, 2000, 500);
      await _pump(
        tester,
        KitAvatar(name: 'Open AI', image: KitImageSource.memory(png)),
        dpr: 3,
      );
      // tile = 28 dp = 84 device px: the decode is 84 px tall.
      final decoded = await _decodedSize(tester, find.byType(KitAvatar));
      expect(decoded, const Size(336, 84));
    });

    testWidgets('filterQuality is high for memory, asset and provider', (
      tester,
    ) async {
      for (final source in [
        KitImageSource.memory(_onePixelPng),
        const KitImageSource.asset('assets/does_not_matter.png'),
        KitImageSource.provider(_placeholderProvider),
      ]) {
        await _pump(
          tester,
          KitImage(source: source, semanticsLabel: 'photo', width: 40),
        );
        final image = tester.widget<Image>(find.byType(Image));
        expect(image.filterQuality, FilterQuality.high);
      }
    });
  });

  group('KitImage states', () {
    testWidgets('loading shows a surface2 fill with no spinner', (
      tester,
    ) async {
      final context = await _pump(
        tester,
        const KitImage(
          source: KitImageSource.provider(_FailingProvider()),
          semanticsLabel: 'photo',
          width: 100,
          height: 100,
        ),
      );
      // Before the (async) error lands, the frame is null: no spinner.
      expect(find.byType(CircularProgressIndicator), findsNothing);
      final tokens = KitTokens.of(context);
      final decoration =
          tester
                  .widgetList<DecoratedBox>(find.byType(DecoratedBox))
                  .first
                  .decoration
              as ShapeDecoration;
      expect(decoration.color, tokens.roles.surface2);
    });

    testWidgets(
      'a failing provider shows the broken-image glyph; at 120dp+ the words too',
      (tester) async {
        await _pump(
          tester,
          const KitImage(
            source: KitImageSource.provider(_FailingProvider()),
            semanticsLabel: 'photo',
            width: 200,
            height: 200,
          ),
        );
        await tester.pump();
        expect(find.byIcon(AppIconography.imageBroken), findsOneWidget);
        expect(find.text(_unavailable), findsOneWidget);
      },
    );

    testWidgets('a narrow failure (<120dp) shows the glyph only', (
      tester,
    ) async {
      await _pump(
        tester,
        const KitImage(
          source: KitImageSource.provider(_FailingProvider()),
          semanticsLabel: 'photo',
          width: 80,
          height: 80,
        ),
      );
      await tester.pump();
      expect(find.byIcon(AppIconography.imageBroken), findsOneWidget);
      expect(find.text(_unavailable), findsNothing);
    });

    testWidgets('at 2.0 text the words that need more than two lines are '
        'dropped, never ellipsized; the glyph alone shows', (tester) async {
      await _pump(
        tester,
        const KitImage(
          source: KitImageSource.provider(_FailingProvider()),
          semanticsLabel: 'photo',
          width: 120,
          height: 120,
        ),
        textScaler: const TextScaler.linear(2),
      );
      await tester.pump();
      expect(find.byIcon(AppIconography.imageBroken), findsOneWidget);
      expect(find.text(_unavailable), findsNothing);
      // Nothing is cut: no paragraph in the failure state is truncated.
      for (final paragraph in tester.renderObjectList<RenderParagraph>(
        find.descendant(
          of: find.byType(KitImage),
          matching: find.byType(RichText),
        ),
      )) {
        expect(paragraph.didExceedMaxLines, isFalse);
      }
    });

    testWidgets('fallback replaces the failure notice when given', (
      tester,
    ) async {
      await _pump(
        tester,
        const KitImage(
          source: KitImageSource.provider(_FailingProvider()),
          semanticsLabel: 'photo',
          width: 200,
          height: 200,
          fallback: Text('fallback shown'),
        ),
      );
      await tester.pump();
      expect(find.text('fallback shown'), findsOneWidget);
      expect(find.byIcon(AppIconography.imageBroken), findsNothing);
    });

    testWidgets('a source change from a good image to a failing one shows the '
        'failure state, never the old image', (tester) async {
      await _pump(
        tester,
        KitImage(
          source: KitImageSource.memory(_onePixelPng),
          semanticsLabel: 'photo',
          width: 100,
          height: 100,
        ),
      );
      await tester.pumpAndSettle();
      expect(find.byIcon(AppIconography.imageBroken), findsNothing);

      await _pump(
        tester,
        const KitImage(
          source: KitImageSource.provider(_FailingProvider()),
          semanticsLabel: 'photo',
          width: 100,
          height: 100,
        ),
      );
      await tester.pump();
      expect(find.byIcon(AppIconography.imageBroken), findsOneWidget);
    });

    testWidgets('the first frame cross-fades in', (tester) async {
      final png = await _pngOf(tester, 8, 8);
      await _pump(
        tester,
        KitImage(
          source: KitImageSource.memory(png),
          semanticsLabel: 'photo',
          width: 40,
          height: 40,
        ),
      );
      await _decodedSize(tester, find.byType(KitImage));
      final fading = tester
          .widgetList<Opacity>(
            find.descendant(
              of: find.byType(KitImage),
              matching: find.byType(Opacity),
            ),
          )
          .where((o) => o.opacity < 1);
      expect(fading, isNotEmpty);
      await tester.pumpAndSettle();
    });

    testWidgets('under reduced motion the first frame shows at once', (
      tester,
    ) async {
      final png = await _pngOf(tester, 8, 8);
      await _pump(
        tester,
        KitImage(
          source: KitImageSource.memory(png),
          semanticsLabel: 'photo',
          width: 40,
          height: 40,
        ),
        disableAnimations: true,
      );
      await _decodedSize(tester, find.byType(KitImage));
      final fading = tester
          .widgetList<Opacity>(
            find.descendant(
              of: find.byType(KitImage),
              matching: find.byType(Opacity),
            ),
          )
          .where((o) => o.opacity < 1);
      expect(fading, isEmpty);
      expect(tester.hasRunningAnimations, isFalse);
    });
  });

  group('KitImage semantics (A11Y)', () {
    testWidgets('semanticsLabel null gives no semantics node', (tester) async {
      await _pump(
        tester,
        KitImage(
          source: KitImageSource.memory(_onePixelPng),
          semanticsLabel: null,
          width: 40,
          height: 40,
        ),
      );
      await tester.pumpAndSettle();
      expect(_ownSemantics(tester, find.byType(KitImage)), isNull);
    });

    testWidgets('a label gives one image node', (tester) async {
      await _pump(
        tester,
        KitImage(
          source: KitImageSource.memory(_onePixelPng),
          semanticsLabel: 'A cat',
          width: 40,
          height: 40,
        ),
      );
      await tester.pumpAndSettle();
      final semantics = _ownSemantics(tester, find.byType(KitImage))!;
      expect(semantics.properties.label, 'A cat');
      expect(semantics.properties.image, isTrue);
    });

    for (final width in [80.0, 200.0]) {
      testWidgets('a failing labelled image (${width.toInt()}dp) reads its '
          'failure words', (tester) async {
        final handle = tester.ensureSemantics();
        await _pump(
          tester,
          KitImage(
            source: const KitImageSource.provider(_FailingProvider()),
            semanticsLabel: 'A cat',
            width: width,
            height: 80,
          ),
        );
        await tester.pump();
        final node = tester.getSemantics(find.byType(KitImage));
        expect(node.label, contains('A cat'));
        expect(node.label, contains(_unavailable));
        expect(node.flagsCollection.isImage, isTrue);
        handle.dispose();
      });
    }

    testWidgets('a loaded labelled image does not read the failure words', (
      tester,
    ) async {
      final handle = tester.ensureSemantics();
      await _pump(
        tester,
        KitImage(
          source: KitImageSource.memory(_onePixelPng),
          semanticsLabel: 'A cat',
          width: 200,
          height: 80,
        ),
      );
      await tester.pumpAndSettle();
      expect(tester.getSemantics(find.byType(KitImage)).label, 'A cat');
      handle.dispose();
    });
  });

  group('KitAvatar', () {
    testWidgets('shows initials and the semantic label', (tester) async {
      await _pump(tester, const KitAvatar(name: 'Open AI'));
      expect(find.text('OA'), findsOneWidget);
      final semantics = _ownSemantics(tester, find.byType(KitAvatar))!;
      expect(semantics.properties.label, 'Open AI');
      expect(semantics.properties.image, isTrue);
    });

    testWidgets('an Arabic name shows its first graphemes, no case change', (
      tester,
    ) async {
      await _pump(tester, const KitAvatar(name: 'محمد علي'));
      expect(find.text('مع'), findsOneWidget);
    });

    testWidgets('decorative gives no semantics node', (tester) async {
      await _pump(tester, const KitAvatar(name: 'Open AI', decorative: true));
      expect(_ownSemantics(tester, find.byType(KitAvatar)), isNull);
    });

    testWidgets('a failing image keeps showing the initials', (tester) async {
      await _pump(
        tester,
        const KitAvatar(
          name: 'Open AI',
          image: KitImageSource.provider(_FailingProvider()),
        ),
      );
      await tester.pump();
      expect(find.text('OA'), findsOneWidget);
    });

    testWidgets('a failing image is said in words and the error glyph, not '
        'a red dot (LOOK-5, STATE-9)', (tester) async {
      await _pump(
        tester,
        const KitAvatar(
          name: 'Open AI',
          image: KitImageSource.provider(_FailingProvider()),
        ),
      );
      await tester.pumpAndSettle();
      final semantics = _ownSemantics(tester, find.byType(KitAvatar))!;
      expect(semantics.properties.label, 'Open AI');
      expect(semantics.properties.value, _unavailable);
      final roles = ThemeRoles.resolve(AppTheme.dark());
      final glyph = tester.widget<Icon>(
        find.descendant(
          of: find.byType(KitAvatar),
          matching: find.byType(Icon),
        ),
      );
      expect(glyph.icon, KitTokens.glyphFor(AppStatusTone.failure));
      expect(glyph.color, roles.text1);
      expect(glyph.color, isNotRed);
      final ring = tester.widget<Container>(
        find.ancestor(
          of: find.byWidget(glyph),
          matching: find.byType(Container),
        ),
      );
      expect((ring.decoration! as BoxDecoration).color, roles.ground);
    });

    testWidgets('an optional image that fails keeps the initials, no badge', (
      tester,
    ) async {
      await _pump(
        tester,
        const KitAvatar(
          name: 'Open AI',
          image: KitImageSource.provider(_FailingProvider()),
          optionalImage: true,
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('OA'), findsOneWidget);
      expect(
        find.descendant(
          of: find.byType(KitAvatar),
          matching: find.byType(Icon),
        ),
        findsNothing,
      );
      expect(find.textContaining(_unavailable), findsNothing);
    });

    testWidgets('at 2.0 text the initials stay clamped', (tester) async {
      await _pump(
        tester,
        const KitAvatar(name: 'Open AI'),
        textScaler: const TextScaler.linear(2),
      );
      final rendered = tester.renderObject<RenderParagraph>(
        find.descendant(
          of: find.byType(KitAvatar),
          matching: find.byType(RichText),
        ),
      );
      // MediaQuery.withClampedTextScaling caps the scaler the KitText below
      // it reads, so the paragraph's applied scale is under 2.0.
      expect(
        rendered.textScaler.scale(10),
        lessThan(const TextScaler.linear(2).scale(10)),
      );
    });
  });

  group('KitZoom', () {
    setUp(() {
      debugPlatformCapabilities = const PlatformCapabilities.linuxDesktop();
    });
    tearDown(() => debugPlatformCapabilities = null);

    Widget zoom({
      KitZoomMode mode = KitZoomMode.fit,
      KitZoomController? controller,
      Object? resetKey,
      Key? resetControlKey,
      Size child = const Size(100, 100),
    }) => SizedBox(
      width: 300,
      height: 300,
      child: KitZoom(
        label: 'Screenshot.png',
        mode: mode,
        controller: controller,
        resetKey: resetKey,
        resetControlKey: resetControlKey,
        child: SizedBox(
          width: child.width,
          height: child.height,
          child: const ColoredBox(color: Colors.blue),
        ),
      ),
    );

    String? zoomValue(WidgetTester tester) {
      final matches = tester
          .widgetList<Semantics>(
            find.descendant(
              of: find.byType(KitZoom),
              matching: find.byType(Semantics),
            ),
          )
          .where((s) => s.properties.value != null);
      return matches.isEmpty ? null : matches.first.properties.value;
    }

    Map<String, VoidCallback> zoomActions(WidgetTester tester) {
      final matches = tester
          .widgetList<Semantics>(
            find.descendant(
              of: find.byType(KitZoom),
              matching: find.byType(Semantics),
            ),
          )
          .where((s) => s.properties.customSemanticsActions != null);
      if (matches.isEmpty) return const {};
      return {
        for (final e
            in matches.first.properties.customSemanticsActions!.entries)
          e.key.label ?? '': e.value,
      };
    }

    Offset translation(KitZoomController c) =>
        Offset(c.value.storage[12], c.value.storage[13]);

    Future<void> doubleTapAt(WidgetTester tester, Offset point) async {
      await tester.tapAt(point);
      await tester.pump(kDoubleTapMinTime);
      await tester.tapAt(point);
      await tester.pump();
    }

    Future<void> focusZoom(WidgetTester tester) async {
      await tester.tap(find.byType(KitZoom));
      // A tap only resolves once the double-tap disambiguation window
      // passes with no second tap (the same GestureDetector also handles
      // double-tap-to-zoom).
      await tester.pump(kDoubleTapTimeout);
    }

    Future<void> ctrl(WidgetTester tester, LogicalKeyboardKey key) async {
      await tester.sendKeyDownEvent(LogicalKeyboardKey.control);
      await tester.sendKeyEvent(key);
      await tester.sendKeyUpEvent(LogicalKeyboardKey.control);
      await tester.pumpAndSettle();
    }

    // KitIconButton never shows its label as a mounted Text (only as a
    // tooltip and a semantic name), so it is found by its field, not by
    // find.widgetWithText.
    Finder byLabel(String label) =>
        find.byWidgetPredicate((w) => w is KitIconButton && w.tooltip == label);

    testWidgets('double-tap goes to 2x and back to 1x', (tester) async {
      await _pump(tester, zoom());
      final center = tester.getCenter(find.byType(KitZoom));
      await doubleTapAt(tester, center);
      await tester.pumpAndSettle();
      expect(zoomValue(tester), '200 %');

      await doubleTapAt(tester, center);
      await tester.pumpAndSettle();
      expect(zoomValue(tester), '100 %');
    });

    testWidgets('in canvas mode double-tap goes to an absolute 2x', (
      tester,
    ) async {
      final controller = KitZoomController();
      addTearDown(controller.dispose);
      await _pump(
        tester,
        zoom(
          mode: KitZoomMode.canvas,
          controller: controller,
          child: const Size(2000, 2000),
        ),
      );
      await tester.pump();
      // 300 / 2000 = 0.15 is under the smallest fit, so it clamps there.
      expect(
        controller.value.getMaxScaleOnAxis(),
        closeTo(KitZoom.minScale, 0.001),
      );
      await doubleTapAt(tester, tester.getCenter(find.byType(KitZoom)));
      await tester.pumpAndSettle();
      expect(controller.value.getMaxScaleOnAxis(), closeTo(2, 0.001));
    });

    testWidgets('Ctrl+= then Ctrl+0 returns to 1x (desktop capabilities)', (
      tester,
    ) async {
      await _pump(tester, zoom());
      await focusZoom(tester);
      await ctrl(tester, LogicalKeyboardKey.equal);
      expect(zoomValue(tester), '150 %');

      await ctrl(tester, LogicalKeyboardKey.digit0);
      expect(zoomValue(tester), '100 %');
    });

    testWidgets('one Ctrl+wheel notch zooms exactly one step at the pointer; '
        'a plain wheel does not zoom', (tester) async {
      await _pump(tester, zoom());
      final center = tester.getCenter(find.byType(KitZoom));
      final mouse = TestPointer(1, PointerDeviceKind.mouse);
      await tester.sendEventToBinding(mouse.hover(center));

      await tester.sendEventToBinding(mouse.scroll(const Offset(0, -100)));
      await tester.pump();
      expect(zoomValue(tester), '100 %');

      await tester.sendKeyDownEvent(LogicalKeyboardKey.control);
      await tester.sendEventToBinding(mouse.scroll(const Offset(0, -100)));
      await tester.sendKeyUpEvent(LogicalKeyboardKey.control);
      await tester.pump();
      expect(zoomValue(tester), '150 %');
    });

    testWidgets('the custom actions zoom in, zoom out and reset', (
      tester,
    ) async {
      await _pump(tester, zoom());
      // At rest only zoom in is offered: there is nothing to reset and
      // nothing to zoom out of.
      expect(zoomActions(tester).keys, ['Zoom in']);

      zoomActions(tester)['Zoom in']!();
      await tester.pumpAndSettle();
      zoomActions(tester)['Zoom in']!();
      await tester.pumpAndSettle();
      expect(zoomValue(tester), '225 %');

      zoomActions(tester)['Zoom out']!();
      await tester.pumpAndSettle();
      expect(zoomValue(tester), '150 %');

      zoomActions(tester)['Reset zoom']!();
      await tester.pumpAndSettle();
      expect(zoomValue(tester), '100 %');
      expect(zoomActions(tester).keys, isNot(contains('Reset zoom')));
    });

    testWidgets(
      'at 1x reset is disabled with its reason, at max zoom in is disabled',
      (tester) async {
        await _pump(tester, zoom());
        final reset = tester.widget<KitIconButton>(
          byLabel('Already at full view'),
        );
        expect(reset.onPressed, isNull);

        for (var i = 0; i < 20; i++) {
          final zoomInFinder = find.widgetWithIcon(
            KitIconButton,
            AppIconography.expand,
          );
          final button = tester.widget<KitIconButton>(zoomInFinder);
          if (button.onPressed == null) break;
          button.onPressed!();
          await tester.pumpAndSettle();
        }
        final atMax = tester.widget<KitIconButton>(byLabel('Largest zoom'));
        expect(atMax.onPressed, isNull);
      },
    );

    testWidgets('with a fine pointer each tooltip carries its shortcut', (
      tester,
    ) async {
      await _pump(tester, zoom());
      await focusZoom(tester);
      await ctrl(tester, LogicalKeyboardKey.equal);
      expect(byLabel('Zoom in · Ctrl+='), findsOneWidget);
      expect(byLabel('Zoom out · Ctrl+−'), findsOneWidget);
      expect(byLabel('Reset zoom · Ctrl+0'), findsOneWidget);
    });

    testWidgets('arrows pan only while zoomed, clamped to the image; at rest '
        'they reach the host', (tester) async {
      final controller = KitZoomController();
      addTearDown(controller.dispose);
      final reachedHost = <LogicalKeyboardKey>[];
      await _pump(
        tester,
        Focus(
          onKeyEvent: (node, event) {
            if (event is KeyDownEvent) reachedHost.add(event.logicalKey);
            return KeyEventResult.ignored;
          },
          child: zoom(controller: controller),
        ),
      );
      await focusZoom(tester);

      await tester.sendKeyEvent(LogicalKeyboardKey.arrowRight);
      await tester.pump();
      expect(reachedHost, [LogicalKeyboardKey.arrowRight]);
      expect(translation(controller), Offset.zero);

      await ctrl(tester, LogicalKeyboardKey.equal); // 150 %, centred
      expect(translation(controller).dx, closeTo(-75, 0.01));
      reachedHost.clear();

      await tester.sendKeyEvent(LogicalKeyboardKey.arrowRight);
      await tester.pump();
      expect(reachedHost, isEmpty);
      expect(translation(controller).dx, closeTo(-107, 0.01));

      for (var i = 0; i < 10; i++) {
        await tester.sendKeyEvent(LogicalKeyboardKey.arrowRight);
        await tester.pump();
      }
      // The 300dp image is 450dp wide at 150 %: its far edge stops at the
      // viewport's edge, and the arrow that cannot move goes to the host.
      expect(translation(controller).dx, closeTo(-150, 0.01));
      expect(reachedHost, contains(LogicalKeyboardKey.arrowRight));
    });

    testWidgets('a new resetKey resets', (tester) async {
      await _pump(tester, zoom(resetKey: 1));
      final center = tester.getCenter(find.byType(KitZoom));
      await doubleTapAt(tester, center);
      await tester.pumpAndSettle();
      expect(zoomValue(tester), '200 %');

      await _pump(tester, zoom(resetKey: 2));
      await tester.pumpAndSettle();
      expect(zoomValue(tester), '100 %');
    });

    testWidgets('canvas mode fits a 2000x2000 child on open, pans within its '
        'bounds, and reset() returns to the fit', (tester) async {
      final controller = KitZoomController();
      addTearDown(controller.dispose);
      await _pump(
        tester,
        zoom(
          mode: KitZoomMode.canvas,
          controller: controller,
          resetControlKey: const ValueKey('fit'),
          child: const Size(2000, 2000),
        ),
      );
      await tester.pump();
      final fitted = controller.value.getMaxScaleOnAxis();
      expect(fitted, lessThanOrEqualTo(1.0));
      expect(fitted, greaterThanOrEqualTo(KitZoom.minScale));
      // At the fitted start view the Fit control is disabled with its
      // reason.
      final fit = tester.widget<KitIconButton>(
        find.byKey(const ValueKey('fit')),
      );
      expect(fit.onPressed, isNull);
      expect(fit.tooltip, 'Already at full view');

      controller.zoomIn();
      await tester.pumpAndSettle();
      final scale = controller.value.getMaxScaleOnAxis();
      final far = 300 - 2000 * scale;

      // However far it is dragged, the graph still covers the viewport:
      // it stops with its edge at the viewport's edge.
      await tester.drag(find.byType(KitZoom), const Offset(-5000, -5000));
      await tester.pumpAndSettle();
      expect(translation(controller).dx, closeTo(far, 0.01));
      expect(translation(controller).dy, closeTo(far, 0.01));
      await tester.drag(find.byType(KitZoom), const Offset(5000, 5000));
      await tester.pumpAndSettle();
      expect(translation(controller), Offset.zero);

      expect(
        tester.widget<KitIconButton>(find.byKey(const ValueKey('fit'))).tooltip,
        'Fit to screen · Ctrl+0',
      );
      controller.reset();
      await tester.pumpAndSettle();
      expect(controller.value.getMaxScaleOnAxis(), closeTo(fitted, 0.01));
    });

    testWidgets('a canvas smaller than the view on one axis keeps panning '
        'room at the fitted start', (tester) async {
      final controller = KitZoomController();
      addTearDown(controller.dispose);
      await _pump(
        tester,
        zoom(
          mode: KitZoomMode.canvas,
          controller: controller,
          child: const Size(1200, 600),
        ),
      );
      await tester.pump();
      // Fitted at 0.25: 300 x 150, centred at y = 75.
      expect(translation(controller).dy, closeTo(75, 0.01));
      await focusZoom(tester);
      await tester.sendKeyEvent(LogicalKeyboardKey.arrowDown);
      await tester.pump();
      expect(translation(controller).dy, closeTo(43, 0.01));
    });

    testWidgets('under reduced motion the reset settles after one pump', (
      tester,
    ) async {
      await _pump(tester, zoom(), disableAnimations: true);
      final center = tester.getCenter(find.byType(KitZoom));
      await doubleTapAt(tester, center);
      // _animateTo jumps straight to the target under reduced motion
      // (KitMotion.reduced) instead of starting the shared
      // AnimationController, so the value is exact after doubleTapAt's own
      // single pump, never mid-tween.
      expect(zoomValue(tester), '200 %');

      tester.widget<KitIconButton>(byLabel('Reset zoom · Ctrl+0')).onPressed!();
      await tester.pump();
      expect(zoomValue(tester), '100 %');
      // What is left running past this one pump is Material's own
      // IconButton enabled/disabled chrome transition (kThemeChangeDuration
      // — the button changed from "Reset zoom" to the disabled "Already at
      // full view"), not KitZoom's motion: the v1 KitIconButton this unit
      // depends on (KitImage.md "Depends on") owns that timing, and it is
      // bounded and unrelated to KitMotion.reduced.
      final flushed = await tester.pumpAndSettle(
        const Duration(milliseconds: 16),
      );
      expect(flushed, lessThan(30));
    });

    testWidgets('no HapticFeedback call', (tester) async {
      final calls = <MethodCall>[];
      tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
        SystemChannels.platform,
        (call) async {
          if (call.method.startsWith('HapticFeedback')) calls.add(call);
          return null;
        },
      );
      addTearDown(
        () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
          SystemChannels.platform,
          null,
        ),
      );
      await _pump(tester, zoom());
      final center = tester.getCenter(find.byType(KitZoom));
      await doubleTapAt(tester, center);
      await tester.pumpAndSettle();
      await tester.tap(byLabel('Reset zoom · Ctrl+0'));
      await tester.pumpAndSettle();
      expect(calls, isEmpty);
    });
  });
}
