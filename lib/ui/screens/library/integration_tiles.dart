part of '../library_screen.dart';

class _McpServerTile extends StatelessWidget {
  final McpServerInfo server;
  final String subtitle;
  final String actionLabel;
  final bool busy;
  final VoidCallback? onAction;

  /// §7 row 9: this server generation has no MCP OAuth endpoints, so a server
  /// waiting on authorization says where the work has to happen instead of
  /// offering a button that cannot do it.
  final bool authGated;

  const _McpServerTile({
    required this.server,
    required this.subtitle,
    required this.actionLabel,
    required this.busy,
    required this.onAction,
    this.authGated = false,
  });

  Widget _action() {
    if (authGated) {
      return const Chip(
        key: ValueKey('gated-mcp-oauth'),
        label: Text('Authenticate from the server machine'),
        visualDensity: VisualDensity.compact,
      );
    }
    return busy
        ? const Padding(
            padding: EdgeInsets.all(14),
            child: SizedBox.square(
              dimension: 20,
              child: CircularProgressIndicator(strokeWidth: 2),
            ),
          )
        : TextButton(onPressed: onAction, child: Text(actionLabel));
  }

  @override
  Widget build(BuildContext context) => LayoutBuilder(
    builder: (context, constraints) {
      final scaledBody = MediaQuery.textScalerOf(context).scale(14);
      // The gated explainer chip is far wider than a "Connect" button, so it
      // always takes the stacked layout rather than a cramped trailing slot.
      final stackAction =
          authGated || constraints.maxWidth < 420 || scaledBody > 20;
      if (!stackAction) {
        return ListTile(
          leading: _McpStatus(status: server.status),
          title: Text(server.name),
          subtitle: Text(
            subtitle,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
          ),
          trailing: _action(),
        );
      }
      return Padding(
        padding: const EdgeInsets.fromLTRB(16, 8, 12, 8),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Padding(
              padding: const EdgeInsets.only(top: 4),
              child: _McpStatus(status: server.status),
            ),
            const SizedBox(width: 16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    server.name,
                    style: Theme.of(context).textTheme.bodyLarge,
                  ),
                  const SizedBox(height: 2),
                  Text(
                    subtitle,
                    style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      color: Theme.of(context).colorScheme.onSurfaceVariant,
                    ),
                  ),
                  const SizedBox(height: 4),
                  _action(),
                ],
              ),
            ),
          ],
        ),
      );
    },
  );
}

class _PendingMcpOAuth {
  final McpServerInfo server;
  final McpAuthLaunch launch;
  final McpOAuthLoopbackListener? listener;

  const _PendingMcpOAuth({
    required this.server,
    required this.launch,
    required this.listener,
  });
}

class _PendingMcpOAuthTile extends StatelessWidget {
  final _PendingMcpOAuth pending;
  final bool busy;
  final VoidCallback onEnterCode;
  final VoidCallback onCancel;

  const _PendingMcpOAuthTile({
    required this.pending,
    required this.busy,
    required this.onEnterCode,
    required this.onCancel,
  });

  @override
  Widget build(BuildContext context) => Semantics(
    container: true,
    label: 'MCP authorization pending for ${pending.server.name}',
    child: Padding(
      key: const ValueKey('pending-mcp-oauth'),
      padding: const EdgeInsets.fromLTRB(16, 4, 12, 12),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Padding(
            padding: EdgeInsets.only(top: 4),
            child: Icon(Icons.phonelink_lock_outlined),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Waiting for browser authorization',
                  style: Theme.of(context).textTheme.titleSmall,
                ),
                const SizedBox(height: 4),
                Text(
                  pending.listener == null
                      ? 'Automatic callback capture is unavailable. Paste the callback URL or authorization code.'
                      : 'The phone is securely listening for this authorization callback. You can also enter it manually.',
                  style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    color: Theme.of(context).colorScheme.onSurfaceVariant,
                  ),
                ),
                const SizedBox(height: 4),
                Wrap(
                  spacing: 4,
                  runSpacing: 4,
                  children: [
                    TextButton(
                      key: const ValueKey('enter-mcp-oauth-code'),
                      onPressed: busy ? null : onEnterCode,
                      child: const Text('Enter code'),
                    ),
                    TextButton.icon(
                      key: const ValueKey('cancel-mcp-oauth'),
                      onPressed: busy ? null : onCancel,
                      icon: const Icon(Icons.close_rounded),
                      label: const Text('Cancel'),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    ),
  );
}

class _McpOAuthCodeDialog extends StatefulWidget {
  final String expectedState;

  const _McpOAuthCodeDialog({required this.expectedState});

  @override
  State<_McpOAuthCodeDialog> createState() => _McpOAuthCodeDialogState();
}

class _McpOAuthCodeDialogState extends State<_McpOAuthCodeDialog> {
  final _controller = TextEditingController();
  String? _error;

  void _submit() {
    try {
      final code = parseMcpAuthorizationCode(
        _controller.text,
        expectedState: widget.expectedState,
      );
      Navigator.pop(context, code);
    } catch (error) {
      setState(() => _error = productErrorText(error));
    }
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
    scrollable: true,
    title: const Text('Complete MCP authorization'),
    content: TextField(
      key: const ValueKey('mcp-oauth-code-input'),
      controller: _controller,
      autofocus: true,
      minLines: 1,
      maxLines: 4,
      autocorrect: false,
      enableSuggestions: false,
      decoration: InputDecoration(
        labelText: 'Callback URL or code',
        helperText:
            'Paste the complete callback URL when available so its security state can be verified.',
        errorText: _error,
      ),
      onSubmitted: (_) => _submit(),
    ),
    actions: [
      TextButton(
        onPressed: () => Navigator.pop(context),
        child: const Text('Back'),
      ),
      FilledButton(
        key: const ValueKey('complete-mcp-oauth'),
        onPressed: _submit,
        child: const Text('Complete'),
      ),
    ],
  );

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }
}

class _ProviderIntegrationTile extends StatelessWidget {
  final PresentedIntegration presented;
  final String subtitle;
  final bool busy;
  final VoidCallback onConnect;
  final VoidCallback onDisconnect;

  const _ProviderIntegrationTile({
    required this.presented,
    required this.subtitle,
    required this.busy,
    required this.onConnect,
    required this.onDisconnect,
  });

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final textScale = MediaQuery.textScalerOf(context).scale(1);
        final stackAction = constraints.maxWidth < 400 && textScale > 1.3;
        final action = _action();
        return ListTile(
          leading: const Icon(Icons.link_rounded),
          title: Text(presented.name),
          subtitle: stackAction
              ? Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(subtitle),
                    if (action != null) ...[
                      const SizedBox(height: 4),
                      Align(
                        alignment: AlignmentDirectional.centerEnd,
                        child: action,
                      ),
                    ],
                  ],
                )
              : Text(subtitle),
          trailing: stackAction ? null : action,
        );
      },
    );
  }

  Widget? _action() {
    final integration = presented.integration;
    if (integration.credentialIDs.isNotEmpty) {
      if (busy) {
        return const SizedBox.square(
          dimension: 20,
          child: CircularProgressIndicator(strokeWidth: 2),
        );
      }
      return TextButton(
        key: ValueKey('disconnect-provider-${integration.id}'),
        onPressed: onDisconnect,
        child: const Text('Disconnect'),
      );
    }
    if (presented.connected) {
      return Text(
        integration.hasEnvironmentConnection
            ? 'Server\nenvironment'
            : 'Connected\n(server-managed)',
        textAlign: TextAlign.end,
      );
    }
    final canConnect = integration.methods.any(
      (method) => method.type == 'key' || method.type == 'oauth',
    );
    return TextButton(
      onPressed: !busy && canConnect ? onConnect : null,
      child: const Text('Connect'),
    );
  }
}

class _PendingIntegrationOAuth {
  final String integrationID;
  final String integrationName;
  final IntegrationAuthLaunch launch;
  final IntegrationAuthStatus? status;

  const _PendingIntegrationOAuth({
    required this.integrationID,
    required this.integrationName,
    required this.launch,
    this.status,
  });

  _PendingIntegrationOAuth copyWith({required IntegrationAuthStatus status}) =>
      _PendingIntegrationOAuth(
        integrationID: integrationID,
        integrationName: integrationName,
        launch: launch,
        status: status,
      );
}

class _PendingOAuthTile extends StatelessWidget {
  final _PendingIntegrationOAuth pending;
  final bool checking;
  final Future<void> Function() onContinue;
  final Future<void> Function() onCancel;

  const _PendingOAuthTile({
    required this.pending,
    required this.checking,
    required this.onContinue,
    required this.onCancel,
  });

  @override
  Widget build(BuildContext context) {
    final state = pending.status?.state ?? IntegrationAuthState.pending;
    final terminal =
        state == IntegrationAuthState.failed ||
        state == IntegrationAuthState.expired;
    final message = switch (state) {
      IntegrationAuthState.failed =>
        pending.status?.message ?? 'Authentication failed',
      IntegrationAuthState.expired => 'Authentication attempt expired',
      IntegrationAuthState.complete => 'Authentication complete',
      IntegrationAuthState.pending =>
        pending.launch.instructions.trim().isEmpty
            ? pending.launch.mode == IntegrationAuthMode.code
                  ? 'Return from the browser and enter the authorization code.'
                  : 'Finish authentication in the browser, then check its status.'
            : pending.launch.instructions.trim(),
    };
    final actionLabel = terminal
        ? 'Dismiss'
        : pending.launch.mode == IntegrationAuthMode.code
        ? 'Enter code'
        : 'Check';

    return ListTile(
      key: const ValueKey('pending-provider-oauth'),
      leading: checking
          ? const SizedBox.square(
              dimension: 22,
              child: CircularProgressIndicator(strokeWidth: 2),
            )
          : Icon(terminal ? Icons.error_outline_rounded : Icons.login_rounded),
      title: Text('Connecting ${pending.integrationName}'),
      subtitle: Text(message, maxLines: 3, overflow: TextOverflow.ellipsis),
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          TextButton(
            onPressed: checking
                ? null
                : terminal
                ? onCancel
                : onContinue,
            child: Text(actionLabel),
          ),
          if (!terminal)
            PopupMenuButton<void>(
              tooltip: 'Authentication options',
              itemBuilder: (context) => [
                PopupMenuItem<void>(
                  onTap: onCancel,
                  child: const ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: Icon(Icons.close_rounded),
                    title: Text('Cancel attempt'),
                  ),
                ),
              ],
            ),
        ],
      ),
    );
  }
}

class _OAuthCodeDialog extends StatefulWidget {
  final String integrationName;
  final String instructions;

  const _OAuthCodeDialog({
    required this.integrationName,
    required this.instructions,
  });

  @override
  State<_OAuthCodeDialog> createState() => _OAuthCodeDialogState();
}

class _OAuthCodeDialogState extends State<_OAuthCodeDialog> {
  final _controller = TextEditingController();

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: Text('Finish ${widget.integrationName}'),
    content: SingleChildScrollView(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (widget.instructions.trim().isNotEmpty) ...[
            Text(widget.instructions.trim()),
            const SizedBox(height: 16),
          ],
          TextField(
            key: const ValueKey('oauth-completion-code'),
            controller: _controller,
            autofocus: true,
            autocorrect: false,
            enableSuggestions: false,
            decoration: const InputDecoration(labelText: 'Authorization code'),
            onSubmitted: (_) => _complete(),
          ),
        ],
      ),
    ),
    actions: [
      TextButton(
        onPressed: () => Navigator.pop(context),
        child: const Text('Not yet'),
      ),
      FilledButton(onPressed: _complete, child: const Text('Complete')),
    ],
  );

  void _complete() {
    final value = _controller.text.trim();
    if (value.isNotEmpty) Navigator.pop(context, value);
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }
}

bool _isPromptVisible(Map<String, dynamic> prompt, Map<String, String> values) {
  final when = prompt['when'];
  if (when is! Map) return true;
  final key = when['key']?.toString();
  final expected = when['value']?.toString();
  final operation = when['op']?.toString();
  if (key == null || expected == null) return true;
  return switch (operation) {
    'eq' => values[key] == expected,
    'neq' => values[key] != expected,
    _ => true,
  };
}

bool _isPromptRequired(Map<String, dynamic> prompt) =>
    prompt['required'] != false;

class _OAuthInputsDialog extends StatefulWidget {
  final IntegrationMethodInfo method;
  const _OAuthInputsDialog({required this.method});

  @override
  State<_OAuthInputsDialog> createState() => _OAuthInputsDialogState();
}

class _OAuthInputsDialogState extends State<_OAuthInputsDialog> {
  final _formKey = GlobalKey<FormState>();
  final _textControllers = <String, TextEditingController>{};
  final _values = <String, String>{};

  @override
  void initState() {
    super.initState();
    for (final prompt in widget.method.prompts) {
      if (prompt['type'] == 'text') {
        _textControllers[prompt['key'].toString()] = TextEditingController();
      }
    }
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: Text(widget.method.label),
    content: SingleChildScrollView(
      child: Form(
        key: _formKey,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            for (final prompt in widget.method.prompts)
              if (_isPromptVisible(prompt, _values))
                Padding(
                  padding: const EdgeInsets.only(bottom: 12),
                  child: prompt['type'] == 'select'
                      ? DropdownButtonFormField<String>(
                          key: ValueKey('oauth-prompt-${prompt['key']}'),
                          initialValue: _values[prompt['key'].toString()],
                          decoration: InputDecoration(
                            labelText: prompt['message']?.toString(),
                            border: const OutlineInputBorder(),
                          ),
                          items: [
                            for (final option
                                in (prompt['options'] as List? ?? const []))
                              if (option is Map && option.containsKey('value'))
                                DropdownMenuItem(
                                  value: option['value'].toString(),
                                  child: Text(
                                    option['label']?.toString() ?? '',
                                  ),
                                ),
                          ],
                          validator: (value) =>
                              _isPromptRequired(prompt) && value == null
                              ? 'Select an option'
                              : null,
                          onChanged: (value) => setState(() {
                            final key = prompt['key'].toString();
                            if (value == null) {
                              _values.remove(key);
                            } else {
                              _values[key] = value;
                            }
                          }),
                        )
                      : TextFormField(
                          key: ValueKey('oauth-prompt-${prompt['key']}'),
                          controller:
                              _textControllers[prompt['key'].toString()],
                          decoration: InputDecoration(
                            labelText: prompt['message']?.toString(),
                            hintText: prompt['placeholder']?.toString(),
                            border: const OutlineInputBorder(),
                          ),
                          validator: (value) =>
                              _isPromptRequired(prompt) &&
                                  (value == null || value.trim().isEmpty)
                              ? 'Enter a value'
                              : null,
                          onChanged: (value) => setState(
                            () => _values[prompt['key'].toString()] = value,
                          ),
                        ),
                ),
          ],
        ),
      ),
    ),
    actions: [
      TextButton(
        onPressed: () => Navigator.pop(context),
        child: const Text('Cancel'),
      ),
      FilledButton(onPressed: _submit, child: const Text('Continue')),
    ],
  );

  void _submit() {
    if (_formKey.currentState?.validate() != true) return;
    final visibleValues = <String, String>{};
    for (final prompt in widget.method.prompts) {
      if (!_isPromptVisible(prompt, _values)) continue;
      final key = prompt['key'].toString();
      if (prompt['type'] == 'text') {
        visibleValues[key] = _textControllers[key]?.text ?? '';
      } else if (_values[key] case final value?) {
        visibleValues[key] = value;
      }
    }
    Navigator.pop(context, visibleValues);
  }

  @override
  void dispose() {
    for (final controller in _textControllers.values) {
      controller.dispose();
    }
    super.dispose();
  }
}

/// Parses a server-provided authorization URL using the app's external-launch
/// policy. Authentication is allowed only on HTTPS origins without embedded
/// credentials.
Uri parseAuthorizationUrl(String value) {
  final uri = Uri.tryParse(value.trim());
  if (uri == null ||
      uri.scheme.toLowerCase() != 'https' ||
      uri.host.trim().isEmpty ||
      uri.userInfo.isNotEmpty) {
    throw const ProductException(
      'The server returned an unsafe authorization link. '
      'Only HTTPS links with a valid host and no embedded credentials are allowed.',
    );
  }
  return uri;
}

class _SectionLoading extends StatelessWidget {
  final String label;
  const _SectionLoading({required this.label});

  @override
  Widget build(BuildContext context) => ListTile(
    leading: const SizedBox.square(
      dimension: 20,
      child: CircularProgressIndicator(strokeWidth: 2),
    ),
    title: Text(label),
  );
}

class _SectionLoadError extends StatelessWidget {
  final String message;
  final VoidCallback onRetry;
  const _SectionLoadError({required this.message, required this.onRetry});

  @override
  Widget build(BuildContext context) => ListTile(
    leading: Icon(
      Icons.error_outline_rounded,
      color: Theme.of(context).colorScheme.error,
    ),
    title: const Text('Could not load this section'),
    subtitle: Text(message, maxLines: 3, overflow: TextOverflow.ellipsis),
    trailing: TextButton(onPressed: onRetry, child: const Text('Try again')),
  );
}

class _McpStatus extends StatelessWidget {
  final String status;
  const _McpStatus({required this.status});

  @override
  Widget build(BuildContext context) {
    final color = AppTheme.statusColor(Theme.of(context), switch (status) {
      'connected' => AppStatusTone.ok,
      'failed' => AppStatusTone.failure,
      'needs_auth' || 'needs_client_registration' => AppStatusTone.attention,
      _ => AppStatusTone.neutral,
    });
    return Semantics(
      label: status.replaceAll('_', ' '),
      child: Container(
        width: 10,
        height: 10,
        decoration: BoxDecoration(color: color, shape: BoxShape.circle),
      ),
    );
  }
}
