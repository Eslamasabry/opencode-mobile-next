import 'dart:async';

import 'package:flutter/material.dart';

import '../../api/product_repository.dart';
import '../../state/connection.dart';
import '../widgets/product_states.dart';

class GlobalSessionsScreen extends StatefulWidget {
  final ConnectionController controller;

  const GlobalSessionsScreen({super.key, required this.controller});

  @override
  State<GlobalSessionsScreen> createState() => _GlobalSessionsScreenState();
}

class _GlobalSessionsScreenState extends State<GlobalSessionsScreen> {
  static const _pageSize = 50;

  final _search = TextEditingController();
  final _scroll = ScrollController();
  List<GlobalSessionResult> _results = const [];
  Timer? _debounce;
  Object? _error;
  bool _includeArchived = false;
  bool _loading = true;
  bool _loadingMore = false;
  bool _hasMore = false;
  String? _openingSessionID;
  int _queryGeneration = 0;
  int _dataRefreshRevision = 0;

  @override
  void initState() {
    super.initState();
    _dataRefreshRevision = widget.controller.dataRefreshRevision;
    widget.controller.addListener(_controllerChanged);
    _scroll.addListener(_scrollChanged);
    unawaited(_reload());
  }

  void _controllerChanged() {
    if (!mounted) return;
    final revision = widget.controller.dataRefreshRevision;
    if (revision == _dataRefreshRevision) return;
    _dataRefreshRevision = revision;
    if (widget.controller.repository != null) unawaited(_reload());
  }

  void _scrollChanged() {
    if (!_scroll.hasClients ||
        _scroll.position.extentAfter > 280 ||
        !_hasMore ||
        _loadingMore) {
      return;
    }
    unawaited(_loadMore());
  }

  void _searchChanged(String _) {
    _debounce?.cancel();
    _debounce = Timer(const Duration(milliseconds: 300), () {
      if (mounted) unawaited(_reload());
    });
    setState(() {});
  }

  Future<ProductRepository> _repository() async {
    final repository = await widget.controller.prepareActionRepository();
    if (repository != null) return repository;
    throw const ProductException('OpenCode is reconnecting. Try again.');
  }

  Future<void> _reload() async {
    final generation = ++_queryGeneration;
    final query = _search.text.trim();
    final includeArchived = _includeArchived;
    setState(() {
      _loading = true;
      _loadingMore = false;
      _error = null;
      _hasMore = false;
    });
    try {
      final repository = await _repository();
      final results = await repository.listGlobalSessions(
        search: query,
        includeArchived: includeArchived,
        limit: _pageSize,
      );
      if (!mounted || generation != _queryGeneration) return;
      setState(() {
        _results = results;
        _hasMore = results.length == _pageSize && _cursor(results) != null;
      });
    } catch (error) {
      if (!mounted || generation != _queryGeneration) return;
      setState(() {
        _results = const [];
        _error = error;
      });
    } finally {
      if (mounted && generation == _queryGeneration) {
        setState(() => _loading = false);
      }
    }
  }

  Future<void> _loadMore() async {
    final generation = _queryGeneration;
    final cursor = _cursor(_results);
    if (_loading || _loadingMore || !_hasMore || cursor == null) return;
    setState(() {
      _loadingMore = true;
      _error = null;
    });
    try {
      final repository = await _repository();
      final page = await repository.listGlobalSessions(
        search: _search.text.trim(),
        includeArchived: _includeArchived,
        cursor: cursor,
        limit: _pageSize,
      );
      if (!mounted || generation != _queryGeneration) return;
      final existing = _results.map((result) => result.session.id).toSet();
      final added = page
          .where((result) => existing.add(result.session.id))
          .toList();
      final nextCursor = _cursor(page);
      setState(() {
        _results = [..._results, ...added];
        _hasMore =
            page.length == _pageSize &&
            added.isNotEmpty &&
            nextCursor != null &&
            nextCursor < cursor;
      });
    } catch (error) {
      if (mounted && generation == _queryGeneration) {
        setState(() => _error = error);
      }
    } finally {
      if (mounted && generation == _queryGeneration) {
        setState(() => _loadingMore = false);
      }
    }
  }

  static int? _cursor(List<GlobalSessionResult> results) {
    if (results.isEmpty) return null;
    final time = results.last.session.time;
    return time?.updated ?? time?.created;
  }

  Future<void> _open(GlobalSessionResult result) async {
    final session = result.session;
    if (_openingSessionID != null) return;
    setState(() => _openingSessionID = session.id);
    try {
      await widget.controller.selectLocation(
        directory: session.directory ?? result.projectDirectory,
        workspace: session.workspaceID,
      );
      if (!mounted) return;
      await Navigator.of(context).pushNamed('/chat/${session.id}');
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Could not open the session: $error')),
        );
      }
    } finally {
      if (mounted) setState(() => _openingSessionID = null);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('All sessions')),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
            child: TextField(
              key: const ValueKey('global-session-search'),
              controller: _search,
              textInputAction: TextInputAction.search,
              onChanged: _searchChanged,
              decoration: InputDecoration(
                labelText: 'Search session titles',
                hintText: 'Across every OpenCode project',
                prefixIcon: const Icon(Icons.search_rounded),
                suffixIcon: _search.text.isEmpty
                    ? null
                    : IconButton(
                        tooltip: 'Clear session search',
                        onPressed: () {
                          _search.clear();
                          _debounce?.cancel();
                          unawaited(_reload());
                          setState(() {});
                        },
                        icon: const Icon(Icons.close_rounded),
                      ),
              ),
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
            child: LayoutBuilder(
              builder: (context, constraints) {
                final compact =
                    constraints.maxWidth < 360 ||
                    MediaQuery.textScalerOf(context).scale(14) > 20;
                return Row(
                  children: [
                    Flexible(
                      child: FilterChip(
                        key: const ValueKey('include-archived-sessions'),
                        selected: _includeArchived,
                        label: Text(
                          compact ? 'Archived' : 'Include archived',
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                        onSelected: (selected) {
                          setState(() => _includeArchived = selected);
                          unawaited(_reload());
                        },
                      ),
                    ),
                    const Spacer(),
                    if (!_loading && _results.isNotEmpty)
                      Text(
                        _hasMore ? '${_results.length}+' : '${_results.length}',
                        key: const ValueKey('global-session-count'),
                        style: Theme.of(context).textTheme.labelMedium
                            ?.copyWith(
                              color: Theme.of(
                                context,
                              ).colorScheme.onSurfaceVariant,
                            ),
                      ),
                  ],
                );
              },
            ),
          ),
          const Divider(height: 1),
          Expanded(child: _content()),
        ],
      ),
    );
  }

  Widget _content() {
    if (_loading) return const LoadingList(rows: 7);
    if (_error != null && _results.isEmpty) {
      return ProductErrorState(message: _error.toString(), onRetry: _reload);
    }
    if (_results.isEmpty) {
      final query = _search.text.trim();
      return ProductEmptyState(
        icon: Icons.manage_search_rounded,
        title: query.isEmpty ? 'No sessions yet' : 'No matching sessions',
        message: query.isEmpty
            ? 'Sessions from every OpenCode project will appear here.'
            : 'Try a shorter title search or include archived sessions.',
        actionLabel: query.isEmpty ? 'Refresh' : 'Clear search',
        onAction: query.isEmpty
            ? _reload
            : () {
                _search.clear();
                setState(() {});
                unawaited(_reload());
              },
      );
    }

    final extraRows = (_error != null || _loadingMore) ? 1 : 0;
    return RefreshIndicator(
      onRefresh: _reload,
      child: ListView.separated(
        key: const PageStorageKey('global-sessions-list'),
        controller: _scroll,
        physics: const AlwaysScrollableScrollPhysics(),
        itemCount: _results.length + extraRows,
        separatorBuilder: (_, index) => index < _results.length - 1
            ? const Divider(height: 1)
            : const SizedBox.shrink(),
        itemBuilder: (context, index) {
          if (index == _results.length) {
            if (_error != null) {
              return ListTile(
                leading: Icon(
                  Icons.error_outline_rounded,
                  color: Theme.of(context).colorScheme.error,
                ),
                title: const Text('Could not load more sessions'),
                subtitle: Text(_error.toString()),
                trailing: TextButton(
                  onPressed: _loadMore,
                  child: const Text('Retry'),
                ),
              );
            }
            return const Padding(
              padding: EdgeInsets.symmetric(vertical: 18),
              child: Center(
                child: SizedBox.square(
                  dimension: 22,
                  child: CircularProgressIndicator(strokeWidth: 2),
                ),
              ),
            );
          }
          return _GlobalSessionRow(
            result: _results[index],
            opening: _openingSessionID == _results[index].session.id,
            onTap: () => _open(_results[index]),
          );
        },
      ),
    );
  }

  @override
  void dispose() {
    _debounce?.cancel();
    widget.controller.removeListener(_controllerChanged);
    _scroll
      ..removeListener(_scrollChanged)
      ..dispose();
    _search.dispose();
    super.dispose();
  }
}

class _GlobalSessionRow extends StatelessWidget {
  final GlobalSessionResult result;
  final bool opening;
  final VoidCallback onTap;

  const _GlobalSessionRow({
    required this.result,
    required this.opening,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final session = result.session;
    final title = session.title?.trim().isNotEmpty == true
        ? session.title!.trim()
        : 'Untitled session';
    final project = _projectLabel(result);
    final details = <String>[
      project,
      if (session.path?.trim().isNotEmpty == true) session.path!.trim(),
      _formatTimestamp(session.time?.updated ?? session.time?.created),
      if (session.archived) 'Archived',
    ].where((value) => value.isNotEmpty).join(' · ');
    return Semantics(
      button: true,
      label: 'Open $title. $details',
      onTap: opening ? null : onTap,
      child: ExcludeSemantics(
        child: ListTile(
          key: ValueKey('global-session-${session.id}'),
          minTileHeight: 72,
          contentPadding: const EdgeInsets.symmetric(
            horizontal: 16,
            vertical: 4,
          ),
          leading: Icon(
            session.archived
                ? Icons.inventory_2_outlined
                : Icons.chat_bubble_outline_rounded,
            size: 22,
          ),
          title: Text(title, maxLines: 2, overflow: TextOverflow.ellipsis),
          subtitle: Text(details, maxLines: 3, overflow: TextOverflow.ellipsis),
          trailing: opening
              ? const SizedBox.square(
                  dimension: 20,
                  child: CircularProgressIndicator(strokeWidth: 2),
                )
              : const Icon(Icons.chevron_right_rounded),
          enabled: !opening,
          onTap: opening ? null : onTap,
        ),
      ),
    );
  }

  static String _projectLabel(GlobalSessionResult result) {
    final named = result.projectName?.trim();
    if (named?.isNotEmpty == true) return named!;
    for (final path in [result.projectDirectory, result.session.directory]) {
      final parts = (path ?? '')
          .replaceAll('\\', '/')
          .split('/')
          .where((part) => part.isNotEmpty)
          .toList();
      if (parts.isNotEmpty) return parts.last;
    }
    return 'Unknown project';
  }

  static String _formatTimestamp(int? milliseconds) {
    if (milliseconds == null || milliseconds <= 0) return '';
    final value = DateTime.fromMillisecondsSinceEpoch(milliseconds).toLocal();
    const months = [
      'Jan',
      'Feb',
      'Mar',
      'Apr',
      'May',
      'Jun',
      'Jul',
      'Aug',
      'Sep',
      'Oct',
      'Nov',
      'Dec',
    ];
    final now = DateTime.now();
    final date = value.year == now.year
        ? '${months[value.month - 1]} ${value.day}'
        : '${months[value.month - 1]} ${value.day}, ${value.year}';
    final hour = value.hour.toString().padLeft(2, '0');
    final minute = value.minute.toString().padLeft(2, '0');
    return '$date, $hour:$minute';
  }
}
