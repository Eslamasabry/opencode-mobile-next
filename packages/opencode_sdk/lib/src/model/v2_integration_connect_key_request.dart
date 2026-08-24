//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'v2_integration_connect_key_request.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class V2IntegrationConnectKeyRequest {
  /// Returns a new [V2IntegrationConnectKeyRequest] instance.
  V2IntegrationConnectKeyRequest({required this.key, this.label});

  @JsonKey(name: r'key', required: true, includeIfNull: false)
  final String key;

  @JsonKey(name: r'label', required: false, includeIfNull: false)
  final String? label;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is V2IntegrationConnectKeyRequest &&
            runtimeType == other.runtimeType &&
            equals([key, label], [other.key, other.label]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([key, label]);

  factory V2IntegrationConnectKeyRequest.fromJson(Map<String, dynamic> json) =>
      _$V2IntegrationConnectKeyRequestFromJson(json);

  Map<String, dynamic> toJson() => _$V2IntegrationConnectKeyRequestToJson(this);

  String toString() {
    return toJson().toString();
  }
}
