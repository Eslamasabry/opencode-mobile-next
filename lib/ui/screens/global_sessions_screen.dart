import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/semantics.dart' show CustomSemanticsAction;

import '../../api/product_repository.dart';
import '../../l10n/app_localizations.dart';
import '../../state/connection.dart';
import '../desktop/context_menu.dart';
import '../widgets/confirm_sheet.dart';
import '../widgets/product_states.dart';
import '../widgets/session_read_state.dart';
import '../widgets/session_handoff.dart';
import 'session_relations_screen.dart';

class GlobalSessionsScreen extends StatefulWidget {
  final ConnectionController controller;

  const GlobalSessionsScreen({super.key, required this.controller});

  @override
  State<GlobalSessionsScreen> createState() => _GlobalSessionsScreenState();
}

class _GlobalSessionsScope {
  const _GlobalSessionsScope({
    required this.profileID,
    required this.query,
    required this.includeArchived,
  });

  final String? profileID;
  final String query;
  final bool includeArchived;

  @override
  bool operator ==(Object other) =>
      other is _GlobalSessionsScope &&
      other.profileID == profileID &&
      other.query == query &&
      other.includeArchived == includeArchived;

  @override
  int get hashCode => Object.hash(profileID, query, includeArchived);
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
  _GlobalSessionsScope? _loadedScope;
  bool _errorWasRefresh = false;
  final Map<String, FocusNode> _rowFocus = {};

  _GlobalSessionsScope get _scope => _GlobalSessionsScope(
    profileID: widget.controller.profile?.id,
    query: _search.text.trim(),
    includeArchived: _includeArchived,
  );

  bool _requestIsCurrent(
    int generation,
    _GlobalSessionsScope scope,
    ServerOperationsGateway repository,
  ) =>
      mounted &&
      generation == _queryGeneration &&
      scope == _scope &&
      identical(repository, widget.controller.repository);

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
    final profileChanged = profileID != _profileID;
    final repositoryChanged = !identical(repository, _activeRepository);
    // Location selection and chat refreshes must not discard older pages.
    if ((_openingSessionID != null || _stealingSessionID != null) &&
        !profileChanged &&
        !repositoryChanged) {
      _dataRefreshRevision = revision;
      _activeRepository = repository;
      return;
    }
    if (revision == _dataRefreshRevision &&
        !repositoryChanged &&
        !profileChanged) {
      return;
    }
    // A replacement transport must retire delayed pages even though the
    // server-wide list remains logically scoped to the same profile.
    if (repositoryChanged || profileChanged) _queryGeneration++;
    if (profileID == _profileID && _results.isNotEmpty) {
      // The global inventory is profile-scoped, not location-scoped. Keep the
      // search, cursor chain and loaded older rows until an explicit refresh.
      _dataRefreshRevision = revision;
      _activeRepository = repository;
      // Rebuild callbacks against the current location after reconnecting.
      // Keeping the rows must not keep their retired navigation guards.
      setState(() {
        // A replacement repository retires any request it was serving. The
        // retained rows and cursor chain remain available for a retry.
        if (repositoryChanged) {
          _loading = false;
          _loadingMore = false;
          _error = null;
          _errorWasRefresh = false;
        }
      });
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
      _usedCursors.clear();
      _restartPagination = false;
      _error = null;
      _errorWasRefresh = false;
      if (_loadedScope != _scope) {
        _results = const [];
        _loadedScope = null;
      }
    });
  }

  Future<ServerOperationsGateway> _repository() async {
    final repository = await widget.controller.prepareActionRepository();
    if (repository != null) return repository;
    throw const ProductException('OpenCode is reconnecting. Try again.');
  }

  Future<void> _reload() async {
    final generation = ++_queryGeneration;
    final scope = _scope;
    final retainRows = _loadedScope == scope && _results.isNotEmpty;
    final previousCursor = _nextCursor;
    final previousCursors = Set<String>.of(_usedCursors);
    final previousRestartPagination = _restartPagination;
    setState(() {
      _loading = true;
      _loadingMore = false;
      _error = null;
      _errorWasRefresh = false;
      if (!retainRows) {
        _nextCursor = null;
        _usedCursors.clear();
        _restartPagination = false;
        _results = const [];
        _loadedScope = null;
      }
    });
    try {
      final repository = await _repository();
      if (!_requestIsCurrent(generation, scope, repository)) return;
      final results = await repository.listGlobalSessions(
        search: scope.query,
        includeArchived: scope.includeArchived,
        limit: _pageSize,
      );
      if (!_requestIsCurrent(generation, scope, repository)) return;
      setState(() {
        final seen = <String>{};
        _results = results.items
            .where((result) => seen.add(result.session.id))
            .toList();
        _nextCursor = results.hasMore ? results.nextCursor : null;
        _usedCursors.clear();
        _restartPagination = false;
        _loadedScope = scope;
      });
    } catch (error) {
      if (!mounted || generation != _queryGeneration || scope != _scope) {
        return;
      }
      setState(() {
        if (retainRows) {
          _nextCursor = previousCursor;
          _usedCursors
            ..clear()
            ..addAll(previousCursors);
          _restartPagination = previousRestartPagination;
          _errorWasRefresh = true;
        } else {
          _results = const [];
          _loadedScope = null;
        }
        _error = error;
      });
    } finally {
      if (mounted && generation == _queryGeneration && scope == _scope) {
        setState(() => _loading = false);
      }
    }
  }

  Future<void> _loadMore() async {
    final generation = _queryGeneration;
    final scope = _scope;
    final cursor = _nextCursor;
    if (_loading || _loadingMore || !_hasMore || cursor == null) return;
    setState(() {
      _loadingMore = true;
      _error = null;
      _errorWasRefresh = false;
    });
    try {
      final repository = await _repository();
      if (!_requestIsCurrent(generation, scope, repository)) return;
      final page = await repository.listGlobalSessions(
        search: scope.query,
        includeArchived: scope.includeArchived,
        cursor: cursor,
        limit: _pageSize,
      );
      if (!_requestIsCurrent(generation, scope, repository)) return;
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
      if (mounted && generation == _queryGeneration && scope == _scope) {
        setState(() => _error = error);
      }
    } finally {
      if (mounted && generation == _queryGeneration && scope == _scope) {
        setState(() => _loadingMore = false);
      }
    }
  }

  Future<void> _open(
    GlobalSessionResult result, {
    bool related = false,
    bool handoff = false,
  }) async {
    final session = result.session;
    if (_openingSessionID != null) return;
    if (!RegExp(r'^[A-Za-z0-9_-]+$').hasMatch(session.id)) {
      showProductError(
        context,
        'Session reference unavailable. Refresh and try again.',
      );
      return;
    }
    final profileID = widget.controller.profile?.id;
    final directory = session.directory ?? result.projectDirectory;
    if (profileID != _profileID || directory == null) {
      showProductError(
        context,
        'Session location unavailable. Refresh and try again.',
      );
      return;
    }
    setState(() => _openingSessionID = session.id);
    try {
      await widget.controller.selectLocationForExistingSession(
        directory: directory,
        workspace: session.workspaceID,
      );
      if (!mounted) return;
      if (widget.controller.profile?.id != profileID ||
          widget.controller.directory != directory ||
          widget.controller.workspace != session.workspaceID) {
        throw StateError('Session location changed. Return and try again.');
      }
      final scope = SessionNavigationScope(widget.controller);
      final repository = await _repository();
      scope.check(widget.controller);
      final current = await repository.getSessionDetails(session.id);
      scope.check(widget.controller);
      if (!mounted) return;
      if (current.id != session.id ||
          current.directory != session.directory ||
          current.workspaceID != session.workspaceID) {
        throw StateError('Session location changed. Refresh and try again.');
      }
      if (handoff) {
        await showSessionHandoff(
          context,
          controller: widget.controller,
          sessionID: current.id,
          projectID: current.projectID,
        );
      } else if (related) {
        final selected = await Navigator.of(context).push<Session>(
          MaterialPageRoute(
            builder: (_) => SessionRelationsScreen(
              controller: widget.controller,
              sessionID: session.id,
            ),
          ),
        );
        scope.check(widget.controller);
        if (!mounted || selected == null) return;
        if (!RegExp(r'^[A-Za-z0-9_-]+$').hasMatch(selected.id)) {
          throw StateError('Session reference unavailable.');
        }
        await Navigator.of(context).pushNamed('/chat/${selected.id}');
      } else {
        await Navigator.of(context).pushNamed('/chat/${session.id}');
      }
    } catch (error) {
      if (mounted) showProductError(context, error);
    } finally {
      if (mounted) {
        setState(() => _openingSessionID = null);
        _rowFocus[session.id]?.requestFocus();
      }
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
    final scope = SessionNavigationScope(widget.controller);
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
      scope.check(widget.controller);
      final repository = await _repository();
      scope.check(widget.controller);
      final stolenID = await repository.stealSessionIntoWorkspace(session.id);
      scope.check(widget.controller);
      if (!mounted) return;
      if (!RegExp(r'^[A-Za-z0-9_-]+$').hasMatch(stolenID)) {
        throw StateError('Session reference unavailable.');
      }
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('“$title” now belongs to this workspace')),
      );
      await Navigator.of(context).pushNamed('/chat/$stolenID');
    } catch (error) {
      if (mounted) showProductError(context, error);
    } finally {
      if (mounted) {
        setState(() => _stealingSessionID = null);
        _rowFocus[session.id]?.requestFocus();
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n =
        Localizations.of<AppLocalizations>(context, AppLocalizations) ??
        lookupAppLocalizations(Localizations.localeOf(context));
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
                        tooltip: l10n.commonClearSearch,
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
    final l10n =
        Localizations.of<AppLocalizations>(context, AppLocalizations) ??
        lookupAppLocalizations(Localizations.localeOf(context));
    if (_loading && _results.isEmpty) return const LoadingList(rows: 7);
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
        actionLabel: query.isEmpty ? 'Refresh' : l10n.commonClearSearch,
        onAction: query.isEmpty
            ? _reload
            : () {
                _search.clear();
                setState(() {});
                unawaited(_reload());
              },
      );
    }

    final extraRows = (_error != null || _hasMore || _loading) ? 1 : 0;
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
                title: Text(
                  _errorWasRefresh
                      ? lookupAppLocalizations(
                          Localizations.localeOf(context),
                        ).globalSessionsRefreshFailed
                      : 'Could not load more sessions',
                ),
                subtitle: Text(productErrorText(_error!)),
                trailing: TextButton(
                  onPressed: _errorWasRefresh || _restartPagination
                      ? _reload
                      : _loadMore,
                  child: Text(l10n.commonRetry),
                ),
              );
            }
            if (_loading) {
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
          final result = _results[index];
          final scope = SessionNavigationScope(widget.controller);
          void guarded(VoidCallback action) {
            if (!scope.matches(widget.controller)) {
              showProductError(
                context,
                'Session location changed. Return and try again.',
              );
              return;
            }
            action();
          }

          return Focus(
            focusNode: _rowFocus.putIfAbsent(result.session.id, FocusNode.new),
            child: _GlobalSessionRow(
              controller: widget.controller,
              result: _results[index],
              opening: _openingSessionID == _results[index].session.id,
              stealing: _stealingSessionID == _results[index].session.id,
              onTap: () => guarded(() => _open(result)),
              onRelated: () => guarded(() => _open(result, related: true)),
              onHandoff: () => guarded(() => _open(result, handoff: true)),
              // §7 row 7: "Continue here" is steal + sync-start, neither of
              // which v2 has. A future rebuild is export+import+move.
              onSteal:
                  widget.controller.capabilities.sessionSteal &&
                      _isElsewhere(_results[index])
                  ? () => guarded(() => unawaited(_steal(result)))
                  : null,
            ),
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
    for (final node in _rowFocus.values) {
      node.dispose();
    }
    super.dispose();
  }
}

class _GlobalSessionRow extends StatelessWidget {
  final ConnectionController controller;
  final GlobalSessionResult result;
  final bool opening;
  final bool stealing;
  final VoidCallback onTap;
  final VoidCallback onRelated;
  final VoidCallback onHandoff;

  /// Non-null only when the session lives outside the active location.
  final VoidCallback? onSteal;

  const _GlobalSessionRow({
    required this.controller,
    required this.result,
    required this.opening,
    this.stealing = false,
    required this.onTap,
    required this.onRelated,
    required this.onHandoff,
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
        if (!opening && !stealing) ...{
          const CustomSemanticsAction(label: 'Open related'): onRelated,
          const CustomSemanticsAction(label: 'Copy handoff'): onHandoff,
        },
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
          subtitle: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              SessionUnreadBadge(controller: controller, session: session),
              Text(details, maxLines: 3, overflow: TextOverflow.ellipsis),
            ],
          ),
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
                    if (value == 'related') onRelated();
                    if (value == 'handoff') onHandoff();
                  },
                  itemBuilder: (_) => [
                    const PopupMenuItem(value: 'open', child: Text('Open')),
                    PopupMenuItem(
                      value: 'related',
                      child: Text(
                        lookupAppLocalizations(
                          Localizations.localeOf(context),
                        ).sessionOpenRelated,
                      ),
                    ),
                    PopupMenuItem(
                      value: 'handoff',
                      child: Text(
                        lookupAppLocalizations(
                          Localizations.localeOf(context),
                        ).sessionCopyHandoff,
                      ),
                    ),
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
