// Under an open chat, the conversation list does not build the whole feed
// again on every change of the connection; uncovered, it shows the feed as
// it is now (2026-10-27). While a chat above it opens or closes, the rows
// hold still, so a chat shrinks back into the row it came from
// (2026-10-28).
import 'package:clock/clock.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/chat_feed.dart';
import 'package:opencode_mobile/ui/kit/kit_bidi.dart';
import 'package:opencode_mobile/ui/kit/kit_page_route.dart';
import 'package:opencode_mobile/ui/screens/chats/chats_home_screen.dart';
import 'support/chats_fakes.dart';

final _now = DateTime(2026, 10, 27, 12);

class _CountingSource extends FakeChatFeedSource {
  _CountingSource({super.items});
  int reads = 0;

  @override
  ChatFeedSnapshot chatFeed([ChatFeedFilter filter = ChatFeedFilter.all]) {
    reads += 1;
    return super.chatFeed(filter);
  }
}

class _Host extends FakeChatsHost {
  _Host(super.fake);
  final changes = ValueNotifier<int>(0);

  @override
  Listenable? get listenable => changes;
}

/// Opens a real page, as the app does: the push's future completes when
/// the page is popped, at the start of Back.
class _PushingHost extends _Host {
  _PushingHost(super.fake);

  @override
  Future<String?> openChat(BuildContext context, ChatFeedItem item) async {
    opened.add(item.sessionID);
    await pushKitPage<void>(
      context,
      (_) => const Scaffold(body: Text('Chat page')),
    );
    return null;
  }
}

void main() {
  testWidgets('a covered list waits, and catches up when it shows again', (
    tester,
  ) async {
    await withClock(Clock.fixed(_now), () async {
      final source = _CountingSource(
        items: [chat('a', 'Fix the login bug', at: _now)],
      );
      final host = _Host(source);
      addTearDown(host.changes.dispose);
      await tester.pumpWidget(chatsApp(host, const ChatsHomeScreen()));
      await tester.pump(const Duration(milliseconds: 300));
      expect(find.text(KitBidi.auto('Fix the login bug')), findsOneWidget);

      // A shown list follows every change.
      var before = source.reads;
      host.changes.value += 1;
      await tester.pump();
      expect(source.reads, greaterThan(before));

      // An open chat covers it.
      final navigator = tester.state<NavigatorState>(find.byType(Navigator));
      navigator.push(
        MaterialPageRoute<void>(builder: (_) => const SizedBox.expand()),
      );
      await tester.pumpAndSettle();
      source.items = [
        chat('a', 'Fix the login bug', at: _now),
        chat('b', 'Add dark mode', at: _now),
      ];
      before = source.reads;
      for (var i = 0; i < 5; i++) {
        host.changes.value += 1;
        await tester.pump();
      }
      expect(source.reads, before);

      navigator.pop();
      await tester.pumpAndSettle();
      expect(source.reads, greaterThan(before));
      expect(find.text(KitBidi.auto('Add dark mode')), findsOneWidget);
    });
  });

  testWidgets('Back lands on the row it came from, then the list catches up', (
    tester,
  ) async {
    await withClock(Clock.fixed(_now), () async {
      final source = _CountingSource(
        items: [
          chat('a', 'Fix the login bug', at: _now),
          chat('b', 'Add dark mode', at: _now),
        ],
      );
      final host = _PushingHost(source);
      addTearDown(host.changes.dispose);
      await tester.pumpWidget(chatsApp(host, const ChatsHomeScreen()));
      await tester.pump(const Duration(milliseconds: 300));
      final dark = find.text(KitBidi.auto('Add dark mode'));
      final login = find.text(KitBidi.auto('Fix the login bug'));
      final darkBefore = tester.getTopLeft(dark).dy;

      await tester.tap(dark);
      await tester.pumpAndSettle();
      expect(find.text('Chat page'), findsOneWidget);

      // A message was sent there: it now sorts first.
      source.items = [
        chat('b', 'Add dark mode', at: _now),
        chat('a', 'Fix the login bug', at: _now),
      ];
      host.changes.value += 1;

      Navigator.of(tester.element(find.text('Chat page'))).pop();
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));
      // While the chat closes, the rows hold still.
      expect(tester.getTopLeft(dark).dy, darkBefore);
      expect(tester.getTopLeft(login).dy, lessThan(darkBefore));

      await tester.pumpAndSettle();
      expect(tester.getTopLeft(dark).dy, lessThan(tester.getTopLeft(login).dy));
    });
  });
}
