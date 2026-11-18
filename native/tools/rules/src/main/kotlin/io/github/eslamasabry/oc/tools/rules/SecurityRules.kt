package io.github.eslamasabry.oc.tools.rules

import io.gitlab.arturbosch.detekt.api.Config

/**
 * K-SEC rules decided by tokens in comment-free source (KSecRulesTest, `SecTextRules`). "Everywhere" is every source
 * set of every product module, tests included; "production" excludes test and qa source sets.
 */
private val KSEC_IDS =
    mapOf(
        "OcSecNoAutoLink" to "K-SEC-1", "OcSecTlsTrustOnlyInTrustGate" to "K-SEC-2", "OcSecNoPinnerOrNetLogging" to "K-SEC-3",
        "OcSecWebViewOnlyInKitWebPreview" to "K-SEC-9", "OcSecNoWeakRandomForSecrets" to "K-SEC-12",
    )

internal abstract class OcSecRule(config: Config, id: String, description: String, private val way: String) :
    OcRule(config, id, description) {
    /** The finding is already on the security owner's list (quality/config/sec/tracked-security.txt): it stays open there. */
    private fun tracked(src: Source, subject: String): Boolean {
        val ksec = issue.id.let { KSEC_IDS[it] } ?: return false
        val file = java.io.File(src.root ?: return false, "quality/config/sec/tracked-security.txt")
        if (!file.isFile) return false
        val key = "$ksec ${src.rel} $subject"
        return file.readLines().any { key in it }
    }

    protected fun hits(src: Source, regex: Regex, what: (MatchResult) -> String = { it.value }) {
        regex.findAll(src.code).forEach { m ->
            val text = what(m)
            if (!tracked(src, text.substringBefore(':'))) flag(src, src.lineOf(m.range.first), "$text: $way")
        }
    }
}

/** K-SEC-1 (replaces KSecRulesTest K-SEC-1; KSecPlantedTest keeps the planted proofs). */
internal class OcSecNoAutoLink(config: Config) :
    OcSecRule(
        config, "OcSecNoAutoLink", "No autoLink or Linkify; LocalUriHandler is provided only at the shell root.",
        "links the app did not author go through ExternalLinkPolicy and ExternalLinkOpener, never a text view's auto-link.",
    ) {
    private val autoLink = Regex("""\bLinkify\b|\bautoLinkMask\b|\bsetAutoLinkMask\b|android:autoLink\b""")
    private val uriHandlerProvides = Regex("""\bLocalUriHandler\s+provides\b""")

    override fun check(src: Source) {
        if (!src.isSecProductModule) return
        hits(src, autoLink)
        if (!(src.module == "ui/shell" && src.name == "App.kt")) hits(src, uriHandlerProvides)
    }
}

/** K-SEC-2 (replaces K-SEC-2): custom TLS trust lives only in core/net TrustGate. */
internal class OcSecTlsTrustOnlyInTrustGate(config: Config) :
    OcSecRule(
        config, "OcSecTlsTrustOnlyInTrustGate", "Custom TLS trust only in the reviewed core/net TrustGate.",
        "custom certificate trust belongs only to core/net okhttp/TrustGate.kt; ask the security owner to extend it there.",
    ) {
    private val trustTokens =
        Regex("""\bX509(Extended)?TrustManager\b|\bHostnameVerifier\b|\.sslSocketFactory\s*[({]|\.hostnameVerifier\s*[({]""")
    private val contextInit = Regex("""\b(SSLContext|TrustManagerFactory)\b""")
    private val initCall = Regex("""\.init\s*\(""")

    override fun check(src: Source) {
        if (!src.isSecProductModule) return
        if (src.module == "core/net" && src.rel.substringAfter("/kotlin/", "") == "io/github/eslamasabry/oc/core/net/okhttp/TrustGate.kt") return
        hits(src, trustTokens)
        if (contextInit.containsMatchIn(src.code)) hits(src, initCall) { "SSLContext or TrustManagerFactory ${it.value}" }
    }
}

/** K-SEC-3 (replaces K-SEC-3): no certificate pinner, logging interceptor, Ktor Logging, retry or redirect. */
internal class OcSecNoPinnerOrNetLogging(config: Config) :
    OcSecRule(
        config, "OcSecNoPinnerOrNetLogging", "No certificate pinner, HTTP logging, Ktor Logging, retry or redirect.",
        "a pinner breaks users behind a corporate proxy and a logging plugin prints credentials; remove it.",
    ) {
    private val netBans =
        Regex(
            """\bCertificatePinner\b|\bHttpLoggingInterceptor\b|\binstall\s*\(\s*(Logging|HttpRequestRetry|HttpRedirect)\b""" +
                """|import\s+io\.ktor\.client\.plugins\.logging\b""",
        )

    override fun check(src: Source) {
        if (src.isSecProductModule) hits(src, netBans)
    }
}

/** K-SEC-9 (replaces K-SEC-9): WebView only in KitWebPreview, never with a bridge, file access or proceed. */
internal class OcSecWebViewOnlyInKitWebPreview(config: Config) :
    OcSecRule(
        config, "OcSecWebViewOnlyInKitWebPreview", "WebView only in KitWebPreview, and never with a bridge or file access.",
        "preview web pages through the kit's KitWebPreview; a WebView elsewhere skips its link policy.",
    ) {
    private val webViewNew = Regex("""(?<![\w.])WebView\s*\(""")
    private val webViewBans =
        Regex(
            """\baddJavascriptInterface\b|\bsetAllowFileAccess\s*\(\s*true\s*\)|\ballowFileAccess\s*=\s*true\b""" +
                """|\bsetAllowUniversalAccessFromFileURLs\b|\ballowUniversalAccessFromFileURLs\b|\bMIXED_CONTENT_ALWAYS_ALLOW\b""" +
                """|\bhandler\s*\.\s*proceed\s*\(|\.proceed\s*\(\s*\)""",
        )
    private val funBefore = Regex("""\bfun\s+$""")

    override fun check(src: Source) {
        if (!src.isSecProductModule) return
        if (src.name != "KitWebPreview.kt") {
            val code = src.code
            webViewNew.findAll(code)
                .filterNot { m -> funBefore.containsMatchIn(code.substring(maxOf(0, m.range.first - 8), m.range.first)) }
                .forEach { flag(src, src.lineOf(it.range.first), "WebView( outside KitWebPreview.kt: preview web pages through the kit's KitWebPreview.") }
        }
        hits(src, webViewBans)
    }
}

/** K-SEC-12 (replaces K-SEC-12): no kotlin or java Random where tokens and passwords are made. */
internal class OcSecNoWeakRandomForSecrets(config: Config) :
    OcSecRule(
        config, "OcSecNoWeakRandomForSecrets", "No non-cryptographic Random in the modules that mint tokens and passwords.",
        "use SecureRandom (or the secret generator in core/security); kotlin.random and java.util.Random are guessable.",
    ) {
    private val randomImport = Regex("""^\s*import\s+(kotlin\.random\.(Random|\*)|java\.util\.(Random|\*))\b""", RegexOption.MULTILINE)
    private val randomQualified = Regex("""\b(kotlin\.random\.Random|java\.util\.Random)\b""")

    private fun inScope(f: Source): Boolean =
        f.module in setOf("core/security", "engine/profiles", "android/linux", "android/platform", "engine/analytics") ||
            (f.module == "engine/media" && "/share/" in f.rel) ||
            (f.module == "engine/diagnostics" && "/analytics/" in f.rel)

    override fun check(src: Source) {
        if (!src.isSecProduction || !inScope(src)) return
        hits(src, randomImport) { it.value.trim() }
        val lines = src.code.lines()
        randomQualified.findAll(src.code).forEach { m ->
            val line = src.lineOf(m.range.first)
            if (!randomImport.containsMatchIn(lines[line - 1])) flag(src, line, "${m.value}: use SecureRandom; kotlin.random and java.util.Random are guessable.")
        }
    }
}
