part of '../chat_screen.dart';

// The composer layer: what floats above the composer and the composer
// itself.

extension _ChatComposerRegion on _ChatScreenState {
  /// A sub-agent that answers only its main conversation (Claude Code's):
  /// it takes no messages here and is stopped from there, so this page
  /// offers neither Send nor Stop.
  bool get _subagentReadOnly =>
      !_conn.isIsolated &&
      !_conn.capabilities.subagentReplies &&
      _conn.sessionsById[widget.sessionID]?.parentID != null;

  /// The agent is at work on this conversation: a reply runs or is on its
  /// way. Stop then sits at the trailing end of the chips row.
  bool get _agentWorking =>
      _conn.busySessions.contains(widget.sessionID) ||
      _live != null ||
      _sending;

  /// The model and Auto-approve chips over the composer. They stay while the
  /// agent works (owner, 30 Oct 2026: Stop sits at the end of this same row,
  /// so the row never comes and goes with a run); a window too short for the
  /// line keeps them behind the composer's "+".
  bool _settingsChipsShown(BoxConstraints bodyConstraints) =>
      !_conn.isIsolated &&
      (!bodyConstraints.hasBoundedHeight || bodyConstraints.maxHeight >= 480);

  /// Stop is on the chips row for as long as a turn runs, except while the
  /// turn waits on the person (the card owns the composer then).
  bool get _stopRowShown =>
      _agentWorking && !_subagentReadOnly && !_waitsForYou;

  Widget _composerStatusStrip(BoxConstraints bodyConstraints) =>
      // A permission answered here and held for its Undo window stops
      // waiting on the person: Stop comes back without the screen drawing.
      ListenableBuilder(
        listenable: _conn.delayedAnswers,
        builder: (context, _) => _statusStripNow(bodyConstraints),
      );

  Widget _statusStripNow(BoxConstraints bodyConstraints) {
    final chips = _composerStatusChips(bodyConstraints);
    final settings = _settingsChipsShown(bodyConstraints);
    final approval = settings && _approvalModeOffered(_conn)
        ? _conn.autoApprovalFor(widget.sessionID)
        : null;
    final all = [
      if (approval != null)
        _AutoApprovalIndicator(
          key: const ValueKey('auto-approval-indicator-slot'),
          effective: approval,
          connected: _conn.isConnected,
          starting: _conn.connectionStarting,
          approved: _conn.autoApprovedFor(widget.sessionID),
          onOpen: (chipContext) =>
              unawaited(_showApprovalModeMenu(chipContext)),
        ),
      ...chips,
    ];
    final stop = _stopRowShown ? _stopRowChip() : null;
    final empty = all.isEmpty && !settings && stop == null;
    // Comes and goes with a short fold and fade (KitReveal; instant with
    // reduced motion) as a reply starts and ends.
    return KitReveal(
      child: empty
          ? null
          : KitComposerStatusStrip(
              stripKey: const Key('composer-status-strip'),
              chips: all,
              model: settings ? _modelChipWidget() : null,
              stop: stop,
            ),
    );
  }

  /// The one Stop: stops this session and keeps the draft. Amber and
  /// worded for a step that looks stuck.
  KitComposerStop _stopRowChip() {
    final strings = _chatL10n(context);
    final named = strings.stopRowSemantics(
      KitBidi.auto(_agentName ?? 'OpenCode'),
    );
    return KitComposerStop(
      chipKey: const Key('chat-stop-button'),
      label: strings.kitTurnLiveStopShort,
      // "Stop after this step" was chosen: the name says it waits.
      spoken: _stopAfterStepPending
          ? '$named, ${strings.kitComposerStopPending}'
          : named,
      stuck: _stuckFor != null,
      working: _aborting,
      onPressed: () => unawaited(_abort()),
      menu: _stopAfterStepPending ? const [] : _stopMenu(),
      tip: _stopTip(),
    );
  }

  /// The model's words for the "+" sheet: its name, the agent when it is not
  /// the server's default, the effort when it is a real choice.
  String _modelSettingLabel() {
    final chip = _modelChipWidget();
    final fallback =
        chip.selectionFallback ?? _chatL10n(context).modelServerDefault;
    final model = chip.selectedModel;
    final signIn = chip.contextLabel(context);
    return (model != null && model.modelID.isNotEmpty) ||
            signIn == _chatL10n(context).kitModelSignIn
        ? signIn
        : fallback;
  }

  /// This conversation's own settings, the first rows of the composer's "+"
  /// sheet. The chips row that held them is gone (owner decision 4, 20 Oct
  /// 2026): nothing of them stays on screen while the agent works, apart
  /// from the one line under the header while Auto-approve is on.
  List<_ComposerSetting> _composerSettings() {
    if (_conn.isIsolated) return const [];
    final strings = _chatL10n(context);
    final plan = _planRow;
    return [
      _ComposerSetting(
        key: 'model',
        icon: AppIconography.model,
        title: strings.composerSettingModel,
        supporting: _modelSettingLabel().isEmpty ? null : _modelSettingLabel(),
        onTap: () => showModelPicker(
          context,
          applyScope: _modelApplyScope,
          sessionID: widget.sessionID,
        ),
      ),
      if (_approvalModeOffered(_conn))
        _ComposerSetting(
          key: 'approval',
          icon: AppIconography.shield,
          title: strings.chatStripAutoApprove,
          custom: _AutoApproveSwitchRow(
            conn: _conn,
            sessionID: widget.sessionID,
            onChange: (on) => unawaited(
              _chooseApprovalMode(
                on ? _ApprovalChoice.auto : _ApprovalChoice.ask,
              ),
            ),
          ),
        ),
      if (_approvalModeOffered(_conn))
        _ComposerSetting(
          key: 'approval-settings',
          icon: AppIconography.permissions,
          title: strings.approvalModeSettings,
          onTap: () => unawaited(
            showSessionApprovalsSheet(
              context,
              controller: _conn,
              sessionID: widget.sessionID,
            ),
          ),
        ),
      if (plan != null)
        _ComposerSetting(
          key: 'plan',
          icon: AppIconography.checklist,
          title: strings.composerSettingPlan,
          supporting: plan.current == null
              ? plan.progress
              : '${plan.progress} · ${plan.current}',
          onTap: plan.open,
        ),
      if (_canBackgroundWork)
        _ComposerSetting(
          key: 'background',
          icon: AppIconography.lowPriority,
          title: strings.chatStripBackground,
          supporting: strings.backgroundWorkShortcut,
          onTap: () => unawaited(_backgroundRunningWork()),
        ),
    ];
  }

  _ChatModelChip _modelChipWidget() => _ChatModelChip(
    conn: _conn,
    busy: _conn.busySessions.contains(widget.sessionID) || _live != null,
    selectedAgent: _conn.agentForSession(widget.sessionID),
    defaultAgent: _defaultAgentName,
    agentIsMode: _conn.api is AgentFeatureGateway,
    selectedModel: _conn.displayModelForSession(widget.sessionID),
    modelLabel: _presentedModelLabel,
    selectionFallback: !_conn.serverOwnsSessionSelection
        ? null
        : _conn.selectionForSession(widget.sessionID).modelKnown
        ? _chatL10n(context).modelServerDefault
        : _chatL10n(context).modelChooseTitle,
    selectedVariant: _conn.variantForSession(widget.sessionID),
    onChooseModel: () => showModelPicker(
      context,
      applyScope: _modelApplyScope,
      sessionID: widget.sessionID,
    ),
    modelSwitch: _modelCycleButton(),
  );

  /// Standing facts about this conversation's run, as labelled chips on the
  /// line above the composer's field (the model chip ends it): how approvals are answered, and that the
  /// running work can be sent to the background. They used to be a bar and a
  /// link of their own, repeated above the composer on every running turn.
  List<Widget> _composerStatusChips(BoxConstraints bodyConstraints) {
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
    // The agent's own switches (fast mode and the like), where it has any:
    // a chip that opens "<Agent> settings".
    final features = _conn.api;
    final showFeatures =
        !_conn.isIsolated &&
        features is AgentFeatureGateway &&
        (features as AgentFeatureGateway).agentFeaturesSupported;
    return [
      if (showFeatures)
        AgentFeaturesChip(
          key: const ValueKey('agent-features-slot'),
          gateway: features as AgentFeatureGateway,
          sessionID: widget.sessionID,
          refreshKey: _conn.displayModelForSession(widget.sessionID)?.wireName,
        ),
      if (pendingContext > 0)
        KitChip(
          key: const Key('pending-context-chip'),
          icon: AppIconography.sparkle,
          label: pendingContext > 1
              ? '${strings.chatStripContextPending} · $pendingContext'
              : strings.chatStripContextPending,
        ),
    ];
  }

  /// Under this height the request's viewport cannot hold its question and
  /// its answers together; it then shows the answers (the bottom) first.
  static const double _requestSliverMin = 140;

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
    // A tip never competes with the keyboard, an open find or a waiting
    // request for the height over the composer; the conversation keeps it.
    final quiet =
        compactComposer || _findOpen || _attentionPending(pendingPermissions);
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
        // Stop never scrolls away: while it shows, the strip keeps its height
        // too (one line, 48 dp) and the transcript gives up the room.
        if (bodyConstraints.hasBoundedHeight &&
            (_attentionPending(pendingPermissions) || _stopRowShown))
          // Anchored at the top: the question is read first and the answers
          // are a scroll away. A sliver too short for the question and the
          // answers together (large text with the keyboard up) anchors at
          // the bottom instead, so Allow once is what is under the finger.
          Flexible(
            child: LayoutBuilder(
              builder: (context, box) => _RequestScrollHost(
                child: ListView(
                  shrinkWrap: true,
                  reverse: box.maxHeight < _requestSliverMin,
                  padding: EdgeInsets.zero,
                  children: [_attentionRegion(pendingPermissions)],
                ),
              ),
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
        if (!quiet)
          ConstrainedBox(
            constraints: BoxConstraints(
              maxHeight: bodyConstraints.maxHeight / 3,
            ),
            child: ListView(
              shrinkWrap: true,
              reverse: true,
              padding: EdgeInsets.zero,
              // A note comes and goes with a short fold and fade
              // (KitReveal; instant with reduced motion).
              children: [
                KitReveal(
                  child: switch (_nudgeSlot(context)) {
                    SizedBox() => null,
                    final slot => slot,
                  },
                ),
              ],
            ),
          ),
        // The offline-draft half of the strip is v1-safe; only the inbox
        // bubbles are v2-only (§7 rule 5). Always in place, empty when
        // nothing waits, so the first queued message unfolds in and the last
        // one folds away (design standard §10).
        _PendingSendsStrip(
          key: const ValueKey('pending-sends-strip'),
          drafts: _waitingDrafts(),
          inboxItems: _conn.capabilities.inbox
              // What you sent and is waiting. The server's own pending
              // context updates are a standing fact and live in the chip
              // strip.
              ? _conn
                    .inboxItemsFor(widget.sessionID)
                    .where(
                      (item) =>
                          item.type == 'user' && !_isStopAfterStepNote(item),
                    )
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
          Flexible(
            child: ListView(
              primary: false,
              shrinkWrap: true,
              padding: EdgeInsets.zero,
              children: [
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
                            tone: KitTextTone.primary,
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
                                KitCopy.copy(
                                  context,
                                  _composer.text,
                                  redact: false,
                                ),
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
              ],
            ),
          ),
        KitReveal(
          child: _composerNote == null
              ? null
              : _ComposerNote(
                  key: _composerNoteKey,
                  text: _composerNote!,
                  onDismiss: _dismissComposerNote,
                ),
        ),
        // The person's own common short phrases, one tap from sending.
        _phraseChipsRow(bodyConstraints, pendingPermissions),
        // The chips wrap at a narrow width or a large text size; in the room
        // left over the composer they scroll instead of overflowing. While a
        // request waits it owns that room (its own Flexible above), so the
        // strip keeps its natural height and does not split the room with it.
        // Stop never scrolls away: while it shows, the strip keeps its height
        // too (one line, 48 dp) and the transcript gives up the room.
        if (bodyConstraints.hasBoundedHeight &&
            (_attentionPending(pendingPermissions) || _stopRowShown))
          _composerStatusStrip(bodyConstraints)
        else
          Flexible(
            child: ListView(
              reverse: true,
              primary: false,
              shrinkWrap: true,
              padding: EdgeInsets.zero,
              children: [_composerStatusStrip(bodyConstraints)],
            ),
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
      agentName: _agentName,
      cardWaiting: _cardWaits,
      waitsForYou: () => _waitsForYou,
      // A sub-agent that answers only its main conversation (Claude's).
      blockedReason: _subagentReadOnly
          ? _chatL10n(context).chatSubagentReadOnly
          : null,
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
      fileHits: _canMentionFiles ? _fileHits : const [],
      onSelectFile: _canMentionFiles ? _insertFileMention : null,
      onOpenProjectFiles: _canMentionFiles ? _openProjectFiles : null,
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
      promptImagesOnly: _conn.capabilities.promptImagesOnly,
      webSourcesSupported: _conn.capabilities.webSearch,
      busy: busy || _live != null,
      // Send becomes Stop; while the prompt is still on its way, Stop calls
      // the send off.
      settings: _composerSettings(),
      sendMenu: _sendMenu(),
      sendTip: _sendTip(),
      sending: _sending,
      // OpenCode 1 runs a send made mid-turn after that turn; OpenCode 2
      // and Codex steer it; a Paseo agent adds it to the running turn or
      // starts over from it. Either way Send stays live.
      canSendWhileBusy: !_voiceConversation,
      promptSteering: _conn.capabilities.promptSteering,
      midTurnPrompt: _midTurnPrompt,
      stopFirst: _stopsBeforeSend(busy || _live != null),
      stuck: _stuckFor != null,
      stuckCommand: _stuckName,
      voiceOpening: _voiceOpening,
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
      onRemoveAttachment: (attachment) =>
          _setChatState(() => _attachments.remove(attachment)),
      // UX-103 review handoff (start).
      references: _stagedReferences,
      onRemoveReference: _removeStagedReference,
      // UX-103 review handoff (end).
    ),
  );
}
