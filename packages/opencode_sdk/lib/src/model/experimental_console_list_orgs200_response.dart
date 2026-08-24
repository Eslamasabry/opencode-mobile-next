//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/experimental_console_list_orgs200_response_orgs_inner.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'experimental_console_list_orgs200_response.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ExperimentalConsoleListOrgs200Response {
  /// Returns a new [ExperimentalConsoleListOrgs200Response] instance.
  ExperimentalConsoleListOrgs200Response({required this.orgs});

  @JsonKey(name: r'orgs', required: true, includeIfNull: false)
  final List<ExperimentalConsoleListOrgs200ResponseOrgsInner> orgs;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ExperimentalConsoleListOrgs200Response &&
            runtimeType == other.runtimeType &&
            equals([orgs], [other.orgs]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([orgs]);

  factory ExperimentalConsoleListOrgs200Response.fromJson(
    Map<String, dynamic> json,
  ) => _$ExperimentalConsoleListOrgs200ResponseFromJson(json);

  Map<String, dynamic> toJson() =>
      _$ExperimentalConsoleListOrgs200ResponseToJson(this);

  String toString() {
    return toJson().toString();
  }
}
