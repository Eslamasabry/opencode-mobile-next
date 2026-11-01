import 'dart:async';

import 'package:flutter/material.dart';

import '../../domain/file_share_gateway.dart';
import '../../l10n/app_localizations.dart';
import '../app_iconography.dart';
import '../kit/kit_buttons.dart';
import '../kit/kit_code_block.dart';
import '../kit/kit_notice.dart';
import '../kit/kit_qr.dart';
import '../kit/kit_sheet.dart';
import '../kit/kit_text.dart';
import '../kit/kit_tokens.dart';

/// "Share on Wi-Fi" for one opened file: starts the read-only download
/// server, shows the QR code and link, and stops the share when the sheet
/// closes (or on Stop, or after 15 minutes).
Future<void> showFileShareSheet(
  BuildContext context, {
  required FileShareGateway gateway,
  FileShareSource? source,
  FileShareFailure? refused,
}) async {
  assert(source != null || refused != null);
  final l10n = AppLocalizations.of(context);
  FileShareFailure? failure = refused;
  if (failure == null && source != null) {
    try {
      await gateway.start(source);
    } on FileShareException catch (error) {
      failure = error.failure;
    } on Object {
      failure = FileShareFailure.unavailable;
    }
  }
  if (!context.mounted) {
    await gateway.stop();
    return;
  }
  try {
    await showKitSheet<void>(
      context,
      title: l10n.fileShareTitle,
      icon: AppIconography.qrCode,
      sheetKey: const Key('file-share-sheet'),
      body: (context) => _FileShareBody(
        gateway: gateway,
        failure: failure,
        length: source?.length,
      ),
    );
  } finally {
    await gateway.stop();
  }
}

class _FileShareBody extends StatefulWidget {
  const _FileShareBody({
    required this.gateway,
    required this.failure,
    this.length,
  });

  final FileShareGateway gateway;
  final FileShareFailure? failure;
  final int? length;

  @override
  State<_FileShareBody> createState() => _FileShareBodyState();
}

class _FileShareBodyState extends State<_FileShareBody> {
  Timer? _tick;

  @override
  void initState() {
    super.initState();
    _tick = Timer.periodic(const Duration(seconds: 1), (_) {
      if (mounted && widget.gateway.value != null) setState(() {});
    });
  }

  @override
  void dispose() {
    _tick?.cancel();
    super.dispose();
  }

  static String _clock(Duration d) {
    final minutes = d.inMinutes;
    final seconds = (d.inSeconds % 60).toString().padLeft(2, '0');
    return '$minutes:$seconds';
  }

  static String _size(int bytes) {
    if (bytes < 1024) return '$bytes B';
    if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)} KB';
    if (bytes < 1024 * 1024 * 1024) {
      return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
    }
    return '${(bytes / (1024 * 1024 * 1024)).toStringAsFixed(2)} GB';
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final tokens = KitTokens.of(context);
    final gap = SizedBox(height: tokens.space3);
    final failure = widget.failure;
    if (failure != null) {
      return KitNotice(
        key: const Key('file-share-failure'),
        icon: failure == FileShareFailure.noWifi
            ? AppIconography.networkOff
            : AppIconography.info,
        message: switch (failure) {
          FileShareFailure.noWifi => l10n.fileShareNoWifi,
          FileShareFailure.privateFile => l10n.fileSharePrivate,
          FileShareFailure.unavailable => l10n.fileShareUnavailable,
        },
      );
    }
    return ListenableBuilder(
      listenable: widget.gateway,
      builder: (context, _) {
        final state = widget.gateway.value;
        if (state == null) {
          return KitNotice(
            key: const Key('file-share-ended'),
            icon: AppIconography.info,
            message: l10n.fileShareEnded,
          );
        }
        final left = state.remaining(DateTime.now());
        return Column(
          key: const Key('file-share-live'),
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            KitText(
              widget.length == null
                  ? state.name
                  : '${state.name} · ${_size(widget.length!)}',
              key: const Key('file-share-file'),
              role: KitTextRole.rowTitle,
            ),
            gap,
            Center(
              child: KitQr(
                data: state.url,
                semanticsLabel: l10n.fileShareQrLabel(state.name),
                qrKey: const Key('file-share-qr'),
              ),
            ),
            gap,
            KitText(
              l10n.fileShareWarning,
              key: const Key('file-share-warning'),
              role: KitTextRole.secondary,
              tone: KitTextTone.secondary,
            ),
            gap,
            KitCodeBlock(
              text: state.url,
              kind: KitCodeKind.output,
              caption: l10n.fileShareLinkLabel,
              wrap: true,
              showWrapToggle: false,
              highlight: false,
              copyLabel: l10n.fileShareCopyLink,
              blockKey: const Key('file-share-link'),
              copyKey: const Key('file-share-copy'),
            ),
            gap,
            KitText(
              l10n.fileShareDownloads(state.downloads),
              key: const Key('file-share-downloads'),
            ),
            KitText(
              l10n.fileShareTimeLeft(_clock(left)),
              key: const Key('file-share-time'),
              tabular: true,
              role: KitTextRole.secondary,
              tone: KitTextTone.secondary,
            ),
            gap,
            KitButton.secondary(
              key: const Key('file-share-stop'),
              label: l10n.fileShareStop,
              icon: AppIconography.close,
              onPressed: () {
                unawaited(widget.gateway.stop());
              },
            ),
          ],
        );
      },
    );
  }
}
