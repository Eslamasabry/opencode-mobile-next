part of '../connection.dart';

// Project and workspace location: saved location restore, recent locations and switching.

/// Whether a folder can be read on the server: [exists] when a listing
/// succeeded, [missing] only when the server says the folder is not there,
/// [unknown] when the check could not run (offline, timeout, server error).
enum _FolderCheck { exists, missing, unknown }

/// [ConnectionController]'s project and workspace location.
mixin _ConnectionControllerLocations on ChangeNotifier {
  ConnectionController get _self;

  int locationRevision = 0;
  String? directory;
  String? workspace;
  bool locationLoading = false;
  String? locationError;
  String? locationNotice;

  /// True while an OpenCode connection has no usable project folder: nothing
  /// was chosen yet, or the choice resolves to a home folder or filesystem
  /// root. Workspace blocks session creation until the user creates or opens
  /// a real project folder; the server's own working directory is never used
  /// as a workspace (see `workspace_paths.dart`).
  bool get workspaceChoiceRequired => _self._workspaceChoiceRequired;

  /// Set when the saved directory was restored without the project list
  /// confirming it; [revalidateRestoredLocation] clears it once the list
  /// confirms the directory.
  bool _pendingLocationRevalidation = false;
  bool _restoringSavedLocation = false;
  Future<void> _locationWrite = Future.value();

  @visibleForTesting
  bool get pendingLocationRevalidation => _pendingLocationRevalidation;

  /// True while [connect] is still validating the saved project folder: the
  /// folder is known (see [savedProjectDirectory]) but not open yet.
  bool get restoringSavedLocation => _restoringSavedLocation;

  /// The project folder saved for this server, while no folder is open:
  /// the one a restore will open. Null once a folder is open, and for a
  /// saved home folder or AI Team folder, which are never restored.
  String? get savedProjectDirectory => _self._savedProjectDirectory;

  /// Opens the saved project folder when a connection came up without it
  /// (and nothing else is restoring it). The Work tab calls this instead of
  /// offering the folder chooser to someone who already has a project.
  Future<void> restoreSavedLocation() => _self._restoreSavedLocation();

  /// Clears the one-line location notice once the user has read it.
  void dismissLocationNotice() {
    if (locationNotice == null) return;
    locationNotice = null;
    notifyListeners();
  }

  /// Re-checks a directory restored while the project list was unavailable.
  /// A later catalog can confirm the selection, but cannot replace it.
  /// Missing or unavailable entries leave the explicit directory intact.
  Future<void> revalidateRestoredLocation() =>
      _self._revalidateRestoredLocation();

  /// Confirms a typed [directory] exists on the connected server without
  /// touching the active location. Returns a plain-sentence problem, or null
  /// when the folder can be opened. The listing runs on a throwaway
  /// transport so a wrong path never rescopes live requests.
  Future<String?> probeProjectFolder(String directory) =>
      _self._probeProjectFolder(directory);

  /// Forgets whatever OpenCode 1 cached for [directory]. Asked about a folder
  /// before it existed, OpenCode keeps that folder's instance as broken and
  /// fails there even after the folder is made, until the instance is
  /// disposed. Called right after the app creates a folder and before it
  /// opens it; nothing can be running in a folder that did not exist, so
  /// disposing loses nothing. Best effort: a failure here only means the
  /// open that follows reports its own error.
  Future<void> disposeFolderInstance(String directory) =>
      _self._disposeFolderInstance(directory);

  /// Projects used on this server, most recent first, the current one
  /// included.
  List<ProfileLocation> get recentLocations => _self._recentLocations;

  Future<void> forgetRecentLocation(String directory) =>
      _self._forgetRecentLocation(directory);

  Future<void> selectLocation({String? directory, String? workspace}) =>
      _self._selectLocation(directory: directory, workspace: workspace);

  /// Rescopes onto the folder of a conversation that already exists there,
  /// even when that folder is the server's home. Reading or continuing an
  /// earlier conversation is the one thing still allowed in a home folder;
  /// [workspaceChoiceRequired] stays true, so Workspace keeps asking for a
  /// project folder before any new session starts.
  Future<void> selectLocationForExistingSession({
    String? directory,
    String? workspace,
  }) => _self._selectLocationForExistingSession(
    directory: directory,
    workspace: workspace,
  );

  /// A discovered default can fill an empty connection only after restore.
  /// Retained screens may finish loading while connect is still validating
  /// the saved choice, or after an explicit selection has already won.
  Future<void> selectInitialLocation({String? directory, String? workspace}) =>
      _self._selectInitialLocation(directory: directory, workspace: workspace);
}

extension _ConnectionControllerLocationsImpl on ConnectionController {
  /// The body of [workspaceChoiceRequired].
  bool get _workspaceChoiceRequired {
    if (_connectedProfile?.usesAgentSocket ?? false) return false;
    if (!capabilities.projectManagement) return false;
    return isProtectedWorkspaceDirectory(directory);
  }

  /// The body of [savedProjectDirectory].
  String? get _savedProjectDirectory {
    if (directory != null || workspace != null) return null;
    final owner = profile;
    if (owner == null || owner.usesAgentSocket) return null;
    final saved = store.locationFor(owner.id)?.directory;
    if (saved == null ||
        isProtectedWorkspaceDirectory(saved) ||
        isAiTeamDirectory(saved)) {
      return null;
    }
    return saved;
  }

  /// The body of [restoreSavedLocation].
  Future<void> _restoreSavedLocation() async {
    if (_restoringSavedLocation) return;
    final saved = savedProjectDirectory;
    final owner = profile;
    if (saved == null || owner == null) return;
    await selectLocation(
      directory: saved,
      workspace: store.locationFor(owner.id)?.workspace,
    );
  }

  Future<void> _forgetSavedLocation(ServerProfile profile) async {
    try {
      await store.clearLocation(profile.id);
    } catch (_) {
      // The current connection can still recover to its server root.
    }
  }

  /// Resolves the location to restore for [profile]. The saved directory is
  /// kept even when the server catalog does not list it. The catalog tracks
  /// known projects, not every existing folder or worktree; absence is not
  /// proof of deletion and never authorizes switching to another project.
  Future<ProfileLocation?> _validatedSavedLocation(
    ServerProfile profile,
    ServerOperationsGateway currentRepository,
    int generation,
    ServerGateway currentApi,
  ) => PerfTrace.span(
    'connect.saved_location',
    () => _validatedSavedLocationUntraced(
      profile,
      currentRepository,
      generation,
      currentApi,
    ),
  );

  Future<ProfileLocation?> _validatedSavedLocationUntraced(
    ServerProfile profile,
    ServerOperationsGateway currentRepository,
    int generation,
    ServerGateway currentApi,
  ) async {
    final saved = store.locationFor(profile.id);
    if (saved == null) return null;
    final savedDirectory = saved.directory;
    var directory = savedDirectory == null
        ? null
        : ConnectionController.normalizeDirectoryPath(savedDirectory);
    if (directory != null && directory.isEmpty) directory = null;
    if (directory != null && isProtectedWorkspaceDirectory(directory)) {
      // A location saved by an older build that still allowed the server's
      // home folder. It is forgotten rather than restored, and Workspace asks
      // for a project folder instead.
      await _forgetSavedLocation(profile);
      locationNotice = ConnectionController.workspaceChoiceNotice;
      return null;
    }
    final workspace = saved.workspace;
    _pendingLocationRevalidation = false;
    try {
      if (directory != null) {
        currentRepository.setLocation(directory: directory, workspace: null);
        WorkspaceProject? project;
        try {
          project = await currentRepository.loadCurrentProject();
        } catch (_) {
          // Verified against the project list below.
        }
        if (!_isCurrent(generation, currentApi)) return null;
        final confirmed =
            project != null &&
            ConnectionController.projectContainsDirectory(project, directory);
        if (!confirmed) {
          currentRepository.setLocation(directory: null, workspace: null);
          List<WorkspaceProject>? projects;
          try {
            projects = await currentRepository.listProjects();
          } catch (_) {
            projects = null;
          }
          if (!_isCurrent(generation, currentApi)) return null;
          final confirmedByCatalog =
              projects?.any(
                (candidate) => ConnectionController.projectContainsDirectory(
                  candidate,
                  directory!,
                ),
              ) ??
              false;
          if (!confirmedByCatalog) {
            // The catalog only lists projects with history. A folder the
            // server can read is valid even with no sessions yet; only a
            // folder the server says is absent is dropped, quietly.
            switch (await _checkFolder(profile, directory)) {
              case _FolderCheck.exists:
                break;
              case _FolderCheck.missing:
                if (!_isCurrent(generation, currentApi)) return null;
                await _forgetSavedLocation(profile);
                return null;
              case _FolderCheck.unknown:
                if (!_isCurrent(generation, currentApi)) return null;
                _pendingLocationRevalidation = true;
            }
            if (!_isCurrent(generation, currentApi)) return null;
          }
        }
      }
      if (workspace != null) {
        currentRepository.setLocation(directory: directory, workspace: null);
        List<WorkspaceInfo>? workspaces;
        try {
          workspaces = await currentRepository.listWorkspaces();
        } catch (_) {
          workspaces = null;
        }
        if (!_isCurrent(generation, currentApi)) return null;
        if (workspaces == null ||
            !workspaces.any((candidate) => candidate.id == workspace)) {
          // Kept silently; revalidated when the workspace list answers.
          _pendingLocationRevalidation = true;
        }
      }
      return ProfileLocation(directory: directory, workspace: workspace);
    } catch (_) {
      if (!_isCurrent(generation, currentApi)) return null;
      _pendingLocationRevalidation = true;
      return ProfileLocation(directory: directory, workspace: workspace);
    } finally {
      currentRepository.setLocation(directory: null, workspace: null);
    }
  }

  /// The body of [revalidateRestoredLocation].
  Future<void> _revalidateRestoredLocation() async {
    if (!_pendingLocationRevalidation) return;
    final currentRepository = repository;
    final currentApi = api;
    final directory = this.directory;
    if (currentRepository == null || currentApi == null || directory == null) {
      _pendingLocationRevalidation = false;
      return;
    }
    final generation = _generation;
    List<WorkspaceProject>? projects;
    try {
      projects = await currentRepository.listProjects();
    } catch (_) {
      projects = null; // The folder check below can still settle it.
    }
    if (!_isCurrent(generation, currentApi) || this.directory != directory) {
      return;
    }
    final listed =
        projects?.any(
          (candidate) => ConnectionController.projectContainsDirectory(
            candidate,
            directory,
          ),
        ) ??
        false;
    final owner = _connectedProfile;
    if (!listed) {
      if (owner == null) return;
      final check = await _checkFolder(owner, directory);
      if (!_isCurrent(generation, currentApi) || this.directory != directory) {
        return;
      }
      if (check == _FolderCheck.unknown) return; // Try again later.
      if (check == _FolderCheck.missing) {
        // Gone from the server: forget the saved choice, no banner. The open
        // folder is left for the person to leave on their own.
        _pendingLocationRevalidation = false;
        await _forgetSavedLocation(owner);
        return;
      }
    }
    final workspace = this.workspace;
    if (workspace != null) {
      List<WorkspaceInfo> workspaces;
      try {
        workspaces = await currentRepository.listWorkspaces();
      } catch (_) {
        return;
      }
      if (!_isCurrent(generation, currentApi) ||
          this.directory != directory ||
          this.workspace != workspace ||
          !workspaces.any((candidate) => candidate.id == workspace)) {
        return;
      }
    }
    _pendingLocationRevalidation = false;
  }

  /// Reads [directory] on a throwaway transport so a wrong path never
  /// rescopes live requests. Only an explicit "not found" answer counts as
  /// [_FolderCheck.missing]; any other failure leaves the question open.
  Future<_FolderCheck> _checkFolder(
    ServerProfile profile,
    String directory,
  ) async {
    if (isIsolated) return _FolderCheck.unknown;
    ServerGateway? gateway;
    try {
      gateway = _buildTransportPair(profile).gateway
        ..setLocation(directory: directory, workspace: null);
      await gateway.listFiles('.').timeout(const Duration(seconds: 8));
      return _FolderCheck.exists;
    } on ApiException catch (error) {
      final message = error.message.toLowerCase();
      return error.statusCode == 404 ||
              message.contains('enoent') ||
              message.contains('no such file')
          ? _FolderCheck.missing
          : _FolderCheck.unknown;
    } catch (_) {
      return _FolderCheck.unknown;
    } finally {
      gateway?.close();
    }
  }

  /// The body of [probeProjectFolder].
  Future<String?> _probeProjectFolder(String directory) async {
    final profile = _connectedProfile;
    if (profile == null || isIsolated) return 'OpenCode is not connected.';
    final problem = workspaceDirectoryProblem(directory);
    if (problem != null) return problem;
    final pair = _buildTransportPair(profile);
    pair.gateway.setLocation(
      directory: ConnectionController.normalizeDirectoryPath(directory),
      workspace: null,
    );
    try {
      await pair.gateway.listFiles('.');
      return null;
    } catch (_) {
      return 'That folder was not found on the server. Check the path and '
          'try again.';
    } finally {
      pair.gateway.close();
    }
  }

  /// The body of [disposeFolderInstance].
  Future<void> _disposeFolderInstance(String directory) async {
    final profile = _connectedProfile;
    if (profile == null ||
        isIsolated ||
        profile.backend != ServerBackend.openCode ||
        profile.flavor == ServerFlavor.v2) {
      return;
    }
    // Through the one transport builder, like every other connection here.
    final pair = _buildTransportPair(profile);
    final gateway = pair.gateway
      ..setLocation(
        directory: ConnectionController.normalizeDirectoryPath(directory),
        workspace: null,
      );
    try {
      if (gateway is OpenCodeApi) await gateway.disposeInstance();
    } catch (_) {
      // See above: the open reports anything that is really wrong.
    } finally {
      gateway.close();
    }
  }

  /// The body of [recentLocations].
  List<ProfileLocation> get _recentLocations {
    final id = profile?.id;
    return id == null ? const [] : store.recentLocations(id);
  }

  /// The body of [forgetRecentLocation].
  Future<void> _forgetRecentLocation(String directory) async {
    final id = profile?.id;
    if (id == null) return;
    await store.forgetRecentLocation(id, directory);
    if (!_disposed) _notifyListeners();
  }

  /// The body of [selectLocationForExistingSession].
  Future<void> _selectLocationForExistingSession({
    String? directory,
    String? workspace,
  }) => _selectLocation(
    directory: directory,
    workspace: workspace,
    allowProtectedDirectory: true,
  );

  /// The body of [selectInitialLocation].
  Future<void> _selectInitialLocation({
    String? directory,
    String? workspace,
  }) async {
    final profile = _connectedProfile;
    if (_restoringSavedLocation ||
        this.directory != null ||
        this.workspace != null ||
        (profile != null && store.locationFor(profile.id) != null)) {
      return;
    }
    await _selectLocation(
      directory: directory,
      workspace: workspace,
      preserveNotice: true,
    );
  }

  Future<void> _rememberSelectedLocation(
    ServerProfile profile,
    String? directory,
    String? workspace,
    int generation,
    ServerGateway currentApi,
  ) async {
    if (isProtectedWorkspaceDirectory(directory)) return;
    final previous = _locationWrite;
    final write = () async {
      try {
        await previous;
      } catch (_) {}
      if (_deletingReadProfiles.contains(profile.id)) return;
      await store.setLocation(
        profile.id,
        directory: directory,
        workspace: workspace,
      );
    }();
    _locationWrite = write;
    try {
      await write;
    } catch (_) {
      if (_isCurrent(generation, currentApi)) {
        locationNotice =
            'This location is active, but it could not be remembered '
            'for the next launch.';
      }
    }
  }

  Future<void> _selectLocation({
    String? directory,
    String? workspace,
    bool preserveNotice = false,
    bool allowProtectedDirectory = false,
    bool preserveConnectionAttempt = false,
  }) => PerfTrace.span(
    'location.select',
    () => _selectLocationUntraced(
      directory: directory,
      workspace: workspace,
      preserveNotice: preserveNotice,
      allowProtectedDirectory: allowProtectedDirectory,
      preserveConnectionAttempt: preserveConnectionAttempt,
    ),
  );

  Future<void> _selectLocationUntraced({
    String? directory,
    String? workspace,
    bool preserveNotice = false,
    bool allowProtectedDirectory = false,
    bool preserveConnectionAttempt = false,
  }) async {
    final profile = _connectedProfile;
    if (profile == null ||
        api == null ||
        _deletingReadProfiles.contains(profile.id)) {
      return;
    }
    if (profile.usesAgentSocket) {
      directory ??= profile.codexDirectory;
      if (workspace != null ||
          validateCodexProjectDirectory(directory) != null) {
        locationError = 'Choose a valid project folder for this connection.';
        _notifyListeners();
        return;
      }
    } else if (directory != null &&
        !allowProtectedDirectory &&
        isProtectedWorkspaceDirectory(directory)) {
      // Never rescope onto a home folder or the filesystem root: the server
      // would watch and scan everything underneath it.
      locationError = workspaceDirectoryProblem(directory);
      _notifyListeners();
      return;
    }
    _restoringSavedLocation = false;
    if (!preserveNotice) {
      locationNotice = null;
      _pendingLocationRevalidation = false;
    }
    if (this.directory == directory && this.workspace == workspace) {
      if (isProtectedWorkspaceDirectory(directory)) {
        _notifyListeners();
        return;
      }
      final generation = _generation;
      final currentApi = api!;
      await _rememberSelectedLocation(
        profile,
        directory,
        workspace,
        generation,
        currentApi,
      );
      if (!_isCurrent(generation, currentApi) ||
          _deletingReadProfiles.contains(profile.id)) {
        return;
      }
      _notifyListeners();
      return;
    }

    final generation = _beginGeneration(
      preserveConnectionAttempt: preserveConnectionAttempt,
    );
    final previousVersion = version;
    _retireTransport();
    // Rebuild through the flavor-aware builder: a v2 profile must not be
    // rescoped onto a v1 transport, whose health check cannot succeed and
    // reads to the user as a rotated password. Guarded by
    // test/connection_transport_factory_guard_test.dart, because this fix
    // has already been lost to a merge once.
    final pair = _buildTransportPair(profile);
    final currentApi = pair.gateway
      ..setLocation(directory: directory, workspace: workspace);
    final currentRepository = pair.operations
      ..setLocation(directory: directory, workspace: workspace);
    api = currentApi;
    repository = currentRepository;
    this.directory = directory;
    this.workspace = workspace;
    version = previousVersion;
    locationRevision += 1;
    locationLoading = true;
    locationError = null;
    lastError = null;
    final savedLibrary = _modelLibrary;
    final savedSessionModels = sessionModels;
    // Same server, another folder: the models and agents are the server's,
    // so the ones already shown stay while this folder's copy loads. Waiting
    // for a fresh catalog here held the whole folder open behind OpenCode 1's
    // 6 MB provider list (7.7 s on a phone-hosted server).
    final savedProviders = providers;
    final savedAgents = agents;
    final savedCatalog = catalog;
    final savedCatalogDetailed = catalogDetailed;
    final savedUnloaded = unloadedProviderIDs;
    final savedUnusable = unloadedProvidersUnusable;
    _clearLocationData();
    _modelLibrary = savedLibrary;
    sessionModels = savedSessionModels;
    providers = savedProviders;
    agents = savedAgents;
    catalog = savedCatalog;
    catalogDetailed = savedCatalogDetailed;
    unloadedProviderIDs = savedUnloaded;
    unloadedProvidersUnusable = savedUnusable;
    status = StreamStatus.connecting;
    _notifyListeners();
    enablePollingFallback();
    _startEvents(generation, currentApi);
    // Remember the user's scope before waiting for catalog/session requests.
    // A slow or failed refresh must not restore the previous project later.
    await _rememberSelectedLocation(
      profile,
      directory,
      workspace,
      generation,
      currentApi,
    );
    if (!_isCurrent(generation, currentApi) ||
        _deletingReadProfiles.contains(profile.id)) {
      return;
    }
    _markDataRefreshReady(generation, currentApi);

    // The folder is open once its conversations and waiting requests are in.
    // The catalog follows rather than alongside: OpenCode 1 answers on one
    // thread, and asked together the small reads queued behind the catalog
    // (permissions took 6.2 s waiting for a 7.7 s catalog on the phone).
    await Future.wait<void>([
      refreshSessions(),
      _syncPendingPermissions(),
      _syncPendingQuestions(),
    ]);
    if (!_isCurrent(generation, currentApi)) return;
    locationLoading = false;
    _notifyListeners();
    // The one-time provider runtime refresh only matters for the model list,
    // so it runs after the folder is open and before the catalog (it held a
    // new project's open for 6.8 s on the phone).
    unawaited(
      _refreshPreexistingProviderRuntime(
        generation: generation,
        currentApi: currentApi,
        currentRepository: currentRepository,
        profile: profile,
      ).then((_) {
        if (_isCurrent(generation, currentApi)) return _ensureCatalog();
      }),
    );
    if (_pendingLocationRevalidation) unawaited(revalidateRestoredLocation());
  }
}
