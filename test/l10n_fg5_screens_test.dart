// FG5: the first-run welcome and a new chat's composer read in Japanese,
// Simplified Chinese, Spanish, Brazilian Portuguese and Russian, and fit:
// a translated string is on screen, no English stands where a translation
// exists, and nothing overflows at the default and at a large text size on
// a narrow phone. English is the control: what it does at the same size
// (the model chip leaves the composer's row at large text, for one) the
// other languages may do too.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';

import 'support/fg5_locale_screens.dart';

AppLocalizations _l10n(Locale locale) => lookupAppLocalizations(locale);

const _phone = Size(360, 780);

/// The welcome is a lazy list: at large text a line is not built until it
/// is scrolled near.
Future<void> _scrollTo(WidgetTester tester, String text) async {
  await tester.scrollUntilVisible(
    find.text(text),
    120,
    scrollable: find.byType(Scrollable).first,
  );
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  final english = _l10n(const Locale('en'));

  for (final scale in const [1.0, 1.5]) {
    testWidgets('control: English welcome at ${scale}x text fits', (
      tester,
    ) async {
      final done = await mountFg5Welcome(
        tester,
        locale: const Locale('en'),
        size: _phone,
        textScale: scale,
      );
      addTearDown(done);
      expect(tester.takeException(), isNull);
    });

    testWidgets('control: English composer at ${scale}x text fits', (
      tester,
    ) async {
      final done = await mountFg5Composer(
        tester,
        locale: const Locale('en'),
        size: _phone,
        textScale: scale,
      );
      addTearDown(done);
      expect(tester.takeException(), isNull);
    });
  }

  for (final locale in fg5Locales) {
    final token = fg5Token(locale);
    final l10n = _l10n(Locale(locale.languageCode));

    testWidgets('welcome in $token reads in the language', (tester) async {
      final done = await mountFg5Welcome(tester, locale: locale, size: _phone);
      addTearDown(done);
      expect(tester.takeException(), isNull);
      expect(find.text(l10n.onboardingValueTitle), findsOneWidget);
      expect(find.text(l10n.firstRunWhereQuestion), findsOneWidget);
      expect(find.text(l10n.firstRunOnComputer), findsOneWidget);
      expect(find.text(l10n.onboardingTermuxSetup), findsOneWidget);
      expect(find.text(l10n.firstRunJustShowMe), findsOneWidget);
      // The English words are gone where the language has its own.
      expect(find.text(english.onboardingValueTitle), findsNothing);
      expect(find.text(english.firstRunWhereQuestion), findsNothing);
      expect(find.text(english.firstRunOnComputer), findsNothing);
      expect(find.text(english.firstRunJustShowMe), findsNothing);
    });

    testWidgets('welcome in $token fits at large text', (tester) async {
      final done = await mountFg5Welcome(
        tester,
        locale: locale,
        size: _phone,
        textScale: 1.5,
      );
      addTearDown(done);
      expect(tester.takeException(), isNull);
      for (final text in [
        l10n.onboardingValueTitle,
        l10n.firstRunWhereQuestion,
        l10n.firstRunOnComputer,
        l10n.firstRunJustShowMe,
      ]) {
        await _scrollTo(tester, text);
        expect(find.text(text), findsOneWidget);
      }
      expect(tester.takeException(), isNull);
    });

    testWidgets('composer in $token reads in the language', (tester) async {
      final done = await mountFg5Composer(tester, locale: locale, size: _phone);
      addTearDown(done);
      expect(tester.takeException(), isNull);
      expect(find.text(l10n.chatsNewPrompt), findsOneWidget);
      expect(find.text(l10n.chatStartExplainProject), findsOneWidget);
      // The offline note sits under the draft in the composer.
      expect(find.text(l10n.kitComposerOffline), findsOneWidget);
      expect(find.text(l10n.kitModelChoose), findsOneWidget);
      expect(find.text(english.chatsNewPrompt), findsNothing);
      expect(find.text(english.kitComposerOffline), findsNothing);
      expect(find.text(english.kitModelChoose), findsNothing);
    });

    testWidgets('composer in $token fits at large text', (tester) async {
      final done = await mountFg5Composer(
        tester,
        locale: locale,
        size: _phone,
        textScale: 1.5,
      );
      addTearDown(done);
      expect(tester.takeException(), isNull);
      expect(find.text(l10n.kitComposerOffline), findsOneWidget);
    });
  }
}
