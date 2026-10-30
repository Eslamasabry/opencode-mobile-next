// Gallery of P10.3 "Voice as a composer mode": the chat's composer turned
// into voice mode — dictating (the level meter, the clock, Done in the send
// slot), a denied microphone explained in the mode with its fix, and a voice
// conversation (Send, "Read replies aloud"). Phone 412x915 and one wide
// window (1280x800), dark and light, with the app's real fonts at DPR 1.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/revamp/slice_p10_3_golden_test.dart
// and look at every changed image before committing it.
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/voice/audio.dart';
import 'package:opencode_mobile/voice/controller.dart';

import '../../tool/capture/fixtures.dart' show captureTheme, loadCaptureFonts;
import 'chat_3_support.dart';
import 'screen_voice_1_fixtures.dart';
import '../support/composer_tools.dart';

typedef _Scene =
    Future<void> Function(WidgetTester tester, ScriptedVoiceComposer voice);

Future<void> _mic(WidgetTester tester) async {
  await tester.tap(find.byKey(const Key('composer-voice-button')));
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 600));
}

final _scenes = <String, (_Scene, List<Size>)>{
  'p103_voice_dictating': (
    (tester, voice) => _mic(tester),
    const [Size(412, 915), Size(1280, 800)],
  ),
  'p103_mic_denied': (
    (tester, voice) async {
      await _mic(tester);
      voice.show(
        VoiceComposerState.error,
        failure: const VoicePermissionDenied(permanent: true),
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 600));
    },
    const [Size(412, 915)],
  ),
  'p103_voice_conversation': (
    (tester, voice) async {
      await openComposerTools(tester);
      await tester.pumpAndSettle();
      await tester.ensureVisible(
        find.byKey(const Key('composer-tools-advanced')),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('composer-tools-advanced')));
      await tester.pumpAndSettle();
      await tester.ensureVisible(
        find.byKey(const Key('composer-tool-conversation')),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('composer-tool-conversation')));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 600));
    },
    const [Size(412, 915)],
  ),
};

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);
  setUp(chat3MockSecureStorage);

  for (final MapEntry(key: name, value: (scene, sizes)) in _scenes.entries) {
    for (final size in sizes) {
      for (final light in [false, true]) {
        final sized = size.width == 412
            ? ''
            : '_${size.width.toInt()}x${size.height.toInt()}';
        final file = '$name${sized}_${light ? 'light' : 'dark'}';
        testWidgets(file, (tester) async {
          debugPlatformCapabilities = const PlatformCapabilities.android();
          debugDefaultTargetPlatformOverride = TargetPlatform.android;
          tester.view.physicalSize = size;
          tester.view.devicePixelRatio = 1;
          addTearDown(tester.view.reset);
          final api = Chat3Api()
            ..transcript = [
              chat3Prompt(
                'm1',
                'The checkout test fails about one run in five.',
              ),
            ];
          final conn = await chat3Controller(api: api);
          addTearDown(conn.dispose);
          final models = await ScriptedVoiceModels.create();
          final voice = ScriptedVoiceComposer(models: models);
          addTearDown(voice.dispose);
          final boundary = GlobalKey();
          try {
            await tester.pumpWidget(
              RepaintBoundary(
                key: boundary,
                child: ProviderScope(
                  overrides: [connProvider.overrideWithValue(conn)],
                  child: MaterialApp(
                    debugShowCheckedModeBanner: false,
                    theme: captureTheme(light: light),
                    localizationsDelegates:
                        AppLocalizations.localizationsDelegates,
                    supportedLocales: AppLocalizations.supportedLocales,
                    builder: (context, child) => MediaQuery(
                      data: MediaQuery.of(
                        context,
                      ).copyWith(disableAnimations: true),
                      child: child!,
                    ),
                    home: ChatScreen(
                      sessionID: 'session-1',
                      voiceController: voice,
                    ),
                  ),
                ),
              ),
            );
            await tester.pumpAndSettle();
            await scene(tester, voice);
            expect(find.byKey(const Key('voice-mode')), findsOneWidget);
            expect(tester.takeException(), isNull);
            await expectLater(
              find.byKey(boundary),
              matchesGoldenFile('goldens/$file.png'),
            );
          } finally {
            await tester.pumpWidget(const SizedBox.shrink());
            await tester.pump();
            debugDefaultTargetPlatformOverride = null;
            debugPlatformCapabilities = null;
          }
        });
      }
    }
  }
}
