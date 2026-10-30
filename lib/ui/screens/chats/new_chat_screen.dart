import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../builtin/git_link_repair.dart' show GitRepairActions;
import '../../../domain/chat_feed.dart';
import '../../../domain/phone_agents_source.dart';
import '../../../domain/product_failure.dart';
import '../../../domain/server_gateway.dart' show WorkspaceProject;
import '../../../domain/agent_persona.dart';
import '../../../domain/agent_tools/agent_certification.dart';
import '../../../domain/phone_agents.dart' show AgentRow;
import '../../../l10n/app_localizations.dart';
import '../../app_iconography.dart';
import '../../kit/kit.dart';
import '../../widgets/product_states.dart'
    show productErrorDetails, productErrorText;
import '../agents/agent_model_sheet.dart';
import '../../agents/agent_persona_text.dart';
import '../agents/agent_sheet.dart';
import '../agents/agents_text.dart' show agentChoosable;
import 'chats_host.dart';
import 'claude_import_screen.dart';
import 'git_repair_sheet.dart';

/// Opens the start screen for a new chat. [directory] is the project to
/// start in; null uses the last project the person used on this server.
///
/// [initialText] fills the composer so the person can read, change and send
/// it (a FactsBook offer's summary); nothing is sent until they do.
Future<void> showNewChat(
  BuildContext context, {
  String? directory,
  String initialText = '',
  VoidCallback? onSent,
}) => pushKitPage<void>(
  context,
  (_) => NewChatScreen(
    directory: directory,
    initialText: initialText,
    onSent: onSent,
  ),
  // Making something new: a full-screen page that closes, not one that
  // goes back.
  fullscreenDialog: true,
);

/// New conversation: a centred title with Close, the glowing question field
/// ("What would you like to build?"), the agents this connection can start
/// as large cards, one Model and Project group, and one pinned
/// `Start with <agent>` pill. Sending starts the conversation in the chosen
/// project with the typed text as its first message and replaces this
/// screen with that conversation.
class NewChatScreen extends ConsumerStatefulWidget {
  const NewChatScreen({
    super.key,
    this.directory,
    this.initialText = '',
    this.onSent,
  });

  final String? directory;

  /// The composer starts with this text, unsent.
  final String initialText;

  /// Called once the first message has been sent and the chat started.
  final VoidCallback? onSent;

  @override
  ConsumerState<NewChatScreen> createState() => _NewChatScreenState();
}

final _noChanges = ValueNotifier<int>(0);

/// The unsent words' draft target for [directory]'s project
/// (`oc.draft.new-chat.<project>.<profileId>`, swept with the profile).
String newChatDraftTarget(String? directory) =>
    'new-chat.${_draftProjectKey(directory)}';

/// The project's folder in URL-safe base64, so its dots and slashes never
/// split the key; `none` while no project is chosen.
String _draftProjectKey(String? directory) => directory == null
    ? 'none'
    : base64Url.encode(utf8.encode(directory)).replaceAll('=', '');

class _NewChatScreenState extends ConsumerState<NewChatScreen> {
  final _text = TextEditingController();
  final _focus = FocusNode();
  String? _directory;
  bool _resolved = false;
  bool _sending = false;
  String? _failure;

  /// The redacted technical text behind [_failure], for its Details; null
  /// when the failure has none.
  String? _failureDetails;

  /// The repair of the project's Git when that is what failed; it takes
  /// Retry's place on the failure (retrying would fail the same way).
  GitRepairActions? _gitRepair;

  /// The project a separate copy can be made of, for [_copyFor]'s folder;
  /// null hides the option.
  WorkspaceProject? _copyProject;
  String? _copyFor;

  /// "In a separate copy" is on in the choices sheet: Start makes the copy.
  bool _copyOn = false;

  /// The phone agent whose model list was already asked for.
  String? _warmedAgent;

  /// Where the typed words are kept until they are sent: Back, a swipe or
  /// Close never loses them. Null while there is no project's draft to keep
  /// (no server profile, or an offered text, which is not the person's own).
  KitDraft? _draft;

  @override
  void initState() {
    super.initState();
    _text.text = widget.initialText;
    _text.addListener(_saveDraft);
    // The agent chip shows the saved agent; read the rows so it is checked
    // against what this phone can run now (and a stopped helper restarts).
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      final agents = ref.read(chatsHostProvider).agents;
      if (agents != null && agents.phoneAgentsAvailable) {
        unawaited(agents.refreshAgentRows());
      }
    });
  }

  @override
  void dispose() {
    _text.removeListener(_saveDraft);
    _text.dispose();
    _focus.dispose();
    super.dispose();
  }

  /// Keeps the draft for [directory]'s project: what the field holds moves
  /// there (the person changed the project under their words), or, when it
  /// is empty, that project's saved words come back.
  void _useDraftFor(ChatsHost host, String? directory) {
    final profileId = host.profileId;
    if (widget.initialText.isNotEmpty ||
        profileId == null ||
        profileId.isEmpty) {
      return;
    }
    final before = _draft;
    final draft = KitDraft(
      target: newChatDraftTarget(directory),
      profileId: profileId,
      controller: _text,
    );
    if (before?.key == draft.key) return;
    _draft = draft;
    if (_text.text.isEmpty) {
      unawaited(draft.restore());
    } else {
      unawaited(() async {
        await before?.clear();
        await draft.save();
      }());
    }
  }

  /// The words last handed to [_draft], so a cursor move writes nothing.
  String? _savedText;

  /// Saves every change of the words (an emptied field forgets them).
  void _saveDraft() {
    final draft = _draft;
    if (draft == null || _sending || _text.text == _savedText) return;
    _savedText = _text.text;
    unawaited(draft.save());
  }

  /// The project the chip starts on: the one asked for, else the last used;
  /// never a temporary folder.
  String? _initialDirectory(ChatFeedSource source) {
    for (final candidate in [
      widget.directory,
      source.lastUsedProjectDirectory,
    ]) {
      if (candidate != null &&
          candidate.trim().isNotEmpty &&
          !source.isTemporaryProject(candidate)) {
        return candidate;
      }
    }
    return null;
  }

  /// Asks, once per folder, whether this server can make a separate copy.
  void _resolveCopy(ChatsHost host, String? directory) {
    if (directory == _copyFor) return;
    _copyFor = directory;
    _copyProject = null;
    if (directory == null) return;
    unawaited(() async {
      final project = await host.separateCopyProject(directory);
      if (!mounted || _copyFor != directory) return;
      setState(() => _copyProject = project);
    }());
  }

  /// Start: in a separate copy when that is switched on and still possible
  /// (OpenCode only), else straight into the project.
  Future<void> _start(ChatsHost host) {
    final agents = host.agents;
    final chosen = agents != null && agents.phoneAgentsAvailable
        ? agents.selectedChatAgentId
        : null;
    final copy =
        _copyOn &&
        _copyProject != null &&
        (chosen == null || chosen == openCodeChatAgentId);
    return copy ? _startCopy(host) : _send(host);
  }

  Future<void> _startCopy(ChatsHost host) async {
    final project = _copyProject;
    if (_sending || project == null) return;
    setState(() {
      _failure = null;
      _failureDetails = null;
      _gitRepair = null;
    });
    final id = await host.startSeparateCopy(
      context,
      project,
      initialTask: _text.text.trim(),
    );
    if (id == null) return;
    // The words went out as the copy's first task.
    await _draft?.clear();
    if (!mounted) return;
    final problem = await host.showStartedChat(context, sessionID: id);
    if (!mounted || problem == null) return;
    setState(() => _failure = problem);
  }

  /// "Import from Claude Code": the person picks a conversation they began
  /// in Claude Code; it is brought in and opened here.
  Future<void> _importFromClaude(ChatsHost host) async {
    final gateway = host.conversationImport;
    if (gateway == null) return;
    final id = await showClaudeImport(context, gateway);
    if (!mounted || id == null) return;
    final problem = await host.showStartedChat(context, sessionID: id);
    if (!mounted || problem == null) return;
    setState(() => _failure = problem);
  }

  Future<void> _changeProject(ChatsHost host) async {
    final directory = await host.openProject(context);
    if (!mounted || directory == null) return;
    if (host.source.isTemporaryProject(directory)) return;
    setState(() {
      _directory = directory;
      _failure = null;
      _failureDetails = null;
      _gitRepair = null;
    });
    _useDraftFor(host, directory);
  }

  Future<void> _send(ChatsHost host) async {
    final directory = _directory;
    final text = _text.text.trim();
    if (_sending || directory == null || text.isEmpty) return;
    final l10n = AppLocalizations.of(context);
    setState(() {
      _sending = true;
      _failure = null;
      _failureDetails = null;
      _gitRepair = null;
    });
    try {
      final id = await host.startChat(
        directory,
        agentId: host.agents?.selectedChatAgentId ?? openCodeChatAgentId,
        prompt: text,
      );
      // Sent: the words are the conversation's first message now.
      await _draft?.clear();
      widget.onSent?.call();
      if (!mounted) return;
      final problem = await host.showStartedChat(context, sessionID: id);
      if (!mounted) return;
      if (problem != null) {
        setState(() {
          _sending = false;
          _failure = problem;
        });
      }
    } catch (error) {
      if (!mounted) return;
      // Plain words; the draft stays in the field. The redacted technical
      // text sits under Details (the host also keeps it for a problem
      // report) and goes to the device log.
      final details = productErrorDetails(error);
      debugPrint('oc.chat-start failed: $details');
      final repair =
          ProductFailure.from(error).category ==
              ProductFailureCategory.projectGit
          ? host.projectGitRepair(directory)
          : null;
      final words = repair != null
          ? l10n.gitRepairFailureWords
          : productErrorText(error, l10n: l10n);
      setState(() {
        _sending = false;
        _failure = words.trim().isEmpty ? l10n.chatsNewFailed : words;
        _failureDetails = details;
        _gitRepair = repair;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final host = ref.watch(chatsHostProvider);
    return ListenableBuilder(
      listenable: host.listenable ?? _noChanges,
      builder: (context, _) => _screen(context, host),
    );
  }

  /// An agent's name: its persona's, else the one the connection gave.
  String _agentLabel(
    AppLocalizations l10n,
    String agentIdOrName,
    String given,
  ) {
    final persona = AgentPersona.of(agentIdOrName);
    return persona.id == AgentPersonaId.other ? given : persona.name(l10n);
  }

  /// Chooses an agent card; a refusal is said in plain words.
  Future<void> _selectAgent(PhoneAgentsSource agents, String id) async {
    try {
      await agents.selectChatAgent(id);
    } catch (error) {
      if (!mounted) return;
      final l10n = AppLocalizations.of(context);
      setState(() {
        _failure = productErrorText(error, l10n: l10n);
        _failureDetails = productErrorDetails(error);
        _gitRepair = null;
      });
    }
  }

  Widget _screen(BuildContext context, ChatsHost host) {
    final source = host.source;
    if (!_resolved) {
      _resolved = true;
      _directory = _initialDirectory(source);
      _useDraftFor(host, _directory);
    }
    final l10n = AppLocalizations.of(context);
    final tokens = KitTokens.of(context);
    final directory = _directory;
    _resolveCopy(host, directory);
    final summary = directory == null
        ? null
        : source.projectSummaries
              .where((project) => project.directory == directory)
              .firstOrNull;
    final name = directory == null
        ? null
        : summary?.name ?? _basename(directory);

    // Agents on this phone: only those this connection can really start
    // (ready and certified, the agent sheet's own rule) are choices. One
    // that is certified but not ready yet and has a way forward (install,
    // phone check, sign in, resume) is a dimmed card that opens its setup;
    // the rest are left out, since setting them up would still not make
    // them a choice here. The agent already chosen (its sheet's "Use …"
    // chooses a ready agent before it is certified) always shows, chosen,
    // so Start names what it starts (issue #95).
    final agents = host.agents;
    final phoneAgents = agents != null && agents.phoneAgentsAvailable;
    final choices = !phoneAgents
        ? <ChatAgentChoice>[]
        : [
            for (final choice in agents.chatAgentChoices)
              if (choice.row == null ||
                  choice.selected ||
                  agentChoosable(choice.row!))
                choice,
          ];
    final shownIds = {for (final choice in choices) choice.agentId};
    // The registry's own order (Claude Code, Codex, Gemini, Kilo, Cline,
    // OpenCode, then anything else as the source gave it), ready before set up.
    int rank(String agentId, String iconKey) {
      final persona = AgentPersona.of(
        AgentPersona.of(agentId).id == AgentPersonaId.other ? iconKey : agentId,
      );
      final at = AgentPersona.all.indexWhere((p) => p.id == persona.id);
      return at < 0 ? AgentPersona.all.length : at;
    }

    final setupRows = !phoneAgents
        ? <AgentRow>[]
        : [
            for (final row in agents.agentRows)
              if ((row.setupVisible || row.chatVisible) &&
                  !shownIds.contains(row.id) &&
                  row.fixAction != null &&
                  AgentCertificationMatrix.bundled.certifiedForChat(row.id))
                row,
          ];
    choices.sort(
      (a, b) =>
          rank(a.agentId, a.iconKey).compareTo(rank(b.agentId, b.iconKey)),
    );
    setupRows.sort(
      (a, b) => rank(a.id, a.iconKey).compareTo(rank(b.id, b.iconKey)),
    );
    // A single agent (a remote server's own) never shows one lonely card.
    final grid = choices.length + setupRows.length >= 2;
    final agentChoice = !phoneAgents
        ? null
        : choices.where((choice) => choice.selected).firstOrNull;
    final given = agentChoice?.name ?? host.startAgentName ?? 'OpenCode';
    final agentName = _agentLabel(l10n, agentChoice?.agentId ?? given, given);
    final otherAgent =
        agentChoice != null && agentChoice.agentId != openCodeChatAgentId;
    // A phone agent's helper lists its runtimes slowly the first time (about
    // 10 s): ask while the person types, so Start doesn't wait for it. The
    // list also names the agent's own default, which the Model row then
    // shows instead of "Default model".
    final chosenAgent = agentChoice?.agentId;
    if (agents != null &&
        chosenAgent != null &&
        chosenAgent != openCodeChatAgentId &&
        chosenAgent != _warmedAgent) {
      _warmedAgent = chosenAgent;
      unawaited(
        agents.agentModels(chosenAgent).then((models) {
          rememberAgentModels(chosenAgent, models);
          if (mounted) setState(() {});
        }, onError: (Object _) {}),
      );
    }

    // Why Start cannot run yet, in words; null when it can.
    final blocked = directory == null
        ? l10n.chatsNewNeedProject
        // Agents on this phone work in the shared project space only.
        : chosenAgent != null &&
              chosenAgent != openCodeChatAgentId &&
              !directory.startsWith('/root/projects/')
        ? l10n.chatsNewAgentNeedsProjectsFolder(agentName)
        : null;

    Widget card(
      String id,
      String serverName,
      String iconKey,
      KitAgentChoiceState state,
      VoidCallback onPressed,
    ) => KitAgentChoice(
      tappableKey: ValueKey('chats-new-agent-$id'),
      persona: AgentPersona.of(
        AgentPersona.of(id).id == AgentPersonaId.other ? iconKey : id,
      ),
      otherName: serverName,
      state: state,
      onPressed: onPressed,
    );
    final gutter = EdgeInsetsDirectional.symmetric(horizontal: tokens.gutter);
    final cards = <Widget>[
      if (grid) ...[
        for (final choice in choices)
          card(
            choice.agentId,
            choice.name,
            choice.iconKey,
            choice.selected
                ? KitAgentChoiceState.selected
                : KitAgentChoiceState.available,
            () => unawaited(_selectAgent(agents!, choice.agentId)),
          ),
        for (final row in setupRows)
          card(
            row.id,
            row.name,
            row.iconKey,
            KitAgentChoiceState.needsSetup,
            () => unawaited(
              showAgentSheet(
                context,
                agentId: row.id,
                step: agentSetupStep(row),
              ),
            ),
          ),
      ],
    ];
    return KitScreen(
      topBar: KitTopBar(title: l10n.chatsHomeNewChat, centred: true),
      // A wide window keeps the field, the rows and Start at a reading
      // width, centred, so "Model" sits near its value (audit P2-7).
      width: KitScreenWidth.reading,
      // One edge for everything on the page (audit P3-13): the field, the
      // cards and the notice sit on the gutter, and the row group brings its
      // own gutter, so its icons and the reason line under it start there
      // too (the Settings pattern).
      body: ListView(
        // A little more room on top, so the field's halo fades out before
        // the list's edge (it is cut there when the list scrolls).
        padding: EdgeInsetsDirectional.only(
          top: tokens.space6,
          bottom: tokens.gutter,
        ),
        children: [
          Padding(
            padding: gutter,
            child: KitStartBox(
              fieldKey: const ValueKey('chats-new-field'),
              controller: _text,
              focusNode: _focus,
              question: l10n.chatsNewBuildPrompt,
              enabled: !_sending,
              autofocus: true,
              // Two lines beside the agent cards (they are the hero); taller
              // when it is the screen's one choice.
              minLines: grid ? 2 : 4,
            ),
          ),
          SizedBox(height: tokens.space4),
          // A single agent (a remote server's own) shows no cards and no
          // line: the button names it.
          if (grid) ...[
            Padding(
              padding: gutter,
              child: KitAgentGrid(children: cards),
            ),
            SizedBox(height: tokens.space4),
          ],
          KitRowGroup(
            children: [
              KitRow(
                key: const ValueKey('chats-new-model'),
                leading: KitRow.icon(context, AppIconography.model),
                title: l10n.chatsNewModelRow,
                // An agent on this phone has its own models; OpenCode's
                // picker would list the server's instead.
                trailing: KitRowValue(
                  otherAgent
                      ? agentModelLabel(l10n, agents!, agentChoice.agentId)
                      : host.modelValue(context),
                  prominent: true,
                ),
                onTap: () => unawaited(
                  otherAgent
                      ? showAgentModelSheet(
                          context,
                          agents: agents!,
                          agentId: agentChoice.agentId,
                        )
                      : host.chooseModel(context),
                ),
              ),
              KitRow(
                key: const ValueKey('chats-new-project'),
                leading: KitRow.icon(context, AppIconography.files),
                title: l10n.chatsNewProjectRow,
                trailing: KitRowValue(
                  name == null
                      ? l10n.chatsNewChooseProject
                      : KitBidi.auto(name),
                  prominent: true,
                ),
                onTap: () => unawaited(_changeProject(host)),
              ),
              // Only where the server can make a separate copy: the copy is
              // OpenCode's, so not while another agent is chosen.
              if (_copyProject != null && !otherAgent)
                KitSwitchRow(
                  switchKey: const ValueKey('chats-new-separate-copy'),
                  leading: KitRow.icon(context, AppIconography.fork),
                  title: l10n.chatsNewSeparateCopy,
                  supporting: l10n.chatsNewSeparateCopyDetail,
                  value: _copyOn,
                  onChanged: (on) => setState(() => _copyOn = on),
                ),
              // Where the agent has its own app, a conversation begun there
              // can be carried on from here.
              if (host.conversationImport != null)
                KitRow(
                  key: const ValueKey('chats-new-import-claude'),
                  leading: KitRow.icon(context, AppIconography.download),
                  title: l10n.claudeImportTitle,
                  onTap: () => unawaited(_importFromClaude(host)),
                ),
            ],
          ),
          // Why Start is off, on the group's own edge.
          if (blocked != null) KitGroupNote(message: blocked),
          if (_failure != null) ...[
            SizedBox(height: tokens.space3),
            Padding(
              padding: gutter,
              child: KitNotice(
                messageKey: const ValueKey('chats-new-failure'),
                message: _failure!,
                icon: AppIconography.info,
                actions: [
                  if (_gitRepair != null)
                    KitAction(
                      key: const ValueKey('chats-new-git-repair'),
                      label: l10n.gitRepairAction,
                      onPressed: () =>
                          unawaited(_repair(host, name ?? directory!)),
                    )
                  else
                    KitAction(
                      label: l10n.kitComposerRailRetry,
                      onPressed: () => unawaited(_start(host)),
                    ),
                  if (_failureDetails != null)
                    KitAction(
                      label: l10n.kitDetails,
                      onPressed: () => unawaited(_showFailureDetails()),
                    ),
                ],
              ),
            ),
          ],
        ],
      ),
      // The one solid-mint control: it names the agent it starts, rides above
      // the keyboard and wakes as soon as there is a word to send.
      bottom: ListenableBuilder(
        listenable: _text,
        builder: (context, _) => KitButton.primary(
          key: const ValueKey('chats-new-send'),
          hero: true,
          label: l10n.chatsNewStartWith(agentName),
          working: _sending,
          onPressed: blocked != null || _text.text.trim().isEmpty || _sending
              ? null
              : () => unawaited(_start(host)),
        ),
      ),
    );
  }

  /// Repairs the project's Git in a sheet; once Git can read it, the
  /// conversation starts with the message still in the field.
  Future<void> _repair(ChatsHost host, String project) async {
    final repair = _gitRepair;
    if (repair == null || _sending) return;
    final repaired = await showGitRepairSheet(
      context,
      project: project,
      actions: repair,
    );
    if (!mounted || !repaired) return;
    await _send(host);
  }

  /// The failure in plain words, its technical text folded under Details.
  Future<void> _showFailureDetails() {
    final l10n = AppLocalizations.of(context);
    return showKitAlert(
      context,
      title: l10n.chatsNewFailedTitle,
      body:
          '${_failure ?? l10n.chatsNewFailed}\n\n${l10n.chatsNewFailedReportHint}',
      details: [
        KitTechnicalValue(l10n.productErrorDetailsLabel, _failureDetails ?? ''),
      ],
      icon: AppIconography.error,
      alertKey: const ValueKey('chats-new-failure-details'),
    );
  }

  static String _basename(String directory) {
    final parts = directory.split('/').where((part) => part.isNotEmpty);
    return parts.isEmpty ? directory : parts.last;
  }
}
