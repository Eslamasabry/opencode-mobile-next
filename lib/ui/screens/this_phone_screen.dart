import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../builtin/app_exit_recovery.dart' show appLifecycleBridgeProvider;
import '../../builtin/builtin_linux.dart';
import '../../builtin/builtin_server.dart';
import '../../builtin/project_export.dart' show MethodChannelProjectExport;
import '../../builtin/project_export_controller.dart';
import '../../builtin/reply_watch.dart';
import '../../builtin/setup/component_removal.dart';
import '../../builtin/setup/components.dart' show SetupComponentIds;
import '../../builtin/setup/phone_setup.dart';
import '../../builtin/setup/setup_contract.dart';
import '../../builtin/setup/termux_setup_host.dart';
import '../../builtin/team/builtin_team.dart';
import '../../l10n/app_localizations.dart';
import '../../state/connection.dart';
import '../../state/free_model_notice.dart';
import '../../state/orchestration_store.dart' show PhoneOffer;
import '../../state/phone_host.dart';
import '../../state/profiles.dart' show OrchestrationHostKind;
import '../../state/termux_host_setup.dart';
import '../../termux/bridge.dart';
import '../../termux/processes.dart';
import '../../termux/termux_reach.dart';
import '../app_iconography.dart';
import '../kit/kit.dart';
import '../widgets/builtin_team_section.dart' show forgetBuiltinTeam;
import '../widgets/managed_server_recovery_option.dart';
import '../widgets/phone_server_card.dart';
import '../widgets/phone_server_consents.dart';
import '../widgets/safety_confirms.dart';
import '../widgets/team_phone_onboarding.dart' show teamPhoneRuntime;
import '../widgets/termux_migration_entry.dart';
import '../widgets/termux_phone_tools.dart';
import '../widgets/termux_problem_fix.dart';
import 'keep_running_screen.dart';
import 'library_screen.dart' show IntegrationsScreen, IntegrationsMode;
import 'local_agent_screen.dart';
import 'manage_space_screen.dart' show ProjectExportScreen;
import 'phone_setup/phone_setup_routes.dart';
import 'phone_setup/phone_setup_selection.dart';
import 'phone_setup/phone_setup_termux_job_screen.dart';
import 'phone_setup/phone_setup_termux_screen.dart';
import 'termux_processes_screen.dart';

/// Which host This phone shows when nobody said: the one in use, else the
/// in-app one when it is saved, else Termux.
PhoneHostKind defaultPhoneHostKind(ConnectionController connection) {
  final active = connection.profile;
  if (active != null && TermuxBridge.managesServerUrl(active.baseUrl)) {
    return PhoneHostKind.termux;
  }
  if (looksLikeInAppServer(active)) return PhoneHostKind.inApp;
  final store = connection.store;
  if (store.profiles.any(looksLikeInAppServer)) return PhoneHostKind.inApp;
  if (store.profiles.any((p) => TermuxBridge.managesServerUrl(p.baseUrl))) {
    return PhoneHostKind.termux;
  }
  return BuiltinLinux.supported ? PhoneHostKind.inApp : PhoneHostKind.termux;
}

/// This phone's route (lib/main.dart registers it on Android). Its
/// argument is the [PhoneHostKind] to show; none means the one in use.
const thisPhoneRoute = '/this-phone';

/// Opens This phone for [kind] (the one in use when null).
Future<void> openThisPhone(BuildContext context, {PhoneHostKind? kind}) =>
    Navigator.of(context).pushNamed<void>(thisPhoneRoute, arguments: kind);

/// This phone: OpenCode on this phone, whichever host runs it (inside the
/// app or in Termux), on one page (programme P1.5).
///
/// One list ordered by what is most likely needed: the status with the one
/// act it needs now (start, connect, finish a switch), then update, switch
/// between OpenCode 1 and 2, add tools, what is installed, storage, what
/// runs (Running on this phone, where the host can list it; left out when
/// the list cannot be read), keeping it running, the log, and removing it
/// last. Each act names
/// what it acts on ("Stop the server on this phone"). Installing, updating,
/// switching and adding tools run through phone setup's progress screen.
///
/// Each optional tool setup installed (Python, AI Team, voice typing) has
/// its own "Remove {tool}" at the end of the list (P1.4), with the one line
/// of what it deletes; its question says what goes, what stays and the
/// measured space that comes back. A tool something else still uses is
/// named with what uses it and cannot be removed.
class ThisPhoneScreen extends ConsumerStatefulWidget {
  const ThisPhoneScreen({
    super.key,
    this.kind,
    this.host,
    this.removal,
    this.scanProcesses,
  });

  /// Which host; null picks [defaultPhoneHostKind].
  final PhoneHostKind? kind;

  /// Tests pass their own host; the app makes one from the providers.
  final PhoneHost? host;

  /// Removes the host's tools one by one; tests pass their own, the app
  /// makes one for the host.
  final ComponentRemovalService? removal;

  /// Reads what runs in Termux for the Running on this phone row; tests
  /// pass their own, the app reads through the tools script.
  final Future<TermuxProcessReport> Function()? scanProcesses;

  @override
  ConsumerState<ThisPhoneScreen> createState() => _ThisPhoneScreenState();
}

class _ThisPhoneScreenState extends ConsumerState<ThisPhoneScreen> {
  late final PhoneHost _host;
  late final bool _ownsHost;
  Set<String>? _installedOptional;
  bool _connecting = false;
  bool _removing = false;

  /// The host's tools that can be removed one by one (P1.4), read after
  /// the page loads; null until then or when there is no engine.
  ComponentRemovalService? _removal;
  List<ComponentRemovalEntry>? _removable;

  /// The tool whose removal runs now.
  String? _removingTool;

  /// What runs in Termux (P5.3), for the Running on this phone row: null
  /// until read. When it cannot be read the row is left out of the list
  /// (the owner's review: no dead "Not available right now" row).
  TermuxProcessReport? _processes;
  bool _processesUnavailable = false;

  /// The server log under Details (P1.5): folded until someone opens it.
  final _log = KitLogBuffer();
  bool _detailsOpen = false;

  /// How proot runs the in-app server and whether the phone is kept awake
  /// (Performance, under Details); read when Details opens.
  BuiltinPerformance? _performance;

  /// Times replies (Reply speed, Performance); listened to while open.
  late final ReplyWatch _replies;

  AppLocalizations get _l10n =>
      lookupAppLocalizations(Localizations.localeOf(context));

  ConnectionController get _connection => ref.read(connProvider);

  static SetupEngine? _engine() {
    try {
      return PhoneSetup.engine;
    } on StateError {
      return null;
    }
  }

  /// The engine of the host this page shows: its registry and checks say
  /// what is installed there.
  SetupEngine? _hostEngine() {
    if (_host.kind != PhoneHostKind.termux) return _engine();
    try {
      return PhoneSetup.termux;
    } on StateError {
      return null;
    }
  }

  /// Back from Android's settings, Termux or the F-Droid page after a fix:
  /// the phone is read again, and a server found running is connected.
  late final AppLifecycleListener _lifecycle = AppLifecycleListener(
    onResume: () {
      if (_host.termuxProblem != null && mounted) unawaited(_afterFix());
    },
  );

  /// A Termux fix was made: the next look that finds OpenCode running
  /// connects without another tap.
  bool _connectAfterFix = false;

  Future<void> _afterFix() async {
    await _load();
    if (!mounted || !_connectAfterFix || _host.termuxProblem != null) return;
    _connectAfterFix = false;
    if (_host.state == PhoneHostState.running &&
        _connection.api == null &&
        _host.profile != null) {
      await _connect();
    }
  }

  /// The one act that fixes why Termux cannot be reached.
  Future<void> _fixTermux(TermuxProblem problem) async {
    _connectAfterFix = true;
    final again = await fixTermuxProblem(
      context,
      problem,
      restart: _start,
      retry: _load,
    );
    if (again && mounted && problem != TermuxProblem.notAnswering) {
      await _afterFix();
    }
  }

  @override
  void initState() {
    super.initState();
    _lifecycle;
    _ownsHost = widget.host == null;
    _host = widget.host ?? _makeHost();
    _host.addListener(_changed);
    _replies = ref.read(replyWatchProvider)..addListener(_changed);
    _removal = widget.removal ?? _makeRemoval();
    setupToolsChanged.addListener(_onSetup);
    unawaited(_load());
  }

  /// A setup job ended (a failed Add tools still leaves its finished
  /// tools): the page reads the phone again, as Settings > AI Team does.
  void _onSetup() {
    if (mounted) unawaited(_load());
  }

  /// One removal service for this page, on the host it shows: the app's
  /// own Linux with the in-app team, or Termux's Ubuntu with Termux's team
  /// manager.
  ComponentRemovalService? _makeRemoval() {
    final engine = _hostEngine();
    if (engine == null) return null;
    if (_host.kind == PhoneHostKind.termux) {
      final host = TermuxSetupHost();
      return ComponentRemovalService(
        linux: host,
        registry: engine.registry,
        removeTeam: ComponentRemovalService.termuxTeamRemover(
          runtime: teamPhoneRuntime,
          host: host,
        ),
      );
    }
    final linux = ref.read(builtinLinuxProvider);
    return ComponentRemovalService(
      linux: linux,
      registry: engine.registry,
      team: BuiltinTeam(linux: linux),
    );
  }

  PhoneHost _makeHost() {
    final kind = widget.kind ?? defaultPhoneHostKind(_connection);
    if (kind == PhoneHostKind.termux) {
      return TermuxPhoneHost(connection: _connection, copy: () => _l10n);
    }
    return InAppPhoneHost(
      linux: ref.read(builtinLinuxProvider),
      starter: ref.read(builtinServerStarterProvider),
      connection: _connection,
      engine: _engine(),
    );
  }

  Future<void> _load() async {
    await _host.refresh();
    // Installed tools are read on both hosts (Termux's checks run in its
    // Ubuntu); nothing to read before setup put anything there.
    if (!_host.installed) {
      if (mounted) setState(() => _removable = null);
      return;
    }
    // The reads run in the host's Linux; none waits for another.
    await Future.wait([_readInstalled(), _readRemovable(), _readProcesses()]);
  }

  /// Termux's process list: only Termux's tools can list what runs there
  /// (the in-app Linux has no process inventory yet, so its page has no
  /// such row).
  Future<void> _readProcesses() async {
    if (_host.kind != PhoneHostKind.termux) return;
    try {
      final report = await (widget.scanProcesses ?? TermuxProcesses.scan)();
      if (!mounted) return;
      setState(() {
        _processes = report;
        _processesUnavailable = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _processes = null;
        _processesUnavailable = true;
      });
    }
  }

  Future<void> _openProcesses() async {
    await pushKitPage<void>(context, (_) => const TermuxProcessesScreen());
    if (mounted) unawaited(_readProcesses());
  }

  Future<void> _readInstalled() async {
    final engine = _hostEngine();
    if (engine == null) return;
    try {
      final installed = await engine.installedOptional();
      if (mounted) setState(() => _installedOptional = installed);
    } catch (_) {
      // Unknown: the list shows what every setup installs.
    }
  }

  Future<void> _readRemovable() async {
    final removal = _removal;
    if (removal == null) return;
    try {
      final entries = await removal.inventory();
      if (mounted) setState(() => _removable = entries);
    } catch (_) {
      // Unknown: no tool offers removal until it is read.
      if (mounted) setState(() => _removable = null);
    }
  }

  @override
  void dispose() {
    setupToolsChanged.removeListener(_onSetup);
    _lifecycle.dispose();
    _host.removeListener(_changed);
    _replies.removeListener(_changed);
    if (_ownsHost) _host.dispose();
    _log.dispose();
    super.dispose();
  }

  void _changed() {
    if (mounted) setState(() {});
  }

  String _runtimeName(TermuxRuntime runtime) =>
      runtime == TermuxRuntime.openCode2
      ? _l10n.setupRuntimeTwo
      : _l10n.setupRuntimeOne;

  TermuxRuntime get _otherRuntime => _host.runtime == TermuxRuntime.openCode2
      ? TermuxRuntime.openCode1
      : TermuxRuntime.openCode2;

  bool get _connectedHere {
    final profile = _host.profile;
    final connection = ref.watch(connProvider);
    return profile != null &&
        connection.hasConnectedServer &&
        connection.profile?.id == profile.id;
  }

  // --- Acts ------------------------------------------------------------------

  Future<void> _setUp() async {
    if (_host.kind == PhoneHostKind.termux) {
      await openPhoneSetupTermux(context, firstSetup: true);
    } else {
      await openPhoneSetupStart(context);
    }
    if (mounted) unawaited(_load());
  }

  /// A fresh start with the in-app server (phone setup, where it leads).
  Future<void> _setUpInApp() async {
    await openPhoneSetupStart(context);
    if (mounted) unawaited(_load());
  }

  /// Installs the same version again after an install or update that did
  /// not finish.
  Future<void> _reinstall() async {
    if (_host.kind == PhoneHostKind.inApp) {
      return _inApp(PhoneServerAction.update);
    }
    await openPhoneSetupTermux(context, job: TermuxHostJob.update);
    if (mounted) unawaited(_load());
  }

  Future<void> _showProgress() async {
    if (_host.kind == PhoneHostKind.termux) {
      await openPhoneSetupTermux(context, job: TermuxHostJob.update);
    } else {
      await openPhoneSetupProgress(context);
    }
    if (mounted) unawaited(_load());
  }

  Future<void> _start() async {
    await _host.start(_l10n);
    if (!mounted || _host.state != PhoneHostState.running) return;
    // The first start is the moment keeping it alive matters (P6.7): asked
    // once per server, nothing on later starts.
    final profile = _host.profile;
    if (profile != null) {
      await askPhoneServerConsents(
        context,
        connection: _connection,
        bridge: ref.read(appLifecycleBridgeProvider),
        profileId: profile.id,
      );
      if (!mounted) return;
    }
    // Started with nothing else in use: connecting is the obvious next step.
    if (_connection.api == null) await _connect(openHome: false);
  }

  Future<void> _stop() async {
    if (!await confirmStopLocalServer(context) || !mounted) return;
    await _host.stop(_l10n);
  }

  Future<void> _connect({bool openHome = true}) async {
    final profile = _host.profile;
    if (profile == null || _connecting) return;
    setState(() => _connecting = true);
    final navigator = Navigator.of(context);
    final connection = _connection;
    await connection.connect(profile);
    if (!mounted) return;
    setState(() => _connecting = false);
    if (connection.hasConnectedServer && openHome) {
      unawaited(navigator.pushNamedAndRemoveUntil('/home', (_) => false));
    }
  }

  /// In-app acts run as the "This phone" card runs them: through phone
  /// setup's engine and its progress screen.
  Future<void> _inApp(PhoneServerAction action) async {
    final profile = _host.profile;
    if (profile == null) return;
    final removed = await runPhoneServerAction(
      context,
      action,
      connection: _connection,
      linux: ref.read(builtinLinuxProvider),
      profile: profile,
      bytesUsed: _host.bytesUsed,
      // Busy only while a confirmed attempt runs; a failed one leaves the
      // question open with Try again and the page no longer removing.
      onRemovingChanged: (working) {
        if (mounted) setState(() => _removing = working);
      },
    );
    if (!mounted) return;
    setState(() => _removing = false);
    if (removed || action != PhoneServerAction.terminal) unawaited(_load());
  }

  Future<void> _update() async {
    if (_host.kind == PhoneHostKind.inApp) {
      return _inApp(PhoneServerAction.update);
    }
    final l10n = _l10n;
    final runtime = _runtimeName(_host.runtime);
    if (_connection.busySessions.isNotEmpty) {
      await showKitAlert(
        context,
        title: l10n.thisPhoneUpdateTitle(runtime),
        body: l10n.thisPhoneUpdateBusy,
        icon: AppIconography.warning,
      );
      return;
    }
    final confirmed = await showKitConfirm(
      context,
      title: l10n.thisPhoneUpdateTitle(runtime),
      body: l10n.thisPhoneUpdateBody(_host.runtime.pinnedVersion),
      consequences: [l10n.thisPhoneUpdateKept],
      confirmLabel: l10n.thisPhoneUpdate,
      icon: AppIconography.systemDownload,
      confirmKey: const ValueKey('this-phone-update-confirm'),
    );
    if (!confirmed || !mounted) return;
    await openPhoneSetupTermux(context, job: TermuxHostJob.update);
    if (mounted) unawaited(_load());
  }

  Future<void> _switchRuntime(TermuxRuntime target) async {
    final l10n = _l10n;
    final confirmed = await showKitConfirm(
      context,
      title: l10n.setupSwitchConfirmTitle(_runtimeName(target)),
      body: l10n.setupSwitchConfirmDetail,
      confirmLabel: l10n.setupSwitchConfirm(_runtimeName(target)),
      icon: AppIconography.sync,
      sheetKey: const ValueKey('this-phone-switch-sheet'),
      confirmKey: const ValueKey('this-phone-switch-confirm'),
    );
    if (!confirmed || !mounted) return;
    if (_host.kind == PhoneHostKind.inApp) {
      return _inApp(PhoneServerAction.switchRuntime);
    }
    await openPhoneSetupTermux(
      context,
      job: TermuxHostJob.switchRuntime,
      target: target,
    );
    if (mounted) unawaited(_load());
  }

  Future<void> _addTools() async {
    if (_host.kind == PhoneHostKind.inApp) {
      return _inApp(PhoneServerAction.addTools);
    }
    // The same sheet and the same job as the in-app host (P1.2), with
    // Termux's engine: it lists what Termux has as installed and adds the
    // rest in Termux.
    final ids = await showPhoneSetupCustomize(
      context,
      addMode: true,
      host: SetupHostKind.termux,
    );
    if (ids == null || ids.isEmpty || !mounted) return;
    await openPhoneSetupTermuxJob(context, adding: ids);
    if (mounted) unawaited(_load());
  }

  /// Asks before removing one tool (P1.4): what it deletes, what stays,
  /// and the space that comes back, measured now (a slow or failed
  /// reading leaves the figure out). Removal runs inside the question, so
  /// a failure keeps it open with Try again. The list is read again
  /// afterwards either way: a failed removal may have removed part.
  Future<void> _removeTool(SetupComponent component) async {
    final removal = _removal;
    if (removal == null || _removingTool != null) return;
    final l10n = _l10n;
    final id = component.id;
    final tool = component.shortTitle;
    int? bytes;
    try {
      bytes = await removal
          .freedBytes(id)
          .timeout(const Duration(seconds: 3), onTimeout: () => null);
    } catch (_) {
      bytes = null;
    }
    if (!mounted) return;
    final removed = await showKitConfirm(
      context,
      title: l10n.thisPhoneRemoveToolTitle(tool),
      body: bytes != null
          ? l10n.thisPhoneRemoveToolBody(formatPhoneStorage(bytes))
          : l10n.thisPhoneRemoveToolBodyUnmeasured,
      confirmLabel: l10n.thisPhoneRemoveTool(tool),
      kind: KitConfirmKind.destructive,
      icon: AppIconography.delete,
      consequenceItems: _removeConsequences(l10n, id),
      action: () async {
        if (mounted) setState(() => _removingTool = id);
        try {
          await removal.remove(id);
        } finally {
          if (mounted) setState(() => _removingTool = null);
        }
      },
      sheetKey: ValueKey('this-phone-remove-$id-sheet'),
      confirmKey: ValueKey('this-phone-remove-$id-confirm'),
    );
    if (removed && id == SetupComponentIds.aiTeam) await _forgetTeam();
    if (mounted) unawaited(_load());
  }

  List<KitConsequence> _removeConsequences(AppLocalizations l10n, String id) =>
      switch (id) {
        SetupComponentIds.python => [
          KitConsequence(
            l10n.thisPhoneRemovePythonLost,
            mark: KitConsequenceMark.lost,
          ),
          KitConsequence(
            l10n.thisPhoneRemovePythonKept,
            mark: KitConsequenceMark.kept,
          ),
        ],
        SetupComponentIds.aiTeam => [
          KitConsequence(
            l10n.thisPhoneRemoveTeamLost,
            mark: KitConsequenceMark.lost,
          ),
          KitConsequence(
            l10n.thisPhoneRemoveTeamLostWork,
            mark: KitConsequenceMark.lost,
          ),
          KitConsequence(
            l10n.thisPhoneRemoveTeamKept,
            mark: KitConsequenceMark.kept,
          ),
        ],
        SetupComponentIds.voice => [
          KitConsequence(
            l10n.thisPhoneRemoveVoiceLost,
            mark: KitConsequenceMark.lost,
          ),
        ],
        _ => [
          KitConsequence(
            l10n.thisPhoneRemoveToolKept,
            mark: KitConsequenceMark.kept,
          ),
        ],
      };

  String _removeDetail(AppLocalizations l10n, String id) => switch (id) {
    SetupComponentIds.python => l10n.thisPhoneRemovePythonDetail,
    SetupComponentIds.aiTeam => l10n.thisPhoneRemoveTeamDetail,
    SetupComponentIds.voice => l10n.thisPhoneRemoveVoiceDetail,
    _ => l10n.thisPhoneRemoveToolDetail,
  };

  /// The removed team's config leaves every saved profile of this host
  /// that points at it, so no screen keeps reading a team that is gone.
  Future<void> _forgetTeam() async {
    final connection = _connection;
    try {
      if (_host.kind == PhoneHostKind.inApp) {
        await forgetBuiltinTeam(connection);
        return;
      }
      for (final profile in [...connection.store.profiles]) {
        if (!TermuxBridge.managesServerUrl(profile.baseUrl) ||
            profile.orchestration?.hostKind != OrchestrationHostKind.phone) {
          continue;
        }
        final current = connection.orchestration;
        if (current != null && current.profileId == profile.id) {
          await current.remove();
        } else {
          await connection.orchestrationStore.sweep(profile.id);
        }
        profile.orchestration = null;
        await connection.store.upsert(profile);
        await connection.orchestrationStore.setPhoneOffer(
          profile.id,
          PhoneOffer.dismissed,
        );
      }
      connection.syncOrchestration();
    } catch (_) {
      // The team is gone either way; its screens say it is not there.
    }
  }

  /// Claude Code runs beside OpenCode in Termux: its own page, with its own
  /// cost and steps.
  void _openLocalAgent() => unawaited(
    pushKitPage<void>(
      context,
      (_) => LocalAgentScreen(
        onConnected: () => Navigator.of(
          context,
        ).pushNamedAndRemoveUntil('/home', (_) => false),
      ),
    ),
  );

  /// The server's log, read when Details opens and again while it stays
  /// open and the server runs.
  Future<void> _readLog() async {
    try {
      _log.replaceText((await _host.readLog()).trimRight());
    } catch (_) {
      // Nothing to show is said by the panel's empty words.
    }
  }

  void _details(bool open) {
    setState(() => _detailsOpen = open);
    if (open) {
      unawaited(_readLog());
      unawaited(_readPerformance());
    }
  }

  Future<void> _readPerformance() async {
    if (_host.kind != PhoneHostKind.inApp) return;
    try {
      final performance = await ref.read(builtinLinuxProvider).performance();
      if (mounted) setState(() => _performance = performance);
    } catch (_) {
      // Left out: the rows say nothing rather than a guess.
    }
  }

  /// Provider sign-in on the connected server: the way off OpenCode's free
  /// model.
  Future<void> _signInToProvider() => pushKitPage<void>(
    context,
    (_) => IntegrationsScreen(
      controller: _connection,
      mode: IntegrationsMode.providers,
    ),
  );

  /// "4.1 s": one decimal, isolated left to right.
  String _seconds(AppLocalizations l10n, Duration duration) => KitBidi.ltr(
    l10n.replySpeedSeconds((duration.inMilliseconds / 1000).toStringAsFixed(1)),
  );

  // --- The page --------------------------------------------------------------

  @override
  Widget build(BuildContext context) {
    final l10n = _l10n;
    final tokens = KitTokens.of(context);
    final state = _host.state;
    return KitScreen(
      topBar: KitTopBar(title: l10n.phoneServerCardTitle),
      width: KitScreenWidth.reading,
      loading: state == PhoneHostState.checking,
      loadingLabel: l10n.phoneServerCardChecking,
      // One Column inside the list, so every row is laid out at once: a
      // search result that lands on a row further down ("Restart after a
      // crash", P9.4) finds it built even on a small screen at large text.
      body: ListView(
        key: const ValueKey('this-phone'),
        padding: EdgeInsetsDirectional.only(
          top: tokens.space2,
          bottom: KitScreen.endPadding(context),
        ),
        children: [
          Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              _status(context, l10n),
              // Replies from OpenCode's free model are the slow ones: say
              // so where this server is looked after, with the way out.
              if (_connectedHere &&
                  connectionUsesFreeModel(ref.watch(connProvider))) ...[
                SizedBox(height: tokens.space3),
                Padding(
                  padding: EdgeInsetsDirectional.symmetric(
                    horizontal: tokens.gutter,
                  ),
                  child: KitNotice(
                    key: const ValueKey('this-phone-free-model'),
                    icon: AppIconography.speed,
                    message: l10n.freeModelNotice,
                    liveRegion: false,
                    actions: [
                      KitAction(
                        key: const ValueKey('this-phone-free-model-sign-in'),
                        label: l10n.freeModelSignIn,
                        onPressed: () => unawaited(_signInToProvider()),
                      ),
                    ],
                  ),
                ),
              ],
              if (_host.installed) ...[
                SizedBox(height: tokens.sectionGap),
                _list(context, l10n),
                SizedBox(height: tokens.sectionGap),
                _logFold(context, l10n),
              ],
            ],
          ),
        ],
      ),
    );
  }

  /// OpenCode's row: what it is, where it runs, how it is, and under it the
  /// one act it needs now.
  Widget _status(BuildContext context, AppLocalizations l10n) {
    final tokens = KitTokens.of(context);
    final state = _host.state;
    final connected = _connectedHere;
    final working =
        _connecting ||
        _removing ||
        _removingTool != null ||
        const {
          PhoneHostState.checking,
          PhoneHostState.starting,
          PhoneHostState.stopping,
          PhoneHostState.settingUp,
        }.contains(state);
    final word = _removing
        ? l10n.phoneServerCardRemoving
        : switch (state) {
            PhoneHostState.checking => l10n.phoneServerCardChecking,
            PhoneHostState.notSetUp => l10n.phoneServerCardNotSetUp,
            PhoneHostState.settingUp => l10n.phoneServerCardSettingUp,
            PhoneHostState.needsYou => l10n.thisPhoneNeedsAttention,
            PhoneHostState.starting => l10n.phoneServerCardStarting,
            PhoneHostState.stopping => l10n.phoneServerCardStopping,
            PhoneHostState.stopped => l10n.phoneServerCardStopped,
            PhoneHostState.running => l10n.phoneServerCardRunning,
          };
    final where = _host.kind == PhoneHostKind.termux
        ? l10n.thisPhoneHostTermux
        : l10n.thisPhoneHostInApp;
    // The generation is the title, so the line under it says the rest once;
    // one the page could not read is not guessed.
    final title = _host.runtimeKnown
        ? _runtimeName(_host.runtime)
        : l10n.firstRunAgentOpenCode;
    final reach = working ? null : _host.termuxProblem;
    final reachWords = reach == null
        ? null
        : termuxProblemWords(
            l10n,
            reach,
            runtime: _host.runtimeKnown ? title : null,
          );
    final detail = [?_host.version, where].join(' · ');
    final switchTarget = _host.switchTarget;
    final switchPrevious = _host.switchPrevious;
    // What went wrong, in plain words; the host's own text is under
    // Details at the end of the page.
    final problem = _removing || working ? null : _host.problem;
    final runtimeName = _runtimeName(_host.runtime);
    final String? line = switch (problem) {
      _ when _removing || working => null,
      _ when reachWords != null => reachWords.line,
      _ when state == PhoneHostState.needsYou && switchTarget != null =>
        l10n.thisPhoneSwitchStopped(_runtimeName(switchTarget)),
      PhoneHostProblem.start => l10n.thisPhoneStartFailed(runtimeName),
      PhoneHostProblem.install => l10n.thisPhoneInstallFailed(runtimeName),
      PhoneHostProblem.stop => l10n.thisPhoneStopFailed(runtimeName),
      PhoneHostProblem.check => l10n.thisPhoneCheckFailed(runtimeName),
      null => null,
    };

    KitAction? primary;
    KitAction? secondary;
    if (!working && reach != null) {
      // The cause's own fix; "Try again" only where retrying can help.
      primary = KitAction(
        key: const ValueKey('this-phone-termux-fix'),
        label: reachWords!.action,
        onPressed: () => unawaited(_fixTermux(reach)),
      );
    } else if (!working) {
      switch (state) {
        case PhoneHostState.notSetUp:
          primary = KitAction(
            key: const ValueKey('this-phone-set-up'),
            label: l10n.thisPhoneSetUp,
            onPressed: () => unawaited(_setUp()),
          );
        case PhoneHostState.needsYou when switchTarget != null:
          primary = KitAction(
            key: const ValueKey('this-phone-switch-retry'),
            label: l10n.setupSwitchRetry(_runtimeName(switchTarget)),
            onPressed: () => unawaited(
              openPhoneSetupTermux(
                context,
                job: TermuxHostJob.switchRuntime,
                target: switchTarget,
              ),
            ),
          );
          if (switchPrevious != null && switchPrevious != switchTarget) {
            secondary = KitAction(
              key: const ValueKey('this-phone-switch-return'),
              label: l10n.setupSwitchReturn(_runtimeName(switchPrevious)),
              onPressed: () => unawaited(
                openPhoneSetupTermux(
                  context,
                  job: TermuxHostJob.switchRuntime,
                  target: switchPrevious,
                ),
              ),
            );
          }
        case PhoneHostState.needsYou || PhoneHostState.notSetUp
            when problem == PhoneHostProblem.check:
          primary = KitAction(
            key: const ValueKey('this-phone-check-again'),
            label: l10n.commonRetry,
            onPressed: () => unawaited(_load()),
          );
        case PhoneHostState.needsYou when problem == PhoneHostProblem.install:
          primary = KitAction(
            key: const ValueKey('this-phone-reinstall'),
            label: l10n.thisPhoneInstallAgain,
            onPressed: () => unawaited(_reinstall()),
          );
          // The version installed before may still start.
          secondary = KitAction(
            key: const ValueKey('this-phone-start'),
            label: l10n.thisPhoneStart,
            onPressed: () => unawaited(_start()),
          );
        case PhoneHostState.needsYou || PhoneHostState.stopped:
          primary = KitAction(
            key: const ValueKey('this-phone-start'),
            label: problem == PhoneHostProblem.start
                ? l10n.thisPhoneStartAgain
                : l10n.thisPhoneStart,
            onPressed: () => unawaited(_start()),
          );
        case PhoneHostState.running:
          if (!connected && _host.profile != null) {
            primary = KitAction(
              key: const ValueKey('this-phone-connect'),
              label: l10n.thisPhoneConnect,
              onPressed: () => unawaited(_connect()),
            );
          }
          secondary = KitAction(
            key: const ValueKey('this-phone-stop'),
            label: l10n.thisPhoneStop,
            destructive: true,
            onPressed: () => unawaited(_stop()),
          );
        case PhoneHostState.checking ||
            PhoneHostState.settingUp ||
            PhoneHostState.starting ||
            PhoneHostState.stopping:
          break;
      }
    } else if (state == PhoneHostState.settingUp) {
      primary = KitAction(
        key: const ValueKey('this-phone-progress'),
        label: l10n.phoneServerCardShowProgress,
        onPressed: () => unawaited(_showProgress()),
      );
    }
    final actions = KitActionBlock(primary: primary, secondary: secondary);
    final running = state == PhoneHostState.running;
    // "Needs you" starts the line, inline (never a word floating at the
    // row's end); every other state keeps its word at the end.
    final needsYou = state == PhoneHostState.needsYou && !working;
    return KitRowGroup(
      key: const ValueKey('this-phone-status'),
      children: [
        Semantics(
          container: true,
          liveRegion: true,
          child: KitRow(
            leading: working
                ? const KitStatusMark(state: KitMarkState.working)
                : KitRowIcon(AppIconography.phone, current: connected),
            title: title,
            titleKey: const ValueKey('this-phone-title'),
            supporting: TextSpan(
              children: [
                if (connected) kitCurrentSpan(context, l10n.serverRowConnected),
                if (needsYou) KitNeedsYou.span(context),
                TextSpan(text: detail),
              ],
            ),
            supportingKey: const ValueKey('this-phone-detail'),
            supportingMaxLines: 2,
            // The state word keeps the row's end inset (a trailing slot
            // leaves only space1), never against the panel's edge.
            trailing: needsYou
                ? null
                : Padding(
                    padding: EdgeInsetsDirectional.only(end: tokens.space3),
                    child: KitText(
                      word,
                      key: const ValueKey('this-phone-state'),
                      role: KitTextRole.secondary,
                      tone: running && !working
                          ? KitTextTone.success
                          : KitTextTone.secondary,
                    ),
                  ),
            below: line == null
                ? null
                : KitText(
                    line,
                    key: const ValueKey('this-phone-failure'),
                    role: KitTextRole.secondary,
                    tone: KitTextTone.primary,
                  ),
          ),
        ),
        if (!actions.isEmpty)
          Padding(
            padding: EdgeInsetsDirectional.fromSTEB(
              tokens.space4,
              tokens.space2,
              tokens.space4,
              tokens.space3,
            ),
            child: actions,
          ),
      ],
    );
  }

  /// Everything else, in one list: the likely acts first, the facts next,
  /// the rare tools after, Remove last.
  Widget _list(BuildContext context, AppLocalizations l10n) {
    final state = _host.state;
    final inApp = _host.kind == PhoneHostKind.inApp;
    final busy =
        _connecting ||
        _removing ||
        _removingTool != null ||
        const {
          PhoneHostState.settingUp,
          PhoneHostState.starting,
          PhoneHostState.stopping,
        }.contains(state);
    final busyReason = busy ? l10n.thisPhoneBusy : null;
    final hasEngine = !inApp || _engine() != null;
    final other = _otherRuntime;
    final switchLocked = !inApp && !_host.canSwitchRuntime;
    final halfSwitched = _host.switchTarget != null;
    // Update only when the installed server is not already the pinned one.
    final installed = _host.version?.trim().replaceFirst(RegExp('^v'), '');
    final upToDate = installed == _host.runtime.pinnedVersion;
    // After an install that did not finish, Install again at the top is
    // this very act: it is not offered twice.
    final reinstalling = _host.problem == PhoneHostProblem.install;
    final recoveryProfile = inApp
        ? null
        : _connection.store.profiles
              .where((p) => TermuxBridge.managesServerUrl(p.baseUrl))
              .firstOrNull;
    Widget icon(IconData data) => KitRow.icon(context, data);

    final migrateFrom = inApp ? null : _host.profile;
    final reach = inApp ? null : _host.termuxProblem;
    if (reach != null && reach.blocksTermux) {
      // Nothing that runs in Termux can work now: those acts wait until
      // the fix above is done. The in-app server is the simpler way, and
      // is always offered to a Termux user.
      return KitRowGroup(
        key: const ValueKey('this-phone-list'),
        children: [
          if (BuiltinLinux.supported)
            KitRow(
              key: const ValueKey('this-phone-in-app-instead'),
              leading: icon(AppIconography.swap),
              title: l10n.termuxInAppInstead,
              titleMaxLines: 2,
              supporting: TextSpan(text: l10n.termuxInAppInsteadBlocked),
              supportingMaxLines: 3,
              trailing: const KitChevron(),
              onTap: () => unawaited(_setUpInApp()),
            ),
          KitRow(
            key: const ValueKey('this-phone-keep-running'),
            leading: icon(AppIconography.batteryCharging),
            title: l10n.keepRunningTitle,
            supporting: TextSpan(text: l10n.keepRunningRowSubtitle),
            supportingMaxLines: 2,
            trailing: const KitChevron(),
            onTap: () => unawaited(openKeepRunningScreen(context)),
          ),
        ],
      );
    }
    return KitRowGroup(
      key: const ValueKey('this-phone-list'),
      children: [
        // A Termux user's way to the in-app server leads the list: it is
        // the one change this page cannot do in place (owner, 2026-09-28).
        if (offersTermuxMigration(migrateFrom))
          TermuxMigrationRow(profile: migrateFrom!)
        else if (!inApp && BuiltinLinux.supported)
          KitRow(
            key: const ValueKey('this-phone-in-app-instead'),
            leading: icon(AppIconography.swap),
            title: l10n.termuxInAppInstead,
            titleMaxLines: 2,
            supporting: TextSpan(text: l10n.termuxInAppInsteadDetail),
            supportingMaxLines: 3,
            trailing: const KitChevron(),
            onTap: () => unawaited(_setUpInApp()),
          ),
        if (hasEngine && !halfSwitched && upToDate)
          KitRow(
            key: const ValueKey('this-phone-up-to-date'),
            leading: icon(AppIconography.check),
            title: l10n.thisPhoneUpToDate(_host.runtime.pinnedVersion),
          ),
        if (hasEngine && !halfSwitched && !upToDate && !reinstalling)
          KitRow(
            key: const ValueKey('this-phone-update'),
            leading: icon(AppIconography.systemDownload),
            title: l10n.thisPhoneUpdate,
            supporting: TextSpan(
              text: l10n.thisPhoneUpdateDetail(_host.runtime.pinnedVersion),
            ),
            trailing: const KitChevron(),
            enabled: !busy,
            disabledReason: busyReason,
            onTap: () => unawaited(_update()),
          ),
        if (hasEngine && !halfSwitched)
          KitRow(
            key: const ValueKey('this-phone-switch'),
            leading: icon(AppIconography.sync),
            title: l10n.phoneServerCardSwitchTo(_runtimeName(other)),
            supporting: switchLocked
                ? TextSpan(text: l10n.setupSwitchLegacyTwo)
                : null,
            supportingMaxLines: 3,
            trailing: const KitChevron(),
            enabled: !busy && !switchLocked,
            disabledReason: switchLocked
                ? l10n.setupSwitchLegacyTwo
                : busyReason,
            onTap: () => unawaited(_switchRuntime(other)),
          ),
        if (hasEngine)
          KitRow(
            key: const ValueKey('this-phone-add-tools'),
            leading: icon(AppIconography.tools),
            title: l10n.thisPhoneAddTools,
            supporting: TextSpan(text: l10n.thisPhoneAddToolsDetail),
            trailing: const KitChevron(),
            enabled: !busy,
            disabledReason: busyReason,
            onTap: () => unawaited(_addTools()),
          ),
        if (!inApp)
          KitRow(
            key: const ValueKey('local-agent-row'),
            leading: icon(AppIconography.agent),
            title: l10n.localAgentTitle,
            supporting: TextSpan(text: l10n.localAgentRowOptional),
            trailing: const KitChevron(),
            onTap: _openLocalAgent,
          ),
        _installed(context, l10n),
        if (inApp)
          KitRow(
            key: const ValueKey('this-phone-storage'),
            leading: icon(AppIconography.database),
            title: l10n.thisPhoneStorage,
            trailing: _host.bytesUsed == null
                ? null
                : KitRowValue(
                    formatPhoneStorage(_host.bytesUsed!),
                    chevron: false,
                  ),
          ),
        if (inApp) ?_replySpeed(context, l10n),
        if (inApp)
          KitRow(
            key: const ValueKey('this-phone-export-projects'),
            leading: icon(AppIconography.zip),
            title: l10n.thisPhoneExportProjects,
            supporting: TextSpan(text: l10n.thisPhoneExportProjectsDetail),
            trailing: const KitChevron(),
            onTap: () => unawaited(
              pushKitPage<void>(
                context,
                (_) => ProjectExportScreen(
                  controller: ProjectExportController(
                    platform: MethodChannelProjectExport(),
                  ),
                ),
              ),
            ),
          )
        else ...[
          const TermuxStorageRow(),
          // Left out, not dead, when the list cannot be read: the group
          // then draws no hairline for it either.
          if (!_processesUnavailable)
            PhoneProcessesRow(
              report: _processes,
              onTap: () => unawaited(_openProcesses()),
            ),
        ],
        if (recoveryProfile != null)
          ManagedServerRecoveryOption(
            prefs: _connection.store.prefs,
            profileID: recoveryProfile.id,
          ),
        KitRow(
          key: const ValueKey('this-phone-keep-running'),
          leading: icon(AppIconography.batteryCharging),
          title: l10n.keepRunningTitle,
          supporting: TextSpan(text: l10n.keepRunningRowSubtitle),
          supportingMaxLines: 2,
          trailing: const KitChevron(),
          onTap: () => unawaited(openKeepRunningScreen(context)),
        ),
        if (inApp)
          KitRow(
            key: const ValueKey('this-phone-terminal'),
            leading: icon(AppIconography.terminal),
            title: l10n.thisPhoneTerminal,
            trailing: const KitChevron(),
            enabled: !_removing,
            onTap: () => unawaited(_inApp(PhoneServerAction.terminal)),
          ),
        // Destructive rows sit last (the group moves them there): each
        // optional tool on its own, then OpenCode itself.
        ..._toolRemoveRows(context, l10n, busy: busy, busyReason: busyReason),
        if (inApp)
          KitRow(
            key: const ValueKey('this-phone-remove'),
            leading: icon(AppIconography.delete),
            title: l10n.thisPhoneRemove,
            destructive: true,
            enabled: !busy && state != PhoneHostState.settingUp,
            disabledReason: busyReason,
            onTap: () => unawaited(_inApp(PhoneServerAction.remove)),
          ),
      ],
    );
  }

  /// "Remove {tool}" for each optional tool setup installed on this host,
  /// in the registry's order. Required parts (Linux, Node.js, OpenCode) go
  /// only with OpenCode itself. A tool something still uses says what, and
  /// is off (never removed from under it).
  List<Widget> _toolRemoveRows(
    BuildContext context,
    AppLocalizations l10n, {
    required bool busy,
    required String? busyReason,
  }) {
    final entries = _removable;
    if (entries == null) return const [];
    final names = {
      for (final entry in entries)
        entry.component.id: entry.component.shortTitle,
    };
    final rows = <Widget>[];
    for (final entry in entries) {
      final component = entry.component;
      if (component.required ||
          component.native ||
          entry.presence != ComponentPresence.installed) {
        continue;
      }
      final String? neededBy;
      switch (entry.block) {
        case null:
          neededBy = null;
        case ComponentRemovalBlock.dependentInstalled ||
            ComponentRemovalBlock.dependencyUnknown:
          neededBy = l10n.thisPhoneRemoveToolNeededBy(
            joinSetupNames(l10n, [
              for (final id in entry.blockingDependents) names[id] ?? id,
            ]),
          );
        default:
          // Cannot be removed on its own here (no removal script, or its
          // state could not be read): no row rather than a dead one.
          continue;
      }
      rows.add(
        KitRow(
          key: ValueKey('this-phone-remove-${component.id}'),
          leading: KitRow.icon(context, AppIconography.delete),
          title: l10n.thisPhoneRemoveTool(component.shortTitle),
          supporting: TextSpan(
            text: neededBy ?? _removeDetail(l10n, component.id),
          ),
          supportingMaxLines: 2,
          destructive: true,
          enabled: !busy && neededBy == null,
          disabledReason: neededBy ?? busyReason,
          onTap: () => unawaited(_removeTool(component)),
        ),
      );
    }
    return rows;
  }

  /// The technical truth, last and folded (KIT-33): the server's log in
  /// the one log view (KIT-31), titled by the server it comes from so it
  /// does not repeat "Details".
  Widget _logFold(BuildContext context, AppLocalizations l10n) {
    final tokens = KitTokens.of(context);
    final running = _host.state == PhoneHostState.running;
    final version = _host.version;
    return Padding(
      padding: EdgeInsetsDirectional.symmetric(horizontal: tokens.gutter),
      child: KitDetailsFold(
        foldKey: const ValueKey('this-phone-details'),
        expanded: _detailsOpen,
        onExpansionChanged: _details,
        // What the host said went wrong, word for word (redacted by the
        // fold); the status row says it in plain words.
        text: _removing ? null : _host.failure,
        textKey: const ValueKey('this-phone-failure-details'),
        values: _performanceValues(l10n),
        child: KitLogPanel(
          lines: _log,
          panelKey: const ValueKey('this-phone-log'),
          title: version == null
              ? _runtimeName(_host.runtime)
              : l10n.phoneServerCardVersion(version),
          emptyText: l10n.phoneServerCardLogEmpty,
          live: running,
          onRefresh: running ? _readLog : null,
          ended: running ? null : const KitLogEnd(),
        ),
      ),
    );
  }

  /// How fast the last reply on this phone's server came, in plain words;
  /// left out until one was timed since the app opened.
  Widget? _replySpeed(BuildContext context, AppLocalizations l10n) {
    final last = _replies.lastInApp;
    if (last == null) return null;
    final first = last.firstToken;
    return KitRow(
      key: const ValueKey('this-phone-reply-speed'),
      leading: KitRow.icon(context, AppIconography.speed),
      title: l10n.replySpeedTitle,
      supporting: TextSpan(
        text: first == null
            ? l10n.replySpeedNoWords(_seconds(l10n, last.total))
            : l10n.replySpeedLast(
                _seconds(l10n, first),
                _seconds(l10n, last.total),
              ),
      ),
      supportingKey: const ValueKey('this-phone-reply-speed-detail'),
      supportingMaxLines: 2,
    );
  }

  /// Performance, technical and folded under Details: how proot runs the
  /// server, whether the phone is kept awake for a reply, and the last
  /// reply's first-words wait split between the app and the server.
  List<KitTechnicalValue> _performanceValues(AppLocalizations l10n) {
    if (_host.kind != PhoneHostKind.inApp) return const [];
    final performance = _performance;
    final last = _replies.lastInApp;
    final first = last?.firstToken;
    final server = last?.serverFirstToken;
    return [
      if (performance != null) ...[
        KitTechnicalValue(
          l10n.perfDetailLinuxMode,
          switch (performance.prootMode) {
            BuiltinProotMode.seccomp => l10n.perfLinuxModeFast,
            BuiltinProotMode.ptrace => l10n.perfLinuxModeSlow,
            BuiltinProotMode.unknown => l10n.perfLinuxModeUnknown,
          },
          key: const ValueKey('this-phone-perf-mode'),
          copyable: false,
        ),
        KitTechnicalValue(
          l10n.perfDetailAwake,
          performance.workHeld ? l10n.perfAwakeNow : l10n.perfAwakeWhenWorking,
          key: const ValueKey('this-phone-perf-awake'),
          copyable: false,
        ),
      ],
      if (first != null)
        KitTechnicalValue(
          l10n.perfDetailFirstWords,
          server == null
              ? _seconds(l10n, first)
              : l10n.perfFirstWordsSplit(
                  _seconds(l10n, first),
                  _seconds(l10n, server),
                ),
          key: const ValueKey('this-phone-perf-first-words'),
        ),
      if (last?.model case final model?)
        KitTechnicalValue(
          l10n.perfDetailModel,
          model,
          key: const ValueKey('this-phone-perf-model'),
        ),
    ];
  }

  /// What is installed on this phone, folded: the parts setup put there,
  /// on either host. What every setup installs, then the optional tools the
  /// host's checks find (Python, AI Team, voice typing and more).
  Widget _installed(BuildContext context, AppLocalizations l10n) {
    final names = <String>[];
    final engine = _hostEngine();
    final optional = _installedOptional ?? const <String>{};
    for (final component
        in engine == null
            ? const <SetupComponent>[]
            : installableComponents(engine.registry)) {
      if (component.required || optional.contains(component.id)) {
        names.add(component.shortTitle);
      }
    }
    if (names.isEmpty) {
      names.add(l10n.phoneSetupLinuxTitle);
      if (_host.kind == PhoneHostKind.termux) {
        names.add(_runtimeName(_host.runtime));
      }
    }
    return KitExpandRow(
      key: const ValueKey('this-phone-installed'),
      headerKey: const ValueKey('this-phone-installed-header'),
      leading: KitRow.icon(context, AppIconography.check),
      title: l10n.thisPhoneInstalled,
      supporting: TextSpan(text: joinSetupNames(l10n, names)),
      supportingMaxLines: 2,
      children: [
        for (final name in names)
          KitRow(
            title: name,
            leading: const KitStatusMark(state: KitMarkState.done),
          ),
      ],
    );
  }
}
