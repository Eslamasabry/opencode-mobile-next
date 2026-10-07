import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart' show PermissionRequest;
import 'package:opencode_mobile/domain/chat_feed.dart';
import 'package:opencode_mobile/domain/form_request.dart';
import 'package:opencode_mobile/domain/genui/gen_ui.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit_request_card.dart';
import 'package:opencode_mobile/ui/screens/chats/chats_host.dart';
import 'package:opencode_mobile/ui/widgets/form_renderer.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _FormsConnection extends ConnectionController {
  _FormsConnection(super.store);

  final requests = <String, CapturedFormRequest>{};

  @override
  CapturedFormRequest? formRequestForFeedItem(ChatFeedItem item) {
    final request = requests[item.directory];
    return request?.isPending() == true ? request : null;
  }

  @override
  ConnectionController? connectionForRow(ChatFeedItem item) => null;

  @override
  PermissionRequest? permissionForFeedItem(ChatFeedItem item) => null;

  @override
  PendingQuestion? questionForFeedItem(ChatFeedItem item) => null;

  @override
  List<GenUiCard> waitingCardsForFeedItem(ChatFeedItem item) => const [];

  void changed() => notifyListeners();
}

ChatFeedItem _row(String directory) => ChatFeedItem(
  sessionID: 'same-session',
  title: directory,
  directory: directory,
  projectName: directory,
  isGit: false,
  status: ChatStatus.needsYou,
  lastActivity: DateTime(2026),
  sourceId: 'profile:side',
);

Api2FormInfo _form(String title) => Api2FormInfo(
  id: 'same-form',
  sessionID: 'same-session',
  title: title,
  fields: [
    Api2FormField(
      key: 'count',
      type: Api2FormFieldType.integer,
      title: 'Count',
      defaultValue: 3,
    ),
    Api2FormField(
      key: 'confirm',
      type: Api2FormFieldType.boolean,
      title: 'Confirm',
      defaultValue: true,
    ),
    Api2FormField(
      key: 'tags',
      type: Api2FormFieldType.multiselect,
      title: 'Tags',
      options: [Api2FormOption(value: 'one', label: 'One')],
      defaultValue: ['one'],
    ),
  ],
);

CapturedFormRequest _request(
  Api2FormInfo form,
  String directory, {
  required bool Function() pending,
  required Future<void> Function(Map<String, dynamic>) reply,
  required Future<void> Function() cancel,
}) => CapturedFormRequest(
  form: form,
  identity: FormRequestIdentity(
    profileID: 'side',
    directory: directory,
    sessionID: form.sessionID,
    formID: form.id,
    revision: formSchemaRevision(form),
  ),
  isPending: pending,
  reply: reply,
  cancel: cancel,
);

void main() {
  late _FormsConnection connection;

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    connection = _FormsConnection(
      ProfileStore(prefs: await SharedPreferences.getInstance()),
    );
  });
  tearDown(() {
    connection.dispose();
    debugForgetFormAnswers();
  });

  Future<void> pumpRows(WidgetTester tester, List<String> directories) async {
    final host = ConnectionChatsHost(connection);
    await tester.pumpWidget(
      MaterialApp(
        theme: AppTheme.light(),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(
          body: SingleChildScrollView(
            child: Column(
              children: [
                for (final directory in directories)
                  Builder(
                    key: ValueKey(directory),
                    builder: (context) =>
                        host.listRequest(context, _row(directory))!,
                  ),
              ],
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  Future<void> open(WidgetTester tester, String directory) async {
    final button = find.descendant(
      of: find.byKey(ValueKey(directory)),
      matching: find.text('Send answers'),
    );
    // The card may use the kit's "Answer" copy; its semantic button is the
    // only answer action in this row.
    final answer = button.evaluate().isNotEmpty
        ? button
        : find.descendant(
            of: find.byKey(ValueKey(directory)),
            matching: find.text('Answer'),
          );
    await tester.ensureVisible(answer.first);
    await tester.tap(answer.first);
    await tester.pumpAndSettle();
  }

  testWidgets('an unopened list row submits the captured typed form', (
    tester,
  ) async {
    var pending = true;
    final replies = <Map<String, dynamic>>[];
    connection.requests['/other'] = _request(
      _form('Choose deployment'),
      '/other',
      pending: () => pending,
      reply: (answer) async {
        replies.add(answer);
        pending = false;
        connection.changed();
      },
      cancel: () async {},
    );
    await pumpRows(tester, ['/other']);
    expect(find.text('Choose deployment'), findsOneWidget);
    // A list row's Answer is secondary, as a list question's is: the list
    // has no single primary action per row.
    final card = tester.widget<KitRequestCard>(find.byType(KitRequestCard));
    expect((card.answers! as KitRequestInSheet).secondary, isTrue);
    await open(tester, '/other');
    expect(find.byKey(const Key('form-sheet')), findsOneWidget);
    await tester.tap(find.byKey(const Key('form-submit')));
    await tester.pumpAndSettle();
    expect(replies.single, {
      'count': 3,
      'confirm': true,
      'tags': ['one'],
    });
    expect(find.byKey(const Key('form-sheet')), findsNothing);
    expect(find.text('Choose deployment'), findsNothing);
  });

  testWidgets('settling the captured list form closes its nested decline', (
    tester,
  ) async {
    var pending = true;
    var cancels = 0;
    connection.requests['/other'] = _request(
      _form('Choose deployment'),
      '/other',
      pending: () => pending,
      reply: (_) async {},
      cancel: () async => cancels++,
    );
    await pumpRows(tester, ['/other']);
    expect(find.text('Choose deployment'), findsOneWidget);
    await open(tester, '/other');
    await tester.tap(find.byKey(const Key('form-cancel')));
    await tester.pumpAndSettle();
    expect(
      find.byKey(const Key('form-dismiss-confirm-button')),
      findsOneWidget,
    );
    pending = false;
    connection.changed();
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('form-sheet')), findsNothing);
    expect(find.byKey(const Key('form-dismiss-confirm-button')), findsNothing);
    expect(cancels, 0);
  });

  testWidgets('form failure receipt stays with its captured directory', (
    tester,
  ) async {
    for (final directory in ['/first', '/second']) {
      connection.requests[directory] = _request(
        _form(directory == '/first' ? 'First form' : 'Second form'),
        directory,
        pending: () => true,
        reply: (_) async => throw const ProductException('Delivery failed.'),
        cancel: () async {},
      );
    }
    await pumpRows(tester, ['/first', '/second']);
    expect(find.text('First form'), findsOneWidget);
    expect(find.text('Second form'), findsOneWidget);
    await open(tester, '/first');
    await tester.tap(find.byKey(const Key('form-submit')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('kit-sheet-close')));
    await tester.pumpAndSettle();
    expect(
      find.descendant(
        of: find.byKey(const ValueKey('/first')),
        matching: find.textContaining('Not accepted'),
      ),
      findsOneWidget,
    );
    expect(
      find.descendant(
        of: find.byKey(const ValueKey('/second')),
        matching: find.textContaining('Not accepted'),
      ),
      findsNothing,
    );
  });
}
