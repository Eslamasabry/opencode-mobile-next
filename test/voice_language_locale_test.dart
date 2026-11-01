// Voice language: "Same as the app" listens in the app's language when the
// speech model knows it; the other languages the multilingual model has can
// be named; old stored choices still load.
import 'dart:ui' show Locale;

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/voice/controller.dart';
import 'package:opencode_mobile/voice/model_manifest.dart';
import 'package:opencode_mobile/voice/presentation.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'voice_controller_test.dart'
    show FakeVoiceRecorder, FakeVoiceRecognizer, readyVoiceModelManager;

void main() {
  group('resolveVoiceLanguage', () {
    test('Same as the app follows every language the app is translated to', () {
      for (final locale in AppLocalizations.supportedLocales) {
        final resolved = resolveVoiceLanguage(
          VoiceLanguage.auto,
          locale.languageCode,
        );
        expect(resolved.isNamed, isTrue, reason: locale.languageCode);
        expect(resolved.whisperCode, locale.languageCode);
      }
    });

    test('a language the model lacks falls back to listening for it', () {
      expect(
        resolveVoiceLanguage(VoiceLanguage.auto, 'tlh'),
        VoiceLanguage.detect,
      );
      expect(
        resolveVoiceLanguage(VoiceLanguage.auto, null),
        VoiceLanguage.detect,
      );
      expect(
        resolveVoiceLanguage(VoiceLanguage.auto, ''),
        VoiceLanguage.detect,
      );
    });

    test('older and regional codes are folded in', () {
      expect(VoiceLanguage.forLanguageCode('iw'), VoiceLanguage.hebrew);
      expect(VoiceLanguage.forLanguageCode('nb'), VoiceLanguage.norwegian);
      expect(VoiceLanguage.forLanguageCode('in'), VoiceLanguage.indonesian);
      expect(VoiceLanguage.forLanguageCode('ZH'), VoiceLanguage.chinese);
    });

    test('a named language is used whatever the app speaks', () {
      expect(
        resolveVoiceLanguage(VoiceLanguage.french, 'ar'),
        VoiceLanguage.french,
      );
      expect(
        resolveVoiceLanguage(VoiceLanguage.detect, 'ar'),
        VoiceLanguage.detect,
      );
    });

    test('more than English and Arabic are offered, with unique codes', () {
      final named = VoiceLanguage.values.where((l) => l.isNamed).toList();
      expect(named.length, greaterThan(20));
      expect(named.map((l) => l.whisperCode).toSet().length, named.length);
    });

    test('the old stored choices keep their meaning', () async {
      SharedPreferences.setMockInitialValues({'voice.language': 'arabic'});
      expect(VoiceLanguage.fromID('arabic').whisperCode, 'ar');
      expect(VoiceLanguage.fromID('english').whisperCode, 'en');
      expect(VoiceLanguage.fromID('auto'), VoiceLanguage.auto);
      expect(VoiceLanguage.fromID('nonsense'), VoiceLanguage.auto);
    });
  });

  group('the recognizer is told the right language', () {
    Future<String> told({
      required VoiceLanguage chosen,
      required String appLanguage,
    }) async {
      final manager = await readyVoiceModelManager();
      await manager.setLanguage(chosen);
      final recorder = FakeVoiceRecorder();
      final recognizer = FakeVoiceRecognizer()..automaticResult = 'hello';
      final controller = VoiceComposerController(
        models: manager,
        recorder: recorder,
        recognizer: recognizer,
      )..appLanguageCode = appLanguage;
      addTearDown(controller.dispose);
      await controller.startListening();
      recorder.addSamples();
      await controller.stopListening();
      return recognizer.request!.language.whisperCode;
    }

    test('Same as the app in an Arabic app listens for Arabic', () async {
      expect(await told(chosen: VoiceLanguage.auto, appLanguage: 'ar'), 'ar');
    });

    test('Same as the app in a Japanese app listens for Japanese', () async {
      expect(await told(chosen: VoiceLanguage.auto, appLanguage: 'ja'), 'ja');
    });

    test('Detect from speech ignores the app language', () async {
      expect(await told(chosen: VoiceLanguage.detect, appLanguage: 'ar'), '');
    });

    test('a named language wins over the app language', () async {
      expect(
        await told(chosen: VoiceLanguage.spanish, appLanguage: 'ar'),
        'es',
      );
    });
  });

  test('every language has a name to show', () {
    final strings = lookupAppLocalizations(const Locale('en'));
    for (final language in VoiceLanguage.values) {
      expect(voiceLanguageLabel(language, strings), isNotEmpty);
    }
  });

  group('voiceLanguageNote', () {
    final strings = lookupAppLocalizations(const Locale('en'));
    final base = voiceModelPack('base');
    final tiny = voiceModelPack('tiny');

    test('says plainly when the app language is not known offline', () {
      final note = voiceLanguageNote(
        strings,
        chosen: VoiceLanguage.auto,
        pack: base,
        appLanguageCode: 'tlh',
      );
      expect(note, contains('tlh'));
      expect(note, contains('offline'));
    });

    test('says nothing when the language works', () {
      expect(
        voiceLanguageNote(
          strings,
          chosen: VoiceLanguage.auto,
          pack: base,
          appLanguageCode: 'ar',
        ),
        isNull,
      );
    });

    test('warns that the Compact model is rough outside English', () {
      expect(
        voiceLanguageNote(
          strings,
          chosen: VoiceLanguage.arabic,
          pack: tiny,
          appLanguageCode: 'en',
        ),
        isNotNull,
      );
      expect(
        voiceLanguageNote(
          strings,
          chosen: VoiceLanguage.english,
          pack: tiny,
          appLanguageCode: 'ar',
        ),
        isNull,
      );
    });
  });
}
