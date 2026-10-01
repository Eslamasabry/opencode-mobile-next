part of '../kit_diff_view.dart';

// The diff view's rows, gutter and gap bars.

extension _KitDiffRows on _KitDiffViewState {
  Widget _gutterGestures(BuildContext anchor) {
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      // Each row's own node carries its select action.
      excludeFromSemantics: true,
      // A drag selects from the line it went down on.
      dragStartBehavior: DragStartBehavior.down,
      onTapUp: (details) {
        final hit = _gutterLineAt(details.globalPosition);
        if (hit == null) return;
        _select(
          hit.$1,
          hit.$2,
          extend: HardwareKeyboard.instance.isShiftPressed,
        );
      },
      onSecondaryTapUp: (details) {
        final hit = _gutterLineAt(details.globalPosition);
        if (hit == null) return;
        _lineMenu(hit.$1, hit.$2, details.globalPosition, anchor);
      },
      onVerticalDragStart: (details) {
        final hit = _gutterLineAt(details.globalPosition);
        _dragSide = hit?.$1;
        if (hit != null) _select(hit.$1, hit.$2);
      },
      onVerticalDragUpdate: (details) {
        final side = _dragSide;
        if (side != null) _dragTo(side, details.globalPosition);
      },
      onVerticalDragEnd: (_) => _dragSide = null,
      child: const SizedBox.expand(),
    );
  }

  Widget _itemView(
    BuildContext context,
    KitTokens tokens,
    _Item item,
    _Geometry g,
  ) => switch (item) {
    _LineItem(:final line) => _unifiedRow(context, tokens, line, g),
    _PairItem(:final old, :final current) => _splitRow(
      context,
      tokens,
      old,
      current,
      g,
    ),
    _HunkItem(:final seg, :final line) => _hunkRow(tokens, seg, line, g),
    _GapItem(:final seg, :final gap) => _gapView(context, tokens, seg, gap, g),
  };

  Widget _hunkRow(KitTokens tokens, int seg, KitDiffLine line, _Geometry g) {
    final match = KitDiffFile._hunkHeader.firstMatch(line.text);
    var label = line.text;
    if (match != null) {
      final newStart = int.parse(match.group(3)!);
      final newCount = int.parse(match.group(4) ?? '1');
      final oldStart = int.parse(match.group(1)!);
      final oldCount = int.parse(match.group(2) ?? '1');
      final (start, count) = newCount > 0
          ? (newStart, newCount)
          : (oldStart, oldCount);
      label = _l10n.kitDiffLines(start, start + math.max<int>(count, 1) - 1);
    }
    final text = Padding(
      padding: EdgeInsetsDirectional.only(
        start: g.indent,
        top: tokens.space1,
        bottom: tokens.space1,
      ),
      child: KitText(
        label,
        role: KitTextRole.mono,
        tone: KitTextTone.secondary,
      ),
    );
    final selectable =
        g.selectable &&
        _FileIndex.of(widget.files[_file]).hunks.any((h) => h.header == seg);
    if (!selectable) return text;
    // With selection on, the range selects its whole hunk.
    return _Registered(
      register: _blockers.add,
      unregister: _blockers.remove,
      child: KitTappable(
        key: ValueKey('${widget.keyPrefix}-hunk-$_file-$seg'),
        onTap: () => _selectHunk(seg),
        tooltip: _l10n.kitDiffSelectHunk,
        child: Align(alignment: AlignmentDirectional.centerStart, child: text),
      ),
    );
  }

  Color? _tint(ThemeRoles roles, KitDiffLineKind kind) => switch (kind) {
    KitDiffLineKind.added => roles.codeAddedSurface,
    KitDiffLineKind.removed => roles.codeRemovedSurface,
    _ => null,
  };

  String _glyph(KitDiffLineKind kind) => switch (kind) {
    KitDiffLineKind.added => '+',
    KitDiffLineKind.removed => '−',
    _ => '',
  };

  /// "Line 12 added: …", "Line 9 removed: …", "Line 14: …": every line is
  /// read with its number, and a blank line still has a name.
  String _lineLabel(KitDiffLine line, [KitDiffSide? side]) {
    final l10n = _l10n;
    final name = switch (line.kind) {
      KitDiffLineKind.added => l10n.kitDiffLineAdded(line.newNo ?? 0),
      KitDiffLineKind.removed => l10n.kitDiffLineRemoved(line.oldNo ?? 0),
      _ => l10n.kitDiffLine(
        (side == KitDiffSide.old ? line.oldNo : line.newNo) ?? line.oldNo ?? 0,
      ),
    };
    return line.text.trim().isEmpty ? name : '$name: ${line.text}';
  }

  /// The side a line belongs to when its row is selected as a whole.
  KitDiffSide _sideOf(KitDiffLine line) => line.kind == KitDiffLineKind.removed
      ? KitDiffSide.old
      : KitDiffSide.current;

  int? _numberOn(KitDiffLine line, KitDiffSide side) =>
      side == KitDiffSide.old ? line.oldNo : line.newNo;

  Widget _numberCell(KitTokens tokens, KitDiffSide side, int? n, _Geometry g) {
    final roles = tokens.roles;
    final selected = _isSelected(side, n);
    // The row stays at the text's line height; the gutter's hit area over
    // the list gives the number its 48 dp reach (A11Y-2, _gutterLineAt).
    final text = SizedBox(
      width: g.number,
      child: Padding(
        padding: EdgeInsetsDirectional.only(end: tokens.space1),
        child: Text(
          n?.toString() ?? '',
          textAlign: TextAlign.end,
          style: KitText.styleOf(
            context,
            KitTextRole.mono,
          ).copyWith(color: selected ? roles.accent : roles.text3),
        ),
      ),
    );
    if (!g.selectable || n == null) return text;
    return DecoratedBox(
      key: ValueKey('${widget.keyPrefix}-line-$_file-${side.name}-$n'),
      decoration: BoxDecoration(
        border: BorderDirectional(
          start: BorderSide(
            color: selected ? roles.accent : Colors.transparent,
            width: KitTokens.focusRingWidth(context),
          ),
        ),
      ),
      child: text,
    );
  }

  Widget _glyphCell(KitTokens tokens, KitDiffLine? line, _Geometry g) {
    final roles = tokens.roles;
    final kind = line?.kind;
    return SizedBox(
      width: g.glyph,
      child: Text(
        kind == null ? '' : _glyph(kind),
        textAlign: TextAlign.center,
        style: KitText.styleOf(context, KitTextRole.mono).copyWith(
          color: switch (kind) {
            KitDiffLineKind.added => roles.success,
            KitDiffLineKind.removed => roles.codeRemoved,
            _ => roles.text3,
          },
        ),
      ),
    );
  }

  Widget _textCell(KitTokens tokens, KitDiffLine? line, _Geometry g) {
    final text = Text(
      line?.text ?? '',
      softWrap: g.wrap,
      overflow: TextOverflow.clip,
      style: KitText.styleOf(
        context,
        KitTextRole.mono,
      ).copyWith(color: tokens.roles.text1),
    );
    return Padding(
      padding: EdgeInsetsDirectional.only(end: tokens.space2),
      child: text,
    );
  }

  Widget _rowFrame({
    required Widget child,
    required String label,
    required Color? tint,
    required bool selected,
    required KitDiffSide side,
    required int? number,
    required _Geometry g,
    required KitTokens tokens,
    List<(KitDiffSide, int?, double)> gutter = const [],
  }) {
    final roles = tokens.roles;
    final color = selected
        ? Color.alphaBlend(
            roles.accent.withValues(alpha: tokens.markTintAlpha),
            tint ?? tokens.detailsSurface,
          )
        : tint;
    // Always a ColoredBox: a row that gains a tint when selected keeps its
    // element tree.
    Widget row = ColoredBox(color: color ?? Colors.transparent, child: child);
    final selectable = g.selectable && number != null;
    final cells = [
      for (final (cellSide, n, dx) in gutter)
        if (n != null) (cellSide, n, dx),
    ];
    if (g.selectable && cells.isNotEmpty) {
      row = _Registered(
        register: (ctx) {
          for (final (cellSide, n, dx) in cells) {
            _cells[(cellSide, n)] = (ctx, dx);
          }
        },
        unregister: (ctx) {
          for (final (cellSide, n, _) in cells) {
            if (identical(_cells[(cellSide, n)]?.$1, ctx)) {
              _cells.remove((cellSide, n));
            }
          }
        },
        child: row,
      );
    }
    row = Semantics(
      container: true,
      label: label,
      selected: g.selectable ? selected : null,
      onTap: selectable ? () => _select(side, number) : null,
      excludeSemantics: true,
      child: row,
    );
    if (!selectable) return row;
    final line = row;
    return Builder(
      builder: (anchor) => GestureDetector(
        // The row's own node carries the select action (above); the
        // right-click menu is a pointer twin of the selection bar.
        excludeFromSemantics: true,
        onSecondaryTapUp: (details) =>
            _lineMenu(side, number, details.globalPosition, anchor),
        child: line,
      ),
    );
  }

  Widget _unifiedRow(
    BuildContext context,
    KitTokens tokens,
    KitDiffLine line,
    _Geometry g,
  ) {
    final side = _sideOf(line);
    final number = _numberOn(line, side);
    final cells = Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _numberCell(tokens, KitDiffSide.old, line.oldNo, g),
        _numberCell(tokens, KitDiffSide.current, line.newNo, g),
        _glyphCell(tokens, line, g),
        if (g.textWidth != null)
          SizedBox(width: g.textWidth, child: _textCell(tokens, line, g))
        else
          Expanded(child: _textCell(tokens, line, g)),
      ],
    );
    return _rowFrame(
      child: cells,
      label: _lineLabel(line),
      tint: _tint(tokens.roles, line.kind),
      selected:
          _isSelected(KitDiffSide.old, line.oldNo) ||
          _isSelected(KitDiffSide.current, line.newNo),
      side: side,
      number: number,
      g: g,
      tokens: tokens,
      gutter: [
        (KitDiffSide.old, line.oldNo, 0),
        (KitDiffSide.current, line.newNo, g.number),
      ],
    );
  }

  Widget _half(
    KitTokens tokens,
    KitDiffLine? line,
    KitDiffSide side,
    _Geometry g,
  ) {
    final number = line == null ? null : _numberOn(line, side);
    final cells = Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _numberCell(tokens, side, number, g),
        _glyphCell(tokens, line, g),
        Expanded(child: _textCell(tokens, line, g)),
      ],
    );
    if (line == null) return cells;
    return _rowFrame(
      child: cells,
      label: _lineLabel(line, side),
      tint: _tint(tokens.roles, line.kind),
      selected: _isSelected(side, number),
      side: side,
      number: number,
      g: g,
      tokens: tokens,
      gutter: [(side, number, 0)],
    );
  }

  Widget _splitRow(
    BuildContext context,
    KitTokens tokens,
    KitDiffLine? old,
    KitDiffLine? current,
    _Geometry g,
  ) {
    final divider = KitTokens.hairlineWidth(context);
    final halfWidth = g.textWidth == null
        ? null
        : g.number + g.glyph + g.textWidth!;
    Widget side(Widget child) => halfWidth == null
        ? Expanded(child: child)
        : SizedBox(width: halfWidth, child: child);
    return IntrinsicHeight(
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          side(_half(tokens, old, KitDiffSide.old, g)),
          ColoredBox(
            color: tokens.roles.hairline,
            child: SizedBox(width: divider),
          ),
          side(_half(tokens, current, KitDiffSide.current, g)),
        ],
      ),
    );
  }

  Widget _gapView(
    BuildContext context,
    KitTokens tokens,
    int seg,
    KitDiffGap gap,
    _Geometry g,
  ) {
    final l10n = _l10n;
    final lines = gap.lines;
    final top = (_shownTop[seg] ?? 0).clamp(0, gap.count);
    final bottom = (_shownBottom[seg] ?? 0).clamp(0, gap.count - top);
    final remaining = gap.count - top - bottom;
    final expandable = lines != null;
    final step = remaining.clamp(1, KitDiffView.expandStep);
    final indent = g.indent;

    Widget rowsOf(Iterable<KitDiffLine> ls) => Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        for (final line in ls)
          g.split
              ? _splitRow(context, tokens, line, line, g)
              : _unifiedRow(context, tokens, line, g),
      ],
    );

    Widget bar(_GapBar bar) => g.selectable
        ? _Registered(
            register: _blockers.add,
            unregister: _blockers.remove,
            child: bar,
          )
        : bar;

    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (expandable && top + bottom > 0)
          bar(
            _GapBar(
              key: ValueKey('${widget.keyPrefix}-collapse-$seg'),
              label: l10n.kitDiffHideUnchanged,
              icon: AppIconography.unfoldLess,
              indent: indent,
              onTap: () => _update(() {
                _shownTop.remove(seg);
                _shownBottom.remove(seg);
              }),
            ),
          ),
        KitReveal(
          child: expandable && top > 0 ? rowsOf(lines.take(top)) : null,
        ),
        if (remaining > 0 && expandable && seg > 0)
          bar(
            _GapBar(
              key: ValueKey('${widget.keyPrefix}-expand-down-$seg'),
              label: l10n.kitDiffShowUnchanged(step),
              icon: AppIconography.chevronDown,
              indent: indent,
              onTap: () =>
                  _update(() => _shownTop[seg] = top + KitDiffView.expandStep),
            ),
          ),
        if (remaining > 0)
          bar(
            _GapBar(
              key: ValueKey('${widget.keyPrefix}-gap-$seg'),
              label: expandable
                  ? l10n.kitDiffShowUnchanged(step)
                  : l10n.kitDiffUnchangedCount(remaining),
              icon: expandable ? AppIconography.chevronUp : null,
              indent: indent,
              onTap: expandable
                  ? () => _update(
                      () => _shownBottom[seg] = bottom + KitDiffView.expandStep,
                    )
                  : null,
            ),
          ),
        KitReveal(
          child: expandable && bottom > 0
              ? rowsOf(lines.skip(gap.count - bottom))
              : null,
        ),
      ],
    );
  }
}

/// Registers its context while built, so the gutter's hit area can find
/// the row (or the gap bar) under the pointer.
class _Registered extends StatefulWidget {
  const _Registered({
    required this.register,
    required this.unregister,
    required this.child,
  });

  final void Function(BuildContext context) register;
  final void Function(BuildContext context) unregister;
  final Widget child;

  @override
  State<_Registered> createState() => _RegisteredState();
}

class _RegisteredState extends State<_Registered> {
  @override
  void initState() {
    super.initState();
    widget.register(context);
  }

  @override
  void didUpdateWidget(_Registered old) {
    super.didUpdateWidget(old);
    widget.register(context);
  }

  @override
  void dispose() {
    widget.unregister(context);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => widget.child;
}

/// `+n −n` in the success and removed code roles.
class _Counts extends StatelessWidget {
  const _Counts({required this.added, required this.removed});

  final int added, removed;

  @override
  Widget build(BuildContext context) {
    final roles = KitTokens.of(context).roles;
    final mono = KitText.styleOf(context, KitTextRole.mono);
    return Text.rich(
      TextSpan(
        children: [
          TextSpan(
            text: '+$added',
            style: mono.copyWith(color: roles.success),
          ),
          const TextSpan(text: ' '),
          TextSpan(
            text: '−$removed',
            style: mono.copyWith(color: roles.codeRemoved),
          ),
        ],
      ),
      textDirection: TextDirection.ltr,
    );
  }
}

/// A full-width bar standing in for folded unchanged lines, or offering to
/// reveal or hide them. 48 dp tall; a bar with no [onTap] only states.
class _GapBar extends StatelessWidget {
  const _GapBar({
    super.key,
    required this.label,
    required this.icon,
    required this.indent,
    required this.onTap,
  });

  final String label;
  final IconData? icon;
  final double indent;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final start = math.max(0.0, indent - tokens.smallIconSize - tokens.space2);
    final content = ConstrainedBox(
      constraints: BoxConstraints(minHeight: tokens.minTarget),
      child: Padding(
        padding: EdgeInsetsDirectional.only(start: start, end: tokens.space2),
        child: Row(
          children: [
            SizedBox(
              width: tokens.smallIconSize + tokens.space2,
              child: icon == null
                  ? null
                  : Icon(icon, size: tokens.smallIconSize, color: roles.text2),
            ),
            Flexible(
              child: Padding(
                padding: EdgeInsets.symmetric(vertical: tokens.space2),
                child: KitText(label, role: KitTextRole.secondary),
              ),
            ),
          ],
        ),
      ),
    );
    final bar = ColoredBox(
      color: roles.surface2,
      child: onTap == null
          ? Semantics(
              container: true,
              label: label,
              excludeSemantics: true,
              child: content,
            )
          : Material(
              type: MaterialType.transparency,
              child: InkWell(onTap: onTap, child: content),
            ),
    );
    return Semantics(button: onTap != null, child: bar);
  }
}

/// Takes a pointer only where [claims] says a line's number is within
/// reach; everywhere else the lines below get it (text selection, gap bars,
/// the row's right-click menu).
class _GutterHitArea extends SingleChildRenderObjectWidget {
  const _GutterHitArea({required this.claims, super.child});

  final bool Function(Offset global) claims;

  @override
  RenderObject createRenderObject(BuildContext context) =>
      _RenderGutterHitArea(claims);

  @override
  void updateRenderObject(
    BuildContext context,
    _RenderGutterHitArea renderObject,
  ) => renderObject.claims = claims;
}

class _RenderGutterHitArea extends RenderProxyBox {
  _RenderGutterHitArea(this.claims);

  bool Function(Offset global) claims;

  @override
  bool hitTest(BoxHitTestResult result, {required Offset position}) {
    if (!size.contains(position) || !claims(localToGlobal(position))) {
      return false;
    }
    return super.hitTest(result, position: position);
  }
}
