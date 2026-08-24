//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/sync_event_session_next_tool_input_ended_sync_event.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'sync_event_session_next_tool_input_ended.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SyncEventSessionNextToolInputEnded {
  /// Returns a new [SyncEventSessionNextToolInputEnded] instance.
  SyncEventSessionNextToolInputEnded({
    required this.type,

    required this.id,

    required this.syncEvent,
  });

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue:
        SyncEventSessionNextToolInputEndedTypeEnum.unknownDefaultOpenApi,
  )
  final SyncEventSessionNextToolInputEndedTypeEnum type;

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(name: r'syncEvent', required: true, includeIfNull: false)
  final SyncEventSessionNextToolInputEndedSyncEvent syncEvent;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SyncEventSessionNextToolInputEnded &&
            runtimeType == other.runtimeType &&
            equals(
              [type, id, syncEvent],
              [other.type, other.id, other.syncEvent],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([type, id, syncEvent]);

  factory SyncEventSessionNextToolInputEnded.fromJson(
    Map<String, dynamic> json,
  ) => _$SyncEventSessionNextToolInputEndedFromJson(json);

  Map<String, dynamic> toJson() =>
      _$SyncEventSessionNextToolInputEndedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum SyncEventSessionNextToolInputEndedTypeEnum {
  @JsonValue(r'sync')
  sync_(r'sync'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const SyncEventSessionNextToolInputEndedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
