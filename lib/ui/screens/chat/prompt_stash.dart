part of '../chat_screen.dart';

class _PromptStashSheet extends StatefulWidget {
  const _PromptStashSheet({required this.controller, required this.location});
  final ConnectionController controller;
  final int location;
  @override
  State<_PromptStashSheet> createState() => _PromptStashSheetState();
}

class _PromptStashSheetState extends State<_PromptStashSheet> {
  String? _error;
  bool _deleting = false;

  Future<void> _delete(StashedPrompt prompt) async {
    final confirmed = await showConfirmSheet(
      context,
      icon: Icons.delete_outline_rounded,
      title: _chatL10n(context).promptStashDeleteTitle,
      message: _chatL10n(context).promptStashDeleteDetail,
      confirmLabel: _chatL10n(context).promptStashDelete,
      destructive: true,
    );
    if (!mounted || !confirmed) return;
    setState(() {
      _deleting = true;
      _error = null;
    });
    try {
      await widget.controller.removePromptStash(
        prompt.id,
        locationRevision: widget.location,
      );
    } catch (_) {
      if (mounted) {
        setState(() => _error = _chatL10n(context).promptStashDeleteFailed);
      }
    } finally {
      if (mounted) setState(() => _deleting = false);
    }
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: widget.controller,
    builder: (context, _) {
      final l10n = _chatL10n(context);
      List<StashedPrompt> prompts = const [];
      var error = _error;
      final current = widget.location == widget.controller.locationRevision;
      if (!current) {
        error = l10n.promptStashScopeChanged;
      } else {
        try {
          prompts = widget.controller.promptStash;
        } catch (_) {
          error = l10n.promptStashReadFailed;
        }
      }
      return SafeArea(
        top: false,
        child: ConstrainedBox(
          constraints: BoxConstraints(
            maxHeight: MediaQuery.sizeOf(context).height * .85,
          ),
          child: ListView(
            key: const Key('prompt-stash-sheet'),
            shrinkWrap: true,
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 20),
            children: [
              Text(
                l10n.promptStashTitle,
                style: Theme.of(context).textTheme.titleLarge,
              ),
              const SizedBox(height: 6),
              Text(l10n.promptStashListDescription),
              const SizedBox(height: 12),
              if (error != null)
                Text(
                  error,
                  style: TextStyle(color: Theme.of(context).colorScheme.error),
                ),
              if (_deleting) const LinearProgressIndicator(minHeight: 2),
              if (prompts.isEmpty && error == null)
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 24),
                  child: Text(l10n.promptStashEmpty),
                ),
              for (final prompt in prompts)
                Card(
                  child: Padding(
                    padding: const EdgeInsets.all(12),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          MaterialLocalizations.of(context).formatMediumDate(
                            DateTime.fromMillisecondsSinceEpoch(
                              prompt.createdAt,
                            ),
                          ),
                          style: Theme.of(context).textTheme.labelSmall,
                        ),
                        ExpansionTile(
                          tilePadding: EdgeInsets.zero,
                          childrenPadding: const EdgeInsets.only(bottom: 12),
                          title: Text(
                            prompt.text.isEmpty
                                ? l10n.promptStashContextOnly
                                : prompt.text,
                            maxLines: 3,
                            overflow: TextOverflow.ellipsis,
                          ),
                          children: [
                            Align(
                              alignment: AlignmentDirectional.centerStart,
                              child: SelectableText(prompt.text),
                            ),
                          ],
                        ),
                        if (prompt.attachments.isNotEmpty ||
                            prompt.references.isNotEmpty)
                          Text(
                            [
                              if (prompt.attachments.isNotEmpty)
                                l10n.promptStashAttachments(
                                  prompt.attachments.length,
                                ),
                              if (prompt.references.isNotEmpty)
                                l10n.promptStashReferences(
                                  prompt.references.length,
                                ),
                            ].join(' · '),
                          ),
                        for (final attachment in prompt.attachments)
                          Text(
                            attachment.filename,
                            maxLines: 2,
                            overflow: TextOverflow.ellipsis,
                          ),
                        for (final reference in prompt.references)
                          Text(
                            reference.description,
                            maxLines: 2,
                            overflow: TextOverflow.ellipsis,
                          ),
                        if (prompt.locationBound)
                          Text(
                            prompt.directory ?? l10n.promptDefaultLocation,
                            style: Theme.of(context).textTheme.bodySmall,
                          ),
                        Wrap(
                          spacing: 8,
                          children: [
                            TextButton.icon(
                              key: ValueKey('restore-stash-${prompt.id}'),
                              onPressed: _deleting || !current
                                  ? null
                                  : () => Navigator.pop(context, prompt),
                              icon: const Icon(Icons.unarchive_outlined),
                              label: Text(l10n.promptRestore),
                            ),
                            TextButton.icon(
                              key: ValueKey('delete-stash-${prompt.id}'),
                              onPressed: _deleting || !current
                                  ? null
                                  : () => _delete(prompt),
                              icon: const Icon(Icons.delete_outline_rounded),
                              label: Text(l10n.promptStashDelete),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                ),
            ],
          ),
        ),
      );
    },
  );
}
