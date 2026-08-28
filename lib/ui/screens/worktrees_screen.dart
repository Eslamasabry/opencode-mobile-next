import 'dart:async';

import 'package:flutter/material.dart';

import '../../api/models.dart';
import '../../api/product_repository.dart';
import '../../state/connection.dart';
import '../widgets/product_states.dart';

class WorktreesScreen extends StatefulWidget {
  final ConnectionController controller;
  final WorkspaceProject project;

  const WorktreesScreen({
    super.key,
    required this.controller,
    required this.project,
  });

  @override
  State<WorktreesScreen> createState() => _WorktreesScreenState();
}

class _WorktreesScreenState extends State<WorktreesScreen> {
  List<WorktreeInfo>? _worktrees;
  final Map<String, WorktreeInfo> _knownWorktrees = {};
  final Set<String> _preparing = {};
  final Map<String, String> _failures = {};
  final Map<String, Timer> _preparationTimers = {};
  StreamSubscription<EventEnvelope>? _events;
  String? _loadError;
  String? _busyDirectory;
  bool _creating = false;
  int _loadGeneration = 0;

  @override
  void initState() {
    super.initState();
    _events = widget.controller.events.listen(_handleEvent);
    unawaited(_load());
  }

  Future<void> _load() async {
    final generation = ++_loadGeneration;
    final repository = await widget.controller.prepareActionRepository();
    if (!mounted || generation != _loadGeneration) return;
    if (repository == null) {
      setState(() {
        _loadError = 'OpenCode is reconnecting. Try again shortly.';
      });
      return;
    }
    try {
      final worktrees = await repository.listWorktrees(
        projectDirectory: widget.project.directory,
        projectID: widget.project.id,
      );
      if (!mounted || generation != _loadGeneration) return;
      final merged = worktrees.map((worktree) {
        final known = _knownWorktrees[worktree.directory];
        return known == null
            ? worktree
            : WorktreeInfo(
                name: known.name,
                directory: worktree.directory,
                branch: known.branch,
              );
      }).toList()..sort((a, b) => a.name.compareTo(b.name));
      setState(() {
        _worktrees = _dedupeWorktrees(merged);
        _loadError = null;
      });
    } catch (error) {
      if (!mounted || generation != _loadGeneration) return;
      setState(() => _loadError = error.toString());
    }
  }

  void _handleEvent(EventEnvelope event) {
    if (!mounted ||
        (event.type != 'worktree.ready' && event.type != 'worktree.failed')) {
      return;
    }
    final directory = event.directory?.trim() ?? '';
    if (directory.isEmpty || !_knownWorktrees.containsKey(directory)) return;
    _preparationTimers.remove(directory)?.cancel();
    if (event.type == 'worktree.ready') {
      final current = _knownWorktrees[directory]!;
      final name = event.properties['name']?.toString().trim();
      final branch = event.properties['branch']?.toString().trim();
      setState(() {
        _knownWorktrees[directory] = WorktreeInfo(
          name: name?.isNotEmpty == true ? name! : current.name,
          directory: directory,
          branch: branch?.isNotEmpty == true ? branch : current.branch,
        );
        _preparing.remove(directory);
        _failures.remove(directory);
        _replaceKnownWorktree(directory);
      });
      _showMessage('${_basename(directory)} is ready');
      return;
    }
    final message = event.properties['message']?.toString().trim();
    setState(() {
      _preparing.remove(directory);
      _failures[directory] = message?.isNotEmpty == true
          ? message!
          : 'OpenCode could not prepare this worktree.';
    });
  }

  void _replaceKnownWorktree(String directory) {
    final current = _worktrees;
    final known = _knownWorktrees[directory];
    if (current == null || known == null) return;
    final index = current.indexWhere((item) => item.directory == directory);
    if (index >= 0) current[index] = known;
  }

  List<WorktreeInfo> _dedupeWorktrees(List<WorktreeInfo> worktrees) {
    final byName = <String, WorktreeInfo>{};
    for (final worktree in worktrees) {
      final key = worktree.name.toLowerCase();
      final existing = byName[key];
      if (existing == null ||
          _isExactCurrent(worktree.directory) ||
          (!_isExactCurrent(existing.directory) &&
              _knownWorktrees.containsKey(worktree.directory))) {
        byName[key] = worktree;
      }
    }
    final result = byName.values.toList();
    result.sort((a, b) => a.name.compareTo(b.name));
    return result;
  }

  bool _isExactCurrent(String directory) =>
      widget.controller.directory == directory;

  bool _isCurrentWorktree(WorktreeInfo worktree) {
    final selected = widget.controller.directory;
    if (selected == worktree.directory) return true;
    if (selected == null || selected == widget.project.directory) return false;
    return widget.project.worktrees.contains(selected) &&
        _basename(selected) == worktree.name;
  }

  void _markPreparationUnconfirmed(String directory) {
    if (!mounted || !_preparing.contains(directory)) return;
    setState(() => _preparing.remove(directory));
    _showMessage(
      '${_basename(directory)} was created. Its setup status is not yet confirmed.',
    );
  }

  Future<ProductRepository> _repository() async {
    final repository = await widget.controller.prepareActionRepository();
    if (repository != null) return repository;
    throw const ProductException('OpenCode is reconnecting. Try again.');
  }

  Future<void> _create() async {
    if (_creating) return;
    final name = await showDialog<String>(
      context: context,
      builder: (context) => const _CreateWorktreeDialog(),
    );
    if (name == null || !mounted) return;
    setState(() => _creating = true);
    try {
      final created = await (await _repository()).createWorktree(
        projectDirectory: widget.project.directory,
        name: name,
      );
      if (!mounted) return;
      setState(() {
        _knownWorktrees[created.directory] = created;
        _preparing.add(created.directory);
        _failures.remove(created.directory);
        final current = _worktrees ?? <WorktreeInfo>[];
        if (!current.any((item) => item.directory == created.directory)) {
          current.add(created);
          current.sort((a, b) => a.name.compareTo(b.name));
        }
        _worktrees = current;
      });
      _preparationTimers[created.directory]?.cancel();
      _preparationTimers[created.directory] = Timer(
        const Duration(seconds: 45),
        () => _markPreparationUnconfirmed(created.directory),
      );
      _showMessage('${created.name} created. OpenCode is preparing it.');
    } catch (error) {
      if (mounted) _showError(error.toString());
    } finally {
      if (mounted) setState(() => _creating = false);
    }
  }

  Future<void> _open(String directory) async {
    if (_busyDirectory != null) return;
    if (_preparing.contains(directory)) {
      _showMessage('Wait for OpenCode to finish preparing this worktree.');
      return;
    }
    setState(() => _busyDirectory = directory);
    try {
      await widget.controller.selectLocation(directory: directory);
      if (!mounted) return;
      if (widget.controller.directory != directory) {
        throw const ProductException('OpenCode did not switch locations.');
      }
      Navigator.of(context).pop(true);
    } catch (error) {
      if (mounted) _showError(error.toString());
    } finally {
      if (mounted) setState(() => _busyDirectory = null);
    }
  }

  Future<List<VersionControlFile>?> _inspect(WorktreeInfo worktree) async {
    setState(() => _busyDirectory = worktree.directory);
    try {
      return await (await _repository()).listWorktreeFileStatuses(
        worktree.directory,
      );
    } catch (error) {
      if (mounted) {
        _showError(
          'Could not verify ${worktree.name} before this destructive action: $error',
        );
      }
      return null;
    } finally {
      if (mounted) setState(() => _busyDirectory = null);
    }
  }

  Future<void> _reset(WorktreeInfo worktree) async {
    if (_busyDirectory != null) return;
    final changes = await _inspect(worktree);
    if (!mounted || changes == null) return;
    final confirmed = await _confirmReset(worktree, changes);
    if (!mounted || !confirmed) return;
    setState(() => _busyDirectory = worktree.directory);
    try {
      await (await _repository()).resetWorktree(
        projectDirectory: widget.project.directory,
        directory: worktree.directory,
      );
      if (_isCurrentWorktree(worktree)) {
        await widget.controller.selectLocation(
          directory: widget.project.directory,
        );
        await widget.controller.selectLocation(directory: worktree.directory);
      }
      if (!mounted) return;
      _showMessage('${worktree.name} reset to the default branch');
      await _load();
    } catch (error) {
      if (mounted) _showError(error.toString());
    } finally {
      if (mounted) setState(() => _busyDirectory = null);
    }
  }

  Future<void> _remove(WorktreeInfo worktree) async {
    if (_busyDirectory != null) return;
    final changes = await _inspect(worktree);
    if (!mounted || changes == null) return;
    final confirmed = await _confirmRemove(worktree, changes);
    if (!mounted || !confirmed) return;
    setState(() => _busyDirectory = worktree.directory);
    try {
      if (_isCurrentWorktree(worktree)) {
        await widget.controller.selectLocation(
          directory: widget.project.directory,
        );
      }
      await (await _repository()).removeWorktree(
        projectDirectory: widget.project.directory,
        directory: worktree.directory,
      );
      _preparationTimers.remove(worktree.directory)?.cancel();
      _knownWorktrees.remove(worktree.directory);
      _preparing.remove(worktree.directory);
      _failures.remove(worktree.directory);
      if (!mounted) return;
      _showMessage('${worktree.name} and its branch were removed');
      await _load();
    } catch (error) {
      if (mounted) _showError(error.toString());
    } finally {
      if (mounted) setState(() => _busyDirectory = null);
    }
  }

  Future<bool> _confirmReset(
    WorktreeInfo worktree,
    List<VersionControlFile> changes,
  ) async =>
      (await showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
          title: Text('Reset ${worktree.name}?'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _ChangeWarning(changes: changes),
                const SizedBox(height: 12),
                const Text(
                  'This permanently discards tracked changes and deletes all untracked and ignored files. Submodules are also reset and cleaned. This cannot be undone.',
                ),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('Cancel'),
            ),
            FilledButton(
              key: const ValueKey('confirm-reset-worktree'),
              style: FilledButton.styleFrom(
                backgroundColor: Theme.of(context).colorScheme.error,
              ),
              onPressed: () => Navigator.pop(context, true),
              child: const Text('Reset worktree'),
            ),
          ],
        ),
      )) ??
      false;

  Future<bool> _confirmRemove(
    WorktreeInfo worktree,
    List<VersionControlFile> changes,
  ) async =>
      (await showDialog<bool>(
        context: context,
        builder: (context) =>
            _RemoveWorktreeDialog(worktree: worktree, changes: changes),
      )) ??
      false;

  @override
  Widget build(BuildContext context) {
    final worktrees = _worktrees;
    return Scaffold(
      appBar: AppBar(
        title: const Text('Worktrees'),
        actions: [
          IconButton(
            tooltip: 'Refresh worktrees',
            onPressed: _busyDirectory == null && !_creating ? _load : null,
            icon: const Icon(Icons.refresh_rounded),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        key: const ValueKey('create-worktree'),
        onPressed: _creating ? null : _create,
        icon: _creating
            ? const SizedBox.square(
                dimension: 18,
                child: CircularProgressIndicator(strokeWidth: 2),
              )
            : const Icon(Icons.add_rounded),
        label: const Text('New worktree'),
      ),
      body: RefreshIndicator(
        onRefresh: _load,
        child: ListView(
          key: const ValueKey('worktrees-list'),
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.only(bottom: 104),
          children: [
            ListTile(
              leading: const Icon(Icons.call_split_rounded),
              title: Text(widget.project.name),
              subtitle: const Text(
                'Use isolated branches for parallel coding without mixing changes.',
              ),
            ),
            const SectionLabel('Primary'),
            _LocationTile(
              key: const ValueKey('primary-worktree'),
              name: _basename(widget.project.directory),
              directory: widget.project.directory,
              primary: true,
              current: widget.controller.directory == widget.project.directory,
              busy: _busyDirectory == widget.project.directory,
              onOpen: () => _open(widget.project.directory),
            ),
            SectionLabel(
              'Worktrees',
              trailing: worktrees == null ? null : Text('${worktrees.length}'),
            ),
            if (worktrees == null && _loadError == null)
              const SizedBox(height: 216, child: LoadingList(rows: 3))
            else if (_loadError != null)
              ProductErrorState(message: _loadError!, onRetry: _load)
            else if (worktrees!.isEmpty)
              const ListTile(
                key: ValueKey('no-worktrees'),
                leading: Icon(Icons.account_tree_outlined),
                title: Text('No isolated worktrees yet'),
                subtitle: Text(
                  'Create one when you want OpenCode to work on a separate branch.',
                ),
              )
            else
              for (var index = 0; index < worktrees.length; index++) ...[
                _WorktreeTile(
                  worktree: worktrees[index],
                  current: _isCurrentWorktree(worktrees[index]),
                  preparing: _preparing.contains(worktrees[index].directory),
                  failure: _failures[worktrees[index].directory],
                  busy: _busyDirectory == worktrees[index].directory,
                  onOpen: () => _open(worktrees[index].directory),
                  onReset: () => _reset(worktrees[index]),
                  onRemove: () => _remove(worktrees[index]),
                ),
                if (index != worktrees.length - 1)
                  const Divider(height: 1, indent: 56),
              ],
          ],
        ),
      ),
    );
  }

  static String _basename(String path) {
    final parts = path.split('/').where((part) => part.isNotEmpty).toList();
    return parts.isEmpty ? path : parts.last;
  }

  void _showMessage(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }

  void _showError(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(
        SnackBar(
          content: Text(message),
          backgroundColor: Theme.of(context).colorScheme.error,
        ),
      );
  }

  @override
  void dispose() {
    _loadGeneration++;
    _events?.cancel();
    for (final timer in _preparationTimers.values) {
      timer.cancel();
    }
    super.dispose();
  }
}

class _LocationTile extends StatelessWidget {
  final String name;
  final String directory;
  final bool primary;
  final bool current;
  final bool busy;
  final VoidCallback onOpen;

  const _LocationTile({
    super.key,
    required this.name,
    required this.directory,
    required this.primary,
    required this.current,
    required this.busy,
    required this.onOpen,
  });

  @override
  Widget build(BuildContext context) => ListTile(
    selected: current,
    leading: Icon(primary ? Icons.home_work_outlined : Icons.account_tree),
    title: Text(name),
    subtitle: Text(
      primary ? 'Default project · $directory' : directory,
      maxLines: 2,
      overflow: TextOverflow.ellipsis,
    ),
    trailing: busy
        ? const SizedBox.square(
            dimension: 20,
            child: CircularProgressIndicator(strokeWidth: 2),
          )
        : current
        ? const Icon(Icons.check_circle_rounded)
        : const Icon(Icons.chevron_right_rounded),
    onTap: busy || current ? null : onOpen,
  );
}

class _WorktreeTile extends StatelessWidget {
  final WorktreeInfo worktree;
  final bool current;
  final bool preparing;
  final String? failure;
  final bool busy;
  final VoidCallback onOpen;
  final VoidCallback onReset;
  final VoidCallback onRemove;

  const _WorktreeTile({
    required this.worktree,
    required this.current,
    required this.preparing,
    required this.failure,
    required this.busy,
    required this.onOpen,
    required this.onReset,
    required this.onRemove,
  });

  @override
  Widget build(BuildContext context) {
    final status = failure != null
        ? 'Setup failed · $failure'
        : preparing
        ? 'Preparing files and project tasks…'
        : worktree.branch?.isNotEmpty == true
        ? worktree.branch!
        : worktree.directory;
    return ListTile(
      key: ValueKey('worktree-${worktree.directory}'),
      selected: current,
      leading: preparing || busy
          ? const SizedBox.square(
              dimension: 22,
              child: CircularProgressIndicator(strokeWidth: 2),
            )
          : Icon(
              failure == null
                  ? Icons.account_tree_outlined
                  : Icons.error_outline_rounded,
              color: failure == null
                  ? null
                  : Theme.of(context).colorScheme.error,
            ),
      title: Text(worktree.name),
      subtitle: Text(status, maxLines: 2, overflow: TextOverflow.ellipsis),
      onTap: busy || preparing || current ? null : onOpen,
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (current)
            const Padding(
              padding: EdgeInsets.only(right: 2),
              child: Icon(Icons.check_circle_rounded, size: 20),
            ),
          PopupMenuButton<String>(
            tooltip: 'Worktree actions',
            enabled: !busy,
            onSelected: (action) {
              if (action == 'open') onOpen();
              if (action == 'reset') onReset();
              if (action == 'remove') onRemove();
            },
            itemBuilder: (context) => [
              if (!current && !preparing && failure == null)
                const PopupMenuItem(value: 'open', child: Text('Open')),
              if (!preparing && failure == null)
                const PopupMenuItem(value: 'reset', child: Text('Reset')),
              const PopupMenuItem(value: 'remove', child: Text('Remove')),
            ],
          ),
        ],
      ),
    );
  }
}

class _ChangeWarning extends StatelessWidget {
  final List<VersionControlFile> changes;

  const _ChangeWarning({required this.changes});

  @override
  Widget build(BuildContext context) {
    final clean = changes.isEmpty;
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(
          clean ? Icons.check_circle_outline_rounded : Icons.warning_amber,
          color: clean ? null : Theme.of(context).colorScheme.error,
        ),
        const SizedBox(width: 10),
        Expanded(
          child: Text(
            clean
                ? 'No changed files were detected.'
                : '${changes.length} changed ${changes.length == 1 ? 'file was' : 'files were'} detected.',
          ),
        ),
      ],
    );
  }
}

class _CreateWorktreeDialog extends StatefulWidget {
  const _CreateWorktreeDialog();

  @override
  State<_CreateWorktreeDialog> createState() => _CreateWorktreeDialogState();
}

class _CreateWorktreeDialogState extends State<_CreateWorktreeDialog> {
  final _controller = TextEditingController();

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: const Text('New worktree'),
    content: SingleChildScrollView(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'OpenCode will create an isolated Git branch and working directory. Project startup tasks run automatically.',
          ),
          const SizedBox(height: 16),
          TextField(
            key: const ValueKey('worktree-name-field'),
            controller: _controller,
            autofocus: true,
            textInputAction: TextInputAction.done,
            decoration: const InputDecoration(
              labelText: 'Name (optional)',
              hintText: 'mobile-review',
              helperText: 'OpenCode makes the name URL-safe and unique.',
            ),
            onSubmitted: (value) => Navigator.pop(context, value.trim()),
          ),
        ],
      ),
    ),
    actions: [
      TextButton(
        onPressed: () => Navigator.pop(context),
        child: const Text('Cancel'),
      ),
      FilledButton(
        key: const ValueKey('confirm-create-worktree'),
        onPressed: () => Navigator.pop(context, _controller.text.trim()),
        child: const Text('Create'),
      ),
    ],
  );

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }
}

class _RemoveWorktreeDialog extends StatefulWidget {
  final WorktreeInfo worktree;
  final List<VersionControlFile> changes;

  const _RemoveWorktreeDialog({required this.worktree, required this.changes});

  @override
  State<_RemoveWorktreeDialog> createState() => _RemoveWorktreeDialogState();
}

class _RemoveWorktreeDialogState extends State<_RemoveWorktreeDialog> {
  final _controller = TextEditingController();

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: Text('Remove ${widget.worktree.name}?'),
    content: SingleChildScrollView(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _ChangeWarning(changes: widget.changes),
          const SizedBox(height: 12),
          const Text(
            'The worktree directory and its Git branch will be permanently deleted. Existing chats remain in history, but their working directory will no longer exist.',
          ),
          const SizedBox(height: 16),
          TextField(
            key: const ValueKey('remove-worktree-confirmation'),
            controller: _controller,
            decoration: InputDecoration(
              labelText: 'Type ${widget.worktree.name} to confirm',
            ),
            onChanged: (_) => setState(() {}),
          ),
        ],
      ),
    ),
    actions: [
      TextButton(
        onPressed: () => Navigator.pop(context, false),
        child: const Text('Cancel'),
      ),
      FilledButton(
        key: const ValueKey('confirm-remove-worktree'),
        style: FilledButton.styleFrom(
          backgroundColor: Theme.of(context).colorScheme.error,
        ),
        onPressed: _controller.text == widget.worktree.name
            ? () => Navigator.pop(context, true)
            : null,
        child: const Text('Remove permanently'),
      ),
    ],
  );

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }
}
