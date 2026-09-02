import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/widgets/info_label.dart';

void main() {
  testWidgets('tapping a glossary term opens its explanation', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(body: Center(child: InfoLabel.glossary(Glossary.mcp))),
      ),
    );
    expect(find.text('MCP'), findsOneWidget);
    await tester.tap(find.text('MCP'));
    await tester.pumpAndSettle();
    expect(find.textContaining('Model Context Protocol'), findsOneWidget);
    await tester.tap(find.text('Got it'));
    await tester.pumpAndSettle();
    expect(find.textContaining('Model Context Protocol'), findsNothing);
  });

  testWidgets('term is announced as a button with a hint', (tester) async {
    final handle = tester.ensureSemantics();
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: InfoLabel('Worktree', explanation: 'A separate checkout.'),
        ),
      ),
    );
    expect(
      tester.getSemantics(find.byType(InfoLabel)),
      matchesSemantics(
        isButton: true,
        hasTapAction: true,
        label: 'Worktree. Tap for an explanation.',
      ),
    );
    handle.dispose();
  });

  test('every glossary entry stays short enough to read in one glance', () {
    for (final entry in [
      Glossary.mcp,
      Glossary.worktree,
      Glossary.provider,
      Glossary.context,
      Glossary.agent,
      Glossary.reasoning,
      Glossary.permission,
      Glossary.variant,
    ]) {
      expect(entry.explanation.length, lessThan(260), reason: entry.term);
    }
  });
}
