import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../domain/chat_feed.dart';
import '../../../domain/phone_agents_source.dart';
import '../../../domain/server_gateway.dart' show WorkspaceProject;
import '../../../l10n/app_localizations.dart';
import '../../app_iconography.dart';
import '../../kit/kit.dart';
import '../../widgets/product_states.dart' show productErrorText;
import '../agents/agent_sheet.dart';
import '../agents/agents_text.dart';
import 'chats_host.dart';
import 'chats_project_sheet.dart' show chatsProjectIcon;

/// Opens the start screen for a new chat. [directory] is the project to
/// start in; null uses the last project the person used on this server.
Future<void> showNewChat(BuildContext context, {String? directory}) =>
    pushKitPage<void>(context, (_) => NewChatScreen(directory: directory));

/// "What should we work on?": the project as a chip, the composer under it.
/// Sending starts the conversation in the chosen project with the typed text
/// as its first message and replaces this screen with that conversation.
class NewChatScreen extends ConsumerStatefulWidget {
  const NewChatScreen({super.key, this.directory});

  final String? directory;

  @override
  ConsumerState<NewChatScreen> createState() => _NewChatScreenState();
}

final _noChanges = ValueNotifier<int>(0);

class _NewChatScreenState extends ConsumerState<NewChatScreen> {
  final _text = TextEditingController();
  final _focus = FocusNode();
  String? _directory;
  bool _resolved = false;
  bool _sending = false;
  String? _failure;

  /// The project a separate copy can be made of, for [_copyFor]'s folder;
  /// null hides the option.
  WorkspaceProject? _copyProject;
  String? _copyFor;

  @override
  void dispose() {
    _text.dispose();
    _focus.dispose();
    super.dispose();
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

  Future<void> _startCopy(ChatsHost host) async {
    final project = _copyProject;
    if (_sending || project == null) return;
    final id = await host.startSeparateCopy(context, project);
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
    });
  }

  Future<void> _send(ChatsHost host) async {
    final directory = _directory;
    final text = _text.text.trim();
    if (_sending || directory == null || text.isEmpty) return;
    final l10n = AppLocalizations.of(context);
    setState(() {
      _sending = true;
      _failure = null;
    });
    try {
      final id = await host.startChat(
        directory,
        agentId: host.agents?.selectedChatAgentId ?? openCodeChatAgentId,
        prompt: text,
      );
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
      // Plain words; the draft stays in the field.
      final words = productErrorText(error, l10n: l10n);
      setState(() {
        _sending = false;
        _failure = words.trim().isEmpty ? l10n.chatsNewFailed : words;
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

  Widget _screen(BuildContext context, ChatsHost host) {
    final source = host.source;
    if (!_resolved) {
      _resolved = true;
      _directory = _initialDirectory(source);
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

    // The agent chip shows only where this phone can run other agents.
    final agents = host.agents;
    final showAgent = agents != null && agents.phoneAgentsAvailable;
    final agentChoice = !showAgent
        ? null
        : agents.chatAgentChoices
              .where((choice) => choice.agentId == agents.selectedChatAgentId)
              .firstOrNull;
    final agentName = agentChoice?.name ?? 'OpenCode';

    final chip = Wrap(
      alignment: WrapAlignment.center,
      crossAxisAlignment: WrapCrossAlignment.center,
      spacing: tokens.space2,
      children: [
        KitChip.action(
          key: const ValueKey('chats-new-project'),
          label: name == null ? l10n.chatsNewChooseProject : KitBidi.auto(name),
          icon: name == null
              ? AppIconography.folderOpen
              : chatsProjectIcon(summary?.kind),
          onPressed: () => unawaited(_changeProject(host)),
        ),
        if (summary?.isGit ?? false)
          KitChip(label: l10n.phoneScanGit, icon: AppIconography.branch),
        if (showAgent)
          KitChip.summary(
            key: const ValueKey('chats-new-agent'),
            label: KitBidi.auto(agentName),
            icon: agentIcon(agentChoice?.iconKey ?? 'opencode'),
            expanded: false,
            onPressed: () => unawaited(showAgentSheet(context)),
          ),
      ],
    );

    return KitScreen(
      topBar: KitTopBar(title: l10n.chatsHomeNewChat),
      body: Center(
        child: ListView(
          shrinkWrap: true,
          padding: EdgeInsets.all(tokens.gutter),
          children: [
            KitText(
              l10n.chatsNewPrompt,
              role: KitTextRole.title,
              textAlign: TextAlign.center,
            ),
            SizedBox(height: tokens.space3),
            Center(child: chip),
            // Quiet, and only where the server can make a separate copy.
            if (_copyProject != null) ...[
              SizedBox(height: tokens.space1),
              Center(
                child: KitChip.action(
                  key: const ValueKey('chats-new-separate-copy'),
                  label: l10n.chatsNewSeparateCopy,
                  icon: AppIconography.branch,
                  onPressed: () => unawaited(_startCopy(host)),
                ),
              ),
            ],
          ],
        ),
      ),
      bottom: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        spacing: tokens.space2,
        children: [
          KitComposerStatusStrip(
            chips: const [],
            model: host.modelChip(context),
          ),
          KitComposer(
            composerKey: const ValueKey('chats-new-composer'),
            fieldKey: const ValueKey('chats-new-field'),
            sendKey: const ValueKey('chats-new-send'),
            controller: _text,
            focusNode: _focus,
            hint: l10n.chatUiAskOpenCode,
            fieldLabel: l10n.chatUiAskOpenCode,
            readOnlyReason: directory == null ? l10n.chatsNewNeedProject : null,
            sending: _sending,
            onSend: () => unawaited(_send(host)),
            failure: _failure == null
                ? null
                : KitComposerFailure(
                    words: _failure!,
                    onRetry: () => unawaited(_send(host)),
                  ),
          ),
        ],
      ),
    );
  }

  static String _basename(String directory) {
    final parts = directory.split('/').where((part) => part.isNotEmpty);
    return parts.isEmpty ? directory : parts.last;
  }
}
