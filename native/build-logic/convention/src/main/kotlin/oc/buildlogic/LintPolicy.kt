package oc.buildlogic

import com.android.build.api.dsl.Lint
import java.io.File

/**
 * Android lint for every `oc.android.*` module (plan 03 §19.5, plan 16 §2.2): `abortOnError = true`, no baseline, and the
 * security table raised to error. `androidApp/lint.xml` repeats the table and holds the one path suppression
 * (`InsecureBaseConfiguration` on `network_security_config.xml`). `:quality:secTest` LintTableTest keeps the two equal
 * to plan 16 §2.2, so neither can drift alone. Owned by S1-SECGATES (the lint part of build-logic).
 */
internal object LintPolicy {
    /** Plan 16 §2.2, in its order. Suppress a single use in code with `@SuppressLint` and a reason comment, never here. */
    val SECURITY_ERRORS: Set<String> =
        setOf(
            "TrustAllX509TrustManager", "BadHostnameVerifier", "AllowAllHostnameVerifier",
            "SSLCertificateSocketFactoryCreateSocket", "SSLCertificateSocketFactoryGetInsecure",
            "WebViewClientOnReceivedSslError", "AddJavascriptInterface", "SetJavaScriptEnabled",
            "UnspecifiedImmutableFlag", "MutableImplicitPendingIntent", "UnsafeImplicitIntentLaunch", "UnsafeIntentLaunch",
            "ExportedContentProvider", "ExportedReceiver", "ExportedService", "ExportedPreferenceActivity", "GrantAllUris",
            "WorldReadableFiles", "WorldWriteableFiles", "HardcodedDebugMode", "SecureRandom", "TrulyRandom", "GetInstance",
            "UnsafeDynamicallyLoadedCode", "UnsafeNativeCodeLocation", "PackageManagerGetSignatures",
            "AcceptsUserCertificates", "DataExtractionRules", "AllowBackup", "UnprotectedSMSBroadcastReceiver",
        )

    /** The one lint block every Android convention applies. [config] is the module's `lint.xml`, when it has one. */
    fun Lint.ocLint(config: File?) {
        abortOnError = true
        error += SECURITY_ERRORS
        if (config != null && config.isFile) lintConfig = config
    }
}
