part of 'kit_viewer.dart';

extension _KitViewerBuild on _KitViewerState {
  Widget _header(
    BuildContext context,
    KitTokens tokens,
    AppLocalizations l10n,
  ) {
    final compact = KitLayout.windowOf(context) == KitWindow.compact;
    final path = widget.path;
    final folder = path == null ? null : KitViewer.folderOf(path, widget.name);
    final routeNamed = _KitViewerRouteScope.of(context);
    // Binary content offers the caller's action in its own state instead.
    final primary = _content?.kind == KitViewerKind.binary
        ? null
        : widget.primary;
    final title = Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        Semantics(
          header: true,
          namesRoute: routeNamed,
          child: KitText(widget.name, role: KitTextRole.headline),
        ),
        // The parent folder only: the name is already the title, so a root
        // file shows no second line. The full path stays in semantics.
        if (folder != null) ...[
          SizedBox(height: tokens.space1 / 2),
          Semantics(
            label: path,
            child: ExcludeSemantics(
              child: KitText.mono(
                folder,
                cut: KitMonoCut.middle,
                tone: KitTextTone.secondary,
              ),
            ),
          ),
        ],
      ],
    );
    // The primary's own row follows the name directly: no bottom inset.
    final topBottom = primary == null ? tokens.space2 : 0.0;
    final top = Padding(
      padding: EdgeInsetsDirectional.fromSTEB(
        tokens.gutter,
        widget.onClose == null ? tokens.space3 : tokens.space1,
        tokens.space2,
        topBottom,
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(
            child: Padding(
              padding: EdgeInsetsDirectional.only(
                top: tokens.space2,
                end: tokens.space2,
              ),
              child: title,
            ),
          ),
          FocusTraversalOrder(
            order: const NumericFocusOrder(2),
            child: Builder(
              builder: (anchor) => KitIconButton(
                key: const ValueKey('kit-viewer-more'),
                icon: AppIconography.more,
                tooltip: l10n.kitMore,
                onPressed: () => unawaited(_openMenu(anchor)),
              ),
            ),
          ),
          if (widget.onClose case final close?)
            FocusTraversalOrder(
              order: const NumericFocusOrder(3),
              child: KitIconButton(
                key: const ValueKey('kit-viewer-close'),
                icon: AppIconography.close,
                tooltip: l10n.kitSheetClose,
                onPressed: close,
              ),
            ),
        ],
      ),
    );
    if (primary == null) return top;
    // The one labelled action has its own full-width row under the name,
    // starting where the name starts: never squeezed beside More and Close,
    // never wrapped to two centred lines.
    final reason = primary.enabled ? null : primary.disabledReason;
    final button = KeyedSubtree(
      key: const ValueKey('kit-viewer-primary'),
      child: KitButton.fromAction(
        primary,
        role: compact ? KitButtonRole.tertiary : KitButtonRole.secondary,
        expand: false,
      ),
    );
    final primaryRow = Padding(
      padding: EdgeInsetsDirectional.fromSTEB(
        tokens.gutter,
        tokens.space2,
        tokens.gutter,
        tokens.space2,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          FocusTraversalOrder(
            order: const NumericFocusOrder(1),
            child: compact
                ? Transform.translate(
                    // The tertiary button's words line up with the name.
                    offset: Offset(
                      Directionality.of(context) == TextDirection.rtl
                          ? KitButton.tertiaryInset
                          : -KitButton.tertiaryInset,
                      0,
                    ),
                    child: button,
                  )
                : button,
          ),
          if (reason != null) KitText(reason, role: KitTextRole.secondary),
        ],
      ),
    );
    // Tab still reaches the primary first, then More and Close (LAY-10).
    return FocusTraversalGroup(
      policy: OrderedTraversalPolicy(),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [top, primaryRow],
      ),
    );
  }

  Widget _findBar(KitTokens tokens, AppLocalizations l10n) {
    final hasMarks = _marks.isNotEmpty;
    return Padding(
      padding: EdgeInsetsDirectional.fromSTEB(
        tokens.gutter,
        tokens.space2,
        tokens.space2,
        tokens.space2,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: [
          Row(
            children: [
              Expanded(
                child: KitSearchField(
                  label: l10n.kitViewerFind,
                  controller: _findController,
                  focusNode: _findFocus,
                  autofocus: true,
                  fieldKey: const ValueKey('kit-viewer-find'),
                  onChanged: _onQuery,
                  onSubmitted: (_) {
                    _step(1);
                    _findFocus.requestFocus();
                  },
                ),
              ),
              SizedBox(width: tokens.space1),
              // Up and down: never mirrored.
              KitIconButton(
                key: const ValueKey('kit-viewer-find-previous'),
                icon: AppIconography.chevronUp,
                tooltip: l10n.kitViewerFindPrevious,
                shortcut: 'Shift+Enter',
                onPressed: hasMarks ? () => _step(-1) : null,
              ),
              KitIconButton(
                key: const ValueKey('kit-viewer-find-next'),
                icon: AppIconography.chevronDown,
                tooltip: l10n.kitViewerFindNext,
                shortcut: 'Enter',
                onPressed: hasMarks ? () => _step(1) : null,
              ),
              KitIconButton(
                key: const ValueKey('kit-viewer-find-close'),
                icon: AppIconography.close,
                tooltip: l10n.kitViewerFindClose,
                shortcut: 'Esc',
                onPressed: _closeFind,
              ),
            ],
          ),
          if (_query.isNotEmpty)
            Padding(
              padding: EdgeInsetsDirectional.only(top: tokens.space2),
              child: KitText(
                _countText(l10n),
                key: const ValueKey('kit-viewer-find-count'),
                role: KitTextRole.secondary,
                tabular: true,
              ),
            ),
        ],
      ),
    );
  }

  Widget? _truncationNotice(AppLocalizations l10n) {
    final content = _content;
    if (content == null || _isEmpty) return null;
    String? message;
    final code = _codeText;
    if (code != null &&
        (content.kind == KitViewerKind.text ||
            content.kind == KitViewerKind.code ||
            _sourceView)) {
      final prepared = _prepare(code);
      if (content._truncated || prepared.capped) {
        final known = content._totalLines;
        final total = prepared.capped
            ? math.max(prepared.totalLines, known ?? 0)
            : known;
        message = total != null && total > prepared.shownLines
            ? l10n.kitViewerTruncated(prepared.shownLines, total)
            : l10n.kitViewerPartial;
      }
    } else if (content._truncated) {
      message = l10n.kitViewerPartial;
    }
    if (message == null) return null;
    final openAll = widget.onOpenAll;
    return KitNotice(
      message: message,
      liveRegion: false,
      actions: [
        if (openAll != null)
          KitAction(
            key: const ValueKey('kit-viewer-open-all'),
            label: l10n.kitViewerOpenAll,
            onPressed: openAll,
          ),
      ],
    );
  }

  Widget _body(BuildContext context, KitTokens tokens, AppLocalizations l10n) {
    if (_loading) {
      return SingleChildScrollView(
        physics: const NeverScrollableScrollPhysics(),
        padding: EdgeInsets.symmetric(horizontal: tokens.gutter),
        child: const KitSkeletonRows(count: 6),
      );
    }
    final error = _error;
    if (error != null) {
      return _centred(
        tokens,
        KitStateView(
          icon: AppIconography.error,
          tone: AppStatusTone.failure,
          title: l10n.kitViewerLoadFailed(widget.name),
          size: KitStateSize.inline,
          details: KitRedact.text('$error'),
          primary: KitAction(
            key: const ValueKey('kit-viewer-retry'),
            label: l10n.kitTryAgain,
            onPressed: _retry,
          ),
        ),
      );
    }
    final content = _content;
    if (content == null) return const SizedBox.shrink();
    if (_isEmpty) {
      return Padding(
        padding: EdgeInsets.all(tokens.gutter),
        child: KitText(
          l10n.kitViewerEmpty,
          key: const ValueKey('kit-viewer-empty'),
          tone: KitTextTone.tertiary,
        ),
      );
    }
    final code = _codeText;
    if (code != null) {
      final lineNumbers = content.kind != KitViewerKind.text;
      return _codeBody(
        context,
        tokens,
        _prepare(code),
        language: switch (content.kind) {
          KitViewerKind.code => content._language,
          KitViewerKind.markdown => 'markdown',
          KitViewerKind.svg => 'xml',
          _ => null,
        },
        lineNumbers: lineNumbers,
        highlight: content.kind != KitViewerKind.text,
        initialLine: content._initialLine,
      );
    }
    return switch (content.kind) {
      KitViewerKind.markdown => _Prose(
        source: content._text ?? '',
        interactive: widget.interactive,
        wrap: widget.wrap,
        onWrapChanged: widget.onWrapChanged,
      ),
      KitViewerKind.image => KitZoom(
        label: widget.name,
        resetKey: content,
        child: Center(
          child: KitImage(
            source: KitImageSource.memory(content._bytes!),
            semanticsLabel: content._semanticsLabel ?? widget.name,
            imageKey: const ValueKey('kit-viewer-image'),
          ),
        ),
      ),
      KitViewerKind.pdf => _KitPdfBody(content: content, name: widget.name),
      KitViewerKind.svg => KitZoom(
        label: widget.name,
        resetKey: content,
        child: Center(
          child: Padding(
            padding: EdgeInsets.all(tokens.space4),
            child: SvgPicture.string(
              content._text!,
              fit: BoxFit.contain,
              semanticsLabel: widget.name,
              errorBuilder: (context, error, stack) =>
                  KitText(l10n.kitViewerCantShow, tone: KitTextTone.tertiary),
            ),
          ),
        ),
      ),
      KitViewerKind.delimited => _KitTable(
        key: const ValueKey('kit-viewer-table'),
        rows: content._rows,
        header: content._header,
      ),
      KitViewerKind.binary => _centred(
        tokens,
        KitStateView(
          icon: AppIconography.file,
          title: l10n.kitViewerCantShow,
          body: l10n.kitViewerCantShowBody(
            content._mimeType ?? l10n.kitViewerUnknownType,
            _sizeInWords(context, content._byteLength) ??
                l10n.kitViewerUnknownSize,
          ),
          size: KitStateSize.inline,
          primary: widget.primary,
        ),
      ),
      _ => const SizedBox.shrink(),
    };
  }

  // The inline KitStateView brings its own gutter at the sides; adding
  // another would put it at twice the gutter, off the name's line.
  Widget _centred(KitTokens tokens, Widget child) => Center(
    child: SingleChildScrollView(
      padding: EdgeInsets.symmetric(vertical: tokens.gutter),
      child: child,
    ),
  );

  Widget _codeBody(
    BuildContext context,
    KitTokens tokens,
    _Prepared prepared, {
    required String? language,
    required bool lineNumbers,
    required bool highlight,
    int? initialLine,
  }) {
    final wrap = _effectiveWrap(context);
    final block = KitCodeBlock.fill(
      text: prepared.shown,
      language: language,
      wrap: wrap,
      lineNumbers: lineNumbers,
      highlight: highlight,
      marks: _findOpen ? _marks : const [],
      activeMark: _findOpen && _marks.isNotEmpty ? _active : null,
      initialLine: initialLine,
    );
    // The body starts on the same gutter as the header's name.
    final inset = EdgeInsetsDirectional.symmetric(
      horizontal: tokens.gutter,
      vertical: tokens.space2,
    );
    Widget body;
    if (wrap) {
      body = Padding(padding: inset, child: block);
    } else {
      // One horizontal scroller for the whole file: the block is laid out
      // as wide as its longest line (it needs a bounded width).
      final style = KitText.styleOf(context, KitTextRole.mono);
      final scaler = MediaQuery.textScalerOf(context);
      final digits = lineNumbers ? '${prepared.shownLines}'.length : 0;
      final painter = TextPainter(
        text: TextSpan(
          text: '${''.padLeft(digits, '9')} ${prepared.longest}',
          style: style,
        ),
        textDirection: TextDirection.ltr,
        textScaler: scaler,
        maxLines: 1,
      )..layout();
      final natural =
          (painter.width + tokens.space2 + tokens.gutter * 2 + tokens.space4)
              .ceilToDouble();
      painter.dispose();
      body = LayoutBuilder(
        builder: (context, constraints) {
          final width = math.max(constraints.maxWidth, natural);
          return Scrollbar(
            controller: _codeHorizontal,
            thumbVisibility: KitLayout.finePointer(context),
            notificationPredicate: (n) => n.depth == 0,
            child: SingleChildScrollView(
              controller: _codeHorizontal,
              scrollDirection: Axis.horizontal,
              child: SizedBox(
                width: width,
                height: constraints.maxHeight,
                child: Padding(padding: inset, child: block),
              ),
            ),
          );
        },
      );
    }
    return Directionality(
      textDirection: TextDirection.ltr,
      child: ColoredBox(color: tokens.detailsSurface, child: body),
    );
  }
}
