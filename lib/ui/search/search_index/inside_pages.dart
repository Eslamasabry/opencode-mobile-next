part of '../search_index.dart';

extension _IndexInsidePages on _IndexBuild {
  List<SearchEntry> insidePages() {
    return [
      // ---- Inside second-level settings screens ------------------------
      // Disconnect lives on the page of the server it disconnects from; the
      // result opens that page at the row, which confirms before it acts.
      SearchEntry(
        id: 'inside-server-disconnect',
        kind: SearchEntryKind.insideSettings,
        icon: AppIconography.unlink,
        title: l10n.e7SettingsUi8,
        parent: l10n.settingsHubThisServer,
        keywords: l10n.settingsHubSearchDisconnectAliases,
        pages: const ['server-settings'],
        target: const SettingsSearchTarget(
          pageId: 'server-settings',
          sectionId: 'disconnect',
        ),
        gate: (scope) => scope.controller.profile != null,
        open: _screen(
          (scope) => ServerSettingsScreen(
            controller: scope.controller,
            initialSection: ServerSettingsScreen.disconnectSection,
          ),
        ),
      ),
      // AI setup (review only, 2026-09-28) is a row of the connected
      // server's page; the result opens the page itself. Present exactly
      // when that row is: a saved server that can share its configuration.
      // The UI ledger has no page of its own for it yet, so the door page
      // is the server's settings page.
      SearchEntry(
        id: 'inside-server-ai-setup',
        kind: SearchEntryKind.insideSettings,
        icon: AppIconography.sparkle,
        title: l10n.aiSetupTitle,
        parent: l10n.settingsHubThisServer,
        keywords: l10n.aiSetupEntryDetail,
        pages: const ['server-settings', 'ai-setup'],
        gate: (scope) =>
            scope.controller.profile != null &&
            scope.capabilities.setupConfigRead,
        serverGate: (scope) => scope.capabilities.setupConfigRead,
        open: (context, scope) {
          final profile = scope.controller.profile;
          if (profile == null) return Future<void>.value();
          return _push(
            context,
            AiSetupScreen(
              controller: scope.controller,
              serverName: serverDisplayName(
                profile,
                lookupAppLocalizations(Localizations.localeOf(context)),
                among: scope.controller.store.profiles,
              ),
            ),
          );
        },
      ),
      SearchEntry(
        id: 'inside-notifications-what',
        kind: SearchEntryKind.insideSettings,
        icon: AppIconography.notificationImportant,
        title: l10n.notifySectionWhat,
        parent: notifications,
        keywords: l10n.discoverNotifyWhatAliases,
        pages: const ['notifications-settings'],
        target: const SettingsSearchTarget(
          pageId: 'notifications-settings',
          sectionId: 'what',
        ),
        gate: (scope) => scope.platform.supportsNotifications,
        open: _screen(
          (scope) => NotificationsSettingsScreen(
            controller: scope.controller,
            initialSection: 'what',
          ),
        ),
      ),
      SearchEntry(
        id: 'inside-notifications-quiet',
        kind: SearchEntryKind.insideSettings,
        icon: AppIconography.clock,
        title: l10n.monitorQuiet,
        parent: notifications,
        keywords: l10n.discoverNotifyQuietAliases,
        pages: const ['notifications-settings'],
        target: const SettingsSearchTarget(
          pageId: 'notifications-settings',
          sectionId: 'quiet',
        ),
        // Quiet hours only silence notifications.
        gate: (scope) => scope.platform.supportsNotifications,
        open: _screen(
          (scope) => NotificationsSettingsScreen(
            controller: scope.controller,
            initialSection: 'quiet',
          ),
        ),
      ),
      SearchEntry(
        id: 'inside-notifications-background',
        kind: SearchEntryKind.insideSettings,
        icon: AppIconography.sync,
        title: l10n.e7SettingsUi25,
        parent: notifications,
        keywords: l10n.discoverNotifyBackgroundAliases,
        pages: const ['notifications-settings'],
        target: const SettingsSearchTarget(
          pageId: 'notifications-settings',
          sectionId: 'background',
        ),
        gate: (scope) => scope.platform.supportsBackgroundService,
        open: _screen(
          (scope) => NotificationsSettingsScreen(
            controller: scope.controller,
            initialSection: 'background',
          ),
        ),
      ),
      SearchEntry(
        id: 'inside-notifications-servers',
        kind: SearchEntryKind.insideSettings,
        icon: AppIconography.database,
        title: l10n.discoverNotifyServersTitle,
        parent: notifications,
        // "Background checks" was a page of its own until slice-close-misc;
        // its words still find where each server's checks are turned on.
        keywords:
            '${l10n.discoverNotifyServersAliases} '
            '${l10n.monitorBackgroundChecks} ${l10n.discoverMonitorAliases}',
        pages: const ['notifications-settings'],
        target: const SettingsSearchTarget(
          pageId: 'notifications-settings',
          sectionId: 'servers',
        ),
        gate: (scope) =>
            !scope.controller.isIsolated &&
            scope.controller.store.profiles.isNotEmpty,
        open: _screen(
          (scope) => NotificationsSettingsScreen(
            controller: scope.controller,
            initialSection: 'servers',
          ),
        ),
      ),
      SearchEntry(
        id: 'inside-appearance-mode',
        kind: SearchEntryKind.insideSettings,
        icon: AppIconography.contrast,
        title: l10n.e7SettingsUi69,
        parent: appearance,
        keywords: l10n.discoverAppearanceModeAliases,
        pages: const ['appearance-settings'],
        target: const SettingsSearchTarget(
          pageId: 'appearance-settings',
          sectionId: 'mode',
        ),
        open: _screen(
          (scope) => AppearanceSettingsScreen(
            controller: scope.controller,
            initialSection: AppearanceSection.mode,
          ),
        ),
      ),
      SearchEntry(
        id: 'inside-appearance-language',
        kind: SearchEntryKind.insideSettings,
        icon: AppIconography.globe,
        title: l10n.e7LocaleUiLanguage,
        parent: appearance,
        keywords: l10n.discoverLanguageAliases,
        pages: const ['appearance-settings'],
        target: const SettingsSearchTarget(
          pageId: 'appearance-settings',
          sectionId: 'language',
        ),
        open: _screen(
          (scope) => AppearanceSettingsScreen(
            controller: scope.controller,
            initialSection: AppearanceSection.language,
          ),
        ),
      ),
      SearchEntry(
        id: 'inside-appearance-theme',
        kind: SearchEntryKind.insideSettings,
        icon: AppIconography.appearance,
        title: l10n.e7SettingsUi70,
        parent: appearance,
        keywords: l10n.discoverThemeAliases,
        pages: const ['appearance-settings'],
        target: const SettingsSearchTarget(
          pageId: 'appearance-settings',
          sectionId: 'theme',
        ),
        open: _screen(
          (scope) => AppearanceSettingsScreen(
            controller: scope.controller,
            initialSection: AppearanceSection.theme,
          ),
        ),
      ),
      // Settings › Appearance › Effects, one result per row.
      row('inside-appearance-motion', AppIconography.playCircle),
      row('inside-appearance-glow', AppIconography.sparkle),
      // Keep running's rows: the battery exemption, and the heat pause only
      // where the guard runs (the page hides its switch otherwise).
      row(
        'inside-keep-running-battery',
        AppIconography.batteryCharging,
        gate: background,
      ),
      row(
        'inside-keep-running-thermal',
        AppIconography.pause,
        gate: (scope) => background(scope) && scope.thermalGuard,
      ),
      SearchEntry(
        id: 'inside-usage-spent',
        kind: SearchEntryKind.insideSettings,
        icon: AppIconography.usage,
        title: l10n.usageSectionSpent,
        parent: usage,
        keywords: l10n.discoverSpentAliases,
        pages: const ['usage'],
        target: const SettingsSearchTarget(pageId: 'usage', sectionId: 'spent'),
        gate: (scope) => scope.controller.supportsUsageStatistics,
        open: _screen(
          (scope) => UsageHubScreen(
            controller: scope.controller,
            initialSection: UsageSection.spent,
          ),
        ),
      ),
      SearchEntry(
        id: 'inside-usage-budgets',
        kind: SearchEntryKind.insideSettings,
        icon: AppIconography.usageRing,
        title: l10n.usageBudgetTitle,
        parent: usage,
        keywords: l10n.discoverBudgetAliases,
        pages: const ['usage'],
        target: const SettingsSearchTarget(pageId: 'usage', sectionId: 'spent'),
        gate: (scope) => scope.controller.supportsUsageStatistics,
        open: _screen(
          (scope) => UsageHubScreen(
            controller: scope.controller,
            initialSection: UsageSection.spent,
          ),
        ),
      ),
      SearchEntry(
        id: 'inside-usage-remaining',
        kind: SearchEntryKind.insideSettings,
        icon: AppIconography.usageRing,
        title: l10n.usageSectionRemaining,
        parent: usage,
        keywords: l10n.discoverRemainingAliases,
        pages: const ['provider-quota'],
        target: const SettingsSearchTarget(
          pageId: 'usage',
          sectionId: 'remaining',
        ),
        gate: (scope) => scope.controller.profile != null,
        open: _screen(
          (scope) => UsageHubScreen(
            controller: scope.controller,
            initialSection: UsageSection.remaining,
          ),
        ),
      ),
      SearchEntry(
        id: 'inside-usage-quota-monitoring',
        kind: SearchEntryKind.insideSettings,
        icon: AppIconography.notificationImportant,
        title: l10n.quotaMonitorTitle,
        parent: usage,
        keywords: l10n.discoverQuotaMonitorAliases,
        pages: const ['provider-quota'],
        target: const SettingsSearchTarget(
          pageId: 'usage',
          sectionId: 'remaining',
        ),
        gate: (scope) => scope.controller.profile != null,
        open: _screen(
          (scope) => UsageHubScreen(
            controller: scope.controller,
            initialSection: UsageSection.remaining,
          ),
        ),
      ),
      SearchEntry(
        id: 'inside-capabilities-commands',
        kind: SearchEntryKind.insideSettings,
        icon: AppIconography.play,
        title: l10n.runResultsCommandsTitle,
        parent: commandsAndTools,
        keywords:
            '${l10n.discoverCommandsAliases} ${l10n.e7LibraryServerCommands}',
        pages: const ['commands'],
        gate: _catalog,
        open: _screen(
          (scope) => CapabilitiesScreen(
            controller: scope.controller,
            initialSection: ToolsSection.commands,
          ),
        ),
      ),
      SearchEntry(
        id: 'inside-capabilities-tools',
        kind: SearchEntryKind.insideSettings,
        icon: AppIconography.tools,
        title: l10n.e7SettingsDetailUi25,
        parent: commandsAndTools,
        keywords:
            '${l10n.discoverToolsAliases} ${l10n.e7LibraryToolsAndCapabilities}',
        pages: const ['tools'],
        gate: (scope) => _catalog(scope) && scope.capabilities.toolInventory,
        open: _screen(
          (scope) => CapabilitiesScreen(
            controller: scope.controller,
            initialSection: ToolsSection.tools,
          ),
        ),
      ),
      SearchEntry(
        id: 'inside-capabilities-skills',
        kind: SearchEntryKind.insideSettings,
        icon: AppIconography.sparkle,
        title: l10n.e7SettingsDetailUi26,
        parent: commandsAndTools,
        keywords: l10n.discoverSkillsAliases,
        pages: const ['skills'],
        gate: _catalog,
        open: _screen(
          (scope) => CapabilitiesScreen(
            controller: scope.controller,
            initialSection: ToolsSection.skills,
          ),
        ),
      ),
      SearchEntry(
        id: 'inside-capabilities-references',
        kind: SearchEntryKind.insideSettings,
        icon: AppIconography.bookmarks,
        title: l10n.e7SettingsDetailUi27,
        parent: commandsAndTools,
        keywords: l10n.discoverReferencesAliases,
        pages: const ['references'],
        gate: _catalog,
        open: _screen(
          (scope) => CapabilitiesScreen(
            controller: scope.controller,
            initialSection: ToolsSection.references,
          ),
        ),
      ),
      SearchEntry(
        id: 'inside-phone-running-now',
        kind: SearchEntryKind.insideSettings,
        icon: AppIconography.processor,
        title: l10n.termuxProcsTitle,
        parent: onThisPhone,
        keywords: l10n.discoverRunningNowAliases,
        pages: const ['termux-processes'],
        gate: (scope) => scope.platform.supportsTermux,
        open: _screen((_) => const TermuxProcessesScreen()),
      ),
      SearchEntry(
        id: 'inside-phone-storage',
        kind: SearchEntryKind.insideSettings,
        icon: AppIconography.database,
        title: l10n.termuxStorageTitle,
        parent: onThisPhone,
        keywords: l10n.discoverStorageAliases,
        pages: const ['termux-storage'],
        gate: (scope) => scope.platform.supportsTermux,
        open: _screen((_) => const TermuxStorageScreen()),
      ),
      row(
        'inside-phone-crash-recovery',
        AppIconography.restart,
        gate: _managedRecovery,
      ),
      SearchEntry(
        id: 'inside-phone-claude-code',
        kind: SearchEntryKind.insideSettings,
        icon: AppIconography.agent,
        title: l10n.localAgentPageTitle,
        parent: onThisPhone,
        keywords: l10n.localAgentTitle,
        pages: const ['local-agent-page'],
        gate: (scope) => scope.platform.supportsTermux,
        open: (context, _) => _push(
          context,
          LocalAgentScreen(
            onConnected: () => Navigator.of(
              context,
            ).pushNamedAndRemoveUntil('/home', (_) => false),
          ),
        ),
      ),
      // The same door where the Termux bridge is missing: it explains why
      // instead of vanishing from search (P7.4).
      SearchEntry(
        id: 'inside-phone-claude-code-unavailable',
        kind: SearchEntryKind.insideSettings,
        icon: AppIconography.agent,
        title: l10n.localAgentPageTitle,
        parent: onThisPhone,
        keywords: l10n.localAgentTitle,
        pages: const ['local-agent-page'],
        gate: (scope) => !scope.platform.supportsTermux,
        open: _explainClaudeCodeGate,
      ),
      SearchEntry(
        id: 'settings-add-server',
        kind: SearchEntryKind.insideSettings,
        parent: l10n.activitySavedServers,
        icon: AppIconography.add,
        title: l10n.e7SetupAddServer,
        // Connection help folded into Add server (P3.9): its checks explain
        // an address where it is typed.
        keywords: l10n.discoverConnectionHelpAliases,
        // Add server is the Servers page's action; its form stays excluded
        // from title search (many titles, one form).
        pages: const ['servers'],
        open: (context, _) => Navigator.of(
          context,
        ).pushNamed('/servers', arguments: const ServersRouteRequest.add()),
      ),
    ];
  }
}
