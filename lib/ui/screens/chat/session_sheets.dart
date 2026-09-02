part of '../chat_screen.dart';

class _TodosSheet extends StatefulWidget {
  final ConnectionController conn;
  final String sessionID;
  const _TodosSheet({required this.conn, required this.sessionID});

  @override
  State<_TodosSheet> createState() => _TodosSheetState();
}

class _TodosSheetState extends State<_TodosSheet> {
  List<Todo>? _todos;
  Object? _error;

  @override
  void initState() {
    super.initState();
    unawaited(_fetch());
  }

  Future<void> _fetch() async {
    if (_error != null) setState(() => _error = null);
    try {
      final api = await widget.conn.prepareActionTransport();
      if (api == null) {
        throw const ProductException('OpenCode is reconnecting. Try again.');
      }
      final t = await api.todos(widget.sessionID);
      if (mounted) setState(() => _todos = t);
    } catch (e) {
      if (mounted) setState(() => _error = e);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: _error != null
            ? SizedBox(
                height: 260,
                child: ProductErrorState(
                  message: productErrorText(_error!),
                  onRetry: _fetch,
                ),
              )
            : _todos == null
            ? const SizedBox(height: 240, child: LoadingList(rows: 4))
            : _todos!.isEmpty
            ? const ProductInlineEmpty(
                icon: Icons.checklist_rounded,
                title: 'No todos in this session',
                message:
                    'When the assistant plans work as a todo list, the items appear here.',
              )
            : Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const SectionLabel.inline('Todo list'),
                  Flexible(
                    child: ListView(
                      shrinkWrap: true,
                      children: [
                        for (final t in _todos!)
                          CheckboxListTile(
                            dense: true,
                            value: t.done,
                            controlAffinity: ListTileControlAffinity.leading,
                            title: Text(
                              t.content,
                              style: TextStyle(
                                decoration: t.done
                                    ? TextDecoration.lineThrough
                                    : null,
                                color: t.done ? AppTheme.mutedOf(theme) : null,
                              ),
                            ),
                            subtitle:
                                t.status == 'pending' && t.priority == null
                                ? null
                                : Text(
                                    [
                                      if (t.status != 'pending')
                                        t.status.replaceAll('_', ' '),
                                      if (t.priority != null)
                                        '${t.priority} priority',
                                    ].join(' · '),
                                    style: theme.textTheme.labelSmall?.copyWith(
                                      color: AppTheme.mutedOf(theme),
                                    ),
                                  ),
                            onChanged: null,
                          ),
                      ],
                    ),
                  ),
                ],
              ),
      ),
    );
  }
}

class _DiffSheet extends StatefulWidget {
  final ConnectionController conn;
  final String sessionID;
  const _DiffSheet({required this.conn, required this.sessionID});

  @override
  State<_DiffSheet> createState() => _DiffSheetState();
}

class _DiffSheetState extends State<_DiffSheet> {
  List<FileDiff>? _diffs;
  Object? _error;

  @override
  void initState() {
    super.initState();
    unawaited(_fetch());
  }

  Future<void> _fetch() async {
    if (_error != null) setState(() => _error = null);
    try {
      final api = await widget.conn.prepareActionTransport();
      if (api == null) {
        throw const ProductException('OpenCode is reconnecting. Try again.');
      }
      final d = await api.diff(widget.sessionID);
      if (mounted) setState(() => _diffs = d);
    } catch (e) {
      if (mounted) setState(() => _error = e);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return SafeArea(
      child: SizedBox(
        height: MediaQuery.of(context).size.height * .75,
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: _error != null
              ? ProductErrorState(
                  message: productErrorText(_error!),
                  onRetry: _fetch,
                )
              : _diffs == null
              ? const LoadingList(rows: 6)
              : _diffs!.isEmpty
              ? const ProductInlineEmpty(
                  icon: Icons.difference_outlined,
                  title: 'No file changes yet',
                  message:
                      'File edits made in this session will be listed here.',
                )
              : Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const SectionLabel.inline('Changes'),
                    Expanded(
                      child: ListView.builder(
                        itemCount: _diffs!.length,
                        itemBuilder: (context, i) {
                          final d = _diffs![i];
                          final c = d.counts;
                          return Card.filled(
                            margin: const EdgeInsets.symmetric(vertical: 3),
                            child: ListTile(
                              dense: true,
                              leading: const Icon(
                                Icons.description_outlined,
                                size: 20,
                              ),
                              title: Text(
                                d.file.split('/').last,
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                              ),
                              subtitle: Text(
                                d.file,
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: const TextStyle(
                                  fontSize: AppTheme.captionFontSize,
                                ),
                              ),
                              trailing: Row(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  Text(
                                    '+${c.added}',
                                    style: TextStyle(
                                      color: AppTheme.successOf(
                                        Theme.of(context),
                                      ),
                                      fontFamily: AppTheme.monoFamily,
                                    ),
                                  ),
                                  const SizedBox(width: 6),
                                  Text(
                                    '-${c.removed}',
                                    style: TextStyle(
                                      color: theme.colorScheme.error,
                                      fontFamily: AppTheme.monoFamily,
                                    ),
                                  ),
                                  const SizedBox(width: 6),
                                  const Icon(Icons.chevron_right_rounded),
                                ],
                              ),
                              onTap: () {
                                // A full-screen route, not a sheet on a sheet:
                                // diffs need the whole width and a back
                                // affordance the reader already knows.
                                Navigator.of(context).push(
                                  MaterialPageRoute<void>(
                                    fullscreenDialog: true,
                                    builder: (_) => _FileDiffView(diff: d),
                                  ),
                                );
                              },
                            ),
                          );
                        },
                      ),
                    ),
                  ],
                ),
        ),
      ),
    );
  }
}

class _DiffLine {
  const _DiffLine(this.text, this.kind, this.number);

  final String text;
  final _DiffLineKind kind;

  /// Line number in the new file (context, added) or the old file (removed);
  /// null for hunk and file headers.
  final int? number;
}

/// Full-screen view of one file's changes. Lines wrap on narrow screens
/// (< 600dp) so nothing hides off to the right; wider screens keep a
/// horizontal scroll so code stays column-aligned. Every line carries its
/// number in a muted mono gutter.
class _FileDiffView extends StatelessWidget {
  final FileDiff diff;
  const _FileDiffView({required this.diff});

  static const _wrapBelow = 600.0;

  List<_DiffLine> _lines() {
    final patch = diff.patch;
    if (patch != null && patch.isNotEmpty) return _patchLines(patch);
    return _alignedLines();
  }

  static final _hunkHeader = RegExp(r'^@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@');

  static List<_DiffLine> _patchLines(String patch) {
    final lines = <_DiffLine>[];
    var oldNo = 0;
    var newNo = 0;
    var inHunk = false;
    for (final line in patch.split('\n')) {
      if (line.startsWith('@@')) {
        final match = _hunkHeader.firstMatch(line);
        if (match != null) {
          oldNo = int.parse(match.group(1)!);
          newNo = int.parse(match.group(2)!);
        }
        inHunk = true;
        lines.add(_DiffLine(line, _DiffLineKind.header, null));
      } else if (!inHunk &&
          (line.startsWith('+++') || line.startsWith('---'))) {
        lines.add(_DiffLine(line, _DiffLineKind.context, null));
      } else if (line.startsWith('+')) {
        lines.add(_DiffLine(line, _DiffLineKind.added, newNo++));
      } else if (line.startsWith('-')) {
        lines.add(_DiffLine(line, _DiffLineKind.removed, oldNo++));
      } else if (line.startsWith('\\')) {
        lines.add(_DiffLine(line, _DiffLineKind.context, null));
      } else {
        oldNo++;
        lines.add(_DiffLine(line, _DiffLineKind.context, newNo++));
      }
    }
    return lines;
  }

  /// Naive alignment for servers that send before/after only: common
  /// prefix and suffix are kept, the middle block is shown removed-then-added.
  List<_DiffLine> _alignedLines() {
    final beforeLines = diff.before?.split('\n') ?? [];
    final afterLines = diff.after?.split('\n') ?? [];
    var p = 0;
    while (p < beforeLines.length &&
        p < afterLines.length &&
        beforeLines[p] == afterLines[p]) {
      p++;
    }
    var s = 0;
    while (s < beforeLines.length - p &&
        s < afterLines.length - p &&
        beforeLines[beforeLines.length - 1 - s] ==
            afterLines[afterLines.length - 1 - s]) {
      s++;
    }
    final lines = <_DiffLine>[];
    for (var i = 0; i < p && i < beforeLines.length; i++) {
      lines.add(_DiffLine(beforeLines[i], _DiffLineKind.context, i + 1));
    }
    for (var i = p; i < beforeLines.length - s; i++) {
      lines.add(_DiffLine(beforeLines[i], _DiffLineKind.removed, i + 1));
    }
    for (var i = p; i < afterLines.length - s; i++) {
      lines.add(_DiffLine(afterLines[i], _DiffLineKind.added, i + 1));
    }
    for (var i = afterLines.length - s; i < afterLines.length; i++) {
      if (i >= 0) {
        lines.add(_DiffLine(afterLines[i], _DiffLineKind.context, i + 1));
      }
    }
    return lines;
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final lines = _lines();
    final copyText = diff.after ?? diff.patch ?? '';
    final copyLabel = diff.after != null ? 'Copy updated file' : 'Copy patch';
    final emptyLabel = diff.patch != null ? '(empty diff)' : '(empty)';
    var maxNumber = 1;
    for (final line in lines) {
      if (line.number case final number? when number > maxNumber) {
        maxNumber = number;
      }
    }
    final digits = maxNumber.toString().length;
    final gutterWidth =
        MediaQuery.textScalerOf(context).scale(AppTheme.codeFontSize) *
            .62 *
            digits +
        6;

    return Scaffold(
      key: const Key('file-diff-page'),
      appBar: AppBar(
        title: Text(
          diff.file.split('/').last,
          overflow: TextOverflow.ellipsis,
          style: const TextStyle(fontFamily: AppTheme.monoFamily),
        ),
        actions: [
          IconButton(
            tooltip: copyLabel,
            icon: const Icon(AppIcons.copy),
            onPressed: copyText.isEmpty
                ? null
                : () => Clipboard.setData(ClipboardData(text: copyText)),
          ),
        ],
      ),
      body: SafeArea(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
              child: Text(
                diff.status == null ? diff.file : '${diff.file} · ${diff.status}',
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: theme.textTheme.bodySmall?.copyWith(
                  fontFamily: AppTheme.monoFamily,
                  color: AppTheme.mutedOf(theme),
                ),
              ),
            ),
            Divider(height: 1, color: AppTheme.hairline(theme)),
            Expanded(
              child: lines.isEmpty
                  ? Center(child: Text(emptyLabel))
                  : LayoutBuilder(
                      builder: (context, constraints) {
                        final wrap = constraints.maxWidth < _wrapBelow;
                        if (wrap) {
                          return ListView.builder(
                            key: const Key('file-diff-wrapped'),
                            padding: const EdgeInsets.symmetric(vertical: 8),
                            itemCount: lines.length,
                            itemBuilder: (context, index) => _lineRow(
                              theme,
                              lines[index],
                              wrap: true,
                              gutterWidth: gutterWidth,
                            ),
                          );
                        }
                        return SingleChildScrollView(
                          key: const Key('file-diff-scrolling'),
                          padding: const EdgeInsets.symmetric(vertical: 8),
                          child: SingleChildScrollView(
                            scrollDirection: Axis.horizontal,
                            child: IntrinsicWidth(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.stretch,
                                children: [
                                  for (final line in lines)
                                    _lineRow(
                                      theme,
                                      line,
                                      wrap: false,
                                      gutterWidth: gutterWidth,
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
        ),
      ),
    );
  }

  Widget _lineRow(
    ThemeData theme,
    _DiffLine line, {
    required bool wrap,
    required double gutterWidth,
  }) {
    final bg = switch (line.kind) {
      _DiffLineKind.added => AppTheme.successOf(theme).withValues(alpha: .15),
      _DiffLineKind.removed => theme.colorScheme.error.withValues(alpha: .15),
      _DiffLineKind.header => theme.colorScheme.primary.withValues(alpha: .12),
      _DiffLineKind.context => null,
    };
    final foreground = switch (line.kind) {
      _DiffLineKind.removed => theme.colorScheme.error,
      _DiffLineKind.header => theme.colorScheme.primary,
      _ => null,
    };
    final codeStyle = TextStyle(
      fontFamily: AppTheme.monoFamily,
      fontSize: AppTheme.codeFontSize,
      height: 1.4,
      color: foreground,
    );
    final text = Text(line.text, style: codeStyle, softWrap: wrap);
    return Container(
      color: bg,
      padding: const EdgeInsets.symmetric(horizontal: 6),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: gutterWidth,
            child: Text(
              line.number?.toString() ?? '',
              textAlign: TextAlign.right,
              style: codeStyle.copyWith(
                color: AppTheme.mutedOf(theme).withValues(alpha: .7),
              ),
            ),
          ),
          const SizedBox(width: 10),
          if (wrap) Expanded(child: text) else text,
        ],
      ),
    );
  }
}

enum _DiffLineKind { context, added, removed, header }
