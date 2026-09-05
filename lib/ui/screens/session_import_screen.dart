import 'dart:async';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';

import '../../api/models.dart' show Session;
import '../../api2/transport.dart';
import '../../domain/server_gateway.dart';
import '../../l10n/app_localizations.dart';
import '../../state/connection.dart';

class SessionImportFile {
  final String name;
  final Future<int> Function() length;
  final Stream<List<int>> Function() read;
  const SessionImportFile({
    required this.name,
    required this.length,
    required this.read,
  });
}

Future<SessionImportFile?> _pickImportFile() async {
  final file = await FilePicker.pickFile(
    type: FileType.custom,
    allowedExtensions: ['json'],
  );
  return file == null
      ? null
      : SessionImportFile(
          name: file.name,
          length: file.length,
          read: file.readAsByteStream,
        );
}

class SessionImportScreen extends StatefulWidget {
  const SessionImportScreen({
    super.key,
    required this.controller,
    this.pickFile = _pickImportFile,
  });
  final ConnectionController controller;
  final Future<SessionImportFile?> Function() pickFile;
  @override
  State<SessionImportScreen> createState() => _SessionImportScreenState();
}

class _SessionImportScreenState extends State<SessionImportScreen> {
  late final ServerOperationsGateway? _repository;
  late final int _location;
  late final String _serverName;
  SessionImportDestination? _destination;
  SessionImportDocument? _document;
  String? _fileName;
  String? _error;
  Session? _imported;
  bool _reading = false;
  bool _importing = false;
  bool _choosing = false;
  bool _opening = false;

  bool get _busy => _reading || _importing || _choosing || _opening;
  bool get _current =>
      widget.controller.locationRevision == _location &&
      identical(widget.controller.repository, _repository);
  bool get _supported =>
      _repository is SessionImportGateway &&
      (_repository as SessionImportGateway).sessionImportSupported;
  AppLocalizations get _l10n =>
      lookupAppLocalizations(Localizations.localeOf(context));

  @override
  void initState() {
    super.initState();
    _repository = widget.controller.repository;
    _location = widget.controller.locationRevision;
    _serverName = widget.controller.profile?.name ?? '';
    final directory = widget.controller.directory;
    if (directory?.isNotEmpty == true) {
      _destination = SessionImportDestination(
        directory: directory!,
        workspaceID: widget.controller.workspace,
      );
    }
    widget.controller.addListener(_changed);
    if (_destination == null) unawaited(_resolveDefault());
  }

  void _changed() {
    if (mounted) setState(() {});
  }

  @override
  void dispose() {
    widget.controller.removeListener(_changed);
    super.dispose();
  }

  Future<void> _resolveDefault() async {
    _choosing = true;
    try {
      final project = await _repository?.loadCurrentProject();
      if (mounted && _current && project?.directory.isNotEmpty == true) {
        setState(
          () => _destination = SessionImportDestination(
            directory: project!.directory,
          ),
        );
      }
    } catch (_) {
      // The explicit destination chooser remains available after a failed probe.
    } finally {
      if (mounted) setState(() => _choosing = false);
    }
  }

  Future<void> _pick() async {
    if (_busy || !_current) return;
    setState(() {
      _reading = true;
      _error = null;
    });
    try {
      final file = await widget.pickFile();
      if (!mounted || file == null) return;
      setState(() {
        _fileName = file.name;
        _document = null;
        _imported = null;
      });
      if (await file.length() > SessionImportDocument.maxBytes) {
        throw const SessionImportTooLarge();
      }
      final document = await SessionImportDocument.read(file.read());
      if (mounted) setState(() => _document = document);
    } catch (error) {
      if (mounted) {
        setState(
          () => _error = error is SessionImportTooLarge
              ? _l10n.importTooLarge
              : _l10n.importInvalidFile,
        );
      }
    } finally {
      if (mounted) setState(() => _reading = false);
    }
  }

  Future<void> _chooseDestination() async {
    if (_busy || !_current || _repository == null) return;
    setState(() {
      _choosing = true;
      _error = null;
    });
    try {
      final projects = await _repository.listProjects();
      if (!mounted || !_current) return;
      final workspaces = widget.controller.capabilities.managedWorkspaces
          ? await _repository.listWorkspaces()
          : <WorkspaceInfo>[];
      if (!mounted || !_current) return;
      final choices = <({String label, SessionImportDestination destination})>[
        for (final project in projects) ...[
          if (project.directory.isNotEmpty)
            (
              label: project.name,
              destination: SessionImportDestination(
                directory: project.directory,
              ),
            ),
          for (final path in project.worktrees)
            if (path.isNotEmpty && path != project.directory)
              (
                label: project.name,
                destination: SessionImportDestination(directory: path),
              ),
        ],
        for (final workspace in workspaces)
          if (workspace.directory?.isNotEmpty == true)
            (
              label: workspace.name,
              destination: SessionImportDestination(
                directory: workspace.directory!,
                workspaceID: workspace.id,
              ),
            ),
      ];
      // Loading has finished; the modal now owns interaction. Do not keep an
      // indeterminate preparation indicator animating behind the choices.
      setState(() => _choosing = false);
      final result = await showModalBottomSheet<SessionImportDestination>(
        context: context,
        showDragHandle: true,
        isScrollControlled: true,
        builder: (context) => SafeArea(
          child: ConstrainedBox(
            constraints: BoxConstraints(
              maxHeight: MediaQuery.sizeOf(context).height * .75,
            ),
            child: ListView(
              shrinkWrap: true,
              children: [
                ListTile(title: Text(_l10n.importDestination)),
                if (choices.isEmpty)
                  Padding(
                    padding: const EdgeInsets.all(20),
                    child: Text(_l10n.importNoDestinations),
                  ),
                for (final choice in choices)
                  ListTile(
                    leading: Icon(
                      choice.destination.workspaceID == null
                          ? Icons.folder_outlined
                          : Icons.cloud_outlined,
                    ),
                    title: Text(choice.label),
                    subtitle: Text(choice.destination.directory),
                    onTap: () => Navigator.pop(context, choice.destination),
                  ),
              ],
            ),
          ),
        ),
      );
      if (mounted && _current && result != null) {
        setState(() => _destination = result);
      }
    } catch (_) {
      if (mounted) setState(() => _error = _l10n.importDestinationFailed);
    } finally {
      if (mounted) setState(() => _choosing = false);
    }
  }

  Future<void> _import() async {
    final document = _document;
    final destination = _destination;
    if (_busy ||
        !_current ||
        !_supported ||
        document == null ||
        destination == null ||
        _imported != null) {
      return;
    }
    setState(() {
      _importing = true;
      _error = null;
    });
    try {
      final ready = await widget.controller.prepareActionRepository();
      if (!mounted || !_current) return;
      if (!identical(ready, _repository)) {
        throw StateError('Connection not ready');
      }
      final session = await (_repository as SessionImportGateway).importSession(
        document,
        destination,
      );
      // Preserve a confirmed successful result even if the UI changed location
      // while the request ran. Never retry an acknowledged import automatically.
      if (mounted) setState(() => _imported = session);
      if (_current) unawaited(widget.controller.refreshSessions());
    } catch (error) {
      if (mounted) {
        setState(
          () => _error = switch (error) {
            SessionImportUnsupported() => _l10n.importUnsupported,
            SessionImportInvalid() => _l10n.importInvalidFile,
            Api2Error(statusCode: 409) => _l10n.importConflict,
            Api2Error(statusCode: 401 || 403) => _l10n.importAuthorization,
            Api2Error(tag: 'SessionNotFoundError') => _l10n.importParentMissing,
            Api2Error(statusCode: 400) => _l10n.importRejected,
            _ => _l10n.importUnconfirmed,
          },
        );
      }
    } finally {
      if (mounted) setState(() => _importing = false);
    }
  }

  Future<void> _open() async {
    final session = _imported;
    if (_busy || !_current || session == null) return;
    final profileID = widget.controller.profile?.id;
    final serverUrl = widget.controller.profile?.baseUrl;
    final route = ModalRoute.of(context);
    setState(() => _opening = true);
    try {
      await widget.controller.selectLocation(
        directory: session.directory,
        workspace: session.workspaceID,
      );
      if (!mounted ||
          route?.isCurrent != true ||
          widget.controller.profile?.id != profileID ||
          widget.controller.profile?.baseUrl != serverUrl ||
          widget.controller.directory != session.directory ||
          widget.controller.workspace != session.workspaceID) {
        return;
      }
      await Navigator.of(context).pushReplacementNamed('/chat/${session.id}');
    } catch (_) {
      if (mounted) setState(() => _error = _l10n.importOpenFailed);
    } finally {
      if (mounted) setState(() => _opening = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = _l10n;
    final document = _document;
    return PopScope(
      canPop: !_importing && !_opening,
      child: Scaffold(
        appBar: AppBar(title: Text(l10n.importTitle)),
        bottomNavigationBar: SafeArea(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(20, 12, 20, 16),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                if (!_current) Text(l10n.importChanged),
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
                if (_imported != null) ...[
                  Semantics(
                    liveRegion: true,
                    child: Text(l10n.importSucceeded),
                  ),
                  const SizedBox(height: 12),
                  FilledButton(
                    onPressed: !_busy && _current ? _open : null,
                    child: Text(l10n.importOpen),
                  ),
                ] else if (_busy) ...[
                  const LinearProgressIndicator(),
                  const SizedBox(height: 12),
                  Text(_importing ? l10n.importSending : l10n.importReading),
                ] else
                  FilledButton.icon(
                    onPressed:
                        _current &&
                            _supported &&
                            document != null &&
                            _destination != null
                        ? _import
                        : null,
                    icon: const Icon(Icons.file_download_outlined),
                    label: Text(l10n.importAction),
                  ),
              ],
            ),
          ),
        ),
        body: SafeArea(
          child: Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 600),
              child: ListView(
                key: const ValueKey('import-review-scroll'),
                padding: const EdgeInsets.all(20),
                children: [
                  Text(
                    l10n.importDescription,
                    style: Theme.of(context).textTheme.bodyLarge,
                  ),
                  const SizedBox(height: 20),
                  OutlinedButton.icon(
                    onPressed: !_busy && _current && _supported ? _pick : null,
                    icon: const Icon(Icons.upload_file_outlined),
                    label: Text(
                      _fileName == null
                          ? l10n.importChoose
                          : l10n.importChooseAnother,
                    ),
                  ),
                  if (_fileName != null)
                    Padding(
                      padding: const EdgeInsets.symmetric(vertical: 12),
                      child: Text(_fileName!),
                    ),
                  if (document != null) ...[
                    Card(
                      child: Padding(
                        padding: const EdgeInsets.all(16),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              document.title?.isNotEmpty == true
                                  ? document.title!
                                  : l10n.importUntitled,
                              style: Theme.of(context).textTheme.titleMedium,
                            ),
                            const SizedBox(height: 8),
                            Text(
                              l10n.importMessageCount(document.messageCount),
                            ),
                            SelectableText(document.id),
                          ],
                        ),
                      ),
                    ),
                    if (document.hasRedactions) _notice(l10n.importRedacted),
                    if (document.parentID != null)
                      _notice(l10n.importParent(document.parentID!)),
                    if (document.archived) _notice(l10n.importArchived),
                  ],
                  const SizedBox(height: 20),
                  Text(
                    l10n.importDestination,
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                  if (_serverName.isNotEmpty)
                    Padding(
                      padding: const EdgeInsets.only(top: 8),
                      child: Text(_serverName),
                    ),
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: const Icon(Icons.folder_outlined),
                    title: Text(
                      _destination?.directory ?? l10n.importChooseDestination,
                    ),
                    subtitle: _destination?.workspaceID == null
                        ? null
                        : Text(_destination!.workspaceID!),
                  ),
                  if (_imported == null)
                    TextButton(
                      onPressed: !_busy && _current ? _chooseDestination : null,
                      child: Text(l10n.importChangeDestination),
                    ),
                  const SizedBox(height: 8),
                  Text(l10n.importPreserves),
                  if (!_supported) _notice(l10n.importUnsupported),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _notice(String text) => Padding(
    padding: const EdgeInsets.only(top: 12),
    child: Text(
      text,
      style: TextStyle(color: Theme.of(context).colorScheme.onSurfaceVariant),
    ),
  );
}
