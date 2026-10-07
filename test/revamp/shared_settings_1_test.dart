// shared-settings-1 (wave 2a): the Language sheet's honest Arabic offer, the
// theme preview sheet's "In use now" line and Undo after Apply.
import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit_undo.dart';
import 'package:opencode_mobile/ui/widgets/appearance_picker.dart';
import 'package:opencode_mobile/ui/widgets/language_picker.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../l10n_coverage_test.dart' show scan;

final _l10n = lookupAppLocalizations(const Locale('en'));

Widget _app(Widget home) => MaterialApp(
  theme: AppTheme.dark(),
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  // Reduced motion: the preview's working mark holds still, so the tree
  // settles (G8).
  builder: (context, child) => MediaQuery(
    data: MediaQuery.of(context).copyWith(disableAnimations: true),
    child: child!,
  ),
  home: Scaffold(body: home),
);

Map<String, Object?> _arb(String path) =>
    jsonDecode(File(path).readAsStringSync()) as Map<String, Object?>;

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    for (final channel in ['oc/background', 'oc/shortcut']) {
      messenger.setMockMethodCallHandler(
        MethodChannel(channel),
        (_) async => null,
      );
      addTearDown(
        () => messenger.setMockMethodCallHandler(MethodChannel(channel), null),
      );
    }
  });

  group('language sheet', () {
    test('the Arabic share is the real coverage, within 3 points', () {
      bool message(String key) => !key.startsWith('@');
      final en = _arb('lib/l10n/app_en.arb').keys.where(message).toSet();
      final ar = _arb(
        'lib/l10n/app_ar.arb',
      ).keys.where(message).where(en.contains).length;
      final hardcoded = scan().values.fold<int>(0, (a, b) => a + b);
      final percent = ar * 100 ~/ (en.length + hardcoded);
      expect(
        (percent - arabicTranslatedPercent).abs(),
        lessThanOrEqualTo(3),
        reason:
            'Arabic covers $percent % ($ar of ${en.length} catalogue '
            'strings + $hardcoded in code); set arabicTranslatedPercent in '
            'lib/ui/widgets/language_picker.dart to $percent.',
      );
    });

    testWidgets(
      'Arabic carries a partly-translated note only while it is incomplete',
      (tester) async {
        SharedPreferences.setMockInitialValues({});
        final controller = ConnectionController(
          ProfileStore(prefs: await SharedPreferences.getInstance()),
          isIsolated: true,
        );
        addTearDown(controller.dispose);
        await tester.pumpWidget(
          _app(LanguageSettingsTile(controller: controller)),
        );
        await tester.tap(find.text(_l10n.e7LocaleUiLanguage));
        await tester.pumpAndSettle();
        final share = _l10n.languagePickerPartlyTranslated(
          arabicTranslatedPercent,
        );
        expect(share, 'Partly translated ($arabicTranslatedPercent %)');
        // Complete (100 %): no note at all; incomplete: only Arabic has it.
        expect(
          find.text(share),
          arabicTranslatedPercent < 100 ? findsOneWidget : findsNothing,
        );
        if (arabicTranslatedPercent < 100) {
          expect(
            find.descendant(
              of: find.byKey(const ValueKey('language-choice-ar')),
              matching: find.text(share),
            ),
            findsOneWidget,
          );
        }
        // Choosing the language in use just closes the sheet.
        await tester.tap(find.text(_l10n.e7LocaleUiSystem).last);
        await tester.pumpAndSettle();
        expect(find.byKey(const ValueKey('language-sheet')), findsNothing);
        expect(controller.appLocale.value, isNull);
      },
    );
  });

  group('theme preview sheet', () {
    Future<ConnectionController> open(
      WidgetTester tester,
      ThemePackId pack,
    ) async {
      SharedPreferences.setMockInitialValues({});
      final controller = ConnectionController(
        ProfileStore(prefs: await SharedPreferences.getInstance()),
        isIsolated: true,
      );
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        _app(
          Builder(
            builder: (context) => Center(
              child: GestureDetector(
                onTap: () => showThemePackPreview(
                  context,
                  controller: controller,
                  pack: pack,
                ),
                child: const Text('Open'),
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('Open'));
      await tester.pumpAndSettle();
      return controller;
    }

    testWidgets('the pack in use says so in words, not a disabled button', (
      tester,
    ) async {
      await open(tester, ThemePackId.opencode);
      expect(find.text(_l10n.appearancePickerInUse), findsOneWidget);
      expect(find.text(_l10n.e7AppearanceApply), findsNothing);
      expect(find.text(_l10n.e7AppearanceCurrent), findsNothing);
    });

    testWidgets('Apply saves the pack and Undo puts the old one back', (
      tester,
    ) async {
      final controller = await open(tester, ThemePackId.gruvbox);
      expect(find.text(_l10n.appearancePickerInUse), findsNothing);
      // Previewing in light only changes the picture.
      final appearance = controller.appearance.value;
      await tester.tap(find.byKey(const ValueKey('appearance-preview-light')));
      await tester.pumpAndSettle();
      expect(controller.appearance.value, appearance);
      // Apply ends the sheet's scrolling body (appearance_picker.dart); on
      // the 800x600 test window it sits below the fold, as on a short
      // landscape phone, so bring it into view first.
      await tester.ensureVisible(find.text(_l10n.e7AppearanceApply));
      await tester.pumpAndSettle();
      await tester.tap(find.text(_l10n.e7AppearanceApply));
      await tester.pumpAndSettle();
      expect(controller.themePack.value, ThemePackId.gruvbox);
      expect(find.byKey(const Key('appearance-picker')), findsNothing);
      expect(
        find.text(_l10n.appearancePickerThemeApplied('Gruvbox')),
        findsOneWidget,
      );
      await tester.tap(find.text(_l10n.kitUndoAction));
      await tester.pumpAndSettle();
      expect(controller.themePack.value, ThemePackId.opencode);
      expect(controller.store.themePack, ThemePackId.opencode);
      KitUndo.commitPending();
    });
  });
}
