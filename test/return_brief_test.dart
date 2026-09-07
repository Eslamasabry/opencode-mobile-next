import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/domain/return_brief.dart';

Session session(String id, int idle, {String? parent}) => Session(
  id: id,
  title: id,
  parentID: parent,
  time: SessionTime(created: 1, updated: idle, idle: idle),
);

ReturnBrief brief(
  List<Session> sessions, {
  ReturnBriefAck? ack,
  Map<String, (ReturnBriefBlocker, String)> blockers = const {},
  bool known = true,
  bool partial = false,
  Set<String> busy = const {},
  Set<String> read = const {},
}) => ReturnBrief.build(
  sessions: sessions,
  readStateKnown: known,
  inventoryPartial: partial,
  isUnread: (s) => !read.contains(s.id),
  isBusy: busy.contains,
  blockerOf: (id) => blockers[id],
  ack: ack,
);

void main() {
  test(
    'first visit reports unreviewed identities, never a time interval or outcome',
    () {
      final result = brief([
        session('a', 10),
        session('b', 20),
        session('child', 30, parent: 'a'),
      ]);
      expect(result.unreviewed.map((r) => r.session.id), ['b', 'a']);
      expect(result.acknowledgement(null).runs, {('a', 10), ('b', 20)});
    },
  );

  test(
    'equal and older idle on a later-loaded session are never blanket acknowledged',
    () {
      final ack = brief([session('a', 20)]).acknowledgement(null);
      final result = brief([
        session('a', 20),
        session('b', 20),
        session('c', 10),
      ], ack: ack);
      expect(result.unreviewed.map((r) => r.session.id), ['b', 'c']);
      expect(brief([session('a', 21)], ack: ack).unreviewed, hasLength(1));
    },
  );

  test(
    'dismiss captures only shown rows and exact request kind/session/id',
    () {
      final sessions = [for (var i = 1; i <= 5; i++) session('s$i', i)];
      final shown = brief(sessions);
      expect(shown.hiddenUnreviewed, 2);
      final ack = shown.acknowledgement(null);
      expect(brief(sessions, ack: ack).unreviewed.map((r) => r.session.id), [
        's2',
        's1',
      ]);
      final requestAck = brief(
        [session('a', 1)],
        blockers: {'a': (ReturnBriefBlocker.permission, 'same')},
      ).acknowledgement(null);
      final next = brief(
        [session('a', 1), session('b', 1)],
        ack: requestAck,
        blockers: {
          'a': (ReturnBriefBlocker.question, 'same'),
          'b': (ReturnBriefBlocker.permission, 'same'),
        },
      );
      expect(next.requests, hasLength(2));
    },
  );

  test('bounded request rows leave overflow unacknowledged', () {
    final sessions = [for (var i = 0; i < 5; i++) session('s$i', 1)];
    final blockers = {
      for (var i = 0; i < 5; i++) 's$i': (ReturnBriefBlocker.question, 'q$i'),
    };
    final shown = brief(sessions, blockers: blockers);
    expect(shown.requests, hasLength(3));
    expect(shown.hiddenRequests, 2);
    expect(
      brief(
        sessions,
        blockers: blockers,
        ack: shown.acknowledgement(null),
      ).requests,
      hasLength(2),
    );
  });

  test('busy, viewed and blocked sessions do not become result rows', () {
    final result = brief(
      [session('busy', 10), session('read', 10), session('blocked', 10)],
      busy: {'busy'},
      read: {'read'},
      blockers: {'blocked': (ReturnBriefBlocker.form, 'f')},
    );
    expect(result.unreviewed, isEmpty);
    expect(result.requests.single.blocker, ReturnBriefBlocker.form);
  });

  test(
    'unsupported read state and partial inventory remain distinguishable from empty',
    () {
      final result = brief([session('a', 10)], known: false, partial: true);
      expect(result.isEmpty, isTrue);
      expect(result.readStateKnown, isFalse);
      expect(result.inventoryPartial, isTrue);
    },
  );

  test(
    'acknowledgements round trip; corrupt and overflow entries never hide extra rows',
    () {
      final ack = ReturnBriefAck.empty.merge(
        shownRuns: [for (var i = 1; i <= 70; i++) ('s$i', i)],
        shownRequestIDs: [for (var i = 0; i < 40; i++) 'q$i'],
      );
      expect(ack.runs, hasLength(64));
      expect(ack.requestIDs, hasLength(32));
      expect(ack.coversRun('s1', 1), isFalse);
      expect(ReturnBriefAck.fromJson(ack.toJson())!.runs, ack.runs);
      expect(ReturnBriefAck.fromJson({'idle': 999, 'requests': []}), isNull);
      expect(
        ReturnBriefAck.fromJson({
          'runs': [
            ['s', -1],
          ],
          'requests': [],
        }),
        isNull,
      );
    },
  );
}
