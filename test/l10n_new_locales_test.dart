// FG5: Spanish, Japanese, Brazilian Portuguese, Russian and Simplified
// Chinese are offered, matched from the device's language, and fall back to
// English for any message they do not carry (docs/l10n/fg5-key-set.md).
import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/app_locale.dart';

const _languages = ['es', 'ja', 'pt', 'ru', 'zh'];

Map<String, Object?> _arb(String code) =>
    jsonDecode(File('lib/l10n/app_$code.arb').readAsStringSync())
        as Map<String, Object?>;

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  final supported = AppLocalizations.supportedLocales;

  group('the app ships the languages', () {
    test('supportedLocales, the saved-choice set and the picker agree', () {
      final shipped = {for (final locale in supported) locale.languageCode};
      expect(shipped, {'en', 'ar', ..._languages});
      expect(AppLocaleStore.supportedLanguages, shipped);
      expect(AppLocaleStore.pickerLanguages.toSet(), shipped);
      expect(AppLocaleStore.pickerLanguages.first, 'en');
    });

    test('each ARB names its own locale and and matches its file name', () {
      for (final code in _languages) {
        expect(_arb(code)['@@locale'], code, reason: code);
      }
    });
  });

  group('the device language decides', () {
    Locale resolve(List<Locale> device) => resolveAppLocales(device, supported);

    test('Flutter alone would hand an unshipped language to Arabic', () {
      // The reason resolveAppLocales exists: the generated list is
      // alphabetical, so its first entry is Arabic.
      expect(supported.first, const Locale('ar'));
      expect(
        basicLocaleListResolution(const [Locale('de', 'DE')], supported),
        const Locale('ar'),
      );
    });

    test('a language the app does not ship reads English', () {
      expect(resolve(const [Locale('de', 'DE')]), const Locale('en'));
      expect(resolve(const [Locale('ko'), Locale('fr')]), const Locale('en'));
      expect(resolve(const []), const Locale('en'));
    });

    test('each shipped language is found by its code, region or script', () {
      expect(resolve(const [Locale('ja', 'JP')]), const Locale('ja'));
      expect(resolve(const [Locale('es', 'MX')]), const Locale('es'));
      expect(resolve(const [Locale('ru', 'RU')]), const Locale('ru'));
      expect(resolve(const [Locale('ar', 'EG')]), const Locale('ar'));
      // Brazilian Portuguese is the one Portuguese catalogue.
      expect(resolve(const [Locale('pt', 'BR')]), const Locale('pt'));
      expect(resolve(const [Locale('pt', 'PT')]), const Locale('pt'));
      // Simplified Chinese is the one Chinese catalogue; Traditional readers
      // get it too rather than English.
      expect(
        resolve(const [
          Locale.fromSubtags(
            languageCode: 'zh',
            scriptCode: 'Hans',
            countryCode: 'CN',
          ),
        ]),
        const Locale('zh'),
      );
      expect(
        resolve(const [
          Locale.fromSubtags(
            languageCode: 'zh',
            scriptCode: 'Hant',
            countryCode: 'TW',
          ),
        ]),
        const Locale('zh'),
      );
    });

    test('the next language in the device list is tried before English', () {
      expect(resolve(const [Locale('de'), Locale('ru')]), const Locale('ru'));
    });

    testWidgets('an app with the callback shows the resolved language', (
      tester,
    ) async {
      Locale? seen;
      addTearDown(tester.platformDispatcher.clearLocalesTestValue);
      for (final (device, expected) in [
        (const Locale('de', 'DE'), const Locale('en')),
        (const Locale('ja', 'JP'), const Locale('ja')),
      ]) {
        tester.platformDispatcher.localesTestValue = [device];
        await tester.pumpWidget(
          MaterialApp(
            supportedLocales: supported,
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            localeListResolutionCallback: resolveAppLocales,
            home: Builder(
              builder: (context) {
                seen = Localizations.localeOf(context);
                return const SizedBox();
              },
            ),
          ),
        );
        expect(seen, expected, reason: '$device');
      }
    });
  });

  group('the catalogues', () {
    final english = lookupAppLocalizations(const Locale('en'));

    test('a translated message reads in the language', () {
      for (final code in _languages) {
        final l10n = lookupAppLocalizations(Locale(code));
        expect(
          l10n.kitComposerSend,
          isNot(english.kitComposerSend),
          reason: code,
        );
        expect(l10n.localeName, code);
      }
      expect(lookupAppLocalizations(const Locale('ja')).kitComposerSend, '送信');
      expect(
        lookupAppLocalizations(const Locale('es')).chatsHomeNewChat,
        'Nueva conversación',
      );
    });

    test('a message the language does not carry reads English, not Arabic', () {
      // A Settings string outside the first-run and chat core.
      final arb = _arb('ja');
      expect(arb.containsKey('servicesTitle'), isFalse);
      for (final code in _languages) {
        final l10n = lookupAppLocalizations(Locale(code));
        expect(l10n.servicesTitle, english.servicesTitle, reason: code);
      }
    });

    test('plural forms follow the language', () {
      expect(
        lookupAppLocalizations(const Locale('ru')).chatsFilterChatCount(1),
        '1 чат',
      );
      expect(
        lookupAppLocalizations(const Locale('ru')).chatsFilterChatCount(3),
        '3 чата',
      );
      expect(
        lookupAppLocalizations(const Locale('ru')).chatsFilterChatCount(5),
        '5 чатов',
      );
      expect(
        lookupAppLocalizations(const Locale('ru')).chatsFilterChatCount(21),
        '21 чат',
      );
      expect(
        lookupAppLocalizations(const Locale('es')).chatsFilterChatCount(1),
        '1 conversación',
      );
      expect(
        lookupAppLocalizations(const Locale('es')).chatsFilterChatCount(2),
        '2 conversaciones',
      );
      expect(
        lookupAppLocalizations(const Locale('ja')).chatsFilterChatCount(1),
        '1件の会話',
      );
      expect(
        lookupAppLocalizations(const Locale('zh')).chatsFilterChatCount(2),
        '2 个对话',
      );
    });

    test('every core key is a message the language translates', () {
      final core = File('tool/l10n/partial_locales_core_keys.txt')
          .readAsLinesSync()
          .where((line) => line.isNotEmpty && !line.startsWith('#'))
          .toSet();
      expect(core.length, greaterThan(600));
      for (final code in _languages) {
        final arb = _arb(code);
        expect(
          core.where((key) => arb[key] is! String),
          isEmpty,
          reason: '$code lacks core keys',
        );
      }
    });
  });
}
