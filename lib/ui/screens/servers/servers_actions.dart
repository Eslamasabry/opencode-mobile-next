part of '../servers_screen.dart';

/// The Servers screen's actions, in the state's own library.
extension _ServersActions on _ServersScreenState {
  void _observedTermux(TermuxRunningServer server) {
    final found =
        server.state != TermuxRunningServerState.unsupported &&
        server.state != TermuxRunningServerState.absent;
    if (found != _termuxFound && mounted) {
      _set(() => _termuxFound = found);
    }
  }

  Future<void> _handleRouteRequest(ServersRouteRequest request) async {
    ServerProfile? target;
    for (final p in ref.read(bootstrapProvider).store.profiles) {
      if (p.id == request.profileID) target = p;
    }
    switch (request.kind) {
      case ServersRouteRequestKind.add:
        await _edit(
          initialBackend: request.backend,
          initialUrl: request.initialUrl,
        );
      case ServersRouteRequestKind.connect:
        if (target != null) {
          await _connect(target, detectedRunning: request.detectedRunning);
        }
      case ServersRouteRequestKind.enterPhoneCredentials:
        await _enterPhoneCredentials(
          existing: target,
          openCode2: request.openCode2,
        );
      case ServersRouteRequestKind.forget:
        if (target != null) await _delete(target);
    }
  }

  /// A removal that did not finish, said where the list is: the same inline
  /// notice a failed connect uses, never a snackbar (KIT-34, STATE-3).
  void _showFailure(String message, {String? details}) {
    if (!mounted) return;
    _set(() {
      _listFailure = message;
      _listFailureDetails = details;
    });
  }

  /// This phone for the Termux server: its status, versions, tools and log.
  Future<void> _openTermuxSetup() async {
    await openThisPhone(context, kind: PhoneHostKind.termux);
    if (mounted) _set(() => _termuxRevision++);
  }

  /// The one door to running an agent on this phone (phone setup v2,
  /// screen A). Termux and the in-app setup both live behind it, so the
  /// welcome and the list never offer two competing phone paths.
  Future<void> _openPhoneSetup() async {
    await openPhoneSetupStart(context);
    // Termux may have been set up from its "Other ways" row meanwhile.
    if (mounted) _set(() => _termuxRevision++);
  }

  /// The detected running-server entry for [profiles]: it decides on its own
  /// whether anything is shown, so both the welcome and the list embed it
  /// unconditionally and stay platform-gated through it.
  Widget _runningServerEntry(
    List<ServerProfile> profiles,
    ConnectionController connection, {
    bool dividerAbove = false,
    bool lead = false,
  }) {
    // Both servers this app can run on the phone lead the list and are
    // controlled in place: OpenCode first, then the Claude Code daemon. Each
    // entry decides on its own whether it has anything to show.
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        _openCodeServerEntry(
          profiles,
          connection,
          dividerAbove: dividerAbove,
          lead: lead,
        ),
        LocalAgentServerEntry(
          // In a list, a hairline above it whenever a row may precede it.
          dividerAbove:
              dividerAbove || savedManagedPhoneProfile(profiles) != null,
          profiles: profiles,
          busy: _busy,
          revision: _termuxRevision,
          connectedProfileID: connection.api == null
              ? null
              : connection.profile?.id,
          busyConversations: connection.busySessions.length,
          onDisconnect: () async {
            if (!await confirmDisconnectServer(context, connection)) return;
            await connection.disconnect(keepActive: true);
          },
          onForget: _delete,
          onManage: _openTermuxSetup,
          onConnect: (profile) => _connect(profile, detectedRunning: true),
          onOpenSaved: _connect,
        ),
      ],
    );
  }

  Widget _openCodeServerEntry(
    List<ServerProfile> profiles,
    ConnectionController connection, {
    bool dividerAbove = false,
    bool lead = false,
  }) {
    final entry = TermuxRunningServerEntry(
      dividerAbove: dividerAbove,
      lead: lead,
      onObserved: lead ? _observedTermux : null,
      profiles: profiles,
      busy: _busy,
      revision: _termuxRevision,
      connectedProfileID: connection.api == null
          ? null
          : connection.profile?.id,
      busyConversations: connection.busySessions.length,
      actions: () {
        final controls = LocalServerControls(
          store: ref.read(bootstrapProvider).store,
          connection: connection,
        );
        return LocalServerCardActions(
          restart: () async => controls.restart(),
          stop: controls.stop,
        );
      }(),
      onDisconnect: () async {
        if (!await confirmDisconnectServer(context, connection)) return;
        await connection.disconnect(keepActive: true);
      },
      onForget: _delete,
      onManage: _openTermuxSetup,
      onConnect: (profile) => _connect(profile, detectedRunning: true),
      onOpenSaved: _connect,
      onEnterCredentials: (server, existing) => _enterPhoneCredentials(
        existing: existing,
        openCode2: server.flavor == ServerFlavor.v2,
      ),
    );
    // Under the Termux server's row, once: the move to the in-app server.
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        entry,
        TermuxMigrationOffer(profiles: profiles, store: connection.store),
      ],
    );
  }

  /// The phone's own server is running but the app has no usable password for
  /// it. The app wrote that password, so it first restores it from the phone;
  /// only when that is impossible (or the restored one was already refused)
  /// does it ask the person to type one.
  Future<void> _enterPhoneCredentials({
    required ServerProfile? existing,
    required bool openCode2,
  }) async {
    if (_busy) return;
    final recovered = await TermuxBridge.managedServerPassword();
    if (!mounted) return;
    final alreadyRefused =
        existing != null &&
        !existing.requiresPasswordReentry &&
        existing.password == recovered;
    if (recovered != null && !alreadyRefused) {
      final store = ref.read(bootstrapProvider).store;
      final profile =
          existing ??
          ServerProfile(
            id: DateTime.now().microsecondsSinceEpoch.toString(),
            name: lookupAppLocalizations(
              Localizations.localeOf(context),
            ).e7SetupThisDevice,
            baseUrl: TermuxBridge.managedServerUrl,
            flavor: openCode2 ? ServerFlavor.v2 : ServerFlavor.v1,
          );
      profile
        ..username = 'opencode'
        ..password = recovered
        ..requiresPasswordReentry = false;
      await store.upsert(profile);
      if (!mounted) return;
      _set(() {});
      await _connect(profile, detectedRunning: true);
      return;
    }
    await _edit(
      existing: existing,
      connectOnSave: true,
      initialUrl: TermuxBridge.managedServerUrl,
      focusPassword: true,
      openCode2Intent: openCode2,
    );
  }

  /// [detectedRunning] means the caller already knows which managed runtime
  /// is live and chose its profile, so the runtime-choice detour is moot.
  Future<void> _connect(ServerProfile p, {bool detectedRunning = false}) async {
    if (_busy) return;
    if (!detectedRunning &&
        _needsManagedRuntimeChoice(
          p,
          ref.read(bootstrapProvider).store.profiles,
        )) {
      await _openTermuxSetup();
      return;
    }
    if (p.requiresPasswordReentry || p.requiresCodexTokenReentry) {
      await _edit(
        existing: p,
        focusPassword: p.backend == ServerBackend.openCode,
        connectOnSave: detectedRunning,
      );
      return;
    }
    _set(() {
      _busy = true;
      _listFailure = null;
      _listFailureDetails = null;
    });
    final conn = ref.read(connProvider);
    Object? failure;
    try {
      await conn.connect(p);
    } catch (error) {
      failure = error;
    } finally {
      if (mounted) _set(() => _busy = false);
    }
    if (!mounted) return;
    if (conn.api != null && failure == null) {
      Navigator.of(context).pushNamedAndRemoveUntil('/home', (_) => false);
    } else {
      final detail = productErrorText(
        conn.lastError ??
            failure ??
            lookupAppLocalizations(
              Localizations.localeOf(context),
            ).e7SetupConnectionFailed,
      );
      _set(() {
        _listFailure = lookupAppLocalizations(
          Localizations.localeOf(context),
        ).e7SetupConnectFailedDetail(p.name, detail);
      });
    }
  }

  /// Completes with whether the editor saved a server.
  ///
  /// A new server with nothing preset opens at the flow's first step (what
  /// runs there), whether it came from Add server, the welcome's "On my
  /// computer", the server switcher's Add or phone setup: one path.
  Future<bool> _edit({
    ServerProfile? existing,
    ServerBackend? initialBackend,
    bool focusPassword = false,
    String? initialUrl,
    bool openCode2Intent = false,
    bool connectOnSave = false,
  }) async {
    final isNew = existing == null;
    final useTailscale =
        existing != null &&
        ref
                .read(bootstrapProvider)
                .store
                .prefs
                .getBool('oc.tailscale.${existing.id}') ==
            true;
    // The editor stays open until the save (and, for new or active profiles,
    // the connect) has succeeded, so any failure is shown where the fields
    // that fix it are — not as a snackbar over a list the user just left.
    final result = await Navigator.of(context).push<ServerProfile>(
      KitPageRoute<ServerProfile>(
        builder: (_) => _ProfileEditorScreen(
          existing: existing,
          initialBackend: initialBackend,
          reconnectOnSave:
              connectOnSave ||
              existing?.id == ref.read(bootstrapProvider).store.activeId,
          focusPassword: focusPassword,
          tailscale: useTailscale,
          initialUrl: initialUrl,
          openCode2Intent: openCode2Intent,
          // A new server's first step also offers the other ways in, so
          // the list holds no second panel of them (R3).
          onPhoneSetup: isNew && platformCapabilities.supportsTermux
              ? _openPhoneSetup
              : null,
          onExternalAgents: isNew ? _externalAgents : null,
          onSubmit: (profile, {required tailscale}) => _saveAndConnect(
            profile,
            isNew: isNew,
            tailscale: tailscale,
            forceConnect: connectOnSave,
          ),
          secureStorageProbe: () =>
              ref.read(bootstrapProvider).store.secureStorageProblem(),
        ),
      ),
    );
    if (result == null) return false;
    if (!mounted) return true;
    if (isNew || connectOnSave || result.backend == ServerBackend.codex) {
      Navigator.of(context).pushNamedAndRemoveUntil('/home', (_) => false);
    }
    return true;
  }

  /// Saves [result] and connects profiles whose submit action promises it.
  /// A brand-new profile and every Codex profile promise "Save & connect";
  /// edits of existing non-active OpenCode profiles keep saving only.
  Future<_SubmitOutcome> _saveAndConnect(
    ServerProfile result, {
    required bool isNew,
    bool tailscale = false,
    bool forceConnect = false,
  }) async {
    final copy = lookupAppLocalizations(Localizations.localeOf(context));
    final store = ref.read(bootstrapProvider).store;
    final wasActive = store.activeId == result.id;
    var saved = false;
    _set(() {
      _busy = true;
      _listFailure = null;
      _listFailureDetails = null;
    });
    try {
      await store.upsert(result);
      saved = true;
      if (tailscale &&
          !await store.prefs.setBool('oc.tailscale.${result.id}', true)) {
        throw StateError(copy.e7SetupGuidanceSaveFailed);
      }
      if (wasActive ||
          isNew ||
          forceConnect ||
          result.backend == ServerBackend.codex) {
        final savedProfile = store.profiles.firstWhere(
          (profile) => profile.id == result.id,
        );
        final conn = ref.read(connProvider);
        await conn.connect(savedProfile);
        if (conn.api == null) {
          // The connection's raw failure is the cause (for details); the
          // words say what it means.
          throw ProductException(
            productErrorText(conn.lastError ?? copy.e7SetupDidNotConnect),
            cause: conn.lastError,
          );
        }
      }
      return (saved: true, failure: null, details: null);
    } catch (error) {
      final detail = productErrorText(error);
      return (
        saved: saved,
        failure: saved
            ? copy.e7SetupSavedConnectFailed(result.name, detail)
            : copy.e7SetupSaveFailed(result.name, detail),
        details: productErrorDetails(error),
      );
    } finally {
      if (mounted) _set(() => _busy = false);
    }
  }

  /// Names what removal actually deletes, as counted facts (DATA-11: a
  /// removal nobody can restore is confirmed first). Queued prompts and
  /// drafts are the only unsent work at stake, so they are counted; queued
  /// prompts move to Saved prompts unless the person deletes them too
  /// (P7.2); the server itself keeps everything; and removing the server in
  /// use says what the person sees next.
  List<KitConsequence> _removalConsequences(
    AppLocalizations copy,
    ConnectionController connection,
    String id, {
    required QueuedPromptRemovalPlan? queued,
    required bool active,
  }) {
    final drafts = connection.draftCountForProfile(id);
    return [
      if (queued != null && queued.count > 0)
        KitConsequence(
          copy.serversRemoveQueuedKept(queued.count),
          key: const ValueKey('remove-server-queued-kept'),
          mark: KitConsequenceMark.kept,
        ),
      if (queued != null && queued.uncertainCount > 0)
        KitConsequence(
          copy.serversRemoveQueuedUncertain(queued.uncertainCount),
        ),
      if (drafts > 0)
        KitConsequence(
          copy.serversRemoveDrafts(drafts),
          mark: KitConsequenceMark.lost,
        ),
      if (active)
        KitConsequence(
          copy.serversRemoveActiveNext,
          key: const ValueKey('remove-server-active-next'),
        ),
      KitConsequence(
        copy.serversRemoveServerKeeps,
        mark: KitConsequenceMark.kept,
      ),
    ];
  }

  Future<void> _delete(ServerProfile p) async {
    final copy = lookupAppLocalizations(Localizations.localeOf(context));
    final connection = ref.read(connProvider);
    final active =
        ref.read(bootstrapProvider).store.activeId == p.id &&
        connection.api != null;
    // Counted now and acted on exactly: a changed queue stops the removal.
    final QueuedPromptRemovalPlan queued;
    try {
      queued = connection.inspectQueuedPromptsForRemoval(p.id);
    } catch (error) {
      _showFailure(
        copy.serversRemoveQueuedUnreadable(p.name),
        details: productErrorDetails(error),
      );
      return;
    }
    var deleteQueued = false;
    final ok = await showKitConfirm(
      context,
      kind: KitConfirmKind.destructive,
      title: copy.e7SetupRemoveServer(p.name),
      body: copy.serversRemoveBody,
      confirmLabel: copy.capsuleRemove,
      icon: AppIconography.delete,
      consequenceItems: _removalConsequences(
        copy,
        connection,
        p.id,
        queued: queued,
        active: active,
      ),
      alternative: queued.count > 0
          ? KitAction(
              key: ValueKey('remove-server-delete-queued-${p.id}'),
              label: copy.serversRemoveDeleteQueued(queued.count),
              destructive: true,
              onPressed: () => deleteQueued = true,
            )
          : null,
      sheetKey: ValueKey('remove-server-sheet-${p.id}'),
      confirmKey: ValueKey('confirm-remove-server-${p.id}'),
    );
    if (!(ok || deleteQueued) || !mounted) return;
    final store = ref.read(bootstrapProvider).store;
    final wasActive = store.activeId == p.id;
    var removed = false;
    _set(() {
      _busy = true;
      _listFailure = null;
      _listFailureDetails = null;
    });
    try {
      // The cascade verifies every store it writes and reports what refused.
      // A partial deletion is stated, never rounded up to the silent success
      // the list rebuild would otherwise imply.
      final result = await connection.deleteProfileAndLocalData(
        p.id,
        queuedPrompts: queued,
        keepQueuedPrompts: !deleteQueued,
      );
      final partial = result.partialDeletionMessage;
      if (partial != null) {
        _showFailure(partial);
        if (!result.removedProfile) return;
      }
      removed = true;
      if (wasActive) {
        await connection.disconnect(keepActive: true);
      }
    } on QueuedPromptRemovalException catch (error) {
      _showFailure(
        error.unreadable
            ? copy.serversRemoveQueuedUnreadable(p.name)
            : error.changed
            ? copy.serversRemoveQueuedChanged(p.name)
            : copy.serversRemoveQueuedNotKept(p.name),
        details: error.unreadable ? productErrorDetails(error) : null,
      );
    } catch (error) {
      if (removed) {
        _showFailure(
          copy.e7SetupRemovedDisconnectFailed(p.name, productErrorText(error)),
        );
      } else {
        _showFailure(copy.e7SetupRemoveFailed(p.name, productErrorText(error)));
      }
    } finally {
      if (mounted) _set(() => _busy = false);
    }
  }

  /// Prompts queued for [p] while it cannot send them: every server but
  /// the connected one. An isolated view never reads other servers.
  int _waitingFor(ServerProfile p, ConnectionController connection) {
    if (connection.isIsolated) return 0;
    if (connection.api != null && connection.profile?.id == p.id) return 0;
    return connection.queuedPromptCountForProfile(p.id);
  }

  /// The connected server [p]'s waiting prompts can move to, by name, or
  /// null when there is none (slice-queue-move).
  String? _moveDestinationName(
    ServerProfile p,
    ConnectionController connection,
    AppLocalizations copy,
  ) {
    final destination = connection.queuedPromptMoveDestination;
    if (destination == null || destination.id == p.id) return null;
    return serverDisplayName(
      destination,
      copy,
      among: connection.store.profiles,
    );
  }

  /// Moves prompts waiting for [p] into a conversation on the connected
  /// server; the sheet asks which, and says what happened.
  Future<void> _moveQueued(ServerProfile p) async {
    _set(() {
      _listFailure = null;
      _listFailureDetails = null;
    });
    await showQueuedPromptMoveSheet(
      context,
      connection: ref.read(connProvider),
      source: p,
      onProblem: (message, {details}) =>
          _showFailure(message, details: details),
    );
  }

  Future<void> _externalAgents() async {
    final bootstrap = ref.read(bootstrapProvider);
    final store = ExternalAgentStore(
      bootstrap.store.prefs,
      bootstrap.store.secure,
    );
    try {
      await pushKitPage<void>(
        context,
        (_) => ExternalAgentsScreen(store: store),
      );
    } finally {
      store.dispose();
    }
  }

  void _demo() =>
      unawaited(pushKitPage<void>(context, (_) => const DemoScreen()));

  /// The monitor's current words about [profile], or null when it has none
  /// (isolated, unreadable or stale): a row then says nothing rather than
  /// something old.
  ProfileAttentionSnapshot? _snapshotFor(
    ServerProfile profile,
    ConnectionController connection,
  ) {
    if (connection.isIsolated || !connection.isProfileReadable(profile.id)) {
      return null;
    }
    final snapshot = connection.profileMonitor.snapshotFor(profile.id);
    return snapshot.isCurrent ? snapshot : null;
  }

  /// [profiles] most urgent first (R1): what waits on the person, then
  /// what is working, then the rest in the order they were saved.
  List<ServerProfile> _byUrgency(
    List<ServerProfile> profiles,
    ConnectionController connection,
  ) {
    int rank(ServerProfile profile) {
      final snapshot = _snapshotFor(profile, connection);
      final connected =
          connection.api != null && connection.profile?.id == profile.id;
      if ((snapshot?.requests.length ?? 0) > 0 ||
          (connected &&
              (connection.awaitingPermissions.isNotEmpty ||
                  connection.questions.isNotEmpty))) {
        return 0;
      }
      if ((snapshot?.runningCount ?? 0) > 0 ||
          (connected && connection.busySessions.isNotEmpty)) {
        return 1;
      }
      return 2;
    }

    final indexed = [for (final (i, p) in profiles.indexed) (i, p, rank(p))];
    indexed.sort((a, b) {
      final byRank = a.$3.compareTo(b.$3);
      return byRank != 0 ? byRank : a.$1.compareTo(b.$1);
    });
    return [for (final entry in indexed) entry.$2];
  }
}
