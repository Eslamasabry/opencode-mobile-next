import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';

import '../../api2/transport.dart';
import '../../domain/server_gateway.dart';
import '../../l10n/app_localizations.dart';
import '../../state/connection.dart';

typedef SaveSessionExport =
    Future<Uri?> Function(String name, Uint8List bytes, String mimeType);

Future<Uri?> _saveExport(String name, Uint8List bytes, String mimeType) =>
    FilePicker.saveFile(fileName: name, bytes: bytes, mimeType: mimeType);

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
  late final _location = widget.controller.locationRevision;
  late final _repository = widget.controller.repository;
  bool _json = true;
  bool _sanitize = true;
  bool _busy = false;
  bool _saving = false;
  bool _saved = false;
  double? _progress;
  String? _error;
  CancelToken? _cancel;

  bool get _current =>
      widget.controller.locationRevision == _location &&
      identical(widget.controller.repository, _repository);
  bool get _supported =>
      _repository is SessionExportGateway &&
      (_repository as SessionExportGateway).sessionExportSupported;

  AppLocalizations get _l10n =>
      lookupAppLocalizations(Localizations.localeOf(context));

  @override
  void initState() {
    super.initState();
    widget.controller.addListener(_changed);
  }

  void _changed() {
    if (!_current) _cancel?.cancel();
    if (mounted) setState(() {});
  }

  @override
  void dispose() {
    widget.controller.removeListener(_changed);
    _cancel?.cancel();
    super.dispose();
  }

  Future<void> _export() async {
    if (_busy || !_current || (_json && !_supported)) return;
    final l10n = _l10n;
    final token = CancelToken();
    _cancel = token;
    setState(() {
      _busy = true;
      _saved = false;
      _error = null;
      _progress = null;
    });
    try {
      final Uint8List bytes;
      if (_json) {
        final ready = await widget.controller.prepareActionRepository();
        if (!mounted || !_current || token.isCancelled) {
          return;
        }
        if (!identical(ready, _repository)) {
          throw StateError('Export connection is not ready');
        }
        bytes = await (_repository as SessionExportGateway).exportSession(
          widget.sessionID,
          sanitize: _sanitize,
          cancelToken: token,
          onReceiveProgress: (received, total) {
            if (mounted && !token.isCancelled) {
              setState(() => _progress = total > 0 ? received / total : null);
            }
          },
        );
      } else {
        bytes = widget.markdown();
      }
      if (!mounted || !_current || token.isCancelled) return;
      setState(() => _saving = true);
      final id = widget.sessionID.replaceAll(RegExp(r'[^a-zA-Z0-9_-]'), '_');
      final result = await widget.saveFile(
        'opencode-$id.${_json ? 'json' : 'md'}',
        bytes,
        _json ? 'application/json' : 'text/markdown',
      );
      if (mounted) setState(() => _saved = result != null);
    } catch (error) {
      if (!mounted || token.isCancelled) return;
      setState(() {
        _error = switch (error) {
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

  @override
  Widget build(BuildContext context) {
    final l10n = _l10n;
    return PopScope(
      canPop: !_saving,
      child: Scaffold(
        appBar: AppBar(title: Text(l10n.exportTitle)),
        bottomNavigationBar: SafeArea(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(20, 12, 20, 16),
            child: Align(
              heightFactor: 1,
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 560),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    if (!_current) Text(l10n.exportChanged),
                    if (_error != null) ...[
                      Semantics(
                        liveRegion: true,
                        child: Text(
                          _error!,
                          style: TextStyle(
                            color: Theme.of(context).colorScheme.error,
                          ),
                        ),
                      ),
                      const SizedBox(height: 12),
                    ],
                    if (_busy) ...[
                      LinearProgressIndicator(
                        value: _saving ? null : _progress,
                      ),
                      const SizedBox(height: 12),
                      Text(
                        _saving ? l10n.exportSaving : l10n.exportDownloading,
                      ),
                      if (!_saving)
                        TextButton(
                          onPressed: () => _cancel?.cancel(),
                          child: Text(l10n.exportCancel),
                        ),
                    ] else
                      FilledButton.icon(
                        onPressed: _current && (!_json || _supported)
                            ? _export
                            : null,
                        icon: const Icon(Icons.save_alt),
                        label: Text(l10n.exportSave),
                      ),
                    if (_saved) ...[
                      const SizedBox(height: 12),
                      Semantics(
                        liveRegion: true,
                        child: Text(l10n.exportSaved),
                      ),
                    ],
                  ],
                ),
              ),
            ),
          ),
        ),
        body: SafeArea(
          child: Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 600),
              child: ListView(
                padding: const EdgeInsets.all(20),
                children: [
                  Text(
                    l10n.exportDescription,
                    style: Theme.of(context).textTheme.bodyLarge,
                  ),
                  const SizedBox(height: 20),
                  if (_supported)
                    _format(
                      title: l10n.exportJson,
                      description: l10n.exportJsonDescription,
                      icon: Icons.data_object,
                      selected: _json,
                      onTap: () => setState(() {
                        _json = true;
                        _error = null;
                        _saved = false;
                      }),
                    ),
                  _format(
                    title: l10n.exportMarkdown,
                    description: l10n.exportMarkdownDescription,
                    icon: Icons.description_outlined,
                    selected: !_json,
                    onTap: () => setState(() {
                      _json = false;
                      _error = null;
                      _saved = false;
                    }),
                  ),
                  if (_json && _supported) ...[
                    const SizedBox(height: 16),
                    SwitchListTile.adaptive(
                      contentPadding: EdgeInsets.zero,
                      title: Text(l10n.exportRedact),
                      value: _sanitize,
                      onChanged: _busy || !_current
                          ? null
                          : (value) => setState(() {
                              _sanitize = value;
                              _saved = false;
                            }),
                    ),
                    Text(l10n.exportRedactDescription),
                    if (!_sanitize) ...[
                      const SizedBox(height: 12),
                      Text(
                        l10n.exportUnredacted,
                        style: TextStyle(
                          color: Theme.of(context).colorScheme.error,
                        ),
                      ),
                    ],
                  ],
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _format({
    required String title,
    required String description,
    required IconData icon,
    required bool selected,
    required VoidCallback onTap,
  }) => Card(
    color: selected ? Theme.of(context).colorScheme.secondaryContainer : null,
    child: Semantics(
      selected: selected,
      child: ListTile(
        enabled: !_busy && _current,
        leading: Icon(icon),
        title: Text(title),
        subtitle: Text(description),
        trailing: Icon(
          selected ? Icons.radio_button_checked : Icons.radio_button_off,
        ),
        onTap: onTap,
      ),
    ),
  );
}
