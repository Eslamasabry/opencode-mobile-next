//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'permission_not_found_error.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class PermissionNotFoundError {
  /// Returns a new [PermissionNotFoundError] instance.
  PermissionNotFoundError({
    required this.tag,

    required this.requestID,

    required this.message,
  });

  @JsonKey(
    name: r'_tag',
    required: true,
    includeIfNull: false,
    unknownEnumValue: PermissionNotFoundErrorTagEnum.unknownDefaultOpenApi,
  )
  final PermissionNotFoundErrorTagEnum tag;

  @JsonKey(name: r'requestID', required: true, includeIfNull: false)
  final String requestID;

  @JsonKey(name: r'message', required: true, includeIfNull: false)
  final String message;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is PermissionNotFoundError &&
            runtimeType == other.runtimeType &&
            equals(
              [tag, requestID, message],
              [other.tag, other.requestID, other.message],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([tag, requestID, message]);

  factory PermissionNotFoundError.fromJson(Map<String, dynamic> json) =>
      _$PermissionNotFoundErrorFromJson(json);

  Map<String, dynamic> toJson() => _$PermissionNotFoundErrorToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum PermissionNotFoundErrorTagEnum {
  @JsonValue(r'PermissionNotFoundError')
  permissionNotFoundError(r'PermissionNotFoundError'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const PermissionNotFoundErrorTagEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
