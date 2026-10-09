// Pictures in an agent's Markdown (docs/ux-system/kit-api/KitMarkdown.md
// "Pictures"). `![alt](target)` never reaches the screen as syntax: the
// scanner takes it out of the text and each picture is drawn as a kit part.
//
// - A file on the connected server or phone (a path, `file:` URI): a
//   thumbnail the app reads through the same file transport the path links
//   use, or a chip with the file's name when it cannot. Tapping opens the
//   existing file viewer through [KitMarkdownFileLinks.open].
// - A web address: never loaded (privacy). A chip with the host; a tap goes
//   through [openExternalLink] (SEC-1), which asks before it opens.
// - Anything else (`data:` URIs, other schemes, a path the server cannot
//   read): the same chip with the description, not openable.
//
// The scanner and the tile are one library with kit_markdown.dart, so the
// tile reads the same scope (interaction, file links) as the paragraph.
part of 'kit_markdown.dart';

/// What a Markdown picture points at.
enum KitMarkdownImageKind {
  /// A path on the connected server or phone, which the host may read.
  file,

  /// An http(s) address. Never fetched by the app.
  web,

  /// Nothing the app can open: `data:` URIs, other schemes, a bad target.
  inert,
}

/// One `![alt](target)` found in a line of Markdown.
@immutable
class KitMarkdownImage {
  const KitMarkdownImage._({
    required this.alt,
    required this.target,
    required this.kind,
    this.path,
    this.url,
  });

  /// Reads the picture at [start] of [source], which must begin `![`.
  /// Null when it is not a complete picture.
  static KitMarkdownImage? parse(String source) {
    final found = _imageAt(source, 0);
    return found != null && found.end == source.length ? found.image : null;
  }

  /// The words that describe it, trimmed; empty when there are none.
  final String alt;

  /// The destination as written (without any `<...>`).
  final String target;

  final KitMarkdownImageKind kind;

  /// [KitMarkdownImageKind.file]: the path to ask the host for, decoded.
  final String? path;

  /// [KitMarkdownImageKind.web]: the address a tap hands to
  /// [openExternalLink]. For a picture inside a link, the link's address.
  final String? url;

  /// Whether [alt] says something. Empty and the bare word "Image" are what
  /// agents write when they have nothing to say; those get the app's own
  /// word.
  bool get hasDescription =>
      alt.isNotEmpty &&
      alt.toLowerCase() != 'image' &&
      alt.toLowerCase() != 'img';

  /// The file's name, for a [KitMarkdownImageKind.file] picture.
  String? get fileName {
    final value = path;
    if (value == null) return null;
    final name = value.substring(value.lastIndexOf('/') + 1);
    return name.isEmpty ? null : name;
  }

  /// The address's host, for a [KitMarkdownImageKind.web] picture.
  String? get host {
    final value = url;
    final uri = value == null ? null : Uri.tryParse(value);
    return uri == null || uri.host.isEmpty ? null : externalLinkHost(uri);
  }

  /// Whether Flutter can draw the file as a thumbnail (not SVG, not HEIC).
  bool get drawable {
    final name = fileName?.toLowerCase() ?? '';
    return _drawableExtension.hasMatch(name);
  }

  /// Splits [source] (one line) into the text around its pictures and the
  /// pictures. Inline code is left alone. A line with no picture comes back
  /// as one text segment.
  static List<({String? text, KitMarkdownImage? image})> scan(String source) {
    if (!source.contains('![')) return [(text: source, image: null)];
    final parts = <({String? text, KitMarkdownImage? image})>[];
    var textStart = 0;
    var i = 0;
    while (i < source.length) {
      final unit = source.codeUnitAt(i);
      if (unit == 0x60) {
        // `code`: a picture inside it is code, not a picture.
        final close = source.indexOf('`', i + 1);
        i = close < 0 ? i + 1 : close + 1;
        continue;
      }
      ({KitMarkdownImage image, int end})? found;
      if (unit == 0x21 && source.startsWith('![', i)) {
        found = _imageAt(source, i);
      } else if (unit == 0x5b && source.startsWith('[![', i)) {
        found = _linkedImageAt(source, i);
      }
      if (found == null) {
        i++;
        continue;
      }
      if (i > textStart) {
        parts.add((text: source.substring(textStart, i), image: null));
      }
      parts.add((text: null, image: found.image));
      i = textStart = found.end;
    }
    if (textStart < source.length) {
      parts.add((text: source.substring(textStart), image: null));
    }
    return parts;
  }

  /// The pictures of a line that holds nothing else (spaces aside), or null.
  static List<KitMarkdownImage>? only(String line) {
    if (!line.contains('![')) return null;
    final images = <KitMarkdownImage>[];
    for (final part in scan(line)) {
      final image = part.image;
      if (image != null) {
        images.add(image);
      } else if (part.text!.trim().isNotEmpty) {
        return null;
      }
    }
    return images.isEmpty ? null : images;
  }

  /// [source] with every picture replaced by its alt text: what is spoken,
  /// measured or copied as prose. No target ever appears in it.
  static String withAltText(String source) {
    if (!source.contains('![')) return source;
    final out = StringBuffer();
    for (final part in scan(source)) {
      out.write(part.image?.alt ?? part.text);
    }
    return out.toString();
  }

  /// [line] without a picture that is still being written (`![Image](/tmp/
  /// sho`), so a streaming reply never flashes the syntax. A finished
  /// picture is left alone.
  static String withoutUnfinished(String line) {
    final at = line.lastIndexOf('![');
    if (at < 0) return line;
    final tail = _unfinished.firstMatch(line.substring(at));
    return tail == null ? line : line.substring(0, at);
  }

  static final _unfinished = RegExp(r'^!\[[^\]\n]*(?:\](?:\([^)\n]*)?)?$');
  static final _drawableExtension = RegExp(r'\.(png|jpe?g|gif|webp|bmp|wbmp)$');
  static final _imageExtension = RegExp(
    r'\.(png|jpe?g|gif|webp|bmp|wbmp|svg|heic|heif|avif|tiff?|ico)$',
    caseSensitive: false,
  );
  static final _scheme = RegExp(r'^([A-Za-z][A-Za-z0-9+.\-]+):');

  @override
  bool operator ==(Object other) =>
      other is KitMarkdownImage &&
      other.alt == alt &&
      other.target == target &&
      other.url == url;

  @override
  int get hashCode => Object.hash(alt, target, url);

  // -- Parsing ---------------------------------------------------------------

  /// A picture whose first `!` is at [start], and the index after it.
  static ({KitMarkdownImage image, int end})? _imageAt(String s, int start) {
    if (!s.startsWith('![', start)) return null;
    final altEnd = _closingBracket(s, start + 1);
    if (altEnd < 0 || altEnd + 1 >= s.length || s[altEnd + 1] != '(') {
      return null;
    }
    final destination = _destinationAt(s, altEnd + 1);
    if (destination == null) return null;
    return (
      image: _classify(
        alt: s.substring(start + 2, altEnd).trim(),
        target: destination.target,
      ),
      end: destination.end,
    );
  }

  /// `[![alt](src)](href)`: a picture that is also a link.
  static ({KitMarkdownImage image, int end})? _linkedImageAt(
    String s,
    int start,
  ) {
    final inner = _imageAt(s, start + 1);
    if (inner == null) return null;
    final close = inner.end;
    if (close + 1 >= s.length || s[close] != ']' || s[close + 1] != '(') {
      return null;
    }
    final link = _destinationAt(s, close + 1);
    if (link == null) return null;
    return (
      image: _classify(
        alt: inner.image.alt,
        target: inner.image.target,
        href: link.target,
      ),
      end: link.end,
    );
  }

  /// The `]` that closes the `[` at [open], counting nested pairs and
  /// skipping escaped ones; -1 when the line ends first.
  static int _closingBracket(String s, int open) {
    var depth = 0;
    for (var i = open; i < s.length; i++) {
      final c = s[i];
      if (c == '\n') return -1;
      if (c == r'\') {
        i++;
      } else if (c == '[') {
        depth++;
      } else if (c == ']') {
        depth--;
        if (depth == 0) return i;
      }
    }
    return -1;
  }

  /// The `(destination "title")` that opens at [open]: the destination and
  /// the index after its `)`.
  static ({String target, int end})? _destinationAt(String s, int open) {
    var i = open + 1;
    bool space(int at) => s[at] == ' ' || s[at] == '\t';
    while (i < s.length && space(i)) {
      i++;
    }
    String target;
    if (i < s.length && s[i] == '<') {
      final close = s.indexOf('>', i + 1);
      if (close < 0 || s.substring(i, close).contains('\n')) return null;
      target = s.substring(i + 1, close);
      i = close + 1;
    } else {
      final begin = i;
      var depth = 0;
      while (i < s.length && !space(i) && s[i] != '\n') {
        final c = s[i];
        if (c == r'\' && i + 1 < s.length) {
          i++;
        } else if (c == '(') {
          depth++;
        } else if (c == ')') {
          if (depth == 0) break;
          depth--;
        }
        i++;
      }
      target = s.substring(begin, i);
    }
    while (i < s.length && space(i)) {
      i++;
    }
    if (i < s.length && (s[i] == '"' || s[i] == "'")) {
      final close = s.indexOf(s[i], i + 1);
      if (close < 0) return null;
      i = close + 1;
      while (i < s.length && space(i)) {
        i++;
      }
    }
    if (i >= s.length || s[i] != ')') return null;
    return (target: target.trim(), end: i + 1);
  }

  static KitMarkdownImage _classify({
    required String alt,
    required String target,
    String? href,
  }) {
    final path = _pathOf(target);
    final web = _webAddress(href) ?? _webAddress(target);
    final kind = path != null
        ? KitMarkdownImageKind.file
        : web != null
        ? KitMarkdownImageKind.web
        : KitMarkdownImageKind.inert;
    return KitMarkdownImage._(
      alt: alt,
      target: target,
      kind: kind,
      path: path,
      url: kind == KitMarkdownImageKind.web ? web : null,
    );
  }

  /// [value] when it is an http(s) address with a host, else null.
  static String? _webAddress(String? value) {
    final text = value?.trim();
    if (text == null || text.isEmpty || text.length > 2048) return null;
    final uri = Uri.tryParse(text);
    if (uri == null || uri.host.isEmpty) return null;
    final scheme = uri.scheme.toLowerCase();
    return scheme == 'http' || scheme == 'https' ? text : null;
  }

  /// The server path [target] names, or null when it names anything else:
  /// a path (anchored, relative, or a bare file name) or a `file:` URI.
  static String? _pathOf(String target) {
    if (target.isEmpty || target.length > 1024) return null;
    String path;
    final scheme = _scheme.firstMatch(target)?.group(1)?.toLowerCase();
    if (scheme == null) {
      if (target.startsWith('//')) return null;
      path = target;
    } else if (scheme == 'file') {
      final uri = Uri.tryParse(target);
      if (uri == null ||
          (uri.host.isNotEmpty && uri.host != 'localhost') ||
          uri.path.isEmpty) {
        return null;
      }
      path = uri.path;
    } else {
      return null;
    }
    try {
      path = Uri.decodeFull(path);
    } on ArgumentError {
      return null;
    } on FormatException {
      return null;
    }
    if (path.contains(RegExp(r'[\x00-\x1f\\:?#]'))) return null;
    if (!_imageExtension.hasMatch(path) &&
        !KitMarkdown.looksLikeFilePath(path)) {
      return null;
    }
    return path.contains('/') ? path : './$path';
  }
}

// ---------------------------------------------------------------------------
// Where pictures sit in a reply.

/// Line [index] of [lines]. The last line may still be arriving, so a
/// picture half written on it is held back rather than shown as syntax.
String _lineOf(List<String> lines, int index) => index == lines.length - 1
    ? KitMarkdownImage.withoutUnfinished(lines[index])
    : lines[index];

/// The pictures that start at line [start]: that line when it holds nothing
/// else, and every picture-only line after it (blank lines between them
/// included). Null when line [start] is not picture-only. [end] is the index
/// of the first line after the group; [source] is its text, the cache key.
({List<KitMarkdownImage> images, String source, int end})? _pictureGroup(
  List<String> lines,
  int start,
) {
  final first = KitMarkdownImage.only(_lineOf(lines, start));
  if (first == null) return null;
  final images = [...first];
  final source = [_lineOf(lines, start).trim()];
  var end = start + 1;
  while (end < lines.length) {
    var next = end;
    while (next < lines.length && lines[next].trim().isEmpty) {
      next++;
    }
    final more = next < lines.length
        ? KitMarkdownImage.only(_lineOf(lines, next))
        : null;
    if (more == null) break;
    images.addAll(more);
    source.add(_lineOf(lines, next).trim());
    end = next + 1;
  }
  return (
    images: List.unmodifiable(images),
    source: source.join('\n'),
    end: end,
  );
}

/// [src] as spans when it holds a picture in the middle of its words: the
/// words through [spansOf], each picture a run of its own. Null when it holds
/// none.
List<InlineSpan>? _pictureSpans(
  String src,
  KitTextRole role,
  List<InlineSpan> Function(String text) spansOf,
) {
  if (!src.contains('![')) return null;
  final parts = KitMarkdownImage.scan(src);
  if (parts.every((part) => part.image == null)) return null;
  return [
    for (final part in parts)
      if (part.image case final image?)
        WidgetSpan(
          alignment: PlaceholderAlignment.middle,
          child: _KitMdImageTile(image: image, inline: true, role: role),
        )
      else
        ...spansOf(part.text!),
  ];
}

// ---------------------------------------------------------------------------
// The pictures of a reply, drawn.

/// Consecutive pictures of a reply as one wrapping group.
class _KitMdImages extends StatelessWidget {
  const _KitMdImages({required this.images});

  final List<KitMarkdownImage> images;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    return Wrap(
      spacing: tokens.space2,
      runSpacing: tokens.space1,
      children: [
        for (var i = 0; i < images.length; i++)
          _KitMdImageTile(key: ValueKey('kit-md-image-$i'), image: images[i]),
      ],
    );
  }
}

/// One picture. [inline] is a picture in the middle of a sentence: a small
/// link-like run in the sentence's [role] that wraps with the words, never a
/// thumbnail.
class _KitMdImageTile extends StatefulWidget {
  const _KitMdImageTile({
    super.key,
    required this.image,
    this.inline = false,
    this.role = KitTextRole.body,
  });

  final KitMarkdownImage image;
  final bool inline;
  final KitTextRole role;

  @override
  State<_KitMdImageTile> createState() => _KitMdImageTileState();
}

class _KitMdImageTileState extends State<_KitMdImageTile> {
  /// The file links the path was last checked against, and the answer.
  KitMarkdownFileLinks? _links;
  bool _readable = false;
  Timer? _retry;
  int _retriesLeft = 5;

  /// How long an unconfirmed path waits before asking again: the file
  /// provider's miss cadence (the same as a path link's).
  static const _retryAfter = Duration(seconds: 22);

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _sync();
  }

  @override
  void didUpdateWidget(_KitMdImageTile oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.image.path != widget.image.path) _forget();
    _sync();
  }

  @override
  void dispose() {
    _retry?.cancel();
    super.dispose();
  }

  void _forget() {
    _retry?.cancel();
    _readable = false;
    _retriesLeft = 5;
  }

  /// Checks a file picture against the host once per scope and again on a
  /// rebuild while it is unconfirmed: the host memoises positives and keeps
  /// misses briefly, so this is free until a miss expires.
  void _sync() {
    final links = _KitMdScope.of(context).fileLinks;
    if (links != _links) {
      _links = links;
      _forget();
    }
    if (links != null && widget.image.path != null && !_readable) {
      _check(links, widget.image.path!);
    }
  }

  void _check(KitMarkdownFileLinks links, String path) {
    links.validate(path).then((ok) {
      if (!mounted || links != _links || path != widget.image.path) return;
      if (ok) {
        _retry?.cancel();
        setState(() => _readable = true);
        return;
      }
      // An idle transcript never rebuilds: ask a few more times, then wait
      // for the next rebuild.
      if (_retriesLeft > 0 && (_retry == null || !_retry!.isActive)) {
        _retriesLeft--;
        _retry = Timer(_retryAfter, () {
          if (mounted && !_readable && _links != null) {
            _check(_links!, path);
          }
        });
      }
    });
  }

  /// What a tap does, or null when this picture cannot be opened.
  VoidCallback? _opener(_KitMdScope scope) {
    if (!scope.interactive) return null;
    final image = widget.image;
    switch (image.kind) {
      case KitMarkdownImageKind.file:
        final links = scope.fileLinks;
        final path = image.path!;
        return _readable && links != null ? () => links.open(path) : null;
      case KitMarkdownImageKind.web:
        final url = image.url!;
        return () => unawaited(openExternalLink(context, url));
      case KitMarkdownImageKind.inert:
        return null;
    }
  }

  @override
  Widget build(BuildContext context) {
    final scope = _KitMdScope.of(context);
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final image = widget.image;
    final open = _opener(scope);
    final detail = switch (image.kind) {
      KitMarkdownImageKind.file => image.fileName,
      KitMarkdownImageKind.web => image.host,
      KitMarkdownImageKind.inert => null,
    };
    final label = image.hasDescription
        ? KitBidi.auto(image.alt)
        : l10n.kitMarkdownImage;
    final words = detail == null ? label : '$label · ${KitBidi.ltr(detail)}';

    if (widget.inline) {
      return _KitMdInlineImage(words: words, role: widget.role, onOpen: open);
    }
    final links = scope.fileLinks;
    final read = links?.readImage;
    if (_readable && image.drawable && read != null && open != null) {
      final caption = image.hasDescription ? label : KitBidi.ltr(detail ?? '');
      return _KitMdThumbnail(
        provider: _KitMdFileImage(read: read, path: image.path!),
        caption: caption,
        semanticsLabel: l10n.kitAttachmentOpen(
          image.hasDescription ? image.alt : detail ?? l10n.kitMarkdownImage,
        ),
        onOpen: open,
      );
    }
    return open == null
        ? KitChip(label: words, icon: AppIconography.image)
        : KitChip.action(
            label: words,
            icon: AppIconography.image,
            onPressed: open,
          );
  }
}

/// A picture the host read: a square thumbnail that opens the viewer, with
/// its name under it.
class _KitMdThumbnail extends StatelessWidget {
  const _KitMdThumbnail({
    required this.provider,
    required this.caption,
    required this.semanticsLabel,
    required this.onOpen,
  });

  final ImageProvider provider;
  final String caption;
  final String semanticsLabel;
  final VoidCallback onOpen;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    return SizedBox(
      width: KitLayout.promptPhotoSize,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          KitTappable(
            onTap: onOpen,
            label: semanticsLabel,
            shape: KitShape.tile,
            child: ExcludeSemantics(
              child: KitImage(
                source: KitImageSource.provider(provider),
                semanticsLabel: null,
                fit: KitImageFit.cover,
                shape: KitShape.tile,
                width: KitLayout.promptPhotoSize,
                height: KitLayout.promptPhotoSize,
              ),
            ),
          ),
          SizedBox(height: tokens.space1),
          ExcludeSemantics(
            child: KitText(
              caption,
              role: KitTextRole.caption,
              tone: KitTextTone.secondary,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
          ),
        ],
      ),
    );
  }
}

/// A picture in the middle of a sentence: its words after a small picture
/// glyph, accent and underlined when a tap opens it (the same look as a
/// link), plain when it cannot be opened.
class _KitMdInlineImage extends StatelessWidget {
  const _KitMdInlineImage({
    required this.words,
    required this.role,
    required this.onOpen,
  });

  final String words;
  final KitTextRole role;
  final VoidCallback? onOpen;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final openable = onOpen != null;
    final color = openable ? roles.accent : roles.text1;
    final style = KitText.styleOf(context, role).copyWith(
      color: color,
      decoration: openable ? TextDecoration.underline : null,
      decorationColor: roles.accent,
    );
    // The enclosing WidgetSpan already scales the run, so its glyph and
    // words are not scaled again. An openable run paints through RichText
    // like the path chip's link: its semantics are the node below, and the
    // G5 contrast check samples accent text drawn this way.
    final label = openable
        ? RichText(
            text: TextSpan(text: words, style: style),
            textScaler: TextScaler.noScaling,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          )
        : Text(
            words,
            style: style,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            textScaler: TextScaler.noScaling,
          );
    final row = Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(AppIconography.image, size: tokens.smallIconSize, color: color),
        SizedBox(width: tokens.space1),
        Flexible(child: label),
      ],
    );
    if (!openable) return row;
    return Semantics(
      link: true,
      label: words,
      onTap: onOpen,
      excludeSemantics: true,
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: onOpen,
        child: MouseRegion(cursor: SystemMouseCursors.click, child: row),
      ),
    );
  }
}

/// A picture the server keeps as a file, read through the host's own file
/// transport when its thumbnail is first drawn. Two thumbnails of the same
/// path through the same host share one cached image.
@immutable
class _KitMdFileImage extends ImageProvider<_KitMdFileImage> {
  const _KitMdFileImage({required this.read, required this.path});

  final Future<Uint8List?> Function(String path) read;
  final String path;

  @override
  Future<_KitMdFileImage> obtainKey(ImageConfiguration configuration) =>
      SynchronousFuture<_KitMdFileImage>(this);

  @override
  ImageStreamCompleter loadImage(
    _KitMdFileImage key,
    ImageDecoderCallback decode,
  ) => MultiFrameImageStreamCompleter(codec: _codec(decode), scale: 1);

  Future<ui.Codec> _codec(ImageDecoderCallback decode) async {
    final bytes = await read(path);
    if (bytes == null || bytes.isEmpty) {
      throw StateError('The picture could not be read.');
    }
    return decode(await ui.ImmutableBuffer.fromUint8List(bytes));
  }

  @override
  bool operator ==(Object other) =>
      other is _KitMdFileImage && other.read == read && other.path == path;

  @override
  int get hashCode => Object.hash(read, path);
}
