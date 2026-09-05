import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart' show PlatformException;

import '../../api/mcp_oauth.dart' show McpOAuthCallbackException;
import '../../api/opencode_api.dart';
import '../../api/product_repository.dart';
import '../../feedback/bug_report.dart';
import '../../l10n/app_localizations.dart';
import '../../state/profiles.dart' show SecureStorageUnavailable;
import '../app_theme.dart';

/// Maps any thrown object onto copy that is safe to show users.
///
/// - [ProductException], [ApiException], and [McpOAuthCallbackException]
///   carry product-facing messages and pass through unchanged.
/// - [SecureStorageUnavailable] names the missing keyring; any other
///   [PlatformException] is a device-side failure, so its own message is
///   shown rather than blaming the server.
/// - A [String] is treated as already-composed product copy.
/// - Everything else — [StateError]s, socket/transport failures, and other
///   internals — collapses to one generic connectivity line instead of leaking
///   `Bad state:` prefixes or raw exception dumps.
String productErrorText(Object error) {
  if (error is ProductException) return error.message;
  if (error is ApiException) return error.message;
  if (error is McpOAuthCallbackException) return error.message;
  if (error is SecureStorageUnavailable) return error.message;
  if (error is PlatformException) {
    final message = error.message?.trim();
    if (message != null && message.isNotEmpty) return message;
    return 'This device reported an error (${error.code}).';
  }
  if (error is String && error.trim().isNotEmpty) return error;
  return 'OpenCode is unreachable. Try again.';
}

/// The one styled error snackbar for mutation failures: error-red background,
/// replaces any snackbar currently showing, and routes the thrown object
/// through [productErrorText] so raw exceptions never reach users.
void showProductError(BuildContext context, Object error) {
  ScaffoldMessenger.of(context)
    ..hideCurrentSnackBar()
    ..showSnackBar(
      SnackBar(
        content: Text(productErrorText(error)),
        backgroundColor: Theme.of(context).colorScheme.error,
      ),
    );
}

/// Keeps cached content mounted while a refresh failure offers a retry.
class ProductRefreshBody extends StatelessWidget {
  const ProductRefreshBody({
    super.key,
    required this.message,
    required this.onRetry,
    required this.child,
  });

  final String? message;
  final VoidCallback onRetry;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final l10n =
        Localizations.of<AppLocalizations>(context, AppLocalizations) ??
        lookupAppLocalizations(Localizations.localeOf(context));
    return Column(
      children: [
        if (message != null)
          MaterialBanner(
            content: Text('${l10n.refreshFailed}\n$message'),
            actions: [
              TextButton(onPressed: onRetry, child: Text(l10n.refreshRetry)),
            ],
          ),
        Expanded(key: const ValueKey('refresh-content'), child: child),
      ],
    );
  }
}

class LoadingList extends StatelessWidget {
  final int rows;
  const LoadingList({super.key, this.rows = 5});

  @override
  Widget build(BuildContext context) {
    final color = Theme.of(context).colorScheme.surfaceContainerHighest;
    return ListView.separated(
      physics: const AlwaysScrollableScrollPhysics(),
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      itemCount: rows,
      separatorBuilder: (_, _) => const SizedBox(height: 14),
      itemBuilder: (_, index) => Row(
        children: [
          Container(
            width: 36,
            height: 36,
            decoration: BoxDecoration(
              color: color,
              borderRadius: BorderRadius.circular(8),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                FractionallySizedBox(
                  widthFactor: index.isEven ? .62 : .45,
                  child: Container(height: 12, color: color),
                ),
                const SizedBox(height: 8),
                FractionallySizedBox(
                  widthFactor: index.isEven ? .38 : .7,
                  child: Container(height: 8, color: color),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class ProductEmptyState extends StatelessWidget {
  final IconData icon;
  final String title;
  final String message;
  final String? actionLabel;
  final VoidCallback? onAction;

  const ProductEmptyState({
    super.key,
    required this.icon,
    required this.title,
    required this.message,
    this.actionLabel,
    this.onAction,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return LayoutBuilder(
      builder: (context, constraints) => SingleChildScrollView(
        physics: const AlwaysScrollableScrollPhysics(),
        child: ConstrainedBox(
          constraints: BoxConstraints(
            minHeight: constraints.hasBoundedHeight ? constraints.maxHeight : 0,
          ),
          child: Center(
            child: Padding(
              padding: const EdgeInsets.all(32),
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 360),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(icon, size: 38, color: AppTheme.mutedOf(theme)),
                    const SizedBox(height: 14),
                    Text(title, style: theme.textTheme.titleMedium),
                    const SizedBox(height: 6),
                    Text(
                      message,
                      textAlign: TextAlign.center,
                      style: theme.textTheme.bodySmall?.copyWith(
                        color: AppTheme.mutedOf(theme),
                      ),
                    ),
                    if (actionLabel != null && onAction != null) ...[
                      const SizedBox(height: 18),
                      FilledButton.tonal(
                        onPressed: onAction,
                        child: Text(actionLabel!),
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

class ProductErrorState extends StatelessWidget {
  final String message;
  final Future<void> Function() onRetry;

  const ProductErrorState({
    super.key,
    required this.message,
    required this.onRetry,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return LayoutBuilder(
      builder: (context, constraints) => SingleChildScrollView(
        physics: const AlwaysScrollableScrollPhysics(),
        child: ConstrainedBox(
          constraints: BoxConstraints(
            minHeight: constraints.hasBoundedHeight ? constraints.maxHeight : 0,
          ),
          child: Center(
            child: Padding(
              padding: const EdgeInsets.all(28),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(
                    Icons.error_outline_rounded,
                    color: theme.colorScheme.error,
                  ),
                  const SizedBox(height: 10),
                  Text(message, textAlign: TextAlign.center),
                  const SizedBox(height: 14),
                  FilledButton.tonal(
                    onPressed: onRetry,
                    child: const Text('Try again'),
                  ),
                  // The failure surface is where a bug is actually
                  // discovered, so the report affordance lives here too —
                  // one implementation covering every screen that uses this
                  // state, without adding chrome to any app bar.
                  TextButton.icon(
                    key: const ValueKey('product-error-report-bug'),
                    onPressed: () => unawaited(openBugReport(context)),
                    icon: const Icon(Icons.bug_report_outlined, size: 18),
                    label: const Text('Report a bug'),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// A compact, intentionally designed empty state for one section of a longer
/// scrolling surface, where the full-screen [ProductEmptyState] is too tall
/// and a bare [ListTile] reads as a broken list row.
class ProductInlineEmpty extends StatelessWidget {
  final IconData icon;
  final String title;
  final String message;
  final String? actionLabel;
  final VoidCallback? onAction;

  const ProductInlineEmpty({
    super.key,
    required this.icon,
    required this.title,
    required this.message,
    this.actionLabel,
    this.onAction,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.fromLTRB(16, 18, 16, 14),
        decoration: BoxDecoration(
          color: theme.colorScheme.surfaceContainerLow.withValues(alpha: .6),
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: AppTheme.hairline(theme)),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: 22, color: AppTheme.mutedOf(theme)),
            const SizedBox(height: 8),
            Text(
              title,
              textAlign: TextAlign.center,
              style: theme.textTheme.bodyMedium?.copyWith(
                fontWeight: FontWeight.w600,
              ),
            ),
            const SizedBox(height: 3),
            Text(
              message,
              textAlign: TextAlign.center,
              style: theme.textTheme.bodySmall?.copyWith(
                color: AppTheme.mutedOf(theme),
              ),
            ),
            if (actionLabel != null && onAction != null) ...[
              const SizedBox(height: 6),
              TextButton(onPressed: onAction, child: Text(actionLabel!)),
            ],
          ],
        ),
      ),
    );
  }
}

/// The one explainer row for a feature the connected server cannot do.
///
/// Settings are where users go looking for a thing they remember, so a
/// vanished row reads as a bug (`docs/opencode2-ui-design.md` §7, rule 2).
/// This renders the row it replaces at 55% opacity with `enabled: false` and
/// the [explainer] swapped in for the subtitle; tapping says which server
/// generation the feature needs. Capability gating, not plan gating — no
/// upsell styling, no call to action.
///
/// Menu actions, nav destinations and More-grid tiles are *hidden* instead;
/// this widget is only for surviving settings/health surfaces.
class GatedRow extends StatelessWidget {
  /// Feature id; the row's key is `gated-<feature>`.
  final String feature;

  /// The title the enabled row would have carried.
  final String title;

  /// One honest line saying why the row is dead, e.g.
  /// "Not available on OpenCode 2 servers".
  final String explainer;

  final Widget? leading;

  /// Server generation named in the tap snackbar. 1 for v1-only features
  /// (the usual case), 2 for the rare v2-only row we choose to show.
  final int requiresGeneration;

  const GatedRow({
    super.key,
    required this.feature,
    required this.title,
    required this.explainer,
    this.leading,
    this.requiresGeneration = 1,
  });

  @override
  Widget build(BuildContext context) {
    return Opacity(
      opacity: .55,
      child: ListTile(
        key: ValueKey('gated-$feature'),
        enabled: false,
        leading: leading,
        title: Text(title),
        subtitle: Text(explainer),
        // `enabled: false` swallows ListTile.onTap, so the explanation tap
        // target lives outside it.
        onTap: null,
      ),
    );
  }
}

/// [GatedRow] plus the tap-to-explain snackbar. Split out so the row itself
/// stays a pure `ListTile` for callers that embed it in their own gesture
/// handling.
class GatedRowTile extends StatelessWidget {
  final String feature;
  final String title;
  final String explainer;
  final Widget? leading;
  final int requiresGeneration;

  const GatedRowTile({
    super.key,
    required this.feature,
    required this.title,
    required this.explainer,
    this.leading,
    this.requiresGeneration = 1,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: () {
        ScaffoldMessenger.of(context)
          ..hideCurrentSnackBar()
          ..showSnackBar(
            SnackBar(
              content: Text('Requires an OpenCode $requiresGeneration server'),
            ),
          );
      },
      child: GatedRow(
        feature: feature,
        title: title,
        explainer: explainer,
        leading: leading,
        requiresGeneration: requiresGeneration,
      ),
    );
  }
}

/// The stock explainer for a v1 feature with no OpenCode 2 endpoint.
const String gatedOnV2Explainer = 'Not available on OpenCode 2 servers';

class SectionLabel extends StatelessWidget {
  final String text;
  final Widget? trailing;

  /// Overrides the list-level inset for surfaces that already pad their own
  /// content — sheets and cards — so those can reuse this label instead of
  /// hand-rolling the same uppercase caption.
  final EdgeInsetsGeometry? padding;

  const SectionLabel(this.text, {super.key, this.trailing, this.padding});

  /// The same label with no inset of its own, for already-padded contexts.
  const SectionLabel.inline(this.text, {super.key, this.trailing})
    : padding = const EdgeInsets.only(bottom: 8);

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: padding ?? const EdgeInsets.fromLTRB(16, 18, 12, 6),
      child: Row(
        children: [
          Expanded(
            child: Text(
              text.toUpperCase(),
              style: theme.textTheme.labelSmall?.copyWith(
                color: AppTheme.mutedOf(theme),
                letterSpacing: 1.1,
              ),
            ),
          ),
          ?trailing,
        ],
      ),
    );
  }
}
