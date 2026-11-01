import 'dart:async';

import 'package:flutter/widgets.dart';

import '../l10n/app_localizations.dart';
import '../ui/kit/kit.dart';
import 'model_manager.dart';
import 'model_manifest.dart';
import 'presentation.dart';

/// Voice typing's language: one row that says what is chosen and opens the
/// list. "Follow the app" listens for the app's own language when the speech
/// model knows it; "Detect from speech" always listens for it; the rest name
/// a language (every language the multilingual model supports and the list
/// offers).
class VoiceLanguageRow extends StatelessWidget {
  const VoiceLanguageRow({
    super.key,
    required this.manager,
    required this.strings,
    this.enabled = true,
    this.disabledReason,
  });

  final VoiceModelManager manager;
  final AppLocalizations strings;
  final bool enabled;
  final String? disabledReason;

  @override
  Widget build(BuildContext context) {
    final note = voiceLanguageNote(
      strings,
      chosen: manager.language,
      pack: manager.selectedPack,
      appLanguageCode: Localizations.localeOf(context).languageCode,
    );
    final detail = [
      if (manager.language == VoiceLanguage.auto)
        strings.vpttLanguageFollowAppDetail,
      ?note,
    ].join(' ');
    return _row(context, detail.isEmpty ? null : detail);
  }

  Widget _row(BuildContext context, String? detail) => KitRow(
    key: const Key('voice-model-language'),
    title: strings.e7VoiceUiLanguage,
    supporting: detail == null ? null : TextSpan(text: detail),
    supportingMaxLines: 6,
    trailing: KitRowValue(voiceLanguageLabel(manager.language, strings)),
    enabled: enabled,
    disabledReason: enabled ? null : disabledReason,
    onTap: enabled ? () => unawaited(_open(context)) : null,
  );

  Future<void> _open(BuildContext context) async {
    final chosen = await showKitSheet<VoiceLanguage>(
      context,
      title: strings.e7VoiceUiLanguage,
      secondary: KitAction(
        label: strings.e7VoiceUiNotNow,
        onPressed: () => Navigator.of(context).maybePop(),
      ),
      secondaryDismisses: true,
      itemCount: 1,
      itemBuilder: (sheetContext, _) => KitChoiceList<VoiceLanguage>.single(
        key: const Key('voice-language-list'),
        semanticsLabel: strings.e7VoiceUiLanguage,
        selected: manager.language,
        onSelected: (value) => KitSheet.close(sheetContext, value),
        choices: [
          for (final language in VoiceLanguage.values)
            KitChoice<VoiceLanguage>(
              key: Key('voice-language-${language.name}'),
              value: language,
              title: voiceLanguageLabel(language, strings),
              supporting: switch (language) {
                VoiceLanguage.auto => strings.vpttLanguageFollowAppDetail,
                VoiceLanguage.detect => strings.vpttLanguageDetectDetail,
                _ => null,
              },
            ),
        ],
      ),
    );
    if (chosen != null) unawaited(manager.setLanguage(chosen));
  }
}

/// Voice typing's "Send when I stop talking": off until the person turns it
/// on. It ends a recording after a pause and sends the words after a short
/// countdown with Cancel; a held mic and a tap on Done are never sent for
/// them.
class VoiceAutoSendRow extends StatelessWidget {
  const VoiceAutoSendRow({
    super.key,
    required this.manager,
    required this.strings,
  });

  final VoiceModelManager manager;
  final AppLocalizations strings;

  @override
  Widget build(BuildContext context) => KitSwitchRow(
    switchKey: const Key('voice-auto-send'),
    title: strings.vpttAutoSendTitle,
    supporting: strings.vpttAutoSendDetail,
    supportingMaxLines: 4,
    value: manager.autoSend,
    onChanged: (value) => unawaited(manager.setAutoSend(value)),
  );
}
