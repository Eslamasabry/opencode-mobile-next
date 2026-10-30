// Captures of the in-app preview (vision slice, 2026-10-20, Preview A): the
// real DevPreviewScreen pushed over a page (so the bar has its Back), real
// fonts, blurred shadows, no server. Phone 412x915 dp at 2x pixels, one full
// screen per image.
//
// The platform web view cannot draw in a test, so the page is a stand-in:
// a still drawing of the brick-breaker game the owner previewed (today's
// screen, ref-preview.png), painted where the web view sits. It is the dev
// server's page, not the app's: nothing on it comes from the app.
//
//   flutter test --concurrency=1 tool/capture/vision_preview_test.dart
//   flutter test --concurrency=1 --dart-define=VISION_OUT=<dir> \
//     tool/capture/vision_preview_test.dart
//
// Output: /home/eslam/vision-assets/out/vision-preview/<scene>.png
import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/dev_preview.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/dev_preview_screen.dart';
import 'package:opencode_mobile/voice/controller.dart';
import 'package:webview_flutter_platform_interface/webview_flutter_platform_interface.dart';

import '../../test/goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import '../../test/support/fake_webview_platform.dart';
import '../../test/voice_controller_test.dart'
    show FakeVoiceRecorder, FakeVoiceRecognizer, readyVoiceModelManager;
import 'fixtures.dart';
// AUDIT TOOLING (audit/style-coherence): no-op unless AUDIT_STYLE is set.
import 'audit/audit_env.dart';

const _out = String.fromEnvironment(
  'VISION_OUT',
  defaultValue: '/home/eslam/vision-assets/out/vision-preview',
);

enum _Step { ready, typed, sent, notSent, listening, failed, details }

enum _Scene {
  mainDark('main-dark'),
  mainLight('main-light', light: true),
  typed('typed-dark', step: _Step.typed),
  sent('sent-dark', step: _Step.sent),
  sentBusy('sent-while-working-dark', step: _Step.sent, busy: true),
  notSent('not-sent-dark', step: _Step.notSent),
  listening('listening-dark', step: _Step.listening),
  failed('nothing-answering-dark', step: _Step.failed),
  failedRemote(
    'nothing-answering-remote-dark',
    step: _Step.failed,
    remote: true,
  ),
  details('details-dark', step: _Step.details, logs: true),
  noConversation('no-conversation-dark', conversation: false),
  small('small-320-text2-dark', size: Size(320, 693), scale: 2),
  smallTyped(
    'small-320-text2-typed-dark',
    step: _Step.typed,
    size: Size(320, 693),
    scale: 2,
  ),
  arabic('arabic-rtl-dark', locale: Locale('ar')),
  arabicTyped('arabic-rtl-typed-dark', step: _Step.typed, locale: Locale('ar'));

  const _Scene(
    this.file, {
    this.light = false,
    this.step = _Step.ready,
    this.busy = false,
    this.remote = false,
    this.logs = false,
    this.conversation = true,
    this.size = const Size(412, 915),
    this.scale = 1,
    this.locale = const Locale('en'),
  });
  final String file;
  final bool light;
  final _Step step;
  final bool busy;
  final bool remote;
  final bool logs;
  final bool conversation;
  final Size size;
  final double scale;
  final Locale locale;
}

/// The fake platform, drawing [_GamePage] where the web view sits.
class _PagePlatform extends FakeWebViewPlatform {
  @override
  PlatformWebViewWidget createPlatformWebViewWidget(
    PlatformWebViewWidgetCreationParams params,
  ) => _PageWidget(params);
}

class _PageWidget extends PlatformWebViewWidget {
  _PageWidget(super.params) : super.implementation();

  @override
  Widget build(BuildContext context) => const _GamePage();
}

/// A still frame of the dev server's page: the neon brick breaker.
class _GamePage extends StatelessWidget {
  const _GamePage();

  @override
  Widget build(BuildContext context) => const ColoredBox(
    color: Color(0xFF05060B),
    child: SizedBox.expand(child: CustomPaint(painter: _GamePainter())),
  );
}

class _GamePainter extends CustomPainter {
  const _GamePainter();

  static const _pink = Color(0xFFFF2E88);
  static const _violet = Color(0xFFB026FF);
  static const _blue = Color(0xFF2D5BFF);
  static const _cyan = Color(0xFF52F2FF);

  void _text(
    Canvas canvas,
    String text,
    Offset at,
    Color color,
    double size, {
    bool end = false,
    double width = 0,
  }) {
    final painter = TextPainter(
      text: TextSpan(
        text: text,
        style: TextStyle(
          color: color,
          fontSize: size,
          fontWeight: FontWeight.w800,
          letterSpacing: .6,
          fontFamily: 'Roboto',
          shadows: [Shadow(color: color.withValues(alpha: .8), blurRadius: 8)],
        ),
      ),
      textDirection: TextDirection.ltr,
    )..layout();
    painter.paint(
      canvas,
      end ? Offset(width - at.dx - painter.width, at.dy) : at,
    );
    painter.dispose();
  }

  void _brick(Canvas canvas, Rect rect, Color color) {
    final r = RRect.fromRectAndRadius(rect, const Radius.circular(7));
    canvas.drawRRect(
      r,
      Paint()
        ..color = color.withValues(alpha: .55)
        ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 7),
    );
    canvas.drawRRect(r, Paint()..color = color.withValues(alpha: .28));
    canvas.drawRRect(
      r,
      Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = 2
        ..color = color,
    );
  }

  @override
  void paint(Canvas canvas, Size size) {
    final w = size.width;
    final h = size.height;
    // A few faint stars.
    final rng = math.Random(7);
    final star = Paint()..color = const Color(0x55FFFFFF);
    for (var i = 0; i < 46; i++) {
      canvas.drawCircle(
        Offset(rng.nextDouble() * w, rng.nextDouble() * h),
        rng.nextDouble() * .9 + .3,
        star,
      );
    }
    _text(canvas, 'SCORE 380', const Offset(18, 18), _cyan, 17);
    _text(canvas, 'LV 1   HI 0', const Offset(18, 44), _pink, 12);
    // Balls left.
    for (var i = 0; i < 3; i++) {
      final c = Offset(w - 22 - i * 22, 28);
      if (i == 2) {
        canvas.drawCircle(
          c,
          6,
          Paint()
            ..style = PaintingStyle.stroke
            ..strokeWidth = 1.5
            ..color = _cyan.withValues(alpha: .5),
        );
      } else {
        canvas.drawCircle(c, 6, Paint()..color = _cyan);
      }
    }
    _text(
      canvas,
      'x1 BALLS',
      const Offset(14, 46),
      _cyan.withValues(alpha: .5),
      11,
      end: true,
      width: w,
    );
    // Bricks.
    final bw = (w - 18 * 2 - 8 * 4) / 5;
    const bh = 26.0;
    Rect at(int col, int row) =>
        Rect.fromLTWH(18 + col * (bw + 8), 92 + row * (bh + 10), bw, bh);
    _brick(canvas, at(0, 0), _pink);
    _brick(canvas, at(2, 1), _violet);
    _brick(canvas, at(3, 1), _violet);
    _brick(canvas, at(2, 2), _blue);
    _brick(canvas, at(3, 2), _blue);
    _brick(canvas, at(4, 0), _pink);
    _brick(canvas, at(1, 3), _blue);
    // The ball and its trail.
    final ball = Offset(w * .46, h * .56);
    for (var i = 10; i > 0; i--) {
      canvas.drawCircle(
        ball.translate(-i * 1.2, -i * 17.0),
        3.2 + (10 - i) * .25,
        Paint()..color = _cyan.withValues(alpha: .05 + (10 - i) * .03),
      );
    }
    canvas.drawCircle(
      ball,
      9,
      Paint()
        ..color = _cyan.withValues(alpha: .6)
        ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 8),
    );
    canvas.drawCircle(ball, 6, Paint()..color = Colors.white);
    // The paddle.
    final paddle = RRect.fromRectAndRadius(
      Rect.fromCenter(center: Offset(w * .43, h - 30), width: 112, height: 12),
      const Radius.circular(6),
    );
    canvas.drawRRect(
      paddle,
      Paint()
        ..shader = const LinearGradient(
          colors: [_pink, _cyan, _pink],
        ).createShader(paddle.outerRect)
        ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 6),
    );
    canvas.drawRRect(
      paddle.deflate(2),
      Paint()
        ..shader = const LinearGradient(
          colors: [Color(0xFFFF9BC8), Colors.white, Color(0xFFFF9BC8)],
        ).createShader(paddle.outerRect),
    );
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadKitGalleryFonts);
  setUp(() {
    KitLiveBackground.paintWithoutLoops = true;
    KitMotion.loops = false;
  });
  tearDown(() {
    KitLiveBackground.paintWithoutLoops = false;
    KitLiveBackground.frozenSeconds = null;
  });

  for (final scene in _Scene.values) {
    testWidgets(scene.file, (tester) async {
      debugDefaultTargetPlatformOverride = TargetPlatform.android;
      // Blurred shadows as a phone draws them (the test binding flattens
      // them); put back before the test ends.
      debugDisableShadows = false;
      tester.view.physicalSize = scene.size * 2;
      tester.view.devicePixelRatio = 2;
      // A phone's status bar and gesture bar.
      tester.view.padding = const FakeViewPadding(top: 48, bottom: 32);
      tester.view.viewPadding = const FakeViewPadding(top: 48, bottom: 32);
      addTearDown(tester.view.reset);
      KitLiveBackground.frozenSeconds = 21;
      final platform = _PagePlatform();
      WebViewPlatform.instance = platform;

      final recorder = FakeVoiceRecorder();
      final models = await tester.runAsync(readyVoiceModelManager);
      final conversation = DevPreviewConversation(
        delivery: () =>
            scene.busy ? DevPreviewDelivery.joinsTurn : DevPreviewDelivery.now,
        send: (_) async {
          if (scene.step == _Step.notSent) throw StateError('offline');
        },
      );
      final target = DevPreviewTarget.resolve(
        const DevPreviewLink(port: 8080),
        serverBaseUrl: scene.remote
            ? 'http://100.64.1.2:4096'
            : 'http://127.0.0.1:4096',
      );
      final boundary = GlobalKey();
      final theme = captureTheme(light: scene.light);
      await tester.pumpWidget(
        RepaintBoundary(
          key: boundary,
          child: ProviderScope(
            child: MaterialApp(
              debugShowCheckedModeBanner: false,
              theme: AppTheme.forLocale(theme, scene.locale),
              locale: scene.locale,
              supportedLocales: AppLocalizations.supportedLocales,
              localizationsDelegates: AppLocalizations.localizationsDelegates,
              builder: (context, child) => MediaQuery(
                data: MediaQuery.of(
                  context,
                ).copyWith(textScaler: TextScaler.linear(scene.scale)),
                child: auditWrap(context, child!),
              ),
              home: const SizedBox.expand(key: ValueKey('opener')),
            ),
          ),
        ),
      );
      unawaited(
        Navigator.of(
          tester.element(find.byKey(const ValueKey('opener'))),
        ).push<void>(
          KitPageRoute<void>(
            builder: (_) => DevPreviewScreen(
              target: target,
              supported: true,
              launcher: (_) async => true,
              onLogs: scene.logs ? () {} : null,
              conversation: scene.conversation ? conversation : null,
              voiceSupported: true,
              voice: () async => VoiceComposerController(
                models: models!,
                recorder: recorder,
                recognizer: FakeVoiceRecognizer(),
                ownsModels: false,
              ),
            ),
          ),
        ),
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 50));
      if (scene.step == _Step.failed) {
        platform.delegate?.onError?.call(
          const WebResourceError(
            errorCode: -6,
            description: 'refused',
            isForMainFrame: true,
          ),
        );
      } else {
        platform.delegate?.onFinished?.call(target.uri.toString());
      }
      await tester.pumpAndSettle();

      final words = scene.locale.languageCode == 'ar'
          ? 'اجعل الكرة أسرع قليلًا وأضف عدّاد الضربات'
          : 'Make the ball a little faster and add a combo counter';
      final field = find.byKey(const ValueKey('dev-preview-ask-field'));
      switch (scene.step) {
        case _Step.typed:
          await tester.enterText(field, words);
          await tester.pumpAndSettle();
        // The keyboard is not part of the capture; the field keeps its
        // words and its lit edge.
        case _Step.sent || _Step.notSent:
          await tester.enterText(field, words);
          await tester.pump();
          await tester.tap(find.byKey(const ValueKey('dev-preview-ask-send')));
          await tester.pump();
          await tester.pump(const Duration(milliseconds: 300));
        case _Step.listening:
          await tester.tap(find.byKey(const ValueKey('dev-preview-ask-voice')));
          await tester.runAsync(
            () => Future<void>.delayed(const Duration(milliseconds: 50)),
          );
          await tester.pump();
          await tester.pump(const Duration(milliseconds: 300));
        case _Step.details:
          await tester.tap(find.byKey(const ValueKey('dev-preview-title')));
          await tester.pumpAndSettle();
        case _Step.ready || _Step.failed:
          break;
      }
      expect(tester.takeException(), isNull);
      await writePng(
        '$_out/${scene.file}.png',
        await capturePng(tester, boundary, pixelRatio: 2),
      );
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pump(const Duration(seconds: 6));
      debugDisableShadows = true;
      debugDefaultTargetPlatformOverride = null;
    });
  }
}
