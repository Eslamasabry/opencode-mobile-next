import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart' show Clipboard, TextInputAction;
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../domain/agent_sign_in.dart';
import '../../../domain/phone_agent_host.dart';
import '../../../domain/phone_agents.dart';
import '../../../domain/phone_agents_source.dart';
import '../../../l10n/app_localizations.dart';
import '../../app_iconography.dart';
import '../../kit/kit.dart';
import '../chats/chats_host.dart';
import 'agent_error_notice.dart';
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
  bool _submitting = false;
  bool _signInKicked = false;
  Timer? _closeSoon;
  final _code = TextEditingController();

  PhoneAgentsSource get _agents => ref.read(chatsHostProvider).agents!;

  @override
  void initState() {
    super.initState();
    _agentId = widget.agentId;
    // Submit follows what is in the field.
    _code.addListener(() {
      if (mounted) setState(() {});
    });
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
    _code.dispose();
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
    // A login waiting for its code goes straight to the code field.
    if (agentLoginPending(agents, id)) {
      _enter(AgentSheetStep.signIn);
      return;
    }
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
      _fail(error);
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

  Future<void> _beginSignIn() async {
    final id = _agentId;
    if (id == null || _signInKicked) return;
    _signInKicked = true;
    final state = _agents.agentSignInState(id);
    if (state != null &&
        state.inspected &&
        state.phase != AgentSignInPhase.signedOut) {
      return;
    }
    try {
      await _agents.startAgentSignIn(id);
    } on AgentSignInException {
      // The state carries the failure; the step says it in words.
    } catch (error) {
      _fail(error);
    }
  }

  Future<void> _restartSignIn() async {
    final id = _agentId;
    if (id == null) return;
    try {
      await _agents.cancelAgentSignIn(id);
    } catch (_) {}
    _signInKicked = false;
    await _beginSignIn();
  }

  /// Reads the clipboard into the code field (Claude's page has a copy
  /// button; this is the one tap that brings the code back).
  Future<void> _paste() async {
    final data = await Clipboard.getData(Clipboard.kTextPlain);
    final text = data?.text?.trim() ?? '';
    if (!mounted || text.isEmpty) return;
    _code.text = text;
    _code.selection = TextSelection.collapsed(offset: text.length);
  }

  Future<void> _submitCode() async {
    final id = _agentId;
    if (id == null || _submitting) return;
    final l10n = AppLocalizations.of(context);
    final AgentSignInCode code;
    try {
      code = AgentSignInCode(_code.text.trim());
    } on AgentSignInException catch (error) {
      setState(() => _notice = agentFailure(l10n, error));
      return;
    }
    // The pasted code leaves the field the moment it is sent.
    _code.clear();
    setState(() {
      _submitting = true;
      _notice = null;
    });
    try {
      await _agents.submitAgentSignInCode(id, code);
      if (mounted &&
          _agents.agentSignInState(id)?.phase == AgentSignInPhase.signedIn) {
        // "Signed in" shows for a moment, then back to New conversation with
        // this agent chosen.
        _closeSoon?.cancel();
        _closeSoon = Timer(
          const Duration(milliseconds: 900),
          () => unawaited(_chooseAfterSignIn(id)),
        );
      }
    } catch (error) {
      _fail(error);
    } finally {
      code.clear();
      if (mounted) setState(() => _submitting = false);
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
          builder: (context, _) => switch (_step) {
            AgentSheetStep.list => _listFrame(context),
            AgentSheetStep.setup => _setupFrame(context),
            AgentSheetStep.check => _checkFrame(context),
            AgentSheetStep.signIn => _signInFrame(context),
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
    final choices = agents.chatAgentChoices;
    final shown = {for (final choice in choices) choice.agentId};
    final rest = [
      for (final row in agents.agentRows)
        if (row.setupVisible && !shown.contains(row.id)) row,
    ];
    Widget tile(
      String id,
      String name,
      String iconKey,
      String line,
      bool selected,
      AgentRow? row,
    ) => KitRow(
      key: ValueKey('agents-choice-$id'),
      title: KitBidi.auto(name),
      leading: KitRowIcon(agentIcon(iconKey), current: selected),
      supporting: TextSpan(text: line),
      supportingMaxLines: 2,
      selected: selected,
      trailing: selected
          ? const KitIcon(AppIconography.check, size: KitIconSize.small)
          : row?.fixAction == PhoneAgentFixAction.install
          ? KitText(
              l10n.agentsInstallHint,
              role: KitTextRole.caption,
              tone: KitTextTone.tertiary,
            )
          : null,
      onTap: () => unawaited(_pick(id, row)),
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
              for (final choice in choices)
                tile(
                  choice.agentId,
                  choice.name,
                  choice.iconKey,
                  choice.row == null
                      ? l10n.agentsStateReady
                      : agentLoginPending(agents, choice.agentId)
                      ? l10n.agentsEnterCode
                      : agentRowLine(l10n, choice.row!),
                  choice.selected,
                  choice.row,
                ),
              for (final row in rest)
                tile(
                  row.id,
                  row.name,
                  row.iconKey,
                  agentLoginPending(agents, row.id)
                      ? l10n.agentsEnterCode
                      : agentRowLine(l10n, row),
                  false,
                  row,
                ),
            ],
          ),
          _noticeLine(context),
        ],
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
              onPressed: () => unawaited(_install(id)),
            ),
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
      child: AgentPhoneCheckView(agent: name, result: _check),
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
    final signedIn = phase == AgentSignInPhase.signedIn;
    final failed = phase == AgentSignInPhase.failed;
    final hostKey = state?.hostOnlyApiKey ?? false;
    final limit = phase == AgentSignInPhase.limitReached;
    final url = _agents.agentSignInUrl(id);
    // A login is waiting for its code: the field is always there, right
    // under the page button, whether or not the host has asked for the code
    // yet (sending it asks the host first).
    final pending =
        (phase == AgentSignInPhase.urlReady ||
            phase == AgentSignInPhase.awaitingCode) &&
        url != null;
    final badCode =
        failed &&
        (state?.failure == AgentSignInFailure.invalidCode ||
            state?.failure == AgentSignInFailure.authenticationRejected);
    final codeText = _code.text.trim();

    KitAction? primary;
    if (signedIn) {
      primary = KitAction(
        key: const ValueKey('agents-sign-in-done'),
        label: l10n.agentsSignInDone(name),
        onPressed: () => unawaited(_choose(id)),
      );
    } else if (pending) {
      primary = KitAction(
        key: const ValueKey('agents-code-submit'),
        label: _submitting
            ? l10n.agentsSignInSubmitting
            : l10n.agentsSignInSubmit,
        working: _submitting,
        disabledReason: codeText.isEmpty ? l10n.agentsSignInCodeFirst : null,
        onPressed: () => unawaited(_submitCode()),
      );
    } else if (failed) {
      primary = KitAction(
        key: const ValueKey('agents-sign-in-again'),
        label: badCode
            ? l10n.agentsSignInNewCode
            : l10n.agentsSignInStart(name),
        onPressed: () => unawaited(_restartSignIn()),
      );
    } else if (phase == AgentSignInPhase.signedOut && !checking && !hostKey) {
      primary = KitAction(
        key: const ValueKey('agents-sign-in-start'),
        label: l10n.agentsSignInStart(name),
        onPressed: () => unawaited(_restartSignIn()),
      );
    }

    final String body;
    if (signedIn) {
      body = l10n.agentsSignedInBody(name);
    } else if (failed) {
      body = agentSignInFailureText(l10n, state?.failure, name);
    } else if (limit) {
      body = l10n.agentsSignInLimit(name);
    } else if (hostKey) {
      body = l10n.agentsSignInUnavailable;
    } else if (checking) {
      body = l10n.agentsSignInChecking;
    } else {
      body = l10n.agentsSignInIntro(name);
    }

    return KitSheet(
      handle: false,
      step: AgentSheetStep.signIn,
      title: signedIn ? l10n.agentsSignedIn : l10n.agentsSignInTitle(name),
      leading: _backToList,
      onClose: () => unawaited(_close()),
      loading: checking && !failed,
      primary: primary,
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
          if (pending) ...[
            KitButton.secondary(
              key: const ValueKey('agents-open-page'),
              label: l10n.agentsSignInOpenPage(name),
              icon: AppIconography.link,
              onPressed: () =>
                  unawaited(ref.read(chatsHostProvider).openLink(context, url)),
            ),
            KitField(
              fieldKey: const ValueKey('agents-code-field'),
              label: l10n.agentsSignInCodeLabel,
              controller: _code,
              enabled: !_submitting,
              disabledReason: l10n.agentsSignInSubmitting,
              textInputAction: TextInputAction.done,
              onSubmitted: (_) => unawaited(_submitCode()),
            ),
            KitButton.tertiary(
              key: const ValueKey('agents-code-paste'),
              label: l10n.agentsSignInPaste,
              icon: AppIconography.paste,
              onPressed: () => unawaited(_paste()),
            ),
          ],
          if (failed)
            AgentErrorNotice(
              failure: AgentFailure(
                body,
                'AgentSignInFailure.${state?.failure?.name ?? 'unknown'}',
              ),
            ),
          if (!failed) _noticeLine(context),
        ],
      ),
    );
  }
}
