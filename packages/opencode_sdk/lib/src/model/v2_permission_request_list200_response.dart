//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/location_info.dart';
import 'package:opencode_sdk/src/model/permission_v2_request.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'v2_permission_request_list200_response.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class V2PermissionRequestList200Response {
  /// Returns a new [V2PermissionRequestList200Response] instance.
  V2PermissionRequestList200Response({
    required this.location,

    required this.data,
  });

  @JsonKey(name: r'location', required: true, includeIfNull: false)
  final LocationInfo location;

  @JsonKey(name: r'data', required: true, includeIfNull: false)
  final List<PermissionV2Request> data;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is V2PermissionRequestList200Response &&
            runtimeType == other.runtimeType &&
            equals([location, data], [other.location, other.data]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([location, data]);

  factory V2PermissionRequestList200Response.fromJson(
    Map<String, dynamic> json,
  ) => _$V2PermissionRequestList200ResponseFromJson(json);

  Map<String, dynamic> toJson() =>
      _$V2PermissionRequestList200ResponseToJson(this);

  String toString() {
    return toJson().toString();
  }
}
