// KitLogPanel: the one log view — live or finished output from one process
// (server logs, setup output, dev services, shell output, the phone team's
// steps) (docs/ux-system/kit-api/KitLogPanel.md; kit-v2.md §1.11, §4.4,
// §8.2; G9, G12; KIT-31, KIT-32, LOOK-4, LOOK-5, LOOK-16, PERF-4, A11Y-3,
// MOT-5, MOT-7).
//
// Mono, left to right in every locale, folded under Details unless the page
// exists to show the log. It does not compose KitCodeBlock: no syntax
// colour, no terminal emulation.
import 'dart:async';
import 'dart:math' as math;

import 'package:clock/clock.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../l10n/app_localizations.dart';
import '../app_iconography.dart';
import 'kit_bidi.dart';
import 'kit_buttons.dart';
import 'kit_code_surface.dart';
import 'kit_details_fold.dart';
import 'kit_icon_button.dart';
import 'kit_jump_pill.dart';
import 'kit_layout.dart';
import 'kit_max_width.dart';
import 'kit_motion.dart';
import 'kit_notice.dart';
import 'kit_redact.dart';
import 'kit_search_field.dart';
import 'kit_since.dart';
import 'kit_surface_look.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';

part 'kit_log_panel_tools.dart';

/// What kind of line: drives the look (a neutral gutter glyph and `text1`),
/// never colour alone.
enum KitLogLevel { normal, warning, error }

/// One line of output.
@immutable
class KitLogLine {
  const KitLogLine(this.text, {this.level = KitLogLevel.normal, this.at});

  /// One line, no newline; redacted by the panel before it is drawn or
  /// copied.
  final String text;
  final KitLogLevel level;

  /// When it arrived (for "Last line 12 s ago").
  final DateTime? at;
}

/// How the source ended.
@immutable
class KitLogEnd {
  const KitLogEnd({this.exitCode, this.failed = false, this.reason});

  /// "Ended · exit 1".
  final int? exitCode;

  /// The header word is "Failed" instead of "Ended".
  final bool failed;

  /// One line in words, from the caller (agentErrorWords).
  final String? reason;
}

enum KitLogSize {
  /// About 12 lines tall ([KitTokens.logFoldedLines]): the panel inside a
  /// KitDetailsFold or a card.
  folded,

  /// Fills its host: a page whose job is the log, or a full-height sheet.
  /// The host gives it a bounded height.
  fill,
}

/// A bounded ring buffer of lines, for sources that hand over text chunks
/// (a process's stdout, SetupTerminal's `output`). Splits on newlines,
/// keeps a partial last line open until its newline arrives (shown as the
/// last line meanwhile), drops the oldest lines past [capacity] and counts
/// them in [dropped].
class KitLogBuffer extends ValueNotifier<List<KitLogLine>> {
  KitLogBuffer({this.capacity = KitLogPanel.defaultMaxLines})
    : assert(capacity > 0),
      super(const []);

  final int capacity;

  final List<KitLogLine> _lines = [];
  String _partial = '';
  KitLogLevel _partialLevel = KitLogLevel.normal;
  DateTime? _partialAt;
  int _dropped = 0;
  int _debugCreatedLines = 0;

  /// Counts internally materialized lines in debug/test builds only.
  @visibleForTesting
  int get debugCreatedLineCount => _debugCreatedLines;

  KitLogLine _line(
    String text, {
    KitLogLevel level = KitLogLevel.normal,
    DateTime? at,
  }) {
    assert(() {
      _debugCreatedLines++;
      return true;
    }());
    return KitLogLine(text, level: level, at: at);
  }

  /// Lines dropped past [capacity] since the last [clear].
  int get dropped => _dropped;

  void add(KitLogLine line) {
    _closePartial();
    _lines.add(line);
    _publish();
  }

  void appendText(String chunk, {KitLogLevel level = KitLogLevel.normal}) {
    if (chunk.isEmpty) return;
    final text = _partial + chunk;
    final now = clock.now();
    var end = text.lastIndexOf('\n');
    _partial = text.substring(end + 1);
    final keep = _partial.isEmpty ? capacity : capacity - 1;
    // Count all completed lines, but materialize only the tail that can survive
    // this publication. A large polled log must not allocate its discarded
    // prefix as thousands of strings and line objects first.
    final tail = <KitLogLine>[];
    var completed = 0;
    while (end >= 0) {
      final start = end == 0 ? -1 : text.lastIndexOf('\n', end - 1);
      completed++;
      if (tail.length < keep) {
        tail.add(
          _line(_strip(text.substring(start + 1, end)), level: level, at: now),
        );
      }
      end = start;
    }
    if (completed > keep) {
      _dropped += _lines.length + completed - keep;
      _lines.clear();
    }
    _lines.addAll(tail.reversed);
    _partialLevel = level;
    if (_partial.isNotEmpty) _partialAt = now;
    _publish();
  }

  /// A source that re-sends its whole output: replaces every line.
  void replaceText(String text) {
    _lines.clear();
    _partial = '';
    _dropped = 0;
    appendText(text);
    if (text.isEmpty) _publish();
  }

  void clear() {
    _lines.clear();
    _partial = '';
    _dropped = 0;
    _publish();
  }

  void _closePartial() {
    if (_partial.isEmpty) return;
    _lines.add(_line(_strip(_partial), level: _partialLevel, at: _partialAt));
    _partial = '';
  }

  static String _strip(String s) =>
      s.endsWith('\r') ? s.substring(0, s.length - 1) : s;

  void _publish() {
    final keep = _partial.isEmpty ? capacity : capacity - 1;
    final int over = _lines.length - (keep < 0 ? 0 : keep);
    if (over > 0) {
      _lines.removeRange(0, over);
      _dropped += over;
    }
    value = List.unmodifiable([
      ..._lines,
      if (_partial.isNotEmpty)
        _line(_strip(_partial), level: _partialLevel, at: _partialAt),
    ]);
  }
}

/// The one log view (K2 §1.11, KIT-31).
///
/// States: empty, live, quiet, ended, failed, failed-to-read, scrolled-up,
/// dropped.
///
/// Follows the newest line until the person scrolls up (then a
/// [KitJumpPill] counts the new lines), calls [onRefresh] only while it is
/// on screen, redacts every line once ([KitRedact.text]) and says in words
/// whether the source is live, quiet or ended. The body is an LTR mono
/// block in every locale; the header follows the locale.
class KitLogPanel extends StatefulWidget {
  const KitLogPanel({
    super.key,
    required this.lines,
    this.title,
    this.live = false,
    this.ended,
    this.onRefresh,
    this.pollEvery = KitMotion.logPoll,
    this.follow = true,
    this.maxLines = defaultMaxLines,
    this.emptyText,
    this.size = KitLogSize.folded,
    this.wrap,
    this.onWrapChanged,
    this.panelKey,
    this.copyAllKey,
    this.wrapKey,
    this.headerAction,
    this.searchable = false,
    this.selectable,
  });

  /// Adds a search field above the lines (matches are marked, Next and
  /// Previous step between them), and a Pause follow toggle. For a page or
  /// full sheet whose job is the log; a folded panel stays plain.
  final bool searchable;

  /// Lets the person select lines and copy the selection. Null is on for
  /// wide windows, and for a [searchable] panel on any window.
  final bool? selectable;

  /// A [KitLogBuffer] or the caller's own listenable.
  final ValueListenable<List<KitLogLine>> lines;

  /// Header words; null is l10n.kitLogTitle ("Output").
  final String? title;

  /// Null is l10n.kitLogEmpty ("No output yet").
  final String? emptyText;

  /// The source is still writing.
  final bool live;

  /// Sticks to the newest line until the person scrolls up. False never
  /// auto-scrolls.
  final bool follow;

  /// The source finished; polling stops.
  final KitLogEnd? ended;

  /// Called every [pollEvery], only while the panel is visible, never
  /// overlapping, until [ended] is set.
  final Future<void> Function()? onRefresh;
  final Duration pollEvery;

  /// Lines kept on screen; older ones are dropped and counted.
  final int maxLines;
  final KitLogSize size;

  /// Null wraps on compact and scrolls sideways from medium (the Wrap
  /// toggle then flips the panel's own choice). Non-null is controlled:
  /// the toggle calls [onWrapChanged].
  final bool? wrap;
  final ValueChanged<bool>? onWrapChanged;

  /// The panel; null is `ValueKey('kit-log-panel')`.
  final Key? panelKey;

  /// Copy all; null is `ValueKey('kit-log-copy-all')`.
  final Key? copyAllKey;

  /// Wrap; null is `ValueKey('kit-log-wrap')`.
  final Key? wrapKey;

  /// One more action in the header, after Copy all, with its words shown
  /// (R3): a host's "Copy failure report" belongs inside the panel it
  /// reports on, not under it. It shares the header row when the row has
  /// room and takes its own header line, under Copy all, when it has not
  /// (a phone), so its words are never cut short. A [KitAction.copy]
  /// copies with the check feedback. Null shows nothing.
  final KitAction? headerAction;

  /// Lines kept on screen; older ones are dropped and counted (honest state).
  static const int defaultMaxLines = 2000;

  /// The folded panel inside the one details fold (K2 §4.4: folded under
  /// Details unless the page exists to show the log). The fold's label is
  /// l10n.kitLogShowOutput ("Show output"). A page that already has a
  /// KitDetailsFold passes a KitLogPanel as that fold's `child` instead
  /// (one fold per page, KIT-33).
  static Widget fold({
    Key? key,
    required ValueListenable<List<KitLogLine>> lines,
    bool live = false,
    KitLogEnd? ended,
    Future<void> Function()? onRefresh,
    String? label,
    Key? foldKey,
    Key? panelKey,
  }) => Builder(
    key: key,
    builder: (context) => KitDetailsFold(
      label: label ?? _l10n(context).kitLogShowOutput,
      foldKey: foldKey,
      child: KitLogPanel(
        lines: lines,
        live: live,
        ended: ended,
        onRefresh: onRefresh,
        panelKey: panelKey,
      ),
    ),
  );

  @override
  State<KitLogPanel> createState() => _KitLogPanelState();
}

AppLocalizations _l10n(BuildContext context) =>
    lookupAppLocalizations(Localizations.localeOf(context));

/// Each line's redacted text, computed once when the line first reaches a
/// panel (G12, SEC-2).
final Expando<String> _redacted = Expando<String>('KitLogPanel.redacted');

String _safe(KitLogLine line) => _redacted[line] ??= KitRedact.text(line.text);

class _KitLogPanelState extends State<KitLogPanel> with WidgetsBindingObserver {
  final ScrollController _vertical = ScrollController();
  final ScrollController _horizontal = ScrollController();
  final FocusNode _bodyFocus = FocusNode(debugLabel: 'KitLogPanel body');

  bool _following = true;
  int _unseen = 0;
  int _seenTotal = 0;
  bool? _ownWrap;
  DateTime _lastLineAt = clock.now();

  Timer? _poll;
  bool _polling = false;
  bool _readFailed = false;
  bool _tickerEnabled = true;
  bool _routeCurrent = true;
  bool _jumping = false;

  // Search (searchable panels): the settled query, the lines holding it
  // and which of them is current; Pause stops the automatic scroll.
  String _query = '';
  List<int> _matches = const [];
  int _current = -1;
  bool _paused = false;

  bool get _follow => widget.follow && !_paused;

  /// With wrap off every row has one extent, so the newest end is known
  /// exactly before the list lays out new rows: rows × extent. Null while
  /// wrapped (the list only estimates its extent then).
  double? _fixedContent;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    widget.lines.addListener(_onLines);
    _vertical.addListener(_onScroll);
    _seenTotal = _total(widget.lines.value);
    _lastLineAt = _latestAt(widget.lines.value) ?? clock.now();
    _scheduleJumpToEnd();
    _schedulePoll();
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _tickerEnabled = TickerMode.valuesOf(context).enabled;
    _routeCurrent = ModalRoute.of(context)?.isCurrent ?? true;
  }

  @override
  void didUpdateWidget(KitLogPanel old) {
    super.didUpdateWidget(old);
    if (old.lines != widget.lines) {
      old.lines.removeListener(_onLines);
      widget.lines.addListener(_onLines);
      _seenTotal = _total(widget.lines.value);
      _unseen = 0;
    }
    if (old.ended != widget.ended ||
        old.onRefresh != widget.onRefresh ||
        old.pollEvery != widget.pollEvery) {
      _poll?.cancel();
      _schedulePoll();
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    widget.lines.removeListener(_onLines);
    _poll?.cancel();
    _vertical.dispose();
    _horizontal.dispose();
    _bodyFocus.dispose();
    super.dispose();
  }

  // ---- lines and following ------------------------------------------------

  int _dropped(List<KitLogLine> lines) {
    final source = widget.lines;
    final buffered = source is KitLogBuffer ? source.dropped : 0;
    return buffered + math.max(0, lines.length - widget.maxLines);
  }

  int _total(List<KitLogLine> lines) => lines.length + _dropped(lines);

  static DateTime? _latestAt(List<KitLogLine> lines) =>
      lines.isEmpty ? null : lines.last.at;

  void _onLines() {
    if (!mounted) return;
    final lines = widget.lines.value;
    final total = _total(lines);
    final added = math.max(0, total - _seenTotal);
    _seenTotal = total;
    if (_query.isNotEmpty) _findMatches(keepPosition: true);
    setState(() {
      if (added > 0) {
        _lastLineAt = _latestAt(lines) ?? clock.now();
        if (!_following || !_follow) _unseen += added;
      }
    });
    if (_following && _follow) _scheduleJumpToEnd();
  }

  /// Jumps to the newest line after the next layout. With wrapped lines the
  /// list only estimates its extent until the end is laid out, so it jumps
  /// again (a few frames at most) until it really sits at the end.
  void _scheduleJumpToEnd([int attempts = 3]) {
    if (!_follow) return;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted || !_vertical.hasClients || !_following) return;
      final position = _vertical.position;
      if (position.pixels >= position.maxScrollExtent) return;
      _jumping = true;
      _vertical.jumpTo(position.maxScrollExtent);
      _jumping = false;
      if (attempts > 1) {
        _scheduleJumpToEnd(attempts - 1);
        WidgetsBinding.instance.scheduleFrame();
      }
    });
  }

  void _onScroll() {
    if (_jumping || !_vertical.hasClients) return;
    final position = _vertical.position;
    final atEnd =
        position.pixels >= position.maxScrollExtent - _lineExtent(context);
    if (atEnd == _following) return;
    setState(() {
      _following = atEnd;
      if (atEnd) _unseen = 0;
    });
  }

  final GlobalKey _activeKey = GlobalKey(debugLabel: 'KitLogPanel active line');

  /// Brings line [index] into view. Fixed-height rows know their offset;
  /// wrapped rows are estimated, then corrected once the row is built.
  void _jumpToLine(int index, [int attempts = 4]) {
    if (!_vertical.hasClients) return;
    final count = _shownLines.length;
    if (index < 0 || index >= count) return;
    final position = _vertical.position;
    final extent = _lineExtent(context);
    final dropped = _dropped(widget.lines.value) > 0 ? 1 : 0;
    final fixed = (index + dropped) * extent;
    final target = _fixedContent != null
        ? fixed
        : position.maxScrollExtent * (index + dropped) / (count + dropped);
    if (_following) setState(() => _following = false);
    _jumping = true;
    _vertical.jumpTo(
      (target - position.viewportDimension * .35).clamp(
        position.minScrollExtent,
        position.maxScrollExtent,
      ),
    );
    _jumping = false;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      final row = _activeKey.currentContext;
      if (row != null) {
        _jumping = true;
        Scrollable.ensureVisible(row, alignment: .35);
        _jumping = false;
      } else if (attempts > 1) {
        _jumpToLine(index, attempts - 1);
      }
    });
  }

  List<KitLogLine> get _shownLines {
    final all = widget.lines.value;
    return all.length > widget.maxLines
        ? all.sublist(all.length - widget.maxLines)
        : all;
  }

  void _findMatches({required bool keepPosition}) {
    if (_query.isEmpty) {
      _matches = const [];
      _current = -1;
      return;
    }
    final needle = _query.toLowerCase();
    final shown = _shownLines;
    _matches = [
      for (var i = 0; i < shown.length; i++)
        if (_safe(shown[i]).toLowerCase().contains(needle)) i,
    ];
    if (_matches.isEmpty) {
      _current = -1;
    } else if (!keepPosition || _current < 0 || _current >= _matches.length) {
      _current = _matches.length - 1; // the newest match first
    }
  }

  void _search(String query) {
    setState(() {
      _query = query.trim();
      _findMatches(keepPosition: false);
    });
    _revealMatch();
  }

  void _step(int by) {
    if (_matches.isEmpty) return;
    setState(() => _current = (_current + by) % _matches.length);
    _revealMatch();
  }

  void _revealMatch() {
    if (_current < 0) return;
    final line = _matches[_current];
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _jumpToLine(line);
    });
  }

  void _togglePause() {
    if (_paused) {
      unawaited(_jumpToLatest());
    } else {
      setState(() {
        _paused = true;
        _following = false;
      });
    }
  }

  Future<void> _jumpToLatest() async {
    setState(() {
      _following = true;
      _paused = false;
      _unseen = 0;
    });
    if (!_vertical.hasClients) return;
    final position = _vertical.position;
    final fixed = _fixedContent;
    final end = fixed == null
        ? position.maxScrollExtent
        : math.max(
            position.maxScrollExtent,
            fixed - position.viewportDimension,
          );
    _jumping = true;
    try {
      if (KitMotion.reduced(context)) {
        _vertical.jumpTo(end);
      } else {
        await _vertical.animateTo(
          end,
          duration: KitMotion.standard,
          curve: KitMotion.enter,
        );
        if (mounted && _vertical.hasClients) {
          _vertical.jumpTo(_vertical.position.maxScrollExtent);
        }
      }
    } finally {
      _jumping = false;
      if (mounted) _scheduleJumpToEnd();
    }
  }

  // ---- polling (PERF-4) ---------------------------------------------------

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _poll?.cancel();
      _schedulePoll();
    }
  }

  void _schedulePoll() {
    if (widget.onRefresh == null || widget.ended != null || _readFailed) {
      return;
    }
    _poll = Timer(widget.pollEvery, _tick);
  }

  bool get _visible {
    if (!mounted || !_tickerEnabled || !_routeCurrent) return false;
    final lifecycle = WidgetsBinding.instance.lifecycleState;
    if (lifecycle != null && lifecycle != AppLifecycleState.resumed) {
      return false;
    }
    final box = context.findRenderObject();
    if (box is! RenderBox || !box.attached || !box.hasSize) return false;
    final rect = box.localToGlobal(Offset.zero) & box.size;
    final scrollable = Scrollable.maybeOf(context);
    final Rect viewport;
    final host = scrollable?.context.findRenderObject();
    if (host is RenderBox && host.attached && host.hasSize) {
      viewport = host.localToGlobal(Offset.zero) & host.size;
    } else {
      final view = View.of(context);
      viewport = Offset.zero & (view.physicalSize / view.devicePixelRatio);
    }
    final overlap = rect.intersect(viewport);
    return overlap.width > 0 && overlap.height > 0;
  }

  Future<void> _tick() async {
    if (!mounted) return;
    // A lifecycle pause stops the loop; resume restarts it.
    final lifecycle = WidgetsBinding.instance.lifecycleState;
    if (lifecycle != null && lifecycle != AppLifecycleState.resumed) return;
    if (!_visible) {
      _schedulePoll();
      return;
    }
    await _refresh();
    if (mounted) _schedulePoll();
  }

  Future<bool> _refresh() async {
    final refresh = widget.onRefresh;
    if (refresh == null || _polling) return false;
    _polling = true;
    try {
      await refresh();
      if (mounted && _readFailed) setState(() => _readFailed = false);
      return true;
    } catch (_) {
      if (mounted) setState(() => _readFailed = true);
      return false;
    } finally {
      _polling = false;
    }
  }

  Future<void> _tryAgain() async {
    _poll?.cancel();
    final ok = await _refresh();
    if (ok && mounted) _schedulePoll();
  }

  // ---- layout -------------------------------------------------------------

  TextStyle _mono(BuildContext context, KitTextTone tone) =>
      KitText.styleOf(context, KitTextRole.mono, tone: tone);

  double _glyph(BuildContext context) {
    final tokens = KitTokens.of(context);
    return tokens.iconSize(context, tokens.smallIconSize);
  }

  /// One fixed line extent per text scale: the mono line height or the
  /// gutter glyph, whichever is larger.
  double _lineExtent(BuildContext context) {
    final base = KitText.styleFor(KitTextRole.mono);
    final line =
        MediaQuery.textScalerOf(context).scale(base.fontSize!) * base.height!;
    return math.max(line, _glyph(context)).ceilToDouble();
  }

  bool _wrapOn(BuildContext context) =>
      widget.wrap ??
      _ownWrap ??
      KitLayout.windowOf(context) == KitWindow.compact;

  void _toggleWrap(bool current) {
    final next = !current;
    if (widget.wrap == null) setState(() => _ownWrap = next);
    widget.onWrapChanged?.call(next);
  }

  String _copyText(List<KitLogLine> shown, int dropped) => [
    if (dropped > 0) _l10n(context).kitLogDropped(dropped),
    for (final line in shown) _safe(line),
  ].join('\n');

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.list(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    final tokens = KitTokens.of(context);
    final all = widget.lines.value;
    final shown = all.length > widget.maxLines
        ? all.sublist(all.length - widget.maxLines)
        : all;
    final dropped = _dropped(all);
    final wrap = _wrapOn(context);
    final extent = _lineExtent(context);
    final folded = widget.size == KitLogSize.folded;
    _fixedContent = wrap
        ? null
        : (shown.length + (dropped > 0 ? 1 : 0)) * extent;

    final header = _Header(
      title: widget.title ?? _l10n(context).kitLogTitle,
      live: widget.live,
      ended: widget.ended,
      readFailed: _readFailed,
      lastLineAt: _lastLineAt,
      hasLines: shown.isNotEmpty,
      wrap: wrap,
      wrapKey: widget.wrapKey ?? const ValueKey('kit-log-wrap'),
      copyAllKey: widget.copyAllKey ?? const ValueKey('kit-log-copy-all'),
      onWrap: () => _toggleWrap(wrap),
      copyText: () => _copyText(shown, dropped),
      action: widget.headerAction,
    );

    Widget body = _body(context, shown, dropped, wrap, extent);
    body = KitJumpPillLayer(
      clearBottomInset: false,
      pill: KitJumpPill(
        label: _l10n(context).kitLogNewLines(_unseen),
        visible: widget.follow && (!_following || _paused) && _unseen > 0,
        pillKey: const ValueKey('kit-log-jump'),
        onPressed: _jumpToLatest,
      ),
      child: body,
    );
    if (folded) {
      final window = MediaQuery.sizeOf(context).height;
      final height = math.min(
        KitTokens.logFoldedLines * extent + tokens.space2 * 2,
        // Under half the window with room for the header above it (the
        // state words grow at large text), so the panel never overflows.
        window * .45,
      );
      body = SizedBox(height: height.floorToDouble(), child: body);
    } else {
      body = Expanded(child: body);
    }

    final column = Column(
      mainAxisSize: folded ? MainAxisSize.min : MainAxisSize.max,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        header,
        if (widget.searchable)
          _LogTools(
            paused: _paused,
            query: _query,
            matchCount: _matches.length,
            onSearch: _search,
            onStep: _step,
            onPause: _togglePause,
          ),
        if (_readFailed)
          Padding(
            key: const ValueKey('kit-log-read-failed'),
            padding: EdgeInsetsDirectional.only(
              start: tokens.space3,
              top: tokens.space2,
              end: tokens.space3,
            ),
            child: KitNotice.error(
              message: _l10n(context).kitLogReadFailed,
              retry: KitAction(
                label: _l10n(context).kitTryAgain,
                onPressed: _tryAgain,
              ),
            ),
          ),
        body,
      ],
    );

    return kitCodeSurface(
      context,
      key: widget.panelKey ?? const ValueKey('kit-log-panel'),
      fill: tokens.detailsSurface,
      borderRadius: folded
          ? BorderRadius.circular(tokens.detailsRadius)
          : BorderRadius.zero,
      child: ClipRRect(
        borderRadius: folded
            ? BorderRadius.circular(tokens.detailsRadius)
            : BorderRadius.zero,
        child: column,
      ),
    );
  }

  Widget _body(
    BuildContext context,
    List<KitLogLine> shown,
    int dropped,
    bool wrap,
    double extent,
  ) {
    final tokens = KitTokens.of(context);
    if (shown.isEmpty && dropped == 0) {
      return Padding(
        padding: EdgeInsets.symmetric(
          horizontal: tokens.space3,
          vertical: tokens.space2,
        ),
        child: Align(
          alignment: AlignmentDirectional.topStart,
          child: KitText(
            widget.emptyText ?? _l10n(context).kitLogEmpty,
            role: KitTextRole.secondary,
            tone: KitTextTone.tertiary,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
        ),
      );
    }
    final extra = dropped > 0 ? 1 : 0;
    final glyph = _glyph(context);
    final gutter = glyph + tokens.space2;
    // Vertical padding sits outside the scroll viewport, so the viewport is
    // a whole number of lines tall and no line is left cut to a sliver at
    // either edge when the view rests at the newest line.
    final padding = EdgeInsets.symmetric(horizontal: tokens.space3);

    Widget row(BuildContext context, int index) {
      if (index < extra) {
        return _DroppedRow(
          text: _l10n(context).kitLogDropped(dropped),
          wrap: wrap,
          gutter: gutter,
        );
      }
      final i = index - extra;
      final active = _current >= 0 && _matches[_current] == i;
      return _LineRow(
        key: active ? _activeKey : ValueKey('kit-log-line-$i'),
        highlight: _query.isEmpty ? null : _query,
        active: active,
        line: shown[i],
        wrap: wrap,
        glyph: glyph,
        gutter: gutter,
        extent: extent,
        style: _mono(
          context,
          shown[i].level == KitLogLevel.normal
              ? KitTextTone.secondary
              : KitTextTone.primary,
        ),
      );
    }

    final wide = KitLayout.windowOf(context).isWide;

    Widget list(double? width) => ListView.builder(
      controller: _vertical,
      padding: padding,
      itemExtent: wrap ? null : extent,
      itemCount: shown.length + extra,
      itemBuilder: row,
    );

    Widget content;
    if (wrap) {
      content = list(null);
    } else {
      content = LayoutBuilder(
        builder: (context, constraints) {
          final charWidth = _charWidth(context);
          var longest = 0;
          for (final line in shown) {
            longest = math.max(longest, _safe(line).length);
          }
          final needed =
              (longest + 1) * charWidth + gutter + padding.horizontal;
          final width = math.max(constraints.maxWidth, needed.ceilToDouble());
          return SingleChildScrollView(
            controller: _horizontal,
            scrollDirection: Axis.horizontal,
            child: SizedBox(
              width: width,
              height: constraints.maxHeight,
              child: list(width),
            ),
          );
        },
      );
    }
    // Desktop scrollbars come from the app's scroll behaviour
    // (AppScrollBehavior), always visible on a desktop build (KIT-6).
    if (widget.selectable ?? (wide || widget.searchable)) {
      content = SelectionArea(child: content);
    }

    return Focus(
      focusNode: _bodyFocus,
      onKeyEvent: _onKey,
      child: Directionality(
        textDirection: TextDirection.ltr,
        child: Padding(
          padding: EdgeInsets.symmetric(vertical: tokens.space2),
          child: content,
        ),
      ),
    );
  }

  double _charWidth(BuildContext context) {
    final painter = TextPainter(
      text: TextSpan(text: 'M', style: _mono(context, KitTextTone.secondary)),
      textDirection: TextDirection.ltr,
      textScaler: MediaQuery.textScalerOf(context),
    )..layout();
    final width = painter.width;
    painter.dispose();
    return width;
  }

  KeyEventResult _onKey(FocusNode node, KeyEvent event) {
    if (event is! KeyDownEvent && event is! KeyRepeatEvent) {
      return KeyEventResult.ignored;
    }
    if (!_vertical.hasClients) return KeyEventResult.ignored;
    final position = _vertical.position;
    final extent = _lineExtent(context);
    double? to;
    final key = event.logicalKey;
    if (key == LogicalKeyboardKey.arrowDown) {
      to = position.pixels + extent;
    } else if (key == LogicalKeyboardKey.arrowUp) {
      to = position.pixels - extent;
    } else if (key == LogicalKeyboardKey.pageDown) {
      to = position.pixels + position.viewportDimension;
    } else if (key == LogicalKeyboardKey.pageUp) {
      to = position.pixels - position.viewportDimension;
    } else if (key == LogicalKeyboardKey.home) {
      to = position.minScrollExtent;
    } else if (key == LogicalKeyboardKey.end) {
      unawaited(_jumpToLatest());
      return KeyEventResult.handled;
    }
    if (to == null) return KeyEventResult.ignored;
    _vertical.jumpTo(
      to.clamp(position.minScrollExtent, position.maxScrollExtent),
    );
    return KeyEventResult.handled;
  }
}

/// Title, state words (the panel's one polite live region), Wrap, Copy all
/// and the host's one extra action (beside Copy all, or on its own header
/// line under it when the row is narrow). The controls sit inside the header with
/// a space1 inset top and bottom and space2 at the end, so the Wrap toggle's
/// pressed fill never touches the panel's edge or corner (R3).
class _Header extends StatelessWidget {
  const _Header({
    required this.title,
    required this.live,
    required this.ended,
    required this.readFailed,
    required this.lastLineAt,
    required this.hasLines,
    required this.wrap,
    required this.wrapKey,
    required this.copyAllKey,
    required this.onWrap,
    required this.copyText,
    this.action,
  });

  final String title;
  final bool live;
  final KitLogEnd? ended;
  final bool readFailed;
  final DateTime lastLineAt;
  final bool hasLines;
  final bool wrap;
  final Key wrapKey;
  final Key copyAllKey;
  final VoidCallback onWrap;
  final String Function() copyText;
  final KitAction? action;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final l10n = _l10n(context);
    final end = ended;

    Widget words;
    if (readFailed) {
      words = const SizedBox.shrink();
    } else if (end != null) {
      final code = end.exitCode == null ? null : KitBidi.ltr('${end.exitCode}');
      final state = end.failed
          ? (code == null ? l10n.kitLogFailed : l10n.kitLogFailedExit(code))
          : (code == null ? l10n.kitLogEnded : l10n.kitLogEndedExit(code));
      words = _StateWords(text: state, reason: end.reason, failed: end.failed);
    } else if (live) {
      words = KitSince(
        since: lastLineAt,
        ticks: KitSinceTicks.minutes,
        builder: (context, status) {
          if (!status.isSlow) {
            return _StateWords(text: l10n.kitLogLive, dot: true);
          }
          final elapsed = status.elapsed;
          final text = elapsed.inMinutes < 1
              ? l10n.kitLogQuietSeconds(elapsed.inSeconds)
              : l10n.kitLogQuiet(KitSince.ageLabel(context, elapsed));
          return _StateWords(text: text);
        },
      );
    } else {
      words = const SizedBox.shrink();
    }

    // The line under the header is the style's divider (none under Soft:
    // the band keeps its place).
    final line = KitSurfaceLook.divider(
      context,
      color: tokens.roles.hairline,
      width: KitTokens.hairlineWidth(context),
    );
    return DecoratedBox(
      decoration: BoxDecoration(
        border: Border(
          // The width is the divider's: KitTokens.hairlineWidth or the
          // style's.
          bottom: BorderSide.none.copyWith(
            style: BorderStyle.solid,
            color: line.color,
            width: line.width,
          ),
        ),
      ),
      child: Padding(
        padding: EdgeInsetsDirectional.fromSTEB(
          tokens.space3,
          tokens.space1,
          tokens.space2,
          tokens.space1,
        ),
        child: action == null
            ? _row(context, tokens, l10n, words, inlineAction: false)
            : LayoutBuilder(
                builder: (context, constraints) {
                  final inline =
                      constraints.maxWidth >= _inlineWidth(context, tokens);
                  final row = _row(
                    context,
                    tokens,
                    l10n,
                    words,
                    inlineAction: inline,
                  );
                  if (inline) return row;
                  // Too narrow for the words beside everything else: the
                  // action takes its own line in the header, under Copy
                  // all, with its words whole instead of cut short.
                  return Column(
                    mainAxisSize: MainAxisSize.min,
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      row,
                      Align(
                        alignment: AlignmentDirectional.centerEnd,
                        child: _actionButton(),
                      ),
                    ],
                  );
                },
              ),
      ),
    );
  }

  Widget _actionButton() => KitButton.fromAction(
    action!,
    role: KitButtonRole.tertiary,
    expand: false,
  );

  /// The width the header row needs to hold the host's action beside Wrap
  /// and Copy all while the title and state words keep a readable column:
  /// the action's words in the button role, its glyph, gap and padding.
  double _inlineWidth(BuildContext context, KitTokens tokens) {
    final scaler =
        MediaQuery.maybeTextScalerOf(context) ?? TextScaler.noScaling;
    final painter = TextPainter(
      text: TextSpan(
        text: action!.label,
        style: KitText.styleOf(context, KitTextRole.button),
      ),
      textDirection: Directionality.of(context),
      textScaler: scaler,
      maxLines: 1,
    )..layout();
    final label = painter.width;
    painter.dispose();
    final actionWidth =
        label + tokens.smallIconSize + tokens.space2 * 3 + tokens.space1;
    final controls = tokens.minTarget * (hasLines ? 2 : 1);
    return scaler.scale(160) + controls + actionWidth;
  }

  Widget _row(
    BuildContext context,
    KitTokens tokens,
    AppLocalizations l10n,
    Widget words, {
    required bool inlineAction,
  }) => Row(
    children: [
      Expanded(
        child: Padding(
          padding: EdgeInsets.symmetric(vertical: tokens.space2),
          child: Wrap(
            crossAxisAlignment: WrapCrossAlignment.center,
            spacing: tokens.space2,
            runSpacing: tokens.space1,
            children: [
              KitText(title, role: KitTextRole.label),
              words,
            ],
          ),
        ),
      ),
      KitIconButton(
        key: wrapKey,
        icon: AppIconography.wrapText,
        tooltip: l10n.kitWrapLines,
        selected: wrap,
        size: 20,
        onPressed: onWrap,
      ),
      if (hasLines)
        KitIconButton.copy(
          key: copyAllKey,
          text: copyText,
          tooltip: l10n.kitCopyAll,
          size: 20,
        ),
      if (inlineAction) _actionButton(),
    ],
  );
}

class _StateWords extends StatelessWidget {
  const _StateWords({
    required this.text,
    this.reason,
    this.dot = false,
    this.failed = false,
  });

  final String text;
  final String? reason;
  final bool dot;
  final bool failed;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final glyph = tokens.iconSize(context, tokens.smallIconSize);
    final label = reason == null ? text : '$text. $reason';
    return Semantics(
      liveRegion: true,
      container: true,
      label: label,
      child: ExcludeSemantics(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                if (dot) ...[
                  SizedBox.square(
                    key: const ValueKey('kit-log-live-dot'),
                    dimension: tokens.space2,
                    child: DecoratedBox(
                      decoration: BoxDecoration(
                        color: tokens.roles.accent,
                        shape: BoxShape.circle,
                      ),
                    ),
                  ),
                  SizedBox(width: tokens.space1),
                ],
                if (failed) ...[
                  Icon(
                    AppIconography.error,
                    size: glyph,
                    color: tokens.roles.text3,
                  ),
                  SizedBox(width: tokens.space1),
                ],
                Flexible(
                  child: KitText(
                    text,
                    role: KitTextRole.caption,
                    tone: KitTextTone.primary,
                  ),
                ),
              ],
            ),
            if (reason != null) KitText(reason!, role: KitTextRole.caption),
          ],
        ),
      ),
    );
  }
}

class _LineRow extends StatelessWidget {
  const _LineRow({
    super.key,
    this.highlight,
    this.active = false,
    required this.line,
    required this.wrap,
    required this.glyph,
    required this.gutter,
    required this.extent,
    required this.style,
  });

  final String? highlight;
  final bool active;
  final KitLogLine line;
  final bool wrap;
  final double glyph;
  final double gutter;
  final double extent;
  final TextStyle style;

  /// [text] with every case-insensitive occurrence of [highlight] marked
  /// by a fill (the current match fills more).
  TextSpan _marked(String text, TextStyle style, KitTokens tokens) {
    final query = highlight;
    if (query == null || query.isEmpty) {
      return TextSpan(text: text, style: style);
    }
    final lower = text.toLowerCase();
    final needle = query.toLowerCase();
    final mark = style.copyWith(
      backgroundColor: tokens.roles.accent.withValues(alpha: active ? .5 : .24),
    );
    final spans = <TextSpan>[];
    var from = 0;
    while (true) {
      final at = lower.indexOf(needle, from);
      if (at < 0 || needle.length > lower.length) break;
      if (at > from) spans.add(TextSpan(text: text.substring(from, at)));
      spans.add(
        TextSpan(text: text.substring(at, at + needle.length), style: mark),
      );
      from = at + needle.length;
    }
    if (from == 0) return TextSpan(text: text, style: style);
    if (from < text.length) spans.add(TextSpan(text: text.substring(from)));
    return TextSpan(style: style, children: spans);
  }

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final l10n = _l10n(context);
    final text = _safe(line);
    final icon = switch (line.level) {
      KitLogLevel.normal => null,
      KitLogLevel.warning => AppIconography.warning,
      KitLogLevel.error => AppIconography.error,
    };
    final label = switch (line.level) {
      KitLogLevel.normal => text,
      KitLogLevel.warning => l10n.kitLogWarningLine(text),
      KitLogLevel.error => l10n.kitLogErrorLine(text),
    };
    // The semantics node hugs the words (not the row), so a screen reader's
    // focus box and the contrast check both see the text itself.
    return ConstrainedBox(
      constraints: BoxConstraints(minHeight: extent),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: gutter,
            child: icon == null
                ? null
                : Align(
                    alignment: Alignment.topLeft,
                    child: ExcludeSemantics(
                      child: Icon(icon, size: glyph, color: tokens.roles.text3),
                    ),
                  ),
          ),
          Flexible(
            child: Semantics(
              label: label,
              child: ExcludeSemantics(
                child: (highlight ?? '').isEmpty
                    ? Text(
                        text,
                        style: style,
                        softWrap: wrap,
                        maxLines: wrap ? null : 1,
                        overflow: wrap ? null : TextOverflow.ellipsis,
                        textDirection: TextDirection.ltr,
                      )
                    : Text.rich(
                        _marked(text, style, tokens),
                        softWrap: wrap,
                        maxLines: wrap ? null : 1,
                        overflow: wrap ? null : TextOverflow.ellipsis,
                        textDirection: TextDirection.ltr,
                      ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _DroppedRow extends StatelessWidget {
  const _DroppedRow({
    required this.text,
    required this.wrap,
    required this.gutter,
  });

  final String text;
  final bool wrap;
  final double gutter;

  @override
  Widget build(BuildContext context) => Padding(
    key: const ValueKey('kit-log-dropped'),
    padding: EdgeInsetsDirectional.only(start: gutter),
    child: Text(
      text,
      style: KitText.styleOf(
        context,
        KitTextRole.mono,
        tone: KitTextTone.tertiary,
      ),
      maxLines: wrap ? null : 1,
      softWrap: wrap,
      overflow: wrap ? null : TextOverflow.ellipsis,
    ),
  );
}
