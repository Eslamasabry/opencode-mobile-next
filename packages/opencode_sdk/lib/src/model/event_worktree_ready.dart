//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/worktree_ready_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_worktree_ready.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventWorktreeReady {
  /// Returns a new [EventWorktreeReady] instance.
  EventWorktreeReady({
    required this.id,

    required this.type,

    required this.properties,
  });

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: EventWorktreeReadyTypeEnum.unknownDefaultOpenApi,
  )
  final EventWorktreeReadyTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final WorktreeReadyData properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventWorktreeReady &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventWorktreeReady.fromJson(Map<String, dynamic> json) =>
      _$EventWorktreeReadyFromJson(json);

  Map<String, dynamic> toJson() => _$EventWorktreeReadyToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventWorktreeReadyTypeEnum {
  @JsonValue(r'worktree.ready')
  worktreePeriodReady(r'worktree.ready'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventWorktreeReadyTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
