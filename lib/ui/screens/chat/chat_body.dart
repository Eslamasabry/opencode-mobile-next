part of '../chat_screen.dart';

// The page body: the first-load and could-not-load states, or the
// conversation under the floating composer layer.

extension _ChatBody on _ChatScreenState {
  Widget _chatBody({
    required bool keyboardUp,
    required bool busy,
    required SessionTailPreview? openingExcerpt,
    required bool showExcerpt,
    required List<PermissionRequest> pendingPermissions,
    required int queuedAfterIndex,
    required List<List<Part>> displayParts,
    required Set<String> waitingLocalIDs,
    required Set<int> turnActionOwners,
    required bool showAttachmentNote,
  }) {
    // Rehydrates and refreshes must not flash a skeleton or a
    // full-screen error over an already-visible transcript. A
    // permission card must not wait for the transcript: it is pinned to
    // the bottom of the skeleton and error states too.
    return _loading && _messages.isEmpty && !showExcerpt
        ? Column(
            children: [
              const Expanded(child: _ChatLoadingBody()),
              if (!_watching)
                _attentionRegion(pendingPermissions, arrive: false),
            ],
          )
        : _error != null && _messages.isEmpty
        ? Column(
            children: [
              Expanded(
                child: _ChatLoadError(
                  error: _error!,
                  onRetry: () => unawaited(_load()),
                  agentName: _conn.isAgentBackend ? _conn.profile?.name : null,
                ),
              ),
              if (!_watching)
                _attentionRegion(pendingPermissions, arrive: false),
            ],
          )
        : LayoutBuilder(
            builder: (context, bodyConstraints) {
              // The composer keeps one editor structure. A keyboard or
              // short window reduces its line budget without reparenting
              // the focused field or moving its controls.
              final compactComposer =
                  keyboardUp || bodyConstraints.maxHeight < 420;
              final startEmpty =
                  !showExcerpt &&
                  _visibleHistory.isEmpty &&
                  _olderCursor == null &&
                  widget.emptyState == null &&
                  !_watching;
              if (startEmpty) _requestStartFacts();
              final showStarters = startEmpty && !_voiceConversation;
              final watch = widget.watch;
              final Widget conversation = showExcerpt
                  ? _ChatOpeningExcerpt(preview: openingExcerpt!)
                  : _visibleHistory.isEmpty && _olderCursor == null
                  ? Builder(
                      // Clear of the floating composer (watching
                      // floats one too: it writes to the worker).
                      builder: (context) => Padding(
                        padding: EdgeInsetsDirectional.only(
                          bottom: KitBottomInset.of(context).bottom,
                        ),
                        child:
                            widget.emptyState ??
                            (watch != null
                                ? _WatchingEmpty(words: watch.empty?.call())
                                : null) ??
                            _ChatStartArea(
                              header: ValueListenableBuilder<ChatStartFacts?>(
                                valueListenable: _startFacts,
                                builder: (context, _, _) => _ChatStartHeader(
                                  facts: _currentStartFacts(),
                                  compact: compactComposer,
                                ),
                              ),
                              starters: showStarters ? _startersRow() : null,
                            ),
                      ),
                    )
                  : _transcriptSelectionArea(
                      child: MarkdownFileLinks(
                        validate: _validatePathLink,
                        open: _openPathLink,
                        readImage: _readPathImage,
                        child: NotificationListener<ScrollNotification>(
                          onNotification: _onTranscriptScroll,
                          child: _transcript(
                            queuedAfterIndex: queuedAfterIndex,
                            displayParts: displayParts,
                            waitingLocalIDs: waitingLocalIDs,
                            turnActionOwners: turnActionOwners,
                          ),
                        ),
                      ),
                    );
              // Watching: the composer writes to the worker through
              // the team; nothing above it asks the person to act on
              // the worker's session.
              // The Undo bars read their clearance from a context under
              // the composer layer, so they float above the composer
              // instead of over it ([_undoHost]).
              final anchored = Builder(
                builder: (context) {
                  _undoBodyContext = context;
                  return conversation;
                },
              );
              if (watch != null) {
                return _watchLayer(watch: watch, body: anchored);
              }
              // The floating layer (VL §6): the transcript scrolls under
              // the glass composer, the only glass on the page.
              return KitComposer.layer(
                body: anchored,
                aboveMinHeight: _aboveComposerFloor(
                  bodyConstraints,
                  pendingPermissions,
                ),
                above: _aboveComposer(
                  bodyConstraints: bodyConstraints,
                  compactComposer: compactComposer,
                  pendingPermissions: pendingPermissions,
                ),
                composer: _floatingComposer(
                  bodyConstraints: bodyConstraints,
                  compactComposer: compactComposer,
                  busy: busy,
                  showAttachmentNote: showAttachmentNote,
                ),
              );
            },
          );
  }
}
