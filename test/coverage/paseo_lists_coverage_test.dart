// Coverage ratchet for what Paseo sends the chats list (see
// paseo_coverage_support.dart for the rules). Each case is one page of
// fetch_agents_response, answered by a scripted Paseo peer to the app's real
// gateway and chats feed, and drawn by the real list and Context screens.
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/domain/chat_feed.dart';
import 'package:opencode_mobile/paseo/chat_feed_source.dart';
import 'package:opencode_mobile/paseo/gateway.dart';
import 'package:opencode_mobile/paseo/transport.dart';
import 'package:opencode_mobile/ui/screens/chats/chats_home_screen.dart';

import '../../tool/capture/fixtures.dart';
import '../goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import '../paseo_acp_pilot_test.dart' show FakePaseoSocket;
import '../support/chats_fakes.dart';
import 'lists_support.dart';
import 'paseo_coverage_support.dart';

const _directory = '/root/projects/shopfront';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  final family = CoverageFamily('lists');
  final fetched =
      <String, ({List<ChatFeedItem> items, List<Session> sessions})>{};

  setUpAll(() async {
    await loadKitGalleryFonts();
    await loadCaptureFonts();
    for (final variant in family.cases) {
      final socket = FakePaseoSocket();
      final payload = Map<String, dynamic>.from(
        shiftIsoTimes(variant['payload']) as Map,
      );
      socket.handlers['fetch_agents_request'] = (_) =>
          ('fetch_agents_response', payload);
      final gateway = PaseoGateway(
        directory: _directory,
        defaultProviderModes: const {'claude': 'default'},
        transport: PaseoTransport(
          endpoint: 'ws://100.64.0.20:6767',
          socketFactory: (_, _) async => socket,
        ),
      );
      final source = PaseoChatFeedSource(gateway);
      await source.refreshChatFeed();
      final page = await gateway.sessionPage();
      expect(
        page.nextCursor,
        (variant['payload'] as Map)['pageInfo']['hasMore'] == true
            ? 'cur_agents_p2'
            : null,
        reason:
            'the list asks for older conversations with the daemon\'s cursor',
      );
      fetched[variant['id'] as String] = (
        items: source.chatFeed().items,
        sessions: page.items,
      );
      await source.dispose();
      gateway.close();
      await socket.close();
    }
  });

  registerLedgerTests(family);

  for (final variant in family.cases) {
    final id = variant['id'] as String;
    testWidgets('paseo list · $id', (tester) async {
      tester.view.physicalSize = const Size(412, 915);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final data = fetched[id]!;
      final boundary = GlobalKey();
      final homeBoundary = GlobalKey();
      final seen = <String>[];

      final fake = FakeChatFeedSource(
        items: data.items,
        projects: [project('shopfront', chats: data.items.length, git: true)],
      );
      await tester.pumpWidget(
        RepaintBoundary(
          key: homeBoundary,
          child: chatsApp(FakeChatsHost(fake), const ChatsHomeScreen()),
        ),
      );
      await tester.pump(const Duration(milliseconds: 300));
      seen.add(screenText(tester).join('\n'));
      await writeCasePng(tester, homeBoundary, 'lists-paseo-$id-1');

      // The Context page of the first conversation: its cost is the only
      // usage Paseo reports in a form the page can use.
      if (data.sessions.isNotEmpty) {
        final controller = await listsController(sessions: data.sessions);
        final screens = ListsScreens(
          tester,
          controller,
          boundary,
          'paseo-$id-ctx',
        );
        await screens.contextOnly(data.sessions.first);
        seen.add(screens.text);
      }
      File(
        'build/coverage/paseolist_$id.txt',
      ).writeAsStringSync(flat(seen.join('\n')));
      final problems = checkCase(family, variant, seen.join('\n'));
      expect(
        problems,
        isEmpty,
        reason: 'screen text:\n${flat(seen.join('\n'))}',
      );
    });
  }
}
