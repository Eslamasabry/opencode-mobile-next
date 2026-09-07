import 'dart:async';

import 'package:flutter/material.dart';

import '../../api/models.dart';
import '../../domain/run_result.dart';
import '../../domain/server_gateway.dart';
import '../../l10n/app_localizations.dart';
import '../../state/connection.dart';
import '../widgets/run_result_view.dart';

/// Loads a session's newest history pages and shows the latest run's
/// server-recorded outcome and tool evidence. Nothing is persisted: after a
/// restart the same server records rebuild the same view, and the only
/// in-memory fact (whether this phone saw the last step complete live) is
/// reported as absent rather than assumed.
class RunResultScreen extends StatefulWidget {
  const RunResultScreen({
    super.key,
    required this.controller,
    required this.sessionID,
  });

  final ConnectionController controller;
  final String sessionID;

  /// How many older pages to walk looking for the user message that started
  /// the run before giving up and labelling the history partial.
  static const maxPages = 5;

  @override
  State<RunResultScreen> createState() => _RunResultScreenState();
}

class _RunResultScreenState extends State<RunResultScreen> {
  bool _loading = true;
  String? _error;
  RunResult? _result;
  bool _loaded = false;
  int _generation = 0;
  late final Object _boundScope;
  bool _invalidated = false;

  Object get _currentScope => (
    widget.controller,
    widget.sessionID,
    widget.controller.profile?.id,
    widget.controller.profile?.baseUrl,
    widget.controller.connectionRevision,
    widget.controller.locationRevision,
    widget.controller.directory,
    widget.controller.workspace,
  );

  bool get _current => !_invalidated && _boundScope == _currentScope;

  void _invalidateIfChanged() {
    if (_current) return;
    _invalidated = true;
    _generation++;
    _result = null;
    _loaded = false;
    _loading = false;
    _error = null;
  }

  @override
  void initState() {
    super.initState();
    _boundScope = _currentScope;
    widget.controller.addListener(_changed);
    // The first load starts after initState so its setState is legal; the
    // initial fields already describe the loading state for the first frame.
    scheduleMicrotask(_load);
  }

  @override
  void didUpdateWidget(covariant RunResultScreen oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller != widget.controller) {
      oldWidget.controller.removeListener(_changed);
      widget.controller.addListener(_changed);
    }
    _invalidateIfChanged();
  }

  @override
  void dispose() {
    widget.controller.removeListener(_changed);
    super.dispose();
  }

  void _changed() {
    if (mounted) setState(_invalidateIfChanged);
  }

  Future<void> _load() async {
    if (!mounted) return;
    _invalidateIfChanged();
    if (!_current) {
      setState(() {});
      return;
    }
    final generation = ++_generation;
    final controller = widget.controller;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final api = await controller.prepareActionTransport();
      if (!mounted || generation != _generation || !_current) return;
      if (api == null) {
        throw const ProductException('OpenCode is reconnecting. Try again.');
      }
      final loaded = await loadRunHistory(
        api,
        widget.sessionID,
        isCurrent: () => mounted && generation == _generation && _current,
      );
      if (!mounted || generation != _generation || !_current) return;
      setState(() {
        _result = RunResult.fromMessages(
          widget.sessionID,
          loaded.messages,
          historyComplete: loaded.complete,
        );
        _loaded = true;
        _loading = false;
      });
    } catch (error) {
      if (!mounted || generation != _generation || !_current) return;
      setState(() {
        _error = error is ProductException ? error.message : '$error';
        _loading = false;
      });
    }
  }

  void _openConversation() {
    if (!_current) return;
    Navigator.of(context).pushNamed('/chat/${widget.sessionID}');
  }

  @override
  Widget build(BuildContext context) {
    _invalidateIfChanged();
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final theme = Theme.of(context);
    final session = widget.controller.sessionsById[widget.sessionID];
    final result = _result;
    final Widget body;
    if (!_current) {
      body = Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Text(
            l10n.runResultsScopeChanged,
            key: const Key('run-result-scope-changed'),
            textAlign: TextAlign.center,
          ),
        ),
      );
    } else if (_loading && !_loaded) {
      body = const Center(
        key: Key('run-result-loading'),
        child: CircularProgressIndicator(),
      );
    } else if (_error != null && !_loaded) {
      body = Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                _error!,
                key: const Key('run-result-error-state'),
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 12),
              FilledButton.tonal(
                onPressed: _load,
                child: Text(l10n.commonRetry),
              ),
            ],
          ),
        ),
      );
    } else if (result == null) {
      body = ListView(
        padding: const EdgeInsets.all(24),
        children: [
          Text(
            l10n.runResultsEmpty,
            key: const Key('run-result-empty'),
            textAlign: TextAlign.center,
            style: theme.textTheme.bodyMedium,
          ),
        ],
      );
    } else {
      body = RunResultView(
        result: result,
        sessionTitle: session?.title,
        observedLive: widget.controller.observedCompletedMessageIDs.contains(
          result.lastStepID,
        ),
        onOpenConversation: _openConversation,
      );
    }
    return Scaffold(
      appBar: AppBar(title: Text(l10n.runResultsTitle)),
      body: RefreshIndicator(
        onRefresh: _load,
        child: Column(
          children: [
            if (_error != null && _loaded)
              MaterialBanner(
                key: const Key('run-result-refresh-error'),
                content: Text(_error!),
                actions: [
                  TextButton(onPressed: _load, child: Text(l10n.commonRetry)),
                ],
              ),
            Expanded(child: body),
          ],
        ),
      ),
    );
  }
}

/// The newest pages of a session, walked back until the user message that
/// started the latest run is included, the history is exhausted, or
/// [RunResultScreen.maxPages] pages were read. [complete] is true only when
/// the server reported no further pages.
class LoadedRunHistory {
  const LoadedRunHistory({required this.messages, required this.complete});
  final List<MessageWithParts> messages;
  final bool complete;
}

Future<LoadedRunHistory> loadRunHistory(
  ServerGateway api,
  String sessionID, {
  required bool Function() isCurrent,
  int maxPages = RunResultScreen.maxPages,
}) async {
  final messages = <MessageWithParts>[];
  final seen = <String>{};
  final cursors = <String>{};
  String? cursor;
  var complete = false;
  for (var page = 0; page < maxPages; page++) {
    final result = await api.messagePage(sessionID, cursor: cursor);
    if (!isCurrent()) {
      throw const ProductException(
        'The session changed while loading history.',
      );
    }
    // Pages walk backwards; each gateway page keeps its server item order.
    // Prepend older pages so equal timestamps spanning pages stay ordered.
    final older = <MessageWithParts>[];
    for (final message in result.items) {
      if (seen.add(message.info.id)) older.add(message);
    }
    messages.insertAll(0, older);
    if (!result.hasMore) {
      complete = true;
      break;
    }
    if (messages.any((m) => m.info.role == 'user')) break;
    final next = result.nextCursor;
    if (next == null || next == cursor || !cursors.add(next)) break;
    cursor = next;
  }
  return LoadedRunHistory(messages: messages, complete: complete);
}
