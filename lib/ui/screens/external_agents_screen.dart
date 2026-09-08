import 'dart:async';

import 'package:flutter/material.dart';
import 'package:uuid/uuid.dart';

import '../../domain/external_agent.dart';
import '../../l10n/app_localizations.dart';
import '../../state/external_agents.dart';
import '../widgets/confirm_sheet.dart';
import '../widgets/external_link.dart';

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

class _Notice extends StatelessWidget {
  final String text;
  final bool error;
  const _Notice(this.text, {this.error = false});
  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    return Semantics(
      liveRegion: true,
      child: Container(
        margin: const EdgeInsets.only(bottom: 16),
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: error ? colors.errorContainer : colors.secondaryContainer,
          borderRadius: BorderRadius.circular(16),
        ),
        child: Text(
          text,
          style: TextStyle(
            color: error
                ? colors.onErrorContainer
                : colors.onSecondaryContainer,
            height: 1.4,
          ),
        ),
      ),
    );
  }
}

/// Dedicated A2A identities. No project, file or terminal capabilities implied.
class ExternalAgentsScreen extends StatefulWidget {
  final ExternalAgentStore store;
  final ExternalAgentGateway Function()? gatewayFactory;
  const ExternalAgentsScreen({
    super.key,
    required this.store,
    this.gatewayFactory,
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
    await Navigator.of(context).push<void>(
      MaterialPageRoute(
        builder: (_) =>
            _AddAgentScreen(store: widget.store, gateway: _gateway()),
      ),
    );
    if (mounted) setState(() {});
  }

  Future<void> _delete(ExternalAgentProfile profile) async {
    final l = _l(context);
    if (!await showConfirmSheet(
          context,
          title: l.a2aDeleteAgent,
          message: l.a2aDeleteAgentDetail,
          confirmLabel: l.a2aDeleteLocal,
          destructive: true,
        ) ||
        !mounted) {
      return;
    }
    setState(() => _busy = true);
    try {
      await widget.store.delete(profile.id);
    } on ExternalAgentException catch (e) {
      _issue = e.issue;
    }
    if (mounted) setState(() => _busy = false);
  }

  @override
  Widget build(BuildContext context) {
    final l = _l(context);
    List<ExternalAgentProfile> profiles = [];
    try {
      profiles = widget.store.profiles;
    } on ExternalAgentException catch (e) {
      _issue = e.issue;
    }
    return Scaffold(
      appBar: AppBar(title: Text(l.a2aTitle)),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          Text(l.a2aIntro, style: Theme.of(context).textTheme.headlineSmall),
          const SizedBox(height: 12),
          Text(l.a2aBoundary),
          const SizedBox(height: 24),
          if (_issue != null) _Notice(_issueText(l, _issue!), error: true),
          FilledButton.icon(
            onPressed: _busy ? null : _add,
            icon: const Icon(Icons.add_link_rounded),
            label: Text(l.a2aAdd),
          ),
          const SizedBox(height: 24),
          if (profiles.isEmpty) _Notice(l.a2aEmpty),
          for (final profile in profiles)
            Card(
              child: Padding(
                padding: const EdgeInsets.all(8),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    ListTile(
                      leading: const Icon(Icons.hub_outlined),
                      title: Text(profile.card.name),
                      subtitle: Text(Uri.parse(profile.card.cardUrl).origin),
                      onTap: profile.deleting || _busy
                          ? null
                          : () async {
                              await Navigator.of(context).push<void>(
                                MaterialPageRoute(
                                  builder: (_) => ExternalAgentDetailScreen(
                                    store: widget.store,
                                    profile: profile,
                                    gatewayFactory: widget.gatewayFactory,
                                  ),
                                ),
                              );
                              if (mounted) setState(() {});
                            },
                    ),
                    if (profile.deleting)
                      Padding(
                        padding: const EdgeInsets.all(12),
                        child: Text(l.a2aDeletionPending),
                      ),
                    if (profile.deleting)
                      TextButton(
                        onPressed: _busy ? null : () => _delete(profile),
                        child: Text(l.a2aRetryDelete),
                      ),
                  ],
                ),
              ),
            ),
        ],
      ),
    );
  }
}

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
  Future<void> _inspect() async {
    setState(() {
      _busy = true;
      _card = null;
      _issue = null;
      _token.clear();
    });
    try {
      final card = await widget.gateway.discover(_address.text);
      if (mounted) setState(() => _card = card);
    } on ExternalAgentException catch (e) {
      if (mounted) setState(() => _issue = e.issue);
    } catch (_) {
      if (mounted) setState(() => _issue = ExternalAgentIssue.unavailable);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

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

  @override
  Widget build(BuildContext context) {
    final l = _l(context);
    final card = _card;
    return Scaffold(
      appBar: AppBar(title: Text(l.a2aAdd)),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          Text(
            l.a2aInspectIntro,
            style: Theme.of(context).textTheme.titleLarge,
          ),
          const SizedBox(height: 16),
          TextField(
            controller: _address,
            enabled: !_busy,
            keyboardType: TextInputType.url,
            autocorrect: false,
            decoration: InputDecoration(
              labelText: l.a2aAddress,
              hintText: 'https://agent.example',
            ),
            onChanged: (_) => setState(() {
              _card = null;
              _token.clear();
            }),
          ),
          const SizedBox(height: 16),
          FilledButton(
            onPressed: _busy ? null : _inspect,
            child: Text(l.a2aInspect),
          ),
          const SizedBox(height: 20),
          if (_busy) const LinearProgressIndicator(),
          if (_issue != null) _Notice(_issueText(l, _issue!), error: true),
          if (card != null) ...[
            _AgentCardView(card),
            if (!card.supported) _Notice(l.a2aUnsupported, error: true),
            if (card.supported) ...[
              _Notice(
                card.auth == ExternalAgentAuth.bearer
                    ? l.a2aBearerDetail
                    : l.a2aNoAuthDetail,
              ),
              if (card.auth == ExternalAgentAuth.bearer)
                TextField(
                  controller: _token,
                  enabled: !_busy,
                  obscureText: true,
                  autocorrect: false,
                  enableSuggestions: false,
                  maxLength: 8192,
                  decoration: InputDecoration(labelText: l.a2aBearer),
                ),
              const SizedBox(height: 16),
              FilledButton(
                onPressed: _busy ? null : _save,
                child: Text(l.a2aSave),
              ),
            ],
          ],
        ],
      ),
    );
  }
}

class _AgentCardView extends StatelessWidget {
  final ExternalAgentCard card;
  const _AgentCardView(this.card);
  @override
  Widget build(BuildContext context) {
    final l = _l(context);
    final theme = Theme.of(context);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(
              Icons.hub_outlined,
              color: theme.colorScheme.primary,
              size: 32,
            ),
            const SizedBox(height: 12),
            Text(card.name, style: theme.textTheme.headlineSmall),
            const SizedBox(height: 8),
            SelectableText(Uri.parse(card.cardUrl).origin),
            const SizedBox(height: 8),
            Text(l.a2aCardVersion(card.version)),
            if (card.supported) Text(l.a2aSupportedConnection),
            const SizedBox(height: 12),
            Text(card.description),
            const SizedBox(height: 16),
            Text(l.a2aCardClaim, style: theme.textTheme.bodySmall),
            const SizedBox(height: 16),
            Text(l.a2aSkills, style: theme.textTheme.titleMedium),
            for (final skill in card.skills)
              Padding(
                padding: const EdgeInsets.only(top: 12),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(skill.name, style: theme.textTheme.titleSmall),
                    Text(skill.description),
                  ],
                ),
              ),
          ],
        ),
      ),
    );
  }
}

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
    await Navigator.of(context).push<void>(
      MaterialPageRoute(
        builder: (_) => ExternalTaskScreen(
          store: widget.store,
          profile: widget.profile,
          record: record,
          gateway: widget.gatewayFactory?.call(),
        ),
      ),
    );
    if (mounted) setState(() => _busy = false);
  }

  Future<void> _newTask() async {
    final text = await _input(
      context,
      title: _l(context).a2aNewTask,
      label: _l(context).a2aTaskPrompt,
      detail: _l(context).a2aSendDetail,
      action: _l(context).a2aReviewTask,
    );
    if (text == null || !mounted || _busy) return;
    setState(() => _busy = true);
    try {
      final record = ExternalTaskRecord(
        localId: const Uuid().v4(),
        title: text.length > 160 ? text.substring(0, 160) : text,
        created: DateTime.now(),
        draft: text,
      );
      await widget.store.saveTask(widget.profile.id, record);
      if (!mounted) return;
      await Navigator.of(context).push<void>(
        MaterialPageRoute(
          builder: (_) => ExternalTaskScreen(
            store: widget.store,
            profile: widget.profile,
            record: record,
            gateway: widget.gatewayFactory?.call(),
          ),
        ),
      );
    } on ExternalAgentException catch (e) {
      _issue = e.issue;
    }
    if (mounted) setState(() => _busy = false);
  }

  Future<void> _credential() async {
    final l = _l(context);
    final value = await _input(
      context,
      title: l.a2aUpdateCredential,
      label: l.a2aBearer,
      detail: l.a2aBearerDetail,
      action: l.a2aSave,
      secret: true,
    );
    if (value == null || !mounted) return;
    setState(() => _busy = true);
    try {
      await widget.store.updateCredential(widget.profile.id, value);
    } on ExternalAgentException catch (e) {
      _issue = e.issue;
    }
    if (mounted) setState(() => _busy = false);
  }

  Future<void> _delete() async {
    final l = _l(context);
    if (!await showConfirmSheet(
          context,
          title: l.a2aDeleteAgent,
          message: l.a2aDeleteAgentDetail,
          confirmLabel: l.a2aDeleteLocal,
          destructive: true,
        ) ||
        !mounted) {
      return;
    }
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
  Widget build(BuildContext context) {
    final l = _l(context);
    List<ExternalTaskRecord> records = [];
    bool current = false;
    try {
      current = widget.store.contains(widget.profile.id);
      records = widget.store.tasks(widget.profile.id);
    } on ExternalAgentException catch (e) {
      _issue = e.issue;
    }
    return Scaffold(
      appBar: AppBar(title: Text(widget.profile.card.name)),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          _AgentCardView(widget.profile.card),
          const SizedBox(height: 16),
          if (_issue != null) _Notice(_issueText(l, _issue!), error: true),
          FilledButton.icon(
            onPressed: _busy || !current ? null : _newTask,
            icon: const Icon(Icons.add_comment_outlined),
            label: Text(l.a2aNewTask),
          ),
          const SizedBox(height: 24),
          Text(l.a2aSavedTasks, style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 8),
          Text(l.a2aReopenDetail),
          const SizedBox(height: 12),
          for (final record in records)
            Card(
              child: ListTile(
                isThreeLine: true,
                title: Text(
                  record.title,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
                subtitle: Text(
                  record.uncertain
                      ? l.a2aDeliveryUnconfirmed
                      : record.task == null
                      ? l.a2aDraft
                      : externalTaskLabel(l, record.task!.state),
                ),
                trailing: const Icon(Icons.chevron_right_rounded),
                onTap: _busy || !current ? null : () => _open(record),
              ),
            ),
          const SizedBox(height: 24),
          if (widget.profile.card.auth == ExternalAgentAuth.bearer)
            OutlinedButton.icon(
              onPressed: _busy || !current ? null : _credential,
              icon: const Icon(Icons.key_outlined),
              label: Text(l.a2aUpdateCredential),
            ),
          TextButton(
            onPressed: _busy ? null : _delete,
            child: Text(l.a2aDeleteAgent),
          ),
        ],
      ),
    );
  }
}

Future<String?> _input(
  BuildContext context, {
  required String title,
  required String label,
  required String detail,
  required String action,
  bool secret = false,
}) async {
  return showDialog<String>(
    context: context,
    builder: (_) => _InputDialog(
      title: title,
      label: label,
      detail: detail,
      action: action,
      secret: secret,
    ),
  );
}

class _InputDialog extends StatefulWidget {
  final String title, label, detail, action;
  final bool secret;
  const _InputDialog({
    required this.title,
    required this.label,
    required this.detail,
    required this.action,
    required this.secret,
  });
  @override
  State<_InputDialog> createState() => _InputDialogState();
}

class _InputDialogState extends State<_InputDialog> {
  final _controller = TextEditingController();
  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: Text(widget.title),
    content: SingleChildScrollView(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(widget.detail),
          const SizedBox(height: 16),
          TextField(
            controller: _controller,
            obscureText: widget.secret,
            autocorrect: !widget.secret,
            enableSuggestions: !widget.secret,
            maxLength: widget.secret ? 8192 : 16000,
            minLines: widget.secret ? 1 : 3,
            maxLines: widget.secret ? 1 : 8,
            decoration: InputDecoration(labelText: widget.label),
          ),
        ],
      ),
    ),
    actions: [
      TextButton(
        onPressed: () => Navigator.pop(context),
        child: Text(_l(context).a2aBack),
      ),
      FilledButton(
        onPressed: () {
          if (_controller.text.trim().isNotEmpty) {
            Navigator.pop(context, _controller.text.trim());
          }
        },
        child: Text(widget.action),
      ),
    ],
  );
}

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

  Future<void> _leave() async {
    if (_leaving) return;
    _leaving = true;
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
    if (_text.text.trim().isEmpty) return;
    await _controller.submit(_text.text.trim(), continuation: continuation);
    if (mounted && _controller.issue == null) _text.clear();
  }

  Future<void> _cancel() async {
    final l = _l(context);
    _dialog = true;
    final approved = await showConfirmSheet(
      context,
      title: l.a2aCancelTask,
      message: l.a2aCancelDetail,
      confirmLabel: l.a2aRequestCancel,
    );
    _dialog = false;
    if (approved && mounted) await _controller.cancel();
  }

  Future<void> _forget() async {
    final l = _l(context);
    _dialog = true;
    final approved = await showConfirmSheet(
      context,
      title: l.a2aForgetTask,
      message: l.a2aForgetDetail,
      confirmLabel: l.a2aDeleteLocal,
      destructive: true,
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
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(l.a2aStorageError)));
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

  @override
  Widget build(BuildContext context) {
    final l = _l(context);
    final c = _controller;
    final record = c.record;
    final task = record.task;
    final theme = Theme.of(context);
    final draft = task == null && !record.uncertain;
    return PopScope<void>(
      canPop: !draft || _draftIsSaved,
      onPopInvokedWithResult: (didPop, result) {
        if (!didPop && draft) unawaited(_leave());
      },
      child: Scaffold(
        appBar: AppBar(title: Text(l.a2aTaskTitle)),
        body: ListView(
          padding: const EdgeInsets.all(20),
          children: [
            Text(widget.profile.card.name, style: theme.textTheme.labelLarge),
            const SizedBox(height: 8),
            Text(record.title, style: theme.textTheme.headlineSmall),
            const SizedBox(height: 20),
            Card(
              child: Padding(
                padding: const EdgeInsets.all(20),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Icon(
                      task?.terminal == true
                          ? Icons.task_alt_rounded
                          : Icons.chat_bubble_outline_rounded,
                      color: theme.colorScheme.primary,
                      size: 32,
                    ),
                    const SizedBox(height: 12),
                    Semantics(
                      liveRegion: true,
                      child: Text(
                        record.uncertain
                            ? l.a2aDeliveryUnconfirmed
                            : task == null
                            ? l.a2aDraft
                            : externalTaskLabel(l, task.state),
                        style: theme.textTheme.titleLarge,
                      ),
                    ),
                    const SizedBox(height: 8),
                    Text(c.fresh ? l.a2aFresh : l.a2aSavedSnapshot),
                    if (c.busy)
                      const Padding(
                        padding: EdgeInsets.only(top: 16),
                        child: LinearProgressIndicator(),
                      ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 16),
            if (c.issue != null) _Notice(_issueText(l, c.issue!), error: true),
            if (draft && _draftSaveFailed) ...[
              _Notice(l.a2aDraftSaveError, error: true),
              OutlinedButton(
                onPressed: () => _persistDraft(_text.text),
                child: Text(l.a2aRetryDraftSave),
              ),
            ] else if (draft && !_draftIsSaved)
              _Notice(l.a2aSavingDraft),
            if (record.uncertain) _Notice(l.a2aUncertain),
            if (c.cancelUnconfirmed && task?.terminal == false)
              _Notice(l.a2aCancelUnconfirmed),
            if (task?.state == ExternalTaskState.authRequired)
              _Notice(l.a2aAuthRequiredDetail),
            if (task?.state == ExternalTaskState.unknown)
              _Notice(l.a2aUnknownDetail),
            if (task?.parts.isNotEmpty == true) ...[
              Text(l.a2aAgentOutput, style: theme.textTheme.titleLarge),
              const SizedBox(height: 8),
              for (final part in task!.parts)
                Card(
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        if (part.name?.isNotEmpty == true)
                          Padding(
                            padding: const EdgeInsets.only(bottom: 8),
                            child: Text(
                              part.name!,
                              style: theme.textTheme.titleSmall,
                            ),
                          ),
                        if (part.text != null) SelectableText(part.text!),
                        if (part.url != null) ...[
                          Text(
                            safeExternalLinkUri(part.url)?.host ??
                                l.a2aBlockedLink,
                          ),
                          const SizedBox(height: 8),
                          OutlinedButton.icon(
                            onPressed: () =>
                                openExternalLink(context, part.url),
                            icon: const Icon(Icons.open_in_new_rounded),
                            label: Text(l.a2aReviewLink),
                          ),
                        ],
                      ],
                    ),
                  ),
                ),
            ],
            if (task?.omittedContent == true) _Notice(l.a2aOmittedContent),
            if (draft || task?.state == ExternalTaskState.inputRequired) ...[
              if (draft) _Notice(l.a2aSendDetail),
              const SizedBox(height: 16),
              TextField(
                controller: _text,
                onChanged: draft
                    ? (value) => unawaited(_persistDraft(value))
                    : null,
                enabled: !c.busy && c.current && !record.uncertain,
                minLines: 3,
                maxLines: 8,
                maxLength: 16000,
                decoration: InputDecoration(
                  labelText: draft ? l.a2aTaskPrompt : l.a2aYourReply,
                ),
              ),
              const SizedBox(height: 12),
              FilledButton.icon(
                onPressed:
                    ((draft &&
                            c.current &&
                            c.foreground &&
                            !c.busy &&
                            _draftIsSaved) ||
                        c.canContinue)
                    ? () => _send(continuation: !draft)
                    : null,
                icon: const Icon(Icons.send_rounded),
                label: Text(draft ? l.a2aSend : l.a2aReplySameTask),
              ),
              const SizedBox(height: 20),
            ],
            if (task?.id != null) ...[
              const SizedBox(height: 20),
              OutlinedButton.icon(
                onPressed: c.busy || !c.current ? null : c.refresh,
                icon: const Icon(Icons.refresh_rounded),
                label: Text(l.a2aRefresh),
              ),
              if (!task!.terminal)
                TextButton(
                  onPressed: c.canCancel ? _cancel : null,
                  child: Text(l.a2aCancelTask),
                ),
            ],
            const SizedBox(height: 16),
            TextButton(onPressed: _forget, child: Text(l.a2aForgetTask)),
          ],
        ),
      ),
    );
  }
}
