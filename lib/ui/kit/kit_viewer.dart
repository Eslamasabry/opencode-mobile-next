// KitViewer — the one viewer for a file, a code block opened in full, or a
// document (docs/ux-system/kit-api/KitViewer.md; kit-v2.md §1.14, §8.2).
//
// The frame is always the same (name, muted path, one labelled action, an
// overflow, Close) and the body is one of the kit's renderers: text, code,
// Markdown, image, PDF, SVG, a delimited table, or "Can't show this file".
// Parsing, sanitising, file reading and PDF rendering stay with the caller.
import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart' show ScrollCacheExtent;
import 'package:flutter/semantics.dart';
import 'package:flutter/services.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'package:intl/intl.dart' show NumberFormat;

import '../../l10n/app_localizations.dart';
import '../app_theme.dart';
import 'chat/kit_markdown.dart';
import 'kit_bidi.dart';
import 'kit_buttons.dart';
import 'kit_code_block.dart';
import 'kit_copy.dart';
import 'kit_divider.dart';
import 'kit_icon_button.dart';
import 'kit_image.dart';
import 'kit_layout.dart';
import 'kit_menu.dart';
import 'kit_motion.dart';
import 'kit_notice.dart';
import 'kit_page_route.dart';
import 'kit_progress.dart';
import 'kit_redact.dart';
import 'kit_search_field.dart';
import 'kit_state_view.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';
import 'motion/kit_reveal.dart';

part 'kit_viewer_content.dart';
part 'kit_viewer_build.dart';
part 'kit_viewer_bodies.dart';

/// Marks a [KitViewer] that is the whole of a route (sheet or page), so its
/// name names the route.
class _KitViewerRouteScope extends InheritedWidget {
  const _KitViewerRouteScope({required super.child});

  static bool of(BuildContext context) =>
      context.getInheritedWidgetOfExactType<_KitViewerRouteScope>() != null;

  @override
  bool updateShouldNotify(_KitViewerRouteScope oldWidget) => false;
}

/// Opens the viewer (KIT-11). Returns when it closes.
///
/// `asPage: null` picks by window (§8.2): a full-height bottom sheet on a
/// compact window, the same sheet capped at [KitLayout.sheetMaxWidth] on a
/// medium one, and a pushed page (Close at the end) from expanded up.
Future<void> showKitViewer(
  BuildContext context, {
  required String name,
  required KitViewerSource source,
  String? path,
  KitAction? primary,
  List<KitMenuItem> more = const [],
  bool? asPage,
  bool interactive = true,
  VoidCallback? onOpenAll,
  bool? wrap,
  ValueChanged<bool>? onWrapChanged,
  bool? showSource,
  ValueChanged<bool>? onShowSourceChanged,
  Key? viewerKey,
}) {
  final page = asPage ?? KitLayout.windowOf(context).isWide;
  Widget viewer(BuildContext routeContext) => _KitViewerRouteScope(
    child: KitViewer(
      name: name,
      source: source,
      path: path,
      primary: primary,
      more: more,
      onClose: () => Navigator.of(routeContext).pop(),
      interactive: interactive,
      onOpenAll: onOpenAll,
      wrap: wrap,
      onWrapChanged: onWrapChanged,
      showSource: showSource,
      onShowSourceChanged: onShowSourceChanged,
      viewerKey: viewerKey,
    ),
  );
  if (page) {
    return Navigator.of(context).push<void>(
      KitPageRoute<void>(
        fullscreenDialog: true,
        builder: (routeContext) => Scaffold(
          backgroundColor: KitTokens.of(routeContext).roles.ground,
          body: SafeArea(bottom: false, child: viewer(routeContext)),
        ),
      ),
    );
  }
  final tokens = KitTokens.of(context);
  final reduced = KitMotion.reduced(context);
  // The same bottom modal `showKitSheet` presents (kit_sheet.dart), holding
  // the viewer's own frame: the sheet frame's header has no slot for the
  // action and More, and its body is a scroll view, which cannot hold a
  // virtualised file (reported in docs/qa/revamp-kit-KitViewer-2026-09-27/README.md).
  return showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    showDragHandle: false,
    backgroundColor: tokens.sheetSurface,
    barrierColor: tokens.scrim,
    elevation: tokens.sheetElevation,
    shape: RoundedRectangleBorder(
      borderRadius: BorderRadius.vertical(
        top: Radius.circular(tokens.sheetRadius),
      ),
    ),
    constraints: const BoxConstraints(maxWidth: KitLayout.sheetMaxWidth),
    sheetAnimationStyle: reduced
        ? AnimationStyle.noAnimation
        : AnimationStyle(
            duration: KitMotion.standard,
            reverseDuration: KitMotion.standard,
            curve: KitMotion.enter,
            reverseCurve: KitMotion.exit,
          ),
    builder: (sheetContext) => _KitViewerSheet(child: viewer(sheetContext)),
  );
}

/// The full-height sheet body: the handle, then the viewer.
class _KitViewerSheet extends StatelessWidget {
  const _KitViewerSheet({required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final l10n = AppLocalizations.of(context);
    final height =
        MediaQuery.sizeOf(context).height * KitLayout.sheetFullHeight;
    return Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.viewInsetsOf(context).bottom),
      child: SafeArea(
        top: false,
        child: SizedBox(
          height: height,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Semantics(
                label: l10n.kitSheetDismiss,
                onDismiss: () => Navigator.of(context).maybePop(),
                child: SizedBox(
                  height: tokens.handleHeight,
                  child: Center(
                    child: DecoratedBox(
                      decoration: BoxDecoration(
                        color: tokens.handleColor,
                        borderRadius: BorderRadius.circular(
                          tokens.handleSize.height / 2,
                        ),
                      ),
                      child: SizedBox.fromSize(size: tokens.handleSize),
                    ),
                  ),
                ),
              ),
              Expanded(child: child),
            ],
          ),
        ),
      ),
    );
  }
}

/// The same frame and body as [showKitViewer], as a widget: a two-pane
/// detail (Files on a PC), an About tab, and the goldens.
///
/// States: loading, error, empty, loaded, truncated, binary, page-failed,
/// finding.
class KitViewer extends StatefulWidget {
  const KitViewer({
    super.key,
    required this.name,
    required this.source,
    this.path,
    this.primary,
    this.more = const [],
    this.onClose,
    this.interactive = true,
    this.onOpenAll,
    this.wrap,
    this.onWrapChanged,
    this.showSource,
    this.onShowSourceChanged,
    this.showHeader = true,
    this.viewerKey,
  });

  /// "README.md": the title.
  final String name;
  final KitViewerSource source;

  /// The file's full path. The header shows only its parent folder
  /// ([folderOf]); the full value stays in semantics, and "Copy path"
  /// (the caller's [more] item) copies it whole.
  final String? path;

  /// The header's subtitle for [path]: the parent folder with its trailing
  /// slash ("docs/"), keeping anything after the name ("src/ · Line 12").
  /// Null for a root file, so the name never shows twice
  /// ("README.md / README.md"). A [path] whose last segment is not [name]
  /// (a caption such as "MIT License") is shown as it is.
  static String? folderOf(String path, String name) {
    final slash = math.max(path.lastIndexOf('/'), path.lastIndexOf(r'\'));
    final last = path.substring(slash + 1);
    if (name.isEmpty || !last.startsWith(name)) {
      return path.isEmpty ? null : path;
    }
    final folder = path.substring(0, slash + 1);
    var rest = last.substring(name.length).trim();
    if (folder.isEmpty) {
      rest = rest.replaceFirst(RegExp(r'^[\s·:,-]+'), '');
      return rest.isEmpty ? null : rest;
    }
    return rest.isEmpty ? folder : '$folder $rest';
  }

  /// At most one labelled action: "Add to prompt".
  final KitAction? primary;

  /// The caller's overflow items after the kit's own: Copy path, Save,
  /// Share, Open in Files, Open in Review.
  final List<KitMenuItem> more;

  /// Null: no Close (an embedded pane or tab).
  final VoidCallback? onClose;

  /// Markdown links and path chips open (through `openExternalLink`,
  /// SEC-1); false: inert text.
  final bool interactive;

  /// "Open all" for a truncated source.
  final VoidCallback? onOpenAll;

  /// Null: wrap on compact, scroll sideways from medium.
  final bool? wrap;

  /// The reader preference (`ReaderPreferencesStore`).
  final ValueChanged<bool>? onWrapChanged;

  /// Markdown, SVG, delimited: start on the source.
  final bool? showSource;
  final ValueChanged<bool>? onShowSourceChanged;

  /// False inside a host that has its own top bar (an About tab).
  final bool showHeader;

  /// Default `kit-viewer`.
  final Key? viewerKey;

  /// Text sources show at most this many lines; beyond it the viewer says
  /// "Showing the first 2,000 of 5,210 lines · Open all" (K2 §1.14).
  static const int maxLines = 2000;

  @override
  State<KitViewer> createState() => _KitViewerState();
}

/// Text prepared once per content: capped at [KitViewer.maxLines], redacted
/// for Find, with the counts for the truncation notice.
class _Prepared {
  _Prepared(String text) {
    final lines = text.split('\n');
    if (text.endsWith('\n')) lines.removeLast();
    totalLines = lines.length;
    capped = totalLines > KitViewer.maxLines;
    shown = capped ? lines.take(KitViewer.maxLines).join('\n') : text;
    shownLines = math.min(totalLines, KitViewer.maxLines);
    redacted = KitRedact.text(shown);
    var longestLine = '';
    for (final line in shown.split('\n')) {
      if (line.length > longestLine.length) longestLine = line;
    }
    longest = longestLine;
  }

  late final String shown, redacted, longest;
  late final int totalLines, shownLines;
  late final bool capped;
}

class _KitViewerState extends State<KitViewer> {
  /// The most of the viewer's height the frame's top (header and the
  /// truncation notice) takes before it scrolls.
  static const double _topShare = 0.5;

  final ScrollController _codeHorizontal = ScrollController();
  KitViewerContent? _content;
  Object? _error;
  bool _loading = false;
  int _generation = 0;

  bool? _wrap;
  bool? _showSource;

  bool _findOpen = false;
  String _query = '';
  List<TextRange> _marks = const [];
  int _active = 0;
  final TextEditingController _findController = TextEditingController();
  late final FocusNode _findFocus = FocusNode(
    debugLabel: 'kit-viewer-find',
    onKeyEvent: _onFindKey,
  );
  final FocusNode _frameFocus = FocusNode(debugLabel: 'kit-viewer');

  final Map<String, _Prepared> _prepared = {};

  @override
  void initState() {
    super.initState();
    _resolve();
  }

  @override
  void didUpdateWidget(KitViewer old) {
    super.didUpdateWidget(old);
    if (widget.source != old.source) _resolve();
    if (widget.wrap != old.wrap) _wrap = null;
    if (widget.showSource != old.showSource) _showSource = null;
  }

  @override
  void dispose() {
    _generation++;
    _findController.dispose();
    _codeHorizontal.dispose();
    _findFocus.dispose();
    _frameFocus.dispose();
    super.dispose();
  }

  // --- Loading ---------------------------------------------------------------

  void _resolve() {
    final generation = ++_generation;
    _prepared.clear();
    _findOpen = false;
    _query = '';
    _marks = const [];
    _active = 0;
    final source = widget.source;
    final content = source._content;
    if (content != null) {
      _content = content;
      _error = null;
      _loading = false;
      return;
    }
    _content = null;
    _error = null;
    _loading = true;
    Future.sync(source._load!).then(
      (loaded) {
        if (!mounted || generation != _generation) return;
        setState(() {
          _content = loaded;
          _loading = false;
        });
      },
      onError: (Object error, StackTrace _) {
        if (!mounted || generation != _generation) return;
        setState(() {
          _error = error;
          _loading = false;
        });
      },
    );
  }

  void _retry() => setState(_resolve);

  // --- What the body shows ----------------------------------------------------

  _Prepared _prepare(String text) =>
      _prepared.putIfAbsent(text, () => _Prepared(text));

  bool get _sourceView {
    final content = _content;
    if (content == null) return false;
    final on = _showSource ?? widget.showSource ?? false;
    return switch (content.kind) {
      KitViewerKind.markdown => on,
      KitViewerKind.svg ||
      KitViewerKind.delimited => on && content._original != null,
      _ => false,
    };
  }

  bool get _sourceApplies {
    final content = _content;
    if (content == null) return false;
    return switch (content.kind) {
      KitViewerKind.markdown => true,
      KitViewerKind.svg || KitViewerKind.delimited => content._original != null,
      _ => false,
    };
  }

  /// The text the body draws in a code block, or null when the body is not
  /// a code block (prose, image, PDF, picture, table, binary).
  String? get _codeText {
    final content = _content;
    if (content == null) return null;
    return switch (content.kind) {
      KitViewerKind.text || KitViewerKind.code => content._text,
      KitViewerKind.markdown => _sourceView ? content._text : null,
      KitViewerKind.svg => _sourceView ? content._original : null,
      KitViewerKind.delimited => _sourceView ? content._original : null,
      _ => null,
    };
  }

  bool get _isEmpty {
    final content = _content;
    if (content == null) return false;
    return switch (content.kind) {
      KitViewerKind.text ||
      KitViewerKind.code ||
      KitViewerKind.markdown ||
      KitViewerKind.svg => (content._text ?? '').trim().isEmpty,
      KitViewerKind.delimited => content._rows.isEmpty,
      _ => false,
    };
  }

  bool _effectiveWrap(BuildContext context) =>
      _wrap ?? widget.wrap ?? kitViewerDefaultWrap(context, _content?.kind);

  void _toggleWrap() {
    final next = !_effectiveWrap(context);
    setState(() => _wrap = next);
    widget.onWrapChanged?.call(next);
  }

  void _toggleSource() {
    final next = !_sourceView;
    if (_findOpen) _closeFind(refocus: false);
    setState(() => _showSource = next);
    widget.onShowSourceChanged?.call(next);
  }

  // --- Find -------------------------------------------------------------------

  bool get _findable => _codeText != null && !_isEmpty;

  void _openFind() {
    if (!_findable) return;
    if (_findOpen) {
      _findFocus.requestFocus();
      return;
    }
    setState(() => _findOpen = true);
  }

  void _closeFind({bool refocus = true}) {
    if (!_findOpen) return;
    _findController.clear();
    setState(() {
      _findOpen = false;
      _query = '';
      _marks = const [];
      _active = 0;
    });
    if (refocus) _frameFocus.requestFocus();
  }

  void _onQuery(String query) {
    final text = _codeText;
    final marks = <TextRange>[];
    if (query.isNotEmpty && text != null) {
      final haystack = _prepare(text).redacted;
      final pattern = RegExp(RegExp.escape(query), caseSensitive: false);
      for (final match in pattern.allMatches(haystack)) {
        if (match.end == match.start) continue;
        marks.add(TextRange(start: match.start, end: match.end));
      }
    }
    setState(() {
      _query = query;
      _marks = marks;
      _active = 0;
    });
    if (query.isNotEmpty) _announceCount();
  }

  void _announceCount() {
    final view = View.maybeOf(context);
    if (view == null) return;
    unawaited(
      SemanticsService.sendAnnouncement(
        view,
        _countText(AppLocalizations.of(context)),
        Directionality.of(context),
      ),
    );
  }

  String _countText(AppLocalizations l10n) => _marks.isEmpty
      ? l10n.kitViewerFindNone
      : l10n.kitViewerFindCount(_active + 1, _marks.length);

  void _step(int delta) {
    if (_marks.isEmpty) return;
    setState(() {
      _active = (_active + delta) % _marks.length;
      if (_active < 0) _active += _marks.length;
    });
  }

  KeyEventResult _onFindKey(FocusNode node, KeyEvent event) {
    if (event is! KeyDownEvent && event is! KeyRepeatEvent) {
      return KeyEventResult.ignored;
    }
    final key = event.logicalKey;
    if (key == LogicalKeyboardKey.escape) {
      _closeFind();
      return KeyEventResult.handled;
    }
    if (key == LogicalKeyboardKey.enter ||
        key == LogicalKeyboardKey.numpadEnter) {
      _step(HardwareKeyboard.instance.isShiftPressed ? -1 : 1);
      return KeyEventResult.handled;
    }
    return KeyEventResult.ignored;
  }

  // --- Keyboard and menu ------------------------------------------------------

  KeyEventResult _onFrameKey(FocusNode node, KeyEvent event) {
    if (event is! KeyDownEvent) return KeyEventResult.ignored;
    final key = event.logicalKey;
    if (key == LogicalKeyboardKey.escape) {
      if (_findOpen) {
        _closeFind();
        return KeyEventResult.handled;
      }
      final close = widget.onClose;
      if (close != null) {
        close();
        return KeyEventResult.handled;
      }
      return KeyEventResult.ignored;
    }
    final keyboard = HardwareKeyboard.instance;
    if (key == LogicalKeyboardKey.keyF &&
        (keyboard.isControlPressed || keyboard.isMetaPressed) &&
        _findable) {
      _openFind();
      return KeyEventResult.handled;
    }
    return KeyEventResult.ignored;
  }

  List<KitMenuItem> _menuItems(AppLocalizations l10n) {
    const kit = #kitViewer;
    final content = _content;
    final copyText = content?.copyText;
    final fine = KitLayout.finePointer(context);
    return [
      if (_findable)
        KitMenuItem(
          key: const ValueKey('kit-viewer-menu-find'),
          label: l10n.kitViewerFind,
          icon: AppIconography.search,
          group: kit,
          shortcut: fine ? 'Ctrl+F' : null,
          onSelected: _openFind,
        ),
      if (copyText != null)
        KitMenuItem(
          key: const ValueKey('kit-viewer-menu-copy'),
          label: l10n.kitViewerCopyContents,
          icon: AppIconography.copy,
          group: kit,
          enabled: !_isEmpty,
          disabledReason: _isEmpty ? l10n.kitViewerEmpty : null,
          // SEC-13 (coordinator 2026-09-27): the person's own content is
          // copied verbatim; only the screen text is redacted.
          onSelected: () {
            if (mounted) {
              unawaited(KitCopy.copy(context, copyText, redact: false));
            }
          },
        ),
      if (_codeText != null && !_isEmpty)
        KitMenuItem(
          key: const ValueKey('kit-viewer-menu-wrap'),
          label: l10n.kitWrapLines,
          icon: AppIconography.wrapText,
          group: kit,
          checked: _effectiveWrap(context),
          onSelected: _toggleWrap,
        ),
      if (_sourceApplies)
        KitMenuItem(
          key: const ValueKey('kit-viewer-menu-source'),
          label: l10n.kitViewerShowSource,
          icon: AppIconography.code,
          group: kit,
          checked: _sourceView,
          onSelected: _toggleSource,
        ),
      ...widget.more,
    ];
  }

  Future<void> _openMenu(BuildContext anchor, {Offset? position}) =>
      showKitMenu(
        anchor,
        items: _menuItems(AppLocalizations.of(context)),
        position: position,
        semanticsLabel: widget.name,
      );

  // --- Build ------------------------------------------------------------------

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final l10n = AppLocalizations.of(context);
    final notice = _truncationNotice(l10n);
    final body = Builder(
      builder: (bodyContext) => GestureDetector(
        behavior: HitTestBehavior.translucent,
        // The pointer twin of More, which is the labelled control.
        excludeFromSemantics: true,
        onSecondaryTapUp: (details) =>
            unawaited(_openMenu(bodyContext, position: details.globalPosition)),
        child: _body(bodyContext, tokens, l10n),
      ),
    );
    final top = Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (widget.showHeader) ...[
          _header(context, tokens, l10n),
          const KitDivider(),
        ],
        if (notice != null)
          Padding(
            padding: EdgeInsetsDirectional.fromSTEB(
              tokens.gutter,
              tokens.space2,
              tokens.gutter,
              tokens.space2,
            ),
            child: notice,
          ),
      ],
    );
    // The name is never cut: at large text on a small window the frame's
    // top scrolls within at most [_topShare] of the height, and the body
    // keeps the rest.
    final column = LayoutBuilder(
      builder: (context, constraints) => Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          ConstrainedBox(
            constraints: BoxConstraints(
              maxHeight: constraints.maxHeight * _topShare,
            ),
            child: SingleChildScrollView(child: top),
          ),
          // Find stays outside the scrolling top: it is short, and its count
          // must stay in view while the person types.
          KitReveal(child: _findOpen ? _findBar(tokens, l10n) : null),
          Expanded(child: body),
        ],
      ),
    );
    return KeyedSubtree(
      key: widget.viewerKey ?? const ValueKey('kit-viewer'),
      child: Focus(
        focusNode: _frameFocus,
        autofocus: widget.onClose != null,
        includeSemantics: false,
        onKeyEvent: _onFrameKey,
        child: column,
      ),
    );
  }
}
