/// "Claude Code on this phone": the block in the On this phone wizard that
/// installs and runs the Paseo daemon and Claude Code in the app-managed
/// Ubuntu, walks the person through Claude's own sign-in, and connects.
///
/// It follows the AI Team phone onboarding block: offer, then steps with
/// progress and the live output, then success, failure or "Android stopped
/// it". Everything reads and drives [LocalAgentRuntime]; the script's state
/// is the truth, so leaving the screen and coming back resumes from
/// `claude.sh status`.
///
/// The app never asks for, sees or stores an Anthropic credential. Sign-in
/// happens in a Termux terminal, in Claude Code's own flow.
library;

import 'dart:async';

import 'package:flutter/material.dart';

import '../../builtin/builtin_linux.dart';
import '../../l10n/app_localizations.dart';
import '../../platform/platform_capabilities.dart';
import '../../state/connection.dart';
import '../../state/local_agent_server.dart';
import '../../termux/bridge.dart';
import '../../termux/local_agent_runtime.dart';
import '../app_theme.dart';
import '../kit/kit.dart';
import 'product_states.dart' show connectionErrorText, productErrorText;
import 'safety_confirms.dart';
import 'setup_terminal.dart';

AppLocalizations _copy(BuildContext context) =>
    lookupAppLocalizations(Localizations.localeOf(context));

LocalAgentRuntime? _sharedRuntime;

/// The runtime the Claude Code widgets share.
LocalAgentRuntime get localAgentRuntime =>
    debugLocalAgentRuntime ?? (_sharedRuntime ??= LocalAgentRuntime());

/// Tests swap the shared runtime for a fake; reset to null in `addTearDown`.
@visibleForTesting
LocalAgentRuntime? debugLocalAgentRuntime;

/// How often the steps view re-reads status and the log while a verb runs.
const localAgentPollInterval = Duration(seconds: 2);

/// Where "Not now" on the offer is remembered.
const localAgentOfferSkippedKey = 'local_agents_offer_skipped';

/// The five visible steps.
enum LocalAgentUiStep { node, paseo, claude, signIn, start }

enum LocalAgentUiStepState { idle, running, done, error }

/// Which step a failed [status] belongs to.
LocalAgentUiStep localAgentFailedStep(LocalAgentStatus status) {
  switch (status.failureKind) {
    case LocalAgentFailureKind.portInUse:
    case LocalAgentFailureKind.timeout:
    case LocalAgentFailureKind.daemon:
    case LocalAgentFailureKind.notInstalled:
      return LocalAgentUiStep.start;
    case LocalAgentFailureKind.download:
    case LocalAgentFailureKind.checksum:
    case LocalAgentFailureKind.noSpace:
    case LocalAgentFailureKind.needsUbuntu:
    case LocalAgentFailureKind.unsupportedArch:
      return LocalAgentUiStep.node;
    default:
      break;
  }
  if (status.verb == 'start' || status.verb == 'restart') {
    return LocalAgentUiStep.start;
  }
  return switch (status.step) {
    LocalAgentStep.paseo => LocalAgentUiStep.paseo,
    LocalAgentStep.claude => LocalAgentUiStep.claude,
    _ => LocalAgentUiStep.node,
  };
}

/// Where each step stands for [status].
Map<LocalAgentUiStep, LocalAgentUiStepState> localAgentStepStates(
  LocalAgentStatus status,
) {
  final states = {
    for (final step in LocalAgentUiStep.values)
      step: LocalAgentUiStepState.idle,
  };
  void doneBefore(LocalAgentUiStep step) {
    for (final other in LocalAgentUiStep.values) {
      if (other.index < step.index) states[other] = LocalAgentUiStepState.done;
    }
  }

  void running(LocalAgentUiStep step) {
    doneBefore(step);
    states[step] = LocalAgentUiStepState.running;
  }

  final signedIn = status.signedIn == LocalAgentSignIn.yes;
  switch (status.phase) {
    case LocalAgentPhase.installing:
      running(switch (status.step) {
        LocalAgentStep.paseo => LocalAgentUiStep.paseo,
        LocalAgentStep.claude => LocalAgentUiStep.claude,
        _ => LocalAgentUiStep.node,
      });
    case LocalAgentPhase.installed:
    case LocalAgentPhase.stopping:
      doneBefore(LocalAgentUiStep.signIn);
      if (signedIn) {
        states[LocalAgentUiStep.signIn] = LocalAgentUiStepState.done;
      }
    case LocalAgentPhase.starting:
      running(LocalAgentUiStep.start);
    case LocalAgentPhase.ready:
      for (final step in LocalAgentUiStep.values) {
        states[step] = LocalAgentUiStepState.done;
      }
    case LocalAgentPhase.failed:
      final step = localAgentFailedStep(status);
      doneBefore(step);
      states[step] = LocalAgentUiStepState.error;
    case LocalAgentPhase.absent:
    case LocalAgentPhase.needsUbuntu:
    case LocalAgentPhase.removing:
    case LocalAgentPhase.unknown:
      break;
  }
  return states;
}

/// The honest sentence for a failure.
String localAgentFailureText(
  AppLocalizations l10n,
  LocalAgentFailureKind kind,
  String message,
) {
  switch (kind) {
    case LocalAgentFailureKind.needsUbuntu:
      return l10n.localAgentNeedsUbuntuBody;
    case LocalAgentFailureKind.noSpace:
      // The script's sentence ends "…: 900 MB free, 1536 MB needed".
      final detail = message.contains(':')
          ? message.split(':').last.trim()
          : '';
      return l10n.localAgentFailedNoSpace(detail.isEmpty ? '' : '$detail.');
    case LocalAgentFailureKind.download:
      return l10n.localAgentFailedDownload;
    case LocalAgentFailureKind.checksum:
      return l10n.localAgentFailedChecksum;
    case LocalAgentFailureKind.nativeBuild:
      return l10n.localAgentFailedNativeBuild;
    case LocalAgentFailureKind.packages:
      return l10n.localAgentFailedPackages;
    case LocalAgentFailureKind.portInUse:
      return l10n.localAgentFailedPortInUse;
    case LocalAgentFailureKind.timeout:
      return l10n.localAgentFailedTimeout;
    case LocalAgentFailureKind.interrupted:
      return l10n.localAgentFailedInterrupted;
    case LocalAgentFailureKind.unsupportedArch:
      return l10n.localAgentFailedUnsupported;
    case LocalAgentFailureKind.daemon:
      return l10n.localAgentFailedDaemon;
    case LocalAgentFailureKind.bridge:
    case LocalAgentFailureKind.notInstalled:
    case LocalAgentFailureKind.other:
      // The script's own sentence when it is one; its output or a native
      // message is said in words (the raw text is for Details only).
      return l10n.localAgentFailedReason(
        productErrorText(LocalAgentFailure(kind, message), l10n: l10n),
      );
  }
}

enum _View {
  checking,
  hidden,
  offer,
  needsUbuntu,
  steps,
  signIn,
  stopped,
  ready,
  failed,
}

class LocalAgentOnboardingBlock extends StatefulWidget {
  const LocalAgentOnboardingBlock({
    super.key,
    required this.connection,
    required this.onConnected,
    this.onOpenPhoneSetup,
    this.autoStart = false,
    this.runtime,
    this.inAppLinuxProbe,
  });

  final ConnectionController connection;

  /// Called once the app is connected through the daemon.
  final VoidCallback onConnected;

  /// Opens the On this phone wizard when Ubuntu is missing. Null inside the
  /// wizard itself, where the person is already in the right place.
  final VoidCallback? onOpenPhoneSetup;

  /// The person chose "Claude Code" in the wizard's runtime step: skip the
  /// offer and begin installing as soon as Ubuntu is there.
  final bool autoStart;

  final LocalAgentRuntime? runtime;

  /// Whether the app-managed in-app Linux (no Termux) is already installed,
  /// so a missing Termux Ubuntu gets the honest "needs Termux" copy instead
  /// of the generic "finish setup" one. Defaults to a real
  /// [BuiltinLinux.status] read; tests inject a fake.
  final Future<bool> Function()? inAppLinuxProbe;

  @override
  State<LocalAgentOnboardingBlock> createState() =>
      _LocalAgentOnboardingBlockState();
}

class _LocalAgentOnboardingBlockState extends State<LocalAgentOnboardingBlock>
    with WidgetsBindingObserver {
  LocalAgentRuntime get _runtime => widget.runtime ?? localAgentRuntime;

  _View _view = _View.checking;
  LocalAgentStatus _status = const LocalAgentStatus(
    phase: LocalAgentPhase.unknown,
  );
  LocalAgentFailure? _failure;
  String _log = '';
  String? _notice;

  /// Set once [_View.needsUbuntu] is reached: whether the in-app Linux is
  /// already installed, so Claude Code's only path left is Termux.
  bool _needsTermuxOnly = false;
  bool _busy = false;
  bool _connecting = false;
  bool _awaitingSignIn = false;
  bool _autoStarted = false;
  Timer? _poll;
  final ScrollController _logController = ScrollController();

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    unawaited(_load());
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _poll?.cancel();
    _logController.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    // Coming back from Termux: the sign-in either landed or it did not.
    if (state == AppLifecycleState.resumed && _awaitingSignIn && !_busy) {
      unawaited(_refresh(afterSignIn: true));
    }
  }

  bool get _offerSkipped =>
      widget.connection.store.prefs.getBool(localAgentOfferSkippedKey) ?? false;

  Future<void> _load() async {
    if (!platformCapabilities.supportsTermux) {
      if (mounted) setState(() => _view = _View.hidden);
      return;
    }
    await _refresh();
    if (!mounted) return;
    if (widget.autoStart &&
        !_autoStarted &&
        _status.phase == LocalAgentPhase.absent) {
      _autoStarted = true;
      await _install();
    }
  }

  Future<void> _refresh({bool afterSignIn = false}) async {
    LocalAgentStatus status;
    try {
      status = await _runtime.status();
    } on LocalAgentFailure {
      // Termux missing or not permitted: the wizard above says so already.
      if (mounted && _view == _View.checking) {
        setState(() => _view = _View.hidden);
      }
      return;
    }
    if (!mounted) return;
    if (afterSignIn) {
      _awaitingSignIn = false;
      _notice = status.signedIn == LocalAgentSignIn.yes
          ? null
          : _copy(context).localAgentSignInMissing;
    }
    _apply(status);
    if (status.busy) {
      _log = await _runtime.logTail();
      if (mounted) setState(_startPolling);
    } else if (status.phase == LocalAgentPhase.failed) {
      await _refreshLog();
    }
  }

  void _apply(LocalAgentStatus status) {
    _status = status;
    final _View next;
    if (status.busy) {
      next = _View.steps;
    } else {
      switch (status.phase) {
        case LocalAgentPhase.absent:
          next = widget.autoStart || !_offerSkipped
              ? _View.offer
              : _View.hidden;
        case LocalAgentPhase.needsUbuntu:
          next = _View.needsUbuntu;
        case LocalAgentPhase.installed:
          next = status.signedIn == LocalAgentSignIn.no
              ? _View.signIn
              : _View.stopped;
        case LocalAgentPhase.ready:
          next = _View.ready;
        case LocalAgentPhase.failed:
          _failure = status.failure;
          next = status.failureKind == LocalAgentFailureKind.needsUbuntu
              ? _View.needsUbuntu
              : _View.failed;
        case LocalAgentPhase.unknown:
          next = _View.hidden;
        default:
          next = _View.steps;
      }
    }
    if (next == _View.needsUbuntu) unawaited(_probeInAppLinuxOnly());
    if (mounted) setState(() => _view = next);
  }

  Future<void> _probeInAppLinuxOnly() async {
    bool needsTermuxOnly;
    try {
      needsTermuxOnly =
          await (widget.inAppLinuxProbe ?? _defaultInAppLinuxProbe)();
    } catch (_) {
      // Unreadable: the generic "finish setup" copy is still true (Termux
      // Ubuntu really is missing), just not as precise.
      needsTermuxOnly = false;
    }
    if (mounted) setState(() => _needsTermuxOnly = needsTermuxOnly);
  }

  static Future<bool> _defaultInAppLinuxProbe() async =>
      (await BuiltinLinux().status()).installed;

  void _startPolling() {
    _poll?.cancel();
    _poll = Timer.periodic(localAgentPollInterval, (_) => _tick());
  }

  void _stopPolling() {
    _poll?.cancel();
    _poll = null;
  }

  Future<void> _tick() async {
    try {
      final status = await _runtime.status();
      final log = await _runtime.logTail();
      if (!mounted) return;
      _appendLog(log);
      if (_busy) {
        // The verb below owns the view; the tick feeds the steps and panel.
        setState(() => _status = status);
        return;
      }
      if (!status.busy) _stopPolling();
      _apply(status);
    } on LocalAgentFailure {
      // Keep polling: the bridge answers again once Termux is back.
    }
  }

  void _appendLog(String log) {
    if (log == _log) return;
    final follow =
        !_logController.hasClients ||
        _logController.position.maxScrollExtent -
                _logController.position.pixels <
            48;
    _log = log;
    if (follow) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted || !_logController.hasClients) return;
        _logController.jumpTo(_logController.position.maxScrollExtent);
      });
    }
  }

  Future<void> _refreshLog() async {
    final log = await _runtime.logTail();
    if (mounted) setState(() => _appendLog(log));
  }

  /// Runs one long verb with the steps view on screen.
  Future<LocalAgentStatus?> _verb(
    LocalAgentPhase optimistic,
    String verb,
    Future<LocalAgentStatus> Function() action,
  ) async {
    setState(() {
      _busy = true;
      _failure = null;
      _notice = null;
      _view = _View.steps;
      _status = LocalAgentStatus(
        phase: optimistic,
        busy: true,
        verb: verb,
        installed: _status.installed,
        signedIn: _status.signedIn,
        step: optimistic == LocalAgentPhase.installing
            ? LocalAgentStep.node
            : null,
      );
      _startPolling();
    });
    try {
      final status = await action();
      if (!mounted) return null;
      _stopPolling();
      _busy = false;
      return status;
    } on LocalAgentFailure catch (failure) {
      if (!mounted) return null;
      _stopPolling();
      _busy = false;
      // Re-read so the step list shows where it stopped; a bridge failure
      // has no status behind it and keeps the optimistic one.
      try {
        _status = await _runtime.status();
      } on LocalAgentFailure {
        // Keep what is on screen.
      }
      if (!mounted) return null;
      setState(() {
        _failure = failure;
        _view = failure.kind == LocalAgentFailureKind.needsUbuntu
            ? _View.needsUbuntu
            : _View.failed;
      });
      unawaited(_refreshLog());
      return null;
    }
  }

  Future<void> _install() async {
    final status = await _verb(
      LocalAgentPhase.installing,
      'install',
      _runtime.install,
    );
    if (status == null || !mounted) return;
    _apply(status);
    // Signed in already (a reinstall): go straight on to starting it.
    if (status.phase == LocalAgentPhase.installed &&
        status.signedIn == LocalAgentSignIn.yes) {
      await _start();
    }
  }

  /// Whether the installed Paseo is older than the one this build pins.
  /// Paseo carries the list of Claude models the daemon offers, so a new
  /// model (Opus 5.5 needs 0.9.1) arrives with an update.
  bool get _updateAvailable {
    final installed = _status.paseoVersion;
    final pinned = TermuxBridge.localAgentsPins['paseo_version'];
    return _status.installed &&
        installed.isNotEmpty &&
        pinned != null &&
        installed != pinned;
  }

  /// Installs the pinned Paseo and the newest Claude Code over the existing
  /// install, then starts it again. Sign-in and projects are kept.
  Future<void> _update() async {
    if (_status.phase == LocalAgentPhase.ready) {
      final stopped = await _verb(
        LocalAgentPhase.stopping,
        'stop',
        _runtime.stop,
      );
      if (stopped == null || !mounted) return;
      _apply(stopped);
    }
    await _install();
  }

  Future<void> _start() async {
    final status = await _verb(
      LocalAgentPhase.starting,
      'start',
      _runtime.start,
    );
    if (status != null && mounted) _apply(status);
  }

  Future<void> _retry() async {
    final failure = _failure;
    final step = failure == null ? null : localAgentFailedStep(_status);
    if (step == LocalAgentUiStep.start && _status.installed) {
      await _start();
    } else {
      await _install();
    }
  }

  Future<void> _skip() async {
    await widget.connection.store.prefs.setBool(
      localAgentOfferSkippedKey,
      true,
    );
    if (mounted) setState(() => _view = _View.hidden);
  }

  Future<void> _signIn() async {
    final l10n = _copy(context);
    setState(() {
      _busy = true;
      _notice = null;
    });
    var opened = false;
    String? problem;
    try {
      opened = await _runtime.openSignIn();
    } on LocalAgentFailure catch (failure) {
      problem = productErrorText(failure, l10n: l10n);
    }
    if (!mounted) return;
    setState(() {
      _busy = false;
      _awaitingSignIn = opened;
      if (!opened) {
        _notice =
            problem ??
            l10n.localAgentSignInOpenFailed(
              TermuxBridge.localAgentsSignInCommand,
            );
      }
    });
  }

  Future<void> _remove() async {
    if (!await confirmRemoveLocalAgents(context) || !mounted) return;
    final status = await _verb(
      LocalAgentPhase.removing,
      'remove',
      _runtime.remove,
    );
    if (status != null && mounted) _apply(status);
  }

  Future<void> _connect() async {
    if (_connecting) return;
    final l10n = _copy(context);
    final connection = widget.connection;
    final saved = savedLocalAgentProfile(connection.store.profiles);
    // Already connected through it: there is nothing to choose.
    if (saved != null &&
        connection.profile?.id == saved.id &&
        connection.hasConnectedServer) {
      widget.onConnected();
      return;
    }
    final directory = await showLocalAgentProjectSheet(
      context,
      runtime: _runtime,
      initial: saved?.codexDirectory,
    );
    if (directory == null || !mounted) return;
    setState(() {
      _connecting = true;
      _notice = null;
    });
    try {
      final profile = await saveLocalAgentProfile(
        store: connection.store,
        runtime: _runtime,
        name: l10n.localAgentTitle,
        directory: directory,
      );
      await connection.connect(profile);
      if (!mounted) return;
      if (connection.profile?.id == profile.id &&
          connection.hasConnectedServer) {
        setState(() => _connecting = false);
        widget.onConnected();
        return;
      }
      setState(() {
        _connecting = false;
        _notice = l10n.localAgentConnectFailed(
          connectionErrorText(connection, l10n: l10n) ?? '',
        );
      });
    } on LocalAgentFailure catch (failure) {
      if (!mounted) return;
      setState(() {
        _connecting = false;
        _notice = l10n.localAgentConnectFailed(
          productErrorText(failure, l10n: l10n),
        );
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return switch (_view) {
      _View.checking || _View.hidden => const SizedBox.shrink(),
      _View.offer => _offer(context),
      _View.needsUbuntu => _needsUbuntu(context),
      _View.steps => _steps(context),
      _View.signIn => _signInView(context),
      _View.stopped => _stopped(context),
      _View.ready => _ready(context),
      _View.failed => _failed(context),
    };
  }

  /// The block's one panel (slice-P9.10: kit parts only, `KitSurface.panel`
  /// in place of the hand-drawn bordered box).
  Widget _card(
    BuildContext context, {
    required Key key,
    required List<Widget> children,
  }) => SizedBox(
    key: key,
    width: double.infinity,
    child: KitSurface.panel(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: children,
      ),
    ),
  );

  Widget _title(BuildContext context, {bool menu = false}) {
    final l10n = _copy(context);
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Expanded(
          child: KitText(
            l10n.localAgentTitle,
            key: const ValueKey('local-agent-title'),
            role: KitTextRole.headline,
          ),
        ),
        if (menu)
          KitRowMenu(
            key: const ValueKey('local-agent-menu'),
            tooltip: l10n.localAgentMore,
            enabled: !_busy && !_connecting,
            items: [
              KitMenuItem(
                key: const ValueKey('local-agent-refresh'),
                label: l10n.workRefresh,
                onSelected: () => unawaited(_refresh()),
              ),
              KitMenuItem(
                key: const ValueKey('local-agent-update'),
                label: l10n.localAgentUpdate,
                onSelected: () => unawaited(_update()),
              ),
              KitMenuItem(
                key: const ValueKey('local-agent-remove'),
                label: l10n.localAgentRemove,
                destructive: true,
                onSelected: () => unawaited(_remove()),
              ),
            ],
          ),
      ],
    );
  }

  /// A muted line under the title.
  Widget _muted(String text, {Key? key}) =>
      KitText(text, key: key, role: KitTextRole.secondary);

  Widget _gap(BuildContext context, double Function(KitTokens) size) =>
      SizedBox(height: size(KitTokens.of(context)));

  Widget _noticeText(BuildContext context) => Padding(
    padding: EdgeInsetsDirectional.only(top: KitTokens.of(context).space3),
    child: KitNotice(
      message: _notice!,
      messageKey: const ValueKey('local-agent-notice'),
      tone: AppStatusTone.failure,
    ),
  );

  Widget _actions(
    BuildContext context, {
    KitAction? primary,
    KitAction? secondary,
    List<KitAction> tertiary = const [],
  }) => Padding(
    padding: EdgeInsetsDirectional.only(top: KitTokens.of(context).space4),
    child: KitActionBlock(
      primary: primary,
      secondary: secondary,
      tertiary: tertiary,
    ),
  );

  Widget _offer(BuildContext context) {
    final l10n = _copy(context);
    return _card(
      context,
      key: const ValueKey('local-agent-offer'),
      children: [
        KitText(l10n.teamUiPhoneOptionalTag, role: KitTextRole.label),
        _gap(context, (t) => t.space1),
        _title(context),
        _gap(context, (t) => t.space2),
        KitText(l10n.localAgentOfferBody),
        _gap(context, (t) => t.space1),
        _muted(
          l10n.localAgentOfferSize,
          key: const ValueKey('local-agent-offer-size'),
        ),
        _gap(context, (t) => t.space3),
        KitNotice(
          message: l10n.localAgentOfferWarning,
          icon: AppIconography.warning,
          liveRegion: false,
        ),
        _actions(
          context,
          primary: KitAction(
            key: const ValueKey('local-agent-set-up'),
            label: l10n.localAgentSetUp,
            onPressed: _busy ? null : _install,
          ),
          secondary: KitAction(
            key: const ValueKey('local-agent-skip'),
            label: l10n.localAgentNotNow,
            onPressed: _busy ? null : _skip,
          ),
        ),
      ],
    );
  }

  Widget _needsUbuntu(BuildContext context) {
    final l10n = _copy(context);
    final open = widget.onOpenPhoneSetup;
    // Never Refresh alone: the in-app Linux existing means Termux, not a
    // second "On this phone" run, is the only door left to Claude Code.
    final body = _needsTermuxOnly
        ? l10n.localAgentNeedsTermuxBody
        : l10n.localAgentNeedsUbuntuBody;
    final openLabel = _needsTermuxOnly
        ? l10n.localAgentSetUpWithTermux
        : l10n.localAgentOpenSetup;
    return _card(
      context,
      key: const ValueKey('local-agent-needs-ubuntu'),
      children: [
        _title(context),
        _gap(context, (t) => t.space2),
        KitNotice(
          message: body,
          messageKey: const ValueKey('local-agent-needs-ubuntu-body'),
          icon: AppIconography.warning,
          actions: [
            KitAction(
              key: const ValueKey('local-agent-needs-ubuntu-refresh'),
              label: l10n.workRefresh,
              onPressed: () => unawaited(_refresh()),
            ),
            if (open != null)
              KitAction(
                key: const ValueKey('local-agent-open-setup'),
                label: openLabel,
                onPressed: open,
              ),
          ],
        ),
      ],
    );
  }

  /// The five steps as the kit's one step list ([KitChecklist]); each row
  /// keeps its `local-agent-step-<name>` key.
  Widget _stepList(BuildContext context) {
    final l10n = _copy(context);
    final states = localAgentStepStates(_status);
    final titles = {
      LocalAgentUiStep.node: l10n.localAgentStepNode,
      LocalAgentUiStep.paseo: l10n.localAgentStepPaseo,
      LocalAgentUiStep.claude: l10n.localAgentStepClaude,
      LocalAgentUiStep.signIn: l10n.localAgentStepSignIn,
      LocalAgentUiStep.start: l10n.localAgentStepStart,
    };
    return KitChecklist(
      checklistKey: const ValueKey('local-agent-steps'),
      steps: [
        for (final step in LocalAgentUiStep.values)
          KitStep(
            key: ValueKey('local-agent-step-${step.name}'),
            title: titles[step]!,
            state: switch (states[step]!) {
              LocalAgentUiStepState.idle => KitMarkState.waiting,
              LocalAgentUiStepState.running => KitMarkState.working,
              LocalAgentUiStepState.done => KitMarkState.done,
              LocalAgentUiStepState.error => KitMarkState.failed,
            },
          ),
      ],
    );
  }

  Widget _steps(BuildContext context) {
    final l10n = _copy(context);
    return _card(
      context,
      key: const ValueKey('local-agent-setup'),
      children: [
        _title(context),
        _gap(context, (t) => t.space1),
        _muted(l10n.localAgentInstalling),
        _gap(context, (t) => t.space3),
        _stepList(context),
        _gap(context, (t) => t.space3),
        SetupTerminal(
          output: _log,
          running: _busy || _status.busy,
          controller: _logController,
        ),
        _gap(context, (t) => t.space2),
        _muted(
          l10n.localAgentLeaveNote,
          key: const ValueKey('local-agent-leave-note'),
        ),
      ],
    );
  }

  Widget _signInView(BuildContext context) {
    final l10n = _copy(context);
    return _card(
      context,
      key: const ValueKey('local-agent-sign-in'),
      children: [
        _title(context, menu: true),
        _gap(context, (t) => t.space3),
        _stepList(context),
        _gap(context, (t) => t.space3),
        KitText(
          l10n.localAgentSignInBody,
          key: const ValueKey('local-agent-sign-in-body'),
        ),
        if (_notice != null) _noticeText(context),
        _actions(
          context,
          primary: KitAction(
            key: const ValueKey('local-agent-sign-in-open'),
            label: l10n.localAgentStepSignIn,
            onPressed: _busy ? null : _signIn,
          ),
          secondary: KitAction(
            key: const ValueKey('local-agent-sign-in-refresh'),
            label: l10n.workRefresh,
            onPressed: _busy
                ? null
                : () => unawaited(_refresh(afterSignIn: true)),
          ),
          tertiary: [
            KitAction(
              key: const ValueKey('local-agent-sign-in-already'),
              label: l10n.localAgentSignInAlready,
              onPressed: _busy ? null : _start,
            ),
          ],
        ),
      ],
    );
  }

  Widget _versions(BuildContext context) {
    final l10n = _copy(context);
    final status = _status;
    if (status.claudeVersion.isEmpty) return const SizedBox.shrink();
    return Padding(
      padding: EdgeInsetsDirectional.only(top: KitTokens.of(context).space1),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: [
          KitLtr(
            child: _muted(
              l10n.localAgentVersions(
                status.claudeVersion,
                status.paseoVersion,
                status.nodeVersion,
              ),
              key: const ValueKey('local-agent-versions'),
            ),
          ),
          if (_updateAvailable)
            Padding(
              padding: EdgeInsetsDirectional.only(
                top: KitTokens.of(context).space2,
              ),
              child: KitNotice(
                message: l10n.localAgentUpdateAvailable,
                messageKey: const ValueKey('local-agent-update-available'),
                icon: AppIconography.sparkle,
                liveRegion: false,
                actions: [
                  KitAction(
                    key: const ValueKey('local-agent-update-now'),
                    label: l10n.localAgentUpdateNow,
                    onPressed: _busy ? null : () => unawaited(_update()),
                  ),
                ],
              ),
            ),
        ],
      ),
    );
  }

  Widget _stopped(BuildContext context) {
    final l10n = _copy(context);
    return _card(
      context,
      key: const ValueKey('local-agent-stopped'),
      children: [
        _title(context, menu: true),
        _gap(context, (t) => t.space2),
        if (_status.killedByAndroid)
          KitNotice(
            message: l10n.localAgentKilled,
            messageKey: const ValueKey('local-agent-stopped-text'),
            icon: AppIconography.warning,
          )
        else
          KitText(
            l10n.localAgentInstalledTitle,
            key: const ValueKey('local-agent-stopped-text'),
          ),
        _versions(context),
        if (_notice != null) _noticeText(context),
        _actions(
          context,
          primary: KitAction(
            key: const ValueKey('local-agent-start'),
            label: l10n.phoneServerStart,
            icon: AppIconography.play,
            onPressed: _busy ? null : _start,
          ),
        ),
      ],
    );
  }

  Widget _ready(BuildContext context) {
    final l10n = _copy(context);
    final saved = savedLocalAgentProfile(widget.connection.store.profiles);
    final connected =
        saved != null &&
        widget.connection.profile?.id == saved.id &&
        widget.connection.hasConnectedServer;
    return _card(
      context,
      key: const ValueKey('local-agent-ready'),
      children: [
        _title(context, menu: true),
        _gap(context, (t) => t.space2),
        Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const KitIcon.status(AppStatusTone.ok),
            SizedBox(width: KitTokens.of(context).space2),
            Expanded(
              child: KitText(
                l10n.localAgentReadyTitle,
                role: KitTextRole.rowTitle,
              ),
            ),
          ],
        ),
        _gap(context, (t) => t.space1),
        _muted(l10n.localAgentReadyBody),
        _versions(context),
        if (_notice != null) _noticeText(context),
        _actions(
          context,
          // Connecting is this button's own tap in flight (STATE-7): its
          // spinner, not a separate bar.
          primary: KitAction(
            key: const ValueKey('local-agent-connect'),
            label: connected ? l10n.phoneServerOpen : l10n.phoneServerConnect,
            icon: AppIconography.forward,
            working: _connecting,
            onPressed: _busy || _connecting ? null : _connect,
          ),
        ),
      ],
    );
  }

  Widget _failed(BuildContext context) {
    final l10n = _copy(context);
    final failure =
        _failure ??
        _status.failure ??
        const LocalAgentFailure(LocalAgentFailureKind.other, '');
    return _card(
      context,
      key: const ValueKey('local-agent-failed'),
      children: [
        _title(context, menu: _status.installed),
        _gap(context, (t) => t.space1),
        KitText(
          l10n.localAgentFailedTitle,
          role: KitTextRole.rowTitle,
          tone: KitTextTone.danger,
        ),
        _gap(context, (t) => t.space2),
        KitText(
          localAgentFailureText(l10n, failure.kind, failure.message),
          key: const ValueKey('local-agent-failed-reason'),
        ),
        _gap(context, (t) => t.space3),
        _stepList(context),
        _gap(context, (t) => t.space3),
        SetupTerminal(output: _log, running: false, controller: _logController),
        _actions(
          context,
          primary: KitAction(
            key: const ValueKey('local-agent-retry'),
            label: l10n.teamUiPhoneRetry,
            icon: AppIconography.retry,
            onPressed: _busy ? null : _retry,
          ),
        ),
      ],
    );
  }
}

/// Picks the project folder Claude Code works in, on the kit's one sheet
/// frame ([showKitSheet]): one of the folders under `~/projects` in Ubuntu
/// (the script creates `my-first-project` when there are none) or a typed
/// path. Returns the path as Ubuntu sees it, or null when closed.
///
/// [initial] is the folder the saved server already uses, preselected when
/// listed.
Future<String?> showLocalAgentProjectSheet(
  BuildContext context, {
  required LocalAgentRuntime runtime,
  String? initial,
}) async {
  final l10n = _copy(context);
  final pick = _ProjectPick(runtime, initial);
  final primary = ValueNotifier<KitAction?>(null);
  NavigatorState? navigator;
  var open = true;

  Future<void> continueWith() async {
    final path = await pick.resolve(l10n);
    if (path != null && open) navigator?.pop(path);
  }

  void publish() {
    primary.value = KitAction(
      key: const ValueKey('local-agent-project-continue'),
      label: l10n.teamUiPhoneContinue,
      working: pick.working,
      onPressed: pick.canContinue ? () => unawaited(continueWith()) : null,
    );
  }

  pick.addListener(publish);
  publish();
  unawaited(pick.load());
  try {
    return await showKitSheet<String>(
      context,
      sheetKey: const ValueKey('local-agent-project-sheet'),
      title: l10n.localAgentProjectTitle,
      subtitle: l10n.localAgentProjectBody,
      primaryListenable: primary,
      body: (sheetContext) {
        navigator = Navigator.of(sheetContext);
        return ListenableBuilder(
          listenable: pick,
          builder: (context, _) =>
              _LocalAgentProjectBody(pick: pick, onSubmit: continueWith),
        );
      },
    );
  } finally {
    open = false;
    pick.removeListener(publish);
    primary.dispose();
    pick.dispose();
  }
}

/// What the project sheet holds while it is open.
class _ProjectPick extends ChangeNotifier {
  _ProjectPick(this.runtime, this.initial);

  final LocalAgentRuntime runtime;
  final String? initial;
  final path = TextEditingController();

  List<String>? projects;
  String? selected;
  String? problem;
  bool working = false;
  bool _disposed = false;

  bool get canContinue =>
      !working &&
      projects != null &&
      (selected != null || path.text.trim().isNotEmpty);

  /// The listed folder in use: none while a path is typed.
  String? get chosen => path.text.trim().isEmpty ? selected : null;

  void _changed() {
    if (!_disposed) notifyListeners();
  }

  Future<void> load() async {
    List<String> found;
    try {
      found = await runtime.projects();
    } on LocalAgentFailure {
      found = const [];
    }
    if (_disposed) return;
    projects = found;
    final initial = this.initial;
    if (initial != null && found.contains(initial)) {
      selected = initial;
    } else if (found.isNotEmpty) {
      // A typed path wins over the list on Continue, so a remembered folder
      // that is no longer offered (seen live: `/root` from an older setup,
      // outside the agent user's home) must not be pre-typed over it.
      selected = found.first;
    } else if (initial != null && initial.isNotEmpty) {
      path.text = initial;
    }
    _changed();
  }

  void select(String value) {
    selected = value;
    path.clear();
    problem = null;
    _changed();
  }

  void typed() {
    problem = null;
    _changed();
  }

  /// The folder to use, made when typed; null (with [problem] set) when it
  /// cannot be used.
  Future<String?> resolve(AppLocalizations l10n) async {
    if (!canContinue) return null;
    final typed = path.text.trim();
    if (typed.isEmpty) return selected;
    if (localAgentProjectPathProblem(typed) != null) {
      problem = l10n.localAgentProjectPathInvalid;
      _changed();
      return null;
    }
    working = true;
    problem = null;
    _changed();
    try {
      return await runtime.ensureProject(typed);
    } on LocalAgentFailure catch (failure) {
      problem = productErrorText(failure, l10n: l10n);
      return null;
    } finally {
      working = false;
      _changed();
    }
  }

  @override
  void dispose() {
    _disposed = true;
    path.dispose();
    super.dispose();
  }
}

/// The project sheet's body: the listed folders, then a path field.
class _LocalAgentProjectBody extends StatelessWidget {
  const _LocalAgentProjectBody({required this.pick, required this.onSubmit});

  final _ProjectPick pick;
  final Future<void> Function() onSubmit;

  @override
  Widget build(BuildContext context) {
    final l10n = _copy(context);
    final tokens = KitTokens.of(context);
    final projects = pick.projects;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        if (projects == null || projects.isNotEmpty) ...[
          KitChoiceList<String>.single(
            loading: projects == null,
            actsOnTap: false,
            semanticsLabel: l10n.localAgentProjectTitle,
            selected: pick.chosen,
            onSelected: pick.select,
            choices: [
              for (var i = 0; i < (projects ?? const <String>[]).length; i++)
                KitChoice(
                  key: ValueKey('local-agent-project-$i'),
                  value: projects![i],
                  title: projects[i].split('/').last,
                  supporting: projects[i],
                ),
            ],
          ),
          SizedBox(height: tokens.space3),
        ],
        KitField(
          label: l10n.localAgentProjectPathLabel,
          controller: pick.path,
          kind: KitFieldKind.path,
          error: pick.problem,
          fieldKey: const ValueKey('local-agent-project-path'),
          onChanged: (_) => pick.typed(),
          onSubmitted: (_) => unawaited(onSubmit()),
        ),
      ],
    );
  }
}
