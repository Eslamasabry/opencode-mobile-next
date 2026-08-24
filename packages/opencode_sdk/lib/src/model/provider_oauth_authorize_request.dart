//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'provider_oauth_authorize_request.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ProviderOauthAuthorizeRequest {
  /// Returns a new [ProviderOauthAuthorizeRequest] instance.
  ProviderOauthAuthorizeRequest({required this.method, this.inputs});

  /// Auth method index
  @JsonKey(name: r'method', required: true, includeIfNull: false)
  final num method;

  @JsonKey(name: r'inputs', required: false, includeIfNull: false)
  final Map<String, String>? inputs;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ProviderOauthAuthorizeRequest &&
            runtimeType == other.runtimeType &&
            equals([method, inputs], [other.method, other.inputs]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([method, inputs]);

  factory ProviderOauthAuthorizeRequest.fromJson(Map<String, dynamic> json) =>
      _$ProviderOauthAuthorizeRequestFromJson(json);

  Map<String, dynamic> toJson() => _$ProviderOauthAuthorizeRequestToJson(this);

  String toString() {
    return toJson().toString();
  }
}
