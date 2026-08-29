import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/update/shorebird_update_notice.dart';

class _FakeUpdateService implements AppUpdateService {
  _FakeUpdateService(this.state);

  final AppUpdateState state;
  final download = Completer<void>();
  int checkCalls = 0;
  int downloadCalls = 0;

  @override
  bool get isAvailable => true;

  @override
  Future<AppUpdateState> checkForUpdate() async {
    checkCalls += 1;
    return state;
  }

  @override
  Future<void> downloadUpdate() {
    downloadCalls += 1;
    return download.future;
  }
}

void main() {
  testWidgets('shows Shorebird download and restart notices', (tester) async {
    final service = _FakeUpdateService(AppUpdateState.available);
    final messengerKey = GlobalKey<ScaffoldMessengerState>();

    await tester.pumpWidget(
      MaterialApp(
        scaffoldMessengerKey: messengerKey,
        builder: (context, child) => ShorebirdUpdateNotice(
          service: service,
          messengerKey: messengerKey,
          child: child ?? const SizedBox.shrink(),
        ),
        home: const Scaffold(body: Text('OpenCode')),
      ),
    );
    await tester.pump();
    await tester.pump();

    expect(service.checkCalls, 1);
    expect(service.downloadCalls, 1);
    expect(find.text('Receiving Shorebird update…'), findsOneWidget);

    service.download.complete();
    await tester.pump();
    await tester.pump();

    expect(
      find.text('Shorebird update ready — restart to apply.'),
      findsOneWidget,
    );
    expect(find.text('Got it'), findsOneWidget);
  });

  testWidgets('never claims restart readiness after a failed update', (
    tester,
  ) async {
    final service = _FakeUpdateService(AppUpdateState.available);
    final messengerKey = GlobalKey<ScaffoldMessengerState>();

    await tester.pumpWidget(
      MaterialApp(
        scaffoldMessengerKey: messengerKey,
        builder: (context, child) => ShorebirdUpdateNotice(
          service: service,
          messengerKey: messengerKey,
          child: child ?? const SizedBox.shrink(),
        ),
        home: const Scaffold(body: Text('OpenCode')),
      ),
    );
    await tester.pump();
    await tester.pump();

    expect(find.text('Receiving Shorebird update…'), findsOneWidget);
    service.download.completeError(Exception('patch hash mismatch'));
    await tester.pump();
    await tester.pump();

    expect(find.text('Receiving Shorebird update…'), findsNothing);
    expect(
      find.text('Shorebird update ready — restart to apply.'),
      findsNothing,
    );
    expect(tester.takeException(), isNull);
  });
}
