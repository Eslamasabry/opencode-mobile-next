import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/ui/widgets/session_title.dart';

void main() {
  test('the server placeholder loses its ISO timestamp', () {
    expect(
      presentedSessionTitle(
        Session(id: 's', title: 'New session - 2026-09-02T14:47:06.902Z'),
      ),
      'New session',
    );
    expect(
      presentedSessionTitle(
        Session(id: 's', title: 'New session - 2026-09-02T14:47:06Z'),
      ),
      'New session',
    );
  });

  test('real titles pass through untouched', () {
    expect(
      presentedSessionTitle(Session(id: 's', title: 'Fix the login bug')),
      'Fix the login bug',
    );
    // A user could genuinely mention a date; only the exact placeholder
    // shape is collapsed.
    expect(
      presentedSessionTitle(
        Session(id: 's', title: 'New session - notes from 2026-09-02'),
      ),
      'New session - notes from 2026-09-02',
    );
  });

  test('missing or blank titles fall back per surface', () {
    expect(presentedSessionTitle(null), 'New session');
    expect(presentedSessionTitle(Session(id: 's')), 'New session');
    expect(
      presentedSessionTitle(
        Session(id: 's', title: '  '),
        fallback: 'Chat',
      ),
      'Chat',
    );
  });
}
