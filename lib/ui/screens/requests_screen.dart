import 'package:flutter/material.dart';

import '../../api/models.dart';
import '../../api/product_repository.dart';
import '../../state/connection.dart';
import '../permission_presentation.dart';
import '../widgets/product_states.dart';

class RequestsScreen extends StatefulWidget {
  final ConnectionController controller;
  final String? initialQuestionSessionID;

  const RequestsScreen({
    super.key,
    required this.controller,
    this.initialQuestionSessionID,
  });

  @override
  State<RequestsScreen> createState() => _RequestsScreenState();
}

class _RequestsScreenState extends State<RequestsScreen> {
  bool _loading = true;
  String? _error;
  bool _initialQuestionScheduled = false;
  bool _initialQuestionHandled = false;

  @override
  void initState() {
    super.initState();
    widget.controller.addListener(_changed);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      _refresh();
      _scheduleInitialQuestion();
    });
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
      _showQuestion(current);
    });
  }

  Future<void> _showQuestion(PendingQuestion question) =>
      showModalBottomSheet<void>(
        context: context,
        isScrollControlled: true,
        showDragHandle: true,
        builder: (_) =>
            _QuestionSheet(question: question, controller: widget.controller),
      );

  Future<void> _refresh() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      await Future.wait([
        widget.controller.refreshPendingPermissions(),
        widget.controller.refreshPendingQuestions(),
      ]);
    } catch (error) {
      if (mounted) setState(() => _error = error.toString());
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final permissions = widget.controller.permissions.values.toList();
    final questions = widget.controller.questions.values.toList()
      ..sort((a, b) {
        final selected = widget.initialQuestionSessionID;
        if (selected == null) return 0;
        final aSelected = a.sessionID == selected;
        final bSelected = b.sessionID == selected;
        return aSelected == bSelected ? 0 : (aSelected ? -1 : 1);
      });
    final loading =
        _loading ||
        widget.controller.permissionsLoading ||
        widget.controller.questionsLoading;
    final error =
        _error ??
        widget.controller.permissionsError ??
        widget.controller.questionsError;
    return Scaffold(
      appBar: AppBar(title: const Text('Pending requests')),
      body: loading && permissions.isEmpty && questions.isEmpty
          ? const LoadingList()
          : error != null && permissions.isEmpty && questions.isEmpty
          ? RefreshIndicator(
              onRefresh: _refresh,
              child: ProductErrorState(message: error, onRetry: _refresh),
            )
          : permissions.isEmpty && questions.isEmpty
          ? RefreshIndicator(
              onRefresh: _refresh,
              child: ListView(
                physics: const AlwaysScrollableScrollPhysics(),
                children: const [
                  SizedBox(height: 120),
                  ProductEmptyState(
                    icon: Icons.task_alt_rounded,
                    title: 'Nothing needs attention',
                    message:
                        'Permission requests and assistant questions will appear here.',
                  ),
                ],
              ),
            )
          : RefreshIndicator(
              onRefresh: _refresh,
              child: ListView(
                physics: const AlwaysScrollableScrollPhysics(),
                padding: const EdgeInsets.only(bottom: 24),
                children: [
                  if (loading) const LinearProgressIndicator(minHeight: 2),
                  if (error != null)
                    ListTile(
                      leading: Icon(
                        Icons.error_outline,
                        color: Theme.of(context).colorScheme.error,
                      ),
                      title: Text(error),
                      trailing: TextButton(
                        onPressed: _refresh,
                        child: const Text('Retry'),
                      ),
                    ),
                  if (permissions.isNotEmpty) ...[
                    SectionLabel('Permissions (${permissions.length})'),
                    for (final permission in permissions)
                      _PermissionTile(
                        permission: permission,
                        controller: widget.controller,
                      ),
                  ],
                  if (questions.isNotEmpty) ...[
                    SectionLabel('Questions (${questions.length})'),
                    for (final question in questions)
                      _QuestionTile(
                        question: question,
                        controller: widget.controller,
                      ),
                  ],
                ],
              ),
            ),
    );
  }

  @override
  void dispose() {
    widget.controller.removeListener(_changed);
    super.dispose();
  }
}

class _PermissionTile extends StatefulWidget {
  final PermissionRequest permission;
  final ConnectionController controller;

  const _PermissionTile({required this.permission, required this.controller});

  @override
  State<_PermissionTile> createState() => _PermissionTileState();
}

class _PermissionTileState extends State<_PermissionTile> {
  bool _busy = false;
  String? _error;

  Future<void> _reply(String value) async {
    if (_busy) return;
    if (value == 'reject') {
      final confirmed = await showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text('Reject permission?'),
          content: const Text(
            'The current OpenCode action will not receive this permission.',
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
              child: const Text('Reject'),
            ),
          ],
        ),
      );
      if (confirmed != true) return;
    } else if (value == 'always') {
      final broader = widget.permission.always.isNotEmpty
          ? widget.permission.always
          : widget.permission.patterns;
      final confirmed = await showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
          scrollable: true,
          title: const Text('Confirm broader access'),
          content: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'Context: ${permissionRequestTitle(widget.permission.permission)} for '
                '${_sessionTitle(widget.controller, widget.permission.sessionID)}',
              ),
              const SizedBox(height: 12),
              const Text('Always allow patterns:'),
              const SizedBox(height: 4),
              SelectableText(
                broader.isEmpty
                    ? '(all matching requests)'
                    : broader.join('\n'),
                style: const TextStyle(fontFamily: 'monospace'),
              ),
              const SizedBox(height: 12),
              const Text(
                'Future matching actions can run without another prompt until OpenCode restarts. Allow once is safer.',
              ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('Keep asking'),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(context, true),
              child: const Text('Confirm always allow'),
            ),
          ],
        ),
      );
      if (confirmed != true) return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await widget.controller.answerPermission(widget.permission.id, value);
    } catch (error) {
      if (mounted) setState(() => _error = error.toString());
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final permission = widget.permission;
    final theme = Theme.of(context);
    final title = permission.permission.isEmpty
        ? 'Permission required'
        : permissionRequestTitle(permission.permission);
    return ExpansionTile(
      leading: Icon(Icons.shield_outlined, color: theme.colorScheme.tertiary),
      title: Text(title),
      subtitle: Text(
        _sessionTitle(widget.controller, permission.sessionID),
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
      ),
      childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
      children: [
        Align(
          alignment: Alignment.centerLeft,
          child: SelectableText(
            permission.patterns.isEmpty
                ? 'OpenCode requested ${permission.permission} access.'
                : permission.patterns.join('\n'),
            style: const TextStyle(fontFamily: 'monospace', fontSize: 12.5),
          ),
        ),
        if (permission.always.isNotEmpty) ...[
          const SizedBox(height: 12),
          Align(
            alignment: Alignment.centerLeft,
            child: Text(
              'Always allow scope: ${permission.always.join(', ')}',
              style: theme.textTheme.bodySmall?.copyWith(
                color: theme.hintColor,
              ),
            ),
          ),
        ],
        if (_error != null) ...[
          const SizedBox(height: 10),
          Align(
            alignment: Alignment.centerLeft,
            child: Text(
              _error!,
              style: TextStyle(color: theme.colorScheme.error),
            ),
          ),
        ],
        const SizedBox(height: 14),
        Wrap(
          alignment: WrapAlignment.end,
          spacing: 6,
          runSpacing: 8,
          children: [
            TextButton(
              onPressed: _busy ? null : () => _reply('reject'),
              child: const Text('Reject'),
            ),
            OutlinedButton(
              onPressed: _busy ? null : () => _reply('always'),
              child: const Text('Always'),
            ),
            FilledButton(
              onPressed: _busy ? null : () => _reply('once'),
              child: _busy
                  ? const SizedBox.square(
                      dimension: 16,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Text('Allow once'),
            ),
          ],
        ),
      ],
    );
  }
}

class _QuestionTile extends StatelessWidget {
  final PendingQuestion question;
  final ConnectionController controller;

  const _QuestionTile({required this.question, required this.controller});

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
      onTap: () => showModalBottomSheet<void>(
        context: context,
        isScrollControlled: true,
        showDragHandle: true,
        builder: (_) =>
            _QuestionSheet(question: question, controller: controller),
      ),
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
      if (mounted) setState(() => _error = error.toString());
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
      if (mounted) setState(() => _error = error.toString());
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
                          color: theme.hintColor,
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
