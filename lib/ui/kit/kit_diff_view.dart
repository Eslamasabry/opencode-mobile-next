import 'dart:math' as math;
import 'dart:typed_data';

import 'package:flutter/gestures.dart' show DragStartBehavior;
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:highlight/highlight.dart' show Node;

import '../../l10n/app_localizations.dart';
import '../app_iconography.dart';
import '../theme_roles.dart';
import 'kit_bidi.dart';
import 'kit_buttons.dart';
import 'kit_choice_list.dart';
import 'kit_code_block.dart' show KitCodeHighlight;
import 'kit_copy.dart';
import 'kit_icon_button.dart';
import 'kit_layout.dart';
import 'kit_menu.dart';
import 'kit_motion.dart';
import 'kit_page_route.dart';
import 'kit_progress.dart';
import 'kit_redact.dart';
import 'kit_row.dart';
import 'kit_screen.dart';
import 'kit_state_view.dart';
import 'kit_tappable.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';
import 'kit_top_bar.dart';
import 'motion/kit_reveal.dart';

part 'diff/kit_diff_model.dart';
part 'diff/kit_diff_chrome.dart';
part 'diff/kit_diff_rows.dart';

enum KitDiffMode {
  unified,

  /// Side by side. Used only where the diff's own box is expanded or wider
  /// (KitLayout.windowFor(width) >= KitWindow.expanded); narrower, it
  /// renders unified.
  split,
}

enum KitDiffSide { old, current }

/// Selected lines, in one file and one side.
@immutable
class KitDiffSelection {
  const KitDiffSelection({
    required this.file,
    required this.side,
    required this.startLine,
    required this.endLine,
    required this.text,
    this.hunk = false,
  });

  final KitDiffFile file;
  final KitDiffSide side;

  /// That side's line numbers.
  final int startLine, endLine;

  /// The selected lines, redacted.
  final String text;

  /// True when the selection is exactly one whole hunk on [side] (every
  /// line that side has between two `@@` headers or folded gaps), so a
  /// host that stages or reverts can act on it as a hunk.
  final bool hunk;
}

/// The one diff renderer (K2 §1.15): unified on a phone, side by side from
/// an expanded box, one file header, one "Change 1 of N" navigator across
/// files, and (when a handler is passed) line selection for Comment, Add to
/// prompt and Copy lines. The diff itself is always left to right.
///
/// States: loading, empty, error, loaded, binary, renamed, too-big,
/// selecting.
class KitDiffView extends StatefulWidget {
  const KitDiffView({
    super.key,
    required this.files,
    this.mode,
    this.initialFile,
    this.initialChange,
    this.readOnly = true,
    this.onComment,
    this.onAddToPrompt,
    this.fileActions,
    this.onFileChanged,
    this.maxLines,
    this.onOpenAll,
    this.loading = false,
    this.error,
    this.onRetry,
    this.wrap,
    this.onWrapChanged,
    this.keyPrefix = 'kit-diff',
  }) : assert(readOnly || onComment != null || onAddToPrompt != null),
       assert(error == null || onRetry != null);

  final List<KitDiffFile> files;

  /// Null: unified below expanded, split from expanded.
  final KitDiffMode? mode;

  /// [initialFile] indexes [files]; [initialChange] is 0-based across all
  /// files; [maxLines] caps a preview (null: no cap, virtualised).
  final int? initialFile, initialChange, maxLines;

  /// [readOnly] true: no selection, no Comment / Add to prompt.
  final bool readOnly, loading;
  final ValueChanged<KitDiffSelection>? onComment, onAddToPrompt;

  /// The file header's "More" menu (Copy file, Copy patch, Ask about this
  /// file, …). Copies in it are the host's, verbatim (SEC-13).
  final List<KitMenuItem> Function(KitDiffFile file)? fileActions;

  /// Called with the index into [files] whenever the shown file changes
  /// (the switcher, the file list, or the navigator crossing into another
  /// file), so a host can track the current file.
  final ValueChanged<int>? onFileChanged;
  final VoidCallback? onOpenAll, onRetry;

  /// A failure in words; the part shows it with Try again.
  final String? error;

  /// Null: wrap on compact, scroll sideways from medium.
  final bool? wrap;
  final ValueChanged<bool>? onWrapChanged;

  /// Internal keys are `<prefix>-…` (the DiffView wrapper passes 'diff').
  final String keyPrefix;

  /// Unchanged lines revealed per tap on a gap bar.
  static const int expandStep = 20;

  @override
  State<KitDiffView> createState() => _KitDiffViewState();
}

/// Opens a read-only diff as a page (KitPageRoute, Close at the end),
/// titled by what it shows ("Changes in this reply"), never a sheet on a
/// sheet. Returns when it closes. [pageKey] keys the page (KIT-10).
Future<void> showKitDiff(
  BuildContext context, {
  required String title,
  required List<KitDiffFile> files,
  int? initialFile,
  bool? wrap,
  ValueChanged<bool>? onWrapChanged,
  Key? pageKey,
}) => pushKitPage<void>(
  context,
  (context) => _KitDiffPage(
    key: pageKey,
    title: title,
    files: files,
    initialFile: initialFile,
    wrap: wrap,
    onWrapChanged: onWrapChanged,
  ),
  fullscreenDialog: true,
);

class _KitDiffPage extends StatelessWidget {
  const _KitDiffPage({
    super.key,
    required this.title,
    required this.files,
    this.initialFile,
    this.wrap,
    this.onWrapChanged,
  });

  final String title;
  final List<KitDiffFile> files;
  final int? initialFile;
  final bool? wrap;
  final ValueChanged<bool>? onWrapChanged;

  @override
  Widget build(BuildContext context) {
    final roles = KitTokens.of(context).roles;
    return Scaffold(
      backgroundColor: roles.ground,
      body: SafeArea(
        bottom: false,
        child: KitScreen(
          header: [KitTopBar(title: title, exit: KitTopBarExit.close)],
          body: KitDiffView(
            files: files,
            initialFile: initialFile,
            wrap: wrap,
            onWrapChanged: onWrapChanged,
          ),
        ),
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Render items

class _KitDiffViewState extends State<KitDiffView> {
  late int _file;
  late int _change;
  bool? _wrap;
  final ScrollController _lines = ScrollController();
  final FocusNode _bodyFocus = FocusNode(debugLabel: 'KitDiffView lines');

  /// Lines revealed from the top / bottom of each gap, keyed by segment
  /// index in the current file.
  final Map<int, int> _shownTop = {};
  final Map<int, int> _shownBottom = {};

  // Selection: one side, an anchor and an extent (that side's numbers).
  KitDiffSide? _side;
  int? _anchor;
  int? _extent;

  /// Built rows by the side and number they carry, with where that side's
  /// number column starts inside the row: the gutter's hit area resolves a
  /// touch to the nearest of them (compact lines, 48 dp reach, A11Y-2).
  final Map<(KitDiffSide, int), (BuildContext, double)> _cells = {};

  /// Built gap bars and hunk headers: their own taps win over the gutter's
  /// reach.
  final Set<BuildContext> _blockers = {};

  /// Files opened so far (the file list ticks them).
  final Set<int> _viewed = {};

  /// The item the navigator last moved to: keyed, so it can be revealed
  /// precisely once it is built.
  int? _targetItem;
  final GlobalKey _targetKey = GlobalKey();

  AppLocalizations get _l10n =>
      lookupAppLocalizations(Localizations.localeOf(context));

  /// (file, segment) of every change, in order across files.
  List<(int, int)> get _changes => [
    for (var f = 0; f < widget.files.length; f++)
      for (final seg in _FileIndex.of(widget.files[f]).changes) (f, seg),
  ];

  @override
  void initState() {
    super.initState();
    _file = (widget.initialFile ?? 0).clamp(
      0,
      math.max(0, widget.files.length - 1),
    );
    final changes = _changes;
    if (widget.initialChange != null && changes.isNotEmpty) {
      _change = widget.initialChange!.clamp(0, changes.length - 1);
      _file = changes[_change].$1;
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) _revealChange(animate: false);
      });
    } else {
      final first = changes.indexWhere((c) => c.$1 == _file);
      _change = math.max(0, first);
    }
    _viewed.add(_file);
  }

  @override
  void didUpdateWidget(KitDiffView old) {
    super.didUpdateWidget(old);
    if (widget.files.length != old.files.length) {
      _file = _file.clamp(0, math.max(0, widget.files.length - 1));
      _change = _change.clamp(0, math.max(0, _changes.length - 1));
      _viewed
        ..clear()
        ..add(_file);
    }
    if (widget.readOnly && !old.readOnly) _clearSelection();
  }

  @override
  void dispose() {
    _lines.dispose();
    _bodyFocus.dispose();
    super.dispose();
  }

  // -------------------------------------------------------------------------
  // Wrap

  bool _effectiveWrap(KitWindow window) =>
      widget.wrap ?? _wrap ?? window == KitWindow.compact;

  void _toggleWrap(KitWindow window) {
    final next = !_effectiveWrap(window);
    setState(() => _wrap = next);
    widget.onWrapChanged?.call(next);
  }

  // -------------------------------------------------------------------------
  // Files and changes

  void _openFile(int index) {
    if (index == _file) return;
    setState(() {
      _file = index;
      _shownTop.clear();
      _shownBottom.clear();
      _targetItem = null;
      _clearSelectionState();
      final first = _changes.indexWhere((c) => c.$1 == index);
      if (first >= 0) _change = first;
      _viewed.add(index);
    });
    if (_lines.hasClients) _lines.jumpTo(0);
    widget.onFileChanged?.call(index);
  }

  void _goToChange(int index) {
    final changes = _changes;
    if (index < 0 || index >= changes.length) return;
    final (file, _) = changes[index];
    final crossed = file != _file;
    setState(() {
      if (crossed) {
        _file = file;
        _shownTop.clear();
        _shownBottom.clear();
        _clearSelectionState();
        _viewed.add(file);
      }
      _change = index;
    });
    if (crossed) widget.onFileChanged?.call(file);
    _bodyFocus.requestFocus();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _revealChange(animate: !KitMotion.reduced(context));
    });
  }

  /// Scrolls to the current change: first to an estimate from the known
  /// row heights (the row may not be built yet), then precisely once built.
  void _revealChange({required bool animate}) {
    final changes = _changes;
    if (changes.isEmpty || !_lines.hasClients) return;
    final (_, seg) = changes[_change];
    final items = _currentItems(split: _lastSplit);
    final index = items.indexWhere((item) => item.seg == seg);
    if (index < 0) return;
    setState(() => _targetItem = index);
    var offset = 0.0;
    for (var i = 0; i < index; i++) {
      offset += _estimateExtent(items[i]);
    }
    final position = _lines.position;
    final target = (offset - 2 * _lastLineHeight).clamp(
      position.minScrollExtent,
      position.maxScrollExtent,
    );
    void settle() {
      if (!mounted) return;
      final ctx = _targetKey.currentContext;
      if (ctx != null) {
        Scrollable.ensureVisible(ctx, alignment: .2, duration: Duration.zero);
      }
    }

    if (animate) {
      _lines
          .animateTo(
            target,
            duration: KitMotion.standard,
            curve: KitMotion.emphasized,
          )
          .then((_) => settle());
    } else {
      _lines.jumpTo(target);
      WidgetsBinding.instance.addPostFrameCallback((_) => settle());
    }
  }

  bool _lastSplit = false;
  double _lastLineHeight = 19;
  double _lastNumberWidth = 0;
  double _lastBarHeight = 48;

  double _estimateExtent(_Item item) => switch (item) {
    _LineItem() || _PairItem() => _lastLineHeight,
    _HunkItem() => _lastLineHeight + 8,
    _GapItem(:final gap, :final seg) =>
      _lastBarHeight * (gap.lines == null ? 1 : 2) +
          ((_shownTop[seg] ?? 0) + (_shownBottom[seg] ?? 0)) * _lastLineHeight,
  };

  List<_Item> _currentItems({required bool split}) =>
      _itemsFor(widget.files[_file], split: split);

  // -------------------------------------------------------------------------
  // Selection

  bool get _selecting => !widget.readOnly && _side != null;

  void _clearSelectionState() {
    _side = null;
    _anchor = null;
    _extent = null;
  }

  void _clearSelection() => setState(_clearSelectionState);

  void _select(KitDiffSide side, int line, {bool extend = false}) {
    if (widget.readOnly) return;
    // The keyboard follows the selection: Shift+Up/Down, Enter, Esc, Ctrl+C.
    _bodyFocus.requestFocus();
    setState(() {
      if (extend && _side == side && _anchor != null) {
        _extent = line;
      } else {
        _side = side;
        _anchor = line;
        _extent = line;
      }
    });
  }

  bool _isSelected(KitDiffSide side, int? line) {
    if (!_selecting || _side != side || line == null) return false;
    final lo = math.min(_anchor!, _extent!);
    final hi = math.max(_anchor!, _extent!);
    return line >= lo && line <= hi;
  }

  void _dragTo(KitDiffSide side, Offset global) {
    if (_side != side) return;
    int? best;
    for (final MapEntry(key: (cellSide, n), value: (ctx, _))
        in _cells.entries) {
      if (cellSide != side || !ctx.mounted) continue;
      final box = ctx.findRenderObject();
      if (box is! RenderBox || !box.attached) continue;
      final top = box.localToGlobal(Offset.zero).dy;
      if (global.dy >= top && global.dy < top + box.size.height) {
        best = n;
        break;
      }
    }
    if (best != null && best != _extent) setState(() => _extent = best);
  }

  /// The line whose number column is under [global], or within reach of it:
  /// rows draw at the text's line height, and the gutter lends each number
  /// the rest of a 48 dp target above and below it, unless a gap bar or a
  /// hunk header (a target of its own) is there.
  (KitDiffSide, int)? _gutterLineAt(Offset global) {
    Rect? rectOf(BuildContext ctx) {
      if (!ctx.mounted) return null;
      final box = ctx.findRenderObject();
      if (box is! RenderBox || !box.attached || !box.hasSize) return null;
      return box.localToGlobal(Offset.zero) & box.size;
    }

    final number = _lastNumberWidth;
    final reachTarget = _lastBarHeight;
    (KitDiffSide, int)? best;
    var bestDistance = double.infinity;
    for (final MapEntry(key: cell, value: (ctx, dx)) in _cells.entries) {
      final row = rectOf(ctx);
      if (row == null) continue;
      final left = row.left + dx;
      if (global.dx < left || global.dx >= left + number) continue;
      if (global.dy >= row.top && global.dy < row.bottom) return cell;
      final reach = math.max(0.0, (reachTarget - row.height) / 2);
      final distance = global.dy < row.top
          ? row.top - global.dy
          : global.dy - row.bottom;
      if (distance <= reach && distance < bestDistance) {
        best = cell;
        bestDistance = distance;
      }
    }
    if (best == null) return null;
    for (final ctx in _blockers) {
      if (rectOf(ctx)?.contains(global) ?? false) return null;
    }
    return best;
  }

  /// Selects the whole hunk under the `@@` line at segment [seg].
  void _selectHunk(int seg) {
    if (widget.readOnly) return;
    final hunk = _FileIndex.of(
      widget.files[_file],
    ).hunks.where((h) => h.header == seg).firstOrNull;
    if (hunk == null) return;
    final side = hunk.range(KitDiffSide.current) != null
        ? KitDiffSide.current
        : KitDiffSide.old;
    final (lo, hi) = hunk.range(side)!;
    _bodyFocus.requestFocus();
    setState(() {
      _side = side;
      _anchor = lo;
      _extent = hi;
    });
  }

  /// The selected lines of the current file, verbatim.
  String _selectedText() => _selectedLines().join('\n');

  List<String> _selectedLines() {
    final side = _side;
    if (side == null || _anchor == null || _extent == null) return const [];
    final lo = math.min(_anchor!, _extent!);
    final hi = math.max(_anchor!, _extent!);
    final lines = <String>[];
    for (final line in _FileIndex.of(widget.files[_file]).allLines) {
      if (line.kind == KitDiffLineKind.hunk) continue;
      final n = side == KitDiffSide.old ? line.oldNo : line.newNo;
      if (n == null || n < lo || n > hi) continue;
      if (side == KitDiffSide.old && line.kind == KitDiffLineKind.added) {
        continue;
      }
      if (side == KitDiffSide.current && line.kind == KitDiffLineKind.removed) {
        continue;
      }
      lines.add(line.text);
    }
    return lines;
  }

  KitDiffSelection? get _selection {
    if (!_selecting) return null;
    final file = widget.files[_file];
    final side = _side!;
    final lo = math.min(_anchor!, _extent!);
    final hi = math.max(_anchor!, _extent!);
    return KitDiffSelection(
      file: file,
      side: side,
      startLine: lo,
      endLine: hi,
      text: KitRedact.text(_selectedText()),
      hunk: _FileIndex.of(file).hunks.any((h) => h.range(side) == (lo, hi)),
    );
  }

  int get _selectedCount => _selectedLines().length;

  void _comment() {
    final selection = _selection;
    if (selection == null || widget.onComment == null) return;
    widget.onComment!(selection);
  }

  void _addToPrompt() {
    final selection = _selection;
    if (selection == null || widget.onAddToPrompt == null) return;
    widget.onAddToPrompt!(selection);
  }

  /// SEC-13: a diff is the person's own content; it copies verbatim.
  Future<void> _copyLines() async {
    if (!_selecting) return;
    await KitCopy.copy(context, _selectedText(), redact: false);
  }

  Future<void> _lineMenu(
    KitDiffSide side,
    int line,
    Offset position,
    BuildContext anchor,
  ) async {
    if (widget.readOnly) return;
    if (!_isSelected(side, line)) _select(side, line);
    final l10n = _l10n;
    await showKitMenu(
      anchor,
      position: position,
      items: [
        if (widget.onComment != null)
          KitMenuItem(label: l10n.kitDiffComment, onSelected: _comment),
        if (widget.onAddToPrompt != null)
          KitMenuItem(label: l10n.kitDiffAddToPrompt, onSelected: _addToPrompt),
        KitMenuItem(
          label: l10n.kitDiffCopyLines,
          icon: AppIconography.copy,
          onSelected: _copyLines,
        ),
      ],
    );
  }

  // -------------------------------------------------------------------------
  // Keyboard (LAY-10)

  KeyEventResult _onKey(FocusNode node, KeyEvent event) {
    if (event is! KeyDownEvent && event is! KeyRepeatEvent) {
      return KeyEventResult.ignored;
    }
    final keyboard = HardwareKeyboard.instance;
    final key = event.logicalKey;
    final shift = keyboard.isShiftPressed;
    final control = keyboard.isControlPressed || keyboard.isMetaPressed;
    if (control && key == LogicalKeyboardKey.keyC && _selecting) {
      _copyLines();
      return KeyEventResult.handled;
    }
    if (control || keyboard.isAltPressed) return KeyEventResult.ignored;
    if ((key == LogicalKeyboardKey.keyN && !shift) ||
        (key == LogicalKeyboardKey.f7 && !shift)) {
      _goToChange(_change + 1);
      return KeyEventResult.handled;
    }
    if ((key == LogicalKeyboardKey.keyP && !shift) ||
        (key == LogicalKeyboardKey.f7 && shift)) {
      _goToChange(_change - 1);
      return KeyEventResult.handled;
    }
    if (key == LogicalKeyboardKey.escape && _selecting) {
      _clearSelection();
      return KeyEventResult.handled;
    }
    if (key == LogicalKeyboardKey.enter &&
        _selecting &&
        widget.onComment != null) {
      _comment();
      return KeyEventResult.handled;
    }
    if (key == LogicalKeyboardKey.arrowDown ||
        key == LogicalKeyboardKey.arrowUp) {
      final down = key == LogicalKeyboardKey.arrowDown;
      if (shift && _selecting) {
        setState(() => _extent = math.max(1, _extent! + (down ? 1 : -1)));
        return KeyEventResult.handled;
      }
      if (_lines.hasClients) {
        final position = _lines.position;
        _lines.jumpTo(
          (position.pixels + (down ? _lastLineHeight : -_lastLineHeight)).clamp(
            position.minScrollExtent,
            position.maxScrollExtent,
          ),
        );
        return KeyEventResult.handled;
      }
    }
    return KeyEventResult.ignored;
  }

  /// Runs [fn] and rebuilds: lets the part-file extensions rebuild the state.
  void _update(VoidCallback fn) => setState(fn);

  /// The side a gutter drag started on (a field: rows rebuild mid-drag).
  KitDiffSide? _dragSide;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final l10n = _l10n;
    if (widget.loading) return _loading(tokens);
    if (widget.error != null) {
      return KitStateView.error(
        title: l10n.kitDiffLoadFailed,
        body: widget.error,
        size: KitStateSize.inline,
        retry: KitAction(label: l10n.kitTryAgain, onPressed: widget.onRetry),
      );
    }
    if (widget.files.isEmpty) {
      return KitStateView(
        icon: AppIconography.checks,
        title: l10n.kitDiffNoChanges,
        size: KitStateSize.inline,
      );
    }
    return LayoutBuilder(
      builder: (context, constraints) {
        final width = constraints.maxWidth.isFinite
            ? constraints.maxWidth
            : MediaQuery.sizeOf(context).width;
        final window = KitLayout.windowFor(width);
        final bounded = constraints.maxHeight.isFinite;
        final showList = window == KitWindow.large && widget.files.length > 1;
        final diffWidth = showList
            ? width - KitLayout.paneListWidth - tokens.space3
            : width;
        final split =
            (widget.mode ?? KitDiffMode.split) == KitDiffMode.split &&
            KitLayout.windowFor(diffWidth).isWide;
        final wrap = _effectiveWrap(window);
        final column = _diffColumn(
          context,
          tokens,
          l10n,
          width: diffWidth,
          window: window,
          split: split,
          wrap: wrap,
          bounded: bounded,
          picker: !showList,
          fileList: showList ? _fileList(tokens, bounded: bounded) : null,
        );
        return column;
      },
    );
  }
}
