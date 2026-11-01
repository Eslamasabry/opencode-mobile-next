import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart' show Clipboard, ClipboardData;

import '../../l10n/app_localizations.dart';
import '../../platform/local_file_open.dart';
import 'link_file_share.dart';
import '../app_iconography.dart';
import '../kit/kit.dart';

/// Asks whether to hand a file on this phone to Android, and does it: the
/// app chooser ("Open with…"), or for an .apk ([install]) the package
/// installer, whose own screen is the confirmation. Where this build cannot
/// hand files over, it says where the file is instead.
Future<void> offerPhoneFile(
  BuildContext context, {
  required String path,
  required bool install,
  LocalFileOpener? opener,
}) async {
  final l10n = AppLocalizations.of(context);
  final name = path.substring(path.lastIndexOf('/') + 1);
  final opener0 = opener ?? LocalFileOpener();
  if (!await opener0.isSupported()) {
    // This build cannot hand files to Android: no button that cannot work,
    // only where the file is and how to open it.
    if (!context.mounted) return;
    final folder = path.substring(0, path.lastIndexOf('/')).split('/').last;
    await showKitAlert(
      context,
      title: name,
      body: install
          ? l10n.phoneFileWhereInstall(name, folder)
          : l10n.phoneFileWhereOpen(name, folder),
      action: KitAction(
        key: const ValueKey('phone-file-copy-path'),
        label: l10n.readerUiCopyPath,
        onPressed: () => Clipboard.setData(ClipboardData(text: path)),
      ),
      alertKey: const ValueKey('phone-file-where'),
    );
    return;
  }
  if (!context.mounted) return;
  final yes = await showKitConfirm(
    context,
    title: install
        ? l10n.phoneFileInstallTitle(name)
        : l10n.phoneFileOpenTitle(name),
    body: install ? l10n.phoneFileInstallBody : l10n.phoneFileOpenBody,
    confirmLabel: install
        ? l10n.phoneFileInstallAction
        : l10n.phoneFileOpenAction,
    alternative: canShareLinkedFile()
        ? KitAction(
            key: const ValueKey('phone-file-share-wifi'),
            label: l10n.fileShareAction,
            icon: AppIconography.network,
            onPressed: () {
              if (context.mounted) {
                unawaited(shareLinkedFile(context, path: path, onPhone: true));
              }
            },
          )
        : null,
    sheetKey: const ValueKey('phone-file-sheet'),
    confirmKey: const ValueKey('phone-file-confirm'),
  );
  if (!yes || !context.mounted) return;
  final result = await opener0.open(path, install: install);
  if (result == LocalFileOpenResult.opened || !context.mounted) return;
  await showKitAlert(
    context,
    title: l10n.phoneFileFindTitle,
    body: l10n.phoneFileFindBody(path),
    alertKey: const ValueKey('phone-file-find'),
  );
}
