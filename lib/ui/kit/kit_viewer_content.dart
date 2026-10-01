part of 'kit_viewer.dart';

/// Which renderer a [KitViewerContent] uses.
enum KitViewerKind { text, code, markdown, image, pdf, svg, delimited, binary }

/// The wrap a viewer uses before the person picks one: source code scrolls
/// sideways on every window, so a long line is never broken mid-identifier
/// ([KitCodeBlock.defaultWrap]); text, markdown source and the rest read
/// like output, wrapped on a compact window.
bool kitViewerDefaultWrap(BuildContext context, KitViewerKind? kind) =>
    KitCodeBlock.defaultWrap(
      context,
      kind == KitViewerKind.code ? KitCodeKind.code : KitCodeKind.output,
    );

/// One page of a PDF, already rendered by the caller's renderer
/// (lib/platform/local_pdf.dart); the kit only lays pages out.
@immutable
class KitPdfPage {
  const KitPdfPage({
    required this.bytes,
    required this.width,
    required this.height,
  });

  /// An encoded bitmap (PNG) at the requested pixel width.
  final Uint8List bytes;

  /// In pixels.
  final int width, height;
}

/// What the viewer shows. Every constructor is data only: parsing,
/// sanitising and file reading stay with the caller.
@immutable
class KitViewerContent {
  /// Plain text: no line numbers, no syntax colour.
  const KitViewerContent.text(
    String text, {
    bool truncated = false,
    int? totalLines,
  }) : kind = KitViewerKind.text,
       _text = text,
       _language = null,
       _truncated = truncated,
       _totalLines = totalLines,
       _initialLine = null,
       _bytes = null,
       _semanticsLabel = null,
       _pageCount = 0,
       _renderPage = null,
       _cancelPage = null,
       _original = null,
       _rows = const [],
       _header = false,
       _mimeType = null,
       _byteLength = null;

  /// Source code: line numbers, syntax colour from [language]. [initialLine]
  /// (1-based) is scrolled to and marked.
  const KitViewerContent.code(
    String text, {
    String? language,
    bool truncated = false,
    int? totalLines,
    int? initialLine,
  }) : kind = KitViewerKind.code,
       _text = text,
       _language = language,
       _truncated = truncated,
       _totalLines = totalLines,
       _initialLine = initialLine,
       _bytes = null,
       _semanticsLabel = null,
       _pageCount = 0,
       _renderPage = null,
       _cancelPage = null,
       _original = null,
       _rows = const [],
       _header = false,
       _mimeType = null,
       _byteLength = null;

  /// A Markdown document, reflowed as prose (Show source swaps to its text).
  const KitViewerContent.markdown(String source, {bool truncated = false})
    : kind = KitViewerKind.markdown,
      _text = source,
      _language = null,
      _truncated = truncated,
      _totalLines = null,
      _initialLine = null,
      _bytes = null,
      _semanticsLabel = null,
      _pageCount = 0,
      _renderPage = null,
      _cancelPage = null,
      _original = null,
      _rows = const [],
      _header = false,
      _mimeType = null,
      _byteLength = null;

  /// An encoded image; [semanticsLabel] defaults to the viewer's name.
  const KitViewerContent.image(Uint8List bytes, {String? semanticsLabel})
    : kind = KitViewerKind.image,
      _text = null,
      _language = null,
      _truncated = false,
      _totalLines = null,
      _initialLine = null,
      _bytes = bytes,
      _semanticsLabel = semanticsLabel,
      _pageCount = 0,
      _renderPage = null,
      _cancelPage = null,
      _original = null,
      _rows = const [],
      _header = false,
      _mimeType = null,
      _byteLength = null;

  /// A PDF of [pageCount] pages. [renderPage] is called only for visible
  /// pages; [cancelPage] runs for a page scrolled away before it rendered.
  const KitViewerContent.pdf({
    required int pageCount,
    required Future<KitPdfPage> Function(int index, int widthPx) renderPage,
    void Function(int index)? cancelPage,
  }) : kind = KitViewerKind.pdf,
       _text = null,
       _language = null,
       _truncated = false,
       _totalLines = null,
       _initialLine = null,
       _bytes = null,
       _semanticsLabel = null,
       _pageCount = pageCount,
       _renderPage = renderPage,
       _cancelPage = cancelPage,
       _original = null,
       _rows = const [],
       _header = false,
       _mimeType = null,
       _byteLength = null;

  /// An SVG already sanitised by the caller (`StaticSvg`); [original] is the
  /// file's own text for Show source and Copy.
  const KitViewerContent.svg(
    String safeSource, {
    String? original,
    bool truncated = false,
  }) : kind = KitViewerKind.svg,
       _text = safeSource,
       _language = null,
       _truncated = truncated,
       _totalLines = null,
       _initialLine = null,
       _bytes = null,
       _semanticsLabel = null,
       _pageCount = 0,
       _renderPage = null,
       _cancelPage = null,
       _original = original,
       _rows = const [],
       _header = false,
       _mimeType = null,
       _byteLength = null;

  /// A parsed CSV or TSV: [rows] of cells, the first a header row when
  /// [header]. [original] is the raw text for Show source and Copy.
  const KitViewerContent.delimited(
    List<List<String>> rows, {
    String? original,
    bool header = true,
    bool truncated = false,
  }) : kind = KitViewerKind.delimited,
       _text = null,
       _language = null,
       _truncated = truncated,
       _totalLines = null,
       _initialLine = null,
       _bytes = null,
       _semanticsLabel = null,
       _pageCount = 0,
       _renderPage = null,
       _cancelPage = null,
       _original = original,
       _rows = rows,
       _header = header,
       _mimeType = null,
       _byteLength = null;

  /// A file the viewer cannot show: its type and size, and the caller's
  /// action.
  const KitViewerContent.binary({String? mimeType, int? byteLength})
    : kind = KitViewerKind.binary,
      _text = null,
      _language = null,
      _truncated = false,
      _totalLines = null,
      _initialLine = null,
      _bytes = null,
      _semanticsLabel = null,
      _pageCount = 0,
      _renderPage = null,
      _cancelPage = null,
      _original = null,
      _rows = const [],
      _header = false,
      _mimeType = mimeType,
      _byteLength = byteLength;

  final KitViewerKind kind;
  final String? _text, _language, _semanticsLabel, _original, _mimeType;
  final bool _truncated, _header;
  final int? _totalLines, _initialLine, _byteLength;
  final Uint8List? _bytes;
  final int _pageCount;
  final Future<KitPdfPage> Function(int index, int widthPx)? _renderPage;
  final void Function(int index)? _cancelPage;
  final List<List<String>> _rows;

  /// The whole text for Copy and Find (text, code, markdown source, svg and
  /// delimited originals); null for image, pdf and binary.
  String? get copyText => switch (kind) {
    KitViewerKind.text || KitViewerKind.code || KitViewerKind.markdown => _text,
    KitViewerKind.svg => _original ?? _text,
    KitViewerKind.delimited => _original,
    KitViewerKind.image || KitViewerKind.pdf || KitViewerKind.binary => null,
  };
}

/// Where the content comes from.
@immutable
class KitViewerSource {
  /// Content already in hand.
  const KitViewerSource(KitViewerContent content)
    : _content = content,
      _load = null;

  /// Content loaded on open; the viewer shows loading and a failure with
  /// Try again (which runs [load] again).
  const KitViewerSource.load(Future<KitViewerContent> Function() load)
    : _content = null,
      _load = load;

  final KitViewerContent? _content;
  final Future<KitViewerContent> Function()? _load;

  @override
  bool operator ==(Object other) =>
      other is KitViewerSource &&
      identical(other._content, _content) &&
      other._load == _load;

  @override
  int get hashCode => Object.hash(identityHashCode(_content), _load);
}
