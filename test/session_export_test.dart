import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api2/gateway_operations.dart';
import 'package:opencode_mobile/api2/transport.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/session_export_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'api2_interaction_gateway_test.dart'
    show withServer, gatewayFor, writeJson;
import '../tool/capture/fixtures.dart' show loadCaptureFonts, captureTheme;

class _Export extends ProductRepository implements SessionExportGateway {
  final bytes = Uint8List.fromList(utf8.encode('{"data":{"messages":[]}}'));
  final options = <bool>[];
  Completer<void>? wait;
  CancelToken? token;
  Object? error;
  @override
  bool get sessionExportSupported => true;
  @override
  Future<Uint8List> exportSession(
    String id, {
    bool sanitize = true,
    CancelToken? cancelToken,
    ProgressCallback? onReceiveProgress,
  }) async {
    options.add(sanitize);
    token = cancelToken;
    await wait?.future;
    if (error != null) throw error!;
    return bytes;
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _Controller extends ConnectionController {
  _Controller(super.store);
  @override
  Future<ServerOperationsGateway?> prepareActionRepository() async =>
      repository;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUp(() => SharedPreferences.setMockInitialValues({}));

  test(
    'exports complete response bytes with default redaction and no location',
    () async {
      final text =
          '{ "data": {"info": {"id":"ses_full"}, "messages": ['
          '${List.generate(5001, (i) => '{"text":"message $i"}').join(',')}]}}';
      await withServer(
        (server, requests) async {
          final connection = gatewayFor(server);
          addTearDown(connection.close);
          final gateway = Api2OperationsGateway(client: connection.client);
          final result = await gateway.exportSession('ses_full');
          expect(utf8.decode(result), text);
          expect(requests.single.uri.path, '/api/session/ses_full/export');
          expect(requests.single.uri.queryParameters, {'sanitize': 'true'});
          await gateway.exportSession('ses_full', sanitize: false);
          expect(requests.last.uri.queryParameters, {'sanitize': 'false'});
        },
        handler: (request) async {
          expect(request.headers.value('authorization'), startsWith('Basic '));
          request.response.headers.contentType = ContentType.json;
          request.response.write(text);
          await request.response.close();
        },
      );
    },
  );

  for (final status in [401, 404, 500]) {
    test(
      'byte error $status retains tag and does not disable export',
      () async {
        final tag = status == 404 ? 'SessionNotFoundError' : 'UnknownError';
        await withServer(
          (server, requests) async {
            final connection = gatewayFor(server);
            addTearDown(connection.close);
            final gateway = Api2OperationsGateway(client: connection.client);
            await expectLater(
              gateway.exportSession('ses_missing'),
              throwsA(
                isA<Api2Error>()
                    .having((e) => e.statusCode, 'status', status)
                    .having(
                      (e) => e.tag,
                      'tag',
                      status == 401 ? 'UnauthorizedError' : tag,
                    ),
              ),
            );
            expect(gateway.sessionExportSupported, isTrue);
          },
          handler: (request) =>
              writeJson(request, {'_tag': tag}, status: status),
        );
      },
    );
  }

  test('absent endpoint is remembered without repeated requests', () async {
    await withServer(
      (server, requests) async {
        final connection = gatewayFor(server);
        addTearDown(connection.close);
        final gateway = Api2OperationsGateway(client: connection.client);
        for (var i = 0; i < 2; i++) {
          await expectLater(
            gateway.exportSession('ses_x'),
            throwsA(isA<SessionExportUnsupported>()),
          );
        }
        expect(gateway.sessionExportSupported, isFalse);
        expect(requests, hasLength(1));
      },
      handler: (request) =>
          writeJson(request, {'_tag': 'NotFound'}, status: 404),
    );
  });

  Future<(_Controller, _Export)> screen(
    WidgetTester tester,
    SaveSessionExport save, {
    Size? size,
  }) async {
    if (size != null) {
      tester.view.physicalSize = size;
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
    }
    final gateway = _Export();
    final controller = _Controller(
      ProfileStore(prefs: await SharedPreferences.getInstance()),
    )..repository = gateway;
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      MaterialApp(
        home: SessionExportScreen(
          controller: controller,
          sessionID: 'ses_full',
          markdown: () => Uint8List.fromList(utf8.encode('loaded markdown')),
          saveFile: save,
        ),
      ),
    );
    return (controller, gateway);
  }

  testWidgets('JSON defaults to redaction and passes same buffer to saver', (
    tester,
  ) async {
    Uint8List? saved;
    final (_, gateway) = await screen(tester, (name, bytes, mime) async {
      expect(name, 'opencode-ses_full.json');
      expect(mime, 'application/json');
      saved = bytes;
      return Uri.file('/backup.json');
    }, size: const Size(411, 891));
    await tester.tap(find.text('Save file'));
    await tester.pumpAndSettle();
    expect(gateway.options, [true]);
    expect(identical(saved, gateway.bytes), isTrue);
    expect(find.text('Conversation saved'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Markdown uses loaded transcript without a server export', (
    tester,
  ) async {
    final (_, gateway) = await screen(tester, (name, bytes, mime) async {
      expect(name, endsWith('.md'));
      expect(utf8.decode(bytes), 'loaded markdown');
      return null;
    });
    await tester.tap(find.text('Readable transcript · Markdown'));
    await tester.pump();
    await tester.tap(find.text('Save file'));
    await tester.pumpAndSettle();
    expect(gateway.options, isEmpty);
    expect(find.text('Conversation saved'), findsNothing);
  });

  testWidgets('cancel and changed connection never open a save dialog', (
    tester,
  ) async {
    var saves = 0;
    final (controller, gateway) = await screen(tester, (
      name,
      bytes,
      mime,
    ) async {
      saves++;
      return null;
    });
    gateway.wait = Completer<void>();
    await tester.tap(find.text('Save file'));
    await tester.pump();
    await tester.tap(find.text('Cancel download'));
    await tester.pump();
    expect(gateway.token!.isCancelled, isTrue);
    gateway.wait!.complete();
    await tester.pumpAndSettle();
    expect(saves, 0);
    gateway.wait = Completer<void>();
    await tester.tap(find.text('Save file'));
    await tester.pump();
    controller.repository = _Export();
    gateway.wait!.complete();
    await tester.pumpAndSettle();
    expect(saves, 0);
    expect(
      find.textContaining('connection or location changed'),
      findsOneWidget,
    );
  });

  testWidgets('failed export keeps options and allows retry', (tester) async {
    final (_, gateway) = await screen(
      tester,
      (name, bytes, mime) async => Uri.file('/out'),
    );
    gateway.error = const Api2AuthRequired('not authorized');
    await tester.tap(find.text('Save file'));
    await tester.pumpAndSettle();
    expect(find.textContaining('server denied access'), findsOneWidget);
    gateway.error = null;
    await tester.tap(find.text('Save file'));
    await tester.pumpAndSettle();
    expect(gateway.options, [true, true]);
    expect(find.text('Conversation saved'), findsOneWidget);
  });

  for (final width in [411.0, 320.0]) {
    testWidgets('export layout at $width with reachable redaction and save', (
      tester,
    ) async {
      tester.view.physicalSize = Size(width, 891);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      await tester.runAsync(loadCaptureFonts);
      final controller = _Controller(
        ProfileStore(prefs: await SharedPreferences.getInstance()),
      );
      final gateway = _Export();
      controller.repository = gateway;
      addTearDown(controller.dispose);
      final boundary = GlobalKey();
      await tester.pumpWidget(
        MaterialApp(
          theme: captureTheme(light: true),
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(
              context,
            ).copyWith(textScaler: TextScaler.linear(width == 320 ? 1.7 : 1)),
            child: child!,
          ),
          home: RepaintBoundary(
            key: boundary,
            child: SessionExportScreen(
              controller: controller,
              sessionID: 'ses_preview',
              markdown: () => Uint8List(0),
              saveFile: (_, _, _) async => null,
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Save file').hitTestable(), findsOneWidget);
      final preview = Platform.environment['OC_EXPORT_PREVIEW'];
      if (preview != null && width == 411) {
        await tester.runAsync(() async {
          final render =
              boundary.currentContext!.findRenderObject()
                  as RenderRepaintBoundary;
          final image = await render.toImage(pixelRatio: 1);
          final bytes = await image.toByteData(format: ui.ImageByteFormat.png);
          await File(preview).writeAsBytes(bytes!.buffer.asUint8List());
          image.dispose();
        });
      }
      await tester.scrollUntilVisible(
        find.byType(SwitchListTile),
        200,
        scrollable: find.byType(Scrollable),
      );
      await tester.ensureVisible(find.byType(SwitchListTile));
      await tester.pumpAndSettle();
      await tester.tap(find.byType(SwitchListTile));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Save file'));
      await tester.pumpAndSettle();
      expect(gateway.options, [false]);
      expect(tester.takeException(), isNull);
    });
  }
}
