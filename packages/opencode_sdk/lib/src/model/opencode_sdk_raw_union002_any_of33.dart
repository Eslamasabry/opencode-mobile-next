//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/sync_event_session_next_tool_progress_sync_event_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'opencode_sdk_raw_union002_any_of33.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class OpencodeSdkRawUnion002AnyOf33 {
  /// Returns a new [OpencodeSdkRawUnion002AnyOf33] instance.
  OpencodeSdkRawUnion002AnyOf33({
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
    unknownEnumValue:
        OpencodeSdkRawUnion002AnyOf33TypeEnum.unknownDefaultOpenApi,
  )
  final OpencodeSdkRawUnion002AnyOf33TypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final SyncEventSessionNextToolProgressSyncEventData properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is OpencodeSdkRawUnion002AnyOf33 &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory OpencodeSdkRawUnion002AnyOf33.fromJson(Map<String, dynamic> json) =>
      _$OpencodeSdkRawUnion002AnyOf33FromJson(json);

  Map<String, dynamic> toJson() => _$OpencodeSdkRawUnion002AnyOf33ToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum OpencodeSdkRawUnion002AnyOf33TypeEnum {
  @JsonValue(r'session.next.tool.progress')
  sessionPeriodNextPeriodToolPeriodProgress(r'session.next.tool.progress'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const OpencodeSdkRawUnion002AnyOf33TypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
