//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'v2_integration_connect_oauth_request.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class V2IntegrationConnectOauthRequest {
  /// Returns a new [V2IntegrationConnectOauthRequest] instance.
  V2IntegrationConnectOauthRequest({
    required this.methodID,

    required this.inputs,

    this.label,
  });

  @JsonKey(name: r'methodID', required: true, includeIfNull: false)
  final String methodID;

  @JsonKey(name: r'inputs', required: true, includeIfNull: false)
  final Map<String, String> inputs;

  @JsonKey(name: r'label', required: false, includeIfNull: false)
  final String? label;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is V2IntegrationConnectOauthRequest &&
            runtimeType == other.runtimeType &&
            equals(
              [methodID, inputs, label],
              [other.methodID, other.inputs, other.label],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([methodID, inputs, label]);

  factory V2IntegrationConnectOauthRequest.fromJson(
    Map<String, dynamic> json,
  ) => _$V2IntegrationConnectOauthRequestFromJson(json);

  Map<String, dynamic> toJson() =>
      _$V2IntegrationConnectOauthRequestToJson(this);

  String toString() {
    return toJson().toString();
  }
}
