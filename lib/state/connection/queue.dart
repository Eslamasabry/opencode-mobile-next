part of '../connection.dart';

// The offline prompt queue and its flush.

/// [ConnectionController]'s offline prompt queue.
mixin _ConnectionControllerQueue on ChangeNotifier {
  ConnectionController get _self;

  /// Prompts drafted while the server was unreachable, waiting to flush.
  /// Loaded lazily from [OfflineQueueStore] and kept in memory afterward.
  List<QueuedPrompt>? _offlineQueue;
  Future<void> _queueChanges = Future<void>.value();
  OfflineQueueStore? _offlineQueueStore;
  bool _flushingOfflineQueue = false;

  /// The queued prompt a flush is working on: from the moment its dispatch
  /// marker write is queued until its acceptance or failure has been
  /// persisted. Removal and resend refuse this id inside the queue's
  /// serialization, so a confirmation sheet that was opened before the
  /// flush started cannot act on a prompt that is now on the wire.
  String? _queuedPromptInFlight;

  /// Queued prompts the server accepted but whose removal the store refused.
  /// In memory only: after a restart the marker alone remains, which reads
  /// as an unconfirmed send — the honest statement once this is lost.
  final _queuedPromptsAcceptedUnrecorded = <String>{};

  QueuedPromptRemoval? _keptQueuedStore;

  /// The queued prompts removing [profileId] would affect, counted for the
  /// confirmation. Throws when the queue cannot be read (never a zero).
  /// Pass the result to [deleteProfileAndLocalData].
  QueuedPromptRemovalPlan inspectQueuedPromptsForRemoval(String profileId) =>
      _self._keptQueued.inspect(profileId);

  /// Set when the queue dropped entries on its own — expiry or a limit —
  /// and cleared once a screen has shown it. A prompt the user asked to send
  /// never disappears silently.
  String? _queueEvictionNotice;

  /// Reads and clears the pending eviction notice.
  String? takeQueueEvictionNotice() {
    final notice = _queueEvictionNotice;
    _queueEvictionNotice = null;
    return notice;
  }

  /// Bytes this device is holding for unsent work, for the settings readout.
  int get queuedPromptBytes => _self._queueStore.storedBytes();

  bool get queuedPromptStorageReadable => _self._queueStore.readable;

  int get sessionDraftBytes => _self._draftStore.storedBytes();

  /// Drops every queued prompt, for every profile. Returns whether the
  /// store accepted the write; a refusal leaves the queue intact rather
  /// than reporting a clear that did not happen.
  Future<bool> clearAllQueuedPrompts() => _self._clearAllQueuedPrompts();

  /// Drops every saved composer draft, for every session.
  Future<bool> clearAllSessionDrafts() => _self._clearAllSessionDrafts();

  /// Queued prompts across every profile, for the settings readout.
  int get totalQueuedPromptCount => _self._queue.length;

  /// Saved composer drafts across every session, for the settings readout.
  int get totalSessionDraftCount => _self._drafts.length;

  /// Queued prompts for one session of the active profile, oldest first.
  List<QueuedPrompt> queuedPromptsFor(String sessionID) =>
      _self._queuedPromptsFor(sessionID);

  /// Queued prompts across the active profile, for the connection banner.
  int get queuedPromptCount => _self._queuedPromptCount;

  /// Queued prompts of the active profile that left the device without a
  /// confirmed outcome. A flush skips these; only the user's explicit
  /// resend, edit, or discard moves them.
  int get queuedPromptReviewCount => _self._queuedPromptReviewCount;

  /// Whether a flush is dispatching this entry or persisting its outcome.
  bool queuedPromptSending(String id) => _queuedPromptInFlight == id;

  /// Whether the server accepted this entry's send but the device could not
  /// record it. Resending such an entry is a guaranteed duplicate.
  bool queuedPromptAcceptedUnrecorded(String id) =>
      _queuedPromptsAcceptedUnrecorded.contains(id);

  /// Queued prompts that belong to profiles other than the active one. A
  /// flush never sends these; the count lets banners and the flush notice
  /// say "N drafts waiting for other servers" instead of staying silent.
  int get queuedPromptCountForOtherProfiles =>
      _self._queuedPromptCountForOtherProfiles;

  /// Advances after a flush cycle that delivered at least one queued
  /// prompt; [lastFlushedPromptCount] and [lastFlushSkippedForOtherProfiles]
  /// describe that cycle. Screens compare revisions in their listener to
  /// show a one-shot "Sent N queued prompts" confirmation.
  int offlineFlushRevision = 0;
  int lastFlushedPromptCount = 0;
  int lastFlushSkippedForOtherProfiles = 0;

  /// Queued prompts belonging to [profileID], whether or not it is active —
  /// what a "remove this server" confirmation has to disclose.
  int queuedPromptCountForProfile(String profileID) =>
      _self._queue.where((entry) => entry.profileID == profileID).length;

  /// Queued prompts belonging to [profileID], oldest first, for the move
  /// sheet (slice-queue-move).
  List<QueuedPrompt> queuedPromptsForProfile(String profileID) =>
      _self._queuedPromptsForProfile(profileID);

  /// The server queued prompts can move to: the one the app is connected
  /// to, when it keeps a queue. Null when there is none.
  ServerProfile? get queuedPromptMoveDestination =>
      _self._queuedPromptMoveDestination;

  /// Moves [promptIDs] of [sourceProfileID] into [sessionID] (or a new
  /// conversation when null) on [queuedPromptMoveDestination], in one queue
  /// write. Nothing is sent here; [flushOfflineQueue] sends them. Throws
  /// [QueuedPromptMoveException] and moves nothing on any failure.
  Future<QueuedPromptMoveResult> moveQueuedPrompts({
    required String sourceProfileID,
    required Set<String> promptIDs,
    String? sessionID,
  }) => _self._moveQueuedPrompts(
    sourceProfileID: sourceProfileID,
    promptIDs: promptIDs,
    sessionID: sessionID,
  );

  /// Undo for [moveQueuedPrompts]: puts back every moved prompt that is
  /// still waiting, unsent, on the destination. Returns how many returned.
  /// A storage refusal throws [QueuedPromptMoveException] and changes
  /// nothing.
  Future<int> undoQueuedPromptMove(QueuedPromptMoveResult result) =>
      _self._undoQueuedPromptMove(result);

  /// Unsent composer drafts that removing [profileID] would delete.
  int draftCountForProfile(String profileID) =>
      _self._drafts.length -
      SessionDraftStore.withoutProfile(_self._drafts, profileID).length;

  /// Adds a drafted prompt to the offline queue. Returns false when the
  /// entry exceeds the composer's aggregate attachment cap and was not
  /// queued. A storage failure throws [OfflineQueueWriteException], so the
  /// caller can keep the composer and explain why it was not saved.
  Future<bool> queuePrompt(QueuedPrompt prompt) => _self._queuePrompt(prompt);

  /// Removes a queued prompt. Returns false, removing nothing, when a flush
  /// is dispatching that entry right now: the decision was made against a
  /// bubble that said "queued", and the entry's real state is on the wire.
  /// The check runs inside the queue's serialization, after any marker
  /// write ahead of it. A storage refusal throws
  /// [OfflineQueueWriteException].
  Future<bool> removeQueuedPrompt(String id) => _self._removeQueuedPrompt(id);

  /// The user's explicit answer to an unconfirmed send: clear the dispatch
  /// marker so the next flush delivers the entry again, and start that
  /// flush. Returns false without changing anything when the entry is not
  /// in review any more — it is being dispatched, was removed, or its send
  /// is known to have been accepted. A storage refusal throws
  /// [OfflineQueueWriteException] and leaves the entry in review; nothing
  /// is sent until the marker is persisted as cleared.
  Future<bool> resendQueuedPrompt(String id) => _self._resendQueuedPrompt(id);

  /// The person's Retry on a queued prompt whose send failed before it left
  /// the device (the server refused it, so nothing was delivered): it goes
  /// out in a flush that starts now, even when automatic sending is off.
  /// Offline it waits for the next flush with the same explicit request.
  /// False, changing nothing, when the entry is gone, is already on its way,
  /// or left the device once (that is [resendQueuedPrompt]'s review).
  Future<bool> retryQueuedPrompt(String id) => _self._retryQueuedPrompt(id);

  /// Set when a flush is requested while one is running, so the running
  /// flush starts another pass when it finishes. An explicit resend that
  /// lands mid-flush must not wait for the next reconnect.
  bool _flushOfflineQueueAgain = false;
  // Explicit resend authorizes only this entry, never its neighbours. It
  // remains in memory until dispatch and is not an automatic-act receipt.
  final Set<String> _explicitQueueResends = {};

  /// Sends queued prompts for the active profile, oldest first, through the
  /// wake-reconciled transport. A connectivity failure stops the flush (the
  /// server is still unreachable); a declared server failure keeps that
  /// entry with its error inline and continues with the next.
  ///
  /// Every send is bracketed by two persisted writes: the dispatch marker
  /// goes to storage before the request leaves the device, and the entry's
  /// removal goes to storage as soon as the server accepts it. A refusal of
  /// either write stops the batch instead of sending on state the next
  /// launch cannot see. Entries whose marker is set are never sent here;
  /// only the user's explicit resend clears it — see
  /// [QueuedPrompt.dispatchedAt].
  Future<void> flushOfflineQueue() => _self._flushOfflineQueue();
}

extension _ConnectionControllerQueueImpl on ConnectionController {
  OfflineQueueStore get _queueStore =>
      _offlineQueueStore ??= OfflineQueueStore(prefs: store.prefs);

  /// Queued prompts a removed server left behind as drafts (P7.2). App-owned,
  /// so the removed server's deletion sweep does not take them.
  QueuedPromptRemoval get _keptQueued => _keptQueuedStore ??=
      QueuedPromptRemoval(preferences: store.prefs, queue: _queueStore);

  Future<T> _serializeQueueChange<T>(Future<T> Function() change) {
    final operation = _queueChanges.then((_) => change());
    _queueChanges = operation.then<void>((_) {}, onError: (Object _) {});
    return operation;
  }

  /// The queue, with its age/count/byte limits already applied.
  ///
  /// A queue written by an older build — or left to sit while the server
  /// stayed away — is trimmed on first read and written back, so the limits
  /// hold for existing installs and not only for new sends.
  List<QueuedPrompt> get _queue {
    final cached = _offlineQueue;
    if (cached != null) return cached;
    final eviction = OfflineQueueStore.enforceLimits(_queueStore.load());
    _offlineQueue = eviction.kept;
    if (eviction.removed > 0) {
      _queueEvictionNotice = eviction.notice;
      unawaited(_queueStore.save(eviction.kept));
    }
    return eviction.kept;
  }

  /// The body of [clearAllQueuedPrompts].
  Future<bool> _clearAllQueuedPrompts() => _serializeQueueChange(() async {
    if (_queue.isEmpty && _queueStore.readable) return true;
    if (!await _queueStore.save(const [])) return false;
    _offlineQueue = [];
    _queuedPromptsAcceptedUnrecorded.clear();
    _notifyListeners();
    return true;
  });

  /// The body of [clearAllSessionDrafts].
  Future<bool> _clearAllSessionDrafts() => _serializeDraftChange(() async {
    if (!await _draftStore.save(const {})) return false;
    _sessionDrafts = {};
    _notifyListeners();
    return _collectDraftAttachments();
  });

  /// The body of [queuedPromptsFor].
  List<QueuedPrompt> _queuedPromptsFor(String sessionID) {
    final profileID = profile?.id;
    if (profileID == null) return const [];
    return [
      for (final entry in _queue)
        if (entry.profileID == profileID && entry.sessionID == sessionID) entry,
    ];
  }

  /// The body of [queuedPromptCount].
  int get _queuedPromptCount {
    final profileID = profile?.id;
    if (profileID == null) return 0;
    return _queue.where((entry) => entry.profileID == profileID).length;
  }

  /// The body of [queuedPromptReviewCount].
  int get _queuedPromptReviewCount {
    final profileID = profile?.id;
    if (profileID == null) return 0;
    return _queue
        .where((entry) => entry.profileID == profileID && entry.dispatched)
        .length;
  }

  /// The body of [queuedPromptCountForOtherProfiles].
  int get _queuedPromptCountForOtherProfiles {
    final profileID = profile?.id;
    return _queue.where((entry) => entry.profileID != profileID).length;
  }

  /// The body of [queuedPromptsForProfile].
  List<QueuedPrompt> _queuedPromptsForProfile(String profileID) => [
    for (final entry in _queue)
      if (entry.profileID == profileID) entry,
  ];

  /// The body of [queuedPromptMoveDestination].
  ServerProfile? get _queuedPromptMoveDestination {
    final target = _connectedProfile ?? profile;
    if (isIsolated ||
        !isConnected ||
        !capabilities.offlinePromptQueue ||
        target == null ||
        target.id != profile?.id ||
        target.usesAgentSocket ||
        _closedQueueProfiles.contains(target.id)) {
      return null;
    }
    return target;
  }

  /// The body of [moveQueuedPrompts].
  Future<QueuedPromptMoveResult> _moveQueuedPrompts({
    required String sourceProfileID,
    required Set<String> promptIDs,
    String? sessionID,
  }) async {
    final destination = queuedPromptMoveDestination;
    if (destination == null || destination.id == sourceProfileID) {
      throw const QueuedPromptMoveException(
        QueuedPromptMoveProblem.noDestination,
      );
    }
    if (!queuedPromptsForProfile(sourceProfileID).any(
      (p) => promptIDs.contains(p.id) && QueuedPromptMove.blockFor(p) == null,
    )) {
      throw const QueuedPromptMoveException(
        QueuedPromptMoveProblem.nothingToMove,
      );
    }
    final String target;
    if (sessionID != null) {
      if (!sessionsById.containsKey(sessionID)) {
        throw const QueuedPromptMoveException(
          QueuedPromptMoveProblem.conversationGone,
        );
      }
      target = sessionID;
    } else {
      try {
        target = (await createSession()).id;
      } catch (error) {
        throw QueuedPromptMoveException(
          QueuedPromptMoveProblem.newConversationFailed,
          cause: error,
        );
      }
    }
    return _serializeQueueChange(() async {
      if (queuedPromptMoveDestination?.id != destination.id) {
        throw const QueuedPromptMoveException(
          QueuedPromptMoveProblem.destinationChanged,
        );
      }
      final models = catalog == null ? null : modelAvailable;
      final move = QueuedPromptMove.apply(
        removal: _keptQueued,
        queue: _queue,
        sourceProfileID: sourceProfileID,
        destinationProfileID: destination.id,
        availableProfileIDs: {for (final p in store.profiles) p.id},
        promptIDs: promptIDs,
        sessionID: target,
        newConversation: sessionID == null,
        keepsSelection: (p) => QueuedPromptMove.keepsSelection(
          p,
          modelAvailable: models,
          agents: agents,
        ),
      );
      if (!await _queueStore.save(move.queue)) {
        throw const QueuedPromptMoveException(QueuedPromptMoveProblem.notSaved);
      }
      _offlineQueue = move.queue;
      if (!_disposed) _notifyListeners();
      return move.result;
    });
  }

  /// The body of [undoQueuedPromptMove].
  Future<int> _undoQueuedPromptMove(QueuedPromptMoveResult result) =>
      _serializeQueueChange(() async {
        final undo = QueuedPromptMove.undo(
          queue: _queue,
          result: result,
          inFlightID: _queuedPromptInFlight,
        );
        if (undo.restored == 0) return 0;
        if (!await _queueStore.save(undo.queue)) {
          throw const QueuedPromptMoveException(
            QueuedPromptMoveProblem.notSaved,
          );
        }
        _offlineQueue = undo.queue;
        if (!_disposed) _notifyListeners();
        return undo.restored;
      });

  /// The body of [queuePrompt].
  Future<bool> _queuePrompt(QueuedPrompt prompt) =>
      _serializeQueueChange(() async {
        final target = store.profiles.where((p) => p.id == prompt.profileID);
        if (_closedQueueProfiles.contains(prompt.profileID) ||
            target.any((p) => p.usesAgentSocket)) {
          return false;
        }
        if (prompt.payloadBytes > OfflineQueueStore.maxEntryBytes) return false;
        final eviction = OfflineQueueStore.enforceLimits([..._queue, prompt]);
        // The new entry losing its own eviction pass means the queue could not
        // make room for it; say so rather than reporting a queue that silently
        // dropped what the user just wrote.
        if (!eviction.kept.any((entry) => entry.id == prompt.id)) return false;
        if (!await _queueStore.save(eviction.kept)) {
          throw const OfflineQueueWriteException();
        }
        _offlineQueue = eviction.kept;
        if (eviction.removed > 0) _queueEvictionNotice = eviction.notice;
        _notifyListeners();
        return true;
      });

  /// The body of [removeQueuedPrompt].
  Future<bool> _removeQueuedPrompt(String id) =>
      _serializeQueueChange(() async {
        if (_queuedPromptInFlight == id) return false;
        final kept = _queue.where((entry) => entry.id != id).toList();
        if (kept.length == _queue.length) return true;
        if (!await _queueStore.save(kept)) {
          throw const OfflineQueueWriteException();
        }
        _offlineQueue = kept;
        _queuedPromptsAcceptedUnrecorded.remove(id);
        _explicitQueueResends.remove(id);
        _notifyListeners();
        return true;
      });

  /// The body of [resendQueuedPrompt].
  Future<bool> _resendQueuedPrompt(String id) async {
    final cleared = await _serializeQueueChange(() async {
      if (_queuedPromptInFlight == id ||
          _queuedPromptsAcceptedUnrecorded.contains(id)) {
        return false;
      }
      final index = _queue.indexWhere((entry) => entry.id == id);
      if (index < 0 || !_queue[index].dispatched) return false;
      final next = [..._queue]..[index] = _queue[index].withDispatchedAt(null);
      if (!await _queueStore.save(next)) {
        throw const OfflineQueueWriteException();
      }
      _offlineQueue = next;
      _explicitQueueResends.add(id);
      if (!_disposed) _notifyListeners();
      return true;
    });
    if (cleared) unawaited(flushOfflineQueue());
    return cleared;
  }

  /// The body of [retryQueuedPrompt].
  Future<bool> _retryQueuedPrompt(String id) async {
    if (_queuedPromptInFlight == id) return false;
    final index = _queue.indexWhere((entry) => entry.id == id);
    if (index < 0 || _queue[index].dispatched) return false;
    _explicitQueueResends.add(id);
    if (!_disposed) _notifyListeners();
    await flushOfflineQueue();
    return true;
  }

  /// Persists a replacement for one queue entry. Returns null when the entry
  /// is no longer queued, false when the store refused the write (memory
  /// and storage both stay as they were), true when the change persisted.
  Future<bool?> _replaceQueuedPrompt(
    String id,
    QueuedPrompt Function(QueuedPrompt entry) update,
  ) => _serializeQueueChange(() async {
    final index = _queue.indexWhere((entry) => entry.id == id);
    if (index < 0) return null;
    final next = [..._queue]..[index] = update(_queue[index]);
    if (!await _queueStore.save(next)) return false;
    _offlineQueue = next;
    if (!_disposed) _notifyListeners();
    return true;
  });

  /// Persists the queue without [id]. Returns false when the store refused
  /// the write; a stale in-memory removal is never applied over a persisted
  /// entry, because that entry would resend after the next restart.
  Future<bool> _persistQueuedPromptRemoval(String id) =>
      _serializeQueueChange(() async {
        final kept = _queue.where((entry) => entry.id != id).toList();
        if (kept.length == _queue.length) return true;
        if (!await _queueStore.save(kept)) return false;
        _offlineQueue = kept;
        if (!_disposed) _notifyListeners();
        return true;
      });

  /// The body of [flushOfflineQueue].
  Future<void> _flushOfflineQueue() async {
    if (_disposed ||
        !capabilities.offlinePromptQueue ||
        (!automationPolicy.allows(AutomationBehavior.reconcileQueuedSends) &&
            _explicitQueueResends.isEmpty)) {
      return;
    }
    if (_flushingOfflineQueue) {
      _flushOfflineQueueAgain = true;
      return;
    }
    final profileID = profile?.id;
    if (profileID == null) return;
    final origin = (profileID, profile?.baseUrl, directory, workspace);
    final actScope = _automaticActScope();
    bool eligible(QueuedPrompt entry) =>
        entry.profileID == profileID &&
        !entry.dispatched &&
        (_explicitQueueResends.contains(entry.id) ||
            automationPolicy.allows(AutomationBehavior.reconcileQueuedSends));
    if (!_queue.any(eligible)) return;
    _flushingOfflineQueue = true;
    var sent = 0;
    var touched = false;
    try {
      for (final entry in List.of(_queue)) {
        if (!eligible(entry)) continue;
        final explicitlyRequested = _explicitQueueResends.contains(entry.id);
        bool allowed() =>
            !_disposed &&
            origin == (profile?.id, profile?.baseUrl, directory, workspace) &&
            (explicitlyRequested ||
                automationPolicy.allows(
                  AutomationBehavior.reconcileQueuedSends,
                ));
        if (!allowed()) break;
        final currentApi = await prepareActionTransport();
        await _queueChanges;
        if (!allowed() ||
            currentApi == null ||
            !identical(currentApi, api) ||
            status != StreamStatus.connected ||
            origin != (profile?.id, profile?.baseUrl, directory, workspace)) {
          break;
        }
        if (!_queue.any(
          (queued) => queued.id == entry.id && !queued.dispatched,
        )) {
          continue;
        }
        // Set once the dispatch marker is persisted: from here on every
        // failure is an uncertain outcome that keeps the marker.
        var dispatched = false;
        // Set once the transport reported the prompt accepted.
        var delivered = false;
        // Set once the accepted entry's removal reached storage.
        var recorded = false;
        // Set when the store refused the marker write; nothing was sent.
        var markerRefused = false;
        var stop = false;
        touched = true;
        try {
          if (supportsStagedRevert) {
            final fresh = await currentApi.session(entry.sessionID);
            if (_disposed ||
                !identical(currentApi, api) ||
                origin !=
                    (profile?.id, profile?.baseUrl, directory, workspace)) {
              break;
            }
            final revertChanged =
                sessionsById[entry.sessionID]?.stagedRevert?.fingerprint !=
                fresh.stagedRevert?.fingerprint;
            sessionsById[entry.sessionID] = fresh;
            _markSessionChanged(entry.sessionID, affectsStatus: false);
            if (revertChanged) _resetSessionHistory(entry.sessionID);
            if (fresh.reverted || sessionRevertSaving(entry.sessionID)) {
              throw ApiException(
                'Review the staged revert before sending this queued prompt.',
                statusCode: 409,
                errorTag: 'SessionRevertPending',
              );
            }
          }
          // Persists the dispatch marker, then — only if storage accepted
          // it — puts the prompt on the wire, and records the acceptance
          // before anything else (a selection refresh, a session reload)
          // gets to run. Runs after model/agent prep so those preflight
          // failures stay ordinary retryable errors.
          Future<void> dispatch() async {
            if (!allowed()) return;
            final marked = await _replaceQueuedPrompt(entry.id, (queued) {
              // Selection/preflight can still be cancelled. Close removal
              // only once this serialized dispatch write actually begins,
              // then hold it through the persisted delivery outcome.
              _queuedPromptInFlight = entry.id;
              if (!_disposed) _notifyListeners();
              return queued.withDispatchedAt(
                DateTime.now().millisecondsSinceEpoch,
              );
            });
            if (marked == null) return;
            if (!marked) {
              markerRefused = true;
              return;
            }
            if (!allowed()) {
              // Nothing left the device: restore the draft after a policy
              // change during the durable marker write. A refused rollback
              // retains the conservative marker, never sends the message.
              await _replaceQueuedPrompt(entry.id, (_) => entry);
              return;
            }
            _explicitQueueResends.remove(entry.id);
            dispatched = true;
            await currentApi.promptAsync(
              entry.sessionID,
              text: entry.text,
              model: entry.model,
              agent: entry.agent?.isNotEmpty == true ? entry.agent : null,
              variant: entry.variant?.isNotEmpty == true ? entry.variant : null,
              attachments: entry.attachments,
              agentMentions: entry.mentions,
            );
            delivered = true;
            // Until this write lands the persisted marker keeps the entry
            // out of every future flush, so a refusal or a process death
            // here can only cost a review — never a duplicate send.
            recorded = await _persistQueuedPromptRemoval(entry.id);
          }

          if (currentApi is SessionSelectionGateway) {
            await _mutateSessionSelection(entry.sessionID, (gateway) async {
              await _queueChanges;
              if (!allowed() ||
                  !_queue.any((queued) => queued.id == entry.id)) {
                return false;
              }
              // Persisted offline entries carry intentional choices. Online
              // prompt delivery alone never rewrites shared session state.
              final model = entry.model;
              if (model != null) {
                await gateway.setSessionModel(
                  entry.sessionID,
                  model,
                  entry.variant ?? '',
                );
              }
              if (!identical(api, currentApi)) {
                throw const ProductException('The connection changed.');
              }
              await _queueChanges;
              if (!allowed() ||
                  !_queue.any((queued) => queued.id == entry.id)) {
                return true;
              }
              if (entry.agent?.isNotEmpty == true) {
                await gateway.setSessionAgent(entry.sessionID, entry.agent!);
              }
              if (!identical(api, currentApi)) {
                throw const ProductException('The connection changed.');
              }
              await _queueChanges;
              if (!allowed() ||
                  !_queue.any((queued) => queued.id == entry.id)) {
                return true;
              }
              await dispatch();
              return true;
            }, requireConfirmation: false);
          } else {
            await dispatch();
          }
        } on ApiException catch (error) {
          // Once delivered, whatever failed afterwards (a session refresh, a
          // changed connection) does not undo the acceptance.
          if (!delivered && !dispatched) {
            // Preflight failure: nothing left the device, so the entry stays
            // unmarked and the next flush retries it.
            if (error.statusCode == null) {
              stop = true;
            } else {
              await _replaceQueuedPrompt(
                entry.id,
                (queued) => queued.withError(error.message),
              );
            }
          } else if (!delivered) {
            // After dispatch nothing proves non-delivery: neither prompt
            // endpoint offers an idempotency or receipt contract, and a
            // status code only says who answered, not whether the prompt
            // was enqueued first. The marker stays; only the user's review
            // moves this entry.
            await _replaceQueuedPrompt(
              entry.id,
              (queued) => queued.withError(error.message),
            );
            if (error.statusCode == null) stop = true;
          }
        } catch (error) {
          if (!delivered) {
            if (dispatched) {
              await _replaceQueuedPrompt(
                entry.id,
                (queued) => queued.withError(error.toString()),
              );
            }
            stop = true;
          }
        }
        if (delivered && !recorded) {
          // The server has this prompt; the device could not record that.
          // Say so — a plain "unconfirmed" would invite a resend that is a
          // certain duplicate — and keep the marker whatever the store says.
          _queuedPromptsAcceptedUnrecorded.add(entry.id);
          await _replaceQueuedPrompt(
            entry.id,
            (queued) => queued.withError(
              'OpenCode accepted this prompt, but this device could not '
              'update the queue.',
            ),
          );
        }
        _queuedPromptInFlight = null;
        if (!_disposed) _notifyListeners();
        if (markerRefused) break;
        if (delivered) {
          if (!recorded) break;
          sent += 1;
          // Sent by itself once the server was back: filed for While you
          // were away under the conversation it went to (P6.2).
          if (!explicitlyRequested && actScope != null) {
            final at = DateTime.now();
            unawaited(
              recordAutomaticAct(
                profileId: actScope.profileId,
                location: actScope.project,
                kind: AutomaticActKind.queuedSend,
                target: _automaticActSessionTitle(entry.sessionID),
                eventId: 'queue.sent:${entry.id}',
                at: at,
                sessionId: entry.sessionID,
              ),
            );
          }
        }
        if (stop) break;
      }
    } finally {
      _queuedPromptInFlight = null;
      _flushingOfflineQueue = false;
      if (sent > 0) {
        lastFlushedPromptCount = sent;
        lastFlushSkippedForOtherProfiles = queuedPromptCountForOtherProfiles;
        offlineFlushRevision += 1;
      }
      if (touched && !_disposed) _notifyListeners();
      if (_flushOfflineQueueAgain) {
        _flushOfflineQueueAgain = false;
        if (!_disposed) unawaited(flushOfflineQueue());
      }
    }
  }
}
