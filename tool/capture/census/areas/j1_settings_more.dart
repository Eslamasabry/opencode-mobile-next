// Census scenes for the ledger part `j1-settings-more`
// (docs/design/ui-ledger/parts/j1-settings-more.json). See tool/capture/census_test.dart.
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/product_repository.dart';
import 'package:opencode_mobile/domain/plugin_inventory.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/ui/screens/about_screen.dart';
import 'package:opencode_mobile/ui/screens/guide_screen.dart';
import 'package:opencode_mobile/ui/screens/home_screen.dart';
import 'package:opencode_mobile/ui/screens/saved_permissions_screen.dart';
import 'package:opencode_mobile/ui/screens/server_capabilities_screen.dart';
import 'package:opencode_mobile/ui/screens/settings/server_plugins_section.dart';
import 'package:opencode_mobile/ui/screens/usage_hub_screen.dart';
import 'package:opencode_mobile/ui/screens/web_sources_screen.dart';
import 'package:opencode_mobile/ui/widgets/markdown.dart';
import 'package:opencode_mobile/ui/widgets/pickers.dart';

import '../../../../test/support/settings_scenes.dart';
import '../../fixtures.dart';
import '../census_core.dart';

// ---------------------------------------------------------------------------
// Fakes
// ---------------------------------------------------------------------------

/// A healthy server with a default shell, so the hub in the shell shows its
/// normal state rather than "Health unavailable".
/// About reads PRIVACY.md and THIRD_PARTY_NOTICES.md from the asset bundle,
/// which is real file IO: load them outside the fake clock first so the
/// screen's own loads resolve from the bundle's cache.
Future<void> _pumpAbout(CensusKit kit) async {
  const documents = ['THIRD_PARTY_NOTICES.md'];
  // An earlier shot may have left a load pending in the bundle's cache (one
  // started on the fake clock never finishes): drop it and load afresh.
  for (final key in documents) {
    rootBundle.evict(key);
  }
  await kit.tester.runAsync(
    () => Future.wait([
      for (final key in documents) rootBundle.loadString(key),
    ]).timeout(const Duration(seconds: 20)),
  );
  await kit.pumpApp(const AboutScreen(), controller: await kit.connected());
  await kit.realWait();
  kit.expectVisible(find.byType(MarkdownText), 'the loaded document');
}

class _HealthyApi extends CaptureApi {
  @override
  Future<Health> health() async => Health(healthy: true, version: '1.18.25');
}

class _ShellRepository extends CaptureRepository {
  @override
  Future<TerminalShellSettings> loadTerminalShellSettings() async =>
      const TerminalShellSettings(selected: '', options: []);
}

class _PluginApi extends CaptureApi {
  @override
  ServerCapabilities get capabilities =>
      const ServerCapabilities(pluginInventory: true);
}

class _PluginRepository extends CaptureRepository implements PluginGateway {
  @override
  Future<List<PluginInfo>> listPlugins() async => const [
    PluginInfo(
      id: 'code-review',
      status: PluginStatus.active,
      source: PluginSourceKind.package,
      packageName: '@example/code-review',
      terminalUi: true,
    ),
    PluginInfo(
      id: 'local-helper',
      status: PluginStatus.failed,
      source: PluginSourceKind.local,
      terminalUi: false,
    ),
  ];

  @override
  Future<List<CommandInfo>> listCommands() async => const [
    CommandInfo(name: 'review', subtask: false),
    CommandInfo(name: 'test', subtask: false),
  ];
}

class _WebSearchApi extends CaptureApi implements WebSearchGateway {
  @override
  ServerCapabilities get capabilities =>
      const ServerCapabilities(webSearch: true);

  @override
  Future<List<WebSearchProvider>> webSearchProviders() async => const [
    WebSearchProvider(id: 'example', name: 'Configured search provider'),
  ];

  @override
  Future<WebSearchResponse> searchWeb(
    String query, {
    required String providerID,
  }) async => const WebSearchResponse(
    providerID: 'example',
    results: [
      WebSearchResult(
        url: 'https://opencode.ai/docs/share',
        title: 'Sharing a session',
        content: 'How to turn on read-only sharing for a conversation.',
      ),
      WebSearchResult(
        url: 'https://opencode.ai/docs/mcp',
        title: 'Connecting an MCP server',
        content: 'Remote and local MCP servers, and how OAuth detection works.',
      ),
    ],
  );
}

class _UsageRepository extends CaptureRepository
    implements UsageStatisticsGateway {
  @override
  bool get usageStatisticsSupported => true;

  @override
  Future<UsageStatistics> loadUsageStatistics(UsageQuery query) async =>
      UsageStatistics(
        from: DateTime(2026, 9, 1).millisecondsSinceEpoch,
        to: DateTime(2026, 9, 24).millisecondsSinceEpoch,
        sessions: 12,
        subagents: 3,
        prompts: 84,
        steps: 210,
        activeDays: 9,
        streak: 3,
        cost: 4.62,
        tokens: const UsageTokens(
          input: 120000,
          output: 45000,
          reasoning: 8000,
          cacheRead: 30000,
          cacheWrite: 5000,
        ),
        tools: const UsageToolTotals(
          calls: 40,
          succeeded: 36,
          failed: 2,
          unfinished: 2,
        ),
        models: const [
          UsageModel(
            providerID: 'anthropic',
            modelID: 'claude-sonnet-4',
            steps: 120,
            tokens: UsageTokens(
              input: 80000,
              output: 30000,
              reasoning: 5000,
              cacheRead: 20000,
              cacheWrite: 3000,
            ),
            cost: 3.1,
          ),
        ],
        activity: const [
          UsageActivity(date: '2026-09-20', steps: 12),
          UsageActivity(date: '2026-09-21', steps: 18),
          UsageActivity(date: '2026-09-22', steps: 6),
        ],
      );
}

class _SavedPermissionsRepository extends CaptureRepository {
  @override
  Future<List<SavedPermission>> listSavedPermissions() async => const [
    SavedPermission(
      id: 'perm_1',
      projectID: 'project_shopfront',
      action: 'bash',
      resource: 'flutter test *',
    ),
    SavedPermission(
      id: 'perm_2',
      projectID: 'project_shopfront',
      action: 'edit',
      resource: '',
    ),
  ];

  @override
  Future<void> removeSavedPermission(String id) async {}
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

Future<CaptureController> _pluginsController(CensusKit kit) async {
  final controller = await kit.connected(
    api: _PluginApi(),
    repository: _PluginRepository(),
  );
  await kit.pumpApp(
    Scaffold(
      body: SingleChildScrollView(
        child: ServerPluginsSection(controller: controller),
      ),
    ),
    controller: controller,
  );
  return controller;
}

Future<CaptureController> _modelController(
  CensusKit kit, {
  Set<String> unloadedProviders = const {},
}) async {
  final controller = await kit.connected();
  controller.catalog = sampleCatalog();
  final model = controller.catalog!.models.first;
  controller.selectedModel = ModelRef(
    providerID: model.providerID,
    modelID: model.id,
  );
  controller.selectedAgent = 'build';
  controller.unloadedProviderIDs = unloadedProviders;
  await kit.pumpApp(const HomeScreen(), controller: controller);
  return controller;
}

// ---------------------------------------------------------------------------
// Area
// ---------------------------------------------------------------------------

final j1SettingsMoreArea = CensusArea(
  'j1-settings-more',
  shots: [
    // ---- Settings hub ----------------------------------------------------
    CensusShot('settings', state: 'pushed', (kit) async {
      final done = await mountSettingsScene(
        kit.tester,
        SettingsScene.hub,
        light: false,
        boundary: kit.boundaryKey,
      );
      kit.onDispose(done);
      kit.expectText('This server');
    }, note: 'Pushed via Ctrl/Cmd+, or a search result, with its own app bar.'),
    CensusShot(
      'settings',
      state: 'in-shell',
      (kit) async {
        final controller = await kit.connected(
          api: _HealthyApi(),
          repository: _ShellRepository(),
        );
        await kit.pumpApp(
          const HomeScreen(initialTab: 3),
          controller: controller,
        );
        kit.expectText('This server');
      },
      note: 'The same hub embedded as tab 3 of HomeScreen (bottom nav shown).',
    ),
    CensusShot('settings-disconnect-sheet', (kit) async {
      final done = await mountSettingsScene(
        kit.tester,
        SettingsScene.hub,
        light: false,
        boundary: kit.boundaryKey,
      );
      kit.onDispose(done);
      await kit.tapKey('settings-disconnect');
      kit.expectVisible(find.byKey(const ValueKey('confirm-disconnect')));
    }),
    CensusShot('coding-settings-shell-sheet', (kit) async {
      final done = await mountSettingsScene(
        kit.tester,
        SettingsScene.hub,
        light: false,
        boundary: kit.boundaryKey,
      );
      kit.onDispose(done);
      await kit.tapKey('default-shell-settings-entry');
      kit.expectText('Automatic (server default)');
    }),

    // ---- Notifications -----------------------------------------------------
    CensusShot('notifications-settings', state: 'loaded', (kit) async {
      final done = await mountSettingsScene(
        kit.tester,
        SettingsScene.notifications,
        light: false,
        boundary: kit.boundaryKey,
      );
      kit.onDispose(done);
      kit.expectVisible(find.byKey(const ValueKey('notifications-settings')));
    }),
    CensusShot('notifications-settings-quiet-time-dialog', (kit) async {
      final done = await mountSettingsScene(
        kit.tester,
        SettingsScene.notifications,
        light: false,
        boundary: kit.boundaryKey,
      );
      kit.onDispose(done);
      await kit.tapKey('notify-quiet-hours');
      await kit.tapKey('notify-quiet-start');
      kit.expectText('OK');
    }),

    // ---- Usage ---------------------------------------------------------------
    CensusShot('usage-hub', state: 'loaded', (kit) async {
      final controller = await kit.connected(repository: _UsageRepository());
      await kit.pumpApp(
        UsageHubScreen(controller: controller),
        controller: controller,
      );
      kit.expectVisible(find.byKey(const ValueKey('usage-tab-spending')));
      kit.expectVisible(find.byKey(const ValueKey('usage-tab-plans')));
    }),

    // ---- Appearance ------------------------------------------------------
    CensusShot('appearance-settings', state: 'loaded', (kit) async {
      final done = await mountSettingsScene(
        kit.tester,
        SettingsScene.appearance,
        light: false,
        boundary: kit.boundaryKey,
      );
      kit.onDispose(done);
      kit.expectText('Appearance');
    }),
    CensusShot('theme-pack-preview-sheet', (kit) async {
      final done = await mountSettingsScene(
        kit.tester,
        SettingsScene.appearance,
        light: false,
        boundary: kit.boundaryKey,
      );
      kit.onDispose(done);
      await kit.tapKey('theme-pack-opencode');
      kit.expectVisible(find.byKey(const ValueKey('appearance-picker')));
    }),
    CensusShot('language-sheet', (kit) async {
      final done = await mountSettingsScene(
        kit.tester,
        SettingsScene.appearance,
        light: false,
        boundary: kit.boundaryKey,
      );
      kit.onDispose(done);
      await kit.tap(find.widgetWithText(ListTile, 'Language'));
      kit.expectText('Use system language');
    }),

    // ---- Privacy ---------------------------------------------------------
    CensusShot('privacy-settings', state: 'loaded', (kit) async {
      final done = await mountSettingsScene(
        kit.tester,
        SettingsScene.privacy,
        light: false,
        boundary: kit.boundaryKey,
      );
      kit.onDispose(done);
      kit.expectVisible(find.byKey(const ValueKey('clear-queued-prompts')));
    }),
    CensusShot('privacy-settings-clear-queued-sheet', (kit) async {
      final done = await mountSettingsScene(
        kit.tester,
        SettingsScene.privacy,
        light: false,
        boundary: kit.boundaryKey,
      );
      kit.onDispose(done);
      await kit.tapKey('clear-queued-prompts');
      kit.expectText('Delete');
    }),
    CensusShot('privacy-settings-clear-drafts-sheet', (kit) async {
      final done = await mountSettingsScene(
        kit.tester,
        SettingsScene.privacy,
        light: false,
        boundary: kit.boundaryKey,
      );
      kit.onDispose(done);
      await kit.tapKey('clear-session-drafts');
      kit.expectText('Delete');
    }),

    // ---- Plugins -----------------------------------------------------------
    CensusShot('plugins-settings', state: 'loaded', (kit) async {
      await _pluginsController(kit);
      kit.expectVisible(find.byKey(const ValueKey('plugins-section-server')));
    }),

    // ---- About -------------------------------------------------------------
    CensusShot('about', (kit) async {
      await _pumpAbout(kit);
      kit.expectTextContaining('Open source');
    }, note: 'top of the screen: build identity, then Open source'),
    CensusShot('about-open-source-tab', (kit) async {
      await _pumpAbout(kit);
      await kit.tester.drag(
        find.byKey(const ValueKey('about-page')),
        const Offset(0, -600),
      );
      await kit.settle();
    }, note: "About's Open source section (no tabs since slice-P3.10)"),

    // ---- Guide, diagnostics, misc -------------------------------------------
    CensusShot('guide', (kit) async {
      await kit.pumpApp(const GuideScreen(), controller: await kit.connected());
      kit.expectText('Setup guide');
    }),
    CensusShot('app-diagnostics', state: 'loaded', (kit) async {
      final done = await mountSettingsScene(
        kit.tester,
        SettingsScene.diagnostics,
        light: false,
        boundary: kit.boundaryKey,
      );
      kit.onDispose(done);
      kit.expectVisible(find.byKey(const ValueKey('report-problem-send')));
    }),
    CensusShot('app-diagnostics', state: 'empty', (kit) async {
      final done = await mountSettingsScene(
        kit.tester,
        SettingsScene.diagnosticsEmpty,
        light: false,
        boundary: kit.boundaryKey,
      );
      kit.onDispose(done);
      kit.expectVisible(find.byKey(const ValueKey('report-problem-send')));
    }),
    CensusShot('app-diagnostics-clear-sheet', (kit) async {
      final done = await mountSettingsScene(
        kit.tester,
        SettingsScene.diagnostics,
        light: false,
        boundary: kit.boundaryKey,
      );
      kit.onDispose(done);
      // Everything this phone kept is one tap under Details.
      await kit.tapKey('report-problem-details');
      await kit.tapKey('report-problem-kept');
      await kit.tapKey('clear-app-diagnostics');
      kit.expectText('Clear');
    }),

    // ---- Saved permissions -----------------------------------------------
    CensusShot('saved-permissions', state: 'loaded', (kit) async {
      final controller = await kit.connected(
        repository: _SavedPermissionsRepository(),
      );
      await kit.pumpApp(
        SavedPermissionsScreen(controller: controller),
        controller: controller,
      );
      kit.expectVisible(find.byKey(const ValueKey('saved-permission-perm_1')));
    }),
    CensusShot('saved-permissions-revoke-dialog', (kit) async {
      final controller = await kit.connected(
        repository: _SavedPermissionsRepository(),
      );
      await kit.pumpApp(
        SavedPermissionsScreen(controller: controller),
        controller: controller,
      );
      await kit.tapKey('revoke-saved-permission-perm_1');
      kit.expectText('Revoke access');
    }),

    // ---- Web sources -------------------------------------------------------
    CensusShot('web-sources', state: 'reviewed', (kit) async {
      final controller = await kit.connected(api: _WebSearchApi());
      await kit.pumpApp(const HomeScreen(), controller: controller);
      await kit.push(WebSourcesScreen(controller: controller));
      await kit.enterText(
        find.byKey(const ValueKey('web-search-query')),
        'Sharing a conversation',
      );
      await kit.tap(find.widgetWithText(FilledButton, 'Search'));
      kit.expectText('Sharing a session');
    }),

    // ---- Model picker --------------------------------------------------------
    CensusShot('model-picker-sheet', (kit) async {
      await _modelController(kit);
      await kit.present((context) => showModelPicker(context));
      kit.expectVisible(find.byKey(const ValueKey('model-picker-search')));
    }),
    CensusShot('model-picker-sheet-unloaded-providers-dialog', (kit) async {
      await _modelController(kit, unloadedProviders: {'anthropic'});
      await kit.present((context) => showModelPicker(context));
      // Merged into the sheet (shared-chat-1): the explanation and its
      // Reload action sit in the notice; no dialog.
      kit.expectVisible(
        find.byKey(const ValueKey('picker-unloaded-providers')),
      );
      kit.expectText('Reload providers');
    }),
    CensusShot('model-picker-sheet-agent-dialog', (kit) async {
      await _modelController(kit);
      await kit.present(
        (context) => showModelPicker(context, focusAgent: true),
      );
      kit.expectVisible(find.byKey(const ValueKey('model-picker-agent')));
    }),
    CensusShot('model-picker-sheet-options-dialog', (kit) async {
      final controller = await _modelController(kit);
      await kit.present((context) => showModelPicker(context));
      final model = controller.catalog!.models.first;
      await kit.tapKey('model-option-${model.providerID}-${model.id}');
      // Merged into the sheet (shared-chat-1): the chosen model unfolds in
      // place into its details.
      await kit.tapKey('model-picker-options');
      kit.expectVisible(find.byKey(const ValueKey('model-picker-details')));
    }),

    // ---- Server capabilities -----------------------------------------------
    CensusShot('server-capabilities', (kit) async {
      final controller = await kit.connected();
      await kit.pumpApp(
        ServerCapabilitiesScreen(controller: controller),
        controller: controller,
      );
      kit.expectText('Available on this server');
    }),
  ],
  notRendered: {},
);
