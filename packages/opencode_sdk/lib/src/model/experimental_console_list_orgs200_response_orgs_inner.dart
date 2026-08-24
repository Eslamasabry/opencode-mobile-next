//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'experimental_console_list_orgs200_response_orgs_inner.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ExperimentalConsoleListOrgs200ResponseOrgsInner {
  /// Returns a new [ExperimentalConsoleListOrgs200ResponseOrgsInner] instance.
  ExperimentalConsoleListOrgs200ResponseOrgsInner({
    required this.accountID,

    required this.accountEmail,

    required this.accountUrl,

    required this.orgID,

    required this.orgName,

    required this.active,
  });

  @JsonKey(name: r'accountID', required: true, includeIfNull: false)
  final String accountID;

  @JsonKey(name: r'accountEmail', required: true, includeIfNull: false)
  final String accountEmail;

  @JsonKey(name: r'accountUrl', required: true, includeIfNull: false)
  final String accountUrl;

  @JsonKey(name: r'orgID', required: true, includeIfNull: false)
  final String orgID;

  @JsonKey(name: r'orgName', required: true, includeIfNull: false)
  final String orgName;

  @JsonKey(name: r'active', required: true, includeIfNull: false)
  final bool active;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ExperimentalConsoleListOrgs200ResponseOrgsInner &&
            runtimeType == other.runtimeType &&
            equals(
              [accountID, accountEmail, accountUrl, orgID, orgName, active],
              [
                other.accountID,
                other.accountEmail,
                other.accountUrl,
                other.orgID,
                other.orgName,
                other.active,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([
        accountID,
        accountEmail,
        accountUrl,
        orgID,
        orgName,
        active,
      ]);

  factory ExperimentalConsoleListOrgs200ResponseOrgsInner.fromJson(
    Map<String, dynamic> json,
  ) => _$ExperimentalConsoleListOrgs200ResponseOrgsInnerFromJson(json);

  Map<String, dynamic> toJson() =>
      _$ExperimentalConsoleListOrgs200ResponseOrgsInnerToJson(this);

  String toString() {
    return toJson().toString();
  }
}
