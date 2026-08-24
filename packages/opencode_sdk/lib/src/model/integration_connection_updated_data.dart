//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'integration_connection_updated_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class IntegrationConnectionUpdatedData {
  /// Returns a new [IntegrationConnectionUpdatedData] instance.
  IntegrationConnectionUpdatedData({required this.integrationID});

  @JsonKey(name: r'integrationID', required: true, includeIfNull: false)
  final String integrationID;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is IntegrationConnectionUpdatedData &&
            runtimeType == other.runtimeType &&
            equals([integrationID], [other.integrationID]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([integrationID]);

  factory IntegrationConnectionUpdatedData.fromJson(
    Map<String, dynamic> json,
  ) => _$IntegrationConnectionUpdatedDataFromJson(json);

  Map<String, dynamic> toJson() =>
      _$IntegrationConnectionUpdatedDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}
