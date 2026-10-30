// The permission request after chat-5: the retired PermissionSheet widget
// draws the one request card (Allow once and Reject in place), a reply is
// sent once and leaves a receipt, a refusal says so and gives the answers
// back, and Details opens the one request sheet with the whole command, the
// change as a diff, and "Always allow" as a risky switch that states its
// scope before anything is sent.
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/ui/kit/kit_diff_view.dart';
import 'package:opencode_mobile/ui/kit/kit_request_card.dart';
import 'package:opencode_mobile/ui/screens/chat/permission_sheet.dart';
import 'support/plain_text_finder.dart';

PermissionRequest _permission({
  String permission = 'bash',
  List<String> patterns = const ['git push origin main'],
  List<String> always = const [],
  Map<String, dynamic> metadata = const {},
}) => PermissionRequest(
  id: 'per_1',
  sessionID: 'ses_1',
  permission: permission,
  patterns: patterns,
  always: always,
  metadata: metadata,
);

Future<void> _pump(
  WidgetTester tester, {
  required PermissionRequest permission,
  required Future<void> Function(String reply, {String? message}) onReply,
  bool allowPersistentPermission = true,
  double textScale = 1,
}) async {
  await tester.pumpWidget(
    MaterialApp(
      builder: (context, child) => MediaQuery(
        data: MediaQuery.of(context).copyWith(
          textScaler: TextScaler.linear(textScale),
          disableAnimations: true,
        ),
        child: child!,
      ),
      home: Scaffold(
        body: ListView(
          children: [
            PermissionSheet(
              permission: permission,
              onReply: onReply,
              supportsRejectMessage: true,
              allowPersistentPermission: allowPersistentPermission,
            ),
          ],
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

void main() {
  for (final scale in [1.0, 2.5]) {
    testWidgets('Allow once and Reject sit in place on the card at ${scale}x', (
      tester,
    ) async {
      await tester.binding.setSurfaceSize(const Size(320, 740));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      await _pump(
        tester,
        permission: _permission(),
        onReply: (reply, {message}) async {},
        textScale: scale,
      );
      final card = tester.widget<KitRequestCard>(find.byType(KitRequestCard));
      expect(card.kind, KitRequestKind.permission);
      expect(card.summary, 'git push origin main');
      for (final key in ['permission-card-allow', 'permission-card-reject']) {
        final action = find.byKey(Key(key));
        expect(action.hitTestable(), findsOneWidget, reason: key);
        expect(tester.getSize(action).height, greaterThanOrEqualTo(48));
      }
      // "Always allow" is never on the card (KIT-30).
      expect(find.text('Always allow'), findsNothing);
      expect(tester.takeException(), isNull);
    });
  }

  testWidgets('a reply is sent once and shows Sending until it lands', (
    tester,
  ) async {
    final replies = <String>[];
    final reply = Completer<void>();
    await _pump(
      tester,
      permission: _permission(),
      onReply: (value, {message}) {
        replies.add(value);
        return reply.future;
      },
    );
    await tester.tap(find.byKey(const Key('permission-card-allow')));
    await tester.pump();
    expect(find.byKey(const Key('permission-card-allow')), findsNothing);
    expect(find.textContaining('Sending'), findsOneWidget);
    expect(replies, ['once']);
    reply.complete();
    await tester.pumpAndSettle();
    expect(replies, ['once']);
  });

  testWidgets('a failed reply says Not accepted and gives the answers back', (
    tester,
  ) async {
    await _pump(
      tester,
      permission: _permission(),
      onReply: (reply, {message}) async =>
          throw Exception('the server refused'),
    );
    await tester.tap(find.byKey(const Key('permission-card-reject')));
    await tester.pumpAndSettle();
    expect(find.textContaining('Not accepted'), findsOneWidget);
    expect(find.byKey(const Key('permission-card-allow')), findsOneWidget);
  });

  testWidgets('Details shows the whole command; Always allow states its '
      'scope before it sends', (tester) async {
    final replies = <String>[];
    await _pump(
      tester,
      permission: _permission(always: ['git push *']),
      onReply: (reply, {message}) async => replies.add(reply),
    );
    await tester.tap(find.byKey(const Key('permission-card-review')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('permission-sheet')), findsOneWidget);
    expect(find.textContaining('git push origin main'), findsWidgets);

    await tester.tap(find.byKey(const Key('permission-allow-always')));
    await tester.pumpAndSettle();
    expect(find.textContaining('git push *'), findsWidgets);
    expect(replies, isEmpty);
    await tester.ensureVisible(find.text('Turn on'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Turn on'));
    await tester.pumpAndSettle();
    expect(replies, ['always']);
  });

  testWidgets('no Always allow where the server keeps no saved grants', (
    tester,
  ) async {
    await _pump(
      tester,
      permission: _permission(),
      onReply: (reply, {message}) async {},
      allowPersistentPermission: false,
    );
    await tester.tap(find.byKey(const Key('permission-card-review')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('permission-sheet')), findsOneWidget);
    expect(find.byKey(const Key('permission-allow-always')), findsNothing);
  });

  testWidgets('an edit ask shows its change as a read-only diff in Details', (
    tester,
  ) async {
    await _pump(
      tester,
      permission: _permission(
        permission: 'edit',
        patterns: const ['lib/main.dart'],
        metadata: const {
          'filePath': 'lib/main.dart',
          'diff': '@@ -1,1 +1,1 @@\n-old line\n+new line\n',
        },
      ),
      onReply: (reply, {message}) async {},
    );
    expect(findPlainText('Edit main.dart?'), findsOneWidget);
    await tester.tap(find.byKey(const Key('permission-card-review')));
    await tester.pumpAndSettle();
    expect(find.byType(KitDiffView), findsOneWidget);
  });
}
