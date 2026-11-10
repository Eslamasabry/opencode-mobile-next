import 'dart:async';
import 'dart:convert';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/semantics.dart';
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';

import '../../domain/delimited_text.dart';
import '../../l10n/app_localizations.dart';
import '../app_iconography.dart';
import '../kit/chat/kit_markdown.dart';
import '../kit/kit_buttons.dart';
import '../kit/kit_code_block.dart';
import '../kit/kit_copy.dart';
import '../kit/kit_dialog.dart';
import '../kit/kit_icon_button.dart';
import '../kit/kit_menu.dart';
import '../kit/kit_notice.dart';
import '../kit/kit_segmented.dart';
import '../kit/kit_state_view.dart';
import '../kit/kit_tokens.dart';
import '../kit/kit_viewer.dart';
import 'delimited_file_preview.dart';
import 'markdown.dart' show MarkdownInteractionScope;
import 'pdf_file_preview.dart';
import 'product_states.dart' show productErrorText;
import 'reader_preferences.dart';
import 'svg_file_preview.dart';

/// Normalized file content that can be rendered by [FilePreviewBody].
class FilePreviewData {
  FilePreviewData({
    required this.name,
    String? mimeType,
    this.bytes,
    String? text,
    this.originalText,
    this.truncated = false,
    this.error,
  }) : mimeType = _normalizedMime(mimeType) ?? _mimeFromName(name),
       text =
           text ??
           _decodeText(
             bytes,
             _normalizedMime(mimeType) ?? _mimeFromName(name),
             name,
           );

  factory FilePreviewData.fromDataUrl({
    required String name,
    required String? mimeType,
    required String? url,
  }) {
    final normalizedMime = _normalizedMime(mimeType);
    if (url == null || url.trim().isEmpty) {
      return FilePreviewData(
        name: name,
        mimeType: normalizedMime,
        error: 'The attachment content is not included in this message.',
      );
    }
    if (!url.startsWith('data:')) {
      return FilePreviewData(
        name: name,
        mimeType: normalizedMime,
        error: 'Remote attachment previews are not available.',
      );
    }

    try {
      final data = UriData.parse(url);
      final resolvedMime = normalizedMime ?? _normalizedMime(data.mimeType);
      final bytes = Uint8List.fromList(data.contentAsBytes());
      return FilePreviewData(name: name, mimeType: resolvedMime, bytes: bytes);
    } on FormatException {
      return FilePreviewData(
        name: name,
        mimeType: normalizedMime,
        error: 'The attachment data could not be decoded.',
      );
    }
  }

  final String name;
  final String? mimeType;
  final Uint8List? bytes;
  final String? text;

  /// Full source when the caller supplies only a bounded display excerpt.
  final String? originalText;
  final bool truncated;
  final String? error;

  String? get copyText => originalText ?? text;
  String? get separator => DelimitedText.separator(name, mimeType);

  static String? _decodeText(Uint8List? bytes, String? mime, String name) {
    if (bytes == null ||
        (!_isTextMime(mime) && DelimitedText.separator(name, mime) == null)) {
      return null;
    }
    try {
      final value = utf8.decode(bytes);
      if (value.contains('\u0000')) return null;
      return value;
    } on FormatException {
      return null;
    }
  }

  bool get isRasterImage => switch (mimeType) {
    'image/png' ||
    'image/jpeg' ||
    'image/gif' ||
    'image/webp' ||
    'image/bmp' => true,
    _ => false,
  };

  /// A PDF this device may render page by page.
  bool get isPdf => mimeType == 'application/pdf' && bytes?.isNotEmpty == true;

  int? get byteLength => bytes?.length;

  Uint8List? get exportBytes {
    if (bytes != null) return bytes;
    if (copyText != null) return Uint8List.fromList(utf8.encode(copyText!));
    return null;
  }

  /// Markdown by type or name, or text that plainly is Markdown (headings,
  /// fences or a table).
  bool get isMarkdown {
    final lower = name.toLowerCase();
    if (mimeType == 'text/markdown' ||
        lower.endsWith('.md') ||
        lower.endsWith('.mdx')) {
      return true;
    }
    return RegExp(
      r'(^|\n)#{1,6}\s+|(^|\n)```|(^|\n)\|[^\n]+\|\s*\n\|?\s*:?-{3,}',
      multiLine: true,
    ).hasMatch(text ?? '');
  }

  /// The syntax colour for [name]'s extension, or null for plain text.
  String? get language {
    final lower = name.toLowerCase().split('?').first;
    final dot = lower.lastIndexOf('.');
    final extension = dot < 0 ? '' : lower.substring(dot + 1);
    return switch (extension) {
      'dart' => 'dart',
      'js' || 'mjs' || 'cjs' => 'javascript',
      'ts' => 'typescript',
      'tsx' => 'tsx',
      'jsx' => 'jsx',
      'py' => 'python',
      'go' => 'go',
      'rs' => 'rust',
      'java' => 'java',
      'kt' || 'kts' => 'kotlin',
      'swift' => 'swift',
      'c' || 'h' => 'c',
      'cc' || 'cpp' || 'cxx' || 'hpp' => 'cpp',
      'cs' => 'csharp',
      'sh' || 'bash' || 'zsh' => 'shell',
      'html' || 'htm' => 'html',
      'css' => 'css',
      'scss' => 'scss',
      'xml' || 'svg' => 'xml',
      'yaml' || 'yml' => 'yaml',
      'toml' => 'toml',
      'sql' => 'sql',
      'gradle' => 'gradle',
      'diff' || 'patch' => 'diff',
      'json' => 'json',
      _ => null,
    };
  }

  /// JSON laid out two spaces deep, or null when the text is not JSON.
  String? get prettyJson {
    final value = text ?? '';
    final lower = name.toLowerCase();
    final trimmed = value.trim();
    final candidate =
        mimeType == 'application/json' ||
        lower.endsWith('.json') ||
        ((trimmed.startsWith('{') && trimmed.endsWith('}')) ||
            (trimmed.startsWith('[') && trimmed.endsWith(']')));
    if (!candidate || trimmed.isEmpty) return null;
    try {
      return const JsonEncoder.withIndent('  ').convert(jsonDecode(trimmed));
    } on FormatException {
      return null;
    }
  }

  static String? _normalizedMime(String? value) {
    final normalized = value?.split(';').first.trim().toLowerCase();
    return normalized == null || normalized.isEmpty ? null : normalized;
  }

  static String? _mimeFromName(String name) {
    final dot = name.lastIndexOf('.');
    if (dot < 0 || dot == name.length - 1) return null;
    return switch (name.substring(dot + 1).toLowerCase()) {
      'png' => 'image/png',
      'jpg' || 'jpeg' => 'image/jpeg',
      'gif' => 'image/gif',
      'webp' => 'image/webp',
      'bmp' => 'image/bmp',
      'svg' => 'image/svg+xml',
      'pdf' => 'application/pdf',
      'json' => 'application/json',
      'csv' => 'text/csv',
      'tsv' => 'text/tab-separated-values',
      'xml' => 'application/xml',
      'md' ||
      'txt' ||
      'log' ||
      'dart' ||
      'js' ||
      'ts' ||
      'tsx' ||
      'jsx' ||
      'py' ||
      'go' ||
      'rs' ||
      'yaml' ||
      'yml' => 'text/plain',
      _ => null,
    };
  }

  static bool _isTextMime(String? mime) =>
      mime?.startsWith('text/') == true ||
      mime == 'application/json' ||
      mime == 'application/javascript' ||
      mime == 'application/xml' ||
      mime == 'image/svg+xml';
}

/// A preview that cannot show its file, in the person's words.
class FilePreviewUnavailable implements Exception {
  const FilePreviewUnavailable(this.message);

  final String message;

  @override
  String toString() => message;
}

/// The most source text a preview lays out; Copy and Save keep the whole
/// file.
const int filePreviewMaxSourceChars = 200000;

/// [text] cut to [filePreviewMaxSourceChars] without splitting a surrogate
/// pair; [cut] says whether anything was left out.
({String text, bool cut}) capPreviewSource(String text) {
  if (text.length <= filePreviewMaxSourceChars) return (text: text, cut: false);
  var end = filePreviewMaxSourceChars;
  final unit = text.codeUnitAt(end - 1);
  if (unit >= 0xD800 && unit <= 0xDBFF) end--;
  return (text: text.substring(0, end), cut: true);
}

/// The person's words for a [FilePreviewData.error].
String _errorText(AppLocalizations l10n, String error) => switch (error) {
  'The attachment content is not included in this message.' =>
    l10n.readerUiAttachmentMissing,
  'Remote attachment previews are not available.' =>
    l10n.readerUiRemoteAttachment,
  'The attachment data could not be decoded.' => l10n.readerUiAttachmentInvalid,
  final message => message,
};

/// What the viewer shows for one [FilePreviewData], worked out once.
class _Plan {
  _Plan(this.content, {this.notices = const [], this.renderedLabel})
    : source = KitViewerSource(content);

  final KitViewerContent content;
  final KitViewerSource source;

  /// Why the body is not what the file's type suggests.
  final List<String Function(AppLocalizations l10n)> notices;

  /// The rendered view's name beside Source ("Table", "Image",
  /// "Rendered"), when the content has both.
  final String Function(AppLocalizations l10n)? renderedLabel;
}

/// The plan for everything but an error and a PDF.
_Plan _planFor(FilePreviewData data, {int? initialLine}) {
  final bytes = data.bytes;
  if (data.isRasterImage && bytes != null && bytes.isNotEmpty) {
    return _Plan(KitViewerContent.image(bytes, semanticsLabel: data.name));
  }
  final text = data.text;
  if (text == null) {
    return _Plan(
      KitViewerContent.binary(
        mimeType: data.mimeType,
        byteLength: data.byteLength,
      ),
    );
  }
  if (initialLine != null) {
    final shown = capPreviewSource(text);
    final lineCount = '\n'.allMatches(shown.text).length + 1;
    final inside = initialLine >= 1 && initialLine <= lineCount;
    return _Plan(
      KitViewerContent.code(
        shown.text,
        language: data.language,
        truncated: data.truncated || shown.cut,
        initialLine: inside ? initialLine : null,
      ),
      notices: [
        if (!inside) (l10n) => l10n.fileLineOutsidePreview(initialLine),
      ],
    );
  }
  final separator = data.separator;
  if (separator != null) {
    final table = delimitedViewerContent(
      text,
      original: data.copyText ?? text,
      separator: separator,
      truncated: data.truncated,
    );
    final notice = table.notice;
    return _Plan(
      table.content,
      notices: [
        if (notice != null) (l10n) => delimitedNoticeText(l10n, notice),
      ],
      renderedLabel: table.content.kind == KitViewerKind.delimited
          ? (l10n) => l10n.fileTable
          : null,
    );
  }
  if (data.mimeType == 'image/svg+xml') {
    final svg = svgViewerContent(
      text,
      original: data.copyText,
      truncated: data.truncated,
    );
    return _Plan(
      svg.content,
      notices: [if (svg.unsupported) (l10n) => l10n.fileSvgUnsupported],
      renderedLabel: svg.content.kind == KitViewerKind.svg
          ? (l10n) => l10n.fileImage
          : null,
    );
  }
  if (data.isMarkdown) {
    return _Plan(
      KitViewerContent.markdown(text, truncated: data.truncated),
      renderedLabel: (l10n) => l10n.readerUiRendered,
    );
  }
  final pretty = data.truncated ? null : data.prettyJson;
  if (pretty != null) {
    return _Plan(KitViewerContent.code(pretty, language: 'json'));
  }
  final shown = capPreviewSource(text);
  final truncated = data.truncated || shown.cut;
  final language = data.language;
  return _Plan(
    language == null
        ? KitViewerContent.text(shown.text, truncated: truncated)
        : KitViewerContent.code(
            shown.text,
            language: language,
            truncated: truncated,
          ),
  );
}

/// Opens [data] in the kit viewer: the file's name (and [path] when known),
/// Attach to prompt as the one labelled action when [onAttach] is given,
/// and More with Find, Copy, Wrap, Show source, Save to device, Copy path,
/// Open in Files and Open in Review where they apply.
///
/// [onDownload] replaces the system save picker. [initialLine] opens the
/// source at that line. Returns when the viewer closes.
Future<void> showFilePreviewSheet(
  BuildContext context,
  FilePreviewData data, {
  Future<void> Function()? onAttach,
  Future<void> Function()? onDownload,
  String? path,
  int? initialLine,
  VoidCallback? onOpenInFiles,
  VoidCallback? onOpenInReview,
}) {
  final l10n = AppLocalizations.of(context);
  final error = data.error;
  LocalPdfPages? pages;
  final KitViewerSource source;
  String? shownCopy;
  if (error != null) {
    final message = _errorText(l10n, error);
    source = KitViewerSource.load(
      () => Future<KitViewerContent>.error(FilePreviewUnavailable(message)),
    );
  } else if (data.isPdf) {
    if (MarkdownInteractionScope.enabledOf(context)) {
      pages = LocalPdfPages(data.bytes!);
      source = pages.source(l10n);
    } else {
      source = KitViewerSource(
        KitViewerContent.binary(
          mimeType: data.mimeType,
          byteLength: data.byteLength,
        ),
      );
    }
  } else {
    final plan = _planFor(data, initialLine: initialLine);
    source = plan.source;
    shownCopy = plan.content.copyText;
  }
  return _openFileViewer(
    context,
    name: data.name,
    path: path,
    source: source,
    data: () => data,
    shownCopy: shownCopy,
    onAttach: onAttach,
    onDownload: onDownload,
    onOpenInFiles: onOpenInFiles,
    onOpenInReview: onOpenInReview,
  ).whenComplete(() => pages?.dispose());
}

/// Like [showFilePreviewSheet] for a file still to be fetched (a path on
/// the server, a tool's output): the viewer opens at once with its name,
/// shows loading, and a failed fetch says so with Try again, which runs
/// [load] again.
Future<void> showFilePreviewSheetLoading(
  BuildContext context, {
  required String name,
  required Future<FilePreviewData> Function() load,
  Future<void> Function(FilePreviewData data)? onAttach,
  String? path,
  int? initialLine,
  VoidCallback? onOpenInFiles,
  VoidCallback? onOpenInReview,
}) {
  final l10n = AppLocalizations.of(context);
  final interactive = MarkdownInteractionScope.enabledOf(context);
  FilePreviewData? loaded;
  LocalPdfPages? pages;
  Future<KitViewerContent> open() async {
    final data = await load();
    loaded = data;
    final error = data.error;
    if (error != null) throw FilePreviewUnavailable(_errorText(l10n, error));
    if (data.isPdf && interactive) {
      pages?.dispose();
      final opened = pages = LocalPdfPages(data.bytes!);
      return opened.open(l10n);
    }
    if (data.isPdf) {
      return KitViewerContent.binary(
        mimeType: data.mimeType,
        byteLength: data.byteLength,
      );
    }
    return _planFor(data, initialLine: initialLine).content;
  }

  final attach = onAttach;
  return _openFileViewer(
    context,
    name: name,
    path: path,
    source: KitViewerSource.load(open),
    data: () => loaded,
    onAttach: attach == null
        ? null
        : () async {
            final data = loaded;
            if (data != null) await attach(data);
          },
    onOpenInFiles: onOpenInFiles,
    onOpenInReview: onOpenInReview,
  ).whenComplete(() => pages?.dispose());
}

/// The one viewer frame both entry points share.
Future<void> _openFileViewer(
  BuildContext context, {
  required String name,
  required String? path,
  required KitViewerSource source,
  required FilePreviewData? Function() data,
  String? shownCopy,
  Future<void> Function()? onAttach,
  Future<void> Function()? onDownload,
  VoidCallback? onOpenInFiles,
  VoidCallback? onOpenInReview,
}) {
  final l10n = AppLocalizations.of(context);
  final navigator = Navigator.of(context);
  final store = ReaderPreferencesScope.maybeOf(context);
  var open = true;
  var attaching = false;
  var saving = false;

  BuildContext host() => navigator.context;

  void closeViewer() {
    if (open && navigator.mounted) navigator.pop();
  }

  Future<void> failed(String title, Object error) async {
    final context = host();
    if (!context.mounted) return;
    await showKitAlert(
      context,
      title: title,
      body: productErrorText(error, l10n: l10n),
      icon: AppIconography.error,
    );
  }

  Future<void> attach() async {
    final action = onAttach;
    if (action == null || attaching) return;
    attaching = true;
    try {
      await action();
      closeViewer();
    } catch (error) {
      await failed(l10n.filePreviewAttachFailed, error);
    } finally {
      attaching = false;
    }
  }

  Future<void> save() async {
    final file = data();
    final bytes = file?.exportBytes;
    if (file == null || bytes == null || saving) return;
    saving = true;
    try {
      if (onDownload != null) {
        await onDownload();
        return;
      }
      final savedPath = await FilePicker.saveFile(
        dialogTitle: l10n.readerUiSaveNamed(file.name),
        fileName: file.name,
        bytes: bytes,
      );
      final context = host();
      if (savedPath != null && context.mounted) {
        final view = View.maybeOf(context);
        if (view != null) {
          unawaited(
            SemanticsService.sendAnnouncement(
              view,
              l10n.readerUiSaved(file.name),
              Directionality.of(context),
            ),
          );
        }
      }
    } catch (error) {
      await failed(l10n.filePreviewSaveFailed, error);
    } finally {
      saving = false;
    }
  }

  final current = data();
  final whole = current?.copyText;
  final more = <KitMenuItem>[
    if (whole != null && shownCopy != null && whole != shownCopy)
      KitMenuItem(
        key: const ValueKey('file-preview-copy-original'),
        label: l10n.filePreviewCopyOriginal,
        icon: AppIconography.copy,
        // SEC-13: the person's own file is copied verbatim.
        onSelected: () {
          final context = host();
          if (context.mounted) {
            unawaited(KitCopy.copy(context, whole, redact: false));
          }
        },
      ),
    if (current == null || current.exportBytes != null)
      KitMenuItem(
        key: const ValueKey('file-preview-download'),
        label: l10n.readerUiSaveDevice,
        icon: AppIconography.download,
        onSelected: () => unawaited(save()),
      ),
    if (path != null)
      KitMenuItem.copy(
        key: const ValueKey('file-preview-copy-path'),
        label: l10n.readerUiCopyPath,
        text: () => path,
      ),
    if (onOpenInFiles != null)
      KitMenuItem(
        key: const ValueKey('file-preview-open-files'),
        label: l10n.filePreviewOpenInFiles,
        icon: AppIconography.folderOpen,
        onSelected: () {
          closeViewer();
          onOpenInFiles();
        },
      ),
    if (onOpenInReview != null)
      KitMenuItem(
        key: const ValueKey('file-preview-open-review'),
        label: l10n.readerUiOpenReview,
        icon: AppIconography.review,
        onSelected: () {
          closeViewer();
          onOpenInReview();
        },
      ),
  ];

  return showKitViewer(
    context,
    name: name,
    path: path,
    source: source,
    viewerKey: const ValueKey('file-preview-sheet'),
    interactive: MarkdownInteractionScope.enabledOf(context),
    primary: onAttach == null
        ? null
        : KitAction(
            key: const ValueKey('file-preview-attach'),
            label: l10n.readerUiAttachPrompt,
            icon: AppIconography.attach,
            onPressed: () => unawaited(attach()),
          ),
    more: more,
    wrap: store?.value.wrapCode,
    onWrapChanged: store == null
        ? null
        : (wrap) => unawaited(store.update(wrapCode: wrap)),
  ).whenComplete(() => open = false);
}

/// A file's body inside a host that has its own header (the Files viewer,
/// a skill's SKILL.md): full width, no frame of its own. Above it, when
/// they apply, the reasons the body is not what the type suggests, the
/// rendered/Source switch and Wrap lines; More (right-click) and Ctrl+F
/// come from the kit viewer.
class FilePreviewBody extends StatefulWidget {
  const FilePreviewBody({super.key, required this.data, this.initialLine});

  final FilePreviewData data;

  /// Opens the source at this line (1-based) and marks it.
  final int? initialLine;

  @override
  State<FilePreviewBody> createState() => _FilePreviewBodyState();
}

enum _View { rendered, source }

class _FilePreviewBodyState extends State<FilePreviewBody> {
  _Plan? _plan;
  bool _source = false;
  bool? _wrap;

  _Plan get _currentPlan =>
      _plan ??= _planFor(widget.data, initialLine: widget.initialLine);

  @override
  void didUpdateWidget(FilePreviewBody oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!identical(oldWidget.data, widget.data) ||
        oldWidget.initialLine != widget.initialLine) {
      _plan = null;
    }
  }

  bool _effectiveWrap(BuildContext context) =>
      _wrap ??
      ReaderPreferencesScope.maybeOf(context)?.value.wrapCode ??
      kitViewerDefaultWrap(context, _currentPlan.content.kind);

  void _setWrap(bool wrap) {
    setState(() => _wrap = wrap);
    if (ReaderPreferencesScope.maybeOf(context) != null) {
      unawaited(saveReaderPreferences(context, wrapCode: wrap));
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final tokens = KitTokens.of(context);
    final data = widget.data;
    final error = data.error;
    if (error != null) {
      return Center(
        child: KitStateView(
          icon: AppIconography.hidden,
          title: l10n.readerUiPreviewUnavailable,
          body: _errorText(l10n, error),
          size: KitStateSize.inline,
        ),
      );
    }
    if (data.isPdf) return PdfFilePreview(bytes: data.bytes!, name: data.name);
    final plan = _currentPlan;
    final kind = plan.content.kind;
    final rendered = plan.renderedLabel;
    final showsCode =
        kind == KitViewerKind.text ||
        kind == KitViewerKind.code ||
        (rendered != null && _source);
    final wrap = _effectiveWrap(context);
    final bar = <Widget>[
      if (rendered != null)
        Flexible(
          child: KitSegmented<_View>(
            semanticsLabel: l10n.filePreviewViewMode,
            selected: _source ? _View.source : _View.rendered,
            onChanged: (view) => setState(() => _source = view == _View.source),
            segments: [
              KitSegment(
                key: const ValueKey('file-preview-rendered-mode'),
                value: _View.rendered,
                label: rendered(l10n),
              ),
              KitSegment(
                key: const ValueKey('file-preview-raw-mode'),
                value: _View.source,
                label: l10n.fileSource,
              ),
            ],
          ),
        ),
      const Spacer(),
      if (showsCode)
        KitIconButton(
          key: const ValueKey('file-preview-wrap'),
          icon: AppIconography.wrapText,
          tooltip: l10n.kitWrapLines,
          selected: wrap,
          onPressed: () => _setWrap(!wrap),
        ),
    ];
    final hasBar = rendered != null || showsCode;
    final viewer = KitViewer(
      name: data.name,
      source: plan.source,
      showHeader: false,
      interactive: MarkdownInteractionScope.enabledOf(context),
      showSource: _source,
      onShowSourceChanged: (source) => setState(() => _source = source),
      wrap: wrap,
      onWrapChanged: _setWrap,
      viewerKey: ValueKey(switch (kind) {
        KitViewerKind.image => 'file-preview-image',
        KitViewerKind.delimited => 'file-preview-table',
        _ => 'file-preview-text',
      }),
    );
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        for (final notice in plan.notices)
          Padding(
            padding: EdgeInsetsDirectional.only(
              start: tokens.gutter,
              top: tokens.space2,
              end: tokens.gutter,
            ),
            child: KitNotice(message: notice(l10n), liveRegion: false),
          ),
        if (hasBar)
          Padding(
            padding: EdgeInsetsDirectional.fromSTEB(
              tokens.gutter,
              tokens.space2,
              tokens.space2,
              tokens.space1,
            ),
            child: Row(children: bar),
          ),
        Expanded(child: viewer),
      ],
    );
  }
}

/// A short textual result inside a card (a tool's output, a subagent's
/// answer): Markdown as prose with a Rendered/Source switch, JSON laid out,
/// code coloured, plain text as text. The code block's own Copy copies the
/// original text, and "Show all" opens the whole thing in the viewer.
class SmartTextPreview extends StatefulWidget {
  const SmartTextPreview({super.key, required this.data});

  final FilePreviewData data;

  @override
  State<SmartTextPreview> createState() => _SmartTextPreviewState();
}

class _SmartTextPreviewState extends State<SmartTextPreview> {
  bool _source = false;

  void _openFull() => unawaited(showFilePreviewSheet(context, widget.data));

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final tokens = KitTokens.of(context);
    final data = widget.data;
    final text = data.text ?? '';
    final original = data.copyText;
    if (data.isMarkdown) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          KitSegmented<_View>(
            semanticsLabel: l10n.filePreviewViewMode,
            selected: _source ? _View.source : _View.rendered,
            onChanged: (view) => setState(() => _source = view == _View.source),
            segments: [
              KitSegment(
                key: const ValueKey('file-preview-rendered-mode'),
                value: _View.rendered,
                label: l10n.readerUiRendered,
              ),
              KitSegment(
                key: const ValueKey('file-preview-raw-mode'),
                value: _View.source,
                label: l10n.fileSource,
              ),
            ],
          ),
          SizedBox(height: tokens.space2),
          if (_source)
            KitCodeBlock(
              text: text,
              language: 'markdown',
              copyText: original,
              onOpenFull: _openFull,
            )
          else
            KitMarkdown(
              text,
              interactive: MarkdownInteractionScope.enabledOf(context),
            ),
        ],
      );
    }
    final pretty = data.prettyJson;
    final language = pretty != null ? 'json' : data.language;
    return KitCodeBlock(
      text: pretty ?? text,
      kind: language == null ? KitCodeKind.output : KitCodeKind.code,
      language: language,
      copyText: original,
      onOpenFull: _openFull,
    );
  }
}

AppLocalizations readerL10n(BuildContext context) =>
    lookupAppLocalizations(Localizations.localeOf(context));
