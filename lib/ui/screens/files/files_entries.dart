part of '../files_screen.dart';

/// Directory entries, load failures and the symbol list.
extension _FilesEntries on _FilesScreenState {
  /// The listing as shown: deleted files added back where they were, dot
  /// entries left out unless Show hidden files is on (collected into
  /// [hidden] so an all-hidden folder can say so), and the reader's order.
  List<FileNode> _displayEntries(List<FileNode> hidden) {
    final entries = [...?_entries];
    final paths = entries.map((node) => node.path).toSet();
    final searchMode = _searchOriginPath != null;
    final query = _search.text.trim().toLowerCase();
    final prefix = _path.isEmpty ? '' : '$_path/';
    for (final change in _fileStatuses.values) {
      if (change.status != 'deleted') continue;
      if (searchMode) {
        if (query.isNotEmpty && !change.path.toLowerCase().contains(query)) {
          continue;
        }
        if (paths.add(change.path)) {
          entries.add(
            FileNode(
              name: change.path.split('/').last,
              path: change.path,
              isDir: false,
            ),
          );
        }
        continue;
      }
      if (!change.path.startsWith(prefix)) continue;
      final remainder = change.path.substring(prefix.length);
      if (remainder.isEmpty) continue;
      final parts = remainder.split('/');
      final childPath = '$prefix${parts.first}';
      if (paths.add(childPath)) {
        entries.add(
          FileNode(name: parts.first, path: childPath, isDir: parts.length > 1),
        );
      }
    }
    if (!_showHidden && !searchMode) {
      hidden.addAll(entries.where((entry) => entry.name.startsWith('.')));
      entries.removeWhere((entry) => entry.name.startsWith('.'));
    }
    if (ReaderPreferencesScope.maybeOf(context)?.value.sourceFirst == true) {
      // Stable partition: the server's order remains intact within each group.
      // Generated entries remain visible, including deleted files.
      return [
        ...entries.where((entry) => !_isGeneratedEntry(entry)),
        ...entries.where(_isGeneratedEntry),
      ];
    }
    return entries;
  }

  bool _isGeneratedEntry(FileNode node) {
    const generatedDirectories = {
      'node_modules',
      '.git',
      '.dart_tool',
      'build',
      'dist',
      'coverage',
      '__pycache__',
      '.gradle',
    };
    final name = node.name.toLowerCase();
    final directories = node.path.split('/');
    if (!node.isDir && directories.isNotEmpty) directories.removeLast();
    return directories.any(
          (part) => generatedDirectories.contains(part.toLowerCase()),
        ) ||
        name.endsWith('.lock') ||
        name.endsWith('.g.dart') ||
        name.endsWith('.freezed.dart') ||
        name.endsWith('.min.js') ||
        name.endsWith('.map');
  }

  /// The row's second line: where a search result lives, and its change
  /// mark in words (or how many files changed inside a folder).
  String? _fileDetail(
    FileNode node,
    VersionControlFile? change,
    int descendantChanges,
  ) {
    final details = <String>[];
    if (_search.text.isNotEmpty && node.path != node.name) {
      details.add(KitBidi.ltr(node.path));
    }
    if (change != null) {
      details.add(_fileStatusLabel(context, change.status));
    } else if (descendantChanges > 0) {
      details.add(readerL10n(context).readerUiChangedCount(descendantChanges));
    }
    return details.isEmpty ? null : details.join(' · ');
  }

  /// A listing that could not load: the unplugged drawing every load
  /// failure uses, the reason, Try again, and Report a bug.
  Widget _loadFailed(
    String title,
    String message,
    Future<void> Function() onRetry,
  ) {
    final l10n = readerL10n(context);
    return KitStateView(
      key: const ValueKey('files-load-failed'),
      icon: AppIconography.error,
      tone: AppStatusTone.failure,
      illustration: const StatesUnpluggedScene(),
      title: title,
      body: message,
      primary: KitAction(
        label: l10n.commonRetry,
        onPressed: () => unawaited(onRetry()),
      ),
      tertiary: [
        KitAction(
          key: const ValueKey('product-error-report-bug'),
          label: l10n.e7LibraryReportABug,
          onPressed: () => unawaited(openBugReport(context)),
        ),
      ],
    );
  }

  Widget _symbolList(AppLocalizations l10n) {
    Future<void> retry() => _searchSymbols(_search.text);
    if (_loading && _symbols == null) {
      return _waiting(l10n.filesSearching, retry);
    }
    if (_error != null) {
      return KitRefresh(
        onRefresh: retry,
        child: _loadFailed(l10n.filesSymbolsFailedTitle, _error!, retry),
      );
    }
    if (_search.text.trim().isEmpty) {
      return KitStateView(
        key: const ValueKey('files-symbols-hint'),
        icon: AppIconography.dataObject,
        title: l10n.readerUiWorkspaceSymbols,
        body: l10n.readerUiSymbolsHint,
      );
    }
    if (_symbols?.isEmpty == true) {
      return KitRefresh(
        onRefresh: retry,
        child: KitStateView(
          key: const ValueKey('files-no-symbols'),
          icon: AppIconography.search,
          illustration: const StatesSearchScene(),
          title: l10n.readerUiNoSymbols,
          body: l10n.readerUiSymbolsUnavailable,
        ),
      );
    }
    final symbols = _symbols ?? const <WorkspaceSymbol>[];
    return KitRefresh(
      onRefresh: retry,
      child: KitScrollArea(
        builder: (scrollController) => ListView.separated(
          controller: scrollController,
          physics: const AlwaysScrollableScrollPhysics(),
          padding: EdgeInsetsDirectional.only(
            top: KitTokens.of(context).space2,
            bottom: KitScreen.endPadding(context),
          ),
          itemCount: symbols.length,
          separatorBuilder: (_, _) =>
              const KitDivider(inset: KitDividerInset.text),
          itemBuilder: (context, index) {
            final symbol = symbols[index];
            return KitRow(
              key: ValueKey('workspace-symbol-${symbol.path}-${symbol.line}'),
              leading: KitRow.icon(
                context,
                _FilesEntries._symbolIcon(symbol.kind),
              ),
              title: symbol.name,
              supporting: TextSpan(
                text:
                    '${_symbolKind(symbol.kind)} · '
                    '${KitBidi.ltr('${symbol.path}:${symbol.line}:${symbol.column}')}',
              ),
              supportingMaxLines: 2,
              trailing: const KitChevron(),
              onTap: () => _openSymbol(symbol),
            );
          },
        ),
      ),
    );
  }

  /// Lines that contain the typed text: file and line number on top, the
  /// matching line under them; a tap opens the file at that line.
  Widget _textList(AppLocalizations l10n) {
    Future<void> retry() => _searchText(_search.text);
    if (_loading && _textMatches == null) {
      return _waiting(l10n.filesSearching, retry);
    }
    if (_error != null) {
      return KitRefresh(
        onRefresh: retry,
        child: _loadFailed(l10n.filesTextFailedTitle, _error!, retry),
      );
    }
    if (_search.text.trim().isEmpty) {
      return KitStateView(
        key: const ValueKey('files-text-hint'),
        icon: AppIconography.search,
        title: l10n.filesTextHintTitle,
        body: l10n.filesTextHintBody,
      );
    }
    final all = _textMatches ?? const <FindMatch>[];
    if (_textMatches != null && all.isEmpty) {
      return KitRefresh(
        onRefresh: retry,
        child: KitStateView(
          key: const ValueKey('files-no-text'),
          icon: AppIconography.search,
          illustration: const StatesSearchScene(),
          title: l10n.filesNoTextTitle,
          body: l10n.filesNoTextBody,
        ),
      );
    }
    final shown = all.take(_FilesLoading._maxTextMatches).toList();
    final more = all.length > shown.length;
    return KitRefresh(
      onRefresh: retry,
      child: KitScrollArea(
        builder: (scrollController) => ListView.separated(
          controller: scrollController,
          physics: const AlwaysScrollableScrollPhysics(),
          padding: EdgeInsetsDirectional.only(
            top: KitTokens.of(context).space2,
            bottom: KitScreen.endPadding(context),
          ),
          itemCount: shown.length + (more ? 1 : 0),
          separatorBuilder: (_, _) =>
              const KitDivider(inset: KitDividerInset.text),
          itemBuilder: (context, index) {
            if (index == shown.length) {
              return Padding(
                padding: EdgeInsetsDirectional.all(
                  KitTokens.of(context).gutter,
                ),
                child: KitNotice(
                  key: const ValueKey('files-text-limited'),
                  message: l10n.filesTextLimited(shown.length),
                ),
              );
            }
            final match = shown[index];
            final path = _relativePath(match.path);
            return KitRow(
              key: ValueKey('text-match-$path:${match.lineNumber}'),
              leading: KitRow.icon(context, AppIconography.fileText),
              title: match.snippet.trim().isEmpty
                  ? path.split('/').last
                  : match.snippet.trim(),
              titleMaxLines: 2,
              supporting: TextSpan(
                text:
                    '${KitBidi.ltr(path)} · '
                    '${l10n.filesTextMatchLine(match.lineNumber)}',
              ),
              supportingMaxLines: 2,
              trailing: const KitChevron(),
              onTap: () => _openMatch(match),
            );
          },
        ),
      ),
    );
  }

  String _symbolKind(int kind) => switch (kind) {
    1 => readerL10n(context).readerUiSymbolFile,
    2 => readerL10n(context).readerUiSymbolModule,
    3 => readerL10n(context).readerUiSymbolNamespace,
    4 => readerL10n(context).readerUiSymbolPackage,
    5 => readerL10n(context).readerUiSymbolClass,
    6 => readerL10n(context).readerUiSymbolMethod,
    7 => readerL10n(context).readerUiSymbolProperty,
    8 => readerL10n(context).readerUiSymbolField,
    9 => readerL10n(context).readerUiSymbolConstructor,
    10 => readerL10n(context).readerUiSymbolEnum,
    11 => readerL10n(context).readerUiSymbolInterface,
    12 => readerL10n(context).readerUiSymbolFunction,
    13 => readerL10n(context).readerUiSymbolVariable,
    14 => readerL10n(context).readerUiSymbolConstant,
    22 => readerL10n(context).readerUiSymbolEnummember,
    23 => readerL10n(context).readerUiSymbolStruct,
    24 => readerL10n(context).readerUiSymbolEvent,
    25 => readerL10n(context).readerUiSymbolOperator,
    26 => readerL10n(context).readerUiSymbolTypeparameter,
    _ => readerL10n(context).readerUiSymbolSymbol,
  };

  static IconData _symbolIcon(int kind) => switch (kind) {
    5 || 10 || 11 || 23 => AppIconography.category,
    6 || 9 || 12 => AppIconography.function,
    7 || 8 || 13 || 14 => AppIconography.dataObject,
    1 || 2 || 3 || 4 => AppIconography.folders,
    _ => AppIconography.code,
  };
}
