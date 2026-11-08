part of '../chat_screen.dart';

// Opening a path link from a reply: a picture in the gallery, a phone file in
// Android, anything else as a preview through the agent host.

extension _ChatPathLink on _ChatScreenState {
  Future<void> _openPathLink(String raw) async {
    final strings = _chatL10n(context);
    if (_conn.isIsolated || !_conn.capabilities.fileBrowsing) return;
    final path = stripPathLineSuffix(raw);
    final name = path.substring(path.lastIndexOf('/') + 1);
    if (_drawablePictureName.hasMatch(name)) {
      await _openPictureGallery('path:$path', fallbackPath: path);
      return;
    }
    // A file in this phone's shared storage is an Android file: the system
    // opens it (or installs the .apk after its own confirm), not a preview
    // through the agent host.
    final profile = _conn.profile;
    final onPhone = profile != null && ProfileMonitor.isOnThisPhone(profile);
    final phoneAction = phoneFileAction(path, serverOnThisPhone: onPhone);
    if (phoneAction != PhoneFileAction.preview) {
      await offerPhoneFile(
        context,
        path: path,
        install: phoneAction == PhoneFileAction.install,
      );
      return;
    }
    try {
      final api = await _conn.prepareActionTransport();
      if (api == null) {
        throw ProductException(strings.chatUiNotConnectedToTheServerRightNow);
      }
      final FileContent content;
      try {
        content = await api.fileContent(path);
      } catch (error) {
        // Too big for the preview, and the file is on this phone: Android
        // can open it.
        if (onPhone &&
            isPhoneSharedStoragePath(path) &&
            ProductFailure.from(error).category ==
                ProductFailureCategory.fileTooBig) {
          if (!mounted) return;
          await offerPhoneFile(context, path: path, install: false);
          return;
        }
        rethrow;
      }
      final binary = content.isBinary || content.encoding == 'base64';
      final bytes = binary ? content.bytes() : null;
      final data = FilePreviewData(
        name: name,
        mimeType: content.mimeType,
        bytes: bytes,
        text: binary ? null : content.content,
      );
      if (!mounted) return;
      await showFilePreviewSheet(
        context,
        data,
        onAttach: () => _attachProjectFile(path, data),
      );
    } catch (error) {
      if (!mounted) return;
      showProductError(context, error);
    }
  }
}
