enum ExternalAgentAuth { none, bearer }

enum ExternalAgentIssue {
  address,
  unsupported,
  authentication,
  unavailable,
  invalidResponse,
  uncertain,
  storage,
  scope,
}

class ExternalAgentException implements Exception {
  final ExternalAgentIssue issue;
  const ExternalAgentException(this.issue);
  @override
  String toString() => 'External agent operation failed (${issue.name}).';
}

class ExternalAgentSkill {
  final String name;
  final String description;
  const ExternalAgentSkill(this.name, this.description);
  Map<String, dynamic> toJson() => {'name': name, 'description': description};
}

class ExternalAgentCard {
  final String name;
  final String description;
  final String cardUrl;
  final String endpoint;
  final String version;
  final ExternalAgentAuth auth;
  final bool supported;
  final List<ExternalAgentSkill> skills;
  const ExternalAgentCard({
    required this.name,
    required this.description,
    required this.cardUrl,
    required this.endpoint,
    required this.version,
    required this.auth,
    required this.supported,
    required this.skills,
  });
  Map<String, dynamic> toJson() => {
    'name': name,
    'description': description,
    'cardUrl': cardUrl,
    'endpoint': endpoint,
    'version': version,
    'auth': auth.name,
    'supported': supported,
    'skills': skills.map((s) => s.toJson()).toList(),
  };
  factory ExternalAgentCard.fromJson(Map<String, dynamic> j) =>
      ExternalAgentCard(
        name: j['name'] as String,
        description: j['description'] as String,
        cardUrl: j['cardUrl'] as String,
        endpoint: j['endpoint'] as String,
        version: j['version'] as String,
        auth: ExternalAgentAuth.values.byName(j['auth'] as String),
        supported: j['supported'] == true,
        skills: [
          for (final s in j['skills'] as List)
            ExternalAgentSkill(s['name'] as String, s['description'] as String),
        ],
      );
}

class ExternalAgentProfile {
  final String id;
  final ExternalAgentCard card;
  final bool deleting;
  const ExternalAgentProfile(this.id, this.card, {this.deleting = false});
  Map<String, dynamic> toJson() => {
    'id': id,
    'card': card.toJson(),
    'deleting': deleting,
  };
  factory ExternalAgentProfile.fromJson(Map<String, dynamic> j) =>
      ExternalAgentProfile(
        j['id'] as String,
        ExternalAgentCard.fromJson(Map<String, dynamic>.from(j['card'] as Map)),
        deleting: j['deleting'] == true,
      );
}

enum ExternalTaskState {
  submitted,
  working,
  inputRequired,
  authRequired,
  completed,
  failed,
  canceled,
  rejected,
  unknown,
}

class ExternalResultPart {
  final String? text;
  final String? url;
  final String? name;
  const ExternalResultPart({this.text, this.url, this.name});
  Map<String, dynamic> toJson() => {'text': text, 'url': url, 'name': name};
  factory ExternalResultPart.fromJson(Map<String, dynamic> j) =>
      ExternalResultPart(
        text: j['text'] as String?,
        url: j['url'] as String?,
        name: j['name'] as String?,
      );
}

class ExternalTask {
  final String? id;
  final String? contextId;
  final ExternalTaskState state;
  final String? statusMessageId;
  final List<ExternalResultPart> parts;
  final bool omittedContent;
  const ExternalTask({
    this.id,
    this.contextId,
    required this.state,
    this.statusMessageId,
    this.parts = const [],
    this.omittedContent = false,
  });
  bool get terminal => {
    ExternalTaskState.completed,
    ExternalTaskState.failed,
    ExternalTaskState.canceled,
    ExternalTaskState.rejected,
  }.contains(state);
  Map<String, dynamic> toJson() => {
    'id': id,
    'contextId': contextId,
    'state': state.name,
    'statusMessageId': statusMessageId,
    'parts': parts.map((p) => p.toJson()).toList(),
    'omittedContent': omittedContent,
  };
  factory ExternalTask.fromJson(Map<String, dynamic> j) => ExternalTask(
    id: j['id'] as String?,
    contextId: j['contextId'] as String?,
    state: ExternalTaskState.values.byName(j['state'] as String),
    statusMessageId: j['statusMessageId'] as String?,
    parts: [
      for (final p in j['parts'] as List)
        ExternalResultPart.fromJson(Map<String, dynamic>.from(p as Map)),
    ],
    omittedContent: j['omittedContent'] == true,
  );
}

/// A persisted local record exists before dispatch. An unresolved record never
/// causes an automatic retry. Only confirmed server IDs can be queried/canceled.
class ExternalTaskRecord {
  final String localId;
  final String title;
  final DateTime created;
  final ExternalTask? task;
  final bool uncertain;
  final String draft;
  const ExternalTaskRecord({
    required this.localId,
    required this.title,
    required this.created,
    this.task,
    this.uncertain = false,
    this.draft = '',
  });
  ExternalTaskRecord withTask(ExternalTask? value, {bool uncertain = false}) =>
      ExternalTaskRecord(
        localId: localId,
        title: title,
        created: created,
        task: value,
        uncertain: uncertain,
        draft: value == null ? draft : '',
      );
  ExternalTaskRecord withDraft(String value) => ExternalTaskRecord(
    localId: localId,
    title: title,
    created: created,
    task: task,
    uncertain: uncertain,
    draft: value,
  );
  Map<String, dynamic> toJson() => {
    'localId': localId,
    'title': title,
    'created': created.toIso8601String(),
    'task': task?.toJson(),
    'uncertain': uncertain,
    'draft': draft,
  };
  factory ExternalTaskRecord.fromJson(Map<String, dynamic> j) =>
      ExternalTaskRecord(
        localId: j['localId'] as String,
        title: j['title'] as String,
        created: DateTime.parse(j['created'] as String),
        task: j['task'] == null
            ? null
            : ExternalTask.fromJson(
                Map<String, dynamic>.from(j['task'] as Map),
              ),
        uncertain: j['uncertain'] == true,
        draft: j['draft'] as String? ?? '',
      );
}

abstract interface class ExternalAgentGateway {
  Future<ExternalAgentCard> discover(String address);
  Future<ExternalTask> send(
    ExternalAgentCard card,
    String? credential,
    String text, {
    ExternalTask? continuation,
  });
  Future<ExternalTask> getTask(
    ExternalAgentCard card,
    String? credential,
    String id,
  );
  Future<ExternalTask> cancel(
    ExternalAgentCard card,
    String? credential,
    String id,
  );
  void close();
}
