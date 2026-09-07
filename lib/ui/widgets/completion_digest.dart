import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../domain/completion_digest.dart';
import '../../l10n/app_localizations.dart';

/// Deliberately formats only allowlisted counts and app-authored copy.
/// Localization is handed off to the integration owner.
class CompletionDigestCard extends StatelessWidget {
  const CompletionDigestCard({
    super.key,
    required this.digest,
    required this.onOpenConversation,
    required this.onReview,
    required this.onDismiss,
  });

  final CompletionDigest digest;
  final VoidCallback onOpenConversation;
  final VoidCallback onReview;
  final VoidCallback onDismiss;

  String get statusText =>
      'Server reported idle. Success or failure is not verified.';
  String get changedFilesText => digest.changedFiles == null
      ? 'Changed files: unknown.'
      : 'Changed files: ${digest.changedFiles} (session total, not this run).';
  String get pendingDecisionsText => digest.pendingDecisions == null
      ? 'Pending decisions: unknown.'
      : 'Pending decisions: ${digest.pendingDecisions} in the current cache.';
  String get outcomesText => 'Tool outcomes and remaining tasks: unknown.';
  String get provenanceText =>
      'Cached server metadata only. No AI summary or model call. '
      'Open the conversation to verify results and review changes or tasks.';
  String get sanitizedSummary => [
    statusText,
    changedFilesText,
    pendingDecisionsText,
    outcomesText,
    provenanceText,
  ].join('\n');

  @override
  Widget build(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(statusText, style: Theme.of(context).textTheme.titleSmall),
          const SizedBox(height: 8),
          Text(changedFilesText),
          Text(pendingDecisionsText),
          Text(outcomesText),
          const SizedBox(height: 8),
          Text(provenanceText, style: Theme.of(context).textTheme.bodySmall),
          Wrap(
            spacing: 8,
            runSpacing: 4,
            children: [
              TextButton(
                onPressed: onOpenConversation,
                child: Text(l10n.digestOpenConversation),
              ),
              TextButton(onPressed: onReview, child: Text(l10n.digestReview)),
              TextButton.icon(
                onPressed: () async {
                  try {
                    await Clipboard.setData(
                      ClipboardData(text: sanitizedSummary),
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
