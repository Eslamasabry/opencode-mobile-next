import 'package:firebase_analytics/firebase_analytics.dart';
import 'package:firebase_core/firebase_core.dart';

import 'telemetry.dart';

/// Google Analytics for Firebase as one [TelemetryAdapter], for the fixed
/// product events in `analytics_events.dart` only.
///
/// Collection is off in the manifest (`firebase_analytics_collection_enabled`
/// false) and turned on only while the person agrees. Advertising ID, SSAID
/// and ad personalisation are off in the manifest and denied again here
/// through consent mode; the `AD_ID` permission is removed. When the build
/// has no Firebase configuration [probe] is false and nothing here runs.
class FirebaseAnalyticsAdapter implements TelemetryAdapter {
  FirebaseAnalytics? _analytics;

  @override
  Future<bool> probe() async {
    try {
      if (Firebase.apps.isEmpty) await Firebase.initializeApp();
      _analytics = FirebaseAnalytics.instance;
      return true;
    } catch (_) {
      return false;
    }
  }

  @override
  Future<void> setCollection({
    required bool usage,
    required bool crashes,
  }) async {
    final analytics = _analytics;
    if (analytics == null) return;
    await analytics.setConsent(
      analyticsStorageConsentGranted: usage,
      adStorageConsentGranted: false,
      adUserDataConsentGranted: false,
      adPersonalizationSignalsConsentGranted: false,
    );
    await analytics.setAnalyticsCollectionEnabled(usage);
    if (!usage) await analytics.resetAnalyticsData();
  }

  @override
  Future<void> setUserProperties(Map<String, String> properties) async {
    final analytics = _analytics;
    if (analytics == null) return;
    for (final entry in properties.entries) {
      await analytics.setUserProperty(name: entry.key, value: entry.value);
    }
  }

  /// Null while analytics collection is off, as Firebase answers.
  @override
  Future<String?> appInstanceId() async {
    final analytics = _analytics;
    return analytics == null ? null : await analytics.appInstanceId;
  }

  /// `screen_view` goes through Analytics' own screen call, so its reports
  /// group pages by the fixed screen word; every other event as is.
  @override
  Future<void> event(String name, Map<String, String> props) async {
    final analytics = _analytics;
    if (analytics == null) return;
    if (name == 'screen_view') {
      await analytics.logScreenView(screenName: props['screen']);
    } else {
      await analytics.logEvent(name: name, parameters: props);
    }
  }

  /// Crash reports go to Crashlytics, not here.
  @override
  Future<void> crash(
    String category,
    String stack, {
    required bool fatal,
  }) async {}
  @override
  Future<void> failure(String name, Map<String, String> keys) async {}
  @override
  Future<void> breadcrumb(String name) async {}
  @override
  Future<void> setKeys(Map<String, String> keys) async {}
  @override
  Future<void> problemReport(String text, {required bool collecting}) async {}
}
