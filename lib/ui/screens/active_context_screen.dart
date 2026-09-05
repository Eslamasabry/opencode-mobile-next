import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../api/product_repository.dart';
import '../../l10n/app_localizations.dart';
import '../../state/connection.dart';
import '../widgets/product_states.dart';

class ActiveContextScreen extends StatefulWidget {
  const ActiveContextScreen({
    super.key,
    required this.controller,
    required this.sessionID,
  });
  final ConnectionController controller;
  final String sessionID;
  @override
  State<ActiveContextScreen> createState() => _ActiveContextScreenState();
}

class _ActiveContextScreenState extends State<ActiveContextScreen> {
  List<ActiveContextMessage>? _messages;
  Object? _error;
  bool _loading = false;
  int _generation = 0;
  late final int _location;
  late int _history;
  late int _refresh;
  late bool _busy;
  String _query = '';
  final _search = TextEditingController();
  String? _type;
  bool get _sameLocation => widget.controller.locationRevision == _location;
  AppLocalizations get _l10n => AppLocalizations.of(context);

  @override
  void initState() {
    super.initState();
    _location = widget.controller.locationRevision;
    _history = widget.controller.sessionHistoryRevision(widget.sessionID);
    _refresh = widget.controller.dataRefreshRevision;
    _busy = widget.controller.busySessions.contains(widget.sessionID);
    widget.controller.addListener(_changed);
    _load();
  }

  void _changed() {
    if (!_sameLocation) {
      _generation++;
      setState(() {
        _messages = null;
        _loading = false;
        _error = const ActiveContextException(ActiveContextFailure.changed);
      });
      return;
    }
    final c = widget.controller;
    final history = c.sessionHistoryRevision(widget.sessionID);
    final busy = c.busySessions.contains(widget.sessionID);
    final changed =
        history != _history ||
        c.dataRefreshRevision != _refresh ||
        (_busy && !busy);
    if (history != _history) _messages = null;
    _history = history;
    _refresh = c.dataRefreshRevision;
    _busy = busy;
    if (changed) _load();
  }

  Future<void> _load() async {
    if (!_sameLocation) return;
    final generation = ++_generation;
    final history = widget.controller.sessionHistoryRevision(widget.sessionID);
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final repository = await widget.controller.prepareActionRepository();
      if (!mounted || generation != _generation || !_sameLocation) return;
      if (repository is! ActiveContextGateway ||
          !(repository as ActiveContextGateway).activeContextSupported) {
        throw const ActiveContextException(ActiveContextFailure.unsupported);
      }
      final messages = await (repository as ActiveContextGateway)
          .loadActiveContext(widget.sessionID);
      if (!mounted || generation != _generation || !_sameLocation) return;
      if (!identical(repository, widget.controller.repository) ||
          history !=
              widget.controller.sessionHistoryRevision(widget.sessionID)) {
        throw const ActiveContextException(ActiveContextFailure.changed);
      }
      final boundary = widget
          .controller
          .sessionsById[widget.sessionID]
          ?.stagedRevert
          ?.messageID;
      setState(() {
        _messages = boundary == null
            ? messages
            : messages
                  .where((message) => message.id.compareTo(boundary) < 0)
                  .toList();
        if (_type != null &&
            !_messages!.any((message) => message.type == _type)) {
          _type = null;
        }
      });
    } catch (error) {
      if (mounted && generation == _generation) setState(() => _error = error);
    } finally {
      if (mounted && generation == _generation) {
        setState(() => _loading = false);
      }
    }
  }

  @override
  void dispose() {
    _search.dispose();
    widget.controller.removeListener(_changed);
    super.dispose();
  }

  String _errorText(Object error) => error is ActiveContextException
      ? switch (error.failure) {
          ActiveContextFailure.unsupported => _l10n.activeContextUnsupported,
          ActiveContextFailure.changed => _l10n.activeContextChanged,
          ActiveContextFailure.invalidResponse => _l10n.activeContextInvalid,
        }
      : productErrorText(error);

  @override
  Widget build(BuildContext context) {
    final messages = _messages;
    final counts = <String, int>{};
    for (final message in messages ?? const <ActiveContextMessage>[]) {
      counts.update(message.type, (value) => value + 1, ifAbsent: () => 1);
    }
    final visible =
        messages
            ?.where(
              (message) =>
                  (_type == null || message.type == _type) &&
                  message.matches(_query),
            )
            .toList() ??
        [];
    return Scaffold(
      appBar: AppBar(
        title: Text(_l10n.activeContextTitle),
        actions: [
          IconButton(
            tooltip: _l10n.activeContextRefresh,
            onPressed: _loading || !_sameLocation ? null : _load,
            icon: const Icon(Icons.refresh_rounded),
          ),
        ],
      ),
      body: !_sameLocation
          ? Center(
              child: Padding(
                padding: const EdgeInsets.all(24),
                child: Text(_l10n.activeContextChanged),
              ),
            )
          : messages == null
          ? _error != null
                ? ProductErrorState(
                    message: _errorText(_error!),
                    onRetry: _load,
                  )
                : const LoadingList()
          : RefreshIndicator(
              onRefresh: _load,
              child: CustomScrollView(
                key: const ValueKey('active-context-list'),
                physics: const AlwaysScrollableScrollPhysics(),
                keyboardDismissBehavior:
                    ScrollViewKeyboardDismissBehavior.onDrag,
                slivers: [
                  SliverToBoxAdapter(
                    child: Column(
                      children: [
                        if (_loading)
                          const LinearProgressIndicator(minHeight: 2),
                        if (_error != null)
                          Padding(
                            padding: const EdgeInsets.all(12),
                            child: Text(
                              _l10n.activeContextRefreshFailed(
                                _errorText(_error!),
                              ),
                              style: TextStyle(
                                color: Theme.of(context).colorScheme.error,
                              ),
                            ),
                          ),
                        Padding(
                          padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
                          child: Text(_l10n.activeContextHelp),
                        ),
                        Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 16),
                          child: TextField(
                            controller: _search,
                            key: const ValueKey('active-context-search'),
                            onChanged: (value) => setState(
                              () => _query = value.trim().toLowerCase(),
                            ),
                            decoration: InputDecoration(
                              hintText: _l10n.activeContextSearch,
                              prefixIcon: const Icon(Icons.search_rounded),
                              suffixIcon: _query.isEmpty
                                  ? null
                                  : IconButton(
                                      tooltip: _l10n.commonClearSearch,
                                      icon: const Icon(Icons.close_rounded),
                                      onPressed: () {
                                        _search.clear();
                                        setState(() => _query = '');
                                      },
                                    ),
                              isDense: true,
                            ),
                          ),
                        ),
                        SingleChildScrollView(
                          scrollDirection: Axis.horizontal,
                          padding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 8,
                          ),
                          child: Row(
                            children: [
                              ChoiceChip(
                                label: Text(_l10n.activeContextAll),
                                selected: _type == null,
                                onSelected: (_) => setState(() => _type = null),
                              ),
                              for (final entry in counts.entries)
                                Padding(
                                  padding: const EdgeInsets.only(left: 8),
                                  child: ChoiceChip(
                                    label: Text(
                                      '${contextTypeLabel(_l10n, entry.key)} · ${entry.value}',
                                    ),
                                    selected: _type == entry.key,
                                    onSelected: (_) =>
                                        setState(() => _type = entry.key),
                                  ),
                                ),
                            ],
                          ),
                        ),
                        Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 16),
                          child: Align(
                            alignment: AlignmentDirectional.centerStart,
                            child: Text(
                              _l10n.activeContextCount(
                                visible.length,
                                messages.length,
                              ),
                              style: Theme.of(context).textTheme.labelMedium,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                  visible.isEmpty
                      ? SliverToBoxAdapter(
                          child: Padding(
                            padding: const EdgeInsets.all(24),
                            child: Text(
                              messages.isEmpty
                                  ? _l10n.activeContextEmpty
                                  : _l10n.activeContextNoMatches,
                            ),
                          ),
                        )
                      : SliverList.separated(
                          itemCount: visible.length,
                          separatorBuilder: (_, _) => const Divider(height: 1),
                          itemBuilder: (context, index) {
                            final message = visible[index];
                            final preview = message.previewFor(_query);
                            return ListTile(
                              key: ValueKey('active-context-${message.id}'),
                              title: Text(
                                contextTypeLabel(_l10n, message.type),
                              ),
                              subtitle: Text(
                                preview.isEmpty
                                    ? _l10n.activeContextNoText
                                    : preview,
                                maxLines: 3,
                                overflow: TextOverflow.ellipsis,
                              ),
                              trailing: const Icon(Icons.chevron_right_rounded),
                              onTap: () => Navigator.of(context).push(
                                MaterialPageRoute<void>(
                                  builder: (_) => _ContextMessageScreen(
                                    controller: widget.controller,
                                    location: _location,
                                    sessionID: widget.sessionID,
                                    history: _history,
                                    message: message,
                                  ),
                                ),
                              ),
                            );
                          },
                        ),
                  SliverToBoxAdapter(
                    child: SizedBox(
                      height: MediaQuery.paddingOf(context).bottom,
                    ),
                  ),
                ],
              ),
            ),
    );
  }
}

String contextTypeLabel(AppLocalizations l10n, String type) => switch (type) {
  'user' => l10n.activeContextUser,
  'assistant' => l10n.activeContextAssistant,
  'system' => l10n.activeContextSystem,
  'synthetic' => l10n.activeContextSynthetic,
  'skill' => l10n.activeContextSkill,
  'shell' => l10n.activeContextShell,
  'compaction' => l10n.activeContextCompaction,
  'agent-switched' ||
  'model-switched' ||
  'location-switched' => l10n.activeContextChange,
  _ => type,
};

class _ContextMessageScreen extends StatelessWidget {
  const _ContextMessageScreen({
    required this.controller,
    required this.location,
    required this.message,
    required this.sessionID,
    required this.history,
  });
  final ConnectionController controller;
  final int location;
  final ActiveContextMessage message;
  final String sessionID;
  final int history;
  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Scaffold(
      appBar: AppBar(title: Text(contextTypeLabel(l10n, message.type))),
      body: ListenableBuilder(
        listenable: controller,
        builder: (context, _) {
          if (controller.locationRevision != location ||
              controller.sessionHistoryRevision(sessionID) != history) {
            return Center(
              child: Padding(
                padding: const EdgeInsets.all(24),
                child: Text(l10n.activeContextChanged),
              ),
            );
          }
          return ListView.separated(
            padding: EdgeInsets.fromLTRB(
              16,
              16,
              16,
              16 + MediaQuery.paddingOf(context).bottom,
            ),
            itemCount: message.content.length + 1,
            separatorBuilder: (_, _) => const Divider(height: 32),
            itemBuilder: (context, index) {
              if (index == 0) {
                return Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    SelectableText(
                      message.id,
                      style: Theme.of(context).textTheme.labelMedium,
                    ),
                    const SizedBox(height: 8),
                    Text(l10n.activeContextContentHelp),
                  ],
                );
              }
              final part = message.content[index - 1];
              final kind = switch (part.kind) {
                ContextContentKind.text => l10n.activeContextText,
                ContextContentKind.reasoning => l10n.transcriptFindReasoning,
                ContextContentKind.toolInput => l10n.activeContextToolInput,
                ContextContentKind.toolOutput => l10n.activeContextToolOutput,
                ContextContentKind.file => l10n.activeContextFile,
                ContextContentKind.notice => l10n.activeContextNotice,
                ContextContentKind.pruned => l10n.activeContextPruned,
                ContextContentKind.truncated => l10n.activeContextTruncated,
              };
              return Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Row(
                    children: [
                      Expanded(
                        child: Text(
                          part.name?.isNotEmpty == true
                              ? '$kind · ${part.name}'
                              : kind,
                          style: Theme.of(context).textTheme.titleSmall,
                        ),
                      ),
                      if (part.text.isNotEmpty)
                        IconButton(
                          tooltip: MaterialLocalizations.of(
                            context,
                          ).copyButtonLabel,
                          icon: const Icon(Icons.copy_rounded),
                          onPressed: () =>
                              Clipboard.setData(ClipboardData(text: part.text)),
                        ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  if (part.kind != ContextContentKind.pruned &&
                      part.kind != ContextContentKind.truncated)
                    SelectableText(
                      part.text.isEmpty ? l10n.activeContextNoText : part.text,
                    ),
                ],
              );
            },
          );
        },
      ),
    );
  }
}
