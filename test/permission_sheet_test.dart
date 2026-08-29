import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/ui/screens/chat/permission_sheet.dart';

PermissionRequest _permission({
  List<String> patterns = const ['git push origin main'],
  List<String> always = const [],
  String? message,
  PermissionTool? tool,
}) => PermissionRequest(
  id: 'per_1',
  sessionID: 'ses_1',
  permission: 'bash',
  patterns: patterns,
  always: always,
  message: message,
  tool: tool,
);

Future<void> _pump(
  WidgetTester tester, {
  required PermissionRequest permission,
  required Future<void> Function(String reply, {String? message}) onReply,
  bool supportsRejectMessage = true,
  VoidCallback? onShowSource,
}) async {
  await tester.pumpWidget(
    MaterialApp(
      home: Scaffold(
        body: PermissionSheet(
          permission: permission,
          onReply: onReply,
          supportsRejectMessage: supportsRejectMessage,
          onShowSource: onShowSource,
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('renders the triad, resources card, and context line', (
    tester,
  ) async {
    final replies = <(String, String?)>[];
    await _pump(
      tester,
      permission: _permission(message: 'Pushing the release branch'),
      onReply: (reply, {message}) async => replies.add((reply, message)),
    );

    expect(find.byKey(const Key('permission-sheet')), findsOneWidget);
    expect(find.byKey(const Key('permission-resources')), findsOneWidget);
    expect(find.text('Run a shell command'), findsOneWidget);
    expect(find.text('Pushing the release branch'), findsOneWidget);
    expect(find.text('git push origin main'), findsOneWidget);
    expect(find.byKey(const Key('permission-apply-bar')), findsOneWidget);
    expect(find.byKey(const Key('permission-allow-once')), findsOneWidget);
    expect(find.byKey(const Key('permission-allow-always')), findsOneWidget);
    expect(find.byKey(const Key('permission-reject')), findsOneWidget);
    // The keyboard-bearing reject field only exists after Reject… is tapped.
    expect(find.byKey(const Key('permission-reject-message')), findsNothing);

    await tester.tap(find.byKey(const Key('permission-allow-once')));
    await tester.pumpAndSettle();
    expect(replies, [('once', null)]);
  });

  testWidgets('Reject expands inline and sends the typed message', (
    tester,
  ) async {
    final replies = <(String, String?)>[];
    await _pump(
      tester,
      permission: _permission(),
      onReply: (reply, {message}) async => replies.add((reply, message)),
    );

    await tester.tap(find.byKey(const Key('permission-reject')));
    await tester.pumpAndSettle();

    // The triad swaps for the reject pane; nothing was submitted yet.
    expect(replies, isEmpty);
    expect(find.byKey(const Key('permission-reject-message')), findsOneWidget);
    expect(find.byKey(const Key('permission-reject-send')), findsOneWidget);
    expect(find.byKey(const Key('permission-allow-once')), findsNothing);

    await tester.enterText(
      find.byKey(const Key('permission-reject-message')),
      'use the staging remote instead',
    );
    await tester.tap(find.byKey(const Key('permission-reject-send')));
    await tester.pumpAndSettle();
    expect(replies, [('reject', 'use the staging remote instead')]);
  });

  testWidgets('an empty reject reason is valid and Back restores the triad', (
    tester,
  ) async {
    final replies = <(String, String?)>[];
    await _pump(
      tester,
      permission: _permission(),
      onReply: (reply, {message}) async => replies.add((reply, message)),
    );

    await tester.tap(find.byKey(const Key('permission-reject')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Back'));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('permission-reject-message')), findsNothing);
    expect(find.byKey(const Key('permission-allow-once')), findsOneWidget);
    expect(replies, isEmpty);

    await tester.tap(find.byKey(const Key('permission-reject')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('permission-reject-send')));
    await tester.pumpAndSettle();
    expect(replies, [('reject', null)]);
  });

  testWidgets('v1 fallback: Reject submits directly with no message field', (
    tester,
  ) async {
    final replies = <(String, String?)>[];
    await _pump(
      tester,
      permission: _permission(),
      supportsRejectMessage: false,
      onReply: (reply, {message}) async => replies.add((reply, message)),
    );

    expect(find.text('Reject'), findsOneWidget);
    expect(find.text('Reject…'), findsNothing);
    await tester.tap(find.byKey(const Key('permission-reject')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('permission-reject-message')), findsNothing);
    expect(replies, [('reject', null)]);
  });

  testWidgets('Always allow keeps the two-step confirmation', (tester) async {
    final replies = <(String, String?)>[];
    await _pump(
      tester,
      permission: _permission(always: ['git push*']),
      onReply: (reply, {message}) async => replies.add((reply, message)),
    );

    expect(find.text('Always allow would also cover'), findsOneWidget);
    await tester.tap(find.byKey(const Key('permission-allow-always')));
    await tester.pumpAndSettle();
    expect(find.text('Confirm broader access'), findsOneWidget);
    expect(
      find.textContaining('Manage saved grants in Settings'),
      findsOneWidget,
    );
    expect(replies, isEmpty);
    await tester.tap(find.text('Confirm always allow'));
    await tester.pumpAndSettle();
    expect(replies, [('always', null)]);
  });

  testWidgets('the source chip appears only for tool-sourced requests', (
    tester,
  ) async {
    var shown = 0;
    await _pump(
      tester,
      permission: _permission(
        tool: PermissionTool(messageID: 'msg_1', callID: 'call_1'),
      ),
      onReply: (reply, {message}) async {},
      onShowSource: () => shown += 1,
    );
    expect(find.byKey(const Key('permission-source-chip')), findsOneWidget);

    await _pump(
      tester,
      permission: _permission(),
      onReply: (reply, {message}) async {},
      onShowSource: () => shown += 1,
    );
    expect(find.byKey(const Key('permission-source-chip')), findsNothing);
    expect(shown, 0);
  });

  testWidgets('a failed reply keeps the sheet open with the error inline', (
    tester,
  ) async {
    await _pump(
      tester,
      permission: _permission(),
      onReply: (reply, {message}) async =>
          throw Exception('server refused the reply'),
    );

    await tester.tap(find.byKey(const Key('permission-allow-once')));
    await tester.pumpAndSettle();
    expect(find.textContaining('Reply failed:'), findsOneWidget);
    expect(find.byKey(const Key('permission-allow-once')), findsOneWidget);
  });
}
