import 'package:flutter/material.dart';

import '../../domain/attention_item.dart';
import '../../state/attention_overview.dart';
import '../../state/connection.dart';
import '../widgets/product_states.dart';
import 'profile_monitor_screen.dart';
import '../../l10n/app_localizations.dart';
import '../app_iconography.dart';

/// Local overview only. The host owns navigation and any profile-switch guard.
class AttentionOverviewScreen extends StatelessWidget {
  const AttentionOverviewScreen({
    super.key,
    required this.controller,
    this.onOpenProfile,
  });

  final ConnectionController controller;

  /// Called only after an explicit tap, with a still-readable saved profile ID.
  /// The host must revalidate the ID, use its existing safe switch flow (including
  /// any leave-active-work confirmation), and then open that profile's Activity.
  /// This screen never switches profiles or dispatches an approval itself.
  final void Function(String profileID)? onOpenProfile;

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: Text(
        lookupAppLocalizations(Localizations.localeOf(context)).attentionTitle,
      ),
      actions: [
        IconButton(
          icon: const Icon(AppIconography.settings),
          tooltip: lookupAppLocalizations(
            Localizations.localeOf(context),
          ).monitorConfigure,
          onPressed: () => Navigator.of(context).push(
            MaterialPageRoute<void>(
              builder: (_) => ProfileMonitorScreen(controller: controller),
            ),
          ),
        ),
      ],
    ),
    body: SafeArea(
      child: AnimatedBuilder(
        animation: Listenable.merge([
          controller,
          controller.profileDataChanges,
        ]),
        builder: (context, _) {
          final overview = AttentionOverview.fromController(controller);
          if (overview.items.isEmpty) {
            return const ProductEmptyState(
              icon: AppIconography.server,
              title: 'No saved servers',
              message: 'Add a server from Home to see it here.',
            );
          }
          return ListView.builder(
            padding: const EdgeInsets.all(16),
            itemCount: overview.items.length + 1,
            itemBuilder: (context, index) {
              if (index == 0) {
                return Padding(
                  padding: const EdgeInsets.only(bottom: 16),
                  child: Text(
                    lookupAppLocalizations(
                      Localizations.localeOf(context),
                    ).attentionDisclosure,
                  ),
                );
              }
              return _profileCard(context, overview.items[index - 1]);
            },
          );
        },
      ),
    ),
  );

  Widget _profileCard(BuildContext context, AttentionItem item) {
    final theme = Theme.of(context);
    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              item.profileName.trim().isEmpty
                  ? 'Saved server'
                  : item.profileName,
              style: theme.textTheme.titleMedium,
            ),
            const SizedBox(height: 8),
            Text(item.isSelected ? 'Selected server' : 'Inactive server'),
            const SizedBox(height: 8),
            Text(
              item.hasConnectionCache
                  ? 'Source: selected connection’s local cache. '
                        'Scope: currently loaded location and sessions. '
                        'Last refreshed: unknown.'
                  : 'Source: saved profile only. '
                        'Attention status: unknown. Last checked: unknown.',
              style: theme.textTheme.bodySmall,
            ),
            const SizedBox(height: 12),
            Text(
              item.pendingRequests == null
                  ? 'Pending requests: unknown'
                  : 'Last-known pending requests: ${item.pendingRequests}',
            ),
            const SizedBox(height: 4),
            Text(
              item.runningSessions == null
                  ? 'Running sessions: unknown'
                  : 'Last-known running or retrying sessions: '
                        '${item.runningSessions}',
            ),
            const SizedBox(height: 4),
            Text(
              item.unreadSessions == null
                  ? 'Unread sessions: unknown'
                  : 'Last-known unread sessions: ${item.unreadSessions}',
            ),
            const SizedBox(height: 12),
            if (onOpenProfile == null) ...[
              Text(
                lookupAppLocalizations(
                  Localizations.localeOf(context),
                ).attentionNavigationUnavailable,
              ),
              const SizedBox(height: 8),
            ],
            OutlinedButton(
              style: OutlinedButton.styleFrom(
                minimumSize: const Size(48, 48),
                tapTargetSize: MaterialTapTargetSize.padded,
              ),
              onPressed: onOpenProfile == null
                  ? null
                  : () {
                      if (controller.isProfileReadable(item.profileID) &&
                          controller.store.profiles.any(
                            (profile) => profile.id == item.profileID,
                          )) {
                        onOpenProfile!(item.profileID);
                      }
                    },
              child: Text(item.isSelected ? 'Open server' : 'Choose server…'),
            ),
          ],
        ),
      ),
    );
  }
}
