import 'dart:convert';

import 'package:dio/dio.dart' show CancelToken;

import 'models.dart';
import 'transport.dart';

/// Typed client for the OpenCode 2 server API (Phase-1 read + basic-write
/// surface). Location-scoped endpoints automatically carry the pinned
/// `location[directory]`/`location[workspace]` query params.
class Api2Client {
  final Api2Transport transport;
  String? _directory;
  String? _workspace;

  Api2Client({required this.transport, String? directory, String? workspace})
    : _directory = directory,
      _workspace = workspace;

  Api2Client.connect({
    required String baseUrl,
    required String password,
    String? directory,
    String? workspace,
  }) : this(
         transport: Api2Transport(baseUrl: baseUrl, password: password),
         directory: directory,
         workspace: workspace,
       );

  String? get directory => _directory;
  String? get workspace => _workspace;

  void setLocation({String? directory, String? workspace}) {
    _directory = directory;
    _workspace = workspace;
  }

  void close() => transport.close();

  Map<String, dynamic> _loc([Map<String, dynamic> values = const {}]) => {
    if (_directory != null) 'location[directory]': _directory,
    if (_workspace != null) 'location[workspace]': _workspace,
    ...values,
  };

  Map<String, dynamic>? _locationBody() => _directory == null
      ? null
      : {
          'directory': _directory,
          if (_workspace != null) 'workspaceID': _workspace,
        };

  static dynamic _data(dynamic json) =>
      json is Map<String, dynamic> ? json['data'] : null;

  static List<T> _dataList<T>(
    dynamic json,
    T? Function(Map<String, dynamic>) parse,
  ) {
    final out = <T>[];
    final data = _data(json);
    if (data is List) {
      for (final item in data) {
        if (item is! Map) continue;
        try {
          final parsed = parse(Map<String, dynamic>.from(item));
          if (parsed != null) out.add(parsed);
        } catch (_) {}
      }
    }
    return out;
  }

  // ---------------- Server ----------------

  Future<Api2Health> health({CancelToken? cancelToken}) =>
      transport.health(cancelToken: cancelToken);

  Future<Api2Health> checkServer({CancelToken? cancelToken}) =>
      transport.checkServer(cancelToken: cancelToken);

  Future<Api2ServerInfo> serverInfo() async {
    final json = await transport.getJson('/server');
    return Api2ServerInfo.fromJson(json is Map<String, dynamic> ? json : const {});
  }

  /// Resolves the pinned location to its project.
  Future<Api2Location?> location() async {
    final json = await transport.getJson('/location', query: _loc());
    return Api2Location.fromJson(json);
  }

  // ---------------- Sessions ----------------

  Future<Api2Page<Api2Session>> sessions({
    String? directory,
    String? project,
    String? subpath,
    String? workspace,
    int? limit,
    String? order,
    String? search,
    String? parentID,
    bool rootsOnly = false,
    String? cursor,
    CancelToken? cancelToken,
  }) async {
    final json = await transport.getJson(
      '/session',
      query: cursor != null
          ? {'cursor': cursor}
          : {
              'directory': directory ?? (project == null ? _directory : null),
              'project': project,
              'subpath': subpath,
              'workspace': workspace ?? _workspace,
              'limit': limit,
              'order': order,
              'search': search,
              'parentID': rootsOnly ? 'null' : parentID,
            },
      cancelToken: cancelToken,
    );
    return Api2Page.fromJson(json, Api2Session.fromJson);
  }

  Future<Api2Session> session(String sessionID) async {
    final json = await transport.getJson('/session/$sessionID');
    final session = Api2Session.fromJson(_asDataMap(json));
    if (session == null) {
      throw Api2RequestError('GET /session/$sessionID returned no session');
    }
    return session;
  }

  Future<Api2Session> createSession({
    String? id,
    String? title,
    String? agent,
    Api2ModelRef? model,
    Map<String, dynamic>? metadata,
  }) async {
    final json = await transport.postJson(
      '/session',
      body: {
        'id': ?id,
        'title': ?title,
        'agent': ?agent,
        if (model != null) 'model': model.toJson(),
        'metadata': ?metadata,
        'location': ?_locationBody(),
      },
    );
    final session = Api2Session.fromJson(_asDataMap(json));
    if (session == null) {
      throw Api2RequestError('POST /session returned no session');
    }
    return session;
  }

  Future<void> deleteSession(String sessionID) =>
      transport.deleteJson('/session/$sessionID');

  Future<void> renameSession(String sessionID, String title) =>
      transport.postJson('/session/$sessionID/rename', body: {'title': title});

  /// Sessions with a live execution owned by this server process:
  /// `{sessionID: statusType}`.
  Future<Map<String, String>> activeSessions() async {
    final json = await transport.getJson('/session/active');
    final data = _data(json);
    final out = <String, String>{};
    if (data is Map) {
      data.forEach((key, value) {
        final type = value is Map ? value['type']?.toString() : null;
        out[key.toString()] = type ?? 'running';
      });
    }
    return out;
  }

  Future<void> switchAgent(String sessionID, String agent) =>
      transport.postJson('/session/$sessionID/agent', body: {'agent': agent});

  Future<void> switchModel(String sessionID, Api2ModelRef model) => transport
      .postJson('/session/$sessionID/model', body: {'model': model.toJson()});

  /// Long-polls until the session's agent loop becomes idle.
  Future<void> wait(String sessionID, {CancelToken? cancelToken}) =>
      transport.postJson(
        '/session/$sessionID/wait',
        cancelToken: cancelToken,
        receiveTimeout: Api2Transport.longPollTimeout,
      );

  /// Returns whether a running turn was actually interrupted.
  Future<bool> interrupt(String sessionID, {bool resumePending = false}) async {
    final json = await transport.postJson(
      '/session/$sessionID/interrupt',
      query: resumePending ? {'continue': 'true'} : null,
    );
    return json is Map && json['interrupted'] == true;
  }

  // ---------------- Messages ----------------

  Future<Api2Page<Api2Message>> messages(
    String sessionID, {
    int? limit,
    String? order,
    String? cursor,
    CancelToken? cancelToken,
  }) async {
    final json = await transport.getJson(
      '/session/$sessionID/message',
      query: cursor != null
          ? {'cursor': cursor}
          : {'limit': limit, 'order': order},
      cancelToken: cancelToken,
    );
    return Api2Page.fromJson(json, Api2Message.fromJson);
  }

  Future<Api2Message> message(String sessionID, String messageID) async {
    final json = await transport.getJson(
      '/session/$sessionID/message/$messageID',
    );
    final message = Api2Message.fromJson(_asDataMap(json));
    if (message == null) {
      throw Api2RequestError(
        'GET /session/$sessionID/message/$messageID returned no message',
      );
    }
    return message;
  }

  // ---------------- Prompting ----------------

  /// Admits a prompt to the session inbox and schedules the agent loop.
  /// Returns the inbox receipt, not the assistant message — completion is
  /// observed via events or [wait].
  Future<Api2InboxItem> prompt(
    String sessionID, {
    String? id,
    required String text,
    List<Api2PromptFile> files = const [],
    List<Api2PromptAgentMention> agents = const [],
    List<Api2PromptSkillMention> skills = const [],
    Map<String, dynamic>? metadata,
    Api2Delivery? delivery,
    bool? resume,
  }) async {
    final json = await transport.postJson(
      '/session/$sessionID/prompt',
      body: {
        'id': ?id,
        'text': text,
        if (files.isNotEmpty) 'files': files.map((f) => f.toJson()).toList(),
        if (agents.isNotEmpty)
          'agents': agents.map((a) => a.toJson()).toList(),
        if (skills.isNotEmpty)
          'skills': skills.map((s) => s.toJson()).toList(),
        'metadata': ?metadata,
        if (delivery != null) 'delivery': delivery.wire,
        'resume': ?resume,
      },
    );
    final item = Api2InboxItem.fromJson(_asDataMap(json));
    if (item == null) {
      throw Api2RequestError(
        'POST /session/$sessionID/prompt returned no inbox receipt',
      );
    }
    return item;
  }

  /// Builds a `data:<mime>;base64,...` attachment from raw bytes.
  static Api2PromptFile inlineAttachment(
    List<int> bytes, {
    required String mime,
    String? name,
    String? description,
  }) => Api2PromptFile(
    uri: 'data:$mime;base64,${base64Encode(bytes)}',
    name: name,
    description: description,
  );

  /// References a file that already exists on the server host.
  static Api2PromptFile serverFileAttachment(
    String absolutePath, {
    String? name,
    int? startLine,
    int? endLine,
  }) {
    final range = startLine != null
        ? '?start=$startLine${endLine != null ? '&end=$endLine' : ''}'
        : '';
    return Api2PromptFile(
      uri: 'file://$absolutePath$range',
      name: name ?? absolutePath.split('/').last,
    );
  }

  // ---------------- Models / providers / agents ----------------

  Future<List<Api2ModelInfo>> models() async {
    final json = await transport.getJson('/model', query: _loc());
    return _dataList(json, Api2ModelInfo.fromJson);
  }

  Future<Api2ModelInfo?> defaultModel() async {
    final json = await transport.getJson('/model/default', query: _loc());
    final data = _data(json);
    if (data is! Map) return null;
    return Api2ModelInfo.fromJson(Map<String, dynamic>.from(data));
  }

  Future<List<Api2ProviderInfo>> providers() async {
    final json = await transport.getJson('/provider', query: _loc());
    return _dataList(json, Api2ProviderInfo.fromJson);
  }

  Future<List<Api2AgentInfo>> agents() async {
    final json = await transport.getJson('/agent', query: _loc());
    return _dataList(json, Api2AgentInfo.fromJson);
  }

  // ---------------- Permissions ----------------

  /// All pending permission requests for the pinned location.
  Future<List<Api2PermissionRequest>> pendingPermissions() async {
    final json = await transport.getJson('/permission/request', query: _loc());
    return _dataList(json, Api2PermissionRequest.fromJson);
  }

  Future<List<Api2PermissionRequest>> sessionPermissions(
    String sessionID,
  ) async {
    final json = await transport.getJson('/session/$sessionID/permission');
    return _dataList(json, Api2PermissionRequest.fromJson);
  }

  Future<void> replyPermission(
    String sessionID,
    String requestID,
    Api2PermissionReply reply, {
    String? message,
  }) => transport.postJson(
    '/session/$sessionID/permission/$requestID/reply',
    body: {'reply': reply.wire, 'message': ?message},
  );

  // ---------------- Forms ----------------

  /// All pending forms for the pinned location.
  Future<List<Api2FormInfo>> pendingForms() async {
    final json = await transport.getJson('/form/request', query: _loc());
    return _dataList(json, Api2FormInfo.fromJson);
  }

  Future<List<Api2FormInfo>> sessionForms(String sessionID) async {
    final json = await transport.getJson('/session/$sessionID/form');
    return _dataList(json, Api2FormInfo.fromJson);
  }

  Future<Api2FormInfo> form(String sessionID, String formID) async {
    final json = await transport.getJson('/session/$sessionID/form/$formID');
    final form = Api2FormInfo.fromJson(_asDataMap(json));
    if (form == null) {
      throw Api2RequestError(
        'GET /session/$sessionID/form/$formID returned no form',
      );
    }
    return form;
  }

  Future<Api2FormState> formState(String sessionID, String formID) async {
    final json = await transport.getJson(
      '/session/$sessionID/form/$formID/state',
    );
    return Api2FormState.fromJson(_data(json));
  }

  Future<void> replyForm(
    String sessionID,
    String formID,
    Map<String, dynamic> answer,
  ) => transport.postJson(
    '/session/$sessionID/form/$formID/reply',
    body: {'answer': answer},
  );

  Future<void> cancelForm(String sessionID, String formID) =>
      transport.postJson('/session/$sessionID/form/$formID/cancel');

  // ---------------- Filesystem ----------------

  /// Raw bytes of a file relative to the pinned location.
  Future<List<int>> fsReadBytes(String relativePath) {
    final encoded = relativePath
        .split('/')
        .map(Uri.encodeComponent)
        .join('/');
    return transport.getBytes('/fs/read/$encoded', query: _loc());
  }

  Future<String> fsRead(String relativePath) async =>
      utf8.decode(await fsReadBytes(relativePath), allowMalformed: true);

  Future<List<Api2FsEntry>> fsList(String path) async {
    final json = await transport.getJson(
      '/fs/list',
      query: _loc({'path': path}),
    );
    return _dataList(json, Api2FsEntry.fromJson);
  }

  Future<List<Api2FsEntry>> fsFind(
    String query, {
    String? type,
    int? limit,
  }) async {
    final json = await transport.getJson(
      '/fs/find',
      query: _loc({'query': query, 'type': type, 'limit': limit}),
    );
    return _dataList(json, Api2FsEntry.fromJson);
  }

  // ---------------- Catalog ----------------

  Future<List<Api2Command>> commands() async {
    final json = await transport.getJson('/command', query: _loc());
    return _dataList(json, Api2Command.fromJson);
  }

  Future<List<Api2Skill>> skills() async {
    final json = await transport.getJson('/skill', query: _loc());
    return _dataList(json, Api2Skill.fromJson);
  }

  /// Priority-ordered (lowest to highest) config entry list; read-only in v2.
  Future<List<Api2ConfigEntry>> config() async {
    final json = await transport.getJson('/config', query: _loc());
    final out = <Api2ConfigEntry>[];
    final entries = json is List ? json : _data(json);
    if (entries is List) {
      for (final item in entries) {
        if (item is! Map) continue;
        final entry = Api2ConfigEntry.fromJson(
          Map<String, dynamic>.from(item),
        );
        if (entry != null) out.add(entry);
      }
    }
    return out;
  }

  static Map<String, dynamic> _asDataMap(dynamic json) {
    final data = _data(json);
    return data is Map ? Map<String, dynamic>.from(data) : const {};
  }
}
