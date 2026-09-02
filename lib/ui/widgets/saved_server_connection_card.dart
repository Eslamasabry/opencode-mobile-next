import 'package:flutter/material.dart';

import '../app_theme.dart';
import 'connection_failure.dart';

/// The card the app opens on when a saved server is being reconnected, and
/// what it turns into when that fails. A failure names the likely cause, lists
/// what to check, and puts the one action most likely to fix it in the
/// primary slot. "Try again" stays, but it is not pretending to be a fix.
class SavedServerConnectionCard extends StatefulWidget {
  const SavedServerConnectionCard({
    super.key,
    required this.profileName,
    required this.baseUrl,
    required this.error,
    required this.attempts,
    required this.supportsTermux,
    required this.onChangeServer,
    required this.onRetry,
    this.onOpenTermuxSetup,
    this.onUpdatePassword,
  });

  final String profileName;
  final String baseUrl;

  /// Null while connecting; the raw error text once a connect attempt failed.
  final String? error;

  /// How many attempts have failed in a row, including this one.
  final int attempts;
  final bool supportsTermux;
  final VoidCallback onChangeServer;
  final VoidCallback onRetry;
  final VoidCallback? onOpenTermuxSetup;
  final VoidCallback? onUpdatePassword;

  @override
  State<SavedServerConnectionCard> createState() =>
      _SavedServerConnectionCardState();
}

class _SavedServerConnectionCardState extends State<SavedServerConnectionCard> {
  bool _detailsOpen = false;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final error = widget.error;
    final failure = error == null
        ? null
        : ConnectionFailure.diagnose(
            error: error,
            baseUrl: widget.baseUrl,
            supportsTermux: widget.supportsTermux,
            attempts: widget.attempts,
          );
    final failed = failure != null;

    return Semantics(
      container: true,
      liveRegion: true,
      label: failed
          ? '${failure.title}. ${failure.explanation}'
          : 'Connecting to ${widget.profileName}',
      child: Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 440),
            child: DecoratedBox(
              decoration: BoxDecoration(
                color: scheme.surfaceContainerLow,
                borderRadius: BorderRadius.circular(24),
                border: Border.all(color: AppTheme.hairline(theme)),
                boxShadow: AppTheme.raised(theme),
              ),
              child: Padding(
                padding: const EdgeInsets.fromLTRB(24, 26, 24, 24),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Align(
                      alignment: Alignment.centerLeft,
                      child: Container(
                        width: 48,
                        height: 48,
                        decoration: BoxDecoration(
                          color: failed
                              ? scheme.errorContainer
                              : scheme.primaryContainer,
                          borderRadius: BorderRadius.circular(14),
                        ),
                        child: Icon(
                          failed
                              ? Icons.cloud_off_outlined
                              : Icons.terminal_rounded,
                          color: failed
                              ? scheme.onErrorContainer
                              : scheme.onPrimaryContainer,
                        ),
                      ),
                    ),
                    const SizedBox(height: 24),
                    Text(
                      failed
                          ? failure.title
                          : widget.attempts > 1
                          ? 'Connecting again (attempt ${widget.attempts})'
                          : 'Connecting to ${widget.profileName}',
                      key: const ValueKey('saved-server-title'),
                      style: theme.textTheme.titleLarge,
                    ),
                    const SizedBox(height: 8),
                    Text(
                      failed
                          ? failure.explanation
                          : 'Opening your saved workspace.',
                      key: const ValueKey('saved-server-explanation'),
                      style: theme.textTheme.bodyMedium?.copyWith(
                        color: scheme.onSurfaceVariant,
                        height: 1.45,
                      ),
                    ),
                    const SizedBox(height: 18),
                    _AddressRow(baseUrl: widget.baseUrl),
                    if (!failed) ...[
                      const SizedBox(height: 22),
                      const LinearProgressIndicator(
                        key: ValueKey('saved-server-connect-progress'),
                        minHeight: 3,
                        borderRadius: BorderRadius.all(Radius.circular(2)),
                      ),
                    ] else ...[
                      const SizedBox(height: 18),
                      _Checks(checks: failure.checks),
                      const SizedBox(height: 6),
                      _DetailsExpander(
                        open: _detailsOpen,
                        onToggle: () =>
                            setState(() => _detailsOpen = !_detailsOpen),
                        rawError: failure.rawError,
                      ),
                      const SizedBox(height: 16),
                      _Actions(
                        failure: failure,
                        onChangeServer: widget.onChangeServer,
                        onRetry: widget.onRetry,
                        onOpenTermuxSetup: widget.onOpenTermuxSetup,
                        onUpdatePassword: widget.onUpdatePassword,
                      ),
                    ],
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _AddressRow extends StatelessWidget {
  const _AddressRow({required this.baseUrl});
  final String baseUrl;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        color: scheme.surfaceContainer,
        borderRadius: BorderRadius.circular(AppTheme.radiusControl),
      ),
      child: Row(
        children: [
          Icon(Icons.dns_outlined, size: 18, color: scheme.onSurfaceVariant),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              baseUrl,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: theme.textTheme.bodySmall?.copyWith(
                color: scheme.onSurfaceVariant,
                fontFamily: AppTheme.monoFamily,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _Checks extends StatelessWidget {
  const _Checks({required this.checks});
  final List<String> checks;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    return Column(
      key: const ValueKey('saved-server-checks'),
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          'What to check',
          style: theme.textTheme.labelLarge?.copyWith(
            color: scheme.onSurfaceVariant,
          ),
        ),
        const SizedBox(height: 6),
        for (final check in checks)
          Padding(
            padding: const EdgeInsets.only(bottom: 6),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Padding(
                  padding: const EdgeInsets.only(top: 7, right: 10),
                  child: Container(
                    width: 6,
                    height: 6,
                    decoration: BoxDecoration(
                      color: scheme.primary,
                      shape: BoxShape.circle,
                    ),
                  ),
                ),
                Expanded(
                  child: Text(
                    check,
                    style: theme.textTheme.bodyMedium?.copyWith(height: 1.4),
                  ),
                ),
              ],
            ),
          ),
      ],
    );
  }
}

class _DetailsExpander extends StatelessWidget {
  const _DetailsExpander({
    required this.open,
    required this.onToggle,
    required this.rawError,
  });
  final bool open;
  final VoidCallback onToggle;
  final String rawError;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Align(
          alignment: Alignment.centerLeft,
          child: TextButton.icon(
            key: const ValueKey('saved-server-details'),
            onPressed: onToggle,
            icon: Icon(
              open ? Icons.expand_less_rounded : Icons.expand_more_rounded,
              size: 18,
            ),
            label: Text(open ? 'Hide details' : 'Details'),
          ),
        ),
        if (open)
          Container(
            key: const ValueKey('saved-server-raw-error'),
            width: double.infinity,
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: scheme.surfaceContainer,
              borderRadius: BorderRadius.circular(AppTheme.radiusControl),
            ),
            child: SelectableText(
              rawError,
              style: theme.textTheme.bodySmall?.copyWith(
                fontFamily: AppTheme.monoFamily,
                color: scheme.onSurfaceVariant,
              ),
            ),
          ),
      ],
    );
  }
}

class _Actions extends StatelessWidget {
  const _Actions({
    required this.failure,
    required this.onChangeServer,
    required this.onRetry,
    required this.onOpenTermuxSetup,
    required this.onUpdatePassword,
  });
  final ConnectionFailure failure;
  final VoidCallback onChangeServer;
  final VoidCallback onRetry;
  final VoidCallback? onOpenTermuxSetup;
  final VoidCallback? onUpdatePassword;

  @override
  Widget build(BuildContext context) {
    final retry = OutlinedButton.icon(
      key: const ValueKey('saved-server-retry'),
      onPressed: onRetry,
      icon: const Icon(Icons.refresh_rounded, size: 19),
      label: const Text('Try again'),
    );
    final change = TextButton(
      key: const ValueKey('saved-server-change'),
      onPressed: onChangeServer,
      child: const Text('Change server'),
    );
    final Widget primary = switch (failure.primary) {
      ConnectionFailureAction.openTermuxSetup when onOpenTermuxSetup != null =>
        FilledButton.icon(
          key: const ValueKey('saved-server-open-termux'),
          onPressed: onOpenTermuxSetup,
          icon: const Icon(Icons.smartphone_rounded, size: 19),
          label: const Text('Check Termux'),
        ),
      ConnectionFailureAction.updatePassword when onUpdatePassword != null =>
        FilledButton.icon(
          key: const ValueKey('saved-server-update-password'),
          onPressed: onUpdatePassword,
          icon: const Icon(Icons.key_rounded, size: 19),
          label: const Text('Update password'),
        ),
      ConnectionFailureAction.changeServer => FilledButton.icon(
        key: const ValueKey('saved-server-change-primary'),
        onPressed: onChangeServer,
        icon: const Icon(Icons.swap_horiz_rounded, size: 19),
        label: const Text('Change server'),
      ),
      _ => FilledButton.icon(
        key: const ValueKey('saved-server-retry-primary'),
        onPressed: onRetry,
        icon: const Icon(Icons.refresh_rounded, size: 19),
        label: const Text('Try again'),
      ),
    };
    final primaryIsRetry =
        failure.primary == ConnectionFailureAction.retry ||
        (failure.primary == ConnectionFailureAction.openTermuxSetup &&
            onOpenTermuxSetup == null) ||
        (failure.primary == ConnectionFailureAction.updatePassword &&
            onUpdatePassword == null);
    final primaryIsChange =
        failure.primary == ConnectionFailureAction.changeServer;
    final stacked = AppTheme.stackedActions(context);
    final buttons = <Widget>[
      if (!primaryIsChange) change,
      if (!primaryIsRetry) retry,
      primary,
    ];
    if (stacked) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          for (final b in buttons) ...[b, const SizedBox(height: 8)],
        ],
      );
    }
    return Wrap(
      alignment: WrapAlignment.end,
      spacing: 10,
      runSpacing: 10,
      children: buttons,
    );
  }
}
