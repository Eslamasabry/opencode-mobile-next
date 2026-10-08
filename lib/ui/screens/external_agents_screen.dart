import 'dart:async';

import 'package:flutter/material.dart';
import 'package:uuid/uuid.dart';

import '../../domain/external_agent.dart';
import '../../l10n/app_localizations.dart';
import '../../state/external_agents.dart';
import '../app_iconography.dart';
import '../app_theme.dart' show AppStatusTone;
import '../kit/kit.dart';
import '../widgets/external_link.dart';

// External agents (A2A): the list, Add agent, an agent's page and one task.
// Built from kit parts only (screen-library-2). Dedicated A2A identities: no
// project, file or terminal capability is implied.

AppLocalizations _l(BuildContext context) =>
    lookupAppLocalizations(Localizations.localeOf(context));

String externalTaskLabel(AppLocalizations l, ExternalTaskState state) =>
    switch (state) {
      ExternalTaskState.submitted => l.a2aSubmitted,
      ExternalTaskState.working => l.a2aWorking,
      ExternalTaskState.inputRequired => l.a2aInputRequired,
      ExternalTaskState.authRequired => l.a2aAuthRequired,
      ExternalTaskState.completed => l.a2aCompleted,
      ExternalTaskState.failed => l.a2aFailed,
      ExternalTaskState.canceled => l.a2aCanceled,
      ExternalTaskState.rejected => l.a2aRejected,
      ExternalTaskState.unknown => l.a2aUnknown,
    };

String _issueText(AppLocalizations l, ExternalAgentIssue issue) =>
    switch (issue) {
      ExternalAgentIssue.address => l.a2aAddressError,
      ExternalAgentIssue.unsupported => l.a2aUnsupported,
      ExternalAgentIssue.authentication => l.a2aAuthenticationError,
      ExternalAgentIssue.unavailable => l.a2aUnavailable,
      ExternalAgentIssue.invalidResponse => l.a2aInvalidResponse,
      ExternalAgentIssue.uncertain => l.a2aUncertain,
      ExternalAgentIssue.storage => l.a2aStorageError,
      ExternalAgentIssue.scope => l.a2aScopeError,
    };

/// A record's one state word, shared by its row and its page.
String _recordWord(AppLocalizations l, ExternalTaskRecord record) =>
    record.uncertain
    ? l.a2aDeliveryUnconfirmed
    : record.task == null
    ? l.a2aDraft
    : externalTaskLabel(l, record.task!.state);

/// The owner's one order (2026-09-27): needs you (a question, a sign-in, a
/// delivery to check), then running, then the rest newest first.
int _urgency(ExternalTaskRecord record) {
  if (record.uncertain) return 0;
  return switch (record.task?.state) {
    ExternalTaskState.inputRequired || ExternalTaskState.authRequired => 0,
    ExternalTaskState.submitted || ExternalTaskState.working => 1,
    _ => 2,
  };
}

bool _needsYou(ExternalTaskRecord record) => _urgency(record) == 0;

KitTaskState _mark(ExternalTaskRecord record) {
  if (_needsYou(record)) return KitTaskState.needsYou;
  return switch (record.task?.state) {
    null => KitTaskState.waiting,
    ExternalTaskState.submitted ||
    ExternalTaskState.working => KitTaskState.working,
    ExternalTaskState.completed => KitTaskState.done,
    ExternalTaskState.failed ||
    ExternalTaskState.rejected => KitTaskState.failed,
    _ => KitTaskState.stopped,
  };
}

List<ExternalTaskRecord> _ordered(List<ExternalTaskRecord> records) =>
    [...records]..sort((a, b) {
      final byUrgency = _urgency(a).compareTo(_urgency(b));
      return byUrgency != 0 ? byUrgency : b.created.compareTo(a.created);
    });

/// A record's words for its row and page title: what the person wrote.
String _recordTitle(AppLocalizations l, ExternalTaskRecord record) {
  if (record.title.trim().isNotEmpty) return record.title;
  final draft = record.draft.trim();
  if (draft.isNotEmpty) return draft.split('\n').first;
  return l.externalAgentsUntitledTask;
}

String _host(String url) => Uri.tryParse(url)?.host ?? url;

/// The one removal question, shared by the list's row menu and the agent's
/// page (map: one sheet for both), naming the agent.
Future<bool> _confirmRemove(BuildContext context, ExternalAgentProfile p) {
  final l = _l(context);
  return showKitConfirm(
    context,
    kind: KitConfirmKind.destructive,
    icon: AppIconography.delete,
    title: l.externalAgentsRemoveTitle(p.card.name),
    body: l.externalAgentsRemoveBody,
    confirmLabel: l.a2aDeleteLocal,
    confirmKey: const ValueKey('external-agent-remove-confirm'),
  );
}

Widget _issueNotice(BuildContext context, ExternalAgentIssue issue) =>
    KitNotice(
      key: const ValueKey('external-agent-issue'),
      message: _issueText(_l(context), issue),
      tone: AppStatusTone.failure,
      icon: AppIconography.warning,
    );

Widget _gap(BuildContext context) =>
    SizedBox(height: KitTokens.of(context).space5);

// --- External agents ---------------------------------------------------------

/// The saved agents, one list by urgency: a removal that did not finish and
/// agents with a task that needs a reply first, each row saying so.
class ExternalAgentsScreen extends StatefulWidget {
  final ExternalAgentStore store;
  final ExternalAgentGateway Function()? gatewayFactory;

  /// Body only, for the External agents tab of Settings › Tools: no top bar
  /// of its own, and Add is a button at the head of the list.
  final bool embedded;
  const ExternalAgentsScreen({
    super.key,
    required this.store,
    this.gatewayFactory,
    this.embedded = false,
  });
  @override
  State<ExternalAgentsScreen> createState() => _ExternalAgentsScreenState();
}

class _ExternalAgentsScreenState extends State<ExternalAgentsScreen> {
  ExternalAgentIssue? _issue;
  bool _busy = false;

  ExternalAgentGateway _gateway() =>
      widget.gatewayFactory?.call() ?? createExternalAgentGateway();

  Future<void> _add() async {
    await pushKitPage<void>(
      context,
      (_) => _AddAgentScreen(store: widget.store, gateway: _gateway()),
    );
    if (mounted) setState(() {});
  }

  Future<void> _open(ExternalAgentProfile profile) async {
    await pushKitPage<void>(
      context,
      (_) => ExternalAgentDetailScreen(
        store: widget.store,
        profile: profile,
        gatewayFactory: widget.gatewayFactory,
      ),
    );
    if (mounted) setState(() {});
  }

  Future<void> _delete(ExternalAgentProfile profile) async {
    if (!await _confirmRemove(context, profile) || !mounted) return;
    setState(() {
      _busy = true;
      _issue = null;
    });
    try {
      await widget.store.delete(profile.id);
    } on ExternalAgentException catch (e) {
      _issue = e.issue;
    }
    if (mounted) setState(() => _busy = false);
  }

  /// Tasks that need a reply, per agent; unreadable records count none.
  int _needsCount(ExternalAgentProfile profile) {
    if (profile.deleting) return 0;
    try {
      return widget.store.tasks(profile.id).where(_needsYou).length;
    } on ExternalAgentException {
      return 0;
    }
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: widget.store,
    builder: (context, _) => _buildScreen(context),
  );

  Widget _buildScreen(BuildContext context) {
    final l = _l(context);
    var issue = _issue;
    var profiles = <ExternalAgentProfile>[];
    try {
      profiles = widget.store.profiles;
    } on ExternalAgentException catch (e) {
      issue = e.issue;
    }
    final needs = {for (final p in profiles) p.id: _needsCount(p)};
    // One list by urgency: what needs the person first, then the rest,
    // each band newest first. List.sort is not stable, so the newest-first
    // position breaks ties explicitly.
    final newestFirst = profiles.reversed.toList();
    final position = {
      for (final (index, p) in newestFirst.indexed) p.id: index,
    };
    int band(ExternalAgentProfile p) => p.deleting || needs[p.id]! > 0 ? 0 : 1;
    final ordered = newestFirst
      ..sort((a, b) {
        final urgency = band(a).compareTo(band(b));
        if (urgency != 0) return urgency;
        return position[a.id]!.compareTo(position[b.id]!);
      });
    final add = KitAction(
      key: const ValueKey('external-agents-add'),
      label: l.a2aAdd,
      icon: AppIconography.add,
      onPressed: _busy ? null : _add,
    );
    return KitScreen(
      width: KitScreenWidth.list,
      loading: _busy,
      topBar: widget.embedded
          ? null
          : KitTopBar(
              title: l.a2aTitle,
              actions: [if (profiles.isNotEmpty) add],
            ),
      body: ListView(
        padding: KitScreen.padding(context),
        children: [
          // In a Tools tab, Add is a visible button at the head of the list
          // (the empty state carries its own).
          if (widget.embedded && profiles.isNotEmpty) ...[
            Align(
              alignment: AlignmentDirectional.centerStart,
              child: KitButton.fromAction(
                add,
                role: KitButtonRole.secondary,
                expand: false,
              ),
            ),
            _gap(context),
          ],
          if (issue != null) ...[_issueNotice(context, issue), _gap(context)],
          if (profiles.isEmpty)
            KitStateView(
              key: const ValueKey('external-agents-empty'),
              icon: AppIconography.network,
              title: l.externalAgentsEmptyTitle,
              body: l.externalAgentsEmptyBody,
              size: KitStateSize.inline,
              primary: add,
            )
          else
            KitRowGroup(
              margin: EdgeInsets.zero,
              children: [for (final p in ordered) _row(context, p, needs)],
            ),
          SizedBox(height: KitTokens.of(context).space3),
          KitText(
            l.externalAgentsBoundary,
            role: KitTextRole.caption,
            tone: KitTextTone.secondary,
          ),
        ],
      ),
    );
  }

  Widget _row(
    BuildContext context,
    ExternalAgentProfile p,
    Map<String, int> needs,
  ) {
    final l = _l(context);
    final count = needs[p.id] ?? 0;
    final host = _host(p.card.cardUrl);
    return KitRow(
      key: ValueKey('external-agent-${p.id}'),
      // The yellow needs-you mark leads every row that waits on the
      // person, so the list scans by it.
      leading: p.deleting || count > 0
          ? KitNeedsYou.mark()
          : const KitRowIcon(AppIconography.network),
      title: p.card.name,
      supporting: TextSpan(
        children: [
          if (p.deleting)
            // The needs-you mark leads the row; the words say what is left.
            TextSpan(text: l.externalAgentsRemovalIncomplete)
          else ...[
            if (count > 0) KitNeedsYou.span(context, count: count),
            TextSpan(text: host),
          ],
        ],
      ),
      trailing: p.deleting ? null : const KitChevron(),
      onTap: _busy ? null : () => p.deleting ? _delete(p) : _open(p),
      menuLabel: p.card.name,
      menu: [
        KitMenuItem(
          label: l.externalAgentsRemoveNamed(p.card.name),
          icon: AppIconography.delete,
          destructive: true,
          enabled: !_busy,
          disabledReason: _busy ? l.externalAgentsBusy : null,
          onSelected: () => _delete(p),
        ),
      ],
    );
  }
}

// --- Add agent ---------------------------------------------------------------

/// One decision, trust it or not (map `add-agent`, redesign): the address,
/// one pinned primary that turns from Check agent into Save, the card as
/// rows, the protocol under Details, and a key field only when the agent
/// asks for one.
class _AddAgentScreen extends StatefulWidget {
  final ExternalAgentStore store;
  final ExternalAgentGateway gateway;
  const _AddAgentScreen({required this.store, required this.gateway});
  @override
  State<_AddAgentScreen> createState() => _AddAgentScreenState();
}

class _AddAgentScreenState extends State<_AddAgentScreen> {
  final _address = TextEditingController();
  final _token = TextEditingController();
  ExternalAgentCard? _card;
  ExternalAgentIssue? _issue;
  bool _busy = false;

  /// Bumped by Stop checking and by a new check, so a late answer to an
  /// abandoned check is ignored.
  int _generation = 0;

  Future<void> _inspect() async {
    final generation = ++_generation;
    setState(() {
      _busy = true;
      _card = null;
      _issue = null;
      _token.clear();
    });
    ExternalAgentCard? card;
    ExternalAgentIssue? issue;
    try {
      card = await widget.gateway.discover(_address.text);
    } on ExternalAgentException catch (e) {
      issue = e.issue;
    } catch (_) {
      issue = ExternalAgentIssue.unavailable;
    }
    if (!mounted || generation != _generation) return;
    setState(() {
      _card = card;
      _issue = issue;
      _busy = false;
    });
  }

  void _stopChecking() => setState(() {
    _generation++;
    _busy = false;
  });

  Future<void> _save() async {
    final card = _card;
    if (card == null || !card.supported) return;
    setState(() => _busy = true);
    try {
      await widget.store.add(card, _token.text);
      if (mounted) Navigator.pop(context);
    } on ExternalAgentException catch (e) {
      if (mounted) {
        setState(() {
          _issue = e.issue;
          _busy = false;
        });
      }
    }
  }

  @override
  void dispose() {
    widget.gateway.close();
    _address.dispose();
    _token.dispose();
    super.dispose();
  }

  bool get _needsKey =>
      _card?.auth == ExternalAgentAuth.bearer && _token.text.trim().isEmpty;

  @override
  Widget build(BuildContext context) {
    final l = _l(context);
    final card = _card;
    final issue = _issue;
    final checking = _busy && card == null;
    final KitAction primary;
    if (card == null) {
      primary = KitAction(
        key: const ValueKey('external-agent-check'),
        label: l.externalAgentsCheck,
        icon: AppIconography.search,
        working: checking,
        disabledReason: _address.text.trim().isEmpty
            ? l.externalAgentsCheckNeedsAddress
            : null,
        onPressed: _address.text.trim().isEmpty || checking ? null : _inspect,
      );
    } else {
      final reason = !card.supported
          ? l.externalAgentsUnsupportedTitle
          : _needsKey
          ? l.externalAgentsSaveNeedsKey
          : null;
      primary = KitAction(
        key: const ValueKey('external-agent-save'),
        label: l.externalAgentsSaveNamed(card.name),
        icon: AppIconography.save,
        working: _busy,
        disabledReason: reason,
        onPressed: reason != null || _busy ? null : _save,
      );
    }
    return KitScreen(
      width: KitScreenWidth.list,
      topBar: KitTopBar(title: l.a2aAdd),
      bottom: KitActionBlock(
        primary: primary,
        secondary: checking
            ? KitAction(
                key: const ValueKey('external-agent-stop-check'),
                label: l.externalAgentsStopChecking,
                icon: AppIconography.close,
                onPressed: _stopChecking,
              )
            : null,
      ),
      body: ListView(
        padding: KitScreen.padding(context),
        children: [
          KitField(
            label: l.a2aAddress,
            controller: _address,
            kind: KitFieldKind.url,
            hint: 'https://agent.example',
            helper: l.externalAgentsAddressHelper,
            error: issue == ExternalAgentIssue.address
                ? l.a2aAddressError
                : null,
            enabled: !_busy,
            disabledReason: _busy ? l.externalAgentsBusy : null,
            textInputAction: TextInputAction.go,
            onChanged: (_) => setState(() {
              _card = null;
              _token.clear();
              if (_issue == ExternalAgentIssue.address) _issue = null;
            }),
            onSubmitted: (_) {
              if (_address.text.trim().isNotEmpty && !_busy) _inspect();
            },
            fieldKey: const ValueKey('external-agent-address'),
          ),
          if (issue != null && issue != ExternalAgentIssue.address) ...[
            _gap(context),
            KitNotice(
              key: const ValueKey('external-agent-issue'),
              title: card == null ? l.externalAgentsCheckFailedTitle : null,
              message: _issueText(l, issue),
              tone: AppStatusTone.failure,
              icon: AppIconography.warning,
            ),
          ],
          if (card != null) ..._cardSection(context, l, card),
        ],
      ),
    );
  }

  List<Widget> _cardSection(
    BuildContext context,
    AppLocalizations l,
    ExternalAgentCard card,
  ) => [
    _gap(context),
    if (!card.supported) ...[
      KitNotice(
        key: const ValueKey('external-agent-unsupported'),
        title: l.externalAgentsUnsupportedTitle,
        message: l.externalAgentsUnsupportedBody,
        tone: AppStatusTone.failure,
        icon: AppIconography.blocked,
      ),
      _gap(context),
    ],
    _AgentAbout(card: card),
    if (card.supported) ...[
      _gap(context),
      if (card.auth == ExternalAgentAuth.bearer)
        KitField.secret(
          label: l.externalAgentsKeyLabel,
          controller: _token,
          helper: l.externalAgentsKeyHelper,
          enabled: !_busy,
          disabledReason: _busy ? l.externalAgentsBusy : null,
          onChanged: (_) => setState(() {}),
          fieldKey: const ValueKey('external-agent-key'),
        )
      else
        KitNotice(
          key: const ValueKey('external-agent-no-key'),
          message: l.externalAgentsNoKey,
          icon: AppIconography.info,
        ),
    ],
    _gap(context),
    _AgentDetails(card: card),
  ];
}

/// What the agent says about itself, as rows: its description and what it
/// offers; on Add agent, that none of it is verified. The agent's page shows
/// only its skills, and nothing when it lists none.
class _AgentAbout extends StatelessWidget {
  final ExternalAgentCard card;

  /// False on the agent's own page, whose title and first line already
  /// say who it is.
  final bool identity;
  const _AgentAbout({required this.card, this.identity = true});

  @override
  Widget build(BuildContext context) {
    final l = _l(context);
    final tokens = KitTokens.of(context);
    // The agent's page: nothing to say when it lists no skills.
    if (!identity && card.skills.isEmpty) return const SizedBox.shrink();
    final group = KitRowGroup(
      margin: EdgeInsets.zero,
      label: identity ? l.externalAgentsAboutLabel : l.a2aSkills,
      children: [
        if (identity)
          KitRow(
            leading: const KitRowIcon(AppIconography.network),
            title: card.name,
            supporting: TextSpan(
              text: card.description.trim().isEmpty
                  ? _host(card.cardUrl)
                  : card.description,
            ),
            supportingMaxLines: 4,
          ),
        for (final skill in card.skills)
          KitRow(
            leading: const KitRowIcon(AppIconography.sparkle),
            title: skill.name,
            titleMaxLines: 2,
            supporting: skill.description.trim().isEmpty
                ? null
                : TextSpan(text: skill.description),
            supportingMaxLines: 3,
          ),
      ],
    );
    // Said once, where the decision to trust it is made (Add agent).
    if (!identity) return group;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        group,
        SizedBox(height: tokens.space2),
        KitText(
          l.externalAgentsUnverified,
          role: KitTextRole.caption,
          tone: KitTextTone.secondary,
        ),
      ],
    );
  }
}

/// The protocol facts, folded and last (map: "protocol under Details").
class _AgentDetails extends StatelessWidget {
  final ExternalAgentCard card;
  const _AgentDetails({required this.card});

  @override
  Widget build(BuildContext context) {
    final l = _l(context);
    return KitDetailsFold(
      foldKey: const ValueKey('external-agent-details'),
      values: [
        KitTechnicalValue(l.externalAgentsDetailCard, card.cardUrl),
        KitTechnicalValue(l.externalAgentsDetailEndpoint, card.endpoint),
        KitTechnicalValue(
          l.externalAgentsDetailVersion,
          card.version,
          copyable: false,
        ),
        if (card.supported)
          KitTechnicalValue(
            l.externalAgentsDetailConnection,
            l.a2aSupportedConnection,
            copyable: false,
          ),
      ],
    );
  }
}

// --- An agent's page -------------------------------------------------------

/// One agent: its tasks by urgency, New task pinned, and the rarer acts
/// (replace its key, remove it) in the top bar's menu, removal last and
/// confirmed.
class ExternalAgentDetailScreen extends StatefulWidget {
  final ExternalAgentStore store;
  final ExternalAgentProfile profile;
  final ExternalAgentGateway Function()? gatewayFactory;
  const ExternalAgentDetailScreen({
    super.key,
    required this.store,
    required this.profile,
    this.gatewayFactory,
  });
  @override
  State<ExternalAgentDetailScreen> createState() =>
      _ExternalAgentDetailScreenState();
}

class _ExternalAgentDetailScreenState extends State<ExternalAgentDetailScreen> {
  bool _busy = false;
  ExternalAgentIssue? _issue;

  Future<void> _open(ExternalTaskRecord record) async {
    if (_busy) return;
    setState(() => _busy = true);
    await pushKitPage<void>(
      context,
      (_) => ExternalTaskScreen(
        store: widget.store,
        profile: widget.profile,
        record: record,
        gateway: widget.gatewayFactory?.call(),
      ),
    );
    if (mounted) setState(() => _busy = false);
  }

  /// New task opens the task page as a draft directly (map fix): the text
  /// is written and checked there, and nothing is sent until Send.
  Future<void> _newTask() async {
    if (_busy) return;
    setState(() {
      _busy = true;
      _issue = null;
    });
    try {
      final record = ExternalTaskRecord(
        localId: const Uuid().v4(),
        title: '',
        created: DateTime.now(),
      );
      await widget.store.saveTask(widget.profile.id, record);
      if (!mounted) return;
      await pushKitPage<void>(
        context,
        (_) => ExternalTaskScreen(
          store: widget.store,
          profile: widget.profile,
          record: record,
          gateway: widget.gatewayFactory?.call(),
        ),
      );
    } on ExternalAgentException catch (e) {
      _issue = e.issue;
    }
    if (mounted) setState(() => _busy = false);
  }

  /// Replaces the stored key in one secret dialog ("Save key"); a failure
  /// stays in the dialog, the key is never shown or prefilled (SEC-3).
  Future<void> _credential() async {
    final l = _l(context);
    await showKitInputDialog(
      context,
      title: l.externalAgentsReplaceKeyTitle(widget.profile.card.name),
      label: l.externalAgentsKeyLabel,
      helper: l.externalAgentsKeyHelper,
      kind: KitFieldKind.secret,
      confirmLabel: l.externalAgentsSaveKey,
      dialogKey: const ValueKey('external-agent-key-dialog'),
      validate: (value) =>
          value.trim().isEmpty ? l.externalAgentsSaveNeedsKey : null,
      onSubmit: (value) async {
        try {
          await widget.store.updateCredential(widget.profile.id, value);
          return null;
        } on ExternalAgentException catch (e) {
          return _issueText(l, e.issue);
        }
      },
    );
  }

  Future<void> _delete() async {
    if (!await _confirmRemove(context, widget.profile) || !mounted) return;
    setState(() => _busy = true);
    try {
      await widget.store.delete(widget.profile.id);
      if (mounted) Navigator.pop(context);
    } on ExternalAgentException catch (e) {
      if (mounted) {
        setState(() {
          _issue = e.issue;
          _busy = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: widget.store,
    builder: (context, _) => _buildScreen(context),
  );

  Widget _buildScreen(BuildContext context) {
    final l = _l(context);
    final card = widget.profile.card;
    var issue = _issue;
    var records = <ExternalTaskRecord>[];
    var current = false;
    try {
      current = widget.store.contains(widget.profile.id);
      records = _ordered(widget.store.tasks(widget.profile.id));
    } on ExternalAgentException catch (e) {
      issue = e.issue;
    }
    final unavailable = current ? null : l.a2aScopeError;
    return KitScreen(
      width: KitScreenWidth.list,
      loading: _busy,
      topBar: KitTopBar(
        title: card.name,
        subtitle: _host(card.cardUrl),
        menuKey: const ValueKey('external-agent-menu'),
        menu: [
          if (card.auth == ExternalAgentAuth.bearer)
            KitMenuItem(
              key: const ValueKey('external-agent-replace-key'),
              label: l.externalAgentsReplaceKeyNamed(card.name),
              icon: AppIconography.permissions,
              enabled: !_busy && current,
              disabledReason: unavailable,
              onSelected: _credential,
            ),
          KitMenuItem(
            key: const ValueKey('external-agent-remove'),
            label: l.externalAgentsRemoveNamed(card.name),
            icon: AppIconography.delete,
            destructive: true,
            enabled: !_busy,
            disabledReason: _busy ? l.externalAgentsBusy : null,
            onSelected: _delete,
          ),
        ],
      ),
      bottom: KitActionBlock(
        primary: KitAction(
          key: const ValueKey('external-agent-new-task'),
          label: l.externalAgentsNewTaskNamed(card.name),
          icon: AppIconography.add,
          disabledReason: unavailable,
          onPressed: _busy || !current ? null : _newTask,
        ),
      ),
      body: ListView(
        // The section gap under the bar's subtitle (the host), so the
        // description reads as its own paragraph.
        padding: KitScreen.padding(
          context,
        ).copyWith(top: KitTokens.of(context).space5),
        children: [
          if (card.description.trim().isNotEmpty) ...[
            KitText(
              card.description,
              role: KitTextRole.secondary,
              tone: KitTextTone.secondary,
              maxLines: 3,
              overflow: TextOverflow.ellipsis,
            ),
            _gap(context),
          ],
          if (issue != null) ...[_issueNotice(context, issue), _gap(context)],
          if (records.isEmpty)
            KitStateView(
              key: const ValueKey('external-agent-no-tasks'),
              icon: AppIconography.chat,
              title: l.externalAgentsNoTasksTitle,
              body: l.externalAgentsNoTasksBody,
              size: KitStateSize.inline,
            )
          else ...[
            KitRowGroup(
              margin: EdgeInsets.zero,
              label: l.externalAgentsTasksLabel,
              children: [
                for (final record in records)
                  KitRow(
                    key: ValueKey('external-task-${record.localId}'),
                    leading: KitTaskMark(state: _mark(record)),
                    title: _recordTitle(l, record),
                    titleMaxLines: 2,
                    // The mark carries needs-you; the word says which state.
                    supporting: TextSpan(text: _recordWord(l, record)),
                    trailing: const KitChevron(),
                    enabled: !_busy && current,
                    disabledReason: current ? null : unavailable,
                    onTap: () => _open(record),
                  ),
              ],
            ),
          ],
          if (card.skills.isNotEmpty) ...[
            _gap(context),
            _AgentAbout(card: card, identity: false),
          ],
          _gap(context),
          _AgentDetails(card: card),
        ],
      ),
    );
  }
}

// --- One task ----------------------------------------------------------------

/// One task: its state on the status line, the agent's output, and the
/// field plus one pinned primary (Send, or Reply when the agent asks).
/// Pull down checks with the agent; Stop and Forget live in the menu,
/// destructive and confirmed.
class ExternalTaskScreen extends StatefulWidget {
  final ExternalAgentStore store;
  final ExternalAgentProfile profile;
  final ExternalTaskRecord record;
  final ExternalAgentGateway? gateway;
  final Duration pollInterval;
  const ExternalTaskScreen({
    super.key,
    required this.store,
    required this.profile,
    required this.record,
    this.gateway,
    this.pollInterval = const Duration(seconds: 4),
  });
  @override
  State<ExternalTaskScreen> createState() => _ExternalTaskScreenState();
}

class _ExternalTaskScreenState extends State<ExternalTaskScreen>
    with WidgetsBindingObserver {
  late final ExternalTaskController _controller;
  late final TextEditingController _text;
  Timer? _poll;
  bool _dialog = false;
  late String _savedDraft;
  int _draftRevision = 0;
  int _savedDraftRevision = 0;
  bool get _draftIsSaved =>
      _savedDraftRevision == _draftRevision && _savedDraft == _text.text;
  bool _draftSaveFailed = false;
  bool _leaving = false;
  ExternalAgentIssue? _localIssue;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _text = TextEditingController(text: widget.record.draft);
    _savedDraft = widget.record.draft;
    _controller = ExternalTaskController(
      store: widget.store,
      profile: widget.profile,
      record: widget.record,
      gateway: widget.gateway,
    )..addListener(_changed);
    unawaited(_controller.refresh());
    _poll = Timer.periodic(widget.pollInterval, (_) {
      if (!_dialog &&
          _controller.foreground &&
          _controller.current &&
          _controller.issue == null &&
          _controller.record.task?.id != null &&
          _controller.record.task?.terminal == false) {
        unawaited(_controller.refresh());
      }
    });
  }

  void _changed() {
    if (mounted) setState(() {});
  }

  Future<bool> _persistDraft(String value) async {
    final revision = ++_draftRevision;
    _controller.record = _controller.record.withDraft(value);
    setState(() => _draftSaveFailed = false);
    try {
      await widget.store.saveDraft(
        widget.profile.id,
        _controller.record.localId,
        value,
      );
      if (!mounted) return true;
      if (revision == _draftRevision) {
        setState(() {
          _savedDraft = value;
          _savedDraftRevision = revision;
        });
      }
      return revision == _draftRevision;
    } on ExternalAgentException {
      if (mounted && revision == _draftRevision) {
        setState(() => _draftSaveFailed = true);
      }
      return false;
    }
  }

  /// Leaving a draft saves it first. A new task left empty is dropped, so
  /// New task never leaves an empty row behind.
  Future<void> _leave() async {
    if (_leaving) return;
    _leaving = true;
    final record = _controller.record;
    if (record.task == null &&
        !record.uncertain &&
        record.title.trim().isEmpty &&
        _text.text.trim().isEmpty) {
      try {
        _controller.background();
        await widget.store.removeTask(widget.profile.id, record.localId);
        _leaving = false;
        if (mounted) Navigator.pop(context);
        return;
      } on ExternalAgentException {
        // Fall through: keep the (empty) draft and leave as usual.
      }
    }
    final saved = await _persistDraft(_text.text);
    _leaving = false;
    if (saved && mounted) Navigator.pop(context);
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      unawaited(_controller.resume());
    } else {
      _controller.background();
    }
  }

  Future<void> _send({bool continuation = false}) async {
    final text = _text.text.trim();
    if (text.isEmpty) return;
    final record = _controller.record;
    if (!continuation && record.title.trim().isEmpty) {
      // A task opened empty takes its first line as its name before it is
      // sent, so its row reads what was asked.
      final first = text.split('\n').first;
      final titled = ExternalTaskRecord(
        localId: record.localId,
        title: first.length > 160 ? first.substring(0, 160) : first,
        created: record.created,
        task: record.task,
        uncertain: record.uncertain,
        draft: record.draft,
      );
      try {
        await widget.store.saveTask(
          widget.profile.id,
          titled,
          existingOnly: true,
        );
        _controller.record = titled;
      } on ExternalAgentException catch (e) {
        if (mounted) setState(() => _localIssue = e.issue);
        return;
      }
    }
    await _controller.submit(text, continuation: continuation);
    if (mounted && _controller.issue == null) _text.clear();
  }

  Future<void> _cancel() async {
    final l = _l(context);
    _dialog = true;
    final approved = await showKitConfirm(
      context,
      kind: KitConfirmKind.stop,
      icon: AppIconography.stop,
      title: l.externalAgentsStopTaskTitle(_recordTitle(l, _controller.record)),
      body: l.a2aCancelDetail,
      confirmLabel: l.externalAgentsStopTaskConfirm(widget.profile.card.name),
      cancelLabel: l.externalAgentsStopTaskKeep,
      confirmKey: const ValueKey('external-task-stop-confirm'),
    );
    _dialog = false;
    if (approved && mounted) await _controller.cancel();
  }

  Future<void> _forget() async {
    final l = _l(context);
    _dialog = true;
    final approved = await showKitConfirm(
      context,
      kind: KitConfirmKind.destructive,
      icon: AppIconography.delete,
      title: l.externalAgentsForgetTitle,
      body: l.externalAgentsForgetBody,
      confirmLabel: l.externalAgentsForgetConfirm,
      confirmKey: const ValueKey('external-task-forget-confirm'),
    );
    _dialog = false;
    if (!approved || !mounted) return;
    _controller.background();
    try {
      await widget.store.removeTask(
        widget.profile.id,
        _controller.record.localId,
      );
      if (mounted) Navigator.pop(context);
    } on ExternalAgentException {
      if (mounted) {
        unawaited(_controller.resume());
        setState(() => _localIssue = ExternalAgentIssue.storage);
      }
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _poll?.cancel();
    _controller.removeListener(_changed);
    _controller.dispose();
    _text.dispose();
    super.dispose();
  }

  /// The task's one condition, on the screen's status line.
  KitStatus _status(AppLocalizations l, ExternalTaskRecord record) {
    final c = _controller;
    final task = record.task;
    final checked = c.checkedAt;
    final supporting = task == null && !record.uncertain
        ? null
        : c.fresh && checked != null
        ? l.externalAgentsCheckedAt(
            KitSince.ageLabel(context, KitSince.statusOf(checked).elapsed),
          )
        : l.externalAgentsPullToCheck;
    final needs = _needsYou(record);
    final running = _urgency(record) == 1;
    return KitStatus(
      id: 'external-task:${record.localId}',
      kind: KitStatusKind.work,
      icon: needs
          ? AppIconography.notificationImportant
          : task?.terminal == true
          ? AppIconography.checkCircle
          : running
          ? AppIconography.sync
          : AppIconography.editNote,
      tone: running || c.busy
          ? AppStatusTone.progress
          : task?.state == ExternalTaskState.failed ||
                task?.state == ExternalTaskState.rejected
          ? AppStatusTone.failure
          : AppStatusTone.neutral,
      message: _recordWord(l, record),
      supporting: supporting,
    );
  }

  @override
  Widget build(BuildContext context) {
    final l = _l(context);
    final c = _controller;
    final record = c.record;
    final task = record.task;
    final draft = task == null && !record.uncertain;
    final replying = task?.state == ExternalTaskState.inputRequired;
    final issue = _localIssue ?? c.issue;
    final agent = widget.profile.card.name;
    final canSend =
        draft && c.current && c.foreground && !c.busy && _draftIsSaved;
    final String? sendReason;
    if (_text.text.trim().isEmpty) {
      sendReason = draft
          ? l.externalAgentsSendNeedsText
          : l.externalAgentsReplyNeedsText;
    } else if (draft && !_draftIsSaved) {
      sendReason = _draftSaveFailed ? l.a2aDraftSaveError : l.a2aSavingDraft;
    } else {
      sendReason = null;
    }
    return PopScope<void>(
      canPop: !draft || (_draftIsSaved && record.title.trim().isNotEmpty),
      onPopInvokedWithResult: (didPop, result) {
        if (!didPop && draft) unawaited(_leave());
      },
      child: KitScreen(
        width: KitScreenWidth.list,
        loading: c.busy,
        topBar: KitTopBar(
          title: _recordTitle(l, record),
          subtitle: agent,
          menuKey: const ValueKey('external-task-menu'),
          menu: [
            if (task?.id != null && !task!.terminal)
              KitMenuItem(
                key: const ValueKey('external-task-stop'),
                label: l.externalAgentsStopMenu(agent),
                icon: AppIconography.stop,
                destructive: true,
                enabled: c.canCancel,
                disabledReason: c.canCancel
                    ? null
                    : l.externalAgentsStopUnavailable,
                onSelected: _cancel,
              ),
            KitMenuItem(
              key: const ValueKey('external-task-forget'),
              label: l.externalAgentsForgetMenu,
              icon: AppIconography.delete,
              destructive: true,
              onSelected: _forget,
            ),
          ],
        ),
        status: _status(l, record),
        bottom: draft || replying
            ? KitActionBlock(
                primary: KitAction(
                  key: const ValueKey('external-task-send'),
                  label: draft
                      ? l.externalAgentsSendNamed(agent)
                      : l.externalAgentsReplyNamed(agent),
                  icon: AppIconography.send,
                  working: c.busy,
                  disabledReason: sendReason,
                  onPressed:
                      sendReason == null && (draft ? canSend : c.canContinue)
                      ? () => _send(continuation: !draft)
                      : null,
                ),
              )
            : null,
        body: KitRefresh(
          onRefresh: c.refresh,
          child: ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: KitScreen.padding(context),
            children: _body(context, l, record, issue, draft, replying),
          ),
        ),
      ),
    );
  }

  List<Widget> _body(
    BuildContext context,
    AppLocalizations l,
    ExternalTaskRecord record,
    ExternalAgentIssue? issue,
    bool draft,
    bool replying,
  ) {
    final c = _controller;
    final task = record.task;
    final notes = <String>[
      if (record.uncertain) l.a2aUncertain,
      if (c.cancelUnconfirmed && task?.terminal == false)
        l.a2aCancelUnconfirmed,
      if (task?.state == ExternalTaskState.authRequired)
        l.a2aAuthRequiredDetail,
      if (task?.state == ExternalTaskState.unknown) l.a2aUnknownDetail,
    ];
    return [
      if (issue != null) ...[_issueNotice(context, issue), _gap(context)],
      if (draft && _draftSaveFailed) ...[
        KitNotice(
          key: const ValueKey('external-task-draft-failed'),
          message: l.a2aDraftSaveError,
          tone: AppStatusTone.failure,
          icon: AppIconography.warning,
          actions: [
            KitAction(
              key: const ValueKey('external-task-draft-retry'),
              label: l.a2aRetryDraftSave,
              icon: AppIconography.retry,
              onPressed: () => _persistDraft(_text.text),
            ),
          ],
        ),
        _gap(context),
      ],
      for (final note in notes) ...[
        KitNotice(message: note, icon: AppIconography.info),
        _gap(context),
      ],
      if (task?.parts.isNotEmpty == true) ...[
        _output(context, l, task!),
        _gap(context),
      ],
      if (task?.omittedContent == true) ...[
        KitNotice(message: l.a2aOmittedContent, icon: AppIconography.info),
        _gap(context),
      ],
      if (draft || replying) ...[
        KitField(
          label: draft ? l.a2aTaskPrompt : l.a2aYourReply,
          controller: _text,
          kind: KitFieldKind.multiline,
          maxLength: 16000,
          helper: draft ? l.externalAgentsSendNote : null,
          enabled: !c.busy && c.current && !record.uncertain,
          disabledReason: c.busy
              ? l.externalAgentsBusy
              : !c.current
              ? l.a2aScopeError
              : record.uncertain
              ? l.a2aDeliveryUnconfirmed
              : null,
          autofocus: draft && _text.text.isEmpty,
          onChanged: (value) {
            if (draft) {
              unawaited(_persistDraft(value));
            } else {
              setState(() {});
            }
          },
          fieldKey: const ValueKey('external-task-text'),
        ),
      ],
    ];
  }

  /// The agent's output: text as selectable body text, links as rows that
  /// open only through the external-link check.
  Widget _output(BuildContext context, AppLocalizations l, ExternalTask task) {
    final tokens = KitTokens.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Padding(
          padding: EdgeInsetsDirectional.only(bottom: tokens.space2),
          child: KitText(
            l.a2aAgentOutput,
            role: KitTextRole.label,
            tone: KitTextTone.secondary,
          ),
        ),
        for (final part in task.parts) ...[
          KitSurface.panel(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                if (part.name?.isNotEmpty == true)
                  Padding(
                    padding: EdgeInsetsDirectional.only(bottom: tokens.space2),
                    child: KitText(part.name!, role: KitTextRole.rowTitle),
                  ),
                if (part.text != null) KitText.selectable(part.text!),
                if (part.url != null)
                  KitRow(
                    padding: EdgeInsets.zero,
                    leading: const KitRowIcon(AppIconography.externalLink),
                    title:
                        safeExternalLinkUri(part.url)?.host ?? l.a2aBlockedLink,
                    supporting: TextSpan(text: l.a2aReviewLink),
                    trailing: const KitChevron(),
                    onTap: () => openExternalLink(context, part.url),
                  ),
              ],
            ),
          ),
          SizedBox(height: tokens.space3),
        ],
      ],
    );
  }
}
