//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/session_durable_event.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_history.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionHistory {
  /// Returns a new [SessionHistory] instance.
  SessionHistory({required this.data, required this.hasMore});

  @JsonKey(name: r'data', required: true, includeIfNull: false)
  final List<SessionDurableEvent> data;

  @JsonKey(name: r'hasMore', required: true, includeIfNull: false)
  final bool hasMore;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionHistory &&
            runtimeType == other.runtimeType &&
            equals([data, hasMore], [other.data, other.hasMore]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([data, hasMore]);

  factory SessionHistory.fromJson(Map<String, dynamic> json) =>
      _$SessionHistoryFromJson(json);

  Map<String, dynamic> toJson() => _$SessionHistoryToJson(this);

  String toString() {
    return toJson().toString();
  }
}
