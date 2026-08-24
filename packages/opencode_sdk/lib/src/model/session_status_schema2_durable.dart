//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_status_schema2_durable.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionStatusSchema2Durable {
  /// Returns a new [SessionStatusSchema2Durable] instance.
  SessionStatusSchema2Durable({
    required this.aggregateID,

    required this.seq,

    required this.version,
  });

  @JsonKey(name: r'aggregateID', required: true, includeIfNull: false)
  final String aggregateID;

  @JsonKey(name: r'seq', required: true, includeIfNull: false)
  final int seq;

  @JsonKey(name: r'version', required: true, includeIfNull: false)
  final int version;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionStatusSchema2Durable &&
            runtimeType == other.runtimeType &&
            equals(
              [aggregateID, seq, version],
              [other.aggregateID, other.seq, other.version],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([aggregateID, seq, version]);

  factory SessionStatusSchema2Durable.fromJson(Map<String, dynamic> json) =>
      _$SessionStatusSchema2DurableFromJson(json);

  Map<String, dynamic> toJson() => _$SessionStatusSchema2DurableToJson(this);

  String toString() {
    return toJson().toString();
  }
}
