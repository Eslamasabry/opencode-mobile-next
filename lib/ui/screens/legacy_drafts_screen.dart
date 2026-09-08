import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../l10n/app_localizations.dart';
import '../../state/connection.dart';
import '../../state/session_drafts.dart';
import '../widgets/confirm_sheet.dart';
import '../app_iconography.dart';

class LegacyDraftsScreen extends StatefulWidget {
  const LegacyDraftsScreen({super.key, required this.controller});
  final ConnectionController controller;
  @override
  State<LegacyDraftsScreen> createState() => _LegacyDraftsScreenState();
}

class _LegacyDraftsScreenState extends State<LegacyDraftsScreen> {
  String _query = '';
  AppLocalizations get l10n =>
      lookupAppLocalizations(Localizations.localeOf(context));

  Future<void> _review(SessionDraft draft) async {
    FocusManager.instance.primaryFocus?.unfocus();
    final insert = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      showDragHandle: true,
      builder: (sheet) => SizedBox(
        height: MediaQuery.sizeOf(sheet).height * .8,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(20, 0, 20, 16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(
                l10n.legacyDraftsTitle,
                style: Theme.of(sheet).textTheme.titleLarge,
              ),
              const SizedBox(height: 8),
              Text(
                draft.attachments.isEmpty
                    ? l10n.legacyDraftInsertExplanation
                    : l10n.legacyDraftTextOnly,
              ),
              const SizedBox(height: 12),
              Expanded(
                child: SingleChildScrollView(child: SelectableText(draft.text)),
              ),
              const SizedBox(height: 8),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                alignment: WrapAlignment.end,
                children: [
                  IconButton(
                    tooltip: MaterialLocalizations.of(sheet).copyButtonLabel,
                    onPressed: draft.text.isEmpty
                        ? null
                        : () => Clipboard.setData(
                            ClipboardData(text: draft.text),
                          ),
                    icon: const Icon(AppIconography.copy),
                  ),
                  OutlinedButton(
                    onPressed: () async {
                      final confirmed = await showConfirmSheet(
                        sheet,
                        title: l10n.legacyDraftDelete,
                        message: l10n.legacyDraftDeleteExplanation,
                        confirmLabel: l10n.legacyDraftDelete,
                        cancelLabel: MaterialLocalizations.of(
                          sheet,
                        ).cancelButtonLabel,
                        destructive: true,
                      );
                      if (!confirmed || !mounted || !sheet.mounted) return;
                      final removed = await widget.controller
                          .removeLegacySessionDraft(draft);
                      if (!mounted || !sheet.mounted) return;
                      if (removed) {
                        Navigator.pop(sheet, false);
                      } else {
                        ScaffoldMessenger.of(context).showSnackBar(
                          SnackBar(content: Text(l10n.legacyDraftDeleteFailed)),
                        );
                      }
                    },
                    child: Text(l10n.legacyDraftDelete),
                  ),
                  FilledButton(
                    onPressed: draft.text.isEmpty
                        ? null
                        : () => Navigator.pop(sheet, true),
                    child: Text(l10n.legacyDraftInsert),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
    if (mounted && insert == true) Navigator.pop(context, draft.text);
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: Text(l10n.legacyDraftsTitle)),
    body: SafeArea(
      top: false,
      child: ListenableBuilder(
        listenable: widget.controller,
        builder: (context, _) {
          final drafts = widget.controller.legacySessionDrafts
              .where(
                (draft) =>
                    draft.text.toLowerCase().contains(_query) ||
                    draft.sessionID.toLowerCase().contains(_query),
              )
              .toList();
          return CustomScrollView(
            slivers: [
              SliverToBoxAdapter(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
                  child: Text(l10n.legacyDraftsExplanation),
                ),
              ),
              SliverToBoxAdapter(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
                  child: TextField(
                    key: const ValueKey('legacy-drafts-search'),
                    decoration: InputDecoration(
                      labelText: l10n.legacyDraftSearch,
                      prefixIcon: const Icon(AppIconography.search),
                    ),
                    onChanged: (value) =>
                        setState(() => _query = value.toLowerCase()),
                  ),
                ),
              ),
              if (drafts.isEmpty)
                SliverFillRemaining(
                  hasScrollBody: false,
                  child: Center(child: Text(l10n.legacyDraftsEmpty)),
                )
              else
                SliverList.builder(
                  itemCount: drafts.length,
                  itemBuilder: (context, index) {
                    final draft = drafts[index];
                    return ListTile(
                      key: ValueKey('legacy-draft-${draft.sessionID}'),
                      leading: const Icon(AppIconography.history),
                      title: Text(
                        draft.text.isEmpty
                            ? l10n.legacyDraftTextOnly
                            : draft.text,
                        maxLines: 3,
                        overflow: TextOverflow.ellipsis,
                      ),
                      subtitle: Text(
                        MaterialLocalizations.of(context).formatMediumDate(
                          DateTime.fromMillisecondsSinceEpoch(draft.updatedAt),
                        ),
                      ),
                      trailing: const Icon(AppIconography.chevronRight),
                      onTap: () => _review(draft),
                    );
                  },
                ),
            ],
          );
        },
      ),
    ),
  );
}
