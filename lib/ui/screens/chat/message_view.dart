part of '../chat_screen.dart';

class _TypingIndicator extends StatefulWidget {
  @override
  State<_TypingIndicator> createState() => __TypingIndicatorState();
}

class _PromptErrorBanner extends StatelessWidget {
  final String message;
  final VoidCallback onDismiss;

  const _PromptErrorBanner({required this.message, required this.onDismiss});

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Semantics(
      container: true,
      liveRegion: true,
      child: Container(
        key: const ValueKey('prompt-error-banner'),
        margin: const EdgeInsets.fromLTRB(12, 8, 12, 0),
        padding: const EdgeInsets.fromLTRB(12, 10, 4, 10),
        decoration: BoxDecoration(
          color: scheme.errorContainer,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: scheme.error.withValues(alpha: .35)),
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(
              Icons.error_outline_rounded,
              size: 20,
              color: scheme.onErrorContainer,
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Text(
                message,
                style: TextStyle(color: scheme.onErrorContainer, height: 1.35),
              ),
            ),
            IconButton(
              tooltip: 'Dismiss prompt error',
              visualDensity: VisualDensity.compact,
              onPressed: onDismiss,
              icon: Icon(
                Icons.close_rounded,
                size: 19,
                color: scheme.onErrorContainer,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// A terminal-style block caret that blinks while the assistant works.
class __TypingIndicatorState extends State<_TypingIndicator>
    with SingleTickerProviderStateMixin {
  late final AnimationController _c = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 1100),
  );
  bool _animating = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final animate = !MediaQuery.disableAnimationsOf(context);
    if (animate == _animating) return;
    _animating = animate;
    if (animate) {
      _c.repeat();
    } else {
      _c.stop();
      _c.value = 0;
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Semantics(
      label: 'Assistant is working',
      liveRegion: true,
      child: Padding(
        key: const ValueKey('typing-indicator'),
        padding: const EdgeInsets.fromLTRB(10, 12, 10, 12),
        child: Row(
          children: [
            AnimatedBuilder(
              animation: _c,
              builder: (context, child) => Opacity(
                // A hard on/off blink like a terminal caret, not a pulse.
                opacity: _c.value < .55 ? 1 : .18,
                child: child,
              ),
              child: Container(
                width: 9,
                height: 17,
                decoration: BoxDecoration(
                  color: theme.colorScheme.primary,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ),
            const SizedBox(width: 10),
            Text(
              'working',
              style: theme.textTheme.labelMedium!.copyWith(
                color: theme.hintColor,
                letterSpacing: .4,
              ),
            ),
          ],
        ),
      ),
    );
  }

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }
}

/// The first thing a new session shows: a prompt-shaped invitation to act,
/// with suggestions that insert real starting points into the composer.
class _EmptyTranscript extends StatelessWidget {
  const _EmptyTranscript({required this.onSuggestion});

  final ValueChanged<String> onSuggestion;

  static const _suggestions = [
    'Explain this project',
    'What changed recently?',
    'Find and fix a bug',
  ];

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final reduceMotion = MediaQuery.disableAnimationsOf(context);
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
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 380),
                child: _entrance(
                  reduceMotion,
                  Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Text(
                          '❯',
                          style: theme.textTheme.headlineSmall!.copyWith(
                            color: theme.colorScheme.primary,
                            fontFamily: 'AppMono',
                          ),
                        ),
                        const SizedBox(width: 8),
                        Container(
                          width: 11,
                          height: 22,
                          decoration: BoxDecoration(
                            color: theme.colorScheme.primary.withValues(
                              alpha: .45,
                            ),
                            borderRadius: BorderRadius.circular(2),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 16),
                    Text('Start coding', style: theme.textTheme.titleMedium),
                    const SizedBox(height: 6),
                    Text(
                      'Describe a change, ask about this project, '
                      'or paste an error.',
                      textAlign: TextAlign.center,
                      style: theme.textTheme.bodySmall?.copyWith(
                        color: theme.hintColor,
                      ),
                    ),
                    const SizedBox(height: 18),
                    Wrap(
                      alignment: WrapAlignment.center,
                      spacing: 8,
                      runSpacing: 8,
                      children: [
                        for (final suggestion in _suggestions)
                          ActionChip(
                            key: ValueKey('empty-suggestion-$suggestion'),
                            label: Text(suggestion),
                            onPressed: () => onSuggestion(suggestion),
                          ),
                      ],
                    ),
                  ],
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _entrance(bool reduceMotion, Widget child) => reduceMotion
      ? child
      : TweenAnimationBuilder<double>(
          tween: Tween(begin: 0, end: 1),
          duration: const Duration(milliseconds: 260),
          curve: Curves.easeOutCubic,
          child: child,
          builder: (context, t, child) => Opacity(
            opacity: t,
            child: Transform.translate(
              offset: Offset(0, (1 - t) * 10),
              child: child,
            ),
          ),
        );
}

/// A floating affordance shown when the transcript is scrolled away from the
/// newest message; tapping returns to the live end of the conversation.
class _JumpToLatestButton extends StatelessWidget {
  const _JumpToLatestButton({required this.onTap});

  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final pill = Material(
      key: const ValueKey('jump-to-latest'),
      color: theme.colorScheme.surfaceContainerHigh,
      elevation: 3,
      shape: const StadiumBorder(),
      child: InkWell(
        customBorder: const StadiumBorder(),
        onTap: onTap,
        child: Tooltip(
          message: 'Jump to latest',
          child: Padding(
            padding: const EdgeInsets.all(10),
            child: Icon(
              Icons.arrow_downward_rounded,
              size: 20,
              color: theme.colorScheme.primary,
            ),
          ),
        ),
      ),
    );
    if (MediaQuery.disableAnimationsOf(context)) return pill;
    return TweenAnimationBuilder<double>(
      tween: Tween(begin: 0, end: 1),
      duration: const Duration(milliseconds: 200),
      curve: Curves.easeOutBack,
      child: pill,
      builder: (context, t, child) => Opacity(
        opacity: t.clamp(0, 1),
        child: Transform.scale(scale: .9 + t * .1, child: child),
      ),
    );
  }
}

/// A floating chip over long transcripts naming how much history sits above,
/// opening the timeline for direct navigation.
class _EarlierMessagesPill extends StatelessWidget {
  const _EarlierMessagesPill({required this.count, required this.onTap});

  final int count;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Material(
      key: const ValueKey('earlier-messages-pill'),
      color: theme.colorScheme.surfaceContainerHigh,
      elevation: 2,
      shape: const StadiumBorder(),
      child: InkWell(
        customBorder: const StadiumBorder(),
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(12, 6, 8, 6),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                '$count earlier messages',
                style: theme.textTheme.labelSmall?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
              Icon(
                Icons.expand_more_rounded,
                size: 15,
                color: theme.hintColor,
              ),
            ],
          ),
        ),
      ),
    );
  }
}

const _contextToolNames = {'read', 'list', 'glob', 'grep'};

bool _isToolPart(Part part) => part.type == 'tool';

List<List<Part>> _timelineDisplayParts(List<MessageWithParts> messages) {
  final display = List.generate(messages.length, (_) => <Part>[]);
  final pendingParts = <Part>[];
  String? pendingType;
  int? pendingOwner;

  void flushPending() {
    if (pendingOwner case final owner?) {
      if (pendingType == 'text' || pendingType == 'reasoning') {
        display[owner].add(_mergeTextParts(pendingParts));
      } else {
        display[owner].addAll(pendingParts);
      }
    }
    pendingParts.clear();
    pendingType = null;
    pendingOwner = null;
  }

  void appendPart(int owner, Part part) {
    final mergeable =
        part.type == 'tool' || part.type == 'text' || part.type == 'reasoning';
    if (!mergeable) {
      flushPending();
      display[owner].add(part);
      return;
    }
    if (pendingType != null && pendingType != part.type) flushPending();
    pendingType ??= part.type;
    pendingOwner ??= owner;
    pendingParts.add(part);
  }

  for (var index = 0; index < messages.length; index += 1) {
    final message = messages[index];
    final parts = message.parts.where((part) => part.isRenderable);
    if (message.info.role != 'assistant') {
      flushPending();
      display[index].addAll(parts);
      continue;
    }

    if (message.info.errorText != null && parts.isEmpty) flushPending();
    for (final part in parts) {
      appendPart(index, part);
    }
    if (message.info.errorText != null) flushPending();

    final nextIsAssistant =
        index + 1 < messages.length &&
        messages[index + 1].info.role == 'assistant';
    if (!nextIsAssistant) flushPending();
  }
  flushPending();
  return display;
}

Part _mergeTextParts(List<Part> parts) {
  assert(parts.isNotEmpty);
  if (parts.length == 1) return parts.single;
  final first = parts.first;
  final buffer = StringBuffer();
  for (final part in parts) {
    if (part.text.trim().isEmpty) continue;
    if (buffer.isNotEmpty &&
        !buffer.toString().endsWith('\n') &&
        !part.text.startsWith('\n')) {
      buffer.write('\n\n');
    }
    buffer.write(part.text);
  }
  return Part(
    id: first.id,
    messageID: first.messageID,
    type: first.type,
    text: buffer.toString(),
  );
}

class _AssistantPartRun {
  const _AssistantPartRun(this.parts, {this.grouped = false});

  final List<Part> parts;
  final bool grouped;
}

List<_AssistantPartRun> _groupAssistantParts(List<Part> parts) {
  final runs = <_AssistantPartRun>[];
  var index = 0;
  while (index < parts.length) {
    final current = parts[index];
    if (!_isToolPart(current)) {
      if (current.type != 'text' && current.type != 'reasoning') {
        runs.add(_AssistantPartRun([current]));
        index += 1;
        continue;
      }
      final textParts = <Part>[current];
      var next = index + 1;
      while (next < parts.length && parts[next].type == current.type) {
        textParts.add(parts[next]);
        next += 1;
      }
      runs.add(_AssistantPartRun([_mergeTextParts(textParts)]));
      index = next;
      continue;
    }

    final toolParts = <Part>[current];
    var next = index + 1;
    while (next < parts.length && _isToolPart(parts[next])) {
      toolParts.add(parts[next]);
      next += 1;
    }
    if (toolParts.length == 1) {
      runs.add(_AssistantPartRun(toolParts));
    } else {
      runs.add(_AssistantPartRun(toolParts, grouped: true));
    }
    index = next;
  }
  return runs;
}

String _contextToolSummary(List<Part> parts) {
  final counts = <String, int>{};
  for (final part in parts) {
    final name = part.toolName!.trim().toLowerCase();
    counts[name] = (counts[name] ?? 0) + 1;
  }
  String countLabel(String name, String singular, String plural) {
    final count = counts[name] ?? 0;
    return count == 0 ? '' : '$count ${count == 1 ? singular : plural}';
  }

  final searchCount = (counts['glob'] ?? 0) + (counts['grep'] ?? 0);
  return [
    countLabel('read', 'read', 'reads'),
    if (searchCount > 0)
      '$searchCount ${searchCount == 1 ? 'search' : 'searches'}',
    countLabel('list', 'list', 'lists'),
  ].where((label) => label.isNotEmpty).join(' · ');
}

String _toolRunSummary(List<Part> parts) {
  final allContext = parts.every(
    (part) => _contextToolNames.contains(part.toolName?.trim().toLowerCase()),
  );
  if (allContext) return _contextToolSummary(parts);

  final labels = <String>[];
  for (final part in parts) {
    final name = part.toolName?.trim().toLowerCase() ?? 'tool';
    final label = switch (name) {
      'bash' || 'shell' => 'shell',
      'read' => 'read',
      'list' => 'list',
      'glob' || 'grep' => 'search',
      'edit' => 'edit',
      'write' => 'write',
      'patch' || 'apply_patch' => 'patch',
      'task' => 'agent',
      'todowrite' || 'todo' => 'tasks',
      'webfetch' || 'websearch' => 'web',
      _ => name,
    };
    if (!labels.contains(label)) labels.add(label);
  }
  final kinds = labels.take(3).join(' · ');
  return '${parts.length} calls${kinds.isEmpty ? '' : ' · $kinds'}';
}

class _ToolCallGroup extends StatefulWidget {
  const _ToolCallGroup({
    super.key,
    required this.parts,
    required this.filePreviewLoader,
    required this.onAttachFile,
    required this.onDownloadFile,
  });

  final List<Part> parts;
  final ToolOutputFileLoader filePreviewLoader;
  final ToolOutputFileAction onAttachFile;
  final ToolOutputFileAction onDownloadFile;

  @override
  State<_ToolCallGroup> createState() => _ToolCallGroupState();
}

class _ToolCallGroupState extends State<_ToolCallGroup> {
  late bool _expanded;

  @override
  void initState() {
    super.initState();
    _expanded = _shouldOpen(widget.parts);
  }

  @override
  void didUpdateWidget(covariant _ToolCallGroup oldWidget) {
    super.didUpdateWidget(oldWidget);
    if ((!_expanded &&
            widget.parts.length > oldWidget.parts.length &&
            _running) ||
        _shouldOpen(widget.parts)) {
      _expanded = true;
    }
  }

  bool _shouldOpen(List<Part> parts) => parts.any(
    (part) =>
        part.toolState.status == 'pending' ||
        part.toolState.status == 'running' ||
        part.toolState.status == 'error' ||
        part.toolState.outputFiles.isNotEmpty,
  );

  bool get _running => widget.parts.any(
    (part) =>
        part.toolState.status == 'pending' ||
        part.toolState.status == 'running',
  );

  bool get _failed =>
      widget.parts.any((part) => part.toolState.status == 'error');

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final reduceMotion = MediaQuery.disableAnimationsOf(context);
    // While running, the header names the tool actually executing instead of
    // the static run summary, like a build log's live line.
    Part? runningPart;
    for (final part in widget.parts.reversed) {
      final status = part.toolState.status;
      if (status == 'running' || status == 'pending') {
        runningPart = part;
        break;
      }
    }
    final summary = runningPart != null
        ? runningToolTicker(runningPart.toolName ?? 'tool', runningPart.toolState)
        : _toolRunSummary(widget.parts);
    final allContext = widget.parts.every(
      (part) => _contextToolNames.contains(part.toolName?.trim().toLowerCase()),
    );
    final title = allContext
        ? (_running ? 'Exploring' : 'Explored')
        : (_running ? 'Running tools' : 'Tools');
    return Container(
      key: const Key('tool-call-group'),
      margin: const EdgeInsets.symmetric(vertical: 3),
      decoration: BoxDecoration(
        color: theme.colorScheme.surfaceContainerHighest.withValues(alpha: .28),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: theme.dividerColor.withValues(alpha: .35)),
      ),
      child: Column(
        children: [
          Semantics(
            button: true,
            expanded: _expanded,
            label: '$title, ${widget.parts.length} tools',
            child: InkWell(
              key: const Key('tool-call-group-header'),
              onTap: () => setState(() => _expanded = !_expanded),
              borderRadius: BorderRadius.circular(8),
              child: ConstrainedBox(
                constraints: const BoxConstraints(minHeight: 48),
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(10, 7, 8, 7),
                  child: Row(
                    children: [
                      Icon(
                        Icons.search_rounded,
                        size: 16,
                        color: theme.hintColor,
                      ),
                      const SizedBox(width: 8),
                      Text(
                        title,
                        style: theme.textTheme.bodySmall?.copyWith(
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                      const SizedBox(width: 7),
                      Expanded(
                        child: Text(
                          summary,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: theme.textTheme.labelSmall?.copyWith(
                            color: theme.colorScheme.onSurfaceVariant,
                          ),
                        ),
                      ),
                      const SizedBox(width: 6),
                      if (_running && !reduceMotion)
                        SizedBox.square(
                          dimension: 12,
                          child: CircularProgressIndicator(
                            strokeWidth: 1.6,
                            color: theme.colorScheme.primary,
                          ),
                        )
                      else
                        Icon(
                          _failed
                              ? Icons.error_outline_rounded
                              : _running
                              ? Icons.hourglass_top_rounded
                              : Icons.check_circle_outline_rounded,
                          size: 14,
                          color: _failed
                              ? theme.colorScheme.error
                              : theme.colorScheme.primary,
                        ),
                      const SizedBox(width: 4),
                      AnimatedRotation(
                        turns: _expanded ? .5 : 0,
                        duration: reduceMotion
                            ? Duration.zero
                            : const Duration(milliseconds: 150),
                        child: Icon(
                          Icons.expand_more_rounded,
                          size: 16,
                          color: theme.hintColor,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
          if (_expanded)
            Column(
              children: [
                Divider(
                  height: 1,
                  color: theme.dividerColor.withValues(alpha: .35),
                ),
                for (var index = 0; index < widget.parts.length; index++) ...[
                  if (index > 0)
                    Divider(
                      height: 1,
                      indent: 34,
                      color: theme.dividerColor.withValues(alpha: .28),
                    ),
                  ToolCard(
                    key: ValueKey(
                      widget.parts[index].id ?? widget.parts[index].callID,
                    ),
                    toolName: widget.parts[index].toolName!,
                    state: widget.parts[index].toolState,
                    embedded: true,
                    filePreviewLoader: widget.filePreviewLoader,
                    onAttachFile: widget.onAttachFile,
                    onDownloadFile: widget.onDownloadFile,
                  ),
                ],
              ],
            ),
        ],
      ),
    );
  }
}

class _AssistantMessagePart extends StatelessWidget {
  const _AssistantMessagePart({
    required this.part,
    required this.reasoningExpanded,
    required this.filePreviewLoader,
    required this.onAttachFile,
    required this.onDownloadFile,
  });

  final Part part;
  final bool reasoningExpanded;
  final ToolOutputFileLoader filePreviewLoader;
  final ToolOutputFileAction onAttachFile;
  final ToolOutputFileAction onDownloadFile;

  @override
  Widget build(BuildContext context) {
    if (part.type == 'text') {
      return Padding(
        key: const Key('assistant-text-block'),
        padding: const EdgeInsets.only(bottom: 4),
        child: MarkdownText(part.text),
      );
    }
    if (part.type == 'reasoning') {
      return _Reasoning(text: part.text, expanded: reasoningExpanded);
    }
    if (part.type == 'tool') {
      return ToolCard(
        key: ValueKey(part.id ?? part.callID),
        toolName: part.toolName ?? 'tool',
        state: part.toolState,
        filePreviewLoader: filePreviewLoader,
        onAttachFile: onAttachFile,
        onDownloadFile: onDownloadFile,
      );
    }
    if (part.type == 'file') {
      return Padding(
        padding: const EdgeInsets.only(bottom: 6),
        child: Chip(
          avatar: const Icon(Icons.attach_file_rounded, size: 16),
          label: Text(part.filename ?? 'Attachment'),
        ),
      );
    }
    return const SizedBox.shrink();
  }
}

class _MessageView extends StatelessWidget {
  final MessageWithParts m;
  final _MessageMeta meta;
  final List<Part> parts;
  final bool reasoningExpanded;
  final bool showTimestamp;
  final bool highlighted;
  final VoidCallback? onLongPress;
  final ToolOutputFileLoader filePreviewLoader;
  final ToolOutputFileAction onAttachFile;
  final ToolOutputFileAction onDownloadFile;
  const _MessageView({
    super.key,
    required this.m,
    required this.meta,
    required this.parts,
    required this.reasoningExpanded,
    required this.showTimestamp,
    this.highlighted = false,
    this.onLongPress,
    required this.filePreviewLoader,
    required this.onAttachFile,
    required this.onDownloadFile,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isUser = m.info.role == 'user';
    final visibleParts = parts.where((p) => p.isRenderable).toList();
    final assistantRuns = isUser
        ? const <_AssistantPartRun>[]
        : _groupAssistantParts(visibleParts);
    final createdAt = m.info.time?.created;

    final metaParts = <String>[
      if (showTimestamp && createdAt != null) _fmtSessionTime(createdAt),
      ?meta.modelLabel,
      if (meta.turnTokens case final tokens?) '${_fmtTokens(tokens)} tok',
      if (meta.turnCost case final cost?) '\$${cost.toStringAsFixed(4)}',
    ];

    final bubbleWidthCap = MediaQuery.of(context).size.width * .88;

    return GestureDetector(
      onLongPress: onLongPress,
      behavior: HitTestBehavior.translucent,
      // The long-press menu is a pointer shortcut for actions that remain
      // reachable elsewhere (text selection, timeline fork). Excluding it
      // keeps each message part as its own semantics node.
      excludeFromSemantics: true,
      child: AnimatedContainer(
      key: ValueKey('message-highlight-${m.info.id}-$highlighted'),
      duration: const Duration(milliseconds: 180),
      padding: const EdgeInsets.fromLTRB(6, 4, 6, 10),
      decoration: BoxDecoration(
        color: highlighted
            ? theme.colorScheme.primaryContainer.withValues(alpha: .24)
            : Colors.transparent,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        crossAxisAlignment: isUser
            ? CrossAxisAlignment.end
            : CrossAxisAlignment.start,
        children: [
          Container(
            constraints: BoxConstraints(
              // Keep prompts readable on wide screens instead of stretching a
              // bubble across a tablet.
              maxWidth: isUser && bubbleWidthCap > 640 ? 640 : bubbleWidthCap,
            ),
            padding: isUser
                ? const EdgeInsets.symmetric(horizontal: 14, vertical: 10)
                : const EdgeInsets.symmetric(horizontal: 4, vertical: 4),
            decoration: isUser
                ? BoxDecoration(
                    color: theme.colorScheme.primaryContainer.withValues(
                      alpha: .55,
                    ),
                    borderRadius: const BorderRadius.only(
                      topLeft: Radius.circular(16),
                      topRight: Radius.circular(16),
                      bottomLeft: Radius.circular(16),
                      bottomRight: Radius.circular(5),
                    ),
                  )
                : null,
            child: isUser
                ? _UserMessageContent(parts: visibleParts)
                : Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      for (final run in assistantRuns)
                        if (run.grouped)
                          _ToolCallGroup(
                            key: ValueKey(
                              'tools:${run.parts.first.id ?? run.parts.first.callID}',
                            ),
                            parts: run.parts,
                            filePreviewLoader: filePreviewLoader,
                            onAttachFile: onAttachFile,
                            onDownloadFile: onDownloadFile,
                          )
                        else
                          _AssistantMessagePart(
                            part: run.parts.single,
                            reasoningExpanded: reasoningExpanded,
                            filePreviewLoader: filePreviewLoader,
                            onAttachFile: onAttachFile,
                            onDownloadFile: onDownloadFile,
                          ),
                    ],
                  ),
          ),
          if (metaParts.isNotEmpty)
            Padding(
              padding: const EdgeInsets.only(top: 3, left: 6, right: 6),
              child: Text(
                key: ValueKey('message-meta-${m.info.id}'),
                metaParts.join('  ·  '),
                style: theme.textTheme.labelSmall!.copyWith(
                  color: theme.hintColor,
                ),
              ),
            ),
          if (m.info.errorText != null)
            Padding(
              padding: const EdgeInsets.only(top: 3),
              child: Text(
                m.info.errorText!,
                style: theme.textTheme.bodySmall!.copyWith(
                  color: theme.colorScheme.error,
                ),
              ),
            ),
        ],
      ),
      ),
    );
  }

  static String _fmtTokens(int n) {
    if (n >= 1000000) return '${(n / 1000000).toStringAsFixed(1)}M';
    if (n >= 1000) return '${(n / 1000).toStringAsFixed(1)}k';
    return '$n';
  }
}

class _MessageMeta {
  const _MessageMeta({this.modelLabel, this.turnTokens, this.turnCost});

  final String? modelLabel;
  final int? turnTokens;
  final double? turnCost;

  bool get isEmpty =>
      modelLabel == null && turnTokens == null && turnCost == null;
}

String? _modelLabel(MessageInfo info) {
  final provider = info.providerID?.trim();
  final model = info.modelID?.trim();
  if (model?.isNotEmpty != true) return null;
  return provider?.isNotEmpty == true
      ? presentedModelLabel(provider!, model!)
      : model;
}

_MessageMeta _messageMeta(List<MessageWithParts> messages, int index) {
  final current = messages[index];
  if (current.info.role != 'assistant') return const _MessageMeta();

  final currentModel = _modelLabel(current.info);
  String? previousModel;
  for (var previous = index - 1; previous >= 0; previous -= 1) {
    final info = messages[previous].info;
    if (info.role != 'assistant') continue;
    previousModel = _modelLabel(info);
    break;
  }
  final modelChanged = currentModel != null && currentModel != previousModel;

  final endsAssistantRun =
      index == messages.length - 1 ||
      messages[index + 1].info.role != 'assistant';
  if (!endsAssistantRun) {
    return _MessageMeta(modelLabel: modelChanged ? currentModel : null);
  }

  var turnTokens = 0;
  var turnCost = 0.0;
  for (var runIndex = index; runIndex >= 0; runIndex -= 1) {
    final info = messages[runIndex].info;
    if (info.role != 'assistant') break;
    turnTokens += info.tokens.total;
    turnCost += info.cost;
  }
  return _MessageMeta(
    modelLabel: modelChanged ? currentModel : null,
    turnTokens: turnTokens > 0 ? turnTokens : null,
    turnCost: turnCost > 0 ? turnCost : null,
  );
}

class _UserMessageContent extends StatelessWidget {
  final List<Part> parts;

  const _UserMessageContent({required this.parts});

  @override
  Widget build(BuildContext context) {
    final text = parts
        .where((part) => part.type == 'text')
        .map((part) => part.text)
        .where((value) => value.trim().isNotEmpty)
        .join('\n');
    final files = parts.where((part) => part.type == 'file').toList();
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (text.isNotEmpty) MarkdownText(text, selectable: false),
        if (text.isNotEmpty && files.isNotEmpty) const SizedBox(height: 8),
        for (final file in files) _AttachmentPart(part: file),
      ],
    );
  }
}

class _AttachmentPart extends StatelessWidget {
  final Part part;

  const _AttachmentPart({required this.part});

  String get _filename => part.filename?.trim().isNotEmpty == true
      ? part.filename!.trim()
      : 'Attachment';

  String get _type {
    final dot = _filename.lastIndexOf('.');
    if (dot < 0 || dot == _filename.length - 1) return 'FILE';
    return _filename.substring(dot + 1).toUpperCase();
  }

  bool get _isReference =>
      part.mime == PromptAttachment.directoryReferenceMime &&
      Uri.tryParse(part.url ?? '')?.scheme == 'file';

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final reference = _isReference;
    void openPreview() => showFilePreviewSheet(
      context,
      FilePreviewData.fromDataUrl(
        name: _filename,
        mimeType: part.mime,
        url: part.url,
      ),
    );

    return Semantics(
      container: true,
      button: !reference,
      excludeSemantics: true,
      label: reference
          ? 'Reference @$_filename'
          : 'Preview attachment $_filename',
      onTap: reference ? null : openPreview,
      child: Tooltip(
        message: reference
            ? 'Project reference @$_filename'
            : 'Preview attachment',
        child: InkWell(
          borderRadius: BorderRadius.circular(10),
          onTap: reference ? null : openPreview,
          child: Container(
            margin: const EdgeInsets.only(bottom: 4),
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 7),
            decoration: BoxDecoration(
              color: theme.colorScheme.surface.withValues(alpha: .5),
              borderRadius: BorderRadius.circular(10),
              border: Border.all(
                color: theme.colorScheme.outlineVariant.withValues(alpha: .7),
              ),
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(
                  reference
                      ? Icons.bookmark_outline_rounded
                      : Icons.attach_file_rounded,
                  size: 17,
                ),
                const SizedBox(width: 7),
                Flexible(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        reference ? '@$_filename' : _filename,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: theme.textTheme.bodySmall?.copyWith(
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                      Text(
                        reference
                            ? 'Project reference'
                            : '$_type · prompt attachment',
                        style: theme.textTheme.labelSmall?.copyWith(
                          color: theme.colorScheme.onSurfaceVariant,
                        ),
                      ),
                    ],
                  ),
                ),
                if (!reference) ...[
                  const SizedBox(width: 8),
                  const Icon(Icons.visibility_outlined, size: 15),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _Reasoning extends StatefulWidget {
  final String text;
  final bool expanded;
  const _Reasoning({required this.text, required this.expanded});

  @override
  State<_Reasoning> createState() => _ReasoningState();
}

class _ReasoningState extends State<_Reasoning> {
  late bool _open;

  @override
  void initState() {
    super.initState();
    _open = widget.expanded;
  }

  @override
  void didUpdateWidget(covariant _Reasoning oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.expanded != widget.expanded) {
      _open = widget.expanded;
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final textStyle = theme.textTheme.bodySmall!.copyWith(
      fontStyle: FontStyle.italic,
      color: theme.hintColor,
    );
    return LayoutBuilder(
      builder: (context, constraints) {
        final painter = TextPainter(
          text: TextSpan(text: widget.text, style: textStyle),
          textDirection: Directionality.of(context),
        )..layout(maxWidth: constraints.maxWidth - 14);
        final short = painter.computeLineMetrics().length < 2;
        return Container(
          key: const Key('assistant-reasoning-block'),
          margin: const EdgeInsets.only(bottom: 6),
          decoration: BoxDecoration(
            border: Border(
              left: BorderSide(
                color: theme.colorScheme.secondary.withValues(alpha: .5),
                width: 2,
              ),
            ),
          ),
          child: short
              ? KeyedSubtree(
                  key: const Key('reasoning-inline'),
                  child: Padding(
                    padding: const EdgeInsets.fromLTRB(8, 5, 4, 5),
                    child: MarkdownText(widget.text, baseStyle: textStyle),
                  ),
                )
              : Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Semantics(
                      button: true,
                      expanded: _open,
                      excludeSemantics: true,
                      label: _open
                          ? 'Collapse reasoning details'
                          : 'Expand reasoning details',
                      child: InkWell(
                        key: const Key('reasoning-toggle'),
                        onTap: () => setState(() => _open = !_open),
                        child: ConstrainedBox(
                          constraints: const BoxConstraints(minHeight: 48),
                          child: Padding(
                            padding: const EdgeInsets.fromLTRB(8, 4, 4, 4),
                            child: Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Icon(
                                  Icons.psychology_alt_outlined,
                                  size: 13,
                                  color: theme.hintColor,
                                ),
                                const SizedBox(width: 5),
                                Flexible(
                                  child: Text(
                                    _open
                                        ? 'reasoning'
                                        : 'reasoning (tap to expand)',
                                    style: theme.textTheme.labelSmall!.copyWith(
                                      color: theme.hintColor,
                                    ),
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),
                      ),
                    ),
                    if (_open)
                      Padding(
                        padding: const EdgeInsets.fromLTRB(8, 4, 4, 4),
                        child: MarkdownText(widget.text, baseStyle: textStyle),
                      ),
                  ],
                ),
        );
      },
    );
  }
}

class _SubagentContextBanner extends StatelessWidget {
  final int? position;
  final int? total;
  final Future<void> Function() onParent;
  final Future<void> Function() onAll;

  const _SubagentContextBanner({
    required this.position,
    required this.total,
    required this.onParent,
    required this.onAll,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final count = position != null && total != null
        ? '$position of $total'
        : 'Delegated session';
    return Material(
      color: theme.colorScheme.surfaceContainerLow,
      child: Padding(
        padding: const EdgeInsets.only(left: 16),
        child: Row(
          children: [
            Icon(
              Icons.subdirectory_arrow_right_rounded,
              color: theme.colorScheme.primary,
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Text(
                'Subagent · $count',
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: theme.textTheme.bodyMedium,
              ),
            ),
            IconButton(
              key: const ValueKey('subagent-parent-session'),
              tooltip: 'Open parent session',
              onPressed: onParent,
              icon: const Icon(Icons.arrow_upward_rounded),
            ),
            IconButton(
              key: const ValueKey('subagent-session-list'),
              tooltip: 'Show all subagent sessions',
              onPressed: onAll,
              icon: const Icon(Icons.account_tree_outlined),
            ),
          ],
        ),
      ),
    );
  }
}

class _SharedSessionBanner extends StatelessWidget {
  final String url;
  final VoidCallback onStop;

  const _SharedSessionBanner({required this.url, required this.onStop});

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Theme.of(context).colorScheme.tertiaryContainer,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(12, 8, 8, 8),
        child: Row(
          children: [
            const Icon(Icons.public_rounded, size: 20),
            const SizedBox(width: 8),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text('Shared: anyone with the link can view'),
                  Semantics(
                    label: 'Shared session link $url',
                    child: SelectableText(
                      url,
                      maxLines: 1,
                      style: const TextStyle(
                        fontFamily: 'AppMono',
                        fontSize: 11,
                      ),
                    ),
                  ),
                ],
              ),
            ),
            IconButton(
              tooltip: 'Copy share link',
              onPressed: () => Clipboard.setData(ClipboardData(text: url)),
              icon: const Icon(Icons.copy_rounded),
            ),
            TextButton(onPressed: onStop, child: const Text('Stop sharing')),
          ],
        ),
      ),
    );
  }
}

/// Drafts queued while the server is unreachable, shown truthfully as
/// not-yet-sent between the transcript and the composer.
class _QueuedPromptsStrip extends StatelessWidget {
  const _QueuedPromptsStrip({
    required this.entries,
    required this.onEdit,
    required this.onDiscard,
  });

  final List<QueuedPrompt> entries;
  final ValueChanged<QueuedPrompt> onEdit;
  final ValueChanged<QueuedPrompt> onDiscard;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 860, maxHeight: 180),
        child: SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              for (var index = 0; index < entries.length; index++)
                Padding(
                  padding: const EdgeInsets.fromLTRB(48, 2, 16, 2),
                  child: _QueuedPromptBubble(
                    key: ValueKey('queued-send-$index'),
                    entry: entries[index],
                    onEdit: () => onEdit(entries[index]),
                    onDiscard: () => onDiscard(entries[index]),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}

class _QueuedPromptBubble extends StatelessWidget {
  const _QueuedPromptBubble({
    super.key,
    required this.entry,
    required this.onEdit,
    required this.onDiscard,
  });

  final QueuedPrompt entry;
  final VoidCallback onEdit;
  final VoidCallback onDiscard;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final label = entry.error == null
        ? 'Queued — will send when reconnected'
        : 'Failed: ${entry.error}';
    return Semantics(
      button: true,
      label: 'Queued draft. $label',
      child: InkWell(
        borderRadius: BorderRadius.circular(14),
        onTap: () => _showActions(context),
        child: Container(
          padding: const EdgeInsets.fromLTRB(12, 8, 12, 8),
          decoration: BoxDecoration(
            color: theme.colorScheme.surfaceContainerLow,
            borderRadius: BorderRadius.circular(14),
            border: Border.all(
              color: entry.error == null
                  ? theme.colorScheme.outlineVariant
                  : theme.colorScheme.error.withValues(alpha: .6),
            ),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.end,
            mainAxisSize: MainAxisSize.min,
            children: [
              if (entry.text.isNotEmpty)
                Text(
                  entry.text,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: theme.textTheme.bodyMedium,
                ),
              if (entry.attachments.isNotEmpty)
                Text(
                  '${entry.attachments.length} attachment'
                  '${entry.attachments.length == 1 ? '' : 's'}',
                  style: theme.textTheme.labelSmall?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
              const SizedBox(height: 3),
              Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(
                    entry.error == null
                        ? Icons.schedule_rounded
                        : Icons.error_outline_rounded,
                    size: 13,
                    color: entry.error == null
                        ? theme.hintColor
                        : theme.colorScheme.error,
                  ),
                  const SizedBox(width: 5),
                  Flexible(
                    child: Text(
                      label,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: theme.textTheme.labelSmall?.copyWith(
                        color: entry.error == null
                            ? theme.hintColor
                            : theme.colorScheme.error,
                      ),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _showActions(BuildContext context) {
    showModalBottomSheet<void>(
      context: context,
      builder: (sheetContext) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              key: const ValueKey('queued-action-edit'),
              leading: const Icon(Icons.edit_outlined),
              title: const Text('Edit draft'),
              subtitle: const Text('Move it back into the composer'),
              onTap: () {
                Navigator.pop(sheetContext);
                onEdit();
              },
            ),
            ListTile(
              key: const ValueKey('queued-action-discard'),
              leading: const Icon(Icons.delete_outline_rounded),
              title: const Text('Discard draft'),
              onTap: () {
                Navigator.pop(sheetContext);
                onDiscard();
              },
            ),
          ],
        ),
      ),
    );
  }
}

