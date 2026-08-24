//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/v2_session_permission_create200_response_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'v2_session_permission_create200_response.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class V2SessionPermissionCreate200Response {
  /// Returns a new [V2SessionPermissionCreate200Response] instance.
  V2SessionPermissionCreate200Response({required this.data});

  @JsonKey(name: r'data', required: true, includeIfNull: false)
  final V2SessionPermissionCreate200ResponseData data;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is V2SessionPermissionCreate200Response &&
            runtimeType == other.runtimeType &&
            equals([data], [other.data]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([data]);

  factory V2SessionPermissionCreate200Response.fromJson(
    Map<String, dynamic> json,
  ) => _$V2SessionPermissionCreate200ResponseFromJson(json);

  Map<String, dynamic> toJson() =>
      _$V2SessionPermissionCreate200ResponseToJson(this);

  String toString() {
    return toJson().toString();
  }
}
