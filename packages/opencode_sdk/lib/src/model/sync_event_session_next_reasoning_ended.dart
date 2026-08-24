//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/sync_event_session_next_reasoning_ended_sync_event.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'sync_event_session_next_reasoning_ended.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SyncEventSessionNextReasoningEnded {
  /// Returns a new [SyncEventSessionNextReasoningEnded] instance.
  SyncEventSessionNextReasoningEnded({
    required this.type,

    required this.id,

    required this.syncEvent,
  });

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue:
        SyncEventSessionNextReasoningEndedTypeEnum.unknownDefaultOpenApi,
  )
  final SyncEventSessionNextReasoningEndedTypeEnum type;

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(name: r'syncEvent', required: true, includeIfNull: false)
  final SyncEventSessionNextReasoningEndedSyncEvent syncEvent;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SyncEventSessionNextReasoningEnded &&
            runtimeType == other.runtimeType &&
            equals(
              [type, id, syncEvent],
              [other.type, other.id, other.syncEvent],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([type, id, syncEvent]);

  factory SyncEventSessionNextReasoningEnded.fromJson(
    Map<String, dynamic> json,
  ) => _$SyncEventSessionNextReasoningEndedFromJson(json);

  Map<String, dynamic> toJson() =>
      _$SyncEventSessionNextReasoningEndedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum SyncEventSessionNextReasoningEndedTypeEnum {
  @JsonValue(r'sync')
  sync_(r'sync'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const SyncEventSessionNextReasoningEndedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
