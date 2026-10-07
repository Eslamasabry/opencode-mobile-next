import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../l10n/app_localizations.dart';
import '../../app_iconography.dart';
import '../../kit/kit.dart';
import '../chats/chats_host.dart';
import 'agents_section.dart';
import 'cards_from_agents_row.dart';

/// Settings › Agents: the agents this phone can run, where they stand and
/// the one act each needs, and Check this phone. Where agents cannot run
/// (a remote server, Termux) it says so in one line and offers the switch to
/// the built-in server when this phone has one, so the person is never left
/// with an empty page.
class AgentsScreen extends ConsumerWidget {
  const AgentsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final host = ref.watch(chatsHostProvider);
    final l10n = AppLocalizations.of(context);
    final tokens = KitTokens.of(context);
    return KitScreen(
      topBar: KitTopBar(title: l10n.agentsSectionTitle),
      width: KitScreenWidth.reading,
      body: ListenableBuilder(
        listenable: host.listenable ?? ValueNotifier<int>(0),
        builder: (context, _) {
          final available = host.agents?.phoneAgentsAvailable ?? false;
          return ListView(
            key: const ValueKey('agents-screen'),
            padding: EdgeInsetsDirectional.only(
              top: tokens.space2,
              bottom: KitScreen.endPadding(context),
            ),
            children: [
              if (available) ...const [
                AgentsSection(),
                CardsFromAgentsRow(),
              ] else
                KitStateView(
                  key: const ValueKey('agents-unavailable'),
                  icon: AppIconography.phone,
                  title: l10n.agentsRunOnBuiltIn,
                  primary: host.hasBuiltInProfile
                      ? KitAction(
                          key: const ValueKey('agents-switch-builtin'),
                          label: l10n.agentsSwitchToBuiltIn,
                          onPressed: () =>
                              unawaited(host.switchToBuiltIn(context)),
                        )
                      : null,
                ),
            ],
          );
        },
      ),
    );
  }
}
