import 'package:opencode_sdk/opencode_sdk.dart';

void main() {
  const baseUrl = 'http://127.0.0.1:4096';
  final sdk = OpencodeSdk(basePathOverride: baseUrl);
  if (sdk.dio.options.baseUrl != baseUrl) {
    throw StateError('The generated SDK did not apply its base URL override.');
  }
  sdk.getSessionApi();
  sdk.getSessionsApi().v2SessionHistory;
  sdk.globalEventStream;
  sdk.eventSubscribeStream;
  sdk.v2SessionEventsStream;
  sdk.v2EventSubscribeStream;

  final nullableRequest = ExperimentalWorkspaceWarpRequest.fromJson({
    'id': null,
    'sessionID': 'ses_smoke',
  });
  if (nullableRequest.id != null ||
      !nullableRequest.toJson().containsKey('id')) {
    throw StateError(
      'Required nullable fields did not preserve explicit null.',
    );
  }

  final active = V2SessionActive200Response.fromJson(<String, dynamic>{
    'data': <String, dynamic>{},
  });
  if (active.data.isNotEmpty) {
    throw StateError('The active-session map did not deserialize.');
  }
}
