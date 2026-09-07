import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../l10n/app_localizations.dart';
import '../../platform/connection_advice.dart';

/// Visit-scoped text only: no profile, repository, transport or persistence.
class ConnectionHelpScreen extends StatefulWidget {
  const ConnectionHelpScreen({super.key});

  @override
  State<ConnectionHelpScreen> createState() => _ConnectionHelpScreenState();
}

class _ConnectionHelpScreenState extends State<ConnectionHelpScreen> {
  final _address = TextEditingController();
  ConnectionAdvice? _advice;

  @override
  void dispose() {
    _address.clear();
    _address.dispose();
    super.dispose();
  }

  void _explain() {
    final advice = explainConnectionAddress(_address.text);
    _address.clear();
    setState(() => _advice = advice);
  }

  String _explanation(AppLocalizations l10n, ConnectionAdvice advice) =>
      switch (advice) {
        ConnectionAdvice.empty => l10n.connectionHelpEmpty,
        ConnectionAdvice.malformed => l10n.connectionHelpMalformed,
        ConnectionAdvice.credentials => l10n.connectionHelpCredentials,
        ConnectionAdvice.queryOrFragment => l10n.connectionHelpQuery,
        ConnectionAdvice.path => l10n.connectionHelpPath,
        ConnectionAdvice.unsupportedScheme => l10n.connectionHelpScheme,
        ConnectionAdvice.remoteHttp => l10n.connectionHelpRemoteHttp,
        ConnectionAdvice.https => l10n.connectionHelpHttps,
        ConnectionAdvice.loopback => l10n.connectionHelpLoopback,
      };

  @override
  Widget build(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final theme = Theme.of(context);
    return Scaffold(
      appBar: AppBar(title: Text(l10n.connectionHelpTitle)),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text(l10n.connectionHelpPrivacy),
          const SizedBox(height: 16),
          TextField(
            controller: _address,
            decoration: InputDecoration(
              labelText: l10n.connectionHelpAddress,
              border: const OutlineInputBorder(),
            ),
            // Paste remains available; secrets are hidden and not offered to
            // keyboard learning/autofill. No automatic clipboard reads.
            obscureText: true,
            autocorrect: false,
            enableSuggestions: false,
            enableIMEPersonalizedLearning: false,
            autofillHints: const [],
            keyboardType: TextInputType.url,
            textInputAction: TextInputAction.done,
            onChanged: (_) {
              if (_advice != null) setState(() => _advice = null);
            },
            onSubmitted: (_) => _explain(),
          ),
          const SizedBox(height: 12),
          FilledButton(
            onPressed: _explain,
            child: Text(l10n.connectionHelpCheck),
          ),
          if (_advice case final advice?) ...[
            const SizedBox(height: 16),
            Semantics(
              liveRegion: true,
              child: Text(
                _explanation(l10n, advice),
                style: theme.textTheme.bodyLarge,
              ),
            ),
          ],
          const SizedBox(height: 24),
          Text(
            l10n.connectionHelpPrivateTitle,
            style: theme.textTheme.titleMedium,
          ),
          const SizedBox(height: 8),
          Text(l10n.connectionHelpPrivateSteps),
          _example(context, 'https://server.example'),
          const SizedBox(height: 16),
          Text(
            l10n.connectionHelpTunnelTitle,
            style: theme.textTheme.titleMedium,
          ),
          const SizedBox(height: 8),
          Text(l10n.connectionHelpTunnelSteps),
          _example(
            context,
            'ssh -N -L 127.0.0.1:4096:127.0.0.1:4096 user@host',
          ),
          _example(context, 'http://127.0.0.1:4096'),
          const SizedBox(height: 16),
          Text(
            l10n.connectionHelpVerifyTitle,
            style: theme.textTheme.titleMedium,
          ),
          const SizedBox(height: 8),
          Text(l10n.connectionHelpVerifySteps),
        ],
      ),
    );
  }

  // All callers pass app-authored literals, never input-derived values.
  Widget _example(BuildContext context, String example) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    return Padding(
      padding: const EdgeInsets.only(top: 8),
      child: Row(
        children: [
          Expanded(child: SelectableText(example)),
          IconButton(
            tooltip: l10n.connectionHelpCopyExample,
            icon: const Icon(Icons.copy_rounded),
            onPressed: () async {
              var copied = false;
              try {
                await Clipboard.setData(ClipboardData(text: example));
                copied = true;
              } on PlatformException {
                // Never surface platform error details or clipboard contents.
              } on MissingPluginException {
                // Unsupported platform: report failure, not false success.
              }
              if (!context.mounted) return;
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(
                  content: Text(
                    copied
                        ? l10n.connectionHelpCopied
                        : l10n.connectionHelpCopyFailed,
                  ),
                ),
              );
            },
          ),
        ],
      ),
    );
  }
}
