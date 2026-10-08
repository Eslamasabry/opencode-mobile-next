import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../domain/agent_auth_probe.dart';
import '../../../domain/agent_sign_in.dart';
import '../../../domain/phone_agent_host.dart';
import '../../../domain/phone_agents.dart';
import '../../../domain/phone_agents_source.dart';
import '../../../l10n/app_localizations.dart';
import '../../app_iconography.dart';
import '../../app_theme.dart' show AppStatusTone;
import '../../kit/kit.dart';
import '../chats/chats_host.dart';
import 'agent_error_notice.dart';
import 'agent_sign_in_terminal.dart';
import 'agents_text.dart';
import 'phone_check_view.dart';

/// Where the agent sheet opens.
enum AgentSheetStep { list, setup, check, signIn }

/// Opens the agent sheet: the choice of agent, and, in the same sheet, the
/// steps that make an agent ready (install, phone check, sign-in). Returns
/// true when an agent was chosen. One sheet only: every step replaces the
/// frame in place.
Future<bool?> showAgentSheet(
  BuildContext context, {
  String? agentId,
  AgentSheetStep step = AgentSheetStep.list,
}) => showKitFramedSheet<bool>(
  context,
  sheetKey: const ValueKey('agents-sheet'),
  builder: (_) => AgentSheet(agentId: agentId, step: step),
);

final _never = ValueNotifier<int>(0);

class AgentSheet extends ConsumerStatefulWidget {
  const AgentSheet({super.key, this.agentId, this.step = AgentSheetStep.list});

  final String? agentId;
  final AgentSheetStep step;

  @override
  ConsumerState<AgentSheet> createState() => _AgentSheetState();
}

class _AgentSheetState extends ConsumerState<AgentSheet> {
  late AgentSheetStep _step = widget.step;
  String? _agentId;
  AgentFailure? _notice;
  AgentPhoneCheckResult? _check;
  bool _checking = false;
  bool _advancing = false;
  // One advance per finished install: a row that cannot move on yet must not
  // re-read the phone on every rebuild.
  bool _doneHandled = false;
  bool _signInKicked = false;
  // The agent's own sign-out is running (up to 20 seconds). The frame holds
  // its signed-in layout until it ends, so a failed attempt never flashes the
  // sign-in layout.
  bool _signingOut = false;
  // The account the status check named before the sign-out started; the
  // frame keeps showing it while the logout runs.
  AgentAuthProbeResult? _heldAccount;
  Timer? _closeSoon;

  // Removing the installed agent from this phone (BA10): pending while the
  // host deletes it, then the measured result in place of the frame.
  bool _removalPending = false;
  AgentRemovalResult? _removed;

  PhoneAgentsSource get _agents => ref.read(chatsHostProvider).agents!;

  /// Present only when the source can remove an installed agent.
  PhoneAgentRemovalSource? get _removals {
    final agents = _agents;
    return agents is PhoneAgentRemovalSource
        ? agents as PhoneAgentRemovalSource
        : null;
  }

  /// Remove is offered only where the source says it can (never for Claude
  /// Code, whose install and account this app does not manage).
  bool _canRemove(String id) =>
      id != 'claude' && (_removals?.canRemoveAgent(id) ?? false);

  bool _isRemoving(String id) =>
      _removalPending || _removals?.removingAgentId == id;

  /// Present only when the source has qualified status checks and logout.
  PhoneAgentAccountSource? get _accounts {
    final agents = _agents;
    return agents is PhoneAgentAccountSource
        ? agents as PhoneAgentAccountSource
        : null;
  }

  @override
  void initState() {
    super.initState();
    _agentId = widget.agentId;
    // Entering a step directly (a status line's Sign in) starts its work.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      unawaited(_agents.refreshAgentRows());
      _enter(_step);
    });
  }

  @override
  void dispose() {
    _closeSoon?.cancel();
    super.dispose();
  }

  AgentRow? _row(String? id) => id == null
      ? null
      : _agents.agentRows.where((row) => row.id == id).firstOrNull;

  String _name(String? id) =>
      _row(id)?.name ??
      _agents.chatAgentChoices
          .where((choice) => choice.agentId == id)
          .firstOrNull
          ?.name ??
      '';

  void _enter(AgentSheetStep step) {
    setState(() {
      _step = step;
      _notice = null;
    });
    switch (step) {
      case AgentSheetStep.check:
        unawaited(_runCheck());
      case AgentSheetStep.signIn:
        unawaited(_beginSignIn());
      case AgentSheetStep.list || AgentSheetStep.setup:
        break;
    }
  }

  /// What the row still needs, as the step to show; null when it needs none.
  AgentSheetStep? _stepFor(AgentRow? row) => switch (row?.fixAction) {
    PhoneAgentFixAction.install => AgentSheetStep.setup,
    PhoneAgentFixAction.runPhoneCheck => AgentSheetStep.check,
    PhoneAgentFixAction.signIn => AgentSheetStep.signIn,
    _ => null,
  };

  Future<void> _pick(String id, AgentRow? row) async {
    final agents = _agents;
    if (id == openCodeChatAgentId || row == null || row.chatSelectable) {
      await _choose(id);
      return;
    }
    _agentId = id;
    switch (row.fixAction) {
      case PhoneAgentFixAction.resume:
        try {
          await agents.resumeAgentHost();
        } catch (error) {
          _fail(error);
        }
      case null:
        // A real blocker: say exactly what it is.
        setState(
          () => _notice = AgentFailure(
            agentRowLine(AppLocalizations.of(context), row),
          ),
        );
      default:
        _enter(_stepFor(row) ?? AgentSheetStep.setup);
    }
  }

  Future<void> _choose(String id) async {
    try {
      await _agents.selectChatAgent(id);
      if (mounted) KitSheet.close(context, true);
    } catch (error) {
      _fail(error);
    }
  }

  /// Says a step failed in plain words, with the technical text under Details.
  void _fail(Object error) {
    if (!mounted) return;
    final failure = agentFailure(AppLocalizations.of(context), error);
    setState(() => _notice = failure);
  }

  Future<void> _install(String id) async {
    setState(() {
      _notice = null;
      _doneHandled = false;
    });
    try {
      await _agents.installAgent(id);
    } catch (error) {
      // An install is refused while a removal runs: say that, not "failed".
      if (_removals?.removingAgentId != null && mounted) {
        setState(
          () => _notice = AgentFailure(
            AppLocalizations.of(context).agentsRemoveBusy,
          ),
        );
        return;
      }
      _fail(error);
    }
  }

  /// Asks first, then removes the installed agent from this phone. Accounts
  /// and conversations stay. The sheet shows the progress in place, then what
  /// was freed; a failure is one of the three fixed sentences.
  Future<void> _remove(String id) async {
    final removals = _removals;
    if (removals == null || _removalPending || !_canRemove(id)) return;
    final l10n = AppLocalizations.of(context);
    final name = KitBidi.auto(_name(id));
    final confirmed = await showKitConfirm(
      context,
      title: l10n.agentsRemoveTitle(name),
      body: l10n.agentsRemoveBody,
      confirmLabel: l10n.agentsRemoveAction(name),
      kind: KitConfirmKind.destructive,
      sheetKey: const ValueKey('agents-remove-sheet'),
      confirmKey: const ValueKey('agents-remove-confirm'),
    );
    if (!confirmed || !mounted) return;
    setState(() {
      _removalPending = true;
      _notice = null;
    });
    try {
      final result = await removals.removeAgent(id);
      if (mounted) setState(() => _removed = result);
    } catch (error) {
      if (mounted) {
        setState(
          () => _notice = AgentFailure(agentRemovalFailureText(l10n, error)),
        );
      }
    } finally {
      if (mounted) setState(() => _removalPending = false);
    }
  }

  Future<void> _cancelInstall() async {
    try {
      await _agents.cancelAgentInstall();
    } catch (error) {
      _fail(error);
    }
  }

  /// Moves to whatever the agent needs next, or chooses it when it is ready.
  Future<void> _advance() async {
    if (_advancing) return;
    _advancing = true;
    try {
      await _agents.refreshAgentRows();
      if (!mounted) return;
      final row = _row(_agentId);
      final next = _stepFor(row);
      if (next == null || row == null) {
        if (row?.chatSelectable ?? false) _enter(AgentSheetStep.signIn);
        return;
      }
      if (next != _step) _enter(next);
    } finally {
      _advancing = false;
    }
  }

  Future<void> _runCheck() async {
    final id = _agentId;
    if (id == null || _checking) return;
    setState(() {
      _checking = true;
      _check = null;
    });
    final AgentPhoneCheckResult result;
    try {
      result = await _agents.runAgentPhoneCheck(id);
    } catch (error) {
      if (mounted) setState(() => _checking = false);
      _fail(error);
      return;
    }
    if (!mounted) return;
    setState(() {
      _checking = false;
      _check = result;
    });
    if (result.passed) unawaited(_advance());
  }

  /// Entering the step reads whether the agent is signed in already; the
  /// sign-in itself runs on its own terminal ([_openSignIn]).
  Future<void> _beginSignIn() async {
    final id = _agentId;
    if (id == null || _signInKicked) return;
    _signInKicked = true;
    final state = _agents.agentSignInState(id);
    if (state != null && state.inspected) return;
    try {
      await _agents.recheckAgentSignIn(id);
    } catch (error) {
      _fail(error);
    }
  }

  /// Claude's own sign-in on a terminal: the person signs in on Anthropic's
  /// page and gives any code to Claude itself, never to this app.
  Future<void> _openSignIn() async {
    final id = _agentId;
    if (id == null) return;
    final agents = _agents;
    final signedIn = await Navigator.of(context).push<bool>(
      KitPageRoute<bool>(
        builder: (_) => AgentSignInTerminalScreen(
          agents: agents,
          agentId: id,
          agentName: _name(id),
          // Through the app's one link seam: the checked sign-in opener.
          openPage: (context, url) {
            final uri = Uri.tryParse(url);
            return uri == null
                ? Future.value()
                : ref.read(chatsHostProvider).openLink(context, uri);
          },
        ),
      ),
    );
    if (!mounted || signedIn != true) return;
    // "Signed in" shows for a moment, then back to New conversation with
    // this agent chosen.
    _closeSoon?.cancel();
    _closeSoon = Timer(
      const Duration(milliseconds: 900),
      () => unawaited(_chooseAfterSignIn(id)),
    );
  }

  /// Starts the phone's agent host again (the row's own Resume act).
  Future<void> _resume() async {
    try {
      await _agents.resumeAgentHost();
    } catch (error) {
      _fail(error);
    }
  }

  /// Asks first, then runs the agent's own logout. Conversations are not
  /// touched. The status check, not the logout's exit code, decides whether
  /// the agent is signed out: if it cannot confirm that, the sheet says so
  /// and reads the real state again.
  Future<void> _signOut(String id) async {
    final accounts = _accounts;
    if (accounts == null || _signingOut) return;
    final l10n = AppLocalizations.of(context);
    final name = KitBidi.auto(_name(id));
    final confirmed = await showKitConfirm(
      context,
      title: l10n.agentsSignOutTitle(name),
      body: l10n.agentsSignOutBody(name),
      confirmLabel: l10n.agentsSignOutAction(name),
      // Signing out removes the agent's login from this phone: the danger
      // fill, with a neutral Cancel.
      kind: KitConfirmKind.destructive,
      icon: AppIconography.personRemove,
      consequenceItems: [
        KitConsequence(
          l10n.agentsSignOutKept(name),
          mark: KitConsequenceMark.kept,
        ),
      ],
      sheetKey: const ValueKey('agents-sign-out-sheet'),
      confirmKey: const ValueKey('agents-sign-out-confirm'),
    );
    if (!confirmed || !mounted) return;
    setState(() {
      _heldAccount = accounts.agentAccount(id);
      _signingOut = true;
      _notice = null;
    });
    try {
      await accounts.signOutAgent(id);
    } catch (error) {
      // Plain words with the way forward; the technical text is under
      // Details. The unconfirmed answer is replaced by a fresh read.
      if (mounted) {
        setState(
          () => _notice = AgentFailure(
            l10n.agentsSignOutFailed(name),
            agentFailure(l10n, error).technical,
          ),
        );
      }
      try {
        await _agents.recheckAgentSignIn(id);
      } catch (_) {
        // The next read of the sheet shows what the agent says.
      }
    } finally {
      if (mounted) setState(() => _signingOut = false);
    }
  }

  Future<void> _chooseAfterSignIn(String id) async {
    if (!mounted) return;
    await _agents.refreshAgentRows();
    if (mounted) await _choose(id);
  }

  /// Leaving the sheet keeps a pending sign-in alive: the person is in the
  /// browser and comes back to the same code. "Get a new code" ends it.
  Future<void> _close() async {
    if (mounted) KitSheet.close(context, false);
  }

  @override
  Widget build(BuildContext context) {
    final host = ref.watch(chatsHostProvider);
    // The sheet is lifted by the keyboard (the framed route does not do it
    // itself), so the code field and Submit stay above it.
    return Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.viewInsetsOf(context).bottom),
      child: SafeArea(
        top: false,
        child: ListenableBuilder(
          listenable: host.listenable ?? _never,
          builder: (context, _) {
            final id = _agentId;
            if (_removed != null) return _removedFrame(context);
            if (id != null && _step != AgentSheetStep.list && _isRemoving(id)) {
              return _removingFrame(context, id);
            }
            return switch (_step) {
              AgentSheetStep.list => _listFrame(context),
              AgentSheetStep.setup => _setupFrame(context),
              AgentSheetStep.check => _checkFrame(context),
              AgentSheetStep.signIn => _signInFrame(context),
            };
          },
        ),
      ),
    );
  }

  KitAction get _backToList => KitAction(
    key: const ValueKey('agents-back'),
    label: AppLocalizations.of(context).agentsChooseTitle,
    onPressed: () => _enter(AgentSheetStep.list),
  );

  Widget _noticeLine(BuildContext context) => _notice == null
      ? const SizedBox.shrink()
      : Padding(
          padding: EdgeInsets.only(top: KitTokens.of(context).space3),
          child: AgentErrorNotice(failure: _notice!),
        );

  // ---- the choice ---------------------------------------------------------

  Widget _listFrame(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final agents = _agents;
    // OpenCode and the agents that are ready and certified on the version
    // this app installs come first and can be chosen. Every other agent
    // stays in the list (owner rule: never hide one), dimmed, with one
    // plain reason and its way forward where there is one.
    final choices = [
      for (final choice in agents.chatAgentChoices)
        if (choice.row == null || agentChoosable(choice.row!)) choice,
    ];
    final shown = {for (final choice in choices) choice.agentId};
    final rest = [
      for (final row in agents.agentRows)
        if ((row.setupVisible || row.chatVisible) && !shown.contains(row.id))
          row,
    ];
    Widget choosable(ChatAgentChoice choice) => KitRow(
      key: ValueKey('agents-choice-${choice.agentId}'),
      title: KitBidi.auto(choice.name),
      leading: KitRowIcon(agentIcon(choice.iconKey), current: choice.selected),
      supporting: TextSpan(
        text: choice.row == null
            ? l10n.agentsStateReady
            : agentRowLine(l10n, choice.row!),
      ),
      supportingMaxLines: 2,
      selected: choice.selected,
      trailing: choice.selected
          ? const KitIcon(AppIconography.check, size: KitIconSize.small)
          : null,
      onTap: () => unawaited(_pick(choice.agentId, choice.row)),
    );
    Widget notYet(AgentRow row) => KitRow.unavailable(
      key: ValueKey('agents-choice-${row.id}'),
      title: KitBidi.auto(row.name),
      leading: KitRowIcon(agentIcon(row.iconKey)),
      reason: agentPickerLine(l10n, row),
      chip: agentFixChip(
        l10n,
        row,
        key: ValueKey('agents-choice-fix-${row.id}'),
        onPressed: () => unawaited(_pick(row.id, row)),
      ),
    );
    return KitSheet(
      handle: false,
      step: AgentSheetStep.list,
      title: l10n.agentsChooseTitle,
      onClose: () => unawaited(_close()),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          KitRowGroup(
            margin: EdgeInsets.zero,
            children: [
              for (final choice in choices) choosable(choice),
              for (final row in rest) notYet(row),
            ],
          ),
          _noticeLine(context),
        ],
      ),
    );
  }

  // ---- remove -------------------------------------------------------------

  /// Remove, as the quiet destructive action at the bottom of a frame: only
  /// where the agent can be removed now.
  List<KitAction> _removeAction(String id) => [
    if (_canRemove(id))
      KitAction(
        key: const ValueKey('agents-remove'),
        label: AppLocalizations.of(
          context,
        ).agentsRemoveAction(KitBidi.auto(_name(id))),
        destructive: true,
        onPressed: () => unawaited(_remove(id)),
      ),
  ];

  /// Removing, in place: nothing else can be done for this agent until the
  /// host has deleted it (the sheet cannot be closed meanwhile).
  Widget _removingFrame(BuildContext context, String id) {
    final l10n = AppLocalizations.of(context);
    final name = KitBidi.auto(_name(id));
    return KitSheet(
      handle: false,
      step: 'removing',
      title: name,
      child: KitProgressView(
        progress: KitProgress.waiting(
          key: const ValueKey('agents-removing'),
          caption: l10n.agentsRemoving(name),
        ),
      ),
    );
  }

  /// What the removal ended with: what was freed, or that nothing was there.
  Widget _removedFrame(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final name = _name(_agentId);
    return KitSheet(
      handle: false,
      step: 'removed',
      title: KitBidi.auto(name),
      onClose: () => unawaited(_close()),
      primary: KitAction(
        key: const ValueKey('agents-removed-done'),
        label: l10n.agentsDone,
        onPressed: () => unawaited(_close()),
      ),
      child: KitNotice(
        key: const ValueKey('agents-removed-words'),
        icon: AppIconography.checkCircle,
        tone: AppStatusTone.ok,
        message: agentRemovalResultText(l10n, name, _removed!),
      ),
    );
  }

  // ---- install ------------------------------------------------------------

  Widget _setupFrame(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final tokens = KitTokens.of(context);
    final id = _agentId ?? '';
    final name = _name(id);
    final size = agentPayloadSize(id);
    final progress = _agents.agentSetupProgress;
    final mine = progress.agentId == id;
    final installing = mine && progress.phase == AgentSetupPhase.installing;
    final interrupted = mine && progress.phase == AgentSetupPhase.interrupted;
    final failed = mine && progress.phase == AgentSetupPhase.failed;
    if (mine && progress.phase != AgentSetupPhase.done) _doneHandled = false;
    if (mine &&
        progress.phase == AgentSetupPhase.done &&
        !_advancing &&
        !_doneHandled) {
      _doneHandled = true;
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) unawaited(_advance());
      });
    }
    final fraction = progress.fraction;
    return KitSheet(
      handle: false,
      step: AgentSheetStep.setup,
      title: size == null
          ? l10n.agentsSetupTitleNoSize(KitBidi.auto(name))
          : l10n.agentsSetupTitle(KitBidi.auto(name), size),
      leading: installing ? null : _backToList,
      onClose: installing ? null : () => unawaited(_close()),
      primary: installing
          ? null
          : KitAction(
              key: const ValueKey('agents-install'),
              label: l10n.agentsInstallAction(KitBidi.auto(name)),
              icon: AppIconography.download,
              // A new install waits for a removal that is running.
              onPressed: _removals?.removingAgentId != null
                  ? null
                  : () => unawaited(_install(id)),
              disabledReason: _removals?.removingAgentId != null
                  ? l10n.agentsRemoveBusy
                  : null,
            ),
      tertiary: installing ? const [] : _removeAction(id),
      secondary: installing
          ? KitAction(
              key: const ValueKey('agents-cancel-setup'),
              label: l10n.agentsCancelSetup,
              onPressed: () => unawaited(_cancelInstall()),
            )
          : null,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        spacing: tokens.space3,
        children: [
          KitText(
            l10n.agentsSetupBody(KitBidi.auto(name)),
            tone: KitTextTone.secondary,
          ),
          if (size != null)
            KitText(
              l10n.agentsSetupSizeNote(size),
              role: KitTextRole.caption,
              tone: KitTextTone.tertiary,
            ),
          if (installing)
            KitProgressView(
              progress: fraction == null
                  ? KitProgress.waiting(
                      key: const ValueKey('agents-progress'),
                      caption: l10n.agentsInstalling(KitBidi.auto(name)),
                    )
                  : KitProgress.known(
                      fraction.clamp(0.0, 1.0),
                      key: const ValueKey('agents-progress'),
                      caption: l10n.agentsInstalling(KitBidi.auto(name)),
                    ),
            ),
          if (interrupted)
            KitNotice(
              key: const ValueKey('agents-setup-words'),
              message: l10n.agentsSetupInterrupted,
            ),
          if (failed)
            KitNotice(
              key: const ValueKey('agents-setup-words'),
              message: l10n.agentsSetupFailed,
            ),
          _noticeLine(context),
        ],
      ),
    );
  }

  // ---- phone check --------------------------------------------------------

  Widget _checkFrame(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final name = _name(_agentId);
    final failed = _check != null && !_check!.passed;
    return KitSheet(
      handle: false,
      step: AgentSheetStep.check,
      title: l10n.agentsCheckTitle,
      leading: _checking ? null : _backToList,
      onClose: _checking ? null : () => unawaited(_close()),
      primary: failed
          ? KitAction(
              key: const ValueKey('agents-check-again'),
              label: l10n.agentsCheckAction(KitBidi.auto(name)),
              onPressed: () => unawaited(_runCheck()),
            )
          : null,
      tertiary: _checking ? const [] : _removeAction(_agentId ?? ''),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          AgentPhoneCheckView(agent: name, result: _check),
          _noticeLine(context),
        ],
      ),
    );
  }

  // ---- sign in ------------------------------------------------------------

  Widget _signInFrame(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final tokens = KitTokens.of(context);
    final id = _agentId ?? '';
    final name = KitBidi.auto(_name(id));
    final state = _agents.agentSignInState(id);
    final phase = state?.phase;
    final checking = state == null || !state.inspected;
    final signedIn = phase == AgentSignInPhase.signedIn || _signingOut;
    final hostKey = state?.hostOnlyApiKey ?? false;
    // A signed-in agent at its plan limit reads signed in; its row says limit.
    final limit =
        phase == AgentSignInPhase.limitReached ||
        _row(id)?.status == PhoneAgentStatus.limitReached;
    // Sign out is offered only where the agent's own logout is qualified and
    // its status check confirmed the sign-in.
    final accounts = _accounts;
    // Who the agent is signed in as, when its status check said.
    final account = _signingOut ? _heldAccount : accounts?.agentAccount(id);
    final accountName =
        signedIn && account?.state == AgentAuthProbeState.signedIn
        ? account?.accountDisplayName?.trim()
        : null;
    final canSignOut =
        _signingOut ||
        (phase == AgentSignInPhase.signedIn &&
            accounts != null &&
            accounts.agentAccount(id)?.state == AgentAuthProbeState.signedIn &&
            accounts.canSignOutAgent(id));

    // A stopped agent (its row offers only Resume) still opens this page so
    // Sign out and Remove stay reachable; Resume is its one main act.
    final stopped = _row(id)?.fixAction == PhoneAgentFixAction.resume;

    KitAction? primary;
    if (stopped) {
      primary = KitAction(
        key: const ValueKey('agents-resume'),
        label: l10n.agentsResumeAction(name),
        onPressed: _signingOut ? null : () => unawaited(_resume()),
      );
    } else if (signedIn && !limit) {
      primary = KitAction(
        key: const ValueKey('agents-sign-in-done'),
        label: l10n.agentsSignInDone(name),
        onPressed: _signingOut ? null : () => unawaited(_choose(id)),
      );
    } else if (!checking && !hostKey && !limit) {
      primary = KitAction(
        key: const ValueKey('agents-sign-in-start'),
        label: l10n.agentsSignInStart(name),
        onPressed: () => unawaited(_openSignIn()),
      );
    }

    final String body;
    if (stopped) {
      body = l10n.agentsStateStopped;
    } else if (limit) {
      body = l10n.agentsSignInLimit(name);
    } else if (signedIn) {
      body = l10n.agentsSignedInBody(name);
    } else if (hostKey) {
      body = l10n.agentsSignInUnavailable;
    } else if (checking) {
      body = l10n.agentsSignInChecking;
    } else {
      body = id == 'claude'
          ? l10n.agentsSignInIntro(name)
          : l10n.agentsSignInIntroOther(name);
    }

    return KitSheet(
      handle: false,
      step: AgentSheetStep.signIn,
      title: signedIn ? l10n.agentsSignedIn : l10n.agentsSignInTitle(name),
      leading: _backToList,
      onClose: () => unawaited(_close()),
      loading: checking || _signingOut,
      primary: primary,
      // The agent's own status can say signed in while its login no longer
      // works (an expired session): signing in again is always offered.
      secondary: signedIn
          ? KitAction(
              key: const ValueKey('agents-sign-in-again'),
              label: l10n.agentsSignInAgain,
              onPressed: _signingOut ? null : () => unawaited(_openSignIn()),
            )
          : null,
      tertiary: [
        if (canSignOut)
          KitAction(
            key: const ValueKey('agents-sign-out'),
            label: l10n.agentsSignOutAction(name),
            onPressed: _signingOut ? null : () => unawaited(_signOut(id)),
          ),
        if (!_signingOut) ..._removeAction(id),
      ],
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        spacing: tokens.space3,
        children: [
          KitText(
            body,
            key: const ValueKey('agents-sign-in-words'),
            tone: KitTextTone.secondary,
          ),
          if (accountName != null && accountName.isNotEmpty)
            KitText(
              agentSignedInLine(l10n, account!),
              key: const ValueKey('agents-sign-in-account'),
              tone: KitTextTone.secondary,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
          _noticeLine(context),
        ],
      ),
    );
  }
}
