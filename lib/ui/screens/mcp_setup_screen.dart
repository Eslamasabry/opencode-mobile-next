import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../api/product_repository.dart';
import '../../l10n/app_localizations.dart';
import '../../state/connection.dart';
import '../widgets/product_states.dart';
import '../widgets/info_label.dart';

class McpSetupScreen extends StatefulWidget {
  final ConnectionController controller;

  const McpSetupScreen({super.key, required this.controller});

  @override
  State<McpSetupScreen> createState() => _McpSetupScreenState();
}

class _McpSetupScreenState extends State<McpSetupScreen> {
  final _formKey = GlobalKey<FormState>();
  final _name = TextEditingController();
  final _url = TextEditingController();
  final _command = TextEditingController();
  final _cwd = TextEditingController();
  final _headers = TextEditingController();
  final _environment = TextEditingController();
  final _timeout = TextEditingController();

  late McpConfigScope _scope;
  late final int _location;
  late final String? _directory, _workspace;
  late final bool _runtime;
  bool _detached = false;
  McpServerKind _kind = McpServerKind.remote;
  bool _detectOAuth = true;
  bool _saving = false;
  bool _configurationSaved = false;
  String? _saveError;

  bool get _hasProject => _directory?.trim().isNotEmpty == true;
  bool get _editable => !_saving && !_configurationSaved && !_detached;
  bool get _locationMatches =>
      widget.controller.locationRevision == _location &&
      widget.controller.directory == _directory &&
      widget.controller.workspace == _workspace &&
      (_runtime
          ? widget.controller.capabilities.mcpRuntimeAdds
          : widget.controller.capabilities.mcpConfigWrites);

  @override
  void initState() {
    super.initState();
    _location = widget.controller.locationRevision;
    _directory = widget.controller.directory;
    _workspace = widget.controller.workspace;
    _runtime =
        !widget.controller.capabilities.mcpConfigWrites &&
        widget.controller.capabilities.mcpRuntimeAdds;
    _scope = _runtime
        ? McpConfigScope.runtimeLocation
        : _hasProject
        ? McpConfigScope.project
        : McpConfigScope.global;
    _detached = !_locationMatches;
    widget.controller.addListener(_connectionChanged);
  }

  void _connectionChanged() {
    if (!_detached && !_locationMatches && mounted) {
      setState(() => _detached = true);
    }
  }

  @override
  void dispose() {
    widget.controller.removeListener(_connectionChanged);
    _name.dispose();
    _url.dispose();
    _command.dispose();
    _cwd.dispose();
    _headers.dispose();
    _environment.dispose();
    _timeout.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    if (!_editable || !_formKey.currentState!.validate()) {
      return;
    }
    final route = ModalRoute.of(context);
    final navigator = Navigator.of(context);
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    setState(() {
      _saving = true;
      _saveError = null;
    });
    try {
      final timeoutText = _timeout.text.trim();
      final draft = McpServerDraft(
        name: _name.text,
        kind: _kind,
        url: _kind == McpServerKind.remote ? _url.text : null,
        command: _kind == McpServerKind.local
            ? _lines(_command.text)
            : const [],
        cwd: _kind == McpServerKind.local ? _cwd.text : null,
        headers: _kind == McpServerKind.remote
            ? _pairs(_headers.text, 'HTTP header')
            : const {},
        environment: _kind == McpServerKind.local
            ? _pairs(_environment.text, 'environment variable')
            : const {},
        detectOAuth: _detectOAuth,
        timeoutMs: timeoutText.isEmpty ? null : int.parse(timeoutText),
      );
      // Keep repository validation authoritative even if a field validator is
      // changed later.
      draft.toConfigJson();
      final repository = await widget.controller.prepareActionRepository();
      if (!mounted) return;
      if (_detached || !_locationMatches) {
        _detached = true;
        throw ProductException(l10n.mcpLocationChanged);
      }
      if (repository == null) {
        throw const ProductException(
          'OpenCode is reconnecting. Try again shortly.',
        );
      }
      await repository.addMcpServer(draft, scope: _scope);
      _configurationSaved = true;
      // Runtime adds are already applied. Reconnecting the app is only needed
      // after the persistent v1 configuration write, and only in its location.
      if (!_runtime && mounted && _locationMatches) {
        await widget.controller.reloadAfterConfigurationChange();
      }
      if (mounted && route != null && route.isActive) {
        if (route.isCurrent) {
          navigator.pop(true);
        } else {
          navigator.removeRoute(route, true);
        }
      }
    } catch (error) {
      if (mounted) {
        setState(() {
          _saveError = _configurationSaved
              ? 'Saved in OpenCode, but the app could not reconnect. '
                    '${productErrorText(error)}'
              : productErrorText(error);
        });
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    return Scaffold(
      appBar: AppBar(
        title: const Text('Add MCP server'),
        actions: [
          // A full-size info target instead of an inline label: the form is
          // a lazy list and a header row would push its fields below the
          // fold on a narrow large-text phone.
          IconButton(
            key: const ValueKey('mcp-glossary'),
            tooltip: 'What is MCP?',
            icon: const Icon(Icons.info_outline_rounded),
            onPressed: () => InfoLabel.show(
              context,
              term: Glossary.mcp.term,
              explanation: Glossary.mcp.explanation,
            ),
          ),
        ],
      ),
      bottomNavigationBar: SafeArea(
        minimum: const EdgeInsets.fromLTRB(16, 8, 16, 12),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            if (_detached && !_configurationSaved && _saveError == null)
              Padding(
                padding: const EdgeInsets.only(bottom: 8),
                child: Text(l10n.mcpLocationChanged),
              ),
            if (_saveError case final error?) ...[
              Semantics(
                liveRegion: true,
                child: Text(
                  error,
                  key: const ValueKey('mcp-save-error'),
                  style: theme.textTheme.bodyMedium?.copyWith(
                    color: theme.colorScheme.error,
                  ),
                ),
              ),
              const SizedBox(height: 8),
            ],
            FilledButton.icon(
              key: const ValueKey('mcp-save'),
              onPressed: _saving || (_detached && !_configurationSaved)
                  ? null
                  : _configurationSaved
                  ? () => Navigator.pop(context, true)
                  : _save,
              icon: _saving
                  ? const SizedBox.square(
                      dimension: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : Icon(
                      _configurationSaved
                          ? Icons.check_rounded
                          : Icons.save_outlined,
                    ),
              label: Text(
                _saving
                    ? (_runtime ? l10n.mcpAdding : 'Saving configuration')
                    : _configurationSaved
                    ? 'Close'
                    : (_runtime ? l10n.mcpAdd : 'Save MCP server'),
              ),
            ),
          ],
        ),
      ),
      body: Form(
        key: _formKey,
        autovalidateMode: AutovalidateMode.onUserInteraction,
        child: ListView(
          key: const ValueKey('mcp-setup-form'),
          padding: const EdgeInsets.fromLTRB(20, 12, 20, 24),
          children: [
            Text(
              _runtime ? l10n.mcpRuntimeTitle : 'Persisted configuration',
              style: theme.textTheme.titleMedium,
            ),
            const SizedBox(height: 4),
            Text(
              _runtime
                  ? l10n.mcpRuntimeDescription
                  : 'Saved by OpenCode on the server. It remains available after the app or server restarts.',
              style: theme.textTheme.bodyMedium?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
            const SizedBox(height: 16),
            if (_runtime) ...[
              Text(l10n.mcpCurrentLocation, style: theme.textTheme.labelMedium),
              const SizedBox(height: 4),
              Text(
                [
                  if (_hasProject) _directory!,
                  if (_workspace?.isNotEmpty == true)
                    l10n.mcpWorkspaceLocation(_workspace!),
                  if (!_hasProject && _workspace?.isNotEmpty != true)
                    l10n.mcpDefaultLocation,
                ].join('\n'),
                key: const ValueKey('mcp-location'),
              ),
            ] else ...[
              SegmentedButton<McpConfigScope>(
                key: const ValueKey('mcp-scope'),
                showSelectedIcon: false,
                segments: [
                  ButtonSegment(
                    value: McpConfigScope.project,
                    enabled: _hasProject,
                    icon: const Icon(Icons.folder_outlined),
                    label: const Text('This project'),
                  ),
                  const ButtonSegment(
                    value: McpConfigScope.global,
                    icon: Icon(Icons.public_outlined),
                    label: Text('All projects'),
                  ),
                ],
                selected: {_scope},
                onSelectionChanged: !_editable
                    ? null
                    : (value) => setState(() => _scope = value.single),
              ),
              const SizedBox(height: 8),
              Text(
                _scope == McpConfigScope.project
                    ? 'Writes only to $_directory.'
                    : 'Writes to this OpenCode server’s global configuration.',
                style: theme.textTheme.bodySmall?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
            ],
            const SizedBox(height: 20),
            const Divider(),
            const SizedBox(height: 12),
            TextFormField(
              key: const ValueKey('mcp-name'),
              controller: _name,
              enabled: _editable,
              textInputAction: TextInputAction.next,
              decoration: const InputDecoration(
                labelText: 'Server name',
                hintText: 'docs or browser-tools',
                helperText: 'Unique within the selected configuration.',
              ),
              validator: (value) =>
                  value?.trim().isEmpty == true ? 'Enter a server name' : null,
            ),
            const SizedBox(height: 20),
            SegmentedButton<McpServerKind>(
              key: const ValueKey('mcp-kind'),
              showSelectedIcon: false,
              segments: const [
                ButtonSegment(
                  value: McpServerKind.remote,
                  icon: Icon(Icons.cloud_outlined),
                  label: Text('Remote URL'),
                ),
                ButtonSegment(
                  value: McpServerKind.local,
                  icon: Icon(Icons.terminal_rounded),
                  label: Text('Local command'),
                ),
              ],
              selected: {_kind},
              onSelectionChanged: !_editable
                  ? null
                  : (value) => setState(() {
                      _kind = value.single;
                      _saveError = null;
                    }),
            ),
            const SizedBox(height: 16),
            if (_kind == McpServerKind.remote) ..._remoteFields(),
            if (_kind == McpServerKind.local) ..._localFields(),
            const SizedBox(height: 16),
            TextFormField(
              key: const ValueKey('mcp-timeout'),
              controller: _timeout,
              enabled: _editable,
              keyboardType: TextInputType.number,
              inputFormatters: [FilteringTextInputFormatter.digitsOnly],
              decoration: const InputDecoration(
                labelText: 'Timeout in milliseconds',
                hintText: 'Optional',
              ),
              validator: (value) {
                final text = value?.trim() ?? '';
                if (text.isEmpty) return null;
                final timeout = int.tryParse(text);
                return timeout == null || timeout <= 0
                    ? 'Enter a value greater than zero'
                    : null;
              },
            ),
          ],
        ),
      ),
    );
  }

  List<Widget> _remoteFields() => [
    TextFormField(
      key: const ValueKey('mcp-url'),
      controller: _url,
      enabled: _editable,
      keyboardType: TextInputType.url,
      textInputAction: TextInputAction.next,
      autocorrect: false,
      decoration: const InputDecoration(
        labelText: 'MCP endpoint URL',
        hintText: 'https://server.example/mcp',
        helperText: 'HTTP is accepted for local development servers.',
      ),
      validator: (value) {
        final uri = Uri.tryParse(value?.trim() ?? '');
        if (uri == null ||
            !uri.hasAuthority ||
            (uri.scheme != 'https' && uri.scheme != 'http') ||
            uri.userInfo.isNotEmpty) {
          return 'Enter a valid HTTP or HTTPS URL without credentials';
        }
        return null;
      },
    ),
    const SizedBox(height: 16),
    TextFormField(
      key: const ValueKey('mcp-headers'),
      controller: _headers,
      enabled: _editable,
      minLines: 2,
      maxLines: 5,
      keyboardType: TextInputType.multiline,
      autocorrect: false,
      decoration: const InputDecoration(
        labelText: 'HTTP headers',
        hintText: 'Authorization=Bearer token',
        helperText: 'Optional. Enter one KEY=VALUE pair per line.',
        alignLabelWithHint: true,
      ),
      validator: (value) => _pairError(value ?? '', 'HTTP header'),
    ),
    const SizedBox(height: 8),
    SwitchListTile.adaptive(
      key: const ValueKey('mcp-oauth-detection'),
      contentPadding: EdgeInsets.zero,
      title: const Text('Detect OAuth automatically'),
      subtitle: const Text(
        'Turn this off when the server uses headers and should never start OAuth.',
      ),
      value: _detectOAuth,
      onChanged: !_editable
          ? null
          : (value) => setState(() => _detectOAuth = value),
    ),
  ];

  List<Widget> _localFields() => [
    TextFormField(
      key: const ValueKey('mcp-command'),
      controller: _command,
      enabled: _editable,
      minLines: 4,
      maxLines: 8,
      keyboardType: TextInputType.multiline,
      autocorrect: false,
      decoration: const InputDecoration(
        labelText: 'Command and arguments',
        hintText: 'npx\n-y\n@package/mcp-server',
        helperText:
            'Runs on the OpenCode server, not this phone. Enter one argument per line.',
        alignLabelWithHint: true,
      ),
      validator: (value) =>
          _lines(value ?? '').isEmpty ? 'Enter a command' : null,
    ),
    const SizedBox(height: 16),
    TextFormField(
      key: const ValueKey('mcp-cwd'),
      controller: _cwd,
      enabled: _editable,
      textInputAction: TextInputAction.next,
      decoration: const InputDecoration(
        labelText: 'Working directory',
        hintText: 'Optional server path',
      ),
    ),
    const SizedBox(height: 16),
    TextFormField(
      key: const ValueKey('mcp-environment'),
      controller: _environment,
      enabled: _editable,
      minLines: 2,
      maxLines: 5,
      keyboardType: TextInputType.multiline,
      autocorrect: false,
      decoration: const InputDecoration(
        labelText: 'Environment variables',
        hintText: 'LOG_LEVEL=warn',
        helperText: 'Optional. Enter one KEY=VALUE pair per line.',
        alignLabelWithHint: true,
      ),
      validator: (value) => _pairError(value ?? '', 'environment variable'),
    ),
  ];

  static List<String> _lines(String value) => value
      .split('\n')
      .map((line) => line.trim())
      .where((line) => line.isNotEmpty)
      .toList();

  static Map<String, String> _pairs(String value, String label) {
    final result = <String, String>{};
    final lines = value.split('\n');
    for (var index = 0; index < lines.length; index++) {
      final line = lines[index].trim();
      if (line.isEmpty) continue;
      final separator = line.indexOf('=');
      if (separator < 1) {
        throw ProductException(
          'Invalid $label on line ${index + 1}. Use KEY=VALUE.',
        );
      }
      final key = line.substring(0, separator).trim();
      final content = line.substring(separator + 1).trim();
      if (key.isEmpty || key.contains(RegExp(r'[\r\n=]'))) {
        throw ProductException('Invalid $label name on line ${index + 1}.');
      }
      if (result.containsKey(key)) {
        throw ProductException('Duplicate $label name "$key".');
      }
      result[key] = content;
    }
    return result;
  }

  static String? _pairError(String value, String label) {
    try {
      _pairs(value, label);
      return null;
    } on ProductException catch (error) {
      return error.message;
    }
  }
}
