import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../api/models.dart';
import '../app_theme.dart';

/// Full-screen diff reader shared by the Changes sheet and review surfaces.
///
/// One sticky header per file (name bold, directory muted, +N/−M counts),
/// then its lines. Unchanged context collapses into a "+N lines" bar; each
/// hunk ends in an "Expand" bar that reveals 20 lines per tap. Changed lines
/// carry a 3px colour bar at the far left of the gutter and an ~8% tint; line
/// numbers sit in a fixed-width muted mono gutter. Code wraps below 600dp and
/// scrolls horizontally above it.
///
/// Pushed as a route (`MaterialPageRoute(fullscreenDialog: true)`) so it
/// never stacks a sheet on a sheet.
class DiffView extends StatelessWidget {
  const DiffView({super.key, required this.diffs, this.title});

  DiffView.single(FileDiff diff, {Key? key})
    : this(key: key, diffs: [diff], title: diff.file.split('/').last);

  final List<FileDiff> diffs;

  /// Centred app-bar title; defaults to "Diff", or the file name for one file.
  final String? title;

  static const wrapBelow = 600.0;
  static const expandStep = 20;

  static Future<void> open(BuildContext context, List<FileDiff> diffs) =>
      Navigator.of(context).push(
        MaterialPageRoute<void>(
          fullscreenDialog: true,
          builder: (_) => DiffView(diffs: diffs),
        ),
      );

  @override
  Widget build(BuildContext context) {
    final single = diffs.length == 1 ? diffs.single : null;
    final copyText = single == null
        ? null
        : (single.after ?? single.patch ?? '');
    return Scaffold(
      key: const Key('diff-view'),
      appBar: AppBar(
        leading: const CloseButton(),
        centerTitle: true,
        title: Text(
          title ?? (single != null ? single.file.split('/').last : 'Diff'),
          overflow: TextOverflow.ellipsis,
        ),
        actions: [
          if (single != null)
            IconButton(
              tooltip: single.after != null ? 'Copy updated file' : 'Copy patch',
              icon: const Icon(AppIcons.copy),
              onPressed: copyText == null || copyText.isEmpty
                  ? null
                  : () => Clipboard.setData(ClipboardData(text: copyText)),
            ),
        ],
      ),
      body: diffs.isEmpty
          ? const Center(child: Text('No changes'))
          : SafeArea(top: false, child: _DiffBody(diffs: diffs)),
    );
  }
}

class _DiffBody extends StatelessWidget {
  const _DiffBody({required this.diffs});

  final List<FileDiff> diffs;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return LayoutBuilder(
      builder: (context, constraints) {
        final wrap = constraints.maxWidth < DiffView.wrapBelow;
        final scroll = CustomScrollView(
          slivers: [
            for (final diff in diffs) ...[
              SliverPersistentHeader(
                pinned: true,
                delegate: _FileHeaderDelegate(diff: diff, theme: theme),
              ),
              _DiffFileLines(diff: diff, wrap: wrap),
            ],
            const SliverPadding(padding: EdgeInsets.only(bottom: 24)),
          ],
        );
        if (wrap) return scroll;
        // Wide layouts keep code column-aligned: the whole reader (headers
        // included) scrolls sideways as one surface, sized to the longest
        // line so no row gets its own scrollbar.
        final longest = _longestLine(diffs);
        final codeWidth = _measure(context, longest);
        final width = (codeWidth + _gutterWidth(context, diffs) + 48).clamp(
          constraints.maxWidth,
          double.infinity,
        );
        return SingleChildScrollView(
          key: const Key('diff-view-horizontal'),
          scrollDirection: Axis.horizontal,
          child: SizedBox(width: width, child: scroll),
        );
      },
    );
  }

  static String _longestLine(List<FileDiff> diffs) {
    var longest = '';
    for (final diff in diffs) {
      for (final row in _DiffModel.of(diff).allRows) {
        if (row.text.length > longest.length) longest = row.text;
      }
    }
    return longest;
  }

  static double _measure(BuildContext context, String line) {
    // Measure exactly what the rows render: the code style merged over the
    // ambient default (letter spacing included), or long lines drift.
    final style = DefaultTextStyle.of(
      context,
    ).style.merge(_codeStyle(Theme.of(context)));
    final painter = TextPainter(
      text: TextSpan(text: line, style: style),
      textDirection: Directionality.of(context),
      textScaler: MediaQuery.textScalerOf(context),
    )..layout();
    final width = painter.width;
    painter.dispose();
    return width;
  }
}

TextStyle _codeStyle(ThemeData theme, {Color? color}) => TextStyle(
  fontFamily: AppTheme.monoFamily,
  fontSize: AppTheme.codeFontSize,
  height: 1.45,
  color: color,
);

/// Gutter wide enough for the largest line number across [diffs], scaled
/// with the reader's text size.
double _gutterWidth(BuildContext context, List<FileDiff> diffs) {
  var digits = 1;
  for (final diff in diffs) {
    final max = _DiffModel.of(diff).maxLineNumber;
    if (max.toString().length > digits) digits = max.toString().length;
  }
  final glyph = MediaQuery.textScalerOf(context).scale(AppTheme.codeFontSize);
  return glyph * .62 * digits + 14;
}

class _FileHeaderDelegate extends SliverPersistentHeaderDelegate {
  const _FileHeaderDelegate({required this.diff, required this.theme});

  final FileDiff diff;
  final ThemeData theme;

  @override
  double get minExtent => 44;
  @override
  double get maxExtent => 44;

  @override
  Widget build(
    BuildContext context,
    double shrinkOffset,
    bool overlapsContent,
  ) {
    final segments = diff.file.split('/');
    final name = segments.last;
    final directory = segments.length > 1
        ? '${segments.sublist(0, segments.length - 1).join('/')}/'
        : '';
    final counts = diff.counts;
    final mono = theme.textTheme.labelMedium?.copyWith(
      fontFamily: AppTheme.monoFamily,
      fontWeight: FontWeight.w600,
    );
    return Material(
      key: Key('diff-file-header-${diff.file}'),
      color: theme.colorScheme.surfaceContainerLow,
      elevation: overlapsContent ? 1 : 0,
      child: Container(
        height: maxExtent,
        padding: const EdgeInsets.symmetric(horizontal: 12),
        decoration: BoxDecoration(
          border: Border(bottom: BorderSide(color: AppTheme.hairline(theme))),
        ),
        child: Row(
          children: [
            Expanded(
              child: Text.rich(
                TextSpan(
                  children: [
                    TextSpan(
                      text: name,
                      style: const TextStyle(fontWeight: FontWeight.w700),
                    ),
                    if (directory.isNotEmpty)
                      TextSpan(
                        text: '  $directory',
                        style: TextStyle(color: AppTheme.mutedOf(theme)),
                      ),
                    if (diff.status case final status?)
                      TextSpan(
                        text: '  $status',
                        style: TextStyle(color: AppTheme.mutedOf(theme)),
                      ),
                  ],
                ),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: theme.textTheme.bodySmall?.copyWith(
                  fontFamily: AppTheme.monoFamily,
                ),
              ),
            ),
            const SizedBox(width: 12),
            Text(
              '+${counts.added}',
              style: mono?.copyWith(color: AppTheme.successOf(theme)),
            ),
            const SizedBox(width: 8),
            Text(
              '−${counts.removed}',
              style: mono?.copyWith(color: theme.colorScheme.error),
            ),
          ],
        ),
      ),
    );
  }

  @override
  bool shouldRebuild(_FileHeaderDelegate oldDelegate) =>
      oldDelegate.diff != diff || oldDelegate.theme != theme;
}

enum DiffRowKind { context, added, removed, meta }

/// One line of a diff with the numbers it has in each file.
class DiffRow {
  const DiffRow(this.text, this.kind, {this.oldNo, this.newNo});

  final String text;
  final DiffRowKind kind;
  final int? oldNo;
  final int? newNo;

  /// Number shown in the gutter: the new file's for kept and added lines,
  /// the old file's for removed ones.
  int? get gutterNo => kind == DiffRowKind.removed ? oldNo : newNo;
}

/// A run of unchanged lines that starts collapsed. [rows] is null when the
/// source (a unified patch) does not carry the skipped lines, in which case
/// the bar reports the count and cannot expand.
class DiffGap {
  const DiffGap({required this.count, this.rows});

  final int count;
  final List<DiffRow>? rows;
}

/// A file diff normalised into rows and gaps, from either a unified patch or
/// a before/after pair.
class _DiffModel {
  const _DiffModel(this.segments);

  /// Alternating [DiffRow] and [DiffGap] entries in file order.
  final List<Object> segments;

  static final _hunkHeader = RegExp(
    r'^@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@',
  );

  /// Context lines kept visible on each side of a change before the rest of
  /// a run collapses.
  static const _visibleContext = 3;

  static final _cache = Expando<_DiffModel>();

  static _DiffModel of(FileDiff diff) => _cache[diff] ??= _build(diff);

  static _DiffModel _build(FileDiff diff) {
    final patch = diff.patch;
    if (patch != null && patch.isNotEmpty) return _fromPatch(patch);
    return _fromPair(diff.before ?? '', diff.after ?? '');
  }

  static _DiffModel _fromPatch(String patch) {
    final segments = <Object>[];
    var oldNo = 0;
    var newNo = 0;
    var inHunk = false;
    for (final line in patch.split('\n')) {
      if (line.startsWith('@@')) {
        final match = _hunkHeader.firstMatch(line);
        if (match != null) {
          final nextOld = int.parse(match.group(1)!);
          final nextNew = int.parse(match.group(3)!);
          final skipped = inHunk ? nextNew - newNo : nextNew - 1;
          if (skipped > 0) segments.add(DiffGap(count: skipped));
          oldNo = nextOld;
          newNo = nextNew;
        }
        inHunk = true;
        continue;
      }
      if (!inHunk) {
        // `--- a/x` / `+++ b/x` and `diff --git` preambles repeat what the
        // sticky file header already says.
        continue;
      }
      if (line.startsWith('+')) {
        segments.add(
          DiffRow(line.substring(1), DiffRowKind.added, newNo: newNo++),
        );
      } else if (line.startsWith('-')) {
        segments.add(
          DiffRow(line.substring(1), DiffRowKind.removed, oldNo: oldNo++),
        );
      } else if (line.startsWith('\\')) {
        segments.add(DiffRow(line, DiffRowKind.meta));
      } else {
        final text = line.startsWith(' ') ? line.substring(1) : line;
        if (text.isEmpty && line.isEmpty) continue;
        segments.add(
          DiffRow(
            text,
            DiffRowKind.context,
            oldNo: oldNo++,
            newNo: newNo++,
          ),
        );
      }
    }
    return _DiffModel(_collapseContext(segments));
  }

  /// Common prefix and suffix stay as context; the middle block reads as
  /// removed-then-added.
  static _DiffModel _fromPair(String before, String after) {
    final beforeLines = before.split('\n');
    final afterLines = after.split('\n');
    var p = 0;
    while (p < beforeLines.length &&
        p < afterLines.length &&
        beforeLines[p] == afterLines[p]) {
      p++;
    }
    var s = 0;
    while (s < beforeLines.length - p &&
        s < afterLines.length - p &&
        beforeLines[beforeLines.length - 1 - s] ==
            afterLines[afterLines.length - 1 - s]) {
      s++;
    }
    final rows = <Object>[];
    for (var i = 0; i < p; i++) {
      rows.add(
        DiffRow(
          beforeLines[i],
          DiffRowKind.context,
          oldNo: i + 1,
          newNo: i + 1,
        ),
      );
    }
    for (var i = p; i < beforeLines.length - s; i++) {
      rows.add(DiffRow(beforeLines[i], DiffRowKind.removed, oldNo: i + 1));
    }
    for (var i = p; i < afterLines.length - s; i++) {
      rows.add(DiffRow(afterLines[i], DiffRowKind.added, newNo: i + 1));
    }
    for (var i = afterLines.length - s; i < afterLines.length; i++) {
      rows.add(
        DiffRow(
          afterLines[i],
          DiffRowKind.context,
          oldNo: beforeLines.length - (afterLines.length - i) + 1,
          newNo: i + 1,
        ),
      );
    }
    return _DiffModel(_collapseContext(rows));
  }

  /// Folds long runs of context into gaps, keeping [_visibleContext] lines
  /// next to each change so the reader still sees where they are.
  static List<Object> _collapseContext(List<Object> segments) {
    final out = <Object>[];
    var run = <DiffRow>[];
    void flush({required bool atStart, required bool atEnd}) {
      if (run.isEmpty) return;
      final keepTop = atStart ? 0 : _visibleContext;
      final keepBottom = atEnd ? 0 : _visibleContext;
      if (run.length <= keepTop + keepBottom + 1) {
        out.addAll(run);
      } else {
        out.addAll(run.take(keepTop));
        out.add(
          DiffGap(
            count: run.length - keepTop - keepBottom,
            rows: run.sublist(keepTop, run.length - keepBottom),
          ),
        );
        out.addAll(run.skip(run.length - keepBottom));
      }
      run = <DiffRow>[];
    }

    var seenChange = false;
    for (final segment in segments) {
      if (segment is DiffRow && segment.kind == DiffRowKind.context) {
        run.add(segment);
        continue;
      }
      flush(atStart: !seenChange, atEnd: false);
      seenChange = true;
      out.add(segment);
    }
    flush(atStart: !seenChange, atEnd: true);
    return out;
  }

  Iterable<DiffRow> get allRows sync* {
    for (final segment in segments) {
      if (segment is DiffRow) yield segment;
      if (segment is DiffGap && segment.rows != null) yield* segment.rows!;
    }
  }

  int get maxLineNumber {
    var max = 1;
    for (final row in allRows) {
      for (final n in [row.oldNo, row.newNo]) {
        if (n != null && n > max) max = n;
      }
    }
    return max;
  }
}

/// The rows of one file, with per-gap expansion state.
class _DiffFileLines extends StatefulWidget {
  const _DiffFileLines({required this.diff, required this.wrap});

  final FileDiff diff;
  final bool wrap;

  @override
  State<_DiffFileLines> createState() => _DiffFileLinesState();
}

class _DiffFileLinesState extends State<_DiffFileLines> {
  /// Lines revealed from the top / bottom of each gap, keyed by its index in
  /// the model's segment list.
  final _shownTop = <int, int>{};
  final _shownBottom = <int, int>{};

  void _expandTop(int gap) => setState(
    () => _shownTop[gap] = (_shownTop[gap] ?? 0) + DiffView.expandStep,
  );

  void _expandBottom(int gap) => setState(
    () => _shownBottom[gap] = (_shownBottom[gap] ?? 0) + DiffView.expandStep,
  );

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final model = _DiffModel.of(widget.diff);
    final gutter = _gutterWidth(context, [widget.diff]);
    final entries = <Widget>[];
    for (var index = 0; index < model.segments.length; index++) {
      final segment = model.segments[index];
      if (segment is DiffRow) {
        entries.add(_LineRow(row: segment, gutter: gutter, wrap: widget.wrap));
        continue;
      }
      final gap = segment as DiffGap;
      final rows = gap.rows;
      final top = (_shownTop[index] ?? 0).clamp(0, gap.count);
      final bottom = (_shownBottom[index] ?? 0).clamp(0, gap.count - top);
      final remaining = gap.count - top - bottom;
      if (rows != null) {
        for (final row in rows.take(top)) {
          entries.add(_LineRow(row: row, gutter: gutter, wrap: widget.wrap));
        }
      }
      if (remaining > 0) {
        final expandable = rows != null;
        if (expandable && index > 0) {
          entries.add(
            _GapBar(
              key: Key('diff-expand-down-$index'),
              label: 'Expand',
              icon: Icons.keyboard_arrow_down_rounded,
              gutter: gutter,
              onTap: () => _expandTop(index),
            ),
          );
        }
        entries.add(
          _GapBar(
            key: Key('diff-gap-$index'),
            label: '+$remaining ${remaining == 1 ? 'line' : 'lines'}',
            icon: expandable ? Icons.keyboard_arrow_up_rounded : null,
            gutter: gutter,
            onTap: expandable ? () => _expandBottom(index) : null,
          ),
        );
      }
      if (rows != null && bottom > 0) {
        for (final row in rows.skip(gap.count - bottom)) {
          entries.add(_LineRow(row: row, gutter: gutter, wrap: widget.wrap));
        }
      }
    }
    if (entries.isEmpty) {
      entries.add(
        Padding(
          padding: const EdgeInsets.all(20),
          child: Text(
            '(empty diff)',
            style: TextStyle(color: AppTheme.mutedOf(theme)),
          ),
        ),
      );
    }
    return SliverList.builder(
      itemCount: entries.length,
      itemBuilder: (context, index) => entries[index],
    );
  }
}

class _LineRow extends StatelessWidget {
  const _LineRow({
    required this.row,
    required this.gutter,
    required this.wrap,
  });

  final DiffRow row;
  final double gutter;
  final bool wrap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final accent = switch (row.kind) {
      DiffRowKind.added => AppTheme.successOf(theme),
      DiffRowKind.removed => theme.colorScheme.error,
      DiffRowKind.meta => theme.colorScheme.primary,
      DiffRowKind.context => null,
    };
    final text = Text(
      row.text,
      softWrap: wrap,
      style: _codeStyle(
        theme,
        color: row.kind == DiffRowKind.removed
            ? theme.colorScheme.error
            : row.kind == DiffRowKind.meta
            ? theme.colorScheme.primary
            : null,
      ),
    );
    return Container(
      color: accent?.withValues(alpha: .08),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // 3px marker at the far left of the gutter; transparent on context
          // lines so numbers stay aligned.
          Container(
            width: 3,
            color: row.kind == DiffRowKind.meta ? null : accent,
            child: const SizedBox(height: 1),
          ),
          SizedBox(
            width: gutter,
            child: Padding(
              padding: const EdgeInsets.only(right: 6),
              child: Text(
                row.gutterNo?.toString() ?? '',
                textAlign: TextAlign.right,
                style: _codeStyle(
                  theme,
                  color: AppTheme.mutedOf(theme).withValues(alpha: .75),
                ),
              ),
            ),
          ),
          if (wrap)
            Expanded(
              child: Padding(
                padding: const EdgeInsets.only(right: 8),
                child: text,
              ),
            )
          else
            Padding(padding: const EdgeInsets.only(right: 16), child: text),
        ],
      ),
    );
  }
}

/// Full-width bar standing in for collapsed context, or offering to reveal
/// more of it.
class _GapBar extends StatelessWidget {
  const _GapBar({
    super.key,
    required this.label,
    required this.icon,
    required this.gutter,
    required this.onTap,
  });

  final String label;
  final IconData? icon;
  final double gutter;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final muted = AppTheme.mutedOf(theme);
    return Material(
      color: theme.colorScheme.surfaceContainerHighest.withValues(alpha: .45),
      child: InkWell(
        onTap: onTap,
        child: ConstrainedBox(
          constraints: const BoxConstraints(minHeight: 32),
          child: Row(
            children: [
              SizedBox(width: gutter + 3),
              if (icon != null) Icon(icon, size: 16, color: muted),
              if (icon != null) const SizedBox(width: 6),
              Text(
                label,
                style: theme.textTheme.labelSmall?.copyWith(
                  color: muted,
                  fontFamily: AppTheme.monoFamily,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
