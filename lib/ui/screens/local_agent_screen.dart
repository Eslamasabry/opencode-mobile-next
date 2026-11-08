import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import '../kit/kit.dart';
import 'agents/agents_section.dart';

/// The agents on this phone (Claude Code and others), on their own page
/// (docs/design/phone-server-screens-cleanup-2026-09-24.md §2): the phone
/// server's details show it as one optional row, and everything about the
/// agents installed in the app's own Ubuntu lives here, the same rows
/// Settings > Agents shows.
///
/// Built from kit parts only (screen-phone-1): a [KitScreen] page with its
/// [KitTopBar] and the agents section.
// revamp: redesign (slice-P1.6b)
class LocalAgentScreen extends StatelessWidget {
  const LocalAgentScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final tokens = KitTokens.of(context);
    return KitScreen(
      topBar: KitTopBar(title: l10n.agentsSectionTitle),
      width: KitScreenWidth.reading,
      body: ListView(
        key: const ValueKey('local-agent-managed'),
        padding: EdgeInsetsDirectional.only(
          top: tokens.space2,
          bottom: KitScreen.endPadding(context),
        ),
        children: const [AgentsSection()],
      ),
    );
  }
}
