part of '../chat_screen.dart';

// The chat's composer (chat-3): the glass pill is [KitComposer]; the model
// chip, the attachments and the `/` and `@` suggestions are
// [KitComposerChips]; the "+" tools open one [showKitSheet]. This file only
// maps the chat's state onto those parts.

/// What the "+" sheet can start. The sheet returns one and the composer runs
/// it after the sheet has closed, so a tool that opens its own sheet never
/// races this one's dismissal.
enum _PromptTool {
  commands,
  attach,
  webSources,
  gallery,
  camera,
  voice,
  conversation,
  history,
  clearText,
  stash,
  saved,
}

class _ChatComposer extends StatelessWidget {
  const _ChatComposer({
    this.agentName,
    this.cardWaiting = false,
    this.blockedReason,
    required this.compact,
    this.isolated = false,
    this.maxInputHeight = double.infinity,
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
    this.onReusePrompt,
    this.onClearText,
    this.onStashPrompt,
    this.onOpenStash,
    this.onRestoreHistoryDraft,
    this.shelfBusy = false,
    this.shelfLoading = true,
    required this.attachments,
    required this.promptAttachmentsSupported,
    this.promptImagesOnly = false,
    required this.webSourcesSupported,
    required this.busy,
    this.model,
    this.onStop,
    this.stopping = false,
    required this.sending,
    this.canSendWhileBusy = false,
    this.canChooseDelivery = false,
    // P6.6: "Send after this reply" is the default; steering is the choice.
    this.delivery = PromptDelivery.queue,
    this.onDeliveryChanged,
    required this.voiceOpening,
    this.showAttachmentNote = true,
    required this.onAttach,
    required this.onPhotoLibrary,
    required this.onCamera,
    required this.onContentInserted,
    required this.onVoice,
    required this.onConversation,
    required this.onWebSources,
    this.conversationMode = false,
    this.voice,
    required this.onSend,
    required this.onRemoveAttachment,
    this.references = const [],
    this.onRemoveReference,
  });

  /// Who the prompt goes to ("Claude Code"); null means OpenCode.
  final String? agentName;

  /// An agent card of this conversation waits: the hint says the answer can
  /// also be typed.
  final bool cardWaiting;

  /// Why this conversation takes no message (a Claude sub-agent answers
  /// only its main conversation); the field says it instead.
  final String? blockedReason;

  /// A phone-sized window: fewer inline suggestions.
  final bool compact;
  final bool isolated;

  /// Kept for the host (KIT-43); the pill caps its own field at 40 % of the
  /// window (KitLayout.composerMaxShare).
  final double maxInputHeight;
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
  final VoidCallback? onReusePrompt;
  final VoidCallback? onClearText;
  final VoidCallback? onStashPrompt;
  final VoidCallback? onOpenStash;
  final VoidCallback? onRestoreHistoryDraft;
  final bool shelfBusy;
  final bool shelfLoading;
  final List<PromptAttachment> attachments;
  final bool promptAttachmentsSupported;

  /// Pictures only (an agent on this phone): photos and camera, no files.
  final bool promptImagesOnly;
  final bool webSourcesSupported;
  final bool busy;

  /// The model chip, only in a window too tight for the line above the
  /// field (see [KitComposer.model]).
  final Widget? model;

  /// Stop: the composer's Send becomes Stop while a reply runs. Null while
  /// the prompt is still on its way (nothing to stop yet).
  final VoidCallback? onStop;
  final bool stopping;
  final bool sending;

  /// Send stays live while a reply is written (OpenCode 1 runs it after the
  /// reply; OpenCode 2 queues or steers it).
  final bool canSendWhileBusy;

  /// OpenCode 2's inbox: a send made during a reply can wait for it or add
  /// to this turn. Without it the pill says "Sends after this reply".
  final bool canChooseDelivery;

  /// What Send does while a reply is being written.
  final PromptDelivery delivery;
  final ValueChanged<PromptDelivery>? onDeliveryChanged;

  /// Kept for the host (KIT-43): the voice sheet shows its own progress.
  final bool voiceOpening;

  /// The "saved with your draft" note shows once per session.
  final bool showAttachmentNote;

  final VoidCallback onAttach;
  final VoidCallback onPhotoLibrary;
  final VoidCallback onCamera;

  /// Images committed by the IME (keyboard images, clipboard-image chips).
  final ValueChanged<KeyboardInsertedContent> onContentInserted;
  final VoidCallback onVoice;
  final VoidCallback onConversation;
  final VoidCallback onWebSources;
  final bool conversationMode;

  /// Non-null: the pill is in voice mode (P10.3), dictating or in a voice
  /// conversation.
  final KitComposerVoice? voice;
  final VoidCallback onSend;
  final ValueChanged<PromptAttachment> onRemoveAttachment;

  /// Staged Files/Changes/Review references: they upload nothing and become
  /// text in the prompt when it is sent.
  final List<ReviewReference> references;
  final ValueChanged<ReviewReference>? onRemoveReference;

  bool get _hasAttachments => attachments.isNotEmpty || references.isNotEmpty;

  bool get _hasPrompt => controller.text.trim().isNotEmpty || _hasAttachments;

  /// Attaching has to wait for a reply only where Send itself must wait.
  bool get _attachBlocked =>
      sending || shelfBusy || (busy && !canSendWhileBusy);

  void _send() {
    if (_hasPrompt && !sending && !shelfBusy && (!busy || canSendWhileBusy)) {
      onSend();
    }
  }

  @override
  Widget build(BuildContext context) {
    final conn = ProviderScope.containerOf(
      context,
      listen: false,
    ).read(connProvider);
    return ListenableBuilder(
      listenable: Listenable.merge([conn, controller]),
      builder: (context, _) => _layout(context, conn),
    );
  }

  Widget _layout(BuildContext context, ConnectionController conn) {
    final tokens = KitTokens.of(context);
    final l10n = _chatL10n(context);
    // Offline compose queues when the server keeps a queue (P4.3); Send says
    // so in its own words.
    final offline =
        conn.status != StreamStatus.connected &&
        conn.capabilities.offlinePromptQueue;
    final restore = onRestoreHistoryDraft;
    // The floating layer draws the gutters, the bottom edge and the safe
    // area ([KitComposer.layer]).
    return Padding(
      padding: EdgeInsetsDirectional.only(top: tokens.space1),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          if (!conversationMode && restore != null)
            Align(
              alignment: AlignmentDirectional.centerStart,
              child: KitButton.tertiary(
                key: const Key('composer-restore-history-draft'),
                icon: AppIconography.undo,
                label: l10n.promptOriginalDraft,
                onPressed: shelfBusy ? null : restore,
              ),
            ),
          KitComposer(
            controller: controller,
            focusNode: focusNode,
            hint: cardWaiting
                ? l10n.agentCardComposerHint
                : agentName == null
                ? l10n.chatUiAskOpenCode
                : l10n.chatUiAskAgent(KitBidi.auto(agentName!)),
            onSend: _send,
            model: model,
            // Send becomes Stop while a reply runs (the only Stop); the mic
            // stays beside it, so speaking or typing waits to send after
            // the reply. What the reply is doing is written in the turn.
            busy: busy,
            onStop: onStop,
            stopping: stopping,
            sending: sending || (shelfBusy && shelfLoading),
            canSendWhileBusy: canSendWhileBusy,
            // Without an inbox (OpenCode 1) a send made during a reply
            // always runs after it, whatever the host remembers.
            delivery: canChooseDelivery && delivery == PromptDelivery.steer
                ? KitComposerDelivery.addToThisTurn
                : KitComposerDelivery.afterThisReply,
            onDeliveryChanged: canChooseDelivery && onDeliveryChanged != null
                ? (value) => onDeliveryChanged!(
                    value == KitComposerDelivery.addToThisTurn
                        ? PromptDelivery.steer
                        : PromptDelivery.queue,
                  )
                : null,
            offline: offline,
            readOnlyReason:
                blockedReason ??
                (!shelfBusy
                    ? null
                    : shelfLoading
                    ? l10n.composerBusyReason
                    : l10n.composerDraftBlockedReason),
            note: _note(context),
            hasAttachments: _hasAttachments,
            attachments: _attachmentChips(context),
            suggestions: _suggestions(context),
            onTools: isolated || conversationMode
                ? null
                : () => unawaited(_openTools(context)),
            onVoice:
                isolated ||
                    conversationMode ||
                    !platformCapabilities.supportsVoice
                ? null
                : onVoice,
            onOpenEditor: isolated || conversationMode ? null : onOpenEditor,
            voice: voice,
            onContentInserted: isolated || !promptAttachmentsSupported
                ? null
                : onContentInserted,
            fieldLabel: l10n.composerFieldLabel,
            composerKey: const Key('chat-composer-surface'),
            fieldKey: const Key('chat-composer-field'),
            sendKey: const Key('chat-send-button'),
            stopKey: const Key('chat-stop-button'),
            toolsKey: const Key('composer-tools-button'),
            voiceButtonKey: const Key('composer-voice-button'),
            editorKey: const Key('prompt-editor-button'),
            deliveryKey: const Key('composer-delivery-control'),
          ),
        ],
      ),
    );
  }

  /// The pill's one muted line: what the attachments and references do
  /// with the draft.
  String? _note(BuildContext context) {
    final l10n = _chatL10n(context);
    final parts = <String>[
      if (references.length == 1) l10n.chatUi1ReferenceIsAddedAsTextWhen,
      if (references.length > 1)
        l10n.chatUiReferencesAttachedNotice(references.length),
      if (attachments.isNotEmpty && showAttachmentNote)
        promptAttachmentsSupported
            ? l10n.draftAttachmentsLocal
            : l10n.codexTextOnlyPrompt,
    ];
    return parts.isEmpty ? null : parts.join(' ');
  }

  // --- attachments -----------------------------------------------------

  KitComposerChips? _attachmentChips(BuildContext context) {
    if (!_hasAttachments) return null;
    return KitComposerChips.attachments(
      stripKey: references.isEmpty
          ? null
          : const Key('composer-reference-strip'),
      items: [
        for (final reference in references)
          KitAttachment(
            id: reference,
            label: reference.label,
            kind: KitAttachmentKind.reference,
            chipKey: Key('composer-reference-${reference.id}'),
          ),
        for (final attachment in attachments)
          _attachmentChip(context, attachment),
      ],
      onRemove: (item) {
        switch (item.id) {
          case final PromptAttachment attachment:
            onRemoveAttachment(attachment);
          case final ReviewReference reference:
            onRemoveReference?.call(reference);
        }
      },
    );
  }

  KitAttachment _attachmentChip(
    BuildContext context,
    PromptAttachment attachment,
  ) {
    final folder = attachment.isDirectoryReference;
    final bytes = _thumbnailBytes(attachment);
    return KitAttachment(
      id: attachment,
      label: folder ? '@${attachment.filename}' : attachment.filename,
      kind: folder
          ? KitAttachmentKind.folder
          : attachment.mime.startsWith('image/')
          ? KitAttachmentKind.image
          : KitAttachmentKind.file,
      thumbnail: bytes == null ? null : KitImageSource.memory(bytes),
      thumbnailKey: const Key('attachment-thumbnail'),
      onOpen: folder
          ? null
          : () => unawaited(
              showFilePreviewSheet(
                context,
                FilePreviewData.fromDataUrl(
                  name: attachment.filename,
                  mimeType: attachment.mime,
                  url: attachment.url,
                ),
              ),
            ),
    );
  }

  // --- suggestions -----------------------------------------------------

  KitComposerChips? _suggestions(BuildContext context) {
    if (!allowInlineCommands) return null;
    final l10n = _chatL10n(context);
    // The demo has no commands: a typed `/` (at the start or mid-text) gets
    // one line saying so and pointing at the sample prompt, not silence
    // (B12).
    if (isolated) {
      if (!_slashWordAtCaret(controller.value)) return null;
      return KitComposerChips.suggestions(
        listKey: const Key('demo-no-commands'),
        suggestions: const [],
        onSelected: (_) {},
        note: l10n.demoNoCommands,
      );
    }
    final shown = compact ? 3 : KitComposerChips.visibleCount;
    final slash = _slashQuery;
    if (slash != null) {
      final matches =
          commands
              .where(
                (command) =>
                    command.enabled &&
                    command.listed &&
                    command.matchesQuery(slash),
              )
              .toList()
            ..sort((a, b) {
              final score = a.scoreFor(slash).compareTo(b.scoreFor(slash));
              return score != 0 ? score : a.slash.compareTo(b.slash);
            });
      if (matches.isEmpty) return null;
      return KitComposerChips.suggestions(
        listKey: const Key('inline-command-suggestions'),
        suggestions: [
          for (final command in matches.take(shown))
            KitSuggestion(
              id: command,
              label: '/${command.slash}',
              kind: KitSuggestionKind.command,
              // A server command's words are its title; the app's are its
              // description.
              description: command.description.isNotEmpty
                  ? command.description
                  : command.title != '/${command.slash}'
                  ? command.title
                  : null,
              key: Key('inline-command-${command.slash}'),
            ),
        ],
        onSelected: (s) => onSelectCommand(s.id as _ChatCommand),
        onShowAll: matches.length > shown ? onOpenCommands : null,
      );
    }
    final agentQuery = _activeAgentQuery(controller.value);
    if (agentQuery == null) return null;
    final normalized = agentQuery.query.toLowerCase();
    final matches =
        agents.where((agent) {
          return normalized.isEmpty ||
              agent.id.toLowerCase().contains(normalized) ||
              (agent.description?.toLowerCase().contains(normalized) ?? false);
        }).toList()..sort((a, b) {
          final aPrefix = a.id.toLowerCase().startsWith(normalized) ? 0 : 1;
          final bPrefix = b.id.toLowerCase().startsWith(normalized) ? 0 : 1;
          final prefix = aPrefix.compareTo(bPrefix);
          return prefix != 0 ? prefix : a.id.compareTo(b.id);
        });
    if (matches.isEmpty) return null;
    return KitComposerChips.suggestions(
      listKey: const Key('inline-agent-suggestions'),
      suggestions: [
        for (final agent in matches.take(shown))
          KitSuggestion(
            id: agent,
            label: '@${agent.id}',
            kind: KitSuggestionKind.agent,
            description: agent.description ?? l10n.chatUiDelegateThisPrompt,
            key: Key('inline-agent-${agent.id}'),
          ),
      ],
      onSelected: (s) => onSelectAgent(s.id as CatalogAgent),
      onShowAll: matches.length > shown ? onOpenAgents : null,
    );
  }

  /// The word the caret ends starts with `/`: "/" alone, "/rev", or
  /// "fix this /" mid-text. Without a caret, the whole text's start counts.
  static bool _slashWordAtCaret(TextEditingValue value) {
    final text = value.text;
    final selection = value.selection;
    if (!selection.isValid || !selection.isCollapsed) {
      return text.trimLeft().startsWith('/');
    }
    final cursor = selection.baseOffset;
    if (cursor < 1 || cursor > text.length) return false;
    final before = text.substring(0, cursor);
    final start = before.lastIndexOf(RegExp(r'\s')) + 1;
    return before.substring(start).startsWith('/');
  }

  String? get _slashQuery {
    final match = RegExp(r'^/(\S*)$').firstMatch(controller.text.trimLeft());
    return match?.group(1);
  }

  // --- the "+" sheet ---------------------------------------------------

  /// Opens the tools sheet and runs the chosen tool once it has closed.
  Future<void> _openTools(BuildContext context) async {
    if (isolated || shelfBusy || conversationMode) return;
    final l10n = _chatL10n(context);
    final attachBlocked = _attachBlocked;
    // Dictation only fills the draft, so it works while a reply runs; a
    // voice conversation waits for the reply it would talk over.
    final voiceBlocked = sending;
    final conversationBlocked = busy || sending;
    final canClearText = controller.text.isNotEmpty && onClearText != null;
    final canStash = _hasPrompt && onStashPrompt != null;
    final tool = await showKitSheet<_PromptTool>(
      context,
      title: l10n.chatUiPromptTools,
      sheetKey: const Key('composer-tools-sheet'),
      body: (sheetContext) => _PromptToolsList(
        attachBlocked: attachBlocked,
        attachmentsSupported: promptAttachmentsSupported,
        imagesOnly: promptImagesOnly,
        webSourcesSupported: webSourcesSupported,
        voiceBlocked: voiceBlocked,
        conversationBlocked: conversationBlocked,
        attachmentCount: attachments.length,
        canReusePrompt: onReusePrompt != null,
        canClearText: canClearText,
        canStash: canStash,
        canOpenStash: onOpenStash != null,
        agentName: agentName,
        onPick: (tool) => KitSheet.close(sheetContext, tool),
      ),
    );
    switch (tool) {
      case null:
        return;
      case _PromptTool.commands:
        onOpenCommands();
      case _PromptTool.attach:
        onAttach();
      case _PromptTool.webSources:
        onWebSources();
      case _PromptTool.gallery:
        onPhotoLibrary();
      case _PromptTool.camera:
        onCamera();
      case _PromptTool.voice:
        onVoice();
      case _PromptTool.conversation:
        onConversation();
      case _PromptTool.history:
        onReusePrompt?.call();
      case _PromptTool.clearText:
        onClearText?.call();
      case _PromptTool.stash:
        onStashPrompt?.call();
      case _PromptTool.saved:
        onOpenStash?.call();
    }
  }
}

/// The model chip: which model answers, how full its context is, and a quick
/// way to switch. It ends the status line above the message field (owner
/// decision 2 Oct 2026, 14A), so the field keeps its full width.
class _ChatModelChip extends StatelessWidget {
  const _ChatModelChip({
    required this.conn,
    required this.busy,
    required this.selectedAgent,
    this.defaultAgent = '',
    required this.selectedModel,
    this.modelLabel,
    this.selectionFallback,
    required this.selectedVariant,
    required this.onChooseModel,
    this.contextUsage,
    this.modelSwitch,
  });

  final ConnectionController conn;
  final bool busy;
  final String selectedAgent;

  /// The agent the server would pick unprompted. The chip names the agent
  /// only when the selection differs from it.
  final String defaultAgent;
  final ModelRef? selectedModel;

  /// Presented model name (catalog name or provider · model).
  final String? modelLabel;

  /// The words for "no pick": the server default by name, or "Loading…".
  final String? selectionFallback;
  final String selectedVariant;
  final VoidCallback onChooseModel;

  /// Share of the model's context window in use, or null when unknown.
  final double? contextUsage;

  /// The host's model cycle button; its three shortcuts become the model
  /// chip's menu (long-press, right-click, custom actions).
  final Widget? modelSwitch;

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: conn,
    builder: (context, _) => _chip(context),
  );

  KitComposerChips _chip(BuildContext context) {
    final model = selectedModel;
    final picked = model != null && model.modelID.isNotEmpty;
    final catalog = conn.catalog;
    final noModels = catalog != null && catalog.models.isEmpty;
    // P7.7: before the first send with nothing signed in, the chip says so.
    // P7.5: while a reply is being written a model is answering, so the chip
    // never asks to choose one.
    final state = picked
        ? KitModelChipState.chosen
        : noModels && !busy
        ? KitModelChipState.signInNeeded
        : KitModelChipState.serverDefault;
    final cycle = modelSwitch;
    return KitComposerChips.model(
      label: picked ? _contextLabel(context) : (selectionFallback ?? ''),
      onPressed: onChooseModel,
      state: state,
      contextUsed: contextUsage?.clamp(0.0, 1.0),
      menu: cycle is ModelCycleButton
          ? modelCycleMenuItems(
              _chatL10n(context),
              onCycle: cycle.onCycle,
              hasRecent: cycle.hasRecent,
              hasFavorites: cycle.hasFavorites,
            )
          : const [],
      chipKey: const Key('composer-model-context'),
      contextKey: const Key('composer-context-percent'),
    );
  }

  /// The presented model, the agent only when it is not the server's
  /// default, the effort only when it is a real choice.
  String _contextLabel(BuildContext context) {
    final parts = <String>[];
    if (selectedAgent.isNotEmpty && selectedAgent != defaultAgent) {
      parts.add(selectedAgent);
    }
    final model = selectedModel;
    if (model != null && model.modelID.isNotEmpty) {
      final presented = modelLabel?.trim();
      parts.add(
        presented == null || presented.isEmpty
            ? presentedModelLabel(model.providerID, model.modelID)
            : presented,
      );
    }
    final variant = selectedVariant.trim();
    if (variant.isNotEmpty && !_isDefaultVariant(variant)) {
      parts.add(presentedEffort(variant, _chatL10n(context)));
    }
    return parts.join(' · ');
  }

  static bool _isDefaultVariant(String variant) =>
      switch (variant.toLowerCase()) {
        'default' || 'medium' || 'normal' || 'standard' || 'auto' => true,
        _ => false,
      };
}
