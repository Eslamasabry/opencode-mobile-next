import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../api/models.dart' show ModelRef;
import '../../api/mcp_oauth.dart';
import '../../l10n/app_localizations.dart';
import '../../api/provider_presentation.dart';
import '../../api/product_repository.dart';
import '../../state/connection.dart';
import '../app_theme.dart';
import '../widgets/entrance.dart';
import '../widgets/file_preview.dart';
import '../widgets/confirm_sheet.dart';
import '../widgets/product_states.dart';
import '../widgets/pickers.dart';
import 'capabilities_screen.dart';
import 'guide_screen.dart';
import 'mcp_setup_screen.dart';
import 'mission_control_screen.dart';
import 'requests_screen.dart';
import 'settings_screen.dart';

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
        final l10n = AppLocalizations.of(context);
        final pending =
            controller.permissions.length +
            controller.questions.length +
            controller.forms.length;
        return ListView(
          padding: const EdgeInsets.fromLTRB(12, 8, 12, 24),
          children: [
            _ActiveSetupCard(controller: controller),
            SectionLabel(l10n.libraryBrowseSection),
            _DestinationGrid(
              cards: [
                _DestinationCard(
                  key: const ValueKey('library-mission-control'),
                  icon: Icons.space_dashboard_outlined,
                  title: l10n.libraryMissionControlTitle,
                  badge: pending == 0 ? null : '$pending',
                  onTap: () => _open(
                    context,
                    MissionControlScreen(controller: controller),
                  ),
                ),
                _DestinationCard(
                  icon: Icons.model_training_outlined,
                  title: l10n.libraryModelsAgentsTitle,
                  onTap: () =>
                      _open(context, CatalogScreen(controller: controller)),
                ),
                _DestinationCard(
                  icon: Icons.cloud_outlined,
                  title: l10n.libraryProvidersTitle,
                  onTap: () => _open(
                    context,
                    IntegrationsScreen(
                      controller: controller,
                      mode: IntegrationsMode.providers,
                    ),
                  ),
                ),
                _DestinationCard(
                  icon: Icons.hub_outlined,
                  title: l10n.libraryMcpTitle,
                  onTap: () => _open(
                    context,
                    IntegrationsScreen(
                      controller: controller,
                      mode: IntegrationsMode.mcp,
                    ),
                  ),
                ),
                _DestinationCard(
                  icon: Icons.build_circle_outlined,
                  title: l10n.libraryCommandsToolsTitle,
                  onTap: () => _open(
                    context,
                    CapabilitiesScreen(controller: controller),
                  ),
                ),
              ],
            ),
            SectionLabel(l10n.libraryManageSection),
            _DestinationGrid(
              cards: [
                _DestinationCard(
                  icon: Icons.notifications_active_outlined,
                  title: l10n.libraryRequestsTitle,
                  badge: pending == 0 ? null : '$pending',
                  onTap: () =>
                      _open(context, RequestsScreen(controller: controller)),
                ),
                _DestinationCard(
                  icon: Icons.settings_outlined,
                  title: l10n.librarySettingsTitle,
                  onTap: () =>
                      _open(context, SettingsScreen(controller: controller)),
                ),
                _DestinationCard(
                  icon: Icons.menu_book_outlined,
                  title: 'Setup guide',
                  onTap: () => _open(context, const GuideScreen()),
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

/// A visual, icon-forward grid of destinations; columns and card height
/// adapt to width and text scale so accessibility settings reflow instead
/// of overflowing.
class _DestinationGrid extends StatelessWidget {
  final List<Widget> cards;

  const _DestinationGrid({required this.cards});

  @override
  Widget build(BuildContext context) {
    // Tile height tracks the text scale all the way to the app's ceiling;
    // stopping at 2.0 would clip the labels of users above it.
    final scale = MediaQuery.textScalerOf(
      context,
    ).scale(1).clamp(1.0, AppTheme.maxTextScale);
    return LayoutBuilder(
      builder: (context, constraints) {
        final columns = constraints.maxWidth >= 280 ? 2 : 1;
        return GridView(
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          padding: const EdgeInsets.symmetric(horizontal: 4),
          gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
            crossAxisCount: columns,
            mainAxisExtent: 78 + 40 * scale,
            crossAxisSpacing: 10,
            mainAxisSpacing: 10,
          ),
          children: [
            for (var index = 0; index < cards.length; index++)
              EntranceReveal(index: index, child: cards[index]),
          ],
        );
      },
    );
  }
}

class _DestinationCard extends StatelessWidget {
  final IconData icon;
  final String title;
  final String? badge;
  final VoidCallback onTap;

  const _DestinationCard({
    super.key,
    required this.icon,
    required this.title,
    required this.onTap,
    this.badge,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      margin: EdgeInsets.zero,
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(14, 12, 12, 12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  _TileIcon(icon: icon),
                  const Spacer(),
                  if (badge != null) Badge(label: Text(badge!)),
                ],
              ),
              const Spacer(),
              Text(
                title,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: theme.textTheme.titleSmall,
              ),
            ],
          ),
        ),
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
