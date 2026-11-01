package io.github.eslamasabry.oc.quality.sec

import kotlin.test.Test

/**
 * P9-style proofs (plan 16 WP-16.2): every K-SEC rule fails on a planted violation and stays quiet on the near
 * misses next to it. Each planted tree is laid out like `native/`, and the rule code is the one the live test runs.
 */
class KSecPlantedTest {
    private val cm = "src/commonMain/kotlin/p"
    private val am = "src/main/kotlin/p"

    @Test
    fun `K-SEC-1 planted autoLink, Linkify and a stray LocalUriHandler provider fail`() {
        val t = Planted()
            .file("ui/kit-content/src/androidMain/kotlin/p/Text.kt", """
                fun f(v: android.widget.TextView) { v.autoLinkMask = 1; android.text.util.Linkify.addLinks(v, 1) }
            """)
            .file("feature/chat/$cm/Root.kt", "fun r() { CompositionLocalProvider(LocalUriHandler provides h) {} }")
            .file("android/res/src/main/res/layout/x.xml", """<TextView android:autoLink="web" />""")
            // near misses: the shell root may provide it; a comment never counts
            .file("ui/shell/$cm/App.kt", "fun a() { CompositionLocalProvider(LocalUriHandler provides ExternalLinkUriHandler) {} }")
            .file("ui/kit/$cm/Doc.kt", "// Linkify is banned (K-SEC-1)\nfun d() = 1")
        assertCaught(SecTextRules.kSec1(t.files()), "K-SEC-1", 4, "autoLinkMask, Linkify, provides, android:autoLink")
    }

    @Test
    fun `K-SEC-2 planted trust managers outside core net-okhttp fail`() {
        val t = Planted()
            .file("core/net/$cm/Trust.kt", """
                object All : javax.net.ssl.X509TrustManager
                fun c() { val ctx = SSLContext.getInstance("TLS"); ctx.init(null, null, null) }
                fun b(x: OkHttpClient.Builder) = x.hostnameVerifier { _, _ -> true }
            """)
            .file("core/net-okhttp/src/androidMain/kotlin/p/Pinned.kt", "class PinnedLeafTrustManager : X509ExtendedTrustManager()")
        assertCaught(SecTextRules.kSec2(t.files()), "K-SEC-2", 3, "X509TrustManager, SSLContext.init, .hostnameVerifier(")
    }

    @Test
    fun `K-SEC-3 planted pinner, logging interceptor and Ktor installs fail`() {
        val t = Planted()
            .file("core/net/$cm/Client.kt", """
                import io.ktor.client.plugins.logging.Logging
                fun k() = HttpClient { install(Logging); install(HttpRequestRetry) { }; install(HttpRedirect) }
                val p = okhttp3.CertificatePinner.DEFAULT
                val l = HttpLoggingInterceptor()
                fun ok() = HttpClient { install(HttpTimeout) }
            """)
        assertCaught(SecTextRules.kSec3(t.files()), "K-SEC-3", 6, "import, three installs, pinner, interceptor")
    }

    @Test
    fun `K-SEC-4 the plan's planted set - Provider apiKey and Login password fail, Usage, Auth and Flags pass`() {
        val t = Planted().file("core/model/$cm/Planted.kt", """
            package p
            data class Usage(val tokens: Tokens)
            data class Auth(val secretName: String)
            data class Flags(val passwordRejected: Boolean)
            data class Provider(val apiKey: String)
            data class Login(val password: CharArray)
            @Serializable class Wire(val bearerToken: String? = null)
            data class Alert(val monitorToken: String, val credentialId: String?)
            data class Ok(val password: Secret, val token: Secret?)
            class Plain(val password: String)
        """)
        val hits = t.declarations().kSec4(liveConfig)
        assertCaught(hits, "K-SEC-4", 3, "Provider.apiKey, Login.password, Wire.bearerToken")
    }

    @Test
    fun `K-SEC-4 planted reveal outside the allowlist fails, inside it passes`() {
        val t = Planted()
            .file("feature/chat/$cm/Leak.kt", "fun s(x: Secret) = x.reveal()")
            .file("core/security/$cm/Redactor.kt", "fun r(x: Secret) = x.reveal()")
        assertCaught(SecTextRules.kSec4Reveal(t.files(), liveConfig), "K-SEC-4", 1, "reveal in a feature")
    }

    @Test
    fun `K-SEC-5 planted Secret in a route key and in persisted containers fails`() {
        val t = Planted()
            .file("ui/routes/$cm/Routes.kt", """
                package p
                @Serializable data class SignIn(val profileId: String, val password: Secret) : NavKey
                @Serializable data class Chat(val sessionId: String) : AppRoute
            """)
            .file("android/platform/$am/Work.kt", """
                fun w(pw: Secret, i: Intent, b: Bundle) {
                    val d = workDataOf("pw" to pw)
                    i.putExtra("pw", pw.reveal())
                    b.putString("k", pw.toString())
                    val e = Data.Builder().putString("x", pw.reveal()).build()
                    savedStateHandle["pw"] = pw
                    i.putExtra("id", 3L)
                }
            """)
            .file("feature/chat/$cm/Field.kt", """
                @Composable fun F() { val s = rememberSaveable { Secret("") }; val ok = remember { TextFieldState() } }
            """)
        val hits = t.declarations().kSec5Routes() + SecTextRules.kSec5Calls(t.files())
        assertCaught(hits, "K-SEC-5", 7, "route, workDataOf, putExtra, putString, Data.Builder, SavedStateHandle, rememberSaveable")
    }

    @Test
    fun `K-SEC-6 planted clipboard write outside the kit fails`() {
        val t = Planted()
            .file("feature/chat/src/androidMain/kotlin/p/Copy.kt", "fun c() = ClipData.newPlainText(\"x\", \"y\")")
            .file("ui/shell/$cm/Copy.kt", "suspend fun c(cb: Clipboard) = cb.setClipEntry(ClipEntry(data))")
            .file("ui/kit/src/androidMain/kotlin/p/KitCopy.android.kt", "fun c() = ClipData.newPlainText(\"x\", \"y\")")
        assertCaught(SecTextRules.kSec6(t.files()), "K-SEC-6", 2, "feature and shell")
    }

    @Test
    fun `K-SEC-7 planted mutable PendingIntent fails, immutable and allowlisted pass`() {
        val t = Planted()
            .file("android/platform/$am/Notify.kt", """
                val a = PendingIntent.getActivity(ctx, 1, i, PendingIntent.FLAG_UPDATE_CURRENT)
                val b = PendingIntent.getBroadcast(ctx, 2, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val c = PendingIntent.getForegroundService(ctx, 3, i, FLAG_MUTABLE)
            """)
            .file("android/termux/$am/TermuxActivityCommands.kt", "val r = PendingIntent.getService(ctx, id, i, FLAG_ONE_SHOT or FLAG_MUTABLE)")
            .file("feature/chat/$cm/NotAndroid.kt", "val x = PendingIntent.getActivity(ctx, 1, i, 0)")
        assertCaught(SecTextRules.kSec7(t.files(), liveConfig), "K-SEC-7", 2, "getActivity and getForegroundService without FLAG_IMMUTABLE")
    }

    @Test
    fun `K-SEC-8 planted parseUri, getIntent and a relaunched extra intent fail`() {
        val t = Planted()
            .file("androidApp/$am/Entry.kt", """
                fun e(s: String, intent: Intent) {
                    val a = Intent.parseUri(s, 0)
                    val b = Intent.getIntent(s)
                    val next = intent.getParcelableExtra("next", Intent::class.java)
                    startActivity(next)
                    startActivity(intent.getParcelableExtra<Intent>("other"))
                    startActivity(Intent(this, MainActivity::class.java))
                }
            """)
            .file("android/platform/$am/PackageInstallerUpdater.kt", "fun u(i: Intent) { val c = i.getParcelableExtra<Intent>(EXTRA_INTENT); startActivity(c) }")
        assertCaught(SecTextRules.kSec8(t.files()), "K-SEC-8", 4, "parseUri, getIntent, two relaunches")
    }

    @Test
    fun `K-SEC-9 planted WebView construction and dangerous switches fail`() {
        val t = Planted()
            .file("feature/chat/src/androidMain/kotlin/p/Web.kt", """
                fun w(ctx: Context, handler: SslErrorHandler) {
                    val v = WebView(ctx)
                    v.addJavascriptInterface(Bridge(), "app")
                    v.settings.setAllowFileAccess(true)
                    v.settings.allowUniversalAccessFromFileURLs = true
                    v.settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    handler.proceed()
                    chain.proceed(request)
                    v.settings.allowFileAccess = false
                }
            """)
            .file("ui/kit-content/src/androidMain/kotlin/p/KitWebPreview.kt", "fun p(ctx: Context) = WebView(ctx).apply { settings.allowFileAccess = false }")
        assertCaught(SecTextRules.kSec9(t.files()), "K-SEC-9", 6, "WebView(, bridge, file access, universal access, mixed content, proceed")
    }

    @Test
    fun `K-SEC-10 planted URL literal with an unknown host fails, KnownHosts, loopback, qa and fixtures pass`() {
        val t = Planted()
            .file("core/security/$cm/KnownHosts.kt", """
                object KnownHosts { val fetch = listOf("api.github.com", "*.hf.co") }
            """)
            .file("engine/updates/$cm/Feed.kt", """
                val a = "https://api.github.com/repos/x/y/releases/latest"
                val b = "https://cdn-lfs.hf.co/model.bin"
                val c = "http://127.0.0.1:4096/api"
                val d = "ws://localhost:5100"
                val e = "http://${'$'}host:${'$'}port"
                val f = "https://evil.example.net/collect"
                val g = "wss://tracker.example.org"
            """)
            .file("engine/updates/src/qa/kotlin/p/QaFeed.kt", "val q = \"http://fixture.example.com\"")
            .file("engine/updates/$cm/FeedFixtures.kt", "val q = \"http://fixture.example.com\"")
            .file("engine/updates/src/commonTest/kotlin/p/FeedTest.kt", "val q = \"https://test.example.com\"")
        assertCaught(SecTextRules.kSec10(t.files(), SecConfig(t.root)), "K-SEC-10", 2, "evil.example.net and tracker.example.org")
    }

    @Test
    fun `K-SEC-11 planted String, Int and List event properties fail, enums, Boolean and buckets pass`() {
        val t = Planted().file("engine/analytics/$cm/Events.kt", """
            package p
            enum class Outcome { Ok, Failed }
            sealed interface AnalyticsEvent
            data class Good(val outcome: Outcome, val first: Boolean, val count: CountBucket, val took: DurationBucket, val v: VersionCode) : AnalyticsEvent
            data class Bad(val host: String, val n: Int, val tags: List<Outcome>) : AnalyticsEvent { override val name: String get() = "bad" }
        """)
        assertCaught(t.declarations().kSec11(), "K-SEC-11", 3, "String, Int, List")
    }

    @Test
    fun `K-SEC-12 planted kotlin and java Random in a listed module fail, elsewhere pass`() {
        val t = Planted()
            .file("core/security/$cm/Pw.kt", "import kotlin.random.Random\nfun p() = Random.nextBytes(16)")
            .file("android/linux/$am/Tok.kt", "fun t() = java.util.Random().nextInt()")
            .file("engine/media/$cm/share/Link.kt", "import java.util.Random\nfun l() = Random().nextLong()")
            .file("core/net/$cm/Backoff.kt", "import kotlin.random.Random\nfun j() = Random.nextDouble()")
            .file("engine/media/$cm/player/Shuffle.kt", "import kotlin.random.Random\nfun s() = Random.nextInt()")
        assertCaught(SecTextRules.kSec12(t.files()), "K-SEC-12", 3, "core/security, android/linux, engine/media share/")
    }
}
