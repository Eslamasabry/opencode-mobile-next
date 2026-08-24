//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/location_info.dart';
import 'package:opencode_sdk/src/model/integration_attempt.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'v2_integration_connect_oauth200_response.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class V2IntegrationConnectOauth200Response {
  /// Returns a new [V2IntegrationConnectOauth200Response] instance.
  V2IntegrationConnectOauth200Response({
    required this.location,

    required this.data,
  });

  @JsonKey(name: r'location', required: true, includeIfNull: false)
  final LocationInfo location;

  @JsonKey(name: r'data', required: true, includeIfNull: false)
  final IntegrationAttempt data;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is V2IntegrationConnectOauth200Response &&
            runtimeType == other.runtimeType &&
            equals([location, data], [other.location, other.data]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([location, data]);

  factory V2IntegrationConnectOauth200Response.fromJson(
    Map<String, dynamic> json,
  ) => _$V2IntegrationConnectOauth200ResponseFromJson(json);

  Map<String, dynamic> toJson() =>
      _$V2IntegrationConnectOauth200ResponseToJson(this);

  String toString() {
    return toJson().toString();
  }
}
