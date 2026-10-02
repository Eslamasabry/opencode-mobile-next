import '../l10n/app_localizations.dart';
import 'settings_search.dart';

/// Row additions for the existing shared Settings/command-palette catalog.
/// Display strings reuse localization; aliases remain bilingual in either locale.
/// Flags must reflect the destination's current visibility, not server flavor.
/// Merge by id with existing gated documents (these rows take precedence).
List<SettingsSearchDocument> settingsSearchRows(
  AppLocalizations l10n, {
  required bool supportsBackgroundService,
  required bool thermalGuardAvailable,
  required bool managedRecoveryAvailable,
}) {
  SettingsSearchDocument effect(String row, String title, String aliases) =>
      SettingsSearchDocument(
        id: 'inside-appearance-$row',
        title: title,
        parent: '${l10n.e7AppearanceTitle} · ${l10n.effectsSection}',
        aliases: 'appearance effects مظهر تأثيرات $aliases',
        target: SettingsSearchTarget(
          pageId: 'appearance-settings',
          sectionId: 'effects',
          rowId: 'effects-$row',
        ),
      );

  return [
    effect(
      'motion',
      l10n.effectsAnimations,
      'animations animation motion movement reduce reduced calm celebrations celebration confetti حركة تحريك رسوم تقليل هادئ احتفال احتفالات',
    ),
    effect(
      'glow',
      l10n.effectsActivityGlow,
      'glow glowing border ring light edge replying reply running blue outline box '
          'توهج متوهج وهج حد حدود إطار حافة حلقة الرد ضوء',
    ),
    if (supportsBackgroundService) ...[
      SettingsSearchDocument(
        id: 'inside-keep-running-battery',
        title: l10n.keepRunningBatteryTitle,
        parent: l10n.settingsHubGroupNotifications,
        aliases:
            'battery optimization optimisation unrestricted keep alive '
            'keep running background بطارية البطارية توفير طاقة خلفية استمرار تشغيل',
        target: const SettingsSearchTarget(
          pageId: 'keep-running',
          rowId: 'keep-running-battery',
        ),
      ),
      if (thermalGuardAvailable)
        SettingsSearchDocument(
          id: 'inside-keep-running-thermal',
          title: l10n.thermalGuardSetting,
          parent: l10n.settingsHubGroupNotifications,
          aliases:
              'heat hot thermal temperature overheat cool pause '
              'حرارة سخونة ساخن تبريد حماية',
          target: const SettingsSearchTarget(
            pageId: 'keep-running',
            rowId: 'keep-running-thermal',
          ),
        ),
    ],
    if (managedRecoveryAvailable)
      SettingsSearchDocument(
        id: 'inside-phone-crash-recovery',
        title: l10n.managedRecoveryRowTitle,
        parent: l10n.onboardingTermuxSetup,
        aliases:
            'crash restart recovery recover watchdog termux '
            'تعطل انهيار إعادة تشغيل استعادة',
        target: const SettingsSearchTarget(
          pageId: 'termux-setup-installed',
          sectionId: 'options',
          rowId: 'managed-recovery-option',
        ),
      ),
    // "Crash" still has a useful destination without a managed phone server.
    SettingsSearchDocument(
      id: 'app-diagnostics-entry',
      title: l10n.e7SettingsUi88,
      aliases:
          '${l10n.settingsHubSearchDiagnosticsAliases} '
          'crash diagnostics logs errors report تعطل انهيار تشخيص سجلات أخطاء',
      target: const SettingsSearchTarget(pageId: 'app-diagnostics'),
    ),
  ];
}

/// Upgrades the shared catalog without dropping gated legacy entries/aliases.
/// Pass existing documents with BOTH locales' metadata in their aliases.
/// Specific new rows precede broad legacy aliases (e.g. notifications' battery).
List<SettingsSearchDocument> settingsSearchCatalog(
  AppLocalizations l10n, {
  required Iterable<SettingsSearchDocument> existing,
  required bool supportsBackgroundService,
  required bool thermalGuardAvailable,
  required bool managedRecoveryAvailable,
}) {
  final legacy = {for (final document in existing) document.id: document};
  final rows = settingsSearchRows(
    l10n,
    supportsBackgroundService: supportsBackgroundService,
    thermalGuardAvailable: thermalGuardAvailable,
    managedRecoveryAvailable: managedRecoveryAvailable,
  );
  return List.unmodifiable([
    for (final row in rows)
      SettingsSearchDocument(
        id: row.id,
        title: row.title,
        parent: row.parent,
        target: row.target,
        aliases:
            '${row.aliases} ${legacy[row.id]?.title ?? ''} '
            '${legacy[row.id]?.aliases ?? ''}',
      ),
    for (final document in legacy.values)
      if (!rows.any((row) => row.id == document.id)) document,
  ]);
}
