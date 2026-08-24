//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'v2_credential_update_request.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class V2CredentialUpdateRequest {
  /// Returns a new [V2CredentialUpdateRequest] instance.
  V2CredentialUpdateRequest({required this.label});

  @JsonKey(name: r'label', required: true, includeIfNull: false)
  final String label;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is V2CredentialUpdateRequest &&
            runtimeType == other.runtimeType &&
            equals([label], [other.label]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([label]);

  factory V2CredentialUpdateRequest.fromJson(Map<String, dynamic> json) =>
      _$V2CredentialUpdateRequestFromJson(json);

  Map<String, dynamic> toJson() => _$V2CredentialUpdateRequestToJson(this);

  String toString() {
    return toJson().toString();
  }
}
