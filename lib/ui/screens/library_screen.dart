import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../api/models.dart' show ModelRef;
import '../../api/mcp_oauth.dart';
import '../../api/provider_presentation.dart';
import '../../api/product_repository.dart';
import '../../state/connection.dart';
import '../widgets/file_preview.dart';
import '../widgets/confirm_sheet.dart';
import '../widgets/product_states.dart';
import '../widgets/pickers.dart';
import 'mcp_setup_screen.dart';
import 'requests_screen.dart';
import 'settings_screen.dart';
import 'tools_screen.dart';

part 'library/catalog_screen.dart';
part 'library/integrations_screen.dart';
part 'library/integration_tiles.dart';
part 'library/commands_screen.dart';
part 'library/skills_screen.dart';
part 'library/references_screen.dart';

class LibraryScreen extends StatelessWidget {
  final ConnectionController controller;
  const LibraryScreen({super.key, required this.controller});

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: controller,
      builder: (context, _) {
        final pending =
            controller.permissions.length + controller.questions.length;
        return ListView(
          padding: const EdgeInsets.fromLTRB(12, 8, 12, 24),
          children: [
            _ActiveSetupCard(controller: controller),
            const SectionLabel('Configure'),
            _DestinationGroup(
              tiles: [
                _DestinationTile(
                  icon: Icons.model_training_outlined,
                  title: 'Models and agents',
                  subtitle:
                      'Availability, capabilities, variants, and selection',
                  onTap: () =>
                      _open(context, CatalogScreen(controller: controller)),
                ),
                _DestinationTile(
                  icon: Icons.hub_outlined,
                  title: 'MCP and integrations',
                  subtitle: 'Connection status, authentication, and resources',
                  onTap: () => _open(
                    context,
                    IntegrationsScreen(controller: controller),
                  ),
                ),
              ],
            ),
            const SectionLabel('Discover'),
            _DestinationGroup(
              tiles: [
                _DestinationTile(
                  icon: Icons.electric_bolt_outlined,
                  title: 'Server commands',
                  subtitle:
                      'Configured commands, MCP prompts, and slash-capable skills',
                  onTap: () =>
                      _open(context, CommandsScreen(controller: controller)),
                ),
                _DestinationTile(
                  icon: Icons.build_circle_outlined,
                  title: 'Tools and capabilities',
                  subtitle: 'Callable tools for the active provider and model',
                  onTap: () =>
                      _open(context, ToolsScreen(controller: controller)),
                ),
                _DestinationTile(
                  icon: Icons.extension_outlined,
                  title: 'Skills',
                  subtitle: 'Inspect available project and global skills',
                  onTap: () =>
                      _open(context, SkillsScreen(controller: controller)),
                ),
                _DestinationTile(
                  icon: Icons.bookmarks_outlined,
                  title: 'References',
                  subtitle: 'Project context available to OpenCode',
                  onTap: () =>
                      _open(context, ReferencesScreen(controller: controller)),
                ),
              ],
            ),
            const SectionLabel('Manage'),
            _DestinationGroup(
              tiles: [
                _DestinationTile(
                  icon: Icons.notifications_active_outlined,
                  title: 'Pending requests',
                  subtitle: pending == 0
                      ? 'Nothing needs attention'
                      : '$pending awaiting response',
                  badge: pending == 0 ? null : '$pending',
                  onTap: () =>
                      _open(context, RequestsScreen(controller: controller)),
                ),
                _DestinationTile(
                  icon: Icons.settings_outlined,
                  title: 'Settings and server',
                  subtitle:
                      'Connection, health, version, and experimental features',
                  onTap: () =>
                      _open(context, SettingsScreen(controller: controller)),
                ),
              ],
            ),
          ],
        );
      },
    );
  }

  static void _open(BuildContext context, Widget screen) {
    Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => screen));
  }
}

/// The live model/agent/variant selection, promoted to the top of More so the
/// hub reports state instead of only linking away.
class _ActiveSetupCard extends StatelessWidget {
  final ConnectionController controller;

  const _ActiveSetupCard({required this.controller});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final model = controller.selectedModel;
    final modelLabel = model == null
        ? 'No model selected'
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
          details.isEmpty ? 'Active model' : details.join(' · '),
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
        ),
        trailing: const Icon(Icons.swap_horiz_rounded),
        onTap: () => showModelPicker(context),
      ),
    );
  }
}

/// One rounded card per section, so More reads as grouped surfaces rather
/// than a continuous link list.
class _DestinationGroup extends StatelessWidget {
  final List<Widget> tiles;

  const _DestinationGroup({required this.tiles});

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.symmetric(horizontal: 4),
      clipBehavior: Clip.antiAlias,
      child: Column(
        children: [
          for (var i = 0; i < tiles.length; i++) ...[
            if (i > 0) const Divider(height: 1, indent: 64),
            tiles[i],
          ],
        ],
      ),
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
      width: 40,
      height: 40,
      decoration: BoxDecoration(
        color: tint.withValues(alpha: .12),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Icon(icon, size: 22, color: tint),
    );
  }
}

class _DestinationTile extends StatelessWidget {
  final IconData icon;
  final String title;
  final String subtitle;
  final String? badge;
  final VoidCallback onTap;

  const _DestinationTile({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.onTap,
    this.badge,
  });

  @override
  Widget build(BuildContext context) {
    return ListTile(
      minTileHeight: 68,
      leading: _TileIcon(icon: icon),
      title: Text(title),
      subtitle: Text(subtitle, maxLines: 2, overflow: TextOverflow.ellipsis),
      trailing: badge == null
          ? const Icon(Icons.chevron_right_rounded)
          : Badge(label: Text(badge!)),
      onTap: onTap,
    );
  }
}

