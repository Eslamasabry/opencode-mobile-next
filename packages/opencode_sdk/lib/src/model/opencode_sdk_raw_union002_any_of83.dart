//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/event_workspace_status_properties.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'opencode_sdk_raw_union002_any_of83.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class OpencodeSdkRawUnion002AnyOf83 {
  /// Returns a new [OpencodeSdkRawUnion002AnyOf83] instance.
  OpencodeSdkRawUnion002AnyOf83({
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
        OpencodeSdkRawUnion002AnyOf83TypeEnum.unknownDefaultOpenApi,
  )
  final OpencodeSdkRawUnion002AnyOf83TypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final EventWorkspaceStatusProperties properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is OpencodeSdkRawUnion002AnyOf83 &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory OpencodeSdkRawUnion002AnyOf83.fromJson(Map<String, dynamic> json) =>
      _$OpencodeSdkRawUnion002AnyOf83FromJson(json);

  Map<String, dynamic> toJson() => _$OpencodeSdkRawUnion002AnyOf83ToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum OpencodeSdkRawUnion002AnyOf83TypeEnum {
  @JsonValue(r'workspace.status')
  workspacePeriodStatus(r'workspace.status'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const OpencodeSdkRawUnion002AnyOf83TypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
