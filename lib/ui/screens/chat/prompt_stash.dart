part of '../chat_screen.dart';

class _PromptStashSheet extends StatefulWidget {
  const _PromptStashSheet({
    required this.controller,
    required this.location,
    required this.profile,
  });
  final ConnectionController controller;
  final int location;
  final String profile;
  @override
  State<_PromptStashSheet> createState() => _PromptStashSheetState();
}

class _PromptStashSheetState extends State<_PromptStashSheet> {
  final _search = TextEditingController();
  String _query = '';
  String? _error;
  bool _deleting = false;
  bool _preparing = false;
  bool _invalidated = false;
  bool _readFailed = false;
  bool _migrationPending = false;
  List<StashedPrompt> _prompts = const [];

  bool get _current =>
      !_invalidated &&
      widget.controller.canUsePromptShelf &&
      widget.profile == widget.controller.promptShelfProfileID &&
      widget.location == widget.controller.locationRevision;
  bool get _unsafe => !_current || _preparing || _deleting || _readFailed;

  @override
  void initState() {
    super.initState();
    widget.controller.addListener(_changed);
    widget.controller.profileDataChanges.addListener(_changed);
    _readPrompts();
    unawaited(_prepare());
  }

  void _readPrompts() {
    if (!_current) return;
    try {
      _prompts = widget.controller.promptStash;
    } catch (_) {
      _readFailed = true;
    }
  }

  void _changed() {
    if (!mounted) return;
    setState(() {
      if (!_current) _invalidated = true;
      if (_invalidated) {
        _prompts = const [];
      } else {
        _readPrompts();
      }
    });
  }

  Future<void> _prepare() async {
    if (!_current || _preparing || _deleting) return;
    setState(() {
      _preparing = true;
      _error = null;
    });
    try {
      final deferred = await widget.controller.preparePromptStash(
        locationRevision: widget.location,
      );
      if (!mounted || !_current) return;
      setState(() {
        _readFailed = false;
        _migrationPending = deferred.isNotEmpty;
        _readPrompts();
      });
    } catch (_) {
      if (mounted && _current) setState(() => _readFailed = true);
    } finally {
      if (mounted) setState(() => _preparing = false);
    }
  }

  @override
  void dispose() {
    _search.dispose();
    widget.controller.removeListener(_changed);
    widget.controller.profileDataChanges.removeListener(_changed);
    super.dispose();
  }

  Future<void> _delete(StashedPrompt prompt) async {
    if (_unsafe) return;
    setState(() => _deleting = true);
    try {
      final confirmed = await showConfirmSheet(
        context,
        icon: Icons.delete_outline_rounded,
        title: _chatL10n(context).promptStashDeleteTitle,
        message: _chatL10n(context).promptStashDeleteDetail,
        confirmLabel: _chatL10n(context).promptStashDelete,
        cancelLabel: MaterialLocalizations.of(context).cancelButtonLabel,
        destructive: true,
      );
      if (!mounted ||
          !confirmed ||
          !_current ||
          _readFailed ||
          _preparing ||
          !(ModalRoute.of(context)?.isCurrent ?? true)) {
        return;
      }
      setState(() {
        _deleting = true;
        _error = null;
      });
      await widget.controller.removePromptStash(
        prompt.id,
        locationRevision: widget.location,
      );
    } catch (_) {
      if (mounted && _current) {
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
      final query = _query.trim().toLowerCase();
      final prompts = query.isEmpty
          ? _prompts
          : _prompts.where((prompt) {
              final fields = <String>[
                prompt.text,
                ...prompt.attachmentNames,
                if (prompt.directory != null) prompt.directory!,
                if (prompt.workspace != null) prompt.workspace!,
                for (final reference in prompt.references)
                  reference.description,
              ];
              return fields.any((field) => field.toLowerCase().contains(query));
            }).toList();
      var error = _error;
      final current = _current;
      if (!current) {
        error = l10n.promptStashScopeChanged;
      } else if (_readFailed) {
        error = l10n.promptStashReadFailed;
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
            padding: EdgeInsets.fromLTRB(
              16,
              0,
              16,
              20 + MediaQuery.viewInsetsOf(context).bottom,
            ),
            children: [
              Text(
                l10n.promptStashTitle,
                style: Theme.of(context).textTheme.titleLarge,
              ),
              const SizedBox(height: 6),
              Text(l10n.promptStashListDescription),
              const SizedBox(height: 12),
              TextField(
                controller: _search,
                enabled: current,
                textInputAction: TextInputAction.search,
                onChanged: (value) => setState(() => _query = value),
                decoration: InputDecoration(
                  labelText: l10n.promptStashSearch,
                  prefixIcon: const Icon(Icons.search_rounded),
                  suffixIcon: _query.isEmpty
                      ? null
                      : IconButton(
                          tooltip: l10n.commonClearSearch,
                          onPressed: () {
                            _search.clear();
                            setState(() => _query = '');
                          },
                          icon: const Icon(Icons.close_rounded),
                        ),
                ),
              ),
              const SizedBox(height: 12),
              if (error != null)
                Text(
                  error,
                  style: TextStyle(color: Theme.of(context).colorScheme.error),
                ),
              if (_preparing || _deleting)
                const LinearProgressIndicator(minHeight: 2),
              if (_migrationPending && current)
                Text(l10n.promptStashMigrationPending),
              if (current && (_readFailed || _migrationPending))
                Align(
                  alignment: AlignmentDirectional.centerStart,
                  child: TextButton(
                    style: TextButton.styleFrom(
                      minimumSize: const Size(48, 48),
                    ),
                    onPressed: _preparing || _deleting ? null : _prepare,
                    child: Text(l10n.commonRetry),
                  ),
                ),
              if (prompts.isEmpty && error == null && !_preparing)
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 24),
                  child: Text(
                    query.isEmpty
                        ? l10n.promptStashEmpty
                        : l10n.promptStashNoMatches,
                  ),
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
                        if (prompt.attachmentCount > 0 ||
                            prompt.references.isNotEmpty)
                          Text(
                            [
                              if (prompt.attachmentCount > 0)
                                l10n.promptStashAttachments(
                                  prompt.attachmentCount,
                                ),
                              if (prompt.references.isNotEmpty)
                                l10n.promptStashReferences(
                                  prompt.references.length,
                                ),
                            ].join(' · '),
                          ),
                        for (final name in prompt.attachmentNames)
                          Text(
                            name,
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
                              style: TextButton.styleFrom(
                                minimumSize: const Size(48, 48),
                              ),
                              onPressed: _unsafe
                                  ? null
                                  : () {
                                      if (_unsafe ||
                                          !(ModalRoute.of(context)?.isCurrent ??
                                              true)) {
                                        return;
                                      }
                                      setState(() => _deleting = true);
                                      Navigator.pop(context, prompt);
                                    },
                              icon: const Icon(Icons.unarchive_outlined),
                              label: Text(l10n.promptRestore),
                            ),
                            TextButton.icon(
                              key: ValueKey('delete-stash-${prompt.id}'),
                              style: TextButton.styleFrom(
                                minimumSize: const Size(48, 48),
                              ),
                              onPressed: _unsafe ? null : () => _delete(prompt),
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
