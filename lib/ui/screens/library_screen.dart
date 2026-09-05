import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../api/models.dart' show ModelRef, Session;
import '../../api/mcp_oauth.dart';
import '../../l10n/app_localizations.dart';
import '../../api/provider_presentation.dart';
import '../../feedback/bug_report.dart';
import '../../api/product_repository.dart';
import '../../state/connection.dart';
import '../app_theme.dart';
import '../desktop/desktop_interaction.dart';
import '../desktop/shortcuts.dart';
import '../widgets/file_preview.dart';
import '../widgets/connect_methods.dart';
import '../widgets/info_label.dart';
import '../widgets/provider_logo.dart';
import '../widgets/confirm_sheet.dart';
import '../widgets/product_states.dart';
import '../widgets/pickers.dart';
import 'capabilities_screen.dart';
import 'guide_screen.dart';
import 'mcp_setup_screen.dart';
import 'settings_screen.dart';
import 'terminal_screen.dart';

part 'library/catalog_screen.dart';
part 'library/integrations_screen.dart';
part 'library/integration_tiles.dart';
part 'library/commands_screen.dart';
part 'library/skills_screen.dart';
part 'library/references_screen.dart';

class LibraryScreen extends StatefulWidget {
  final ConnectionController controller;
  const LibraryScreen({super.key, required this.controller});

  @override
  State<LibraryScreen> createState() => _LibraryScreenState();
}

class _LibraryScreenState extends State<LibraryScreen> {
  final _search = TextEditingController();
  String _query = '';
  ConnectionController get controller => widget.controller;

  @override
  void dispose() {
    _search.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: controller,
      builder: (context, _) {
        final l10n = AppLocalizations.of(context);
        final group0 = <_DestinationRow>[
          _DestinationRow(
            icon: Icons.model_training_outlined,
            title: l10n.libraryModelsAgentsTitle,
            keywords: 'AI reasoning favorites recent',
            onTap: () => _open(context, CatalogScreen(controller: controller)),
          ),
          _DestinationRow(
            icon: Icons.cloud_outlined,
            title: l10n.libraryProvidersTitle,
            keywords: 'API keys authentication connect',
            onTap: () => _open(
              context,
              IntegrationsScreen(
                controller: controller,
                mode: IntegrationsMode.providers,
              ),
            ),
          ),
          _DestinationRow(
            icon: Icons.hub_outlined,
            title: l10n.libraryMcpTitle,
            keywords: 'integrations servers',
            onTap: () => _open(
              context,
              IntegrationsScreen(
                controller: controller,
                mode: IntegrationsMode.mcp,
              ),
            ),
          ),
          _DestinationRow(
            icon: Icons.build_circle_outlined,
            title: l10n.libraryCommandsToolsTitle,
            keywords: 'slash skills references capabilities',
            onTap: () =>
                _open(context, CapabilitiesScreen(controller: controller)),
          ),
          // §5: Terminal gives up its navigation slot to Activity and
          // is reached from here (and from a session) instead.
          _DestinationRow(
            key: const ValueKey('library-terminal'),
            icon: Icons.terminal_outlined,
            title: l10n.libraryTerminalTitle,
            keywords: 'shell command line',
            onTap: () => _open(context, TerminalPage(controller: controller)),
          ),
        ].where((card) => card.matches(_query)).toList();
        final group1 = <_DestinationRow>[
          _DestinationRow(
            icon: Icons.settings_outlined,
            title: l10n.librarySettingsTitle,
            keywords:
                'appearance theme language notifications privacy voice background server',
            onTap: () => _open(context, SettingsScreen(controller: controller)),
          ),
          _DestinationRow(
            icon: Icons.menu_book_outlined,
            title: 'Setup guide',
            keywords: 'help connect tutorial start',
            onTap: () => _open(context, const GuideScreen()),
          ),
          // The bug form lives in the failure states themselves; this
          // card is the deliberate path for everything a user notices
          // outside a failure surface.
          _DestinationRow(
            key: const ValueKey('library-report-bug'),
            icon: Icons.bug_report_outlined,
            title: 'Report a bug',
            keywords: 'feedback issue support',
            onTap: () => unawaited(openBugReport(context)),
          ),
          // The shortcut layer must be discoverable without already
          // knowing a shortcut.
          if (desktopInteractions)
            _DestinationRow(
              key: const ValueKey('library-keyboard-shortcuts'),
              icon: Icons.keyboard_outlined,
              title: 'Keyboard shortcuts',
              keywords: 'hotkeys help desktop',
              onTap: () => unawaited(showShortcutsHelp(context)),
            ),
        ].where((card) => card.matches(_query)).toList();
        // Audit UX-P0-01: no Mission Control or Requests card here. Pending
        // work has exactly one home — the Activity destination and its badge.
        return DesktopScrollbarArea(
          builder: (scrollController) => ListView(
            controller: scrollController,
            padding: const EdgeInsets.fromLTRB(12, 8, 12, 24),
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(4, 0, 4, 12),
                child: TextField(
                  key: const Key('library-search'),
                  controller: _search,
                  onChanged: (value) =>
                      setState(() => _query = value.trim().toLowerCase()),
                  decoration: InputDecoration(
                    hintText: l10n.librarySearchHint,
                    prefixIcon: const Icon(Icons.search_rounded),
                    suffixIcon: _query.isEmpty
                        ? null
                        : IconButton(
                            tooltip: l10n.commonClearSearch,
                            icon: const Icon(Icons.close_rounded),
                            onPressed: () {
                              _search.clear();
                              setState(() => _query = '');
                            },
                          ),
                  ),
                ),
              ),
              if (_query.isEmpty) _ActiveSetupCard(controller: controller),
              _DestinationGroup(
                title: l10n.libraryBrowseSection,
                cards: group0,
              ),
              _DestinationGroup(
                title: l10n.libraryManageSection,
                cards: group1,
              ),
              if (_query.isNotEmpty)
                Padding(
                  padding: const EdgeInsets.all(16),
                  child: Text(
                    l10n.librarySearchResults(
                      group0.length + group1.length,
                      _search.text.trim(),
                    ),
                    key: const Key('library-search-summary'),
                    style: Theme.of(context).textTheme.bodySmall,
                  ),
                ),
            ],
          ),
        );
      },
    );
  }

  static void _open(BuildContext context, Widget screen) {
    Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => screen));
  }
}

/// Terminal is a body-only tab widget; pushed from More it needs its own
/// Scaffold and an identity in the app bar.

/// The live model/agent/variant selection, promoted to the top of More so the
/// hub reports state instead of only linking away.
class _ActiveSetupCard extends StatelessWidget {
  final ConnectionController controller;

  const _ActiveSetupCard({required this.controller});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final model = controller.selectedModel;
    final catalogModel = controller.catalog?.models
        .where(
          (candidate) =>
              candidate.providerID == model?.providerID &&
              candidate.id == model?.modelID,
        )
        .firstOrNull;
    final modelLabel = model == null
        ? AppLocalizations.of(context).libraryNoModel
        : catalogModel?.name.trim().isNotEmpty == true
        ? catalogModel!.name
        : presentedModelLabel(model.providerID, model.modelID);
    final details = <String>[
      if (controller.selectedAgent.isNotEmpty) controller.selectedAgent,
      if (controller.selectedVariant.isNotEmpty) controller.selectedVariant,
    ];
    return Card(
      key: const ValueKey('library-active-setup'),
      margin: const EdgeInsets.symmetric(horizontal: 4),
      clipBehavior: Clip.antiAlias,
      child: ListTile(
        minTileHeight: 72,
        leading: _TileIcon(
          icon: Icons.memory_rounded,
          color: theme.colorScheme.primary,
        ),
        title: Text(modelLabel, maxLines: 1, overflow: TextOverflow.ellipsis),
        subtitle: Text(
          [
            AppLocalizations.of(context).libraryDefaultModel,
            ...details,
          ].join(' · '),
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
        ),
        trailing: const Icon(Icons.swap_horiz_rounded),
        onTap: () => showModelPicker(context),
      ),
    );
  }
}

/// Grouped rows let people scan destinations without a grid of empty tiles.
/// Intrinsic row heights also accommodate large text without truncating labels.
class _DestinationGroup extends StatelessWidget {
  final List<_DestinationRow> cards;
  final String title;

  const _DestinationGroup({required this.cards, required this.title});

  @override
  Widget build(BuildContext context) {
    final matches = cards;
    if (matches.isEmpty) return const SizedBox.shrink();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        SectionLabel(title),
        Card(
          margin: const EdgeInsets.symmetric(horizontal: 4),
          clipBehavior: Clip.antiAlias,
          child: Column(
            children: [
              for (var index = 0; index < matches.length; index++) ...[
                if (index > 0) const Divider(height: 1, indent: 64),
                matches[index],
              ],
            ],
          ),
        ),
      ],
    );
  }
}

class _DestinationRow extends StatelessWidget {
  final IconData icon;
  final String title;
  final String keywords;
  final VoidCallback onTap;

  const _DestinationRow({
    super.key,
    required this.icon,
    required this.title,
    this.keywords = '',
    required this.onTap,
  });

  bool matches(String query) => query
      .split(RegExp(r'\s+'))
      .every((word) => '$title $keywords'.toLowerCase().contains(word));

  @override
  Widget build(BuildContext context) {
    return ListTile(
      minTileHeight: 60,
      leading: Icon(icon),
      title: Text(title),
      trailing: const Icon(Icons.chevron_right_rounded, size: 20),
      onTap: onTap,
    );
  }
}

class _TileIcon extends StatelessWidget {
  final IconData icon;
  final Color? color;

  const _TileIcon({required this.icon, this.color});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final tint = color ?? theme.colorScheme.onSurfaceVariant;
    return Container(
      width: 36,
      height: 36,
      decoration: BoxDecoration(
        color: tint.withValues(alpha: .12),
        borderRadius: BorderRadius.circular(AppTheme.radiusControl),
      ),
      child: Icon(icon, size: 20, color: tint),
    );
  }
}
