import 'dart:async';

import 'package:flutter/material.dart';

import '../../domain/models.dart';
import '../../l10n/app_localizations.dart';
import '../../state/interaction_defaults.dart';
import '../../state/review_handoff.dart';
import '../app_iconography.dart';
import '../app_theme.dart' show AppStatusTone;
import '../kit/kit_bidi.dart';
import '../kit/kit_buttons.dart' show KitAction, KitActionBlock;
import '../kit/kit_choice_list.dart';
import '../kit/kit_code_block.dart';
import '../kit/kit_copy.dart';
import '../kit/kit_diff_view.dart';
import '../kit/kit_field.dart';
import '../kit/kit_layout.dart';
import '../kit/kit_menu.dart';
import '../kit/kit_notice.dart';
import '../kit/kit_page_route.dart';
import '../kit/kit_screen.dart';
import '../kit/kit_segmented.dart';
import '../kit/kit_sheet.dart';
import '../kit/kit_since.dart';
import '../kit/kit_state_view.dart';
import '../kit/kit_text.dart';
import '../kit/kit_tokens.dart';
import '../kit/kit_top_bar.dart';
import '../kit/kit_undo.dart';
import '../kit/motion/kit_refresh.dart';
import '../widgets/default_notices.dart';
import '../widgets/product_states.dart' show productErrorText;
import '../widgets/reader_preferences.dart';

typedef ReviewDiffLoader = Future<List<FileDiff>> Function();

/// Kept for callers that still name it. The diff now picks unified or side
/// by side from its own width (KitDiffView), so the page has no mode
/// control of its own.
enum ReviewDiffMode { unified, split }

enum ReviewDiffScope { session, workingTree, branch }

bool _sameReviewDiff(FileDiff? a, FileDiff? b) =>
    a != null &&
    b != null &&
    _normalizedPath(a.file) == _normalizedPath(b.file) &&
    a.patch == b.patch &&
    a.before == b.before &&
    a.after == b.after &&
    a.status == b.status;

/// Review changes (map page `review-workspace`, proposal fix): one
/// [KitDiffView] with each file a folded row and the first open (no hunk
/// navigator, vision gaps 2026-10-21), the view picker ([KitSegmented])
/// when the server offers more than one, and the comment sheet (`review-comment-sheet`),
/// whose text is kept while the person types, swipes it away and opens it
/// again (DATA-1, the draft carry of P7.1).
class ReviewWorkspace extends StatefulWidget {
  const ReviewWorkspace({
    super.key,
    this.loadDiffs,
    this.loadWorkingTreeDiffs,
    this.loadBranchDiffs,
    this.initialScope,
    this.initialFile,
    this.handoff,
    this.cacheKey,
    this.profileId,
    this.onUndoAll,
    this.summary,
  }) : assert(
         loadDiffs != null ||
             loadWorkingTreeDiffs != null ||
             loadBranchDiffs != null,
         'At least one review diff loader is required.',
       );

  final ReviewDiffLoader? loadDiffs;
  final ReviewDiffLoader? loadWorkingTreeDiffs;
  final ReviewDiffLoader? loadBranchDiffs;

  /// The view to open, when the caller means one. Without it the page picks
  /// the view that has changes (P6.6 "defaults instead of questions"): this
  /// conversation's first, then uncommitted, then the whole branch, and
  /// says so once when that is not the first view.
  final ReviewDiffScope? initialScope;
  final String? initialFile;

  /// When present, review findings stage as structured references on the
  /// originating session's composer. When absent — Review opened without a
  /// chat behind it — the workspace keeps its older behaviour of returning
  /// the formatted comment to the caller, which falls back to the clipboard.
  final ReviewHandoffSession? handoff;

  /// Names what is being reviewed (server, project, conversation). With it,
  /// reopening shows the last result at once while a fresh one loads, and
  /// the other views load behind the first so switching is instant. A diff
  /// is a git run on the server and, on a phone, a slow one.
  final String? cacheKey;

  /// The saved server's profile id. With it, a comment being typed is also
  /// kept on disk (`oc.draft.<target>.<profileId>`, swept when the profile
  /// is deleted) and survives the app closing; without it the comment is
  /// kept in memory until the app closes.
  final String? profileId;

  /// Goes back to before the agent's changes, when the caller can do it
  /// (a staged revert of this conversation on a server that has one). Null
  /// keeps "Undo all" off the screen: it is never offered where nothing
  /// would happen. The screen confirms first, naming the files.
  final Future<void> Function()? onUndoAll;

  /// One plain sentence on what changed, first on the screen: the agent's
  /// own words for its last turn, or the conversation's title. Null: the
  /// counts line alone ("3 files · +42 −8").
  final String? summary;

  /// Recent results by [cacheKey] and view. Small, in memory only: the
  /// fresh read always follows, so a stale entry is only ever a head start.
  static final _cache = <String, List<FileDiff>>{};
  static const _cacheLimit = 12;

  static void _remember(String key, List<FileDiff> diffs) {
    _cache.remove(key);
    _cache[key] = diffs;
    while (_cache.length > _cacheLimit) {
      _cache.remove(_cache.keys.first);
    }
  }

  /// Comments being typed, by what they are about, until they are added to
  /// the prompt. In memory only; the on-disk copy is the [KitDraft].
  static final _drafts = <String, String>{};
  static const _draftLimit = 24;

  static void _keepDraft(String key, String text) {
    _drafts.remove(key);
    if (text.trim().isEmpty) return;
    _drafts[key] = text;
    while (_drafts.length > _draftLimit) {
      _drafts.remove(_drafts.keys.first);
    }
  }

  @visibleForTesting
  static void clearCache() {
    _cache.clear();
    _drafts.clear();
  }

  @override
  State<ReviewWorkspace> createState() => _ReviewWorkspaceState();
}

class _ReviewWorkspaceState extends State<ReviewWorkspace> {
  List<FileDiff>? _diffs;
  Object? _error;

  /// Files whose diff was on screen this session, per scope — the GitHub
  /// "Viewed" pattern, session-local only.
  final Map<String, FileDiff> _viewedFiles = {};

  /// The file the diff shows now, as the diff's header last drew it.
  String? _shownPath;
  late ReviewDiffScope _scope;

  /// The view is still the page's own pick: no caller named one and the
  /// person has not chosen one. Only then does an empty first view give
  /// way to one with changes, and only on the first load.
  late bool _scopeIsDefault;

  /// Said once, when the page opened a view other than the first because
  /// only that one has changes.
  String? _defaultNotice;
  String? _pendingInitialFile;
  int _loadGeneration = 0;
  bool _refreshing = false;
  DateTime? _loadStarted;

  /// Lines are selected in the diff, so its action bar holds the screen's
  /// one primary and the question steps aside.
  bool _selecting = false;
  final _diffHostKey = GlobalKey();

  /// A staging result that has no Undo (already there, prompt full).
  String? _notice;

  /// One controller per comment target while this page is open; the text
  /// also lives in [ReviewWorkspace._drafts] for the next time it opens.
  final Map<String, TextEditingController> _commentControllers = {};

  AppLocalizations get _l10n => readerL10n(context);

  @override
  void initState() {
    super.initState();
    final scopes = _availableScopes;
    final wanted = widget.initialScope;
    _scope = wanted != null && scopes.contains(wanted) ? wanted : scopes.first;
    _scopeIsDefault = wanted == null && scopes.length > 1;
    _pendingInitialFile = widget.initialFile;
    _load();
  }

  @override
  void dispose() {
    for (final controller in _commentControllers.values) {
      controller.dispose();
    }
    super.dispose();
  }

  // -------------------------------------------------------------------------
  // Loading

  Future<void> _load() async {
    final generation = ++_loadGeneration;
    setState(() {
      _refreshing = true;
      if (_diffs == null) {
        _error = null;
        _loadStarted = DateTime.now();
      }
    });
    final scope = _scope;
    final cached = _cacheKeyFor(scope) == null
        ? null
        : ReviewWorkspace._cache[_cacheKeyFor(scope)];
    if (_diffs == null && cached != null) _apply(cached);
    try {
      var diffs = await _loaderFor(scope)();
      if (!mounted || generation != _loadGeneration) return;
      if (_cacheKeyFor(scope) case final key?) {
        ReviewWorkspace._remember(key, diffs);
      }
      var shown = scope;
      if (_scopeIsDefault) {
        _scopeIsDefault = false;
        if (diffs.isEmpty) {
          final picked = await _viewWithChanges(scope);
          if (!mounted || generation != _loadGeneration) return;
          if (picked != null) {
            shown = _fromDefault(picked.choice.value!);
            diffs = picked.diffs;
            setState(() => _scope = shown);
            unawaited(_announceDefault(picked.choice));
          }
        }
      }
      _apply(diffs);
      unawaited(_prefetchOthers(shown));
    } catch (error) {
      if (mounted && generation == _loadGeneration) {
        setState(() => _error = error);
      }
    } finally {
      if (mounted && generation == _loadGeneration) {
        setState(() => _refreshing = false);
      }
    }
  }

  String? _cacheKeyFor(ReviewDiffScope scope) =>
      widget.cacheKey == null ? null : '${widget.cacheKey}|${scope.name}';

  /// The first view with changes when [empty] has none, in the order
  /// [InteractionDefaults.review] prefers. Each view is read at most once
  /// (the prefetch would read it anyway); one that fails counts as unknown
  /// and is never picked.
  Future<({DefaultChoice<DefaultReviewScope> choice, List<FileDiff> diffs})?>
  _viewWithChanges(ReviewDiffScope empty) async {
    final counts = <DefaultReviewScope, int?>{_asDefault(empty): 0};
    final loaded = <ReviewDiffScope, List<FileDiff>>{};
    for (final scope in _availableScopes) {
      if (scope == empty) continue;
      try {
        final diffs = await _loaderFor(scope)();
        if (_cacheKeyFor(scope) case final key?) {
          ReviewWorkspace._remember(key, diffs);
        }
        loaded[scope] = diffs;
        counts[_asDefault(scope)] = diffs.length;
        // The preferred order is the loaders' order: stop at the first.
        if (diffs.isNotEmpty) break;
      } catch (_) {
        counts[_asDefault(scope)] = null;
      }
      if (!mounted) return null;
    }
    final choice = InteractionDefaults.review(counts);
    final value = choice.value;
    if (value == null) return null;
    final scope = _fromDefault(value);
    return (choice: choice, diffs: loaded[scope]!);
  }

  static DefaultReviewScope _asDefault(ReviewDiffScope scope) =>
      switch (scope) {
        ReviewDiffScope.session => DefaultReviewScope.session,
        ReviewDiffScope.workingTree => DefaultReviewScope.workingTree,
        ReviewDiffScope.branch => DefaultReviewScope.branch,
      };

  static ReviewDiffScope _fromDefault(DefaultReviewScope scope) =>
      switch (scope) {
        DefaultReviewScope.session => ReviewDiffScope.session,
        DefaultReviewScope.workingTree => ReviewDiffScope.workingTree,
        DefaultReviewScope.branch => ReviewDiffScope.branch,
      };

  Future<void> _announceDefault(
    DefaultChoice<DefaultReviewScope> choice,
  ) async {
    final said = await claimDefaultNotice(
      kind: DefaultKind.review,
      choice: choice,
      profileId: widget.profileId,
    );
    if (said == null || !mounted) return;
    final scope = _fromDefault(choice.value!);
    setState(
      () => _defaultNotice = _l10n.defaultReviewScopeNotice(
        _ReviewScopePicker._scopeLabel(_l10n, scope),
      ),
    );
  }

  /// Loads the views not on screen, one at a time, into the cache.
  Future<void> _prefetchOthers(ReviewDiffScope shown) async {
    for (final scope in _availableScopes) {
      if (scope == shown || !mounted) continue;
      final key = _cacheKeyFor(scope);
      if (key == null || ReviewWorkspace._cache.containsKey(key)) continue;
      try {
        final diffs = await _loaderFor(scope)();
        if (mounted) ReviewWorkspace._remember(key, diffs);
      } catch (_) {
        // Loading it when opened reports the failure; ahead of time, none.
      }
    }
  }

  /// Shows [diffs], keeping the file being read when it is still there.
  void _apply(List<FileDiff> diffs) {
    setState(() {
      _diffs = diffs;
      _error = null;
      final wanted = _pendingInitialFile ?? _shownPath;
      final index = wanted == null
          ? -1
          : diffs.indexWhere(
              (diff) => _normalizedPath(diff.file) == _normalizedPath(wanted),
            );
      final shown = diffs.isEmpty ? null : diffs[index < 0 ? 0 : index];
      _shownPath = shown?.file;
      _pendingInitialFile = null;
      _viewedFiles.removeWhere(
        (key, viewed) =>
            key.startsWith('$_scope:') &&
            !diffs.any((diff) => _sameReviewDiff(viewed, diff)),
      );
      // The diff opens on this file: it is on screen from the first frame.
      if (shown != null) _viewedFiles[_viewedKey(shown.file)] = shown;
    });
  }

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
      _scopeIsDefault = false;
      _defaultNotice = null;
      _scope = scope;
      _diffs = null;
      _error = null;
      _notice = null;
      _pendingInitialFile = null;
      _shownPath = null;
    });
    _load();
  }

  // -------------------------------------------------------------------------
  // Viewed

  String _viewedKey(String path) => '$_scope:${_normalizedPath(path)}';

  bool _isViewed(FileDiff diff) =>
      _sameReviewDiff(_viewedFiles[_viewedKey(diff.file)], diff);

  /// The diff moved to [diff] (KitDiffView.onFileChanged): that file is on
  /// screen, so it counts as viewed.
  void _observeShown(FileDiff diff) {
    if (!mounted || !(_diffs?.contains(diff) ?? false)) return;
    if (_shownPath == diff.file && _isViewed(diff)) return;
    setState(() {
      _shownPath = diff.file;
      _viewedFiles[_viewedKey(diff.file)] = diff;
    });
  }

  FileDiff? _diffFor(KitDiffFile file) {
    for (final diff in _diffs ?? const <FileDiff>[]) {
      if (diff.file == file.path) return diff;
    }
    return null;
  }

  // -------------------------------------------------------------------------
  // Build

  @override
  Widget build(BuildContext context) {
    final handoff = widget.handoff;
    if (handoff == null) return _screen(context, 0);
    return ListenableBuilder(
      listenable: handoff.store,
      builder: (context, _) => _screen(context, handoff.references.length),
    );
  }

  Widget _screen(BuildContext context, int staged) {
    final l10n = _l10n;
    final tokens = KitTokens.of(context);
    final diffs = _diffs;
    final viewed = diffs?.where(_isViewed).length ?? 0;
    final allViewed =
        diffs != null && diffs.isNotEmpty && viewed == diffs.length;
    final subtitle = [
      if (staged > 0) l10n.readerUiOnPrompt(staged),
    ].join(' · ');
    Widget onRails(Widget child) => Padding(
      padding: EdgeInsetsDirectional.only(
        start: tokens.gutter,
        top: tokens.space2,
        end: tokens.gutter,
      ),
      child: child,
    );
    final error = _error;
    final notice = _notice;
    return KitScreen(
      key: const Key('review-workspace'),
      topBar: KitTopBar(
        title: l10n.demoReviewChanges,
        subtitle: subtitle.isEmpty ? null : subtitle,
        // One reload at a time: until a diff shows, the body's own Try
        // again (error, slow wait) is the way to load again.
        actions: [
          if (diffs != null)
            KitAction(
              key: const Key('review-refresh'),
              label: l10n.readerUiRefreshChanges,
              icon: AppIconography.retry,
              working: _refreshing,
              onPressed: _load,
            ),
        ],
      ),
      header: [
        if (diffs != null && diffs.isNotEmpty) _summary(diffs, tokens, l10n),
        if (_availableScopes.length > 1)
          _ReviewScopePicker(
            scopes: _availableScopes,
            selected: _scope,
            onSelected: _selectScope,
            // What the view covers is said only when it has nothing to
            // show; with files listed the files say it.
            explain: diffs != null && diffs.isEmpty,
          ),
        if (_defaultNotice case final said?)
          onRails(
            KitNotice(
              key: const Key('review-default-scope'),
              message: said,
              dismissLabel: l10n.workspaceDismissNotice,
              onDismiss: () => setState(() => _defaultNotice = null),
            ),
          ),
        if (diffs != null && error != null)
          onRails(
            KitNotice(
              key: const Key('review-refresh-failed'),
              tone: AppStatusTone.failure,
              title: l10n.reviewWorkspaceRefreshFailed,
              message: productErrorText(error, l10n: l10n),
              actions: [KitAction(label: l10n.kitTryAgain, onPressed: _load)],
            ),
          ),
        if (notice != null)
          onRails(
            KitNotice(
              key: const Key('review-staged-notice'),
              message: notice,
              onDismiss: () => setState(() => _notice = null),
            ),
          ),
        if (allViewed && staged > 0)
          onRails(
            KitNotice(
              key: const Key('review-all-viewed'),
              tone: AppStatusTone.ok,
              title: l10n.reviewWorkspaceAllViewedTitle,
              message: l10n.reviewWorkspaceAllViewedMessage(staged),
              actions: [
                KitAction(
                  key: const Key('review-back-to-chat'),
                  label: l10n.reviewWorkspaceBackToChat,
                  onPressed: () => Navigator.of(context).maybePop(),
                ),
              ],
            ),
          ),
      ],
      loading: _refreshing && diffs != null,
      loadingLabel: l10n.readerUiRefreshChanges,
      bottom: diffs == null || diffs.isEmpty || _selecting
          ? null
          : _question(l10n, staged),
      body: _body(context, l10n),
    );
  }

  /// One sentence on top, and the honest fact that the changes are already
  /// in the project.
  Widget _summary(
    List<FileDiff> diffs,
    KitTokens tokens,
    AppLocalizations l10n,
  ) {
    // At large text the room goes to the changes and the answers; the top
    // bar and the file header still say what changed.
    if (MediaQuery.textScalerOf(context).scale(10) >= 15) {
      return const SizedBox.shrink(key: Key('review-summary'));
    }
    var added = 0, removed = 0;
    for (final diff in diffs) {
      final file = _kitFileOf(diff);
      added += file.added;
      removed += file.removed;
    }
    final sentence = widget.summary?.trim();
    final counts = l10n.reviewSummary(diffs.length, added, removed);
    return Padding(
      key: const Key('review-summary'),
      padding: EdgeInsetsDirectional.only(
        start: tokens.gutter,
        top: tokens.space2,
        end: tokens.gutter,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // The plain sentence first, the counts under it; with no sentence
          // the counts lead.
          if (sentence != null && sentence.isNotEmpty) ...[
            KitText(
              KitBidi.auto(sentence),
              key: const Key('review-summary-sentence'),
              role: KitTextRole.headline,
              maxLines: 3,
              overflow: TextOverflow.ellipsis,
            ),
            KitText(counts, role: KitTextRole.secondary),
          ] else
            KitText(counts, role: KitTextRole.headline),
          KitText(l10n.reviewAlreadyApplied, role: KitTextRole.secondary),
        ],
      ),
    );
  }

  /// "Looks good" or "Undo all"; "Send back" is a note in the chat's box.
  Widget _question(AppLocalizations l10n, int staged) {
    final sendBack = KitAction(
      key: const Key('review-send-back'),
      label: l10n.reviewSendBackWithNote,
      onPressed: _sendBack,
    );
    return KitActionBlock(
      key: const Key('review-question'),
      primary: KitAction(
        key: const Key('review-looks-good'),
        label: l10n.reviewLooksGood,
        onPressed: () => Navigator.of(context).maybePop(),
      ),
      // Send back is a full-width button of its own; with a revert to offer,
      // Undo all takes that place and Send back follows as a text action.
      secondary: widget.onUndoAll == null
          ? sendBack
          : KitAction(
              key: const Key('review-undo-all'),
              label: l10n.reviewUndoAll,
              onPressed: _undoAll,
            ),
      tertiary: [if (widget.onUndoAll != null) sendBack],
    );
  }

  Future<void> _undoAll() async {
    final undo = widget.onUndoAll;
    final l10n = _l10n;
    if (undo == null) return;
    final count = _diffs?.length ?? 0;
    final confirmed = await showKitConfirm(
      context,
      sheetKey: const Key('review-undo-confirm'),
      title: l10n.reviewUndoTitle(count),
      body: l10n.reviewUndoBody,
      confirmLabel: l10n.reviewUndoAll,
      action: undo,
    );
    if (!mounted || !confirmed) return;
    await Navigator.of(context).maybePop();
  }

  /// Notes already on the prompt go back with the person; otherwise the
  /// note is written here, for the file on screen, and lands in the chat's
  /// box with the change referenced.
  Future<void> _sendBack() async {
    if (widget.handoff != null && widget.handoff!.references.isNotEmpty) {
      await Navigator.of(context).maybePop();
      return;
    }
    final diffs = _diffs;
    if (diffs == null || diffs.isEmpty) return;
    final shown = diffs.firstWhere(
      (diff) => diff.file == _shownPath,
      orElse: () => diffs.first,
    );
    final staged = await _openCommentComposer(_kitFileOf(shown), null);
    if (staged && mounted) await Navigator.of(context).maybePop();
  }

  Widget _body(BuildContext context, AppLocalizations l10n) {
    final diffs = _diffs;
    final error = _error;
    if (diffs == null) {
      if (error != null) {
        return KitStateView.error(
          key: const Key('review-error'),
          title: l10n.kitDiffLoadFailed,
          body: productErrorText(error, l10n: l10n),
          error: error,
          retry: KitAction(label: l10n.kitTryAgain, onPressed: _load),
        );
      }
      return KitSince(
        since: _loadStarted,
        builder: (context, wait) => wait.isSlow
            ? KitStateView(
                key: const Key('review-slow'),
                icon: AppIconography.review,
                tone: AppStatusTone.progress,
                title: l10n.reviewWorkspaceSlowTitle,
                body: l10n.reviewWorkspaceSlowBody,
                tertiary: [
                  KitAction(
                    key: const Key('review-slow-retry'),
                    label: l10n.kitTryAgain,
                    onPressed: _load,
                  ),
                ],
              )
            : const KitDiffView(
                key: Key('review-loading'),
                files: [],
                loading: true,
                keyPrefix: 'review',
              ),
      );
    }
    if (diffs.isEmpty) {
      return KitRefresh(
        onRefresh: _load,
        child: CustomScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          slivers: [
            SliverFillRemaining(
              child: KitStateView(
                key: const Key('review-empty'),
                icon: AppIconography.review,
                // One sentence for every scope: the old copy named the
                // backend and said "in this conversation", which is wrong
                // for the project's working tree.
                title: l10n.emptyTeachChangesTitle,
                body: l10n.emptyTeachChangesMessage,
              ),
            ),
          ],
        ),
      );
    }
    final shown = _shownPath == null
        ? 0
        : diffs.indexWhere((diff) => diff.file == _shownPath);
    final preferences = ReaderPreferencesScope.maybeOf(context);
    // The question below steps aside while lines are selected, which changes
    // the screen's layout wrapper above the body; the global key carries the
    // diff (and the selection it holds) across that change.
    return KeyedSubtree(
      key: _diffHostKey,
      child: KitRefresh(
        onRefresh: _load,
        notificationPredicate: (notification) =>
            notification.metrics.axis == Axis.vertical,
        child: KitDiffView(
          // A different set or order of files starts the diff again on the
          // file being read; the same files keep its place, gaps and
          // selection across a refresh.
          key: ValueKey(
            'review-diff|${_scope.name}|'
            '${[for (final diff in diffs) diff.file].join('\n')}',
          ),
          keyPrefix: 'review',
          files: [for (final diff in diffs) _kitFileOf(diff)],
          // Folded rows, the first open; no hunk navigator, no viewed ticks.
          folded: true,
          initialFile: shown < 0 ? 0 : shown,
          readOnly: false,
          onFileChanged: (index) => _observeShown(diffs[index]),
          // The diff's own bar has the primary while lines are selected.
          onSelectingChanged: (selecting) {
            if (mounted && _selecting != selecting) {
              setState(() => _selecting = selecting);
            }
          },
          onComment: (selection) =>
              _openCommentComposer(selection.file, selection),
          onAddToPrompt: widget.handoff == null ? null : _stageSelection,
          fileActions: _fileActions,
          wrap: preferences?.value.wrapCode,
          onWrapChanged: preferences == null
              ? null
              : (wrap) => saveReaderPreferences(context, wrapCode: wrap),
        ),
      ),
    );
  }

  /// The file header's More menu, each entry naming the file it acts on.
  List<KitMenuItem> _fileActions(KitDiffFile file) {
    final l10n = _l10n;
    final name = KitBidi.ltr(_basename(file.path));
    return [
      KitMenuItem(
        key: const Key('review-file-comment'),
        label: l10n.reviewWorkspaceCommentOnFile(name),
        icon: AppIconography.chat,
        onSelected: () => _openCommentComposer(file, null),
      ),
      if (widget.handoff != null)
        KitMenuItem(
          key: const Key('review-add-file'),
          label: l10n.reviewWorkspaceAddFileToPrompt(name),
          icon: AppIconography.note,
          onSelected: () => _stageFile(file),
        ),
      ?_copyAction(context, l10n, file, keyPrefix: 'review'),
    ];
  }

  // -------------------------------------------------------------------------
  // Comments

  String _draftTarget(String path) =>
      'review.${widget.handoff?.sessionID ?? _scope.name}.'
      '${_normalizedPath(path)}';

  TextEditingController _commentController(String target) {
    final memory = '${widget.cacheKey ?? ''}|$target';
    return _commentControllers.putIfAbsent(target, () {
      final controller = TextEditingController(
        text: ReviewWorkspace._drafts[memory] ?? '',
      );
      controller.addListener(
        () => ReviewWorkspace._keepDraft(memory, controller.text),
      );
      return controller;
    });
  }

  /// True when the note went onto the chat's prompt (a handoff); false
  /// when it was dropped, or returned to the caller by popping this page.
  Future<bool> _openCommentComposer(
    KitDiffFile file,
    KitDiffSelection? selection,
  ) async {
    final l10n = _l10n;
    final diff = _diffFor(file);
    final wholeFile = selection == null;
    final selectionLabel = wholeFile
        ? l10n.readerUiEntireChange
        : _selectionLabel(l10n, selection);
    final target = _draftTarget(file.path);
    final controller = _commentController(target);
    final profileId = widget.profileId;
    final draft = profileId == null || profileId.isEmpty
        ? null
        : KitDraft(
            target: target,
            profileId: profileId,
            controller: controller,
          );
    final navigator = Navigator.of(context);
    KitAction addAction() {
      final text = controller.text.trim();
      return KitAction(
        key: const Key('review-add-to-prompt'),
        label: l10n.reviewWorkspaceAddComment,
        onPressed: text.isEmpty ? null : () => navigator.pop(text),
        disabledReason: text.isEmpty ? l10n.reviewWorkspaceCommentEmpty : null,
      );
    }

    final primary = ValueNotifier<KitAction?>(addAction());
    void sync() => primary.value = addAction();
    controller.addListener(sync);
    final comment = await showKitSheet<String>(
      context,
      sheetKey: const Key('review-comment-sheet'),
      title: l10n.reviewWorkspaceCommentOnFile(
        KitBidi.ltr(_basename(file.path)),
      ),
      subtitle: selectionLabel,
      icon: AppIconography.chat,
      primaryListenable: primary,
      draft: draft,
      body: (context) =>
          _ReviewCommentBody(controller: controller, quote: selection?.text),
    );
    controller.removeListener(sync);
    if (!mounted || comment == null || comment.trim().isEmpty) return false;
    // Used: nothing typed needs keeping any more.
    controller.clear();
    unawaited(draft?.clear());

    final handoff = widget.handoff;
    if (handoff != null) {
      // UX-103: the finding goes straight onto the originating session's
      // composer as a structured reference, and review stays open so a pass
      // can stage several notes before switching back to the chat.
      _stage(
        handoff,
        ReviewReference(
          id: handoff.nextID('review-comment'),
          kind: ReviewReferenceKind.comment,
          path: file.path,
          scope: _referenceScope,
          lineLabel: wholeFile ? null : selectionLabel,
          snippet: wholeFile ? null : selection.text,
          comment: comment.trim(),
          added: file.added,
          removed: file.removed,
          status: diff?.status,
        ),
      );
      return true;
    }

    final prompt = _reviewPrompt(
      l10n,
      path: file.path,
      comment: comment.trim(),
      snippet: wholeFile ? null : selection.text,
      selectionLabel: wholeFile ? null : selectionLabel,
    );
    navigator.pop(prompt);
    return false;
  }

  ReviewReferenceScope get _referenceScope => switch (_scope) {
    ReviewDiffScope.session => ReviewReferenceScope.session,
    ReviewDiffScope.workingTree => ReviewReferenceScope.workingTree,
    ReviewDiffScope.branch => ReviewReferenceScope.branch,
  };

  void _stageFile(KitDiffFile file) {
    final handoff = widget.handoff;
    if (handoff == null) return;
    _stage(
      handoff,
      ReviewReference(
        id: handoff.nextID('review-file'),
        kind: ReviewReferenceKind.changedFile,
        path: file.path,
        scope: _referenceScope,
        added: file.added,
        removed: file.removed,
        status: _diffFor(file)?.status,
      ),
    );
  }

  void _stageSelection(KitDiffSelection selection) {
    final handoff = widget.handoff;
    if (handoff == null) return;
    _stage(
      handoff,
      ReviewReference(
        id: handoff.nextID('review-selection'),
        kind: ReviewReferenceKind.selection,
        path: selection.file.path,
        scope: _referenceScope,
        lineLabel: _selectionLabel(_l10n, selection),
        snippet: selection.text,
        status: _diffFor(selection.file)?.status,
      ),
    );
  }

  /// Staged: done, with Undo (KIT-34). Already there or the prompt is full:
  /// nothing happened, said in a notice under the bar.
  void _stage(ReviewHandoffSession handoff, ReviewReference reference) {
    final outcome = handoff.stage(reference);
    if (!mounted) return;
    final l10n = _l10n;
    switch (outcome) {
      case ReviewStageOutcome.staged:
        setState(() => _notice = null);
        showKitUndo(
          context,
          key: const Key('review-staged-undo'),
          message: l10n.readerUiReferenceAdded(reference.label),
          onUndo: () => handoff.store.remove(handoff.sessionID, reference.id),
        );
      case ReviewStageOutcome.duplicate:
        setState(
          () => _notice = l10n.readerUiReferenceDuplicate(reference.label),
        );
      case ReviewStageOutcome.full:
        setState(
          () => _notice = l10n.readerUiReferenceFull(
            ReviewHandoffStore.maxPerSession,
          ),
        );
    }
  }

  static String _selectionLabel(
    AppLocalizations l10n,
    KitDiffSelection selection,
  ) {
    final range = selection.startLine == selection.endLine
        ? l10n.readerUiLine(selection.startLine)
        : l10n.readerUiLineRange(selection.startLine, selection.endLine);
    return switch (selection.side) {
      KitDiffSide.old => l10n.readerUiOldLines(range),
      KitDiffSide.current => l10n.readerUiNewLines(range),
    };
  }

  static String _reviewPrompt(
    AppLocalizations l10n, {
    required String path,
    required String comment,
    String? snippet,
    String? selectionLabel,
  }) {
    final out = StringBuffer(l10n.readerUiReviewPrompt(path));
    if (selectionLabel != null) out.write(' ($selectionLabel)');
    out.write(':\n\n$comment');
    if (snippet?.trim().isNotEmpty == true) {
      out.write('\n\n```diff\n${snippet!.trimRight()}\n```');
    }
    return out.toString();
  }
}

/// Which changes to show: 2–3 views. When the chosen view is empty its
/// meaning is said in words under the control (a tooltip is out of reach on
/// a phone).
class _ReviewScopePicker extends StatelessWidget {
  const _ReviewScopePicker({
    required this.scopes,
    required this.selected,
    required this.onSelected,
    required this.explain,
  });

  final List<ReviewDiffScope> scopes;
  final ReviewDiffScope selected;
  final ValueChanged<ReviewDiffScope> onSelected;

  /// Says under the control what the chosen view covers.
  final bool explain;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final l10n = readerL10n(context);
    return Padding(
      padding: EdgeInsetsDirectional.only(
        start: tokens.gutter,
        top: tokens.space2,
        end: tokens.gutter,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // On a phone three radio-like rows took 40% of the screen above
          // the diff, so the choice is one row that opens a picker; a wide
          // window has room for the segmented control.
          if (KitLayout.windowOf(context) == KitWindow.compact)
            KitPickerRow<ReviewDiffScope>(
              key: const Key('review-scope-picker'),
              title: l10n.reviewWorkspaceScopes,
              choices: [
                for (final scope in scopes)
                  KitChoice(value: scope, title: _scopeLabel(l10n, scope)),
              ],
              selected: selected,
              onSelected: onSelected,
            )
          else
            KitSegmented<ReviewDiffScope>(
              key: const Key('review-scope-picker'),
              semanticsLabel: l10n.reviewWorkspaceScopes,
              segments: [
                for (final scope in scopes)
                  KitSegment(value: scope, label: _scopeLabel(l10n, scope)),
              ],
              selected: selected,
              onChanged: onSelected,
            ),
          if (explain) ...[
            SizedBox(height: tokens.space1),
            KitText(
              _scopeDescription(l10n, selected),
              key: const Key('review-scope-hint'),
              role: KitTextRole.secondary,
            ),
          ],
        ],
      ),
    );
  }

  static String _scopeLabel(AppLocalizations l10n, ReviewDiffScope scope) =>
      switch (scope) {
        ReviewDiffScope.session => l10n.readerUiSession,
        ReviewDiffScope.workingTree => l10n.readerUiWorkingTree,
        ReviewDiffScope.branch => l10n.readerUiBranch,
      };

  static String _scopeDescription(
    AppLocalizations l10n,
    ReviewDiffScope scope,
  ) => switch (scope) {
    ReviewDiffScope.session => l10n.readerUiSessionScopeHint,
    ReviewDiffScope.workingTree => l10n.readerUiWorkingScopeHint,
    ReviewDiffScope.branch => l10n.readerUiBranchScopeHint,
  };
}

/// The comment sheet's body: the lines it is about, then the labelled
/// field. Its one action, "Add comment to prompt", is pinned by the sheet.
class _ReviewCommentBody extends StatelessWidget {
  const _ReviewCommentBody({required this.controller, this.quote});

  final TextEditingController controller;

  /// The selected lines, when the comment is about some of them.
  final String? quote;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final l10n = readerL10n(context);
    final quote = this.quote;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (quote != null && quote.trim().isNotEmpty) ...[
          KitCodeBlock(
            blockKey: const Key('review-comment-quote'),
            text: quote,
            kind: KitCodeKind.output,
            maxLines: 6,
            highlight: false,
            copyable: false,
            showWrapToggle: false,
          ),
          SizedBox(height: tokens.space3),
        ],
        KitField(
          fieldKey: const Key('review-comment-field'),
          label: l10n.reviewWorkspaceCommentLabel,
          controller: controller,
          kind: KitFieldKind.multiline,
          hint: l10n.reviewWorkspaceCommentHint,
          helper: l10n.reviewWorkspaceCommentHelper,
          autofocus: true,
        ),
      ],
    );
  }
}

// ---------------------------------------------------------------------------
// The read-only diff page

/// A file header's copy entry, verbatim (SEC-13: a diff is the person's own
/// content): the patch when there is one, otherwise the updated file.
KitMenuItem? _copyAction(
  BuildContext context,
  AppLocalizations l10n,
  KitDiffFile file, {
  required String keyPrefix,
}) {
  final patch = file.patch;
  final fullText = file.fullText;
  final (key, label, text) = patch != null && patch.isNotEmpty
      ? ('copy-patch', l10n.reviewCopyPatch, patch)
      : fullText != null && fullText.isNotEmpty
      ? ('copy-file', l10n.reviewCopyFile, fullText)
      : (null, null, null);
  if (key == null) return null;
  return KitMenuItem(
    key: Key('$keyPrefix-$key'),
    label: label!,
    icon: AppIconography.copy,
    onSelected: () => KitCopy.copy(context, text!, redact: false),
  );
}

/// The read-only diff page (map page `diff-view`; slice-P3.7a replaced
/// widgets/diff_view.dart with it): [KitDiffView] with its one "Change 1 of
/// N" navigator (N / P and F7 on a keyboard) on a kit page. A single file
/// names itself in the top bar (file name, folder under it); several files
/// keep [title] (or "Review"). Copy is the file header's More menu,
/// verbatim, unless [allowCopy] is false (the demo).
class DiffPage extends StatelessWidget {
  const DiffPage({
    super.key,
    required this.diffs,
    this.title,
    this.allowCopy = true,
  });

  DiffPage.single(FileDiff diff, {Key? key, bool allowCopy = true})
    : this(key: key, diffs: [diff], allowCopy: allowCopy);

  final List<FileDiff> diffs;
  final bool allowCopy;

  /// The page title for several files; defaults to "Review". A single file
  /// is titled with its own name instead.
  final String? title;

  static Future<void> open(BuildContext context, List<FileDiff> diffs) =>
      pushKitPage<void>(
        context,
        (_) => DiffPage(diffs: diffs),
        fullscreenDialog: true,
      );

  @override
  Widget build(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final store = ReaderPreferencesScope.maybeOf(context);
    final single = diffs.length == 1 ? diffs.single.file : null;
    final slash = single?.lastIndexOf('/') ?? -1;
    return KitScreen(
      key: const Key('diff-view'),
      topBar: KitTopBar(
        title: single == null
            ? title ?? l10n.reviewTitle
            : single.substring(slash + 1),
        subtitle: single != null && slash > 0
            ? single.substring(0, slash)
            : null,
        exit: KitTopBarExit.close,
      ),
      body: KitDiffView(
        keyPrefix: 'diff',
        files: [for (final diff in diffs) _kitFileOf(diff)],
        wrap: store?.value.wrapCode,
        onWrapChanged: store == null
            ? null
            : (wrap) => saveReaderPreferences(context, wrapCode: wrap),
        fileActions: allowCopy
            ? (file) => [?_copyAction(context, l10n, file, keyPrefix: 'diff')]
            : null,
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// FileDiff → KitDiffFile (the kit never sees lib/api types, ARCH-1)

final _kitFiles = Expando<KitDiffFile>('review kit files');

KitDiffFile _kitFileOf(FileDiff diff) => _kitFiles[diff] ??= _convert(diff);

KitDiffFileStatus? _kitStatus(String? status) {
  final s = status?.trim().toLowerCase();
  if (s == null || s.isEmpty) return null;
  if (s.startsWith('add') || s == 'new' || s == 'created') {
    return KitDiffFileStatus.added;
  }
  if (s.startsWith('delet') || s.startsWith('remov')) {
    return KitDiffFileStatus.deleted;
  }
  if (s.startsWith('renam')) return KitDiffFileStatus.renamed;
  return KitDiffFileStatus.modified;
}

KitDiffFile _convert(FileDiff diff) {
  final patch = diff.patch;
  final parsed = patch != null && patch.isNotEmpty
      ? KitDiffFile.fromPatch(diff.file, patch, status: _kitStatus(diff.status))
      : KitDiffFile.fromTexts(
          diff.file,
          before: diff.before,
          after: diff.after,
          status: _kitStatus(diff.status),
        );
  // The server's counts when it sent them; otherwise the parsed patch's.
  final counted = diff.additions != null || diff.deletions != null;
  return KitDiffFile(
    path: parsed.path,
    segments: parsed.segments,
    added: counted ? diff.additions ?? 0 : parsed.added,
    removed: counted ? diff.deletions ?? 0 : parsed.removed,
    status: parsed.status,
    oldPath: parsed.oldPath,
    binary: parsed.binary,
    fullText: diff.after,
    patch: diff.patch,
  );
}

String _normalizedPath(String path) =>
    path.split('/').where((part) => part.isNotEmpty).join('/');

String _basename(String path) => path.replaceAll('\\', '/').split('/').last;

AppLocalizations readerL10n(BuildContext context) =>
    lookupAppLocalizations(Localizations.localeOf(context));
