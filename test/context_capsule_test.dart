import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/context_capsule.dart';
import 'package:opencode_mobile/ui/screens/context_capsule_screen.dart';

const capsuleImage = PromptAttachment(
  filename: 'screenshot.png',
  mime: 'image/png',
  url: 'data:image/png;base64,aGVsbG8=',
);

Future<void> openCapsule(
  WidgetTester tester, {
  required ValueNotifier<bool> scope,
  required ValueChanged<ContextCapsule?> onResult,
  Future<PromptAttachment?> Function(List<PromptAttachment>)? pickImage,
}) async {
  await tester.pumpWidget(
    MaterialApp(
      home: Builder(
        builder: (context) => Scaffold(
          body: TextButton(
            onPressed: () async {
              onResult(
                await Navigator.of(context).push<ContextCapsule>(
                  MaterialPageRoute(
                    builder: (_) => ContextCapsuleScreen(
                      sessionTitle: 'Fix checkout on mobile',
                      scopeChanges: scope,
                      isCurrent: () => scope.value,
                      pickImage: pickImage,
                    ),
                  ),
                ),
              );
            },
            child: const Text('Open'),
          ),
        ),
      ),
    ),
  );
  await tester.tap(find.text('Open'));
  await tester.pumpAndSettle();
}

void main() {
  test(
    'appending preserves the exact draft and does not fabricate text for images',
    () {
      expect(
        ContextCapsule(text: 'Error\nfailed').appendTo('Existing  \n'),
        'Existing  \n\n\nError\nfailed',
      );
      expect(
        ContextCapsule(text: '', images: [capsuleImage]).appendTo('Existing'),
        'Existing',
      );
    },
  );

  testWidgets(
    'review edits labels, removes excerpts and returns only on Apply',
    (tester) async {
      final scope = ValueNotifier(true);
      addTearDown(scope.dispose);
      ContextCapsule? result;
      await openCapsule(
        tester,
        scope: scope,
        onResult: (value) => result = value,
      );
      expect(find.text('Fix checkout on mobile'), findsOneWidget);
      expect(find.textContaining('accepts text only'), findsOneWidget);
      await tester.tap(find.text('Error'));
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField).at(0), 'Checkout failure');
      await tester.enterText(find.byType(TextField).at(1), 'Request timed out');
      expect(result, isNull);
      tester.testTextInput.hide();
      await tester.pumpAndSettle();
      await tester.scrollUntilVisible(
        find.text('Apply to draft'),
        200,
        scrollable: find
            .descendant(
              of: find.byType(ContextCapsuleScreen),
              matching: find.byType(Scrollable),
            )
            .first,
      );
      await tester.tap(find.text('Apply to draft'));
      await tester.pumpAndSettle();
      expect(
        result!.text,
        'Context capsule\n\nCheckout failure\nRequest timed out',
      );
    },
  );

  testWidgets('Cancel returns no content after editing', (tester) async {
    final scope = ValueNotifier(true);
    addTearDown(scope.dispose);
    ContextCapsule? result;
    var returned = false;
    await openCapsule(
      tester,
      scope: scope,
      onResult: (value) {
        result = value;
        returned = true;
      },
    );
    await tester.tap(find.text('Code'));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField).last, 'throw error;');
    tester.testTextInput.hide();
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(
      find.text('Cancel'),
      200,
      scrollable: find
          .descendant(
            of: find.byType(ContextCapsuleScreen),
            matching: find.byType(Scrollable),
          )
          .first,
    );
    await tester.tap(find.text('Cancel'));
    await tester.pumpAndSettle();
    expect(returned, isTrue);
    expect(result, isNull);
  });

  testWidgets(
    'scope change during picking rejects late image and stays invalid after return',
    (tester) async {
      final scope = ValueNotifier(true);
      addTearDown(scope.dispose);
      final pending = Completer<PromptAttachment?>();
      await openCapsule(
        tester,
        scope: scope,
        onResult: (_) {},
        pickImage: (_) => pending.future,
      );
      await tester.tap(find.text('Add screenshot or image'));
      await tester.pump();
      scope.value = false;
      await tester.pump();
      scope.value = true;
      pending.complete(capsuleImage);
      await tester.pumpAndSettle();
      expect(find.text('screenshot.png'), findsNothing);
      expect(find.text('Apply to draft'), findsNothing);
      expect(
        find.textContaining('task, connection or draft changed'),
        findsOneWidget,
      );
    },
  );

  testWidgets('cancelling while picker runs discards its late result', (
    tester,
  ) async {
    final scope = ValueNotifier(true);
    addTearDown(scope.dispose);
    final pending = Completer<PromptAttachment?>();
    ContextCapsule? result;
    await openCapsule(
      tester,
      scope: scope,
      onResult: (value) => result = value,
      pickImage: (_) => pending.future,
    );
    await tester.tap(find.text('Add screenshot or image'));
    await tester.pump();
    await tester.tap(find.text('Cancel'));
    await tester.pumpAndSettle();
    pending.complete(capsuleImage);
    await tester.pumpAndSettle();
    expect(result, isNull);
    expect(tester.takeException(), isNull);
  });

  testWidgets('image removal and nonimage rejection keep the bundle honest', (
    tester,
  ) async {
    final scope = ValueNotifier(true);
    addTearDown(scope.dispose);
    PromptAttachment picked = capsuleImage;
    await openCapsule(
      tester,
      scope: scope,
      onResult: (_) {},
      pickImage: (_) async => picked,
    );
    await tester.tap(find.text('Add screenshot or image'));
    await tester.pumpAndSettle();
    expect(find.text('screenshot.png'), findsOneWidget);
    await tester.tap(find.text('Remove'));
    await tester.pumpAndSettle();
    expect(find.text('screenshot.png'), findsNothing);
    picked = const PromptAttachment(
      filename: 'error.log',
      mime: 'text/plain',
      url: 'data:text/plain;base64,',
    );
    await tester.tap(find.text('Add screenshot or image'));
    await tester.pumpAndSettle();
    expect(find.textContaining('Choose a PNG'), findsOneWidget);
    expect(find.text('error.log'), findsNothing);
  });

  testWidgets(
    'clipboard is read only after explicit Paste and scope checked on return',
    (tester) async {
      final scope = ValueNotifier(true);
      addTearDown(scope.dispose);
      var reads = 0;
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(SystemChannels.platform, (call) async {
            if (call.method == 'Clipboard.getData') {
              reads++;
              return {'text': 'Copied error'};
            }
            return null;
          });
      addTearDown(
        () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
            .setMockMethodCallHandler(SystemChannels.platform, null),
      );
      await openCapsule(tester, scope: scope, onResult: (_) {});
      await tester.tap(find.text('Note'));
      await tester.pumpAndSettle();
      expect(reads, 0);
      tester.testTextInput.hide();
      await tester.pumpAndSettle();
      await tester.scrollUntilVisible(
        find.text('Paste'),
        200,
        scrollable: find
            .descendant(
              of: find.byType(ContextCapsuleScreen),
              matching: find.byType(Scrollable),
            )
            .first,
      );
      await tester.tap(find.text('Paste'));
      await tester.pumpAndSettle();
      expect(reads, 1);
      expect(find.text('Copied error'), findsOneWidget);
      await tester.tap(find.text('Remove'));
      await tester.pumpAndSettle();
      expect(find.byType(TextField), findsNothing);
    },
  );
}
