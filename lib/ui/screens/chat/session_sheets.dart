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
                                    builder: (_) => DiffView.single(d),
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
