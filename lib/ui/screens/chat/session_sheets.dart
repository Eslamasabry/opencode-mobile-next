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
  String? _error;

  @override
  void initState() {
    super.initState();
    _fetch();
  }

  Future<void> _fetch() async {
    try {
      final api = await widget.conn.prepareActionTransport();
      if (api == null) throw StateError('OpenCode is reconnecting.');
      final t = await api.todos(widget.sessionID);
      if (mounted) setState(() => _todos = t);
    } catch (e) {
      if (mounted) setState(() => _error = '$e');
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: _todos == null && _error == null
            ? const Center(heightFactor: 2, child: CircularProgressIndicator())
            : _error != null
            ? Text(_error!, style: TextStyle(color: theme.colorScheme.error))
            : _todos!.isEmpty
            ? Text(
                'No todos in this session.',
                style: TextStyle(color: theme.hintColor),
              )
            : Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'TODO LIST',
                    style: theme.textTheme.labelSmall!.copyWith(
                      color: theme.hintColor,
                      letterSpacing: 1,
                    ),
                  ),
                  const SizedBox(height: 8),
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
                                color: t.done ? theme.hintColor : null,
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
                                      color: theme.hintColor,
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
  String? _error;

  @override
  void initState() {
    super.initState();
    _fetch();
  }

  Future<void> _fetch() async {
    try {
      final api = await widget.conn.prepareActionTransport();
      if (api == null) throw StateError('OpenCode is reconnecting.');
      final d = await api.diff(widget.sessionID);
      if (mounted) setState(() => _diffs = d);
    } catch (e) {
      if (mounted) setState(() => _error = '$e');
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
          child: _diffs == null && _error == null
              ? const Center(child: CircularProgressIndicator())
              : _error != null
              ? Text(_error!, style: TextStyle(color: theme.colorScheme.error))
              : _diffs!.isEmpty
              ? Text(
                  'No file changes yet.',
                  style: TextStyle(color: theme.hintColor),
                )
              : Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      'CHANGES',
                      style: theme.textTheme.labelSmall!.copyWith(
                        color: theme.hintColor,
                        letterSpacing: 1,
                      ),
                    ),
                    const SizedBox(height: 8),
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
                                style: const TextStyle(fontSize: 11),
                              ),
                              trailing: Row(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  Text(
                                    '+${c.added}',
                                    style: TextStyle(
                                      color: Colors.green.shade400,
                                      fontFamily: 'monospace',
                                    ),
                                  ),
                                  const SizedBox(width: 6),
                                  Text(
                                    '-${c.removed}',
                                    style: TextStyle(
                                      color: theme.colorScheme.error,
                                      fontFamily: 'monospace',
                                    ),
                                  ),
                                  const SizedBox(width: 6),
                                  const Icon(Icons.chevron_right_rounded),
                                ],
                              ),
                              onTap: () {
                                showModalBottomSheet(
                                  context: context,
                                  isScrollControlled: true,
                                  builder: (_) => _FileDiffView(diff: d),
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

class _FileDiffView extends StatelessWidget {
  final FileDiff diff;
  const _FileDiffView({required this.diff});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final beforeLines = diff.before?.split('\n') ?? [];
    final afterLines = diff.after?.split('\n') ?? [];

    final patch = diff.patch;
    if (patch != null && patch.isNotEmpty) {
      return _patchView(context, patch);
    }

    // Naive alignment: common prefix/suffix; middle block replaced.
    int p = 0;
    while (p < beforeLines.length &&
        p < afterLines.length &&
        beforeLines[p] == afterLines[p]) {
      p++;
    }
    int s = 0;
    while (s < beforeLines.length - p &&
        s < afterLines.length - p &&
        beforeLines[beforeLines.length - 1 - s] ==
            afterLines[afterLines.length - 1 - s]) {
      s++;
    }

    final displayRows = <Widget>[];
    for (var i = 0; i < p && i < beforeLines.length; i++) {
      displayRows.add(_row(beforeLines[i], null, theme));
    }
    for (var i = p; i < beforeLines.length - s; i++) {
      displayRows.add(_row(beforeLines[i], false, theme));
    }
    for (var i = p; i < afterLines.length - s; i++) {
      displayRows.add(_row(afterLines[i], true, theme));
    }
    for (var i = beforeLines.length - s; i < beforeLines.length; i++) {
      if (i >= 0) displayRows.add(_row(beforeLines[i], null, theme));
    }

    return SafeArea(
      child: SizedBox(
        height: MediaQuery.of(context).size.height * .85,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 8, 8, 4),
              child: Row(
                children: [
                  Expanded(
                    child: Text(
                      diff.file,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontFamily: 'monospace',
                        fontSize: 13,
                      ),
                    ),
                  ),
                  IconButton(
                    tooltip: 'Copy updated file',
                    icon: const Icon(Icons.copy_rounded, size: 18),
                    onPressed: () => Clipboard.setData(
                      ClipboardData(text: diff.after ?? ''),
                    ),
                  ),
                ],
              ),
            ),
            Divider(height: 1, color: theme.dividerColor),
            Expanded(
              child: SingleChildScrollView(
                child: SingleChildScrollView(
                  scrollDirection: Axis.horizontal,
                  padding: const EdgeInsets.all(8),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: displayRows.isEmpty
                        ? [
                            const Padding(
                              padding: EdgeInsets.all(20),
                              child: Text('(empty)'),
                            ),
                          ]
                        : displayRows,
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _patchView(BuildContext context, String patch) {
    final theme = Theme.of(context);
    final rows = patch.split('\n').map((line) {
      final kind = line.startsWith('@@')
          ? _DiffLineKind.header
          : line.startsWith('+') && !line.startsWith('+++')
          ? _DiffLineKind.added
          : line.startsWith('-') && !line.startsWith('---')
          ? _DiffLineKind.removed
          : _DiffLineKind.context;
      return _patchRow(line, kind, theme);
    }).toList();
    return _diffScaffold(context, rows);
  }

  Widget _diffScaffold(BuildContext context, List<Widget> rows) {
    final theme = Theme.of(context);
    final copyText = diff.after ?? diff.patch ?? '';
    final copyLabel = diff.after != null ? 'Copy updated file' : 'Copy patch';
    return SafeArea(
      child: SizedBox(
        height: MediaQuery.of(context).size.height * .85,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 8, 8, 4),
              child: Row(
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          diff.file,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            fontFamily: 'monospace',
                            fontSize: 13,
                          ),
                        ),
                        if (diff.status != null)
                          Text(
                            diff.status!,
                            style: theme.textTheme.labelSmall?.copyWith(
                              color: theme.colorScheme.onSurfaceVariant,
                            ),
                          ),
                      ],
                    ),
                  ),
                  IconButton(
                    tooltip: copyLabel,
                    icon: const Icon(Icons.copy_rounded, size: 18),
                    onPressed: copyText.isEmpty
                        ? null
                        : () =>
                              Clipboard.setData(ClipboardData(text: copyText)),
                  ),
                ],
              ),
            ),
            Divider(height: 1, color: theme.dividerColor),
            Expanded(
              child: SingleChildScrollView(
                child: SingleChildScrollView(
                  scrollDirection: Axis.horizontal,
                  padding: const EdgeInsets.all(8),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: rows.isEmpty
                        ? [
                            const Padding(
                              padding: EdgeInsets.all(20),
                              child: Text('(empty diff)'),
                            ),
                          ]
                        : rows,
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _patchRow(String line, _DiffLineKind kind, ThemeData theme) {
    final bg = switch (kind) {
      _DiffLineKind.added => Colors.green.withValues(alpha: .15),
      _DiffLineKind.removed => theme.colorScheme.error.withValues(alpha: .15),
      _DiffLineKind.header => theme.colorScheme.primary.withValues(alpha: .12),
      _DiffLineKind.context => null,
    };
    final foreground = switch (kind) {
      _DiffLineKind.removed => theme.colorScheme.error,
      _DiffLineKind.header => theme.colorScheme.primary,
      _ => null,
    };
    return Container(
      color: bg,
      constraints: const BoxConstraints(minWidth: 400),
      padding: const EdgeInsets.symmetric(horizontal: 6),
      child: Text(
        line,
        style: TextStyle(
          fontFamily: 'monospace',
          fontSize: 12,
          height: 1.4,
          color: foreground,
        ),
      ),
    );
  }

  Widget _row(
    String line,
    bool? addedRemoved /*null=keep,true=add,false=remove*/,
    ThemeData theme,
  ) {
    final bg = addedRemoved == true
        ? Colors.green.withValues(alpha: .15)
        : addedRemoved == false
        ? theme.colorScheme.error.withValues(alpha: .15)
        : null;
    return Container(
      color: bg,
      constraints: const BoxConstraints(minWidth: 400),
      padding: const EdgeInsets.symmetric(horizontal: 6),
      child: Text(
        line,
        style: TextStyle(
          fontFamily: 'monospace',
          fontSize: 12,
          height: 1.4,
          color: addedRemoved == false ? theme.colorScheme.error : null,
        ),
      ),
    );
  }
}

enum _DiffLineKind { context, added, removed, header }
