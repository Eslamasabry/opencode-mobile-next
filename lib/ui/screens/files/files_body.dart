part of '../files_screen.dart';

/// The file browser body: search field, list pane and rows.
extension _FilesBody on _FilesScreenState {
  Widget _body(BuildContext context) {
    final l10n = readerL10n(context);
    final crumbs = _crumbs;
    final viewer = _viewer;
    final screen = KitScreen.twoPane(
      listPaneKey: const ValueKey('files-list-pane'),
      detailPaneKey: const ValueKey('files-detail-pane'),
      search: _searchField(l10n),
      status: _fileStatusesError == null
          ? null
          : KitStatus(
              kind: KitStatusKind.info,
              id: 'files-change-marks',
              icon: AppIconography.info,
              message: _fileStatusesError!,
            ),
      loading: _loading,
      loadingLabel: _surface == _FileSurface.symbols
          ? l10n.filesSearching
          : l10n.filesLoadingFolder,
      list: _listPane(context, l10n, crumbs),
      detail: viewer == null ? null : _embeddedViewer(viewer),
      emptyDetail: KitStateView(
        key: const ValueKey('files-detail-empty'),
        icon: AppIconography.fileText,
        title: l10n.readerUiSelectFile,
      ),
    );
    final topBar = widget.topBar?.call(crumbs.isEmpty ? null : crumbs.last);
    if (topBar == null) return screen;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        topBar,
        Expanded(child: screen),
      ],
    );
  }

  Widget _embeddedViewer(_ViewerSession session) {
    final preferences = ReaderPreferencesScope.maybeOf(context);
    return KitViewer(
      key: ValueKey('files-viewer-${session.path}:${session.initialLine}'),
      name: session.name,
      path: _viewerPath(session),
      source: session.source,
      primary: _viewerPrimary(session),
      more: _viewerMore(session, embedded: true),
      wrap: preferences?.value.wrapCode,
      onWrapChanged: preferences == null
          ? null
          : (wrap) => unawaited(saveReaderPreferences(context, wrapCode: wrap)),
      viewerKey: const ValueKey('files-viewer'),
    );
  }

  KitSearchField _searchField(AppLocalizations l10n) {
    final preferences = ReaderPreferencesScope.maybeOf(context);
    final sourceFirst = preferences?.value.sourceFirst ?? false;
    final symbols = _surface == _FileSurface.symbols;
    final String? activeFilter;
    final VoidCallback? clearFilter;
    if (symbols) {
      activeFilter = l10n.readerUiSymbols;
      clearFilter = () => _selectSurface(_FileSurface.files);
    } else if (sourceFirst) {
      activeFilter = l10n.readerUiSourceFirst;
      clearFilter = () =>
          unawaited(saveReaderPreferences(context, sourceFirst: false));
    } else if (_showHidden) {
      activeFilter = l10n.filesShowHidden;
      clearFilter = () => _set(() => _showHidden = false);
    } else {
      activeFilter = null;
      clearFilter = null;
    }
    return KitSearchField(
      fieldKey: const ValueKey('files-search-field'),
      filterKey: const ValueKey('file-surface-selector'),
      controller: _search,
      focusNode: _searchFocus,
      label: symbols ? l10n.readerUiSearchSymbols : l10n.readerUiSearchFiles,
      // Enter searches at once through onChanged (the field settles the
      // query on submit); a second handler would query twice.
      onChanged: _onSearchChanged,
      activeFilter: activeFilter,
      onClearFilter: clearFilter,
      filters: [
        if (widget.controller.capabilities.workspaceSymbols) ...[
          KitMenuItem(
            key: const ValueKey('file-surface-files'),
            label: l10n.readerUiFiles,
            group: 'surface',
            checked: !symbols,
            onSelected: () => _selectSurface(_FileSurface.files),
          ),
          KitMenuItem(
            key: const ValueKey('file-surface-symbols'),
            label: l10n.readerUiSymbols,
            group: 'surface',
            checked: symbols,
            onSelected: () => _selectSurface(_FileSurface.symbols),
          ),
        ],
        if (!symbols && preferences != null) ...[
          KitMenuItem(
            key: const ValueKey('files-order-server'),
            label: l10n.readerUiServerOrder,
            group: 'order',
            checked: !sourceFirst,
            onSelected: () =>
                unawaited(saveReaderPreferences(context, sourceFirst: false)),
          ),
          KitMenuItem(
            key: const ValueKey('files-order-source-first'),
            label: l10n.readerUiSourceFirst,
            group: 'order',
            checked: sourceFirst,
            onSelected: () =>
                unawaited(saveReaderPreferences(context, sourceFirst: true)),
          ),
        ],
        if (!symbols)
          KitMenuItem(
            key: const ValueKey('files-show-hidden'),
            label: l10n.filesShowHidden,
            icon: AppIconography.hidden,
            checked: _showHidden,
            onSelected: () => _set(() => _showHidden = !_showHidden),
          ),
      ],
    );
  }

  Widget _listPane(
    BuildContext context,
    AppLocalizations l10n,
    List<String> crumbs,
  ) {
    final tokens = KitTokens.of(context);
    final files = _surface == _FileSurface.files;
    final notice = _notice;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (files && crumbs.isNotEmpty && _search.text.isEmpty)
          Padding(
            padding: EdgeInsetsDirectional.only(
              start: tokens.gutter,
              end: tokens.gutter,
              top: tokens.space2,
            ),
            child: KitBreadcrumb(
              breadcrumbKey: const ValueKey('files-breadcrumb'),
              rootLabel: l10n.filesProjectRoot,
              segments: crumbs,
              onSelected: (index) => _navigateTo(
                index < 0 ? '' : crumbs.take(index + 1).join('/'),
              ),
            ),
          ),
        if (notice != null)
          Padding(
            padding: EdgeInsetsDirectional.only(
              start: tokens.gutter,
              end: tokens.gutter,
              top: tokens.space2,
            ),
            child: notice.error
                ? KitNotice.error(
                    key: const ValueKey('files-notice'),
                    message: notice.message,
                  )
                : KitNotice(
                    key: const ValueKey('files-notice'),
                    message: notice.message,
                    tone: AppStatusTone.ok,
                    onDismiss: () => _set(() => _notice = null),
                  ),
          ),
        Expanded(child: files ? _fileList(l10n) : _symbolList(l10n)),
      ],
    );
  }

  /// A first load with nothing to show yet; after 8 s it says it is still
  /// waiting and offers Try again (STATE-5).
  Widget _waiting(String title, Future<void> Function() retry) => KitStateView(
    key: const ValueKey('files-loading'),
    icon: AppIconography.waiting,
    title: title,
    since: _loadStartedAt,
    onSlow: [
      KitAction(
        key: const ValueKey('files-slow-retry'),
        label: readerL10n(context).commonRetry,
        onPressed: () => unawaited(retry()),
      ),
    ],
  );

  /// UX-102: after a run the question is "what changed?", so the changed
  /// set is one tap away as the list's first row: the same inset and
  /// hairline as the file rows under it, not a card of its own. It opens
  /// the diff itself (Review, KitDiffView with its one "Change 1 of N"
  /// navigator and file list); no list of the same files in between.
  Widget? _changesRow(AppLocalizations l10n) {
    if (_fileStatuses.isEmpty) return null;
    var added = 0, removed = 0;
    for (final change in _fileStatuses.values) {
      added += change.additions;
      removed += change.deletions;
    }
    return KitRow(
      key: const ValueKey('files-changes-card'),
      leading: KitRow.icon(context, AppIconography.review),
      title: l10n.readerUiChangedCount(_fileStatuses.length),
      supporting: TextSpan(text: KitBidi.ltr('+$added −$removed')),
      trailing: const KitChevron(),
      onTap: () => unawaited(_reviewChanges()),
    );
  }

  Widget _fileList(AppLocalizations l10n) {
    if (_loading && _entries == null) {
      return _waiting(l10n.filesLoadingFolder, _refreshFiles);
    }
    if (_error != null) {
      return KitRefresh(
        onRefresh: _refreshFiles,
        child: _loadFailed(l10n.filesLoadFailedTitle, _error!, _refreshFiles),
      );
    }
    if (_entries != null &&
        _storageBlock != SharedStorageBlock.none &&
        _search.text.isEmpty) {
      return _storageAccessNotice(l10n);
    }
    final hidden = <FileNode>[];
    final entries = _displayEntries(hidden);
    final changes = _changesRow(l10n);
    if (_entries != null && entries.isEmpty) {
      final searching = _search.text.isNotEmpty;
      final onlyHidden = !searching && hidden.isNotEmpty;
      final state = KitRefresh(
        onRefresh: _refreshFiles,
        // An empty folder is the open folder; a filter that matched nothing
        // is the magnifier (one drawing per kind of state).
        child: KitStateView(
          key: ValueKey(searching ? 'files-no-match' : 'files-empty-folder'),
          icon: AppIconography.folderOpen,
          illustration: searching
              ? const StatesSearchScene()
              : const StatesFolderScene(),
          title: searching ? l10n.readerUiNoFiles : l10n.readerUiEmptyFolder,
          body: searching
              ? l10n.readerUiTryFileName
              : onlyHidden
              ? l10n.filesOnlyHidden
              : l10n.readerUiPullRefresh,
          primary: onlyHidden
              ? KitAction(
                  key: const ValueKey('files-show-hidden-action'),
                  label: l10n.filesShowHidden,
                  onPressed: () => _set(() => _showHidden = true),
                )
              : searching
              ? null
              : KitAction(
                  key: const ValueKey('files-refresh'),
                  label: l10n.fileReload,
                  onPressed: () => unawaited(_refreshFiles()),
                ),
        ),
      );
      if (changes == null) return state;
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Padding(
            padding: EdgeInsetsDirectional.only(
              top: KitTokens.of(context).space2,
            ),
            child: changes,
          ),
          const KitDivider(inset: KitDividerInset.text),
          Expanded(child: state),
        ],
      );
    }
    final lead = changes == null ? 0 : 1;
    return KitRefresh(
      onRefresh: _refreshFiles,
      child: KitScrollArea(
        builder: (scrollController) => ListView.separated(
          controller: scrollController,
          physics: const AlwaysScrollableScrollPhysics(),
          padding: EdgeInsetsDirectional.only(
            top: KitTokens.of(context).space2,
            bottom: KitScreen.endPadding(context),
          ),
          itemCount: lead + entries.length,
          separatorBuilder: (_, _) =>
              const KitDivider(inset: KitDividerInset.text),
          itemBuilder: (context, i) =>
              i < lead ? changes! : _fileRow(context, l10n, entries[i - lead]),
        ),
      ),
    );
  }

  Widget _fileRow(BuildContext context, AppLocalizations l10n, FileNode node) {
    final change = node.isDir ? null : _fileStatuses[node.path];
    final deleted = change?.status == 'deleted';
    final descendantChanges = node.isDir
        ? _fileStatuses.keys
              .where((path) => path.startsWith('${node.path}/'))
              .length
        : 0;
    final detail = _fileDetail(node, change, descendantChanges);
    return KitRow(
      key: ValueKey('project-file-${node.path}'),
      leading: KitRow.icon(
        context,
        node.isDir
            ? AppIconography.files
            : deleted
            ? AppIconography.removeCircle
            : _FilesBody._fileTypeIcon(node.name),
      ),
      title: node.name,
      titleMaxLines: 2,
      supporting: detail == null ? null : TextSpan(text: detail),
      supportingMaxLines: 2,
      selected: node.path == _viewer?.path,
      trailing: node.isDir ? const KitChevron() : null,
      // Long-press, right-click and the keyboard open the row's menu; its
      // items are also the row's semantic actions (KIT-28).
      menu: _fileRowMenu(l10n, node, change),
      menuLabel: l10n.gestureEquivFileRowActions(node.name),
      onTap: deleted
          ? () => unawaited(_reviewFileChange(node))
          : () {
              if (node.isDir) {
                _navigateTo(node.path);
              } else {
                _openFile(node);
              }
            },
    );
  }

  /// The row's rarer actions: the same list on a long press, a right click
  /// and the context-menu key. Opening is the row's tap, so it is not here.
  List<KitMenuItem> _fileRowMenu(
    AppLocalizations l10n,
    FileNode node,
    VersionControlFile? change,
  ) {
    final deleted = change?.status == 'deleted';
    final path = _relativePath(node.path);
    return [
      if (!node.isDir && !deleted && widget.onAttachFile != null)
        KitMenuItem(
          key: const ValueKey('file-menu-attach'),
          label: l10n.readerUiAttachPrompt,
          icon: AppIconography.attach,
          onSelected: () => unawaited(_attachFile(path)),
        ),
      if (!node.isDir && !deleted && widget.handoff != null)
        KitMenuItem(
          key: const ValueKey('file-menu-reference'),
          label: l10n.readerUiAddReference,
          icon: AppIconography.link,
          onSelected: () => _stageProjectFile(path, null),
        ),
      if (change != null)
        KitMenuItem(
          key: const ValueKey('file-menu-review'),
          label: l10n.readerUiOpenReview,
          icon: AppIconography.review,
          onSelected: () => unawaited(_reviewFileChange(node)),
        ),
      KitMenuItem.copy(
        key: const ValueKey('file-menu-copy-path'),
        label: l10n.readerUiCopyPath,
        text: () => path,
      ),
      KitMenuItem.copy(
        key: const ValueKey('file-menu-copy-name'),
        label: l10n.filesCopyName,
        icon: AppIconography.textShort,
        text: () => node.name,
      ),
    ];
  }

  /// Type-aware glyphs so a directory scans by kind, matching the developer
  /// file browsers cited in docs/design-inspiration.md.
  static IconData _fileTypeIcon(String name) {
    return switch (_FilesViewer._extension(name)) {
      'dart' ||
      'js' ||
      'ts' ||
      'tsx' ||
      'jsx' ||
      'py' ||
      'go' ||
      'rs' ||
      'kt' ||
      'java' ||
      'swift' ||
      'c' ||
      'cc' ||
      'cpp' ||
      'h' ||
      'cs' ||
      'rb' ||
      'php' ||
      'sh' => AppIconography.code,
      'png' ||
      'jpg' ||
      'jpeg' ||
      'gif' ||
      'webp' ||
      'svg' ||
      'ico' => AppIconography.image,
      'md' || 'txt' || 'rst' || 'pdf' => AppIconography.article,
      'json' ||
      'yaml' ||
      'yml' ||
      'toml' ||
      'xml' ||
      'ini' ||
      'lock' ||
      'gradle' ||
      'properties' => AppIconography.dataObject,
      'zip' || 'tar' || 'gz' || 'jar' || 'apk' => AppIconography.zip,
      _ => AppIconography.fileText,
    };
  }
}
