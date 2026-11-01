import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import '../app_iconography.dart';
import 'kit_buttons.dart';
import 'kit_sheet.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';

/// What the person chose on the update sheet.
enum KitUpdateChoice { updateNow, openSettings }

/// The small sheet the Settings gear opens while an app update waits:
/// "Version 2.2.0 is ready", one plain line from the release, a What's new
/// link, and two ways on. Back closes it and chooses nothing.
Future<KitUpdateChoice?> showKitUpdateSheet(
  BuildContext context, {
  required String version,
  required String summary,
  VoidCallback? onWhatsNew,
}) {
  final l10n = lookupAppLocalizations(Localizations.localeOf(context));
  return showKitSheet<KitUpdateChoice>(
    context,
    sheetKey: const ValueKey('app-update-sheet'),
    title: l10n.appUpdateReadyTitle(version),
    icon: AppIconography.download,
    body: (context) => Padding(
      padding: EdgeInsetsDirectional.only(bottom: KitTokens.of(context).space2),
      child: summary.isEmpty
          ? const SizedBox.shrink()
          : KitText(summary, role: KitTextRole.body),
    ),
    primary: KitAction(
      key: const ValueKey('app-update-sheet-now'),
      label: l10n.appUpdateNow,
      onPressed: () => Navigator.of(context).pop(KitUpdateChoice.updateNow),
    ),
    secondary: KitAction(
      key: const ValueKey('app-update-sheet-settings'),
      label: l10n.appUpdateOpenSettings,
      onPressed: () => Navigator.of(context).pop(KitUpdateChoice.openSettings),
    ),
    tertiary: [
      if (onWhatsNew != null)
        KitAction(
          key: const ValueKey('app-update-sheet-whats-new'),
          label: l10n.appUpdateWhatsNew,
          onPressed: onWhatsNew,
        ),
    ],
  );
}
