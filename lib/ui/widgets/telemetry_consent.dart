import 'package:flutter/widgets.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../l10n/app_localizations.dart';
import '../../telemetry/telemetry.dart';
import '../../telemetry/telemetry_provider.dart';
import '../app_iconography.dart';
import '../kit/kit.dart';

/// The one-time question "Help improve OpenCode Mobile" (opt-in analytics
/// and crash reports), on the first start of a build that can send them:
/// the first launch after an update and the first run of a new install.
///
/// Asked once ([telemetryConsentAskedKey] is set before it shows, so back,
/// a swipe or the app closing count as an answer). "Share anonymous usage"
/// turns on the same switch as Settings › Privacy and data; "Not now"
/// leaves it off and the app never asks again. Nothing is asked when the
/// build has no vendor, or the person already turned the switch on.
///
/// Returns whether the question was shown.
Future<bool> askTelemetryConsentOnce(
  BuildContext context, {
  required SharedPreferences prefs,
  Telemetry? telemetry,
}) async {
  final reporter = telemetry ?? telemetryOf(context);
  if (prefs.getBool(telemetryConsentAskedKey) ?? false) return false;
  if (!reporter.available) return false;
  await prefs.setBool(telemetryConsentAskedKey, true);
  if (reporter.consented || !context.mounted) return false;
  final l10n = lookupAppLocalizations(Localizations.localeOf(context));
  final share = await showKitConfirm(
    context,
    title: l10n.telemetryConsentTitle,
    body: l10n.telemetryConsentBody,
    icon: AppIconography.shield,
    consequenceItems: [
      KitConsequence(l10n.telemetryConsentSends),
      KitConsequence(l10n.telemetryConsentNever, mark: KitConsequenceMark.kept),
    ],
    confirmLabel: l10n.telemetryConsentShare,
    cancelLabel: l10n.telemetryConsentNotNow,
    sheetKey: const ValueKey('telemetry-consent'),
    confirmKey: const ValueKey('telemetry-consent-share'),
  );
  if (share) await reporter.setConsent(true);
  return true;
}
