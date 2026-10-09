import 'dart:async';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';

import '../../api2/transport.dart';
import '../../domain/server_gateway.dart';
import '../../domain/team_directories.dart';
import '../../l10n/app_localizations.dart';
import '../../state/connection.dart';
import '../app_theme.dart';
import '../kit/kit.dart';

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

/// One place a conversation can be imported into, named for the person.
class _Destination {
  const _Destination({required this.label, required this.destination});

  final String label;
  final SessionImportDestination destination;

  bool matches(SessionImportDestination other) =>
      destination.directory == other.directory &&
      destination.workspaceID == other.workspaceID;
}

/// Import conversation (docs/ux-system/map/all.json `session-import`,
/// proposal "fix"): choose a JSON export, review it (its title and message
/// count; the ids under Details), see where it goes as one row (the
/// project's name, with a current mark in the chooser and no Change when
/// there is only one place), then one pinned primary. An unreadable file
/// and a failed import say so where the file is, and the file stays
/// chosen.
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
  late ConnectionController _controller;
  late ServerOperationsGateway? _repository;
  late int _location;
  late String _serverName;
  SessionImportDestination? _destination;

  /// The destination's project name; null shows the folder's own name.
  String? _destinationLabel;

  /// The places the chooser offered last; null until it has loaded once.
  List<_Destination>? _choices;
  SessionImportDocument? _document;
  String? _fileName;
  String? _error;
  Session? _imported;
  bool _reading = false;
  bool _importing = false;
  bool _choosing = false;
  bool _opening = false;
  int _pickGeneration = 0;
  int _scopeGeneration = 0;

  bool get _busy => _reading || _importing || _choosing || _opening;
  bool get _current =>
      _controller.locationRevision == _location &&
      identical(_controller.repository, _repository);
  bool _isCurrent(int scope) =>
      mounted && scope == _scopeGeneration && _current;
  bool get _supported =>
      _repository is SessionImportGateway &&
      (_repository as SessionImportGateway).sessionImportSupported;
  AppLocalizations get _l10n =>
      lookupAppLocalizations(Localizations.localeOf(context));

  @override
  void initState() {
    super.initState();
    _controller = widget.controller;
    _captureControllerScope();
    final directory = _controller.directory;
    if (directory?.isNotEmpty == true) {
      _destination = SessionImportDestination(
        directory: directory!,
        workspaceID: _controller.workspace,
      );
    }
    _controller.addListener(_changed);
    if (_destination == null) unawaited(_resolveDefault());
  }

  void _captureControllerScope() {
    _repository = _controller.repository;
    _location = _controller.locationRevision;
    _serverName = _controller.profile?.name ?? '';
  }

  @override
  void didUpdateWidget(covariant SessionImportScreen oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (identical(oldWidget.controller, widget.controller)) return;
    oldWidget.controller.removeListener(_changed);
    _controller = widget.controller;
    _scopeGeneration += 1;
    _pickGeneration += 1;
    _captureControllerScope();
    _destination = null;
    _destinationLabel = null;
    _choices = null;
    _document = null;
    _fileName = null;
    _error = null;
    _imported = null;
    _reading = false;
    _importing = false;
    _choosing = false;
    _opening = false;
    _controller.addListener(_changed);
    if (_controller.directory?.isNotEmpty != true) {
      unawaited(_resolveDefault());
    } else {
      _destination = SessionImportDestination(
        directory: _controller.directory!,
        workspaceID: _controller.workspace,
      );
    }
  }

  void _changed() {
    if (mounted) setState(() {});
  }

  @override
  void dispose() {
    _controller.removeListener(_changed);
    super.dispose();
  }

  Future<void> _resolveDefault() async {
    final scope = _scopeGeneration;
    final repository = _repository;
    _choosing = true;
    try {
      final project = await repository?.loadCurrentProject();
      if (_isCurrent(scope) && project?.directory.isNotEmpty == true) {
        setState(() {
          _destination = SessionImportDestination(
            directory: project!.directory,
          );
          _destinationLabel = project.name.trim().isEmpty ? null : project.name;
        });
      }
    } catch (_) {
      // The explicit destination chooser remains available after a failed probe.
    } finally {
      if (mounted && scope == _scopeGeneration) {
        setState(() => _choosing = false);
      }
    }
  }

  Future<void> _pick() async {
    if (_busy || !_current) return;
    final generation = ++_pickGeneration;
    final scope = _scopeGeneration;
    setState(() {
      _reading = true;
      _error = null;
    });
    try {
      final file = await widget.pickFile();
      if (!_isCurrent(scope) || generation != _pickGeneration || file == null) {
        return;
      }
      setState(() {
        _fileName = file.name;
        _document = null;
        _imported = null;
      });
      final length = await file.length();
      if (!_isCurrent(scope) || generation != _pickGeneration) return;
      if (length > SessionImportDocument.maxBytes) {
        throw const SessionImportTooLarge();
      }
      final document = await SessionImportDocument.read(file.read());
      if (_isCurrent(scope) && generation == _pickGeneration) {
        setState(() => _document = document);
      }
    } catch (error) {
      if (_isCurrent(scope) && generation == _pickGeneration) {
        setState(
          () => _error = error is SessionImportTooLarge
              ? _l10n.importTooLarge
              : _l10n.importInvalidFile,
        );
      }
    } finally {
      if (mounted && generation == _pickGeneration) {
        setState(() => _reading = false);
      }
    }
  }

  Future<void> _chooseDestination() async {
    final repository = _repository;
    if (_busy || !_current || repository == null) return;
    final scope = _scopeGeneration;
    setState(() {
      _choosing = true;
      _error = null;
    });
    try {
      final projects = await repository.listProjects();
      if (!_isCurrent(scope)) return;
      final workspaces = _controller.capabilities.managedWorkspaces
          ? await repository.listWorkspaces()
          : <WorkspaceInfo>[];
      if (!_isCurrent(scope)) return;
      final choices = <_Destination>[
        for (final project in projects) ...[
          if (project.directory.isNotEmpty &&
              !isAiTeamDirectory(project.directory))
            _Destination(
              label: project.name,
              destination: SessionImportDestination(
                directory: project.directory,
              ),
            ),
          for (final path in project.worktrees)
            if (path.isNotEmpty &&
                path != project.directory &&
                !isAiTeamDirectory(path))
              _Destination(
                label: project.name,
                destination: SessionImportDestination(directory: path),
              ),
        ],
        for (final workspace in workspaces)
          if (workspace.directory?.isNotEmpty == true)
            _Destination(
              label: workspace.name,
              destination: SessionImportDestination(
                directory: workspace.directory!,
                workspaceID: workspace.id,
              ),
            ),
      ];
      // Loading has finished; the sheet now owns interaction. Do not keep
      // the loading bar running behind the choices.
      if (!mounted) return;
      setState(() {
        _choosing = false;
        _choices = choices;
      });
      final current = _destination;
      final selected = current == null
          ? null
          : choices.indexWhere((choice) => choice.matches(current));
      final result = await showKitSheet<_Destination>(
        context,
        title: _l10n.importDestination,
        icon: AppIconography.folders,
        sheetKey: const ValueKey('import-destination-sheet'),
        body: (sheet) => choices.isEmpty
            ? KitStateView(
                size: KitStateSize.inline,
                icon: AppIconography.folders,
                title: _l10n.importNoDestinationsTitle,
                body: _l10n.importNoDestinations,
              )
            : KitChoiceList<int>.single(
                semanticsLabel: _l10n.importDestination,
                selected: selected == null || selected < 0 ? null : selected,
                choices: [
                  for (final (index, choice) in choices.indexed)
                    KitChoice(
                      key: ValueKey(
                        'import-destination-${choice.destination.directory}',
                      ),
                      value: index,
                      title: choice.label,
                      supporting: KitBidi.ltr(choice.destination.directory),
                      leading: KitRow.icon(
                        sheet,
                        choice.destination.workspaceID == null
                            ? AppIconography.files
                            : AppIconography.cloud,
                      ),
                    ),
                ],
                onSelected: (index) => Navigator.of(sheet).pop(choices[index]),
              ),
      );
      if (_isCurrent(scope) && result != null) {
        setState(() {
          _destination = result.destination;
          _destinationLabel = result.label;
        });
      }
    } catch (_) {
      if (_isCurrent(scope)) {
        setState(() => _error = _l10n.importDestinationFailed);
      }
    } finally {
      if (mounted && scope == _scopeGeneration) {
        setState(() => _choosing = false);
      }
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
    final scope = _scopeGeneration;
    setState(() {
      _importing = true;
      _error = null;
    });
    try {
      final ready = await _controller.prepareActionRepository();
      if (!_isCurrent(scope)) return;
      if (!identical(ready, _repository)) {
        throw StateError('Connection not ready');
      }
      final session = await (_repository as SessionImportGateway).importSession(
        document,
        destination,
      );
      if (session.id != document.id ||
          session.directory != destination.directory ||
          session.workspaceID != destination.workspaceID) {
        throw StateError('Import returned a mismatched session');
      }
      // Preserve a confirmed successful result even if the UI changed location
      // while the request ran. Never retry an acknowledged import automatically.
      if (mounted && scope == _scopeGeneration) {
        setState(() => _imported = session);
      }
      if (_current) unawaited(_controller.refreshSessions());
    } catch (error) {
      if (mounted && scope == _scopeGeneration) {
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
      if (mounted && scope == _scopeGeneration) {
        setState(() => _importing = false);
      }
    }
  }

  Future<void> _open() async {
    final session = _imported;
    if (_busy || !_current || session == null) return;
    final scope = _scopeGeneration;
    final profileID = _controller.profile?.id;
    final serverUrl = _controller.profile?.baseUrl;
    final route = ModalRoute.of(context);
    setState(() => _opening = true);
    try {
      await _controller.selectLocation(
        directory: session.directory,
        workspace: session.workspaceID,
      );
      if (!mounted ||
          scope != _scopeGeneration ||
          route?.isCurrent != true ||
          _controller.profile?.id != profileID ||
          _controller.profile?.baseUrl != serverUrl ||
          _controller.directory != session.directory ||
          _controller.workspace != session.workspaceID) {
        return;
      }
      await Navigator.of(context).pushReplacementNamed('/chat/${session.id}');
    } catch (_) {
      if (mounted) setState(() => _error = _l10n.importOpenFailed);
    } finally {
      if (mounted && scope == _scopeGeneration) {
        setState(() => _opening = false);
      }
    }
  }

  static String _basename(String path) {
    final parts = path
        .replaceAll('\\', '/')
        .split('/')
        .where((part) => part.isNotEmpty)
        .toList();
    return parts.isEmpty ? path : parts.last;
  }

  /// The pinned primary: Import, then Open once it is in. A disabled
  /// Import says what is missing.
  Widget _primary(AppLocalizations l10n) {
    if (_imported != null) {
      return KitActionBlock(
        primary: KitAction(
          key: const ValueKey('import-open'),
          label: l10n.importOpen,
          icon: AppIconography.externalLink,
          working: _opening,
          onPressed: !_busy && _current ? _open : null,
          disabledReason: _current ? null : l10n.importChanged,
        ),
      );
    }
    final missing = !_current
        ? l10n.importChanged
        : !_supported
        ? l10n.importUnsupported
        : _document == null
        ? l10n.importNeedsFile
        : _destination == null
        ? l10n.importNeedsDestination
        : null;
    return KitActionBlock(
      primary: KitAction(
        key: const ValueKey('import-action'),
        label: l10n.importAction,
        icon: AppIconography.download,
        working: _importing,
        onPressed: missing == null && !_busy ? _import : null,
        disabledReason: _busy ? null : missing,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = _l10n;
    final tokens = KitTokens.of(context);
    final document = _document;
    final destination = _destination;
    final error = _error;
    final choices = _choices;
    // One place to go: nothing to change (session-import-destination-sheet).
    final canChange =
        _imported == null &&
        !(choices != null &&
            choices.length <= 1 &&
            destination != null &&
            (choices.isEmpty || choices.single.matches(destination)));
    final gap = SizedBox(height: tokens.sectionGap);
    final rails = EdgeInsets.symmetric(horizontal: tokens.gutter);
    return PopScope(
      canPop: !_importing && !_opening,
      child: KitScreen(
        width: KitScreenWidth.reading,
        topBar: KitTopBar(title: l10n.importTitle),
        loading: _busy,
        loadingLabel: _importing ? l10n.importSending : l10n.importReading,
        bottom: _primary(l10n),
        body: ListView(
          key: const ValueKey('import-review-scroll'),
          padding: EdgeInsetsDirectional.only(
            top: tokens.space2,
            bottom: KitScreen.endPadding(context),
          ),
          children: [
            // A changed connection or a server without import is said
            // once, under the disabled primary (STATE-8).
            Padding(
              padding: rails,
              child: KitText(
                l10n.importDescription,
                role: KitTextRole.body,
                tone: KitTextTone.secondary,
              ),
            ),
            gap,
            // 1. The file.
            KitRowGroup(
              label: l10n.importFileLabel,
              children: [
                KitRow(
                  key: const ValueKey('import-choose-file'),
                  leading: KitRow.icon(context, AppIconography.fileUpload),
                  title: _fileName ?? l10n.importChoose,
                  supporting: _fileName == null
                      ? null
                      : TextSpan(text: l10n.importChooseAnother),
                  trailing: const KitChevron(),
                  onTap: !_busy && _current && _supported ? _pick : null,
                ),
              ],
            ),
            // An unreadable file or a failed import, said where the file
            // is; the file stays chosen.
            if (error != null)
              Padding(
                padding: rails.add(
                  EdgeInsetsDirectional.only(top: tokens.space3),
                ),
                child: KitNotice(
                  key: const ValueKey('import-error'),
                  message: error,
                  tone: AppStatusTone.failure,
                  icon: AppIconography.error,
                ),
              ),
            // 2. What the file holds: its title and size only.
            if (document != null) ...[
              gap,
              KitRowGroup(
                label: l10n.importPreviewLabel,
                children: [
                  KitRow(
                    key: const ValueKey('import-preview-row'),
                    leading: KitRow.icon(context, AppIconography.chat),
                    title: document.title?.isNotEmpty == true
                        ? document.title!
                        : l10n.importUntitled,
                    titleMaxLines: 2,
                    supporting: TextSpan(
                      text: l10n.importMessages(document.messageCount),
                    ),
                  ),
                ],
              ),
              for (final note in [
                if (document.hasRedactions) l10n.importRedacted,
                if (document.parentID != null) l10n.importParent,
                if (document.archived) l10n.importArchived,
              ])
                Padding(
                  padding: rails.add(
                    EdgeInsetsDirectional.only(top: tokens.space3),
                  ),
                  child: KitNotice(
                    message: note,
                    icon: AppIconography.info,
                    liveRegion: false,
                  ),
                ),
            ],
            gap,
            // 3. Where it goes, as one row.
            KitRowGroup(
              label: l10n.importDestination,
              children: [
                KitRow(
                  key: const ValueKey('import-destination'),
                  leading: KitRowIcon(
                    destination?.workspaceID == null
                        ? AppIconography.files
                        : AppIconography.cloud,
                  ),
                  title: destination == null
                      ? l10n.importChooseDestination
                      : (_destinationLabel ?? _basename(destination.directory)),
                  titleMaxLines: 2,
                  supporting: _serverName.isEmpty
                      ? null
                      : TextSpan(text: l10n.importOnServer(_serverName)),
                  trailing: canChange
                      ? KitRowValue(l10n.importChangeDestinationShort)
                      : null,
                  onTap: canChange && !_busy && _current
                      ? _chooseDestination
                      : null,
                ),
              ],
            ),
            if (_imported != null)
              Padding(
                padding: rails.add(
                  EdgeInsetsDirectional.only(top: tokens.space3),
                ),
                child: KitNotice(
                  key: const ValueKey('import-succeeded'),
                  message: l10n.importSucceeded,
                  tone: AppStatusTone.ok,
                  icon: AppIconography.checkCircle,
                ),
              ),
            gap,
            Padding(
              padding: rails,
              child: KitText(
                l10n.importPreserves,
                role: KitTextRole.secondary,
                tone: KitTextTone.secondary,
              ),
            ),
            // 4. The technical values, last and folded.
            Padding(
              padding: rails.add(
                EdgeInsetsDirectional.only(top: tokens.space3),
              ),
              child: KitDetailsFold(
                values: [
                  if (_fileName != null)
                    KitTechnicalValue(l10n.importFileLabel, _fileName!),
                  if (document != null)
                    KitTechnicalValue(l10n.importConversationId, document.id),
                  if (document?.parentID != null)
                    KitTechnicalValue(l10n.importParentId, document!.parentID!),
                  if (destination != null)
                    KitTechnicalValue(l10n.importFolder, destination.directory),
                  if (destination?.workspaceID != null)
                    KitTechnicalValue(
                      l10n.importEnvironmentId,
                      destination!.workspaceID!,
                    ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
