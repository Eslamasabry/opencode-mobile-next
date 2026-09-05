import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import '../../state/connection.dart';

/// Shared continuation controls, including pages with no visible root chats.
class SessionInventoryFooter extends StatelessWidget {
  const SessionInventoryFooter({super.key, required this.controller});

  final ConnectionController controller;

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: controller,
    builder: (context, _) {
      final l10n = lookupAppLocalizations(Localizations.localeOf(context));
      final error = controller.sessionsError ?? controller.sessionsMoreError;
      final loading =
          controller.sessionsLoading || controller.sessionsLoadingMore;
      if (!controller.hasMoreSessions && error == null && !loading) {
        return const SizedBox.shrink();
      }
      return Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              error ?? l10n.sessionsLoadedOnly,
              style: error == null
                  ? null
                  : TextStyle(color: Theme.of(context).colorScheme.error),
            ),
            const SizedBox(height: 6),
            if (loading) const LinearProgressIndicator(minHeight: 2),
            TextButton(
              key: const ValueKey('session-inventory-more'),
              onPressed: loading
                  ? null
                  : controller.sessionsError != null
                  ? controller.refreshSessions
                  : controller.loadMoreSessions,
              child: Text(
                controller.sessionsNeedReload
                    ? l10n.sessionsReload
                    : error != null
                    ? l10n.refreshRetry
                    : l10n.sessionsLoadMore,
              ),
            ),
          ],
        ),
      );
    },
  );
}
