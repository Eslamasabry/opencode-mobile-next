import 'package:flutter/services.dart' show PlatformException;
import 'package:image_picker/image_picker.dart' show ImageSource;

import '../../domain/genui/gen_ui.dart' show PromptAttachment;
import '../../l10n/app_localizations.dart';
import '../../state/prompt_photos.dart';

/// Where a card's photo ask gets its pictures: the camera and the gallery,
/// through the same limits as a prompt's photos (10 MB each, 20 MB together,
/// the picker's own downscaling). The screen that shows the card builds one
/// for its own conversation; a card without one says photos cannot be added
/// from there.
abstract interface class AgentCardPhotos {
  /// Whether this phone has a camera to open.
  bool get canTake;

  /// Up to [limit] photos from the gallery; empty when the person cancels.
  Future<List<PromptAttachment>> choose(int limit);

  /// One photo from the camera; null when the person cancels.
  Future<PromptAttachment?> take();
}

/// The most one photo may weigh, and all photos of one answer together.
const int agentCardPhotoMaxBytes = 10 * 1024 * 1024;
const int agentCardPhotoMaxTotalBytes = 20 * 1024 * 1024;

/// The decoded size of a `data:` URL attachment.
int agentCardAttachmentBytes(PromptAttachment attachment) {
  final comma = attachment.url.indexOf(',');
  if (comma < 0) return 0;
  final payload = attachment.url.substring(comma + 1);
  final padding = payload.endsWith('==')
      ? 2
      : payload.endsWith('=')
      ? 1
      : 0;
  return (payload.length * 3 ~/ 4) - padding;
}

/// The plain words for why a photo could not be added; never the raw error.
String agentCardPhotoError(AppLocalizations l10n, Object error) {
  if (error is PromptPhotoException) {
    return switch (error.failure) {
      PromptPhotoFailure.tooLarge => l10n.photoTooLarge,
      PromptPhotoFailure.unsupported => l10n.chatAttachmentUnsupported,
      PromptPhotoFailure.storage => l10n.photoStorageFailed,
      PromptPhotoFailure.pending => l10n.photoPendingOther,
      PromptPhotoFailure.unavailable => l10n.photoUnavailable,
    };
  }
  if (error is PlatformException &&
      error.code.toLowerCase().contains('denied')) {
    return l10n.photoPermissionDenied;
  }
  return l10n.photoUnavailable;
}

/// [AgentCardPhotos] over the app's prompt-photo store, scoped to one
/// conversation so a photo that outlives the app still finds its way back
/// to that conversation's draft.
class StoreAgentCardPhotos implements AgentCardPhotos {
  StoreAgentCardPhotos({
    required this.store,
    required this.profileID,
    required this.sessionID,
    required this.directory,
    required this.workspace,
    this.canTake = true,
  });

  final PromptPhotoStore store;
  final String profileID;
  final String sessionID;
  final String? directory;
  final String? workspace;

  @override
  final bool canTake;

  @override
  Future<List<PromptAttachment>> choose(int limit) => store.pickMany(
    profileID: profileID,
    sessionID: sessionID,
    directory: directory,
    workspace: workspace,
    limit: limit,
  );

  @override
  Future<PromptAttachment?> take() async {
    final pending = await store.pick(
      profileID: profileID,
      sessionID: sessionID,
      directory: directory,
      workspace: workspace,
      source: ImageSource.camera,
    );
    if (pending == null) return null;
    final attachment = await store.readPending(pending.id);
    await store.discard(pending.id);
    return attachment;
  }
}
