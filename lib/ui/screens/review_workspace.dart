import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../api/models.dart';
import '../app_theme.dart';
import '../widgets/product_states.dart';

typedef ReviewDiffLoader = Future<List<FileDiff>> Function();

enum ReviewDiffMode { unified, split }

enum ReviewDiffScope { session, workingTree, branch }

class ReviewWorkspace extends StatefulWidget {
  const ReviewWorkspace({
    super.key,
    this.loadDiffs,
    this.loadWorkingTreeDiffs,
    this.loadBranchDiffs,
    this.initialScope = ReviewDiffScope.session,
    this.initialFile,
  }) : assert(
         loadDiffs != null ||
             loadWorkingTreeDiffs != null ||
             loadBranchDiffs != null,
         'At least one review diff loader is required.',
       );

  final ReviewDiffLoader? loadDiffs;
  final ReviewDiffLoader? loadWorkingTreeDiffs;
  final ReviewDiffLoader? loadBranchDiffs;
  final ReviewDiffScope initialScope;
  final String? initialFile;

  @override
  State<ReviewWorkspace> createState() => _ReviewWorkspaceState();
}

class _ReviewWorkspaceState extends State<ReviewWorkspace> {
  List<FileDiff>? _diffs;
  Object? _error;
  int _selectedFile = 0;

  /// Files whose diff was opened this session, per scope — the GitHub
  /// "Viewed" pattern, session-local only.
  final Set<String> _viewedFiles = {};
  int? _selectionStart;
  int? _selectionEnd;
  ReviewDiffMode _mode = ReviewDiffMode.unified;
  late ReviewDiffScope _scope;
  String? _pendingInitialFile;
  int _loadGeneration = 0;
  final ScrollController _vertical = ScrollController();
  final ScrollController _horizontal = ScrollController();

  @override
  void initState() {
    super.initState();
    final scopes = _availableScopes;
    _scope = scopes.contains(widget.initialScope)
        ? widget.initialScope
        : scopes.first;
    _pendingInitialFile = widget.initialFile;
    _load();
  }

  @override
  void dispose() {
    _vertical.dispose();
    _horizontal.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    final generation = ++_loadGeneration;
    setState(() {
      _error = null;
      _diffs = null;
    });
    try {
      final diffs = await _loaderFor(_scope)();
      if (!mounted || generation != _loadGeneration) return;
      setState(() {
        _diffs = diffs;
        final initialFile = _pendingInitialFile;
        final initialIndex = initialFile == null
            ? -1
            : diffs.indexWhere(
                (diff) =>
                    _normalizedPath(diff.file) == _normalizedPath(initialFile),
              );
        if (initialIndex >= 0) {
          _selectedFile = initialIndex;
        } else if (_selectedFile >= diffs.length) {
          _selectedFile = 0;
        }
        _markViewed(_selectedFile);
        _pendingInitialFile = null;
        _clearSelection();
      });
    } catch (error) {
      if (mounted && generation == _loadGeneration) {
        setState(() => _error = error);
      }
    }
  }

  void _markViewed(int index) {
    final diffs = _diffs;
    if (diffs == null || index < 0 || index >= diffs.length) return;
    _viewedFiles.add('$_scope:${diffs[index].file}');
  }

  bool _isViewed(FileDiff diff) =>
      _viewedFiles.contains('$_scope:${diff.file}');

  ReviewDiffLoader _loaderFor(ReviewDiffScope scope) => switch (scope) {
    ReviewDiffScope.session => widget.loadDiffs!,
    ReviewDiffScope.workingTree => widget.loadWorkingTreeDiffs!,
    ReviewDiffScope.branch => widget.loadBranchDiffs!,
  };

  List<ReviewDiffScope> get _availableScopes => [
    if (widget.loadDiffs != null) ReviewDiffScope.session,
    if (widget.loadWorkingTreeDiffs != null) ReviewDiffScope.workingTree,
    if (widget.loadBranchDiffs != null) ReviewDiffScope.branch,
  ];

  void _selectScope(ReviewDiffScope scope) {
    if (_scope == scope) return;
    setState(() {
      _scope = scope;
      _selectedFile = 0;
      _clearSelection();
    });
    _load();
  }

  void _clearSelection() {
    _selectionStart = null;
    _selectionEnd = null;
  }

  void _selectFile(int index) {
    if (_selectedFile == index) return;
    setState(() {
      _selectedFile = index;
      _markViewed(index);
      _clearSelection();
    });
    if (_vertical.hasClients) _vertical.jumpTo(0);
    if (_horizontal.hasClients) _horizontal.jumpTo(0);
  }

  void _selectLine(int index) {
    setState(() {
      final start = _selectionStart;
      final end = _selectionEnd;
      if (start == null) {
        _selectionStart = index;
        _selectionEnd = index;
      } else if (start == end && start != index) {
        _selectionStart = start < index ? start : index;
        _selectionEnd = start > index ? start : index;
      } else if (start == index && end == index) {
        _clearSelection();
      } else {
        _selectionStart = index;
        _selectionEnd = index;
      }
    });
  }

  bool _isSelected(int index) {
    final start = _selectionStart;
    final end = _selectionEnd;
    return start != null && end != null && index >= start && index <= end;
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      key: const Key('review-workspace'),
      appBar: AppBar(
        title: const Text('Review changes'),
        actions: [
          IconButton(
            key: const Key('review-refresh'),
            tooltip: 'Refresh changes',
            onPressed: _diffs == null ? null : _load,
            icon: const Icon(Icons.refresh_rounded),
          ),
        ],
      ),
      body: SafeArea(top: false, child: _body(context)),
    );
  }

  Widget _body(BuildContext context) {
    final content = _reviewContent(context);
    if (_availableScopes.length == 1) return content;
    return Column(
      children: [
        _ReviewScopePicker(
          scopes: _availableScopes,
          selected: _scope,
          onSelected: _selectScope,
        ),
        Expanded(child: content),
      ],
    );
  }

  Widget _reviewContent(BuildContext context) {
    if (_diffs == null) {
      return _error == null
          ? const _ReviewLoadingState()
          : _ReviewErrorState(error: _error!, onRetry: _load);
    }
    if (_diffs!.isEmpty) {
      return RefreshIndicator(
        onRefresh: _load,
        child: const _ReviewEmptyState(),
      );
    }

    final diffs = _diffs!;
    final totalAdded = diffs.fold<int>(
      0,
      (sum, diff) => sum + diff.counts.added,
    );
    final totalRemoved = diffs.fold<int>(
      0,
      (sum, diff) => sum + diff.counts.removed,
    );
    final viewedCount = diffs.where(_isViewed).length;
    final summary = _ReviewSummary(
      files: diffs.length,
      added: totalAdded,
      removed: totalRemoved,
      viewed: viewedCount,
    );
    final selected = diffs[_selectedFile];
    final lines = _parseDiff(selected);

    return LayoutBuilder(
      builder: (context, constraints) {
        final wide = constraints.maxWidth >= 840;
        if (wide) {
          return Column(
            children: [
              summary,
              const Divider(height: 1),
              Expanded(
                child: Row(
                  children: [
                    SizedBox(
                      width: 292,
                      child: _ReviewFileList(
                        diffs: diffs,
                        selected: _selectedFile,
                        onSelected: _selectFile,
                      ),
                    ),
                    const VerticalDivider(width: 1),
                    Expanded(child: _diffPane(selected, lines, wide: true)),
                  ],
                ),
              ),
            ],
          );
        }
        final compactHeight = constraints.maxHeight < 360;
        return Column(
          children: [
            if (!compactHeight) summary,
            _ReviewFileStrip(
              diffs: diffs,
              selected: _selectedFile,
              onSelected: _selectFile,
              isViewed: _isViewed,
            ),
            const Divider(height: 1),
            Expanded(
              child: _diffPane(selected, lines, compactToolbar: compactHeight),
            ),
          ],
        );
      },
    );
  }

  Widget _diffPane(
    FileDiff diff,
    List<_ReviewDiffLine> lines, {
    bool compactToolbar = false,
    bool wide = false,
  }) {
    final selectedCount = _selectionStart == null
        ? 0
        : _selectionEnd! - _selectionStart! + 1;
    final hunks = [
      for (final entry in lines.asMap().entries)
        if (entry.value.kind == _ReviewLineKind.hunk) entry.key,
    ];
    return Column(
      children: [
        _ReviewDiffToolbar(
          diff: diff,
          hunks: hunks,
          mode: _mode,
          onModeChanged: (mode) => setState(() {
            _mode = mode;
            _clearSelection();
          }),
          onCopy: () => _copyText(diff.patch ?? diff.after ?? ''),
          onAsk: () => _openCommentComposer(diff, lines, wholeFile: true),
          onHunk: _jumpToHunk,
          compact: compactToolbar,
        ),
        const Divider(height: 1),
        Expanded(
          // Pull-to-refresh mirrors the app-wide idiom; the top-right refresh
          // icon remains for pointer users.
          child: RefreshIndicator(
            onRefresh: _load,
            notificationPredicate: (notification) =>
                notification.metrics.axis == Axis.vertical,
            child: lines.isEmpty
                ? const _NoDiffContent()
                : _ReviewDiffCanvas(
                    lines: lines,
                    mode: _mode,
                    vertical: _vertical,
                    horizontal: _horizontal,
                    isSelected: _isSelected,
                    onSelect: _selectLine,
                  ),
          ),
        ),
        if (selectedCount > 0)
          _ReviewSelectionBar(
            count: selectedCount,
            onClear: () => setState(_clearSelection),
            onCopy: () => _copyText(_selectedSnippet(lines)),
            onComment: () => _openCommentComposer(diff, lines),
          )
        else if (!wide && !compactToolbar && hunks.isNotEmpty)
          // Mirror hunk navigation into the thumb-reachable bottom slot so
          // one-handed review does not require repeated top-corner reaches.
          _ReviewHunkBar(hunks: hunks, onHunk: _jumpToHunk),
      ],
    );
  }

  Future<void> _copyText(String text) async {
    if (text.isEmpty) return;
    await Clipboard.setData(ClipboardData(text: text));
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(
        content: Text('Copied from review'),
        duration: Duration(seconds: 2),
      ),
    );
  }

  String _selectedSnippet(List<_ReviewDiffLine> lines) {
    final start = _selectionStart;
    final end = _selectionEnd;
    if (start == null || end == null) return '';
    return lines
        .asMap()
        .entries
        .where((entry) => entry.key >= start && entry.key <= end)
        .where((entry) => entry.value.selectable)
        .map((entry) => entry.value.text)
        .join('\n');
  }

  Future<void> _openCommentComposer(
    FileDiff diff,
    List<_ReviewDiffLine> lines, {
    bool wholeFile = false,
  }) async {
    final comment = await showModalBottomSheet<String>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      builder: (context) => _ReviewCommentComposer(
        file: diff.file,
        selectionLabel: wholeFile
            ? 'Entire file change'
            : _selectionLabel(lines),
      ),
    );
    if (!mounted || comment == null || comment.trim().isEmpty) return;

    final prompt = _reviewPrompt(
      diff: diff,
      comment: comment.trim(),
      snippet: wholeFile ? null : _selectedSnippet(lines),
      selectionLabel: wholeFile ? null : _selectionLabel(lines),
    );
    Navigator.of(context).pop(prompt);
  }

  String _selectionLabel(List<_ReviewDiffLine> lines) {
    final start = _selectionStart;
    final end = _selectionEnd;
    if (start == null || end == null) return 'Selected change';
    final selected = lines
        .asMap()
        .entries
        .where((entry) => entry.key >= start && entry.key <= end)
        .map((entry) => entry.value)
        .where((line) => line.selectable)
        .toList();
    final oldNumbers = selected.map((line) => line.oldLine).whereType<int>();
    final newNumbers = selected.map((line) => line.newLine).whereType<int>();
    final oldLabel = _lineRange(oldNumbers);
    final newLabel = _lineRange(newNumbers);
    if (oldLabel != null && newLabel != null) {
      return 'old $oldLabel · new $newLabel';
    }
    if (newLabel != null) return 'new $newLabel';
    if (oldLabel != null) return 'old $oldLabel';
    return '${selected.length} selected lines';
  }

  String? _lineRange(Iterable<int> numbers) {
    if (numbers.isEmpty) return null;
    final values = numbers.toList();
    return values.first == values.last
        ? 'line ${values.first}'
        : 'lines ${values.first}–${values.last}';
  }

  String _reviewPrompt({
    required FileDiff diff,
    required String comment,
    String? snippet,
    String? selectionLabel,
  }) {
    final out = StringBuffer('Review `${diff.file}`');
    if (selectionLabel != null) out.write(' ($selectionLabel)');
    out.write(':\n\n$comment');
    if (snippet?.trim().isNotEmpty == true) {
      out.write('\n\n```diff\n${snippet!.trimRight()}\n```');
    }
    return out.toString();
  }

  void _jumpToHunk(int direction, List<int> hunks) {
    if (hunks.isEmpty || !_vertical.hasClients) return;
    const rowExtent = _ReviewDiffCanvas.rowExtent;
    final currentRow = (_vertical.offset / rowExtent).round();
    int target;
    if (direction > 0) {
      target = hunks.firstWhere(
        (row) => row > currentRow + 1,
        orElse: () => hunks.first,
      );
    } else {
      target = hunks.lastWhere(
        (row) => row < currentRow - 1,
        orElse: () => hunks.last,
      );
    }
    _vertical.animateTo(
      target * rowExtent,
      duration: const Duration(milliseconds: 220),
      curve: Curves.easeOutCubic,
    );
  }

  static String _normalizedPath(String path) =>
      path.split('/').where((part) => part.isNotEmpty).join('/');
}

class _ReviewScopePicker extends StatelessWidget {
  const _ReviewScopePicker({
    required this.scopes,
    required this.selected,
    required this.onSelected,
  });

  final List<ReviewDiffScope> scopes;
  final ReviewDiffScope selected;
  final ValueChanged<ReviewDiffScope> onSelected;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.fromLTRB(12, 8, 12, 6),
    child: SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      child: SegmentedButton<ReviewDiffScope>(
        key: const Key('review-scope-picker'),
        showSelectedIcon: false,
        segments: [
          for (final scope in scopes)
            ButtonSegment(
              value: scope,
              label: Text(_scopeLabel(scope)),
              tooltip: _scopeDescription(scope),
            ),
        ],
        selected: {selected},
        onSelectionChanged: (value) => onSelected(value.single),
      ),
    ),
  );

  static String _scopeLabel(ReviewDiffScope scope) => switch (scope) {
    ReviewDiffScope.session => 'Session',
    ReviewDiffScope.workingTree => 'Working tree',
    ReviewDiffScope.branch => 'Branch',
  };

  static String _scopeDescription(ReviewDiffScope scope) => switch (scope) {
    ReviewDiffScope.session => 'Changes attributed to this OpenCode session',
    ReviewDiffScope.workingTree => 'Current uncommitted Git changes',
    ReviewDiffScope.branch => 'Changes against the default branch',
  };
}

class _ReviewSummary extends StatelessWidget {
  const _ReviewSummary({
    required this.files,
    required this.added,
    required this.removed,
    required this.viewed,
  });

  final int files;
  final int added;
  final int removed;
  final int viewed;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '$files changed ${files == 1 ? 'file' : 'files'}',
                  style: theme.textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.w600,
                    letterSpacing: -.2,
                  ),
                ),
                Text(
                  key: const ValueKey('review-viewed-progress'),
                  '$viewed of $files viewed',
                  style: theme.textTheme.labelSmall?.copyWith(
                    color: AppTheme.mutedOf(theme),
                  ),
                ),
              ],
            ),
          ),
          _ChangeCount(value: '+$added', added: true),
          const SizedBox(width: 10),
          _ChangeCount(value: '-$removed', added: false),
        ],
      ),
    );
  }
}

class _ChangeCount extends StatelessWidget {
  const _ChangeCount({required this.value, required this.added});

  final String value;
  final bool added;

  @override
  Widget build(BuildContext context) => Text(
    value,
    style: TextStyle(
      fontFamily: AppTheme.monoFamily,
      fontWeight: FontWeight.w600,
      color: added
          ? _additionColor(Theme.of(context))
          : Theme.of(context).colorScheme.error,
    ),
  );
}

class _ReviewFileStrip extends StatelessWidget {
  const _ReviewFileStrip({
    required this.diffs,
    required this.selected,
    required this.onSelected,
    required this.isViewed,
  });

  final List<FileDiff> diffs;
  final int selected;
  final ValueChanged<int> onSelected;
  final bool Function(FileDiff diff) isViewed;

  @override
  Widget build(BuildContext context) {
    final scale = MediaQuery.textScalerOf(context).scale(1);
    final extraHeight = (scale - 1).clamp(0.0, 1.0).toDouble() * 42;
    return SizedBox(
      key: const Key('review-file-strip'),
      height: 70 + extraHeight,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
        itemCount: diffs.length,
        separatorBuilder: (_, _) => const SizedBox(width: 4),
        itemBuilder: (context, index) => _ReviewFileTab(
          key: Key('review-file-$index'),
          diff: diffs[index],
          label: _fileTabLabel(diffs, index),
          selected: selected == index,
          viewed: isViewed(diffs[index]),
          onTap: () => onSelected(index),
        ),
      ),
    );
  }
}

class _ReviewFileTab extends StatelessWidget {
  const _ReviewFileTab({
    super.key,
    required this.diff,
    required this.label,
    required this.selected,
    required this.viewed,
    required this.onTap,
  });

  final FileDiff diff;
  final String label;
  final bool selected;
  final bool viewed;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final counts = diff.counts;
    return Semantics(
      selected: selected,
      button: true,
      label:
          '${diff.file}, ${_status(diff)}, '
          '${counts.added} additions, ${counts.removed} deletions',
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(AppTheme.radiusControl),
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 160),
          constraints: const BoxConstraints(minWidth: 150, maxWidth: 220),
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
          decoration: BoxDecoration(
            // A filled active tab reads at a glance; unselected tabs keep a
            // faint surface so the strip scans as tabs, not floating text.
            color: selected
                ? theme.colorScheme.primaryContainer.withValues(alpha: .85)
                : theme.colorScheme.surfaceContainerHigh.withValues(alpha: .35),
            borderRadius: BorderRadius.circular(AppTheme.radiusControl),
            border: Border(
              bottom: BorderSide(
                width: 2,
                color: selected
                    ? theme.colorScheme.primary
                    : Colors.transparent,
              ),
            ),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                width: 3,
                height: 28,
                color: _statusColor(context, diff),
              ),
              const SizedBox(width: 8),
              Flexible(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Flexible(
                          child: Text(
                            label,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: theme.textTheme.labelLarge?.copyWith(
                              fontFamily: AppTheme.monoFamily,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                        ),
                        if (viewed && !selected) ...[
                          const SizedBox(width: 4),
                          Icon(
                            Icons.check_circle_rounded,
                            size: 13,
                            color: AppTheme.successOf(theme),
                          ),
                        ],
                      ],
                    ),
                    Text(
                      '+${counts.added}  -${counts.removed}',
                      style: theme.textTheme.labelSmall?.copyWith(
                        fontFamily: AppTheme.monoFamily,
                        color: theme.colorScheme.onSurfaceVariant,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _ReviewFileList extends StatelessWidget {
  const _ReviewFileList({
    required this.diffs,
    required this.selected,
    required this.onSelected,
  });

  final List<FileDiff> diffs;
  final int selected;
  final ValueChanged<int> onSelected;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Column(
      key: const Key('review-file-list'),
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 14, 16, 8),
          child: Text(
            'Changed files',
            style: theme.textTheme.labelLarge?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
        ),
        Expanded(
          child: ListView.builder(
            itemCount: diffs.length,
            itemBuilder: (context, index) {
              final diff = diffs[index];
              final counts = diff.counts;
              final active = selected == index;
              return Semantics(
                selected: active,
                child: InkWell(
                  key: Key('review-file-$index'),
                  onTap: () => onSelected(index),
                  child: Container(
                    decoration: BoxDecoration(
                      color: active
                          ? theme.colorScheme.primaryContainer.withValues(
                              alpha: .32,
                            )
                          : null,
                      border: Border(
                        left: BorderSide(
                          width: 3,
                          color: active
                              ? theme.colorScheme.primary
                              : Colors.transparent,
                        ),
                      ),
                    ),
                    padding: const EdgeInsets.fromLTRB(12, 10, 14, 10),
                    child: Row(
                      children: [
                        Container(
                          width: 3,
                          height: 32,
                          color: _statusColor(context, diff),
                        ),
                        const SizedBox(width: 10),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                _basename(diff.file),
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: const TextStyle(
                                  fontFamily: AppTheme.monoFamily,
                                  fontWeight: FontWeight.w600,
                                ),
                              ),
                              Text(
                                _directory(diff.file),
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: theme.textTheme.labelSmall?.copyWith(
                                  color: theme.colorScheme.onSurfaceVariant,
                                ),
                              ),
                            ],
                          ),
                        ),
                        const SizedBox(width: 8),
                        Text(
                          '+${counts.added}\n-${counts.removed}',
                          textAlign: TextAlign.right,
                          style: theme.textTheme.labelSmall?.copyWith(
                            fontFamily: AppTheme.monoFamily,
                            height: 1.35,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              );
            },
          ),
        ),
      ],
    );
  }
}

class _ReviewDiffToolbar extends StatelessWidget {
  const _ReviewDiffToolbar({
    required this.diff,
    required this.hunks,
    required this.mode,
    required this.onModeChanged,
    required this.onCopy,
    required this.onAsk,
    required this.onHunk,
    required this.compact,
  });

  final FileDiff diff;
  final List<int> hunks;
  final ReviewDiffMode mode;
  final ValueChanged<ReviewDiffMode> onModeChanged;
  final VoidCallback onCopy;
  final VoidCallback onAsk;
  final void Function(int direction, List<int> hunks) onHunk;
  final bool compact;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final counts = diff.counts;
    return LayoutBuilder(
      builder: (context, constraints) {
        if (!compact && constraints.maxWidth < 560) {
          return _ReviewPhoneDiffToolbar(
            diff: diff,
            hunks: hunks,
            mode: mode,
            onModeChanged: onModeChanged,
            onCopy: onCopy,
            onAsk: onAsk,
            onHunk: onHunk,
          );
        }
        return _wideToolbar(context, theme, counts, hunks);
      },
    );
  }

  Widget _wideToolbar(
    BuildContext context,
    ThemeData theme,
    ({int added, int removed}) counts,
    List<int> hunks,
  ) {
    if (compact) {
      return SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
        child: Row(
          children: [
            _ModeButton(
              key: const Key('review-mode-unified'),
              label: 'Unified',
              selected: mode == ReviewDiffMode.unified,
              onPressed: () => onModeChanged(ReviewDiffMode.unified),
            ),
            _ModeButton(
              key: const Key('review-mode-split'),
              label: 'Split',
              selected: mode == ReviewDiffMode.split,
              onPressed: () => onModeChanged(ReviewDiffMode.split),
            ),
            const SizedBox(width: 8),
            Text('${hunks.length} ${hunks.length == 1 ? 'hunk' : 'hunks'}'),
            IconButton(
              tooltip: 'Previous hunk',
              onPressed: hunks.isEmpty ? null : () => onHunk(-1, hunks),
              icon: const RotatedBox(
                quarterTurns: 2,
                child: Icon(Icons.arrow_forward_rounded, size: 18),
              ),
            ),
            IconButton(
              tooltip: 'Next hunk',
              onPressed: hunks.isEmpty ? null : () => onHunk(1, hunks),
              icon: const Icon(Icons.arrow_forward_rounded, size: 18),
            ),
            TextButton(onPressed: onAsk, child: const Text('Ask')),
            TextButton(
              onPressed: (diff.patch ?? diff.after ?? '').isEmpty
                  ? null
                  : onCopy,
              child: const Text('Copy'),
            ),
          ],
        ),
      );
    }
    return Padding(
      padding: const EdgeInsets.fromLTRB(14, 10, 8, 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      diff.file,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: theme.textTheme.titleSmall?.copyWith(
                        fontFamily: AppTheme.monoFamily,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      '${_status(diff)} · +${counts.added} -${counts.removed}',
                      style: theme.textTheme.labelSmall?.copyWith(
                        color: theme.colorScheme.onSurfaceVariant,
                      ),
                    ),
                  ],
                ),
              ),
              TextButton(onPressed: onAsk, child: const Text('Ask about file')),
            ],
          ),
          const SizedBox(height: 8),
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: Row(
              children: [
                _ModeButton(
                  key: const Key('review-mode-unified'),
                  label: 'Unified',
                  selected: mode == ReviewDiffMode.unified,
                  onPressed: () => onModeChanged(ReviewDiffMode.unified),
                ),
                _ModeButton(
                  key: const Key('review-mode-split'),
                  label: 'Split',
                  selected: mode == ReviewDiffMode.split,
                  onPressed: () => onModeChanged(ReviewDiffMode.split),
                ),
                const SizedBox(width: 12),
                Text('${hunks.length} ${hunks.length == 1 ? 'hunk' : 'hunks'}'),
                IconButton(
                  tooltip: 'Previous hunk',
                  onPressed: hunks.isEmpty ? null : () => onHunk(-1, hunks),
                  icon: const RotatedBox(
                    quarterTurns: 2,
                    child: Icon(Icons.arrow_forward_rounded, size: 18),
                  ),
                ),
                IconButton(
                  tooltip: 'Next hunk',
                  onPressed: hunks.isEmpty ? null : () => onHunk(1, hunks),
                  icon: const Icon(Icons.arrow_forward_rounded, size: 18),
                ),
                TextButton(
                  onPressed: (diff.patch ?? diff.after ?? '').isEmpty
                      ? null
                      : onCopy,
                  child: const Text('Copy patch'),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

enum _ReviewFileAction { ask, copy }

class _ReviewPhoneDiffToolbar extends StatelessWidget {
  const _ReviewPhoneDiffToolbar({
    required this.diff,
    required this.hunks,
    required this.mode,
    required this.onModeChanged,
    required this.onCopy,
    required this.onAsk,
    required this.onHunk,
  });

  final FileDiff diff;
  final List<int> hunks;
  final ReviewDiffMode mode;
  final ValueChanged<ReviewDiffMode> onModeChanged;
  final VoidCallback onCopy;
  final VoidCallback onAsk;
  final void Function(int direction, List<int> hunks) onHunk;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final counts = diff.counts;
    final canCopy = (diff.patch ?? diff.after ?? '').isNotEmpty;
    return Semantics(
      container: true,
      label: 'Reviewing ${diff.file}',
      child: Padding(
        key: const Key('review-phone-toolbar'),
        padding: const EdgeInsets.fromLTRB(12, 8, 4, 6),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                Container(
                  width: 3,
                  height: 34,
                  color: _statusColor(context, diff),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: Tooltip(
                    message: diff.file,
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          _basename(diff.file),
                          key: const Key('review-current-file-name'),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: theme.textTheme.titleSmall?.copyWith(
                            fontFamily: AppTheme.monoFamily,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                        Text(
                          '${_status(diff)}  +${counts.added} -${counts.removed}',
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: theme.textTheme.labelSmall?.copyWith(
                            color: theme.colorScheme.onSurfaceVariant,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
                PopupMenuButton<_ReviewFileAction>(
                  key: const Key('review-file-actions'),
                  tooltip: 'File review actions',
                  onSelected: (action) {
                    switch (action) {
                      case _ReviewFileAction.ask:
                        onAsk();
                      case _ReviewFileAction.copy:
                        onCopy();
                    }
                  },
                  itemBuilder: (context) => [
                    const PopupMenuItem(
                      value: _ReviewFileAction.ask,
                      child: ListTile(
                        contentPadding: EdgeInsets.zero,
                        leading: Icon(Icons.chat_bubble_outline_rounded),
                        title: Text('Ask about file'),
                      ),
                    ),
                    PopupMenuItem(
                      value: _ReviewFileAction.copy,
                      enabled: canCopy,
                      child: const ListTile(
                        contentPadding: EdgeInsets.zero,
                        leading: Icon(AppIcons.copy),
                        title: Text('Copy patch'),
                      ),
                    ),
                  ],
                  icon: const Icon(Icons.more_horiz_rounded),
                ),
              ],
            ),
            const SizedBox(height: 5),
            Row(
              children: [
                Expanded(
                  child: _ModeButton(
                    key: const Key('review-mode-unified'),
                    label: 'Unified',
                    selected: mode == ReviewDiffMode.unified,
                    onPressed: () => onModeChanged(ReviewDiffMode.unified),
                  ),
                ),
                Expanded(
                  child: _ModeButton(
                    key: const Key('review-mode-split'),
                    label: 'Split',
                    selected: mode == ReviewDiffMode.split,
                    onPressed: () => onModeChanged(ReviewDiffMode.split),
                  ),
                ),
                const SizedBox(width: 4),
                SizedBox(
                  width: 36,
                  child: Text(
                    '${hunks.length}',
                    textAlign: TextAlign.center,
                    style: theme.textTheme.labelMedium,
                    semanticsLabel:
                        '${hunks.length} ${hunks.length == 1 ? 'hunk' : 'hunks'}',
                  ),
                ),
                IconButton(
                  key: const Key('review-previous-hunk'),
                  tooltip: 'Previous hunk',
                  onPressed: hunks.isEmpty ? null : () => onHunk(-1, hunks),
                  icon: const Icon(Icons.arrow_upward_rounded, size: 20),
                ),
                IconButton(
                  key: const Key('review-next-hunk'),
                  tooltip: 'Next hunk',
                  onPressed: hunks.isEmpty ? null : () => onHunk(1, hunks),
                  icon: const Icon(Icons.arrow_downward_rounded, size: 20),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _ModeButton extends StatelessWidget {
  const _ModeButton({
    super.key,
    required this.label,
    required this.selected,
    required this.onPressed,
  });

  final String label;
  final bool selected;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return TextButton(
      onPressed: onPressed,
      style: TextButton.styleFrom(
        foregroundColor: selected
            ? scheme.onPrimaryContainer
            : scheme.onSurfaceVariant,
        backgroundColor: selected
            ? scheme.primaryContainer
            : Colors.transparent,
        shape: const RoundedRectangleBorder(),
        // 44dp minimum keeps the densest review control row comfortably
        // tappable on phones.
        minimumSize: const Size(64, 44),
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      ),
      child: Text(label),
    );
  }
}

class _ReviewDiffCanvas extends StatelessWidget {
  const _ReviewDiffCanvas({
    required this.lines,
    required this.mode,
    required this.vertical,
    required this.horizontal,
    required this.isSelected,
    required this.onSelect,
  });

  static const rowExtent = 30.0;

  final List<_ReviewDiffLine> lines;
  final ReviewDiffMode mode;
  final ScrollController vertical;
  final ScrollController horizontal;
  final bool Function(int index) isSelected;
  final ValueChanged<int> onSelect;

  @override
  Widget build(BuildContext context) {
    final splitRows = mode == ReviewDiffMode.split ? _splitRows(lines) : null;
    return LayoutBuilder(
      builder: (context, constraints) {
        final minimumWidth = mode == ReviewDiffMode.split ? 1040.0 : 760.0;
        final width = constraints.maxWidth > minimumWidth
            ? constraints.maxWidth
            : minimumWidth;
        return Scrollbar(
          controller: horizontal,
          thumbVisibility: true,
          notificationPredicate: (notification) =>
              notification.metrics.axis == Axis.horizontal,
          child: SingleChildScrollView(
            controller: horizontal,
            scrollDirection: Axis.horizontal,
            child: SizedBox(
              width: width,
              height: constraints.maxHeight,
              child: Scrollbar(
                controller: vertical,
                child: ListView.builder(
                  controller: vertical,
                  // Always scrollable so pull-to-refresh works even when the
                  // diff fits the viewport.
                  physics: const AlwaysScrollableScrollPhysics(),
                  itemExtent: rowExtent,
                  itemCount: splitRows?.length ?? lines.length,
                  itemBuilder: (context, index) {
                    if (splitRows != null) {
                      final row = splitRows[index];
                      return _SplitDiffRow(
                        key: Key('review-split-row-$index'),
                        row: row,
                        selected: row.sourceIndices.any(isSelected),
                        onTap: row.selectable
                            ? () => onSelect(row.sourceIndices.first)
                            : null,
                      );
                    }
                    final line = lines[index];
                    return _UnifiedDiffRow(
                      key: Key('review-line-$index'),
                      line: line,
                      selected: isSelected(index),
                      onTap: line.selectable ? () => onSelect(index) : null,
                    );
                  },
                ),
              ),
            ),
          ),
        );
      },
    );
  }
}

class _UnifiedDiffRow extends StatelessWidget {
  const _UnifiedDiffRow({
    super.key,
    required this.line,
    required this.selected,
    required this.onTap,
  });

  final _ReviewDiffLine line;
  final bool selected;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final background = selected
        ? theme.colorScheme.primaryContainer.withValues(alpha: .62)
        : _lineBackground(theme, line.kind);
    return Semantics(
      button: onTap != null,
      selected: selected,
      label: _lineSemantics(line),
      child: InkWell(
        onTap: onTap,
        child: Container(
          color: background,
          child: Row(
            children: [
              _LineNumber(value: line.oldLine),
              _LineNumber(value: line.newLine),
              Container(width: 2, color: _lineAccent(theme, line.kind)),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  line.text.isEmpty ? ' ' : line.text,
                  maxLines: 1,
                  overflow: TextOverflow.clip,
                  softWrap: false,
                  style: TextStyle(
                    fontFamily: AppTheme.monoFamily,
                    fontSize: AppTheme.codeFontSize,
                    height: 1.55,
                    color: _lineForeground(theme, line.kind),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _SplitDiffRow extends StatelessWidget {
  const _SplitDiffRow({
    super.key,
    required this.row,
    required this.selected,
    required this.onTap,
  });

  final _ReviewSplitRow row;
  final bool selected;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final left = row.left;
    final right = row.right;
    final theme = Theme.of(context);
    return InkWell(
      onTap: onTap,
      child: Container(
        color: selected
            ? theme.colorScheme.primaryContainer.withValues(alpha: .62)
            : null,
        child: Row(
          children: [
            Expanded(child: _SplitCell(line: left, old: true)),
            VerticalDivider(width: 1, color: AppTheme.hairline(theme)),
            Expanded(child: _SplitCell(line: right, old: false)),
          ],
        ),
      ),
    );
  }
}

class _SplitCell extends StatelessWidget {
  const _SplitCell({required this.line, required this.old});

  final _ReviewDiffLine? line;
  final bool old;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final value = line;
    if (value == null) return const SizedBox.expand();
    return Container(
      color: _lineBackground(theme, value.kind),
      child: Row(
        children: [
          _LineNumber(value: old ? value.oldLine : value.newLine),
          Container(width: 2, color: _lineAccent(theme, value.kind)),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              value.text.isEmpty ? ' ' : value.text,
              maxLines: 1,
              softWrap: false,
              overflow: TextOverflow.clip,
              style: TextStyle(
                fontFamily: AppTheme.monoFamily,
                fontSize: AppTheme.codeFontSize,
                height: 1.55,
                color: _lineForeground(theme, value.kind),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _LineNumber extends StatelessWidget {
  const _LineNumber({required this.value});

  final int? value;

  @override
  Widget build(BuildContext context) => Container(
    width: 48,
    alignment: Alignment.centerRight,
    padding: const EdgeInsets.only(right: 8),
    color: Theme.of(context).colorScheme.surfaceContainerLow,
    child: Text(
      value?.toString() ?? '',
      style: TextStyle(
        fontFamily: AppTheme.monoFamily,
        fontSize: AppTheme.captionFontSize,
        color: Theme.of(context).colorScheme.onSurfaceVariant,
      ),
    ),
  );
}

class _ReviewSelectionBar extends StatelessWidget {
  const _ReviewSelectionBar({
    required this.count,
    required this.onClear,
    required this.onCopy,
    required this.onComment,
  });

  final int count;
  final VoidCallback onClear;
  final VoidCallback onCopy;
  final VoidCallback onComment;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      key: const Key('review-selection-bar'),
      decoration: BoxDecoration(
        color: theme.colorScheme.surfaceContainerHigh,
        border: Border(top: BorderSide(color: AppTheme.hairline(theme))),
      ),
      padding: const EdgeInsets.fromLTRB(14, 8, 10, 8),
      child: Row(
        children: [
          Expanded(
            child: Text(
              '$count ${count == 1 ? 'line' : 'lines'} selected',
              style: theme.textTheme.labelLarge,
            ),
          ),
          TextButton(onPressed: onClear, child: const Text('Clear')),
          TextButton(onPressed: onCopy, child: const Text('Copy')),
          const SizedBox(width: 4),
          FilledButton(
            key: const Key('review-comment-action'),
            onPressed: onComment,
            child: const Text('Comment'),
          ),
        ],
      ),
    );
  }
}

/// Bottom-slot mirror of the toolbar's hunk navigation for one-handed phone
/// review; hidden while a selection is active so the selection bar keeps its
/// slot.
class _ReviewHunkBar extends StatelessWidget {
  const _ReviewHunkBar({required this.hunks, required this.onHunk});

  final List<int> hunks;
  final void Function(int direction, List<int> hunks) onHunk;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      key: const Key('review-hunk-bar'),
      decoration: BoxDecoration(
        color: theme.colorScheme.surfaceContainerHigh,
        border: Border(top: BorderSide(color: AppTheme.hairline(theme))),
      ),
      padding: const EdgeInsets.fromLTRB(14, 2, 6, 2),
      child: Row(
        children: [
          Expanded(
            child: Text(
              '${hunks.length} ${hunks.length == 1 ? 'hunk' : 'hunks'}',
              style: theme.textTheme.labelMedium?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
          ),
          IconButton(
            key: const Key('review-hunk-bar-previous'),
            tooltip: 'Previous hunk',
            onPressed: () => onHunk(-1, hunks),
            icon: const Icon(Icons.arrow_upward_rounded, size: 20),
          ),
          IconButton(
            key: const Key('review-hunk-bar-next'),
            tooltip: 'Next hunk',
            onPressed: () => onHunk(1, hunks),
            icon: const Icon(Icons.arrow_downward_rounded, size: 20),
          ),
        ],
      ),
    );
  }
}

class _ReviewCommentComposer extends StatefulWidget {
  const _ReviewCommentComposer({
    required this.file,
    required this.selectionLabel,
  });

  final String file;
  final String selectionLabel;

  @override
  State<_ReviewCommentComposer> createState() => _ReviewCommentComposerState();
}

class _ReviewCommentComposerState extends State<_ReviewCommentComposer> {
  final _controller = TextEditingController();

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final keyboard = MediaQuery.viewInsetsOf(context).bottom;
    final theme = Theme.of(context);
    // Cap the sheet and let it scroll: at short heights or large text the
    // autofocused field plus keyboard would otherwise overflow and push
    // "Add to prompt" off screen.
    return ConstrainedBox(
      constraints: BoxConstraints(
        maxHeight: MediaQuery.sizeOf(context).height * .9,
      ),
      child: SingleChildScrollView(
        key: const Key('review-comment-composer-scroll'),
        padding: EdgeInsets.fromLTRB(18, 12, 18, 18 + keyboard),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Comment on change', style: theme.textTheme.titleLarge),
            const SizedBox(height: 4),
            Text(
              '${widget.file} · ${widget.selectionLabel}',
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: theme.textTheme.bodySmall?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
            const SizedBox(height: 14),
            TextField(
              key: const Key('review-comment-field'),
              controller: _controller,
              autofocus: true,
              minLines: 3,
              maxLines: 7,
              textCapitalization: TextCapitalization.sentences,
              decoration: const InputDecoration(
                hintText: 'What should OpenCode inspect or change?',
              ),
              onSubmitted: (_) => _submit(),
            ),
            const SizedBox(height: 12),
            // OverflowBar wraps the actions when large text scales make the
            // pair wider than the sheet, instead of overflowing the Row.
            OverflowBar(
              alignment: MainAxisAlignment.end,
              overflowAlignment: OverflowBarAlignment.end,
              spacing: 8,
              overflowSpacing: 4,
              children: [
                TextButton(
                  onPressed: () => Navigator.pop(context),
                  child: const Text('Cancel'),
                ),
                ListenableBuilder(
                  listenable: _controller,
                  builder: (context, _) => FilledButton(
                    key: const Key('review-add-to-prompt'),
                    onPressed: _controller.text.trim().isEmpty ? null : _submit,
                    child: const Text('Add to prompt'),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  void _submit() {
    final text = _controller.text.trim();
    if (text.isNotEmpty) Navigator.pop(context, text);
  }
}

class _ReviewLoadingState extends StatelessWidget {
  const _ReviewLoadingState();

  @override
  Widget build(BuildContext context) {
    final color = Theme.of(context).colorScheme.surfaceContainerHigh;
    return ListView(
      key: const Key('review-loading'),
      padding: const EdgeInsets.all(18),
      children: [
        _Skeleton(width: 170, height: 20, color: color),
        const SizedBox(height: 18),
        _Skeleton(width: double.infinity, height: 54, color: color),
        const SizedBox(height: 18),
        for (var i = 0; i < 8; i++) ...[
          _Skeleton(
            width: i.isEven ? double.infinity : 260,
            height: 18,
            color: color,
          ),
          const SizedBox(height: 10),
        ],
      ],
    );
  }
}

class _Skeleton extends StatelessWidget {
  const _Skeleton({
    required this.width,
    required this.height,
    required this.color,
  });

  final double width;
  final double height;
  final Color color;

  @override
  Widget build(BuildContext context) => Container(
    width: width,
    height: height,
    decoration: BoxDecoration(
      color: color,
      borderRadius: BorderRadius.circular(6),
    ),
  );
}

// The review empty/error/notice states delegate to the shared product-state
// components so padding, icon treatment, and the "Try again" action match the
// rest of the app.
class _ReviewEmptyState extends StatelessWidget {
  const _ReviewEmptyState();

  @override
  Widget build(BuildContext context) => const ProductEmptyState(
    key: Key('review-empty'),
    icon: Icons.difference_outlined,
    title: 'No changes to review',
    message: 'OpenCode has not changed any files in this session.',
  );
}

class _ReviewErrorState extends StatelessWidget {
  const _ReviewErrorState({required this.error, required this.onRetry});

  final Object error;
  final Future<void> Function() onRetry;

  @override
  Widget build(BuildContext context) => ProductErrorState(
    key: const Key('review-error'),
    message: error.toString(),
    onRetry: onRetry,
  );
}

class _NoDiffContent extends StatelessWidget {
  const _NoDiffContent();

  @override
  Widget build(BuildContext context) => const ProductEmptyState(
    key: Key('review-no-content'),
    icon: Icons.description_outlined,
    title: 'Diff content unavailable',
    message:
        'The server reported this file but did not include a patch or file contents.',
  );
}

enum _ReviewLineKind { context, added, removed, hunk, metadata }

class _ReviewDiffLine {
  const _ReviewDiffLine({
    required this.text,
    required this.kind,
    this.oldLine,
    this.newLine,
  });

  final String text;
  final _ReviewLineKind kind;
  final int? oldLine;
  final int? newLine;

  bool get selectable =>
      kind == _ReviewLineKind.context ||
      kind == _ReviewLineKind.added ||
      kind == _ReviewLineKind.removed;
}

class _ReviewSplitRow {
  const _ReviewSplitRow({
    required this.left,
    required this.right,
    required this.sourceIndices,
  });

  final _ReviewDiffLine? left;
  final _ReviewDiffLine? right;
  final List<int> sourceIndices;

  bool get selectable =>
      (left?.selectable ?? false) || (right?.selectable ?? false);
}

List<_ReviewDiffLine> _parseDiff(FileDiff diff) {
  final patch = diff.patch;
  if (patch?.isNotEmpty == true) return _parsePatch(patch!);
  final before = diff.before?.split('\n') ?? const <String>[];
  final after = diff.after?.split('\n') ?? const <String>[];
  if (before.isEmpty && after.isEmpty) return const [];

  var prefix = 0;
  while (prefix < before.length &&
      prefix < after.length &&
      before[prefix] == after[prefix]) {
    prefix++;
  }
  var suffix = 0;
  while (suffix < before.length - prefix &&
      suffix < after.length - prefix &&
      before[before.length - suffix - 1] == after[after.length - suffix - 1]) {
    suffix++;
  }

  final lines = <_ReviewDiffLine>[
    const _ReviewDiffLine(
      text: '@@ file contents @@',
      kind: _ReviewLineKind.hunk,
    ),
  ];
  for (var i = 0; i < prefix; i++) {
    lines.add(
      _ReviewDiffLine(
        text: ' ${before[i]}',
        kind: _ReviewLineKind.context,
        oldLine: i + 1,
        newLine: i + 1,
      ),
    );
  }
  for (var i = prefix; i < before.length - suffix; i++) {
    lines.add(
      _ReviewDiffLine(
        text: '-${before[i]}',
        kind: _ReviewLineKind.removed,
        oldLine: i + 1,
      ),
    );
  }
  for (var i = prefix; i < after.length - suffix; i++) {
    lines.add(
      _ReviewDiffLine(
        text: '+${after[i]}',
        kind: _ReviewLineKind.added,
        newLine: i + 1,
      ),
    );
  }
  for (var i = 0; i < suffix; i++) {
    final oldIndex = before.length - suffix + i;
    final newIndex = after.length - suffix + i;
    lines.add(
      _ReviewDiffLine(
        text: ' ${before[oldIndex]}',
        kind: _ReviewLineKind.context,
        oldLine: oldIndex + 1,
        newLine: newIndex + 1,
      ),
    );
  }
  return lines;
}

List<_ReviewDiffLine> _parsePatch(String patch) {
  final lines = <_ReviewDiffLine>[];
  var oldLine = 0;
  var newLine = 0;
  final hunkPattern = RegExp(r'^@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@');
  for (final text in patch.split('\n')) {
    final hunk = hunkPattern.firstMatch(text);
    if (hunk != null) {
      oldLine = int.parse(hunk.group(1)!);
      newLine = int.parse(hunk.group(2)!);
      lines.add(_ReviewDiffLine(text: text, kind: _ReviewLineKind.hunk));
      continue;
    }
    if (text.startsWith('+') && !text.startsWith('+++')) {
      lines.add(
        _ReviewDiffLine(
          text: text,
          kind: _ReviewLineKind.added,
          newLine: newLine++,
        ),
      );
      continue;
    }
    if (text.startsWith('-') && !text.startsWith('---')) {
      lines.add(
        _ReviewDiffLine(
          text: text,
          kind: _ReviewLineKind.removed,
          oldLine: oldLine++,
        ),
      );
      continue;
    }
    if (text.startsWith(' ') || text.isEmpty) {
      lines.add(
        _ReviewDiffLine(
          text: text,
          kind: _ReviewLineKind.context,
          oldLine: oldLine++,
          newLine: newLine++,
        ),
      );
      continue;
    }
    lines.add(_ReviewDiffLine(text: text, kind: _ReviewLineKind.metadata));
  }
  return lines;
}

List<_ReviewSplitRow> _splitRows(List<_ReviewDiffLine> lines) {
  final rows = <_ReviewSplitRow>[];
  var index = 0;
  while (index < lines.length) {
    final line = lines[index];
    if (line.kind == _ReviewLineKind.removed ||
        line.kind == _ReviewLineKind.added) {
      final removed = <MapEntry<int, _ReviewDiffLine>>[];
      final added = <MapEntry<int, _ReviewDiffLine>>[];
      while (index < lines.length &&
          lines[index].kind == _ReviewLineKind.removed) {
        removed.add(MapEntry(index, lines[index++]));
      }
      while (index < lines.length &&
          lines[index].kind == _ReviewLineKind.added) {
        added.add(MapEntry(index, lines[index++]));
      }
      if (removed.isEmpty) {
        while (index < lines.length &&
            lines[index].kind == _ReviewLineKind.removed) {
          removed.add(MapEntry(index, lines[index++]));
        }
      }
      final count = removed.length > added.length
          ? removed.length
          : added.length;
      for (var i = 0; i < count; i++) {
        final left = i < removed.length ? removed[i] : null;
        final right = i < added.length ? added[i] : null;
        rows.add(
          _ReviewSplitRow(
            left: left?.value,
            right: right?.value,
            sourceIndices: [
              if (left != null) left.key,
              if (right != null) right.key,
            ],
          ),
        );
      }
      continue;
    }
    rows.add(_ReviewSplitRow(left: line, right: line, sourceIndices: [index]));
    index++;
  }
  return rows;
}

Color? _lineBackground(ThemeData theme, _ReviewLineKind kind) => switch (kind) {
  _ReviewLineKind.added => _additionBackground(theme),
  _ReviewLineKind.removed => theme.colorScheme.errorContainer.withValues(
    alpha: .38,
  ),
  _ReviewLineKind.hunk => theme.colorScheme.primaryContainer.withValues(
    alpha: .34,
  ),
  _ReviewLineKind.metadata => theme.colorScheme.surfaceContainerHigh,
  _ReviewLineKind.context => null,
};

Color _lineAccent(ThemeData theme, _ReviewLineKind kind) => switch (kind) {
  _ReviewLineKind.added => _additionColor(theme),
  _ReviewLineKind.removed => theme.colorScheme.error,
  _ReviewLineKind.hunk => theme.colorScheme.primary,
  _ => Colors.transparent,
};

Color? _lineForeground(ThemeData theme, _ReviewLineKind kind) => switch (kind) {
  _ReviewLineKind.removed => theme.colorScheme.onErrorContainer,
  _ReviewLineKind.hunk => theme.colorScheme.primary,
  _ReviewLineKind.metadata => theme.colorScheme.onSurfaceVariant,
  _ => null,
};

String _lineSemantics(_ReviewDiffLine line) {
  final kind = switch (line.kind) {
    _ReviewLineKind.added => 'Added',
    _ReviewLineKind.removed => 'Removed',
    _ReviewLineKind.context => 'Unchanged',
    _ReviewLineKind.hunk => 'Hunk',
    _ReviewLineKind.metadata => 'Metadata',
  };
  final number = line.newLine ?? line.oldLine;
  return '$kind${number == null ? '' : ' line $number'}: ${line.text}';
}

String _basename(String path) {
  final normalized = path.replaceAll('\\', '/');
  return normalized.split('/').last;
}

String _fileTabLabel(List<FileDiff> diffs, int index) {
  final target = diffs[index].file.replaceAll('\\', '/');
  final segments = target.split('/').where((part) => part.isNotEmpty).toList();
  if (segments.isEmpty) return target;
  final basename = segments.last;
  final duplicates = diffs
      .map((diff) => diff.file.replaceAll('\\', '/'))
      .where((path) => _basename(path) == basename)
      .toList();
  if (duplicates.length == 1) return basename;

  for (var depth = 1; depth < segments.length; depth++) {
    final start = segments.length - 1 - depth;
    final qualifier = segments.sublist(start, segments.length - 1).join('/');
    final collision = duplicates.any((path) {
      if (path == target) return false;
      final other = path.split('/').where((part) => part.isNotEmpty).toList();
      if (other.length <= depth) return false;
      final otherStart = other.length - 1 - depth;
      return other.sublist(otherStart, other.length - 1).join('/') == qualifier;
    });
    if (!collision) return '$basename · $qualifier';
  }
  return target;
}

String _directory(String path) {
  final normalized = path.replaceAll('\\', '/');
  final slash = normalized.lastIndexOf('/');
  return slash <= 0 ? '.' : normalized.substring(0, slash);
}

String _status(FileDiff diff) {
  final explicit = diff.status?.trim().toLowerCase();
  if (explicit?.isNotEmpty == true) return explicit!;
  if ((diff.before == null || diff.before!.isEmpty) &&
      diff.after?.isNotEmpty == true) {
    return 'added';
  }
  if (diff.before?.isNotEmpty == true &&
      (diff.after == null || diff.after!.isEmpty)) {
    return 'deleted';
  }
  return 'modified';
}

Color _statusColor(BuildContext context, FileDiff diff) =>
    switch (_status(diff)) {
      'added' => _additionColor(Theme.of(context)),
      'deleted' => Theme.of(context).colorScheme.error,
      _ => Theme.of(context).colorScheme.primary,
    };

Color _additionColor(ThemeData theme) => AppTheme.successOf(theme);

/// The diff-addition wash, derived from the pack's success color so it
/// tracks theme packs instead of two hardcoded greens.
Color _additionBackground(ThemeData theme) => AppTheme.successOf(
  theme,
).withValues(alpha: theme.brightness == Brightness.dark ? .18 : .22);
