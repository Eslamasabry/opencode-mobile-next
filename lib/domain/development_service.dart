/// A user-reviewed command and destination, scoped to one saved server and
/// project directory. Saving it never executes the command.
class DevelopmentService {
  const DevelopmentService({
    required this.id,
    required this.name,
    required this.command,
    required this.directory,
    required this.url,
    this.workspace,
    this.run,
  });

  final String id;
  final String name;
  final String command;
  final String directory;
  final String? workspace;
  final String url;
  final DevelopmentServiceRun? run;

  DevelopmentService withRun(DevelopmentServiceRun? value) =>
      DevelopmentService(
        id: id,
        name: name,
        command: command,
        directory: directory,
        workspace: workspace,
        url: url,
        run: value,
      );

  Map<String, dynamic> toJson() => {
    'id': id,
    'name': name,
    'command': command,
    'directory': directory,
    'workspace': workspace,
    'url': url,
    'run': run?.toJson(),
  };

  factory DevelopmentService.fromJson(Map<String, dynamic> json) {
    String value(String key, int max) {
      final raw = json[key];
      if (raw is! String || raw.trim().isEmpty || raw.length > max) {
        throw const FormatException('Invalid saved service');
      }
      return raw;
    }

    return DevelopmentService(
      id: value('id', 100),
      name: value('name', 80),
      command: value('command', 4096),
      directory: value('directory', 4096),
      workspace: json['workspace'] as String?,
      url: json['url'] as String? ?? '',
      run: json['run'] == null
          ? null
          : DevelopmentServiceRun.fromJson(
              Map<String, dynamic>.from(json['run'] as Map),
            ),
    );
  }
}

/// Written BEFORE the create request, so a lost response never silently
/// authorizes a duplicate start. Only the matching server metadata can recover
/// an unknown ID; an arbitrary discovered shell is never adopted.
class DevelopmentServiceRun {
  const DevelopmentServiceRun({
    required this.ownerToken,
    this.shellID,
    this.startedAt,
    this.stopped = false,
  });
  final String ownerToken;
  final String? shellID;
  final int? startedAt;
  final bool stopped;

  Map<String, dynamic> toJson() => {
    'owner': ownerToken,
    'shellID': shellID,
    'startedAt': startedAt,
    'stopped': stopped,
  };

  factory DevelopmentServiceRun.fromJson(Map<String, dynamic> json) {
    final owner = json['owner'];
    if (owner is! String || owner.isEmpty || owner.length > 100) {
      throw const FormatException('Invalid service ownership record');
    }
    return DevelopmentServiceRun(
      ownerToken: owner,
      shellID: json['shellID'] as String?,
      startedAt: json['startedAt'] as int?,
      stopped: json['stopped'] == true,
    );
  }
}

enum DevelopmentServiceStatus { notStarted, running, stopped, unknown }
