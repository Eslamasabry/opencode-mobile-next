/// The Language row and sheet (map page `language-sheet`): the app's
/// language as one check-mark choice on the kit sheet, like the other
/// settings choices. A tap chooses and closes; a refused save keeps the sheet
/// open with the old choice and says so.
///
/// Every language is offered honestly (COPY-29, target-ia cross-cutting): a
/// choice that is not fully translated says "Partly translated (N %)", N
/// being its entry in [languageTranslatedPercent]. Each language shows its
/// own name, so a person who cannot read the current one can still find it.
library;

import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import '../../state/app_locale.dart';
import '../../state/connection.dart';
import '../app_iconography.dart';
import '../app_theme.dart' show AppStatusTone;
import '../kit/kit_choice_list.dart';
import '../kit/kit_notice.dart';
import '../kit/kit_row.dart';
import '../kit/kit_row_parts.dart' show KitChevron;
import '../kit/kit_sheet.dart';
import '../kit/kit_text.dart';
import '../kit/kit_tokens.dart';

/// How much of the app reads in Arabic, in whole per cent: the Arabic
/// catalogue's entries over every string a person can see (the English
/// catalogue plus the literals `test/l10n_coverage_test.dart` still counts
/// in the code). `test/revamp/shared_settings_1_test.dart` recomputes it
/// from the files and fails when this drifts by more than 3 points; update
/// the number it prints then.
const int arabicTranslatedPercent = 100;

/// The same share for every language the picker offers, in whole per cent.
/// English is the catalogue itself and is not listed. Spanish, Japanese,
/// Brazilian Portuguese, Russian and Simplified Chinese carry the first-run
/// screens and the chat core (docs/l10n/fg5-key-set.md); the rest of the app
/// reads in English there. `test/revamp/shared_settings_1_test.dart` fails
/// when one of these drifts by more than 3 points.
const Map<String, int> languageTranslatedPercent = {
  'ar': arabicTranslatedPercent,
  'es': 9,
  'ja': 9,
  'pt': 9,
  'ru': 9,
  'zh': 9,
};

/// Opens the Language sheet.
Future<void> showLanguageSheet(
  BuildContext context, {
  required ConnectionController controller,
}) {
  final l10n = lookupAppLocalizations(Localizations.localeOf(context));
  return showKitSheet<void>(
    context,
    title: l10n.e7LocaleUiLanguage,
    sheetKey: const ValueKey('language-sheet'),
    body: (_) => _LanguageSheet(controller: controller),
  );
}

/// The settings row that opens the Language sheet, showing the choice in
/// use.
class LanguageSettingsTile extends StatelessWidget {
  const LanguageSettingsTile({super.key, required this.controller});

  final ConnectionController controller;

  @override
  Widget build(BuildContext context) => ValueListenableBuilder<Locale?>(
    valueListenable: controller.appLocale,
    builder: (context, locale, _) {
      final l10n = lookupAppLocalizations(Localizations.localeOf(context));
      return KitRow(
        leading: KitRow.icon(context, AppIconography.globe),
        title: l10n.e7LocaleUiLanguage,
        supporting: TextSpan(text: _localeLabel(l10n, _code(locale))),
        trailing: const KitChevron(),
        onTap: () => showLanguageSheet(context, controller: controller),
      );
    },
  );
}

/// '' follows the system; otherwise a language code.
String _code(Locale? locale) => locale?.languageCode ?? '';

String _localeLabel(AppLocalizations l10n, String code) =>
    languageChoiceLabel(l10n, code);

/// The words for one language choice: its own name, or "Use system
/// language" for the empty code.
String languageChoiceLabel(AppLocalizations l10n, String code) =>
    switch (code) {
      'ar' => l10n.e7LocaleUiArabic,
      'en' => l10n.e7LocaleUiEnglish,
      'es' => l10n.e7LocaleUiSpanish,
      'ja' => l10n.e7LocaleUiJapanese,
      'pt' => l10n.e7LocaleUiPortuguese,
      'ru' => l10n.e7LocaleUiRussian,
      'zh' => l10n.e7LocaleUiChinese,
      _ => l10n.e7LocaleUiSystem,
    };

/// "Partly translated (N %)" under a language that is not complete, or null.
String? languageChoiceNote(AppLocalizations l10n, String code) {
  final percent = languageTranslatedPercent[code];
  if (percent == null || percent >= 100) return null;
  return l10n.languagePickerPartlyTranslated(percent);
}

class _LanguageSheet extends StatefulWidget {
  const _LanguageSheet({required this.controller});
  final ConnectionController controller;

  @override
  State<_LanguageSheet> createState() => _LanguageSheetState();
}

class _LanguageSheetState extends State<_LanguageSheet> {
  bool _saving = false;
  bool _failed = false;

  Future<void> _select(String code) async {
    if (_saving) return;
    if (code == _code(widget.controller.appLocale.value)) {
      KitSheet.close<void>(context);
      return;
    }
    setState(() {
      _saving = true;
      _failed = false;
    });
    try {
      await widget.controller.setAppLocale(code.isEmpty ? null : Locale(code));
      if (mounted) KitSheet.close<void>(context);
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _saving = false;
        _failed = true;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final tokens = KitTokens.of(context);
    return PopScope(
      canPop: !_saving,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: [
          KitText(l10n.e7LocaleUiDescription, role: KitTextRole.secondary),
          SizedBox(height: tokens.space3),
          KitChoiceList<String>.single(
            semanticsLabel: l10n.e7LocaleUiLanguage,
            choices: [
              for (final code in const ['', ...AppLocaleStore.pickerLanguages])
                KitChoice(
                  value: code,
                  key: ValueKey(
                    'language-choice-${code.isEmpty ? 'system' : code}',
                  ),
                  title: _localeLabel(l10n, code),
                  // A complete translation needs no note.
                  supporting: languageChoiceNote(l10n, code),
                ),
            ],
            selected: _code(widget.controller.appLocale.value),
            onSelected: _select,
          ),
          if (_saving) ...[
            SizedBox(height: tokens.space2),
            KitNotice(
              key: const ValueKey('language-saving'),
              message: l10n.e7LocaleUiSaving,
              tone: AppStatusTone.progress,
            ),
          ],
          if (_failed) ...[
            SizedBox(height: tokens.space2),
            KitNotice(
              key: const ValueKey('language-save-failed'),
              message: l10n.e7LocaleUiSaveFailed,
              tone: AppStatusTone.failure,
            ),
          ],
        ],
      ),
    );
  }
}
