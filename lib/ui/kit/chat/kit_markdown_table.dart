// Tables in an agent's Markdown: the pipe-table scanner and the part that
// draws one (split from kit_markdown.dart to keep it under the file budget).
part of 'kit_markdown.dart';

// ---------------------------------------------------------------------------
// Tables.

List<String> _tableCells(String line) {
  final trimmed = line.trim();
  if (!trimmed.contains('|')) return const [];
  final cells = <String>[];
  var cell = StringBuffer();
  var start = trimmed.startsWith('|') ? 1 : 0;
  var endedWithSeparator = false;
  while (start < trimmed.length) {
    final char = trimmed[start];
    if (char == r'\' && start + 1 < trimmed.length) {
      final next = trimmed[start + 1];
      if (next == '|') {
        cell.write('|');
        start += 2;
        endedWithSeparator = false;
        continue;
      }
      if (next == r'\') {
        cell.write(r'\\');
        start += 2;
        endedWithSeparator = false;
        continue;
      }
    }
    if (char == '|') {
      cells.add(cell.toString().trim());
      cell = StringBuffer();
      endedWithSeparator = true;
    } else {
      cell.write(char);
      endedWithSeparator = false;
    }
    start++;
  }
  if (!endedWithSeparator) cells.add(cell.toString().trim());
  return cells;
}

bool _isTableDelimiter(String line) {
  final cells = _tableCells(line);
  return cells.length >= 2 &&
      cells.every((cell) => RegExp(r'^:?-{3,}:?$').hasMatch(cell));
}

/// A cell that is one inline code span and nothing else: it never breaks
/// mid-token.
bool _isMonoCell(String cell) => RegExp(r'^`[^`\n]+`$').hasMatch(cell.trim());

/// A cell's words as they are drawn (markup dropped), for measuring.
String _plainOf(String source) =>
    KitMarkdownImage.withAltText(source).replaceAllMapped(
      _renderPattern,
      (m) =>
          m.group(7) ??
          m.group(6) ??
          m.group(1) ??
          m.group(2) ??
          m.group(3) ??
          m.group(4) ??
          m.group(5) ??
          m.group(9) ??
          '',
    );

class _KitMdTable extends StatefulWidget {
  const _KitMdTable({
    required this.headers,
    required this.delimiter,
    required this.rows,
  });

  final List<String> headers;
  final List<String> delimiter;
  final List<List<String>> rows;

  @override
  State<_KitMdTable> createState() => _KitMdTableState();
}

class _KitMdTableState extends State<_KitMdTable> {
  /// The frame as a surface: its lines are the style's dividers, and the
  /// header band sits on its face, as on a panel's.
  static const _hostingFrame = KitSurfaceDecoration(
    fill: Colors.transparent,
    hosts: true,
  );

  final _controller = ScrollController();

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  String _cellAt(List<String> cells, int index) =>
      index < cells.length ? cells[index] : '';

  TextAlign _alignmentAt(int index) {
    final delimiter = widget.delimiter;
    final value = index < delimiter.length ? delimiter[index].trim() : '';
    if (value.startsWith(':') && value.endsWith(':')) return TextAlign.center;
    return value.endsWith(':') ? TextAlign.end : TextAlign.start;
  }

  KeyEventResult _onKey(FocusNode node, KeyEvent event) {
    if (event is KeyUpEvent || !_controller.hasClients) {
      return KeyEventResult.ignored;
    }
    final step = KitTokens.of(context).minTarget;
    final double delta;
    if (event.logicalKey == LogicalKeyboardKey.arrowRight) {
      delta = step;
    } else if (event.logicalKey == LogicalKeyboardKey.arrowLeft) {
      delta = -step;
    } else {
      return KeyEventResult.ignored;
    }
    final position = _controller.position;
    _controller.jumpTo(
      (_controller.offset + delta).clamp(0.0, position.maxScrollExtent),
    );
    return KeyEventResult.handled;
  }

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final scope = _KitMdScope.of(context);
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    // The table's lines are the style's dividers.
    final line = KitSurfaceLook.divider(
      context,
      color: roles.hairline,
      width: KitTokens.hairlineWidth(context),
    );
    final hairline = BorderSide.none.copyWith(
      style: BorderStyle.solid,
      color: line.color,
      width: line.width,
    );
    final columns = widget.headers.length;
    final allRows = [widget.headers, ...widget.rows];
    final allMono = allRows.every(
      (row) => [
        for (var c = 0; c < columns; c++) _cellAt(row, c),
      ].every((cell) => cell.isEmpty || _isMonoCell(cell)),
    );

    return LayoutBuilder(
      builder: (context, constraints) {
        final available = constraints.hasBoundedWidth
            ? constraints.maxWidth
            : MediaQuery.sizeOf(context).width;
        final widths = _columnWidths(context, scope, allRows, available);
        // One widget row per Markdown row, each merged for the screen reader
        // so the table reads row by row; cells line up on their first
        // baseline (a mono address beside body text).
        Widget rowOf(BuildContext context, int r) {
          final own = BoxDecoration(
            color: r == 0 ? roles.surface1 : null,
            border: r == 0 ? null : Border(top: hairline),
          );
          return MergeSemantics(
            child: DecoratedBox(
              // The header band is a surface in the table's frame: a style
              // that keeps one face per stack sets it on the frame's face,
              // inside the frame's lines rather than over them.
              decoration: r == 0
                  ? KitSurfaceLook.decorate(
                      context,
                      solid: own,
                      level: KitSurfaceLevel.surface1,
                    )
                  : own,
              child: Row(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.baseline,
                textBaseline: TextBaseline.alphabetic,
                children: [
                  for (var c = 0; c < columns; c++)
                    SizedBox(
                      width: widths.cells[c],
                      child: Padding(
                        padding: EdgeInsets.symmetric(
                          horizontal: tokens.space3,
                          vertical: tokens.space2,
                        ),
                        child: _KitMdText(
                          source: _cellAt(allRows[r], c),
                          role: scope.role,
                          tone: scope.tone,
                          header: r == 0,
                          textAlign: _alignmentAt(c),
                          detectDirection: false,
                        ),
                      ),
                    ),
                ],
              ),
            ),
          );
        }

        final table = DecoratedBox(
          decoration: BoxDecoration(border: Border.fromBorderSide(hairline)),
          // The frame hosts the header band (the nesting rule).
          child: KitSurfaceLook.castScope(
            context,
            decoration: _hostingFrame,
            child: Builder(
              builder: (context) => Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  for (var r = 0; r < allRows.length; r++) rowOf(context, r),
                ],
              ),
            ),
          ),
        );
        Widget body = table;
        if (!widths.fits) {
          // A table wider than its box scrolls inside it, never the page,
          // with a visible bar on a fine pointer (KIT-6). Tab reaches it and
          // the arrow keys scroll it.
          body = Focus(
            onKeyEvent: _onKey,
            child: Scrollbar(
              controller: _controller,
              thumbVisibility: KitLayout.finePointer(context),
              child: SingleChildScrollView(
                controller: _controller,
                scrollDirection: Axis.horizontal,
                padding: EdgeInsets.only(bottom: tokens.space2),
                child: table,
              ),
            ),
          );
        } else {
          body = Align(
            alignment: AlignmentDirectional.centerStart,
            child: body,
          );
        }
        if (allMono) {
          body = Directionality(textDirection: TextDirection.ltr, child: body);
        }
        return Semantics(
          container: true,
          label: l10n.kitMarkdownTable(widget.rows.length),
          child: body,
        );
      },
    );
  }

  /// Natural widths when the table fits; otherwise every column keeps at
  /// least its longest word (a mono cell its whole token) and the rest is
  /// shared out. When even the longest words do not fit, the table scrolls.
  ({List<double> cells, bool fits}) _columnWidths(
    BuildContext context,
    _KitMdScope scope,
    List<List<String>> rows,
    double available,
  ) {
    final tokens = KitTokens.of(context);
    final pad = tokens.space3 * 2;
    final scaler = MediaQuery.textScalerOf(context);
    final body = KitText.styleOf(context, scope.role, tone: scope.tone);
    final header = body.copyWith(fontWeight: FontWeight.w600);
    final mono = KitText.styleOf(context, KitTextRole.mono);
    double measure(String text, TextStyle style) {
      if (text.isEmpty) return 0;
      final painter = TextPainter(
        text: TextSpan(text: text, style: style),
        textDirection: TextDirection.ltr,
        textScaler: scaler,
      )..layout();
      final width = painter.width.ceilToDouble();
      painter.dispose();
      return width;
    }

    final columns = widget.headers.length;
    final natural = List<double>.filled(columns, 0);
    final minimum = List<double>.filled(columns, 0);
    for (var r = 0; r < rows.length; r++) {
      for (var c = 0; c < columns; c++) {
        final cell = _cellAt(rows[r], c);
        final monoCell = _isMonoCell(cell);
        final style = monoCell ? mono : (r == 0 ? header : body);
        final plain = _plainOf(cell);
        final full = measure(plain, style);
        natural[c] = math.max(natural[c], full);
        final longestWord = monoCell
            ? full
            : plain
                  .split(RegExp(r'\s+'))
                  .map((word) => measure(word, style))
                  .fold<double>(0, math.max);
        minimum[c] = math.max(minimum[c], longestWord);
      }
    }
    // Hairlines add a little to the table; keep one logical pixel spare.
    final room = available - columns * pad - 1;
    final naturalSum = natural.fold<double>(0, (a, b) => a + b);
    if (naturalSum <= room) {
      return (cells: [for (final w in natural) w + pad], fits: true);
    }
    final minimumSum = minimum.fold<double>(0, (a, b) => a + b);
    if (minimumSum > room) {
      // Scrolls: each column wraps at a readable measure.
      final cap = tokens.minTarget * 6;
      return (
        cells: [
          for (var c = 0; c < columns; c++)
            math.max(minimum[c], math.min(natural[c], cap)) + pad,
        ],
        fits: false,
      );
    }
    // Fits once wide columns wrap: find the cap that fills the room.
    var low = 0.0;
    var high = natural.fold<double>(0, math.max);
    double sumAt(double cap) {
      var sum = 0.0;
      for (var c = 0; c < columns; c++) {
        sum += math.max(minimum[c], math.min(natural[c], cap));
      }
      return sum;
    }

    for (var step = 0; step < 24; step++) {
      final mid = (low + high) / 2;
      if (sumAt(mid) <= room) {
        low = mid;
      } else {
        high = mid;
      }
    }
    return (
      cells: [
        for (var c = 0; c < columns; c++)
          math.max(minimum[c], math.min(natural[c], low)).floorToDouble() + pad,
      ],
      fits: true,
    );
  }
}
