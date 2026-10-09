part of '../settings_screen.dart';

/// "This server": connection identity, health, host management, and the
/// server update flow. Saved servers are a sibling row in the hub, not a row
/// here, so each has one home.
///
/// Versions are the health probe's first ("Health-reported version
/// everywhere"): the connection's own reading only fills in until it
/// answers. The address and the other technical values sit in the one
/// Details fold, and Disconnect from this server is the last row, one
/// section gap below: the action lives on the page of the thing it acts on.
class ServerSettingsScreen extends StatefulWidget {
  final ConnectionController controller;

  /// A section to scroll to once the page is shown: search opens the page
  /// at the row it names ([disconnectSection]).
  final String? initialSection;

  const ServerSettingsScreen({
    super.key,
    required this.controller,
    this.initialSection,
  });

  /// [initialSection] for the Disconnect row.
  static const disconnectSection = 'disconnect';

  @override
  State<ServerSettingsScreen> createState() => _ServerSettingsScreenState();
}

/// The helper script's restart, as Run as a Linux service installs it: only
/// right for a server set up with that script, so the sheet folds it away.
const _serverRestartCommand = 'bash ubuntu-opencode.sh restart';

/// The official upgrade and model refresh, run on the server's computer.
const _serverUpdateCommands = 'opencode upgrade\nopencode models --refresh';

/// Last good health per profile id, kept for the process: Settings and its
/// server page paint it at once and refresh behind it.
final Map<String, Health> serverHealthCache = {};

class _ServerSettingsScreenState extends State<ServerSettingsScreen> {
  Health? _health;
  bool _blocking = false;
  String? _healthError;
  bool _checking = false;
  bool _upgradingServer = false;
  String? _serverUpgradeError;

  /// The update commands were copied from their row: its line says so.
  bool _updateCommandsCopied = false;
  final _disconnectKey = GlobalKey();

  @override
  void initState() {
    super.initState();
    if (widget.initialSection == ServerSettingsScreen.disconnectSection) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        final target = _disconnectKey.currentContext;
        if (mounted && target != null) Scrollable.ensureVisible(target);
      });
    }
    _health = serverHealthCache[widget.controller.profile?.id];
    widget.controller.addListener(_connectionChanged);
    _checkHealth();
  }

  /// Confirms, then disconnects and returns to the server list. The same
  /// flow the Settings hub's Disconnect button ran before it moved here.
  Future<void> _disconnect() async {
    final controller = widget.controller;
    final confirmed = await confirmDisconnectServer(context, controller);
    if (!confirmed || !mounted) return;
    final navigator = Navigator.of(context);
    await controller.disconnect();
    navigator.pushNamedAndRemoveUntil('/servers', (_) => false);
  }

  void _connectionChanged() {
    if (mounted) setState(() {});
  }

  /// The running version: the health probe's answer, else the connection's.
  String? get _runningVersion => _health?.version ?? widget.controller.version;

  Future<void> _checkHealth() async {
    // Runs from initState, so inherited lookups are not yet allowed.
    final copy = earlyAppLocalizations(context);
    if (_checking) return;
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
      final id = widget.controller.profile?.id;
      if (id != null) serverHealthCache[id] = health;
      if (mounted) setState(() => _health = health);
    } catch (error) {
      // A failed check outdates the cached answer: never show "Server
      // healthy" beside the reason it did not answer.
      serverHealthCache.remove(widget.controller.profile?.id);
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
        final tokens = KitTokens.of(sheetContext);
        return Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            KitText(
              copy.e7SettingsRestartBody(
                version,
                _runningVersion ?? copy.e7SettingsUi52,
              ),
            ),
            SizedBox(height: tokens.space2),
            // The app cannot tell how the server was started, so the helper
            // script's command is a folded aside for servers set up with it,
            // never the instruction for every server.
            KitDetailsFold(
              foldKey: const ValueKey('server-restart-script'),
              label: copy.serverSettingsRestartCommandLabel,
              child: KitCodeBlock(
                text: _serverRestartCommand,
                kind: KitCodeKind.command,
                copyLabel: copy.handoffCopyCommand,
                copyKey: const ValueKey('server-restart-copy'),
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

  @override
  Widget build(BuildContext context) {
    final controller = widget.controller;
    final profile = controller.profile;
    final copy = _settingsCopy(context);
    final tokens = KitTokens.of(context);
    // Termux management only exists on Android; desktop loopback servers
    // follow the ordinary remote-update path.
    final managedLocally =
        platformCapabilities.supportsTermux &&
        TermuxBridge.managesServerUrl(profile?.baseUrl);
    final availableVersion = managedLocally
        ? null
        : controller.availableServerVersion;
    final installedVersion = managedLocally
        ? null
        : controller.installedServerVersion;
    final running = _runningVersion;
    // The page is titled with the server it is about (R3): no identity row
    // repeats the name under a generic "Server".
    final serverName = profile == null
        ? copy.e7SettingsUi1
        : serverDisplayName(
            profile,
            lookupAppLocalizations(Localizations.localeOf(context)),
            among: controller.store.profiles,
          );
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
      serverUpdateTitle = copy.serverSettingsCopyUpdateCommands(serverName);
      serverUpdateSubtitle = _updateCommandsCopied
          ? copy.serverSettingsUpdateCommandsCopied
          : copy.serverSettingsUpdateCommandsDetail;
      serverUpdateAction = () async {
        await KitCopy.copy(context, _serverUpdateCommands);
        if (mounted) setState(() => _updateCommandsCopied = true);
      };
    }
    final healthy = _health?.healthy == true;
    final hasPassword = profile?.password.isNotEmpty == true;
    final baseUrl = profile?.baseUrl;
    final checkAgain = KitIconButton(
      key: const ValueKey('server-health-check'),
      icon: AppIconography.retry,
      tooltip: copy.e7SettingsUi57,
      // The bar and the "Checking…" row are the reason it rests while a
      // check runs.
      onPressed: _checking ? null : _checkHealth,
      disabledReason: _checking ? copy.e7SettingsUi11 : null,
    );
    return KitScreen(
      topBar: KitTopBar(title: serverName),
      width: KitScreenWidth.reading,
      // The health check and an update in flight are the screen's one
      // loading bar (design standard §4); the rows say what is happening.
      loading: _blocking || _upgradingServer,
      loadingLabel: _upgradingServer ? serverUpdateTitle : copy.e7SettingsUi11,
      body: ListView(
        padding: EdgeInsets.only(
          top: tokens.space3,
          bottom: KitScreen.endPadding(context),
        ),
        children: [
          KitRowGroup(
            children: [
              if (profile == null)
                KitRow(
                  key: const Key('server-identity'),
                  leading: KitRow.icon(context, AppIconography.server),
                  title: copy.e7SettingsUi9,
                  supporting: TextSpan(text: copy.e7SettingsUi56),
                ),
              // Neutral until the first probe answers: a red "unavailable"
              // row that flashes for the half-second before the result lands
              // reads as a real outage.
              if (_health == null && _healthError == null)
                KitRow(
                  key: const Key('server-health-checking'),
                  leading: KitRow.icon(context, AppIconography.activity),
                  title: copy.e7SettingsUi11,
                  supporting: TextSpan(text: copy.e7SettingsUi58),
                  trailing: checkAgain,
                )
              else
                KitRow(
                  key: const Key('server-health-result'),
                  leading: KitRow.icon(
                    context,
                    healthy ? AppIconography.checkCircle : AppIconography.error,
                    color: healthy ? tokens.roles.success : null,
                  ),
                  title: healthy ? copy.e7SettingsUi59 : copy.e7SettingsUi60,
                  supporting: TextSpan(
                    text:
                        _healthError ??
                        copy.e7SettingsVersion(running ?? copy.e7SettingsUi17),
                  ),
                  supportingMaxLines: 3,
                  trailing: checkAgain,
                ),
              // This server's sign-in is changed on this server's page: the
              // row opens its own editor, not the whole list (R2).
              if (profile != null)
                KitRow(
                  key: const Key('server-authentication'),
                  leading: KitRow.icon(context, AppIconography.person),
                  title: copy.serverSettingsChangeSignIn(serverName),
                  titleMaxLines: 2,
                  supporting: TextSpan(
                    text: profile.usesAgentSocket
                        // Claude Code, Pi and Codex keep a password or a
                        // token, not a Basic sign-in.
                        ? profile.codexToken.isNotEmpty
                              ? (profile.backend == ServerBackend.codex
                                    ? copy.serverSettingsAuthTokenSaved
                                    : copy.serverSettingsAuthPasswordSaved)
                              : (profile.backend == ServerBackend.codex
                                    ? copy.serverSettingsAuthTokenMissing
                                    : copy.e7SettingsUi62)
                        : hasPassword
                        ? copy.serverSettingsAuthBasic(
                            profile.username.isNotEmpty
                                ? profile.username
                                : 'opencode',
                          )
                        : copy.e7SettingsUi62,
                  ),
                  supportingMaxLines: 2,
                  trailing: const _RowMark(AppIconography.chevronRight),
                  onTap: () => Navigator.of(
                    context,
                  ).pushNamed('/servers', arguments: 'edit-active'),
                ),
            ],
          ),
          SizedBox(height: tokens.sectionGap),
          KitRowGroup(
            children: [
              // AI setup, review only (owner decision 2026-09-28): the
              // server's settings, tool servers and suggestions. Only a
              // server that can share its configuration gets the row; the
              // page itself explains any later loss of that ability.
              if (profile != null && controller.capabilities.setupConfigRead)
                KitRow(
                  key: const Key('ai-setup-entry'),
                  leading: KitRow.icon(context, AppIconography.sparkle),
                  title: copy.aiSetupTitle,
                  supporting: TextSpan(text: copy.aiSetupEntryDetail),
                  supportingMaxLines: 2,
                  trailing: const _RowMark(AppIconography.chevronRight),
                  onTap: () => pushKitPage<void>(
                    context,
                    (_) => AiSetupScreen(
                      controller: controller,
                      serverName: serverName,
                    ),
                  ),
                ),
              // The app's own server has no Linux service to set up.
              // (The helper script installs OpenCode: Claude Code, Pi and Codex
              // servers have no such page.)
              if (!managedLocally &&
                  !looksLikeInAppServer(profile) &&
                  (profile == null ||
                      profile.backend == ServerBackend.openCode))
                KitRow(
                  key: const Key('host-management-entry'),
                  leading: KitRow.icon(context, AppIconography.terminal),
                  title: copy.e7SettingsUi65,
                  supporting: TextSpan(text: copy.e7SettingsUi66),
                  supportingMaxLines: 2,
                  trailing: const _RowMark(AppIconography.chevronRight),
                  onTap: () => pushKitPage<void>(
                    context,
                    (_) => HostManagementScreen(controller: controller),
                  ),
                ),
              // §7 row 23. A Termux-managed server is upgraded by this
              // device, so that path is never gated — only the remote-host
              // one is. whenMissing: explains (STATE-12).
              if (!managedLocally && !controller.capabilities.remoteUpgrade)
                KitRow.unavailable(
                  key: const ValueKey('gated-remote-upgrade'),
                  title: copy.e7SettingsUi67,
                  reason: copy.e7SettingsUi68,
                  capability: 'remote-upgrade',
                  leading: KitRow.icon(context, AppIconography.systemDownload),
                )
              else
                KitRow(
                  key: const Key('server-updates-tile'),
                  leading: KitRow.icon(context, AppIconography.systemDownload),
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
          if (baseUrl != null) ...[
            SizedBox(height: tokens.sectionGap),
            Padding(
              padding: EdgeInsets.symmetric(horizontal: tokens.gutter),
              child: KitDetailsFold(
                values: [
                  KitTechnicalValue(copy.serverSettingsAddressLabel, baseUrl),
                ],
              ),
            ),
          ],
          // The server's own plugins: only where it reports an inventory.
          if (profile != null &&
              widget.controller.capabilities.pluginInventory) ...[
            SizedBox(height: tokens.sectionGap),
            KeyedSubtree(
              key: const ValueKey('server-plugins'),
              child: KitArrival(
                id: 'settings-server-plugins',
                child: ServerPluginsSection(controller: widget.controller),
              ),
            ),
          ],
          // Destructive and last, one section gap below the rest; it names
          // the server and says what stops and what stays where.
          if (profile != null) ...[
            SizedBox(height: tokens.sectionGap),
            KitRowGroup(
              key: _disconnectKey,
              children: [
                KitRow(
                  key: const ValueKey('server-disconnect'),
                  destructive: true,
                  leading: KitRow.icon(context, AppIconography.unlink),
                  title: copy.serverSettingsDisconnectTitle(profile.name),
                  titleMaxLines: 2,
                  supporting: TextSpan(
                    text: copy.serverSettingsDisconnectDetail(profile.name),
                  ),
                  supportingMaxLines: 4,
                  onTap: _disconnect,
                ),
              ],
            ),
          ],
        ],
      ),
    );
  }

  @override
  void dispose() {
    widget.controller.removeListener(_connectionChanged);
    super.dispose();
  }
}

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
