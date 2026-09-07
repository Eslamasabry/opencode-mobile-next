import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../domain/completion_digest.dart';
import '../../l10n/app_localizations.dart';

/// Deliberately formats only allowlisted counts and app-authored copy.
class CompletionDigestCard extends StatelessWidget {
  const CompletionDigestCard({
    super.key,
    required this.digest,
    required this.onOpenConversation,
    required this.onReview,
    required this.onDismiss,
    this.onRunResults,
  });

  final CompletionDigest digest;
  final VoidCallback onOpenConversation;
  final VoidCallback onReview;
  final VoidCallback onDismiss;

  /// Opens the server-recorded outcome and tool evidence of the latest run.
  /// Null hides the action.
  final VoidCallback? onRunResults;

  String _changedFilesText(AppLocalizations l10n) => digest.changedFiles == null
      ? l10n.digestChangedFilesUnknown
      : l10n.digestChangedFiles(digest.changedFiles!);

  String _pendingDecisionsText(AppLocalizations l10n) =>
      digest.pendingDecisions == null
      ? l10n.digestPendingDecisionsUnknown
      : l10n.digestPendingDecisions(digest.pendingDecisions!);

  String _sanitizedSummary(AppLocalizations l10n) => [
    l10n.digestStatusUnverified,
    _changedFilesText(l10n),
    _pendingDecisionsText(l10n),
    l10n.digestOutcomesUnknown,
    l10n.digestProvenance,
  ].join('\n');

  @override
  Widget build(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      child: Column(
        key: const Key('completion-digest-card'),
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            l10n.digestStatusUnverified,
            style: Theme.of(context).textTheme.titleSmall,
          ),
          const SizedBox(height: 8),
          Text(_changedFilesText(l10n)),
          Text(_pendingDecisionsText(l10n)),
          Text(l10n.digestOutcomesUnknown),
          const SizedBox(height: 8),
          Text(
            l10n.digestProvenance,
            style: Theme.of(context).textTheme.bodySmall,
          ),
          Wrap(
            spacing: 8,
            runSpacing: 4,
            children: [
              TextButton(
                onPressed: onOpenConversation,
                child: Text(l10n.digestOpenConversation),
              ),
              TextButton(onPressed: onReview, child: Text(l10n.digestReview)),
              if (onRunResults != null)
                TextButton.icon(
                  key: const Key('completion-digest-run-results'),
                  onPressed: onRunResults,
                  icon: const Icon(Icons.fact_check_outlined),
                  label: Text(l10n.digestRunResults),
                ),
              TextButton.icon(
                key: const Key('completion-digest-copy'),
                onPressed: () async {
                  try {
                    await Clipboard.setData(
                      ClipboardData(text: _sanitizedSummary(l10n)),
                    );
                    if (!context.mounted) return;
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(content: Text(l10n.digestCopySucceeded)),
                    );
                  } catch (_) {
                    if (!context.mounted) return;
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(content: Text(l10n.digestCopyFailed)),
                    );
                  }
                },
                icon: const Icon(Icons.copy_outlined),
                label: Text(l10n.digestCopy),
              ),
              TextButton(onPressed: onDismiss, child: Text(l10n.digestDismiss)),
            ],
          ),
        ],
      ),
    );
  }
}
