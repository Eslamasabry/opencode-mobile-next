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
  _previewTests();
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
    // The bash pattern is the command, so it renders once as the highlighted
    // command preview (with its own Copy) rather than again as a resource row.
    expect(find.byKey(const Key('permission-command-preview')), findsOneWidget);
    expect(find.byKey(const Key('permission-resources')), findsNothing);
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

// ---------------------------------------------------------------------------
// Previews from the widened PermissionRequest: command, file path, diff.
// ---------------------------------------------------------------------------

void _previewTests() {
  testWidgets('bash asks render the command in a highlighted mono block', (
    tester,
  ) async {
    await _pump(
      tester,
      permission: PermissionRequest(
        id: 'per_2',
        sessionID: 'ses_1',
        permission: 'bash',
        patterns: const ['*'],
        metadata: const {'command': 'git push origin main | tee log.txt'},
      ),
      onReply: (reply, {message}) async {},
    );
    expect(find.byKey(const Key('permission-command-preview')), findsOneWidget);
    expect(
      find.textContaining('git push origin main | tee log.txt'),
      findsOneWidget,
    );
    // The wildcard pattern is not the command, so the resources card stays.
    expect(find.byKey(const Key('permission-resources')), findsOneWidget);
    expect(find.byKey(const Key('permission-file-path')), findsNothing);
    expect(find.byKey(const Key('permission-diff-preview')), findsNothing);
  });

  testWidgets('edit asks show the file path and a bounded diff preview', (
    tester,
  ) async {
    const patch = '@@ -1 +1 @@\n-old line\n+new line';
    await _pump(
      tester,
      permission: PermissionRequest(
        id: 'per_3',
        sessionID: 'ses_1',
        permission: 'edit',
        patterns: const ['/workspace/lib/main.dart'],
        metadata: const {
          'filePath': '/workspace/lib/main.dart',
          'diff': patch,
        },
      ),
      onReply: (reply, {message}) async {},
    );
    expect(find.byKey(const Key('permission-file-path')), findsOneWidget);
    expect(find.byIcon(Icons.folder_outlined), findsOneWidget);
    expect(find.byKey(const Key('permission-command-preview')), findsNothing);
    final preview = find.byKey(const Key('permission-diff-preview'));
    expect(preview, findsOneWidget);
    expect(tester.getSize(preview).height, lessThanOrEqualTo(240));
    expect(find.textContaining('+new line'), findsOneWidget);

    await tester.tap(find.byKey(const Key('permission-see-full-diff')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('diff-view')), findsOneWidget);
    expect(find.text('main.dart'), findsOneWidget);
  });

  testWidgets('read asks without a diff show only the path row', (
    tester,
  ) async {
    await _pump(
      tester,
      permission: PermissionRequest(
        id: 'per_4',
        sessionID: 'ses_1',
        permission: 'read',
        patterns: const ['/workspace/README.md'],
        metadata: const {'patch': 'ignored for read'},
      ),
      onReply: (reply, {message}) async {},
    );
    expect(find.byKey(const Key('permission-file-path')), findsOneWidget);
    expect(find.byKey(const Key('permission-diff-preview')), findsNothing);
  });
}
