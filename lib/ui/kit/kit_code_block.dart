import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart' show SelectedContent;
import 'package:highlight/highlight.dart' show Node, highlight;

import '../../l10n/app_localizations.dart';
import '../app_iconography.dart';
import '../theme_roles.dart';
import 'kit_buttons.dart';
import 'kit_copy.dart';
import 'kit_icon_button.dart';
import 'kit_layout.dart';
import 'kit_motion.dart';
import 'kit_redact.dart';
import 'kit_tappable.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';
import 'motion/kit_reveal.dart';

part 'kit_code_block_parts.dart';

/// What a [KitCodeBlock] holds (docs/ux-system/kit-api/KitCodeBlock.md).
enum KitCodeKind {
  /// Source code: syntax colour from [KitCodeBlock.language].
  code,

  /// A command the person runs elsewhere. A `$` prompt is drawn before each
  /// line, outside the copied text. By default it scrolls sideways with an
  /// edge fade while it overflows; a host that passes `wrap: true` gets a
  /// soft wrap whose continuation lines hang under the text, past the `$`
  /// (R3). Either way the copied command is the source, never the display.
  command,

  /// Output of a tool or process. No syntax colour.
  output,
}

/// Maps fenced-code language hints to the grammar names the `highlight`
/// package registers. Unknown hints render as plain text; nothing is
/// guessed (moved from `lib/ui/widgets/code_highlight.dart`, C24).
const _languageAliases = <String, String>{
  'dart': 'dart',
  'js': 'javascript',
  'javascript': 'javascript',
  'jsx': 'javascript',
  'ts': 'typescript',
  'typescript': 'typescript',
  'tsx': 'typescript',
  'py': 'python',
  'python': 'python',
  'rb': 'ruby',
  'ruby': 'ruby',
  'sh': 'bash',
  'bash': 'bash',
  'shell': 'bash',
  'zsh': 'bash',
  'console': 'bash',
  'json': 'json',
  'yaml': 'yaml',
  'yml': 'yaml',
  'toml': 'ini',
  'ini': 'ini',
  'html': 'xml',
  'xml': 'xml',
  'svg': 'xml',
  'css': 'css',
  'scss': 'scss',
  'sql': 'sql',
  'go': 'go',
  'rust': 'rust',
  'rs': 'rust',
  'kotlin': 'kotlin',
  'kt': 'kotlin',
  'java': 'java',
  'swift': 'swift',
  'c': 'cpp',
  'h': 'cpp',
  'cc': 'cpp',
  'cpp': 'cpp',
  'c++': 'cpp',
  'cs': 'cs',
  'csharp': 'cs',
  'php': 'php',
  'diff': 'diff',
  'patch': 'diff',
  'gradle': 'gradle',
  'dockerfile': 'dockerfile',
  'makefile': 'makefile',
  'proto': 'protobuf',
  'md': 'markdown',
  'markdown': 'markdown',
};

/// Syntax colour (docs/ux-system/kit-api/KitCodeBlock.md), moved from
/// `lib/ui/widgets/code_highlight.dart` (C24): its grammar table, size
/// limit and parse cache live here now. Colours come from [ThemeRoles]'
/// code roles; parse results are cached (48 entries, independent of the
/// active theme) and a source over [sizeLimit] characters is left plain.
abstract final class KitCodeHighlight {
  /// Blocks longer than this render unhighlighted: parsing pathological
  /// payloads on the UI thread is worse than plain text.
  static const int sizeLimit = 20000;

  /// Whether [language] (a fenced-code hint) has a registered grammar.
  static bool supports(String? language) =>
      _languageAliases.containsKey(language?.trim().toLowerCase());

  static const _cacheLimit = 48;
  static final Map<String, List<Node>?> _cache = <String, List<Node>?>{};

  /// The grammar name the `highlight` package registers for a language
  /// hint, or null when the hint is unknown (nothing is guessed).
  static String? grammarFor(String? language) =>
      _languageAliases[language?.trim().toLowerCase()];

  /// The grammar for the file at [path], from its extension (or, for
  /// `Dockerfile` and `Makefile`, its name); null when unknown.
  static String? grammarForPath(String path) {
    final name = path.split(RegExp(r'[/\\]')).last.toLowerCase();
    final dot = name.lastIndexOf('.');
    final hint = dot > 0 && dot < name.length - 1
        ? name.substring(dot + 1)
        : name;
    return grammarFor(hint);
  }

  /// [source] parsed with [grammar] (from [grammarFor]); null when the
  /// grammar fails. Not cached: the caller owns the cache.
  static List<Node>? parseNodes(String source, String grammar) {
    try {
      return highlight.parse(source, language: grammar).nodes;
    } catch (_) {
      return null;
    }
  }

  /// Parsed [nodes] as styled spans painted in [roles]. [legible] may move
  /// each syntax colour (the diff view keeps them readable on its tinted
  /// rows).
  static TextSpan fromNodes(
    List<Node> nodes,
    ThemeRoles roles, {
    Color Function(Color color)? legible,
  }) => TextSpan(children: _spansFor(nodes, roles, legible));

  /// [source] as styled spans for the [language] hint, painted in [roles].
  /// Falls back to one plain span when the language is unknown, the block
  /// is oversized, or the grammar fails.
  static TextSpan spans(String source, String? language, ThemeRoles roles) {
    final grammar = grammarFor(language);
    if (grammar == null || source.length > sizeLimit) {
      return TextSpan(text: source);
    }
    final key = '$grammar\u0000$source';
    List<Node>? nodes;
    if (_cache.containsKey(key)) {
      nodes = _cache[key];
    } else {
      nodes = parseNodes(source, grammar);
      if (_cache.length >= _cacheLimit) {
        _cache.remove(_cache.keys.first);
      }
      _cache[key] = nodes;
    }
    if (nodes == null) return TextSpan(text: source);
    return fromNodes(nodes, roles);
  }

  static List<InlineSpan> _spansFor(
    List<Node> nodes,
    ThemeRoles roles,
    Color Function(Color color)? legible,
  ) {
    final spans = <InlineSpan>[];
    for (final node in nodes) {
      final style = _styleFor(node.className, roles, legible);
      final children = node.children;
      if (children == null) {
        if (node.value?.isNotEmpty == true) {
          spans.add(TextSpan(text: node.value, style: style));
        }
        continue;
      }
      spans.add(
        TextSpan(children: _spansFor(children, roles, legible), style: style),
      );
    }
    return spans;
  }

  static TextStyle? _styleFor(
    String? className,
    ThemeRoles roles,
    Color Function(Color color)? legible,
  ) {
    final style = _rawStyleFor(className, roles);
    final color = style?.color;
    if (legible == null || style == null || color == null) return style;
    return style.copyWith(color: legible(color));
  }

  static TextStyle? _rawStyleFor(String? className, ThemeRoles roles) =>
      switch (className) {
        'keyword' || 'built_in' || 'literal' || 'type' || 'tag' => TextStyle(
          color: roles.codeKeyword,
          fontWeight: FontWeight.w600,
        ),
        'string' ||
        'regexp' ||
        'symbol' ||
        'template-variable' => TextStyle(color: roles.codeString),
        'comment' ||
        'quote' => TextStyle(color: roles.text3, fontStyle: FontStyle.italic),
        'number' ||
        'attr' ||
        'attribute' ||
        'variable' ||
        'title' ||
        'class' ||
        'function' ||
        'section' ||
        'name' ||
        'meta' ||
        'meta-string' ||
        'selector-tag' ||
        'selector-class' => TextStyle(color: roles.codeType),
        'addition' => TextStyle(color: roles.success),
        'deletion' => TextStyle(color: roles.codeRemoved),
        _ => null,
      };
}

/// A block of code, a command or output (K2 §1.9). Always LTR, mono,
/// selectable, redacted.
///
/// States: default, capped, wrapped, scrolling, copied, empty.
///
/// A header appears only for words: a caption, a file name, counts, or a
/// copy label on a block of several lines ("Copy commands", read as a
/// labelled button). Without words there is no header band: Copy sits at
/// the end of the first line, centred on it, with Wrap under it (R3). Wrap
/// is offered only while a line is wider than the block, and shows its on
/// state as an accent glyph.
class KitCodeBlock extends StatefulWidget {
  const KitCodeBlock({
    super.key,
    required this.text,
    this.kind = KitCodeKind.code,
    this.language,
    this.caption,
    this.fileName,
    this.added,
    this.removed,
    this.maxLines = 12,
    this.onOpenFull,
    this.wrap,
    this.onWrapChanged,
    this.showWrapToggle = true,
    this.copyText,
    this.copyLabel,
    this.copyable = true,
    this.highlight = true,
    this.lineNumbers = false,
    this.blockKey,
    this.copyKey,
    this.showAllKey,
  }) : fill = false,
       marks = const [],
       activeMark = null,
       initialLine = null;

  /// The same block filling its host: no cap, no outer radius, virtualised
  /// by line (`ListView.builder`), for KitViewer and full readers. [marks]
  /// highlight find matches; [activeMark] is scrolled into view;
  /// [initialLine] (1-based) is scrolled to and marked on first build.
  const KitCodeBlock.fill({
    super.key,
    required this.text,
    this.kind = KitCodeKind.code,
    this.language,
    this.wrap,
    this.onWrapChanged,
    this.copyText,
    this.highlight = true,
    this.lineNumbers = true,
    this.marks = const [],
    this.activeMark,
    this.initialLine,
    this.blockKey,
  }) : fill = true,
       caption = null,
       fileName = null,
       added = null,
       removed = null,
       maxLines = null,
       onOpenFull = null,
       showWrapToggle = false,
       copyLabel = null,
       copyable = false,
       copyKey = null,
       showAllKey = null;

  final String text;
  final KitCodeKind kind;
  final String? language, caption, fileName, copyText, copyLabel;
  final int? added, removed, maxLines, activeMark, initialLine;
  final VoidCallback? onOpenFull;
  final bool? wrap;
  final ValueChanged<bool>? onWrapChanged;
  final bool showWrapToggle, copyable, highlight, lineNumbers, fill;
  final List<TextRange> marks;
  final Key? blockKey, copyKey, showAllKey;

  /// The wrap a block uses when [wrap] is null: `command` and `code`
  /// scroll sideways on every window, so a long line is never broken
  /// mid-identifier ("getStringExt / ra"); the Wrap toggle wraps them. Output
  /// wraps on a compact window and scrolls sideways from medium up (today's
  /// reader default, `ReaderWrapButton`). A wrapping block prefers breaks at
  /// spaces and punctuation ([kitCodeBreakable]).
  static bool defaultWrap(BuildContext context, KitCodeKind kind) {
    if (kind != KitCodeKind.output) return false;
    return KitLayout.windowOf(context) == KitWindow.compact;
  }

  @override
  State<KitCodeBlock> createState() => _KitCodeBlockState();
}

/// One line of [text] with a trailing newline trimmed first (TEST-3 header
/// note: "Lines are counted after trailing-newline trim").
List<String> _splitLines(String text) {
  final trimmed = text.endsWith('\n')
      ? text.substring(0, text.length - 1)
      : text;
  return trimmed.split('\n');
}

class _KitCodeBlockState extends State<KitCodeBlock> {
  bool? _wrap;
  bool _expanded = false;

  /// The exact text a refused Copy tried to put on the clipboard; Try again
  /// copies this snapshot, not whatever the block shows by then.
  String? _copyFailed;
  final ScrollController _fillController = ScrollController();
  final ScrollController _fillSideways = ScrollController();

  @override
  void initState() {
    super.initState();
    if (widget.fill && widget.initialLine != null) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) _scrollToLine(widget.initialLine!, animate: false);
      });
    }
  }

  @override
  void didUpdateWidget(KitCodeBlock old) {
    super.didUpdateWidget(old);
    if (widget.fill &&
        widget.activeMark != null &&
        widget.activeMark != old.activeMark) {
      final line = _lineOfOffset(_markStart(widget.activeMark!));
      if (line != null) _scrollToLine(line);
    }
  }

  @override
  void dispose() {
    _fillController.dispose();
    _fillSideways.dispose();
    super.dispose();
  }

  int _markStart(int index) =>
      index >= 0 && index < widget.marks.length ? widget.marks[index].start : 0;

  /// The 1-based line [offset] (a character index into [widget.text]) falls
  /// on, or null when the lines are not yet known.
  int? _lineOfOffset(int offset) {
    var cursor = 0;
    final lines = _splitLines(widget.text);
    for (var i = 0; i < lines.length; i++) {
      final end = cursor + lines[i].length;
      if (offset <= end) return i + 1;
      cursor = end + 1;
    }
    return lines.length;
  }

  double get _itemExtent {
    final style = KitText.styleFor(KitTextRole.mono);
    final scale = MediaQuery.maybeTextScalerOf(context)?.scale(1) ?? 1;
    return (style.fontSize ?? 13) * (style.height ?? 19 / 13) * scale;
  }

  void _scrollToLine(int line, {bool animate = true}) {
    if (!_fillController.hasClients) return;
    final target = math.max(0, line - 1) * _itemExtent;
    final max = _fillController.position.maxScrollExtent;
    final clamped = target.clamp(0.0, max);
    if (animate) {
      _fillController.animateTo(
        clamped,
        duration: KitMotion.standard,
        curve: KitMotion.enter,
      );
    } else {
      _fillController.jumpTo(clamped);
    }
  }

  /// [_wrap] is null until the person toggles the header's Wrap control:
  /// before that, wrap tracks [KitCodeBlock.defaultWrap] live, so a block
  /// left uncontrolled still reacts to the window changing (a rotation, a
  /// resize) instead of freezing at whatever the first build computed.
  bool _effectiveWrap(BuildContext context) {
    if (widget.wrap != null) return widget.wrap!;
    if (widget.kind == KitCodeKind.command) return false;
    return _wrap ?? KitCodeBlock.defaultWrap(context, widget.kind);
  }

  void _toggleWrap(BuildContext context) {
    final next = !_effectiveWrap(context);
    if (widget.onWrapChanged != null) {
      widget.onWrapChanged!(next);
      return;
    }
    setState(() => _wrap = next);
  }

  AppLocalizations _l10n(BuildContext context) =>
      lookupAppLocalizations(Localizations.localeOf(context));

  String _copyFailedText(AppLocalizations l10n) => switch (widget.kind) {
    KitCodeKind.code => l10n.kitCodeCopyFailedCode,
    KitCodeKind.command => l10n.kitCodeCopyFailedCommand,
    KitCodeKind.output => l10n.kitCodeCopyFailedOutput,
  };

  Future<void> _retryCopy(String snapshot) async {
    try {
      await KitCopy.copy(context, snapshot);
    } on Exception {
      return; // Still refused: the words and Try again stay.
    }
    if (mounted) setState(() => _copyFailed = null);
  }

  /// What failed, in words, and the way forward, under the block.
  Widget _copyFailure(KitTokens tokens, AppLocalizations l10n, String value) =>
      Padding(
        padding: EdgeInsetsDirectional.only(top: tokens.space2),
        child: Wrap(
          key: const ValueKey('kit-code-copy-failed'),
          crossAxisAlignment: WrapCrossAlignment.center,
          spacing: tokens.space2,
          children: [
            KitText(
              _copyFailedText(l10n),
              role: KitTextRole.secondary,
              tone: KitTextTone.danger,
            ),
            KitButton.tertiary(
              key: const ValueKey('kit-code-copy-retry'),
              label: l10n.kitTryAgain,
              onPressed: () => _retryCopy(value),
            ),
          ],
        ),
      );

  String _copyLabel(AppLocalizations l10n) {
    if (widget.copyLabel != null) return widget.copyLabel!;
    return switch (widget.kind) {
      KitCodeKind.code => l10n.kitCodeCopyCode,
      KitCodeKind.command => l10n.kitCodeCopyCommand,
      KitCodeKind.output => l10n.kitCodeCopyOutput,
    };
  }

  @override
  Widget build(BuildContext context) {
    // SEC-4: a command never carries a real credential; it carries a
    // placeholder ("<your key>"). This is a debug-only contract check, not
    // a display transform (the text itself is still redacted below).
    assert(
      widget.kind != KitCodeKind.command ||
          !KitRedact.containsSecret(widget.text),
      'KitCodeBlock(kind: command) text looks like it carries a real '
      'credential; commands must use a placeholder (SEC-4)',
    );

    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final l10n = _l10n(context);

    if (widget.text.trim().isEmpty) {
      return _empty(context, tokens, roles, l10n);
    }

    if (widget.fill) {
      // `.fill` never has a header (its constructor fixes caption, fileName,
      // added, removed and showWrapToggle to null/false): it hands the
      // scrollable straight to its host's own bounded height, unwrapped, so
      // a `ListView` here never sees the unbounded height a plain `Column`
      // would relay to a non-flex child.
      return _ltrBlock(_buildFill(context, tokens, roles));
    }

    return LayoutBuilder(
      builder: (context, constraints) {
        final plan = _plan(context, tokens, constraints.maxWidth);
        final header = _buildHeader(context, tokens, roles, l10n, plan);
        Widget body = _ltrBlock(
          _buildBounded(context, tokens, roles, l10n, inlineCopy: plan.inline),
        );
        // Copy (and Wrap, while a line overflows) at the end of the first
        // line (R3), stacked, instead of a header band with nothing to say.
        // They sit outside the LTR box, so they keep the locale's end side
        // like every other trailing control.
        if (plan.inline) {
          body = Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(child: body),
              SizedBox(width: tokens.space2),
              Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  if (widget.copyable) _copyIcon(l10n),
                  if (plan.wrapToggle) _wrapToggle(context, l10n),
                ],
              ),
            ],
          );
        }
        final column = Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            if (header != null) ...[header, SizedBox(height: tokens.space2)],
            body,
            if (_copyFailed case final failed?)
              _copyFailure(tokens, l10n, failed),
          ],
        );
        // space4 (16), not space3 (12): codeRadius is 14, and padding under
        // a corner's own radius leaves a line's first glyph inside the
        // curve, where a screen reader's contrast check samples the ground
        // behind the rounded corner instead of detailsSurface (found by gate
        // G5). With Copy on the first line the top, end and bottom insets
        // shrink to space1: the 48 dp Copy target fills that edge and the
        // line is centred on it, so the first glyph still sits well clear of
        // the curve (R3: a one-line command is one line tall).
        final padding = plan.inline
            ? EdgeInsetsDirectional.fromSTEB(
                tokens.space4,
                tokens.space1,
                tokens.space1,
                tokens.space1,
              )
            : EdgeInsetsDirectional.all(tokens.space4);
        return DecoratedBox(
          decoration: ShapeDecoration(
            color: tokens.detailsSurface,
            shape: tokens.shapeOf(KitShape.code),
          ),
          child: Padding(padding: padding, child: column),
        );
      },
    );
  }

  Widget _ltrBlock(Widget body) => Directionality(
    textDirection: TextDirection.ltr,
    child: KeyedSubtree(
      key: widget.blockKey ?? const ValueKey('kit-code-block'),
      child: body,
    ),
  );

  Widget _empty(
    BuildContext context,
    KitTokens tokens,
    ThemeRoles roles,
    AppLocalizations l10n,
  ) {
    final text = KitText(
      l10n.kitCodeEmpty,
      role: KitTextRole.mono,
      tone: KitTextTone.tertiary,
    );
    if (widget.fill) {
      return Padding(padding: EdgeInsets.all(tokens.space3), child: text);
    }
    return DecoratedBox(
      decoration: ShapeDecoration(
        color: tokens.detailsSurface,
        shape: tokens.shapeOf(KitShape.code),
      ),
      child: Padding(padding: EdgeInsets.all(tokens.space4), child: text),
    );
  }

  // ---------------------------------------------------------------------
  // Layout plan (R3): where Copy goes and whether Wrap is offered

  bool get _hasWords =>
      widget.fileName != null ||
      widget.caption != null ||
      widget.added != null ||
      widget.removed != null;

  /// The lines the block shows right now (the head while capped).
  List<String> _visibleLines() {
    final lines = _splitLines(KitRedact.text(widget.text));
    final maxLines = widget.maxLines;
    final capped =
        maxLines != null &&
        lines.length > maxLines &&
        (widget.onOpenFull != null || !_expanded);
    return capped ? lines.sublist(0, maxLines) : lines;
  }

  /// Copy is a labelled button in a header when there is a header for it to
  /// read in: the block already has words, or a caller's label ("Copy
  /// commands") names several lines. A single line keeps Copy on the line
  /// itself (the label is its tooltip and name).
  bool get _labelledCopy =>
      widget.copyable &&
      widget.copyLabel != null &&
      (_hasWords || _splitLines(widget.text).length > 1);

  _CodePlan _plan(BuildContext context, KitTokens tokens, double maxWidth) {
    // An unbounded width (a sideways host) has no line to end: the controls
    // go back to the header, and nothing can overflow.
    if (!maxWidth.isFinite) {
      return const _CodePlan(inline: false, wrapToggle: false);
    }
    // No words for a header to hold: the controls trail the first line.
    final trailing = !_hasWords && !_labelledCopy;
    var overflows = false;
    if (widget.showWrapToggle && widget.kind != KitCodeKind.command) {
      final monoStyle = KitText.styleOf(context, KitTextRole.mono);
      final lines = _visibleLines();
      // The text's room: the block's width less its insets, the gutter and,
      // when the controls trail the first line, their column and its gap.
      var room = trailing
          ? maxWidth -
                tokens.space4 -
                tokens.space1 -
                tokens.minTarget -
                tokens.space2
          : maxWidth - tokens.space4 * 2;
      if (_hasGutter) {
        room -= _gutterWidth(context, monoStyle, lines.length) + tokens.space2;
      }
      overflows = _longestLineWidth(context, monoStyle, lines) > room + .5;
    }
    return _CodePlan(
      inline: trailing && (widget.copyable || overflows),
      wrapToggle: overflows,
    );
  }

  /// The unwrapped width of the widest of [lines]: the longest by character
  /// count, measured once in the block's own mono style and text scale.
  double _longestLineWidth(
    BuildContext context,
    TextStyle style,
    List<String> lines,
  ) {
    var longest = '';
    for (final line in lines) {
      if (line.length > longest.length) longest = line;
    }
    if (longest.isEmpty) return 0;
    final painter = TextPainter(
      text: TextSpan(text: longest, style: style),
      textDirection: TextDirection.ltr,
      textScaler: MediaQuery.maybeTextScalerOf(context) ?? TextScaler.noScaling,
      maxLines: 1,
    )..layout();
    final width = painter.width;
    painter.dispose();
    return width;
  }

  Widget _wrapToggle(BuildContext context, AppLocalizations l10n) => _target(
    _WrapToggle(
      key: const ValueKey('kit-code-wrap'),
      on: _effectiveWrap(context),
      label: l10n.kitWrapLines,
      onPressed: () => _toggleWrap(context),
    ),
  );

  /// Copy and Wrap are at least 48 x 48 dp at any text size.
  Widget _target(Widget child) => ConstrainedBox(
    constraints: const BoxConstraints(minWidth: 48, minHeight: 48),
    child: child,
  );

  Widget _copyIcon(AppLocalizations l10n) => _target(
    KitIconButton.copy(
      key: widget.copyKey ?? const ValueKey('kit-code-copy'),
      text: () {
        // A new tap replaces an earlier refusal's words.
        if (_copyFailed != null) setState(() => _copyFailed = null);
        return widget.copyText ?? widget.text;
      },
      tooltip: _copyLabel(l10n),
      onCopyFailed: (value) {
        if (mounted) setState(() => _copyFailed = value);
      },
    ),
  );

  // ---------------------------------------------------------------------
  // Header

  Widget? _buildHeader(
    BuildContext context,
    KitTokens tokens,
    ThemeRoles roles,
    AppLocalizations l10n,
    _CodePlan plan,
  ) {
    final hasWrapToggle = plan.wrapToggle && !plan.inline;
    final showCopy = widget.copyable && !plan.inline;
    final hasName = widget.fileName != null || widget.caption != null;
    final hasCounts = widget.added != null || widget.removed != null;
    if (!hasName && !hasCounts && !hasWrapToggle && !showCopy) return null;

    Widget? name;
    if (widget.fileName != null) {
      name = Tooltip(
        message: widget.fileName!,
        excludeFromSemantics: true,
        child: KitText.mono(widget.fileName!, cut: KitMonoCut.middle),
      );
    } else if (widget.caption != null) {
      name = KitText(widget.caption!, role: KitTextRole.secondary);
    }

    Widget? counts;
    if (hasCounts) {
      final added = widget.added;
      final removed = widget.removed;
      final monoStyle = KitText.styleOf(context, KitTextRole.mono);
      counts = Semantics(
        label: l10n.kitCodeChanges(added ?? 0, removed ?? 0),
        excludeSemantics: true,
        child: Text.rich(
          TextSpan(
            children: [
              if (added != null)
                TextSpan(
                  text: '+$added',
                  style: monoStyle.copyWith(color: roles.success),
                ),
              if (added != null && removed != null) const TextSpan(text: ' '),
              if (removed != null)
                TextSpan(
                  text: '−$removed',
                  style: monoStyle.copyWith(color: roles.codeRemoved),
                ),
            ],
          ),
          textDirection: TextDirection.ltr,
        ),
      );
    }

    final trailing = <Widget>[
      if (hasWrapToggle) _wrapToggle(context, l10n),
      if (showCopy)
        _labelledCopy
            ? KitButton.fromAction(
                KitAction.copy(
                  key: widget.copyKey ?? const ValueKey('kit-code-copy'),
                  label: widget.copyLabel!,
                  text: () => widget.copyText ?? widget.text,
                ),
                role: KitButtonRole.tertiary,
                expand: false,
              )
            : _copyIcon(l10n),
    ];

    final leading = name != null || counts != null;
    final labelledCopy = showCopy && _labelledCopy;

    Widget row({double? copyMaxWidth}) => Row(
      // With nothing at the start, the controls sit at the end.
      mainAxisAlignment: MainAxisAlignment.end,
      children: [
        if (leading)
          // The name takes every width the controls leave, so it always
          // starts at the block's edge.
          Expanded(
            child: Row(
              children: [
                if (name != null) Flexible(child: name),
                if (counts != null)
                  Padding(
                    padding: name != null
                        ? EdgeInsetsDirectional.only(start: tokens.space2)
                        : EdgeInsetsDirectional.zero,
                    child: counts,
                  ),
              ],
            ),
          ),
        for (var i = 0; i < trailing.length; i++) ...[
          if (i > 0 || leading) SizedBox(width: tokens.space2),
          // Icon controls keep their target size. A labelled copy action
          // needs a width bound so its words can wrap at large text sizes:
          // beside a name it is capped (never flex, which would share the
          // row with the name and push the name off the edge); alone it
          // takes the row.
          if (labelledCopy && i == trailing.length - 1)
            copyMaxWidth != null
                ? ConstrainedBox(
                    constraints: BoxConstraints(maxWidth: copyMaxWidth),
                    child: trailing[i],
                  )
                : Flexible(child: trailing[i])
          else
            trailing[i],
        ],
      ],
    );

    if (!(leading && labelledCopy)) return row();
    return LayoutBuilder(
      builder: (context, constraints) => row(
        copyMaxWidth: constraints.maxWidth.isFinite
            ? constraints.maxWidth * .6
            : double.infinity,
      ),
    );
  }

  // ---------------------------------------------------------------------
  // Bounded body (default and .fill share the per-line row builder below)

  Widget _buildBounded(
    BuildContext context,
    KitTokens tokens,
    ThemeRoles roles,
    AppLocalizations l10n, {
    bool inlineCopy = false,
  }) {
    final safe = KitRedact.text(widget.text);
    final lines = _splitLines(safe);
    final total = lines.length;
    final maxLines = widget.maxLines;
    final hasCap = maxLines != null && total > maxLines;
    final headLines = hasCap ? lines.sublist(0, maxLines) : lines;
    final tailLines = hasCap ? lines.sublist(maxLines) : const <String>[];
    final wrap = _effectiveWrap(context);
    final monoStyle = KitText.styleOf(context, KitTextRole.mono);
    final gutterWidth = _gutterWidth(context, monoStyle, total);

    // No prompt or line-number gutter: every line joins one paragraph
    // (K2 §1.9's "one selectable text node per block"), matching
    // KitTerminalView's output form. Splitting each line into its own tiny
    // Text node (one 19 dp-tall RenderParagraph per line) is what the G5
    // contrast check was tripping on: a node that short samples its glyphs'
    // edge (anti-aliased) pixels rather than solid ink.
    Widget rows(List<String> ls, int startIndex) {
      if (!_hasGutter) {
        final children = <InlineSpan>[];
        for (var i = 0; i < ls.length; i++) {
          if (i > 0) children.add(const TextSpan(text: '\n'));
          children.add(_lineSpan(ls[i], 0, roles, monoStyle, wrap: wrap));
        }
        return Text.rich(
          TextSpan(children: children),
          textDirection: TextDirection.ltr,
          softWrap: wrap,
          overflow: wrap ? TextOverflow.clip : TextOverflow.visible,
        );
      }
      return Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          for (var i = 0; i < ls.length; i++)
            _lineRow(
              context,
              index: startIndex + i,
              line: ls[i],
              roles: roles,
              monoStyle: monoStyle,
              wrap: wrap,
              gutterWidth: gutterWidth,
            ),
        ],
      );
    }

    // The head always shows; the tail unfolds in place (MOT-5: KitReveal,
    // never AnimatedSize) only once "Show all" is pressed. A block with
    // `onOpenFull` never grows the tail in place at all: the cap stays.
    final linesColumn = Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        rows(headLines, 0),
        if (hasCap && widget.onOpenFull == null)
          KitReveal(
            child: _expanded ? rows(tailLines, headLines.length) : null,
          ),
      ],
    );

    final scrolled = wrap
        ? linesColumn
        : _horizontalGutterFrame(context, tokens, roles, [linesColumn]);

    final Widget constrained;
    if (!inlineCopy) {
      constrained = ConstrainedBox(
        constraints: BoxConstraints(minHeight: tokens.minTarget),
        child: SelectionArea(child: _CopiesSource(child: scrolled)),
      );
    } else {
      // Copy on the first line (R3): the lines get the top and bottom inset
      // that centres one line on the 48 dp Copy target, so a one-line
      // command is exactly one target tall and a longer block keeps Copy
      // beside its first line.
      final scaler =
          MediaQuery.maybeTextScalerOf(context) ?? TextScaler.noScaling;
      final lineHeight =
          scaler.scale(monoStyle.fontSize ?? 13) * (monoStyle.height ?? 1);
      final room = math.max(0.0, tokens.minTarget - lineHeight);
      final top = (room / 2).floorToDouble();
      constrained = Padding(
        padding: EdgeInsets.only(top: top, bottom: room - top),
        child: SelectionArea(child: _CopiesSource(child: scrolled)),
      );
    }

    final stillCapped = hasCap && (widget.onOpenFull != null || !_expanded);
    if (!stillCapped) return constrained;

    final capKey =
        widget.showAllKey ??
        ValueKey(
          widget.onOpenFull != null
              ? 'kit-code-open-full'
              : 'kit-code-show-all',
        );
    final label = widget.onOpenFull != null
        ? l10n.kitCodeOpenFull
        : l10n.kitCodeShowAll(total);
    final onPressed =
        widget.onOpenFull ?? () => setState(() => _expanded = true);

    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        constrained,
        Divider(
          height: tokens.space3,
          thickness: KitTokens.hairlineWidth(context),
          color: roles.hairline,
        ),
        Align(
          alignment: AlignmentDirectional.centerStart,
          child: KitButton.tertiary(
            key: capKey,
            label: label,
            onPressed: onPressed,
          ),
        ),
      ],
    );
  }

  /// The gutter and the sideways scroller for the unwrapped bounded body: a
  /// fixed left column (line numbers or the `$` prompt) that never scrolls,
  /// beside one horizontal scroller for every line (K2 §1.9: "one
  /// horizontal scroller for the whole block, not per line").
  Widget _horizontalGutterFrame(
    BuildContext context,
    KitTokens tokens,
    ThemeRoles roles,
    List<Widget> rows,
  ) => _CodeScroller(
    key: const ValueKey('kit-code-horizontal'),
    fadeColor: tokens.detailsSurface,
    fadeWidth: tokens.space6,
    child: Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: rows,
    ),
  );

  bool get _hasGutter =>
      widget.kind == KitCodeKind.command || widget.lineNumbers;

  Widget _buildFill(BuildContext context, KitTokens tokens, ThemeRoles roles) {
    final safe = KitRedact.text(widget.text);
    final lines = _splitLines(safe);
    final lineStarts = _lineStarts(lines);
    final monoStyle = KitText.styleOf(context, KitTextRole.mono);
    final gutterWidth = _gutterWidth(context, monoStyle, lines.length);
    final wrap = _effectiveWrap(context);
    final extent = _itemExtent;

    final list = ListView.builder(
      controller: _fillController,
      itemCount: lines.length,
      itemExtent: wrap ? null : extent,
      itemBuilder: (context, index) => _lineRow(
        context,
        index: index,
        line: lines[index],
        lineStart: lineStarts[index],
        roles: roles,
        monoStyle: monoStyle,
        wrap: wrap,
        gutterWidth: gutterWidth,
      ),
    );

    final selectable = SelectionArea(child: _CopiesSource(child: list));
    if (wrap) return selectable;
    // Unwrapped, a line wider than the host scrolls sideways with every
    // other line (one horizontal scroller for the whole block, K2 §1.9)
    // instead of overflowing it. A host that already lays the block out
    // at its natural width (KitViewer) leaves nothing to scroll here.
    final natural =
        (_longestLineWidth(context, monoStyle, lines) +
                (_hasGutter ? gutterWidth + tokens.space2 : 0))
            .ceilToDouble() +
        1;
    return LayoutBuilder(
      builder: (context, constraints) {
        if (!constraints.hasBoundedHeight ||
            !constraints.hasBoundedWidth ||
            natural <= constraints.maxWidth) {
          return selectable;
        }
        return Scrollbar(
          controller: _fillSideways,
          thumbVisibility: KitLayout.finePointer(context),
          notificationPredicate: (n) => n.depth == 0,
          child: SingleChildScrollView(
            key: const ValueKey('kit-code-horizontal'),
            controller: _fillSideways,
            scrollDirection: Axis.horizontal,
            child: SizedBox(
              width: natural,
              height: constraints.maxHeight,
              child: selectable,
            ),
          ),
        );
      },
    );
  }

  /// Each line's start offset into the joined (redacted) text, so marks
  /// (global [TextRange]s) can be clipped to one line without re-splitting
  /// the whole text per row.
  List<int> _lineStarts(List<String> lines) {
    final starts = List<int>.filled(lines.length, 0);
    var cursor = 0;
    for (var i = 0; i < lines.length; i++) {
      starts[i] = cursor;
      cursor += lines[i].length + 1;
    }
    return starts;
  }

  double _gutterWidth(BuildContext context, TextStyle style, int totalLines) {
    if (!_hasGutter) return 0;
    final digits = widget.kind == KitCodeKind.command
        ? 1
        : totalLines.toString().length;
    final sample = widget.kind == KitCodeKind.command
        ? r'$'
        : ''.padLeft(digits, '9');
    final painter = TextPainter(
      text: TextSpan(text: sample, style: style),
      textDirection: TextDirection.ltr,
      textScaler: MediaQuery.maybeTextScalerOf(context) ?? TextScaler.noScaling,
    )..layout();
    return painter.width;
  }

  Widget _lineRow(
    BuildContext context, {
    required int index,
    required String line,
    required ThemeRoles roles,
    required TextStyle monoStyle,
    required bool wrap,
    required double gutterWidth,
    int lineStart = 0,
  }) {
    final content = _lineSpan(line, lineStart, roles, monoStyle, wrap: wrap);
    final key = widget.fill ? ValueKey('kit-code-line-${index + 1}') : null;
    final text = Text.rich(
      content,
      key: key,
      textDirection: TextDirection.ltr,
      softWrap: wrap,
      overflow: wrap ? TextOverflow.clip : TextOverflow.visible,
    );
    if (!_hasGutter) return text;
    final gutterLabel = widget.kind == KitCodeKind.command
        ? r'$'
        : (index + 1).toString();
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        SizedBox(
          width: gutterWidth,
          child: Text(
            gutterLabel,
            // TextAlign.end, not .right: resolved against this Text's own
            // forced ltr direction it is right, satisfying G7 (LAY-8)
            // without hand-writing the absolute value.
            textAlign: TextAlign.end,
            textDirection: TextDirection.ltr,
            style: monoStyle.copyWith(color: roles.text3),
          ),
        ),
        SizedBox(width: KitTokens.of(context).space2),
        wrap ? Expanded(child: text) : text,
      ],
    );
  }

  /// [line]'s spans: syntax-highlighted for `code` when [KitCodeBlock.highlight]
  /// is on, plain otherwise, with find [KitCodeBlock.marks] painted on top.
  /// [lineStart] is this line's offset into the joined text (`.fill` only;
  /// the bounded body has no [KitCodeBlock.marks] and passes 0). A
  /// wrapping line gets break chances after punctuation ([kitCodeBreakable]).
  TextSpan _lineSpan(
    String line,
    int lineStart,
    ThemeRoles roles,
    TextStyle monoStyle, {
    required bool wrap,
  }) {
    final span = _markedLineSpan(line, lineStart, roles, monoStyle);
    // A command keeps the reviewed R3 wrap (at spaces and `/`), so a file
    // name in it is never split before its extension.
    return wrap && widget.kind != KitCodeKind.command
        ? _breakableSpan(span)
        : span;
  }

  TextSpan _markedLineSpan(
    String line,
    int lineStart,
    ThemeRoles roles,
    TextStyle monoStyle,
  ) {
    final base =
        widget.highlight &&
            widget.kind == KitCodeKind.code &&
            widget.language != null
        ? KitCodeHighlight.spans(line, widget.language, roles)
        : TextSpan(text: line);
    final withStyle = TextSpan(style: monoStyle, children: [base]);
    if (!widget.fill || widget.marks.isEmpty) return withStyle;

    final lineEnd = lineStart + line.length;
    final local = <(int start, int end, bool active)>[];
    for (var i = 0; i < widget.marks.length; i++) {
      final mark = widget.marks[i];
      final start = math.max(mark.start, lineStart);
      final end = math.min(mark.end, lineEnd);
      if (start >= end) continue;
      local.add((start - lineStart, end - lineStart, i == widget.activeMark));
    }
    if (local.isEmpty) return withStyle;
    return _paintMarks(withStyle, local, roles);
  }

  /// [span] with [kitCodeBreakable] applied to every piece of text, keeping
  /// each piece's style (syntax colour, find marks).
  static TextSpan _breakableSpan(TextSpan span) {
    InlineSpan visit(InlineSpan value) {
      if (value is! TextSpan) return value;
      final text = value.text;
      return TextSpan(
        text: text == null ? null : kitCodeBreakable(text),
        style: value.style,
        children: value.children == null
            ? null
            : [for (final c in value.children!) visit(c)],
      );
    }

    return visit(span) as TextSpan;
  }

  TextSpan _paintMarks(
    TextSpan span,
    List<(int start, int end, bool active)> ranges,
    ThemeRoles roles,
  ) {
    var offset = 0;
    InlineSpan visit(InlineSpan value) {
      if (value is! TextSpan) return value;
      final text = value.text;
      if (text == null || text.isEmpty) {
        return TextSpan(
          style: value.style,
          children: [for (final c in value.children ?? const []) visit(c)],
        );
      }
      final start = offset;
      offset += text.length;
      final end = offset;
      final hits = ranges.where((r) => r.$1 < end && r.$2 > start).toList()
        ..sort((a, b) => a.$1.compareTo(b.$1));
      if (hits.isEmpty) return TextSpan(text: text, style: value.style);
      final children = <InlineSpan>[];
      var cursor = 0;
      for (final (rawStart, rawEnd, active) in hits) {
        final from = (rawStart - start).clamp(0, text.length);
        final to = (rawEnd - start).clamp(0, text.length);
        if (from > cursor) {
          children.add(TextSpan(text: text.substring(cursor, from)));
        }
        if (to > from) {
          children.add(
            TextSpan(
              text: text.substring(from, to),
              style: TextStyle(
                backgroundColor: roles.accent.withValues(
                  alpha: active ? .38 : .18,
                ),
              ),
            ),
          );
        }
        cursor = math.max(cursor, to);
      }
      if (cursor < text.length) {
        children.add(TextSpan(text: text.substring(cursor)));
      }
      return TextSpan(style: value.style, children: children);
    }

    return visit(span) as TextSpan;
  }
}

/// The characters a wrapping code line may break after, besides spaces
/// (which already break): member access, argument and statement separators,
/// opening brackets, assignment, logic and path separators.
