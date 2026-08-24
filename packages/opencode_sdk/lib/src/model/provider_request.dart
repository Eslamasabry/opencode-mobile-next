//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'provider_request.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ProviderRequest {
  /// Returns a new [ProviderRequest] instance.
  ProviderRequest({required this.headers, required this.body});

  @JsonKey(name: r'headers', required: true, includeIfNull: false)
  final Map<String, String> headers;

  @JsonKey(name: r'body', required: true, includeIfNull: false)
  final Object body;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ProviderRequest &&
            runtimeType == other.runtimeType &&
            equals([headers, body], [other.headers, other.body]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([headers, body]);

  factory ProviderRequest.fromJson(Map<String, dynamic> json) =>
      _$ProviderRequestFromJson(json);

  Map<String, dynamic> toJson() => _$ProviderRequestToJson(this);

  String toString() {
    return toJson().toString();
  }
}
