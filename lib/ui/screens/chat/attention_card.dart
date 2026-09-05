part of '../chat_screen.dart';

/// Inline attention surface pinned directly above the composer. It replaces
/// the auto-opened, non-dismissible permission sheet: a request arriving
/// mid-sentence no longer steals the keyboard. One card shows at a time
/// (oldest request first); the full sheet stays one tap away behind Review.
class _AttentionCard extends StatelessWidget {
  const _AttentionCard({
    required this.icon,
    required this.title,
    required this.announcement,
    this.summary,
    this.detail,
    required this.primary,
    this.secondary,
    this.accent,
    this.minHeight = 72,
    this.body,
  });

  final IconData icon;
  final String title;

  /// Optional content between the header and the buttons — the question
  /// card puts its option rows here so every flavour shares one frame.
  final Widget? body;

  /// Icon and border tint; defaults to the primary colour. The retry banner
  /// passes the attention tone so it reads as a wait, not an ask.
  final Color? accent;

  /// Minimum card height; the permission card keeps 72 so its two buttons
  /// never crowd, the slimmer retry banner passes 0.
  final double minHeight;

  /// Read by TalkBack when the card appears, e.g. "Permission needed: Run a
  /// shell command". The visible title stays a plain [Text] for tests and
  /// for sighted readers.
  final String announcement;
  final String? summary;
  final String? detail;
  final Widget primary;
  final Widget? secondary;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final tint = accent ?? scheme.primary;
    return Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 860),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(12, 4, 12, 2),
          child: Container(
            constraints: BoxConstraints(minHeight: minHeight),
            padding: const EdgeInsets.fromLTRB(14, 12, 12, 10),
            decoration: BoxDecoration(
              color: scheme.surfaceContainerLow,
              borderRadius: BorderRadius.circular(AppTheme.radiusCard),
              border: Border.all(color: tint.withValues(alpha: .45)),
              boxShadow: AppTheme.raised(theme),
            ),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Padding(
                      padding: const EdgeInsets.only(top: 2),
                      child: Icon(icon, color: tint),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Semantics(
                            liveRegion: true,
                            label: announcement,
                            excludeSemantics: true,
                            child: Text(
                              title,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: theme.textTheme.titleSmall,
                            ),
                          ),
                          if (summary case final summary?
                              when summary.isNotEmpty)
                            Padding(
                              padding: const EdgeInsets.only(top: 2),
                              child: Text(
                                summary,
                                maxLines: 2,
                                overflow: TextOverflow.ellipsis,
                                style: const TextStyle(
                                  fontFamily: AppTheme.monoFamily,
                                  fontSize: AppTheme.codeFontSize,
                                ),
                              ),
                            ),
                          if (detail case final detail? when detail.isNotEmpty)
                            Padding(
                              padding: const EdgeInsets.only(top: 2),
                              child: Text(
                                detail,
                                maxLines: 2,
                                overflow: TextOverflow.ellipsis,
                                style: theme.textTheme.labelSmall?.copyWith(
                                  color: AppTheme.mutedOf(theme),
                                ),
                              ),
                            ),
                        ],
                      ),
                    ),
                  ],
                ),
                if (body case final body?)
                  Padding(padding: const EdgeInsets.only(top: 8), child: body),
                const SizedBox(height: 8),
                Align(
                  alignment: AlignmentDirectional.centerEnd,
                  child: Wrap(
                    spacing: 8,
                    runSpacing: 4,
                    alignment: WrapAlignment.end,
                    crossAxisAlignment: WrapCrossAlignment.center,
                    children: [?secondary, primary],
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// The permission flavour of [_AttentionCard]: tool title, requested
/// patterns, Review (opens the full sheet) and Allow once (the fast path,
/// identical to the sheet's Allow once).
class _PermissionAttentionCard extends StatelessWidget {
  const _PermissionAttentionCard({
    super.key,
    required this.permission,
    required this.replying,
    required this.onReview,
    required this.onAllowOnce,
  });

  final PermissionRequest permission;
  final bool replying;
  final VoidCallback onReview;
  final VoidCallback onAllowOnce;

  @override
  Widget build(BuildContext context) {
    final title = permissionRequestTitle(permission.permission);
    return _AttentionCard(
      icon: Icons.admin_panel_settings_outlined,
      title: title,
      announcement: 'Permission needed: $title',
      summary: permission.patterns.isEmpty
          ? null
          : permission.patterns.join(' · '),
      detail: permission.message,
      secondary: OutlinedButton(
        key: const Key('permission-card-review'),
        onPressed: replying ? null : onReview,
        child: const Text('Review'),
      ),
      primary: FilledButton(
        key: const Key('permission-card-allow-once'),
        onPressed: replying ? null : onAllowOnce,
        child: replying
            ? const SizedBox.square(
                dimension: 16,
                child: CircularProgressIndicator(strokeWidth: 2),
              )
            : const Text('Allow once'),
      ),
    );
  }
}

/// The question flavour of [_AttentionCard]: the prompt header and question,
/// every choice as a tappable option row, the free-text field when the
/// prompt accepts one, and More (opens the full sheet). A single-select
/// prompt answers on tap; multi-select and two-prompt questions collect
/// answers behind Send. Questions the card cannot hold — more than two
/// prompts or a long description — collapse to the question and an Answer
/// button that opens the sheet, mirroring `formPrefersFullScreen`.
class _QuestionAttentionCard extends StatefulWidget {
  const _QuestionAttentionCard({
    super.key,
    required this.question,
    required this.replying,
    required this.onAnswer,
    required this.onMore,
  });

  final PendingQuestion question;
  final bool replying;
  final ValueChanged<List<List<String>>> onAnswer;
  final VoidCallback onMore;

  @override
  State<_QuestionAttentionCard> createState() => _QuestionAttentionCardState();
}

class _QuestionAttentionCardState extends State<_QuestionAttentionCard> {
  late final List<Set<String>> _selected = List.generate(
    widget.question.prompts.length,
    (_) => <String>{},
  );
  late final List<TextEditingController> _custom = List.generate(
    widget.question.prompts.length,
    (_) => TextEditingController(),
  );

  List<QuestionPrompt> get _prompts => widget.question.prompts;

  /// Single-select, one prompt: a tap is the whole answer. Anything else
  /// needs Send so a half-finished selection is never sent early.
  bool get _answersOnTap => _prompts.length == 1 && !_prompts.single.multiple;

  bool get _complete {
    for (var i = 0; i < _prompts.length; i++) {
      if (_selected[i].isEmpty && _custom[i].text.trim().isEmpty) return false;
    }
    return true;
  }

  bool get _hasCustomText =>
      _custom.any((controller) => controller.text.trim().isNotEmpty);

  List<List<String>> _serialize() {
    final answers = <List<String>>[];
    for (var i = 0; i < _prompts.length; i++) {
      final prompt = _prompts[i];
      final customAnswer = _custom[i].text.trim();
      if (prompt.multiple) {
        answers.add([
          ..._selected[i],
          if (customAnswer.isNotEmpty) customAnswer,
        ]);
      } else {
        answers.add([
          if (customAnswer.isNotEmpty)
            customAnswer
          else if (_selected[i].isNotEmpty)
            _selected[i].first,
        ]);
      }
    }
    return answers;
  }

  void _send() {
    if (!_complete || widget.replying) return;
    widget.onAnswer(_serialize());
  }

  void _tap(int index, QuestionPrompt prompt, QuestionChoice choice) {
    if (widget.replying) return;
    setState(() {
      if (prompt.multiple) {
        if (!_selected[index].remove(choice.label)) {
          _selected[index].add(choice.label);
        }
      } else {
        _custom[index].clear();
        _selected[index]
          ..clear()
          ..add(choice.label);
      }
    });
    if (_answersOnTap) _send();
  }

  @override
  void dispose() {
    for (final controller in _custom) {
      controller.dispose();
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final first = _prompts.firstOrNull;
    final title = first?.title.trim().isNotEmpty == true
        ? first!.title.trim()
        : 'OpenCode needs input';
    final more = TextButton(
      key: const Key('question-card-more'),
      onPressed: widget.replying ? null : widget.onMore,
      child: const Text('More'),
    );
    if (first == null || questionPrefersSheet(widget.question)) {
      final count = _prompts.length;
      return _AttentionCard(
        icon: Icons.help_outline_rounded,
        title: title,
        announcement: 'Question: $title',
        detail: first == null
            ? null
            : count > 1
            ? '${first.question} · $count questions'
            : first.question,
        primary: FilledButton(
          key: const Key('question-card-answer'),
          onPressed: widget.replying ? null : widget.onMore,
          child: const Text('Answer'),
        ),
      );
    }

    final sending = widget.replying;
    // Send earns its place only when a tap cannot be the whole answer.
    final showSend = !_answersOnTap || _hasCustomText;
    final Widget primary = sending
        ? Row(
            key: const Key('question-card-sending'),
            mainAxisSize: MainAxisSize.min,
            children: [
              const SizedBox.square(
                dimension: 14,
                child: CircularProgressIndicator(strokeWidth: 2),
              ),
              const SizedBox(width: 8),
              Text(
                'Sending…',
                style: theme.textTheme.labelLarge?.copyWith(
                  color: AppTheme.mutedOf(theme),
                ),
              ),
            ],
          )
        : showSend
        ? FilledButton(
            key: const Key('question-card-send'),
            onPressed: _complete ? _send : null,
            child: const Text('Send'),
          )
        : more;

    return _AttentionCard(
      icon: Icons.help_outline_rounded,
      title: title,
      announcement: 'Question: $title',
      body: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: [
          for (var index = 0; index < _prompts.length; index++) ...[
            if (index > 0)
              Padding(
                padding: const EdgeInsets.only(top: 10, bottom: 2),
                child: Text(
                  _prompts[index].title,
                  style: theme.textTheme.labelLarge,
                ),
              ),
            if (_prompts[index].question.trim().isNotEmpty)
              Padding(
                padding: const EdgeInsets.only(bottom: 4),
                child: Text(
                  _prompts[index].question,
                  key: ValueKey('question-card-question-$index'),
                  style: theme.textTheme.bodyMedium,
                ),
              ),
            for (final choice in _prompts[index].choices)
              QuestionOptionRow(
                choice: choice,
                multiple: _prompts[index].multiple,
                selected: _selected[index].contains(choice.label),
                enabled: !sending,
                onTap: () => _tap(index, _prompts[index], choice),
              ),
            if (_prompts[index].custom)
              Padding(
                padding: const EdgeInsets.only(top: 6),
                child: QuestionCustomAnswerField(
                  key: ValueKey('question-card-custom-$index'),
                  controller: _custom[index],
                  enabled: !sending,
                  maxLines: 2,
                  onChanged: (value) => setState(() {
                    if (!_prompts[index].multiple && value.trim().isNotEmpty) {
                      _selected[index].clear();
                    }
                  }),
                  onSubmitted: (_) => _send(),
                ),
              ),
          ],
        ],
      ),
      secondary: identical(primary, more) ? null : more,
      primary: primary,
    );
  }
}

/// "Rate limited. Retrying 2 in 0:42" — the server sends no attempt ceiling,
/// so the banner names the attempt rather than inventing a total. [now]
/// defaults to the wall clock; tests pass a fixed instant.
@visibleForTesting
String retryBannerHeadline(SessionRetryState retry, {DateTime? now}) {
  final attempt = retry.attempt > 0 ? ' ${retry.attempt}' : '';
  final next = retry.next;
  if (next == null) return 'Rate limited. Retrying$attempt…';
  final delta = next.difference(now ?? DateTime.now());
  final remaining = delta.isNegative ? Duration.zero : delta;
  return 'Rate limited. Retrying$attempt in ${_countdown(remaining)}';
}

String _countdown(Duration d) {
  final total = d.inSeconds;
  final minutes = total ~/ 60;
  final seconds = (total % 60).toString().padLeft(2, '0');
  if (minutes >= 60) {
    final hours = minutes ~/ 60;
    return '$hours:${(minutes % 60).toString().padLeft(2, '0')}:$seconds';
  }
  return '$minutes:$seconds';
}

/// The provider-retry flavour of [_AttentionCard]: a slim banner naming the
/// attempt and counting down to the next one, the server's own words when
/// it sent any, and Stop wired to the same abort as the app-bar button.
class _RetryAttentionCard extends StatelessWidget {
  const _RetryAttentionCard({
    super.key,
    required this.retry,
    required this.stopping,
    required this.onStop,
  });

  final SessionRetryState retry;
  final bool stopping;
  final VoidCallback onStop;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final title = retryBannerHeadline(retry);
    final message = retry.message?.trim();
    return _AttentionCard(
      icon: AppIcons.retry,
      accent: AppTheme.statusColor(theme, AppStatusTone.attention),
      minHeight: 0,
      title: title,
      announcement: title,
      detail: message == null || message.isEmpty ? null : message,
      primary: TextButton(
        key: const Key('retry-banner-stop'),
        onPressed: stopping ? null : onStop,
        child: const Text('Stop'),
      ),
    );
  }
}

/// A single-line, self-hiding note above the composer for composer-local
/// outcomes (queued, staged, already present). It replaces snackbars that
/// used to cover the field the user is typing into; confirmations of remote
/// actions stay snackbars.
class _ComposerNote extends StatelessWidget {
  const _ComposerNote({super.key, required this.text});

  final String text;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 860),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(24, 2, 24, 0),
          child: Semantics(
            liveRegion: true,
            child: Text(
              text,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: theme.textTheme.labelSmall?.copyWith(
                color: AppTheme.mutedOf(theme),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// One app-bar overflow for the whole session: the view destinations and
/// transcript toggles that used to sit behind a second icon, then the
/// mutation and utility actions. Every row pops with its action value.
class _SessionMenuSheet extends StatelessWidget {
  const _SessionMenuSheet({
    required this.reasoningExpanded,
    required this.timestampsVisible,
    required this.todosAvailable,
    required this.reverted,
    required this.shared,
    required this.sharingAvailable,
    this.stagedRevert = false,
    this.notesAvailable = false,
  });

  final bool reasoningExpanded;
  final bool timestampsVisible;
  final bool todosAvailable;
  final bool reverted;
  final bool shared;
  final bool sharingAvailable;
  final bool stagedRevert;
  final bool notesAvailable;

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: SingleChildScrollView(
        child: Column(
          key: const Key('session-menu-sheet'),
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const SectionLabel('Views'),
            // Views are the frequent destinations, so they take a compact
            // chip row instead of a tile each and leave the actions below
            // reachable without scrolling on a phone.
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 4),
              child: Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  _SessionMenuChip(
                    icon: Icons.view_timeline_outlined,
                    label: 'Timeline',
                    value: 'timeline',
                  ),
                  _SessionMenuChip(
                    icon: Icons.donut_large_outlined,
                    label: 'Context usage',
                    value: 'context',
                  ),
                  _SessionMenuChip(
                    icon: Icons.difference_outlined,
                    label: 'Changes',
                    value: 'changes',
                  ),
                  if (todosAvailable)
                    _SessionMenuChip(
                      icon: Icons.checklist_rounded,
                      label: 'Todos',
                      value: 'todos',
                    ),
                  _SessionMenuChip(
                    icon: Icons.account_tree_outlined,
                    label: 'Subagent sessions',
                    value: 'subagents',
                  ),
                ],
              ),
            ),
            const SectionLabel('Transcript'),
            TranscriptDisplayToggles(
              reasoningExpanded: reasoningExpanded,
              timestampsVisible: timestampsVisible,
              dense: true,
            ),
            const SectionLabel('Actions'),
            if (notesAvailable)
              _SessionSheetRow(
                icon: Icons.sticky_note_2_outlined,
                label: _chatL10n(context).sessionNoteTitle,
                value: 'note',
              ),
            _SessionSheetRow(
              icon: Icons.replay_rounded,
              label: 'Retry last prompt',
              value: 'retry',
            ),
            _SessionSheetRow(
              icon: reverted
                  ? Icons.settings_backup_restore_rounded
                  : Icons.history_rounded,
              label: reverted
                  ? (stagedRevert
                        ? _chatL10n(context).revertReviewTitle
                        : 'Restore messages')
                  : 'Revert last prompt',
              value: reverted ? 'restore' : 'revert',
            ),
            _SessionSheetRow(
              icon: Icons.fork_right_rounded,
              label: 'Fork session',
              value: 'fork',
            ),
            _SessionSheetRow(
              icon: Icons.compress_rounded,
              label: 'Compact context',
              value: 'compact',
            ),
            if (sharingAvailable)
              _SessionSheetRow(
                icon: shared ? Icons.public_off_rounded : Icons.public_rounded,
                label: shared ? 'Stop sharing' : 'Share session',
                value: shared ? 'unshare' : 'share',
              ),
            _SessionSheetRow(
              icon: Icons.terminal_rounded,
              label: 'Run shell command',
              value: 'shell',
            ),
            _SessionSheetRow(
              icon: AppIcons.run,
              label: 'Commands',
              value: 'slash',
            ),
            _SessionSheetRow(
              icon: Icons.refresh_rounded,
              label: 'Reload messages',
              value: 'reload',
            ),
          ],
        ),
      ),
    );
  }
}

class _SessionMenuChip extends StatelessWidget {
  const _SessionMenuChip({
    required this.icon,
    required this.label,
    required this.value,
  });

  final IconData icon;
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) => ActionChip(
    avatar: Icon(icon, size: 18),
    label: Text(label),
    // Chips read as light chrome but keep the 48 dp Android target.
    materialTapTargetSize: MaterialTapTargetSize.padded,
    onPressed: () => Navigator.pop(context, value),
  );
}
