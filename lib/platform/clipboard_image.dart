import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

/// A picture read from the system clipboard.
@immutable
class ClipboardImage {
  const ClipboardImage({required this.mime, required this.bytes});

  final String mime;
  final Uint8List bytes;
}

/// The clipboard holds a picture, but a bigger one than an attachment may be.
class ClipboardImageTooLarge implements Exception {
  const ClipboardImageTooLarge();
}

/// Pictures on the Android clipboard (`oc/clipboard`).
///
/// Flutter's own [Clipboard] reads `text/plain` only, so a screenshot copied
/// from Gallery cannot be pasted through it. [hasImage] only looks at the
/// clip's declared types (cheap, no system toast); [read] opens the picture
/// and is called only when the person taps "Paste image" (Android 12+ then
/// shows its own clipboard-access toast). Where there is no native side the
/// answers are "no picture".
class ClipboardImages {
  const ClipboardImages();

  static const _channel = MethodChannel('oc/clipboard');

  Future<bool> hasImage() async {
    try {
      return await _channel.invokeMethod<bool>('hasImage') ?? false;
    } on MissingPluginException {
      return false;
    } on PlatformException {
      return false;
    }
  }

  /// The clipboard's picture, or null when it holds none. Throws
  /// [ClipboardImageTooLarge] when it is bigger than the native cap.
  Future<ClipboardImage?> read() async {
    try {
      final map = await _channel.invokeMapMethod<String, Object?>(
        'clipboardImage',
      );
      final bytes = map?['bytes'];
      final mime = map?['mime'];
      if (bytes is! Uint8List || bytes.isEmpty || mime is! String) return null;
      return ClipboardImage(mime: mime, bytes: bytes);
    } on MissingPluginException {
      return null;
    } on PlatformException catch (error) {
      if (error.code == 'too_large') throw const ClipboardImageTooLarge();
      return null;
    }
  }
}

/// The one instance the composer uses; tests mock the channel.
const clipboardImages = ClipboardImages();
