import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../domain/development_service.dart';
import '../../domain/server_gateway.dart';
import '../../l10n/app_localizations.dart';
import '../../state/connection.dart';
import '../../state/development_service_store.dart';
import '../../state/development_services.dart';
import '../app_theme.dart';
import '../widgets/confirm_sheet.dart';
import '../widgets/external_link.dart';

class DevelopmentServicesScreen extends StatefulWidget {
  const DevelopmentServicesScreen({super.key, required this.controller});
  final ConnectionController controller;

  @override
  State<DevelopmentServicesScreen> createState() =>
      _DevelopmentServicesScreenState();
}

class _DevelopmentServicesScreenState extends State<DevelopmentServicesScreen>
    with WidgetsBindingObserver {
  late final Object _scope;
  late final String _profileID;
  late final DevelopmentServices _model;
  bool _invalid = false;
  bool _foreground = true;
  Timer? _timer;
  late int _connectionRevision;
  late StreamStatus _status;

  Object get _currentScope => (
    widget.controller,
    widget.controller.profile?.id,
    widget.controller.profile?.baseUrl,
    widget.controller.locationRevision,
    widget.controller.directory,
    widget.controller.workspace,
  );

  bool get _current =>
      mounted &&
      !_invalid &&
      _scope == _currentScope &&
      widget.controller.isProfileReadable(_profileID);

  AppLocalizations get _l10n =>
      lookupAppLocalizations(Localizations.localeOf(context));

  @override
  void initState() {
    super.initState();
    final connection = widget.controller;
    _scope = _currentScope;
    _profileID = connection.profile?.id ?? '';
    _connectionRevision = connection.connectionRevision;
    _status = connection.status;
    _invalid = _profileID.isEmpty || connection.directory?.isNotEmpty != true;
    _model = DevelopmentServices(
      store: DevelopmentServiceStore(
        preferences: connection.store.prefs,
        profileID: _profileID,
        canWrite: () => connection.isProfileReadable(_profileID),
      ),
      directory: connection.directory ?? '',
      workspace: connection.workspace,
      isCurrent: () => _current && _foreground,
      supported: () => connection.capabilities.developmentServices,
      resolveGateway: () async {
        final revision = connection.connectionRevision;
        final gateway = await connection.prepareActionRepository();
        if (!_current || revision != connection.connectionRevision) return null;
        return gateway;
      },
    );
    _model.addListener(_changed);
    connection.addListener(_connectionChanged);
    WidgetsBinding.instance.addObserver(this);
    scheduleMicrotask(_refresh);
    _armTimer();
  }

  void _armTimer() {
    _timer?.cancel();
    _timer = Timer.periodic(const Duration(seconds: 5), (_) => _refresh());
  }

  void _changed() {
    if (mounted) setState(() {});
  }

  void _connectionChanged() {
    if (!_current) {
      _invalid = true;
      _timer?.cancel();
      _model.invalidateRuntime();
    } else if (_connectionRevision != widget.controller.connectionRevision ||
        _status != widget.controller.status) {
      _connectionRevision = widget.controller.connectionRevision;
      _status = widget.controller.status;
      _model.invalidateRuntime();
      if (_status == StreamStatus.connected) scheduleMicrotask(_refresh);
    }
    _changed();
  }

  Future<void> _refresh() async {
    if (_current && _foreground) await _model.refresh();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    _foreground = state == AppLifecycleState.resumed;
    if (_foreground) {
      _armTimer();
      unawaited(_refresh());
    } else {
      _timer?.cancel();
      _model.invalidateRuntime();
    }
  }

  @override
  void dispose() {
    _timer?.cancel();
    WidgetsBinding.instance.removeObserver(this);
    widget.controller.removeListener(_connectionChanged);
    _model.removeListener(_changed);
    _model.dispose();
    super.dispose();
  }

  Future<void> _register() async {
    if (!_current || _model.busy) return;
    final result = await showModalBottomSheet<DevelopmentService>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      builder: (_) => _ServiceEditor(
        directory: _model.directory,
        workspace: _model.workspace,
      ),
    );
    if (result != null && _current) await _model.register(result);
  }

  Future<void> _confirm(DevelopmentService service, String action) async {
    if (!_current || _model.busy) return;
    final l = _l10n;
    final (title, message) = switch (action) {
      'start' => (
        l.servicesStart,
        '${l.servicesStartHint}\n\n${service.directory}\n${service.command}',
      ),
      'stop' => (l.servicesStop, l.servicesStopHint),
      'restart' => (l.servicesRestart, l.servicesRestartHint),
      'forget' => (l.servicesForget, l.servicesForgetHint),
      _ => (l.servicesRemove, l.servicesRemoveHint),
    };
    final approved = await showConfirmSheet(
      context,
      title: '$title · ${service.name}',
      message: message,
      confirmLabel: title,
      destructive: action != 'start',
    );
    if (!approved || !_current) return;
    switch (action) {
      case 'start':
        await _model.start(service.id);
      case 'stop':
        await _model.stop(service.id);
      case 'restart':
        await _model.restart(service.id);
      case 'forget':
        await _model.forgetRun(service.id);
      default:
        await _model.remove(service.id);
    }
  }

  Future<void> _logs(DevelopmentService service) async {
    if (!_current) return;
    unawaited(_model.readLogs(service.id));
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      showDragHandle: true,
      builder: (_) => AnimatedBuilder(
        animation: _model,
        builder: (context, _) {
          final l = _l10n;
          return SizedBox(
            height: MediaQuery.sizeOf(context).height * .75,
            child: ListView(
              padding: const EdgeInsets.fromLTRB(20, 4, 20, 24),
              children: [
                Text(
                  '${service.name} · ${l.servicesLogs}',
                  style: Theme.of(context).textTheme.titleLarge,
                ),
                const SizedBox(height: 8),
                if (!_current)
                  Text(l.servicesScopeChanged)
                else ...[
                  Text(l.servicesLogTail),
                  const SizedBox(height: 12),
                  if (_model.busy) const LinearProgressIndicator(),
                  if (_model.error != null)
                    Text(
                      '${_model.error}',
                      style: TextStyle(
                        color: Theme.of(context).colorScheme.error,
                      ),
                    ),
                  SelectableText(
                    _model.logs[service.id]?.isNotEmpty == true
                        ? _model.logs[service.id]!
                        : l.servicesLogEmpty,
                    style: const TextStyle(fontFamily: AppTheme.monoFamily),
                  ),
                  const SizedBox(height: 12),
                  OutlinedButton.icon(
                    onPressed: _model.busy
                        ? null
                        : () => _model.readLogs(service.id),
                    icon: const Icon(AppIconography.retry),
                    label: Text(l.servicesRefresh),
                  ),
                ],
              ],
            ),
          );
        },
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final l = _l10n;
    final theme = Theme.of(context);
    return Scaffold(
      appBar: AppBar(
        title: Text(l.servicesTitle),
        actions: [
          if (_current)
            IconButton(
              tooltip: l.servicesRefresh,
              onPressed: _model.busy ? null : _refresh,
              icon: const Icon(AppIconography.retry),
            ),
        ],
      ),
      body: !_current
          ? Center(
              child: Padding(
                padding: const EdgeInsets.all(24),
                child: Text(l.servicesScopeChanged),
              ),
            )
          : RefreshIndicator(
              onRefresh: _refresh,
              child: ListView(
                physics: const AlwaysScrollableScrollPhysics(),
                padding: const EdgeInsets.fromLTRB(16, 8, 16, 32),
                children: [
                  Container(
                    padding: const EdgeInsets.all(20),
                    decoration: BoxDecoration(
                      color: theme.colorScheme.primaryContainer.withValues(
                        alpha: .45,
                      ),
                      borderRadius: BorderRadius.circular(24),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Icon(
                          AppIconography.processor,
                          size: 32,
                          color: theme.colorScheme.primary,
                        ),
                        const SizedBox(height: 12),
                        Text(
                          l.servicesSubtitle,
                          style: theme.textTheme.titleLarge,
                        ),
                        const SizedBox(height: 8),
                        Text(l.servicesIntro),
                        const SizedBox(height: 12),
                        Text(
                          _model.directory,
                          style: theme.textTheme.bodySmall?.copyWith(
                            fontFamily: AppTheme.monoFamily,
                          ),
                        ),
                        const SizedBox(height: 16),
                        FilledButton.icon(
                          onPressed: _model.busy || !_model.readable
                              ? null
                              : _register,
                          icon: const Icon(AppIconography.add),
                          label: Text(l.servicesAdd),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 16),
                  if (!widget.controller.capabilities.developmentServices)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 12),
                      child: Text(l.servicesUnavailable),
                    ),
                  if (_model.busy) ...[
                    const LinearProgressIndicator(),
                    const SizedBox(height: 8),
                  ],
                  if (_model.error != null)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 12),
                      child: Text(
                        '${_model.error}',
                        style: TextStyle(color: theme.colorScheme.error),
                      ),
                    ),
                  for (final service in _model.services)
                    _card(service, l, theme),
                ],
              ),
            ),
    );
  }

  Widget _card(
    DevelopmentService service,
    AppLocalizations l,
    ThemeData theme,
  ) {
    final status = _model.status(service);
    final label = switch (status) {
      DevelopmentServiceStatus.notStarted => l.servicesNotStarted,
      DevelopmentServiceStatus.running => l.servicesRunning,
      DevelopmentServiceStatus.stopped => l.servicesStopped,
      DevelopmentServiceStatus.unknown => l.servicesUnknown,
    };
    final color = status == DevelopmentServiceStatus.running
        ? theme.colorScheme.primary
        : theme.colorScheme.onSurfaceVariant;
    return Card(
      margin: const EdgeInsets.only(bottom: 14),
      child: Padding(
        padding: const EdgeInsets.all(18),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(service.name, style: theme.textTheme.titleLarge),
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              crossAxisAlignment: WrapCrossAlignment.center,
              children: [
                Icon(
                  status == DevelopmentServiceStatus.running
                      ? AppIconography.statusDot
                      : AppIconography.radioEmpty,
                  size: 14,
                  color: color,
                ),
                Text(
                  label,
                  style: TextStyle(color: color, fontWeight: FontWeight.w600),
                ),
              ],
            ),
            const SizedBox(height: 6),
            Text(l.servicesStatusHint, style: theme.textTheme.bodySmall),
            if (_model.exitCode(service) case final code?)
              Text(l.servicesExit(code)),
            const SizedBox(height: 12),
            SelectableText(
              service.command,
              style: const TextStyle(fontFamily: AppTheme.monoFamily),
            ),
            if (service.url.isNotEmpty) ...[
              const SizedBox(height: 8),
              Text(service.url, style: theme.textTheme.bodySmall),
            ],
            if (status == DevelopmentServiceStatus.unknown) ...[
              const SizedBox(height: 8),
              Text(l.servicesUnknownHint),
            ],
            const SizedBox(height: 12),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                if (widget.controller.capabilities.developmentServices)
                  FilledButton.tonalIcon(
                    onPressed: _model.canStart(service)
                        ? () => _confirm(service, 'start')
                        : null,
                    icon: const Icon(AppIconography.play),
                    label: Text(l.servicesStart),
                  ),
                if (_model.canStop(service)) ...[
                  OutlinedButton(
                    onPressed: () => _confirm(service, 'stop'),
                    child: Text(l.servicesStop),
                  ),
                  OutlinedButton(
                    onPressed: () => _confirm(service, 'restart'),
                    child: Text(l.servicesRestart),
                  ),
                ],
                if (service.run != null &&
                    !service.run!.stopped &&
                    widget.controller.capabilities.developmentServices)
                  OutlinedButton.icon(
                    onPressed: _model.busy ? null : () => _logs(service),
                    icon: const Icon(AppIconography.text),
                    label: Text(l.servicesLogs),
                  ),
                if (safeExternalLinkUri(service.url) != null)
                  OutlinedButton.icon(
                    onPressed: () {
                      if (_current) {
                        unawaited(openExternalLink(context, service.url));
                      }
                    },
                    icon: const Icon(AppIconography.externalLink),
                    label: Text(l.servicesVisit),
                  ),
                IconButton(
                  tooltip: l.servicesCopy,
                  onPressed: () =>
                      Clipboard.setData(ClipboardData(text: service.command)),
                  icon: const Icon(AppIconography.copy),
                ),
              ],
            ),
            Wrap(
              spacing: 8,
              children: [
                if (status == DevelopmentServiceStatus.unknown)
                  TextButton(
                    onPressed: _model.busy
                        ? null
                        : () => _confirm(service, 'forget'),
                    child: Text(l.servicesForget),
                  ),
                TextButton(
                  onPressed: _model.busy
                      ? null
                      : () => _confirm(service, 'remove'),
                  child: Text(l.servicesRemove),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _ServiceEditor extends StatefulWidget {
  const _ServiceEditor({required this.directory, required this.workspace});
  final String directory;
  final String? workspace;
  @override
  State<_ServiceEditor> createState() => _ServiceEditorState();
}

class _ServiceEditorState extends State<_ServiceEditor> {
  final _name = TextEditingController();
  final _command = TextEditingController();
  final _url = TextEditingController();
  bool _invalid = false;
  @override
  void dispose() {
    _name.dispose();
    _command.dispose();
    _url.dispose();
    super.dispose();
  }

  void _save() {
    final name = _name.text.trim();
    final command = _command.text.trim();
    final url = _url.text.trim();
    if (name.isEmpty ||
        command.isEmpty ||
        (url.isNotEmpty && safeExternalLinkUri(url) == null)) {
      setState(() => _invalid = true);
      return;
    }
    Navigator.pop(
      context,
      DevelopmentService(
        id: DevelopmentServices.newID(),
        name: name,
        command: command,
        directory: widget.directory,
        workspace: widget.workspace,
        url: url,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final l = lookupAppLocalizations(Localizations.localeOf(context));
    return SingleChildScrollView(
      padding: EdgeInsets.fromLTRB(
        20,
        20,
        20,
        MediaQuery.viewInsetsOf(context).bottom + 24,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(l.servicesAdd, style: Theme.of(context).textTheme.headlineSmall),
          const SizedBox(height: 8),
          Text(widget.directory),
          const SizedBox(height: 16),
          TextField(
            controller: _name,
            maxLength: 80,
            decoration: InputDecoration(labelText: l.servicesName),
          ),
          const SizedBox(height: 8),
          TextField(
            controller: _command,
            maxLength: 4096,
            minLines: 1,
            maxLines: 5,
            decoration: InputDecoration(
              labelText: l.servicesCommand,
              helperText: l.servicesCommandHint,
              helperMaxLines: 5,
            ),
          ),
          const SizedBox(height: 8),
          TextField(
            controller: _url,
            maxLength: 2048,
            keyboardType: TextInputType.url,
            decoration: InputDecoration(
              labelText: l.servicesUrl,
              helperText: l.servicesUrlHint,
              helperMaxLines: 6,
            ),
          ),
          if (_invalid)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 8),
              child: Text(
                l.servicesInvalid,
                style: TextStyle(color: Theme.of(context).colorScheme.error),
              ),
            ),
          const SizedBox(height: 16),
          FilledButton(onPressed: _save, child: Text(l.servicesSave)),
        ],
      ),
    );
  }
}
