import 'package:flutter/material.dart';

import '../../../domain/chat_feed.dart';
import '../../kit/kit_screen.dart';
import '../../kit/kit_text.dart';

// STUB: replaced by feat/chats-first-home

/// The Chats tab: every conversation across projects, with the "Needs you"
/// and "Running" filter chips.
///
/// [initialFilter] is what the shell asks the list to open on: a notification,
/// the Quick Settings tile or a widget that used to open Inbox passes
/// `ChatFeedFilter(needsYou: true)` (or `running: true`). Null means the
/// plain list. The shell re-creates this widget with a new key when a new
/// request arrives while the tab is showing, so reading it in `initState` is
/// enough.
class ChatsHomeScreen extends StatefulWidget {
  const ChatsHomeScreen({super.key, this.initialFilter});

  final ChatFeedFilter? initialFilter;

  @override
  State<ChatsHomeScreen> createState() => _ChatsHomeScreenState();
}

class _ChatsHomeScreenState extends State<ChatsHomeScreen> {
  @override
  Widget build(BuildContext context) => const KitScreen(
    body: Center(child: KitText('Chats', role: KitTextRole.largeTitle)),
  );
}
