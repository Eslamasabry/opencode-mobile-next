part of '../kit_diff_view.dart';

/// What one line of a [KitDiffFile] is (docs/ux-system/kit-api/KitDiffView.md).
enum KitDiffLineKind { context, added, removed, hunk }

/// One line of a diff with the numbers it has in each file. A [hunk] line
/// carries the `@@` header as [text] and reads as a line range.
@immutable
class KitDiffLine {
  const KitDiffLine(this.text, this.kind, {this.oldNo, this.newNo});

  final String text;
  final KitDiffLineKind kind;
  final int? oldNo, newNo;
}

/// Unchanged lines that start folded. [lines] is null when the source (a
/// unified patch) does not carry them: the bar then only states the count.
@immutable
class KitDiffGap {
  const KitDiffGap({required this.count, this.lines});

  final int count;
  final List<KitDiffLine>? lines;
}

enum KitDiffFileStatus { modified, added, deleted, renamed }

/// One file's change, normalised into lines and gaps. Built by the caller
/// from its own model (the kit never sees lib/api types).
@immutable
class KitDiffFile {
  const KitDiffFile({
    required this.path,
    required this.segments,
    required this.added,
    required this.removed,
    this.status = KitDiffFileStatus.modified,
    this.oldPath,
    this.binary = false,
    this.fullText,
    this.patch,
  });

  /// Parses a unified patch; `@@` headers become hunk lines read as line
  /// ranges ("Lines 12–18"), and 3 context lines stay around each change.
  factory KitDiffFile.fromPatch(
    String path,
    String patch, {
    KitDiffFileStatus? status,
    String? oldPath,
  }) {
    if (patch.contains('GIT binary patch') ||
        RegExp(r'^Binary files .* differ$', multiLine: true).hasMatch(patch)) {
      return KitDiffFile(
        path: path,
        segments: const [],
        added: 0,
        removed: 0,
        status: status ?? KitDiffFileStatus.modified,
        oldPath: oldPath,
        binary: true,
        patch: patch,
      );
    }
    final segments = <Object>[];
    var oldNo = 0;
    var newNo = 0;
    var inHunk = false;
    var added = 0;
    var removed = 0;
    for (final line in patch.split('\n')) {
      if (line.startsWith('@@')) {
        final match = _hunkHeader.firstMatch(line);
        if (match != null) {
          final nextOld = int.parse(match.group(1)!);
          final nextNew = int.parse(match.group(3)!);
          final skipped = inHunk ? nextNew - newNo : nextNew - 1;
          if (skipped > 0) segments.add(KitDiffGap(count: skipped));
          oldNo = nextOld;
          newNo = nextNew;
          segments.add(
            KitDiffLine(
              line,
              KitDiffLineKind.hunk,
              oldNo: nextOld,
              newNo: nextNew,
            ),
          );
        }
        inHunk = true;
        continue;
      }
      // `--- a/x` / `+++ b/x` and `diff --git` preambles repeat what the
      // file header already says.
      if (!inHunk) continue;
      if (line.startsWith('+')) {
        added++;
        segments.add(
          KitDiffLine(line.substring(1), KitDiffLineKind.added, newNo: newNo++),
        );
      } else if (line.startsWith('-')) {
        removed++;
        segments.add(
          KitDiffLine(
            line.substring(1),
            KitDiffLineKind.removed,
            oldNo: oldNo++,
          ),
        );
      } else if (line.startsWith('\\')) {
        // "\ No newline at end of file": a note about the line above, not
        // a line of either file.
        continue;
      } else {
        if (line.isEmpty) continue;
        final text = line.startsWith(' ') ? line.substring(1) : line;
        segments.add(
          KitDiffLine(
            text,
            KitDiffLineKind.context,
            oldNo: oldNo++,
            newNo: newNo++,
          ),
        );
      }
    }
    return KitDiffFile(
      path: path,
      segments: _collapseContext(segments),
      added: added,
      removed: removed,
      status: status ?? KitDiffFileStatus.modified,
      oldPath: oldPath,
      patch: patch,
    );
  }

  /// Diffs a before/after pair (either may be null: added or deleted).
  factory KitDiffFile.fromTexts(
    String path, {
    String? before,
    String? after,
    KitDiffFileStatus? status,
  }) {
    final resolved =
        status ??
        (before == null && after != null
            ? KitDiffFileStatus.added
            : after == null && before != null
            ? KitDiffFileStatus.deleted
            : KitDiffFileStatus.modified);
    final a = before == null || before.isEmpty
        ? const <String>[]
        : _splitLines(before);
    final b = after == null || after.isEmpty
        ? const <String>[]
        : _splitLines(after);
    final lines = _lineDiff(a, b);
    var added = 0;
    var removed = 0;
    for (final line in lines) {
      if (line.kind == KitDiffLineKind.added) added++;
      if (line.kind == KitDiffLineKind.removed) removed++;
    }
    return KitDiffFile(
      path: path,
      segments: _collapseContext(lines),
      added: added,
      removed: removed,
      status: resolved,
      fullText: after,
    );
  }

  final String path;

  /// [KitDiffLine] and [KitDiffGap] entries in file order.
  final List<Object> segments;
  final int added, removed;
  final KitDiffFileStatus status;
  final String? oldPath, fullText, patch;
  final bool binary;

  /// The number of changes (runs of added/removed lines) in this file.
  int get changeCount => _FileIndex.of(this).changes.length;

  static final _hunkHeader = RegExp(
    r'^@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@',
  );
}

/// Context lines kept visible on each side of a change before the rest of a
/// run folds into a gap.
const int _visibleContext = 3;

/// Above this many cells the line diff falls back to one changed block
/// (the common prefix and suffix stay context): a quadratic table on the UI
/// thread is worse than a coarser diff.
const int _lcsCellLimit = 1000000;

List<String> _splitLines(String text) {
  final trimmed = text.endsWith('\n')
      ? text.substring(0, text.length - 1)
      : text;
  return trimmed.split('\n');
}

/// A line diff of [a] against [b]: common prefix and suffix, then a longest
/// common subsequence of the middle (a single removed-then-added block when
/// the middle is too large to compare line by line).
List<KitDiffLine> _lineDiff(List<String> a, List<String> b) {
  var p = 0;
  while (p < a.length && p < b.length && a[p] == b[p]) {
    p++;
  }
  var s = 0;
  while (s < a.length - p &&
      s < b.length - p &&
      a[a.length - 1 - s] == b[b.length - 1 - s]) {
    s++;
  }
  final out = <KitDiffLine>[
    for (var i = 0; i < p; i++)
      KitDiffLine(a[i], KitDiffLineKind.context, oldNo: i + 1, newNo: i + 1),
  ];
  final n = a.length - p - s;
  final m = b.length - p - s;
  if (n > 0 && m > 0 && (n + 1) * (m + 1) <= _lcsCellLimit) {
    // table[i][j]: LCS length of a[p+i..] and b[p+j..].
    final width = m + 1;
    final table = Int32List((n + 1) * width);
    for (var i = n - 1; i >= 0; i--) {
      for (var j = m - 1; j >= 0; j--) {
        table[i * width + j] = a[p + i] == b[p + j]
            ? table[(i + 1) * width + j + 1] + 1
            : math.max(table[(i + 1) * width + j], table[i * width + j + 1]);
      }
    }
    var i = 0;
    var j = 0;
    final removed = <KitDiffLine>[];
    final added = <KitDiffLine>[];
    void flush() {
      out
        ..addAll(removed)
        ..addAll(added);
      removed.clear();
      added.clear();
    }

    while (i < n || j < m) {
      if (i < n && j < m && a[p + i] == b[p + j]) {
        flush();
        out.add(
          KitDiffLine(
            a[p + i],
            KitDiffLineKind.context,
            oldNo: p + i + 1,
            newNo: p + j + 1,
          ),
        );
        i++;
        j++;
      } else if (j < m &&
          (i >= n || table[i * width + j + 1] >= table[(i + 1) * width + j])) {
        added.add(
          KitDiffLine(b[p + j], KitDiffLineKind.added, newNo: p + j + 1),
        );
        j++;
      } else {
        removed.add(
          KitDiffLine(a[p + i], KitDiffLineKind.removed, oldNo: p + i + 1),
        );
        i++;
      }
    }
    flush();
  } else {
    for (var i = p; i < a.length - s; i++) {
      out.add(KitDiffLine(a[i], KitDiffLineKind.removed, oldNo: i + 1));
    }
    for (var j = p; j < b.length - s; j++) {
      out.add(KitDiffLine(b[j], KitDiffLineKind.added, newNo: j + 1));
    }
  }
  for (var k = 0; k < s; k++) {
    final ai = a.length - s + k;
    final bi = b.length - s + k;
    out.add(
      KitDiffLine(a[ai], KitDiffLineKind.context, oldNo: ai + 1, newNo: bi + 1),
    );
  }
  return out;
}

/// Folds long runs of context into gaps, keeping [_visibleContext] lines
/// next to each change so the reader still sees where they are.
List<Object> _collapseContext(List<Object> segments) {
  final out = <Object>[];
  var run = <KitDiffLine>[];
  void flush({required bool atStart, required bool atEnd}) {
    if (run.isEmpty) return;
    final keepTop = atStart ? 0 : _visibleContext;
    final keepBottom = atEnd ? 0 : _visibleContext;
    if (run.length <= keepTop + keepBottom + 1) {
      out.addAll(run);
    } else {
      out.addAll(run.take(keepTop));
      out.add(
        KitDiffGap(
          count: run.length - keepTop - keepBottom,
          lines: List.unmodifiable(
            run.sublist(keepTop, run.length - keepBottom),
          ),
        ),
      );
      out.addAll(run.skip(run.length - keepBottom));
    }
    run = <KitDiffLine>[];
  }

  var seenChange = false;
  for (final segment in segments) {
    if (segment is KitDiffLine && segment.kind == KitDiffLineKind.context) {
      run.add(segment);
      continue;
    }
    // A patch gap or hunk header ends a run like a change does, but only a
    // change keeps context around itself.
    final isChange =
        segment is KitDiffLine &&
        (segment.kind == KitDiffLineKind.added ||
            segment.kind == KitDiffLineKind.removed);
    flush(atStart: !seenChange, atEnd: !isChange);
    if (isChange) seenChange = true;
    out.add(segment);
  }
  flush(atStart: !seenChange, atEnd: true);
  return List.unmodifiable(out);
}

/// Derived facts about one file, computed once per [KitDiffFile] instance.
class _FileIndex {
  _FileIndex(KitDiffFile file) {
    var inRun = false;
    for (var i = 0; i < file.segments.length; i++) {
      final segment = file.segments[i];
      if (segment is KitDiffLine) {
        if (segment.kind != KitDiffLineKind.hunk) total++;
        for (final n in [segment.oldNo, segment.newNo]) {
          if (n != null && n > maxNumber) maxNumber = n;
        }
        if (segment.text.length > longest.length) longest = segment.text;
        final change =
            segment.kind == KitDiffLineKind.added ||
            segment.kind == KitDiffLineKind.removed;
        if (change && !inRun) changes.add(i);
        inRun = change;
        allLines.add(segment);
      } else if (segment is KitDiffGap) {
        inRun = false;
        total += segment.count;
        for (final line in segment.lines ?? const <KitDiffLine>[]) {
          for (final n in [line.oldNo, line.newNo]) {
            if (n != null && n > maxNumber) maxNumber = n;
          }
          if (line.text.length > longest.length) longest = line.text;
          allLines.add(line);
        }
      }
    }
    _Hunk? open;
    void close() {
      if (open != null && open!.hasChange) hunks.add(open!);
      open = null;
    }

    for (var i = 0; i < file.segments.length; i++) {
      final segment = file.segments[i];
      if (segment is KitDiffGap) {
        close();
      } else if (segment is KitDiffLine) {
        if (segment.kind == KitDiffLineKind.hunk) {
          close();
          open = _Hunk(i);
        } else {
          (open ??= _Hunk(null)).add(segment);
        }
      }
    }
    close();
  }

  /// Segment index where each change starts.
  final List<int> changes = [];

  /// The hunks holding a change: runs of lines between `@@` headers or
  /// folded gaps.
  final List<_Hunk> hunks = [];
  final List<KitDiffLine> allLines = [];
  int total = 0;
  int maxNumber = 1;
  String longest = '';

  static final _cache = Expando<_FileIndex>();
  static _FileIndex of(KitDiffFile file) => _cache[file] ??= _FileIndex(file);
}

/// One hunk's line ranges on each side.
class _Hunk {
  _Hunk(this.header);

  /// The segment index of its `@@` line, when the source had one.
  final int? header;
  int? oldLo, oldHi, newLo, newHi;
  bool hasChange = false;

  void add(KitDiffLine line) {
    final old = line.kind == KitDiffLineKind.added ? null : line.oldNo;
    final current = line.kind == KitDiffLineKind.removed ? null : line.newNo;
    if (old != null) {
      oldLo = math.min(oldLo ?? old, old);
      oldHi = math.max(oldHi ?? old, old);
    }
    if (current != null) {
      newLo = math.min(newLo ?? current, current);
      newHi = math.max(newHi ?? current, current);
    }
    if (line.kind == KitDiffLineKind.added ||
        line.kind == KitDiffLineKind.removed) {
      hasChange = true;
    }
  }

  (int, int)? range(KitDiffSide side) => switch (side) {
    KitDiffSide.old when oldLo != null => (oldLo!, oldHi!),
    KitDiffSide.current when newLo != null => (newLo!, newHi!),
    _ => null,
  };
}

sealed class _Item {
  const _Item(this.seg);

  /// The first segment this item shows.
  final int seg;
}

/// A unified line.
class _LineItem extends _Item {
  const _LineItem(super.seg, this.line);
  final KitDiffLine line;
}

/// A split row: the old side's line and the current side's.
class _PairItem extends _Item {
  const _PairItem(super.seg, this.old, this.current);
  final KitDiffLine? old, current;
}

class _HunkItem extends _Item {
  const _HunkItem(super.seg, this.line);
  final KitDiffLine line;
}

class _GapItem extends _Item {
  const _GapItem(super.seg, this.gap);
  final KitDiffGap gap;
}

List<_Item> _itemsFor(KitDiffFile file, {required bool split}) {
  final out = <_Item>[];
  final segments = file.segments;
  var i = 0;
  while (i < segments.length) {
    final segment = segments[i];
    if (segment is KitDiffGap) {
      out.add(_GapItem(i, segment));
      i++;
      continue;
    }
    if (segment is! KitDiffLine) {
      i++;
      continue;
    }
    if (segment.kind == KitDiffLineKind.hunk) {
      out.add(_HunkItem(i, segment));
      i++;
      continue;
    }
    if (!split) {
      out.add(_LineItem(i, segment));
      i++;
      continue;
    }
    if (segment.kind == KitDiffLineKind.context) {
      out.add(_PairItem(i, segment, segment));
      i++;
      continue;
    }
    // A change: its removed lines face its added lines, row by row.
    final removed = <(int, KitDiffLine)>[];
    final added = <(int, KitDiffLine)>[];
    while (i < segments.length) {
      final s = segments[i];
      if (s is KitDiffLine && s.kind == KitDiffLineKind.removed) {
        if (added.isNotEmpty) break;
        removed.add((i, s));
      } else if (s is KitDiffLine && s.kind == KitDiffLineKind.added) {
        added.add((i, s));
      } else {
        break;
      }
      i++;
    }
    final rows = math.max(removed.length, added.length);
    for (var r = 0; r < rows; r++) {
      final left = r < removed.length ? removed[r] : null;
      final right = r < added.length ? added[r] : null;
      out.add(_PairItem((left ?? right)!.$1, left?.$2, right?.$2));
    }
  }
  return out;
}

/// The line geometry shared by every row of one build.
class _Geometry {
  const _Geometry({
    required this.number,
    required this.glyph,
    required this.lineHeight,
    required this.wrap,
    required this.split,
    required this.selectable,
    required this.textWidth,
  });

  /// One line-number cell.
  final double number;

  /// The `+` / `−` column.
  final double glyph;
  final double lineHeight;
  final bool wrap, split, selectable;

  /// The text column's width when lines scroll sideways (null: wrapping).
  final double? textWidth;

  /// Where the text column starts: the number cells and the glyph.
  double get indent => (split ? number : number + number) + glyph;
}
