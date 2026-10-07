part of '../connection.dart';

// Profile deletion and the local data sweep.

/// [ConnectionController]'s profile deletion sweep.
mixin _ConnectionControllerDeletion on ChangeNotifier {
  ConnectionController get _self;

  /// Removes [profileId] and every piece of local data keyed to it, as one
  /// operation.
  ///
  /// "Remove server" reads — in the UI and in the privacy policy — as a
  /// promise that the server's data leaves the device. Honouring that means
  /// more than dropping the profile row: the Keystore password, the
  /// profile-scoped preferences (model, agent, variant, location, provider
  /// migration flags), queued prompts with their embedded attachments,
  /// composer drafts, and the home-screen widget's session titles all have to
  /// go, including the copies this controller is holding in memory — a
  /// surviving cache would write deleted prompts straight back on the next
  /// save.
  ///
  /// The order is a transaction, and it runs backwards from the obvious one.
  /// Every dependent blob — queued prompts, drafts, the widget snapshot,
  /// the scoped preferences — is rewritten and *verified* first, while the
  /// profile is still saved and the operation is still abortable. Only once
  /// all of that is confirmed gone does the profile row and its Keystore
  /// password go.
  ///
  /// Deleting the profile first, as this used to, meant a later failed write
  /// left prompts and drafts on disk with no server row to attribute them to
  /// while the user was told the server had been removed. Every store here
  /// answers with a success flag; none of them is ignored, and anything that
  /// refuses is reported through [DeleteProfileResult.failures] rather than
  /// being rounded up to success.
  ///
  /// Server-side and provider-side data is untouched; only this device is
  /// cleared.
  ///
  /// [queuedPrompts] is the confirmed [inspectQueuedPromptsForRemoval]
  /// snapshot. When given, the removal stops with a
  /// [QueuedPromptRemovalException] (and removes nothing) if the server's
  /// queued prompts changed since, and [keepQueuedPrompts] moves them into
  /// Saved prompts before they leave the queue.
  Future<DeleteProfileResult> deleteProfileAndLocalData(
    String profileId, {
    QueuedPromptRemovalPlan? queuedPrompts,
    bool keepQueuedPrompts = false,
  }) => _self._deleteProfileAndLocalDataBody(
    profileId,
    queuedPrompts: queuedPrompts,
    keepQueuedPrompts: keepQueuedPrompts,
  );
}

extension _ConnectionControllerDeletionImpl on ConnectionController {
  /// The body of [deleteProfileAndLocalData].
  Future<DeleteProfileResult> _deleteProfileAndLocalDataBody(
    String profileId, {
    QueuedPromptRemovalPlan? queuedPrompts,
    bool keepQueuedPrompts = false,
  }) {
    if (_disposed || profileId.isEmpty) {
      return Future.error(StateError('The server profile is unavailable'));
    }
    if (queuedPrompts != null && queuedPrompts.profileID != profileId) {
      return Future.error(ArgumentError('Queued prompts of another server'));
    }
    final pending = _profileDeletions[profileId];
    if (pending != null) return pending;
    // Close admission synchronously, before any drain can yield. An epoch also
    // rejects old callbacks after a failed deletion makes the profile usable.
    _deletingReadProfiles.add(profileId);
    final genUiDrain = _genUiCloseProfile(profileId);
    final sessionLinkDrain = SessionLinkBindings.closeProfile(
      store.prefs,
      profileId,
    );
    _profileDeletionRevisions[profileId] =
        (_profileDeletionRevisions[profileId] ?? 0) + 1;
    _monitorAttentionReader.forget(profileId);
    _profileMonitor?.removeProfile(profileId, retainIdentity: true);
    _quotaMonitor?.removeProfile(profileId, retainIdentity: true);
    _pendingAuth.block(profileId);
    _integrationCommandAttempts.removeWhere(
      (key, _) => _authKeyProfile(key) == profileId,
    );
    _oauthStarts.removeWhere((key) => _authKeyProfile(key) == profileId);
    _authRecoveryActions.removeWhere(
      (key) => _authKeyProfile(key) == profileId,
    );
    final activity = _automaticActivityFor(profileId);
    final policy = AutomationPolicyController.forProfile(
      store.prefs,
      profileId,
    );
    final operation = _profileDeletionChanges
        .then((_) async {
          // Drain admitted Undo before entering the queue lane: an inverse may
          // itself need that lane. Preparation retains history and callbacks.
          await Future.wait([
            if (activity != null) activity.prepareForDeletion(),
            policy.pauseForDeletion(),
            sessionLinkDrain,
            genUiDrain,
          ]);
          // Admitted activity inverses have finished; from here no new prompt
          // may join this profile while the removal is in progress.
          _closedQueueProfiles.add(profileId);
          await _profileMonitor?.drain(profileId);
          await _quotaMonitor?.drain(profileId);
          // Phone agents first: auth, owned setup, host, feeds; ProfileStore
          // removal (native drain, secrets) follows inside the transaction.
          await _genUiDeleteProfile(profileId);
          await _paCloseForDeletion(profileId);
          return _deleteProfileAndLocalData(
            profileId,
            queuedPrompts: queuedPrompts,
            keepQueuedPrompts: keepQueuedPrompts,
          );
        })
        .whenComplete(() async {
          _deletingReadProfiles.remove(profileId);
          if (store.profiles.any((p) => p.id == profileId)) {
            _closedQueueProfiles.remove(profileId);
            _genUiReopenProfile(profileId);
            activity?.cancelDeletion();
            policy.cancelDeletion();
            ConsentOwners.cancelDeletion(store.prefs, profileId);
            await SessionLinkBindings.cancelDeletion(store.prefs, profileId);
            BuiltinServerOwner.forPreferences(
              store.prefs,
            ).cancelDeletion(profileId);
            _profileMonitor?.cancelDeletion(profileId);
            _quotaMonitor?.cancelDeletion(profileId);
            _pendingAuth.cancelDeletion(profileId);
            if (_orchestration?.profileId == profileId &&
                _orchestration?.phase == OrchestrationPhase.stopped) {
              _syncOrchestration(null);
              _syncOrchestration(profile);
            }
          }
          _profileDeletions.remove(profileId);
          _syncProfileServices();
        });
    _profileDeletions[profileId] = operation;
    _profileDeletionChanges = operation.then<void>(
      (_) {},
      onError: (Object _) {},
    );
    _notifyProfileDataChanged();
    return operation;
  }

  Future<DeleteProfileResult> _deleteProfileAndLocalData(
    String profileId, {
    QueuedPromptRemovalPlan? queuedPrompts,
    bool keepQueuedPrompts = false,
  }) async {
    await _draftChanges;
    await _pendingAuth.drain(profileId);
    // Selection writes begin before network refresh; drain them before the
    // profile sweep so a delayed write cannot resurrect its location.
    try {
      await _locationWrite;
    } catch (_) {}
    // Drain shortcut writes before the deletion sweep discovers its keys.
    // A failed write must not prevent the user from removing a profile.
    try {
      await _modelLibraryWrite;
    } catch (_) {}
    try {
      await _sessionReadStore.drain(profileId);
    } catch (_) {}
    try {
      await _returnBriefStore.drain(profileId);
    } catch (_) {}
    try {
      await _sessionPins.drain(profileId);
    } catch (_) {}
    try {
      await sessionAutoApproval.drain(profileId);
    } catch (_) {}
    try {
      await _promptShelf.drain(profileId);
    } catch (_) {}

    await _sessionTailCache.drain(profileId);
    _sessionTailCache.forget(profileId);
    await _sessionInventoryCache.drain(profileId);
    _sessionInventoryCache.forget(profileId);
    final scopedKeys = store.profileScopedPreferenceKeys(profileId);
    // Retain these owners through a failed row/Keystore commit as well as
    // through queue preflight. ProfileStore sweeps them after the row commits.
    final retainedKeys = {
      'oc.automaticActivity.$profileId',
      AutomationPolicyController.keyFor(profileId),
      'oc.teamEngineDeleted.$profileId',
    };
    final failures = <String>[];

    // Snapshot writes stay suspended for the whole transaction: any
    // notification would republish the deleted profile's session titles
    // straight back over the snapshot this method just cleared.
    _widgetSnapshotSuspended = true;
    try {
      // 1. Queued prompts — the largest and most sensitive blob, holding
      //    prompt text and attachment data URLs. Serialized with every other
      //    queue write: a flush may be persisting a dispatch marker for the
      //    active profile at this moment, and a rewrite computed from the
      //    pre-marker list would strip that marker from disk while the
      //    prompt is on the wire.
      var clearedQueued = 0;
      await _serializeQueueChange(() async {
        // Unknown is never empty, including callers without a confirmation.
        if (!_queueStore.readable) {
          throw const QueuedPromptRemovalException(
            changed: false,
            unreadable: true,
          );
        }
        if (keepQueuedPrompts && queuedPrompts == null) {
          throw const QueuedPromptRemovalException(changed: true);
        }
        // A repaired source may differ from a previously unreadable cache.
        // Every controller queue writer has drained before this fresh read.
        _offlineQueue = _queueStore.load();
        if (queuedPrompts != null) {
          // The person confirmed a count: act on exactly that, or on nothing.
          final live = [
            for (final entry in _queue)
              if (entry.profileID == profileId) entry.toJson(),
          ];
          final confirmed = [
            for (final entry in queuedPrompts.prompts) entry.toJson(),
          ];
          try {
            if (jsonEncode(live) != jsonEncode(confirmed)) {
              throw StateError('Queued prompts changed');
            }
            _keptQueued.validateCurrent(queuedPrompts);
          } on StateError {
            throw const QueuedPromptRemovalException(changed: true);
          }
          if (keepQueuedPrompts) {
            try {
              await _keptQueued.keepAsDrafts(queuedPrompts);
            } on StateError {
              throw QueuedPromptRemovalException(
                changed: false,
                unreadable: !_queueStore.readable,
              );
            }
          }
        }
        // Queue validation and preservation succeeded within this lane.
        // Only now invalidate destructive owners and begin cleanup.
        await Future.wait<void>([
          BuiltinServerRecovery.suspendForProfile(store.prefs, profileId),
          ManagedServerRecovery.prepareForProfileDeletion(
            store.prefs,
            profileId,
          ),
        ]);
        // Keep the shared runtime owner through queue preflight failures.
        // Once preservation succeeds, prevent fallback to another profile.
        await BuiltinServerOwner.forPreferences(
          store.prefs,
        ).clearProfile(profileId);
        _promptShelfDeletionRevisions[profileId] =
            (_promptShelfDeletionRevisions[profileId] ?? 0) + 1;
        // Outstanding saved-prompt Undo handles refuse from here on; a deleted
        // server's prompts never come back.
        if (_savedPrompts?.profileID == profileId) {
          _savedPrompts!.dispose();
          _savedPrompts = null;
        }
        await ConsentOwners.closeProfile(store.prefs, profileId);
        final engineProfile = store.profiles
            .where((p) => p.id == profileId)
            .firstOrNull;
        if (engineProfile?.orchestration?.provider ==
                OrchestrationProvider.phoneEngine ||
            (engineProfile?.teamEngineAuth.isNotEmpty ?? false) ||
            BuiltinLinux.managesServerUrl(engineProfile?.baseUrl) ||
            phoneProjectEngine.hasLifecycleOwnership(profileId) ||
            store.prefs.getBool('oc.teamEngineDeleted.$profileId') == true) {
          try {
            await phoneProjectEngine.deleteProfile(profileId);
          } catch (_) {
            failures.add('phone team engine data');
          }
        }
        try {
          // The plugin's sibling stops first so no refetch can rewrite the
          // `oc.orchestration.<id>.` keys the scoped sweep below discovers.
          if (_orchestration?.profileId == profileId) {
            await _orchestration!.stop();
          }
          await _orchestrationStore.drain(profileId);
          // A project fixture may have finished its first durable write while
          // stop drained it. Include every now-settled key in this sweep.
          scopedKeys.addAll(store.profileScopedPreferenceKeys(profileId));
        } catch (_) {}
        final keptQueue = [
          for (final entry in _queue)
            if (entry.profileID != profileId) entry,
        ];
        final removedQueued = _queue.length - keptQueue.length;
        if (removedQueued == 0) return;
        if (await _queueStore.save(keptQueue)) {
          _offlineQueue = keptQueue;
          clearedQueued = removedQueued;
        } else {
          failures.add(
            '$removedQueued queued '
            '${removedQueued == 1 ? 'prompt' : 'prompts'}',
          );
        }
      });

      // 2. Composer drafts.
      var clearedDrafts = 0;
      await _serializeDraftChange(() async {
        final keptDrafts = SessionDraftStore.withoutProfile(_drafts, profileId);
        final removedDrafts = _drafts.length - keptDrafts.length;
        if (removedDrafts > 0) {
          if (await _draftStore.save(keptDrafts)) {
            _sessionDrafts = keptDrafts;
            clearedDrafts = removedDrafts;
          } else {
            failures.add(
              '$removedDrafts unsent ${removedDrafts == 1 ? 'draft' : 'drafts'}',
            );
          }
        }
        if (!await _collectDraftAttachments(owner: profileId)) {
          failures.add('draft attachments');
        }
        if (!await _collectDraftAttachments(owner: '')) {
          failures.add('unattributed draft attachments');
        }
      });

      // 3. The home-screen widget's session titles.
      if (!await promptPhotos.clearForProfile(profileId)) {
        failures.add('pending photo');
      }
      await _pendingWidgetSnapshotWrite;
      final widgetOutcome = await _widgetSnapshot.clearForProfile(profileId);
      if (widgetOutcome == WidgetSnapshotClear.failed) {
        failures.add('the home-screen widget’s sessions');
      }
      // The launcher's pinned-session shortcuts and the tile's count follow
      // the same ownership rule.
      await _pendingLauncherWrite;
      final shortcutOutcome = await _pinnedShortcuts.clearForProfile(profileId);
      if (shortcutOutcome == PinnedShortcutClear.failed) {
        failures.add('the launcher’s pinned-session shortcuts');
      }
      final tileOutcome = await _attentionTile.clearForProfile(profileId);
      if (tileOutcome == AttentionTileClear.failed) {
        failures.add('the Quick Settings tile’s count');
      }

      // 4. Stash metadata and its private files must go before the generic
      // preference sweep. On failure that sweep would erase the durable owner
      // marker and make orphan-file cleanup undiscoverable on the next launch.
      final clearedStash = await _promptShelf.clearForProfile(profileId);
      if (!clearedStash) failures.add('stashed prompts and attachments');
      final unclearedKeys = clearedStash
          ? await store.removeScopedPreferences(
              profileId,
              excluding: retainedKeys,
            )
          : scopedKeys;
      // The folders this server opened in shared storage are no longer bound.
      // Only a server that opened some changes the bound set, and the native
      // refresh never holds up the removal: a platform reply that is slow or
      // never comes (no handler) would otherwise leave the server half
      // deleted. The roots are read before the call's first await.
      if (clearedStash &&
          scopedKeys.contains(SharedProjectRoots.keyFor(profileId))) {
        unawaited(
          SharedProjectRoots.push(store.prefs).catchError((Object _) {}),
        );
      }
      if (clearedStash && unclearedKeys.isNotEmpty) {
        failures.add(
          '${unclearedKeys.length} saved '
          '${unclearedKeys.length == 1 ? 'setting' : 'settings'}',
        );
      }

      // 5. Only now the profile row and the Keystore password. A server
      //    whose data is still on disk keeps its row, so the user is never
      //    told a deletion happened that did not.
      if (failures.isNotEmpty) {
        return DeleteProfileResult(
          removedPreferenceKeys: scopedKeys
              .difference(unclearedKeys)
              .difference(retainedKeys),
          removedQueuedPrompts: clearedQueued,
          removedDrafts: clearedDrafts,
          clearedWidgetSnapshot: widgetOutcome == WidgetSnapshotClear.cleared,
          clearedPinnedShortcuts:
              shortcutOutcome == PinnedShortcutClear.cleared,
          clearedAttentionTile: tileOutcome == AttentionTileClear.cleared,
          removedProfile: false,
          failures: List.unmodifiable(failures),
        );
      }
      await store.remove(profileId);
      _profileMonitor?.removeProfile(profileId);
      _quotaMonitor?.removeProfile(profileId);
      final unclearedCommittedKeys = <String>{};
      await AutomationPolicyController.closeProfile(store.prefs, profileId);
      final policyKey = AutomationPolicyController.keyFor(profileId);
      if (scopedKeys.contains(policyKey)) {
        try {
          if (!await store.prefs.remove(policyKey)) {
            unclearedCommittedKeys.add(policyKey);
          }
        } catch (_) {
          unclearedCommittedKeys.add(policyKey);
        }
        if (unclearedCommittedKeys.contains(policyKey)) {
          failures.add('automation settings');
        }
      }
      if (!await AutomaticActivityController.closeProfile(
        store.prefs,
        profileId,
      )) {
        failures.add('automatic activity');
        unclearedCommittedKeys.add('oc.automaticActivity.$profileId');
      }
      // The plugin's Keystore entries go with the password, never before
      // the row: a kept server keeps its secrets.
      try {
        await _orchestrationStore.sweep(profileId);
      } catch (_) {}

      return DeleteProfileResult(
        removedPreferenceKeys: scopedKeys.difference(unclearedCommittedKeys),
        failures: List.unmodifiable(failures),
        removedQueuedPrompts: clearedQueued,
        removedDrafts: clearedDrafts,
        clearedWidgetSnapshot: widgetOutcome == WidgetSnapshotClear.cleared,
        clearedPinnedShortcuts: shortcutOutcome == PinnedShortcutClear.cleared,
        clearedAttentionTile: tileOutcome == AttentionTileClear.cleared,
      );
    } finally {
      // A partial deletion can already have removed stash/history/pin/read
      // metadata. Reconcile optimistic preference caches even when a later
      // step failed; never let a retained in-memory shelf resurrect that data.
      try {
        await _promptShelf.reload();
      } catch (_) {
        // PromptShelfStore independently fails closed on uncertain writes.
      }
      _sessionReadStore.forgetProfile(profileId);
      _returnBriefStore.forgetProfile(profileId);
      _sessionPins.forget(profileId);
      sessionAutoApproval.forget(profileId);
      _promptShelf.forget(profileId);
      _pendingAuth.forget(profileId);
      // Notify while republishing is still suspended, then lift it: this
      // controller keeps the deleted profile's sessions in memory until the
      // caller disconnects, and a republish would put their titles straight
      // back onto the home screen.
      _deletingReadProfiles.remove(profileId);
      _syncProfileServices();
      if (!_disposed) _notifyListeners();
      _widgetSnapshotSuspended = false;
    }
  }
}
