import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../demo/demo_copy.dart';
import '../../demo/demo_gateway.dart';
import '../../demo/demo_store.dart';
import '../../l10n/app_localizations.dart';
import '../../state/connection.dart';
import '../../state/review_handoff.dart';
import 'chat_screen.dart';

/// Production chat backed by a route-owned gateway and ephemeral stores.
/// Nothing replaces the real connection, profile store or plugin singleton.
class DemoScreen extends StatefulWidget {
  const DemoScreen({super.key});

  @override
  State<DemoScreen> createState() => _DemoScreenState();
}

class _DemoScreenState extends State<DemoScreen> {
  late DemoGateway _gateway;
  late DemoProfileStore _store;
  late ConnectionController _controller;
  late ReviewHandoffStore _handoff;
  var _generation = 0;

  @override
  void initState() {
    super.initState();
    _create();
  }

  void _create() {
    _gateway = DemoGateway();
    _store = DemoProfileStore();
    _handoff = ReviewHandoffStore();
    _controller =
        ConnectionController.isolated(
            _store,
            gateway: _gateway,
            operations: _gateway,
            profile: _store.profiles.single,
          )
          ..sessionsById = {DemoGateway.sessionID: DemoGateway.sampleSession}
          ..catalog = DemoGateway.catalog
          ..selectedModel = DemoGateway.model;
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _gateway.reducedMotion =
        MediaQuery.disableAnimationsOf(context) ||
        MediaQuery.accessibleNavigationOf(context);
  }

  void _reset() {
    final previous = _controller;
    final previousStore = _store;
    _gateway.close();
    setState(() {
      _generation++;
      _create();
      _gateway.reducedMotion =
          MediaQuery.disableAnimationsOf(context) ||
          MediaQuery.accessibleNavigationOf(context);
    });
    // Let the old chat remove its listeners before releasing its controller.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      previous.dispose();
      unawaited(previousStore.prefs.clear());
    });
  }

  void _exit() {
    _gateway.close();
    Navigator.of(context).pop();
  }

  @override
  void dispose() {
    _gateway.close();
    _controller.dispose();
    unawaited(_store.prefs.clear());
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    // The production chat owns keyboard avoidance; avoid subtracting it twice.
    resizeToAvoidBottomInset: false,
    body: SafeArea(
      bottom: false,
      child: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 0, 4, 0),
            child: Row(
              children: [
                const Icon(Icons.science_outlined, size: 20),
                const SizedBox(width: 8),
                const Expanded(child: Text(DemoCopy.title)),
                IconButton(
                  tooltip: DemoCopy.reset,
                  onPressed: _reset,
                  icon: const Icon(Icons.restart_alt_rounded),
                ),
                IconButton(
                  tooltip: DemoCopy.exit,
                  onPressed: _exit,
                  icon: const Icon(Icons.close_rounded),
                ),
              ],
            ),
          ),
          if (MediaQuery.viewInsetsOf(context).bottom == 0)
            const Padding(
              padding: EdgeInsets.fromLTRB(16, 0, 16, 8),
              child: Text(DemoCopy.disclosure),
            ),
          AnimatedBuilder(
            animation: _controller,
            builder: (context, _) =>
                _gateway.hasFinished &&
                    MediaQuery.viewInsetsOf(context).bottom == 0
                ? TextButton(
                    onPressed: _exit,
                    child: Text(
                      lookupAppLocalizations(
                        Localizations.localeOf(context),
                      ).demoSetUpServer,
                    ),
                  )
                : const SizedBox.shrink(),
          ),
          Expanded(
            child: ProviderScope(
              key: ValueKey(_generation),
              overrides: [
                connProvider.overrideWithValue(_controller),
                bootstrapProvider.overrideWithValue(AppBootstrap(_store)),
              ],
              child: ChatScreen(
                sessionID: DemoGateway.sessionID,
                initialText: DemoCopy.prompt,
                handoffStore: _handoff,
              ),
            ),
          ),
        ],
      ),
    ),
  );
}
