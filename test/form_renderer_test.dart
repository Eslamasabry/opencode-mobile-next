import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart' show ApiException;
import 'package:opencode_mobile/api2/models.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/widgets/form_renderer.dart';
import 'package:shared_preferences/shared_preferences.dart';

Api2FormInfo makeForm(
  List<Api2FormField> fields, {
  String id = 'frm_1',
  String sessionID = 'ses_1',
  String? title = 'Connect to Sentry',
}) => Api2FormInfo(id: id, sessionID: sessionID, title: title, fields: fields);

List<Api2FormOption> options(List<String> values) => [
  for (final value in values) Api2FormOption(value: value, label: 'L $value'),
];

Widget _app(Widget home) => MaterialApp(
  theme: AppTheme.light(),
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  home: home,
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() => SharedPreferences.setMockInitialValues({}));
  tearDown(debugForgetFormAnswers);

  Future<void> pumpRenderer(
    WidgetTester tester,
    Api2FormInfo form, {
    FormRendererSubmit? onSubmit,
    FormRendererCancel? onCancel,
    VoidCallback? onClose,
    String? profileId,
  }) async {
    await tester.pumpWidget(
      _app(
        Scaffold(
          body: FormRenderer(
            form: form,
            onSubmit: onSubmit ?? (_) async {},
            onCancel: onCancel ?? () async {},
            onClose: onClose,
            profileId: profileId,
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  Future<void> submit(WidgetTester tester) async {
    await tester.ensureVisible(find.byKey(const Key('form-submit')));
    await tester.tap(find.byKey(const Key('form-submit')));
    await tester.pumpAndSettle();
  }

  Future<void> tapText(WidgetTester tester, String text) async {
    await tester.ensureVisible(find.text(text).last);
    await tester.tap(find.text(text).last);
    await tester.pumpAndSettle();
  }

  Finder fieldText(String key) => find.descendant(
    of: find.byKey(Key('form-field-$key')),
    matching: find.byType(TextField),
  );

  String textOf(WidgetTester tester, String key) =>
      tester.widget<TextField>(fieldText(key).first).controller!.text;

  testWidgets('same form ID keeps drafts isolated between profiles', (
    tester,
  ) async {
    Api2FormInfo form() =>
        makeForm([Api2FormField(key: 'note', type: Api2FormFieldType.string)]);
    await pumpRenderer(tester, form(), profileId: 'main');
    await tester.enterText(fieldText('note'), 'Main server answer');
    await pumpRenderer(tester, form(), profileId: 'side');
    expect(textOf(tester, 'note'), isEmpty);
    await tester.enterText(fieldText('note'), 'Side server answer');
    await pumpRenderer(tester, form(), profileId: 'main');
    expect(textOf(tester, 'note'), 'Main server answer');
  });

  testWidgets('same form ID keeps drafts isolated between sessions', (
    tester,
  ) async {
    Api2FormInfo form(String session) => makeForm([
      Api2FormField(key: 'note', type: Api2FormFieldType.string),
    ], sessionID: session);
    await pumpRenderer(tester, form('first'), profileId: 'main');
    await tester.enterText(fieldText('note'), 'First session answer');
    await pumpRenderer(tester, form('second'), profileId: 'main');
    expect(textOf(tester, 'note'), isEmpty);
  });

  for (final change in ['default', 'options', 'condition']) {
    testWidgets('a changed $change does not reuse the old form draft', (
      tester,
    ) async {
      Api2FormInfo form({required bool changed}) => makeForm([
        Api2FormField(
          key: 'note',
          type: Api2FormFieldType.string,
          defaultValue: changed && change == 'default' ? 'New default' : null,
        ),
        Api2FormField(
          key: 'choice',
          type: Api2FormFieldType.string,
          options: options(changed && change == 'options' ? ['new'] : ['old']),
          when: changed && change == 'condition'
              ? [Api2FormCondition(key: 'note', op: 'eq', value: 'show')]
              : const [],
        ),
      ]);
      await pumpRenderer(tester, form(changed: false));
      await tester.enterText(fieldText('note'), 'Old draft');
      await pumpRenderer(tester, form(changed: true));
      expect(textOf(tester, 'note'), change == 'default' ? 'New default' : '');
    });
  }

  testWidgets('renders the kit sheet: title, origin, fields and actions', (
    tester,
  ) async {
    await pumpRenderer(
      tester,
      makeForm([Api2FormField(key: 'name', type: Api2FormFieldType.string)]),
      onClose: () {},
    );

    expect(find.byKey(const Key('form-sheet')), findsOneWidget);
    expect(find.text('Connect to Sentry'), findsOneWidget);
    expect(
      find.text('Asked by the agent in this conversation'),
      findsOneWidget,
    );
    expect(find.byKey(const Key('form-field-name')), findsOneWidget);
    expect(find.byKey(const Key('kit-sheet-actions')), findsOneWidget);
    expect(find.text('Send answers'), findsOneWidget);
    // The decline says what it declines; there is no separate "Finish
    // later": close, swipe and back keep the answers (owner rule
    // 2026-09-27, one way out each).
    expect(find.text('Decline this request'), findsOneWidget);
    expect(find.text('Dismiss'), findsNothing);
    expect(find.text('Finish later'), findsNothing);
    expect(find.byKey(const Key('form-later')), findsNothing);
  });

  testWidgets('global forms are attributed to an MCP server', (tester) async {
    await pumpRenderer(
      tester,
      makeForm([
        Api2FormField(key: 'name', type: Api2FormFieldType.string),
      ], sessionID: 'global'),
    );
    expect(find.text('Asked by an MCP server'), findsOneWidget);
  });

  testWidgets('free string field prefills, edits, submits', (tester) async {
    Map<String, dynamic>? sent;
    await pumpRenderer(
      tester,
      makeForm([
        Api2FormField(
          key: 'env',
          type: Api2FormFieldType.string,
          title: 'Environment',
          defaultValue: 'staging',
          placeholder: 'e.g. production',
          maxLength: 20,
        ),
      ]),
      onSubmit: (answer) async => sent = answer,
    );

    expect(find.text('Environment'), findsOneWidget);
    expect(textOf(tester, 'env'), 'staging');

    await tester.enterText(fieldText('env'), 'production');
    await submit(tester);
    expect(sent, {'env': 'production'});
  });

  testWidgets('string with few options: choice rows that speak their state', (
    tester,
  ) async {
    Map<String, dynamic>? sent;
    await pumpRenderer(
      tester,
      makeForm([
        Api2FormField(
          key: 'env',
          type: Api2FormFieldType.string,
          title: 'Environment',
          options: [
            Api2FormOption(
              value: 'production',
              label: 'Production',
              description: 'Live traffic',
            ),
            Api2FormOption(value: 'staging', label: 'Staging'),
            Api2FormOption(value: 'dev', label: 'Development'),
          ],
        ),
      ]),
      onSubmit: (answer) async => sent = answer,
    );

    expect(find.text('Live traffic'), findsOneWidget);
    final staging = find.byKey(const Key('form-option-env-staging'));
    expect(
      tester.getSemantics(staging),
      isSemantics(
        hasCheckedState: true,
        isChecked: false,
        isInMutuallyExclusiveGroup: true,
      ),
    );

    await tapText(tester, 'Staging');
    expect(
      tester.getSemantics(staging),
      isSemantics(hasCheckedState: true, isChecked: true),
    );
    await submit(tester);
    expect(sent, {'env': 'staging'});
  });

  testWidgets('string with five or more options picks from the kit menu', (
    tester,
  ) async {
    Map<String, dynamic>? sent;
    await pumpRenderer(
      tester,
      makeForm([
        Api2FormField(
          key: 'region',
          type: Api2FormFieldType.string,
          title: 'Region',
          options: options(['us', 'eu', 'ap', 'sa', 'af']),
        ),
      ]),
      onSubmit: (answer) async => sent = answer,
    );

    expect(find.text('Choose'), findsOneWidget);
    expect(find.text('L eu'), findsNothing);

    await tester.tap(find.byKey(const Key('form-field-region-pick')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('L eu').last);
    await tester.pumpAndSettle();
    // The row names the choice.
    expect(find.text('L eu'), findsOneWidget);
    await submit(tester);
    expect(sent, {'region': 'eu'});
  });

  testWidgets('custom select reveals an Other text field', (tester) async {
    Map<String, dynamic>? sent;
    await pumpRenderer(
      tester,
      makeForm([
        Api2FormField(
          key: 'env',
          type: Api2FormFieldType.string,
          options: options(['a', 'b']),
          custom: true,
        ),
      ]),
      onSubmit: (answer) async => sent = answer,
    );

    expect(find.byKey(const Key('form-field-env-other')), findsNothing);
    await tapText(tester, 'Other…');
    expect(find.byKey(const Key('form-field-env-other')), findsOneWidget);

    await tester.enterText(
      find.descendant(
        of: find.byKey(const Key('form-field-env-other')),
        matching: find.byType(TextField),
      ),
      'my own env',
    );
    await submit(tester);
    expect(sent, {'env': 'my own env'});
  });

  testWidgets('integer field filters to digits and validates the range', (
    tester,
  ) async {
    Map<String, dynamic>? sent;
    var calls = 0;
    await pumpRenderer(
      tester,
      makeForm([
        Api2FormField(
          key: 'retries',
          type: Api2FormFieldType.integer,
          title: 'Retries',
          minimum: 1,
          maximum: 10,
        ),
      ]),
      onSubmit: (answer) async {
        calls++;
        sent = answer;
      },
    );

    await tester.enterText(fieldText('retries'), '9a9');
    await tester.pump();
    expect(textOf(tester, 'retries'), '99');

    await submit(tester);
    expect(calls, 0);
    expect(find.text('Must be between 1 and 10'), findsOneWidget);

    await tester.enterText(fieldText('retries'), '5');
    await submit(tester);
    expect(calls, 1);
    expect(sent, {'retries': 5});
    expect(sent!['retries'], isA<int>());
  });

  testWidgets('number field accepts decimals', (tester) async {
    Map<String, dynamic>? sent;
    await pumpRenderer(
      tester,
      makeForm([Api2FormField(key: 'ratio', type: Api2FormFieldType.number)]),
      onSubmit: (answer) async => sent = answer,
    );
    await tester.enterText(fieldText('ratio'), '0.5');
    await submit(tester);
    expect(sent, {'ratio': 0.5});
  });

  testWidgets('boolean renders a switch row seeded from default', (
    tester,
  ) async {
    Map<String, dynamic>? sent;
    await pumpRenderer(
      tester,
      makeForm([
        Api2FormField(
          key: 'confirm',
          type: Api2FormFieldType.boolean,
          title: 'Confirm',
          description: 'Really do it',
          defaultValue: true,
        ),
      ]),
      onSubmit: (answer) async => sent = answer,
    );

    expect(
      tester.getSemantics(find.text('Confirm')),
      isSemantics(isToggled: true),
    );
    expect(find.text('Really do it'), findsOneWidget);

    await tapText(tester, 'Confirm');
    await submit(tester);
    expect(sent, {'confirm': false});
  });

  testWidgets('multiselect chips show their selection and enforce min/max', (
    tester,
  ) async {
    Map<String, dynamic>? sent;
    var calls = 0;
    await pumpRenderer(
      tester,
      makeForm([
        Api2FormField(
          key: 'tags',
          type: Api2FormFieldType.multiselect,
          title: 'Tags',
          required: true,
          minItems: 1,
          maxItems: 2,
          options: options(['a', 'b', 'c']),
        ),
      ]),
      onSubmit: (answer) async {
        calls++;
        sent = answer;
      },
    );

    expect(find.text('Pick 1–2'), findsOneWidget);
    await submit(tester);
    expect(calls, 0);
    expect(find.text('Required'), findsOneWidget);

    for (final value in ['a', 'b', 'c']) {
      await tester.tap(find.byKey(Key('form-option-tags-$value')));
      await tester.pumpAndSettle();
    }
    // The selected state is visible and spoken (map infoMissing).
    expect(
      tester.getSemantics(find.bySemanticsLabel('L a')),
      isSemantics(hasToggledState: true, isToggled: true),
    );
    expect(find.text('Pick 1–2 · 3 selected'), findsOneWidget);
    await submit(tester);
    expect(calls, 0);
    expect(find.text('Pick at most 2'), findsOneWidget);

    await tester.tap(find.byKey(const Key('form-option-tags-b')));
    await tester.pumpAndSettle();
    expect(
      tester.getSemantics(find.bySemanticsLabel('L b')),
      isSemantics(hasToggledState: true, isToggled: false),
    );
    await submit(tester);
    expect(calls, 1);
    expect(sent, {
      'tags': ['a', 'c'],
    });
  });

  testWidgets('multiselect with nine options renders a check list', (
    tester,
  ) async {
    Map<String, dynamic>? sent;
    final values = List.generate(9, (index) => 'v$index');
    await pumpRenderer(
      tester,
      makeForm([
        Api2FormField(
          key: 'many',
          type: Api2FormFieldType.multiselect,
          options: options(values),
        ),
      ]),
      onSubmit: (answer) async => sent = answer,
    );

    await tapText(tester, 'L v0');
    await tapText(tester, 'L v2');
    expect(
      tester.getSemantics(find.byKey(const Key('form-option-many-v2'))),
      isSemantics(hasCheckedState: true, isChecked: true),
    );
    await submit(tester);
    expect(sent, {
      'many': ['v0', 'v2'],
    });
  });

  testWidgets('custom multiselect adds typed values as removable chips', (
    tester,
  ) async {
    Map<String, dynamic>? sent;
    await pumpRenderer(
      tester,
      makeForm([
        Api2FormField(
          key: 'tags',
          type: Api2FormFieldType.multiselect,
          options: options(['a', 'b']),
          custom: true,
        ),
      ]),
      onSubmit: (answer) async => sent = answer,
    );

    await tester.tap(find.byKey(const Key('form-option-tags-a')));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.descendant(
        of: find.byKey(const Key('form-field-tags-add')),
        matching: find.byType(TextField),
      ),
      'homemade',
    );
    await tester.tap(find.byKey(const Key('form-field-tags-add-button')));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('form-own-tags-homemade')), findsOneWidget);
    expect(find.text('homemade'), findsOneWidget);

    await submit(tester);
    expect(sent, {
      'tags': ['a', 'homemade'],
    });
  });

  testWidgets('date field shows a human date and submits an ISO date', (
    tester,
  ) async {
    Map<String, dynamic>? sent;
    await pumpRenderer(
      tester,
      makeForm([
        Api2FormField(
          key: 'deploy',
          type: Api2FormFieldType.string,
          format: 'date',
          title: 'Deploy day',
          defaultValue: '2026-09-27',
        ),
      ]),
      onSubmit: (answer) async => sent = answer,
    );

    // Human date (map infoMissing), not the ISO string.
    expect(find.text('Sep 27, 2026'), findsOneWidget);
    expect(find.text('2026-09-27'), findsNothing);

    await tester.tap(find.byKey(const Key('form-field-deploy-pick')));
    await tester.pumpAndSettle();
    // The kit's date sheet, never the stock Material dialog.
    expect(find.byKey(const Key('form-field-deploy-picker')), findsOneWidget);
    expect(find.byType(DatePickerDialog), findsNothing);
    await tester.tap(
      find
          .descendant(of: find.byType(Semantics), matching: find.text('28'))
          .first,
    );
    await tester.tap(find.text('Use date'));
    await tester.pumpAndSettle();

    expect(find.text('Sep 28, 2026'), findsOneWidget);
    await submit(tester);
    expect(sent, {'deploy': '2026-09-28'});
  });

  testWidgets('an empty date field asks for a date', (tester) async {
    await pumpRenderer(
      tester,
      makeForm([
        Api2FormField(
          key: 'deploy',
          type: Api2FormFieldType.string,
          format: 'date-time',
          title: 'Deploy time',
        ),
      ]),
    );
    expect(find.text('Choose a date and time'), findsOneWidget);
  });

  testWidgets('external field renders an action row and never answers', (
    tester,
  ) async {
    Map<String, dynamic>? sent;
    await pumpRenderer(
      tester,
      makeForm([
        Api2FormField(
          key: 'connect',
          type: Api2FormFieldType.external,
          title: 'Authorize Sentry',
          description: 'Grants read access',
          required: true, // Must still never block validity.
          url: 'https://example.com/oauth',
        ),
        Api2FormField(
          key: 'note',
          type: Api2FormFieldType.string,
          defaultValue: 'done',
        ),
      ]),
      onSubmit: (answer) async => sent = answer,
    );

    expect(find.byKey(const Key('form-field-connect')), findsOneWidget);
    expect(find.text('Authorize Sentry'), findsOneWidget);
    expect(find.text('Grants read access'), findsOneWidget);
    expect(find.text('Opens example.com in your browser'), findsOneWidget);

    await submit(tester);
    expect(sent, {'note': 'done'});
    expect(sent!.containsKey('connect'), isFalse);
  });

  for (final hostile in const [
    'javascript:alert(1)',
    'file:///data/data/com.opencode.mobile/shared_prefs/prefs.xml',
    'data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==',
    'intent://scan/#Intent;scheme=zxing;end',
    'content://com.android.providers.downloads/all_downloads/1',
    'https://user:secret@example.com/oauth',
  ]) {
    testWidgets('external field refuses to launch $hostile', (tester) async {
      await pumpRenderer(
        tester,
        makeForm([
          Api2FormField(
            key: 'connect',
            type: Api2FormFieldType.external,
            title: 'Authorize Sentry',
            url: hostile,
          ),
        ]),
      );

      // The row says up front that this link is not openable instead of
      // offering a browser it will never reach.
      expect(
        find.text('This server sent a link this app will not open.'),
        findsOneWidget,
      );

      await tester.tap(find.byKey(const Key('form-external-card')));
      await tester.pumpAndSettle();

      // No confirmation, no launch — just the refusal.
      expect(find.textContaining('Link blocked'), findsOneWidget);
      expect(find.text('Open external link?'), findsNothing);
      expect(find.text('Open insecure HTTP link?'), findsNothing);
    });
  }

  testWidgets('external https field confirms with the real host first', (
    tester,
  ) async {
    await pumpRenderer(
      tester,
      makeForm([
        Api2FormField(
          key: 'connect',
          type: Api2FormFieldType.external,
          title: 'Authorize Sentry',
          url: 'https://login.example.org:8443/oauth?next=/a',
        ),
      ]),
    );

    expect(
      find.text('Opens login.example.org:8443 in your browser'),
      findsOneWidget,
    );

    await tester.tap(find.byKey(const Key('form-external-card')));
    await tester.pumpAndSettle();

    expect(find.text('Open external link?'), findsOneWidget);
    // The real host stays in sight in the body (06102116 link gate on kit
    // parts: "Opens {host} outside this app.").
    expect(
      find.text('Opens login.example.org:8443 outside this app.'),
      findsOneWidget,
    );

    // Declining is a real outcome: nothing opens.
    await tester.tap(find.text('Cancel'));
    await tester.pumpAndSettle();
    expect(find.text('Open external link?'), findsNothing);
  });

  testWidgets(
    'when fields reveal, hide, retain drafts, and stay out of the payload',
    (tester) async {
      Map<String, dynamic>? sent;
      await pumpRenderer(
        tester,
        makeForm([
          Api2FormField(
            key: 'env',
            type: Api2FormFieldType.string,
            options: [
              Api2FormOption(value: 'production', label: 'Production'),
              Api2FormOption(value: 'dev', label: 'Development'),
            ],
          ),
          Api2FormField(
            key: 'reason',
            type: Api2FormFieldType.string,
            title: 'Reason',
            when: [
              Api2FormCondition(key: 'env', op: 'eq', value: 'production'),
            ],
          ),
        ]),
        onSubmit: (answer) async => sent = answer,
      );

      // Unanswered controlling field: the dependent stays hidden.
      expect(find.byKey(const Key('form-field-reason')), findsNothing);

      await tapText(tester, 'Production');
      expect(find.byKey(const Key('form-field-reason')), findsOneWidget);

      await tester.enterText(fieldText('reason'), 'because prod');
      await submit(tester);
      expect(sent, {'env': 'production', 'reason': 'because prod'});

      // Deactivate: field disappears and is excluded from the payload...
      await tapText(tester, 'Development');
      expect(find.byKey(const Key('form-field-reason')), findsNothing);
      await submit(tester);
      expect(sent, {'env': 'dev'});
      expect(sent!.containsKey('reason'), isFalse);

      // ...but the draft answer is retained in state.
      await tapText(tester, 'Production');
      expect(textOf(tester, 'reason'), 'because prod');
      await submit(tester);
      expect(sent, {'env': 'production', 'reason': 'because prod'});
    },
  );

  testWidgets('validation blocks submit and shows inline errors', (
    tester,
  ) async {
    var calls = 0;
    Map<String, dynamic>? sent;
    await pumpRenderer(
      tester,
      makeForm([
        Api2FormField(
          key: 'name',
          type: Api2FormFieldType.string,
          title: 'Name',
          required: true,
        ),
        Api2FormField(
          key: 'code',
          type: Api2FormFieldType.string,
          title: 'Code',
          minLength: 4,
        ),
      ]),
      onSubmit: (answer) async {
        calls++;
        sent = answer;
      },
    );

    await tester.enterText(fieldText('code'), 'ab');
    await submit(tester);
    expect(calls, 0);
    expect(find.text('Required'), findsOneWidget);
    expect(find.text('Must be at least 4 characters'), findsOneWidget);

    await tester.enterText(fieldText('name'), 'Eslam');
    await tester.enterText(fieldText('code'), 'abcd');
    await submit(tester);
    expect(calls, 1);
    expect(sent, {'name': 'Eslam', 'code': 'abcd'});
  });

  testWidgets('send failure shows the error notice; Send answers retries', (
    tester,
  ) async {
    var closed = false;
    var attempts = 0;
    Map<String, dynamic>? sent;
    await pumpRenderer(
      tester,
      makeForm([
        Api2FormField(
          key: 'name',
          type: Api2FormFieldType.string,
          defaultValue: 'x',
        ),
      ]),
      onSubmit: (answer) async {
        attempts++;
        if (attempts == 1) {
          throw ApiException(
            'Answer form failed (HTTP 400): server rejected the answer',
            statusCode: 400,
          );
        }
        sent = answer;
      },
      onClose: () => closed = true,
    );

    expect(find.byKey(const Key('form-error-banner')), findsNothing);
    await submit(tester);
    expect(find.byKey(const Key('form-error-banner')), findsOneWidget);
    // Plain words, never the server's prose (a65dcea9 maps protocol
    // failures to domain-owned categories); transport text is details only.
    expect(
      find.text(
        "The server didn't accept the request. Try again, or report the "
        'problem.',
      ),
      findsOneWidget,
    );
    expect(find.textContaining('server rejected the answer'), findsNothing);
    expect(find.textContaining('HTTP 400'), findsNothing);
    expect(find.byKey(const Key('form-sheet')), findsOneWidget);
    expect(closed, isFalse);

    // The notice has no Try again of its own: Send answers is the retry.
    expect(
      find.descendant(
        of: find.byKey(const Key('form-error-banner')),
        matching: find.text('Try again'),
      ),
      findsNothing,
    );
    await submit(tester);
    expect(attempts, 2);
    expect(sent, {'name': 'x'});
    expect(closed, isTrue);
  });

  testWidgets('dismiss confirms before cancelling', (tester) async {
    var cancelled = false;
    var closed = false;
    await pumpRenderer(
      tester,
      makeForm([Api2FormField(key: 'name', type: Api2FormFieldType.string)]),
      onCancel: () async => cancelled = true,
      onClose: () => closed = true,
    );

    // Backing out of the confirm leaves the form untouched.
    await tester.tap(find.byKey(const Key('form-cancel')));
    await tester.pumpAndSettle();
    expect(find.text('Decline this request?'), findsOneWidget);
    expect(
      find.text('The agent continues without your answers.'),
      findsOneWidget,
    );
    await tester.tap(find.text('Cancel'));
    await tester.pumpAndSettle();
    expect(cancelled, isFalse);
    expect(closed, isFalse);

    // Confirming runs the cancel callback, then closes.
    await tester.tap(find.byKey(const Key('form-cancel')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('form-dismiss-confirm-button')));
    await tester.pumpAndSettle();
    expect(cancelled, isTrue);
    expect(closed, isTrue);
  });

  group('presented in the kit sheet', () {
    Future<void> pumpPresenter(
      WidgetTester tester,
      Api2FormInfo form, {
      FormRendererSubmit? onSubmit,
      FormRendererCancel? onCancel,
    }) async {
      await tester.pumpWidget(
        _app(
          Scaffold(
            body: Builder(
              builder: (context) => Center(
                child: TextButton(
                  onPressed: () => presentForm(
                    context,
                    form: form,
                    onSubmit: onSubmit ?? (_) async {},
                    onCancel: onCancel ?? () async {},
                  ),
                  child: const Text('Open form'),
                ),
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('Open form'));
      await tester.pumpAndSettle();
    }

    // Declared-but-inactive fields, to prove the rule counts declarations.
    List<Api2FormField> inactive(int count) => [
      for (var index = 0; index < count; index++)
        Api2FormField(
          key: 'hidden$index',
          type: Api2FormFieldType.string,
          when: [Api2FormCondition(key: 'lead', op: 'eq', value: 'never')],
        ),
    ];

    double sheetHeight(WidgetTester tester) =>
        tester.getSize(find.byKey(const Key('form-sheet'))).height;

    testWidgets('four declared fields open a content-height sheet', (
      tester,
    ) async {
      await pumpPresenter(
        tester,
        makeForm([
          Api2FormField(key: 'lead', type: Api2FormFieldType.string),
          ...inactive(3),
        ]),
      );
      expect(find.byType(BottomSheet), findsOneWidget);
      expect(find.byKey(const Key('form-sheet')), findsOneWidget);
      // Only the active field renders, but the sheet was still chosen.
      expect(find.byKey(const Key('form-field-hidden0')), findsNothing);
      expect(sheetHeight(tester), lessThan(600 * .6));
    });

    testWidgets('five declared fields open a full-height sheet', (
      tester,
    ) async {
      await pumpPresenter(
        tester,
        makeForm([
          Api2FormField(key: 'lead', type: Api2FormFieldType.string),
          ...inactive(4),
        ]),
      );
      expect(find.byType(BottomSheet), findsOneWidget);
      expect(sheetHeight(tester), greaterThan(600 * .8));
      // The actions stay pinned at the bottom of the window.
      final actions = tester.getRect(
        find.byKey(const Key('kit-sheet-actions')),
      );
      expect(actions.bottom, greaterThan(600 * .9));
    });

    testWidgets('a long field description forces the full-height sheet', (
      tester,
    ) async {
      await pumpPresenter(
        tester,
        makeForm([
          Api2FormField(key: 'lead', type: Api2FormFieldType.string),
          Api2FormField(
            key: 'essay',
            type: Api2FormFieldType.string,
            description: 'why ' * 50, // ~200 chars, past the ~140 threshold.
          ),
        ]),
      );
      expect(sheetHeight(tester), greaterThan(600 * .8));
    });

    Api2FormInfo carryForm() => makeForm([
      Api2FormField(key: 'note', type: Api2FormFieldType.string, title: 'Note'),
      Api2FormField(
        key: 'env',
        type: Api2FormFieldType.string,
        title: 'Environment',
        options: [
          Api2FormOption(value: 'production', label: 'Production'),
          Api2FormOption(value: 'dev', label: 'Development'),
        ],
      ),
    ], id: 'frm_carry');

    Future<void> fill(WidgetTester tester) async {
      await tester.enterText(fieldText('note'), 'ship it friday');
      await tapText(tester, 'Development');
    }

    testWidgets('draft carry: answers survive a swipe down and reopen', (
      tester,
    ) async {
      Map<String, dynamic>? sent;
      await pumpPresenter(
        tester,
        carryForm(),
        onSubmit: (answer) async => sent = answer,
      );
      await fill(tester);

      // Swipe the sheet away: it closes silently, nothing is asked.
      await tester.fling(
        find.text('Connect to Sentry'),
        const Offset(0, 500),
        2000,
      );
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('form-sheet')), findsNothing);
      expect(find.text('Decline this request?'), findsNothing);

      await tester.tap(find.text('Open form'));
      await tester.pumpAndSettle();
      expect(textOf(tester, 'note'), 'ship it friday');
      expect(
        tester.getSemantics(find.byKey(const Key('form-option-env-dev'))),
        isSemantics(isChecked: true),
      );

      await submit(tester);
      expect(sent, {'note': 'ship it friday', 'env': 'dev'});
      expect(find.byKey(const Key('form-sheet')), findsNothing);

      // Sent answers are not kept: the form starts over.
      await tester.tap(find.text('Open form'));
      await tester.pumpAndSettle();
      expect(textOf(tester, 'note'), isEmpty);
    });

    testWidgets('draft carry: close keeps the answers', (tester) async {
      await pumpPresenter(tester, carryForm());
      await fill(tester);
      expect(find.text('Finish later'), findsNothing);

      await tester.tap(find.byKey(const ValueKey('kit-sheet-close')));
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('form-sheet')), findsNothing);
      await tester.tap(find.text('Open form'));
      await tester.pumpAndSettle();
      expect(textOf(tester, 'note'), 'ship it friday');
    });

    testWidgets('dismiss asks in place, cancels, and forgets the answers', (
      tester,
    ) async {
      var cancelled = false;
      await pumpPresenter(
        tester,
        carryForm(),
        onCancel: () async => cancelled = true,
      );
      await fill(tester);

      await tester.tap(find.byKey(const Key('form-cancel')));
      await tester.pumpAndSettle();
      expect(find.text('Decline this request?'), findsOneWidget);
      // One sheet: the question replaced its content (no sheet on a sheet).
      expect(find.byType(BottomSheet), findsOneWidget);

      await tester.tap(find.byKey(const Key('form-dismiss-confirm-button')));
      await tester.pumpAndSettle();
      expect(cancelled, isTrue);
      expect(find.byKey(const Key('form-sheet')), findsNothing);

      await tester.tap(find.text('Open form'));
      await tester.pumpAndSettle();
      expect(textOf(tester, 'note'), isEmpty);
    });
  });
}
