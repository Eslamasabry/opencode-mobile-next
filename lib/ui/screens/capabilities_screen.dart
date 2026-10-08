import 'package:flutter/material.dart';

import '../../domain/external_agent.dart' show ExternalAgentGateway;
import '../../l10n/app_localizations.dart';
import '../../state/connection.dart';
import '../../state/external_agents.dart';
import '../app_iconography.dart';
import '../kit/kit.dart';
import '../search/search_index.dart';
import 'external_agents_screen.dart';
import 'library_screen.dart';
import 'server_capabilities_screen.dart';
import 'tools_screen.dart';

/// What the Tools page holds, in tab order (target-ia §1.3 row 7).
enum ToolsSection {
  /// MCP servers and their resources, with Add.
  mcp,

  /// The server's slash commands.
  commands,

  /// The model's tool list.
  tools,

  /// The server's skills.
  skills,

  /// Things a person can attach.
  references,

  /// Agents on other services (A2A) that work can be handed to.
  externalAgents,
}

/// Settings › Tools (target-ia §1.3 row 7): the one page for everything the
/// agent can use besides the model. Tabs: MCP, Commands, Tools, Skills,
/// References and External agents, each a body of its own that is built the
/// first time it is opened and kept from then on (a sign-in in progress on
/// MCP survives a look at Commands).
///
/// Gates explain instead of vanishing (P7.4, STATE-12): a server that does
/// not share its catalog (Codex, Paseo, no saved server) has no catalog
/// tabs; the page then holds External agents alone, with the hub's one muted
/// line "N settings aren't available on this server · Why". A server that
/// shares its catalog but not its tool list keeps the Tools tab, which says
/// why.
///
/// States: loaded (six tabs), tools not listed (Tools tab explains),
/// catalog not shared (External agents alone, with the line).
class CapabilitiesScreen extends StatefulWidget {
  final ConnectionController controller;

  /// The tab to open on, counted the way callers always have: Commands 0,
  /// Tools 1, Skills 2, References 3 where the server lists its tools, and
  /// without Tools (Skills 1, References 2) where it does not. The Tools
  /// tab now stays in both cases, so the count is mapped (see
  /// [_legacyTab]). Null opens the first tab.
  final int? initialTab;

  /// The tab to open on, by name. Wins over [initialTab].
  final ToolsSection? initialSection;

  /// Test seams for the External agents tab: a store to use (it is not
  /// disposed here) and the gateway its pages talk to.
  final ExternalAgentStore? externalAgentStore;
  final ExternalAgentGateway Function()? externalAgentGateway;

  const CapabilitiesScreen({
    super.key,
    required this.controller,
    this.initialTab,
    this.initialSection,
    this.externalAgentStore,
    this.externalAgentGateway,
  });

  /// The catalog rows the Settings hub counts when a server hides them.
  static const _catalogRows = ['settings-mcp', 'settings-commands-tools'];

  @override
  State<CapabilitiesScreen> createState() => _CapabilitiesScreenState();
}

class _CapabilitiesScreenState extends State<CapabilitiesScreen> {
  ToolsSection? _selected;
  final Set<ToolsSection> _visited = {};
  ExternalAgentStore? _ownedStore;

  @override
  void initState() {
    super.initState();
    _selected = widget.initialSection ?? _legacyTab(widget.initialTab);
  }

  @override
  void dispose() {
    _ownedStore?.dispose();
    super.dispose();
  }

  /// Callers that counted the four catalog tabs (Commands 0 … References 3)
  /// and knew Tools was missing counted Skills as 1: move them past the
  /// explained Tools tab so they land where they meant.
  ToolsSection? _legacyTab(int? requested) {
    if (requested == null) return null;
    const legacy = [
      ToolsSection.commands,
      ToolsSection.tools,
      ToolsSection.skills,
      ToolsSection.references,
    ];
    final tools = widget.controller.capabilities.toolInventory;
    final index = !tools && requested >= 1 ? requested + 1 : requested;
    return legacy[index.clamp(0, legacy.length - 1)];
  }

  /// The tabs this server can serve, in order. Without a shared catalog only
  /// External agents is left.
  List<ToolsSection> _sections() => [
    if (widget.controller.capabilities.serverCatalog) ...[
      ToolsSection.mcp,
      ToolsSection.commands,
      ToolsSection.tools,
      ToolsSection.skills,
      ToolsSection.references,
    ],
    ToolsSection.externalAgents,
  ];

  ExternalAgentStore _agentStore() {
    final injected = widget.externalAgentStore;
    if (injected != null) return injected;
    return _ownedStore ??= () {
      final profiles = widget.controller.store;
      return ExternalAgentStore(profiles.prefs, profiles.secure);
    }();
  }

  String _label(AppLocalizations l10n, ToolsSection section) =>
      switch (section) {
        ToolsSection.mcp => l10n.libraryMcpTitle,
        ToolsSection.commands => l10n.runResultsCommandsTitle,
        ToolsSection.tools => l10n.e7SettingsDetailUi25,
        ToolsSection.skills => l10n.e7SettingsDetailUi26,
        ToolsSection.references => l10n.e7SettingsDetailUi27,
        ToolsSection.externalAgents => l10n.a2aTitle,
      };

  Widget _body(ToolsSection section, String? serverName) {
    final controller = widget.controller;
    return KeyedSubtree(
      key: ValueKey('tools-section-${section.name}'),
      child: switch (section) {
        ToolsSection.mcp => IntegrationsScreen(
          controller: controller,
          mode: IntegrationsMode.mcp,
          embedded: true,
        ),
        ToolsSection.commands => CommandsScreen(
          controller: controller,
          embedded: true,
        ),
        ToolsSection.tools =>
          controller.capabilities.toolInventory
              ? ToolsScreen(controller: controller, embedded: true)
              : _ToolsNotListed(serverName: serverName),
        ToolsSection.skills => SkillsScreen(
          controller: controller,
          embedded: true,
        ),
        ToolsSection.references => ReferencesScreen(
          controller: controller,
          embedded: true,
        ),
        ToolsSection.externalAgents => ExternalAgentsScreen(
          store: _agentStore(),
          gatewayFactory: widget.externalAgentGateway,
          embedded: true,
        ),
      },
    );
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: widget.controller,
    builder: (context, _) => _buildScreen(context),
  );

  Widget _buildScreen(BuildContext context) {
    final l10n = _l10n(context);
    final controller = widget.controller;
    final serverName = controller.profile?.name;
    final sections = _sections();
    final current = sections.contains(_selected) ? _selected! : sections.first;
    _visited.add(current);
    final topBar = KitTopBar(
      title: l10n.settingsHubToolsRow,
      subtitle: serverName,
    );

    if (sections.length == 1) {
      // Codex, Paseo, no saved server: External agents alone, and the one
      // line that says what this server leaves out.
      final scope = SearchScope.of(context, controller);
      final hidden = controller.profile == null
          ? 0
          : allSearchEntries(l10n)
                .where(
                  (entry) =>
                      CapabilitiesScreen._catalogRows.contains(entry.id) &&
                      entry.hiddenByServer(scope),
                )
                .length;
      return KitScreen(
        topBar: topBar,
        width: KitScreenWidth.list,
        body: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Expanded(child: _body(current, serverName)),
            if (hidden > 0)
              Padding(
                padding: EdgeInsetsDirectional.only(
                  bottom: KitScreen.endPadding(context),
                ),
                child: KitGroupNote(
                  key: const ValueKey('tools-unavailable'),
                  message: l10n.settingsHubUnavailableCount(hidden),
                  action: KitAction(
                    key: const ValueKey('tools-unavailable-why'),
                    label: l10n.settingsHubUnavailableWhy,
                    onPressed: () => pushKitPage<void>(
                      context,
                      (_) => ServerCapabilitiesScreen(controller: controller),
                    ),
                  ),
                ),
              ),
          ],
        ),
      );
    }

    return KitScreen(
      topBar: topBar,
      width: KitScreenWidth.list,
      body: KitTabSwitcher.tabs(
        semanticsLabel: l10n.settingsHubToolsRow,
        index: sections.indexOf(current),
        onSelected: (index) => setState(() => _selected = sections[index]),
        tabs: [
          for (final section in sections)
            KitTab(
              key: ValueKey('capabilities-tab-${_label(l10n, section)}'),
              label: _label(l10n, section),
            ),
        ],
        children: [
          for (final section in sections)
            _visited.contains(section)
                ? _body(section, serverName)
                : const SizedBox.shrink(),
        ],
      ),
    );
  }
}

/// The Tools tab on a server that does not list its tools (OpenCode 2,
/// Codex, Paseo): why, and which servers do. No enable flow exists for
/// this capability, so it explains only (STATE-8: no dead button).
class _ToolsNotListed extends StatelessWidget {
  const _ToolsNotListed({required this.serverName});

  final String? serverName;

  @override
  Widget build(BuildContext context) {
    final l10n = _l10n(context);
    final name = serverName?.trim();
    return ListView(
      padding: KitScreen.padding(context),
      children: [
        KitStateView.missing(
          key: const ValueKey('capabilities-tools-unavailable'),
          capability: 'server.oc1',
          icon: AppIconography.tools,
          title: name == null || name.isEmpty
              ? l10n.capabilitiesToolsMissingTitle
              : l10n.capabilitiesToolsMissingOnServer(name),
          why: KitCapabilityExplainer.whyOf(context, 'server.oc1'),
        ),
      ],
    );
  }
}

/// Opens Settings › Tools, on [section] when given: the one door every other
/// entry (`/mcps`, the enable flows, Servers, search) uses.
Future<void> openTools(
  BuildContext context,
  ConnectionController controller, {
  ToolsSection? section,
}) => pushKitPage<void>(
  context,
  (_) => CapabilitiesScreen(controller: controller, initialSection: section),
);

/// Opens Tools on its External agents tab.
Future<void> openExternalAgents(
  BuildContext context,
  ConnectionController controller,
) => openTools(context, controller, section: ToolsSection.externalAgents);

AppLocalizations _l10n(BuildContext context) =>
    Localizations.of<AppLocalizations>(context, AppLocalizations) ??
    lookupAppLocalizations(const Locale('en'));
