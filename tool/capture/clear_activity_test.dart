import 'dart:io';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart'
    show PendingQuestion, QuestionPrompt;
import 'package:opencode_mobile/ui/screens/activity_screen.dart';
import 'package:opencode_mobile/state/profiles.dart';

import '../../test/support/setup_capture_preferences.dart';
import 'fixtures.dart';

class _ActivityCaptureController extends CaptureController {
  _ActivityCaptureController(super.store, {required this.unknown});
  final bool unknown;
  @override
  bool get isConnected => true;
  @override
  int get unknownAttentionProfileCount => unknown ? 1 : 0;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);
  for (final state in ['clear', 'pending', 'unknown']) {
    for (final light in [false, true]) {
      for (final scale in [1.0, 2.0]) {
        testWidgets('activity $state ${light ? 'light' : 'dark'} ${scale}x', (
          tester,
        ) async {
          const secure = MethodChannel(
            'plugins.it_nomads.com/flutter_secure_storage',
          );
          tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
            secure,
            (call) async =>
                call.method == 'readAll' ? <String, String>{} : null,
          );
          addTearDown(
            () => tester.binding.defaultBinaryMessenger
                .setMockMethodCallHandler(secure, null),
          );
          tester.view.physicalSize = const Size(390, 844);
          tester.view.devicePixelRatio = 1;
          addTearDown(tester.view.resetPhysicalSize);
          addTearDown(tester.view.resetDevicePixelRatio);
          final prefs = await setupCapturePreferences();
          final controller = _ActivityCaptureController(
            ProfileStore(prefs: prefs),
            unknown: state == 'unknown',
          );
          if (state == 'pending') {
            controller.permissions = {
              'edit': PermissionRequest(
                id: 'edit',
                sessionID: 'checkout',
                permission: 'edit',
                patterns: ['lib/checkout.dart'],
              ),
            };
            controller.questions = {
              'direction': const PendingQuestion(
                id: 'direction',
                sessionID: 'checkout',
                prompts: [
                  QuestionPrompt(
                    title: 'Choose a direction',
                    question: 'Keep guest checkout available?',
                    multiple: false,
                    custom: true,
                    choices: [],
                  ),
                ],
              ),
            };
            controller.sessionsById = {
              'checkout': Session(id: 'checkout', title: 'Improve checkout'),
            };
            controller.busySessions = {'checkout'};
          }
          final boundary = GlobalKey();
          try {
            await tester.pumpWidget(
              captureApp(
                home: Builder(
                  builder: (context) => MediaQuery(
                    data: MediaQuery.of(
                      context,
                    ).copyWith(textScaler: TextScaler.linear(scale)),
                    child: Scaffold(
                      appBar: AppBar(title: const Text('Activity')),
                      body: ActivityScreen(
                        controller: controller,
                        embedded: true,
                      ),
                    ),
                  ),
                ),
                boundaryKey: boundary,
                controller: controller,
                light: light,
              ),
            );
            await tester.pump();
            await tester.pump(const Duration(milliseconds: 500));
            expect(tester.takeException(), isNull);
            final metrics = <String, Object?>{
              'logicalViewport': {'width': 390, 'height': 844},
              'textScale': scale,
              'state': state,
              'theme': light ? 'light' : 'dark',
              'phase':
                  Platform.environment['ACTIVITY_CAPTURE_PHASE'] ?? 'after',
            };
            for (final label in [
              'All clear',
              'All clear here',
              'Status incomplete',
              'Saved-server attention',
              'Saved servers',
              'Edit a file',
              'Completion digests',
            ]) {
              final finder = find.text(label);
              if (finder.evaluate().length == 1) {
                final rect = tester.getRect(finder);
                metrics[label] = {
                  'left': rect.left,
                  'top': rect.top,
                  'width': rect.width,
                  'height': rect.height,
                };
              }
            }
            final metricsFile = File(
              'docs/qa/clear-activity/${Platform.environment['ACTIVITY_CAPTURE_PHASE'] ?? 'after'}/$state-${light ? 'light' : 'dark'}-${scale.toInt()}x.json',
            );
            metricsFile.parent.createSync(recursive: true);
            metricsFile.writeAsStringSync(
              '${const JsonEncoder.withIndent('  ').convert(metrics)}\n',
            );
            await writePng(
              'docs/qa/clear-activity/${Platform.environment['ACTIVITY_CAPTURE_PHASE'] ?? 'after'}/$state-${light ? 'light' : 'dark'}-${scale.toInt()}x.png',
              await capturePng(tester, boundary, pixelRatio: 1),
            );
          } finally {
            await tester.pumpWidget(const SizedBox.shrink());
            controller.dispose();
            await tester.pump();
          }
        });
      }
    }
  }
}
