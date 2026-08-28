part of '../chat_screen.dart';

class _ChatComposer extends StatelessWidget {
  const _ChatComposer({
    required this.compact,
    required this.allowInlineCommands,
    required this.controller,
    required this.focusNode,
    required this.commands,
    required this.agents,
    required this.onSelectCommand,
    required this.onSelectAgent,
    required this.onOpenCommands,
    required this.onOpenAgents,
    required this.onOpenEditor,
    required this.attachments,
    required this.busy,
    required this.sending,
    required this.voiceOpening,
    required this.selectedAgent,
    required this.selectedModel,
    required this.selectedVariant,
    required this.onAttach,
    required this.onVoice,
    required this.onSend,
    required this.onStop,
    required this.onChooseModel,
    required this.onRemoveAttachment,
    this.contextUsage,
  });

  final bool compact;
  final bool allowInlineCommands;
  final TextEditingController controller;
  final FocusNode focusNode;
  final List<_ChatCommand> commands;
  final List<CatalogAgent> agents;
  final ValueChanged<_ChatCommand> onSelectCommand;
  final ValueChanged<CatalogAgent> onSelectAgent;
  final VoidCallback onOpenCommands;
  final VoidCallback onOpenAgents;
  final VoidCallback onOpenEditor;
  final List<PromptAttachment> attachments;
  final bool busy;
  final bool sending;
  final bool voiceOpening;
  final String selectedAgent;
  final ModelRef? selectedModel;
  final String selectedVariant;
  final VoidCallback onAttach;
  final VoidCallback onVoice;
  final VoidCallback onSend;
  final VoidCallback onStop;
  final VoidCallback onChooseModel;
  final ValueChanged<PromptAttachment> onRemoveAttachment;

  /// Fraction of the model's context window the session has consumed, or
  /// null when the limit is unknown.
  final double? contextUsage;

  bool get _hasPrompt =>
      controller.text.trim().isNotEmpty || attachments.isNotEmpty;

  void _submitFromKeyboard() {
    if (_hasPrompt && !busy && !sending) onSend();
  }

  @override
  Widget build(BuildContext context) {
    final media = MediaQuery.of(context);
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final disableAnimations = media.disableAnimations;

    return SafeArea(
      top: false,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(10, 6, 10, 10),
        child: ListenableBuilder(
          listenable: Listenable.merge([focusNode, controller]),
          builder: (context, _) => AnimatedContainer(
            key: const Key('chat-composer-surface'),
            duration: disableAnimations
                ? Duration.zero
                : const Duration(milliseconds: 180),
            curve: Curves.easeOutCubic,
            decoration: BoxDecoration(
              color: scheme.surfaceContainerLow,
              borderRadius: BorderRadius.circular(compact ? 20 : 24),
              border: Border.all(
                color: focusNode.hasFocus
                    ? scheme.primary.withValues(alpha: .8)
                    : scheme.outlineVariant.withValues(alpha: .85),
                width: focusNode.hasFocus ? 1.4 : 1,
              ),
              boxShadow: [
                BoxShadow(
                  color: scheme.surfaceContainerLowest.withValues(alpha: .5),
                  blurRadius: 20,
                  offset: const Offset(0, 10),
                ),
              ],
            ),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                if (allowInlineCommands && _slashQuery != null)
                  _InlineCommandSuggestions(
                    commands: commands,
                    query: _slashQuery!,
                    compact: compact,
                    onSelected: onSelectCommand,
                    onShowAll: onOpenCommands,
                  ),
                if (allowInlineCommands && _agentQuery != null)
                  _InlineAgentSuggestions(
                    agents: agents,
                    query: _agentQuery!.query,
                    compact: compact,
                    onSelected: onSelectAgent,
                    onShowAll: onOpenAgents,
                  ),
                if (attachments.isNotEmpty)
                  Padding(
                    padding: const EdgeInsets.fromLTRB(12, 10, 12, 0),
                    child: SizedBox(
                      height: 56,
                      child: ListView.separated(
                        scrollDirection: Axis.horizontal,
                        itemCount: attachments.length,
                        separatorBuilder: (_, _) => const SizedBox(width: 6),
                        itemBuilder: (context, index) {
                          final attachment = attachments[index];
                          return _PendingAttachmentChip(
                            attachment: attachment,
                            onRemove: () => onRemoveAttachment(attachment),
                          );
                        },
                      ),
                    ),
                  ),
                if (compact)
                  _compactComposer(context)
                else
                  _standardComposer(context),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _standardComposer(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        _ComposerField(
          controller: controller,
          focusNode: focusNode,
          onOpenEditor: onOpenEditor,
          maxLines: 6,
          contentPadding: const EdgeInsets.fromLTRB(16, 14, 16, 10),
          onSubmitShortcut: _submitFromKeyboard,
        ),
        _ContextMeterLine(usage: contextUsage),
        Padding(
          padding: const EdgeInsets.fromLTRB(8, 7, 8, 8),
          child: Row(
            children: [
              _ComposerAction(
                key: const Key('command-launcher-button'),
                tooltip: 'Commands',
                onPressed: onOpenCommands,
                icon: const Icon(Icons.electric_bolt_outlined),
              ),
              const SizedBox(width: 2),
              _ComposerAction(
                tooltip: 'Attach file',
                onPressed: busy || sending ? null : onAttach,
                icon: const Icon(Icons.attach_file_rounded),
              ),
              const SizedBox(width: 2),
              _ComposerAction(
                key: const Key('voice-input-button'),
                tooltip: 'Local voice input',
                onPressed: busy || sending ? null : onVoice,
                icon: voiceOpening
                    ? const SizedBox.square(
                        dimension: 18,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.mic_none_rounded),
              ),
              const SizedBox(width: 6),
              Expanded(
                child: Align(
                  alignment: Alignment.centerLeft,
                  child: TextButton.icon(
                    key: const Key('composer-model-context'),
                    onPressed: onChooseModel,
                    icon: const Icon(Icons.tune_rounded, size: 17),
                    label: Text(
                      _contextLabel,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                    style: TextButton.styleFrom(
                      foregroundColor: scheme.onSurfaceVariant,
                      padding: const EdgeInsets.symmetric(horizontal: 10),
                    ),
                  ),
                ),
              ),
              const SizedBox(width: 6),
              _ComposerSubmit(
                busy: busy,
                sending: sending,
                enabled: _hasPrompt,
                onSend: onSend,
                onStop: onStop,
              ),
            ],
          ),
        ),
      ],
    );
  }

  Widget _compactComposer(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(6),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          _ComposerAction(
            key: const Key('command-launcher-button'),
            tooltip: 'Commands',
            onPressed: onOpenCommands,
            icon: const Icon(Icons.electric_bolt_outlined),
          ),
          _ComposerAction(
            key: const Key('composer-model-context'),
            tooltip: _contextLabel,
            onPressed: onChooseModel,
            icon: const Icon(Icons.tune_rounded),
          ),
          _ComposerAction(
            tooltip: 'Attach file',
            onPressed: busy || sending ? null : onAttach,
            icon: const Icon(Icons.attach_file_rounded),
          ),
          _ComposerAction(
            key: const Key('voice-input-button'),
            tooltip: 'Local voice input',
            onPressed: busy || sending ? null : onVoice,
            icon: voiceOpening
                ? const SizedBox.square(
                    dimension: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.mic_none_rounded),
          ),
          Expanded(
            child: _ComposerField(
              controller: controller,
              focusNode: focusNode,
              onOpenEditor: onOpenEditor,
              onSubmitShortcut: _submitFromKeyboard,
              maxLines: 3,
              contentPadding: const EdgeInsets.symmetric(
                horizontal: 10,
                vertical: 11,
              ),
            ),
          ),
          const SizedBox(width: 4),
          _ComposerSubmit(
            busy: busy,
            sending: sending,
            enabled: _hasPrompt,
            onSend: onSend,
            onStop: onStop,
          ),
        ],
      ),
    );
  }

  String get _contextLabel {
    final parts = <String>[];
    if (selectedAgent.isNotEmpty) parts.add(selectedAgent);
    final model = selectedModel?.modelID;
    if (model != null && model.isNotEmpty) parts.add(model);
    if (selectedVariant.isNotEmpty) parts.add(selectedVariant);
    return parts.isEmpty ? 'Choose model' : parts.join(' · ');
  }

  String? get _slashQuery {
    final match = RegExp(r'^/(\S*)$').firstMatch(controller.text.trimLeft());
    return match?.group(1);
  }

  ({int start, int end, String query})? get _agentQuery =>
      _activeAgentQuery(controller.value);
}

class _ComposerField extends StatelessWidget {
  const _ComposerField({
    required this.controller,
    required this.focusNode,
    required this.onOpenEditor,
    required this.maxLines,
    required this.contentPadding,
    this.onSubmitShortcut,
  });

  final TextEditingController controller;
  final FocusNode focusNode;
  final VoidCallback onOpenEditor;
  final int maxLines;
  final EdgeInsets contentPadding;

  /// Ctrl/Cmd+Enter sends from hardware keyboards (Android with a keyboard
  /// attached today, desktop later) without stealing plain Enter from the
  /// multiline field.
  final VoidCallback? onSubmitShortcut;

  @override
  Widget build(BuildContext context) {
    final field = TextField(
      key: const Key('chat-composer-field'),
      controller: controller,
      focusNode: focusNode,
      minLines: 1,
      maxLines: maxLines,
      textCapitalization: TextCapitalization.sentences,
      decoration: InputDecoration(
        hintText: 'Ask OpenCode…',
        suffixIcon: IconButton(
          key: const Key('prompt-editor-button'),
          tooltip: 'Open full-screen prompt editor',
          onPressed: onOpenEditor,
          icon: const Icon(Icons.open_in_full_rounded, size: 19),
        ),
        filled: false,
        border: InputBorder.none,
        enabledBorder: InputBorder.none,
        focusedBorder: InputBorder.none,
        contentPadding: contentPadding,
      ),
    );
    final onSubmit = onSubmitShortcut;
    if (onSubmit == null) return field;
    return CallbackShortcuts(
      bindings: {
        const SingleActivator(LogicalKeyboardKey.enter, control: true):
            onSubmit,
        const SingleActivator(LogicalKeyboardKey.enter, meta: true): onSubmit,
      },
      child: field,
    );
  }
}

class _ComposerAction extends StatelessWidget {
  const _ComposerAction({
    super.key,
    required this.tooltip,
    required this.onPressed,
    required this.icon,
  });

  final String tooltip;
  final VoidCallback? onPressed;
  final Widget icon;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return IconButton(
      tooltip: tooltip,
      onPressed: onPressed,
      style: IconButton.styleFrom(
        backgroundColor: scheme.surfaceContainerHigh,
        foregroundColor: scheme.onSurfaceVariant,
        disabledBackgroundColor: scheme.surfaceContainerHigh.withValues(
          alpha: .45,
        ),
      ),
      icon: icon,
    );
  }
}

class _ComposerSubmit extends StatelessWidget {
  const _ComposerSubmit({
    required this.busy,
    required this.sending,
    required this.enabled,
    required this.onSend,
    required this.onStop,
  });

  final bool busy;
  final bool sending;
  final bool enabled;
  final VoidCallback onSend;
  final VoidCallback onStop;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    if (busy) {
      return IconButton.filledTonal(
        key: const Key('chat-send-button'),
        tooltip: 'Stop',
        onPressed: onStop,
        style: IconButton.styleFrom(
          foregroundColor: scheme.error,
          backgroundColor: scheme.errorContainer.withValues(alpha: .55),
        ),
        icon: const Icon(Icons.stop_rounded),
      );
    }
    return IconButton.filled(
      key: const Key('chat-send-button'),
      tooltip: 'Send',
      onPressed: sending || !enabled ? null : onSend,
      icon: sending
          ? const SizedBox.square(
              dimension: 18,
              child: CircularProgressIndicator(strokeWidth: 2),
            )
          : const Icon(Icons.arrow_upward_rounded),
    );
  }
}

class _PendingAttachmentChip extends StatelessWidget {
  const _PendingAttachmentChip({
    required this.attachment,
    required this.onRemove,
  });

  final PromptAttachment attachment;
  final VoidCallback onRemove;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final reference = attachment.isDirectoryReference;
    final removeLabel = reference
        ? 'Remove reference @${attachment.filename}'
        : 'Remove attachment ${attachment.filename}';
    void openPreview() => showFilePreviewSheet(
      context,
      FilePreviewData.fromDataUrl(
        name: attachment.filename,
        mimeType: attachment.mime,
        url: attachment.url,
      ),
    );

    return Material(
      color: theme.colorScheme.surfaceContainerHighest,
      shape: StadiumBorder(
        side: BorderSide(color: theme.colorScheme.outlineVariant),
      ),
      clipBehavior: Clip.antiAlias,
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Semantics(
            button: !reference,
            excludeSemantics: true,
            label: reference
                ? 'Reference @${attachment.filename}'
                : 'Preview attachment ${attachment.filename}',
            onTap: reference ? null : openPreview,
            child: Tooltip(
              message: reference
                  ? 'Project reference @${attachment.filename}'
                  : 'Preview ${attachment.filename}',
              child: InkWell(
                onTap: reference ? null : openPreview,
                child: ConstrainedBox(
                  constraints: const BoxConstraints(minHeight: 48),
                  child: Padding(
                    padding: const EdgeInsets.only(left: 12),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(
                          reference
                              ? Icons.bookmark_outline_rounded
                              : Icons.attach_file_rounded,
                          size: 16,
                        ),
                        const SizedBox(width: 6),
                        ConstrainedBox(
                          constraints: const BoxConstraints(maxWidth: 180),
                          child: Text(
                            reference
                                ? '@${attachment.filename}'
                                : attachment.filename,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                        if (!reference) ...[
                          const SizedBox(width: 8),
                          const Icon(Icons.visibility_outlined, size: 16),
                        ],
                      ],
                    ),
                  ),
                ),
              ),
            ),
          ),
          Semantics(
            container: true,
            excludeSemantics: true,
            label: removeLabel,
            button: true,
            onTap: onRemove,
            child: IconButton(
              tooltip: removeLabel,
              constraints: const BoxConstraints.tightFor(width: 48, height: 48),
              onPressed: onRemove,
              icon: const Icon(Icons.close_rounded, size: 18),
            ),
          ),
        ],
      ),
    );
  }
}


/// The hairline between the prompt field and the composer actions doubles as
/// an ambient context-window meter: it fills from the left as the session
/// consumes the model's context, staying a plain divider when no limit is
/// known. Colors escalate as the window approaches full.
class _ContextMeterLine extends StatelessWidget {
  const _ContextMeterLine({required this.usage});

  final double? usage;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final track = scheme.outlineVariant.withValues(alpha: .55);
    final value = usage?.clamp(0.0, 1.0);
    if (value == null) {
      return Divider(height: 1, indent: 14, endIndent: 14, color: track);
    }
    final fill = value >= .9
        ? scheme.error
        : value >= .7
        ? scheme.tertiary
        : scheme.primary;
    final percent = (value * 100).round();
    final reduceMotion = MediaQuery.disableAnimationsOf(context);
    return Semantics(
      label: 'Context window $percent percent used',
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 14),
        child: ClipRRect(
          key: const ValueKey('composer-context-meter'),
          borderRadius: BorderRadius.circular(1.25),
          child: SizedBox(
            height: 2.5,
            width: double.infinity,
            child: ColoredBox(
              color: track,
              child: TweenAnimationBuilder<double>(
                tween: Tween(begin: 0, end: value),
                duration: reduceMotion
                    ? Duration.zero
                    : const Duration(milliseconds: 400),
                curve: Curves.easeOutCubic,
                builder: (context, t, _) => Align(
                  alignment: AlignmentDirectional.centerStart,
                  child: FractionallySizedBox(
                    widthFactor: t.clamp(0.0, 1.0),
                    // Both factors must be tight: with a loose height the
                    // fill box collapses to zero and only the track paints.
                    heightFactor: 1,
                    child: ColoredBox(
                      key: const ValueKey('composer-context-meter-fill'),
                      color: fill,
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
