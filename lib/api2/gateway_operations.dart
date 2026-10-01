/// OpenCode 2 implementation of the protocol-neutral
/// [ServerOperationsGateway]: everything the product screens drive beyond
/// the live transport in `gateway.dart`.
///
/// Extends the v1 [ProductRepository] base so every operation with no v2
/// endpoint keeps the exact graceful "unavailable on this server"
/// [ProductException] the UI already renders; only genuinely supported
/// operations are overridden with real v2 calls.
library;

import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:dio/dio.dart' show CancelToken, Options, ProgressCallback;

import '../api/mcp_oauth.dart';
import '../api/models.dart';
import '../api/product_repository.dart' show ProductRepository;
import '../domain/server_gateway.dart';
import '../ui/kit/kit_redact.dart';
import '../domain/parallel_requests.dart';
import '../domain/session_title_text.dart';
import '../domain/plugin_inventory.dart';
import 'plugin_mapper.dart';
import 'client.dart';
import 'active_context_mapper.dart';
import 'gateway_mappers.dart';
import 'models.dart';
import 'transport.dart';

part 'gateway_operations/ops_extras.dart';
part 'gateway_operations/ops_shells.dart';
part 'gateway_operations/ops_sessions.dart';
part 'gateway_operations/ops_projects.dart';
part 'gateway_operations/ops_dev.dart';
part 'gateway_operations/ops_catalog.dart';
part 'gateway_operations/ops_integrations.dart';

class Api2OperationsGateway extends _Api2Core
    with
        _Api2ExtrasOps,
        _Api2ShellOps,
        _Api2SessionOps,
        _Api2ProjectOps,
        _Api2DevOps,
        _Api2CatalogOps,
        _Api2IntegrationOps
    implements
        StagedRevertGateway,
        SessionReadStateGateway,
        SessionNoteGateway,
        SessionExportGateway,
        SessionImportGateway,
        SessionSkillGateway,
        ActiveContextGateway,
        PluginGateway,
        McpRemovalGateway,
        IntegrationCredentialGateway,
        IntegrationCommandGateway,
        IntegrationAuthRecoveryGateway,
        UsageStatisticsGateway {
  Api2OperationsGateway({required super.client});
}

/// Shared state and helpers for the operation mixins in `gateway_operations/`.
abstract class _Api2Core extends ProductRepository
    implements
        StagedRevertGateway,
        SessionReadStateGateway,
        SessionNoteGateway,
        SessionExportGateway,
        SessionImportGateway,
        SessionSkillGateway,
        ActiveContextGateway,
        PluginGateway,
        McpRemovalGateway,
        IntegrationCredentialGateway,
        IntegrationCommandGateway,
        IntegrationAuthRecoveryGateway,
        UsageStatisticsGateway {
  final Api2Client client;

  _Api2Core({required this.client});

  Api2Transport get _transport => client.transport;

  String? get _directory => client.directory;

  @override
  void setLocation({String? directory, String? workspace}) =>
      client.setLocation(directory: directory, workspace: workspace);

  Map<String, dynamic> _loc([Map<String, dynamic> extra = const {}]) => {
    if (client.directory != null) 'location[directory]': client.directory,
    if (client.workspace != null) 'location[workspace]': client.workspace,
    ...extra,
  };
}

Future<T> _guard<T>(String message, Future<T> Function() action) async {
  try {
    return await action();
  } on ProductException {
    rethrow;
  } catch (error) {
    // ProductException's message is trusted presentation copy. Keep server
    // reasons, including Api2Error messages, only in the technical cause.
    throw ProductException(message, cause: error);
  }
}

Map<String, dynamic> _dataMap(dynamic json) {
  final data = json is Map<String, dynamic> ? json['data'] : null;
  return data is Map ? Map<String, dynamic>.from(data) : const {};
}

List<Map<String, dynamic>> _dataMaps(dynamic json) {
  final data = json is Map<String, dynamic> ? json['data'] : json;
  if (data is! List) return const [];
  return [
    for (final item in data)
      if (item is Map) Map<String, dynamic>.from(item),
  ];
}

String _basename(String path) {
  final normalized = path.endsWith('/')
      ? path.substring(0, path.length - 1)
      : path;
  final index = normalized.lastIndexOf('/');
  return index < 0 ? normalized : normalized.substring(index + 1);
}

/// PTY WebSocket channel: server→client binary frames are raw terminal
/// bytes, except a meta frame starting with 0x00 carrying `{"cursor": n}`;
/// client→server frames are raw input text.
class _Api2TerminalChannel implements TerminalChannel {
  final WebSocket _socket;
  int _cursor;
  late final Stream<String> _output = _socket
      .expand(_decodeFrame)
      .asBroadcastStream();

  _Api2TerminalChannel(this._socket, {required int initialCursor})
    : _cursor = initialCursor;

  Iterable<String> _decodeFrame(dynamic data) sync* {
    if (data is List<int> && data.isNotEmpty && data.first == 0) {
      try {
        final metadata = jsonDecode(utf8.decode(data.sublist(1)));
        final next = metadata is Map<String, dynamic>
            ? metadata['cursor']
            : null;
        if (next is int && next >= 0) _cursor = next;
      } catch (_) {
        // Invalid control frames are transport metadata, never terminal text.
      }
      return;
    }
    final text = data is String
        ? data
        : data is List<int>
        ? utf8.decode(data, allowMalformed: true)
        : data.toString();
    if (text.isEmpty) return;
    _cursor += data is List<int> ? data.length : utf8.encode(text).length;
    yield text;
  }

  @override
  Stream<String> get output => _output;

  @override
  int get cursor => _cursor;

  @override
  void write(String value) => _socket.add(value);

  @override
  Future<void> close() => _socket.close();
}
