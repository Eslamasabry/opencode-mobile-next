part of '../chat_screen.dart';

// The composer layer: what floats above the composer and the composer
// itself.

extension _ChatComposerRegion on _ChatScreenState {
  /// Standing facts about this conversation's run, as labelled chips on the
  /// line above the composer's field (the model chip ends it): that approvals are automatic, and that the
  /// running work can be sent to the background. They used to be a bar and a
  /// link of their own, repeated above the composer on every running turn.
  List<Widget> _composerStatusChips() {
    final approval = _conn.isIsolated
        ? null
        : _conn.autoApprovalFor(widget.sessionID);
    // A request waiting for a person has its own card, which also says when
    // an automatic reply failed; the chip steps aside until it is answered.
    final showApproval =
        approval != null &&
        approval.automatic &&
        _conn.permissionsForSession(widget.sessionID).isEmpty;
    // While the move is in flight the chip steps aside (a chip that cannot
    // act is not shown); the composer note then says how it went.
    final showBackground = _canBackgroundWork;
    // Context the server will hand the agent at its next step (a finished
    // background command, changed instructions). Nothing to do about it, so
    // it is a label, not a bubble of its own above the composer.
    final pendingContext = _conn.isIsolated
        ? 0
        : _conn
              .inboxItemsFor(widget.sessionID)
              .where((item) => item.type != 'user')
              .length;
    final strings = _chatL10n(context);
    return [
      if (pendingContext > 0)
        KitChip(
          key: const Key('pending-context-chip'),
          icon: AppIconography.sparkle,
          label: pendingContext > 1
              ? '${strings.chatStripContextPending} · $pendingContext'
              : strings.chatStripContextPending,
        ),
      if (showApproval)
        _AutoApprovalIndicator(
          key: const ValueKey('auto-approval-indicator-slot'),
          effective: approval,
          connected: _conn.isConnected,
          approved: _conn.autoApprovedFor(widget.sessionID),
          onOpen: () => unawaited(
            showSessionApprovalsSheet(
              context,
              controller: _conn,
              sessionID: widget.sessionID,
            ),
          ),
        ),
      if (showBackground)
        Semantics(
          hint: strings.backgroundWorkShortcut,
          child: KitChip.action(
            key: const Key('background-running-work'),
            icon: AppIconography.lowPriority,
            label: strings.chatStripBackground,
            onPressed: () => unawaited(_backgroundRunningWork()),
          ),
        ),
    ];
  }

  /// The height the composer leaves free over itself while a request waits,
  /// so a tall composer (large text, keyboard up) never squeezes the
  /// request to nothing: its Details and answers stay one scroll away.
  double _aboveComposerFloor(
    BoxConstraints bodyConstraints,
    List<PermissionRequest> pendingPermissions,
  ) => bodyConstraints.hasBoundedHeight && _attentionPending(pendingPermissions)
      ? bodyConstraints.maxHeight * .3
      : 0;

  /// What sits over the composer, most urgent first: find, then what needs
  /// the person, then what the draft is waiting on. Solid parts on the
  /// ground; only the composer below them is glass.
  Widget _aboveComposer({
    required BoxConstraints bodyConstraints,
    required bool compactComposer,
    required List<PermissionRequest> pendingPermissions,
  }) {
    final l10n = _chatL10n(context);
    final tokens = KitTokens.of(context);
    final short = bodyConstraints.maxHeight < 420;
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        // Find sits by the keyboard it is typed with (owner review), over
        // the conversation it searches.
        if (_findOpen)
          ConstrainedBox(
            constraints: BoxConstraints(
              maxHeight: bodyConstraints.maxHeight * .38,
            ),
            child: _TranscriptFindBar(
              controller: _findController,
              focusNode: _findFocus,
              count: _findHits.length,
              current: _findCursor,
              hasOlder: _olderCursor != null,
              loading: _loading || _loadingOlder || _findAllLoading,
              searchingAll: _findAllLoading,
              onCancelLoading: () =>
                  _setChatState(() => _findAllLoading = false),
              error: _olderError,
              needsReload: _olderNeedsReload || _resetHistoryOnLoad,
              onChanged: _changeFind,
              onNext: () => _navigateFind(1),
              onPrevious: () => _navigateFind(-1),
              onClose: _closeFind,
              onLoadOlder: _searchAllHistory,
            ),
          ),
        if (_conn.sessionNoteReceipt(widget.sessionID) case final saved?)
          Padding(
            padding: EdgeInsetsDirectional.symmetric(
              horizontal: tokens.gutter,
              vertical: tokens.space1,
            ),
            child: KitNotice(
              icon: AppIconography.note,
              message: [
                saved ? l10n.sessionNoteSaved : l10n.sessionNoteRemoved,
                l10n.sessionNotePending,
              ].join('. '),
              onDismiss: () =>
                  _conn.dismissSessionNoteReceipt(widget.sessionID),
            ),
          ),
        // The request shares the height left over the composer with the
        // rest (a tip, waiting drafts): it takes what is left and scrolls,
        // its actions reachable, instead of overflowing or pushing Send off
        // screen. The composer keeps a share free for it
        // ([_aboveComposerFloor]).
        if (bodyConstraints.hasBoundedHeight &&
            _attentionPending(pendingPermissions))
          Flexible(
            child: ListView(
              shrinkWrap: true,
              reverse: true,
              padding: EdgeInsets.zero,
              children: [_attentionRegion(pendingPermissions)],
            ),
          )
        else
          _attentionRegion(pendingPermissions),
        // §7 rule 5: v2-only surfaces stay silent on v1. The map is already
        // empty there, but the gate is explicit so a stale entry cannot leak
        // a form card onto a server that cannot answer it.
        if (_conn.formForSession(widget.sessionID) case final pendingForm?
            when _conn.capabilities.forms)
          KitArrival(
            id: chatRequestArrivalId(pendingForm.id),
            child: _FormRequestCard(
              key: ValueKey('form-request-card-${pendingForm.id}'),
              form: pendingForm,
              onAnswer: () => unawaited(_openForm(pendingForm)),
            ),
          ),
        // The one nudge slot: below whatever needs the person. It gives way
        // to a short (keyboard) layout like every quiet strip. At large text
        // the sentence is tall: it takes at most a third of the body and
        // scrolls, ending on its two controls.
        if (!short)
          ConstrainedBox(
            constraints: BoxConstraints(
              maxHeight: bodyConstraints.maxHeight / 3,
            ),
            child: ListView(
              shrinkWrap: true,
              reverse: true,
              padding: EdgeInsets.zero,
              children: [_nudgeSlot(context)],
            ),
          ),
        // The offline-draft half of the strip is v1-safe; only the inbox
        // bubbles are v2-only (§7 rule 5). Always in place, empty when
        // nothing waits, so the first queued message unfolds in and the last
        // one folds away (design standard §10).
        _PendingSendsStrip(
          key: const ValueKey('pending-sends-strip'),
          drafts: _conn.queuedPromptsFor(widget.sessionID),
          inboxItems: _conn.capabilities.inbox
              // What you sent and is waiting. The server's own pending
              // context updates are a standing fact and live in the chip
              // strip.
              ? _conn
                    .inboxItemsFor(widget.sessionID)
                    .where((item) => item.type == 'user')
                    .toList()
              : const <Api2InboxItem>[],
          isSending: (entry) => _conn.queuedPromptSending(entry.id),
          isAcceptedUnrecorded: (entry) =>
              _conn.queuedPromptAcceptedUnrecorded(entry.id),
          onEdit: _editQueuedPrompt,
          onResend: _resendQueuedPrompt,
          receipts: _queueReceipts(),
          isChecking: (entry) => _checkingReceipts.contains(entry.id),
          checkedAt: (entry) => _receiptCheckedAt[entry.id],
          onCheck: _conn.status == StreamStatus.connected
              ? (entry) => unawaited(_checkQueuedReceipt(entry))
              : null,
          onRetry: _conn.status == StreamStatus.connected
              ? (entry) => unawaited(_retryQueuedPrompt(entry))
              : null,
          onDiscard: _discardQueuedPrompt,
          onCancelInbox: _cancelInboxSend,
          onFlipDelivery: _flipInboxDelivery,
        ),
        if (!_conn.isIsolated)
          if (_conn.promptPhotos.pending case final photo?
              when photo.profileID == _draftProfileID &&
                  photo.sessionID == widget.sessionID)
            KitRow(
              key: const ValueKey('pending-photo-recovery'),
              leading: const KitRowIcon(AppIconography.image),
              title: photo.name ?? l10n.photoPendingTitle,
              titleMaxLines: 2,
              onTap: _photoBusy ? null : () => _reviewPendingPhoto(photo),
              trailing: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  KitIconButton(
                    icon: AppIconography.add,
                    tooltip: l10n.photoAddToDraft,
                    onPressed: _promptShelfBusy
                        ? null
                        : () => _applyPendingPhoto(photo),
                  ),
                  KitIconButton(
                    icon: AppIconography.close,
                    tooltip: l10n.photoDiscard,
                    onPressed: _photoBusy
                        ? null
                        : () => _discardPendingPhoto(photo),
                  ),
                ],
              ),
            ),
        if (_draftSaveFailure case final failure?)
          Padding(
            key: const ValueKey('draft-save-error'),
            padding: EdgeInsetsDirectional.fromSTEB(
              tokens.gutter,
              tokens.space2,
              tokens.gutter,
              tokens.space1,
            ),
            child: Row(
              children: [
                Expanded(
                  child: Semantics(
                    container: true,
                    liveRegion: true,
                    label: _draftFailureText(failure),
                    excludeSemantics: true,
                    child: KitText(
                      compactComposer
                          ? l10n.draftUnsaved
                          : _draftFailureText(failure),
                      role: KitTextRole.secondary,
                      tone: KitTextTone.danger,
                    ),
                  ),
                ),
                KitIconButton(
                  icon: AppIconography.copy,
                  tooltip: l10n.chatDraftCopy,
                  // The person's own words, copied as written.
                  onPressed: _composer.text.isEmpty
                      ? null
                      : () => unawaited(
                          KitCopy.copy(context, _composer.text, redact: false),
                        ),
                ),
                KitIconButton(
                  icon: AppIconography.retry,
                  tooltip: l10n.draftRetrySave,
                  onPressed: _restoringDraftAttachments
                      ? null
                      : _retryDraftPersistence,
                ),
              ],
            ),
          ),
        KitReveal(
          child: _composerNote == null
              ? null
              : _ComposerNote(key: _composerNoteKey, text: _composerNote!),
        ),
      ],
    );
  }

  /// The glass composer, as it floats over the transcript.
  Widget _floatingComposer({
    required BoxConstraints bodyConstraints,
    required bool compactComposer,
    required bool busy,
    required bool showAttachmentNote,
  }) => _composerDropTarget(
    child: _ChatComposer(
      // The prompt goes to the agent this server runs, and says so.
      agentName: switch (_conn.profile?.backend) {
        ServerBackend.paseo => 'Claude Code',
        ServerBackend.codex => 'Codex',
        _ => null,
      },
      isolated: _conn.isIsolated,
      compact: compactComposer,
      // The multiline field scrolls within its budget at large text scales,
      // leaving room for the model context and Send controls.
      maxInputHeight: compactComposer
          ? bodyConstraints.maxHeight * .45
          : double.infinity,
      // Isolated (the demo) has no commands; the composer says so when a
      // `/` is typed instead of offering any.
      allowInlineCommands:
          !_voiceConversation && bodyConstraints.maxHeight >= 300,
      controller: _composer,
      focusNode: _focus,
      commands: _chatCommands,
      agents: _supportsPromptAgentMentions
          ? _subagents
          : const <CatalogAgent>[],
      onSelectCommand: _selectChatCommand,
      onSelectAgent: _insertAgentMention,
      onOpenCommands: _openCommandLauncher,
      onOpenAgents: () =>
          _openCommandLauncher(initialTab: CommandSheetTab.agents),
      onOpenEditor: _openPromptEditor,
      onReusePrompt: _recentPrompts.isEmpty ? null : _reusePrompt,
      onClearText: _clearDraftText,
      onStashPrompt: _conn.canUsePromptShelf && !_sending && !_promptShelfBusy
          ? _stashCurrentPrompt
          : null,
      onOpenStash: _conn.canUsePromptShelf && !_sending && !_promptShelfBusy
          ? _openPromptStash
          : null,
      onRestoreHistoryDraft: _promptHistory.original == null
          ? null
          : _restoreHistoryDraft,
      shelfBusy: _promptShelfBusy,
      shelfLoading:
          _photoBusy || _promptShelfOperationBusy || _restoringDraftAttachments,
      attachments: _attachments,
      promptAttachmentsSupported: _supportsPromptAttachments,
      webSourcesSupported: _conn.capabilities.webSearch,
      statusChips: _composerStatusChips(),
      busy: busy || _live != null,
      // Send becomes Stop; nothing to stop while the prompt is on its way.
      onStop: _sending ? null : () => unawaited(_abort()),
      stopping: _aborting,
      sending: _sending,
      // OpenCode 1 runs a send made mid-turn after that turn; OpenCode 2
      // steers or queues it. Either way Send stays live.
      canSendWhileBusy: !_voiceConversation,
      canChooseDelivery: _conn.supportsInbox,
      delivery: _delivery,
      onDeliveryChanged: (delivery) =>
          _setChatState(() => _delivery = delivery),
      voiceOpening: _voiceOpening,
      selectedAgent: _conn.agentForSession(widget.sessionID),
      defaultAgent: _defaultAgentName,
      selectedModel: _conn.modelForSession(widget.sessionID),
      modelLabel: _presentedModelLabel,
      selectionFallback: !_conn.serverOwnsSessionSelection
          ? null
          : _conn.selectionForSession(widget.sessionID).modelKnown
          ? _chatL10n(context).modelServerDefault
          : _chatL10n(context).modelSelectionLoading,
      selectedCatalogModel: _selectedCatalogModel,
      selectedVariant: _conn.variantForSession(widget.sessionID),
      showAttachmentNote: showAttachmentNote,
      onAttach: _pickAttachment,
      onPhotoLibrary: () => _pickPhoto(ImageSource.gallery),
      onCamera: () => _pickPhoto(ImageSource.camera),
      onContentInserted: (content) =>
          unawaited(_handleInsertedContent(content)),
      onVoice: _openVoice,
      onConversation: _startVoiceConversation,
      onWebSources: _addWebSources,
      conversationMode: _voiceConversation,
      voice: _composerVoice(),
      onSend: _send,
      onChooseModel: () {
        if (!_conn.isIsolated) {
          showModelPicker(
            context,
            applyScope: _modelApplyScope,
            sessionID: widget.sessionID,
          );
        }
      },
      contextUsage: _contextWindowUsage(),
      modelSwitch: _modelCycleButton(),
      onRemoveAttachment: (attachment) =>
          _setChatState(() => _attachments.remove(attachment)),
      // UX-103 review handoff (start).
      references: _stagedReferences,
      onRemoveReference: _removeStagedReference,
      // UX-103 review handoff (end).
    ),
  );
}
