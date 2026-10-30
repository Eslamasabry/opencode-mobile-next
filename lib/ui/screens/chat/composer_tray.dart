part of '../chat_screen.dart';

// The composer's "+" tray (plus-menu concept C, owner 20 Oct 2026): Photos,
// Camera, File and More as round buttons attached to the box; More opens
// the tools sheet ([_ChatComposer._openTools]).

extension _ChatComposerTray on _ChatComposer {
  /// What "+" opens in place (plus-menu concept C, owner 20 Oct 2026):
  /// Photos, Camera and File as round buttons attached to the box, and More,
  /// the sheet with everything else (the model and Auto-approve while the
  /// agent works, approvals, a project file, commands, saved prompts, the
  /// rarer tools). A server that takes no files has no tray: "+" opens the
  /// sheet, which says why there is nothing to attach.
  List<KitComposerTrayAction> _trayActions(BuildContext context) {
    if (isolated || conversationMode) return const [];
    final l10n = _chatL10n(context);
    final blocked = _attachBlocked
        ? l10n.chatUiAvailableWhenTheCurrentRunFinishes
        : null;
    final media = [
      if (_trayOffersPhotos) ...[
        KitComposerTrayAction(
          buttonKey: const Key('composer-tool-gallery'),
          icon: AppIconography.images,
          label: l10n.composerTrayPhotos,
          disabledReason: blocked,
          onTap: onPhotoLibrary,
        ),
        KitComposerTrayAction(
          buttonKey: const Key('composer-tool-camera'),
          icon: AppIconography.camera,
          label: l10n.composerTrayCamera,
          disabledReason: blocked,
          onTap: onCamera,
        ),
      ],
      if (_trayOffersFile)
        KitComposerTrayAction(
          buttonKey: const Key('composer-tool-attach'),
          icon: AppIconography.attach,
          label: l10n.composerTrayFile,
          disabledReason: blocked,
          onTap: onAttach,
        ),
    ];
    if (media.isEmpty) return const [];
    return [
      ...media,
      KitComposerTrayAction(
        buttonKey: const Key('composer-tray-more'),
        icon: AppIconography.more,
        label: l10n.composerTrayMore,
        onTap: () => unawaited(_openTools(context)),
      ),
    ];
  }

  bool get _trayOffersPhotos =>
      promptAttachmentsSupported && platformCapabilities.supportsPromptPhotos;

  bool get _trayOffersFile => promptAttachmentsSupported && !promptImagesOnly;
}
