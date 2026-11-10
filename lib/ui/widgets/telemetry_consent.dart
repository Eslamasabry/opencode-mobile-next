import 'dart:async';

import 'package:flutter/widgets.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../l10n/app_localizations.dart';
import '../../telemetry/analytics_events.dart';
import '../../telemetry/telemetry.dart';
import '../../telemetry/telemetry_provider.dart';
import '../app_iconography.dart';
import '../kit/kit.dart';

/// The one-time question "Help improve OpenCode Mobile" (opt-in usage and app
/// health, and crash reports), on the first start of a build that can send
/// them: the first launch after an update and the first run of a new install.
///
/// Asked once ([telemetryConsentAskedKey] is set before it shows, so back,
/// a swipe or the app closing count as an answer). "Share both" turns on both
/// switches of Settings › Privacy and data (`usage` and `crashes`); "Not now"
/// leaves both off and the app never asks again; "Choose" opens the two
/// switches. Nothing is asked when the build has no vendor, or the person
/// already turned a switch on (or carried an answer over from 2.1.5).
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
  var choose = false;
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
    alternative: KitAction(
      key: const ValueKey('telemetry-consent-choose'),
      label: l10n.telemetryConsentChoose,
      onPressed: () => choose = true,
    ),
    sheetKey: const ValueKey('telemetry-consent'),
    confirmKey: const ValueKey('telemetry-consent-share'),
  );
  if (share) {
    await reporter.setConsent(true);
    // This start's cold open happened before the answer and was dropped.
    reporter.track(AnalyticsEvent.appOpen, {'kind': 'cold'});
  } else if (choose && context.mounted) {
    await _chooseSwitches(context, reporter, l10n);
  }
  return true;
}

/// The two switches, with what each sends, and one button that applies them.
Future<void> _chooseSwitches(
  BuildContext context,
  Telemetry reporter,
  AppLocalizations l10n,
) async {
  final usage = ValueNotifier<bool>(false);
  final crashes = ValueNotifier<bool>(false);
  final navigator = Navigator.of(context);
  try {
    await showKitSheet<void>(
      context,
      title: l10n.telemetryConsentTitle,
      icon: AppIconography.shield,
      sheetKey: const ValueKey('telemetry-consent-choose-sheet'),
      primary: KitAction(
        key: const ValueKey('telemetry-consent-save'),
        label: l10n.telemetryConsentSave,
        onPressed: () async {
          await reporter.setUsageConsent(usage.value);
          await reporter.setCrashesConsent(crashes.value);
          if (usage.value) {
            reporter.track(AnalyticsEvent.appOpen, {'kind': 'cold'});
          }
          if (navigator.mounted) navigator.pop();
        },
      ),
      body: (sheetContext) => ListenableBuilder(
        listenable: Listenable.merge([usage, crashes]),
        builder: (context, _) => KitRowGroup(
          children: [
            KitSwitchRow(
              key: const ValueKey('telemetry-consent-usage'),
              leading: KitRow.icon(context, AppIconography.shield),
              title: l10n.telemetryUsageTitle,
              supporting: l10n.telemetryUsageDetail,
              supportingMaxLines: 6,
              value: usage.value,
              onChanged: (value) => usage.value = value,
            ),
            KitSwitchRow(
              key: const ValueKey('telemetry-consent-crashes'),
              leading: KitRow.icon(context, AppIconography.bug),
              title: l10n.telemetryCrashesTitle,
              supporting: l10n.telemetryCrashesDetail,
              supportingMaxLines: 6,
              value: crashes.value,
              onChanged: (value) => crashes.value = value,
            ),
          ],
        ),
      ),
    );
  } finally {
    usage.dispose();
    crashes.dispose();
  }
}
