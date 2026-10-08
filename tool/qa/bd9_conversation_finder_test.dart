import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit_feed_item.dart';
import 'package:opencode_mobile/ui/kit/kit_motion.dart';
import 'package:opencode_mobile/ui/kit/motion/kit_refresh.dart';

import '../../integration_test/bd9_conversation_finder.dart';
import '../../integration_test/bd9_device_smoke_fixture.dart';

void main() {
  late bool loops;
  setUp(() {
    loops = KitMotion.loops;
    KitMotion.loops = false;
  });
  tearDown(() => KitMotion.loops = loops);

  testWidgets('release smoke finds and opens the visible kit conversation', (
    tester,
  ) async {
    var opened = false;
    await tester.pumpWidget(
      MaterialApp(
        theme: AppTheme.light(),
        home: Scaffold(
          body: KitFeedItem(
            project: 'BD9 project',
            title: Bd9DeviceSmokeFixture.title,
            onTap: () => opened = true,
          ),
        ),
      ),
    );
    expect(bd9ConversationFinder(), findsOneWidget);
    await tester.tap(bd9ConversationFinder());
    expect(opened, isTrue);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('release smoke pulls the visible list to refresh its inventory', (
    tester,
  ) async {
    var refreshes = 0;
    await tester.pumpWidget(
      MaterialApp(
        theme: AppTheme.light(),
        home: Scaffold(
          body: KitRefresh(
            onRefresh: () async => refreshes++,
            child: ListView(
              physics: const AlwaysScrollableScrollPhysics(),
              children: [
                KitFeedItem(
                  project: 'BD9 project',
                  title: Bd9DeviceSmokeFixture.title,
                  onTap: () {},
                ),
              ],
            ),
          ),
        ),
      ),
    );
    await bd9RefreshConversations(tester);
    await tester.pump(const Duration(milliseconds: 500));
    expect(refreshes, 1);
    expect(bd9ConversationFinder(), findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
  });
}
