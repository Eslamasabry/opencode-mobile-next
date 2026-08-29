part of '../chat_screen.dart';

/// UX-P0-03: the three secondary prompt tools that used to sit as equal
/// icons around the field. They now live behind one leading affordance.
enum _PromptTool { commands, attach, voice }

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
    this.canSendWhileBusy = false,
    this.delivery = PromptDelivery.steer,
    this.onDeliveryChanged,
    required this.voiceOpening,
    required this.selectedAgent,
    required this.selectedModel,
    required this.selectedVariant,
    required this.onAttach,
    required this.onContentInserted,
    required this.onVoice,
    required this.onSend,
    this.onSendDelivery,
    required this.onStop,
    required this.onChooseModel,
    required this.onRemoveAttachment,
    // UX-103 review handoff (start).
    this.references = const [],
    this.onRemoveReference,
    // UX-103 review handoff (end).
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

  /// OpenCode 2 only (§5): Send stays live while a turn runs. The delivery
  /// control above the field says — and sets — what Send will do; long press
  /// on Send remains a shortcut to the same choice. On v1 the busy composer
  /// keeps its lone Stop button and no delivery control appears.
  final bool canSendWhileBusy;

  /// What Send does while a run is active. Only meaningful when
  /// [canSendWhileBusy] is true.
  final PromptDelivery delivery;

  /// Records an explicit delivery choice so the visible label keeps
  /// matching what Send will do.
  final ValueChanged<PromptDelivery>? onDeliveryChanged;
  final bool voiceOpening;
  final String selectedAgent;
  final ModelRef? selectedModel;
  final String selectedVariant;
  final VoidCallback onAttach;

  /// Receives images committed by the IME (keyboard image insertions and
  /// Android clipboard-image paste chips). See `_handleInsertedContent`.
  final ValueChanged<KeyboardInsertedContent> onContentInserted;
  final VoidCallback onVoice;
  final VoidCallback onSend;

  /// Sends with an explicit delivery mode from the long-press menu.
  final ValueChanged<PromptDelivery>? onSendDelivery;
  final VoidCallback onStop;
  final VoidCallback onChooseModel;
  final ValueChanged<PromptAttachment> onRemoveAttachment;

  // UX-103 review handoff (start): staged Files/Changes/Review references.
  // Unlike attachments these upload nothing — they become text in the
  // prompt when it is sent.
  final List<ReviewReference> references;
  final ValueChanged<ReviewReference>? onRemoveReference;
  // UX-103 review handoff (end).

  /// Fraction of the model's context window the session has consumed, or
  /// null when the limit is unknown.
  final double? contextUsage;

  bool get _hasPrompt =>
      controller.text.trim().isNotEmpty ||
      attachments.isNotEmpty ||
      references.isNotEmpty;

  void _submitFromKeyboard() {
    if (_hasPrompt && !sending && (!busy || canSendWhileBusy)) onSend();
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
                // UX-P0-03: on the compact (short) layout the field shares
                // its row with the leading tools button and Send, so the
                // model/agent context sits above the field as a header chip
                // instead of competing for that row's width. The wide layout
                // keeps it on the action row under the field, where it is
                // already secondary to the full-width field.
                if (compact)
                  Padding(
                    padding: const EdgeInsets.fromLTRB(12, 8, 12, 0),
                    child: Align(
                      alignment: AlignmentDirectional.centerStart,
                      child: _ModelContextChip(
                        label: _contextLabel,
                        onPressed: onChooseModel,
                      ),
                    ),
                  ),
                // UX-P0-04: while a run is active the consequence of Send
                // changes, so the choice is stated in words rather than
                // hidden behind a long press on an unchanged arrow. The
                // strip only exists while it applies, so the idle composer
                // gains no density from it.
                if (busy && canSendWhileBusy && onDeliveryChanged != null)
                  _DeliveryControl(
                    delivery: delivery,
                    onChanged: onDeliveryChanged!,
                  ),
                // UX-103 review handoff (start): staged references ride
                // above the attachment strip so the two never read as one
                // kind of thing.
                if (references.isNotEmpty) ...[
                  Padding(
                    padding: const EdgeInsets.fromLTRB(12, 10, 12, 0),
                    child: SizedBox(
                      height: 48,
                      child: ListView.separated(
                        key: const Key('composer-reference-strip'),
                        scrollDirection: Axis.horizontal,
                        itemCount: references.length,
                        separatorBuilder: (_, _) => const SizedBox(width: 6),
                        itemBuilder: (context, index) {
                          final reference = references[index];
                          return _StagedReferenceChip(
                            reference: reference,
                            onRemove: onRemoveReference == null
                                ? null
                                : () => onRemoveReference!(reference),
                          );
                        },
                      ),
                    ),
                  ),
                  Padding(
                    padding: const EdgeInsets.fromLTRB(14, 6, 14, 0),
                    child: Text(
                      key: const Key('composer-reference-note'),
                      // §7.4 again: staged references live in memory only, so
                      // say so here rather than letting a restart lose them
                      // silently — same promise the attachment note makes.
                      references.length == 1
                          ? '1 reference is added as text when you send. '
                                'Not saved with your draft.'
                          : '${references.length} references are added as '
                                'text when you send. Not saved with your '
                                'draft.',
                      style: theme.textTheme.labelSmall?.copyWith(
                        color: AppTheme.mutedOf(theme),
                      ),
                    ),
                  ),
                ],
                // UX-103 review handoff (end).
                if (attachments.isNotEmpty) ...[
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
                  // §7.4: draft text is persisted per session, attachment
                  // bytes are not. Say so at selection time rather than
                  // letting navigation lose them silently.
                  Padding(
                    padding: const EdgeInsets.fromLTRB(14, 6, 14, 0),
                    child: Text(
                      key: const Key('composer-attachment-draft-note'),
                      'Attachments are not saved with your draft.',
                      style: theme.textTheme.labelSmall?.copyWith(
                        color: AppTheme.mutedOf(theme),
                      ),
                    ),
                  ),
                ],
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
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        _ComposerField(
          controller: controller,
          focusNode: focusNode,
          onOpenEditor: onOpenEditor,
          onContentInserted: onContentInserted,
          maxLines: 6,
          contentPadding: const EdgeInsets.fromLTRB(16, 14, 16, 10),
          onSubmitShortcut: _submitFromKeyboard,
        ),
        _ContextMeterLine(usage: contextUsage),
        Padding(
          padding: const EdgeInsets.fromLTRB(8, 8, 8, 8),
          child: Row(
            children: [
              _PromptToolsButton(
                voiceOpening: voiceOpening,
                onSelected: _openTool,
              ),
              const SizedBox(width: 6),
              // The model/agent reads as context, not as a fifth equal
              // action: it flexes and ellipsizes so the row never pushes
              // Send off the edge at large text scales.
              Expanded(
                child: Align(
                  alignment: AlignmentDirectional.centerStart,
                  child: _ModelContextChip(
                    label: _contextLabel,
                    onPressed: onChooseModel,
                  ),
                ),
              ),
              const SizedBox(width: 6),
              _ComposerSubmit(
                busy: busy,
                sending: sending,
                enabled: _hasPrompt,
                canSendWhileBusy: canSendWhileBusy,
                delivery: delivery,
                onDeliveryChanged: onDeliveryChanged,
                onSend: onSend,
                onSendDelivery: onSendDelivery,
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
          _PromptToolsButton(voiceOpening: voiceOpening, onSelected: _openTool),
          const SizedBox(width: 4),
          Expanded(
            child: _ComposerField(
              controller: controller,
              focusNode: focusNode,
              onOpenEditor: onOpenEditor,
              onContentInserted: onContentInserted,
              onSubmitShortcut: _submitFromKeyboard,
              maxLines: 3,
              contentPadding: const EdgeInsets.symmetric(
                horizontal: 10,
                vertical: 12,
              ),
            ),
          ),
          const SizedBox(width: 4),
          _ComposerSubmit(
            busy: busy,
            sending: sending,
            enabled: _hasPrompt,
            canSendWhileBusy: canSendWhileBusy,
            delivery: delivery,
            onDeliveryChanged: onDeliveryChanged,
            onSend: onSend,
            onSendDelivery: onSendDelivery,
            onStop: onStop,
          ),
        ],
      ),
    );
  }

  /// Opens the tools sheet and runs the chosen action once the sheet has
  /// closed, so a tool that opens its own sheet (Commands, Voice) never
  /// races the dismissal of this one.
  Future<void> _openTool(BuildContext context) async {
    final tool = await showModalBottomSheet<_PromptTool>(
      context: context,
      useSafeArea: true,
      showDragHandle: true,
      // Three tiles at a 2.5x text scale outgrow the default half-screen
      // sheet on a short phone, so the surface may grow and scroll.
      isScrollControlled: true,
      constraints: const BoxConstraints(maxWidth: 720),
      builder: (_) => _PromptToolsSheet(
        blocked: busy || sending,
        attachmentCount: attachments.length,
      ),
    );
    switch (tool) {
      case null:
        return;
      case _PromptTool.commands:
        onOpenCommands();
      case _PromptTool.attach:
        onAttach();
      case _PromptTool.voice:
        onVoice();
    }
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
    required this.onContentInserted,
    required this.maxLines,
    required this.contentPadding,
    this.onSubmitShortcut,
  });

  final TextEditingController controller;
  final FocusNode focusNode;
  final VoidCallback onOpenEditor;
  final ValueChanged<KeyboardInsertedContent> onContentInserted;
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
      // Accepts images committed by the IME (Android commitContent): the
      // default allowed mime types cover the common raster image formats.
      contentInsertionConfiguration: ContentInsertionConfiguration(
        onContentInserted: onContentInserted,
      ),
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

/// UX-P0-03: one leading affordance for Commands, Attach, and Voice. It is
/// a plain [IconButton], so it keeps the 48 dp target, the tooltip, and the
/// keyboard/TalkBack behaviour the three separate buttons had.
class _PromptToolsButton extends StatelessWidget {
  const _PromptToolsButton({
    required this.voiceOpening,
    required this.onSelected,
  });

  /// Voice opening is the one tool with a visible pending state, so the
  /// collapsed button reports it rather than hiding it behind the sheet.
  final bool voiceOpening;
  final Future<void> Function(BuildContext context) onSelected;

  @override
  Widget build(BuildContext context) {
    return _ComposerAction(
      key: const Key('composer-tools-button'),
      tooltip: 'Prompt tools: commands, attach, voice',
      onPressed: () => unawaited(onSelected(context)),
      icon: voiceOpening
          ? const SizedBox.square(
              dimension: 18,
              child: CircularProgressIndicator(strokeWidth: 2),
            )
          : const Icon(Icons.add_rounded),
    );
  }
}

/// The compact tools surface behind [_PromptToolsButton]. Every entry is a
/// full-width [ListTile]: labelled, focusable, and comfortably past the
/// 48 dp minimum at any text scale.
class _PromptToolsSheet extends StatelessWidget {
  const _PromptToolsSheet({
    required this.blocked,
    required this.attachmentCount,
  });

  /// Attach and Voice stay unavailable while a turn is in flight, exactly as
  /// they were when they had their own buttons.
  final bool blocked;
  final int attachmentCount;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return SafeArea(
      top: false,
      child: SingleChildScrollView(
        child: Column(
          key: const Key('composer-tools-sheet'),
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 0, 20, 4),
              child: Text('Prompt tools', style: theme.textTheme.titleMedium),
            ),
            ListTile(
              key: const Key('composer-tool-commands'),
              leading: const Icon(AppIcons.run),
              title: const Text('Commands'),
              subtitle: const Text('Slash commands and agents'),
              onTap: () => Navigator.pop(context, _PromptTool.commands),
            ),
            ListTile(
              key: const Key('composer-tool-attach'),
              enabled: !blocked,
              leading: const Icon(Icons.attach_file_rounded),
              title: const Text('Attach file'),
              subtitle: Text(
                blocked
                    ? 'Available when the current run finishes'
                    : attachmentCount == 0
                    ? 'Add an image or file to the prompt'
                    : '$attachmentCount attached',
              ),
              onTap: blocked
                  ? null
                  : () => Navigator.pop(context, _PromptTool.attach),
            ),
            ListTile(
              key: const Key('composer-tool-voice'),
              enabled: !blocked,
              leading: const Icon(Icons.mic_none_rounded),
              title: const Text('Voice input'),
              subtitle: Text(
                blocked
                    ? 'Available when the current run finishes'
                    : 'Records and transcribes on this device',
              ),
              onTap: blocked
                  ? null
                  : () => Navigator.pop(context, _PromptTool.voice),
            ),
            const SizedBox(height: 8),
          ],
        ),
      ),
    );
  }
}

/// UX-P0-03: the model/agent/variant reads as context chrome — a chip that
/// states the current selection and opens the picker — rather than another
/// icon button with the same weight as Send.
class _ModelContextChip extends StatelessWidget {
  const _ModelContextChip({required this.label, required this.onPressed});

  final String label;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    return Tooltip(
      message: 'Model and agent: $label. Tap to change.',
      child: ActionChip(
        key: const Key('composer-model-context'),
        avatar: Icon(
          Icons.tune_rounded,
          size: 16,
          color: scheme.onSurfaceVariant,
        ),
        label: Text(label, maxLines: 1, overflow: TextOverflow.ellipsis),
        labelStyle: theme.textTheme.labelLarge?.copyWith(
          color: scheme.onSurfaceVariant,
        ),
        backgroundColor: Colors.transparent,
        side: BorderSide(color: scheme.outlineVariant),
        // The chip reads as chrome, but its target still has to clear the
        // 48 dp Android minimum, so the tap area stays padded.
        materialTapTargetSize: MaterialTapTargetSize.padded,
        onPressed: onPressed,
      ),
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
    this.canSendWhileBusy = false,
    this.delivery = PromptDelivery.steer,
    this.onDeliveryChanged,
    required this.onSend,
    this.onSendDelivery,
    required this.onStop,
  });

  final bool busy;
  final bool sending;
  final bool enabled;
  final bool canSendWhileBusy;
  final PromptDelivery delivery;
  final ValueChanged<PromptDelivery>? onDeliveryChanged;
  final VoidCallback onSend;
  final ValueChanged<PromptDelivery>? onSendDelivery;
  final VoidCallback onStop;

  @override
  Widget build(BuildContext context) {
    final reduceMotion = MediaQuery.disableAnimationsOf(context);
    return AnimatedSwitcher(
      duration: reduceMotion
          ? Duration.zero
          : const Duration(milliseconds: 150),
      child: _slot(context),
    );
  }

  Widget _slot(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    if (busy && !canSendWhileBusy) {
      // v1 semantics unchanged: sending is impossible while a turn runs.
      return IconButton.filledTonal(
        key: const Key('chat-send-button'),
        tooltip: 'Stop',
        onPressed: onStop,
        style: IconButton.styleFrom(
          foregroundColor: scheme.error,
          backgroundColor: scheme.errorContainer.withValues(alpha: .55),
        ),
        icon: const Icon(AppIcons.stop),
      );
    }
    final icon = sending
        ? const SizedBox.square(
            dimension: 18,
            child: CircularProgressIndicator(strokeWidth: 2),
          )
        : const Icon(Icons.arrow_upward_rounded);
    if (!busy) {
      return IconButton.filled(
        key: const Key('chat-send-button'),
        tooltip: 'Send',
        onPressed: sending || !enabled ? null : onSend,
        icon: icon,
      );
    }
    // While busy the button carries no tooltip: Tooltip installs its own
    // long-press recognizer, which would swallow the delivery menu gesture.
    // The hint lives in the semantics label instead, and the visible
    // delivery control above the field carries the same choice.
    final send = Semantics(
      label: delivery == PromptDelivery.queue
          ? 'Send — queues after the current run. Long press to choose '
                'delivery.'
          : 'Send — steers the current run. Long press to choose delivery.',
      child: IconButton.filled(
        key: const Key('chat-send-button'),
        onPressed: sending || !enabled ? null : onSend,
        icon: icon,
      ),
    );
    // OpenCode 2 while busy: Stop keeps its own adjacent button; tap Send
    // steers (the v2 default), long-press offers the delivery choice.
    return Row(
      key: const Key('chat-busy-submit-row'),
      mainAxisSize: MainAxisSize.min,
      children: [
        IconButton.filledTonal(
          key: const Key('chat-stop-button'),
          tooltip: 'Stop',
          onPressed: onStop,
          style: IconButton.styleFrom(
            foregroundColor: scheme.error,
            backgroundColor: scheme.errorContainer.withValues(alpha: .55),
          ),
          icon: const Icon(AppIcons.stop),
        ),
        const SizedBox(width: 4),
        GestureDetector(
          onLongPress: sending || !enabled
              ? null
              : () => _showDeliveryMenu(context),
          child: send,
        ),
      ],
    );
  }

  void _showDeliveryMenu(BuildContext context) {
    final onDelivery = onSendDelivery;
    if (onDelivery == null) return;
    void choose(BuildContext sheetContext, PromptDelivery choice) {
      Navigator.pop(sheetContext);
      // Keep the visible control in step with the shortcut: the label has
      // to keep telling the truth about what Send does next.
      onDeliveryChanged?.call(choice);
      onDelivery(choice);
    }

    showModalBottomSheet<void>(
      context: context,
      builder: (sheetContext) => SafeArea(
        child: Column(
          key: const Key('send-delivery-menu'),
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              key: const Key('send-delivery-steer'),
              leading: const Icon(AppIcons.run),
              title: const Text('Send now'),
              subtitle: const Text('Steers the current run'),
              selected: delivery == PromptDelivery.steer,
              onTap: () => choose(sheetContext, PromptDelivery.steer),
            ),
            ListTile(
              key: const Key('send-delivery-queue'),
              leading: const Icon(AppIcons.queue),
              title: const Text('Queue for after this run'),
              subtitle: const Text('Waits for the current run to finish'),
              selected: delivery == PromptDelivery.queue,
              onTap: () => choose(sheetContext, PromptDelivery.queue),
            ),
          ],
        ),
      ),
    );
  }
}

/// UX-P0-04: the labelled delivery choice that replaces long-press-only
/// knowledge. It appears only while a run is active on a server that
/// supports the inbox, states in words what Send will do, and stays
/// operable by keyboard and TalkBack because it is two real buttons.
class _DeliveryControl extends StatelessWidget {
  const _DeliveryControl({required this.delivery, required this.onChanged});

  final PromptDelivery delivery;
  final ValueChanged<PromptDelivery> onChanged;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      key: const Key('composer-delivery-control'),
      padding: const EdgeInsets.fromLTRB(12, 10, 12, 0),
      child: Wrap(
        spacing: 8,
        runSpacing: 4,
        crossAxisAlignment: WrapCrossAlignment.center,
        children: [
          Text(
            'While running',
            style: theme.textTheme.labelSmall?.copyWith(
              color: AppTheme.mutedOf(theme),
            ),
          ),
          _DeliveryChoice(
            buttonKey: const Key('composer-delivery-steer'),
            icon: AppIcons.run,
            label: 'Steer',
            hint: 'Send now and steer the current run',
            selected: delivery == PromptDelivery.steer,
            onSelected: () => onChanged(PromptDelivery.steer),
          ),
          _DeliveryChoice(
            buttonKey: const Key('composer-delivery-queue'),
            icon: AppIcons.queue,
            label: 'Queue',
            hint: 'Wait for the current run to finish, then send',
            selected: delivery == PromptDelivery.queue,
            onSelected: () => onChanged(PromptDelivery.queue),
          ),
        ],
      ),
    );
  }
}

class _DeliveryChoice extends StatelessWidget {
  const _DeliveryChoice({
    required this.buttonKey,
    required this.icon,
    required this.label,
    required this.hint,
    required this.selected,
    required this.onSelected,
  });

  final Key buttonKey;
  final IconData icon;
  final String label;
  final String hint;
  final bool selected;
  final VoidCallback onSelected;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      // The chip announces its own selected state; the hint explains the
      // consequence, which is the part a long press used to hide.
      hint: hint,
      child: Tooltip(
        message: hint,
        child: ChoiceChip(
          key: buttonKey,
          avatar: Icon(icon, size: 16),
          label: Text(label),
          selected: selected,
          showCheckmark: false,
          onSelected: (_) => onSelected(),
        ),
      ),
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

/// UX-103: a review finding staged from Files, Changes, or the Review
/// workspace. It mirrors [_PendingAttachmentChip]'s shape so the strip reads
/// as one family, but stays visibly a *reference* — a tinted chip with a
/// code glyph and a line range, not a paperclip and a filename — because
/// nothing is uploaded: on send it becomes text in the prompt.
class _StagedReferenceChip extends StatelessWidget {
  const _StagedReferenceChip({required this.reference, required this.onRemove});

  final ReviewReference reference;
  final VoidCallback? onRemove;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final removeLabel = 'Remove reference ${reference.label}';
    return Material(
      key: Key('composer-reference-${reference.id}'),
      color: scheme.secondaryContainer,
      shape: StadiumBorder(side: BorderSide(color: scheme.outlineVariant)),
      clipBehavior: Clip.antiAlias,
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Semantics(
            container: true,
            excludeSemantics: true,
            label: reference.description,
            child: Tooltip(
              message: reference.description,
              child: ConstrainedBox(
                constraints: const BoxConstraints(minHeight: 48),
                child: Padding(
                  padding: const EdgeInsets.only(left: 12),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(
                        switch (reference.kind) {
                          ReviewReferenceKind.comment =>
                            Icons.mode_comment_outlined,
                          ReviewReferenceKind.file =>
                            Icons.description_outlined,
                          ReviewReferenceKind.changedFile =>
                            Icons.difference_outlined,
                          _ => Icons.code_rounded,
                        },
                        size: 16,
                        color: scheme.onSecondaryContainer,
                      ),
                      const SizedBox(width: 6),
                      ConstrainedBox(
                        constraints: const BoxConstraints(maxWidth: 200),
                        child: Text(
                          reference.label,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(color: scheme.onSecondaryContainer),
                        ),
                      ),
                    ],
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
              icon: Icon(
                Icons.close_rounded,
                size: 18,
                color: scheme.onSecondaryContainer,
              ),
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
