import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart'
    show ModelRef, SessionSelection;
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api2/gateway.dart';
import 'package:opencode_mobile/api2/gateway_events.dart';
import 'package:opencode_mobile/api2/gateway_mappers.dart'
    show api2ServerCapabilities;
import 'package:opencode_mobile/api2/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';

class _RealHttpOverrides extends HttpOverrides {}

class Recorded {
  final String method;
  final Uri uri;
  final dynamic body;
  Recorded(this.method, this.uri, this.body);
}

Future<void> withServer(
  Future<void> Function(HttpServer server, List<Recorded> requests) body, {
  required Future<void> Function(HttpRequest request) handler,
}) async {
  await HttpOverrides.runZoned(() async {
    final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    final requests = <Recorded>[];
    server.listen((request) async {
      final text = await utf8.decoder.bind(request).join();
      requests.add(
        Recorded(
          request.method,
          request.uri,
          text.isEmpty ? null : jsonDecode(text),
        ),
      );
      await handler(request);
    });
    try {
      await body(server, requests);
    } finally {
      await server.close(force: true);
    }
  }, createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null));
}

Api2Gateway gatewayFor(HttpServer server) => Api2Gateway.connect(
  baseUrl: 'http://${server.address.host}:${server.port}',
  password: 'pw',
  directory: '/home/dev/projects/oc_app',
);

Future<void> writeJson(
  HttpRequest request,
  Object? payload, {
  int status = 200,
}) async {
  request.response.statusCode = status;
  request.response.headers.contentType = ContentType.json;
  if (payload != null) request.response.write(jsonEncode(payload));
  await request.response.close();
}

Future<void> writeNoContent(HttpRequest request) async {
  request.response.statusCode = 204;
  await request.response.close();
}

dynamic fixture(String name) =>
    jsonDecode(File('test/fixtures/api2/$name').readAsStringSync());

const _session = 'ses_fb3c6f68cffe268QQhp3ZL6V8f';
const _form = 'frm_04c3909aa001rDGq3xT0IiMZ0B';
const _inboxID = 'msg_04c392e26001fDTa7Nswnl15Sq';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test(
    'ordinary v2 prompt command and shell preserve server selection',
    () async {
      await withServer(
        handler: (request) => request.uri.path.endsWith('/prompt')
            ? writeJson(request, fixture('prompt_receipt.json'))
            : writeNoContent(request),
        (server, requests) async {
          final gateway = gatewayFor(server);
          addTearDown(gateway.close);
          final model = ModelRef(providerID: 'p', modelID: 'old-default');
          await gateway.promptAsync(
            _session,
            text: 'Hello',
            model: model,
            agent: 'plan',
            variant: 'high',
          );
          await gateway.slashCommand(
            _session,
            '/review',
            'changes',
            model: model,
            variant: 'high',
          );
          await gateway.shell(
            _session,
            command: 'pwd',
            agent: 'build',
            model: model,
          );
          expect(requests.map((r) => r.uri.path), [
            '/api/session/$_session/prompt',
            '/api/session/$_session/command',
            '/api/session/$_session/shell',
          ]);
          for (final request in requests) {
            expect((request.body as Map).containsKey('model'), isFalse);
            expect((request.body as Map).containsKey('agent'), isFalse);
          }
        },
      );
    },
  );

  test(
    'v2 explicit selection writes replace the model ref and creation uses defaults',
    () async {
      await withServer(
        handler: (request) async {
          if (request.uri.path == '/api/session') {
            await writeJson(request, {
              'data': {
                'id': 'new',
                'agent': 'plan',
                'model': {'providerID': 'p', 'id': 'chosen', 'variant': 'high'},
              },
            });
          } else {
            await writeNoContent(request);
          }
        },
        (server, requests) async {
          final gateway = gatewayFor(server);
          addTearDown(gateway.close);
          final model = ModelRef(providerID: 'p', modelID: 'chosen');
          await gateway.setSessionModel(_session, model, '');
          await gateway.setSessionAgent(_session, 'plan');
          final session = await gateway.createSelectedSession(
            SessionSelection(model: model, variant: 'high', agent: 'plan'),
          );
          expect(requests[0].body, {
            'model': {'providerID': 'p', 'id': 'chosen'},
          });
          expect(requests[1].body, {'agent': 'plan'});
          expect(requests[2].body['model'], {
            'providerID': 'p',
            'id': 'chosen',
            'variant': 'high',
          });
          expect(requests[2].body['location'], {
            'directory': '/home/dev/projects/oc_app',
          });
          expect(session.selection!.variant, 'high');
          expect(session.selection!.agent, 'plan');
        },
      );
    },
  );

  group('FormGateway (v2)', () {
    test(
      'lists a session\'s pending forms from the captured payload',
      () async {
        await withServer(
          handler: (request) async {
            expect(request.uri.path, '/api/session/$_session/form');
            await writeJson(request, fixture('forms_session.json'));
          },
          (server, requests) async {
            final gateway = gatewayFor(server);
            final forms = await gateway.sessionForms(_session);
            expect(forms, hasLength(1));
            final form = forms.single;
            expect(form.id, _form);
            expect(form.sessionID, _session);
            expect(form.title, 'Connect to Sentry');
            expect(form.fields, hasLength(4));
            expect(form.fields.first.options, hasLength(2));
            // The conditional field parses its `when` clause.
            final reason = form.fields.last;
            expect(reason.key, 'reason');
            expect(reason.when.single.key, 'confirm');
            expect(reason.activeFor({'confirm': true}), isTrue);
            expect(reason.activeFor({'confirm': false}), isFalse);
            gateway.close();
          },
        );
      },
    );

    test(
      'reads form state and replies through the session-scoped routes',
      () async {
        await withServer(
          handler: (request) async {
            if (request.uri.path.endsWith('/state')) {
              await writeJson(request, fixture('form_state_pending.json'));
              return;
            }
            await writeNoContent(request);
          },
          (server, requests) async {
            final gateway = gatewayFor(server);
            final state = await gateway.formState(_session, _form);
            expect(state.status, Api2FormStatus.pending);
            await gateway.replyForm(_session, _form, {'env': 'prod'});
            await gateway.cancelForm(_session, _form);
            expect(requests[0].method, 'GET');
            expect(
              requests[0].uri.path,
              '/api/session/$_session/form/$_form/state',
            );
            expect(requests[1].method, 'POST');
            expect(
              requests[1].uri.path,
              '/api/session/$_session/form/$_form/reply',
            );
            expect(requests[1].body, {
              'answer': {'env': 'prod'},
            });
            expect(requests[2].method, 'POST');
            expect(
              requests[2].uri.path,
              '/api/session/$_session/form/$_form/cancel',
            );
            gateway.close();
          },
        );
      },
    );

    test(
      'surfaces 400 FormInvalidAnswerError as a tagged ApiException',
      () async {
        await withServer(
          handler: (request) async {
            await writeJson(
              request,
              fixture('error_form_invalid_answer.json'),
              status: 400,
            );
          },
          (server, requests) async {
            final gateway = gatewayFor(server);
            try {
              await gateway.replyForm(_session, _form, {'env': 'nope'});
              fail('expected an ApiException');
            } on ApiException catch (error) {
              expect(error.statusCode, 400);
              expect(error.errorTag, 'FormInvalidAnswerError');
              expect(error.message, contains('Invalid option'));
            }
            gateway.close();
          },
        );
      },
    );

    test('surfaces 409 FormAlreadySettledError with its tag', () async {
      await withServer(
        handler: (request) async {
          await writeJson(request, {
            '_tag': 'FormAlreadySettledError',
            'id': _form,
            'message': 'Form already settled',
          }, status: 409);
        },
        (server, requests) async {
          final gateway = gatewayFor(server);
          try {
            await gateway.replyForm(_session, _form, {'env': 'prod'});
            fail('expected an ApiException');
          } on ApiException catch (error) {
            expect(error.statusCode, 409);
            expect(error.errorTag, 'FormAlreadySettledError');
          }
          gateway.close();
        },
      );
    });
  });

  group('InboxGateway (v2)', () {
    test('lists pending inbox items from the captured payload', () async {
      await withServer(
        handler: (request) async {
          expect(request.uri.path, '/api/session/$_session/inbox');
          await writeJson(request, fixture('inbox_list.json'));
        },
        (server, requests) async {
          final gateway = gatewayFor(server);
          final items = await gateway.inboxItems(_session);
          expect(items, hasLength(1));
          final item = items.single;
          expect(item.id, _inboxID);
          expect(item.type, 'user');
          expect(item.promptText, 'queued while form pending');
          expect(item.delivery, Api2Delivery.queue);
          gateway.close();
        },
      );
    });

    test('steer, queue, and cancel address the item routes', () async {
      await withServer(handler: writeNoContent, (server, requests) async {
        final gateway = gatewayFor(server);
        await gateway.steerInboxItem(_session, _inboxID);
        await gateway.queueInboxItem(_session, _inboxID);
        await gateway.cancelInboxItem(_session, _inboxID);
        expect(requests[0].method, 'POST');
        expect(
          requests[0].uri.path,
          '/api/session/$_session/inbox/$_inboxID/steer',
        );
        expect(requests[1].method, 'POST');
        expect(
          requests[1].uri.path,
          '/api/session/$_session/inbox/$_inboxID/queue',
        );
        expect(requests[2].method, 'DELETE');
        expect(requests[2].uri.path, '/api/session/$_session/inbox/$_inboxID');
        gateway.close();
      });
    });

    test(
      'surfaces 409 ConflictError when an item was already delivered',
      () async {
        await withServer(
          handler: (request) async {
            await writeJson(request, {
              '_tag': 'ConflictError',
              'message': 'Pending input can no longer be cancelled: $_inboxID',
              'resource': _inboxID,
            }, status: 409);
          },
          (server, requests) async {
            final gateway = gatewayFor(server);
            try {
              await gateway.cancelInboxItem(_session, _inboxID);
              fail('expected an ApiException');
            } on ApiException catch (error) {
              expect(error.statusCode, 409);
              expect(error.errorTag, 'ConflictError');
            }
            gateway.close();
          },
        );
      },
    );
  });

  group('prompt delivery + permission message (v2)', () {
    test('promptAsync carries the queue delivery to the wire', () async {
      await withServer(
        handler: (request) async {
          await writeJson(request, fixture('prompt_receipt.json'));
        },
        (server, requests) async {
          final gateway = gatewayFor(server);
          await gateway.promptAsync(
            _session,
            text: 'later please',
            delivery: PromptDelivery.queue,
          );
          final body = requests.single.body as Map;
          expect(body['delivery'], 'queue');
          gateway.close();
        },
      );
    });

    test(
      'promptAsync omits delivery when unspecified (server default steer)',
      () async {
        await withServer(
          handler: (request) async {
            await writeJson(request, fixture('prompt_receipt.json'));
          },
          (server, requests) async {
            final gateway = gatewayFor(server);
            await gateway.promptAsync(_session, text: 'now');
            final body = requests.single.body as Map;
            expect(body.containsKey('delivery'), isFalse);
            gateway.close();
          },
        );
      },
    );

    test('respondPermissionV2 forwards the reject message', () async {
      await withServer(handler: writeNoContent, (server, requests) async {
        final gateway = gatewayFor(server);
        await gateway.respondPermissionV2(
          _session,
          'per_x',
          'reject',
          message: 'use the staging db instead',
        );
        expect(
          requests.single.uri.path,
          '/api/session/$_session/permission/per_x/reply',
        );
        expect(requests.single.body, {
          'reply': 'reject',
          'message': 'use the staging db instead',
        });
        gateway.close();
      });
    });

    test('respondPermissionV2 omits an absent message', () async {
      await withServer(handler: writeNoContent, (server, requests) async {
        final gateway = gatewayFor(server);
        await gateway.respondPermissionV2(_session, 'per_x', 'once');
        expect(requests.single.body, {'reply': 'once'});
        gateway.close();
      });
    });
  });

  group('v1 gateway stays inert', () {
    final api = OpenCodeApi(baseUrl: 'http://127.0.0.1:1');

    test('capability flags are off', () {
      expect(api.capabilities.forms, isFalse);
      expect(api.capabilities.inbox, isFalse);
      expect(api2ServerCapabilities.forms, isTrue);
      expect(api2ServerCapabilities.inbox, isTrue);
    });

    test('form and inbox lists come back empty without any request', () async {
      expect(await api.sessionForms('ses_x'), isEmpty);
      expect(await api.pendingForms(), isEmpty);
      expect(await api.inboxItems('ses_x'), isEmpty);
    });

    test('form and inbox mutations fail with a typed unavailable error', () {
      expect(
        () => api.replyForm('ses_x', 'frm_x', const {}),
        throwsA(isA<ProductException>()),
      );
      expect(
        () => api.cancelForm('ses_x', 'frm_x'),
        throwsA(isA<ProductException>()),
      );
      expect(
        () => api.formState('ses_x', 'frm_x'),
        throwsA(isA<ProductException>()),
      );
      expect(
        () => api.cancelInboxItem('ses_x', 'msg_x'),
        throwsA(isA<ProductException>()),
      );
      expect(
        () => api.steerInboxItem('ses_x', 'msg_x'),
        throwsA(isA<ProductException>()),
      );
      expect(
        () => api.queueInboxItem('ses_x', 'msg_x'),
        throwsA(isA<ProductException>()),
      );
    });
  });

  group('event adaptation', () {
    test('inbox events pass through under their v2 types', () {
      final adapter = Api2EventAdapter();
      final enqueued = adaptApi2EventJson(adapter, {
        'type': 'session.inbox.enqueued',
        'created': 1787985210919,
        'data': {
          'sessionID': _session,
          'inboxID': _inboxID,
          'item': {
            'type': 'user',
            'payload': {'text': 'queued while form pending'},
            'delivery': 'queue',
          },
        },
      });
      expect(
        enqueued.map((event) => event.type),
        containsAll(['session.inbox.enqueued', 'message.updated']),
      );
      final passthrough = enqueued.singleWhere(
        (event) => event.type == 'session.inbox.enqueued',
      );
      expect(passthrough.properties['inboxID'], _inboxID);
      expect((passthrough.properties['item'] as Map)['delivery'], 'queue');

      final delivered = adaptApi2EventJson(adapter, {
        'type': 'session.inbox.delivered',
        'data': {'sessionID': _session, 'inboxID': _inboxID},
      });
      expect(delivered.single.type, 'session.inbox.delivered');
      expect(delivered.single.properties['inboxID'], _inboxID);

      final changed = adaptApi2EventJson(adapter, {
        'type': 'session.inbox.delivery.changed',
        'data': {
          'sessionID': _session,
          'inboxID': _inboxID,
          'delivery': 'steer',
        },
      });
      expect(changed.single.type, 'session.inbox.delivery.changed');
      expect(changed.single.properties['delivery'], 'steer');

      final cancelled = adaptApi2EventJson(adapter, {
        'type': 'session.inbox.cancelled',
        'data': {'sessionID': _session, 'inboxID': _inboxID},
      });
      expect(cancelled.single.type, 'session.inbox.cancelled');
    });

    test('non-user inbox items pass through without a message echo', () {
      final adapter = Api2EventAdapter();
      final events = adaptApi2EventJson(adapter, {
        'type': 'session.inbox.enqueued',
        'data': {
          'sessionID': _session,
          'inboxID': 'msg_synth',
          'item': {
            'type': 'synthetic',
            'payload': {'text': 'ctx'},
          },
        },
      });
      expect(events.single.type, 'session.inbox.enqueued');
    });

    test('form events surface as form.v2.* envelopes', () {
      final adapter = Api2EventAdapter();
      final formJson = (fixture('forms_session.json') as Map)['data'][0] as Map;
      final created = adaptApi2EventJson(adapter, {
        'type': 'form.created',
        'data': {'form': formJson},
      });
      expect(created.single.type, 'form.v2.created');
      final parsed = Api2FormInfo.fromJson(
        Map<String, dynamic>.from(created.single.properties['form'] as Map),
      );
      expect(parsed!.id, _form);
      expect(parsed.fields, hasLength(4));

      final replied = adaptApi2EventJson(adapter, {
        'type': 'form.replied',
        'data': {
          'id': _form,
          'sessionID': _session,
          'answer': {'env': 'prod'},
        },
      });
      expect(replied.single.type, 'form.v2.replied');
      expect(replied.single.properties, {'id': _form, 'sessionID': _session});

      final cancelledForm = adaptApi2EventJson(adapter, {
        'type': 'form.cancelled',
        'data': {'id': _form, 'sessionID': _session},
      });
      expect(cancelledForm.single.type, 'form.v2.cancelled');
    });
  });
}
