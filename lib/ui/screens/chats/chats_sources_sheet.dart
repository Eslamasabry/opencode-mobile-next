import 'dart:async';

import 'package:flutter/material.dart';

import '../../../domain/chat_feed.dart';
import '../../../l10n/app_localizations.dart';
import '../../app_iconography.dart';
import '../../kit/kit.dart';
import '../agents/agents_text.dart' show agentIcon;

/// "Show conversations from": one switch per connection the list can show
/// (each saved OpenCode server, the agents on this phone). The connection
/// the app is on is always shown; servers elsewhere are off until turned on.
Future<void> showChatsSourcesSheet(
  BuildContext context, {
  required ChatListSources sources,
  required String Function(ChatListSource source) nameOf,
}) {
  final l10n = AppLocalizations.of(context);
  return showKitSheet<void>(
    context,
    sheetKey: const ValueKey('chats-sources-sheet'),
    title: l10n.chatsSourcesSheetTitle,
    body: (sheetContext) => StatefulBuilder(
      builder: (context, setState) {
        String where(ChatListSource source) => source.main
            ? l10n.chatsSourcesMain
            : source.kind == ChatListSourceKind.agents
            ? l10n.chatsSourcesAgents
            : source.onThisPhone
            ? l10n.chatsSourcesOnPhone
            : l10n.chatsSourcesElsewhere;
        return KitRowGroup(
          margin: EdgeInsets.zero,
          children: [
            for (final source in sources.chatListSources)
              KitSwitchRow(
                key: ValueKey('chats-source-${source.id}'),
                title: KitBidi.auto(nameOf(source)),
                supporting: where(source),
                leading: KitRowIcon(
                  source.kind == ChatListSourceKind.agents
                      ? agentIcon('claude')
                      : source.onThisPhone
                      ? AppIconography.phone
                      : AppIconography.server,
                ),
                value: source.shown,
                locked: source.main ? l10n.chatsSourcesAlways : null,
                onChanged: source.main
                    ? null
                    : (on) => unawaited(
                        sources
                            .setChatListSourceShown(source.id, on)
                            .whenComplete(() {
                              if (context.mounted) setState(() {});
                            }),
                      ),
              ),
          ],
        );
      },
    ),
  );
}
