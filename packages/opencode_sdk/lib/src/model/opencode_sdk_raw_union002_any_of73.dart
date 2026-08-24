//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/project_updated_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'opencode_sdk_raw_union002_any_of73.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class OpencodeSdkRawUnion002AnyOf73 {
  /// Returns a new [OpencodeSdkRawUnion002AnyOf73] instance.
  OpencodeSdkRawUnion002AnyOf73({
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
        OpencodeSdkRawUnion002AnyOf73TypeEnum.unknownDefaultOpenApi,
  )
  final OpencodeSdkRawUnion002AnyOf73TypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final ProjectUpdatedData properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is OpencodeSdkRawUnion002AnyOf73 &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory OpencodeSdkRawUnion002AnyOf73.fromJson(Map<String, dynamic> json) =>
      _$OpencodeSdkRawUnion002AnyOf73FromJson(json);

  Map<String, dynamic> toJson() => _$OpencodeSdkRawUnion002AnyOf73ToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum OpencodeSdkRawUnion002AnyOf73TypeEnum {
  @JsonValue(r'project.updated')
  projectPeriodUpdated(r'project.updated'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const OpencodeSdkRawUnion002AnyOf73TypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
