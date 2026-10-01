part of '../kit_diff_view.dart';

// The diff view's frame: build, file list, header, selection bar.

extension _KitDiffChrome on _KitDiffViewState {
  // -------------------------------------------------------------------------
  // Build

  /// The header bar and up to six skeleton rows: in a short bounded room
  /// (a phone in landscape) only the whole rows that fit, never an
  /// overflow.
  Widget _loading(KitTokens tokens) => LayoutBuilder(
    builder: (context, constraints) {
      final room = constraints.maxHeight - tokens.rowHeight;
      final rows = constraints.maxHeight.isFinite
          ? math.max(
              0,
              math.min(6, (room / KitTokens.skeletonRowHeight).floor()),
            )
          : 6;
      return Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Container(
            height: tokens.rowHeight,
            decoration: BoxDecoration(
              color: tokens.roles.surface1,
              border: Border(
                bottom: BorderSide(
                  color: tokens.roles.hairline,
                  width: KitTokens.hairlineWidth(context),
                ),
              ),
            ),
          ),
          if (rows > 0) KitSkeletonRows(count: rows),
        ],
      );
    },
  );

  String _statusLine(KitDiffFile file, AppLocalizations l10n) =>
      switch (file.status) {
        KitDiffFileStatus.renamed when file.oldPath != null =>
          l10n.kitDiffRenamed(KitBidi.ltr(file.oldPath!)),
        KitDiffFileStatus.added => l10n.kitDiffAddedFile,
        KitDiffFileStatus.deleted => l10n.kitDiffDeletedFile,
        _ => '',
      };

  String _baseName(String path) => path.split('/').last;

  String _directory(String path) {
    final parts = path.split('/');
    return parts.length > 1
        ? '${parts.sublist(0, parts.length - 1).join('/')}/'
        : '';
  }

  String _fileSupporting(KitDiffFile file, AppLocalizations l10n) => [
    '+${file.added} −${file.removed}',
    _statusLine(file, l10n),
  ].where((s) => s.isNotEmpty).join(' · ');

  Future<void> _pickFile() async {
    final l10n = _l10n;
    final picked = await showKitChoiceSheet<int>(
      context,
      title: l10n.kitDiffFiles(widget.files.length),
      choices: [
        for (var i = 0; i < widget.files.length; i++)
          KitChoice(
            value: i,
            title: KitBidi.ltr(widget.files[i].path),
            supporting: _fileSupporting(widget.files[i], l10n),
          ),
      ],
      selected: _file,
    );
    if (picked != null && mounted) _openFile(picked);
  }

  /// The large box's file list: the open file is highlighted (no word for
  /// it), and a tick marks each file already viewed.
  Widget _fileList(KitTokens tokens, {required bool bounded}) {
    final roles = tokens.roles;
    final l10n = _l10n;
    final list = Semantics(
      container: true,
      explicitChildNodes: true,
      label: l10n.kitDiffFiles(widget.files.length),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          for (var i = 0; i < widget.files.length; i++)
            KitRow(
              key: ValueKey('${widget.keyPrefix}-file-row-$i'),
              title: KitBidi.ltr(widget.files[i].path),
              titleMaxLines: 2,
              supporting: TextSpan(
                text: _fileSupporting(widget.files[i], l10n),
              ),
              selected: i == _file,
              trailing: _viewed.contains(i)
                  ? Semantics(
                      label: l10n.kitDiffViewed,
                      child: Icon(
                        AppIconography.check,
                        key: ValueKey('${widget.keyPrefix}-file-viewed-$i'),
                        size: tokens.smallIconSize,
                        color: roles.text2,
                      ),
                    )
                  : null,
              onTap: () => _openFile(i),
            ),
        ],
      ),
    );
    return DecoratedBox(
      key: ValueKey('${widget.keyPrefix}-file-list'),
      decoration: BoxDecoration(
        color: roles.surface1,
        borderRadius: BorderRadius.circular(tokens.panelCornerRadius),
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(tokens.panelCornerRadius),
        child: bounded
            ? SingleChildScrollView(
                padding: EdgeInsets.symmetric(vertical: tokens.space2),
                child: list,
              )
            : Padding(
                padding: EdgeInsets.symmetric(vertical: tokens.space2),
                child: list,
              ),
      ),
    );
  }

  Widget _diffColumn(
    BuildContext context,
    KitTokens tokens,
    AppLocalizations l10n, {
    required double width,
    required KitWindow window,
    required bool split,
    required bool wrap,
    required bool bounded,
    required bool picker,
    Widget? fileList,
  }) {
    final file = widget.files[_file];
    final header = _header(context, tokens, l10n, file, window, wrap, picker);
    Widget body;
    Widget? footer;
    if (file.binary) {
      final message = KitText(
        l10n.kitDiffBinary,
        role: KitTextRole.secondary,
        tone: KitTextTone.secondary,
      );
      // The message names the whole body, so a screen reader moves from
      // the header (or the file list) into it in order (A11Y-4).
      body = Semantics(
        container: true,
        label: l10n.kitDiffBinary,
        excludeSemantics: true,
        child: Align(
          alignment: AlignmentDirectional.topStart,
          child: Padding(
            padding: EdgeInsets.all(tokens.gutter),
            child: message,
          ),
        ),
      );
    } else {
      final (lines, capped) = _linesView(
        context,
        tokens,
        file: file,
        width: width,
        split: split,
        wrap: wrap,
        bounded: bounded,
      );
      body = lines;
      if (capped != null) {
        footer = _tooBig(tokens, l10n, capped.$1, capped.$2);
      }
    }
    final selection = _selecting ? _selectionBar(tokens, l10n) : null;
    Widget linesColumnBody({double? selectionMaxHeight}) => Column(
      mainAxisSize: bounded ? MainAxisSize.max : MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (bounded) Expanded(child: body) else body,
        ?footer,
        if (selection != null)
          if (selectionMaxHeight == null)
            selection
          else
            ConstrainedBox(
              constraints: BoxConstraints(maxHeight: selectionMaxHeight),
              child: SingleChildScrollView(child: selection),
            ),
      ],
    );
    // Large text can make the selection actions taller than the space left
    // below the file header. Keep source visible and let those actions scroll;
    // a footer that fits still takes only its natural height.
    final linesColumn = bounded
        ? LayoutBuilder(
            builder: (context, constraints) =>
                linesColumnBody(selectionMaxHeight: constraints.maxHeight / 2),
          )
        : linesColumnBody();
    // A large box: the header spans the top, the file list sits at the
    // start of the lines (read after the header, top to bottom, A11Y-4).
    final Widget main = fileList == null
        ? linesColumn
        : Row(
            crossAxisAlignment: bounded
                ? CrossAxisAlignment.stretch
                : CrossAxisAlignment.start,
            children: [
              SizedBox(width: KitLayout.paneListWidth, child: fileList),
              SizedBox(width: tokens.space3),
              Expanded(child: linesColumn),
            ],
          );
    return Column(
      mainAxisSize: bounded ? MainAxisSize.max : MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        header,
        if (bounded) Expanded(child: main) else main,
      ],
    );
  }

  Widget _header(
    BuildContext context,
    KitTokens tokens,
    AppLocalizations l10n,
    KitDiffFile file,
    KitWindow window,
    bool wrap,
    bool picker,
  ) {
    final roles = tokens.roles;
    final status = _statusLine(file, l10n);
    final multi = widget.files.length > 1;
    final counts = Semantics(
      label: l10n.kitDiffCounts(file.added, file.removed),
      excludeSemantics: true,
      child: _Counts(added: file.added, removed: file.removed),
    );

    // One row names the file; with 2+ files and no file list it is the
    // switcher, "checkout_page.dart · 1 of 3 ›", and the count is said
    // only there. The counts sit at the end, the folder and the status
    // word under the name.
    final switcher = multi && picker;
    final directory = _directory(file.path);
    final supporting = [
      if (directory.isNotEmpty) KitBidi.ltr(directory),
      status,
    ].where((s) => s.isNotEmpty).join(' · ');
    final position = l10n.kitDiffFilePosition(_file + 1, widget.files.length);
    final name = Text.rich(
      TextSpan(
        children: [
          TextSpan(
            text: KitBidi.ltr(_baseName(file.path)),
            style: tokens.rowTitle.copyWith(color: roles.text1),
          ),
          if (switcher)
            TextSpan(
              // Isolated: in a right-to-left window "1 of 3" stays whole.
              text: ' · ${KitBidi.auto(position)}',
              style: KitText.styleOf(
                context,
                KitTextRole.secondary,
              ).copyWith(color: roles.text2),
            ),
        ],
      ),
      maxLines: 2,
      overflow: TextOverflow.ellipsis,
    );
    final label = [
      file.path,
      l10n.kitDiffCounts(file.added, file.removed),
      if (switcher)
        l10n.kitDiffFilePositionSpoken(_file + 1, widget.files.length),
      if (status.isNotEmpty) status,
    ].join(', ');
    // Without the switcher the file list follows at once: no bottom inset.
    final rowBottom = switcher ? tokens.space2 : 0.0;
    final row = Padding(
      padding: EdgeInsetsDirectional.only(
        start: tokens.gutter,
        top: tokens.space2,
        end: tokens.gutter,
        bottom: rowBottom,
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                if (switcher)
                  Row(
                    children: [
                      Flexible(child: name),
                      SizedBox(width: tokens.space1),
                      Icon(
                        AppIconography.chevronRight,
                        size: tokens.smallIconSize,
                        color: roles.text2,
                      ),
                    ],
                  )
                else
                  name,
                if (supporting.isNotEmpty)
                  KitText(
                    supporting,
                    role: KitTextRole.secondary,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                  ),
              ],
            ),
          ),
          SizedBox(width: tokens.space3),
          counts,
        ],
      ),
    );
    final Widget title = switcher
        ? KitTappable(
            key: ValueKey('${widget.keyPrefix}-file-picker'),
            label: label,
            onTap: _pickFile,
            child: row,
          )
        : Semantics(
            container: true,
            label: label,
            excludeSemantics: true,
            child: row,
          );

    final changes = _changes;
    final more = widget.fileActions?.call(file) ?? const <KitMenuItem>[];
    final tools = Padding(
      // The last icon target clears the edge by a full step.
      padding: EdgeInsetsDirectional.only(
        start: tokens.space1,
        end: tokens.space3,
      ),
      child: Row(
        children: [
          if (changes.isNotEmpty) ...[
            KitIconButton(
              key: ValueKey('${widget.keyPrefix}-nav-previous'),
              icon: AppIconography.chevronUp,
              tooltip: l10n.kitDiffPreviousChange,
              shortcut: 'P',
              onPressed: _change > 0 ? () => _goToChange(_change - 1) : null,
            ),
            KitIconButton(
              key: ValueKey('${widget.keyPrefix}-nav-next'),
              icon: AppIconography.chevronDown,
              tooltip: l10n.kitDiffNextChange,
              shortcut: 'N',
              onPressed: _change < changes.length - 1
                  ? () => _goToChange(_change + 1)
                  : null,
            ),
            SizedBox(width: tokens.space1),
            Expanded(
              child: Semantics(
                liveRegion: true,
                child: KitText(
                  key: ValueKey('${widget.keyPrefix}-nav-label'),
                  l10n.kitDiffChangeOf(_change + 1, changes.length),
                  role: KitTextRole.caption,
                ),
              ),
            ),
          ] else
            const Spacer(),
          if (!file.binary)
            KitIconButton(
              icon: AppIconography.wrapText,
              tooltip: l10n.kitWrapLines,
              selected: wrap,
              onPressed: () => _toggleWrap(window),
            ),
          if (more.isNotEmpty)
            Builder(
              builder: (anchor) => KitIconButton(
                icon: AppIconography.more,
                tooltip: l10n.kitMore,
                onPressed: () =>
                    showKitMenu(anchor, items: more, semanticsLabel: file.path),
              ),
            ),
        ],
      ),
    );

    return Container(
      key: ValueKey('${widget.keyPrefix}-file-header-${file.path}'),
      decoration: BoxDecoration(
        color: roles.surface1,
        border: Border(
          bottom: BorderSide(
            color: roles.hairline,
            width: KitTokens.hairlineWidth(context),
          ),
        ),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [title, tools],
      ),
    );
  }

  Widget _tooBig(
    KitTokens tokens,
    AppLocalizations l10n,
    int shown,
    int total,
  ) => Padding(
    padding: EdgeInsetsDirectional.fromSTEB(
      tokens.gutter,
      tokens.space2,
      tokens.gutter,
      tokens.space2,
    ),
    child: Wrap(
      crossAxisAlignment: WrapCrossAlignment.center,
      spacing: tokens.space3,
      runSpacing: tokens.space1,
      children: [
        KitText(l10n.kitDiffTooBig(shown, total), role: KitTextRole.secondary),
        if (widget.onOpenAll != null)
          KitButton.tertiary(
            key: ValueKey('${widget.keyPrefix}-open-all'),
            label: l10n.kitDiffOpenAll,
            onPressed: widget.onOpenAll,
          ),
      ],
    ),
  );

  Widget _selectionBar(KitTokens tokens, AppLocalizations l10n) {
    final roles = tokens.roles;
    final count = Semantics(
      liveRegion: true,
      child: KitText(
        l10n.kitDiffSelected(_selectedCount),
        role: KitTextRole.label,
      ),
    );
    final clear = KitIconButton(
      icon: AppIconography.close,
      tooltip: l10n.kitDiffClearSelection,
      shortcut: 'Esc',
      onPressed: _clearSelection,
    );
    final actions = KitActionBlock(
      primary: widget.onComment == null
          ? null
          : KitAction(label: l10n.kitDiffComment, onPressed: _comment),
      tertiary: [
        if (widget.onAddToPrompt != null)
          KitAction(label: l10n.kitDiffAddToPrompt, onPressed: _addToPrompt),
        KitAction(
          label: l10n.kitDiffCopyLines,
          icon: AppIconography.copy,
          onPressed: _copyLines,
        ),
      ],
    );
    // Compact: the count and Clear, then the stacked actions. Wider: one
    // line read start to end, count, actions, Clear (A11Y-4).
    final compact = KitLayout.windowOf(context) == KitWindow.compact;
    final Widget content = compact
        ? Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(
                children: [
                  Expanded(child: count),
                  clear,
                ],
              ),
              SizedBox(height: tokens.space1),
              Padding(
                padding: EdgeInsetsDirectional.only(end: tokens.space3),
                child: actions,
              ),
            ],
          )
        : Row(
            children: [
              Expanded(child: count),
              SizedBox(width: tokens.space3),
              Flexible(flex: 3, child: actions),
              SizedBox(width: tokens.space2),
              clear,
            ],
          );
    return Container(
      key: ValueKey('${widget.keyPrefix}-selection-bar'),
      decoration: BoxDecoration(
        color: roles.surface1,
        border: Border(
          top: BorderSide(
            color: roles.hairline,
            width: KitTokens.hairlineWidth(context),
          ),
        ),
      ),
      padding: EdgeInsetsDirectional.fromSTEB(
        tokens.gutter,
        tokens.space1,
        tokens.space1,
        tokens.space2,
      ),
      child: content,
    );
  }

  /// The lines of [file], and (shown, total) when [KitDiffView.maxLines]
  /// cut them short.
  (Widget, (int, int)?) _linesView(
    BuildContext context,
    KitTokens tokens, {
    required KitDiffFile file,
    required double width,
    required bool split,
    required bool wrap,
    required bool bounded,
  }) {
    final index = _FileIndex.of(file);
    final mono = KitText.styleOf(context, KitTextRole.mono);
    final scaler = MediaQuery.textScalerOf(context);
    final digits = index.maxNumber.toString().length;
    final digitPainter = TextPainter(
      text: TextSpan(text: '0' * digits, style: mono),
      textDirection: TextDirection.ltr,
      textScaler: scaler,
    )..layout();
    final lineHeight = digitPainter.height;
    final numberWidth = (digitPainter.width + tokens.space2 * 1.5)
        .ceilToDouble();
    digitPainter.dispose();
    final glyphWidth = (scaler.scale(mono.fontSize ?? 13) + tokens.space1)
        .ceilToDouble();
    _lastSplit = split;
    _lastLineHeight = lineHeight;
    _lastBarHeight = tokens.minTarget;
    _lastNumberWidth = numberWidth;

    final gutter = (split ? numberWidth : numberWidth * 2) + glyphWidth;
    double? textWidth;
    var contentWidth = width;
    if (!wrap) {
      final painter = TextPainter(
        text: TextSpan(text: index.longest, style: mono),
        textDirection: TextDirection.ltr,
        textScaler: scaler,
        maxLines: 1,
      )..layout();
      final longest = painter.width.ceilToDouble() + tokens.space4;
      painter.dispose();
      final divider = KitTokens.hairlineWidth(context);
      if (split) {
        final half = math.max((width - divider) / 2, gutter + longest);
        textWidth = half - gutter;
        contentWidth = half * 2 + divider;
      } else {
        textWidth = math.max(width - gutter, longest);
        contentWidth = gutter + textWidth;
      }
    }
    final geometry = _Geometry(
      number: numberWidth,
      glyph: glyphWidth,
      lineHeight: lineHeight,
      wrap: wrap,
      split: split,
      selectable: !widget.readOnly,
      textWidth: textWidth,
    );

    var items = _currentItems(split: split);
    (int, int)? capped;
    final maxLines = widget.maxLines;
    if (maxLines != null) {
      var rows = 0;
      var cut = items.length;
      for (var i = 0; i < items.length; i++) {
        if (items[i] is _LineItem || items[i] is _PairItem) rows++;
        if (rows > maxLines) {
          cut = i;
          break;
        }
      }
      if (cut < items.length) {
        items = items.sublist(0, cut);
        capped = (maxLines, index.total);
      }
    }

    Widget itemAt(BuildContext context, int i) {
      final item = items[i];
      final child = _itemView(context, tokens, item, geometry);
      if (i == _targetItem) {
        return KeyedSubtree(key: _targetKey, child: child);
      }
      return child;
    }

    Widget list = ListView.builder(
      controller: bounded ? _lines : null,
      shrinkWrap: !bounded,
      physics: bounded ? null : const NeverScrollableScrollPhysics(),
      padding: EdgeInsets.only(bottom: tokens.space4),
      itemCount: items.length,
      itemBuilder: itemAt,
    );
    list = SelectionArea(child: list);
    if (!widget.readOnly) {
      // The gutter's hit area lies over the list, so a number's reach can
      // extend past its compact row into its neighbours' space.
      list = Stack(
        children: [
          list,
          PositionedDirectional(
            start: 0,
            end: 0,
            top: 0,
            bottom: 0,
            child: _GutterHitArea(
              claims: (global) => _gutterLineAt(global) != null,
              child: Builder(builder: _gutterGestures),
            ),
          ),
        ],
      );
    }
    if (!wrap) {
      list = SingleChildScrollView(
        key: ValueKey('${widget.keyPrefix}-horizontal'),
        scrollDirection: Axis.horizontal,
        child: SizedBox(width: contentWidth, child: list),
      );
    }
    final view = Focus(
      focusNode: _bodyFocus,
      onKeyEvent: _onKey,
      child: Directionality(
        textDirection: TextDirection.ltr,
        child: ColoredBox(color: tokens.detailsSurface, child: list),
      ),
    );
    return (view, capped);
  }
}
