//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/location_ref.dart';
import 'package:opencode_sdk/src/model/session_status_schema2_durable.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_tool_called_sync_event_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_next_tool_called.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionNextToolCalled {
  /// Returns a new [SessionNextToolCalled] instance.
  SessionNextToolCalled({
    required this.id,

    this.metadata,

    required this.type,

    this.durable,

    this.location,

    required this.data,
  });

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(name: r'metadata', required: false, includeIfNull: false)
  final Object? metadata;

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: SessionNextToolCalledTypeEnum.unknownDefaultOpenApi,
  )
  final SessionNextToolCalledTypeEnum type;

  @JsonKey(name: r'durable', required: false, includeIfNull: false)
  final SessionStatusSchema2Durable? durable;

  @JsonKey(name: r'location', required: false, includeIfNull: false)
  final LocationRef? location;

  @JsonKey(name: r'data', required: true, includeIfNull: false)
  final SyncEventSessionNextToolCalledSyncEventData data;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionNextToolCalled &&
            runtimeType == other.runtimeType &&
            equals(
              [id, metadata, type, durable, location, data],
              [
                other.id,
                other.metadata,
                other.type,
                other.durable,
                other.location,
                other.data,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([id, metadata, type, durable, location, data]);

  factory SessionNextToolCalled.fromJson(Map<String, dynamic> json) =>
      _$SessionNextToolCalledFromJson(json);

  Map<String, dynamic> toJson() => _$SessionNextToolCalledToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum SessionNextToolCalledTypeEnum {
  @JsonValue(r'session.next.tool.called')
  sessionPeriodNextPeriodToolPeriodCalled(r'session.next.tool.called'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const SessionNextToolCalledTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
