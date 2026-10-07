/// The Language row and sheet (map page `language-sheet`): the app's
/// language as one check-mark choice on the kit sheet, like the other
/// settings choices. A tap chooses and closes; a refused save keeps the sheet
/// open with the old choice and says so.
///
/// Arabic is offered honestly (COPY-29, target-ia cross-cutting): its choice
/// says "Partly translated (N %)", N being [arabicTranslatedPercent].
library;

import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
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
const int arabicTranslatedPercent = 93;

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

String _localeLabel(AppLocalizations l10n, String code) => switch (code) {
  'ar' => l10n.e7LocaleUiArabic,
  'en' => l10n.e7LocaleUiEnglish,
  _ => l10n.e7LocaleUiSystem,
};

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
              for (final code in const ['', 'en', 'ar'])
                KitChoice(
                  value: code,
                  key: ValueKey(
                    'language-choice-${code.isEmpty ? 'system' : code}',
                  ),
                  title: _localeLabel(l10n, code),
                  supporting: code == 'ar'
                      ? l10n.languagePickerPartlyTranslated(
                          arabicTranslatedPercent,
                        )
                      : null,
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
