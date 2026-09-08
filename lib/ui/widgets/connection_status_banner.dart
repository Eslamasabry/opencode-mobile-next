import 'dart:async';

import 'package:flutter/material.dart';

import '../../api/sse.dart';
import '../../state/connection.dart';
import '../../l10n/app_localizations.dart';
import '../app_iconography.dart';

/// A shared, flat connection state for retained product surfaces.
class ConnectionStatusBanner extends StatelessWidget {
  const ConnectionStatusBanner({
    super.key,
    required this.controller,
    this.showChangeServer = true,
    this.note,
  });

  final ConnectionController controller;
  final bool showChangeServer;

  /// One optional extra line, e.g. how many drafts are queued for delivery.
  final String? note;

  @override
  Widget build(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    if (controller.status == StreamStatus.connected) {
      return const SizedBox.shrink();
    }

    // A rejected Codex token cannot self-heal through retries: surface the
    // one action that fixes it and keep it a banner, never a modal.
    if (controller.passwordRejected && controller.usesConnectionToken) {
      return Semantics(
        container: true,
        liveRegion: true,
        label: 'The connection token was rejected',
        child: MaterialBanner(
          key: const ValueKey('connection-status-banner'),
          leading: const Icon(Icons.key_off_outlined),
          content: Text(l10n.connectionTokenRejected),
          actions: [
            TextButton(
              key: const ValueKey('banner-update-token'),
              onPressed: () => Navigator.of(
                context,
              ).pushNamed('/servers', arguments: 'edit-active'),
              child: Text(l10n.updateConnectionToken),
            ),
          ],
        ),
      );
    }

    // A rotated v2 serve password cannot self-heal through retries: surface
    // the one action that fixes it and keep it a banner, never a modal.
    if (controller.passwordRejected) {
      return Semantics(
        container: true,
        liveRegion: true,
        label: 'The server password changed',
        child: MaterialBanner(
          key: const ValueKey('connection-status-banner'),
          leading: const Icon(Icons.key_off_outlined),
          content: Text(
            note == null || note!.isEmpty
                ? 'Server password changed — reconnect.'
                : 'Server password changed — reconnect.\n${note!}',
          ),
          actions: [
            TextButton(
              key: const ValueKey('banner-update-password'),
              onPressed: () => Navigator.of(
                context,
              ).pushNamed('/servers', arguments: 'edit-active'),
              child: const Text('Update password'),
            ),
          ],
        ),
      );
    }

    final manualRetry = controller.manualReconnectInProgress;
    final reconnecting = controller.connectionLoading || manualRetry;
    final error = controller.connectionError?.trim();
    final server = controller.profile?.name ?? 'OpenCode';
    // One line. The raw error and the secondary action live behind Details,
    // so the banner never grows into a paragraph over the content it sits on.
    final message = reconnecting
        ? 'Reconnecting to $server…'
        : 'Connection lost';
    final codexReconnect = controller.usesConnectionToken && reconnecting;
    final content = codexReconnect
        ? '$message\nReview draft stays here; nothing is sent automatically.${note == null || note!.isEmpty ? '' : '\n${note!}'}'
        : note == null || note!.isEmpty
        ? message
        : '$message\n${note!}';

    return Semantics(
      container: true,
      liveRegion: true,
      label: reconnecting ? 'Reconnecting to $server' : 'Connection lost',
      child: MaterialBanner(
        key: const ValueKey('connection-status-banner'),
        leading: reconnecting
            ? const SizedBox.square(
                dimension: 20,
                child: CircularProgressIndicator(strokeWidth: 2),
              )
            : const Icon(AppIconography.cloudOff),
        content: Text(
          content,
          maxLines: codexReconnect && note != null && note!.isNotEmpty ? 3 : 2,
          overflow: TextOverflow.ellipsis,
        ),
        actions: [
          TextButton(
            onPressed: manualRetry
                ? null
                : () => unawaited(controller.retryConnection()),
            child: Text(manualRetry ? 'Retrying' : 'Try again'),
          ),
          TextButton(
            key: const ValueKey('connection-banner-details'),
            onPressed: () => _showDetails(
              context,
              reconnecting: reconnecting,
              manualRetry: manualRetry,
              error: error,
            ),
            child: const Text('Details'),
          ),
        ],
      ),
    );
  }

  Future<void> _showDetails(
    BuildContext context, {
    required bool reconnecting,
    required bool manualRetry,
    required String? error,
  }) {
    return showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder: (sheetContext) {
        final theme = Theme.of(sheetContext);
        return SafeArea(
          child: SingleChildScrollView(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(20, 4, 20, 16),
              child: Column(
                key: const ValueKey('connection-banner-details-sheet'),
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Text(
                    reconnecting ? 'Reconnecting' : 'Connection lost',
                    style: theme.textTheme.titleLarge,
                  ),
                  const SizedBox(height: 8),
                  Text(
                    reconnecting
                        ? 'What you see stays available while OpenCode is '
                              'checked. Live updates resume on their own.'
                        : 'What you see may be stale until OpenCode is '
                              'reachable again.',
                    style: theme.textTheme.bodyMedium?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                      height: 1.35,
                    ),
                  ),
                  if (error != null && error.isNotEmpty) ...[
                    const SizedBox(height: 12),
                    Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: theme.colorScheme.surfaceContainerHighest,
                        borderRadius: BorderRadius.circular(10),
                      ),
                      child: SelectableText(
                        error,
                        style: theme.textTheme.bodySmall?.copyWith(
                          fontFamily: 'monospace',
                          height: 1.35,
                        ),
                      ),
                    ),
                  ],
                  const SizedBox(height: 16),
                  FilledButton(
                    onPressed: manualRetry
                        ? null
                        : () {
                            Navigator.of(sheetContext).pop();
                            unawaited(controller.retryConnection());
                          },
                    child: Text(manualRetry ? 'Retrying' : 'Try again'),
                  ),
                  if (showChangeServer && !manualRetry) ...[
                    const SizedBox(height: 8),
                    TextButton(
                      onPressed: () {
                        Navigator.of(sheetContext).pop();
                        Navigator.of(context).pushNamed('/servers');
                      },
                      child: const Text('Change server'),
                    ),
                  ],
                ],
              ),
            ),
          ),
        );
      },
    );
  }
}
