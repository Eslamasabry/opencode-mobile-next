import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/background/notification_text.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';

MessageWithParts _message(String role, String text, {bool synthetic = false}) =>
    MessageWithParts(
      info: MessageInfo(id: 'm', sessionID: 's', role: role),
      parts: [Part(type: 'text', text: text, synthetic: synthetic)],
    );

PendingQuestion _question(List<String> labels, {bool multiple = false}) =>
    PendingQuestion(
      id: 'q',
      sessionID: 's',
      prompts: [
        QuestionPrompt(
          title: 't',
          question: 'q',
          multiple: multiple,
          custom: true,
          choices: [
            for (final label in labels)
              QuestionChoice(label: label, description: ''),
          ],
        ),
      ],
    );

void main() {
  group('redactForNotification', () {
    test('keeps an ordinary command whole', () {
      expect(redactForNotification('npm run build'), 'npm run build');
      expect(
        redactForNotification('flutter test test/tv/tv_focus_test.dart'),
        'flutter test test/tv/tv_focus_test.dart',
      );
    });

    test('masks bearer tokens and authorization values', () {
      final out = redactForNotification(
        'curl -H "Authorization: Bearer abcdef1234567890" https://x.test',
      );
      expect(out, isNot(contains('abcdef1234567890')));
      expect(out, contains('•••'));
    });

    test('masks provider keys, tokens and passwords', () {
      for (final secret in [
        'sk-ant-api03-ABCDEFGHIJKLMNOP',
        'ghp_ABCDEFGHIJKLMNOPQRSTUV',
        'xoxb-1234567890-abcdefghij',
        'AKIAABCDEFGHIJKLMNOP',
        'hunter2hunter2',
      ]) {
        final out = redactForNotification(
          'deploy --token $secret && echo PASSWORD=$secret',
        );
        expect(out, isNot(contains(secret)), reason: secret);
      }
    });

    test('masks credentials inside a URL but keeps the host', () {
      final out = redactForNotification(
        'git clone https://me:s3cretpass@github.com/a/b.git',
      );
      expect(out, isNot(contains('s3cretpass')));
      expect(out, contains('github.com'));
    });

    test('masks a long unbroken token-looking run', () {
      final out = redactForNotification('echo ${'a1B2' * 12} done');
      expect(out, isNot(contains('a1B2a1B2a1B2')));
    });

    test('keeps the name of a masked key so the line still reads', () {
      expect(redactForNotification('API_KEY=abc123xyz run'), 'API_KEY=••• run');
    });

    test('is one line, cut with an ellipsis', () {
      final out = redactForNotification('a\nb\n${'word ' * 80}', maxLength: 40);
      expect(out.contains('\n'), isFalse);
      expect(out.length, lessThanOrEqualTo(40));
      expect(out, endsWith('…'));
    });
  });

  group('firstReplyLine', () {
    test('takes the first line of the newest assistant reply', () {
      expect(
        firstReplyLine([
          _message('assistant', 'Old reply'),
          _message('user', 'next'),
          _message('assistant', '\n## Dark theme added to Settings.\nMore'),
        ]),
        'Dark theme added to Settings.',
      );
    });

    test('a line that introduces a list is joined with the first item', () {
      expect(
        firstReplyLine([
          _message(
            'assistant',
            'Done. The site now follows the system theme by default:\n'
                '\n- Light and dark both work\n- The toggle stays',
          ),
        ]),
        'Done. The site now follows the system theme by default: '
        'Light and dark both work',
      );
      expect(
        firstReplyLine([_message('assistant', 'Changes made:')]),
        'Changes made',
      );
      expect(
        firstReplyLine([
          _message(
            'assistant',
            'Intro that ends with a colon:\n- ${'x' * 200}',
          ),
        ], maxLength: 60).length,
        lessThanOrEqualTo(60),
      );
    });

    test('skips user and synthetic text, and is empty with nothing', () {
      expect(firstReplyLine([_message('user', 'hello')]), isEmpty);
      expect(
        firstReplyLine([_message('assistant', 'hidden', synthetic: true)]),
        isEmpty,
      );
      expect(firstReplyLine(const []), isEmpty);
    });

    test('never carries a secret from the reply', () {
      expect(
        firstReplyLine([
          _message('assistant', 'Set token=ghp_ABCDEFGHIJKLMNOPQRSTUV now'),
        ]),
        isNot(contains('ghp_')),
      );
    });
  });

  group('stoppedReasonToken', () {
    test('turns error names into plain tokens, never raw text', () {
      expect(stoppedReasonToken('ProviderAuthError'), 'auth');
      expect(stoppedReasonToken('ContextOverflowError'), 'context');
      expect(stoppedReasonToken('MessageOutputLengthError'), 'length');
      expect(stoppedReasonToken('ModelNotFoundError'), 'model');
      expect(stoppedReasonToken('SomethingNew'), 'other');
      expect(stoppedReasonToken(null), 'other');
    });
  });

  group('notificationOptionsOf', () {
    test('three or fewer short single-choice options become buttons', () {
      expect(notificationOptionsOf(_question(['Yes', 'No'])), ['Yes', 'No']);
      expect(notificationOptionsOf(_question(['a', 'b', 'c'])), [
        'a',
        'b',
        'c',
      ]);
    });

    test('too many, too long, multiple-choice or repeated give none', () {
      expect(notificationOptionsOf(_question(['a', 'b', 'c', 'd'])), isEmpty);
      expect(
        notificationOptionsOf(_question(['Use the long answer option now'])),
        isEmpty,
      );
      expect(
        notificationOptionsOf(_question(['a', 'b'], multiple: true)),
        isEmpty,
      );
      expect(notificationOptionsOf(_question(['a', 'a'])), isEmpty);
      expect(notificationOptionsOf(_question([])), isEmpty);
    });
  });

  group('permissionAskOf', () {
    PermissionRequest ask(
      String permission, {
      Map<String, dynamic> metadata = const {},
      List<String> patterns = const [],
    }) => PermissionRequest(
      id: 'p',
      sessionID: 's',
      permission: permission,
      metadata: metadata,
      patterns: patterns,
    );

    test('a command asks to run its program and sub-command', () {
      expect(
        permissionAskOf(
          ask('bash', metadata: {'command': 'npm install --save left-pad'}),
        ),
        (action: 'run', target: 'npm install'),
      );
      expect(commandHead('git push origin main'), 'git push');
      expect(commandHead('/usr/bin/pytest -q'), 'pytest');
      expect(commandHead('rm -rf build'), 'rm');
      expect(commandHead('CI=1 FOO=bar make test'), 'make test');
    });

    test('a secret never reaches the title', () {
      final head = commandHead('sk-live-ABCDEFGHIJ1234 --token x');
      expect(head, isNot(contains('sk-live')));
    });

    test('a file asks by its name, in the right verb', () {
      expect(permissionAskOf(ask('edit', patterns: ['lib/ui/settings.dart'])), (
        action: 'edit',
        target: 'settings.dart',
      ));
      expect(permissionAskOf(ask('read', patterns: ['config/.env'])), (
        action: 'read',
        target: '.env',
      ));
      expect(
        permissionAskOf(ask('external_directory', patterns: ['/srv/api/'])),
        (action: 'folder', target: 'api'),
      );
    });

    test('an ask with nothing to show has no target', () {
      expect(permissionAskOf(ask('webfetch')), (action: '', target: ''));
    });
  });

  test('the subtitle is server · project, either part optional', () {
    expect(
      notificationSubtitle('MacBook', 'api-server'),
      'MacBook · api-server',
    );
    expect(notificationSubtitle('', 'api-server'), 'api-server');
    expect(notificationSubtitle('MacBook', null), 'MacBook');
    expect(notificationSubtitle(null, ' '), '');
  });
}
