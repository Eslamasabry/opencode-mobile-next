import 'package:flutter/material.dart';

import '../../api/models.dart';
import '../../api/product_repository.dart';
import '../../api2/models.dart' show Api2FormInfo;
import '../../state/connection.dart';
import '../app_theme.dart';
import '../desktop/desktop_interaction.dart';
import '../permission_presentation.dart';
import '../widgets/product_states.dart';
import 'chat/form_flow.dart';
import 'chat/permission_sheet.dart';
import 'global_sessions_screen.dart';

/// Activity: the single cross-session control centre (audit §3, §8).
///
/// It replaces the former Mission Control and Pending requests screens, which
/// showed the same pending count behind two mental models. One destination,
/// one badge, three sections:
///
/// 1. **Needs attention** — permissions, questions, and v2 forms, each row
///    opening the *exact* resolver (the same permission sheet and form flow
///    chat uses), never merely a link to the related chat.
/// 2. **Running** — sessions busy right now, with their subagent counts.
/// 3. **Recently completed** — where recent work stopped.
///
/// Every row is server truth the controller already holds; nothing here is
/// estimated. Cross-project discovery stays with the all-sessions finder,
/// reachable from the footer.
class ActivityScreen extends StatefulWidget {
  final ConnectionController controller;

  /// A notification tap can name the session whose question should open
  /// immediately, so the alert lands on the answer rather than a list.
  final String? initialQuestionSessionID;

  /// True when Activity is hosted as a primary navigation destination, which
  /// already supplies the app bar. Pushed routes (deep links, notifications)
  /// keep their own Scaffold.
  final bool embedded;

  const ActivityScreen({
    super.key,
    required this.controller,
    this.initialQuestionSessionID,
    this.embedded = false,
  });

  @override
  State<ActivityScreen> createState() => _ActivityScreenState();
}

class _ActivityScreenState extends State<ActivityScreen> {
  /// The embedded tab is built by the shell's IndexedStack at launch and
  /// stays alive; it takes its truth from the controller's own hydration and
  /// live events, and reconciles on pull-to-refresh. Only a pushed Activity —
  /// a deep link or a notification tap — pays for an entry refresh, exactly
  /// as the former Requests screen did.
  late bool _loading = !widget.embedded;
  bool _refreshing = false;
  String? _error;
  bool _initialQuestionScheduled = false;
  bool _initialQuestionHandled = false;

  @override
  void initState() {
    super.initState();
    widget.controller.addListener(_changed);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      if (!widget.embedded) _refreshPending();
      _scheduleInitialQuestion();
    });
  }

  @override
  void dispose() {
    widget.controller.removeListener(_changed);
    super.dispose();
  }

  void _changed() {
    if (!mounted) return;
    setState(() {});
    _scheduleInitialQuestion();
  }

  void _scheduleInitialQuestion() {
    final sessionID = widget.initialQuestionSessionID;
    if (!mounted ||
        sessionID == null ||
        _initialQuestionHandled ||
        _initialQuestionScheduled) {
      return;
    }
    PendingQuestion? target;
    for (final question in widget.controller.questions.values) {
      if (question.sessionID == sessionID) {
        target = question;
        break;
      }
    }
    if (target == null) return;
    _initialQuestionScheduled = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _initialQuestionScheduled = false;
      if (!mounted || _initialQuestionHandled) return;
      final current = widget.controller.questions[target!.id];
      if (current == null || current.sessionID != sessionID) return;
      _initialQuestionHandled = true;
      showQuestionSheet(context, widget.controller, current);
    });
  }

  /// Pending work only — the cheap half, run on entry so a notification tap
  /// never shows a stale queue.
  Future<void> _refreshPending() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      await Future.wait([
        widget.controller.refreshPendingPermissions(),
        widget.controller.refreshPendingQuestions(),
        widget.controller.refreshPendingForms(),
      ]);
    } catch (error) {
      if (mounted) setState(() => _error = productErrorText(error));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  /// Wake-safe manual refresh: reconciliation first, so a retained screen
  /// cannot query through a repository being retired after Android idle.
  /// Refreshes both halves — pending work and the session fleet.
  Future<void> _refresh() async {
    if (_refreshing) return;
    setState(() {
      _refreshing = true;
      _error = null;
    });
    try {
      final repository = await widget.controller.prepareActionRepository();
      if (repository == null) {
        throw const ProductException('OpenCode is reconnecting. Try again.');
      }
      await widget.controller.refreshSessions();
    } catch (error) {
      if (mounted) setState(() => _error = productErrorText(error));
    } finally {
      if (mounted) setState(() => _refreshing = false);
    }
    if (!mounted) return;
    await _refreshPending();
  }

  int _subagentCount(String rootID) {
    var count = 0;
    for (final session in widget.controller.sessionsById.values) {
      if (session.parentID == rootID) count += 1;
    }
    return count;
  }

  void _openChat(String sessionID) {
    Navigator.of(context).pushNamed('/chat/$sessionID');
  }

  void _openFinder() {
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => GlobalSessionsScreen(controller: widget.controller),
      ),
    );
  }

  static String _relative(int? ms) {
    if (ms == null || ms <= 0) return '';
    final delta = DateTime.now().difference(
      DateTime.fromMillisecondsSinceEpoch(ms),
    );
    if (delta.inMinutes < 1) return 'now';
    if (delta.inHours < 1) return '${delta.inMinutes}m ago';
    if (delta.inDays < 1) return '${delta.inHours}h ago';
    return '${delta.inDays}d ago';
  }

  static String _place(Session session) {
    final directory = session.directory?.trim() ?? '';
    if (directory.isEmpty) return '';
    final parts = directory
        .split('/')
        .where((part) => part.isNotEmpty)
        .toList();
    return parts.isEmpty ? directory : parts.last;
  }

  @override
  Widget build(BuildContext context) {
    final controller = widget.controller;
    final permissions = controller.permissions.values.toList();
    final questions = controller.questions.values.toList()
      ..sort((a, b) {
        final selected = widget.initialQuestionSessionID;
        if (selected == null) return 0;
        final aSelected = a.sessionID == selected;
        final bSelected = b.sessionID == selected;
        return aSelected == bSelected ? 0 : (aSelected ? -1 : 1);
      });
    // §7 rule 5: forms are v2-only, so a v1 connection never lists them even
    // if a stale entry survived a server switch.
    final formsAvailable = controller.capabilities.forms;
    final sessionForms = !formsAvailable
        ? const <Api2FormInfo>[]
        : controller.forms.values
              .where((form) => form.sessionID != 'global')
              .toList();
    // Global (MCP elicitation) forms have no session to open, so they keep
    // their own subsection rather than pretending to map to a chat.
    final globalForms = !formsAvailable
        ? const <Api2FormInfo>[]
        : controller.forms.values
              .where((form) => form.sessionID == 'global')
              .toList();

    final roots = controller.sortedSessions();
    final running = roots
        .where((session) => controller.busySessions.contains(session.id))
        .toList();
    final recent = roots
        .where((session) => !controller.busySessions.contains(session.id))
        .take(8)
        .toList();

    final loading =
        _loading ||
        controller.permissionsLoading ||
        controller.questionsLoading ||
        controller.formsLoading;
    final error =
        _error ??
        controller.permissionsError ??
        controller.questionsError ??
        controller.formsError;
    final attentionCount =
        permissions.length +
        questions.length +
        sessionForms.length +
        globalForms.length;
    final empty = attentionCount == 0 && roots.isEmpty;

    final body = RefreshIndicator(
      onRefresh: _refresh,
      child: loading && empty
          ? const LoadingList()
          : error != null && empty
          ? ProductErrorState(message: error, onRetry: _refresh)
          : empty
          ? ListView(
              physics: const AlwaysScrollableScrollPhysics(),
              children: [
                const SizedBox(height: 80),
                ProductEmptyState(
                  icon: Icons.task_alt_rounded,
                  title: 'Nothing needs attention',
                  message:
                      'Permission requests, questions, and running sessions '
                      'appear here. Start one from Workspace, or find a '
                      'session anywhere on this server.',
                  actionLabel: 'All sessions',
                  onAction: _openFinder,
                ),
              ],
            )
          : DesktopScrollbarArea(
              builder: (scrollController) => ListView(
              controller: scrollController,
              physics: const AlwaysScrollableScrollPhysics(),
              padding: const EdgeInsets.only(bottom: 24),
              children: [
                if (loading) const LinearProgressIndicator(minHeight: 2),
                if (error != null)
                  ProductInlineEmpty(
                    icon: Icons.sync_problem_rounded,
                    title: 'Could not refresh',
                    message: error,
                    actionLabel: 'Try again',
                    onAction: _refresh,
                  ),
                const SectionLabel('Needs attention'),
                if (attentionCount == 0)
                  const ProductInlineEmpty(
                    icon: Icons.task_alt_rounded,
                    title: 'Nothing needs attention',
                    message:
                        'Permission requests, assistant questions, and forms '
                        'appear here the moment a session asks.',
                  ),
                for (final permission in permissions)
                  ActivityPermissionTile(
                    key: ValueKey('activity-permission-${permission.id}'),
                    permission: permission,
                    controller: controller,
                  ),
                for (final question in questions)
                  ActivityQuestionTile(
                    key: ValueKey('activity-question-${question.id}'),
                    question: question,
                    controller: controller,
                  ),
                for (final form in sessionForms)
                  ActivityFormTile(form: form, controller: controller),
                if (globalForms.isNotEmpty) ...[
                  const SectionLabel('Server requests'),
                  for (final form in globalForms)
                    ActivityFormTile(form: form, controller: controller),
                ],
                const SectionLabel('Running'),
                if (running.isEmpty)
                  const ProductInlineEmpty(
                    icon: AppIcons.run,
                    title: 'Nothing running',
                    message:
                        'Busy sessions appear here the moment a run starts.',
                  )
                else
                  for (final session in running)
                    _SessionRow(
                      key: ValueKey('activity-running-${session.id}'),
                      session: session,
                      running: true,
                      subagents: _subagentCount(session.id),
                      detail: _place(session),
                      onTap: () => _openChat(session.id),
                    ),
                if (recent.isNotEmpty) ...[
                  const SectionLabel('Recently completed'),
                  for (final session in recent)
                    _SessionRow(
                      key: ValueKey('activity-recent-${session.id}'),
                      session: session,
                      running: false,
                      subagents: _subagentCount(session.id),
                      detail: [
                        _relative(
                          session.time?.updated ?? session.time?.created,
                        ),
                        _place(session),
                      ].where((part) => part.isNotEmpty).join(' · '),
                      onTap: () => _openChat(session.id),
                    ),
                ],
                const Divider(height: 24),
                ListTile(
                  key: const ValueKey('activity-all-sessions'),
                  leading: const Icon(Icons.travel_explore_rounded),
                  title: const Text('All sessions, every project'),
                  trailing: const Icon(Icons.chevron_right_rounded),
                  onTap: _openFinder,
                ),
              ],
              ),
            ),
    );

    if (widget.embedded) return body;
    return Scaffold(
      appBar: AppBar(
        title: const Text('Activity'),
        actions: [
          IconButton(
            tooltip: 'Refresh',
            onPressed: _refreshing ? null : _refresh,
            icon: _refreshing
                ? const SizedBox.square(
                    dimension: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.refresh_rounded),
          ),
        ],
      ),
      body: body,
    );
  }
}

/// One permission component, three entry points: this row opens the same
/// sheet the chat auto-presents, so resolving here is resolving there.
class ActivityPermissionTile extends StatelessWidget {
  final PermissionRequest permission;
  final ConnectionController controller;

  const ActivityPermissionTile({
    super.key,
    required this.permission,
    required this.controller,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final title = permission.permission.isEmpty
        ? 'Permission required'
        : permissionRequestTitle(permission.permission);
    return ListTile(
      minTileHeight: 66,
      leading: Icon(Icons.shield_outlined, color: theme.colorScheme.tertiary),
      title: Text(title),
      subtitle: Text(
        permission.patterns.isNotEmpty
            ? permission.patterns.first
            : _sessionTitle(controller, permission.sessionID),
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        style: permission.patterns.isNotEmpty
            ? const TextStyle(
                fontFamily: AppTheme.monoFamily,
                fontSize: AppTheme.codeFontSize,
              )
            : null,
      ),
      trailing: const Icon(Icons.chevron_right_rounded),
      onTap: () => showPermissionSheet(
        context,
        permission: permission,
        supportsRejectMessage: controller.permissionSupportsRejectMessage(
          permission.id,
        ),
        contextLabel: 'for ${_sessionTitle(controller, permission.sessionID)}',
        onReply: (reply, {message}) =>
            controller.answerPermission(permission.id, reply, message: message),
      ),
    );
  }
}

class ActivityQuestionTile extends StatelessWidget {
  final PendingQuestion question;
  final ConnectionController controller;

  const ActivityQuestionTile({
    super.key,
    required this.question,
    required this.controller,
  });

  @override
  Widget build(BuildContext context) {
    return ListTile(
      minTileHeight: 66,
      leading: Icon(
        Icons.contact_support_outlined,
        color: Theme.of(context).colorScheme.primary,
      ),
      title: Text(
        question.prompts.isEmpty
            ? 'Assistant question'
            : question.prompts.first.title,
      ),
      subtitle: Text(
        question.prompts.isEmpty
            ? _sessionTitle(controller, question.sessionID)
            : question.prompts.first.question,
        maxLines: 2,
        overflow: TextOverflow.ellipsis,
      ),
      trailing: const Icon(Icons.chevron_right_rounded),
      onTap: () => showQuestionSheet(context, controller, question),
    );
  }
}

class ActivityFormTile extends StatelessWidget {
  final Api2FormInfo form;
  final ConnectionController controller;

  const ActivityFormTile({
    super.key,
    required this.form,
    required this.controller,
  });

  @override
  Widget build(BuildContext context) {
    final count = form.fields.length;
    return ListTile(
      key: ValueKey('form-request-tile-${form.id}'),
      minTileHeight: 66,
      leading: Icon(
        Icons.fact_check_outlined,
        color: Theme.of(context).colorScheme.primary,
      ),
      title: Text(form.title ?? 'Input requested'),
      subtitle: Text(
        form.sessionID == 'global'
            ? 'Asked by an MCP server'
            : '$count question${count == 1 ? '' : 's'} · '
                  '${_sessionTitle(controller, form.sessionID)}',
        maxLines: 2,
        overflow: TextOverflow.ellipsis,
      ),
      trailing: const Icon(Icons.chevron_right_rounded),
      onTap: () => presentConnectionForm(context, controller, form),
    );
  }
}

/// The exact answer surface, shared by Activity rows and notification taps.
Future<void> showQuestionSheet(
  BuildContext context,
  ConnectionController controller,
  PendingQuestion question,
) => showModalBottomSheet<void>(
  context: context,
  isScrollControlled: true,
  showDragHandle: true,
  builder: (_) => _QuestionSheet(question: question, controller: controller),
);

class _SessionRow extends StatelessWidget {
  final Session session;
  final bool running;
  final int subagents;
  final String detail;
  final VoidCallback onTap;

  const _SessionRow({
    super.key,
    required this.session,
    required this.running,
    required this.subagents,
    required this.detail,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final title = session.title?.trim().isNotEmpty == true
        ? session.title!.trim()
        : 'Untitled session';
    return ListTile(
      leading: running
          ? SizedBox.square(
              dimension: 20,
              child: CircularProgressIndicator(
                strokeWidth: 2,
                color: theme.colorScheme.primary,
              ),
            )
          : Icon(
              Icons.chat_bubble_outline_rounded,
              color: theme.colorScheme.onSurfaceVariant,
            ),
      title: Text(title, maxLines: 1, overflow: TextOverflow.ellipsis),
      subtitle: detail.isEmpty
          ? null
          : Text(detail, maxLines: 1, overflow: TextOverflow.ellipsis),
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (subagents > 0)
            Tooltip(
              message: '$subagents subagent${subagents == 1 ? '' : 's'}',
              child: Badge(
                label: Text('$subagents'),
                child: const Icon(Icons.account_tree_outlined, size: 19),
              ),
            ),
          const SizedBox(width: 4),
          const Icon(Icons.chevron_right_rounded),
        ],
      ),
      onTap: onTap,
    );
  }
}

class _QuestionSheet extends StatefulWidget {
  final PendingQuestion question;
  final ConnectionController controller;

  const _QuestionSheet({required this.question, required this.controller});

  @override
  State<_QuestionSheet> createState() => _QuestionSheetState();
}

class _QuestionSheetState extends State<_QuestionSheet> {
  late final List<Set<String>> _answers = List.generate(
    widget.question.prompts.length,
    (_) => <String>{},
  );
  late final List<TextEditingController> _custom = List.generate(
    widget.question.prompts.length,
    (_) => TextEditingController(),
  );
  bool _busy = false;
  String? _error;

  bool get _complete {
    for (var i = 0; i < widget.question.prompts.length; i++) {
      if (_answers[i].isEmpty && _custom[i].text.trim().isEmpty) return false;
    }
    return true;
  }

  Future<void> _submit() async {
    if (!_complete || _busy) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    final answers = <List<String>>[];
    for (var i = 0; i < _answers.length; i++) {
      final prompt = widget.question.prompts[i];
      final customAnswer = _custom[i].text.trim();
      if (prompt.multiple) {
        answers.add([
          ..._answers[i],
          if (customAnswer.isNotEmpty) customAnswer,
        ]);
      } else {
        answers.add([
          if (customAnswer.isNotEmpty)
            customAnswer
          else if (_answers[i].isNotEmpty)
            _answers[i].first,
        ]);
      }
    }
    try {
      await widget.controller.answerQuestion(widget.question.id, answers);
      if (mounted) Navigator.pop(context);
    } catch (error) {
      if (mounted) setState(() => _error = productErrorText(error));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _reject() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Dismiss this request?'),
        content: const Text(
          'OpenCode will continue without answers to these questions.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(
              backgroundColor: Theme.of(context).colorScheme.error,
            ),
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Dismiss'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;
    setState(() => _busy = true);
    try {
      await widget.controller.rejectQuestion(widget.question.id);
      if (mounted) Navigator.pop(context);
    } catch (error) {
      if (mounted) setState(() => _error = productErrorText(error));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final media = MediaQuery.of(context);
    final availableHeight = (media.size.height - media.viewInsets.bottom - 8)
        .clamp(96.0, media.size.height * .9);
    return Padding(
      padding: EdgeInsets.only(bottom: media.viewInsets.bottom),
      child: SafeArea(
        top: false,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(20, 0, 20, 8),
          child: SizedBox(
            height: availableHeight,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  child: ListView(
                    keyboardDismissBehavior:
                        ScrollViewKeyboardDismissBehavior.onDrag,
                    children: [
                      Text(
                        'OpenCode needs input',
                        style: theme.textTheme.titleLarge,
                      ),
                      const SizedBox(height: 4),
                      Text(
                        _sessionTitle(
                          widget.controller,
                          widget.question.sessionID,
                        ),
                        style: theme.textTheme.bodySmall?.copyWith(
                          color: AppTheme.mutedOf(theme),
                        ),
                      ),
                      const SizedBox(height: 12),
                      for (
                        var index = 0;
                        index < widget.question.prompts.length;
                        index++
                      )
                        Builder(
                          builder: (context) {
                            final prompt = widget.question.prompts[index];
                            return Padding(
                              padding: const EdgeInsets.only(bottom: 22),
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    prompt.title,
                                    style: theme.textTheme.labelLarge,
                                  ),
                                  const SizedBox(height: 4),
                                  Text(prompt.question),
                                  const SizedBox(height: 10),
                                  if (prompt.multiple)
                                    for (final choice in prompt.choices)
                                      CheckboxListTile(
                                        contentPadding: EdgeInsets.zero,
                                        dense: true,
                                        value: _answers[index].contains(
                                          choice.label,
                                        ),
                                        title: Text(choice.label),
                                        subtitle: choice.description.isEmpty
                                            ? null
                                            : Text(choice.description),
                                        controlAffinity:
                                            ListTileControlAffinity.leading,
                                        onChanged: (selected) {
                                          setState(() {
                                            if (selected == true) {
                                              _answers[index].add(choice.label);
                                            } else {
                                              _answers[index].remove(
                                                choice.label,
                                              );
                                            }
                                          });
                                        },
                                      )
                                  else
                                    RadioGroup<String>(
                                      groupValue: _answers[index].isEmpty
                                          ? null
                                          : _answers[index].first,
                                      onChanged: (value) {
                                        if (value == null) return;
                                        setState(() {
                                          _custom[index].clear();
                                          _answers[index]
                                            ..clear()
                                            ..add(value);
                                        });
                                      },
                                      child: Column(
                                        children: [
                                          for (final choice in prompt.choices)
                                            RadioListTile<String>(
                                              contentPadding: EdgeInsets.zero,
                                              value: choice.label,
                                              title: Text(choice.label),
                                              subtitle:
                                                  choice.description.isEmpty
                                                  ? null
                                                  : Text(choice.description),
                                            ),
                                        ],
                                      ),
                                    ),
                                  if (prompt.custom)
                                    TextField(
                                      controller: _custom[index],
                                      maxLines: 3,
                                      onChanged: (value) {
                                        setState(() {
                                          if (!prompt.multiple &&
                                              value.trim().isNotEmpty) {
                                            _answers[index].clear();
                                          }
                                        });
                                      },
                                      decoration: const InputDecoration(
                                        labelText: 'Your answer',
                                        border: OutlineInputBorder(),
                                      ),
                                    ),
                                ],
                              ),
                            );
                          },
                        ),
                    ],
                  ),
                ),
                if (_error != null)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 8),
                    child: Text(
                      _error!,
                      style: TextStyle(color: theme.colorScheme.error),
                    ),
                  ),
                Wrap(
                  alignment: WrapAlignment.end,
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    TextButton(
                      onPressed: _busy ? null : _reject,
                      child: const Text('Dismiss'),
                    ),
                    FilledButton(
                      onPressed: _complete && !_busy ? _submit : null,
                      child: _busy
                          ? const SizedBox.square(
                              dimension: 16,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                          : const Text('Send answers'),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  @override
  void dispose() {
    for (final controller in _custom) {
      controller.dispose();
    }
    super.dispose();
  }
}

String _sessionTitle(ConnectionController controller, String id) {
  final session = controller.sessionsById[id];
  return session?.title?.isNotEmpty == true ? session!.title! : 'Session $id';
}
