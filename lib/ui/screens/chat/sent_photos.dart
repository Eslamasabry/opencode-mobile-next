part of '../chat_screen.dart';

// Sent photos as thumbnails under the prompt (FC4). A picture comes back in
// one of two forms: its bytes inline as a `data:` URI (OpenCode 1; OpenCode
// 2 for an upload, after it re-encodes the image; the app's own copy kept
// for an agent on this phone, whose server keeps the text only), or as a
// file the server keeps (`file:///abs/path`, OpenCode 2), read through the
// app's own file transport. A web address is never fetched: that picture
// stays a named chip.

/// Decoded bytes of a part's `data:` URI, kept per part so a rebuild never
/// decodes the same picture twice (and the thumbnail's image cache entry,
/// keyed by the bytes, stays the same).
final _sentPhotoBytes = Expando<Uint8List>('sentPhotoBytes');

/// A picture type Flutter can draw (not SVG).
bool _isDrawablePicture(String? mime) {
  final type = mime?.toLowerCase().trim() ?? '';
  return type.startsWith('image/') && !type.startsWith('image/svg');
}

/// The pixels of [part]'s `data:` URI, or null when it has none.
Uint8List? _sentPhotoDataBytes(Part part) {
  final url = part.url;
  if (url == null || !url.startsWith('data:')) return null;
  final cached = _sentPhotoBytes[part];
  if (cached != null) return cached;
  try {
    final data = UriData.parse(url);
    if (!_isDrawablePicture(part.mime ?? data.mimeType)) return null;
    final bytes = data.contentAsBytes();
    if (bytes.isEmpty) return null;
    _sentPhotoBytes[part] = bytes;
    return bytes;
  } on FormatException {
    return null;
  }
}

/// The server's own path for a picture it keeps as a file (`file:///…`).
String? _sentPhotoServerPath(Part part) {
  final uri = Uri.tryParse(part.url ?? '');
  if (uri == null || uri.scheme != 'file' || uri.path.isEmpty) return null;
  try {
    return Uri.decodeFull(uri.path);
  } on ArgumentError {
    return null;
  }
}

/// A picture the server keeps as a file, read through the app's own file
/// transport ([load]) when the thumbnail is first drawn. Two thumbnails of
/// the same path on the same server share one cached image.
@immutable
class _SentFileImage extends ImageProvider<_SentFileImage> {
  const _SentFileImage({
    required this.scope,
    required this.path,
    required this.load,
  });

  /// Which server the path belongs to (the profile id).
  final String scope;
  final String path;
  final Future<Uint8List?> Function() load;

  @override
  Future<_SentFileImage> obtainKey(ImageConfiguration configuration) =>
      SynchronousFuture<_SentFileImage>(this);

  @override
  ImageStreamCompleter loadImage(
    _SentFileImage key,
    ImageDecoderCallback decode,
  ) => MultiFrameImageStreamCompleter(codec: _codec(decode), scale: 1);

  Future<ui.Codec> _codec(ImageDecoderCallback decode) async {
    final bytes = await load();
    if (bytes == null || bytes.isEmpty) {
      throw StateError('The picture could not be read.');
    }
    return decode(await ui.ImmutableBuffer.fromUint8List(bytes));
  }

  @override
  bool operator ==(Object other) =>
      other is _SentFileImage && other.scope == scope && other.path == path;

  @override
  int get hashCode => Object.hash(scope, path);
}
