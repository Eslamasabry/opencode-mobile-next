import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/app_diagnostics_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _DiagnosticsRepository implements ProductRepository {
  int sends = 0;
  String? message;
  Map<String, Object?>? extra;

  @override
  Future<void> writeClientLog({
    required String message,
    Map<String, Object?> extra = const {},
  }) async {
    sends++;
    this.message = message;
    this.extra = extra;
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

Future<ConnectionController> _controller(ProductRepository repository) async {
  SharedPreferences.setMockInitialValues({});
  final store = ProfileStore(prefs: await SharedPreferences.getInstance());
  return ConnectionController(store)..repository = repository;
}

void main() {
  testWidgets('diagnostics are readable at narrow width and sent explicitly', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(640, 1280);
    tester.view.devicePixelRatio = 2;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    final repository = _DiagnosticsRepository();
    final controller = await _controller(repository);
    addTearDown(controller.dispose);
    controller.diagnostics.record(
      StateError('render failed safely'),
      StackTrace.fromString('at build (lib/chat.dart:42:3)'),
      source: 'flutter',
    );

    await tester.pumpWidget(
      MaterialApp(home: AppDiagnosticsScreen(controller: controller)),
    );
    await tester.pumpAndSettle();

    expect(find.text('Private until you send it'), findsOneWidget);
    expect(
      find.textContaining('Nothing is sent automatically'),
      findsOneWidget,
    );
    expect(find.textContaining('render failed safely'), findsOneWidget);
    expect(tester.takeException(), isNull);

    await tester.tap(find.byKey(const Key('send-app-diagnostics')));
    await tester.pumpAndSettle();

    expect(repository.sends, 1);
    expect(repository.message, contains('1 handled errors'));
    expect(repository.extra?['entryCount'], 1);
    expect(find.text('Diagnostics sent to OpenCode'), findsOneWidget);
  });

  testWidgets('diagnostics screen explains an empty process-local report', (
    tester,
  ) async {
    final controller = await _controller(_DiagnosticsRepository());
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      MaterialApp(home: AppDiagnosticsScreen(controller: controller)),
    );
    await tester.pumpAndSettle();

    expect(find.text('No captured app errors'), findsOneWidget);
    expect(
      tester
          .widget<FilledButton>(find.byKey(const Key('send-app-diagnostics')))
          .onPressed,
      isNull,
    );
  });
}
