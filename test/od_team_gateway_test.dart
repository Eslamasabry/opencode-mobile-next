// OD1 team lane: what each new control sends to a Gas City supervisor (the
// routes in contracts/gascity-supervisor-openapi-v0-3648ca2d499a.json), and
// that a bare supervisor (no host front) is refused instead of written to.
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/orchestration_gateway.dart';

import 'coverage/lists_support.dart' show withRealHttp;
import 'od_team_support.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('project controls: suspend, resume and delete routes', () async {
    final host = await OdTeam.boot();
    addTearDown(host.close);
    await withRealHttp(() async {
      final g = host.gateway;
      final a = await g.controlProject(
        'shopfront',
        ProjectControlAction.suspend,
        requestId: 'r1',
      );
      final b = await g.controlProject(
        'shopfront',
        ProjectControlAction.resume,
        requestId: 'r2',
      );
      final c = await g.controlProject(
        'shopfront',
        ProjectControlAction.remove,
        requestId: 'r3',
      );
      expect([a, b, c].every((r) => r.isAccepted), isTrue);
    });
    expect(
      [for (final w in host.writes) '${w.method} ${w.path}'],
      [
        'POST /v0/city/phone/rig/shopfront/suspend',
        'POST /v0/city/phone/rig/shopfront/resume',
        'DELETE /v0/city/phone/rig/shopfront',
      ],
    );
  });

  test('a bare supervisor refuses project controls', () async {
    final host = await OdTeam.boot(front: false);
    addTearDown(host.close);
    await withRealHttp(() async {
      final receipt = await host.gateway.controlProject(
        'shopfront',
        ProjectControlAction.suspend,
        requestId: 'r1',
      );
      expect(receipt.isAccepted, isFalse);
    });
    expect(host.writes, isEmpty);
    expect(host.gateway.capabilities.controlProject, isFalse);
  });
}
