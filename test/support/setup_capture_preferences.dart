import 'package:shared_preferences/shared_preferences.dart';

/// Empty synthetic preferences for the setup widget capture tests.
Future<SharedPreferences> setupCapturePreferences() async {
  SharedPreferences.setMockInitialValues({});
  return SharedPreferences.getInstance();
}
