// Look-gate pictures for the OD1 team lane: a screen drawn on a phone-sized
// view and written to build/coverage/od-team-<name>.png for the coordinator.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'coverage/paseo_coverage_support.dart' show writeCasePng;
import 'coverage/team_support.dart';

Future<void> odShot(WidgetTester tester, Widget home, String name) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  final boundary = GlobalKey();
  await tester.pumpWidget(RepaintBoundary(key: boundary, child: teamApp(home)));
  await teamSettle(tester);
  await writeCasePng(tester, boundary, 'od-team-$name');
}
