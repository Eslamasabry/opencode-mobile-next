part of '../search_index.dart';

extension _IndexHubRows on _IndexBuild {
  List<SearchEntry> hubRows() {
    return [
      // ---- Settings hub rows (target-ia §1.3), in hub order ------------
      // Server: the connected server, every saved server, this phone.
      // The connected server's health, version and updates: a page of the
      // server (Servers › the server), no longer a row of the hub.
      SearchEntry(
        id: 'settings-category-server',
        kind: SearchEntryKind.insideSettings,
        parent: l10n.settingsV3Servers,
        icon: AppIconography.server,
        title: l10n.settingsV3StatusVersion,
        keywords:
            '${l10n.settingsHubSearchServerAliases} ${l10n.e7SettingsUi1} '
            '${l10n.e7SettingsUi65} ${l10n.settingsHubThisServer}',
        pages: const ['server-page', 'host-management'],
        gate: (scope) => scope.controller.profile != null,
        open: (context, scope) => openServerPage(
          context,
          scope.controller,
          scope.controller.profile!.id,
        ),
      ),
      SearchEntry(
        id: 'settings-saved-servers',
        kind: SearchEntryKind.hubRow,
        group: SettingsGroup.connections,
        icon: AppIconography.database,
        title: l10n.settingsV3Servers,
        // The screen's own heading is the product name over "Servers".
        keywords:
            '${l10n.settingsHubSearchSavedServersAliases} '
            '${l10n.openCodeConnectionLabel} — ${l10n.e7SetupServers}',
        // The servers screen also holds the scanner and the editor; this row
        // is the one door to them. Each server row says what it needs.
        pages: const ['servers'],
        open: (context, _) => Navigator.of(context).pushNamed('/servers'),
      ),
      // Once OpenCode is set up on this phone, This phone is a place of its
      // own: what runs here, storage, updates and removal.
      SearchEntry(
        id: 'settings-this-phone',
        kind: SearchEntryKind.hubRow,
        group: SettingsGroup.connections,
        icon: AppIconography.phone,
        title: l10n.settingsV3ThisPhone,
        keywords:
            '${l10n.settingsHubSearchPhoneAliases} $onThisPhone '
            '${l10n.phoneServerCardTitle}',
        pages: const ['termux-setup-installed'],
        // Always findable on a phone that can host OpenCode: set up it is
        // the place of its own, otherwise it starts the setup.
        gate: (scope) => scope.platform.supportsTermux,
        open: (context, scope) => _phoneSetUp(scope)
            ? openThisPhone(context)
            : openPhoneSetupStart(context),
      ),
      // The agents this phone can run (Claude Code and friends). Visible
      // whenever they can run here, and on any phone, so it is always
      // findable: where they cannot run, the page says why in one line.
      SearchEntry(
        id: 'settings-agents',
        kind: SearchEntryKind.hubRow,
        group: SettingsGroup.ai,
        icon: AppIconography.terminal,
        title: l10n.agentsSectionTitle,
        keywords:
            'agents ${l10n.teamUiHomeTitle} ${l10n.a2aTitle} claude code gemini ${l10n.settingsHubSearchPhoneAliases} $onThisPhone',
        pages: const ['agents-screen', 'agents-hub'],
        open: _screen((scope) => AgentsHubScreen(controller: scope.controller)),
      ),
      // Before that, setting it up is one of Add server's ways (R3): the hub
      // holds no second door to it, and search finds it here.
      SearchEntry(
        id: 'settings-on-this-phone',
        kind: SearchEntryKind.insideSettings,
        parent: l10n.activitySavedServers,
        icon: AppIconography.phone,
        title: onThisPhone,
        keywords: l10n.settingsHubSearchPhoneAliases,
        pages: const ['phone-setup-start'],
        // Local Android tools belong to the phone, not the connected server's
        // capability set.
        gate: (scope) => scope.platform.supportsTermux && !_phoneSetUp(scope),
        open: (context, _) => openPhoneSetupStart(context),
      ),
      // Agent: the model, who pays for it, what it can use, the AI Team.
      SearchEntry(
        id: 'settings-model-and-mode',
        kind: SearchEntryKind.hubRow,
        group: SettingsGroup.ai,
        icon: AppIconography.model,
        title: l10n.settingsHubModelRow,
        keywords:
            '${l10n.settingsHubModelAndMode} '
            '${l10n.settingsHubSearchModelModeAliases}',
        open: (context, _) async => showModelPicker(context),
      ),
      // One row for whoever the model is paid through: the providers and
      // their keys, or the Codex account where the server signs in itself.
      SearchEntry(
        id: 'settings-providers',
        kind: SearchEntryKind.hubRow,
        group: SettingsGroup.ai,
        icon: AppIconography.cloud,
        title: l10n.settingsV3Providers,
        keywords:
            '${l10n.settingsHubProvidersRow} ${l10n.libraryProvidersTitle} ${l10n.settingsHubAccounts} '
            '${l10n.settingsHubSearchProvidersAliases} '
            '${l10n.settingsHubSearchAccountsAliases}',
        pages: const ['integrations', 'agent-account'],
        gate: (scope) => _catalog(scope) || _account(scope),
        serverGate: (scope) => _catalog(scope) || _account(scope),
        open: (context, scope) => _catalog(scope)
            ? _push(
                context,
                IntegrationsScreen(
                  controller: scope.controller,
                  mode: IntegrationsMode.providers,
                ),
              )
            : _push(context, AgentAccountScreen(connection: scope.controller)),
      ),
      SearchEntry(
        id: 'settings-tools',
        kind: SearchEntryKind.hubRow,
        group: SettingsGroup.ai,
        icon: AppIconography.tools,
        title: l10n.settingsHubToolsRow,
        keywords:
            '${l10n.settingsHubSearchToolsAliases} ${l10n.libraryMcpTitle} '
            '$commandsAndTools ${l10n.teamUiPluginsTitle} ${l10n.a2aTitle}',
        pages: const ['capabilities'],
        // External agents live in this app, so Tools always has a tab.
        open: _screen(
          (scope) => CapabilitiesScreen(controller: scope.controller),
        ),
      ),
      // The AI Team as a place of its own in Settings, not only a plugin
      // (docs/qa/team-discover-2026-09-25): the one team page (P3.4), on or
      // off; off, it sets the team up for this kind of server. The same door
      // 'ai-team' below uses.
      SearchEntry(
        id: 'settings-ai-team',
        kind: SearchEntryKind.insideSettings,
        parent: l10n.agentsSectionTitle,
        icon: AppIconography.agent,
        title: l10n.teamUiHomeTitle,
        keywords:
            '${l10n.discoverTeamAliases} ${l10n.teamSettingsTitle} ${l10n.teamUiPluginsTitle}',
        pages: const ['team-intro', 'team-settings'],
        gate: (scope) => scope.controller.profile != null,
        // Setup only: Team settings while on, the turn-on flow while off.
        open: (context, scope) => openTeamSetup(context, scope.controller),
      ),
      // Conversations: what runs by itself, how a transcript shows,
      // the shell it runs commands in, and voice.
      // What runs by itself (P6.1): the team's level and the doors to the
      // rules and the watching that let things happen without asking.
      // What runs by itself (P6.1): the last section of Notifications and
      // background; still found by its own words.
      SearchEntry(
        id: 'settings-automation',
        kind: SearchEntryKind.hubRow,
        group: SettingsGroup.chat,
        icon: AppIconography.permissions,
        title: l10n.settingsV3Permissions,
        keywords:
            '${l10n.automationTitle} ${l10n.automationSearchAliases} '
            '${l10n.settingsHubSearchPermissionsAliases}',
        pages: const ['notifications-settings', 'permissions-automation'],
        target: const SettingsSearchTarget(
          pageId: 'notifications-settings',
          sectionId: 'automation',
        ),
        gate: (scope) => AutomationSettingsSections.of(
          scope.controller,
          team: scope.hasTeam,
        ).any,
        serverGate: (scope) => AutomationSettingsSections.of(
          scope.controller,
          team: scope.hasTeam,
        ).any,
        open: _screen(
          (scope) => PermissionsAutomationScreen(
            controller: scope.controller,
            teamAvailable: scope.hasTeam,
          ),
        ),
      ),
      // Inside Notifications and background; still found by its own words.
      SearchEntry(
        id: 'saved-permissions-entry',
        kind: SearchEntryKind.insideSettings,
        parent: notifications,
        icon: AppIconography.permissions,
        title: l10n.e7SettingsUi74,
        keywords: l10n.settingsHubSearchPermissionsAliases,
        pages: const ['saved-permissions'],
        gate: (scope) => scope.controller.capabilities.savedPermissionList,
        open: _screen(
          (scope) => SavedPermissionsScreen(controller: scope.controller),
        ),
      ),
      // The two transcript switches are hub rows themselves (the old sheet
      // is gone); from anywhere else, open the hub arrived at the switch.
      SearchEntry(
        id: 'settings-show-reasoning',
        kind: SearchEntryKind.hubRow,
        group: SettingsGroup.chat,
        icon: AppIconography.idea,
        title: l10n.settingsHubShowReasoning,
        keywords:
            '${l10n.settingsHubSearchReasoningAliases} '
            '${l10n.chatUiTranscriptDisplay} ${l10n.transcriptFindReasoning}',
        pages: const ['settings'],
        open: _hubAt(SettingsGroup.chat, 'settings-show-reasoning'),
      ),
      SearchEntry(
        id: 'settings-show-timestamps',
        kind: SearchEntryKind.hubRow,
        group: SettingsGroup.chat,
        icon: AppIconography.clock,
        title: l10n.settingsHubShowTimestamps,
        keywords:
            '${l10n.settingsHubSearchTranscriptAliases} '
            '${l10n.chatUiTranscriptDisplay} ${l10n.chatUiTimestampsUsage} '
            'cost tokens',
        pages: const ['settings'],
        open: _hubAt(SettingsGroup.chat, 'settings-show-timestamps'),
      ),
      SearchEntry(
        id: 'default-shell-settings-entry',
        kind: SearchEntryKind.hubRow,
        group: SettingsGroup.chat,
        icon: AppIconography.terminal,
        title: l10n.settingsV3Shell,
        keywords:
            '${l10n.e7SettingsUi35} ${l10n.settingsHubSearchShellAliases}',
        // Rule 7: absent where the server cannot change it; the group's line
        // counts it and "Available on this server" says why.
        gate: (scope) => scope.capabilities.shellSettings,
        serverGate: (scope) => scope.capabilities.shellSettings,
        // The hub row is its own control; from anywhere else, open the hub
        // arrived at it.
        open: _hubAt(SettingsGroup.chat, 'default-shell-settings-entry'),
      ),
      SearchEntry(
        id: 'settings-phrase-chips',
        kind: SearchEntryKind.hubRow,
        group: SettingsGroup.chat,
        icon: AppIconography.chat,
        title: l10n.settingsPhraseChipsTitle,
        keywords: l10n.settingsPhraseChipsSupporting,
        pages: const ['settings'],
        open: _hubAt(SettingsGroup.chat, 'settings-phrase-chips'),
      ),
      SearchEntry(
        id: 'settings-voice',
        kind: SearchEntryKind.hubRow,
        group: SettingsGroup.chat,
        icon: AppIconography.mic,
        title: l10n.settingsV3VoiceTyping,
        keywords:
            '${l10n.settingsHubVoice} ${l10n.settingsHubSearchVoiceAliases}',
        // The speech models can neither download nor run off Android.
        gate: (scope) => scope.platform.supportsVoice,
        open: _openVoice,
      ),
      // This app: what notifies, what keeps it running, how it looks, what
      // it keeps, what it has cost.
      SearchEntry(
        id: 'settings-category-notifications',
        kind: SearchEntryKind.hubRow,
        group: SettingsGroup.thisApp,
        icon: AppIconography.notificationImportant,
        title: l10n.settingsV3Notifications,
        keywords:
            '$notifications ${l10n.settingsHubSearchNotificationsAliases}',
        pages: const ['notifications-settings'],
        open: _screen(
          (scope) => NotificationsSettingsScreen(
            controller: scope.controller,
            page: NotificationsPage.notifications,
          ),
        ),
      ),
      SearchEntry(
        id: 'settings-category-background',
        kind: SearchEntryKind.hubRow,
        group: SettingsGroup.thisApp,
        icon: AppIconography.sync,
        title: l10n.settingsV3Background,
        keywords:
            '${l10n.keepRunningTitle} ${l10n.e7SettingsUi25} '
            '${l10n.settingsHubSearchNotificationsAliases}',
        pages: const ['notifications-settings'],
        gate: (scope) => scope.platform.supportsBackgroundService,
        open: _screen(
          (scope) => NotificationsSettingsScreen(
            controller: scope.controller,
            page: NotificationsPage.background,
          ),
        ),
      ),
      SearchEntry(
        id: 'settings-keep-running',
        kind: SearchEntryKind.insideSettings,
        parent: notifications,
        icon: AppIconography.batteryCharging,
        title: l10n.keepRunningTitle,
        keywords: l10n.keepRunningRowSubtitle,
        pages: const ['notifications-settings'],
        target: const SettingsSearchTarget(
          pageId: 'notifications-settings',
          sectionId: 'keep-running',
        ),
        // What to allow so Android leaves the app (and the OpenCode inside
        // it) running: Android only.
        gate: (scope) => scope.platform.supportsBackgroundService,
        open: (context, _) => openKeepRunningScreen(context),
      ),
      SearchEntry(
        id: 'settings-category-appearance',
        kind: SearchEntryKind.hubRow,
        group: SettingsGroup.thisApp,
        icon: AppIconography.appearance,
        title: appearance,
        keywords: l10n.settingsHubSearchAppearanceAliases,
        pages: const ['appearance-settings'],
        open: _screen(
          (scope) => AppearanceSettingsScreen(controller: scope.controller),
        ),
      ),
      SearchEntry(
        id: 'settings-category-language',
        kind: SearchEntryKind.hubRow,
        group: SettingsGroup.thisApp,
        icon: AppIconography.globe,
        title: l10n.settingsV3Language,
        keywords: '${l10n.e7LocaleUiLanguage} ${l10n.discoverLanguageAliases}',
        pages: const ['appearance-settings'],
        target: const SettingsSearchTarget(
          pageId: 'appearance-settings',
          sectionId: 'language',
        ),
        // A quick pick that acts in place: open the hub arrived at it.
        open: _hubAt(SettingsGroup.thisApp, 'settings-category-language'),
      ),
      SearchEntry(
        id: 'settings-category-privacy',
        kind: SearchEntryKind.hubRow,
        group: SettingsGroup.thisApp,
        icon: AppIconography.privacy,
        title: l10n.settingsHubPrivacyRow,
        // Not "Older drafts": they are listed from the conversation that owns
        // them, and this page does not hold them.
        keywords: l10n.settingsHubSearchPrivacyAliases,
        pages: const ['privacy-settings'],
        open: _screen(
          (scope) => PrivacySettingsScreen(controller: scope.controller),
        ),
      ),
      SearchEntry(
        id: 'settings-category-usage',
        kind: SearchEntryKind.hubRow,
        group: SettingsGroup.ai,
        icon: AppIconography.usage,
        title: usage,
        keywords: l10n.settingsHubSearchUsageAliases,
        pages: const ['usage-hub'],
        // "Spending" needs usage statistics, "Plans" needs a saved server.
        gate: (scope) =>
            UsageHubScreen.sectionsFor(scope.controller).isNotEmpty,
        open: _screen((scope) => UsageHubScreen(controller: scope.controller)),
      ),
      SearchEntry(
        id: 'settings-category-usage-widget',
        // Inside Usage, so This app keeps five rows (G-rules).
        kind: SearchEntryKind.insideSettings,
        parent: usage,
        icon: AppIconography.usageRing,
        title: l10n.usageWidgetTitle,
        keywords: l10n.usageWidgetSearchAliases,
        pages: const ['usage-widget'],
        open: _screen(
          (scope) => UsageWidgetScreen(controller: scope.controller),
        ),
      ),
      // Help, the last, unlabelled panel.
      SearchEntry(
        id: 'settings-setup-guide',
        kind: SearchEntryKind.hubRow,
        group: SettingsGroup.help,
        icon: AppIconography.guide,
        title: l10n.onboardingSetupGuide,
        keywords: l10n.settingsHubSearchGuideAliases,
        pages: const ['guide'],
        open: _screen((_) => GuideScreen(embedded: false)),
      ),
      // Report a problem (P8.2): the one row for the GitHub form and the
      // diagnostics, which used to be two paths.
      SearchEntry(
        id: 'library-report-bug',
        kind: SearchEntryKind.hubRow,
        group: SettingsGroup.help,
        icon: AppIconography.bug,
        title: l10n.e7LibraryReportABug,
        // "crash" too: what went wrong is here even without a phone server.
        keywords:
            '${l10n.settingsHubSearchBugAliases} ${l10n.e7SettingsUi88} '
            '${l10n.settingsHubSearchDiagnosticsAliases} ${diagnostics.aliases}',
        pages: const ['app-diagnostics'],
        target: diagnostics.target,
        open: _arrive(diagnostics.target),
      ),
      // The explanation for every row the connected server hides; each
      // group's "Why" opens it too.
      SearchEntry(
        id: 'settings-server-capabilities',
        kind: SearchEntryKind.insideSettings,
        parent: l10n.settingsV3Servers,
        icon: AppIconography.checklist,
        title: l10n.capabilityScreenTitle,
        keywords: l10n.capabilityScreenAliases,
        pages: const ['server-capabilities'],
        // It describes the connected server, so there is nothing to show
        // without one.
        gate: (scope) => scope.controller.isConnected,
        open: _screen(
          (scope) => ServerCapabilitiesScreen(controller: scope.controller),
        ),
      ),
      SearchEntry(
        id: 'settings-tips',
        kind: SearchEntryKind.hubRow,
        group: SettingsGroup.help,
        icon: AppIconography.idea,
        title: l10n.tipsTitle,
        keywords: '${l10n.tipsSearchAliases} ${l10n.tipsHubSubtitle}',
        pages: const ['tips-page'],
        open: _screen((scope) => TipsScreen(controller: scope.controller)),
      ),
      SearchEntry(
        id: 'settings-about-notices',
        kind: SearchEntryKind.hubRow,
        group: SettingsGroup.help,
        icon: AppIconography.info,
        title: l10n.aboutTitle,
        keywords:
            '${l10n.settingsHubSearchAboutAliases} ${l10n.e7SettingsUi96} '
            '${l10n.e7SettingsDetailUi18}',
        pages: const ['about', 'about-open-source-tab'],
        open: _screen((scope) => AboutScreen(controller: scope.controller)),
      ),

      // ---- Inside the hub's pages ----------------------------------------
      SearchEntry(
        id: 'settings-tailscale',
        kind: SearchEntryKind.insideSettings,
        parent: l10n.activitySavedServers,
        icon: AppIconography.network,
        title: l10n.tailscaleTitle,
        keywords: l10n.settingsHubSearchTailscaleAliases,
        pages: const ['tailscale-setup'],
        gate: (scope) => scope.platform.supportsTailscaleHandoff,
        open: _screen((_) => const TailscaleSetupScreen()),
      ),
      SearchEntry(
        id: 'settings-models',
        kind: SearchEntryKind.insideSettings,
        parent: l10n.settingsHubModelRow,
        icon: AppIconography.model,
        title: l10n.libraryModelsAgentsTitle,
        // The hub says "Models & agents"; the screen spells it out.
        keywords:
            '${l10n.settingsHubSearchModelsAliases} '
            '${l10n.e7LibraryModelsAndAgents}',
        // The model picker is the catalogue: the same list, where choosing
        // one is what a person came for.
        pages: const ['model-picker-sheet'],
        gate: _catalog,
        open: (context, _) async => showModelPicker(context),
      ),
      // The Codex account, when the Providers row already leads to the
      // providers (a server with both): otherwise that row is its door.
      SearchEntry(
        id: 'settings-accounts',
        kind: SearchEntryKind.insideSettings,
        parent: l10n.settingsHubProvidersRow,
        icon: AppIconography.person,
        title: l10n.settingsHubAccounts,
        keywords:
            '${l10n.settingsHubSearchAccountsAliases} ${l10n.agentAccountTitle}',
        pages: const ['agent-account'],
        gate: (scope) => _account(scope) && _catalog(scope),
        open: _screen(
          (scope) => AgentAccountScreen(connection: scope.controller),
        ),
      ),
      // Tools' rows (external-agents moved inside it).
      SearchEntry(
        id: 'settings-mcp',
        kind: SearchEntryKind.insideSettings,
        parent: l10n.settingsHubToolsRow,
        icon: AppIconography.network,
        title: l10n.libraryMcpTitle,
        // "Add MCP server" is a button on this screen, so its title leads here.
        keywords:
            '${l10n.settingsHubSearchMcpAliases} ${l10n.mcpAdd} '
            '${l10n.e7LibraryMCPAndIntegrations} ${l10n.mcpCatalogTitle}',
        pages: const ['integrations', 'mcp-setup', 'mcp-catalog'],
        gate: _catalog,
        serverGate: _catalog,
        // The MCP tab of Tools (Add is its first button).
        open: _screen(
          (scope) => CapabilitiesScreen(
            controller: scope.controller,
            initialSection: ToolsSection.mcp,
          ),
        ),
      ),
      SearchEntry(
        id: 'settings-commands-tools',
        kind: SearchEntryKind.insideSettings,
        parent: l10n.settingsHubToolsRow,
        icon: AppIconography.play,
        title: commandsAndTools,
        keywords: l10n.settingsHubSearchCommandsAliases,
        pages: const ['capabilities'],
        gate: _catalog,
        serverGate: _catalog,
        open: _screen(
          (scope) => CapabilitiesScreen(
            controller: scope.controller,
            initialSection: ToolsSection.commands,
          ),
        ),
      ),
      // The server's own plugins: a section of Settings > This server (the
      // Plugins page it used to sit on is gone).
      SearchEntry(
        id: 'settings-server-plugins',
        kind: SearchEntryKind.insideSettings,
        parent: l10n.settingsHubThisServer,
        icon: AppIconography.extensions,
        title: l10n.pluginsSectionOnServer,
        keywords:
            '${l10n.settingsHubSearchPluginsAliases} ${l10n.teamUiPluginsTitle}',
        pages: const ['server-page'],
        gate: (scope) =>
            scope.controller.profile != null &&
            scope.capabilities.pluginInventory,
        open: (context, scope) => openServerPage(
          context,
          scope.controller,
          scope.controller.profile!.id,
          arrive: 'settings-server-plugins',
        ),
      ),
      SearchEntry(
        id: 'settings-external-agents',
        kind: SearchEntryKind.insideSettings,
        parent: l10n.settingsHubToolsRow,
        icon: AppIconography.support,
        title: l10n.a2aTitle,
        keywords: l10n.settingsHubSearchExternalAgentsAliases,
        pages: const ['external-agents'],
        open: (context, scope) => openExternalAgents(context, scope.controller),
      ),
      SearchEntry(
        id: 'library-import-session',
        kind: SearchEntryKind.destination,
        parent: l10n.globalSessionsTitle,
        icon: AppIconography.fileUpload,
        title: l10n.importTitle,
        keywords: l10n.e7LibrarySearchImportAliases,
        pages: const ['session-import'],
        gate: _canImport,
        open: _screen(
          (scope) => SessionImportScreen(controller: scope.controller),
        ),
      ),
      SearchEntry(
        id: 'settings-privacy-data-use',
        kind: SearchEntryKind.insideSettings,
        // About's privacy tab merged into Privacy and data (P3.10).
        parent: l10n.settingsHubPrivacyRow,
        icon: AppIconography.policy,
        title: l10n.privacyPolicyTitle,
        keywords:
            '${l10n.e7SettingsUi92} ${l10n.settingsHubSearchAboutAliases} '
            '${l10n.e7SettingsDetailUi17}',
        pages: const ['privacy-settings'],
        open: (context, _) => showPrivacyPolicy(context),
      ),
      // The demo is the first-run welcome's "Just show me"; once a server is
      // saved, the setup guide is where it stays reachable.
      SearchEntry(
        id: 'settings-try-demo',
        kind: SearchEntryKind.insideSettings,
        parent: l10n.onboardingSetupGuide,
        icon: AppIconography.play,
        title: l10n.settingsTryDemo,
        keywords: '${l10n.demoScreenTitle} ${l10n.demoScreenSimulated}',
        pages: const ['demo'],
        open: _screen((_) => const DemoScreen()),
      ),
      // About holds the tips, the shortcuts and the open source notices.
      SearchEntry(
        id: 'library-keyboard-shortcuts',
        kind: SearchEntryKind.insideSettings,
        parent: l10n.aboutTitle,
        icon: AppIconography.keyboard,
        title: l10n.e7LibraryKeyboardShortcuts,
        keywords: l10n.e7LibrarySearchShortcutsAliases,
        // The shortcut layer must be discoverable without already knowing a
        // shortcut, and means nothing without a keyboard.
        gate: (scope) => scope.desktop,
        open: (context, _) => showShortcutsHelp(context),
      ),
      SearchEntry(
        id: 'settings-show-tips-again',
        kind: SearchEntryKind.insideSettings,
        parent: l10n.aboutTitle,
        icon: AppIconography.idea,
        title: l10n.discoverShowTipsAgain,
        keywords: l10n.discoverShowTipsAliases,
        // About's row is the control itself; from anywhere else, open About
        // arrived at it.
        open: _screen(
          (scope) => KitArrivalScope(
            rowId: 'settings-show-tips-again',
            child: AboutScreen(controller: scope.controller),
          ),
        ),
      ),
      SearchEntry(
        id: 'settings-voice-notices',
        kind: SearchEntryKind.insideSettings,
        // About › Open source holds every licence, the voice models' too.
        parent: l10n.aboutTitle,
        icon: AppIconography.policy,
        title: l10n.e7SettingsUi94,
        keywords:
            '${l10n.settingsHubSearchAboutAliases} ${l10n.e7SettingsDetailUi18}',
        pages: const ['about'],
        gate: (scope) => scope.platform.supportsVoice,
        // The voice licences are a group of About; from anywhere else, open
        // About arrived at it.
        open: _screen(
          (scope) => KitArrivalScope(
            rowId: 'settings-voice-notices',
            child: AboutScreen(controller: scope.controller),
          ),
        ),
      ),
    ];
  }
}
