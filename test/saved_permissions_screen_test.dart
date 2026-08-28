import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/saved_permissions_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _PermissionRepository implements ProductRepository {
  _PermissionRepository({List<SavedPermission>? permissions})
    : permissions = List.of(permissions ?? const []);

  final List<SavedPermission> permissions;
  int listCalls = 0;
  final List<String> removeCalls = [];
  Object? listError;
  Object? removeError;

  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  Future<List<SavedPermission>> listSavedPermissions() async {
    listCalls += 1;
    if (listError case final error?) throw error;
    return List.of(permissions);
  }

  @override
  Future<void> removeSavedPermission(String id) async {
    removeCalls.add(id);
    if (removeError case final error?) throw error;
    permissions.removeWhere((permission) => permission.id == id);
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

Future<ConnectionController> _controller(ProductRepository repository) async {
  SharedPreferences.setMockInitialValues({});
  final controller =
      ConnectionController(
          ProfileStore(prefs: await SharedPreferences.getInstance()),
        )
        ..repository = repository
        ..status = StreamStatus.connected;
  return controller;
}

Widget _app(Widget home, {double textScale = 1}) => MaterialApp(
  home: Builder(
    builder: (context) => MediaQuery(
      data: MediaQuery.of(
        context,
      ).copyWith(textScaler: TextScaler.linear(textScale)),
      child: home,
    ),
  ),
);

const _permissions = [
  SavedPermission(
    id: 'permission-bash',
    projectID: 'project-1',
    action: 'bash',
    resource: 'git status',
  ),
  SavedPermission(
    id: 'permission-edit',
    projectID: 'project-1',
    action: 'edit',
    resource: 'lib/**',
  ),
];

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('lists and revokes one exact always allowed action', (
    tester,
  ) async {
    final repository = _PermissionRepository(permissions: _permissions);
    final controller = await _controller(repository);
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      _app(SavedPermissionsScreen(controller: controller)),
    );
    await tester.pumpAndSettle();

    expect(find.text('Run a shell command'), findsOneWidget);
    expect(find.text('Edit a file'), findsOneWidget);
    expect(find.text('git status'), findsOneWidget);
    expect(find.text('lib/**'), findsOneWidget);
    expect(find.byType(Card), findsNothing);

    final revoke = find.byKey(
      const ValueKey('revoke-saved-permission-permission-edit'),
    );
    await tester.tap(revoke);
    await tester.pumpAndSettle();
    expect(find.text('Revoke always allowed action?'), findsOneWidget);
    expect(find.text('Edit a file'), findsWidgets);
    expect(find.text('lib/**'), findsWidgets);
    expect(repository.removeCalls, isEmpty);

    await tester.tap(find.text('Revoke access'));
    await tester.pumpAndSettle();

    expect(repository.removeCalls, ['permission-edit']);
    expect(find.text('Edit a file'), findsNothing);
    expect(find.text('Run a shell command'), findsOneWidget);
    expect(find.text('Always allowed action revoked'), findsOneWidget);
  });

  testWidgets('failed revocation keeps the grant visible and retryable', (
    tester,
  ) async {
    final repository = _PermissionRepository(permissions: _permissions)
      ..removeError = const ProductException('Server refused revocation');
    final controller = await _controller(repository);
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      _app(SavedPermissionsScreen(controller: controller)),
    );
    await tester.pumpAndSettle();
    await tester.tap(
      find.byKey(const ValueKey('revoke-saved-permission-permission-bash')),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('Revoke access'));
    await tester.pumpAndSettle();

    expect(repository.removeCalls, ['permission-bash']);
    expect(find.text('Run a shell command'), findsOneWidget);
    expect(find.text('The last action failed'), findsOneWidget);
    expect(find.text('Server refused revocation'), findsWidgets);
  });

  testWidgets('waits for the post-wake repository before loading', (
    tester,
  ) async {
    final retainedRepository = _PermissionRepository(
      permissions: const [
        SavedPermission(
          id: 'stale',
          projectID: 'project-1',
          action: 'read',
          resource: 'stale.txt',
        ),
      ],
    );
    final replacementRepository = _PermissionRepository(
      permissions: const [
        SavedPermission(
          id: 'current',
          projectID: 'project-1',
          action: 'edit',
          resource: 'current.dart',
        ),
      ],
    );
    final ready = Completer<ProductRepository?>();
    final controller = await _controller(retainedRepository);
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      _app(
        SavedPermissionsScreen(
          controller: controller,
          repositoryResolver: () => ready.future,
        ),
      ),
    );
    await tester.pump();
    expect(retainedRepository.listCalls, 0);
    expect(replacementRepository.listCalls, 0);

    ready.complete(replacementRepository);
    await tester.pumpAndSettle();

    expect(retainedRepository.listCalls, 0);
    expect(replacementRepository.listCalls, 1);
    expect(find.text('current.dart'), findsOneWidget);
    expect(find.text('stale.txt'), findsNothing);
  });

  testWidgets('unsupported servers show a scoped retryable error', (
    tester,
  ) async {
    final repository = _PermissionRepository()
      ..listError = const ProductException(
        'Saved permission management is unavailable on this server',
      );
    final controller = await _controller(repository);
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      _app(SavedPermissionsScreen(controller: controller)),
    );
    await tester.pumpAndSettle();

    expect(
      find.text('Saved permission management is unavailable on this server'),
      findsOneWidget,
    );
    expect(find.text('Try again'), findsOneWidget);
    expect(find.text('No always allowed actions'), findsNothing);
  });

  testWidgets('revocation confirmation fits a compact large-text phone', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(320, 480);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final repository = _PermissionRepository(permissions: [_permissions.first]);
    final controller = await _controller(repository);
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      _app(SavedPermissionsScreen(controller: controller), textScale: 2),
    );
    await tester.pumpAndSettle();
    final revoke = find.byKey(
      const ValueKey('revoke-saved-permission-permission-bash'),
    );
    await tester.ensureVisible(revoke);
    await tester.tap(revoke);
    await tester.pumpAndSettle();

    expect(find.text('Revoke always allowed action?'), findsOneWidget);
    expect(find.text('Keep access'), findsOneWidget);
    expect(find.text('Revoke access'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('empty state fits a compact large-text phone', (tester) async {
    tester.view.physicalSize = const Size(320, 480);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final repository = _PermissionRepository();
    final controller = await _controller(repository);
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      _app(SavedPermissionsScreen(controller: controller), textScale: 2),
    );
    await tester.pumpAndSettle();

    expect(find.text('No always allowed actions'), findsOneWidget);
    expect(find.textContaining('Always allow'), findsWidgets);
    expect(tester.takeException(), isNull);
  });
}
