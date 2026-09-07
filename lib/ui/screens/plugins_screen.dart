import 'dart:async';

import 'package:flutter/material.dart';

import '../../domain/plugin_inventory.dart';
import '../../domain/server_gateway.dart' show StreamStatus, CommandInfo;
import '../../state/plugin_command_mappings.dart';
import '../widgets/run_command_dialog.dart';
import '../widgets/confirm_sheet.dart';
import '../../l10n/app_localizations.dart';
import '../../state/connection.dart';
import '../app_theme.dart';

class PluginsScreen extends StatefulWidget {
  const PluginsScreen({super.key, required this.controller});
  final ConnectionController controller;
  @override
  State<PluginsScreen> createState() => _PluginsScreenState();
}

class _PluginsScreenState extends State<PluginsScreen> {
  StreamSubscription<dynamic>? _events;
  Object? _source;
  List<PluginInfo>? _plugins;
  bool _loading = false;
  bool _failed = false;
  bool _reloadQueued = false;
  int _request = 0;
  bool _editingMapping = false;
  bool _mappingLoading = false;
  Map<String, List<String>> _mappings = {};
  PluginCommandMappings? get _mappingStore {
    final id = _controller.profile?.id;
    return id == null
        ? null
        : PluginCommandMappings(
            _controller.store.prefs,
            id,
            () => _controller.isProfileReadable(id),
          );
  }

  String get _mappingScope => PluginCommandMappings.scope(
    baseUrl: _controller.profile?.baseUrl ?? '',
    username: _controller.profile?.username,
    directory: _controller.directory,
    workspace: _controller.workspace,
  );
  ConnectionController get _controller => widget.controller;
  bool get _connected =>
      _controller.status == StreamStatus.connected &&
      _controller.isProfileReadable(_controller.profile?.id ?? '');
  bool get _supported =>
      _controller.capabilities.pluginInventory &&
      _controller.repository is PluginGateway;
  Object get _scope => (
    _controller.profile?.id,
    _controller.profile?.baseUrl,
    _controller.profile?.username,
    _controller.directory,
    _controller.workspace,
    _controller.locationRevision,
    _controller.connectionRevision,
    _controller.repository,
    _connected,
    _supported,
  );

  @override
  void initState() {
    super.initState();
    _attach();
    unawaited(_load());
  }

  void _attach() {
    _source = _scope;
    _controller.addListener(_changed);
    _controller.profileDataChanges.addListener(_changed);
    _events = _controller.events.listen((event) {
      if (event.type == 'plugin.added' || event.type == 'plugin.updated') {
        unawaited(_load());
      }
    });
  }

  void _detach(ConnectionController controller) {
    controller.removeListener(_changed);
    controller.profileDataChanges.removeListener(_changed);
    unawaited(_events?.cancel());
  }

  @override
  void didUpdateWidget(covariant PluginsScreen oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!identical(oldWidget.controller, widget.controller)) {
      _detach(oldWidget.controller);
      _request++;
      _plugins = null;
      _mappings = {};
      _loading = false;
      _failed = false;
      _reloadQueued = false;
      _attach();
      unawaited(_load());
    }
  }

  void _changed() {
    if (!mounted || _source == _scope) return;
    setState(() {
      _source = _scope;
      _request++;
      _plugins = null;
      _mappings = {};
      _loading = false;
      _failed = false;
      _reloadQueued = false;
    });
    unawaited(_load());
  }

  Future<void> _load() async {
    if (!mounted || !_connected || !_supported) return;
    if (_loading) {
      _reloadQueued = true;
      return;
    }
    final scope = _scope;
    final request = ++_request;
    final gateway = _controller.repository as PluginGateway;
    setState(() {
      _loading = true;
      _failed = false;
    });
    bool current() => mounted && request == _request && scope == _scope;
    try {
      final result = await gateway.listPlugins();
      if (current()) {
        setState(() {
          _plugins = result;
          _mappings = _mappingStore?.load(_mappingScope) ?? {};
        });
      }
    } catch (_) {
      if (current()) setState(() => _failed = true);
    } finally {
      if (current()) {
        setState(() => _loading = false);
        if (_reloadQueued) {
          _reloadQueued = false;
          unawaited(_load());
        }
      }
    }
  }

  @override
  void dispose() {
    _request++;
    _detach(_controller);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.pluginsTitle),
        actions: [
          if (_connected && _supported)
            IconButton(
              tooltip: l10n.pluginsRefresh,
              onPressed: _loading ? null : _load,
              icon: const Icon(Icons.refresh_rounded),
            ),
        ],
      ),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            Text(l10n.pluginsDescription),
            if (_mappingStore != null)
              Align(
                alignment: AlignmentDirectional.centerStart,
                child: TextButton.icon(
                  icon: const Icon(Icons.link_off_rounded),
                  label: Text(l10n.pluginMappingClearAll),
                  onPressed: _editingMapping ? null : _clearMappings,
                ),
              ),
            const SizedBox(height: 20),
            if (!_supported)
              Text(l10n.pluginsUnsupported)
            else if (!_connected)
              Text(l10n.pluginsDisconnected)
            else ...[
              if (_loading || _mappingLoading) const LinearProgressIndicator(),
              if (_failed) ...[
                Text(l10n.pluginsLoadFailed),
                Align(
                  alignment: AlignmentDirectional.centerStart,
                  child: TextButton(
                    onPressed: _loading ? null : _load,
                    child: Text(l10n.pluginsRetry),
                  ),
                ),
              ],
              if (_plugins?.isEmpty == true && !_loading && !_failed)
                Text(l10n.pluginsEmpty),
              for (final plugin in _plugins ?? const <PluginInfo>[]) ...[
                _row(plugin, l10n),
                const Divider(height: 1),
              ],
            ],
          ],
        ),
      ),
    );
  }

  AppLocalizations get _l10n =>
      lookupAppLocalizations(Localizations.localeOf(context));

  void _mappingError(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(SnackBar(content: Text(message)));
  }

  Future<void> _clearMappings() async {
    if (_editingMapping) return;
    final store = _mappingStore;
    final scope = _scope;
    if (store == null) return;
    setState(() => _editingMapping = true);
    try {
      final confirmed = await showConfirmSheet(
        context,
        title: _l10n.pluginMappingClearTitle,
        message: _l10n.pluginMappingClearDescription,
        confirmLabel: _l10n.pluginMappingClearConfirm,
        cancelLabel: MaterialLocalizations.of(context).cancelButtonLabel,
        icon: Icons.link_off_rounded,
        destructive: true,
      );
      if (!confirmed || !mounted) return;
      if (scope != _scope) throw StateError('Location changed.');
      setState(() => _mappingLoading = true);
      await store.clear();
      if (mounted && scope == _scope) setState(() => _mappings = {});
    } catch (_) {
      if (mounted && scope == _scope) {
        _mappingError(_l10n.pluginMappingClearFailed);
      }
    } finally {
      if (mounted) {
        setState(() {
          _editingMapping = false;
          _mappingLoading = false;
        });
      }
    }
  }

  Future<void> _editMapping(PluginInfo plugin) async {
    if (_editingMapping || plugin.id == null) return;
    final scope = _scope;
    final mappingScope = _mappingScope;
    final store = _mappingStore;
    if (store == null) return;
    setState(() {
      _editingMapping = true;
      _mappingLoading = true;
    });
    try {
      final commands = await _controller.repository!.listCommands();
      if (!mounted || scope != _scope) return;
      final available = {
        for (final command in commands.take(512))
          if (PluginCommandMappings.validName(command.name)) command.name,
      };
      final selected = {...?_mappings[plugin.id]};
      final names = {...selected, ...available}.toList()..sort();
      bool saving = false;
      String? error;
      setState(() => _mappingLoading = false);
      final saved = await showDialog<bool>(
        context: context,
        builder: (dialogContext) => StatefulBuilder(
          builder: (dialogContext, update) => PopScope(
            canPop: !saving,
            child: AlertDialog(
              scrollable: true,
              title: Text(_l10n.pluginMappingManage),
              content: SizedBox(
                width: 480,
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(_l10n.pluginMappingDescription),
                    const SizedBox(height: 12),
                    Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        if (names.isEmpty) Text(_l10n.pluginMappingEmpty),
                        for (final name in names)
                          CheckboxListTile(
                            contentPadding: EdgeInsets.zero,
                            title: Text('/$name'),
                            subtitle: available.contains(name)
                                ? null
                                : Text(_l10n.pluginMappingUnavailable),
                            value: selected.contains(name),
                            onChanged: saving
                                ? null
                                : (value) => update(() {
                                    if (value == true) {
                                      if (selected.length >=
                                          PluginCommandMappings.maxCommands) {
                                        error = _l10n.pluginMappingLimit;
                                      } else {
                                        selected.add(name);
                                        error = null;
                                      }
                                    } else {
                                      selected.remove(name);
                                      error = null;
                                    }
                                  }),
                          ),
                      ],
                    ),
                    if (error != null)
                      Text(
                        error!,
                        style: TextStyle(
                          color: Theme.of(dialogContext).colorScheme.error,
                        ),
                      ),
                  ],
                ),
              ),
              actions: [
                TextButton(
                  onPressed: saving ? null : () => Navigator.pop(dialogContext),
                  child: Text(
                    MaterialLocalizations.of(context).cancelButtonLabel,
                  ),
                ),
                FilledButton(
                  onPressed: saving
                      ? null
                      : () async {
                          update(() {
                            saving = true;
                            error = null;
                          });
                          try {
                            if (!mounted || scope != _scope) {
                              throw StateError('Location changed.');
                            }
                            await store.set(mappingScope, plugin.id!, selected);
                            if (!dialogContext.mounted) return;
                            if (!mounted || scope != _scope) {
                              Navigator.pop(dialogContext);
                              return;
                            }
                            Navigator.pop(dialogContext, true);
                          } catch (_) {
                            if (dialogContext.mounted) {
                              if (!mounted || scope != _scope) {
                                Navigator.pop(dialogContext);
                                return;
                              }
                              update(() {
                                saving = false;
                                error = _l10n.pluginMappingSaveFailed;
                              });
                            }
                          }
                        },
                  child: Text(_l10n.pluginMappingSave),
                ),
              ],
            ),
          ),
        ),
      );
      if (mounted && scope == _scope && saved == true) {
        setState(() => _mappings = store.load(mappingScope));
      }
    } catch (_) {
      if (mounted && scope == _scope) {
        _mappingError(_l10n.pluginMappingLoadFailed);
      }
    } finally {
      if (mounted) {
        setState(() {
          _editingMapping = false;
          _mappingLoading = false;
        });
      }
    }
  }

  Future<void> _reviewCommand(PluginInfo plugin, String name) async {
    if (_editingMapping) return;
    final scope = _scope;
    final store = _mappingStore;
    final mappingScope = _mappingScope;
    final repository = _controller.repository;
    if (store == null || repository == null) return;
    setState(() {
      _editingMapping = true;
      _mappingLoading = true;
    });
    Future<bool> valid() async {
      if (!mounted || scope != _scope || !_supported || !_connected) {
        return false;
      }
      final plugins = await (repository as PluginGateway).listPlugins();
      if (!mounted || scope != _scope || !_supported || !_connected) {
        return false;
      }
      final commands = await repository.listCommands();
      return mounted &&
          scope == _scope &&
          store.load(mappingScope)[plugin.id]?.contains(name) == true &&
          plugins.any(
            (p) => p.id == plugin.id && p.status == PluginStatus.active,
          ) &&
          commands.any((c) => c.name == name);
    }

    try {
      if (!await valid()) {
        if (mounted && scope == _scope) {
          _mappingError(_l10n.pluginMappingUnavailable);
        }
        return;
      }
      if (!mounted) return;
      setState(() => _mappingLoading = false);
      final sessionID = await showRunCommandDialog(
        context,
        controller: _controller,
        command: CommandInfo(name: name, subtask: false),
        validateCommand: valid,
      );
      if (mounted && sessionID != null && scope == _scope) {
        Navigator.of(context).pushNamed('/chat/$sessionID');
      }
    } catch (_) {
      if (mounted && scope == _scope) {
        _mappingError(_l10n.pluginMappingUnavailable);
      }
    } finally {
      if (mounted) {
        setState(() {
          _editingMapping = false;
          _mappingLoading = false;
        });
      }
    }
  }

  Widget _row(PluginInfo plugin, AppLocalizations l10n) {
    final theme = Theme.of(context);
    final source = switch (plugin.source) {
      PluginSourceKind.builtin => l10n.pluginsSourceBuiltin,
      PluginSourceKind.package => l10n.pluginsSourcePackage,
      PluginSourceKind.local => l10n.pluginsSourceLocal,
      PluginSourceKind.sdk => l10n.pluginsSourceSdk,
      PluginSourceKind.unknown => l10n.pluginsSourceUnknown,
    };
    final status = switch (plugin.status) {
      PluginStatus.active => l10n.pluginsStatusActive,
      PluginStatus.failed => l10n.pluginsStatusFailed,
      PluginStatus.unknown => l10n.pluginsStatusUnknown,
    };
    final tone = switch (plugin.status) {
      PluginStatus.active => AppStatusTone.ok,
      PluginStatus.failed => AppStatusTone.failure,
      PluginStatus.unknown => AppStatusTone.neutral,
    };
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 16),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(
            plugin.status == PluginStatus.active
                ? Icons.check_circle_outline
                : plugin.status == PluginStatus.failed
                ? Icons.error_outline
                : Icons.help_outline,
            color: AppTheme.statusColor(theme, tone),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  plugin.id ?? l10n.pluginsUnnamed,
                  style: theme.textTheme.titleMedium,
                ),
                Text(
                  status,
                  style: TextStyle(color: AppTheme.statusColor(theme, tone)),
                ),
                Text(
                  plugin.packageName == null
                      ? source
                      : '$source · ${plugin.packageName}',
                ),
                if (plugin.terminalUi) Text(l10n.pluginsTerminalUi),
                if (plugin.id case final id?) ...[
                  if ((_mappings[id] ?? []).isNotEmpty)
                    Text(l10n.pluginMappingPersonal),
                  for (final name in _mappings[id] ?? const <String>[])
                    Align(
                      alignment: AlignmentDirectional.centerStart,
                      child: TextButton.icon(
                        icon: const Icon(Icons.play_arrow_rounded),
                        label: Text(l10n.pluginMappingReview(name)),
                        onPressed:
                            !_connected ||
                                _editingMapping ||
                                plugin.status != PluginStatus.active
                            ? null
                            : () => _reviewCommand(plugin, name),
                      ),
                    ),
                  if (PluginCommandMappings.validName(id))
                    Align(
                      alignment: AlignmentDirectional.centerStart,
                      child: TextButton.icon(
                        icon: const Icon(Icons.link_rounded),
                        label: Text(l10n.pluginMappingManage),
                        onPressed: !_connected || _editingMapping
                            ? null
                            : () => _editMapping(plugin),
                      ),
                    ),
                ],
                if (plugin.status == PluginStatus.failed)
                  Text(l10n.pluginsFailureDetail),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
