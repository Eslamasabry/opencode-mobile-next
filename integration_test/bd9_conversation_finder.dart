import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/kit/kit_bidi.dart';
import 'package:opencode_mobile/ui/kit/motion/kit_refresh.dart';

import 'bd9_device_smoke_fixture.dart';

/// Match the visible kit title, including its bidirectional text isolation.
Finder bd9ConversationFinder() =>
    find.text(KitBidi.auto(Bd9DeviceSmokeFixture.title));

/// Exercise the visible list's normal pull-to-refresh action, not a transport.
Future<void> bd9RefreshConversations(WidgetTester tester) async {
  final refresh = find.byType(KitRefresh);
  expect(refresh, findsOneWidget);
  final size = tester.getSize(refresh);
  await tester.dragFrom(
    tester.getTopLeft(refresh) + Offset(size.width / 2, size.height / 4),
    Offset(0, size.height / 2),
  );
  await tester.pump();
}
