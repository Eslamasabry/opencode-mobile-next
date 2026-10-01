part of 'kit_viewer.dart';

/// A byte count in words: "2.4 MB". Null when unknown.
String? _sizeInWords(BuildContext context, int? bytes) {
  if (bytes == null || bytes < 0) return null;
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  var value = bytes.toDouble();
  var unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit++;
  }
  final format = NumberFormat.decimalPattern(
    Localizations.localeOf(context).toString(),
  )..maximumFractionDigits = unit == 0 || value >= 10 ? 0 : 1;
  return '${format.format(value)} ${units[unit]}';
}

/// Markdown as prose on the reading rails, centred at
/// [KitLayout.readingWidth] on a wide window.
class _Prose extends StatefulWidget {
  const _Prose({
    required this.source,
    required this.interactive,
    this.wrap,
    this.onWrapChanged,
  });

  final String source;
  final bool interactive;
  final bool? wrap;
  final ValueChanged<bool>? onWrapChanged;

  @override
  State<_Prose> createState() => _ProseState();
}

class _ProseState extends State<_Prose> {
  final ScrollController _controller = ScrollController();

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    return Scrollbar(
      controller: _controller,
      thumbVisibility: KitLayout.finePointer(context),
      child: SingleChildScrollView(
        controller: _controller,
        padding: EdgeInsets.symmetric(
          horizontal: tokens.gutter,
          vertical: tokens.space4,
        ),
        child: Align(
          alignment: Alignment.topCenter,
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: KitLayout.readingWidth),
            child: KitMarkdown(
              widget.source,
              interactive: widget.interactive,
              codeWrap: widget.wrap,
              onCodeWrapChanged: widget.onWrapChanged,
            ),
          ),
        ),
      ),
    );
  }
}

// --- PDF ----------------------------------------------------------------------

class _KitPdfBody extends StatefulWidget {
  const _KitPdfBody({required this.content, required this.name});

  final KitViewerContent content;
  final String name;

  @override
  State<_KitPdfBody> createState() => _KitPdfBodyState();
}

class _KitPdfBodyState extends State<_KitPdfBody> {
  final ScrollController _controller = ScrollController();

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final content = widget.content;
    final wide = KitLayout.windowOf(context).isWide;
    return LayoutBuilder(
      builder: (context, constraints) {
        final pageWidth = math
            .min(
              constraints.maxWidth - tokens.space3 * 2,
              KitLayout.readingWidth,
            )
            .floorToDouble();
        return KitZoom(
          label: widget.name,
          resetKey: content,
          child: Scrollbar(
            controller: _controller,
            thumbVisibility: KitLayout.finePointer(context),
            child: ListView.builder(
              controller: _controller,
              // PERF-4: a page renders only while it is on screen.
              scrollCacheExtent: const ScrollCacheExtent.pixels(0),
              padding: EdgeInsets.symmetric(vertical: tokens.space3),
              itemCount: content._pageCount,
              itemBuilder: (context, index) => Center(
                child: _KitPdfPageView(
                  key: ValueKey('kit-viewer-page-$index'),
                  index: index,
                  count: content._pageCount,
                  width: pageWidth,
                  rounded: wide,
                  render: content._renderPage!,
                  cancel: content._cancelPage,
                ),
              ),
            ),
          ),
        );
      },
    );
  }
}

class _KitPdfPageView extends StatefulWidget {
  const _KitPdfPageView({
    super.key,
    required this.index,
    required this.count,
    required this.width,
    required this.rounded,
    required this.render,
    this.cancel,
  });

  final int index, count;
  final double width;
  final bool rounded;
  final Future<KitPdfPage> Function(int index, int widthPx) render;
  final void Function(int index)? cancel;

  @override
  State<_KitPdfPageView> createState() => _KitPdfPageViewState();
}

class _KitPdfPageViewState extends State<_KitPdfPageView> {
  KitPdfPage? _page;
  Object? _error;
  bool _pending = false;
  bool _requested = false;
  int _generation = 0;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (!_requested) _request();
  }

  @override
  void dispose() {
    _generation++;
    if (_pending) widget.cancel?.call(widget.index);
    super.dispose();
  }

  void _request() {
    _requested = true;
    final dpr = MediaQuery.devicePixelRatioOf(context);
    final widthPx = math.max(1, (widget.width * dpr).round());
    final generation = ++_generation;
    _pending = true;
    _error = null;
    Future.sync(() => widget.render(widget.index, widthPx)).then(
      (page) {
        if (!mounted || generation != _generation) return;
        setState(() {
          _page = page;
          _pending = false;
        });
      },
      onError: (Object error, StackTrace _) {
        if (!mounted || generation != _generation) return;
        setState(() {
          _error = error;
          _pending = false;
        });
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final l10n = AppLocalizations.of(context);
    final label = l10n.kitViewerPage(widget.index + 1, widget.count);
    final page = _page;
    final width = widget.width;
    final height = page != null && page.width > 0
        ? (width * page.height / page.width).roundToDouble()
        : (width * math.sqrt2).roundToDouble();
    final shape = widget.rounded ? KitShape.panel : KitShape.square;
    final Widget child;
    if (_error != null) {
      child = SizedBox(
        width: width,
        height: height,
        child: Center(
          child: SingleChildScrollView(
            child: KitStateView(
              icon: AppIconography.error,
              tone: AppStatusTone.failure,
              title: l10n.kitViewerPageFailed(widget.index + 1),
              size: KitStateSize.inline,
              primary: KitAction(
                key: ValueKey('kit-viewer-page-${widget.index}-retry'),
                label: l10n.kitTryAgain,
                onPressed: () => setState(_request),
              ),
            ),
          ),
        ),
      );
    } else if (page != null) {
      child = KitImage(
        source: KitImageSource.memory(page.bytes),
        semanticsLabel: label,
        width: width,
        height: height,
        shape: shape,
      );
    } else {
      child = Semantics(
        label: label,
        child: DecoratedBox(
          decoration: ShapeDecoration(
            color: tokens.detailsSurface,
            shape: tokens.shapeOf(shape),
          ),
          child: SizedBox(width: width, height: height),
        ),
      );
    }
    return Padding(
      padding: EdgeInsets.only(bottom: tokens.space3),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          child,
          SizedBox(height: tokens.space1),
          ExcludeSemantics(
            child: KitText(label, role: KitTextRole.caption, tabular: true),
          ),
        ],
      ),
    );
  }
}

// --- Delimited table ------------------------------------------------------------

class _KitTable extends StatefulWidget {
  const _KitTable({super.key, required this.rows, required this.header});

  final List<List<String>> rows;
  final bool header;

  @override
  State<_KitTable> createState() => _KitTableState();
}

class _KitTableState extends State<_KitTable> {
  final ScrollController _horizontal = ScrollController();
  final ScrollController _vertical = ScrollController();

  /// How many rows are measured to size the columns.
  static const _measuredRows = 100;

  /// Cells longer than this many characters do not widen their column.
  static const _measuredChars = 40;

  @override
  void dispose() {
    _horizontal.dispose();
    _vertical.dispose();
    super.dispose();
  }

  List<double> _columnWidths(BuildContext context, KitTokens tokens) {
    final columns = widget.rows.fold<int>(
      0,
      (count, row) => math.max(count, row.length),
    );
    final longest = List<String>.filled(columns, '');
    for (final row in widget.rows.take(_measuredRows)) {
      for (var c = 0; c < row.length; c++) {
        final cell = row[c].length > _measuredChars
            ? row[c].substring(0, _measuredChars)
            : row[c];
        if (cell.length > longest[c].length) longest[c] = cell;
      }
    }
    final style = KitText.styleOf(context, KitTextRole.secondary);
    final scaler = MediaQuery.textScalerOf(context);
    final min = tokens.minTarget * 2;
    const max = KitLayout.readingWidth / 3;
    return [
      for (final text in longest)
        () {
          final painter = TextPainter(
            text: TextSpan(text: text, style: style),
            textDirection: TextDirection.ltr,
            textScaler: scaler,
            maxLines: 1,
          )..layout();
          final width = painter.width;
          painter.dispose();
          return (width.clamp(min, max) + tokens.space3 * 2).ceilToDouble();
        }(),
    ];
  }

  Widget _row(
    BuildContext context,
    KitTokens tokens,
    List<String> cells,
    List<double> widths, {
    required bool header,
  }) {
    return DecoratedBox(
      decoration: BoxDecoration(
        border: Border(
          bottom: BorderSide(
            color: tokens.roles.hairline,
            width: KitTokens.hairlineWidth(context),
          ),
        ),
      ),
      child: ConstrainedBox(
        constraints: BoxConstraints(minHeight: tokens.rowHeight),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            for (var c = 0; c < widths.length; c++)
              SizedBox(
                width: widths[c],
                child: Padding(
                  padding: EdgeInsetsDirectional.symmetric(
                    horizontal: tokens.space3,
                    vertical: tokens.space2,
                  ),
                  child: Semantics(
                    label: c < cells.length ? cells[c] : '',
                    header: header,
                    child: ExcludeSemantics(
                      child: KitText(
                        KitBidi.auto(c < cells.length ? cells[c] : ''),
                        role: header
                            ? KitTextRole.label
                            : KitTextRole.secondary,
                        tone: KitTextTone.primary,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final widths = _columnWidths(context, tokens);
    final natural = widths.fold<double>(0, (sum, w) => sum + w);
    final header = widget.header && widget.rows.isNotEmpty
        ? widget.rows.first
        : null;
    final body = header == null ? widget.rows : widget.rows.sublist(1);
    final fine = KitLayout.finePointer(context);
    // The file's column order, left to right, whatever the locale; each
    // cell is isolated (KitBidi.auto) so its own words shape correctly.
    return Directionality(
      textDirection: TextDirection.ltr,
      child: LayoutBuilder(
        builder: (context, constraints) {
          final width = math.max(constraints.maxWidth, natural);
          return Scrollbar(
            controller: _horizontal,
            thumbVisibility: fine,
            child: SingleChildScrollView(
              controller: _horizontal,
              scrollDirection: Axis.horizontal,
              child: SizedBox(
                width: width,
                height: constraints.maxHeight,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    if (header != null)
                      KeyedSubtree(
                        key: const ValueKey('kit-viewer-table-header'),
                        child: _row(
                          context,
                          tokens,
                          header,
                          widths,
                          header: true,
                        ),
                      ),
                    Expanded(
                      child: Scrollbar(
                        controller: _vertical,
                        thumbVisibility: fine,
                        child: ListView.builder(
                          controller: _vertical,
                          itemCount: body.length,
                          itemBuilder: (context, index) => KeyedSubtree(
                            key: ValueKey('kit-viewer-table-row-$index'),
                            child: _row(
                              context,
                              tokens,
                              body[index],
                              widths,
                              header: false,
                            ),
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          );
        },
      ),
    );
  }
}
