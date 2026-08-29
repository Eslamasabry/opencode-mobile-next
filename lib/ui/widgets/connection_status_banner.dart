import 'dart:async';

import 'package:flutter/material.dart';

import '../../api/sse.dart';
import '../../state/connection.dart';

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
    if (controller.status == StreamStatus.connected) {
      return const SizedBox.shrink();
    }

    final manualRetry = controller.manualReconnectInProgress;
    final reconnecting = controller.connectionLoading || manualRetry;
    final error = controller.connectionError?.trim();
    final message = reconnecting
        ? 'Reconnecting. Displayed data stays available while OpenCode is checked.'
        : error != null && error.isNotEmpty
        ? 'Could not reconnect: $error Displayed data may be stale until OpenCode is available.'
        : 'Live updates are offline. Displayed data may be stale until OpenCode reconnects.';

    return Semantics(
      container: true,
      liveRegion: true,
      label: reconnecting ? 'Reconnecting to OpenCode' : 'OpenCode is offline',
      child: MaterialBanner(
        key: const ValueKey('connection-status-banner'),
        leading: reconnecting
            ? const SizedBox.square(
                dimension: 20,
                child: CircularProgressIndicator(strokeWidth: 2),
              )
            : const Icon(Icons.cloud_off_outlined),
        content: Text(
          note == null || note!.isEmpty ? message : '$message\n${note!}',
        ),
        actions: [
          TextButton(
            onPressed: manualRetry
                ? null
                : () => unawaited(controller.retryConnection()),
            child: Text(manualRetry ? 'Retrying' : 'Try again'),
          ),
          if (showChangeServer && !manualRetry)
            TextButton(
              onPressed: () => Navigator.of(context).pushNamed('/servers'),
              child: const Text('Change server'),
            ),
        ],
      ),
    );
  }
}
