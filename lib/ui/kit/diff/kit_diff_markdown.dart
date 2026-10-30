part of '../kit_diff_view.dart';

// A Markdown file's changes, drawn as Markdown: each run of added, removed
// or unchanged lines is cut into its blocks (a heading, a paragraph, a list
// item, a quote, a table), and each block is drawn rendered with the first
// line's numbers and its + / − in the gutter, on its row's tint. Code fences
// stay code: their lines are drawn as the source rows they are. "Source"
// brings back the lines as they are written.

/// Whether [path] names a Markdown file (.md, .markdown, .mdx).
bool _isMarkdownPath(String path) {
  final name = path.split('/').last.toLowerCase();
  return name.endsWith('.md') ||
      name.endsWith('.markdown') ||
      name.endsWith('.mdx');
}

final _mdDiffFence = RegExp(r'^ {0,3}(`{3,}|~{3,})');
final _mdDiffHeading = RegExp(r'^ {0,3}#{1,6}(\s|$)');
final _mdDiffRule = RegExp(r'^\s*(-{3,}|\*{3,}|_{3,})\s*$');
final _mdDiffItem = RegExp(r'^(\s*)([-*+]|\d{1,9}[.)])\s+');

/// One block of a run of same-kind lines.
@immutable
class _MdUnit {
  const _MdUnit(this.lines, {required this.code});

  /// The source lines, in order; all of one kind.
  final List<KitDiffLine> lines;

  /// A code fence (or part of one): drawn as source rows.
  final bool code;

  KitDiffLineKind get kind => lines.first.kind;
  KitDiffLine get first => lines.first;
  KitDiffLine get last => lines.last;
  bool get blank => lines.every((l) => l.text.trim().isEmpty);

  /// The first [count] lines, as a block of its own (a preview's cut).
  _MdUnit take(int count) =>
      _MdUnit(List.unmodifiable(lines.take(count)), code: code);

  /// The Markdown to draw: a list item's or a paragraph's wrapped lines
  /// joined into one, so a hard-wrapped source reads as one sentence.
  String get markdown {
    if (lines.length == 1) return lines.first.text;
    final text = lines.first.text;
    if (_mdDiffItem.hasMatch(text)) {
      final out = <String>[];
      for (final line in lines) {
        if (_mdDiffItem.hasMatch(line.text) || out.isEmpty) {
          out.add(line.text);
        } else {
          out[out.length - 1] = '${out.last.trimRight()} ${line.text.trim()}';
        }
      }
      return out.join('\n');
    }
    if (text.trimLeft().startsWith('>') || text.trimLeft().startsWith('|')) {
      return lines.map((l) => l.text).join('\n');
    }
    return lines.map((l) => l.text.trim()).join(' ');
  }

  /// What a screen reader says for the block: the words, not the marks.
  String get spoken {
    final source = lines.map((l) => l.text).join('\n');
    try {
      return KitMarkdown.proseForSpeech(source);
    } on FormatException {
      return source;
    }
  }
}

/// Cuts [run] (lines of one kind, in order) into blocks. [inFence]: the
/// run starts inside a code fence opened above it.
List<_MdUnit> _mdUnits(List<KitDiffLine> run, {required bool inFence}) {
  final out = <_MdUnit>[];
  bool fence(int i) => _mdDiffFence.hasMatch(run[i].text);
  bool blank(int i) => run[i].text.trim().isEmpty;
  bool heading(int i) => _mdDiffHeading.hasMatch(run[i].text);
  bool rule(int i) => _mdDiffRule.hasMatch(run[i].text);
  bool quote(int i) => run[i].text.trimLeft().startsWith('>');
  bool table(int i) => run[i].text.trimLeft().startsWith('|');
  bool startsBlock(int i) =>
      blank(i) || fence(i) || heading(i) || rule(i) || quote(i) || table(i);
  var open = inFence;
  var i = 0;
  while (i < run.length) {
    final start = i;
    if (open || fence(i)) {
      // A fence, or the rest of one: its lines stay code. One that does
      // not close inside the run is still code to its last line.
      if (!open) i++;
      while (i < run.length && !fence(i)) {
        i++;
      }
      if (i < run.length) {
        i++;
        open = false;
      } else {
        open = true;
      }
      out.add(_MdUnit(List.unmodifiable(run.sublist(start, i)), code: true));
      continue;
    }
    if (quote(i) || table(i)) {
      final isQuote = quote(i);
      i++;
      while (i < run.length && (isQuote ? quote(i) : table(i))) {
        i++;
      }
    } else if (_mdDiffItem.firstMatch(run[i].text) case final item?) {
      // A list item and its wrapped lines and nested items.
      final indent = item.group(1)!.length;
      i++;
      while (i < run.length && !startsBlock(i)) {
        final next = _mdDiffItem.firstMatch(run[i].text);
        if (next != null && next.group(1)!.length <= indent) break;
        i++;
      }
    } else if (blank(i) || heading(i) || rule(i)) {
      i++;
    } else {
      i++;
      while (i < run.length &&
          !startsBlock(i) &&
          !_mdDiffItem.hasMatch(run[i].text)) {
        i++;
      }
    }
    out.add(_MdUnit(List.unmodifiable(run.sublist(start, i)), code: false));
  }
  return out;
}

/// A rendered block in the diff's list.
class _MdItem extends _Item {
  const _MdItem(super.seg, this.unit);
  final _MdUnit unit;
}

/// Where each line of a Markdown file stands against code fences, and the
/// file's rendered items, worked out once per [KitDiffFile].
class _MdFileIndex {
  _MdFileIndex(KitDiffFile file) {
    // Each side keeps its own fence state: an added fence opens only in the
    // new file. A gap whose lines are not known resets both.
    var oldOpen = false;
    var newOpen = false;
    void walk(KitDiffLine line) {
      final open = line.kind == KitDiffLineKind.removed ? oldOpen : newOpen;
      _inFence[line] = open;
      if (!_mdDiffFence.hasMatch(line.text)) return;
      if (line.kind != KitDiffLineKind.added) oldOpen = !oldOpen;
      if (line.kind != KitDiffLineKind.removed) newOpen = !newOpen;
    }

    for (final segment in file.segments) {
      if (segment is KitDiffLine && segment.kind != KitDiffLineKind.hunk) {
        walk(segment);
      } else if (segment is KitDiffGap) {
        final lines = segment.lines;
        if (lines == null) {
          oldOpen = false;
          newOpen = false;
        } else {
          lines.forEach(walk);
        }
      }
    }
    final segments = file.segments;
    var i = 0;
    while (i < segments.length) {
      final segment = segments[i];
      if (segment is KitDiffGap) {
        items.add(_GapItem(i, segment));
        i++;
        continue;
      }
      if (segment is! KitDiffLine) {
        i++;
        continue;
      }
      if (segment.kind == KitDiffLineKind.hunk) {
        items.add(_HunkItem(i, segment));
        i++;
        continue;
      }
      final start = i;
      final run = <KitDiffLine>[];
      while (i < segments.length) {
        final s = segments[i];
        if (s is! KitDiffLine || s.kind != segment.kind) break;
        run.add(s);
        i++;
      }
      var seg = start;
      for (final unit in _mdUnits(run, inFence: inFence(run.first))) {
        if (unit.code) {
          for (final line in unit.lines) {
            items.add(_LineItem(seg++, line));
          }
        } else {
          items.add(_MdItem(seg, unit));
          seg += unit.lines.length;
        }
      }
    }
  }

  final _inFence = HashMap<KitDiffLine, bool>.identity();
  final List<_Item> items = [];

  /// Whether [line] sits inside a code fence opened above it.
  bool inFence(KitDiffLine line) => _inFence[line] ?? false;

  static final _cache = Expando<_MdFileIndex>();
  static _MdFileIndex of(KitDiffFile file) =>
      _cache[file] ??= _MdFileIndex(file);
}

extension _KitDiffMarkdown on _KitDiffViewState {
  /// Whether [file]'s lines are drawn as Markdown now.
  bool _renderedFor(KitDiffFile file) =>
      !_source && !file.binary && _isMarkdownPath(file.path);

  /// "Source" / "Formatted": the one switch between the drawn
  /// Markdown and the lines as written.
  Widget _sourceSwitch(AppLocalizations l10n) => KitButton.tertiary(
    key: ValueKey('${widget.keyPrefix}-md-switch'),
    label: _source ? l10n.kitDiffShowFormatted : l10n.kitDiffShowSource,
    onPressed: () => _update(() {
      _source = !_source;
      _sourceSwitched = true;
      _mdSpans.clear();
      _targetItem = null;
    }),
  );

  /// The lines a run of [lines] (one gap's shown part) is drawn as.
  List<Widget> _renderedRows(
    BuildContext context,
    KitTokens tokens,
    List<KitDiffLine> lines,
    _Geometry g,
  ) {
    if (lines.isEmpty) return const [];
    final index = _MdFileIndex.of(widget.files[_file]);
    return [
      for (final unit in _mdUnits(lines, inFence: index.inFence(lines.first)))
        if (unit.code)
          for (final line in unit.lines) _unifiedRow(context, tokens, line, g)
        else
          _mdRow(context, tokens, unit, g),
    ];
  }

  /// One rendered block: the first line's numbers and its + / − at the
  /// start, the block drawn as Markdown after them, the row's tint behind.
  Widget _mdRow(
    BuildContext context,
    KitTokens tokens,
    _MdUnit unit,
    _Geometry g,
  ) {
    final first = unit.first;
    final last = unit.last;
    // A tap on the block's number selects all of its lines.
    for (final side in KitDiffSide.values) {
      final lo = _numberOn(first, side);
      final hi = _numberOn(last, side);
      if (lo != null && hi != null && hi > lo) _mdSpans[(side, lo)] = hi;
    }
    final side = _sideOf(first);
    final body = unit.blank
        ? SizedBox(height: g.lineHeight)
        : Padding(
            padding: EdgeInsetsDirectional.only(
              // One small step between blocks.
              bottom: tokens.space1,
              end: tokens.space2,
            ),
            child: KitMarkdown(
              unit.markdown,
              // Near the code's size, in the reading colour: a diff is
              // read line by line, not as a page of prose.
              role: KitTextRole.secondary,
              tone: KitTextTone.primary,
              // The diff's own selection area holds the text.
              selectable: false,
            ),
          );
    final cells = Row(
      crossAxisAlignment: CrossAxisAlignment.baseline,
      textBaseline: TextBaseline.alphabetic,
      children: [
        _numberCell(tokens, KitDiffSide.old, first.oldNo, g),
        _numberCell(tokens, KitDiffSide.current, first.newNo, g),
        _glyphCell(tokens, first, g),
        Expanded(child: body),
      ],
    );
    bool selectedOn(KitDiffSide s) {
      for (final line in unit.lines) {
        if (_isSelected(s, _numberOn(line, s))) return true;
      }
      return false;
    }

    return _rowFrame(
      child: cells,
      label: _blockLabel(unit),
      tint: _tint(tokens.roles, unit.kind),
      selected: selectedOn(KitDiffSide.old) || selectedOn(KitDiffSide.current),
      side: side,
      number: _numberOn(first, side),
      g: g,
      tokens: tokens,
      gutter: [
        (KitDiffSide.old, first.oldNo, 0),
        (KitDiffSide.current, first.newNo, g.number),
      ],
    );
  }

  /// "Lines 3–5 added: …", "Line 1 added: …": a block is read with its
  /// line range and what happened to it.
  String _blockLabel(_MdUnit unit) {
    if (unit.lines.length == 1) {
      final line = unit.first;
      final spoken = unit.spoken;
      final name = _lineLabel(
        KitDiffLine('', line.kind, oldNo: line.oldNo, newNo: line.newNo),
      );
      return spoken.isEmpty ? name : '$name: $spoken';
    }
    final l10n = _l10n;
    final side = _sideOf(unit.first);
    final start = _numberOn(unit.first, side) ?? 0;
    final end = _numberOn(unit.last, side) ?? start;
    final name = switch (unit.kind) {
      KitDiffLineKind.added => l10n.kitDiffLinesAdded(start, end),
      KitDiffLineKind.removed => l10n.kitDiffLinesRemoved(start, end),
      _ => l10n.kitDiffLines(start, end),
    };
    final spoken = unit.spoken;
    return spoken.isEmpty ? name : '$name: $spoken';
  }
}
