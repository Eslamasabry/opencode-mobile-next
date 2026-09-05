import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/semantics.dart' show CustomSemanticsAction;

import '../../api/product_repository.dart';
import '../../l10n/app_localizations.dart';
import '../../state/connection.dart';
import '../desktop/context_menu.dart';
import '../widgets/confirm_sheet.dart';
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
  String? _nextCursor;
  final Set<String> _usedCursors = {};
  bool _restartPagination = false;
  bool get _hasMore => _nextCursor != null;
  String? _openingSessionID;
  String? _stealingSessionID;
  int _queryGeneration = 0;
  int _dataRefreshRevision = 0;
  ServerOperationsGateway? _activeRepository;
  String? _profileID;

  @override
  void initState() {
    super.initState();
    _dataRefreshRevision = widget.controller.dataRefreshRevision;
    _activeRepository = widget.controller.repository;
    _profileID = widget.controller.profile?.id;
    widget.controller.addListener(_controllerChanged);
    _scroll.addListener(_scrollChanged);
    unawaited(_reload());
  }

  void _controllerChanged() {
    if (!mounted) return;
    final revision = widget.controller.dataRefreshRevision;
    final repository = widget.controller.repository;
    final profileID = widget.controller.profile?.id;
    if (revision == _dataRefreshRevision &&
        identical(repository, _activeRepository) &&
        profileID == _profileID) {
      return;
    }
    _dataRefreshRevision = revision;
    _activeRepository = repository;
    _profileID = profileID;
    unawaited(_reload());
  }

  void _scrollChanged() {
    if (!_scroll.hasClients ||
        _scroll.position.extentAfter > 280 ||
        !_hasMore ||
        _error != null ||
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
    // Invalidate in-flight pages immediately, before the debounce expires.
    setState(() {
      _queryGeneration++;
      _loading = true;
      _loadingMore = false;
      _nextCursor = null;
    });
  }

  Future<ServerOperationsGateway> _repository() async {
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
      _nextCursor = null;
      _usedCursors.clear();
      _restartPagination = false;
    });
    try {
      final repository = await _repository();
      if (!mounted || generation != _queryGeneration) return;
      final results = await repository.listGlobalSessions(
        search: query,
        includeArchived: includeArchived,
        limit: _pageSize,
      );
      if (!mounted || generation != _queryGeneration) return;
      setState(() {
        final seen = <String>{};
        _results = results.items
            .where((result) => seen.add(result.session.id))
            .toList();
        _nextCursor = results.hasMore ? results.nextCursor : null;
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
    final cursor = _nextCursor;
    if (_loading || _loadingMore || !_hasMore || cursor == null) return;
    final query = _search.text.trim();
    final includeArchived = _includeArchived;
    setState(() {
      _loadingMore = true;
      _error = null;
    });
    try {
      final repository = await _repository();
      if (!mounted || generation != _queryGeneration) return;
      final page = await repository.listGlobalSessions(
        search: query,
        includeArchived: includeArchived,
        cursor: cursor,
        limit: _pageSize,
      );
      if (!mounted || generation != _queryGeneration) return;
      final existing = _results.map((result) => result.session.id).toSet();
      final added = page.items
          .where((result) => existing.add(result.session.id))
          .toList();
      final nextCursor = page.hasMore ? page.nextCursor : null;
      if (nextCursor != null &&
          (nextCursor == cursor || _usedCursors.contains(nextCursor))) {
        _restartPagination = true;
        throw const ProductException(
          'Session pagination could not advance. Refresh the list to continue.',
        );
      }
      setState(() {
        _results = [..._results, ...added];
        _usedCursors.add(cursor);
        _nextCursor = nextCursor;
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
      if (mounted) showProductError(context, error);
    } finally {
      if (mounted) setState(() => _openingSessionID = null);
    }
  }

  /// True when a steal can genuinely move this session here. Live OpenCode
  /// 1.18.23 refuses `/sync/steal` (BadRequest) for plain cross-directory
  /// sessions — steal operates on the workspace sync system only, and plain
  /// directory transfer remains the /move workflow. So the affordance shows
  /// only when a workspace is involved on either side, never for ordinary
  /// cross-project rows.
  bool _isElsewhere(GlobalSessionResult result) {
    final active = widget.controller.directory?.trim() ?? '';
    if (active.isEmpty) return false;
    final activeWorkspace = widget.controller.workspace?.trim() ?? '';
    final sessionWorkspace = result.session.workspaceID?.trim() ?? '';
    if (sessionWorkspace.isEmpty && activeWorkspace.isEmpty) return false;
    if (sessionWorkspace != activeWorkspace) return true;
    final sessionDirectory =
        (result.session.directory ?? result.projectDirectory)?.trim() ?? '';
    return sessionDirectory.isNotEmpty && sessionDirectory != active;
  }

  Future<void> _steal(GlobalSessionResult result) async {
    final session = result.session;
    if (_stealingSessionID != null || _openingSessionID != null) return;
    final title = session.title?.trim().isNotEmpty == true
        ? session.title!.trim()
        : 'Untitled session';
    final confirmed = await showConfirmSheet(
      context,
      icon: Icons.move_to_inbox_rounded,
      title: 'Continue this session here?',
      message:
          '“$title” will belong to your current workspace through the '
          'server’s sync system. It stops belonging to the workspace it '
          'runs in now.',
      confirmLabel: 'Continue here',
    );
    if (!confirmed || !mounted) return;
    setState(() => _stealingSessionID = session.id);
    try {
      final repository = await _repository();
      final stolenID = await repository.stealSessionIntoWorkspace(session.id);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('“$title” now belongs to this workspace')),
      );
      await Navigator.of(context).pushNamed('/chat/$stolenID');
      if (mounted) unawaited(_reload());
    } catch (error) {
      if (mounted) showProductError(context, error);
    } finally {
      if (mounted) setState(() => _stealingSessionID = null);
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
      return ProductErrorState(
        message: productErrorText(_error!),
        onRetry: _hasMore && !_restartPagination ? _loadMore : _reload,
      );
    }
    if (_results.isEmpty && !_hasMore) {
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

    final extraRows = (_error != null || _hasMore) ? 1 : 0;
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
                subtitle: Text(productErrorText(_error!)),
                trailing: TextButton(
                  onPressed: _restartPagination ? _reload : _loadMore,
                  child: const Text('Try again'),
                ),
              );
            }
            if (!_loadingMore) {
              final l10n =
                  Localizations.of<AppLocalizations>(
                    context,
                    AppLocalizations,
                  ) ??
                  lookupAppLocalizations(Localizations.localeOf(context));
              return Padding(
                padding: const EdgeInsets.all(16),
                child: OutlinedButton(
                  key: const ValueKey('global-sessions-load-more'),
                  onPressed: _loadMore,
                  child: Text(l10n.globalSessionsLoadMore),
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
            stealing: _stealingSessionID == _results[index].session.id,
            onTap: () => _open(_results[index]),
            // §7 row 7: "Continue here" is steal + sync-start, neither of
            // which v2 has. A future rebuild is export+import+move.
            onSteal:
                widget.controller.capabilities.sessionSteal &&
                    _isElsewhere(_results[index])
                ? () => unawaited(_steal(_results[index]))
                : null,
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
  final bool stealing;
  final VoidCallback onTap;

  /// Non-null only when the session lives outside the active location.
  final VoidCallback? onSteal;

  const _GlobalSessionRow({
    required this.result,
    required this.opening,
    this.stealing = false,
    required this.onTap,
    this.onSteal,
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
    final row = Semantics(
      button: true,
      label: 'Open $title. $details',
      onTap: opening ? null : onTap,
      customSemanticsActions: {
        if (onSteal != null && !opening && !stealing)
          const CustomSemanticsAction(label: 'Continue here'): onSteal!,
      },
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
          // One overflow menu instead of a per-row icon: Open is the tap,
          // Continue here rides in the menu (and, on desktop, right click).
          trailing: opening || stealing
              ? const SizedBox.square(
                  dimension: 20,
                  child: CircularProgressIndicator(strokeWidth: 2),
                )
              : PopupMenuButton<String>(
                  key: ValueKey('global-session-actions-${session.id}'),
                  tooltip: 'Session actions',
                  onSelected: (value) {
                    if (value == 'open') onTap();
                    if (value == 'steal') onSteal?.call();
                  },
                  itemBuilder: (_) => [
                    const PopupMenuItem(value: 'open', child: Text('Open')),
                    if (onSteal != null)
                      PopupMenuItem(
                        key: ValueKey('steal-session-${session.id}'),
                        value: 'steal',
                        child: const Text('Continue here'),
                      ),
                  ],
                ),
          enabled: !opening && !stealing,
          onTap: opening || stealing ? null : onTap,
        ),
      ),
    );
    // Steal is offered only where it is genuinely possible, matching the
    // trailing button's own gate. Off desktop this wrapper is a pass-through.
    return ContextMenuRegion(
      actions: () => [
        if (!opening && !stealing)
          ContextMenuAction(
            menuKey: const ValueKey('global-session-menu-open'),
            label: 'Open',
            icon: Icons.open_in_new_rounded,
            onSelected: onTap,
          ),
        if (onSteal != null && !opening && !stealing)
          ContextMenuAction(
            menuKey: const ValueKey('global-session-menu-steal'),
            label: 'Continue here',
            icon: Icons.move_to_inbox_rounded,
            onSelected: onSteal!,
          ),
      ],
      child: row,
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
