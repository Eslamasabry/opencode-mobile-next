package io.github.eslamasabry.oc.quality.sec

/**
 * The K-SEC rules of plan 16 §2.1 that are decided by tokens in the comment-stripped source. Each is a pure function
 * of the scanned files, so the planted-violation suite runs the exact code the live tree runs.
 * The declaration rules (K-SEC-4 types, K-SEC-5 routes, K-SEC-11) are in [SecDeclarationRules] (Konsist).
 */
object SecTextRules {
    /** "everywhere" in plan 16 §2.1: every source set of every product module, tests included. */
    private fun everywhere(files: List<SecFile>) = files.filter { it.isProductModule }

    private fun production(files: List<SecFile>) = files.filter { it.isProduction }

    /** `:android:*` and `:androidApp` (K-SEC-7, K-SEC-8), production only. */
    private fun androidSide(files: List<SecFile>) =
        production(files).filter { it.rel.startsWith("android/") || it.rel.startsWith("androidApp/") }

    private fun hits(rule: String, file: SecFile, regex: Regex, what: (MatchResult) -> String = { it.value }) =
        regex.findAll(file.code).map { Violation(rule, file.rel, file.lineOf(it.range.first), what(it)) }.toList()

    // ---- K-SEC-1: no autoLink / Linkify; LocalUriHandler is provided only at the shell root
    private val autoLink = Regex("""\bLinkify\b|\bautoLinkMask\b|\bsetAutoLinkMask\b|android:autoLink\b""")
    private val uriHandlerProvides = Regex("""\bLocalUriHandler\s+provides\b""")

    fun kSec1(files: List<SecFile>): List<Violation> =
        everywhere(files).flatMap { f ->
            hits("K-SEC-1", f, autoLink) +
                if (f.isKotlin && !(f.module == "ui/shell" && f.name == "App.kt")) hits("K-SEC-1", f, uriHandlerProvides) else emptyList()
        }

    // ---- K-SEC-2: custom TLS trust only in :core:net-okhttp
    private val trustTokens =
        Regex("""\bX509(Extended)?TrustManager\b|\bHostnameVerifier\b|\.sslSocketFactory\s*[({]|\.hostnameVerifier\s*[({]""")
    private val contextInit = Regex("""\b(SSLContext|TrustManagerFactory)\b""")
    private val initCall = Regex("""\.init\s*\(""")

    fun kSec2(files: List<SecFile>): List<Violation> =
        everywhere(files).filter { it.isKotlin && it.module != "core/net-okhttp" }.flatMap { f ->
            val direct = hits("K-SEC-2", f, trustTokens)
            val init = if (contextInit.containsMatchIn(f.code)) hits("K-SEC-2", f, initCall) { "SSLContext/TrustManagerFactory ${it.value}" } else emptyList()
            direct + init
        }

    // ---- K-SEC-3: no certificate pinner, logging interceptor, Ktor Logging / retry / redirect installs
    private val netBans =
        Regex(
            """\bCertificatePinner\b|\bHttpLoggingInterceptor\b|\binstall\s*\(\s*(Logging|HttpRequestRetry|HttpRedirect)\b""" +
                """|import\s+io\.ktor\.client\.plugins\.logging\b""",
        )

    fun kSec3(files: List<SecFile>): List<Violation> = everywhere(files).filter { it.isKotlin }.flatMap { hits("K-SEC-3", it, netBans) }

    // ---- K-SEC-4 (second half): Secret.reveal() only in the allowlisted files
    private val reveal = Regex("""\.reveal\s*\(\s*\)""")

    fun kSec4Reveal(files: List<SecFile>, config: SecConfig): List<Violation> =
        production(files).filter { it.isKotlin && it.name !in config.secretRevealFiles }.flatMap { hits("K-SEC-4", it, reveal) { "Secret.reveal() outside secret-reveal-allowlist.txt" } }

    // ---- K-SEC-5 (call sites): no Secret value handed to a persisted container
    private val secretDecl = Regex("""\b(?:val|var)?\s*(\w+)\s*:\s*Secret\??(?![\w.])""")
    private val persistCalls =
        Regex("""\b(rememberSaveable|rememberTextFieldState|workDataOf|putExtra|putString|putCharSequence|putParcelable|putSerializable|putByteArray|putCharArray|putStringArray|putAll)\s*(<[^>(]*>)?\s*[({]""")
    private val savedStateSet = Regex("""\b(savedStateHandle|SavedStateHandle\b[^\n]*)\s*\[[^\]]*]\s*=\s*([^\n]+)|\bsavedStateHandle\.set\s*\(""")

    fun kSec5Calls(files: List<SecFile>): List<Violation> =
        production(files).filter { it.isKotlin }.flatMap { f ->
            val names = secretDecl.findAll(f.code).map { it.groupValues[1] }.filter { it.isNotEmpty() }.toSet()
            fun mentionsSecret(s: String) = s.contains(Regex("""\bSecret\b""")) || names.any { Regex("""\b${Regex.escape(it)}\b""").containsMatchIn(s) }
            val calls =
                persistCalls.findAll(f.code).mapNotNull { m ->
                    val open = m.range.last
                    val body = if (f.code[open] == '(') KotlinText.balancedArgs(f.code, open) else braceBody(f.code, open)
                    val generic = m.groupValues[2]
                    if (mentionsSecret(body) || generic.contains("Secret")) Violation("K-SEC-5", f.rel, f.lineOf(m.range.first), "Secret into ${m.groupValues[1]}") else null
                }
            val handle =
                savedStateSet.findAll(f.code).mapNotNull { m ->
                    val rest = if (m.value.endsWith("(")) KotlinText.balancedArgs(f.code, m.range.last) else m.groupValues[2]
                    if (mentionsSecret(rest)) Violation("K-SEC-5", f.rel, f.lineOf(m.range.first), "Secret into SavedStateHandle") else null
                }
            // Data.Builder().putString(…) is caught by its put* call above
            (calls + handle).toList()
        }

    private fun braceBody(code: String, open: Int): String {
        var depth = 0
        for (i in open until code.length) {
            when (code[i]) {
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) return code.substring(open + 1, i) }
            }
        }
        return code.substring(open + 1)
    }

    // ---- K-SEC-6: clipboard writes only in the kit's KitCopy / KitPlatform
    private val clip = Regex("""\bClipData\.newPlainText\b|\bClipEntry\s*\(""")

    fun kSec6(files: List<SecFile>): List<Violation> =
        everywhere(files).filter { it.isKotlin && !(it.module == "ui/kit" && (it.name.startsWith("KitCopy") || it.name.startsWith("KitPlatform"))) }
            .flatMap { hits("K-SEC-6", it, clip) }

    // ---- K-SEC-7: every PendingIntent is FLAG_IMMUTABLE, except three reviewed files
    private val pendingIntent = Regex("""\bPendingIntent\s*\.\s*(getActivity|getActivities|getBroadcast|getService|getForegroundService)\s*\(""")

    fun kSec7(files: List<SecFile>, config: SecConfig): List<Violation> =
        androidSide(files).filter { it.isKotlin && it.name !in config.mutablePendingIntentFiles }.flatMap { f ->
            pendingIntent.findAll(f.code).mapNotNull { m ->
                val args = KotlinText.balancedArgs(f.code, m.range.last)
                if ("FLAG_IMMUTABLE" in args) null else Violation("K-SEC-7", f.rel, f.lineOf(m.range.first), "PendingIntent.${m.groupValues[1]} without FLAG_IMMUTABLE")
            }.toList()
        }

    // ---- K-SEC-8: no parseUri / getIntent / relaunching an intent taken from extras
    private val parse = Regex("""\bIntent\s*\.\s*parseUri\s*\(|\bparseUri\s*\(|\bIntent\s*\.\s*getIntent\s*\(""")
    private val fromExtras = Regex("""\b(?:val|var)\s+(\w+)\b[^\n=]*=\s*[^\n]*\bgetParcelable(Extra)?\s*(<[^>]*>)?\s*\(""")
    private val start = Regex("""\bstartActivit(y|ies)\s*\(""")

    fun kSec8(files: List<SecFile>): List<Violation> =
        androidSide(files).filter { it.isKotlin && it.name != "PackageInstallerUpdater.kt" }.flatMap { f ->
            val tainted = fromExtras.findAll(f.code).map { it.groupValues[1] }.toSet()
            val relaunch =
                start.findAll(f.code).mapNotNull { m ->
                    val args = KotlinText.balancedArgs(f.code, m.range.last)
                    val bad = Regex("""\bgetParcelable(Extra)?\s*(<[^>]*>)?\s*\(""").containsMatchIn(args) ||
                        tainted.any { Regex("""\b${Regex.escape(it)}\b""").containsMatchIn(args) }
                    if (bad) Violation("K-SEC-8", f.rel, f.lineOf(m.range.first), "startActivity with an intent taken from extras") else null
                }
            hits("K-SEC-8", f, parse) + relaunch
        }

    // ---- K-SEC-9: WebView only in KitWebPreview; its dangerous switches banned everywhere
    private val webViewNew = Regex("""(?<![\w.])WebView\s*\(""")
    private val webViewBans =
        Regex(
            """\baddJavascriptInterface\b|\bsetAllowFileAccess\s*\(\s*true\s*\)|\ballowFileAccess\s*=\s*true\b""" +
                """|\bsetAllowUniversalAccessFromFileURLs\b|\ballowUniversalAccessFromFileURLs\b|\bMIXED_CONTENT_ALWAYS_ALLOW\b""" +
                """|\bhandler\s*\.\s*proceed\s*\(|\.proceed\s*\(\s*\)""",
        )

    fun kSec9(files: List<SecFile>): List<Violation> =
        everywhere(files).filter { it.isKotlin }.flatMap { f ->
            val construct =
                if (f.name == "KitWebPreview.kt") emptyList()
                else webViewNew.findAll(f.code).filterNot { m -> Regex("""\bfun\s+$""").containsMatchIn(f.code.substring(maxOf(0, m.range.first - 8), m.range.first)) }
                    .map { Violation("K-SEC-9", f.rel, f.lineOf(it.range.first), "WebView( outside KitWebPreview.kt") }.toList()
            construct + hits("K-SEC-9", f, webViewBans)
        }

    // ---- K-SEC-10: URL literals name a KnownHosts host (or loopback)
    private val urlLiteral = Regex(""""(https?|wss?)://([^"/:?#\s$\\]*)""")
    private val loopback = setOf("127.0.0.1", "localhost", "[::1]", "")
    private val hostLiteral = Regex(""""(\*\.)?([a-z0-9-]+\.)+[a-z]{2,}"""")

    /** The hosts `KnownHosts.kt` (`:core:security`) names: every quoted host-shaped literal in it. */
    fun knownHosts(files: List<SecFile>): Set<String> =
        files.filter { it.isProduction && it.name == "KnownHosts.kt" }
            .flatMap { f -> hostLiteral.findAll(f.code).map { it.value.trim('"') } }
            .toSet()

    fun hostAllowed(host: String, allowed: Set<String>): Boolean =
        host.lowercase() in allowed || allowed.any { it.startsWith("*.") && host.lowercase().endsWith(it.substring(1)) }

    fun kSec10(files: List<SecFile>, config: SecConfig): List<Violation> {
        val allowed = knownHosts(files) + config.pendingKnownHosts
        return production(files).filter { it.isKotlin && !it.name.endsWith("Fixtures.kt") && "/src/qa/" !in it.rel }.flatMap { f ->
            urlLiteral.findAll(f.code).mapNotNull { m ->
                val host = m.groupValues[2]
                // a template ("http://$host") has no literal host: the address check of 04 §4.1 covers it
                val templated = f.code.getOrNull(m.range.last + 1) == '$' && host.isEmpty()
                when {
                    templated || host.lowercase() in loopback -> null
                    host.startsWith("[") && host == "[::1]" -> null
                    hostAllowed(host, allowed) -> null
                    else -> Violation("K-SEC-10", f.rel, f.lineOf(m.range.first), "$host is not in KnownHosts")
                }
            }.toList()
        }
    }

    // ---- K-SEC-12: no non-cryptographic Random in the modules that mint tokens and passwords
    private val randomImport = Regex("""^\s*import\s+(kotlin\.random\.(Random|\*)|java\.util\.(Random|\*))\b""", RegexOption.MULTILINE)
    private val randomQualified = Regex("""\b(kotlin\.random\.Random|java\.util\.Random)\b""")

    /** Plan 16 §2.1 K-SEC-12 scope. `:engine:analytics` is where plan 18 put the analytics of `:engine:diagnostics`. */
    fun inKSec12Scope(f: SecFile): Boolean =
        f.module in setOf("core/security", "engine/profiles", "android/linux", "android/platform", "engine/analytics") ||
            (f.module == "engine/media" && "/share/" in f.rel) ||
            (f.module == "engine/diagnostics" && "/analytics/" in f.rel)

    fun kSec12(files: List<SecFile>): List<Violation> =
        production(files).filter { it.isKotlin && inKSec12Scope(it) }.flatMap { f ->
            hits("K-SEC-12", f, randomImport) { it.value.trim() } +
                hits("K-SEC-12", f, randomQualified).filterNot { v -> randomImport.containsMatchIn(f.code.lines()[v.line - 1]) }
        }
}
