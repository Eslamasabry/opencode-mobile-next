import 'dart:async';
import 'dart:io';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:path_provider/path_provider.dart';

import '../l10n/app_localizations.dart';
import '../telemetry/flow_events.dart';
import '../platform/app_installer.dart';
import '../ui/app_iconography.dart';
import '../ui/kit/kit_buttons.dart';
import '../ui/kit/kit_progress.dart';
import '../ui/kit/kit_sheet.dart';
import '../ui/kit/kit_text.dart';
import '../ui/kit/kit_tokens.dart';
import 'in_app_update_service.dart';

enum _UpdateStatus { idle, downloading, ready, failed }

/// Presents the in-app update prompt sheet.
Future<void> showInAppUpdateSheet(
  BuildContext context, {
  required AppReleaseInfo release,
  InAppUpdateService? service,
}) async {
  final l10n = lookupAppLocalizations(Localizations.localeOf(context));
  final updateService = service ?? InAppUpdateService.instance;

  final primaryNotifier = ValueNotifier<KitAction?>(null);

  await showKitSheet<void>(
    context,
    title: l10n.appUpdatePromptTitle,
    subtitle: '${release.name} · ${release.formattedSize}',
    icon: AppIconography.download,
    height: KitSheetHeight.half,
    primaryListenable: primaryNotifier,
    secondary: KitAction(
      label: l10n.appUpdateLater,
      onPressed: () {
        HealthFlow.updateResult(kind: 'in_app', result: 'declined');
        updateService.dismissForSession(release.tag);
        Navigator.of(context).pop();
      },
    ),
    secondaryDismisses: true,
    body: (sheetContext) => _UpdateSheetBody(
      release: release,
      service: updateService,
      primaryNotifier: primaryNotifier,
      l10n: l10n,
    ),
  );
}

class _UpdateSheetBody extends StatefulWidget {
  const _UpdateSheetBody({
    required this.release,
    required this.service,
    required this.primaryNotifier,
    required this.l10n,
  });

  final AppReleaseInfo release;
  final InAppUpdateService service;
  final ValueNotifier<KitAction?> primaryNotifier;
  final AppLocalizations l10n;

  @override
  State<_UpdateSheetBody> createState() => _UpdateSheetBodyState();
}

class _UpdateSheetBodyState extends State<_UpdateSheetBody> {
  _UpdateStatus _status = _UpdateStatus.idle;
  double _fraction = 0.0;
  int _receivedBytes = 0;
  int _totalBytes = 0;
  String? _errorMessage;
  CancelToken? _cancelToken;
  String? _downloadedFilePath;

  @override
  void initState() {
    super.initState();
    _updatePrimaryAction();
  }

  @override
  void dispose() {
    _cancelToken?.cancel();
    super.dispose();
  }

  void _updatePrimaryAction() {
    switch (_status) {
      case _UpdateStatus.idle:
        widget.primaryNotifier.value = KitAction(
          label: widget.l10n.appUpdateDownloadAndUpdate,
          onPressed: _startDownload,
        );
      case _UpdateStatus.downloading:
        widget.primaryNotifier.value = KitAction(
          label: widget.l10n.appUpdateDownloading,
          onPressed: null,
        );
      case _UpdateStatus.ready:
        widget.primaryNotifier.value = KitAction(
          label: widget.l10n.appUpdateInstall,
          onPressed: _installDownloadedApk,
        );
      case _UpdateStatus.failed:
        widget.primaryNotifier.value = KitAction(
          label: widget.l10n.appUpdateRetry,
          onPressed: _startDownload,
        );
    }
  }

  Future<void> _startDownload() async {
    setState(() {
      _status = _UpdateStatus.downloading;
      _fraction = 0.0;
      _receivedBytes = 0;
      _totalBytes = widget.release.apkSize;
      _errorMessage = null;
    });
    _updatePrimaryAction();

    _cancelToken = CancelToken();

    try {
      final tempDir = await getTemporaryDirectory();
      final updateDir = Directory('${tempDir.path}/updates');
      if (!await updateDir.exists()) {
        await updateDir.create(recursive: true);
      }
      final targetFile = File('${updateDir.path}/${widget.release.apkName}');
      _downloadedFilePath = targetFile.path;

      await widget.service.downloadAndInstall(
        release: widget.release,
        cancelToken: _cancelToken,
        onProgress: (fraction, received, total) {
          if (!mounted) return;
          setState(() {
            _fraction = fraction;
            _receivedBytes = received;
            _totalBytes = total > 0 ? total : widget.release.apkSize;
          });
        },
      );

      if (!mounted) return;
      setState(() {
        _status = _UpdateStatus.ready;
      });
      _updatePrimaryAction();
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _status = _UpdateStatus.failed;
        _errorMessage = e.toString();
      });
      _updatePrimaryAction();
    }
  }

  Future<void> _installDownloadedApk() async {
    final path = _downloadedFilePath;
    if (path == null) return;
    await AppInstaller.installApk(path);
  }

  String _formatBytes(int bytes) {
    if (bytes <= 0) return '0.0 MB';
    final mb = bytes / (1024 * 1024);
    return '${mb.toStringAsFixed(1)} MB';
  }

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);

    return Padding(
      padding: EdgeInsets.symmetric(horizontal: tokens.space4),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(height: tokens.space2),
          KitText(
            widget.l10n.appUpdateSize(widget.release.formattedSize),
            role: KitTextRole.secondary,
          ),
          SizedBox(height: tokens.space4),
          if (_status == _UpdateStatus.downloading) ...[
            KitProgressView(
              progress: KitProgress.known(
                _fraction,
                caption:
                    '${(_fraction * 100).toInt()}% (${_formatBytes(_receivedBytes)} / ${_formatBytes(_totalBytes)})',
              ),
            ),
            SizedBox(height: tokens.space3),
            KitText(
              widget.l10n.appUpdateDownloading,
              role: KitTextRole.secondary,
            ),
          ] else if (_status == _UpdateStatus.ready) ...[
            KitText(
              widget.l10n.appUpdateReadyToInstall,
              role: KitTextRole.body,
            ),
          ] else if (_status == _UpdateStatus.failed) ...[
            KitText(
              widget.l10n.appUpdateDownloadFailed,
              role: KitTextRole.body,
            ),
            if (_errorMessage != null) ...[
              SizedBox(height: tokens.space2),
              KitText(_errorMessage!, role: KitTextRole.secondary),
            ],
          ] else ...[
            KitText(widget.l10n.appUpdateChangelog, role: KitTextRole.headline),
            SizedBox(height: tokens.space2),
            if (widget.release.changelog.isNotEmpty)
              KitText(widget.release.changelog, role: KitTextRole.body)
            else
              KitText(
                widget.l10n.desktopReleaseWhatChanged,
                role: KitTextRole.secondary,
              ),
          ],
          SizedBox(height: tokens.space4),
        ],
      ),
    );
  }
}
