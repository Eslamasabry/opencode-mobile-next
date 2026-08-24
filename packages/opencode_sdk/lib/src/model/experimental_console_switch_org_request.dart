//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'experimental_console_switch_org_request.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ExperimentalConsoleSwitchOrgRequest {
  /// Returns a new [ExperimentalConsoleSwitchOrgRequest] instance.
  ExperimentalConsoleSwitchOrgRequest({
    required this.accountID,

    required this.orgID,
  });

  @JsonKey(name: r'accountID', required: true, includeIfNull: false)
  final String accountID;

  @JsonKey(name: r'orgID', required: true, includeIfNull: false)
  final String orgID;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ExperimentalConsoleSwitchOrgRequest &&
            runtimeType == other.runtimeType &&
            equals([accountID, orgID], [other.accountID, other.orgID]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([accountID, orgID]);

  factory ExperimentalConsoleSwitchOrgRequest.fromJson(
    Map<String, dynamic> json,
  ) => _$ExperimentalConsoleSwitchOrgRequestFromJson(json);

  Map<String, dynamic> toJson() =>
      _$ExperimentalConsoleSwitchOrgRequestToJson(this);

  String toString() {
    return toJson().toString();
  }
}
