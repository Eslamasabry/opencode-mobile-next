import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';

import '../../api2/transport.dart';
import '../../domain/server_gateway.dart';
import '../../l10n/app_localizations.dart';
import '../../state/connection.dart';
import '../app_theme.dart';
import '../kit/kit_buttons.dart';
import '../kit/kit_choice_list.dart';
import '../kit/kit_notice.dart';
import '../kit/kit_progress.dart';
import '../kit/kit_row.dart';
import '../kit/kit_row_parts.dart';
import '../kit/kit_screen.dart';
import '../kit/kit_section_label.dart';
import '../kit/kit_text.dart';
import '../kit/kit_tokens.dart';
import '../kit/kit_top_bar.dart';

typedef SaveSessionExport =
    Future<Uri?> Function(String name, Uint8List bytes, String mimeType);

Future<Uri?> _saveExport(String name, Uint8List bytes, String mimeType) =>
    FilePicker.saveFile(fileName: name, bytes: bytes, mimeType: mimeType);

/// Export conversation (map page `session-export`): save this conversation
/// as a file, either the complete copy the server sends (JSON, redacted by
/// default) or the readable transcript loaded here (Markdown).
///
/// One format list, the redaction switch only for the complete copy (a
/// transcript is never redacted), and one bottom primary that names what it
/// saves. A server without the complete copy keeps that choice visible with
/// its reason (STATE-12) and starts on the transcript.
class SessionExportScreen extends StatefulWidget {
  const SessionExportScreen({
    super.key,
    required this.controller,
    required this.sessionID,
    required this.markdown,
    this.saveFile = _saveExport,
  });

  final ConnectionController controller;
  final String sessionID;
  final Uint8List Function() markdown;
  final SaveSessionExport saveFile;

  @override
  State<SessionExportScreen> createState() => _SessionExportScreenState();
}

class _SessionExportScreenState extends State<SessionExportScreen> {
  late final ConnectionController _controller;
  late final int _location;
  late final ServerOperationsGateway? _repository;
  late final String _sessionID;
  late final Uint8List Function() _markdown;
  late final SaveSessionExport _saveFile;
  late bool _json;
  bool _sanitize = true;
  bool _busy = false;
  bool _saving = false;
  bool _saved = false;
  double? _progress;
  String? _error;
  CancelToken? _cancel;

  bool get _current =>
      _controller.locationRevision == _location &&
      identical(_controller.repository, _repository);
  bool get _supported =>
      _repository is SessionExportGateway &&
      (_repository as SessionExportGateway).sessionExportSupported;

  AppLocalizations get _l10n =>
      lookupAppLocalizations(Localizations.localeOf(context));

  @override
  void initState() {
    super.initState();
    _controller = widget.controller;
    _location = _controller.locationRevision;
    _repository = _controller.repository;
    _sessionID = widget.sessionID;
    _markdown = widget.markdown;
    _saveFile = widget.saveFile;
    // A server without the complete copy starts on the transcript, so the
    // primary works at once instead of waiting for a second tap.
    _json = _supported;
    _controller.addListener(_changed);
  }

  void _changed() {
    if (!_current) _cancel?.cancel();
    if (mounted) setState(() {});
  }

  @override
  void dispose() {
    _controller.removeListener(_changed);
    _cancel?.cancel();
    super.dispose();
  }

  Future<void> _export() async {
    if (_busy || !_current || (_json && !_supported)) return;
    final l10n = _l10n;
    final token = CancelToken();
    _cancel = token;
    var writing = false;
    setState(() {
      _busy = true;
      _saved = false;
      _error = null;
      _progress = null;
    });
    try {
      final Uint8List bytes;
      if (_json) {
        final ready = await _controller.prepareActionRepository();
        if (!mounted || !_current || token.isCancelled) {
          return;
        }
        if (!identical(ready, _repository)) {
          throw StateError('Export connection is not ready');
        }
        bytes = await (_repository as SessionExportGateway).exportSession(
          _sessionID,
          sanitize: _sanitize,
          cancelToken: token,
          onReceiveProgress: (received, total) {
            if (mounted && _current && !token.isCancelled) {
              final value = total > 0
                  ? (received.clamp(0, total) / total).toDouble()
                  : null;
              setState(() => _progress = value);
            }
          },
        );
      } else {
        bytes = _markdown();
      }
      if (!mounted || !_current || token.isCancelled) return;
      setState(() => _saving = true);
      final id = _sessionID.replaceAll(RegExp(r'[^a-zA-Z0-9_-]'), '_');
      final saveBytes = Uint8List.fromList(bytes);
      writing = true;
      final result = await _saveFile(
        'opencode-$id.${_json ? 'json' : 'md'}',
        saveBytes,
        _json ? 'application/json' : 'text/markdown',
      );
      if (mounted && _current && !token.isCancelled) {
        setState(() => _saved = result != null);
      }
    } catch (error) {
      if (!mounted || token.isCancelled) return;
      setState(() {
        if (error is SessionExportUnsupported) _json = false;
        _error = writing
            ? l10n.sessionExportSaveFailed
            : switch (error) {
                SessionExportUnsupported() => l10n.exportUnsupported,
                Api2Error(statusCode: 401 || 403) => l10n.exportAuthorization,
                Api2Error(tag: 'SessionNotFoundError') => l10n.exportMissing,
                _ => l10n.exportFailed,
              };
      });
    } finally {
      if (mounted) {
        setState(() {
          _busy = false;
          _saving = false;
        });
      }
      if (identical(_cancel, token)) _cancel = null;
    }
  }

  void _choose(bool json) {
    if (_busy || !_current || json == _json) return;
    if (json && !_supported) return;
    setState(() {
      _json = json;
      _error = null;
      _saved = false;
    });
  }

  @override
  Widget build(BuildContext context) {
    final l10n = _l10n;
    final tokens = KitTokens.of(context);
    final canAct = _current && (!_json || _supported);
    final progress = _saving
        ? KitProgress.waiting(caption: l10n.exportSaving)
        : switch (_progress) {
            final value? => KitProgress.known(
              value,
              caption: l10n.exportDownloading,
            ),
            null => KitProgress.waiting(caption: l10n.exportDownloading),
          };
    return PopScope(
      canPop: !_saving,
      child: KitScreen(
        topBar: KitTopBar(title: l10n.exportTitle),
        width: KitScreenWidth.reading,
        bottom: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            if (_error case final error?) ...[
              KitNotice.error(message: error),
              SizedBox(height: tokens.space3),
            ],
            if (_saved) ...[
              KitNotice(tone: AppStatusTone.ok, message: l10n.exportSaved),
              SizedBox(height: tokens.space3),
            ],
            if (_busy) ...[
              KitProgressView(progress: progress),
              SizedBox(height: tokens.space3),
            ],
            KitActionBlock(
              primary: KitAction(
                label: _json
                    ? l10n.sessionExportSaveJson
                    : l10n.sessionExportSaveMarkdown,
                icon: AppIconography.download,
                working: _busy,
                onPressed: canAct ? _export : null,
                disabledReason: !_current
                    ? l10n.exportChanged
                    : canAct
                    ? null
                    : l10n.exportUnsupported,
              ),
              tertiary: [
                if (_busy && !_saving)
                  KitAction(
                    label: l10n.exportCancel,
                    onPressed: () => _cancel?.cancel(),
                  ),
              ],
            ),
          ],
        ),
        body: ListView(
          padding: KitScreen.padding(context),
          children: [
            SizedBox(height: tokens.space2),
            KitText(l10n.exportDescription, tone: KitTextTone.secondary),
            KitSectionLabel(
              l10n.sessionExportFormatLabel,
              margin: EdgeInsets.zero,
            ),
            KitChoiceList<bool>.single(
              semanticsLabel: l10n.sessionExportFormatLabel,
              actsOnTap: false,
              selected: _json,
              choices: [
                KitChoice(
                  value: true,
                  title: l10n.exportJson,
                  supporting: _supported ? l10n.exportJsonDescription : null,
                  leading: const KitRowIcon(AppIconography.dataObject),
                  enabled: _supported,
                  disabledReason: _supported
                      ? null
                      : l10n.sessionExportJsonUnavailable,
                ),
                KitChoice(
                  value: false,
                  title: l10n.exportMarkdown,
                  supporting: l10n.exportMarkdownDescription,
                  leading: const KitRowIcon(AppIconography.fileText),
                ),
              ],
              onSelected: _choose,
            ),
            if (!_json) ...[
              // The transcript is the loaded messages as they are: say so
              // before it is saved, since nothing in it is masked.
              SizedBox(height: tokens.sectionGap),
              KitNotice(
                key: const ValueKey('export-markdown-unredacted'),
                icon: AppIconography.warning,
                message: l10n.exportMarkdownUnredacted,
              ),
            ],
            if (_json && _supported) ...[
              SizedBox(height: tokens.sectionGap),
              KitRowGroup(
                label: l10n.sessionExportPrivacyLabel,
                margin: EdgeInsetsDirectional.zero,
                leadingIcons: false,
                children: [
                  KitSwitchRow(
                    title: l10n.exportRedact,
                    supporting: l10n.sessionExportRedactKeeps,
                    value: _sanitize,
                    disabledReason: _current
                        ? l10n.sessionExportRedactBusy
                        : l10n.sessionExportRedactChanged,
                    onChanged: _busy || !_current
                        ? null
                        : (value) => setState(() {
                            _sanitize = value;
                            _saved = false;
                          }),
                  ),
                ],
              ),
              if (!_sanitize) ...[
                SizedBox(height: tokens.space3),
                KitNotice(
                  icon: AppIconography.warning,
                  message: l10n.exportUnredacted,
                ),
              ],
            ],
          ],
        ),
      ),
    );
  }
}
