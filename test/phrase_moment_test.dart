import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/state/phrase_chips.dart';
import 'package:opencode_mobile/state/phrase_moment.dart';

MessageWithParts _user(String id) => MessageWithParts(
  info: MessageInfo(id: id, role: 'user', sessionID: 's'),
);

MessageWithParts _assistant(
  String id, {
  String? error,
  MessageErrorKind? kind,
  String? finish,
  bool done = true,
  int created = 1000,
  List<Part> parts = const [],
}) => MessageWithParts(
  info: MessageInfo(
    id: id,
    role: 'assistant',
    sessionID: 's',
    time: MsgTime(created: created, completed: done ? created + 5 : null),
    errorText: error,
    errorKind: kind,
    finish: finish,
  ),
  parts: [...parts],
);

Part _tool(String name, {String status = 'completed'}) => Part(
  id: 't-$name',
  type: 'tool',
  toolName: name,
  toolState: ToolState(status: status),
);

final _later = DateTime.fromMillisecondsSinceEpoch(1000 + 120000);

PhraseMomentState _derive(
  List<MessageWithParts> messages, {
  bool busy = false,
  bool stopped = false,
  bool offline = false,
}) => derivePhraseMoment(
  messages: messages,
  busy: busy,
  stoppedByPerson: stopped,
  offline: offline,
  now: _later,
);

void main() {
  test('an empty new chat has no moment', () {
    expect(_derive([]).moment, isNull);
    expect(_derive([_user('local-1')]).moment, isNull);
  });

  test('busy is working, even with no history', () {
    expect(_derive([], busy: true).moment, PhraseMoment.working);
    expect(
      _derive([_user('u1'), _assistant('a1')], busy: true).moment,
      PhraseMoment.working,
    );
  });

  test('a finished turn with no edits is idle', () {
    expect(
      _derive([
        _user('u1'),
        _assistant('a1', parts: [_tool('read')]),
      ]).moment,
      PhraseMoment.idle,
    );
  });

  test('a finished turn that edited files is finishedWithChanges', () {
    for (final tool in ['edit', 'write', 'apply_patch', 'multiedit']) {
      expect(
        _derive([
          _user('u1'),
          _assistant('a1', parts: [_tool(tool)]),
        ]).moment,
        PhraseMoment.finishedWithChanges,
        reason: tool,
      );
    }
  });

  test('only the last turn counts, and a failed edit is not a change', () {
    expect(
      _derive([
        _user('u1'),
        _assistant('a1', parts: [_tool('edit')]),
        _user('u2'),
        _assistant('a2', parts: [_tool('read')]),
      ]).moment,
      PhraseMoment.idle,
    );
    expect(
      _derive([
        _user('u1'),
        _assistant('a1', parts: [_tool('edit', status: 'error')]),
      ]).moment,
      PhraseMoment.idle,
    );
  });

  test('cut off by length is stoppedPartway; its Continue button shows', () {
    final cut = _derive([
      _user('u1'),
      _assistant(
        'a1',
        error: 'Output length limit reached',
        kind: MessageErrorKind.outputLength,
      ),
    ]);
    expect(cut.moment, PhraseMoment.stoppedPartway);
    expect(cut.continueShown, isTrue);
    // Offline the transcript offers no Continue, so the chip may.
    expect(
      _derive([
        _user('u1'),
        _assistant('a1', error: 'x', kind: MessageErrorKind.outputLength),
      ], offline: true).continueShown,
      isFalse,
    );
  });

  test('finish length with no error is stoppedPartway with no button', () {
    final cut = _derive([_user('u1'), _assistant('a1', finish: 'length')]);
    expect(cut.moment, PhraseMoment.stoppedPartway);
    expect(cut.continueShown, isFalse);
  });

  test('stopped by the person, an error, and an interruption', () {
    expect(
      _derive([_user('u1'), _assistant('a1')], stopped: true).moment,
      PhraseMoment.stoppedPartway,
    );
    expect(
      _derive([_user('u1')], stopped: true).moment,
      PhraseMoment.stoppedPartway,
    );
    expect(
      _derive([
        _user('u1'),
        _assistant(
          'a1',
          error: 'Provider failed',
          kind: MessageErrorKind.unknown,
        ),
      ]).moment,
      PhraseMoment.stoppedPartway,
    );
    expect(
      _derive([_user('u1'), _assistant('a1', done: false)]).moment,
      PhraseMoment.stoppedPartway,
    );
  });

  test('an error notice hides the row; a stop or a length cut does not', () {
    // Unslop 2026-10-30: the notice holds the way on (another model, Send
    // again); a row of retry chips under it only crowds it.
    expect(
      _derive([
        _user('u1'),
        _assistant(
          'a1',
          error: "This model's usage limit reached",
          kind: MessageErrorKind.unknown,
        ),
      ]).errorShown,
      isTrue,
    );
    expect(
      _derive([
        _user('u1'),
        _assistant('a1', error: 'aborted', kind: MessageErrorKind.aborted),
      ]).errorShown,
      isFalse,
    );
    expect(
      _derive([
        _user('u1'),
        _assistant('a1', error: 'x', kind: MessageErrorKind.outputLength),
      ]).errorShown,
      isFalse,
    );
    expect(_derive([_user('u1'), _assistant('a1')]).errorShown, isFalse);
  });

  test('a just-started reply that is not busy yet is not interrupted', () {
    expect(
      derivePhraseMoment(
        messages: [_user('u1'), _assistant('a1', done: false)],
        busy: false,
        now: DateTime.fromMillisecondsSinceEpoch(1000 + 2000),
      ).moment,
      PhraseMoment.idle,
    );
  });
}
