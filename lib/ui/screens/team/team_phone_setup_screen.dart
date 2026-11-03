/// "Turn on AI Team on this phone": the one-tap setup page.
///
/// Finish line: the person taps once and watches four real steps, each
/// with its own time and a tick: the reply finishes, OpenCode and the
/// terminals stop (only after a yes, in one line), the engine proves this
/// phone safe, OpenCode comes back protected. A failure says what happened
/// in plain words, keeps its technical text under Details and offers Start
/// again. Non-goal: the engine's own checks; this page only shows them.
library;

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../builtin/builtin_server.dart'
    show BuiltinServerStarter, builtinServerStarterProvider;
import '../../../l10n/app_localizations.dart';
import '../../../state/connection.dart';
import '../../../state/phone_team_setup.dart';
import '../../../termux/bridge.dart' show TermuxBridge;
import '../../app_theme.dart';
import '../../kit/kit.dart';
import '../../kit/scenes/team_scenes.dart';
import 'team_page.dart' show TeamPage;

/// Opens the setup page. [autoStart] starts the run at once (the tap that
/// brought the person here was the start); the Work strip opens it without,
/// to show a run already going.
Future<void> openPhoneTeamSetup(
  BuildContext context,
  ConnectionController connection, {
  PhoneTeamSetupController? controller,
  bool autoStart = true,
}) => pushKitPage<void>(
  context,
  (_) => TeamPhoneSetupScreen(
    connection: connection,
    controller: controller,
    autoStart: autoStart,
  ),
);

/// Every UI entry uses the same server owner as the shell and recovery banner.
PhoneTeamSetupController phoneTeamSetupForContext(
  BuildContext context,
  ConnectionController connection,
) {
  BuiltinServerStarter? starter;
  try {
    starter = ProviderScope.containerOf(
      context,
      listen: false,
    ).read(builtinServerStarterProvider);
  } on StateError {
    // Bare widget tests supply debugPorts. Real setup requires the app owner;
    // PhoneTeamSetup.of refuses to allocate an independent starter.
  }
  return PhoneTeamSetup.of(
    connection,
    copy: AppLocalizations.of(context),
    starter: starter,
  );
}

/// The plain title and body of a failure; its technical text is Details.
/// With [flow] the body also says exactly what state the phone is in:
/// whether OpenCode is on again and whether terminals were closed.
({String title, String body}) phoneTeamProblemCopy(
  AppLocalizations l,
  PhoneTeamSetupProblem problem, [
  PhoneTeamSetupController? flow,
]) {
  final base = flow?.needsComponentRecovery == true
      ? (
          title: l.phoneTeamFailServerTitle,
          body: l.inAppServerOwnershipRecoveryBody,
        )
      : _problemCopy(l, problem);
  if (flow == null) return base;
  final state = <String>[
    if (const {
      PhoneTeamSetupProblem.unsafe,
      PhoneTeamSetupProblem.engine,
      PhoneTeamSetupProblem.stopFailed,
    }.contains(problem))
      switch (flow.serverState) {
        PhoneTeamServerState.backOn => l.phoneTeamStateBackOn,
        PhoneTeamServerState.stillOff => l.phoneTeamStateStillOff,
        PhoneTeamServerState.untouched => l.phoneTeamStateNotStopped,
      },
    if (flow.terminalsClosed &&
        problem != PhoneTeamSetupProblem.declined &&
        problem != PhoneTeamSetupProblem.noServer)
      l.phoneTeamStateTerminalsClosed,
  ];
  if (state.isEmpty) return base;
  return (title: base.title, body: '${base.body} ${state.join(' ')}');
}

/// Details: one plain sentence, then the technical code.
/// The one plain line for the boundary the engine proved, or null when the
/// tier is unknown (`none`, empty, anything new).
String? phoneTeamProtectionText(AppLocalizations l, String tier) =>
    switch (tier) {
      'proot' => l.phoneTeamProtectedProot,
      'landlock' => l.phoneTeamProtectedLandlock,
      _ => null,
    };

/// The plain line naming where a failed safety check stopped (issue #111),
/// or null when the failure did not come from the check.
String? phoneTeamProofStepText(AppLocalizations l, String? details) {
  final step = PhoneProofStep.parse(details);
  if (step == null) return null;
  final check = step.check ?? step.code;
  return switch (step.kind) {
    PhoneProofStepKind.signal => l.phoneTeamProofSignal(step.signal ?? 0),
    PhoneProofStepKind.checkSignal => l.phoneTeamProofCheckSignal(
      check,
      step.signal ?? 0,
    ),
    PhoneProofStepKind.notBlocked => l.phoneTeamProofNotBlocked(check),
    PhoneProofStepKind.sandbox => l.phoneTeamProofSandbox,
    PhoneProofStepKind.ubuntu => l.phoneTeamProofUbuntu(check),
    PhoneProofStepKind.fixture => l.phoneTeamProofFixture,
    PhoneProofStepKind.noAnswer => l.phoneTeamProofNoAnswer,
    PhoneProofStepKind.proot => l.phoneTeamProofProot(check),
    PhoneProofStepKind.other => l.phoneTeamProofOther(check),
  };
}

/// Details: the plain sentence, the step a failed safety check stopped at
/// (when it did), then the technical code.
String? phoneTeamDetailsText(
  AppLocalizations l,
  PhoneTeamSetupProblem problem,
  String? code,
) {
  if (code == null || code.isEmpty) return null;
  final proof = phoneTeamProofStepText(l, code);
  // A failed safety check is not an engine crash: say which it was.
  final sentence = proof != null
      ? l.phoneTeamWhyUnsafe
      : switch (problem) {
          PhoneTeamSetupProblem.unsafe => l.phoneTeamWhyUnsafe,
          PhoneTeamSetupProblem.engine => l.phoneTeamWhyEngine,
          PhoneTeamSetupProblem.stopFailed => l.phoneTeamWhyStop,
          PhoneTeamSetupProblem.serverStart => l.phoneTeamWhyServer,
          PhoneTeamSetupProblem.notReady => l.phoneTeamWhyNotReady,
          PhoneTeamSetupProblem.declined ||
          PhoneTeamSetupProblem.noServer => null,
        };
  return [?sentence, ?proof, code].join('\n');
}

({String title, String body}) _problemCopy(
  AppLocalizations l,
  PhoneTeamSetupProblem problem,
) => switch (problem) {
  PhoneTeamSetupProblem.unsafe => (
    title: l.phoneTeamFailUnsafeTitle,
    body: l.phoneTeamFailUnsafeBody,
  ),
  PhoneTeamSetupProblem.engine => (
    title: l.phoneTeamFailEngineTitle,
    body: l.phoneTeamFailEngineBody,
  ),
  PhoneTeamSetupProblem.stopFailed => (
    title: l.phoneTeamFailStopTitle,
    body: l.phoneTeamFailStopBody,
  ),
  PhoneTeamSetupProblem.serverStart => (
    title: l.phoneTeamFailServerTitle,
    body: l.phoneTeamFailServerBody,
  ),
  PhoneTeamSetupProblem.notReady => (
    title: l.phoneTeamFailNotReadyTitle,
    body: l.phoneTeamFailNotReadyBody,
  ),
  PhoneTeamSetupProblem.declined => (
    title: l.phoneTeamFailDeclinedTitle,
    body: l.phoneTeamFailDeclinedBody,
  ),
  PhoneTeamSetupProblem.noServer => (
    title: l.phoneTeamFailNoServerTitle,
    body: l.phoneTeamFailNoServerBody,
  ),
};

String _elapsed(AppLocalizations l, Duration d) => d.inSeconds < 60
    ? l.teamProjectElapsedSeconds(d.inSeconds < 0 ? 0 : d.inSeconds)
    : l.teamProjectElapsedMinutes(d.inMinutes);

String _stepTitle(AppLocalizations l, PhoneTeamSetupStep step) =>
    switch (step) {
      PhoneTeamSetupStep.reply => l.phoneTeamStepReply,
      PhoneTeamSetupStep.stop => l.phoneTeamStepStop,
      PhoneTeamSetupStep.check => l.phoneTeamStepCheck,
      PhoneTeamSetupStep.server => l.phoneTeamStepServer,
    };

/// One row per step; every mark comes from the controller's own signals.
List<KitStep> phoneTeamSetupSteps(
  AppLocalizations l,
  PhoneTeamSetupController c,
) => [
  for (final step in PhoneTeamSetupStep.values)
    () {
      final KitMarkState state;
      String? supporting;
      final took = c.took(step);
      final elapsed = c.elapsed(step);
      final failed =
          c.phase == PhoneTeamSetupPhase.failed && c.failedStep == step;
      if (failed) {
        state = KitMarkState.failed;
        if (step == PhoneTeamSetupStep.check &&
            c.problem == PhoneTeamSetupProblem.unsafe &&
            (c.details ?? '').contains('not_packaged')) {
          supporting = l.phoneTeamReasonNotPackaged;
        }
      } else if (took != null) {
        state = KitMarkState.done;
        supporting = c.wasSkipped(step)
            ? l.phoneTeamNotNeeded
            : l.aiteamComponentStageTook(_elapsed(l, took));
      } else if (elapsed != null) {
        state = KitMarkState.working;
        final words = switch (step) {
          PhoneTeamSetupStep.reply => l.phoneTeamReplyWaiting,
          PhoneTeamSetupStep.stop
              when c.phase == PhoneTeamSetupPhase.confirming =>
            l.phoneTeamStopWaiting,
          _ => null,
        };
        final time = l.aiteamComponentStageSoFar(_elapsed(l, elapsed));
        supporting = words == null ? time : '$words · $time';
      } else {
        state = KitMarkState.waiting;
      }
      return KitStep(
        key: ValueKey('phone-team-step-${step.name}'),
        title: _stepTitle(l, step),
        state: state,
        supporting: supporting,
      );
    }(),
];

class TeamPhoneSetupScreen extends StatefulWidget {
  const TeamPhoneSetupScreen({
    super.key,
    required this.connection,
    this.controller,
    this.autoStart = true,
  });

  final ConnectionController connection;

  /// The flow to show; the app's shared one by default, tests pass a fake.
  final PhoneTeamSetupController? controller;
  final bool autoStart;

  @override
  State<TeamPhoneSetupScreen> createState() => _TeamPhoneSetupScreenState();
}

class _TeamPhoneSetupScreenState extends State<TeamPhoneSetupScreen> {
  late PhoneTeamSetupController _flow;
  Timer? _tick;
  bool _asking = false;
  bool _started = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_started) return;
    _started = true;
    _flow =
        widget.controller ??
        phoneTeamSetupForContext(context, widget.connection);
    _flow.addListener(_changed);
    if (widget.autoStart && !_flow.isRunning && !_flow.isDone) {
      unawaited(_flow.run());
    }
    _changed();
  }

  @override
  void dispose() {
    _tick?.cancel();
    if (_started) _flow.removeListener(_changed);
    super.dispose();
  }

  void _changed() {
    if (!mounted) return;
    setState(() {});
    // One timer for the "so far" times, only while a run is going.
    if (_flow.isRunning) {
      _tick ??= Timer.periodic(const Duration(seconds: 1), (_) {
        if (mounted) setState(() {});
      });
    } else {
      _tick?.cancel();
      _tick = null;
    }
    if (_flow.phase == PhoneTeamSetupPhase.confirming && !_asking) {
      _asking = true;
      WidgetsBinding.instance.addPostFrameCallback((_) => _ask());
    }
  }

  Future<void> _ask() async {
    if (!mounted) {
      _asking = false;
      return;
    }
    final l = AppLocalizations.of(context);
    final yes = await showKitConfirm(
      context,
      title: l.phoneTeamStopTitle,
      body: l.phoneTeamStopBody,
      confirmLabel: l.phoneTeamStopConfirm,
      cancelLabel: l.phoneTeamStopCancel,
      confirmKey: const ValueKey('phone-team-stop-confirm'),
    );
    _asking = false;
    _flow.answer(yes);
  }

  /// Ready: leave the stack of pages that held the old team and open the
  /// team fresh, so nothing keeps a stopped controller.
  void _openTeam() {
    final navigator = Navigator.of(context);
    final connection = widget.connection;
    navigator.popUntil((route) => route.isFirst);
    unawaited(
      navigator.push(
        KitPageRoute<void>(builder: (_) => TeamPage(connection: connection)),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final steps = phoneTeamSetupSteps(l, _flow);
    final checklist = KitChecklist(
      checklistKey: const ValueKey('phone-team-steps'),
      steps: steps,
    );
    final Widget body;
    switch (_flow.phase) {
      case PhoneTeamSetupPhase.done:
        final health = _flow.health;
        final protection = health != null && health.canExecute
            ? phoneTeamProtectionText(l, health.boundaryTier)
            : null;
        body = KitStateView(
          key: const ValueKey('phone-team-ready'),
          icon: AppIconography.checkCircle,
          tone: AppStatusTone.ok,
          title: l.phoneTeamDoneTitle,
          body: l.phoneTeamDoneBody,
          content: protection == null
              ? checklist
              : Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    KitNotice(
                      key: const ValueKey('phone-team-ready-protection'),
                      message: protection,
                      icon: Icons.shield_outlined,
                    ),
                    checklist,
                  ],
                ),
          primary: KitAction(
            key: const ValueKey('phone-team-new-project'),
            label: l.teamProjectNew,
            onPressed: _openTeam,
          ),
        );
      case PhoneTeamSetupPhase.failed:
        final problem = _flow.problem ?? PhoneTeamSetupProblem.engine;
        final copy = phoneTeamProblemCopy(l, problem, _flow);
        final calm =
            problem == PhoneTeamSetupProblem.declined ||
            problem == PhoneTeamSetupProblem.noServer;
        final details = _flow.details;
        body = KitStateView(
          key: const ValueKey('phone-team-failed'),
          icon: calm ? AppIconography.phone : AppIconography.warning,
          tone: calm ? AppStatusTone.neutral : AppStatusTone.failure,
          title: copy.title,
          body: copy.body,
          content: problem == PhoneTeamSetupProblem.noServer ? null : checklist,
          primary: problem == PhoneTeamSetupProblem.noServer
              ? null
              : _flow.needsComponentRecovery
              ? KitAction(
                  key: const ValueKey('phone-team-recover-ownership'),
                  label: l.e7SetupOpenAppSettings,
                  onPressed: () => unawaited(TermuxBridge.openAppSettings()),
                )
              : KitAction(
                  key: const ValueKey('phone-team-start-again'),
                  label: l.teamStartAgain,
                  icon: AppIconography.play,
                  onPressed: () => unawaited(_flow.run()),
                ),
          details: calm ? null : phoneTeamDetailsText(l, problem, details),
        );
      case PhoneTeamSetupPhase.idle:
      case PhoneTeamSetupPhase.running:
      case PhoneTeamSetupPhase.confirming:
        final finished = steps
            .where((s) => s.state == KitMarkState.done)
            .length;
        final idle = _flow.phase == PhoneTeamSetupPhase.idle;
        body = KitStateView(
          key: const ValueKey('phone-team-running'),
          icon: AppIconography.waiting,
          tone: AppStatusTone.progress,
          illustration: const TeamWakingScene(),
          illustrationAmbient: true,
          title: l.phoneTeamSetupTitle,
          progress: KitProgress.known(finished / steps.length),
          content: checklist,
          primary: idle
              ? KitAction(
                  key: const ValueKey('phone-team-start'),
                  label: l.teamIntroTurnOnPhone,
                  icon: AppIconography.play,
                  onPressed: () => unawaited(_flow.run()),
                )
              : null,
        );
    }
    return KitScreen(
      topBar: KitTopBar(title: l.teamUiHomeTitle),
      width: KitScreenWidth.reading,
      body: body,
    );
  }
}
