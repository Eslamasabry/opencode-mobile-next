//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/sync_event_session_next_model_switched_sync_event.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'sync_event_session_next_model_switched.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SyncEventSessionNextModelSwitched {
  /// Returns a new [SyncEventSessionNextModelSwitched] instance.
  SyncEventSessionNextModelSwitched({
    required this.type,

    required this.id,

    required this.syncEvent,
  });

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue:
        SyncEventSessionNextModelSwitchedTypeEnum.unknownDefaultOpenApi,
  )
  final SyncEventSessionNextModelSwitchedTypeEnum type;

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(name: r'syncEvent', required: true, includeIfNull: false)
  final SyncEventSessionNextModelSwitchedSyncEvent syncEvent;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SyncEventSessionNextModelSwitched &&
            runtimeType == other.runtimeType &&
            equals(
              [type, id, syncEvent],
              [other.type, other.id, other.syncEvent],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([type, id, syncEvent]);

  factory SyncEventSessionNextModelSwitched.fromJson(
    Map<String, dynamic> json,
  ) => _$SyncEventSessionNextModelSwitchedFromJson(json);

  Map<String, dynamic> toJson() =>
      _$SyncEventSessionNextModelSwitchedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum SyncEventSessionNextModelSwitchedTypeEnum {
  @JsonValue(r'sync')
  sync_(r'sync'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const SyncEventSessionNextModelSwitchedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
