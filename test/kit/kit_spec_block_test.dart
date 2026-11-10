import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'kit_motion_still.dart';
import 'team_kit_test_support.dart';

void main() {
  kitMotionStillTests(
    'KitSpecBlock',
    builds: {
      'editable': () => KitSpecBlock(
        label: 'Goal',
        controller: TextEditingController(text: 'Keep drafts'),
      ),
      'read-only': () => KitSpecBlock(
        label: 'Goal',
        controller: TextEditingController(text: 'Keep drafts'),
        readOnly: true,
      ),
    },
  );
  testWidgets('edits caller-owned draft and retains it read-only', (
    tester,
  ) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    await pumpTeam(tester, KitSpecBlock(label: 'Goal', controller: controller));
    await tester.enterText(
      find.byType(TextField),
      'Keep drafts across restarts',
    );
    expect(controller.text, 'Keep drafts across restarts');
    await pumpTeam(
      tester,
      KitSpecBlock(label: 'Goal', controller: controller, readOnly: true),
    );
    expect(find.text('Keep drafts across restarts'), findsOneWidget);
    expect(find.byType(TextField), findsNothing);
  });
}
