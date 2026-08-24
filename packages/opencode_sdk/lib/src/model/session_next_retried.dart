//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/location_ref.dart';
import 'package:opencode_sdk/src/model/sync_event_session_next_retried_sync_event_data.dart';
import 'package:opencode_sdk/src/model/session_status_schema2_durable.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_next_retried.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionNextRetried {
  /// Returns a new [SessionNextRetried] instance.
  SessionNextRetried({
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
    unknownEnumValue: SessionNextRetriedTypeEnum.unknownDefaultOpenApi,
  )
  final SessionNextRetriedTypeEnum type;

  @JsonKey(name: r'durable', required: false, includeIfNull: false)
  final SessionStatusSchema2Durable? durable;

  @JsonKey(name: r'location', required: false, includeIfNull: false)
  final LocationRef? location;

  @JsonKey(name: r'data', required: true, includeIfNull: false)
  final SyncEventSessionNextRetriedSyncEventData data;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionNextRetried &&
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

  factory SessionNextRetried.fromJson(Map<String, dynamic> json) =>
      _$SessionNextRetriedFromJson(json);

  Map<String, dynamic> toJson() => _$SessionNextRetriedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum SessionNextRetriedTypeEnum {
  @JsonValue(r'session.next.retried')
  sessionPeriodNextPeriodRetried(r'session.next.retried'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const SessionNextRetriedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
