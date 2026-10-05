import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart' show Clipboard;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:xterm/xterm.dart' as xterm;

import '../../../builtin/local_terminal.dart';
import '../../../domain/agent_catalog.dart';
import '../../../domain/agent_sign_in.dart';
import '../../../domain/phone_agents_source.dart';
import '../../../l10n/app_localizations.dart';
import '../../app_theme.dart';
import '../../kit/kit.dart';
import '../../kit/terminal_key_bar.dart';
import '../../widgets/external_link.dart';

/// Claude's own sign-in, on a terminal: `claude auth login` for the agents'
/// account on this phone. Claude asks the system for its sign-in page and
/// the app opens it; the person signs in on Anthropic's page and, when
/// Claude asks for a code, pastes it into Claude itself. The app never reads
/// or passes on the code (Anthropic's terms: sign-in completes in its own
/// flow). Pops true once the agent is signed in.
class AgentSignInTerminalScreen extends ConsumerStatefulWidget {
  const AgentSignInTerminalScreen({
    super.key,
    required this.agents,
    required this.agentId,
    required this.agentName,
    this.sessions,
    this.openPage,
  });

  final PhoneAgentsSource agents;
  final String agentId;
  final String agentName;

  /// The terminal owner; the app's one by default.
  final LocalTerminalSessions? sessions;

  /// Opens Claude's page; the checked sign-in opener by default.
  final Future<void> Function(BuildContext context, String url)? openPage;

  @override
  ConsumerState<AgentSignInTerminalScreen> createState() =>
      _AgentSignInTerminalScreenState();
}

class _AgentSignInTerminalScreenState
    extends ConsumerState<AgentSignInTerminalScreen> {
  late final LocalTerminalSessions _sessions =
      widget.sessions ?? ref.read(localTerminalProvider);
  final _keys = TerminalKeyBarController();
  final _selection = xterm.TerminalController();
  final _focus = FocusNode();
  LocalShell? _shell;
  bool _checking = false;
  bool _notYet = false;

  AppLocalizations get _l10n =>
      lookupAppLocalizations(Localizations.localeOf(context));

  @override
  void initState() {
    super.initState();
    _start();
  }

  void _start() {
    final profile = widget.agents.agentSignInProfileId;
    if (profile == null) return;
    final shell = _sessions.startSignIn(profile, _program)
      ..onOpenUrl = _openPage
      ..inputFilter = _keys.apply
      ..addListener(_shellChanged);
    setState(() {
      _shell = shell;
      _notYet = false;
    });
  }

  /// The agent's own login command, from the catalog: its program and
  /// sign-in arguments, or the program alone where it signs in on start.
  List<String> get _program {
    final recipe = AgentCatalog.builtIn.byId(widget.agentId)?.recipe;
    if (recipe == null) return const ['claude', 'auth', 'login', '--claudeai'];
    return [recipe.executable, ...recipe.signInArgs];
  }

  void _openPage(String url) {
    if (!mounted) return;
    final open = widget.openPage;
    unawaited(
      open != null ? open(context, url) : openAgentSignInPage(context, url),
    );
  }

  void _shellChanged() {
    final shell = _shell;
    if (shell == null || !mounted) return;
    if (shell.state == LocalShellState.exited && !_checking && !_notYet) {
      unawaited(_check());
    } else {
      setState(() {});
    }
  }

  /// Claude's sign-in ended: ask it whether it is signed in now.
  Future<void> _check() async {
    setState(() => _checking = true);
    await widget.agents.recheckAgentSignIn(widget.agentId);
    if (!mounted) return;
    final row = widget.agents.agentRows
        .where((row) => row.id == widget.agentId)
        .firstOrNull;
    final signedIn =
        widget.agents.agentSignInState(widget.agentId)?.phase ==
            AgentSignInPhase.signedIn ||
        (row?.chatSelectable ?? false);
    if (signedIn) {
      Navigator.of(context).pop(true);
      return;
    }
    setState(() {
      _checking = false;
      _notYet = true;
    });
  }

  Future<void> _again() async {
    final shell = _shell;
    if (shell != null) {
      shell.removeListener(_shellChanged);
      await _sessions.endSignIn(shell);
    }
    if (mounted) _start();
  }

  Future<void> _paste() async {
    final shell = _shell;
    final data = await Clipboard.getData(Clipboard.kTextPlain);
    final text = data?.text;
    if (shell == null || text == null || text.isEmpty) return;
    shell.terminal.paste(text.trim());
    _focus.requestFocus();
  }

  @override
  void dispose() {
    final shell = _shell;
    if (shell != null) {
      shell.removeListener(_shellChanged);
      unawaited(_sessions.endSignIn(shell));
    }
    _focus.dispose();
    _selection.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = _l10n;
    final tokens = KitTokens.of(context);
    final name = KitBidi.auto(widget.agentName);
    final shell = _shell;
    final running = shell?.running ?? false;
    return KitScreen(
      topBar: KitTopBar(
        title: l10n.agentsSignInTitle(name),
        menuKey: const ValueKey('agents-sign-in-terminal-menu'),
        menu: [
          if (running)
            KitMenuItem(
              key: const ValueKey('agents-sign-in-terminal-paste'),
              label: l10n.agentsSignInPaste,
              icon: AppIconography.paste,
              onSelected: () => unawaited(_paste()),
            ),
        ],
      ),
      header: [
        Padding(
          padding: EdgeInsetsDirectional.fromSTEB(
            tokens.gutter,
            tokens.space1,
            tokens.gutter,
            tokens.space2,
          ),
          child: KitText(
            widget.agentId == 'claude'
                ? l10n.agentsSignInTerminalIntro(name)
                : l10n.agentsSignInTerminalIntroOther(name),
            key: const ValueKey('agents-sign-in-terminal-intro'),
            tone: KitTextTone.secondary,
          ),
        ),
      ],
      loading:
          shell == null || shell.state == LocalShellState.starting || _checking,
      loadingLabel: _checking
          ? l10n.agentsSignInChecking
          : l10n.localTerminalStarting,
      body: shell == null ? const SizedBox.shrink() : _body(shell, name),
    );
  }

  Widget _body(LocalShell shell, String name) {
    final l10n = _l10n;
    if (shell.state == LocalShellState.failed) {
      return KitStateView(
        key: const ValueKey('agents-sign-in-terminal-failed'),
        icon: AppIconography.error,
        tone: AppStatusTone.failure,
        title: l10n.localTerminalFailedTitle,
        body: l10n.localTerminalFailedBody,
        primary: KitAction(
          key: const ValueKey('agents-sign-in-terminal-again'),
          label: l10n.agentsSignInStart(name),
          onPressed: () => unawaited(_again()),
        ),
        details: shell.failure,
      );
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Expanded(
          child: KitTerminalView.live(
            terminal: shell.terminal,
            semanticsLabel: l10n.localTerminalSemantics,
            viewKey: const ValueKey('agents-sign-in-terminal-view'),
            controller: _selection,
            focusNode: _focus,
            readOnly: !shell.running,
            keys: shell.running ? _keys : null,
            keysKey: const ValueKey('agents-sign-in-terminal-keys'),
          ),
        ),
        if (_notYet)
          SafeArea(
            top: false,
            child: KitStateView(
              key: const ValueKey('agents-sign-in-terminal-not-yet'),
              size: KitStateSize.inline,
              icon: AppIconography.terminal,
              title: l10n.agentsSignInTerminalNotYet(name),
              primary: KitAction(
                key: const ValueKey('agents-sign-in-terminal-again'),
                label: l10n.agentsSignInStart(name),
                onPressed: () => unawaited(_again()),
              ),
            ),
          ),
      ],
    );
  }
}
