import 'package:flutter/material.dart';

import '../../domain/return_brief.dart';
import '../../l10n/app_localizations.dart';

/// A compact summary, never a read receipt or a request decision.
class ReturnBriefCard extends StatelessWidget {
  const ReturnBriefCard({
    super.key,
    required this.brief,
    required this.stale,
    required this.onReview,
    required this.onContinue,
    required this.onAnswer,
    required this.onDismiss,
    this.saving = false,
    this.saveFailed = false,
  });

  final ReturnBrief brief;
  final bool stale;
  final bool saving;
  final bool saveFailed;
  final ValueChanged<ReturnBriefRun> onReview;
  final ValueChanged<String> onContinue;
  final ValueChanged<ReturnBriefRequest> onAnswer;
  final VoidCallback onDismiss;

  @override
  Widget build(BuildContext context) {
    if (brief.isEmpty &&
        brief.readStateKnown &&
        !brief.inventoryPartial &&
        !stale) {
      return const SizedBox.shrink();
    }
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final theme = Theme.of(context);
    final colors = theme.colorScheme;
    String title(String? text) =>
        text?.isNotEmpty == true ? text! : l10n.returnBriefUntitled;
    return Card(
      margin: const EdgeInsets.fromLTRB(16, 8, 16, 16),
      color: colors.surfaceContainerLow,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(20),
        side: BorderSide(color: colors.outlineVariant),
      ),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Wrap(
              spacing: 10,
              runSpacing: 8,
              crossAxisAlignment: WrapCrossAlignment.center,
              children: [
                Icon(Icons.auto_stories_outlined, color: colors.primary),
                Text(l10n.returnBriefTitle, style: theme.textTheme.titleLarge),
              ],
            ),
            const SizedBox(height: 8),
            Text(l10n.returnBriefDescription, style: theme.textTheme.bodySmall),
            if (stale)
              Padding(
                padding: const EdgeInsets.only(top: 8),
                child: Text(
                  l10n.returnBriefStale,
                  style: theme.textTheme.bodyMedium,
                ),
              ),
            if (!brief.readStateKnown)
              Padding(
                padding: const EdgeInsets.only(top: 8),
                child: Text(l10n.returnBriefUnknown),
              ),
            if (brief.inventoryPartial)
              Padding(
                padding: const EdgeInsets.only(top: 8),
                child: Text(l10n.returnBriefPartial),
              ),
            for (final request in brief.requests) ...[
              const Divider(height: 24),
              Text(
                title(request.session.title),
                style: theme.textTheme.titleSmall,
              ),
              Text(switch (request.blocker) {
                ReturnBriefBlocker.permission => l10n.monitorPermission,
                ReturnBriefBlocker.question => l10n.monitorQuestion,
                ReturnBriefBlocker.form => l10n.monitorForm,
              }),
              Align(
                alignment: AlignmentDirectional.centerStart,
                child: FilledButton.tonalIcon(
                  onPressed: () => onAnswer(request),
                  icon: const Icon(Icons.reply_rounded),
                  label: Text(l10n.returnBriefAnswer),
                ),
              ),
            ],
            for (final run in brief.unreviewed) ...[
              const Divider(height: 24),
              Text(title(run.session.title), style: theme.textTheme.titleSmall),
              Text(
                l10n.returnBriefUnreviewed,
                style: theme.textTheme.bodySmall,
              ),
              Wrap(
                spacing: 8,
                runSpacing: 4,
                children: [
                  FilledButton.tonal(
                    onPressed: () => onReview(run),
                    child: Text(l10n.returnBriefReview),
                  ),
                  TextButton(
                    onPressed: () => onContinue(run.session.id),
                    child: Text(l10n.returnBriefContinue),
                  ),
                ],
              ),
            ],
            if (brief.hiddenRequests + brief.hiddenUnreviewed > 0)
              Padding(
                padding: const EdgeInsets.only(top: 12),
                child: Text(
                  l10n.returnBriefMore(
                    brief.hiddenRequests + brief.hiddenUnreviewed,
                  ),
                ),
              ),
            if (saveFailed)
              Padding(
                padding: const EdgeInsets.only(top: 12),
                child: Semantics(
                  liveRegion: true,
                  child: Text(
                    l10n.returnBriefSaveFailed,
                    style: TextStyle(color: colors.error),
                  ),
                ),
              ),
            if (!brief.isEmpty) ...[
              const SizedBox(height: 8),
              Align(
                alignment: AlignmentDirectional.centerEnd,
                child: TextButton(
                  onPressed: saving ? null : onDismiss,
                  child: Text(
                    saving ? l10n.returnBriefSaving : l10n.returnBriefDismiss,
                  ),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
