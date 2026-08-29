import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api2/models.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/widgets/form_renderer.dart';

Api2FormInfo makeForm(
  List<Api2FormField> fields, {
  String sessionID = 'ses_1',
  String? title = 'Connect to Sentry',
}) => Api2FormInfo(
  id: 'frm_1',
  sessionID: sessionID,
  title: title,
  fields: fields,
);

List<Api2FormOption> options(List<String> values) => [
  for (final value in values) Api2FormOption(value: value, label: 'L $value'),
];

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  Future<void> pumpRenderer(
    WidgetTester tester,
    Api2FormInfo form, {
    FormRendererSubmit? onSubmit,
    FormRendererCancel? onCancel,
    VoidCallback? onClose,
  }) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: AppTheme.light(),
        home: Scaffold(
          body: FormRenderer(
            form: form,
            onSubmit: onSubmit ?? (_) async {},
            onCancel: onCancel ?? () async {},
            onClose: onClose,
          ),
        ),
      ),
    );
  }

  Future<void> submit(WidgetTester tester) async {
    await tester.tap(find.byKey(const Key('form-submit')));
    await tester.pumpAndSettle();
  }

  Finder fieldText(String key) => find.descendant(
    of: find.byKey(Key('form-field-$key')),
    matching: find.byType(TextField),
  );

  testWidgets('renders header, origin line, apply bar, and locked keys', (
    tester,
  ) async {
    await pumpRenderer(
      tester,
      makeForm([Api2FormField(key: 'name', type: Api2FormFieldType.string)]),
    );

    expect(find.byKey(const Key('form-sheet')), findsOneWidget);
    expect(find.byKey(const Key('form-title')), findsOneWidget);
    expect(find.text('Connect to Sentry'), findsOneWidget);
    expect(find.text('Asked by the agent in this session'), findsOneWidget);
    expect(find.byKey(const Key('form-field-name')), findsOneWidget);
    expect(find.byKey(const Key('form-apply-bar')), findsOneWidget);
    expect(find.byKey(const Key('form-submit')), findsOneWidget);
    expect(find.byKey(const Key('form-cancel')), findsOneWidget);
    expect(find.text('Send answers'), findsOneWidget);
    expect(find.text('Dismiss'), findsOneWidget);
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

  testWidgets('free string field prefills, shows counter, edits, submits', (
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
          defaultValue: 'staging',
          placeholder: 'e.g. production',
          maxLength: 20,
        ),
      ]),
      onSubmit: (answer) async => sent = answer,
    );

    expect(find.text('staging'), findsOneWidget);
    expect(find.text('7/20'), findsOneWidget); // M3 counter from maxLength.

    await tester.enterText(fieldText('env'), 'production');
    await submit(tester);
    expect(sent, {'env': 'production'});
  });

  testWidgets('string with few options renders a radio card group', (
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

    expect(find.byType(RadioListTile<String>), findsNWidgets(3));
    expect(find.text('Live traffic'), findsOneWidget);

    await tester.tap(find.text('Staging'));
    await tester.pumpAndSettle();
    await submit(tester);
    expect(sent, {'env': 'staging'});
  });

  testWidgets('string with five or more options uses a dropdown menu', (
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

    expect(find.byType(DropdownMenu<String>), findsOneWidget);
    expect(find.byType(RadioListTile<String>), findsNothing);

    await tester.tap(find.byType(DropdownMenu<String>));
    await tester.pumpAndSettle();
    await tester.tap(find.text('L eu').last);
    await tester.pumpAndSettle();
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
    await tester.tap(find.text('Other…'));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('form-field-env-other')), findsOneWidget);

    await tester.enterText(
      find.byKey(const Key('form-field-env-other')),
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
    expect(
      tester.widget<TextField>(fieldText('retries')).controller!.text,
      '99',
    );

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

  testWidgets('boolean renders a switch seeded from default', (tester) async {
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

    final tile = tester.widget<SwitchListTile>(find.byType(SwitchListTile));
    expect(tile.value, isTrue);
    expect(find.text('Really do it'), findsOneWidget);

    await tester.tap(find.byType(SwitchListTile));
    await tester.pumpAndSettle();
    await submit(tester);
    expect(sent, {'confirm': false});
  });

  testWidgets('multiselect chips enforce min/max items with a pick caption', (
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

    expect(find.byType(FilterChip), findsNWidgets(3));
    expect(find.text('Pick 1–2'), findsOneWidget);

    await submit(tester);
    expect(calls, 0);
    expect(find.text('Required'), findsOneWidget);

    for (final value in ['L a', 'L b', 'L c']) {
      await tester.tap(find.text(value));
      await tester.pumpAndSettle();
    }
    expect(find.text('Pick 1–2 · 3 selected'), findsOneWidget);
    await submit(tester);
    expect(calls, 0);
    expect(find.text('Pick at most 2'), findsOneWidget);

    await tester.tap(find.text('L b'));
    await tester.pumpAndSettle();
    await submit(tester);
    expect(calls, 1);
    expect(sent, {
      'tags': ['a', 'c'],
    });
  });

  testWidgets('multiselect with nine options renders a checkbox list', (
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

    expect(find.byType(CheckboxListTile), findsNWidgets(9));
    expect(find.byType(FilterChip), findsNothing);

    await tester.tap(find.text('L v0'));
    await tester.tap(find.text('L v2'));
    await tester.pumpAndSettle();
    await submit(tester);
    expect(sent, {
      'many': ['v0', 'v2'],
    });
  });

  testWidgets('custom multiselect adds typed values as input chips', (
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

    await tester.tap(find.text('L a'));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const Key('form-field-tags-add')),
      'homemade',
    );
    await tester.tap(find.byTooltip('Add answer'));
    await tester.pumpAndSettle();

    expect(find.byType(InputChip), findsOneWidget);
    expect(find.text('homemade'), findsOneWidget);

    await submit(tester);
    expect(sent, {
      'tags': ['a', 'homemade'],
    });
  });

  testWidgets('date field opens the date picker and submits an ISO date', (
    tester,
  ) async {
    Map<String, dynamic>? sent;
    await pumpRenderer(
      tester,
      makeForm([
        Api2FormField(
          key: 'birthday',
          type: Api2FormFieldType.string,
          format: 'date',
          title: 'Birthday',
        ),
      ]),
      onSubmit: (answer) async => sent = answer,
    );

    await tester.tap(fieldText('birthday'));
    await tester.pumpAndSettle();
    expect(find.byType(DatePickerDialog), findsOneWidget);
    await tester.tap(find.text('OK'));
    await tester.pumpAndSettle();

    final now = DateTime.now();
    final iso =
        '${now.year}-'
        '${now.month.toString().padLeft(2, '0')}-'
        '${now.day.toString().padLeft(2, '0')}';
    expect(
      tester.widget<TextField>(fieldText('birthday')).controller!.text,
      iso,
    );
    await submit(tester);
    expect(sent, {'birthday': iso});
  });

  testWidgets('external field renders an action card and never answers', (
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
    expect(find.text('Opens in your browser'), findsOneWidget);
    expect(find.byIcon(Icons.open_in_new_rounded), findsOneWidget);

    await submit(tester);
    expect(sent, {'note': 'done'});
    expect(sent!.containsKey('connect'), isFalse);
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

      await tester.tap(find.text('Production'));
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('form-field-reason')), findsOneWidget);

      await tester.enterText(fieldText('reason'), 'because prod');
      await submit(tester);
      expect(sent, {'env': 'production', 'reason': 'because prod'});

      // Deactivate: field disappears and is excluded from the payload...
      await tester.tap(find.text('Development'));
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('form-field-reason')), findsNothing);
      await submit(tester);
      expect(sent, {'env': 'dev'});
      expect(sent!.containsKey('reason'), isFalse);

      // ...but the draft answer is retained in state.
      await tester.tap(find.text('Production'));
      await tester.pumpAndSettle();
      expect(
        tester.widget<TextField>(fieldText('reason')).controller!.text,
        'because prod',
      );
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

  testWidgets('submit failure surfaces the error banner and stays open', (
    tester,
  ) async {
    var closed = false;
    await pumpRenderer(
      tester,
      makeForm([
        Api2FormField(
          key: 'name',
          type: Api2FormFieldType.string,
          defaultValue: 'x',
        ),
      ]),
      onSubmit: (_) async => throw Exception('server rejected the answer'),
      onClose: () => closed = true,
    );

    expect(find.byKey(const Key('form-error-banner')), findsNothing);
    await submit(tester);
    expect(find.byKey(const Key('form-error-banner')), findsOneWidget);
    expect(find.text('server rejected the answer'), findsOneWidget);
    expect(find.byKey(const Key('form-sheet')), findsOneWidget);
    expect(closed, isFalse);
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
    expect(find.text('Dismiss this request?'), findsOneWidget);
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

  group('presentation split on DECLARED field count', () {
    Future<void> pumpPresenter(WidgetTester tester, Api2FormInfo form) async {
      await tester.pumpWidget(
        MaterialApp(
          theme: AppTheme.light(),
          home: Scaffold(
            body: Builder(
              builder: (context) => Center(
                child: FilledButton(
                  onPressed: () => presentForm(
                    context,
                    form: form,
                    onSubmit: (_) async {},
                    onCancel: () async {},
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

    testWidgets('four declared fields open as a modal bottom sheet', (
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
    });

    testWidgets('five declared fields open as a full-screen dialog', (
      tester,
    ) async {
      await pumpPresenter(
        tester,
        makeForm([
          Api2FormField(key: 'lead', type: Api2FormFieldType.string),
          ...inactive(4),
        ]),
      );
      expect(find.byType(BottomSheet), findsNothing);
      expect(find.byKey(const Key('form-sheet')), findsOneWidget);
      final route =
          ModalRoute.of(tester.element(find.byKey(const Key('form-title'))))!
              as MaterialPageRoute<void>;
      expect(route.fullscreenDialog, isTrue);
    });

    testWidgets('a long field description forces the full-screen dialog', (
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
      expect(find.byType(BottomSheet), findsNothing);
      final route =
          ModalRoute.of(tester.element(find.byKey(const Key('form-title'))))!
              as MaterialPageRoute<void>;
      expect(route.fullscreenDialog, isTrue);
    });
  });
}
