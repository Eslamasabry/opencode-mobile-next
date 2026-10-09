// Retired by kit-KitMarkdown (docs/ux-system/kit-api/KitMarkdown.md,
// Compatibility): the parser, table, headings, quotes, lists, inline parser
// and path chip live in lib/ui/kit/chat/kit_markdown.dart. What stays here
// forwards to the kit, so callers keep compiling until their unit moves
// them. No @Deprecated (KIT-43 wins over R12).
import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';

import '../../l10n/app_localizations.dart';
import '../app_iconography.dart';
import '../kit/chat/kit_markdown.dart';
import '../kit/kit_buttons.dart';
import '../kit/kit_code_block.dart';
import '../kit/kit_page_route.dart';
import '../kit/kit_screen.dart';
import '../kit/kit_shape.dart';
import '../kit/kit_surface.dart';
import '../kit/kit_text.dart';
import '../kit/kit_top_bar.dart';
import 'agent_blocks.dart';
import 'reader_preferences.dart';
import 'transcript_highlight.dart';

/// Restricts markdown to local presentation in isolated previews. Defaults to
/// normal product interaction when no scope is installed.
class MarkdownInteractionScope extends InheritedWidget {
  const MarkdownInteractionScope({
    super.key,
    required this.enabled,
    required super.child,
  });
  final bool enabled;
  static bool enabledOf(BuildContext context) =>
      context
          .dependOnInheritedWidgetOfExactType<MarkdownInteractionScope>()
          ?.enabled ??
      true;
  @override
  bool updateShouldNotify(MarkdownInteractionScope oldWidget) =>
      enabled != oldWidget.enabled;
}

/// Installed by screens that can resolve server file paths. Inline code
/// spans that look like paths stay plain until [validate] confirms the file
/// is actually readable on the connected server; only then do they render
/// as tappable links routed through [open].
class MarkdownFileLinks extends InheritedWidget {
  const MarkdownFileLinks({
    super.key,
    required this.validate,
    required this.open,
    this.readImage,
    required super.child,
  });

  /// Must be memoized by the provider: spans re-request on every rebuild.
  final Future<bool> Function(String path) validate;
  final void Function(String path) open;

  /// The bytes of a picture file the reply names, for its thumbnail. Null:
  /// pictures show as named chips. Keep it a stable method, not a closure
  /// built per rebuild (thumbnails of one path share one cached image).
  final Future<Uint8List?> Function(String path)? readImage;

  static MarkdownFileLinks? maybeOf(BuildContext context) =>
      MarkdownInteractionScope.enabledOf(context)
      ? context.dependOnInheritedWidgetOfExactType<MarkdownFileLinks>()
      : null;

  @override
  bool updateShouldNotify(MarkdownFileLinks oldWidget) =>
      validate != oldWidget.validate ||
      open != oldWidget.open ||
      readImage != oldWidget.readImage;
}

/// Retired by kit-KitMarkdown: use KitMarkdown.stripPathLineSuffix.
String stripPathLineSuffix(String code) =>
    KitMarkdown.stripPathLineSuffix(code);

/// Retired by kit-KitMarkdown: use KitMarkdown.proseForSpeech.
String markdownProseForSpeech(String source) =>
    KitMarkdown.proseForSpeech(source);

/// Retired by kit-KitMarkdown: use KitMarkdown.
///
/// Forwards to [KitMarkdown]: a non-null [baseStyle] means the secondary
/// role and tone, the agent blocks arrive through the block builder, the
/// find-in-conversation highlight through the highlighter, and the
/// interaction, file-link and reader-preference scopes are read here.
class MarkdownText extends StatefulWidget {
  final String data;
  final TextStyle? baseStyle;
  final String? codeBlockLanguage;

  /// Chat bubbles that own a long-press action menu render non-selectable
  /// prose so the gesture reaches the menu instead of text selection.
  final bool selectable;

  /// Receives the text of a tapped option from a ```choices block. Without a
  /// handler the option is copied to the clipboard instead.
  final ValueChanged<String>? onChoice;

  const MarkdownText(
    this.data, {
    super.key,
    this.baseStyle,
    this.codeBlockLanguage,
    this.selectable = true,
    this.onChoice,
  });

  /// Counts full block re-parses; reads [KitMarkdown.debugParseCount].
  @visibleForTesting
  static int get debugParseCount => KitMarkdown.debugParseCount;

  @visibleForTesting
  static set debugParseCount(int value) => KitMarkdown.debugParseCount = value;

  @override
  State<MarkdownText> createState() => _MarkdownTextState();
}

class _MarkdownTextState extends State<MarkdownText> {
  MarkdownFileLinks? _linksSource;
  KitMarkdownFileLinks? _links;

  /// One stable value per host scope, so the kit's own scope does not
  /// notify every block on each rebuild.
  KitMarkdownFileLinks? _fileLinks(MarkdownFileLinks? source) {
    if (source == null) return _links = _linksSource = null;
    if (_linksSource == null ||
        _linksSource!.validate != source.validate ||
        _linksSource!.open != source.open ||
        _linksSource!.readImage != source.readImage) {
      _linksSource = source;
      _links = KitMarkdownFileLinks(
        validate: source.validate,
        open: source.open,
        readImage: source.readImage,
      );
    }
    return _links;
  }

  /// Fences with a reserved info string render as agent blocks; see
  /// [AgentBlockKinds].
  Widget? _agentBlock(BuildContext context, String info, String body) {
    if (!AgentBlockKinds.matches(info)) return null;
    return switch (info.trim().toLowerCase()) {
      AgentBlockKinds.choices => AgentChoicesBlock(
        options: AgentChoicesBlock.parse(body),
      ),
      AgentBlockKinds.checklist => AgentChecklistBlock(
        items: AgentChecklistBlock.parse(body),
      ),
      _ => AgentCommandBlock(commands: AgentCommandBlock.parse(body)),
    };
  }

  void _saveWrap(bool wrap) => saveReaderPreferences(context, wrapCode: wrap);

  /// Whether an open code reader may still offer its controls. The reader
  /// is a snapshot page that can outlive this reply: it retires Copy, Wrap
  /// and selection once the source turns inert or leaves the tree.
  ValueNotifier<bool>? _readerLive;
  bool _readerLiveDisposed = false;
  int _readersOpen = 0;

  void _openCode(String code, String? language) =>
      unawaited(_pushReader(code, language));

  Future<void> _pushReader(String code, String? language) async {
    final store = ReaderPreferencesScope.maybeOf(context);
    final live = _readerLive ??= ValueNotifier(true);
    _readersOpen++;
    try {
      await pushKitPage<void>(
        context,
        (_) => _CodeReaderPage(
          code: code,
          language: language,
          initialWrap: store?.value.wrapCode,
          onWrapChanged: store == null ? null : _saveWrap,
          live: live,
        ),
      );
    } finally {
      _readersOpen--;
      if (!mounted && _readersOpen == 0 && !_readerLiveDisposed) {
        _readerLiveDisposed = true;
        live.dispose();
      }
    }
  }

  /// Follows [interactive] after this build: the reader route listens to
  /// [_readerLive], and a listener must not be dirtied mid-build.
  void _syncReaders(bool interactive) {
    final live = _readerLive;
    if (live == null || live.value == interactive) return;
    scheduleMicrotask(() {
      if (!_readerLiveDisposed) live.value = interactive;
    });
  }

  @override
  void dispose() {
    final live = _readerLive;
    if (live != null && !_readerLiveDisposed) {
      if (_readersOpen > 0) {
        // The reader closes later and disposes it; retire it now.
        scheduleMicrotask(() {
          if (!_readerLiveDisposed) live.value = false;
        });
      } else {
        _readerLiveDisposed = true;
        live.dispose();
      }
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final interactive = MarkdownInteractionScope.enabledOf(context);
    _syncReaders(interactive);
    final preferences = ReaderPreferencesScope.maybeOf(context);
    final secondary = widget.baseStyle != null;
    return AgentChoiceScope(
      onChoice: widget.onChoice,
      child: KitMarkdown(
        widget.data,
        role: secondary ? KitTextRole.secondary : KitTextRole.body,
        tone: secondary ? KitTextTone.secondary : null,
        selectable: widget.selectable,
        interactive: interactive,
        blockBuilder: _agentBlock,
        highlighter: TranscriptHighlight.decorate,
        fileLinks: _fileLinks(MarkdownFileLinks.maybeOf(context)),
        codeWrap: preferences?.value.wrapCode,
        onCodeWrapChanged: preferences == null ? null : _saveWrap,
        onOpenCode: _openCode,
        codeLanguage: widget.codeBlockLanguage,
      ),
    );
  }
}

/// The full-screen code reader a capped block opens: the snapshot taken at
/// the tap, filling the page.
class _CodeReaderPage extends StatefulWidget {
  const _CodeReaderPage({
    required this.code,
    required this.language,
    required this.initialWrap,
    required this.onWrapChanged,
    required this.live,
  });

  final String code;
  final String? language;
  final bool? initialWrap;
  final ValueChanged<bool>? onWrapChanged;

  /// False once the source reply is inert or gone: no Copy, Wrap or
  /// selection on what is left of it.
  final ValueListenable<bool> live;

  @override
  State<_CodeReaderPage> createState() => _CodeReaderPageState();
}

class _CodeReaderPageState extends State<_CodeReaderPage> {
  late bool? _wrap = widget.initialWrap;

  @override
  Widget build(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final wrap = _wrap ?? KitCodeBlock.defaultWrap(context, KitCodeKind.code);
    return ValueListenableBuilder<bool>(
      valueListenable: widget.live,
      builder: (context, live, _) => KitSurface(
        level: KitSurfaceLevel.ground,
        shape: KitShape.square,
        padding: KitSurfacePadding.none,
        child: SafeArea(
          child: KitScreen(
            header: [
              KitTopBar(
                title: l10n.markdownReaderTitle,
                actions: [
                  if (live) ...[
                    KitAction(
                      label: wrap
                          ? l10n.markdownScrollCode
                          : l10n.markdownWrapCode,
                      icon: AppIconography.wrapText,
                      onPressed: () {
                        setState(() => _wrap = !wrap);
                        widget.onWrapChanged?.call(!wrap);
                      },
                    ),
                    KitAction.copy(
                      label: l10n.kitCodeCopyCode,
                      icon: AppIconography.copy,
                      text: () => widget.code,
                    ),
                  ],
                ],
              ),
            ],
            body: IgnorePointer(
              ignoring: !live,
              child: KitCodeBlock.fill(
                text: widget.code,
                language: widget.language,
                copyText: widget.code,
                wrap: wrap,
              ),
            ),
          ),
        ),
      ),
    );
  }
}
