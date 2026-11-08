package io.github.eslamasabry.opencode_mobile.telemetry

import android.content.Context
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import io.github.eslamasabry.oc.engine.analytics.product.ProductEventSink

/**
 * B-180: Google Analytics for Firebase as the one [ProductEventSink], the twin of Flutter 2.1.5
 * `FirebaseAnalyticsAdapter`. It receives only the fixed events and words `ProductAnalytics` already checked, and only
 * while the person agreed.
 *
 * Collection is off in the manifest (`firebase_analytics_collection_enabled` false) and turned on only while the person
 * agrees. Advertising ID, SSAID and ad personalisation are off in the manifest and denied again here through consent
 * mode; the `AD_ID` permission is removed. When the build has no Firebase config [create] answers null and the app uses
 * the no-op sink.
 *
 * Same package and same Firebase app as Flutter (`google-services-io.github.eslamasabry.opencode_mobile.json`), so the
 * Analytics app-instance id kept in the app's data survives the update; [setCollectionEnabled] never resets it.
 */
internal class FirebaseAnalyticsSink private constructor(private val analytics: FirebaseAnalytics) :
    ProductEventSink {
    override fun setCollectionEnabled(enabled: Boolean) {
        analytics.setConsent(consent(enabled))
        analytics.setAnalyticsCollectionEnabled(enabled)
    }

    override fun forget() {
        analytics.resetAnalyticsData()
    }

    /** `screen_view` goes through Analytics' own screen event, so reports group pages by the fixed screen word. */
    override fun event(name: String, props: Map<String, String>) {
        analytics.logEvent(wireName(name), bundleOf(name, props))
    }

    companion object {
        /** Null when this build has no Firebase config (or the vendor could not start). */
        fun create(context: Context): FirebaseAnalyticsSink? =
            try {
                if (FirebaseApp.getApps(context).isEmpty()) null
                else FirebaseAnalyticsSink(FirebaseAnalytics.getInstance(context))
            } catch (_: Throwable) {
                null
            }

        /** Analytics storage follows the answer; every advertising signal stays denied. */
        fun consent(enabled: Boolean): Map<FirebaseAnalytics.ConsentType, FirebaseAnalytics.ConsentStatus> {
            val granted = FirebaseAnalytics.ConsentStatus.GRANTED
            val denied = FirebaseAnalytics.ConsentStatus.DENIED
            return mapOf(
                FirebaseAnalytics.ConsentType.ANALYTICS_STORAGE to if (enabled) granted else denied,
                FirebaseAnalytics.ConsentType.AD_STORAGE to denied,
                FirebaseAnalytics.ConsentType.AD_USER_DATA to denied,
                FirebaseAnalytics.ConsentType.AD_PERSONALIZATION to denied,
            )
        }

        fun wireName(name: String): String =
            if (name == SCREEN_VIEW) FirebaseAnalytics.Event.SCREEN_VIEW else name

        private fun bundleOf(name: String, props: Map<String, String>): Bundle =
            Bundle().apply {
                for ((key, value) in props) {
                    val param = if (name == SCREEN_VIEW && key == "screen") FirebaseAnalytics.Param.SCREEN_NAME else key
                    putString(param, value)
                }
            }

        private const val SCREEN_VIEW = "screen_view"
    }
}
