// "Archive conversation" and "Delete conversation" live in the open
// conversation's menu, named for what the server can do (capability flags,
// never the backend), ask first naming the conversation, run inside the
// question, and leave the conversation on success.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/paseo/gateway.dart';
import 'package:opencode_mobile/ui/widgets/session_menu.dart';

import 'revamp/chat_3_support.dart';

class _LeaveApi extends Chat3Api {
  final deleted = <String>[];
  Object? failure;

  @override
  Future<void> deleteSession(String id) async {
    final error = failure;
    if (error != null) throw error;
    deleted.add(id);
  }
}

class _LeaveRepository implements ProductRepository {
  final archived = <String>[];

  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  Future<void> archiveSession(String id) async => archived.add(id);

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

Future<void> _open(WidgetTester tester, String key) async {
  await tester.tap(find.byTooltip('Conversation menu'));
  await tester.pumpAndSettle();
  await tester.ensureVisible(find.byKey(ValueKey(key)));
  await tester.pumpAndSettle();
  await tester.tap(find.byKey(ValueKey(key)));
  await tester.pumpAndSettle();
}

void main() {
  setUpAll(chat3MockSecureStorage);

  testWidgets('Delete names the conversation, says it cannot be undone, '
      'deletes and leaves', (tester) async {
    final api = _LeaveApi();
    final conn = await chat3Controller(api: api);
    addTearDown(conn.dispose);
    conn.sessionsById['session-1'] = Session(
      id: 'session-1',
      title: 'Fix the cart total rounding',
    );
    await pumpChat3(tester, conn);

    await _open(tester, 'session-menu-delete');
    expect(find.text('Delete “Fix the cart total rounding”?'), findsOneWidget);
    expect(find.textContaining('This cannot be undone.'), findsOneWidget);
    expect(api.deleted, isEmpty);
    await tester.tap(find.byKey(const ValueKey('session-delete-confirm')));
    await tester.pumpAndSettle();
    expect(api.deleted, ['session-1']);
    expect(conn.sessionsById.containsKey('session-1'), isFalse);
  });

  testWidgets('a failed delete keeps the question open', (tester) async {
    final api = _LeaveApi()..failure = StateError('no');
    final conn = await chat3Controller(api: api);
    addTearDown(conn.dispose);
    await pumpChat3(tester, conn);
    await _open(tester, 'session-menu-delete');
    await tester.tap(find.byKey(const ValueKey('session-delete-confirm')));
    await tester.pumpAndSettle();
    expect(
      find.byKey(const ValueKey('session-delete-confirm')),
      findsOneWidget,
    );
    expect(api.deleted, isEmpty);
  });

  testWidgets('Archive asks, archives through the repository and leaves', (
    tester,
  ) async {
    final conn = await chat3Controller();
    final repository = _LeaveRepository();
    conn.repository = repository;
    addTearDown(conn.dispose);
    await pumpChat3(tester, conn);
    await _open(tester, 'session-menu-archive');
    await tester.tap(find.byKey(const ValueKey('session-archive-confirm')));
    await tester.pumpAndSettle();
    expect(repository.archived, ['session-1']);
  });

  testWidgets('Paseo offers Archive and no Delete', (tester) async {
    late List<String> keys;
    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Builder(
          builder: (context) {
            keys = [
              for (final item in sessionMenuItems(
                AppLocalizations.of(context),
                SessionMenuOffer.of(
                  paseoServerCapabilities,
                  shared: false,
                  savedServer: true,
                ),
                onSelected: (_) {},
              ))
                (item.key as ValueKey<String>).value,
            ];
            return const SizedBox.shrink();
          },
        ),
      ),
    );
    expect(keys, contains('session-menu-archive'));
    expect(keys, isNot(contains('session-menu-delete')));
  });
}
