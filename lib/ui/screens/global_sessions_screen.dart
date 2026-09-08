import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/semantics.dart' show CustomSemanticsAction;

import '../../api/product_repository.dart';
import '../../l10n/app_localizations.dart';
import '../../state/connection.dart';
import '../app_theme.dart';
import '../desktop/context_menu.dart';
import '../widgets/confirm_sheet.dart';
import '../widgets/product_states.dart';
import '../widgets/relative_time.dart';
import '../widgets/session_read_state.dart';
import '../widgets/session_title.dart';
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

  /// The loaded results grouped by working directory, newest first: the
  /// groups are ordered by their most recent session, and each group lists
  /// its sessions from newest to oldest. Rebuilt from [_results] on every
  /// build so a loaded page slots into the right group.
  List<_GlobalGroup> get _groups => _groupByDirectory(_results);

  /// A folder chip narrows the list to one group; null shows every group.
  String? _folderFilter;

  static int _recency(GlobalSessionResult result) =>
      result.session.time?.updated ?? result.session.time?.created ?? 0;

  static String _directoryOf(GlobalSessionResult result) {
    final value = (result.session.directory ?? result.projectDirectory)?.trim();
    return value == null || value.isEmpty
        ? ''
        : ConnectionController.normalizeDirectoryPath(value);
  }

  static List<_GlobalGroup> _groupByDirectory(
    List<GlobalSessionResult> results,
  ) {
    final indexed = results.indexed.toList()
      ..sort((a, b) {
        final byRecency = _recency(b.$2).compareTo(_recency(a.$2));
        return byRecency != 0 ? byRecency : a.$1.compareTo(b.$1);
      });
    final groups = <String, List<GlobalSessionResult>>{};
    for (final (_, result) in indexed) {
      groups.putIfAbsent(_directoryOf(result), () => []).add(result);
    }
    // Two folders can share a name (a project and its worktree, or two
    // checkouts). Those get their parent folder in the label so the chips
    // and card headers stay distinguishable.
    final labels = {
      for (final entry in groups.entries)
        entry.key: _GlobalSessionRow._projectLabel(entry.value.first),
    };
    final counts = <String, int>{};
    for (final label in labels.values) {
      counts[label] = (counts[label] ?? 0) + 1;
    }
    return [
      for (final entry in groups.entries)
        _GlobalGroup(
          directory: entry.key,
          label: (counts[labels[entry.key]] ?? 0) > 1
              ? _pathTail(entry.key, fallback: labels[entry.key]!)
              : labels[entry.key]!,
          results: entry.value,
        ),
    ];
  }

  /// The last two path segments, `parent/name`, for a label that would
  /// otherwise collide with another folder's.
  static String _pathTail(String directory, {required String fallback}) {
    final parts = directory
        .replaceAll('\\', '/')
        .split('/')
        .where((part) => part.isNotEmpty)
        .toList();
    if (parts.length < 2) return fallback;
    return '${parts[parts.length - 2]}/${parts.last}';
  }

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
    final theme = Theme.of(context);
    final muted = AppTheme.mutedOf(theme);
    final groups = _groups;
    final filter = _folderFilter;
    final visible = filter == null
        ? groups
        : groups.where((group) => group.directory == filter).toList();
    final loadedCount = _hasMore ? '${_results.length}+' : '${_results.length}';
    final shown = visible.fold<int>(
      0,
      (sum, group) => sum + group.results.length,
    );
    final summary = _results.isEmpty
        ? null
        : filter != null
        ? l10n.globalSessionsFilteredSummary(shown, loadedCount)
        : groups.length == 1
        ? l10n.globalSessionsSummaryOneFolder(loadedCount)
        : l10n.globalSessionsSummary(loadedCount, groups.length);

    return Scaffold(
      appBar: AppBar(title: Text(l10n.globalSessionsTitle)),
      body: Column(
        children: [
          // 1. Search first: the page exists to find one conversation among
          // every folder on the server.
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 4, 16, 4),
            child: TextField(
              key: const ValueKey('global-session-search'),
              controller: _search,
              textInputAction: TextInputAction.search,
              onChanged: _searchChanged,
              decoration: InputDecoration(
                labelText: l10n.globalSessionsSearchLabel,
                hintText: l10n.globalSessionsSearchHint,
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
          // 2. One scrolling strip of filters: archived, then a chip per
          // folder the loaded results came from. Folder chips narrow the
          // list on the phone without another request.
          SizedBox(
            height: 48,
            child: ListView(
              key: const ValueKey('global-session-filters'),
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: 16),
              children: [
                Center(
                  child: FilterChip(
                    key: const ValueKey('include-archived-sessions'),
                    selected: _includeArchived,
                    showCheckmark: false,
                    avatar: Icon(
                      _includeArchived
                          ? Icons.inventory_2_rounded
                          : Icons.inventory_2_outlined,
                      size: 16,
                    ),
                    label: Text(
                      MediaQuery.textScalerOf(context).scale(14) > 20
                          ? l10n.globalSessionsArchivedShort
                          : l10n.globalSessionsIncludeArchived,
                    ),
                    onSelected: (selected) {
                      setState(() => _includeArchived = selected);
                      unawaited(_reload());
                    },
                  ),
                ),
                if (groups.length > 1) ...[
                  const SizedBox(width: 8),
                  Center(
                    child: ChoiceChip(
                      key: const ValueKey('global-session-folder-all'),
                      selected: filter == null,
                      showCheckmark: false,
                      label: Text(l10n.globalSessionsAllFolders),
                      onSelected: (_) => setState(() => _folderFilter = null),
                    ),
                  ),
                  for (final group in groups) ...[
                    const SizedBox(width: 8),
                    Center(
                      child: ChoiceChip(
                        key: ValueKey(
                          'global-session-folder-${group.directory}',
                        ),
                        selected: filter == group.directory,
                        showCheckmark: false,
                        avatar: const Icon(Icons.folder_rounded, size: 16),
                        label: Text(group.label),
                        onSelected: (_) => setState(
                          () => _folderFilter = filter == group.directory
                              ? null
                              : group.directory,
                        ),
                      ),
                    ),
                  ],
                ],
              ],
            ),
          ),
          if (summary != null)
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 4, 20, 6),
              child: Align(
                alignment: AlignmentDirectional.centerStart,
                child: Text(
                  summary,
                  key: const ValueKey('global-session-count'),
                  style: theme.textTheme.labelMedium?.copyWith(color: muted),
                ),
              ),
            ),
          Expanded(child: _content(visible, l10n)),
        ],
      ),
    );
  }

  Widget _content(List<_GlobalGroup> groups, AppLocalizations l10n) {
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
        title: query.isEmpty
            ? l10n.globalSessionsEmptyTitle
            : l10n.globalSessionsNoMatchTitle,
        message: query.isEmpty
            ? l10n.globalSessionsEmptyMessage
            : l10n.globalSessionsNoMatchMessage,
        actionLabel: query.isEmpty
            ? l10n.globalSessionsRefresh
            : l10n.commonClearSearch,
        onAction: query.isEmpty
            ? _reload
            : () {
                _search.clear();
                setState(() {});
                unawaited(_reload());
              },
      );
    }

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

    // 3. One card per working directory, newest folder first, each row a
    // conversation newest first. The card header carries the folder, so
    // rows keep only what differs between them.
    return RefreshIndicator(
      onRefresh: _reload,
      child: ListView(
        key: const PageStorageKey('global-sessions-list'),
        controller: _scroll,
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(12, 4, 12, 24),
        children: [
          for (final group in groups)
            _FolderCard(
              key: ValueKey('global-session-group-${group.directory}'),
              group: group,
              unknownLocation: l10n.globalSessionsUnknownLocation,
              rows: [
                for (final result in group.results)
                  Focus(
                    focusNode: _rowFocus.putIfAbsent(
                      result.session.id,
                      FocusNode.new,
                    ),
                    child: _GlobalSessionRow(
                      controller: widget.controller,
                      result: result,
                      opening: _openingSessionID == result.session.id,
                      stealing: _stealingSessionID == result.session.id,
                      onTap: () => guarded(() => _open(result)),
                      onRelated: () =>
                          guarded(() => _open(result, related: true)),
                      onHandoff: () =>
                          guarded(() => _open(result, handoff: true)),
                      // §7 row 7: "Continue here" is steal + sync-start,
                      // neither of which v2 has. A future rebuild is
                      // export+import+move.
                      onSteal:
                          widget.controller.capabilities.sessionSteal &&
                              _isElsewhere(result)
                          ? () => guarded(() => unawaited(_steal(result)))
                          : null,
                    ),
                  ),
              ],
            ),
          _footer(l10n),
        ],
      ),
    );
  }

  /// The paging tail: a load error with retry, a spinner while a page
  /// loads, or the explicit Load more control. Empty once everything is in.
  Widget _footer(AppLocalizations l10n) {
    if (_error != null) {
      return ListTile(
        leading: Icon(
          Icons.error_outline_rounded,
          color: Theme.of(context).colorScheme.error,
        ),
        title: Text(
          _errorWasRefresh
              ? l10n.globalSessionsRefreshFailed
              : l10n.globalSessionsLoadMoreFailed,
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
    if (_loading || _loadingMore) {
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
    if (_hasMore) {
      return Padding(
        padding: const EdgeInsets.fromLTRB(4, 12, 4, 0),
        child: OutlinedButton(
          key: const ValueKey('global-sessions-load-more'),
          onPressed: _loadMore,
          child: Text(l10n.globalSessionsLoadMore),
        ),
      );
    }
    return const SizedBox.shrink();
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

/// The loaded results for one working directory, newest session first.
class _GlobalGroup {
  const _GlobalGroup({
    required this.directory,
    required this.label,
    required this.results,
  });

  /// Normalized working directory; empty when the server reported none.
  final String directory;
  final String label;
  final List<GlobalSessionResult> results;
}

/// One folder's conversations: a header naming the folder, then its rows
/// with no leading icons or hard dividers, so the eye reads title, age and
/// state and nothing else.
class _FolderCard extends StatelessWidget {
  const _FolderCard({
    super.key,
    required this.group,
    required this.unknownLocation,
    required this.rows,
  });

  final _GlobalGroup group;
  final String unknownLocation;
  final List<Widget> rows;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final muted = AppTheme.mutedOf(theme);
    final path = group.directory.isEmpty ? unknownLocation : group.directory;
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: theme.colorScheme.surfaceContainerLow,
          borderRadius: BorderRadius.circular(20),
          border: Border.all(color: theme.colorScheme.outlineVariant),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Semantics(
              header: true,
              child: Padding(
                padding: const EdgeInsets.fromLTRB(14, 14, 14, 6),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Container(
                      width: 34,
                      height: 34,
                      decoration: BoxDecoration(
                        color: theme.colorScheme.primaryContainer,
                        borderRadius: BorderRadius.circular(10),
                      ),
                      child: Icon(
                        Icons.folder_rounded,
                        size: 18,
                        color: theme.colorScheme.onPrimaryContainer,
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            group.label,
                            style: theme.textTheme.titleMedium,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                          Text(
                            path,
                            style: theme.textTheme.bodySmall?.copyWith(
                              color: muted,
                            ),
                            maxLines: 2,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(width: 8),
                    Padding(
                      padding: const EdgeInsets.only(top: 6),
                      child: Text(
                        '${group.results.length}',
                        style: theme.textTheme.labelMedium?.copyWith(
                          color: muted,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
            ...rows,
            const SizedBox(height: 6),
          ],
        ),
      ),
    );
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
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final theme = Theme.of(context);
    final muted = AppTheme.mutedOf(theme);
    final session = result.session;
    final title = presentedSessionTitle(
      session,
      fallback: l10n.globalSessionsUntitled,
    );
    final project = _projectLabel(result);
    final working = controller.busySessions.contains(session.id);
    final updated = session.time?.updated ?? session.time?.created;
    final details = <String>[
      if (working) l10n.globalSessionsWorking,
      if (updated != null && updated > 0) relativeTimeLabel(updated),
      if (session.path?.trim().isNotEmpty == true) session.path!.trim(),
    ].join(' · ');
    // A screen reader hears the folder on every row; the card header
    // carries it visually.
    final spoken = [
      project,
      details,
      if (session.archived) l10n.globalSessionsArchivedShort,
    ].where((value) => value.isNotEmpty).join(' · ');
    final busy = opening || stealing;

    final row = Semantics(
      button: true,
      label: 'Open $title. $spoken',
      onTap: busy ? null : onTap,
      customSemanticsActions: {
        if (!busy) ...{
          CustomSemanticsAction(label: l10n.sessionOpenRelated): onRelated,
          CustomSemanticsAction(label: l10n.sessionCopyHandoff): onHandoff,
        },
        if (onSteal != null && !busy)
          CustomSemanticsAction(label: l10n.globalSessionsContinueHere):
              onSteal!,
      },
      child: ExcludeSemantics(
        child: InkWell(
          key: ValueKey('global-session-${session.id}'),
          onTap: busy ? null : onTap,
          borderRadius: BorderRadius.circular(12),
          child: Padding(
            padding: const EdgeInsetsDirectional.fromSTEB(14, 8, 4, 8),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.center,
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          if (working)
                            Padding(
                              padding: const EdgeInsetsDirectional.only(end: 8),
                              child: Container(
                                width: 8,
                                height: 8,
                                decoration: BoxDecoration(
                                  color: theme.colorScheme.primary,
                                  shape: BoxShape.circle,
                                ),
                              ),
                            ),
                          Expanded(
                            child: Text(
                              title,
                              style: theme.textTheme.bodyLarge,
                              maxLines: 2,
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                          if (session.archived)
                            Padding(
                              padding: const EdgeInsetsDirectional.only(
                                start: 8,
                              ),
                              child: _Pill(l10n.globalSessionsArchivedShort),
                            ),
                        ],
                      ),
                      SessionUnreadBadge(
                        controller: controller,
                        session: session,
                      ),
                      if (details.isNotEmpty)
                        Text(
                          details,
                          style: theme.textTheme.bodySmall?.copyWith(
                            color: muted,
                          ),
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                        ),
                    ],
                  ),
                ),
                // One overflow menu instead of a per-row icon: Open is the
                // tap, Continue here rides in the menu (and, on desktop,
                // right click).
                if (busy)
                  const Padding(
                    padding: EdgeInsets.all(12),
                    child: SizedBox.square(
                      dimension: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    ),
                  )
                else
                  PopupMenuButton<String>(
                    key: ValueKey('global-session-actions-${session.id}'),
                    tooltip: l10n.globalSessionsActions,
                    iconColor: muted,
                    onSelected: (value) {
                      if (value == 'open') onTap();
                      if (value == 'steal') onSteal?.call();
                      if (value == 'related') onRelated();
                      if (value == 'handoff') onHandoff();
                    },
                    itemBuilder: (_) => [
                      PopupMenuItem(
                        value: 'open',
                        child: Text(l10n.globalSessionsOpen),
                      ),
                      PopupMenuItem(
                        value: 'related',
                        child: Text(l10n.sessionOpenRelated),
                      ),
                      PopupMenuItem(
                        value: 'handoff',
                        child: Text(l10n.sessionCopyHandoff),
                      ),
                      if (onSteal != null)
                        PopupMenuItem(
                          key: ValueKey('steal-session-${session.id}'),
                          value: 'steal',
                          child: Text(l10n.globalSessionsContinueHere),
                        ),
                    ],
                  ),
              ],
            ),
          ),
        ),
      ),
    );
    // Steal is offered only where it is genuinely possible, matching the
    // menu's own gate. Off desktop this wrapper is a pass-through.
    return ContextMenuRegion(
      actions: () => [
        if (!busy)
          ContextMenuAction(
            menuKey: const ValueKey('global-session-menu-open'),
            label: l10n.globalSessionsOpen,
            icon: Icons.open_in_new_rounded,
            onSelected: onTap,
          ),
        if (onSteal != null && !busy)
          ContextMenuAction(
            menuKey: const ValueKey('global-session-menu-steal'),
            label: l10n.globalSessionsContinueHere,
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
}

/// A small outlined tag, used for the archived state.
class _Pill extends StatelessWidget {
  const _Pill(this.text);

  final String text;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: theme.colorScheme.outlineVariant),
      ),
      child: Text(
        text,
        style: theme.textTheme.labelSmall?.copyWith(
          color: AppTheme.mutedOf(theme),
        ),
      ),
    );
  }
}
