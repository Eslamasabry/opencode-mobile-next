import 'dart:async';

import 'package:flutter/material.dart';

import '../../domain/server_gateway.dart' show ServerCapabilities;
import '../../l10n/app_localizations.dart';
import '../../platform/platform_capabilities.dart';
import '../../state/connection.dart';
import '../app_theme.dart';
import '../kit/kit.dart';

/// Where a feature's answer comes from, which decides the heading an absent
/// one is listed under: the server cannot do it, or this device cannot.
enum ServerFeatureSource { server, device }

/// What [ServerFeature.available] may read.
typedef ServerFeatureFacts = ({
  ServerCapabilities server,
  PlatformCapabilities device,
  bool usageStatistics,
});

/// One thing the app can do, in the person's words. Plain data, so the list
/// can be tested against a capability set without a widget.
class ServerFeature {
  const ServerFeature({
    required this.id,
    required this.title,
    required this.detail,
    required this.available,
    this.source = ServerFeatureSource.server,
    this.capability,
  });

  final String id;
  final String Function(AppLocalizations l10n) title;
  final String Function(AppLocalizations l10n) detail;
  final bool Function(ServerFeatureFacts facts) available;
  final ServerFeatureSource source;

  /// The capabilities.json matrix id this feature belongs to, when it has
  /// one: a missing feature then says which servers have it
  /// ([KitCapabilityExplainer.hostsLineOf]).
  final String? capability;
}

/// Every feature that rule 7 can hide, in the order a person meets them:
/// project tools, then what a conversation can do, then setup. A backend
/// name never appears here; the only proper noun on the screen is the
/// server's own name.
final List<ServerFeature> serverFeatures = [
  ServerFeature(
    id: 'files',
    capability: 'flag:fileBrowsing+terminal',
    title: (l10n) => l10n.capabilityFiles,
    detail: (l10n) => l10n.capabilityFilesDetail,
    available: (facts) => facts.server.fileBrowsing,
  ),
  ServerFeature(
    id: 'changes',
    capability: 'flag:sessionDiff',
    title: (l10n) => l10n.capabilityChanges,
    detail: (l10n) => l10n.capabilityChangesDetail,
    // Either door is enough: a conversation's own diff, or the project's
    // working tree through the file API.
    available: (facts) => facts.server.sessionDiff || facts.server.fileBrowsing,
  ),
  ServerFeature(
    id: 'terminal',
    capability: 'flag:fileBrowsing+terminal',
    title: (l10n) => l10n.capabilityTerminal,
    detail: (l10n) => l10n.capabilityTerminalDetail,
    available: (facts) => facts.server.terminal,
  ),
  ServerFeature(
    id: 'shell',
    title: (l10n) => l10n.capabilityShell,
    detail: (l10n) => l10n.capabilityShellDetail,
    available: (facts) => facts.server.shellSettings,
  ),
  ServerFeature(
    id: 'dev-services',
    capability: 'flag:developmentServices',
    title: (l10n) => l10n.capabilityDevServices,
    detail: (l10n) => l10n.capabilityDevServicesDetail,
    available: (facts) => facts.server.developmentServices,
  ),
  ServerFeature(
    id: 'projects',
    title: (l10n) => l10n.capabilityProjects,
    detail: (l10n) => l10n.capabilityProjectsDetail,
    available: (facts) => facts.server.projectManagement,
  ),
  ServerFeature(
    id: 'worktrees',
    capability: 'flag:worktreeCreate+sessionShare+managedWorkspaces',
    title: (l10n) => l10n.capabilityWorktrees,
    detail: (l10n) => l10n.capabilityWorktreesDetail,
    available: (facts) => facts.server.projectManagement,
  ),
  ServerFeature(
    id: 'cloud-environments',
    capability: 'flag:worktreeCreate+sessionShare+managedWorkspaces',
    title: (l10n) => l10n.capabilityCloud,
    detail: (l10n) => l10n.capabilityCloudDetail,
    available: (facts) => facts.server.managedWorkspaces,
  ),
  ServerFeature(
    id: 'attachments',
    capability: 'flag:promptAttachments',
    title: (l10n) => l10n.capabilityAttachments,
    detail: (l10n) => l10n.capabilityAttachmentsDetail,
    available: (facts) => facts.server.promptAttachments,
  ),
  ServerFeature(
    id: 'web-search',
    title: (l10n) => l10n.capabilityWebSearch,
    detail: (l10n) => l10n.capabilityWebSearchDetail,
    available: (facts) => facts.server.webSearch,
  ),
  ServerFeature(
    id: 'subagents',
    title: (l10n) => l10n.capabilitySubagents,
    detail: (l10n) => l10n.capabilitySubagentsDetail,
    available: (facts) => facts.server.promptAgentMentions,
  ),
  ServerFeature(
    id: 'compact',
    title: (l10n) => l10n.capabilityCompact,
    detail: (l10n) => l10n.capabilityCompactDetail,
    available: (facts) => facts.server.sessionCompact,
  ),
  ServerFeature(
    id: 'share',
    capability: 'flag:worktreeCreate+sessionShare+managedWorkspaces',
    title: (l10n) => l10n.capabilityShare,
    detail: (l10n) => l10n.capabilityShareDetail,
    available: (facts) => facts.server.sessionShare,
  ),
  ServerFeature(
    id: 'fork',
    title: (l10n) => l10n.capabilityFork,
    detail: (l10n) => l10n.capabilityForkDetail,
    available: (facts) => facts.server.sessionFork,
  ),
  ServerFeature(
    id: 'revert',
    capability: 'flag:stagedRevert+sessionNotes',
    title: (l10n) => l10n.capabilityRevert,
    detail: (l10n) => l10n.capabilityRevertDetail,
    available: (facts) => facts.server.sessionRevert,
  ),
  ServerFeature(
    id: 'archive',
    title: (l10n) => l10n.capabilityArchive,
    detail: (l10n) => l10n.capabilityArchiveDetail,
    available: (facts) => facts.server.sessionArchive,
  ),
  ServerFeature(
    id: 'delete-message',
    title: (l10n) => l10n.capabilityDeleteMessage,
    detail: (l10n) => l10n.capabilityDeleteMessageDetail,
    available: (facts) => facts.server.messageDelete,
  ),
  ServerFeature(
    id: 'todos',
    title: (l10n) => l10n.capabilityTodos,
    detail: (l10n) => l10n.capabilityTodosDetail,
    available: (facts) => facts.server.sessionTodos,
  ),
  ServerFeature(
    id: 'notes',
    capability: 'flag:stagedRevert+sessionNotes',
    title: (l10n) => l10n.capabilityNotes,
    detail: (l10n) => l10n.capabilityNotesDetail,
    available: (facts) => facts.server.sessionNotes,
  ),
  ServerFeature(
    id: 'import-export',
    title: (l10n) => l10n.capabilityImportExport,
    detail: (l10n) => l10n.capabilityImportExportDetail,
    available: (facts) => facts.server.sessionImportExport,
  ),
  ServerFeature(
    id: 'all-conversations',
    title: (l10n) => l10n.capabilitySearchAll,
    detail: (l10n) => l10n.capabilitySearchAllDetail,
    available: (facts) => facts.server.globalSessionSearch,
  ),
  ServerFeature(
    id: 'always-allow',
    title: (l10n) => l10n.capabilityAlwaysAllow,
    detail: (l10n) => l10n.capabilityAlwaysAllowDetail,
    available: (facts) => facts.server.persistentPermissionGrants,
  ),
  ServerFeature(
    id: 'saved-permissions',
    title: (l10n) => l10n.capabilitySavedPermissions,
    detail: (l10n) => l10n.capabilitySavedPermissionsDetail,
    available: (facts) => facts.server.savedPermissionList,
  ),
  ServerFeature(
    id: 'approvals',
    title: (l10n) => l10n.capabilityApprovals,
    detail: (l10n) => l10n.capabilityApprovalsDetail,
    available: (facts) => facts.server.permissionRequests,
  ),
  ServerFeature(
    id: 'send-later',
    title: (l10n) => l10n.capabilityOfflineQueue,
    detail: (l10n) => l10n.capabilityOfflineQueueDetail,
    available: (facts) => facts.server.offlinePromptQueue,
  ),
  ServerFeature(
    id: 'send-while-working',
    title: (l10n) => l10n.capabilitySteer,
    detail: (l10n) => l10n.capabilitySteerDetail,
    available: (facts) => facts.server.inbox,
  ),
  ServerFeature(
    id: 'continue-on-computer',
    title: (l10n) => l10n.capabilityContinueOnComputer,
    detail: (l10n) => l10n.capabilityContinueOnComputerDetail,
    available: (facts) => facts.server.cliSessionResume,
  ),
  ServerFeature(
    id: 'models',
    capability: 'flag:serverCatalog',
    title: (l10n) => l10n.capabilityModels,
    detail: (l10n) => l10n.capabilityModelsDetail,
    available: (facts) => facts.server.serverCatalog,
  ),
  ServerFeature(
    id: 'skills',
    capability: 'flag:serverCatalog',
    title: (l10n) => l10n.capabilitySkills,
    detail: (l10n) => l10n.capabilitySkillsDetail,
    available: (facts) => facts.server.serverCatalog,
  ),
  ServerFeature(
    id: 'mcp',
    capability: 'flag:serverCatalog',
    title: (l10n) => l10n.capabilityMcp,
    detail: (l10n) => l10n.capabilityMcpDetail,
    available: (facts) => facts.server.serverCatalog,
  ),
  ServerFeature(
    id: 'mcp-add',
    title: (l10n) => l10n.capabilityMcpAdd,
    detail: (l10n) => l10n.capabilityMcpAddDetail,
    available: (facts) =>
        facts.server.mcpConfigWrites || facts.server.mcpRuntimeAdds,
  ),
  ServerFeature(
    id: 'plugins',
    title: (l10n) => l10n.capabilityPlugins,
    detail: (l10n) => l10n.capabilityPluginsDetail,
    available: (facts) => facts.server.pluginInventory,
  ),
  ServerFeature(
    id: 'usage',
    capability: 'flag:usageStatistics',
    title: (l10n) => l10n.capabilityUsage,
    detail: (l10n) => l10n.capabilityUsageDetail,
    available: (facts) => facts.usageStatistics,
  ),
  ServerFeature(
    id: 'server-updates',
    capability: 'flag:remoteUpgrade',
    title: (l10n) => l10n.capabilityServerUpdates,
    detail: (l10n) => l10n.capabilityServerUpdatesDetail,
    available: (facts) => facts.server.remoteUpgrade,
  ),
  // What the device decides. On a phone these are all "Available here"; the
  // third heading only exists where one of them is missing.
  ServerFeature(
    id: 'background-notifications',
    source: ServerFeatureSource.device,
    title: (l10n) => l10n.capabilityBackgroundNotifications,
    detail: (l10n) => l10n.capabilityBackgroundNotificationsDetail,
    available: (facts) =>
        facts.device.supportsNotifications &&
        facts.device.supportsBackgroundService,
  ),
  ServerFeature(
    id: 'on-this-phone',
    source: ServerFeatureSource.device,
    title: (l10n) => l10n.capabilityOnThisPhone,
    detail: (l10n) => l10n.capabilityOnThisPhoneDetail,
    available: (facts) => facts.device.supportsTermux,
  ),
  ServerFeature(
    id: 'voice',
    source: ServerFeatureSource.device,
    title: (l10n) => l10n.capabilityVoice,
    detail: (l10n) => l10n.capabilityVoiceDetail,
    available: (facts) => facts.device.supportsVoice,
  ),
];

/// Settings → Help → "Available on this server": the explanation for every
/// row rule 7 hides. A feature the connected server cannot do is absent from
/// the menus and tabs, so this is the one place that says so.
///
/// Built from kit parts only (screen-system-1). People come here asking why
/// something is missing, so it is one list ordered by urgency: what this
/// server lacks first, each row saying so in words (the intro says once
/// where missing features work; a row names its servers only when it is
/// the focused one or its answer differs), then what the device lacks, then one row that unfolds everything
/// that works here. Where the app can turn a missing thing on (another
/// server), the list ends with that action; it appears only when the app
/// registered the flow, so it never leads nowhere.
class ServerCapabilitiesScreen extends StatelessWidget {
  const ServerCapabilitiesScreen({
    super.key,
    required this.controller,
    this.focusFeature,
  });

  final ConnectionController controller;

  /// A [ServerFeature.id] the person came here about (from a hidden row's
  /// explainer): it is listed first, whether this server has it or not.
  final String? focusFeature;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final tokens = KitTokens.of(context);
    return KitScreen(
      topBar: KitTopBar(title: l10n.capabilityScreenTitle),
      width: KitScreenWidth.reading,
      body: ListenableBuilder(
        listenable: controller,
        builder: (context, _) {
          final ServerFeatureFacts facts = (
            server: controller.capabilities,
            device: platformCapabilities,
            usageStatistics: controller.supportsUsageStatistics,
          );
          final serverName = controller.profile?.name ?? l10n.e7SettingsUi9;
          bool first(ServerFeature feature) => feature.id == focusFeature;
          final available = [
            for (final feature in serverFeatures)
              if (feature.available(facts) && !first(feature)) feature,
          ];
          List<ServerFeature> missing(ServerFeatureSource source) => [
            for (final feature in serverFeatures)
              if (!feature.available(facts) &&
                  feature.source == source &&
                  !first(feature))
                feature,
          ];
          final onServer = missing(ServerFeatureSource.server);
          final onDevice = missing(ServerFeatureSource.device);
          final focused = [
            for (final feature in serverFeatures)
              if (first(feature)) feature,
          ];
          final focusMissing = focused.any((f) => !f.available(facts));
          final serverGaps =
              onServer.isNotEmpty ||
              focused.any(
                (f) =>
                    !f.available(facts) &&
                    f.source == ServerFeatureSource.server,
              );
          final canAddServer =
              serverGaps && KitCapabilities.canEnable('server.any');

          // Which servers have a missing feature is said once: the intro
          // covers the most common answer, and a row repeats it only when
          // the person came about that feature or its answer differs.
          String hosts(ServerFeature feature) => feature.capability == null
              ? ''
              : KitCapabilityExplainer.hostsLineOf(
                  context,
                  feature.capability!,
                );
          final tally = <String, int>{};
          for (final feature in [...focused, ...onServer]) {
            if (feature.available(facts) ||
                feature.source != ServerFeatureSource.server) {
              continue;
            }
            final line = hosts(feature);
            if (line.isNotEmpty) {
              tally.update(line, (n) => n + 1, ifAbsent: () => 1);
            }
          }
          String? common;
          for (final entry in tally.entries) {
            if (common == null || entry.value > tally[common]!) {
              common = entry.key;
            }
          }

          Widget row(ServerFeature feature) {
            final line = hosts(feature);
            return _FeatureRow(
              feature: feature,
              present: feature.available(facts),
              hosts: line.isEmpty || (!first(feature) && line == common)
                  ? null
                  : line,
            );
          }

          return ListView(
            key: const ValueKey('server-capabilities'),
            padding: EdgeInsetsDirectional.only(
              top: tokens.space4,
              bottom: KitScreen.endPadding(context),
            ),
            children: [
              Padding(
                padding: EdgeInsetsDirectional.only(
                  start: tokens.gutter,
                  end: tokens.gutter,
                  bottom: tokens.space4,
                ),
                child: KitText(
                  serverGaps
                      ? l10n.capabilityScreenIntroWithGaps(
                          KitBidi.auto(serverName),
                        )
                      : l10n.capabilityScreenIntro(KitBidi.auto(serverName)),
                  key: const ValueKey('server-capabilities-intro'),
                  role: KitTextRole.secondary,
                ),
              ),
              if (onServer.isEmpty && !focusMissing) ...[
                Padding(
                  padding: EdgeInsetsDirectional.only(
                    start: tokens.gutter,
                    end: tokens.gutter,
                    bottom: tokens.space4,
                  ),
                  child: KitNotice(
                    key: const ValueKey('server-capabilities-all'),
                    tone: AppStatusTone.ok,
                    icon: AppIconography.checkCircle,
                    message: l10n.capabilityAllAvailable,
                    liveRegion: false,
                  ),
                ),
              ],
              KitRowGroup(
                key: const ValueKey('capabilities-list'),
                children: [
                  for (final feature in focused) row(feature),
                  for (final feature in onServer) row(feature),
                  for (final feature in onDevice) row(feature),
                  if (canAddServer)
                    KitRow(
                      key: const ValueKey('capabilities-add-server'),
                      leading: KitRow.icon(context, AppIconography.add),
                      title: l10n.capabilityAddServer,
                      supporting: TextSpan(
                        text: l10n.capabilityAddServerDetail,
                      ),
                      supportingMaxLines: 2,
                      trailing: const KitChevron(),
                      onTap: () => unawaited(
                        KitCapabilities.enable(
                          context,
                          'server.any',
                          serverName: serverName,
                          source: 'server-capabilities',
                        ),
                      ),
                    ),
                  if (available.isNotEmpty)
                    KitExpandRow(
                      key: const ValueKey('capabilities-available'),
                      headerKey: const ValueKey('capabilities-available-fold'),
                      leading: KitRow.icon(
                        context,
                        AppIconography.checkCircle,
                        color: tokens.roles.success,
                      ),
                      title: l10n.capabilityAvailableCount(available.length),
                      supporting: TextSpan(
                        text: l10n.capabilityAvailableCountDetail,
                      ),
                      supportingMaxLines: 2,
                      children: [for (final feature in available) row(feature)],
                    ),
                ],
              ),
            ],
          );
        },
      ),
    );
  }
}

/// One feature: present ones in a check tile; missing ones say where the
/// gap is in words (this server or this device) and, when [hosts] is set,
/// which servers have it. Read-only: nothing here is a control.
class _FeatureRow extends StatelessWidget {
  const _FeatureRow({required this.feature, required this.present, this.hosts});

  final ServerFeature feature;
  final bool present;

  /// "Works on …" for a server gap, when the screen decided this row says
  /// it (the focused feature, or an answer unlike the intro's).
  final String? hosts;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final tokens = KitTokens.of(context);
    final device = feature.source == ServerFeatureSource.device;
    final slug = present
        ? 'available'
        : device
        ? 'device'
        : 'unavailable';
    final where = present
        ? null
        : device
        ? l10n.capabilityNeedsAndroid
        : hosts;
    final state = present
        ? l10n.capabilityStateHere
        : device
        ? l10n.capabilityStateNotDevice
        : l10n.capabilityStateNotServer;
    return KitRow(
      key: ValueKey('capability-$slug-${feature.id}'),
      leading: KitRow.icon(
        context,
        present ? AppIconography.check : AppIconography.blocked,
        color: present ? tokens.roles.success : null,
      ),
      title: feature.title(l10n),
      titleMaxLines: 2,
      supporting: TextSpan(
        children: [
          TextSpan(
            text: '$state · ',
            style: tokens.rowSupporting.copyWith(
              color: present ? tokens.roles.success : tokens.roles.text1,
            ),
          ),
          TextSpan(text: feature.detail(l10n)),
          if (where != null) TextSpan(text: '. $where.'),
        ],
      ),
      supportingMaxLines: 5,
    );
  }
}
