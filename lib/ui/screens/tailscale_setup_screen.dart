import 'dart:async';

import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import '../../platform/tailscale.dart';
import '../../state/tailscale_address.dart';
import '../widgets/external_link.dart';

/// User-controlled app handoff, then an address review. Returning a URL does
/// not probe, save, authenticate or assert that Tailscale is connected.
class TailscaleSetupScreen extends StatefulWidget {
  const TailscaleSetupScreen({
    super.key,
    this.initialAddress = '',
    this.bridge = const TailscaleBridge(),
  });
  final String initialAddress;
  final TailscaleBridge bridge;

  @override
  State<TailscaleSetupScreen> createState() => _TailscaleSetupScreenState();
}

class _TailscaleSetupScreenState extends State<TailscaleSetupScreen>
    with WidgetsBindingObserver {
  late final _address = TextEditingController(text: widget.initialAddress);
  TailscaleAppState? _state;
  bool _checking = false,
      _opening = false,
      _returned = false,
      _openFailed = false;
  bool _addressError = false;
  int _generation = 0;
  AppLocalizations get l10n =>
      lookupAppLocalizations(Localizations.localeOf(context));

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    unawaited(_check());
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      setState(() => _returned = true);
      unawaited(_check());
    }
  }

  Future<void> _check() async {
    final generation = ++_generation;
    setState(() => _checking = true);
    final result = await widget.bridge.check();
    if (!mounted || generation != _generation) return;
    setState(() {
      _state = result;
      _checking = false;
    });
  }

  Future<void> _open() async {
    if (_opening) return;
    setState(() {
      _opening = true;
      _openFailed = false;
    });
    final opened = await widget.bridge.open();
    if (!mounted) return;
    setState(() {
      _opening = false;
      _openFailed = !opened;
    });
    if (!opened) await _check();
  }

  void _continue() {
    if (!isValidTailscaleAddress(_address.text)) {
      setState(() => _addressError = true);
      return;
    }
    Navigator.of(context).pop(normalizeTailscaleAddress(_address.text));
  }

  @override
  void dispose() {
    _generation++;
    WidgetsBinding.instance.removeObserver(this);
    _address.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final strings = l10n;
    return Scaffold(
      appBar: AppBar(title: Text(strings.tailscaleTitle)),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            Text(strings.tailscaleIntro),
            const SizedBox(height: 16),
            Card.filled(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Text(
                      strings.tailscaleAppStep,
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                    const SizedBox(height: 8),
                    Text(
                      _checking
                          ? strings.tailscaleChecking
                          : switch (_state) {
                              TailscaleAppState.installed =>
                                strings.tailscaleInstalled,
                              TailscaleAppState.missing =>
                                strings.tailscaleMissing,
                              TailscaleAppState.unsupported =>
                                strings.tailscaleUnsupported,
                              _ => strings.tailscaleUnknown,
                            },
                    ),
                    const SizedBox(height: 8),
                    Text(strings.tailscaleVpnHandoff),
                    if (_returned) ...[
                      const SizedBox(height: 8),
                      Text(strings.tailscaleReturned),
                    ],
                    if (_openFailed) ...[
                      const SizedBox(height: 8),
                      Text(strings.tailscaleOpenFailed),
                    ],
                    const SizedBox(height: 12),
                    if (_state == TailscaleAppState.installed)
                      FilledButton.icon(
                        onPressed: _opening ? null : _open,
                        icon: const Icon(Icons.open_in_new),
                        label: Text(strings.tailscaleOpen),
                      ),
                    if (_state != TailscaleAppState.installed)
                      OutlinedButton.icon(
                        onPressed: () => openExternalLink(
                          context,
                          'https://play.google.com/store/apps/details?id=com.tailscale.ipn',
                        ),
                        icon: const Icon(Icons.download_outlined),
                        label: Text(strings.tailscaleInstall),
                      ),
                    TextButton(
                      onPressed: _checking ? null : _check,
                      child: Text(strings.tailscaleCheckAgain),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 12),
            Text(
              strings.tailscaleAddressStep,
              style: Theme.of(context).textTheme.titleMedium,
            ),
            const SizedBox(height: 8),
            Text(strings.tailscaleAddressDetail),
            const SizedBox(height: 12),
            TextField(
              controller: _address,
              maxLength: 2048,
              autocorrect: false,
              keyboardType: TextInputType.url,
              textInputAction: TextInputAction.done,
              onSubmitted: (_) => _continue(),
              onChanged: (_) {
                if (_addressError) setState(() => _addressError = false);
              },
              decoration: InputDecoration(
                labelText: strings.tailscaleAddressLabel,
                hintText: 'https://computer.tailnet-name.ts.net',
                errorText: _addressError ? strings.tailscaleAddressError : null,
              ),
            ),
            Text(strings.tailscaleReviewDetail),
            const SizedBox(height: 12),
            FilledButton.icon(
              onPressed: _continue,
              icon: const Icon(Icons.arrow_forward),
              label: Text(strings.tailscaleContinue),
            ),
            const SizedBox(height: 16),
            ExpansionTile(
              tilePadding: EdgeInsets.zero,
              title: Text(strings.tailscaleHelp),
              children: [
                Text(strings.tailscaleServeHelp),
                TextButton(
                  onPressed: () => openExternalLink(
                    context,
                    'https://tailscale.com/docs/features/tailscale-serve',
                  ),
                  child: Text(strings.tailscaleServeDocs),
                ),
                Text(strings.tailscaleRecovery),
                TextButton(
                  onPressed: () => openExternalLink(
                    context,
                    'https://tailscale.com/docs/install/android',
                  ),
                  child: Text(strings.tailscaleAndroidDocs),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
