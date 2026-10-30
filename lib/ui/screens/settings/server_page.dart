part of '../settings_screen.dart';

/// One saved server's own page (Settings Standard SC2): three sections that
/// say who a change affects before it is made.
///
/// - **Connection**: reaching the server (status, sign-in, name and address,
///   what it supports);
/// - **In this app**: choices kept on this phone about the server (the model
///   for new chats, notifications, watching, read state);
/// - **On the server**: things saved on the server itself, shared with every
///   other app that uses it, under the line that says so (SC6).
///
/// The page is pinned to the profile it opened for (SC11): switching server
/// in the app never redirects an edit. Rows that need a live connection are
/// offered only while the app is connected to that server; otherwise the page
/// says so and offers to connect.
/// The pinned official upgrade, run on the server's computer.
const _serverUpdateCommands = 'npm install -g @opencode/cli@2.1.1';

/// Last good health per profile id, kept for the process: Settings and its
/// server page paint it at once and refresh behind it.
final Map<String, Health> serverHealthCache = {};

/// A row's trailing mark: what tapping the row does (opens, restarts,
/// downloads).
class _RowMark extends StatelessWidget {
  const _RowMark(this.icon);

  final IconData icon;

  @override
  Widget build(BuildContext context) => Padding(
    padding: EdgeInsetsDirectional.only(end: KitTokens.of(context).space3),
    child: KitIcon(icon, size: KitIconSize.small, tone: KitTextTone.tertiary),
  );
}

class ServerPageScreen extends StatefulWidget {
  const ServerPageScreen({
    super.key,
    required this.controller,
    required this.profileId,
  });

  final ConnectionController controller;

  /// The saved server this page is about.
  final String profileId;

  @override
  State<ServerPageScreen> createState() => _ServerPageScreenState();
}

class _ServerPageScreenState extends State<ServerPageScreen> {
  bool _saveFailed = false;

  Health? _health;
  bool _blocking = false;
  String? _healthError;
  bool _checking = false;
  bool _healthStarted = false;
  bool _upgradingServer = false;

  /// Paseo: asking the daemon which agents are ready again.
  bool _recheckingAgents = false;
  String? _agentsLine;
  bool _agentsFailed = false;
  String? _serverUpgradeError;

  /// Paseo: updating the helper on the computer (server_host_update.dart).
  bool _hostUpdating = false;
  HostUpdatePhase? _hostPhase;
  String? _hostUpdateLine;
  String? _hostUpdateReason;
  bool _hostUpdateFailed = false;

  /// Extensions in the part files cannot call [setState] directly.
  void _setState(VoidCallback fn) => setState(fn);

  /// The update commands were copied from their row: its line says so.
  bool _updateCommandsCopied = false;

  /// The running version: the health probe's answer, else the connection's.
  String? get _runningVersion => _health?.version ?? widget.controller.version;

  /// Health is only asked of the server the app is connected to; it starts
  /// once, the first time this page's server is the live one.
  void _startHealthIfLive() {
    if (_healthStarted) return;
    final profile = _profile;
    if (profile == null || !_isConnectedServer(_controller, profile)) return;
    _healthStarted = true;
    _health = serverHealthCache[profile.id];
    _checkHealth();
  }

  /// What Use my default just put back, said once at the top of the page.
  String? _resetNote;

  ConnectionController get _controller => widget.controller;

  /// Edits go through the target-pinned facade: the profile id is captured
  /// here, so a server switch elsewhere can never redirect a save (SC11).
  late final ProfileSettingsAccess _access = ProfileSettingsAccess(
    _controller.store,
  );
  late final SettingsTarget _target = SettingsTarget.connection(
    widget.profileId,
  );

  @override
  void initState() {
    super.initState();
    _controller.addListener(_changed);
    _controller.profileMonitor.addListener(_changed);
    _controller.store.appPreferences.readStateConsent.addListener(_changed);
    _startHealthIfLive();
  }

  @override
  void dispose() {
    _controller.removeListener(_changed);
    _controller.profileMonitor.removeListener(_changed);
    _controller.store.appPreferences.readStateConsent.removeListener(_changed);
    _access.dispose();
    super.dispose();
  }

  /// Share read state for this server only: acknowledged before it shows on,
  /// and switching off takes effect at once.
  Future<void> _setShareRead(bool value) async {
    setState(() => _saveFailed = false);
    try {
      final token = await _access.beginEdit('shareReadState', _target);
      await _access.writeSetting(token, value);
    } catch (_) {
      if (mounted) setState(() => _saveFailed = true);
    }
  }

  void _changed() {
    if (!mounted) return;
    if (!_healthStarted) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) _startHealthIfLive();
      });
    }
    // The monitor can speak while another page is mid-build; redraw after.
    if (SchedulerBinding.instance.schedulerPhase ==
        SchedulerPhase.persistentCallbacks) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) setState(() {});
      });
      return;
    }
    setState(() {});
  }

  ServerProfile? get _profile => _controller.store.profiles
      .where((p) => p.id == widget.profileId)
      .firstOrNull;

  /// Runs the search index's door [id] for this connection, so the page and
  /// search open the same place.
  Future<void> _door(String id) async {
    final copy = _settingsCopy(context);
    final entry = allSearchEntries(
      copy,
    ).where((entry) => entry.id == id).firstOrNull;
    if (entry == null) return;
    await entry.open(context, SearchScope.of(context, _controller));
  }

  Future<void> _push(Widget screen) =>
      pushKitPage<void>(context, (_) => screen);

  Future<void> _edit() => Navigator.of(context).pushNamed<void>(
    '/servers',
    arguments: ServersRouteRequest.edit(widget.profileId),
  );

  Future<void> _connect() => Navigator.of(context).pushNamed<void>(
    '/servers',
    arguments: ServersRouteRequest.connect(widget.profileId),
  );

  Future<void> _disconnect() async {
    final confirmed = await confirmDisconnectServer(context, _controller);
    if (!confirmed || !mounted) return;
    final navigator = Navigator.of(context);
    await _controller.disconnect();
    navigator.pushNamedAndRemoveUntil('/servers', (_) => false);
  }

  /// What this page shows for [profile] that differs from the person's own
  /// choice: notifications turned off for this server, a new-chat model picked
  /// for it, read state shared with it. Read from the same stored values the
  /// facade reports as explicit (SC11).
  _ServerOverrides _overridesOf(ServerProfile profile) {
    final store = _controller.store;
    final rules = _controller.profileMonitor.rulesFor(profile.id);
    final consent = store.appPreferences.readStateConsent;
    return _ServerOverrides(
      notifications: rules.enabled && !rules.notifications,
      model:
          store.confirmedSelectionPreference('oc.model.${profile.id}') != null,
      readState: consent.enabled(profile.id) != consent.defaultFor(profile.id),
    );
  }

  /// Removes every difference this page shows for the server, not only the
  /// notification one. Each step is independent: a failure is said in place
  /// and what already went back stays back.
  Future<void> _useMyDefault(ServerProfile profile) async {
    setState(() => _saveFailed = false);
    var failed = false;
    final overrides = _overridesOf(profile);
    if (overrides.notifications) {
      try {
        final monitor = _controller.profileMonitor;
        await monitor.setRules(
          profile.id,
          monitor.rulesFor(profile.id).copyWith(notifications: true),
        );
      } catch (_) {
        failed = true;
      }
    }
    if (overrides.model) {
      try {
        await _controller.store.clearModel(profile.id);
      } catch (_) {
        failed = true;
      }
    }
    if (overrides.readState) {
      try {
        final token = await _access.beginEdit('shareReadState', _target);
        await _access.writeSetting(
          token,
          _controller.store.appPreferences.readStateConsent.defaultFor(
            profile.id,
          ),
        );
      } catch (_) {
        failed = true;
      }
    }
    if (!mounted) return;
    final copy = _settingsCopy(context);
    setState(() {
      _saveFailed = failed;
      _resetNote = failed
          ? null
          : useMyDefaultDoneNote(
              copy,
              serverDisplayName(
                profile,
                lookupAppLocalizations(Localizations.localeOf(context)),
                among: _controller.store.profiles,
              ),
              [
                if (overrides.notifications) copy.settingsV3Notifications,
                if (overrides.model) copy.settingsV3ModelNew,
                if (overrides.readState) copy.settingsV3ShareRead,
              ].join(', '),
            );
    });
  }

  Future<void> _recheckAgents() async {
    final gateway = widget.controller.api;
    if (_recheckingAgents || gateway is! HostAgentProviderGateway) return;
    final copy = _settingsCopy(context);
    setState(() {
      _recheckingAgents = true;
      _agentsFailed = false;
    });
    try {
      final catalog = await (gateway as HostAgentProviderGateway)
          .loadHostAgentProviders(refresh: true);
      final names = [for (final p in catalog.selectable) p.displayName];
      if (mounted) {
        setState(
          () => _agentsLine = names.isEmpty
              ? copy.paseoCheckNoneReady
              : copy.paseoCheckReady(names.join(', ')),
        );
      }
    } catch (_) {
      if (mounted) setState(() => _agentsFailed = true);
    } finally {
      if (mounted) setState(() => _recheckingAgents = false);
    }
  }

  Future<void> _checkHealth() async {
    // Runs from initState, so inherited lookups are not yet allowed.
    final copy = earlyAppLocalizations(context);
    if (_checking) return;
    // Cached means the page painted a last known answer at once (S1).
    final span = PerfTrace.begin(
      'settings.health',
      attrs: {'cached': _health != null},
    );
    setState(() {
      _checking = true;
      _blocking = _health == null;
      _healthError = null;
    });
    try {
      final api = await widget.controller.prepareActionTransport();
      if (api == null) {
        throw ProductException(copy.e7SettingsUi18);
      }
      final health = await api.health();
      serverHealthCache[widget.profileId] = health;
      if (mounted) setState(() => _health = health);
      span.finish();
    } catch (error) {
      span.finish(error: error);
      // A failed check outdates the cached answer: never show "Server
      // healthy" beside the reason it did not answer.
      serverHealthCache.remove(widget.profileId);
      if (mounted) {
        setState(() {
          _health = null;
          _healthError = productErrorText(error);
        });
      }
    } finally {
      if (mounted) {
        setState(() {
          _checking = false;
          _blocking = false;
        });
      }
    }
  }

  /// "Restart OpenCode on its host": the command to run there, and "I
  /// restarted it", which checks the server again (map: restart dialog
  /// "fix").
  Future<void> _showRemoteRestartSheet(String version) async {
    final copy = _settingsCopy(context);
    final restarted = await showKitSheet<bool>(
      context,
      title: copy.e7SettingsUi46,
      icon: AppIconography.restart,
      sheetKey: const ValueKey('server-restart-sheet'),
      body: (sheetContext) {
        return Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            KitText(
              copy.e7SettingsRestartBody(
                version,
                _runningVersion ?? copy.e7SettingsUi52,
              ),
            ),
          ],
        );
      },
      primary: KitAction(
        key: const ValueKey('server-restart-done'),
        label: copy.serverSettingsRestartedIt,
        icon: AppIconography.retry,
        onPressed: () => Navigator.of(context).pop(true),
      ),
    );
    if (restarted == true && mounted) await _checkHealth();
  }

  /// Installs [target] inside the confirmation: the question shows the
  /// install working, and a failure keeps it open with Try again (the row
  /// says why as well).
  Future<void> _install(String target, ServerProfile profile) async {
    final copy = _settingsCopy(context);
    setState(() {
      _upgradingServer = true;
      _serverUpgradeError = null;
    });
    try {
      final repository = await widget.controller.prepareActionRepository();
      if (repository == null) {
        throw ProductException(copy.e7SettingsUi19);
      }
      final installed = await repository.upgradeServer(target);
      if (widget.controller.profile?.id != profile.id) {
        throw ProductException(copy.e7SettingsUi49);
      }
      widget.controller.recordServerUpgradeInstalled(installed);
    } catch (error) {
      if (mounted) {
        setState(() => _serverUpgradeError = productErrorText(error));
      }
      rethrow;
    } finally {
      if (mounted) setState(() => _upgradingServer = false);
    }
  }

  Future<void> _upgradeRemoteServer(String target) async {
    final copy = _settingsCopy(context);
    if (_upgradingServer || !isExactServerVersion(target)) return;
    final profile = widget.controller.profile;
    if (profile == null) return;
    final current = _runningVersion ?? copy.e7SettingsUi53;
    await showKitConfirm(
      context,
      title: copy.e7SettingsUi48,
      body: copy.serverSettingsUpgradeBody(target, profile.name, current),
      confirmLabel: copy.e7SettingsInstallVersion(target),
      icon: AppIconography.download,
      consequenceItems: [
        KitConsequence(copy.serverSettingsUpgradeKeepsRunning(current)),
        KitConsequence(copy.serverSettingsUpgradeRestartAfter(target)),
        KitConsequence(
          copy.serverSettingsUpgradeKeepsData,
          mark: KitConsequenceMark.kept,
        ),
      ],
      confirmKey: const Key('confirm-server-upgrade'),
      action: () => _install(target, profile),
    );
  }

  String _signInValue(AppLocalizations copy, ServerProfile profile) {
    if (profile.usesAgentSocket) {
      return profile.codexToken.isNotEmpty
          ? (profile.backend == ServerBackend.codex
                ? copy.settingsV3SignInToken
                : copy.settingsV3SignInPassword)
          : copy.settingsV3SignInNone;
    }
    return profile.password.isNotEmpty
        ? copy.settingsV3SignInPassword
        : copy.settingsV3SignInNone;
  }

  @override
  Widget build(BuildContext context) {
    final copy = _settingsCopy(context);
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final tokens = KitTokens.of(context);
    final profile = _profile;
    if (profile == null) {
      // The server was removed while its page was open.
      return KitScreen(
        topBar: KitTopBar(title: copy.settingsV3Servers),
        width: KitScreenWidth.reading,
        body: KitStateView(
          key: const ValueKey('server-page-gone'),
          icon: AppIconography.server,
          title: copy.settingsV3ServerGone,
        ),
      );
    }
    final controller = _controller;
    final name = serverDisplayName(
      profile,
      l10n,
      among: controller.store.profiles,
    );
    final live = _isConnectedServer(controller, profile);
    // The app's own server is named "On this phone"; an action on it says
    // "this phone's server", never "Remove On this phone". It updates with
    // the app, so it has no update row.
    final ownServer = looksLikeInAppServer(profile);
    final actionName = ownServer ? copy.serverPageThisPhonesServer : name;
    final monitor = controller.profileMonitor;
    final rules = monitor.rulesFor(profile.id);
    final monitorSupported = monitor.supportsProfile(profile);
    final different = rules.enabled && !rules.notifications;
    final (modelProvider, modelId) = controller.store.modelFor(profile.id);
    // The same words the hub shows, connected or not.
    final modelValue = modelProvider == null || modelId == null
        ? copy.settingsV3ServerPick
        : serverModelLabel(controller, profile, modelProvider, modelId);
    final version =
        serverHealthCache[profile.id]?.version ??
        (live ? controller.version : null) ??
        profile.serverVersion;

    Widget arrival(String id, Widget child) => KitArrival(id: id, child: child);

    // Update row: what applies to this server's kind and state.
    final managedLocally =
        platformCapabilities.supportsTermux &&
        TermuxBridge.managesServerUrl(profile.baseUrl);
    final availableVersion = managedLocally
        ? null
        : controller.availableServerVersion;
    final installedVersion = managedLocally
        ? null
        : controller.installedServerVersion;
    late final String serverUpdateTitle;
    late final String serverUpdateSubtitle;
    Widget? serverUpdateTrailing;
    VoidCallback? serverUpdateAction;
    if (managedLocally) {
      serverUpdateTitle = copy.e7SettingsUi50;
      serverUpdateSubtitle = copy.e7SettingsUi51;
      serverUpdateTrailing = const _RowMark(AppIconography.chevronRight);
      serverUpdateAction = () =>
          openThisPhone(context, kind: PhoneHostKind.termux);
    } else if (installedVersion != null) {
      serverUpdateTitle = copy.e7SettingsRestartVersion(installedVersion);
      serverUpdateSubtitle = _serverUpgradeError != null
          ? copy.e7SettingsRetryError(_serverUpgradeError!)
          : copy.e7SettingsInstalledVersion(installedVersion);
      serverUpdateTrailing = const _RowMark(AppIconography.restart);
      serverUpdateAction = () => _showRemoteRestartSheet(installedVersion);
    } else if (availableVersion != null) {
      serverUpdateTitle = copy.e7SettingsUpdateVersion(availableVersion);
      // The running version is said once, on the health row (R3).
      serverUpdateSubtitle = _serverUpgradeError != null
          ? copy.e7SettingsRetryError(_serverUpgradeError!)
          : copy.serverSettingsUpdateHint;
      serverUpdateAction = () => _upgradeRemoteServer(availableVersion);
    } else {
      // The row is the copy (its title says so): tapping it copies, and its
      // line then says it was copied, never a snackbar (K2 §4.8).
      serverUpdateTitle = copy.serverSettingsCopyUpdateCommands(name);
      serverUpdateSubtitle = _updateCommandsCopied
          ? copy.serverSettingsUpdateCommandsCopied
          : copy.serverSettingsUpdateCommandsDetail;
      serverUpdateAction = () async {
        await KitCopy.copy(context, _serverUpdateCommands);
        if (mounted) setState(() => _updateCommandsCopied = true);
      };
    }
    final healthy = _health?.healthy == true;
    final checkAgain = KitIconButton(
      key: const ValueKey('server-health-check'),
      icon: AppIconography.retry,
      tooltip: copy.e7SettingsUi57,
      // The bar and the "Checking…" row are the reason it rests while a
      // check runs.
      onPressed: _checking ? null : _checkHealth,
      disabledReason: _checking ? copy.e7SettingsUi11 : null,
    );
    final running = _runningVersion;

    final connection = KitRowGroup(
      key: const ValueKey('server-section-connection'),
      label: copy.settingsV3SectionConnection,
      children: [
        if (!live)
          arrival(
            'server-row-status',
            KitRow(
              key: const ValueKey('server-row-status'),
              title: copy.settingsV3StatusVersion,
              // Under the title, never at the row's end: "Not connected ·
              // last seen 1.0.193" is longer than the half row a value gets
              // and would be cut.
              supporting: TextSpan(
                // Not connected: a bare version would read as live.
                text: version == null
                    ? copy.settingsV3NotConnected
                    : copy.settingsV3NotConnectedVersion(
                        _plainServerVersion(version),
                      ),
              ),
              supportingMaxLines: 3,
            ),
          )
        // Neutral until the first probe answers: a red "unavailable" row
        // that flashes for the half-second before the result lands reads as
        // a real outage.
        else if (_health == null && _healthError == null)
          arrival(
            'server-row-status',
            KitRow(
              key: const ValueKey('server-health-checking'),
              title: copy.e7SettingsUi11,
              supporting: TextSpan(text: copy.e7SettingsUi58),
              trailing: checkAgain,
            ),
          )
        else
          arrival(
            'server-row-status',
            KitRow(
              key: const ValueKey('server-health-result'),
              title: healthy ? copy.e7SettingsUi59 : copy.e7SettingsUi60,
              supporting: TextSpan(
                text:
                    _healthError ??
                    copy.e7SettingsVersion(running ?? copy.e7SettingsUi17),
              ),
              supportingMaxLines: 3,
              trailing: checkAgain,
            ),
          ),
        KitRow(
          key: const ValueKey('server-row-sign-in'),
          title: copy.settingsV3SignIn,
          trailing: KitRowValue(_signInValue(copy, profile)),
          onTap: _edit,
        ),
        KitRow(
          key: const ValueKey('server-row-address'),
          title: copy.settingsV3NameAddress,
          trailing: const KitChevron(),
          onTap: _edit,
        ),
        if (live)
          KitRow(
            key: const ValueKey('server-row-supports'),
            title: copy.settingsV3Supports,
            trailing: const KitChevron(),
            onTap: () =>
                _push(ServerCapabilitiesScreen(controller: controller)),
          ),
        if (live &&
            profile.backend == ServerBackend.paseo &&
            controller.api is HostAgentProviderGateway)
          KitRow(
            key: const Key('server-recheck-agents'),
            title: _recheckingAgents
                ? copy.serverSettingsRecheckChecking
                : copy.serverSettingsRecheckAgents,
            supporting: TextSpan(
              text: _agentsFailed
                  ? copy.serverSettingsRecheckFailed
                  : _agentsLine ?? copy.serverSettingsRecheckAgentsDetail,
            ),
            supportingMaxLines: 3,
            enabled: !_recheckingAgents,
            onTap: _recheckAgents,
          ),
      ],
    );

    final inApp = KitRowGroup(
      key: const ValueKey('server-section-in-app'),
      label: copy.settingsV3SectionInApp,
      children: [
        arrival(
          'server-row-model',
          KitRow(
            key: const ValueKey('server-row-model'),
            title: copy.settingsV3ModelNew,
            // The label wins the line: a long model name goes under it.
            supporting: TextSpan(text: modelValue),
            supportingMaxLines: 2,
            trailing: live ? const KitChevron() : null,
            onTap: live
                ? () => unawaited(
                    showModelPicker(
                      context,
                      title: newConversationsTitle(copy, name),
                    ),
                  )
                : null,
          ),
        ),
        KitRow(
          key: const ValueKey('server-row-notifications'),
          title: copy.settingsV3Notifications,
          trailing: KitRowValue(
            different
                ? copy.settingsV3DifferentHere
                : rules.enabled
                ? copy.settingsV3On
                : copy.settingsV3Off,
          ),
          onTap: () => _push(
            NotificationsSettingsScreen(
              controller: controller,
              page: NotificationsPage.notifications,
              onlyProfileId: profile.id,
            ),
          ),
        ),
        KitSwitchRow(
          key: const ValueKey('server-row-watch'),
          title: copy.settingsV3Watch,
          value: rules.enabled,
          disabledReason: monitorSupported
              ? null
              : copy.e7ProjectMonitorUnsupported,
          onChanged: monitorSupported
              ? (value) => unawaited(
                  monitor.setRules(
                    profile.id,
                    monitor.rulesFor(profile.id).copyWith(enabled: value),
                  ),
                )
              : null,
        ),
        // Local consent for this server, kept on this phone. It does not
        // say the server supports read receipts; sending also needs that.
        KitSwitchRow(
          key: const ValueKey('server-row-share-read'),
          title: copy.settingsV3ShareRead,
          value: controller.store.appPreferences.readStateConsent.enabled(
            profile.id,
          ),
          disabledReason:
              controller.store.appPreferences.readStateConsent.saving(
                profile.id,
              )
              ? copy.privacySaving
              : null,
          onChanged:
              controller.store.appPreferences.readStateConsent.saving(
                profile.id,
              )
              ? null
              : _setShareRead,
        ),
      ],
    );

    final capabilities = controller.capabilities;
    final onServer = live
        ? [
            // The note belongs to this section: it is the section's hint for
            // a screen reader, and its words stay visible under the rows
            // without being read again as loose page content.
            Semantics(
              key: const ValueKey('server-section-on-semantics'),
              container: true,
              explicitChildNodes: true,
              hint: savedOnNote(copy, name),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  KitRowGroup(
                    key: const ValueKey('server-section-on'),
                    label: sectionOnTitle(copy, name),
                    children: [
                      if (capabilities.serverCatalog ||
                          capabilities.agentAccount)
                        arrival(
                          'server-row-providers',
                          KitRow(
                            key: const ValueKey('server-row-providers'),
                            title: copy.settingsV3Providers,
                            trailing: const KitChevron(),
                            onTap: () => _door('settings-providers'),
                          ),
                        ),
                      arrival(
                        'server-row-tools',
                        KitRow(
                          key: const ValueKey('server-row-tools'),
                          title: copy.settingsHubToolsRow,
                          trailing: const KitChevron(),
                          onTap: () => _door('settings-tools'),
                        ),
                      ),
                      if (capabilities.shellSettings)
                        arrival(
                          'server-row-shell',
                          DefaultShellRow(controller: controller, icon: false),
                        ),
                      KitRow(
                        key: const ValueKey('server-row-permissions'),
                        title: copy.settingsV3Permissions,
                        trailing: const KitChevron(),
                        onTap: () => _door('settings-automation'),
                      ),
                      if (capabilities.setupConfigRead)
                        KitRow(
                          key: const ValueKey('ai-setup-entry'),
                          title: copy.aiSetupTitle,
                          trailing: const KitChevron(),
                          onTap: () => _push(
                            AiSetupScreen(
                              controller: controller,
                              serverName: name,
                            ),
                          ),
                        ),
                      // The app's own server has no Linux service to set
                      // up; Claude Code, Pi and Codex servers have no such
                      // page either.
                      if (!managedLocally &&
                          !looksLikeInAppServer(profile) &&
                          profile.backend == ServerBackend.openCode)
                        KitRow(
                          key: const Key('host-management-entry'),
                          title: copy.e7SettingsUi65,
                          supporting: TextSpan(text: copy.e7SettingsUi66),
                          supportingMaxLines: 2,
                          trailing: const KitChevron(),
                          onTap: () => _push(
                            HostManagementScreen(controller: controller),
                          ),
                        ),
                      ..._hostUpdateRows(context, copy, name),
                      // §7 row 23. A Termux-managed server is upgraded by this
                      // device, so that path is never gated; only the
                      // remote-host one is. Paseo updates its own helper from
                      // the row above.
                      if (_hostUpdater == null && !ownServer)
                        if (!managedLocally && !capabilities.remoteUpgrade)
                          KitRow.unavailable(
                            key: const ValueKey('gated-remote-upgrade'),
                            title: copy.e7SettingsUi67,
                            reason: copy.e7SettingsUi68,
                            capability: 'remote-upgrade',
                          )
                        else
                          KitRow(
                            key: const Key('server-updates-tile'),
                            title: serverUpdateTitle,
                            titleMaxLines: 2,
                            supporting: TextSpan(text: serverUpdateSubtitle),
                            supportingMaxLines: 3,
                            trailing: serverUpdateTrailing,
                            // Rests while its own update runs; the bar says so.
                            enabled: !_upgradingServer,
                            onTap: serverUpdateAction,
                          ),
                    ],
                  ),
                  ExcludeSemantics(
                    child: KitGroupNote(
                      key: const ValueKey('server-saved-on-note'),
                      message: savedOnNote(copy, name),
                    ),
                  ),
                ],
              ),
            ),
          ]
        : [
            // Not the connected server: what lives on it cannot be read or
            // changed from here, and nothing is guessed (SC11).
            KitRowGroup(
              key: const ValueKey('server-section-on'),
              label: sectionOnTitle(copy, name),
              children: [
                KitRow(
                  key: const ValueKey('server-row-connect'),
                  title: copy.settingsV3ConnectTo(actionName),
                  supporting: TextSpan(text: copy.settingsV3ConnectToChange),
                  supportingMaxLines: 3,
                  trailing: const KitChevron(),
                  onTap: _connect,
                ),
              ],
            ),
          ];

    final resetsDefault = _overridesOf(profile).any;

    return KitScreen(
      topBar: KitTopBar(title: name),
      width: KitScreenWidth.reading,
      // The health check and an update in flight are the screen's one
      // loading bar (design standard §4); the rows say what is happening.
      loading: _blocking || _upgradingServer || _hostUpdating,
      loadingLabel: _hostUpdating
          ? _phaseWords(copy, _hostPhase)
          : _upgradingServer
          ? serverUpdateTitle
          : copy.e7SettingsUi11,
      // Not a lazy list: every row must exist for a search result to arrive
      // at it, and the page is a few dozen plain rows in one Column.
      body: ListView(
        key: const ValueKey('server-page-list'),
        padding: EdgeInsetsDirectional.only(
          top: tokens.space2,
          bottom: KitScreen.endPadding(context),
        ),
        children: [
          Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              if (_saveFailed)
                Padding(
                  padding: EdgeInsetsDirectional.only(
                    start: tokens.gutter,
                    end: tokens.gutter,
                    bottom: tokens.sectionGap,
                  ),
                  child: KitNotice(
                    key: const ValueKey('server-page-save-failed'),
                    tone: AppStatusTone.failure,
                    message: copy.shareSessionViewsSaveError,
                    onDismiss: () => setState(() => _saveFailed = false),
                    dismissLabel: copy.notifyDismiss,
                  ),
                ),
              if (_resetNote case final note?)
                Padding(
                  padding: EdgeInsetsDirectional.only(
                    start: tokens.gutter,
                    end: tokens.gutter,
                    bottom: tokens.sectionGap,
                  ),
                  child: KitNotice(
                    key: const ValueKey('server-page-reset-done'),
                    tone: AppStatusTone.ok,
                    message: note,
                    onDismiss: () => setState(() => _resetNote = null),
                    dismissLabel: copy.notifyDismiss,
                  ),
                ),
              connection,
              SizedBox(height: tokens.sectionGap),
              inApp,
              SizedBox(height: tokens.sectionGap),
              ...onServer,
              // The server's own plugins: only where it reports an inventory.
              if (live && capabilities.pluginInventory) ...[
                SizedBox(height: tokens.sectionGap),
                KeyedSubtree(
                  key: const ValueKey('server-plugins'),
                  child: KitArrival(
                    id: 'settings-server-plugins',
                    child: ServerPluginsSection(controller: controller),
                  ),
                ),
              ],
              SizedBox(height: tokens.sectionGap),
              Padding(
                padding: EdgeInsets.symmetric(horizontal: tokens.gutter),
                child: KitDetailsFold(
                  key: const ValueKey('server-page-details'),
                  values: [
                    KitTechnicalValue(
                      copy.serverSettingsAddressLabel,
                      profile.baseUrl,
                    ),
                  ],
                ),
              ),
              // Every page with a difference ends by offering the way back
              // (Discord's Reset Override): it removes the difference, it does
              // not write a value.
              if (resetsDefault) ...[
                SizedBox(height: tokens.sectionGap),
                KitRowGroup(
                  key: const ValueKey('server-section-reset'),
                  children: [
                    KitRow(
                      key: const ValueKey('server-use-my-default'),
                      title: copy.settingsV3UseMyDefault,
                      supporting: TextSpan(
                        text: copy.settingsV3UseMyDefaultDetail(actionName),
                      ),
                      supportingMaxLines: 3,
                      trailing: const KitChevron(),
                      onTap: () => unawaited(_useMyDefault(profile)),
                    ),
                  ],
                ),
              ],
              SizedBox(height: tokens.sectionGap),
              KitRowGroup(
                key: const ValueKey('server-section-actions'),
                children: [
                  if (live)
                    KitRow(
                      key: const ValueKey('server-reconnect'),
                      title: copy.settingsV3ReconnectTo(actionName),
                      titleMaxLines: 2,
                      onTap: _connect,
                    ),
                  if (live)
                    arrival(
                      'server-disconnect',
                      KitRow(
                        key: const ValueKey('server-disconnect'),
                        destructive: true,
                        title: copy.serverSettingsDisconnectTitle(actionName),
                        titleMaxLines: 2,
                        onTap: _disconnect,
                      ),
                    ),
                  KitRow(
                    key: const ValueKey('server-remove'),
                    destructive: true,
                    title: copy.serverSettingsRemoveTitle(actionName),
                    titleMaxLines: 2,
                    onTap: () => Navigator.of(context).pushNamed<void>(
                      '/servers',
                      arguments: ServersRouteRequest.forget(profile.id),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ],
      ),
    );
  }
}

/// The differences a server's page can remove with Use my default.
class _ServerOverrides {
  const _ServerOverrides({
    required this.notifications,
    required this.model,
    required this.readState,
  });

  final bool notifications;
  final bool model;
  final bool readState;

  bool get any => notifications || model || readState;
}
