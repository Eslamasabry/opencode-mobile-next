// Send when I stop talking: a recording ends by itself after 1.5 s of quiet
// once there was speech; the words wait in a visible countdown with Cancel;
// nothing empty or near-empty, and no command, goes out by itself; the
// setting is off until the person turns it on.
import 'dart:typed_data';

import 'package:clock/clock.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_composer.dart';
import 'package:opencode_mobile/voice/controller.dart';
import 'package:opencode_mobile/voice/silence.dart';

import 'voice_controller_test.dart'
    show FakeVoiceRecorder, FakeVoiceRecognizer, readyVoiceModelManager;

/// 16-bit mono PCM, 128 ms long (a recorder buffer), at [amplitude] of full
/// scale.
List<int> _buffer(double amplitude) {
  final bytes = ByteData(4096);
  final value = (amplitude * 32767).round();
  for (var i = 0; i < 2048; i++) {
    bytes.setInt16(i * 2, i.isEven ? value : -value, Endian.little);
  }
  return bytes.buffer.asUint8List();
}

const _span = Duration(milliseconds: 128);

void main() {
  group('SilenceDetector', () {
    test('quiet before any speech never ends the recording', () {
      final detector = SilenceDetector();
      for (var i = 0; i < 100; i++) {
        expect(detector.add(0.001, _span), isFalse);
      }
    });

    test('1.5 s of quiet after speech ends it, and not a moment sooner', () {
      final detector = SilenceDetector();
      for (var i = 0; i < 8; i++) {
        expect(detector.add(0.1, _span), isFalse);
      }
      // 11 buffers = 1.408 s: not yet; the 12th makes 1.536 s.
      for (var i = 0; i < 11; i++) {
        expect(detector.add(0.002, _span), isFalse, reason: 'buffer ${i + 1}');
      }
      expect(detector.add(0.002, _span), isTrue);
    });

    test('speaking again restarts the quiet', () {
      final detector = SilenceDetector();
      for (var i = 0; i < 8; i++) {
        detector.add(0.1, _span);
      }
      for (var i = 0; i < 10; i++) {
        detector.add(0.002, _span);
      }
      expect(detector.add(0.1, _span), isFalse);
      for (var i = 0; i < 11; i++) {
        expect(detector.add(0.002, _span), isFalse);
      }
    });

    test('a click is not speech', () {
      final detector = SilenceDetector();
      detector.add(0.2, _span);
      for (var i = 0; i < 100; i++) {
        expect(detector.add(0.001, _span), isFalse);
      }
    });
  });

  group('what may be sent by itself', () {
    test('nothing, a stray sound and the model\'s notes are not sent', () {
      for (final words in [
        '',
        '   ',
        'a',
        'ا',
        '[BLANK_AUDIO]',
        '(music)',
        '♪ ♪',
        '[ Silence ] .',
        '...',
        '*sigh*',
      ]) {
        expect(mayAutoSend(words), isFalse, reason: '"$words"');
      }
    });

    test('real words are sent, in any script', () {
      for (final words in [
        'ok',
        'Run the tests',
        'شغّل الاختبارات',
        '[BLANK_AUDIO] fix the build',
        '42',
      ]) {
        expect(mayAutoSend(words), isTrue, reason: '"$words"');
      }
    });

    test('a spoken slash or bang is never run as a command', () {
      expect(mayAutoSend('/new session'), isFalse);
      expect(mayAutoSend('  !rm -rf build'), isFalse);
    });
  });

  group('the controller', () {
    test('auto-send is off by default', () async {
      final manager = await readyVoiceModelManager();
      expect(manager.autoSend, isFalse);
      await manager.setAutoSend(true);
      expect(manager.autoSend, isTrue);
      manager.dispose();
    });

    test('stops by itself after the pause and says why', () async {
      final recorder = FakeVoiceRecorder();
      final recognizer = FakeVoiceRecognizer()..automaticResult = 'run it';
      final controller = VoiceComposerController(
        models: await readyVoiceModelManager(),
        recorder: recorder,
        recognizer: recognizer,
      );
      addTearDown(controller.dispose);

      controller.stopOnSilence = true;
      await controller.startListening();
      for (var i = 0; i < 8; i++) {
        recorder.addSamples(_buffer(0.2));
      }
      for (var i = 0; i < 11; i++) {
        recorder.addSamples(_buffer(0.001));
      }
      await Future<void>.delayed(Duration.zero);
      expect(controller.state, VoiceComposerState.listening);
      expect(recorder.stopCalls, 0);

      recorder.addSamples(_buffer(0.001));
      for (var i = 0; i < 20; i++) {
        await Future<void>.delayed(Duration.zero);
      }
      expect(recorder.stopCalls, 1);
      expect(controller.state, VoiceComposerState.draft);
      expect(controller.draft, 'run it');
      expect(controller.stoppedOnSilence, isTrue);
    });

    test('without the opt-in a long pause never stops it', () async {
      final recorder = FakeVoiceRecorder();
      final controller = VoiceComposerController(
        models: await readyVoiceModelManager(),
        recorder: recorder,
        recognizer: FakeVoiceRecognizer(),
      );
      addTearDown(controller.dispose);

      await controller.startListening();
      for (var i = 0; i < 8; i++) {
        recorder.addSamples(_buffer(0.2));
      }
      for (var i = 0; i < 60; i++) {
        recorder.addSamples(_buffer(0.001));
      }
      await Future<void>.delayed(Duration.zero);
      expect(recorder.stopCalls, 0);
      expect(controller.state, VoiceComposerState.listening);
      expect(controller.stoppedOnSilence, isFalse);
    });
  });

  group('the countdown in the pill', () {
    Future<void> pump(
      WidgetTester tester, {
      required DateTime sendAt,
      required VoidCallback onSendNow,
      required VoidCallback onCancelSend,
    }) async {
      final controller = TextEditingController(text: 'Run the tests');
      final focus = FocusNode();
      addTearDown(controller.dispose);
      addTearDown(focus.dispose);
      tester.view.physicalSize = const Size(412, 915);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(
        MaterialApp(
          theme: AppTheme.dark(),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Scaffold(
            body: Align(
              alignment: Alignment.bottomCenter,
              child: KitComposer(
                controller: controller,
                focusNode: focus,
                hint: 'Ask OpenCode…',
                onSend: () {},
                voice: KitComposerVoice(
                  phase: KitVoicePhase.sendingSoon,
                  onExit: onCancelSend,
                  sendAt: sendAt,
                  onSendNow: onSendNow,
                  onCancelSend: onCancelSend,
                ),
              ),
            ),
          ),
        ),
      );
      await tester.pump();
    }

    testWidgets('counts 3, 2, 1 and then sends once', (tester) async {
      var sent = 0;
      await pump(
        tester,
        sendAt: clock.now().add(voiceAutoSendCountdown),
        onSendNow: () => sent++,
        onCancelSend: () {},
      );
      expect(find.text('Sending in 3…'), findsOneWidget);
      await tester.pump(const Duration(seconds: 1));
      expect(find.text('Sending in 2…'), findsOneWidget);
      await tester.pump(const Duration(seconds: 1));
      expect(find.text('Sending in 1…'), findsOneWidget);
      expect(sent, 0);
      await tester.pump(const Duration(seconds: 1));
      expect(sent, 1);
      await tester.pump(const Duration(seconds: 3));
      expect(sent, 1, reason: 'once');
    });

    testWidgets('Cancel keeps the words and sends nothing', (tester) async {
      var sent = 0, cancelled = 0;
      await pump(
        tester,
        sendAt: clock.now().add(voiceAutoSendCountdown),
        onSendNow: () => sent++,
        onCancelSend: () => cancelled++,
      );
      await tester.tap(find.byKey(const ValueKey('kit-voice-cancel-send')));
      await tester.pump();
      expect(cancelled, 1);
      expect(sent, 0);
    });

    testWidgets('Send now sends at once, once', (tester) async {
      var sent = 0;
      await pump(
        tester,
        sendAt: clock.now().add(voiceAutoSendCountdown),
        onSendNow: () => sent++,
        onCancelSend: () {},
      );
      await tester.tap(find.byKey(const ValueKey('kit-voice-send-now')));
      await tester.pump(const Duration(seconds: 5));
      expect(sent, 1);
    });
  });
}
