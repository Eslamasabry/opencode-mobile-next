// Review frames for Settings > Appearance > Surfaces: each surface style
// (solid, soft, brutal, paper) in light and dark behind four real screens: the
// Conversations home, a chat with a running turn and a pending question,
// the Settings hub, and the composer's tools sheet open over a chat. The
// choice reaches the screens the way the app does it: stored through the
// controller and provided above the navigator by a KitEffectsScope. The live
// look (aurora) is painted at a fixed second, blurred shadows render as on a
// phone, real fonts, no server. Phone 412x915 dp, written at 2x pixels.
//
//   TZ=UTC flutter test -j 2 tool/capture/surfaces_test.dart
//
// Output: SURFACES_SHOTS_DIR (default build/surfaces-review), files named
// <screen>-<style>-<light|dark>.png.
import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/kit/style/kit_style_theme.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/screens/home_screen.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';

import '../../test/support/composer_tools.dart';
import '../../test/support/setup_capture_preferences.dart';
import 'fixtures.dart';

final _out =
    Platform.environment['SURFACES_SHOTS_DIR'] ?? 'build/surfaces-review';

/// The question the running checkout turn is waiting on.
const _question = PendingQuestion(
  id: 'q_checkout',
  sessionID: checkoutSessionID,
  prompts: [
    QuestionPrompt(
      title: 'Test scope',
      question: 'Run the whole suite, or only the checkout tests?',
      multiple: false,
      custom: true,
      choices: [
        QuestionChoice(label: 'Whole suite', description: 'About 4 min'),
        QuestionChoice(label: 'Only checkout', description: 'About 20 s'),
      ],
    ),
  ],
);

void _mockPlatform(WidgetTester tester) {
  const secure = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');
  const info = MethodChannel('dev.fluttercommunity.plus/package_info');
  final messenger = tester.binding.defaultBinaryMessenger;
  messenger.setMockMethodCallHandler(
    secure,
    (call) async => call.method == 'readAll' ? <String, String>{} : null,
  );
  messenger.setMockMethodCallHandler(
    info,
    (call) async => <String, dynamic>{
      'appName': 'OpenCode Mobile',
      'packageName': 'com.opencode.mobile',
      'version': '1.0.44',
      'buildNumber': '52',
      'buildSignature': '',
    },
  );
  addTearDown(() {
    messenger.setMockMethodCallHandler(secure, null);
    messenger.setMockMethodCallHandler(info, null);
  });
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() async {
    await loadCaptureFonts();
    chatScreenFor = (id) => ChatScreen(sessionID: id);
  });
  setUp(() {
    KitLiveBackground.paintWithoutLoops = true;
    KitLiveBackground.frozenSeconds = 8;
    KitMotion.loops = false;
  });
  tearDown(() {
    KitLiveBackground.paintWithoutLoops = false;
    KitLiveBackground.frozenSeconds = null;
  });

  /// Live parts keep a ticker running, so settle by time, not by quiet.
  Future<void> settle(WidgetTester tester) async {
    for (var i = 0; i < 12; i++) {
      await tester.pump(const Duration(milliseconds: 100));
    }
  }

  /// Mounts [home] the way main.dart hosts the app: the stored effects
  /// above the navigator, so pushed routes and sheets read them too.
  Future<(ConnectionController, GlobalKey)> mount(
    WidgetTester tester, {
    required Widget Function(ConnectionController controller) home,
    required KitSurfaceStyle style,
    required bool light,
    CaptureApi? api,
    void Function(CaptureController controller)? before,
  }) async {
    _mockPlatform(tester);
    tester.view.physicalSize = const Size(412, 915);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final controller = await captureController(
      prefs: await setupCapturePreferences(),
      api: api ?? CaptureApi(),
    );
    addTearDown(controller.dispose);
    controller.catalog = sampleCatalog();
    controller.appearance.value = light
        ? AppAppearance.light
        : AppAppearance.dark;
    await tester.runAsync(
      // As the Style picker writes it (hook H7: Paper starts with the live
      // look off).
      () => controller.setEffects(
        KitSurfaceLook.choose(const KitEffects(), style),
      ),
    );
    expect(controller.effects.value.surfaceStyle, style);
    before?.call(controller);
    final boundary = GlobalKey();
    await tester.pumpWidget(
      captureApp(
        home: home(controller),
        boundaryKey: boundary,
        controller: controller,
        light: light,
        routes: {
          '/home': (_) => const Scaffold(),
          '/servers': (_) => const Scaffold(),
          '/guide': (_) => const Scaffold(),
        },
        aboveNavigator: (navigator) => ValueListenableBuilder<KitEffects>(
          valueListenable: controller.effects,
          builder: (context, effects, child) =>
              // The Style's own ground, as main.dart places it (hook H6).
              KitEffectsScope(
                effects: effects,
                child: kitStyleTheme(child: child!),
              ),
          child: navigator,
        ),
      ),
    );
    // The look loads its warm-up off the fake clock.
    await tester.runAsync(
      () => Future<void>.delayed(const Duration(milliseconds: 300)),
    );
    await settle(tester);
    return (controller, boundary);
  }

  Future<void> shoot(
    WidgetTester tester,
    GlobalKey boundary,
    String screen,
    KitSurfaceStyle style,
    bool light,
  ) async {
    expect(tester.takeException(), isNull);
    await writePng(
      '$_out/$screen-${style.name}-${light ? 'light' : 'dark'}.png',
      await capturePng(tester, boundary, pixelRatio: 2),
    );
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pump(const Duration(seconds: 1));
  }

  /// Blurred shadows as a phone draws them (soft surfaces are made of
  /// them); put back inside the test, before the binding checks it.
  Future<void> withShadows(Future<void> Function() body) async {
    debugDisableShadows = false;
    try {
      await body();
    } finally {
      debugDisableShadows = true;
    }
  }

  for (final style in KitSurfaceStyle.values) {
    for (final light in [true, false]) {
      final mode = light ? 'light' : 'dark';

      // The Conversations home: the shell's list with the sample sessions,
      // the checkout one busy.
      testWidgets('home ${style.name} $mode', (tester) async {
        await withShadows(() async {
          final (_, boundary) = await mount(
            tester,
            home: (_) => const HomeScreen(initialTab: 0),
            style: style,
            light: light,
          );
          await shoot(tester, boundary, 'home', style, light);
        });
      });

      // A running turn (the shell tool still running) and the question it
      // waits on, as a card in the conversation.
      testWidgets('chat ${style.name} $mode', (tester) async {
        await withShadows(() async {
          final api = CaptureApi()
            ..messagesHandler = (_) async =>
                sampleTranscript(awaitingPermission: true);
          final (_, boundary) = await mount(
            tester,
            home: (_) => const ChatScreen(sessionID: checkoutSessionID),
            style: style,
            light: light,
            api: api,
            before: (controller) =>
                controller.questions = {_question.id: _question},
          );
          await shoot(tester, boundary, 'chat', style, light);
        });
      });

      testWidgets('settings ${style.name} $mode', (tester) async {
        await withShadows(() async {
          final (_, boundary) = await mount(
            tester,
            home: (controller) => SettingsScreen(controller: controller),
            style: style,
            light: light,
          );
          await shoot(tester, boundary, 'settings', style, light);
        });
      });

      // The composer's tools sheet ("+", then More where there is a tray)
      // open over a finished chat.
      testWidgets('sheet ${style.name} $mode', (tester) async {
        await withShadows(() async {
          final api = CaptureApi()
            ..busy = {}
            ..messagesHandler = (_) async => sampleTranscript();
          final (_, boundary) = await mount(
            tester,
            home: (_) => const ChatScreen(sessionID: checkoutSessionID),
            style: style,
            light: light,
            api: api,
          );
          await openComposerTools(tester);
          await settle(tester);
          expect(find.byKey(const Key('composer-tools-sheet')), findsOneWidget);
          await shoot(tester, boundary, 'sheet', style, light);
        });
      });

      // A finished chat (contract §8, 3): the prompt bubble, the reply, the
      // folded work line and a code block, the composer at rest.
      testWidgets('done ${style.name} $mode', (tester) async {
        await withShadows(() async {
          final api = CaptureApi()
            ..busy = {}
            ..messagesHandler = (_) async => sampleTranscript();
          final (_, boundary) = await mount(
            tester,
            home: (_) => const ChatScreen(sessionID: checkoutSessionID),
            style: style,
            light: light,
            api: api,
          );
          await shoot(tester, boundary, 'done', style, light);
        });
      });

      // A confirmation sheet (contract §8, 6) over Settings: the main
      // action and the neutral one; then the main action held down (the
      // press look).
      for (final held in [false, true]) {
        final screen = held ? 'confirm-pressed' : 'confirm';
        testWidgets('$screen ${style.name} $mode', (tester) async {
          await withShadows(() async {
            final (_, boundary) = await mount(
              tester,
              home: (controller) => SettingsScreen(controller: controller),
              style: style,
              light: light,
            );
            const confirm = Key('capture-confirm');
            unawaited(
              showKitConfirm(
                tester.element(find.byType(SettingsScreen)),
                title: 'Share this conversation?',
                body:
                    'Anyone with the link can read it. You can stop '
                    'sharing at any time.',
                confirmLabel: 'Share',
                consequences: const ['The link works until you stop sharing'],
                confirmKey: confirm,
              ),
            );
            await settle(tester);
            TestGesture? gesture;
            if (held) {
              gesture = await tester.startGesture(
                tester.getCenter(find.byKey(confirm)),
              );
              // The state fade starts on the first frame and ends on the next.
              await tester.pump(const Duration(milliseconds: 50));
              await tester.pump(const Duration(milliseconds: 300));
            }
            await shoot(tester, boundary, screen, style, light);
            await gesture?.cancel();
          });
        });
      }
    }
  }

  // Frame cost while the Conversations list scrolls, per style: the time
  // the framework spends building, laying out and recording each frame
  // (the UI thread; the raster side runs on the phone's GPU and is not
  // measured here), and how many background blurs are live. No blur may
  // sit inside a scrolling list. Printed as a line starting with PERF.
  for (final style in KitSurfaceStyle.values) {
    for (final light in [true, false]) {
      final mode = light ? 'light' : 'dark';
      testWidgets('frame timing ${style.name} $mode', (tester) async {
        await withShadows(() async {
          await mount(
            tester,
            home: (_) => const HomeScreen(initialTab: 0),
            style: style,
            light: light,
          );
          final blurs = tester
              .widgetList<BackdropFilter>(find.byType(BackdropFilter))
              .where((b) => b.enabled)
              .length;
          final inLists = find
              .descendant(
                of: find.byType(Scrollable),
                matching: find.byWidgetPredicate(
                  (w) => w is BackdropFilter && w.enabled,
                ),
              )
              .evaluate()
              .length;
          expect(inLists, 0, reason: 'a blur inside a scrolling list');
          final scrollable = find.byType(Scrollable).first;
          final gesture = await tester.startGesture(
            tester.getCenter(scrollable),
          );
          final frames = <int>[];
          for (var i = 0; i < 120; i++) {
            await gesture.moveBy(Offset(0, i < 60 ? -6 : 6));
            final watch = Stopwatch()..start();
            await tester.pump(const Duration(milliseconds: 16));
            frames.add(watch.elapsedMicroseconds);
          }
          await gesture.up();
          await tester.pump(const Duration(seconds: 1));
          // The first frames warm caches; count the steady part.
          final steady = frames.sublist(20)..sort();
          double ms(int us) => us / 1000;
          final mean = steady.reduce((a, b) => a + b) / steady.length;
          final line =
              '${style.name} $mode: blurs $blurs, mean '
              '${ms(mean.round()).toStringAsFixed(2)} ms, p90 '
              '${ms(steady[(steady.length * .9).floor()]).toStringAsFixed(2)}'
              ' ms, worst ${ms(steady.last).toStringAsFixed(2)} ms '
              '(${steady.length} frames)';
          stdout.writeln('PERF $line');
          await tester.pumpWidget(const SizedBox.shrink());
          await tester.pump(const Duration(seconds: 1));
        });
      });
    }
  }
}
