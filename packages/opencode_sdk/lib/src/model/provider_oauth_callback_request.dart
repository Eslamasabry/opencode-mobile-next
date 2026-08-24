//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'provider_oauth_callback_request.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ProviderOauthCallbackRequest {
  /// Returns a new [ProviderOauthCallbackRequest] instance.
  ProviderOauthCallbackRequest({required this.method, this.code});

  /// Auth method index
  @JsonKey(name: r'method', required: true, includeIfNull: false)
  final num method;

  @JsonKey(name: r'code', required: false, includeIfNull: false)
  final String? code;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ProviderOauthCallbackRequest &&
            runtimeType == other.runtimeType &&
            equals([method, code], [other.method, other.code]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([method, code]);

  factory ProviderOauthCallbackRequest.fromJson(Map<String, dynamic> json) =>
      _$ProviderOauthCallbackRequestFromJson(json);

  Map<String, dynamic> toJson() => _$ProviderOauthCallbackRequestToJson(this);

  String toString() {
    return toJson().toString();
  }
}
