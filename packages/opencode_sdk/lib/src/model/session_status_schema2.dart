//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/location_ref.dart';
import 'package:opencode_sdk/src/model/session_status_schema2_data.dart';
import 'package:opencode_sdk/src/model/session_status_schema2_durable.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_status_schema2.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionStatusSchema2 {
  /// Returns a new [SessionStatusSchema2] instance.
  SessionStatusSchema2({
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
    unknownEnumValue: SessionStatusSchema2TypeEnum.unknownDefaultOpenApi,
  )
  final SessionStatusSchema2TypeEnum type;

  @JsonKey(name: r'durable', required: false, includeIfNull: false)
  final SessionStatusSchema2Durable? durable;

  @JsonKey(name: r'location', required: false, includeIfNull: false)
  final LocationRef? location;

  @JsonKey(name: r'data', required: true, includeIfNull: false)
  final SessionStatusSchema2Data data;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionStatusSchema2 &&
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

  factory SessionStatusSchema2.fromJson(Map<String, dynamic> json) =>
      _$SessionStatusSchema2FromJson(json);

  Map<String, dynamic> toJson() => _$SessionStatusSchema2ToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum SessionStatusSchema2TypeEnum {
  @JsonValue(r'session.status')
  sessionPeriodStatus(r'session.status'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const SessionStatusSchema2TypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
